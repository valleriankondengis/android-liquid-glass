// MainActivity: the single activity. Enables edge-to-edge drawing
// and hosts the app shell.

package com.vallxz.glass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.vallxz.glass.ui.GlassApp
import com.vallxz.glass.ui.theme.GlassTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ComponentActivity.enableEdgeToEdge() is deprecated in
        // activity 1.14.0-alpha03; WindowCompat is the replacement.
        // System-bar icon colors are set in GlassTheme (see Theme.kt).
        WindowCompat.enableEdgeToEdge(window)
        setContent {
            GlassTheme {
                GlassApp()
            }
        }
    }
}
