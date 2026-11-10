package com.muddassir.squadgym

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muddassir.squadgym.core.di.AppContainer
import com.muddassir.squadgym.core.ui.theme.SquadGymTheme
import com.muddassir.squadgym.data.local.SettingsStore
import com.muddassir.squadgym.navigation.SquadGymRoot

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container = (application as SquadGymApp).container
        handleIntent(intent)

        setContent {
            val themeMode by container.settingsStore.themeMode
                .collectAsStateWithLifecycle(initialValue = SettingsStore.ThemeMode.SYSTEM)

            val darkTheme = when (themeMode) {
                SettingsStore.ThemeMode.LIGHT -> false
                SettingsStore.ThemeMode.DARK -> true
                SettingsStore.ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            SquadGymTheme(darkTheme = darkTheme) {
                SquadGymRoot(container = container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** `squadgym://invite/<token>` opens the invitation acceptance screen. */
    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "squadgym") return
        val token = when (data.host) {
            "invite" -> data.lastPathSegment ?: data.getQueryParameter("token")
            else -> data.getQueryParameter("token")
        }
        if (!token.isNullOrBlank()) {
            container.pendingInviteToken.value = token
        }
    }
}
