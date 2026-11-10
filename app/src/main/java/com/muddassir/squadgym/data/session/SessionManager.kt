package com.muddassir.squadgym.data.session

import com.muddassir.squadgym.core.error.AppError
import com.muddassir.squadgym.data.local.SecureTokenStore
import com.muddassir.squadgym.data.remote.dto.GymDto
import com.muddassir.squadgym.data.remote.dto.ProfileDto
import com.muddassir.squadgym.data.remote.dto.SessionDto
import com.muddassir.squadgym.data.remote.dto.ShiftDto
import com.muddassir.squadgym.data.remote.dto.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(
        val user: UserDto,
        val gym: GymDto,
        val shifts: List<ShiftDto>,
        /** True when rendered from the encrypted cache before the server confirms. */
        val cached: Boolean = false,
    ) : SessionState
}

/**
 * Single source of truth for "who is signed in".
 *
 * Tokens live in [SecureTokenStore]; the profile is cached so a warm start can
 * render instantly, then refreshed from `/auth/me`. Navigation reacts to [state].
 */
class SessionManager(
    private val tokenStore: SecureTokenStore,
    private val json: Json,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    val currentUser: UserDto?
        get() = (_state.value as? SessionState.SignedIn)?.user

    val currentGym: GymDto?
        get() = (_state.value as? SessionState.SignedIn)?.gym

    val hasStoredSession: Boolean
        get() = tokenStore.refreshToken != null && tokenStore.cachedUserJson != null

    /** Warm start: decrypt the cached profile. Returns true when a session existed. */
    fun restoreFromCache(): Boolean {
        val userJson = tokenStore.cachedUserJson ?: return false
        val gymJson = tokenStore.cachedGymJson ?: return false
        if (tokenStore.refreshToken == null) return false
        return try {
            val user = json.decodeFromString(UserDto.serializer(), userJson)
            val gym = json.decodeFromString(GymDto.serializer(), gymJson)
            val shifts = tokenStore.cachedShiftsJson
                ?.let { runCatching { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ShiftDto.serializer()), it) }.getOrNull() }
                .orEmpty()
            _state.value = SessionState.SignedIn(user, gym, shifts, cached = true)
            true
        } catch (_: Exception) {
            tokenStore.clearSession()
            false
        }
    }

    fun onSignedIn(session: SessionDto) {
        persist(session)
        _state.value = SessionState.SignedIn(session.user, session.gym, session.shifts)
    }

    fun onProfileLoaded(profile: ProfileDto) {
        tokenStore.cachedUserJson = json.encodeToString(UserDto.serializer(), profile.user)
        tokenStore.cachedGymJson = json.encodeToString(GymDto.serializer(), profile.gym)
        tokenStore.cachedShiftsJson = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(ShiftDto.serializer()),
            profile.shifts,
        )
        _state.value = SessionState.SignedIn(profile.user, profile.gym, profile.shifts)
    }

    /** Called by the OkHttp authenticator after a silent token refresh. */
    fun onTokensRefreshed(session: SessionDto) {
        persist(session)
        val current = _state.value
        val user = if (current is SessionState.SignedIn) current.user else session.user
        val gym = if (current is SessionState.SignedIn) current.gym else session.gym
        val shifts = if (current is SessionState.SignedIn) current.shifts else session.shifts
        _state.value = SessionState.SignedIn(user, gym, shifts)
    }

    fun forceSignOut() {
        tokenStore.clearSession()
        _state.value = SessionState.SignedOut
    }

    fun signedOut() {
        forceSignOut()
    }

    private fun persist(session: SessionDto) {
        tokenStore.saveTokens(session.accessToken, session.refreshToken)
        tokenStore.cachedUserJson = json.encodeToString(UserDto.serializer(), session.user)
        tokenStore.cachedGymJson = json.encodeToString(GymDto.serializer(), session.gym)
        tokenStore.cachedShiftsJson = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(ShiftDto.serializer()),
            session.shifts,
        )
    }

    companion object {
        /** Error shown when a protected call fails after retries. */
        val sessionExpired = AppError.Unauthorized
    }
}
