package com.haiom.app

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.haiom.app.ui.HaiOmApp

class MainActivity : ComponentActivity() {
    private val oauthCallback = mutableStateOf<String?>(null)

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
        oauthCallback.value = readOAuthCallback(intent)

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
                    HaiOmApp(
                        oauthCallback = oauthCallback.value,
                        onOAuthCallbackConsumed = {
                            oauthCallback.value = null
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readOAuthCallback(intent)?.let {
            oauthCallback.value = it
        }
    }

    private fun readOAuthCallback(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        return if (
            uri.scheme == "om" &&
            uri.host == "github-auth"
        ) {
            uri.toString()
        } else {
            null
        }
    }
}
