package com.deepshield.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.deepshield.ai.navigation.DeepShieldNavGraph
import com.deepshield.ai.ui.theme.CyberBlack
import com.deepshield.ai.ui.theme.DeepShieldTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main Activity — single Activity architecture hosting the Compose navigation graph.
 * All 10 module screens are rendered within this Activity via Jetpack Navigation.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DeepShieldTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CyberBlack
                ) {
                    DeepShieldNavGraph()
                }
            }
        }
    }
}
