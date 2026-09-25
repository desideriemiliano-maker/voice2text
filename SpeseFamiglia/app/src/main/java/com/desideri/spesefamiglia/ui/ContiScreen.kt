package com.desideri.spesefamiglia.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.desideri.spesefamiglia.SpeseViewModel
import com.desideri.spesefamiglia.logica.Calcoli
import com.desideri.spesefamiglia.logica.formattaData

@Composable
fun ContiScreen(vm: SpeseViewModel, onApriConto: (Long) -> Unit, onAnagraficaConti: () -> Unit) {
    val dati by vm.dati.collectAsStateWithLifecycle()
    val saldi by vm.saldi.collectAsStateWithLifecycle()
    val impostazioni by vm.impostazioni.collectAsStateWithLifecycle()

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

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Saldo totale (in EUR)", style = MaterialTheme.typography.labelLarge)
                    TestoImporto(totaleEuro, grassetto = true)
                    if (dati.contiValuta.any { it.valuta != "EUR" }) {
                        Text(
                            "CHF convertiti a ${impostazioni.cambioChfEur} (Impostazioni)",
                            style = MaterialTheme.typography.bodySmall
                        )
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
