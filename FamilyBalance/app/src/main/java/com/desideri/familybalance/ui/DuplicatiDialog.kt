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
    val gruppi = remember(dati.operazioni, contoValutaId, giorni) {
        Duplicati.gruppi(dati.operazioni.filter { it.contoValutaId == contoValutaId }, giorni)
    }
    var inModifica by remember { mutableStateOf<Operazione?>(null) }

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text("Duplicati · ${dati.etichetta(contoValutaId)}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Operazioni con lo stesso importo a non più di $giorni giorni l'una dall'altra (Impostazioni). " +
                        "Tocca un'operazione per modificarla o eliminarla.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (gruppi.isEmpty()) Text("Nessun possibile duplicato.", style = MaterialTheme.typography.bodyMedium)
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
                                    Row {
                                        Text(formattaData(op.data), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        Text(descrizione, style = MaterialTheme.typography.bodySmall)
                                    }
                                    op.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
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
}
