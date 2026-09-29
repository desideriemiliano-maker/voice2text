package com.desideri.familybalance.ui

import androidx.compose.foundation.clickable
import com.desideri.familybalance.logica.ValoreGrafico
import com.desideri.familybalance.logica.Aggregazione
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Icon
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
import com.desideri.familybalance.logica.Calcoli
import com.desideri.familybalance.logica.formattaData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContiScreen(vm: SpeseViewModel, onApriConto: (Long) -> Unit, onAnagraficaConti: () -> Unit) {
    val dati by vm.dati.collectAsStateWithLifecycle()
    val saldi by vm.saldi.collectAsStateWithLifecycle()
    val impostazioni by vm.impostazioni.collectAsStateWithLifecycle()
    val ricaricaInCorso by vm.ricaricaInCorso.collectAsStateWithLifecycle()
    val cambi by vm.cambi.collectAsStateWithLifecycle()
    var mostraGrafico by remember { mutableStateOf(false) }

    if (dati.caricati && dati.contiValuta.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Nessun conto configurato.", style = MaterialTheme.typography.titleMedium)
            Text(
                "Crea i conti dall'anagrafica oppure importa l'Excel dal menu ⋮ › Importa da Excel.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            Button(onClick = onAnagraficaConti) { Text("Anagrafica conti") }
        }
        return
    }

    val totaleEuro = dati.contiValuta.sumOf { Calcoli.inEuro(saldi[it.id] ?: 0L, it.valuta, impostazioni.cambioChfEur) }
    val ultimaPerConto = dati.operazioni.groupBy { it.contoValutaId }.mapValues { (_, ops) -> ops.maxOf { it.data } }

    // Trascinando verso il basso si rileggono i dati dal database.
    PullToRefreshBox(isRefreshing = ricaricaInCorso, onRefresh = vm::ricaricaDati, modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Saldo totale (in EUR)", style = MaterialTheme.typography.labelLarge)
                        TestoImporto(totaleEuro, grassetto = true)
                        if (dati.contiValuta.any { it.valuta != "EUR" }) {
                            Text(
                                "CHF convertiti a ${impostazioni.cambioChfEur} (Impostazioni)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    IconButton(onClick = { mostraGrafico = true }, enabled = dati.operazioni.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Grafico del saldo")
                    }
                    }
                }
            }
            items(dati.contiValutaOrdinati, key = { it.id }) { cv ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onApriConto(cv.id) }) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(dati.etichetta(cv.id), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            ultimaPerConto[cv.id]?.let {
                                Text("Ultima operazione: ${formattaData(it)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        TestoImporto((saldi[cv.id] ?: 0L) / 100.0, cv.valuta, grassetto = true)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }
    }

    if (mostraGrafico) {
        // Saldo di ogni conto/valuta e totale, in EUR al cambio del mese, a ogni giorno con movimenti.
        val colonne = dati.contiValutaOrdinati
        val serie = listOf(SerieGrafico("Totale", coloreSerie(0, null))) +
            colonne.mapIndexed { i, cv -> SerieGrafico(dati.etichetta(cv.id), coloreSerie(i + 1, null)) }
        val valori = remember(dati.operazioni, cambi) {
            val saldiCent = colonne.associate { it.id to it.saldoInizialeCent }.toMutableMap()
            val indice = colonne.withIndex().associate { (i, cv) -> cv.id to i + 1 }
            buildList {
                dati.operazioni.groupBy { it.data }.toSortedMap().forEach { (giorno, ops) ->
                    ops.forEach { op -> saldiCent[op.contoValutaId]?.let { saldiCent[op.contoValutaId] = it + op.importoCent } }
                    val mese = Calcoli.mese(giorno)
                    var totale = 0.0
                    colonne.forEach { cv ->
                        val eur = cambi.inEuro(saldiCent.getValue(cv.id), cv.valuta, mese)
                        totale += eur
                        add(ValoreGrafico(indice.getValue(cv.id), giorno, eur))
                    }
                    add(ValoreGrafico(0, giorno, totale))
                }
            }
        }
        GraficoSpeseDialog(
            titolo = "Saldo",
            nota = "Saldo totale e dei singoli conti a fine giorno/mese/anno, in EUR (CHF al cambio del mese).",
            serie = serie,
            valori = valori,
            aggregazione = Aggregazione.ULTIMO,
            onChiudi = { mostraGrafico = false }
        )
    }
}
