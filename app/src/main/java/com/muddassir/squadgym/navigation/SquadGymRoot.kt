package com.muddassir.squadgym.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.muddassir.squadgym.core.di.AppContainer
import com.muddassir.squadgym.data.session.SessionState
import com.muddassir.squadgym.features.auth.ForgotPasswordScreen
import com.muddassir.squadgym.features.auth.InvitationScreen
import com.muddassir.squadgym.features.auth.LoginScreen
import com.muddassir.squadgym.features.splash.SplashScreen

/**
 * Root of the app: the global session state decides which navigation graph is
 * mounted, so signing in/out never leaves stale screens on the back stack.
 */
@Composable
fun SquadGymRoot(container: AppContainer) {
    val session by container.sessionManager.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        val restored = container.authRepository.restoreCachedSession()
        if (!restored) {
            container.sessionManager.signedOut()
        } else {
            // Confirm the cached session; a 401 clears it and lands on sign-in.
            container.authRepository.refreshProfile()
        }
    }

    when (val current = session) {
        SessionState.Loading -> SplashScreen()
        SessionState.SignedOut -> AuthNavHost(container)
        is SessionState.SignedIn -> AppNavHost(container = container, session = current)
    }
}

@Composable
private fun AuthNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val pendingInvite by container.pendingInviteToken.collectAsStateWithLifecycle()

    LaunchedEffect(pendingInvite) {
        val token = pendingInvite
        if (!token.isNullOrBlank()) {
            container.pendingInviteToken.value = null
            navController.navigate(Routes.invite(token))
        }
    }

    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(
                container = container,
                onForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) },
                onAcceptInvitation = { navController.navigate(Routes.INVITE_ENTRY) },
            )
        }
        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(
                container = container,
                onBackToLogin = { navController.popBackStack() },
            )
        }
        composable(Routes.INVITE_ENTRY) {
            InvitationScreen(
                container = container,
                token = null,
                onBackToLogin = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.INVITE,
            arguments = listOf(navArgument("token") { type = NavType.StringType }),
        ) { entry ->
            InvitationScreen(
                container = container,
                token = entry.arguments?.getString("token"),
                onBackToLogin = { navController.popBackStack() },
            )
        }
    }
}
