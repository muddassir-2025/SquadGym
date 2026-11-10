package com.muddassir.squadgym.data.repository

import com.muddassir.squadgym.core.error.ApiResult
import com.muddassir.squadgym.core.error.map
import com.muddassir.squadgym.data.local.CacheStore
import com.muddassir.squadgym.data.remote.ApiCaller
import com.muddassir.squadgym.data.remote.api.MemberApi
import com.muddassir.squadgym.data.remote.dto.ArchiveMemberRequest
import com.muddassir.squadgym.data.remote.dto.CreateMemberRequest
import com.muddassir.squadgym.data.remote.dto.CreateMemberResponse
import com.muddassir.squadgym.data.remote.dto.MemberContextDto
import com.muddassir.squadgym.data.remote.dto.MemberDetailDto
import com.muddassir.squadgym.data.remote.dto.MemberDto
import com.muddassir.squadgym.data.remote.dto.MemberListDto
import com.muddassir.squadgym.data.remote.dto.MembershipHistoryResponse
import com.muddassir.squadgym.data.remote.dto.PaginationMeta
import com.muddassir.squadgym.data.remote.dto.PhotoUploadResponse
import com.muddassir.squadgym.data.remote.dto.UpdateMemberRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class MemberRepository(
    private val api: MemberApi,
    cache: CacheStore,
    caller: ApiCaller,
) : CachedRepository(cache, caller) {

    data class ListFilter(
        val tab: String = "all",
        val search: String = "",
        val planId: String? = null,
        val shiftId: String? = null,
        val gender: String? = null,
        val status: String? = null,
        val sort: String? = null,
        val page: Int = 1,
        val pageSize: Int = 20,
    ) {
        fun toQuery(): Map<String, String> = buildMap {
            put("tab", tab)
            put("page", page.toString())
            put("pageSize", pageSize.toString())
            if (search.isNotBlank()) put("search", search.trim())
            planId?.let { put("planId", it) }
            shiftId?.let { put("shiftId", it) }
            gender?.let { put("gender", it) }
            status?.let { put("status", it) }
            sort?.let { put("sort", it) }
        }

        val cacheKey: String
            get() = "members:list:${tab}:${search.trim()}:${planId.orEmpty()}:${shiftId.orEmpty()}:" +
                "${gender.orEmpty()}:${status.orEmpty()}:${sort.orEmpty()}:$page:$pageSize"

        val isDirectory: Boolean
            get() = tab == "all" && search.isBlank() && planId == null && shiftId == null &&
                gender == null && status == null && page == 1
    }

    suspend fun list(filter: ListFilter): ApiResult<MemberListDto> {
        val cached = cache.get(filter.cacheKey, MemberListDto.serializer())
        val result = caller.call { api.list(filter.toQuery()) }

        return when (result) {
            is ApiResult.Success -> {
                cache.put(filter.cacheKey, MemberListDto.serializer(), result.data)
                if (filter.isDirectory) cache.cacheMembers(result.data.members)
                result
            }

            is ApiResult.Failure -> {
                if (caller.shouldUseCache(result.error)) {
                    val offline = if (filter.isDirectory) {
                        cached?.value ?: offlineDirectory()
                    } else {
                        cached?.value
                    }
                    if (offline != null) {
                        ApiResult.Success(offline, fromCache = true, cachedAt = cached?.cachedAt)
                    } else {
                        result
                    }
                } else {
                    result
                }
            }
        }
    }

    private suspend fun offlineDirectory(): MemberListDto? {
        val members = cache.cachedMembers()
        if (members.isEmpty()) return null
        return MemberListDto(
            members = members,
            context = MemberContextDto(),
            meta = PaginationMeta(page = 1, pageSize = members.size, total = members.size, totalPages = 1),
        )
    }

    suspend fun detail(memberId: String): ApiResult<MemberDetailDto> =
        load("member:$memberId", MemberDetailDto.serializer()) { api.detail(memberId) }

    suspend fun create(request: CreateMemberRequest): ApiResult<CreateMemberResponse> =
        caller.call { api.create(request) }

    suspend fun update(memberId: String, request: UpdateMemberRequest): ApiResult<MemberDto> {
        val result = caller.call { api.update(memberId, request) }
        if (result is ApiResult.Success) {
            cache.removeMember(memberId)
            cache.get("member:$memberId", MemberDetailDto.serializer())?.let { cached ->
                cache.put(
                    "member:$memberId",
                    MemberDetailDto.serializer(),
                    cached.value.copy(member = result.data),
                )
            }
        }
        return result
    }

    suspend fun archive(memberId: String, reason: String?): ApiResult<Boolean> {
        val result = caller.call { api.archive(memberId, ArchiveMemberRequest(reason)) }
        if (result is ApiResult.Success) cache.removeMember(memberId)
        return result.map { it.archived }
    }

    suspend fun restore(memberId: String): ApiResult<Boolean> {
        val result = caller.call { api.restore(memberId) }
        if (result is ApiResult.Success) cache.removeMember(memberId)
        return result.map { !it.archived }
    }

    suspend fun uploadPhoto(
        memberId: String,
        bytes: ByteArray,
        mimeType: String,
        fileName: String = "photo.jpg",
    ): ApiResult<PhotoUploadResponse> {
        val part = MultipartBody.Part.createFormData(
            name = "photo",
            filename = fileName,
            body = bytes.toRequestBody(mimeType.toMediaType()),
        )
        val result = caller.call { api.uploadPhoto(memberId, part) }
        if (result is ApiResult.Success) cache.removeMember(memberId)
        return result
    }

    suspend fun membershipHistory(memberId: String): ApiResult<MembershipHistoryResponse> =
        caller.call { api.membershipHistory(memberId) }
}
