package com.desideri.spesefamiglia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.desideri.spesefamiglia.ui.SpeseApp
import com.desideri.spesefamiglia.ui.tema.TemaSpese

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TemaSpese {
                SpeseApp()
            }
        }
    }
}
