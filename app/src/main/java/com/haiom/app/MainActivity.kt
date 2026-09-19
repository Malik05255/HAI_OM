package com.haiom.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.haiom.app.ui.HaiOmApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                AndroidColor.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.dark(
                AndroidColor.TRANSPARENT
            )
        )
        setContent {
            val colors = darkColorScheme(
                primary = Color(0xFF68E1FF),
                onPrimary = Color(0xFF071014),
                primaryContainer = Color(0xFF102832),
                onPrimaryContainer = Color(0xFFC9F5FF),
                secondary = Color(0xFFA78BFA),
                secondaryContainer = Color(0xFF241C3C),
                background = Color(0xFF090C11),
                surface = Color(0xFF10151D),
                surfaceVariant = Color(0xFF151B25),
                onSurface = Color(0xFFF3F7FB),
                onSurfaceVariant = Color(0xFF9AA6B5),
                outline = Color(0xFF25303E),
                error = Color(0xFFFF6B7A)
            )
            MaterialTheme(colorScheme = colors) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    HaiOmApp()
                }
            }
        }
    }

}
