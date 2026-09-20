package com.haiom.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.haiom.app.ui.HAgentApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            )
        )
        setContent {
            val colors = lightColorScheme(
                primary = Color(0xFF4F6BFF),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFEEF2FF),
                onPrimaryContainer = Color(0xFF1E2A62),
                secondary = Color(0xFF8C7CFF),
                secondaryContainer = Color(0xFFF2F1FF),
                background = Color(0xFFF8FBFF),
                surface = Color.White,
                surfaceVariant = Color(0xFFF4F7FB),
                onSurface = Color(0xFF172033),
                onSurfaceVariant = Color(0xFF8A94A6),
                outline = Color(0xFFE4EAF2),
                error = Color(0xFFDB5D6A)
            )
            MaterialTheme(colorScheme = colors) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    HAgentApp()
                }
            }
        }
    }

}
