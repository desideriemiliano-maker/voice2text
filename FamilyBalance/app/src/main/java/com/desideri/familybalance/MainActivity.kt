package com.desideri.familybalance

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.desideri.familybalance.sicurezza.BloccoBiometrico
import com.desideri.familybalance.ui.ConBarreDiSistema
import com.desideri.familybalance.ui.SpeseApp
import com.desideri.familybalance.ui.tema.TemaFamilyBalance

// FragmentActivity, non ComponentActivity: BiometricPrompt (vedi BloccoBiometrico) richiede una
// FragmentActivity per mostrare il suo dialog di sistema. Come in WorkoutAnalyzer.
class MainActivity : FragmentActivity() {
    private val vm: SpeseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) riceviCondivisione(intent)
        enableEdgeToEdge()
        setContent {
            TemaFamilyBalance {
                ConBarreDiSistema {
                    BloccoBiometrico(activity = this) {
                        SpeseApp(vm)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        riceviCondivisione(intent)
    }

    /** File condiviso da un'altra app (Condividi › FamilyBalance): lo prende in carico il ViewModel. */
    private fun riceviCondivisione(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        uri?.let { vm.riceviFileCondiviso(it) }
    }
}
