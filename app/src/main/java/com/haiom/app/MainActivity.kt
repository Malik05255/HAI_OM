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
                primary = Color(0xFF3F51B5),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE8EAFF),
                onPrimaryContainer = Color(0xFF20245A),
                secondary = Color(0xFF3E7C78),
                secondaryContainer = Color(0xFFDFF2EF),
                background = Color(0xFFF9FAFC),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFF0F2F6),
                onSurface = Color(0xFF17191F),
                onSurfaceVariant = Color(0xFF666A74),
                outline = Color(0xFFD9DCE3),
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
