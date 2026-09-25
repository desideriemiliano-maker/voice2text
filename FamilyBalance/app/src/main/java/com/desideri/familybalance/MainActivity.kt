package com.desideri.familybalance

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.desideri.familybalance.sicurezza.BloccoBiometrico
import com.desideri.familybalance.ui.SpeseApp
import com.desideri.familybalance.ui.tema.TemaFamilyBalance

// FragmentActivity, non ComponentActivity: BiometricPrompt (vedi BloccoBiometrico) richiede una
// FragmentActivity per mostrare il suo dialog di sistema. Come in WorkoutAnalyzer.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TemaFamilyBalance {
                BloccoBiometrico(activity = this) {
                    SpeseApp()
                }
            }
        }
    }
}
