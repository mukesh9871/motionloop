package com.motionloop.app

import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motionloop.app.data.settings.AppSettings
import com.motionloop.app.domain.model.ThemeMode
import com.motionloop.app.navigation.MotionLoopNavHost
import androidx.compose.ui.graphics.toArgb
import com.motionloop.app.ui.theme.DarkPalette
import com.motionloop.app.ui.theme.LightPalette
import com.motionloop.app.ui.theme.MotionLoopTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The window background shows until Compose draws: match the chosen theme, not only the system one.
        val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val startDark = when (settings.state.value.theme) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        window.setBackgroundDrawable(ColorDrawable((if (startDark) DarkPalette else LightPalette).background.toArgb()))
        setContent {
            val state by settings.state.collectAsStateWithLifecycle()
            val dark = when (state.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Status and navigation bar icons follow the app theme, not only the system one.
            // SideEffect: a synchronous call with nothing to clean up.
            SideEffect {
                val transparent = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            MotionLoopTheme(dark = dark) {
                MotionLoopNavHost()
            }
        }
    }
}
