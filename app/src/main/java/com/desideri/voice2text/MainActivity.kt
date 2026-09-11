package com.desideri.voice2text

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desideri.voice2text.ui.TranscriptionViewModel
import com.desideri.voice2text.ui.Voice2TextScreen
import com.desideri.voice2text.ui.theme.Voice2TextTheme

/**
 * singleTask + gestione di onNewIntent: se l'app e' gia' in background e l'utente condivide
 * un altro audio da WhatsApp, Android riusa questa istanza invece di crearne una nuova.
 */
class MainActivity : ComponentActivity() {
    private var intentState = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        intentState.value = intent

        setContent {
            Voice2TextTheme {
                val viewModel: TranscriptionViewModel = viewModel()
                Voice2TextScreen(viewModel = viewModel, sharedIntent = intentState.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentState.value = intent
    }
}
