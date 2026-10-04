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
import androidx.compose.material3.Checkbox
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
    var minutiBlocco by remember { mutableStateOf(impostazioni.minutiBlocco.toString()) }
    var giorniDuplicati by remember { mutableStateOf(impostazioni.giorniDuplicati.toString()) }
    var giorniConferma by remember { mutableStateOf(impostazioni.giorniConfermaDuplicati.toString()) }
    var giorniIntorno by remember { mutableStateOf(impostazioni.giorniIntornoDuplicati.toString()) }
    var mesiMedia by remember { mutableStateOf(impostazioni.mesiMediaStipendio.toString()) }
    var giornoStipendio by remember { mutableStateOf(impostazioni.giornoStipendio.toString()) }
    var giornoLavorativo by remember { mutableStateOf(impostazioni.stipendioGiornoLavorativo) }
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
                label = { Text("Cambio attuale: 1 CHF = … EUR") },
                supportingText = {
                    Text(
                        "Per sommare i conti in CHF a quelli in EUR nel saldo totale e nel bilancio del mese corrente e dei " +
                            "futuri (1 = nessuna conversione). Per i mesi passati si usano i cambi mensili (menu ⋮ › Cambi CHF/EUR)."
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = giornoStipendio,
                onValueChange = { giornoStipendio = it },
                label = { Text("Giorno dello stipendio") },
                supportingText = { Text("Giorno del mese in cui arriva lo stipendio (1-31, predefinito 20): nel bilancio del mese corrente si indicano le ricorrenti da pagare prima.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = giornoLavorativo, onCheckedChange = { giornoLavorativo = it })
                Text("Se cade nel weekend, il giorno lavorativo successivo", style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedTextField(
                value = mesiMedia,
                onValueChange = { mesiMedia = it },
                label = { Text("Mesi per la media dello stipendio") },
                supportingText = { Text("Nel mese corrente, finché lo stipendio non è entrato, si usa la media degli ultimi mesi (1-24, predefinito 5).") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = giorniDuplicati,
                onValueChange = { giorniDuplicati = it },
                label = { Text("Giorni per rilevare i duplicati") },
                supportingText = { Text("Operazioni dello stesso conto con lo stesso importo entro questi giorni sono segnalate in \"Rileva duplicati\" (0-30, predefinito 3).") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = giorniIntorno,
                onValueChange = { giorniIntorno = it },
                label = { Text("Intorno dei duplicati (giorni)") },
                supportingText = { Text("Per ogni possibile duplicato si mostrano anche le operazioni dello stesso importo entro questi giorni (0-90, predefinito 10).") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = giorniConferma,
                onValueChange = { giorniConferma = it },
                label = { Text("Giorni esclusi dalla conferma dei duplicati") },
                supportingText = { Text("\"Conferma controllo\" in Rileva duplicati vale fino a oggi meno questi giorni (0-90, predefinito 7): le operazioni più recenti restano da controllare.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                        if (bloccoPossibile) "Impronta, volto o PIN del dispositivo; richiesto di nuovo dopo i minuti in background indicati sotto."
                        else "Nessun blocco schermo configurato sul telefono: lo sblocco non è disponibile.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = bloccoBiometrico && bloccoPossibile, onCheckedChange = { bloccoBiometrico = it }, enabled = bloccoPossibile)
            }
            OutlinedTextField(
                value = minutiBlocco,
                onValueChange = { minutiBlocco = it },
                label = { Text("Minuti prima di richiedere di nuovo lo sblocco") },
                supportingText = { Text("Tempo in background dopo cui, tornando nell'app, si chiede di nuovo impronta o PIN (0-120, predefinito 3). Con 0 lo chiede anche dopo la scelta di un file o dell'account Google.") },
                singleLine = true,
                enabled = bloccoBiometrico && bloccoPossibile,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            errore?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val targetCent = testoInCent(target)
                    val tasso = cambio.trim().replace(',', '.').toDoubleOrNull()
                    val numeroBackup = backupDaMantenere.trim().toIntOrNull()
                    val minuti = minutiBlocco.trim().toIntOrNull()
                    val giorni = giorniDuplicati.trim().toIntOrNull()
                    val conferma = giorniConferma.trim().toIntOrNull()
                    val intorno = giorniIntorno.trim().toIntOrNull()
                    val media = mesiMedia.trim().toIntOrNull()
                    val giornoStip = giornoStipendio.trim().toIntOrNull()
                    errore = when {
                        targetCent == null -> "Target non valido"
                        tasso == null || tasso <= 0 -> "Cambio non valido"
                        numeroBackup == null || numeroBackup !in 1..20 -> "Numero di backup non valido (1-20)"
                        minuti == null || minuti !in 0..120 -> "Minuti non validi (0-120)"
                        giorni == null || giorni !in 0..30 -> "Giorni per i duplicati non validi (0-30)"
                        conferma == null || conferma !in 0..90 -> "Giorni esclusi dalla conferma non validi (0-90)"
                        intorno == null || intorno !in 0..90 -> "Intorno dei duplicati non valido (0-90)"
                        media == null || media !in 1..24 -> "Mesi per la media dello stipendio non validi (1-24)"
                        giornoStip == null || giornoStip !in 1..31 -> "Giorno dello stipendio non valido (1-31)"
                        else -> {
                            vm.salvaImpostazioni(
                                impostazioni.copy(
                                    targetRisparmioCent = targetCent,
                                    cambioChfEur = tasso,
                                    bloccoBiometrico = bloccoBiometrico,
                                    backupDaMantenere = numeroBackup,
                                    minutiBlocco = minuti,
                                    giorniDuplicati = giorni,
                                    giorniConfermaDuplicati = conferma,
                                    giorniIntornoDuplicati = intorno,
                                    mesiMediaStipendio = media,
                                    giornoStipendio = giornoStip,
                                    stipendioGiornoLavorativo = giornoLavorativo
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
