package com.desideri.familybalance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.logica.RigaBilancio
import com.desideri.familybalance.logica.StatoMese
import com.desideri.familybalance.logica.formattaImporto
import com.desideri.familybalance.logica.formattaMese
import java.util.Locale
import kotlin.math.abs

/**
 * Bilancio nel periodo scelto (Dal/Al, fisso in alto), in ordine cronologico: il mese corrente separato tra spese correnti, ricorrenti e stipendio/interessi; per i
 * mesi futuri il saldo previsto (saldo precedente + target di risparmio − ricorrenti previste);
 * per i mesi passati saldo a fine mese e scostamento del risparmio dal target.
 */
@Composable
fun BilancioScreen(vm: SpeseViewModel) {
    val righe by vm.bilancio.collectAsStateWithLifecycle()
    val periodo by vm.periodoBilancio.collectAsStateWithLifecycle()
    val impostazioni by vm.impostazioni.collectAsStateWithLifecycle()
    val stato = rememberLazyListState()
    var mostraGrafico by remember { mutableStateOf(false) }
    var mostraReport by remember { mutableStateOf(false) }

    // All'apertura la lista parte dal mese corrente (i passati sono sopra, i futuri sotto).
    LaunchedEffect(righe.isNotEmpty()) {
        val indice = righe.indexOfFirst { it.stato == StatoMese.CORRENTE }
        if (indice >= 0) stato.scrollToItem(indice)
    }

    // Periodo fisso in alto; sotto scorre la lista dei mesi in ordine cronologico.
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SceltaMese("Dal", periodo.first, { vm.impostaPeriodoBilancio(it, periodo.second) }, Modifier.weight(1f))
                SceltaMese("Al", periodo.second, { vm.impostaPeriodoBilancio(periodo.first, it) }, Modifier.weight(1f))
                MenuSezione(
                    listOf(
                        VoceMenuSezione("Grafico", Icons.AutoMirrored.Filled.ShowChart, abilitata = righe.isNotEmpty()) { mostraGrafico = true },
                        VoceMenuSezione("Report", Icons.Filled.TableChart) { mostraReport = true }
                    )
                )
            }
            if (impostazioni.targetRisparmioCent == 0L) {
                Text(
                    "Imposta il target di risparmio mensile in Impostazioni (menu ⋮) per le previsioni.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        LazyColumn(state = stato, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            if (righe.isEmpty()) item { Text("Nessun mese nel periodo scelto.", style = MaterialTheme.typography.bodyMedium) }
            items(righe, key = { it.mese.toString() }) { r ->
                when (r.stato) {
                    StatoMese.CORRENTE -> CardMeseCorrente(r)
                    StatoMese.FUTURO -> CardMeseFuturo(r)
                    StatoMese.PASSATO -> CardMesePassato(r)
                }
            }
        }
    }
    if (mostraGrafico) GraficoBilancioDialog(righe, onChiudi = { mostraGrafico = false })
    if (mostraReport) ReportBilancioDialog(vm, onChiudi = { mostraReport = false })
}

@Composable
private fun RigaValore(etichetta: String, valore: Double, grassetto: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(etichetta, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), fontWeight = if (grassetto) FontWeight.Bold else null)
        TestoImporto(valore, grassetto = grassetto)
    }
}

@Composable
private fun CardMeseCorrente(r: RigaBilancio) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Bilancio attuale · ${formattaMese(r.mese)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            r.saldoFine?.let { RigaValore("Saldo attuale", it, grassetto = true) }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            RigaValore("Stipendio / interessi", r.entrate)
            RigaValore("Spese correnti", r.correnti)
            RigaValore("Spese ricorrenti pagate", r.ricorrentiPagati)
            if (r.ricorrentiPrevisti != 0.0) RigaValore("Spese ricorrenti ancora previste", r.ricorrentiPrevisti)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            RigaValore("Risparmio finora (entrate + correnti)", r.risparmio)
            RigaValore("Rispetto al target di ${formattaImporto(r.target)}", r.deltaTarget)
            RigheCambio(r)
            r.saldoPrevisto?.let { RigaValore("Saldo previsto a fine mese", it, grassetto = true) }
        }
    }
}

@Composable
private fun CardMeseFuturo(r: RigaBilancio) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(formattaMese(r.mese), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            RigaValore("Risparmio previsto (target)", r.target)
            RigaValore("Spese ricorrenti previste", r.ricorrentiTotali)
            r.saldoPrevisto?.let { RigaValore("Saldo previsto", it, grassetto = true) }
        }
    }
}

@Composable
private fun CardMesePassato(r: RigaBilancio) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formattaMese(r.mese), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                r.saldoFine?.let { Text("Saldo ${formattaImporto(it)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            }
            RigaValore("Stipendio / interessi", r.entrate)
            RigaValore("Spese correnti", r.correnti)
            RigaValore("Spese ricorrenti", r.ricorrentiPagati)
            RigaValore("Risparmio (entrate + correnti)", r.risparmio)
            RigaValore("Delta rispetto al target", r.deltaTarget, grassetto = true)
            RigheCambio(r)
        }
    }
}

/** Effetto del cambio CHF/EUR sul saldo del mese (solo se c'è). */
@Composable
private fun RigheCambio(r: RigaBilancio) {
    if (abs(r.effettoCambio) < 0.005 && abs(r.cambioSpostamenti) < 0.005) return
    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
    Text(
        "Cambio del mese: 1 CHF = ${String.format(Locale.ITALY, "%.4f", r.cambioChfEur)} EUR",
        style = MaterialTheme.typography.bodySmall
    )
    if (abs(r.effettoCambio) >= 0.005) RigaValore("Effetto cambio sui saldi CHF", r.effettoCambio)
    if (abs(r.cambioSpostamenti) >= 0.005) RigaValore("Cambio applicato negli spostamenti", r.cambioSpostamenti)
}
