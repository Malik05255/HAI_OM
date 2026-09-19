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
import com.haiom.app.ui.HaiOmApp

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
                primary = Color(0xFF1F8A70),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE5F4EF),
                onPrimaryContainer = Color(0xFF16362D),
                secondary = Color(0xFFD97757),
                secondaryContainer = Color(0xFFFFE9E2),
                background = Color(0xFFF7F8F4),
                surface = Color.White,
                surfaceVariant = Color(0xFFEEF3EC),
                onSurface = Color(0xFF17211D),
                onSurfaceVariant = Color(0xFF66726C),
                outline = Color(0xFFD9E2DD),
                error = Color(0xFFD85F50)
            )
            MaterialTheme(colorScheme = colors) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    HaiOmApp()
                }
            }
        }
    }

}
