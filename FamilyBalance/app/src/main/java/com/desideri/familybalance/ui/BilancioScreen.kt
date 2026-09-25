package com.desideri.familybalance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * Bilancio: il mese corrente separato tra spese correnti, ricorrenti e stipendio/interessi; per i
 * mesi futuri il saldo previsto (saldo precedente + target di risparmio − ricorrenti previste);
 * per i mesi passati saldo a fine mese e scostamento del risparmio dal target.
 */
@Composable
fun BilancioScreen(vm: SpeseViewModel) {
    val righe by vm.bilancio.collectAsStateWithLifecycle()
    val impostazioni by vm.impostazioni.collectAsStateWithLifecycle()
    val corrente = righe.firstOrNull { it.stato == StatoMese.CORRENTE }
    val futuri = righe.filter { it.stato == StatoMese.FUTURO }
    val passati = righe.filter { it.stato == StatoMese.PASSATO }.asReversed()

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (impostazioni.targetRisparmioCent == 0L) {
            item {
                Text(
                    "Imposta il target di risparmio mensile in Impostazioni (menu ⋮) per le previsioni.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        corrente?.let { item { CardMeseCorrente(it) } }
        if (futuri.isNotEmpty()) {
            item { Titolo("Previsione prossimi mesi") }
            items(futuri, key = { "f" + it.mese }) { CardMeseFuturo(it) }
        }
        if (passati.isNotEmpty()) {
            item { Titolo("Mesi passati") }
            items(passati, key = { "p" + it.mese }) { CardMesePassato(it) }
        }
    }
}

@Composable
private fun Titolo(testo: String) {
    Text(testo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
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
        }
    }
}
