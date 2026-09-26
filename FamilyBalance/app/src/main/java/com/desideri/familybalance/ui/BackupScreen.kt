package com.desideri.familybalance.ui

import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.ui.Alignment
import com.desideri.familybalance.backup.InfoBackup
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
import com.desideri.familybalance.SpeseViewModel
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
    var nuovoBackup by remember { mutableStateOf(false) }
    var sceltaRipristino by remember { mutableStateOf(false) }

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
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
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
                if (stato.inCorso) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                stato.errore?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { nuovoBackup = true }, enabled = !stato.inCorso) { Text("Esegui backup") }
                    OutlinedButton(onClick = { sceltaRipristino = true }, enabled = !stato.inCorso && stato.backup.isNotEmpty()) { Text("Ripristina") }
                }
                Text(
                    "Backup su Drive (${stato.backup.size}, se ne conservano ${impostazioni.backupDaMantenere}: si cambia in Impostazioni)",
                    style = MaterialTheme.typography.titleSmall
                )
                when {
                    stato.backup.isNotEmpty() -> stato.backup.forEach { b ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(descrizioneBackup(b), style = MaterialTheme.typography.bodyLarge)
                                b.testo?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                            }
                        }
                    }
                    stato.infoCaricata -> Text("Nessun backup presente", style = MaterialTheme.typography.bodyMedium)
                    else -> Text("—", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Text(
                "I backup vengono salvati nella cartella riservata all'app su Google Drive dell'account scelto " +
                    "(non visibile tra i file di Drive); dopo ogni backup i più vecchi oltre il numero impostato vengono eliminati.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (nuovoBackup) {
        var testo by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { nuovoBackup = false },
            title = { Text("Esegui backup") },
            text = {
                OutlinedTextField(
                    value = testo,
                    onValueChange = { testo = it },
                    label = { Text("Testo (facoltativo)") },
                    placeholder = { Text("es. prima dell'import di settembre") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    nuovoBackup = false
                    vm.eseguiBackup(testo)
                }) { Text("Esegui") }
            },
            dismissButton = { TextButton(onClick = { nuovoBackup = false }) { Text("Annulla") } }
        )
    }

    if (sceltaRipristino) {
        // Preselezionato il più recente; con un solo backup la scelta è solo una conferma.
        var scelto by remember { mutableStateOf(stato.backup.firstOrNull()?.id) }
        AlertDialog(
            onDismissRequest = { sceltaRipristino = false },
            title = { Text("Ripristina backup") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Tutti i dati attuali verranno sostituiti con quelli del backup scelto e l'app verrà riavviata.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    stato.backup.forEach { b ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { scelto = b.id }.padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = scelto == b.id, onClick = { scelto = b.id })
                            Column {
                                Text(descrizioneBackup(b), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    b.testo ?: "(nessun testo)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (b.testo != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    sceltaRipristino = false
                    scelto?.let { vm.ripristinaBackup(it) }
                }, enabled = scelto != null) { Text("Ripristina", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { sceltaRipristino = false }) { Text("Annulla") } }
        )
    }
}

private fun descrizioneBackup(b: InfoBackup): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date(b.modificatoMillis)) + " · ${b.dimensioneByte / 1024} KB"
