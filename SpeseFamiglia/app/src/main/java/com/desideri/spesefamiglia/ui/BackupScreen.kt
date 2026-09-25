package com.desideri.spesefamiglia.ui

import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desideri.spesefamiglia.SpeseViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup del database su Google Drive dell'account scelto (menu ⋮ › Backup Google Drive): il file
 * sta nella cartella nascosta dell'app su Drive, uno per account.
 */
@Composable
fun BackupScreen(vm: SpeseViewModel, onIndietro: () -> Unit) {
    val impostazioni by vm.impostazioni.collectAsStateWithLifecycle()
    val stato by vm.statoBackup.collectAsStateWithLifecycle()
    val richiestaAutorizzazione by vm.richiestaAutorizzazione.collectAsStateWithLifecycle()
    var confermaRipristino by remember { mutableStateOf(false) }

    // Selettore di sistema degli account Google: restituisce solo l'email scelta.
    val sceltaAccount = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { risultato ->
        val email = risultato.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (risultato.resultCode == Activity.RESULT_OK && email != null) vm.impostaAccountBackup(email)
    }
    val autorizzazione = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { risultato ->
        vm.esitoAutorizzazione(risultato.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(richiestaAutorizzazione) {
        richiestaAutorizzazione?.let { autorizzazione.launch(it) }
    }
    LaunchedEffect(impostazioni.emailBackup) {
        if (impostazioni.emailBackup != null && !stato.infoCaricata) vm.aggiornaInfoBackup()
    }

    Scaffold(topBar = { BarraIndietro("Backup Google Drive", onIndietro) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Account Google", style = MaterialTheme.typography.titleSmall)
                    Text(impostazioni.emailBackup ?: "Nessun account scelto", style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = {
                        @Suppress("DEPRECATION")
                        val intent = AccountManager.newChooseAccountIntent(null, null, arrayOf("com.google"), null, null, null, null)
                        sceltaAccount.launch(intent)
                    }) { Text(if (impostazioni.emailBackup == null) "Scegli account" else "Cambia account") }
                }
            }

            if (impostazioni.emailBackup != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Ultimo backup su Drive", style = MaterialTheme.typography.titleSmall)
                        val info = stato.info
                        Text(
                            when {
                                info != null -> SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date(info.modificatoMillis)) +
                                    " · ${info.dimensioneByte / 1024} KB"
                                stato.infoCaricata -> "Nessun backup presente"
                                else -> "—"
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                if (stato.inCorso) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                stato.errore?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.eseguiBackup() }, enabled = !stato.inCorso) { Text("Esegui backup") }
                    OutlinedButton(onClick = { confermaRipristino = true }, enabled = !stato.inCorso && stato.info != null) { Text("Ripristina") }
                }
            }

            Text(
                "Il backup viene salvato nella cartella riservata all'app su Google Drive dell'account scelto " +
                    "(non visibile tra i file di Drive) e sostituisce il backup precedente.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (confermaRipristino) {
        DialogConferma(
            titolo = "Ripristina backup",
            testo = "Tutti i dati attuali verranno sostituiti con quelli del backup su Drive e l'app verrà riavviata.",
            conferma = "Ripristina",
            onConferma = { vm.ripristinaBackup() },
            onAnnulla = { confermaRipristino = false }
        )
    }
}
