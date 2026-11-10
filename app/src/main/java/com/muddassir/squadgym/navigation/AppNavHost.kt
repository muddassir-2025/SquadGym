package com.muddassir.squadgym.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.muddassir.squadgym.core.di.AppContainer
import com.muddassir.squadgym.core.ui.components.ErrorState
import com.muddassir.squadgym.core.ui.components.ScreenBackground
import com.muddassir.squadgym.data.session.SessionState
import com.muddassir.squadgym.features.attendance.AttendanceScreen
import com.muddassir.squadgym.features.dashboard.AdminDashboardScreen
import com.muddassir.squadgym.features.leaderboard.LeaderboardScreen
import com.muddassir.squadgym.features.member.MemberHomeScreen
import com.muddassir.squadgym.features.members.MemberDetailScreen
import com.muddassir.squadgym.features.members.MemberFormScreen
import com.muddassir.squadgym.features.members.MembersScreen
import com.muddassir.squadgym.features.profile.ProfileScreen
import com.muddassir.squadgym.features.settings.PlansScreen
import com.muddassir.squadgym.features.superadmin.AdminFormScreen
import com.muddassir.squadgym.features.superadmin.AdminTeamScreen
import com.muddassir.squadgym.features.superadmin.AuditLogScreen
import com.muddassir.squadgym.features.superadmin.GymSettingsScreen

@Composable
fun AppNavHost(
    container: AppContainer,
    session: SessionState.SignedIn,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isMember = session.user.isMember
    val topLevelRoutes = if (isMember) Routes.memberTopLevel else Routes.staffTopLevel

    fun navigateTopLevel(target: String) {
        if (currentRoute == target) return
        navController.navigate(target) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute != null && topLevelRoutes.contains(currentRoute)) {
                SquadBottomBar(
                    currentRoute = currentRoute,
                    isMember = isMember,
                    onNavigate = ::navigateTopLevel,
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (isMember) Routes.MEMBER_HOME else Routes.DASHBOARD,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.DASHBOARD) {
                if (session.user.isStaff) {
                    AdminDashboardScreen(
                        container = container,
                        user = session.user,
                        gym = session.gym,
                        onTakeAttendance = { navigateTopLevel(Routes.ATTENDANCE) },
                        onAddMember = { navController.navigate(Routes.MEMBER_NEW) },
                        onViewMembers = { navigateTopLevel(Routes.MEMBERS) },
                        onLeaderboard = { navigateTopLevel(Routes.LEADERBOARD) },
                        onOpenPlans = { navController.navigate(Routes.PLANS) },
                        onOpenGymSettings = { navController.navigate(Routes.GYM_SETTINGS) },
                    )
                } else {
                    NotAvailable()
                }
            }

            composable(Routes.ATTENDANCE) {
                if (session.user.isStaff) {
                    AttendanceScreen(
                        container = container,
                        gym = session.gym,
                        shifts = session.shifts,
                        onOpenMember = { id -> navController.navigate(Routes.memberDetail(id)) },
                    )
                } else {
                    NotAvailable()
                }
            }

            composable(Routes.MEMBERS) {
                if (session.user.isStaff) {
                    MembersScreen(
                        container = container,
                        gymTimezone = session.gym.timezone,
                        onOpenMember = { id -> navController.navigate(Routes.memberDetail(id)) },
                        onAddMember = { navController.navigate(Routes.MEMBER_NEW) },
                    )
                } else {
                    NotAvailable()
                }
            }

            composable(
                route = Routes.MEMBER_DETAIL,
                arguments = listOf(navArgument("memberId") { type = NavType.StringType }),
            ) { entry ->
                val memberId = entry.arguments?.getString("memberId").orEmpty()
                if (session.user.isStaff) {
                    MemberDetailScreen(
                        container = container,
                        memberId = memberId,
                        timezone = session.gym.timezone,
                        onBack = { navController.popBackStack() },
                        onEdit = { id -> navController.navigate(Routes.memberEdit(id)) },
                    )
                } else {
                    NotAvailable()
                }
            }

            composable(Routes.MEMBER_NEW) {
                if (session.user.isStaff) {
                    MemberFormScreen(
                        container = container,
                        memberId = null,
                        shifts = session.shifts,
                        timezone = session.gym.timezone,
                        onBack = { navController.popBackStack() },
                        onSaved = { id ->
                            navController.navigate(Routes.memberDetail(id)) {
                                popUpTo(Routes.MEMBER_NEW) { inclusive = true }
                            }
                        },
                    )
                } else {
                    NotAvailable()
                }
            }

            composable(
                route = Routes.MEMBER_EDIT,
                arguments = listOf(navArgument("memberId") { type = NavType.StringType }),
            ) { entry ->
                val memberId = entry.arguments?.getString("memberId").orEmpty()
                if (session.user.isStaff) {
                    MemberFormScreen(
                        container = container,
                        memberId = memberId,
                        shifts = session.shifts,
                        timezone = session.gym.timezone,
                        onBack = { navController.popBackStack() },
                        onSaved = { id ->
                            navController.navigate(Routes.memberDetail(id)) {
                                popUpTo(Routes.MEMBER_DETAIL) { inclusive = true }
                            }
                        },
                    )
                } else {
                    NotAvailable()
                }
            }

            composable(Routes.LEADERBOARD) {
                LeaderboardScreen(container = container)
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    container = container,
                    user = session.user,
                    gym = session.gym,
                    onAdminTeam = { navController.navigate(Routes.ADMIN_TEAM) },
                    onAuditLog = { navController.navigate(Routes.AUDIT_LOG) },
                    onGymSettings = { navController.navigate(Routes.GYM_SETTINGS) },
                )
            }

            composable(Routes.MEMBER_HOME) {
                if (session.user.isMember) {
                    MemberHomeScreen(
                        container = container,
                        user = session.user,
                        onLeaderboard = { navigateTopLevel(Routes.LEADERBOARD) },
                    )
                } else {
                    NotAvailable()
                }
            }

            composable(Routes.ADMIN_TEAM) {
                if (session.user.isSuperAdmin) {
                    AdminTeamScreen(
                        container = container,
                        onAddAdmin = { navController.navigate(Routes.ADMIN_NEW) },
                    )
                } else {
                    NotAvailable()
                }
            }

            composable(Routes.ADMIN_NEW) {
                if (session.user.isSuperAdmin) {
                    AdminFormScreen(
                        container = container,
                        onBack = { navController.popBackStack() },
                        onCreated = { navController.popBackStack() },
                    )
                } else {
                    NotAvailable()
                }
            }

            composable(Routes.AUDIT_LOG) {
                if (session.user.isSuperAdmin) {
                    AuditLogScreen(
                        container = container,
                        timezone = session.gym.timezone,
                        onBack = { navController.popBackStack() },
                    )
                } else {
                    NotAvailable()
                }
            }

            composable(Routes.PLANS) {
                if (session.user.isStaff) {
                    PlansScreen(
                        container = container,
                        canManage = session.user.hasPermission("memberships.manage"),
                        onBack = { navController.popBackStack() },
                    )
                } else {
                    NotAvailable()
                }
            }

            composable(Routes.GYM_SETTINGS) {
                if (session.user.isSuperAdmin) {
                    GymSettingsScreen(
                        container = container,
                        onBack = { navController.popBackStack() },
                    )
                } else {
                    NotAvailable()
                }
            }
        }
    }
}

@Composable
private fun NotAvailable() {
    ScreenBackground {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            ErrorState(
                message = "This area isn't available for your account.",
                title = "Not available",
            )
        }
    }
}
