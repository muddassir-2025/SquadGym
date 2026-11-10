package com.muddassir.squadgym.data.local

import com.muddassir.squadgym.data.remote.dto.MemberDto
import com.muddassir.squadgym.data.remote.dto.MembershipSummaryDto
import com.muddassir.squadgym.data.remote.dto.PlanDto
import com.muddassir.squadgym.data.remote.dto.ShiftRefDto
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer

data class Cached<T>(val value: T, val cachedAt: Long)

/**
 * Disk cache facade.
 *
 * * Member rows are normalised so the directory/dashboard can be shown offline and
 *   insta-rendered on warm start.
 * * Everything else is stored as a versioned JSON document keyed by the full query,
 *   so a filtered list can never show another filter's rows.
 */
class CacheStore(
    private val database: SquadGymDatabase,
    private val json: Json,
) {
    private val dao = database.cacheDao()

    suspend fun <T> put(key: String, serializer: KSerializer<T>, value: T, now: Long = System.currentTimeMillis()) {
        dao.putDocument(
            CachedDocumentEntity(
                key = key,
                json = json.encodeToString(serializer, value),
                cachedAt = now,
            ),
        )
    }

    suspend fun <T> get(key: String, serializer: KSerializer<T>): Cached<T>? {
        val document = dao.document(key) ?: return null
        return runCatching { Cached(json.decodeFromString(serializer, document.json), document.cachedAt) }.getOrNull()
    }

    suspend fun cacheMembers(members: List<MemberDto>, now: Long = System.currentTimeMillis()) {
        if (members.isEmpty()) return
        dao.upsertMembers(members.map { it.toEntity(now) })
        // Drop rows for members that no longer exist on the server.
        val keep = members.map { it.id }.toSet()
        val existing = dao.members().map { it.id }.filterNot { keep.contains(it) }
        if (existing.isNotEmpty()) dao.deleteMembers(existing)
    }

    suspend fun removeMember(memberId: String) {
        dao.deleteMembers(listOf(memberId))
    }

    suspend fun cachedMembers(): List<MemberDto> = dao.members().map { it.toDto() }

    suspend fun cachedMember(memberId: String): MemberDto? = dao.member(memberId)?.toDto()

    suspend fun cachePlans(plans: List<PlanDto>) {
        if (plans.isEmpty()) {
            dao.clearPlans()
            return
        }
        dao.upsertPlans(
            plans.map {
                CachedPlanEntity(
                    id = it.id,
                    name = it.name,
                    durationDays = it.durationDays,
                    priceCents = it.priceCents ?: 0,
                    currency = it.currency,
                    isActive = it.isActive,
                    sortOrder = it.sortOrder,
                    description = it.description,
                )
            },
        )
        // Drop plans the server no longer returns so offline lists stay truthful.
        dao.deletePlansExcept(plans.map { it.id })
    }

    suspend fun cachedPlans(): List<PlanDto> = dao.plans().map {
        PlanDto(
            id = it.id,
            name = it.name,
            durationDays = it.durationDays,
            priceCents = it.priceCents ?: 0,
            currency = it.currency,
            isActive = it.isActive,
            sortOrder = it.sortOrder,
            description = it.description,
        )
    }

    suspend fun <T> list(key: String, serializer: KSerializer<T>): List<T> =
        get(key, ListSerializer(serializer))?.value.orEmpty()

    /** Called on logout: cached member data must not leak across sessions. */
    suspend fun clearAll() {
        database.clearAll()
    }

    fun memberCacheKey(scope: String): String = "members:$scope"

    private fun MemberDto.toEntity(now: Long) = CachedMemberEntity(
        id = id,
        fullName = fullName,
        email = email,
        phone = phone,
        photoUrl = photoUrl,
        imageVersion = imageVersion,
        planName = membership?.planName,
        shiftName = shift?.name,
        membershipStatus = membershipStatus,
        endDate = membership?.endDate,
        score = score,
        archived = isArchived,
        cachedAt = now,
    )

    private fun CachedMemberEntity.toDto() = MemberDto(
        id = id,
        fullName = fullName,
        email = email,
        phone = phone,
        photoUrl = photoUrl,
        imageVersion = imageVersion,
        membershipStatus = membershipStatus,
        score = score,
        archivedAt = if (archived) "cached" else null,
        shift = shiftName?.let { ShiftRefDto(id = "", name = it) },
        membership = endDate?.let {
            MembershipSummaryDto(
                id = "",
                planName = planName,
                endDate = it,
                status = membershipStatus,
            )
        },
    )
}
