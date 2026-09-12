package io.rovly.pitchee

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.rovly.pitchee.ui.PitcheeApp
import io.rovly.pitchee.ui.theme.PitcheeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PitcheeTheme {
                PitcheeApp()
            }
        }
    }
}
