package com.desideri.familybalance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.desideri.familybalance.DatiApp
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.data.Operazione
import com.desideri.familybalance.logica.Riscontro
import com.desideri.familybalance.logica.Spostamenti
import com.desideri.familybalance.logica.centInTesto
import com.desideri.familybalance.logica.formattaCent
import com.desideri.familybalance.logica.formattaData
import com.desideri.familybalance.logica.testoInCent
import kotlin.math.abs

/** Scelta della riga da collegare: una riga esistente, una nuova da creare o nessuna per ora. */
private sealed interface Collegamento {
    data class Riga(val id: Long) : Collegamento
    data object Nuova : Collegamento
    data object Nessuno : Collegamento
}

private fun importo(op: Operazione, dati: DatiApp): String =
    formattaCent(op.importoCent, dati.contiValutaPerId[op.contoValutaId]?.valuta ?: "EUR")

/**
 * Risoluzione di uno spostamento senza riga corrispondente (riscontro spostamenti), tutto in una
 * schermata: a sinistra (sopra, su schermi stretti) l'operazione, modificabile o eliminabile; a
 * destra (sotto) le righe del conto di destinazione a cui collegarla, o una nuova da creare, e i
 * possibili doppioni sullo stesso conto da eliminare. Le candidate seguono le modifiche fatte
 * all'operazione (data, importo, segno, conto di destinazione).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RisolviSpostamentoDialog(vm: SpeseViewModel, dati: DatiApp, opId: Long, onChiudi: () -> Unit) {
    val originale = dati.operazioni.firstOrNull { it.id == opId }
    if (originale == null) {
        LaunchedEffect(Unit) { onChiudi() }
        return
    }
    val valutaPropria = dati.contiValutaPerId[originale.contoValutaId]?.valuta ?: "EUR"
    val altriConti = dati.contiValutaOrdinati.filter { it.id != originale.contoValutaId }
    val valutaDi = { id: Long -> dati.contiValutaPerId[id]?.valuta }

    var data by remember(opId) { mutableStateOf(originale.data) }
    var importoTesto by remember(opId) { mutableStateOf(centInTesto(abs(originale.importoCent))) }
    var entrata by remember(opId) { mutableStateOf(originale.importoCent > 0) }
    var destinazione by remember(opId) { mutableStateOf(originale.contoValutaDestId) }
    var note by remember(opId) { mutableStateOf(originale.note ?: "") }
    var importoDestTesto by remember(opId) { mutableStateOf(centInTesto(abs(originale.importoCent))) }
    val daEliminare = remember(opId) { mutableStateListOf<Long>() }
    var errore by remember(opId) { mutableStateOf<String?>(null) }
    var confermaElimina by remember(opId) { mutableStateOf(false) }

    // Operazione come risulta dalle modifiche in corso: le candidate si ricalcolano su questa.
    val cent = testoInCent(importoTesto)?.let { abs(it) } ?: 0L
    val opForm = originale.copy(data = data, importoCent = if (entrata) cent else -cent, contoValutaDestId = destinazione)
    val associabili = remember(opForm, dati.operazioni) {
        Spostamenti.candidate(opForm, null, destinazione, dati.operazioni).sortedWith(compareBy({ abs(it.data - opForm.data) }, { it.id }))
    }
    val doppioni = remember(opForm, dati.operazioni) {
        dati.operazioni.filter {
            it.id != opId && it.contoValutaId == originale.contoValutaId && it.importoCent == opForm.importoCent &&
                abs(it.data - opForm.data) <= Riscontro.GIORNI_SIMILI
        }.sortedWith(compareBy({ abs(it.data - opForm.data) }, { it.id }))
    }
    // Preselezionata la riga corrispondente certa (stessa valuta e importo, pochi giorni), se c'è.
    var collegamento by remember(opId) {
        mutableStateOf<Collegamento>(
            Spostamenti.trovaControparte(originale, originale.contoValutaDestId, dati.operazioni, valutaDi)
                ?.let { Collegamento.Riga(it.id) } ?: Collegamento.Nessuno
        )
    }
    // Se la riga scelta non è più tra le candidate (es. cambiata la data), si torna a "nessuna".
    val collegamentoValido = when (val c = collegamento) {
        is Collegamento.Riga -> if (associabili.any { it.id == c.id }) c else Collegamento.Nessuno
        else -> c
    }
    val valutaDest = destinazione?.let { valutaDi(it) }
    val cambioValuta = valutaDest != null && valutaDest != valutaPropria
    val eliminati = doppioni.filter { it.id in daEliminare }

    fun salva() {
        if (cent == 0L) return run { errore = "Importo non valido" }
        if (destinazione == null) return run { errore = "Scegli il conto di destinazione" }
        val importoDest = if (collegamentoValido == Collegamento.Nuova && cambioValuta) {
            testoInCent(importoDestTesto)?.let { abs(it) }?.takeIf { it > 0 }
                ?: return run { errore = "Importo in $valutaDest non valido" }
        } else {
            cent
        }
        vm.risolviSpostamento(
            op = originale.copy(data = data, importoCent = if (entrata) cent else -cent, contoValutaDestId = destinazione, note = note.trim().ifEmpty { null }),
            associaId = (collegamentoValido as? Collegamento.Riga)?.id,
            creaNuova = collegamentoValido == Collegamento.Nuova,
            importoDestCent = if (entrata) -importoDest else importoDest,
            elimina = eliminati.map { it.id }
        )
        onChiudi()
    }

    Dialog(onDismissRequest = onChiudi, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Risolvi spostamento", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val largo = maxWidth >= 600.dp
                    val operazione: @Composable () -> Unit = {
                        SezioneOperazione(
                            dati, originale, valutaPropria, altriConti,
                            data, { data = it }, importoTesto, { importoTesto = it }, entrata, { entrata = it },
                            destinazione, { destinazione = it }, note, { note = it }
                        )
                    }
                    val candidate: @Composable () -> Unit = {
                        SezioneCandidate(
                            dati, opForm, destinazione, associabili, collegamentoValido, { collegamento = it },
                            cambioValuta, valutaDest, importoDestTesto, { importoDestTesto = it },
                            doppioni, daEliminare, eliminati
                        )
                    }
                    if (largo) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(end = 12.dp)) { operazione() }
                            VerticalDivider()
                            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 12.dp)) { candidate() }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            operazione()
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            candidate()
                        }
                    }
                }
                errore?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp)) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    TextButton(onClick = { confermaElimina = true }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onChiudi) { Text("Annulla") }
                    TextButton(onClick = ::salva) { Text("Salva") }
                }
            }
        }
    }

    if (confermaElimina) {
        DialogConferma(
            titolo = "Elimina spostamento",
            testo = "Eliminare questo spostamento del ${formattaData(originale.data)} di ${importo(originale, dati)} su " +
                "${dati.etichetta(originale.contoValutaId)}? Le altre righe non vengono toccate.",
            conferma = "Elimina",
            onConferma = {
                vm.eliminaSoloRiga(originale)
                onChiudi()
            },
            onAnnulla = { confermaElimina = false }
        )
    }
}

/** L'operazione, modificabile: data, uscita/entrata, importo, conto di destinazione, note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SezioneOperazione(
    dati: DatiApp,
    originale: Operazione,
    valutaPropria: String,
    altriConti: List<com.desideri.familybalance.data.ContoValuta>,
    data: Long,
    onData: (Long) -> Unit,
    importoTesto: String,
    onImporto: (String) -> Unit,
    entrata: Boolean,
    onEntrata: (Boolean) -> Unit,
    destinazione: Long?,
    onDestinazione: (Long) -> Unit,
    note: String,
    onNote: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Spostamento su ${dati.etichetta(originale.contoValutaId)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        CampoData("Data", data, { if (it != null) onData(it) }, Modifier.fillMaxWidth())
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(selected = !entrata, onClick = { onEntrata(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Uscita") }
            SegmentedButton(selected = entrata, onClick = { onEntrata(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Entrata") }
        }
        OutlinedTextField(
            value = importoTesto,
            onValueChange = onImporto,
            label = { Text("Importo $valutaPropria") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        CampoScelta(
            etichetta = if (entrata) "Conto di provenienza" else "Conto di destinazione",
            selezionato = altriConti.firstOrNull { it.id == destinazione },
            opzioni = altriConti,
            testo = { dati.etichetta(it.id) },
            onScelta = { onDestinazione(it.id) }
        )
        OutlinedTextField(value = note, onValueChange = onNote, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
    }
}

/** Righe del conto di destinazione a cui collegare lo spostamento e possibili doppioni da eliminare. */
@Composable
private fun SezioneCandidate(
    dati: DatiApp,
    opForm: Operazione,
    destinazione: Long?,
    associabili: List<Operazione>,
    collegamento: Collegamento,
    onCollegamento: (Collegamento) -> Unit,
    cambioValuta: Boolean,
    valutaDest: String?,
    importoDestTesto: String,
    onImportoDest: (String) -> Unit,
    doppioni: List<Operazione>,
    daEliminare: MutableList<Long>,
    eliminati: List<Operazione>
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (destinazione == null) "Collega a una riga (scegli il conto)" else "Collega a una riga su ${dati.etichetta(destinazione)}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        if (destinazione != null) {
            if (associabili.isEmpty()) {
                Text(
                    "Nessuna operazione di segno opposto e non collegata entro ${Spostamenti.GIORNI_CANDIDATE} giorni.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            associabili.forEach { c ->
                val scelta = collegamento == Collegamento.Riga(c.id)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onCollegamento(Collegamento.Riga(c.id)) }) {
                    RadioButton(selected = scelta, onClick = { onCollegamento(Collegamento.Riga(c.id)) })
                    DettaglioRiga(c, dati, giorni = abs(c.data - opForm.data), modifier = Modifier.weight(1f))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onCollegamento(Collegamento.Nuova) }) {
                RadioButton(selected = collegamento == Collegamento.Nuova, onClick = { onCollegamento(Collegamento.Nuova) })
                Text("Crea una nuova riga su ${dati.etichetta(destinazione)}", style = MaterialTheme.typography.bodyMedium)
            }
            if (collegamento == Collegamento.Nuova && cambioValuta) {
                OutlinedTextField(
                    value = importoDestTesto,
                    onValueChange = onImportoDest,
                    label = { Text("Importo in $valutaDest") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(start = 48.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onCollegamento(Collegamento.Nessuno) }) {
                RadioButton(selected = collegamento == Collegamento.Nessuno, onClick = { onCollegamento(Collegamento.Nessuno) })
                Text("Nessuna per ora", style = MaterialTheme.typography.bodyMedium)
            }
            if (collegamento is Collegamento.Riga) {
                Text(
                    "La riga scelta diventa uno spostamento collegato a questo (se aveva un tipo di spesa, lo perde); importo e data restano i suoi.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (doppioni.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                "Possibili doppioni su ${dati.etichetta(opForm.contoValutaId)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Stesso importo entro ${Riscontro.GIORNI_SIMILI} giorni. Spunta solo quelle da eliminare: possono anche essere movimenti veri.",
                style = MaterialTheme.typography.bodySmall
            )
            doppioni.forEach { d ->
                val spuntata = d.id in daEliminare
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { if (spuntata) daEliminare.remove(d.id) else daEliminare.add(d.id) }
                ) {
                    Checkbox(checked = spuntata, onCheckedChange = { if (it) daEliminare.add(d.id) else daEliminare.remove(d.id) })
                    DettaglioRiga(d, dati, giorni = abs(d.data - opForm.data), modifier = Modifier.weight(1f))
                }
            }
            if (eliminati.any { it.collegataId != null } && collegamento == Collegamento.Nessuno) {
                Text(
                    "Eliminando una riga collegata, il suo collegamento con l'altro conto passa a questo spostamento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (eliminati.isNotEmpty()) {
                Text(
                    "Da eliminare: ${eliminati.size}. Le righe sugli altri conti non vengono eliminate.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/** Una riga candidata: data, importo, distanza, descrizione e, se collegata, la riga sull'altro conto. */
@Composable
private fun DettaglioRiga(op: Operazione, dati: DatiApp, giorni: Long, modifier: Modifier = Modifier) {
    val tipo = when {
        op.trasferimento && op.contoValutaDestId != null ->
            "Spostamento " + (if (op.importoCent < 0) "verso " else "da ") + dati.etichetta(op.contoValutaDestId)
        op.trasferimento -> "Spostamento"
        else -> op.voceId?.let { dati.vociPerId[it]?.descrizione } ?: "Senza tipo"
    }
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Row {
            Text(formattaData(op.data), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(importo(op, dati), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Text(
            (if (giorni == 0L) "stesso giorno" else "$giorni giorni di distanza") + " · " + tipo,
            style = MaterialTheme.typography.bodySmall
        )
        op.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
        op.collegataId?.let { id -> dati.operazioni.firstOrNull { it.id == id } }?.let { c ->
            Text(
                "↔ ${dati.etichetta(c.contoValutaId)} · ${formattaData(c.data)} · ${importo(c, dati)}" + (c.note?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2
            )
        }
    }
}
