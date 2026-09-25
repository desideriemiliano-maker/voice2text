package com.desideri.familybalance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.desideri.familybalance.ui.SpeseApp
import com.desideri.familybalance.ui.tema.TemaFamilyBalance

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TemaFamilyBalance {
                SpeseApp()
            }
        }
    }
}
