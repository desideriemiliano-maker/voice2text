package com.desideri.familybalance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.logica.centInTesto
import com.desideri.familybalance.sicurezza.bloccoDisponibile
import com.desideri.familybalance.logica.testoInCent

@Composable
fun ImpostazioniScreen(vm: SpeseViewModel, onIndietro: () -> Unit) {
    val impostazioni by vm.impostazioni.collectAsStateWithLifecycle()
    var target by remember { mutableStateOf(centInTesto(impostazioni.targetRisparmioCent)) }
    var cambio by remember { mutableStateOf(impostazioni.cambioChfEur.toString().replace('.', ',')) }
    var bloccoBiometrico by remember { mutableStateOf(impostazioni.bloccoBiometrico) }
    var backupDaMantenere by remember { mutableStateOf(impostazioni.backupDaMantenere.toString()) }
    val bloccoPossibile = bloccoDisponibile(LocalContext.current)
    var errore by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { BarraIndietro("Impostazioni", onIndietro) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = target,
                onValueChange = { target = it },
                label = { Text("Target di risparmio mensile (EUR)") },
                supportingText = { Text("Risparmio atteso ogni mese (entrate − spese correnti): usato nel Bilancio per il delta e le previsioni.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = cambio,
                onValueChange = { cambio = it },
                label = { Text("Cambio: 1 CHF = … EUR") },
                supportingText = { Text("Per sommare i conti in CHF a quelli in EUR nel saldo totale e nel bilancio (1 = nessuna conversione, come nell'Excel).") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = backupDaMantenere,
                onValueChange = { backupDaMantenere = it },
                label = { Text("Backup su Drive da mantenere") },
                supportingText = { Text("Dopo ogni backup quelli più vecchi oltre questo numero vengono eliminati (1-20).") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sblocco biometrico all'apertura", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (bloccoPossibile) "Impronta, volto o PIN del dispositivo; richiesto di nuovo dopo 3 minuti in background."
                        else "Nessun blocco schermo configurato sul telefono: lo sblocco non è disponibile.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = bloccoBiometrico && bloccoPossibile, onCheckedChange = { bloccoBiometrico = it }, enabled = bloccoPossibile)
            }
            errore?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val targetCent = testoInCent(target)
                    val tasso = cambio.trim().replace(',', '.').toDoubleOrNull()
                    val numeroBackup = backupDaMantenere.trim().toIntOrNull()
                    errore = when {
                        targetCent == null -> "Target non valido"
                        tasso == null || tasso <= 0 -> "Cambio non valido"
                        numeroBackup == null || numeroBackup !in 1..20 -> "Numero di backup non valido (1-20)"
                        else -> {
                            vm.salvaImpostazioni(
                                impostazioni.copy(
                                    targetRisparmioCent = targetCent,
                                    cambioChfEur = tasso,
                                    bloccoBiometrico = bloccoBiometrico,
                                    backupDaMantenere = numeroBackup
                                )
                            )
                            onIndietro()
                            null
                        }
                    }
                },
                modifier = Modifier.align(Alignment.End)
            ) { Text("Salva") }
        }
    }
}
