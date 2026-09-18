package com.haiom.app

import android.os.Bundle
import androidx.activity.ComponentActivity
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
        setContent {
            val colors = lightColorScheme(
                primary = Color(0xFF2F7AE9),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFF0E8F7),
                onPrimaryContainer = Color(0xFF4A3F53),
                secondary = Color(0xFF7B6B86),
                secondaryContainer = Color(0xFFF1ECF5),
                background = Color(0xFFFCFBFE),
                surface = Color(0xFFFFFBFF),
                surfaceVariant = Color(0xFFF3EEF5),
                onSurface = Color(0xFF17151A),
                onSurfaceVariant = Color(0xFF706A73),
                outline = Color(0xFFE2DBE7),
                error = Color(0xFFB3261E)
            )
            MaterialTheme(colorScheme = colors) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    HaiOmApp()
                }
            }
        }
    }
}
