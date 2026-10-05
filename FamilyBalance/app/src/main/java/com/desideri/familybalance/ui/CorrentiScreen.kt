package com.desideri.familybalance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desideri.familybalance.DatiApp
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.data.Operazione
import com.desideri.familybalance.data.Voce
import com.desideri.familybalance.logica.Calcoli
import com.desideri.familybalance.logica.Classe
import com.desideri.familybalance.logica.ValoreGrafico
import com.desideri.familybalance.logica.formattaCent
import com.desideri.familybalance.logica.formattaData
import com.desideri.familybalance.logica.formattaImporto
import com.desideri.familybalance.logica.formattaMese
import java.time.YearMonth

/** Id usato nel filtro per le operazioni senza tipo. */
private const val SENZA_TIPO = 0L

/** Una spesa corrente in un mese: la voce (null = senza tipo), il totale in EUR e le operazioni. */
private data class RigaCorrente(val voce: Voce?, val totale: Double, val operazioni: List<Operazione>)

private data class MeseCorrenti(val mese: YearMonth, val righe: List<RigaCorrente>) {
    val totale: Double get() = righe.sumOf { it.totale }
}

/**
 * Spese correnti (né ricorrenti, né entrate, né spostamenti) raggruppate per mese e per voce, con
 * lo stesso layout delle Ricorrenti: filtro a scelta multipla delle spese e periodo fissi in alto,
 * dal mese più vecchio al più recente. Toccando una riga si vedono le sue operazioni (e si modificano).
 */
@Composable
fun CorrentiScreen(vm: SpeseViewModel) {
    val dati by vm.dati.collectAsStateWithLifecycle()
    val periodo by vm.periodoCorrenti.collectAsStateWithLifecycle()
    val cambi by vm.cambi.collectAsStateWithLifecycle()
    val stato = rememberLazyListState()
    var filtroVoci by rememberSaveable { mutableStateOf<List<Long>>(emptyList()) }
    var sceltaVoci by remember { mutableStateOf(false) }
    var mostraGrafico by remember { mutableStateOf(false) }
    var mostraReport by remember { mutableStateOf(false) }
    var aperta by remember { mutableStateOf<Pair<RigaCorrente, YearMonth>?>(null) }

    // Voci correnti (più "Senza tipo") per il filtro.
    val vociCorrenti = remember(dati.voci) {
        dati.voci.filter { !it.ricorrente && !it.entrata }.sortedBy { it.descrizione.lowercase() } + Voce(id = SENZA_TIPO, tipo = "Senza tipo")
    }
    // CHF convertiti come nel Bilancio: passati al cambio del mese, corrente al cambio delle Impostazioni.
    fun inEuro(op: Operazione) = cambi.inEuro(op.importoCent, dati.contiValutaPerId[op.contoValutaId]?.valuta ?: "EUR", Calcoli.mese(op.data))
    val correnti = remember(dati, periodo, filtroVoci) {
        dati.operazioni.filter { op ->
            val voce = op.voceId?.let { dati.vociPerId[it] }
            val m = Calcoli.mese(op.data)
            Calcoli.classifica(op, voce) == Classe.CORRENTE && m >= periodo.first && m <= periodo.second &&
                (filtroVoci.isEmpty() || (voce?.id ?: SENZA_TIPO) in filtroVoci)
        }
    }
    // Dal mese più vecchio (in alto) al più recente, come nelle altre sezioni.
    val mesi = remember(correnti, periodo, cambi) {
        generateSequence(periodo.first) { it.plusMonths(1) }.takeWhile { it <= periodo.second }.map { m ->
            val delMese = correnti.filter { Calcoli.mese(it.data) == m }
            MeseCorrenti(m, delMese.groupBy { it.voceId }.map { (voceId, ops) ->
                RigaCorrente(voceId?.let { dati.vociPerId[it] }, ops.sumOf { inEuro(it) }, ops.sortedByDescending { it.data })
            }.sortedBy { it.totale })
        }.toList()
    }

    // All'apertura la lista parte dal mese corrente (o dall'ultimo del periodo).
    LaunchedEffect(mesi.isNotEmpty()) {
        val indice = mesi.indexOfFirst { it.mese == YearMonth.now() }.takeIf { it >= 0 } ?: mesi.lastIndex
        if (indice >= 0) stato.scrollToItem(indice)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = if (filtroVoci.isEmpty()) "Tutte" else vociCorrenti.filter { it.id in filtroVoci }.joinToString(", ") { it.descrizione },
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(if (filtroVoci.size > 1) "Spese correnti (${filtroVoci.size})" else "Spese correnti") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { sceltaVoci = true })
                }
                MenuSezione(
                    listOf(
                        VoceMenuSezione("Grafico", Icons.AutoMirrored.Filled.ShowChart, abilitata = correnti.isNotEmpty()) { mostraGrafico = true },
                        VoceMenuSezione("Report", Icons.Filled.TableChart) { mostraReport = true }
                    )
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SceltaMese("Dal", periodo.first, { vm.impostaPeriodoCorrenti(it, periodo.second) }, Modifier.weight(1f))
                SceltaMese("Al", periodo.second, { vm.impostaPeriodoCorrenti(periodo.first, it) }, Modifier.weight(1f))
            }
        }
        LazyColumn(state = stato, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(mesi, key = { it.mese.toString() }) { mese -> CardMeseCorrenti(mese, onRiga = { aperta = it to mese.mese }) }
        }
    }

    if (sceltaVoci) {
        SceltaVociDialog(
            voci = vociCorrenti,
            selezionate = filtroVoci.toSet(),
            onConferma = {
                filtroVoci = it.toList()
                sceltaVoci = false
            },
            onAnnulla = { sceltaVoci = false },
            titolo = "Spese correnti"
        )
    }
    if (mostraGrafico) {
        // Spese filtrate nel periodo, in EUR: per voce se ne sono scelte fino a 8, altrimenti il totale.
        val scelte = if (filtroVoci.isEmpty()) vociCorrenti else vociCorrenti.filter { it.id in filtroVoci }
        val perVoce = filtroVoci.isNotEmpty() && scelte.size <= 8
        val indice = scelte.withIndex().associate { (i, v) -> v.id to i }
        val serie = if (perVoce) scelte.mapIndexed { i, v -> SerieGrafico(v.descrizione, coloreSerie(i, v.colore)) }
        else listOf(SerieGrafico(if (filtroVoci.isEmpty()) "Tutte le correnti" else "Totale selezionate", coloreSerie(0, null)))
        val valori = correnti.map { op -> ValoreGrafico(if (perVoce) indice[op.voceId ?: SENZA_TIPO] ?: 0 else 0, op.data, -inEuro(op)) }
        GraficoSpeseDialog(
            titolo = "Spese correnti",
            nota = "Spese di ${formattaMese(periodo.first)} – ${formattaMese(periodo.second)} delle voci filtrate, in EUR" +
                (if (!perVoce && filtroVoci.size > 8) " (più di 8 voci: mostrato il totale)" else "") + ".",
            serie = serie,
            valori = valori,
            onChiudi = { mostraGrafico = false }
        )
    }
    if (mostraReport) ReportCorrentiDialog(vm, onChiudi = { mostraReport = false })
    aperta?.let { (riga, mese) -> OperazioniCorrentiDialog(vm, dati, riga, mese, onChiudi = { aperta = null }) }
}

@Composable
private fun CardMeseCorrenti(mese: MeseCorrenti, onRiga: (RigaCorrente) -> Unit) {
    val corrente = mese.mese == YearMonth.now()
    // Mesi passati e futuri chiusi per default; il corrente sempre aperto.
    var espanso by rememberSaveable(mese.mese.toString()) { mutableStateOf(corrente) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (corrente) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(enabled = !corrente) { espanso = !espanso }) {
                if (!corrente) Icon(if (espanso) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (espanso) "Comprimi" else "Espandi")
                Text(
                    formattaMese(mese.mese) + if (corrente) " (corrente)" else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TestoImporto(mese.totale, grassetto = true)
            }
            if (espanso) {
            if (mese.righe.isEmpty()) {
                Text("Nessuna spesa corrente", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
            mese.righe.forEachIndexed { indice, riga ->
                if (indice == 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onRiga(riga) }.padding(vertical = 4.dp)
                ) {
                    PallinoColore(riga.voce?.colore, modifier = Modifier.padding(end = 6.dp), dimensione = 10.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(riga.voce?.descrizione ?: "Senza tipo", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (riga.operazioni.size == 1) "1 operazione" else "${riga.operazioni.size} operazioni",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TestoImporto(riga.totale)
                }
            }
            }
        }
    }
}

/** Le operazioni di una spesa corrente nel mese; toccandone una la si modifica. */
@Composable
private fun OperazioniCorrentiDialog(vm: SpeseViewModel, dati: DatiApp, riga: RigaCorrente, mese: YearMonth, onChiudi: () -> Unit) {
    var inModifica by remember { mutableStateOf<Operazione?>(null) }
    // Le operazioni aggiornate si prendono dai dati correnti (dopo una modifica o un'eliminazione).
    val ids = riga.operazioni.map { it.id }.toSet()
    val operazioni = dati.operazioni.filter { it.id in ids }.sortedByDescending { it.data }
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text("${riga.voce?.descrizione ?: "Senza tipo"} · ${formattaMese(mese)}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (operazioni.isEmpty()) Text("Nessuna operazione.", style = MaterialTheme.typography.bodySmall)
                operazioni.forEach { op ->
                    Column(modifier = Modifier.fillMaxWidth().clickable { inModifica = op }.padding(vertical = 4.dp)) {
                        Row {
                            Text("${formattaData(op.data)} · ${dati.etichetta(op.contoValutaId)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                formattaCent(op.importoCent, dati.contiValutaPerId[op.contoValutaId]?.valuta ?: "EUR"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        op.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                    }
                    HorizontalDivider()
                }
                Text("Totale: ${formattaImporto(riga.totale)} · tocca un'operazione per modificarla.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onChiudi) { Text("Chiudi") } }
    )
    inModifica?.let { op -> OperazioneDialog(vm, dati, op.contoValutaId, op, onChiudi = { inModifica = null }) }
}
