package com.desideri.familybalance.sicurezza

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.desideri.familybalance.data.Preferenze

private const val AUTENTICATORI = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * Tempo in background dopo cui, al ritorno, si richiede di nuovo lo sblocco: abbastanza da non
 * riproporlo dopo la scelta del file Excel, dell'account Google o il consenso a Drive, che portano
 * momentaneamente l'app in background.
 */
private const val MINUTI_GRAZIA = 3L

/**
 * True se il dispositivo ha un metodo di sblocco configurato (biometria o PIN/sequenza/password):
 * altrimenti il blocco non può essere mostrato e viene saltato (mai un lucchetto senza chiave).
 */
fun bloccoDisponibile(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(AUTENTICATORI) == BiometricManager.BIOMETRIC_SUCCESS

/**
 * Blocco dell'intera app dietro impronta/volto/PIN di sistema all'apertura, disattivabile in
 * Impostazioni. Stessa logica di BloccoAppGate in WorkoutAnalyzer: si ripropone al ritorno in
 * primo piano dopo più di [MINUTI_GRAZIA] minuti in background; [autenticazioneInCorso] evita che
 * l'ON_STOP causato dal prompt di sistema stesso (es. schermata del PIN) conti come uscita
 * dall'app. L'impostazione è riletta a ogni ritorno in primo piano.
 */
@Composable
fun BloccoBiometrico(activity: FragmentActivity, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferenze = remember { Preferenze(context) }
    fun bloccoAttivo() = preferenze.carica().bloccoBiometrico && bloccoDisponibile(context)

    var attivo by remember { mutableStateOf(bloccoAttivo()) }
    var sbloccato by remember { mutableStateOf(false) }
    var autenticazioneInCorso by remember { mutableStateOf(false) }
    var ultimoBackgroundMs by remember { mutableStateOf(0L) }

    val prompt = remember {
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    autenticazioneInCorso = false
                    sbloccato = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    autenticazioneInCorso = false
                }
            }
        )
    }

    fun avviaAutenticazione() {
        autenticazioneInCorso = true
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Sblocca FamilyBalance")
                .setAllowedAuthenticators(AUTENTICATORI)
                .build()
        )
    }

    // Nessun avvio esplicito: aggiungendo l'osservatore si riceve subito ON_START, che mostra il prompt.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val osservatore = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_STOP -> if (!autenticazioneInCorso) ultimoBackgroundMs = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> {
                    attivo = bloccoAttivo()
                    if (attivo && sbloccato && !autenticazioneInCorso &&
                        System.currentTimeMillis() - ultimoBackgroundMs > MINUTI_GRAZIA * 60_000L
                    ) {
                        sbloccato = false
                    }
                    if (attivo && !sbloccato && !autenticazioneInCorso) avviaAutenticazione()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(osservatore)
        onDispose { lifecycleOwner.lifecycle.removeObserver(osservatore) }
    }

    if (!attivo || sbloccato) {
        content()
    } else {
        SchermataBloccata(onSblocca = ::avviaAutenticazione)
    }
}

@Composable
private fun SchermataBloccata(onSblocca: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(48.dp))
            Text("FamilyBalance è bloccata", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
            Text(
                "Sblocca con l'impronta, il volto o il PIN del dispositivo per continuare.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )
            Button(onClick = onSblocca) { Text("Sblocca") }
        }
    }
}
