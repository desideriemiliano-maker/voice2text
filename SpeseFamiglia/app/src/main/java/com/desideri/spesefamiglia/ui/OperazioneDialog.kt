package com.desideri.spesefamiglia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.desideri.spesefamiglia.DatiApp
import com.desideri.spesefamiglia.SpeseViewModel
import com.desideri.spesefamiglia.data.Operazione
import com.desideri.spesefamiglia.logica.centInTesto
import com.desideri.spesefamiglia.logica.testoInCent
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

/**
 * Inserimento/modifica di un'operazione sul conto/valuta [contoValutaId]. L'importo si scrive in
 * positivo e il segno si sceglie con Uscita/Entrata. Con "Spostamento" non si indica la voce ma il
 * conto di destinazione (e, se in valuta diversa, l'importo accreditato/addebitato là).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperazioneDialog(vm: SpeseViewModel, dati: DatiApp, contoValutaId: Long, esistente: Operazione?, onChiudi: () -> Unit) {
    val scope = rememberCoroutineScope()
    val voceIniziale = esistente?.voceId?.let { dati.vociPerId[it] }
    val collegata = esistente?.collegataId?.let { id -> dati.operazioni.firstOrNull { it.id == id } }
    val valutaPropria = dati.contiValutaPerId[contoValutaId]?.valuta

    var data by remember { mutableStateOf(esistente?.data ?: LocalDate.now().toEpochDay()) }
    var importo by remember { mutableStateOf(esistente?.let { centInTesto(abs(it.importoCent)) } ?: "") }
    var entrata by remember { mutableStateOf((esistente?.importoCent ?: -1L) > 0) }
    var spostamento by remember { mutableStateOf(esistente?.trasferimento ?: false) }
    var destinazione by remember { mutableStateOf(esistente?.contoValutaDestId) }
    var importoDest by remember { mutableStateOf(collegata?.let { centInTesto(abs(it.importoCent)) } ?: "") }
    var tipo by remember { mutableStateOf(voceIniziale?.tipo ?: "") }
    var sottotipo by remember { mutableStateOf(voceIniziale?.sottotipo ?: "") }
    var note by remember { mutableStateOf(esistente?.note ?: "") }
    var errore by remember { mutableStateOf<String?>(null) }
    var confermaElimina by remember { mutableStateOf(false) }

    val tipi = remember(dati.voci) { dati.voci.map { it.tipo }.distinct().sortedBy { it.lowercase() } }
    val sottotipi = remember(dati.voci, tipo) {
        dati.voci.filter { it.tipo.equals(tipo.trim(), ignoreCase = true) }.mapNotNull { it.sottotipo }.distinct().sortedBy { it.lowercase() }
    }
    val altriConti = dati.contiValutaOrdinati.filter { it.id != contoValutaId }
    val valutaDest = destinazione?.let { dati.contiValutaPerId[it]?.valuta }
    val cambioValuta = spostamento && valutaDest != null && valutaDest != valutaPropria
    // Un nuovo spostamento crea la contro-operazione; uno importato senza collegamento no.
    val gestisceControparte = esistente == null || collegata != null || esistente.trasferimento == false

    fun salva() {
        val cent = testoInCent(importo)?.let { abs(it) }
        if (cent == null || cent == 0L) return run { errore = "Importo non valido" }
        val importoConSegno = if (entrata) cent else -cent
        scope.launch {
            if (spostamento) {
                val dest = destinazione ?: return@launch run { errore = "Scegli il conto di destinazione" }
                val centDest = if (cambioValuta && gestisceControparte) {
                    testoInCent(importoDest)?.let { abs(it) }?.takeIf { it > 0 } ?: return@launch run { errore = "Importo sul conto di destinazione non valido" }
                } else {
                    cent
                }
                val op = Operazione(
                    id = esistente?.id ?: 0,
                    contoValutaId = contoValutaId,
                    data = data,
                    importoCent = importoConSegno,
                    trasferimento = true,
                    contoValutaDestId = dest,
                    collegataId = esistente?.collegataId,
                    note = note.trim().ifEmpty { null }
                )
                vm.salvaOperazione(op, if (entrata) -centDest else centDest)
            } else {
                if (tipo.isBlank()) return@launch run { errore = "Scegli il tipo di spesa" }
                val voceId = vm.voceId(tipo, sottotipo)
                    ?: return@launch run { errore = "Tipo/sottotipo non presente: definiscilo in Anagrafica spese" }
                val op = Operazione(
                    id = esistente?.id ?: 0,
                    contoValutaId = contoValutaId,
                    data = data,
                    importoCent = importoConSegno,
                    voceId = voceId,
                    collegataId = esistente?.collegataId,
                    note = note.trim().ifEmpty { null }
                )
                vm.salvaOperazione(op, null)
            }
            onChiudi()
        }
    }

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(if (esistente == null) "Nuova operazione" else "Modifica operazione") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoData("Data", data, { if (it != null) data = it }, Modifier.fillMaxWidth())
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !entrata, onClick = { entrata = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Uscita") }
                    SegmentedButton(selected = entrata, onClick = { entrata = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Entrata") }
                }
                OutlinedTextField(
                    value = importo,
                    onValueChange = { importo = it },
                    label = { Text("Importo ${valutaPropria ?: ""}") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = spostamento, onCheckedChange = { spostamento = it })
                    Text("Spostamento tra conti")
                }
                if (spostamento) {
                    CampoScelta(
                        etichetta = if (entrata) "Conto di provenienza" else "Conto di destinazione",
                        selezionato = altriConti.firstOrNull { it.id == destinazione },
                        opzioni = altriConti,
                        testo = { dati.etichetta(it.id) },
                        onScelta = { destinazione = it.id }
                    )
                    if (cambioValuta && gestisceControparte) {
                        OutlinedTextField(
                            value = importoDest,
                            onValueChange = { importoDest = it },
                            label = { Text("Importo in $valutaDest") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (!gestisceControparte) {
                        Text(
                            "Spostamento importato: la riga corrispondente sull'altro conto va modificata a parte.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    CampoAutocompletamento("Tipo", tipo, tipi, {
                        tipo = it
                        sottotipo = ""
                        if (dati.voci.any { v -> v.tipo.equals(it.trim(), true) && v.entrata }) entrata = true
                    })
                    CampoAutocompletamento("Sottotipo (opzionale)", sottotipo, sottotipi, { sottotipo = it })
                }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
                errore?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = ::salva) { Text("Salva") } },
        dismissButton = {
            Row {
                if (esistente != null) {
                    TextButton(onClick = { confermaElimina = true }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onChiudi) { Text("Annulla") }
            }
        }
    )

    if (confermaElimina && esistente != null) {
        DialogConferma(
            titolo = "Elimina operazione",
            testo = if (collegata != null) "Verrà eliminata anche l'operazione collegata sull'altro conto." else "Eliminare l'operazione?",
            conferma = "Elimina",
            onConferma = {
                vm.eliminaOperazione(esistente)
                onChiudi()
            },
            onAnnulla = { confermaElimina = false }
        )
    }
}
