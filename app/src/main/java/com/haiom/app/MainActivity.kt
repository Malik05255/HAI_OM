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
                primary = Color(0xFF3156D3),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE6EBFF),
                onPrimaryContainer = Color(0xFF17224F),
                secondary = Color(0xFFFF6B4A),
                secondaryContainer = Color(0xFFFFE5DD),
                background = Color(0xFFF3EFE7),
                surface = Color(0xFFFFFCF6),
                surfaceVariant = Color(0xFFEDE7DD),
                onSurface = Color(0xFF17181B),
                onSurfaceVariant = Color(0xFF716D66),
                outline = Color(0xFFCBC1B2),
                error = Color(0xFFD94B43)
            )
            MaterialTheme(colorScheme = colors) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    HaiOmApp()
                }
            }
        }
    }

}
