package io.rovly.pitchee

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import io.rovly.pitchee.ui.PitcheeApp
import io.rovly.pitchee.ui.theme.PitcheeTheme

class MainActivity : AppCompatActivity() {
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
