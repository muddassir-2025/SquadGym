package com.muddassir.squadgym.data.remote.api

import com.muddassir.squadgym.data.remote.dto.ApiEnvelope
import com.muddassir.squadgym.data.remote.dto.UndoRenewalResultDto
import retrofit2.http.POST
import retrofit2.http.Path

interface MembershipApi {
    @POST("memberships/renewals/{id}/undo")
    suspend fun undoRenewal(@Path("id") renewalId: String): ApiEnvelope<UndoRenewalResultDto>
}
