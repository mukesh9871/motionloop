package com.motionloop.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.motionloop.app.navigation.MotionLoopNavHost
import com.motionloop.app.ui.theme.MotionLoopTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MotionLoopTheme {
                MotionLoopNavHost()
            }
        }
    }
}
