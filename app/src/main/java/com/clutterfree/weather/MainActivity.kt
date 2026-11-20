package com.clutterfree.weather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.clutterfree.weather.data.NwsWeatherRepository
import com.clutterfree.weather.ui.home.HomeScreen
import com.clutterfree.weather.ui.theme.ClutterFreeWeatherTheme

class MainActivity : ComponentActivity() {
    private val repository by lazy { NwsWeatherRepository(filesDir) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClutterFreeWeatherTheme {
                HomeScreen(repository)
            }
        }
    }
}
