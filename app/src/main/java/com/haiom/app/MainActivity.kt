package com.haiom.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.LayoutDirection
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import com.haiom.app.ui.HaiOmApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colors = lightColorScheme(
                primary = Color(0xFF355F59),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFDCECE8),
                surface = Color(0xFFF7F8FA),
                surfaceVariant = Color(0xFFECEFF1),
                background = Color(0xFFF7F8FA),
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
