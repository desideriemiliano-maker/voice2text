package com.desideri.familybalance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.Alignment
import java.time.LocalDate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.data.Operazione
import com.desideri.familybalance.logica.Duplicati
import com.desideri.familybalance.logica.formattaCent
import com.desideri.familybalance.logica.formattaData

/**
 * Possibili duplicati di un conto/valuta: gruppi di operazioni con lo stesso importo entro i
 * giorni indicati in Impostazioni. Toccando un'operazione la si modifica o la si elimina.
 */
@Composable
fun DuplicatiDialog(vm: SpeseViewModel, contoValutaId: Long, onChiudi: () -> Unit) {
    val dati by vm.dati.collectAsStateWithLifecycle()
    val impostazioni by vm.impostazioni.collectAsStateWithLifecycle()
    val giorni = impostazioni.giorniDuplicati
    val valuta = dati.contiValutaPerId[contoValutaId]?.valuta ?: "EUR"
    // Controllo già confermato fino a questo giorno: i gruppi tutti precedenti non si mostrano più.
    var confermatoFino by remember { mutableStateOf(vm.duplicatiConfermatiFino(contoValutaId)) }
    val tutti = remember(dati.operazioni, contoValutaId, giorni) {
        Duplicati.gruppi(dati.operazioni.filter { it.contoValutaId == contoValutaId }, giorni)
    }
    val gruppi = tutti.filter { g -> confermatoFino.let { f -> f == null || g.any { it.data > f } } }
    val nuovaConferma = LocalDate.now().minusDays(impostazioni.giorniConfermaDuplicati.toLong()).toEpochDay()
    var inModifica by remember { mutableStateOf<Operazione?>(null) }
    var chiediConferma by remember { mutableStateOf(false) }
    var chiediReset by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text("Duplicati · ${dati.etichetta(contoValutaId)}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Stato della conferma del controllo, con reset.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        confermatoFino?.let { "Controllo confermato fino al ${formattaData(it)}" } ?: "Controllo mai confermato",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (confermatoFino != null) TextButton(onClick = { chiediReset = true }) { Text("Reset") }
                }
                Text(
                    "Operazioni con lo stesso importo a non più di $giorni giorni l'una dall'altra (Impostazioni)" +
                        (if (confermatoFino != null) "; nascosti i gruppi già controllati (${tutti.size - gruppi.size})" else "") +
                        ". Tocca un'operazione per aprirne i dettagli e modificarla o eliminarla.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = { chiediConferma = true },
                    enabled = confermatoFino == null || nuovaConferma > confermatoFino!!,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Conferma controllo fino al ${formattaData(nuovaConferma)}") }
                if (gruppi.isEmpty()) Text("Nessun possibile duplicato da controllare.", style = MaterialTheme.typography.bodyMedium)
                gruppi.forEach { gruppo ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                "${formattaCent(gruppo.first().importoCent, valuta)} · ${gruppo.size} operazioni",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            gruppo.forEachIndexed { i, op ->
                                if (i > 0) HorizontalDivider()
                                Column(modifier = Modifier.fillMaxWidth().clickable { inModifica = op }.padding(vertical = 4.dp)) {
                                    val descrizione = op.voceId?.let { dati.vociPerId[it]?.descrizione }
                                        ?: if (op.trasferimento) "Spostamento" + (op.contoValutaDestId?.let { " ↔ ${dati.etichetta(it)}" } ?: "") else "Senza tipo"
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row {
                                                Text(formattaData(op.data), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                                Text(descrizione, style = MaterialTheme.typography.bodySmall)
                                            }
                                            op.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                                        }
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Apri")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onChiudi) { Text("Chiudi") } }
    )
    inModifica?.let { op -> OperazioneDialog(vm, dati, op.contoValutaId, op, onChiudi = { inModifica = null }) }
    if (chiediConferma) {
        AlertDialog(
            onDismissRequest = { chiediConferma = false },
            title = { Text("Conferma controllo") },
            text = {
                Text(
                    "I possibili duplicati con tutte le operazioni fino al ${formattaData(nuovaConferma)} (oggi meno " +
                        "${impostazioni.giorniConfermaDuplicati} giorni) sono stati controllati e non verranno più mostrati. " +
                        "Le operazioni non vengono modificate; la conferma si può annullare con Reset."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.confermaDuplicatiFino(contoValutaId, nuovaConferma)
                    confermatoFino = nuovaConferma
                    chiediConferma = false
                }) { Text("Conferma") }
            },
            dismissButton = { TextButton(onClick = { chiediConferma = false }) { Text("Annulla") } }
        )
    }
    if (chiediReset) {
        AlertDialog(
            onDismissRequest = { chiediReset = false },
            title = { Text("Reset del controllo") },
            text = { Text("Annullare la conferma del controllo? Verranno mostrati di nuovo tutti i possibili duplicati del conto.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.confermaDuplicatiFino(contoValutaId, null)
                    confermatoFino = null
                    chiediReset = false
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { chiediReset = false }) { Text("Annulla") } }
        )
    }
}
