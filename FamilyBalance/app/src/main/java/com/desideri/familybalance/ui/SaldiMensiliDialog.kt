package com.desideri.familybalance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Radar
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.desideri.familybalance.DatiApp
import com.desideri.familybalance.logica.Calcoli
import com.desideri.familybalance.logica.formattaCent
import com.desideri.familybalance.logica.formattaMeseBreve
import com.desideri.familybalance.logica.formattaMese
import java.time.YearMonth

/** Un mese della tabella dei saldi (centesimi nella valuta del conto). */
private data class SaldoMese(val mese: YearMonth, val iniziale: Long, val entrate: Long, val uscite: Long, val finale: Long = iniziale + entrate + uscite)

/**
 * Saldi mese per mese di un conto/valuta: saldo precedente, totale entrate, totale uscite (tutte le
 * operazioni, spostamenti compresi) e saldo finale, dal primo mese con operazioni al corrente.
 */
@Composable
fun SaldiMensiliDialog(dati: DatiApp, contoValutaId: Long, pausaRadarMs: Int = 1000, onChiudi: () -> Unit) {
    val cv = dati.contiValutaPerId[contoValutaId] ?: return
    SaldiMensiliDialog(
        dati, listOf(contoValutaId), dati.etichetta(contoValutaId), cv.valuta,
        "Tutte le operazioni del conto, spostamenti compresi, in ${cv.valuta}", null, pausaRadarMs, onChiudi
    )
}

/**
 * Saldi mensili di più conti/valuta insieme (es. il saldo totale): saldo a fine mese dei conti
 * convertito in [valuta] con [converti] (null = stessa valuta), entrate e uscite senza gli
 * spostamenti tra i conti considerati; il saldo precedente è quello di fine mese prima.
 */
@Composable
fun SaldiMensiliDialog(
    dati: DatiApp,
    contiValutaIds: List<Long>,
    nome: String,
    valuta: String,
    nota: String,
    converti: ((Long, String) -> Long)?,
    pausaRadarMs: Int = 1000,
    onChiudi: () -> Unit
) {
    val insieme = contiValutaIds.toSet()
    fun inValuta(cent: Long, cvId: Long): Long = converti?.invoke(cent, dati.contiValutaPerId[cvId]?.valuta ?: valuta) ?: cent
    // Operazioni dei conti, senza gli spostamenti tra due conti considerati (con più conti).
    val operazioni = remember(dati.operazioni, insieme) {
        val perId = dati.operazioni.associateBy { it.id }
        dati.operazioni.filter { op ->
            op.contoValutaId in insieme && !(op.trasferimento && insieme.size > 1 &&
                (op.contoValutaDestId in insieme || op.collegataId?.let { perId[it]?.contoValutaId } in insieme))
        }
    }
    // Saldo nativo di ogni conto a fine di ogni giorno con operazioni (spostamenti interni compresi).
    val tutteDeiConti = remember(dati.operazioni, insieme) { dati.operazioni.filter { it.contoValutaId in insieme } }
    fun saldoConvertito(saldi: Map<Long, Long>) = saldi.entries.sumOf { (id, c) -> inValuta(c, id) }
    val tuttiIMesi: List<SaldoMese> = remember(tutteDeiConti, operazioni) {
        if (tutteDeiConti.isEmpty()) return@remember emptyList<SaldoMese>()
        val perMese = tutteDeiConti.groupBy { Calcoli.mese(it.data) }
        val esterniPerMese = operazioni.groupBy { Calcoli.mese(it.data) }
        val ultimo = maxOf(YearMonth.now(), perMese.keys.max())
        val saldi = insieme.associateWith { dati.contiValutaPerId[it]?.saldoInizialeCent ?: 0L }.toMutableMap()
        var precedente = saldoConvertito(saldi)
        generateSequence(perMese.keys.min()) { it.plusMonths(1) }.takeWhile { it <= ultimo }.map { m ->
            perMese[m].orEmpty().forEach { saldi[it.contoValutaId] = (saldi[it.contoValutaId] ?: 0L) + it.importoCent }
            val esterni = esterniPerMese[m].orEmpty().map { inValuta(it.importoCent, it.contoValutaId) }
            SaldoMese(m, precedente, esterni.filter { it > 0 }.sum(), esterni.filter { it < 0 }.sum(), saldoConvertito(saldi))
                .also { precedente = it.finale }
        }.toList()
    }
    // Periodo mostrato (filtro a scomparsa): null = senza limite.
    var mostraFiltro by remember { mutableStateOf(false) }
    var da by remember { mutableStateOf<YearMonth?>(null) }
    var a by remember { mutableStateOf<YearMonth?>(null) }
    val mesi = tuttiIMesi.filter { (da == null || it.mese >= da!!) && (a == null || it.mese <= a!!) }
    val stato = rememberLazyListState()
    var mostraGrafico by remember { mutableStateOf(false) }
    var mostraRadar by remember { mutableStateOf(false) }
    LaunchedEffect(mesi.size) { if (mesi.isNotEmpty()) stato.scrollToItem(mesi.lastIndex) }
    val zebra = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)

    Dialog(onDismissRequest = onChiudi, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize(), tonalElevation = 4.dp) {
            Column(modifier = Modifier.fillMaxSize().paddingBarreDialog().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Saldi mensili · $nome", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { mostraFiltro = !mostraFiltro }) {
                        Icon(
                            Icons.Filled.FilterList, contentDescription = "Periodo",
                            tint = if (mostraFiltro || da != null || a != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { mostraRadar = true }, enabled = mesi.isNotEmpty()) {
                        Icon(Icons.Filled.Radar, contentDescription = "Radar animato")
                    }
                    IconButton(onClick = { mostraGrafico = true }, enabled = mesi.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Grafico dei saldi")
                    }
                    IconButton(onClick = onChiudi) { Icon(Icons.Filled.Close, contentDescription = "Chiudi") }
                }
                if (mostraFiltro) {
                    val primo = tuttiIMesi.firstOrNull()?.mese ?: YearMonth.now()
                    val ultimo = tuttiIMesi.lastOrNull()?.mese ?: YearMonth.now()
                    Row {
                        SceltaMese("Dal", da ?: primo, { da = it; if (a != null && a!! < it) a = it }, Modifier.weight(1f))
                        SceltaMese("Al", a ?: ultimo, { a = it; if (da != null && da!! > it) da = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf(12L to "12 mesi", 24L to "24 mesi").forEach { (n, testo) ->
                            val inizio = YearMonth.now().minusMonths(n - 1)
                            FilterChip(selected = da == inizio && a == null, onClick = { da = inizio; a = null }, label = { Text(testo) })
                        }
                        FilterChip(selected = da == YearMonth.now().withMonth(1) && a == null, onClick = { da = YearMonth.now().withMonth(1); a = null }, label = { Text("Anno in corso") })
                        FilterChip(selected = da == null && a == null, onClick = { da = null; a = null }, label = { Text("Tutto") })
                    }
                }
                Text(
                    "$nota.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Riga("Mese", "Saldo prec.", "Entrate", "Uscite", "Saldo fine", intestazione = true)
                HorizontalDivider()
                if (mesi.isEmpty()) Text(if (tuttiIMesi.isEmpty()) "Nessuna operazione." else "Nessun mese nel periodo scelto.", modifier = Modifier.padding(16.dp))
                LazyColumn(state = stato, modifier = Modifier.weight(1f)) {
                    itemsIndexed(mesi, key = { _, m -> m.mese.toString() }) { i, m ->
                        Riga(
                            formattaMeseBreve(m.mese),
                            formattaCent(m.iniziale, valuta),
                            formattaCent(m.entrate, valuta),
                            formattaCent(m.uscite, valuta),
                            formattaCent(m.finale, valuta),
                            sfondo = if (i % 2 == 1) zebra else Color.Transparent,
                            evidenzia = m.mese == YearMonth.now()
                        )
                    }
                }
            }
        }
    }

    if (mostraRadar) {
        RadarSaldiDialog("Radar · $nome", mesi, valuta, pausaRadarMs, onChiudi = { mostraRadar = false })
    }
    if (mostraGrafico) {
        // Saldo a fine giorno dei conti (convertito), entrate e uscite (positive) delle operazioni considerate.
        val conto = remember(tutteDeiConti, operazioni, da, a) {
            fun nelPeriodo(giorno: Long) = Calcoli.mese(giorno).let { (da == null || it >= da!!) && (a == null || it <= a!!) }
            val saldiConti = insieme.associateWith { dati.contiValutaPerId[it]?.saldoInizialeCent ?: 0L }.toMutableMap()
            val saldi = ArrayList<Pair<Long, Double>>()
            tutteDeiConti.groupBy { it.data }.toSortedMap().forEach { (giorno, del) ->
                del.forEach { saldiConti[it.contoValutaId] = (saldiConti[it.contoValutaId] ?: 0L) + it.importoCent }
                if (nelPeriodo(giorno)) saldi += giorno to saldoConvertito(saldiConti) / 100.0
            }
            val ops = operazioni.filter { nelPeriodo(it.data) }.map { it.data to inValuta(it.importoCent, it.contoValutaId) / 100.0 }
            ContoGrafico(nome, saldi, ops.filter { it.second > 0 }, ops.filter { it.second < 0 }.map { it.first to -it.second })
        }
        GraficoContiDialog(
            titolo = "Saldi · $nome",
            nota = nota + (if (da != null || a != null) ", nel periodo scelto" else "") +
                ". Saldo a fine periodo, entrate e uscite sommate nel periodo.",
            conti = listOf(conto),
            valuta = valuta,
            onChiudi = { mostraGrafico = false }
        )
    }
}

/** Radar animato dei saldi mensili: un asse per colonna della tabella, un poligono per mese. */
@Composable
private fun RadarSaldiDialog(titolo: String, mesi: List<SaldoMese>, valuta: String, pausaMs: Int, onChiudi: () -> Unit) {
    Dialog(onDismissRequest = onChiudi, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize(), tonalElevation = 4.dp) {
            Column(modifier = Modifier.fillMaxSize().paddingBarreDialog().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(titolo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onChiudi) { Icon(Icons.Filled.Close, contentDescription = "Chiudi") }
                }
                RadarAnimato(
                    assi = listOf("Saldo prec.", "Entrate", "Uscite", "Saldo fine"),
                    etichette = mesi.map { formattaMese(it.mese) },
                    valori = mesi.map { listOf(it.iniziale, it.entrate, it.uscite, it.finale) },
                    formatta = { formattaCent(it, valuta) },
                    pausaMs = pausaMs,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }
}

@Composable
private fun Riga(
    mese: String,
    iniziale: String,
    entrate: String,
    uscite: String,
    finale: String,
    intestazione: Boolean = false,
    sfondo: Color = Color.Transparent,
    evidenzia: Boolean = false
) {
    val stile = if (intestazione) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
    val peso = if (intestazione || evidenzia) FontWeight.Bold else null
    Row(modifier = Modifier.fillMaxWidth().background(sfondo).padding(vertical = 6.dp, horizontal = 2.dp)) {
        Text(mese, style = stile, fontWeight = peso, modifier = Modifier.weight(0.7f))
        listOf(iniziale, entrate, uscite, finale).forEachIndexed { i, t ->
            Text(
                t, style = stile, textAlign = TextAlign.End, maxLines = 2,
                fontWeight = if (i == 3) FontWeight.Bold else peso,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
