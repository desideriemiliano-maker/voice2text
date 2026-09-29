package com.desideri.familybalance.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.desideri.familybalance.DatiApp
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.data.Operazione
import com.desideri.familybalance.logica.Spostamenti
import com.desideri.familybalance.logica.centInTesto
import com.desideri.familybalance.logica.formattaCent
import com.desideri.familybalance.logica.formattaData
import com.desideri.familybalance.logica.testoInCent
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
fun OperazioneDialog(
    vm: SpeseViewModel,
    dati: DatiApp,
    contoValutaId: Long,
    esistente: Operazione?,
    onChiudi: () -> Unit,
    // Valori iniziali per una nuova operazione (es. dal calendario delle ricorrenti).
    tipoIniziale: String? = null,
    sottotipoIniziale: String? = null,
    dataIniziale: Long? = null
) {
    val scope = rememberCoroutineScope()
    val voceIniziale = esistente?.voceId?.let { dati.vociPerId[it] }
    val collegata = esistente?.collegataId?.let { id -> dati.operazioni.firstOrNull { it.id == id } }
    // Spostamento importato senza collegamento: la riga speculare sull'altro conto, se c'è.
    val speculare = remember(esistente, dati.operazioni) {
        if (esistente != null && esistente.trasferimento && collegata == null) {
            Spostamenti.trovaControparte(esistente, esistente.contoValutaDestId, dati.operazioni) { dati.contiValutaPerId[it]?.valuta }
        } else null
    }
    val valutaPropria = dati.contiValutaPerId[contoValutaId]?.valuta

    var data by remember { mutableStateOf(esistente?.data ?: dataIniziale ?: LocalDate.now().toEpochDay()) }
    var importo by remember { mutableStateOf(esistente?.let { centInTesto(abs(it.importoCent)) } ?: "") }
    var entrata by remember { mutableStateOf((esistente?.importoCent ?: -1L) > 0) }
    var spostamento by remember { mutableStateOf(esistente?.trasferimento ?: false) }
    var destinazione by remember { mutableStateOf(esistente?.contoValutaDestId) }
    var importoDest by remember { mutableStateOf((collegata ?: speculare)?.let { centInTesto(abs(it.importoCent)) } ?: "") }
    var tipo by remember { mutableStateOf(voceIniziale?.tipo ?: tipoIniziale ?: "") }
    var sottotipo by remember { mutableStateOf(voceIniziale?.sottotipo ?: sottotipoIniziale ?: "") }
    var note by remember { mutableStateOf(esistente?.note ?: "") }
    var dataRicorrente by remember { mutableStateOf(esistente?.dataRicorrente) }
    var errore by remember { mutableStateOf<String?>(null) }
    var confermaElimina by remember { mutableStateOf(false) }

    val tipi = remember(dati.voci) { dati.vociAttive.map { it.tipo }.distinct().sortedBy { it.lowercase() } }
    val sottotipi = remember(dati.voci, tipo) {
        dati.vociAttive.filter { it.tipo.equals(tipo.trim(), ignoreCase = true) }.mapNotNull { it.sottotipo }.distinct().sortedBy { it.lowercase() }
    }
    val altriConti = dati.contiValutaOrdinati.filter { it.id != contoValutaId }
    val valutaDest = destinazione?.let { dati.contiValutaPerId[it]?.valuta }
    val cambioValuta = spostamento && valutaDest != null && valutaDest != valutaPropria
    val valutaDi = { id: Long -> dati.contiValutaPerId[id]?.valuta }

    // Riga sul conto di destinazione che fa da contro-operazione: proposte quelle compatibili,
    // preselezionata la collegata o la speculare; senza scelta si sposta la vecchia o se ne crea una.
    val centForm = testoInCent(importo)?.let { abs(it) } ?: 0L
    val opForm = Operazione(
        id = esistente?.id ?: -1L,
        contoValutaId = contoValutaId,
        data = data,
        importoCent = if (entrata) centForm else -centForm,
        trasferimento = true,
        contoValutaDestId = destinazione
    )
    val candidate = remember(destinazione, data, entrata, dati.operazioni) {
        Spostamenti.candidate(opForm, collegata, destinazione, dati.operazioni)
    }
    var sceltaId by remember(destinazione) {
        mutableStateOf(Spostamenti.controparteSu(opForm, collegata, destinazione, dati.operazioni, valutaDi)?.id)
    }
    val scelta = candidate.firstOrNull { it.id == sceltaId }
    // Riga che, senza scelta, viene spostata sul nuovo conto (la collegata o la speculare importata).
    val daSpostare = (collegata ?: speculare)?.takeIf { it.contoValutaId != destinazione }
    // L'importo nell'altra valuta si chiede solo se la riga non esiste già (o è quella collegata).
    val chiediImportoDest = cambioValuta && (scelta == null || scelta.id == collegata?.id)
    LaunchedEffect(sceltaId, destinazione) {
        val riga = scelta ?: daSpostare?.takeIf { valutaDi(it.contoValutaId) == valutaDest }
        riga?.let { importoDest = centInTesto(abs(it.importoCent)) }
    }

    fun salva() {
        val cent = testoInCent(importo)?.let { abs(it) }
        if (cent == null || cent == 0L) return run { errore = "Importo non valido" }
        val importoConSegno = if (entrata) cent else -cent
        scope.launch {
            if (spostamento) {
                val dest = destinazione ?: return@launch run { errore = "Scegli il conto di destinazione" }
                val centDest = if (chiediImportoDest) {
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
                    note = note.trim().ifEmpty { null },
                    ordine = esistente?.ordine
                )
                vm.salvaOperazione(op, if (entrata) -centDest else centDest, scelta?.id)
            } else {
                if (tipo.isBlank()) return@launch run { errore = "Scegli il tipo di spesa" }
                val voceId = vm.voceId(tipo, sottotipo)
                    ?: return@launch run { errore = "Tipo/sottotipo non presente: definiscilo in Anagrafica spese" }
                // Una voce obsoleta resta solo sulle operazioni che l'avevano già.
                if (dati.vociPerId[voceId]?.obsoleta == true && voceId != esistente?.voceId) {
                    return@launch run { errore = "Voce obsoleta: non si può più usare (Anagrafica spese)" }
                }
                val op = Operazione(
                    id = esistente?.id ?: 0,
                    contoValutaId = contoValutaId,
                    data = data,
                    importoCent = importoConSegno,
                    voceId = voceId,
                    collegataId = esistente?.collegataId,
                    note = note.trim().ifEmpty { null },
                    ordine = esistente?.ordine,
                    esclusaDaMedia = esistente?.esclusaDaMedia ?: false,
                    // La data per la ricorrente conta solo per le voci ricorrenti e se diversa dalla data.
                    dataRicorrente = dataRicorrente.takeIf { dati.vociPerId[voceId]?.ricorrente == true && it != data }
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
                    if (destinazione != null && (candidate.isNotEmpty() || daSpostare != null)) {
                        val nessuna = OpzioneRiga(
                            null,
                            when {
                                daSpostare != null -> "Sposta qui la riga di ${dati.etichetta(daSpostare.contoValutaId)}"
                                else -> "Crea una nuova riga"
                            }
                        )
                        val opzioni = candidate.map { OpzioneRiga(it, descriviRiga(it, dati, collegata)) } +
                            listOfNotNull(nessuna.takeIf { collegata == null || collegata.contoValutaId != destinazione })
                        CampoScelta(
                            etichetta = "Riga su ${dati.etichetta(destinazione)}",
                            selezionato = opzioni.firstOrNull { it.op?.id == sceltaId } ?: nessuna,
                            opzioni = opzioni,
                            testo = { it.testo },
                            onScelta = { sceltaId = it.op?.id }
                        )
                    }
                    if (scelta != null && scelta.id != collegata?.id) {
                        Text(
                            "La riga scelta viene collegata così com'è: l'importo sull'altro conto è il suo." +
                                if (collegata != null) " La riga collegata finora su ${dati.etichetta(collegata.contoValutaId)} verrà eliminata." else "",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (chiediImportoDest) {
                        OutlinedTextField(
                            value = importoDest,
                            onValueChange = { importoDest = it },
                            label = { Text("Importo in $valutaDest") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    CampoAutocompletamento("Tipo", tipo, tipi, {
                        tipo = it
                        sottotipo = ""
                        if (dati.voci.any { v -> v.tipo.equals(it.trim(), true) && v.entrata }) entrata = true
                    })
                    CampoAutocompletamento("Sottotipo (opzionale)", sottotipo, sottotipi, { sottotipo = it })
                    val ricorrente = dati.vociAttive.any { it.ricorrente && it.tipo.equals(tipo.trim(), true) } ||
                        (voceIniziale?.ricorrente == true && voceIniziale.tipo.equals(tipo.trim(), true))
                    if (ricorrente) {
                        CampoData("Data per la spesa ricorrente (facoltativa)", dataRicorrente, { dataRicorrente = it }, Modifier.fillMaxWidth(), consentiVuoto = true)
                        Text(
                            "Se l'addebito è in un mese diverso da quello della spesa (es. il 1° del mese per il mese prima), la ricorrente la conta in questa data.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
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
        if (collegata == null) {
            DialogConferma(
                titolo = "Elimina operazione",
                testo = "Eliminare l'operazione?",
                conferma = "Elimina",
                onConferma = {
                    vm.eliminaOperazione(esistente)
                    onChiudi()
                },
                onAnnulla = { confermaElimina = false }
            )
        } else {
            // Spostamento collegato: si sceglie se eliminare anche la riga sull'altro conto.
            AlertDialog(
                onDismissRequest = { confermaElimina = false },
                title = { Text("Elimina spostamento") },
                text = {
                    Text(
                        "È collegato alla riga del ${formattaData(collegata.data)} su ${dati.etichetta(collegata.contoValutaId)}. " +
                            "\"Solo questa\" lascia quella riga (senza collegamento) e il saldo di quel conto non cambia; " +
                            "\"Entrambe\" la elimina."
                    )
                },
                confirmButton = {
                    Row {
                        TextButton(onClick = {
                            vm.eliminaSoloRiga(esistente)
                            onChiudi()
                        }) { Text("Solo questa", color = MaterialTheme.colorScheme.error) }
                        TextButton(onClick = {
                            vm.eliminaOperazione(esistente)
                            onChiudi()
                        }) { Text("Entrambe", color = MaterialTheme.colorScheme.error) }
                    }
                },
                dismissButton = { TextButton(onClick = { confermaElimina = false }) { Text("Annulla") } }
            )
        }
    }
}

/** Opzione della scelta della riga sull'altro conto ([op] null: nessuna riga esistente). */
private data class OpzioneRiga(val op: Operazione?, val testo: String)

private fun descriviRiga(op: Operazione, dati: DatiApp, collegata: Operazione?): String {
    val valuta = dati.contiValutaPerId[op.contoValutaId]?.valuta ?: "EUR"
    val descrizione = op.note ?: op.voceId?.let { dati.vociPerId[it]?.tipo } ?: if (op.trasferimento) "Spostamento" else ""
    return listOf(formattaData(op.data), formattaCent(op.importoCent, valuta), descrizione)
        .filter { it.isNotBlank() }
        .joinToString(" · ") + if (op.id == collegata?.id) " (collegata)" else ""
}
