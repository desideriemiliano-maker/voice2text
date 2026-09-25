package com.desideri.familybalance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.data.Voce
import com.desideri.familybalance.logica.centInTesto
import com.desideri.familybalance.logica.formattaCent
import com.desideri.familybalance.logica.formattaMeseBreve
import com.desideri.familybalance.logica.testoInCent
import com.desideri.familybalance.logica.testoInMese
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.desideri.familybalance.DatiApp
import com.desideri.familybalance.data.Operazione
import com.desideri.familybalance.data.Valute
import com.desideri.familybalance.logica.formattaData
import java.time.YearMonth

/** Anagrafica delle voci di spesa (tipo / sottotipo opzionale), con filtro testuale. */
@Composable
fun AnagraficaSpeseScreen(vm: SpeseViewModel, onIndietro: () -> Unit) {
    val dati by vm.dati.collectAsStateWithLifecycle()
    var filtro by rememberSaveable { mutableStateOf("") }
    var inModifica by remember { mutableStateOf<Voce?>(null) }
    var operazioniDi by remember { mutableStateOf<Voce?>(null) }

    val utilizzi = remember(dati.operazioni) { dati.operazioni.mapNotNull { it.voceId }.groupingBy { it }.eachCount() }
    val filtrate = remember(dati.voci, filtro) {
        val f = filtro.trim()
        if (f.isEmpty()) dati.voci else dati.voci.filter { it.tipo.contains(f, true) || (it.sottotipo?.contains(f, true) == true) }
    }

    Scaffold(
        topBar = { BarraIndietro("Anagrafica spese", onIndietro) },
        floatingActionButton = {
            FloatingActionButton(onClick = { inModifica = Voce(tipo = "") }) { Icon(Icons.Filled.Add, contentDescription = "Nuova voce") }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = filtro,
                onValueChange = { filtro = it },
                label = { Text("Cerca tipo o sottotipo") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            )
            Text(
                "${filtrate.size} voci",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 88.dp)) {
                items(filtrate, key = { it.id }) { voce ->
                    RigaVoce(voce, utilizzi[voce.id] ?: 0, onClick = { inModifica = voce }, onOperazioni = { operazioniDi = voce })
                    HorizontalDivider()
                }
            }
        }
    }

    operazioniDi?.let { voce ->
        OperazioniVoceDialog(vm, dati, voce, onChiudi = { operazioniDi = null })
    }

    inModifica?.let { voce ->
        VoceDialog(
            voce = voce,
            tipiEsistenti = remember(dati.voci) { dati.voci.map { it.tipo }.distinct().sortedBy { it.lowercase() } },
            utilizzi = utilizzi[voce.id] ?: 0,
            onSalva = { vm.salvaVoce(it) { inModifica = null } },
            onElimina = { vm.eliminaVoce(voce) { inModifica = null } },
            onChiudi = { inModifica = null }
        )
    }
}

@Composable
private fun RigaVoce(voce: Voce, utilizzi: Int, onClick: () -> Unit, onOperazioni: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(voce.tipo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                voce.sottotipo?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            if (utilizzi > 0) {
                TextButton(onClick = onOperazioni) { Text("$utilizzi op.") }
            } else {
                Text("0 op.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (voce.entrata || voce.ricorrente) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (voce.entrata) AssistChip(onClick = onClick, label = { Text("Entrata") })
                if (voce.ricorrente) {
                    val periodo = if (voce.mesiRicorrenza == 1) "ogni mese" else "ogni ${voce.mesiRicorrenza} mesi"
                    val inizio = voce.meseInizio?.let { testoInMese(it) }?.let { " da ${formattaMeseBreve(it)}" } ?: " (senza previsione)"
                    AssistChip(onClick = onClick, label = { Text("Ricorrente $periodo$inizio") })
                }
                voce.importoPrevistoCent?.let { AssistChip(onClick = onClick, label = { Text("≈ ${formattaCent(it)}") }) }
            }
        }
    }
}

@Composable
private fun VoceDialog(
    voce: Voce,
    tipiEsistenti: List<String>,
    utilizzi: Int,
    onSalva: (Voce) -> Unit,
    onElimina: () -> Unit,
    onChiudi: () -> Unit
) {
    var tipo by remember { mutableStateOf(voce.tipo) }
    var sottotipo by remember { mutableStateOf(voce.sottotipo ?: "") }
    var entrata by remember { mutableStateOf(voce.entrata) }
    var ricorrente by remember { mutableStateOf(voce.ricorrente) }
    var mesi by remember { mutableStateOf(voce.mesiRicorrenza.toString()) }
    var meseInizio by remember {
        mutableStateOf(voce.meseInizio?.let { testoInMese(it) }?.let { formattaMeseBreve(it) } ?: if (voce.id == 0L) formattaMeseBreve(YearMonth.now()) else "")
    }
    var importoPrevisto by remember { mutableStateOf(voce.importoPrevistoCent?.let { centInTesto(it) } ?: "") }
    var errore by remember { mutableStateOf<String?>(null) }
    var confermaElimina by remember { mutableStateOf(false) }

    fun salva() {
        val numeroMesi = mesi.trim().toIntOrNull()
        if (ricorrente && (numeroMesi == null || numeroMesi !in 1..120)) return run { errore = "Numero di mesi non valido" }
        val inizio = meseInizio.trim().takeIf { it.isNotEmpty() }?.let { testoInMese(it) ?: return run { errore = "Mese di partenza non valido (MM/AAAA)" } }
        val previsto = importoPrevisto.trim().takeIf { it.isNotEmpty() }?.let { testoInCent(it) ?: return run { errore = "Importo previsto non valido" } }
        onSalva(
            voce.copy(
                tipo = tipo,
                sottotipo = sottotipo,
                entrata = entrata,
                ricorrente = ricorrente,
                mesiRicorrenza = if (ricorrente) numeroMesi ?: 1 else voce.mesiRicorrenza,
                meseInizio = if (ricorrente) inizio?.toString() else voce.meseInizio,
                importoPrevistoCent = if (ricorrente) previsto?.let { kotlin.math.abs(it) } else voce.importoPrevistoCent
            )
        )
    }

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(if (voce.id == 0L) "Nuova voce" else "Modifica voce") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoAutocompletamento("Tipo", tipo, tipiEsistenti, { tipo = it })
                OutlinedTextField(value = sottotipo, onValueChange = { sottotipo = it }, label = { Text("Sottotipo (opzionale)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = entrata, onCheckedChange = { entrata = it })
                    Text("Entrata (stipendio, interessi)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = ricorrente, onCheckedChange = { ricorrente = it })
                    Text("Spesa ricorrente")
                }
                if (ricorrente) {
                    OutlinedTextField(
                        value = mesi,
                        onValueChange = { mesi = it },
                        label = { Text("Ricorre ogni quanti mesi") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = meseInizio,
                        onValueChange = { meseInizio = it },
                        label = { Text("Mese di una scadenza (MM/AAAA)") },
                        supportingText = { Text("Vuoto = nessuna previsione") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = importoPrevisto,
                        onValueChange = { importoPrevisto = it },
                        label = { Text("Importo previsto EUR") },
                        supportingText = { Text("Vuoto = media delle ultime 6 pagate") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                errore?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = ::salva) { Text("Salva") } },
        dismissButton = {
            Row {
                if (voce.id != 0L) {
                    TextButton(onClick = { confermaElimina = true }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onChiudi) { Text("Annulla") }
            }
        }
    )

    if (confermaElimina) {
        DialogConferma(
            titolo = "Elimina voce",
            testo = if (utilizzi > 0) "La voce è usata da $utilizzi operazioni: non può essere eliminata finché non le modifichi." else "Eliminare \"${voce.descrizione}\"?",
            conferma = "Elimina",
            onConferma = onElimina,
            onAnnulla = { confermaElimina = false }
        )
    }
}

/** Elenco delle operazioni di una voce (dall'anagrafica): toccandone una si apre la modifica. */
@Composable
private fun OperazioniVoceDialog(vm: SpeseViewModel, dati: DatiApp, voce: Voce, onChiudi: () -> Unit) {
    val operazioni = remember(dati.operazioni, voce.id) { dati.operazioni.filter { it.voceId == voce.id } }
    val totaliPerValuta = remember(operazioni, dati.contiValuta) {
        operazioni.groupBy { dati.contiValutaPerId[it.contoValutaId]?.valuta ?: Valute.EUR }
            .mapValues { (_, ops) -> ops.sumOf { it.importoCent } }
            .toSortedMap()
    }
    var inModifica by remember { mutableStateOf<Operazione?>(null) }
    var sceltaDestinazione by remember { mutableStateOf(false) }
    var chiediEliminazione by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onChiudi, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(voce.descrizione, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onChiudi) { Icon(Icons.Filled.Close, contentDescription = "Chiudi") }
                }
                Text(
                    "${operazioni.size} operazioni · totale " + totaliPerValuta.entries.joinToString(" + ") { (valuta, cent) -> formattaCent(cent, valuta) },
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Tocca un'operazione per modificarla.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (operazioni.isEmpty()) item { Text("Nessuna operazione.", modifier = Modifier.padding(vertical = 12.dp)) }
                    items(operazioni, key = { it.id }) { op ->
                        val valuta = dati.contiValutaPerId[op.contoValutaId]?.valuta ?: Valute.EUR
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { inModifica = op }.padding(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(formattaData(op.data), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    dati.etichetta(op.contoValutaId) + (op.note?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TestoImporto(op.importoCent / 100.0, valuta, grassetto = true)
                        }
                        HorizontalDivider()
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { sceltaDestinazione = true }, enabled = operazioni.isNotEmpty()) { Text("Sposta tutte su…") }
                    TextButton(onClick = onChiudi) { Text("Chiudi") }
                }
            }
        }
    }

    if (sceltaDestinazione) {
        SpostaOperazioniDialog(
            sorgente = voce,
            numeroOperazioni = operazioni.size,
            voci = dati.voci,
            onConferma = { destinazione ->
                sceltaDestinazione = false
                vm.spostaOperazioniVoce(voce, destinazione) { chiediEliminazione = true }
            },
            onAnnulla = { sceltaDestinazione = false }
        )
    }

    if (chiediEliminazione) {
        AlertDialog(
            onDismissRequest = { chiediEliminazione = false },
            title = { Text("Eliminare la voce di origine?") },
            text = { Text("\"${voce.descrizione}\" non ha più operazioni. Vuoi eliminarla dall'anagrafica?") },
            confirmButton = {
                TextButton(onClick = {
                    chiediEliminazione = false
                    vm.eliminaVoce(voce) { onChiudi() }
                }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { chiediEliminazione = false }) { Text("Mantieni") } }
        )
    }

    inModifica?.let { op ->
        OperazioneDialog(vm = vm, dati = dati, contoValutaId = op.contoValutaId, esistente = op, onChiudi = { inModifica = null })
    }
}

/** Scelta della voce su cui spostare tutte le operazioni di [sorgente], con conferma. */
@Composable
private fun SpostaOperazioniDialog(
    sorgente: Voce,
    numeroOperazioni: Int,
    voci: List<Voce>,
    onConferma: (Voce) -> Unit,
    onAnnulla: () -> Unit
) {
    val candidate = remember(voci, sorgente.id) { voci.filter { it.id != sorgente.id }.sortedBy { it.descrizione.lowercase() } }
    var testo by remember { mutableStateOf("") }
    val destinazione = candidate.firstOrNull { it.descrizione.equals(testo.trim(), ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onAnnulla,
        title = { Text("Sposta operazioni") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sposta le $numeroOperazioni operazioni di \"${sorgente.descrizione}\" su:")
                CampoAutocompletamento(
                    etichetta = "Voce di destinazione",
                    valore = testo,
                    opzioni = candidate.map { it.descrizione },
                    onValore = { testo = it },
                    errore = if (testo.isNotBlank() && destinazione == null) "Scegli una voce dall'elenco" else null
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { destinazione?.let(onConferma) }, enabled = destinazione != null) { Text("Sposta") }
        },
        dismissButton = { TextButton(onClick = onAnnulla) { Text("Annulla") } }
    )
}
