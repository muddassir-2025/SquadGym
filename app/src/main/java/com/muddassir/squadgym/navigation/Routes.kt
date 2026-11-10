package com.muddassir.squadgym.navigation

import android.net.Uri

object Routes {
    // Unauthenticated
    const val LOGIN = "login"
    const val FORGOT_PASSWORD = "forgot-password"
    const val INVITE = "invite/{token}"
    const val INVITE_ENTRY = "invite-entry"

    // Staff
    const val DASHBOARD = "dashboard"
    const val ATTENDANCE = "attendance"
    const val MEMBERS = "members"
    const val MEMBER_DETAIL = "member/{memberId}"
    const val MEMBER_NEW = "member-new"
    const val MEMBER_EDIT = "member/{memberId}/edit"
    const val LEADERBOARD = "leaderboard"
    const val PROFILE = "profile"

    // Member
    const val MEMBER_HOME = "home"

    // Super Admin
    const val ADMIN_TEAM = "admin-team"
    const val ADMIN_NEW = "admin-team/new"
    const val ADMIN_EDIT = "admin-team/{adminId}/edit"
    const val AUDIT_LOG = "audit-log"
    const val GYM_SETTINGS = "gym-settings"

    fun invite(token: String): String = "invite/${Uri.encode(token)}"
    fun memberDetail(memberId: String): String = "member/$memberId"
    fun memberEdit(memberId: String): String = "member/$memberId/edit"
    fun adminEdit(adminId: String): String = "admin-team/$adminId/edit"

    val staffTopLevel = setOf(DASHBOARD, ATTENDANCE, MEMBERS, LEADERBOARD, PROFILE)
    val memberTopLevel = setOf(MEMBER_HOME, LEADERBOARD, PROFILE)
}
