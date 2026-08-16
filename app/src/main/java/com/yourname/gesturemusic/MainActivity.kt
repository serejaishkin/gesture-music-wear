package com.yourname.gesturemusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.yourname.gesturemusic.ui.screens.ControlScreen
import com.yourname.gesturemusic.ui.theme.GestureMusicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            GestureMusicTheme {
                ControlScreen()
            }
        }
    }
}
