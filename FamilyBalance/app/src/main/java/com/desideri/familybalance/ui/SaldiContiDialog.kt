package com.desideri.familybalance.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.desideri.familybalance.DatiApp
import com.desideri.familybalance.data.ContoValuta
import com.desideri.familybalance.logica.Calcoli
import com.desideri.familybalance.logica.formattaCent
import com.desideri.familybalance.logica.formattaMeseBreve
import java.time.YearMonth

private val L_MESE_SALDI = 64.dp
private val L_CONTO_SALDI = 104.dp

/**
 * Saldi dei conti (menu ⋮ del saldo totale): per ogni mese il saldo a fine mese di ciascun conto
 * (nella sua valuta) e il totale dei conti scelti in EUR (CHF al cambio delle Impostazioni). Si
 * filtrano il periodo (filtro a scomparsa) e i conti (i virtuali solo se mostrati); la tabella si
 * vede anche come grafico e come radar animato (un asse per conto più il totale, in EUR).
 */
@Composable
fun SaldiContiDialog(dati: DatiApp, mostraVirtuali: Boolean, cambioChfEur: Double, pausaRadarMs: Int, onChiudi: () -> Unit) {
    val disponibili = dati.contiValutaOrdinati.filter { mostraVirtuali || !dati.virtuale(it.id) }
    var scelti by remember { mutableStateOf(disponibili) }
    val conti = disponibili.filter { c -> scelti.any { it.id == c.id } }
    var mostraFiltro by remember { mutableStateOf(false) }
    var da by remember { mutableStateOf<YearMonth?>(null) }
    var a by remember { mutableStateOf<YearMonth?>(null) }
    var mostraGrafico by remember { mutableStateOf(false) }
    var mostraRadar by remember { mutableStateOf(false) }
    fun inEuro(cent: Long, cv: ContoValuta) = Calcoli.inEuro(cent, cv.valuta, cambioChfEur)

    // Saldo a fine mese (centesimi nella valuta del conto) di tutti i conti disponibili.
    val saldiPerMese: List<Pair<YearMonth, Map<Long, Long>>> = remember(dati.operazioni, disponibili) {
        val ids = disponibili.map { it.id }.toSet()
        val ops = dati.operazioni.filter { it.contoValutaId in ids }
        if (ops.isEmpty()) return@remember emptyList()
        val perMese = ops.groupBy { Calcoli.mese(it.data) }
        val saldi = disponibili.associate { it.id to it.saldoInizialeCent }.toMutableMap()
        val ultimo = maxOf(YearMonth.now(), perMese.keys.max())
        generateSequence(perMese.keys.min()) { it.plusMonths(1) }.takeWhile { it <= ultimo }.map { m ->
            perMese[m].orEmpty().forEach { saldi[it.contoValutaId] = (saldi[it.contoValutaId] ?: 0L) + it.importoCent }
            m to saldi.toMap()
        }.toList()
    }
    val mesi = saldiPerMese.filter { (m, _) -> (da == null || m >= da!!) && (a == null || m <= a!!) }
    fun totale(saldi: Map<Long, Long>) = conti.sumOf { inEuro(saldi[it.id] ?: 0L, it) }
    val nomeTotale = if (conti.size == disponibili.size) "Totale" else "Totale scelti"

    val verticale = rememberScrollState()
    val orizzontale = rememberScrollState()
    LaunchedEffect(mesi.size) { verticale.scrollTo(verticale.maxValue) }
    val zebra = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val coloreTotale = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)

    Dialog(onDismissRequest = onChiudi, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize(), tonalElevation = 4.dp) {
            Column(modifier = Modifier.fillMaxSize().paddingBarreDialog().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Saldi dei conti", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { mostraFiltro = !mostraFiltro }) {
                        Icon(
                            Icons.Filled.FilterList, contentDescription = "Filtri",
                            tint = if (mostraFiltro || da != null || a != null || conti.size != disponibili.size) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { mostraRadar = true }, enabled = mesi.isNotEmpty() && conti.isNotEmpty()) {
                        Icon(Icons.Filled.Radar, contentDescription = "Radar animato")
                    }
                    IconButton(onClick = { mostraGrafico = true }, enabled = mesi.isNotEmpty() && conti.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Grafico")
                    }
                    IconButton(onClick = onChiudi) { Icon(Icons.Filled.Close, contentDescription = "Chiudi") }
                }
                if (mostraFiltro) {
                    MenuMultiplo("Conti", disponibili, conti, { dati.etichetta(it.id) }, onCambia = { scelti = it }, modifier = Modifier.fillMaxWidth(), conTutti = true)
                    val primo = saldiPerMese.firstOrNull()?.first ?: YearMonth.now()
                    val ultimo = saldiPerMese.lastOrNull()?.first ?: YearMonth.now()
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
                    "Saldo a fine mese di ogni conto nella sua valuta" + (if (!mostraVirtuali && dati.contiValutaVirtuali.isNotEmpty()) " (virtuali nascosti)" else "") +
                        "; $nomeTotale in EUR, CHF a $cambioChfEur (Impostazioni).",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp)
                )
                if (mesi.isEmpty() || conti.isEmpty()) {
                    Text(if (conti.isEmpty()) "Scegli almeno un conto." else "Nessun mese nel periodo.", modifier = Modifier.padding(16.dp))
                } else {
                    // Intestazione fissa, mese fermo a sinistra, colonne che scorrono insieme.
                    Row {
                        CellaReport("Mese", null, L_MESE_SALDI, grassetto = true, alta = true)
                        Row(modifier = Modifier.horizontalScroll(orizzontale)) {
                            conti.forEach { CellaReport(dati.etichetta(it.id), null, L_CONTO_SALDI, grassetto = true, alta = true) }
                            CellaReport(nomeTotale, null, L_CONTO_SALDI, grassetto = true, alta = true, sfondo = coloreTotale)
                        }
                    }
                    Row(modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.verticalScroll(verticale)) {
                            mesi.forEachIndexed { i, (m, _) ->
                                CellaReport(formattaMeseBreve(m), null, L_MESE_SALDI, grassetto = m == YearMonth.now(), alta = true, sfondo = if (i % 2 == 1) zebra else Color.Transparent)
                            }
                        }
                        Column(modifier = Modifier.verticalScroll(verticale).horizontalScroll(orizzontale)) {
                            mesi.forEachIndexed { i, (_, saldi) ->
                                val sfondo = if (i % 2 == 1) zebra else Color.Transparent
                                Row {
                                    conti.forEach { cv -> CellaReport(formattaCent(saldi[cv.id] ?: 0L, cv.valuta), null, L_CONTO_SALDI, alta = true, sfondo = sfondo) }
                                    CellaReport(formattaCent(Math.round(totale(saldi) * 100)), null, L_CONTO_SALDI, grassetto = true, alta = true, sfondo = coloreTotale)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (mostraRadar) {
        RadarMensileDialog(
            titolo = "Saldi dei conti · radar",
            nota = "Saldo a fine mese di ogni conto scelto e $nomeTotale, in EUR (CHF a $cambioChfEur).",
            assi = conti.map { dati.etichetta(it.id) } + nomeTotale,
            mesi = mesi.map { it.first },
            valori = mesi.map { (_, saldi) -> conti.map { inEuro(saldi[it.id] ?: 0L, it) } + totale(saldi) },
            pausaMs = pausaRadarMs,
            onChiudi = { mostraRadar = false }
        )
    }
    if (mostraGrafico) {
        // Saldo a fine giorno di ogni conto scelto e del totale, in EUR; entrate e uscite senza spostamenti.
        val serie = remember(dati.operazioni, conti, da, a) {
            fun nelPeriodo(g: Long) = Calcoli.mese(g).let { (da == null || it >= da!!) && (a == null || it <= a!!) }
            val ids = conti.map { it.id }.toSet()
            val saldi = conti.associate { it.id to it.saldoInizialeCent }.toMutableMap()
            val perConto = conti.associate { it.id to ArrayList<Pair<Long, Double>>() }
            val entrate = conti.associate { it.id to ArrayList<Pair<Long, Double>>() }
            val uscite = conti.associate { it.id to ArrayList<Pair<Long, Double>>() }
            val totali = ArrayList<Pair<Long, Double>>()
            dati.operazioni.filter { it.contoValutaId in ids }.groupBy { it.data }.toSortedMap().forEach { (giorno, ops) ->
                ops.forEach { op ->
                    val cv = dati.contiValutaPerId[op.contoValutaId] ?: return@forEach
                    saldi[cv.id] = (saldi[cv.id] ?: 0L) + op.importoCent
                    if (!op.trasferimento && nelPeriodo(giorno)) {
                        val eur = inEuro(op.importoCent, cv)
                        if (eur > 0) entrate[cv.id]?.add(giorno to eur) else uscite[cv.id]?.add(giorno to -eur)
                    }
                }
                if (nelPeriodo(giorno)) {
                    conti.forEach { cv -> perConto.getValue(cv.id).add(giorno to inEuro(saldi[cv.id] ?: 0L, cv)) }
                    totali += giorno to conti.sumOf { inEuro(saldi[it.id] ?: 0L, it) }
                }
            }
            conti.map { cv -> ContoGrafico(dati.etichetta(cv.id), perConto.getValue(cv.id), entrate.getValue(cv.id), uscite.getValue(cv.id), dati.coloreConto(cv.id)) } +
                ContoGrafico(nomeTotale, totali, conti.flatMap { entrate.getValue(it.id) }, conti.flatMap { uscite.getValue(it.id) })
        }
        GraficoContiDialog(
            titolo = "Saldi dei conti",
            nota = "In EUR (CHF a $cambioChfEur). Saldo a fine periodo; entrate e uscite sommate nel periodo, esclusi gli spostamenti.",
            conti = serie,
            onChiudi = { mostraGrafico = false }
        )
    }
}
