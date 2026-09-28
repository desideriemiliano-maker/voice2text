package com.desideri.familybalance.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Checkbox
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.desideri.familybalance.DatiApp
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.data.Operazione
import com.desideri.familybalance.logica.formattaCent
import com.desideri.familybalance.logica.formattaData
import com.desideri.familybalance.logica.testoInCent
import kotlin.math.abs

/** Filtri della lista operazioni: periodo, intervallo di importo (valore assoluto), tipi (uno o più) e sottotipo. */
private data class Filtri(
    val da: Long? = null,
    val a: Long? = null,
    val importoMin: String = "",
    val importoMax: String = "",
    /** Tipi ammessi (minuscoli; [TIPO_VUOTO] = operazioni senza tipo); vuoto = tutti. */
    val tipi: Set<String> = emptySet(),
    val sottotipo: String = ""
) {
    val attivi: Boolean get() = this != Filtri()
}

/** Chiave del filtro per le operazioni senza tipo di spesa. */
private const val TIPO_VUOTO = ""

private data class RigaOperazione(val operazione: Operazione, val saldoDopo: Long)

@Composable
fun OperazioniScreen(vm: SpeseViewModel, contoValutaId: Long, onIndietro: () -> Unit) {
    val dati by vm.dati.collectAsStateWithLifecycle()
    val cv = dati.contiValutaPerId[contoValutaId]
    var filtri by remember { mutableStateOf(Filtri()) }
    var mostraFiltri by rememberSaveable { mutableStateOf(false) }
    var inModifica by remember { mutableStateOf<Operazione?>(null) }
    // Selezione multipla: si attiva tenendo premuta un'operazione; vuota = modalità normale.
    var selezione by remember { mutableStateOf(setOf<Long>()) }
    var confermaEliminazione by remember { mutableStateOf(false) }
    var spostaInConto by remember { mutableStateOf(false) }
    BackHandler(enabled = selezione.isNotEmpty()) { selezione = emptySet() }
    var nuova by remember { mutableStateOf(false) }

    if (cv == null) {
        Scaffold(topBar = { BarraIndietro("Conto", onIndietro) }) { padding ->
            Text("Conto non più presente.", modifier = Modifier.padding(padding).padding(16.dp))
        }
        return
    }

    // Saldo progressivo calcolato su tutte le operazioni del conto, in ordine di data.
    val righe = remember(dati.operazioni, cv) {
        var saldo = cv.saldoInizialeCent
        dati.operazioni.filter { it.contoValutaId == contoValutaId }
            // A parità di data: ordine dell'estratto conto (le operazioni senza ordine in coda), poi id.
            .sortedWith(compareBy({ it.data }, { it.ordine ?: Long.MAX_VALUE }, { it.id }))
            .map { op ->
                saldo += op.importoCent
                RigaOperazione(op, saldo)
            }
            .asReversed()
    }
    val saldoAttuale = righe.firstOrNull()?.saldoDopo ?: cv.saldoInizialeCent
    val filtrate = remember(righe, filtri, dati.voci) { righe.filter { corrisponde(it.operazione, filtri, dati) } }

    Scaffold(
        topBar = {
            if (selezione.isNotEmpty()) {
                // "Tutte" = le operazioni mostrate, cioè quelle che passano i filtri attivi.
                val mostrate = filtrate.map { it.operazione.id }.toSet()
                val tutteSelezionate = mostrate.isNotEmpty() && selezione.containsAll(mostrate)
                BarraIndietro("${selezione.size} selezionate", onIndietro = { selezione = emptySet() }) {
                    IconButton(onClick = { selezione = if (tutteSelezionate) selezione - mostrate else selezione + mostrate }) {
                        Icon(
                            if (tutteSelezionate) Icons.Filled.Deselect else Icons.Filled.SelectAll,
                            contentDescription = if (tutteSelezionate) "Deseleziona le mostrate" else "Seleziona tutte le mostrate"
                        )
                    }
                    IconButton(onClick = { spostaInConto = true }) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = "Sposta in altro conto")
                    }
                    IconButton(onClick = { confermaEliminazione = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Elimina selezionate")
                    }
                }
            } else {
                BarraIndietro(dati.etichetta(contoValutaId), onIndietro) {
                    IconButton(onClick = { selezione = filtrate.map { it.operazione.id }.toSet() }, enabled = filtrate.isNotEmpty()) {
                        Icon(Icons.Filled.SelectAll, contentDescription = "Seleziona tutte le mostrate")
                    }
                    IconButton(onClick = { mostraFiltri = !mostraFiltri }) {
                        Icon(if (mostraFiltri) Icons.Filled.FilterListOff else Icons.Filled.FilterList, contentDescription = "Filtri")
                    }
                }
            }
        },
        floatingActionButton = {
            if (selezione.isEmpty()) {
                FloatingActionButton(onClick = { nuova = true }) { Icon(Icons.Filled.Add, contentDescription = "Nuova operazione") }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Saldo", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TestoImporto(saldoAttuale / 100.0, cv.valuta, grassetto = true)
                        }
                        Text("Saldo iniziale ${formattaCent(cv.saldoInizialeCent, cv.valuta)}", style = MaterialTheme.typography.bodySmall)
                        if (filtri.attivi) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                                Text("Totale filtrato (${filtrate.size} operazioni)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                TestoImporto(filtrate.sumOf { it.operazione.importoCent } / 100.0, cv.valuta, grassetto = true)
                            }
                        }
                    }
                }
            }
            if (mostraFiltri) {
                item { PannelloFiltri(filtri, dati, onFiltri = { filtri = it }) }
            }
            if (filtrate.isEmpty()) {
                item { Text("Nessuna operazione.", modifier = Modifier.padding(16.dp)) }
            }
            items(filtrate, key = { it.operazione.id }) { riga ->
                val id = riga.operazione.id
                RigaListaOperazione(
                    riga, dati, cv.valuta,
                    inSelezione = selezione.isNotEmpty(),
                    selezionata = id in selezione,
                    onClick = {
                        if (selezione.isNotEmpty()) selezione = if (id in selezione) selezione - id else selezione + id
                        else inModifica = riga.operazione
                    },
                    onLongClick = { selezione = selezione + id }
                )
                HorizontalDivider()
            }
        }
    }

    if (spostaInConto) {
        // Correzione di operazioni finite nel conto/valuta sbagliato (es. movimenti CHF in LGT EUR).
        val altri = dati.contiValutaOrdinati.filter { it.id != contoValutaId }
        var destinazione by remember { mutableStateOf(altri.firstOrNull()?.id) }
        AlertDialog(
            onDismissRequest = { spostaInConto = false },
            title = { Text("Sposta in altro conto") },
            text = {
                Column {
                    Text(
                        "Le ${selezione.size} operazioni selezionate verranno spostate nel conto/valuta scelto, " +
                            "con lo stesso importo, data e tipo.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    altri.forEach { cv ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { destinazione = cv.id }
                        ) {
                            RadioButton(selected = destinazione == cv.id, onClick = { destinazione = cv.id })
                            Text(dati.etichetta(cv.id))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        destinazione?.let { dest -> vm.spostaOperazioniConto(dati.operazioni.filter { it.id in selezione }, dest) }
                        selezione = emptySet()
                        spostaInConto = false
                    },
                    enabled = destinazione != null
                ) { Text("Sposta") }
            },
            dismissButton = { TextButton(onClick = { spostaInConto = false }) { Text("Annulla") } }
        )
    }

    if (confermaEliminazione) {
        DialogEliminaSelezionate(
            numero = selezione.size,
            onConferma = {
                vm.eliminaOperazioni(dati.operazioni.filter { it.id in selezione })
                selezione = emptySet()
            },
            onAnnulla = { confermaEliminazione = false }
        )
    }

    if (nuova || inModifica != null) {
        OperazioneDialog(
            vm = vm,
            dati = dati,
            contoValutaId = contoValutaId,
            esistente = inModifica,
            onChiudi = {
                nuova = false
                inModifica = null
            }
        )
    }
}

@Composable
private fun DialogEliminaSelezionate(numero: Int, onConferma: () -> Unit, onAnnulla: () -> Unit) {
    DialogConferma(
        titolo = "Elimina operazioni",
        testo = "Eliminare le $numero operazioni selezionate? Per gli spostamenti inseriti dall'app viene eliminata " +
            "anche l'operazione collegata sull'altro conto.",
        conferma = "Elimina",
        onConferma = onConferma,
        onAnnulla = onAnnulla
    )
}

private fun corrisponde(op: Operazione, f: Filtri, dati: DatiApp): Boolean {
    if (f.da != null && op.data < f.da) return false
    if (f.a != null && op.data > f.a) return false
    val importo = abs(op.importoCent)
    testoInCent(f.importoMin)?.let { if (importo < abs(it)) return false }
    testoInCent(f.importoMax)?.let { if (importo > abs(it)) return false }
    val voce = op.voceId?.let { dati.vociPerId[it] }
    val tipo = if (op.trasferimento) "Spostamento" else voce?.tipo ?: ""
    if (f.tipi.isNotEmpty() && tipo.lowercase() !in f.tipi) return false
    if (f.sottotipo.isNotBlank() && !(voce?.sottotipo ?: "").contains(f.sottotipo.trim(), ignoreCase = true)) return false
    return true
}

@Composable
private fun PannelloFiltri(filtri: Filtri, dati: DatiApp, onFiltri: (Filtri) -> Unit) {
    val tipi = remember(dati.voci) { (dati.voci.map { it.tipo } + "Spostamento").distinct().sortedBy { it.lowercase() } }
    var sceltaTipi by remember { mutableStateOf(false) }
    val sottotipi = remember(dati.voci, filtri.tipi) {
        dati.voci.filter { filtri.tipi.isEmpty() || it.tipo.lowercase() in filtri.tipi }
            .mapNotNull { it.sottotipo }.distinct().sortedBy { it.lowercase() }
    }
    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Filtri", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoData("Dal", filtri.da, { onFiltri(filtri.copy(da = it)) }, Modifier.weight(1f), consentiVuoto = true)
                CampoData("Al", filtri.a, { onFiltri(filtri.copy(a = it)) }, Modifier.weight(1f), consentiVuoto = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = filtri.importoMin,
                    onValueChange = { onFiltri(filtri.copy(importoMin = it)) },
                    label = { Text("Importo da") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = filtri.importoMax,
                    onValueChange = { onFiltri(filtri.copy(importoMax = it)) },
                    label = { Text("Importo a") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
            Box {
                OutlinedTextField(
                    value = when {
                        filtri.tipi.isEmpty() -> "Tutti"
                        else -> (tipi.filter { it.lowercase() in filtri.tipi } + listOfNotNull("Senza tipo".takeIf { TIPO_VUOTO in filtri.tipi }))
                            .joinToString(", ")
                    },
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text(if (filtri.tipi.size > 1) "Tipi (${filtri.tipi.size})" else "Tipo") },
                    modifier = Modifier.fillMaxWidth()
                )
                // Il campo è in sola lettura: un tocco apre la scelta multipla.
                Box(modifier = Modifier.matchParentSize().clickable { sceltaTipi = true })
            }
            CampoAutocompletamento("Sottotipo", filtri.sottotipo, sottotipi, { onFiltri(filtri.copy(sottotipo = it)) })
            if (filtri.attivi) {
                TextButton(onClick = { onFiltri(Filtri()) }, modifier = Modifier.align(Alignment.End)) { Text("Azzera filtri") }
            }
        }
    }
    if (sceltaTipi) {
        SceltaTipiDialog(
            tipi = tipi,
            selezionati = filtri.tipi,
            onConferma = {
                onFiltri(filtri.copy(tipi = it, sottotipo = ""))
                sceltaTipi = false
            },
            onAnnulla = { sceltaTipi = false }
        )
    }
}

/** Scelta di uno o più tipi (con ricerca); nessuno selezionato = tutti. */
@Composable
private fun SceltaTipiDialog(tipi: List<String>, selezionati: Set<String>, onConferma: (Set<String>) -> Unit, onAnnulla: () -> Unit) {
    val scelti = remember { mutableStateListOf<String>().apply { addAll(selezionati) } }
    var cerca by remember { mutableStateOf("") }
    val opzioni = (tipi.map { it to it.lowercase() } + ("Senza tipo" to TIPO_VUOTO)).distinctBy { it.second }
    val mostrate = opzioni.filter { cerca.isBlank() || it.first.contains(cerca.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onAnnulla,
        title = { Text("Tipi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = cerca,
                    onValueChange = { cerca = it },
                    label = { Text("Cerca") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row {
                    TextButton(onClick = { mostrate.forEach { (_, k) -> if (k !in scelti) scelti.add(k) } }) { Text("Seleziona mostrati") }
                    TextButton(onClick = { scelti.clear() }) { Text("Nessuno") }
                }
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(mostrate, key = { "t:" + it.second }) { (nome, chiave) ->
                        val selezionato = chiave in scelti
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { if (selezionato) scelti.remove(chiave) else scelti.add(chiave) }
                        ) {
                            Checkbox(checked = selezionato, onCheckedChange = { if (it) scelti.add(chiave) else scelti.remove(chiave) })
                            Text(nome, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Text(
                    if (scelti.isEmpty()) "Nessun tipo selezionato: si mostrano tutti." else "${scelti.size} selezionati",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConferma(scelti.toSet()) }) { Text("Applica") } },
        dismissButton = { TextButton(onClick = onAnnulla) { Text("Annulla") } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RigaListaOperazione(
    riga: RigaOperazione,
    dati: DatiApp,
    valuta: String,
    inSelezione: Boolean,
    selezionata: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val op = riga.operazione
    val descrizione = if (op.trasferimento) {
        val verso = if (op.importoCent < 0) "verso" else "da"
        "↔ Spostamento $verso ${dati.etichetta(op.contoValutaDestId)}"
    } else {
        op.voceId?.let { dati.vociPerId[it]?.descrizione } ?: "Senza voce"
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(if (selezionata) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (inSelezione) {
            Checkbox(checked = selezionata, onCheckedChange = { onClick() })
        }
        PallinoColore(op.voceId?.let { dati.vociPerId[it]?.colore }, modifier = Modifier.padding(end = 8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(descrizione, style = MaterialTheme.typography.bodyLarge)
            Text(
                formattaData(op.data) + (op.note?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            TestoImporto(op.importoCent / 100.0, valuta, grassetto = true)
            Text(
                "saldo ${formattaCent(riga.saldoDopo, valuta)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
