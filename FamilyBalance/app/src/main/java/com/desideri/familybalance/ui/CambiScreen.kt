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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.logica.CambioMese
import com.desideri.familybalance.logica.Calcoli
import com.desideri.familybalance.logica.FonteCambio
import com.desideri.familybalance.logica.formattaMese
import java.time.YearMonth
import java.util.Locale

private fun formattaCambio(x: Double): String = String.format(Locale.ITALY, "%.4f", x)

/**
 * Cambi CHF/EUR mensili (menu ⋮): per ogni mese passato il cambio usato nel bilancio e da dove
 * viene; toccando un mese lo si imposta a mano (o si torna a quello calcolato).
 */
@Composable
fun CambiScreen(vm: SpeseViewModel, onIndietro: () -> Unit) {
    val dati by vm.dati.collectAsStateWithLifecycle()
    val cambi by vm.cambi.collectAsStateWithLifecycle()
    val impostazioni by vm.impostazioni.collectAsStateWithLifecycle()
    var inModifica by remember { mutableStateOf<CambioMese?>(null) }

    val oggi = YearMonth.now()
    val primo = dati.operazioni.minOfOrNull { it.data }?.let { Calcoli.mese(it) } ?: oggi
    val mesi = generateSequence(oggi.minusMonths(1)) { it.minusMonths(1) }.takeWhile { it >= primo }.map { cambi.cambioMese(it) }.toList()

    Scaffold(topBar = { BarraIndietro("Cambi CHF/EUR", onIndietro) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Cambio attuale: 1 CHF = ${formattaCambio(impostazioni.cambioChfEur)} EUR", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Usato per il mese corrente e i futuri; si cambia in Impostazioni. Per i mesi passati vale il cambio " +
                                "inserito qui, altrimenti la media degli spostamenti CHF↔EUR collegati del mese, altrimenti quello " +
                                "del mese più vicino. Nel Bilancio i saldi CHF a fine mese sono valutati al cambio del mese e la " +
                                "differenza dovuta al cambio è indicata come \"Effetto cambio\".",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            if (mesi.isEmpty()) {
                item { Text("Nessun mese passato con operazioni.", style = MaterialTheme.typography.bodyMedium) }
            }
            items(mesi, key = { it.mese.toString() }) { c ->
                Column(modifier = Modifier.fillMaxWidth().clickable { inModifica = c }.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formattaMese(c.mese), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            formattaCambio(c.chfEur),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (c.fonte == FonteCambio.INSERITO) FontWeight.Bold else null
                        )
                    }
                    Text(
                        c.fonte.descrizione,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (c.fonte == FonteCambio.INSERITO) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider()
            }
        }
    }

    inModifica?.let { c ->
        var testo by remember(c) { mutableStateOf(formattaCambio(c.chfEur)) }
        var errore by remember(c) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { inModifica = null },
            title = { Text("Cambio di ${formattaMese(c.mese)}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Attuale: ${formattaCambio(c.chfEur)} (${c.fonte.descrizione})", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = testo,
                        onValueChange = { testo = it; errore = false },
                        label = { Text("1 CHF = … EUR") },
                        singleLine = true,
                        isError = errore,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val valore = testo.trim().replace(',', '.').toDoubleOrNull()
                    if (valore == null || valore <= 0) {
                        errore = true
                    } else {
                        vm.salvaCambio(c.mese, valore)
                        inModifica = null
                    }
                }) { Text("Salva") }
            },
            dismissButton = {
                Row {
                    if (c.fonte == FonteCambio.INSERITO) {
                        TextButton(onClick = {
                            vm.salvaCambio(c.mese, null)
                            inModifica = null
                        }) { Text("Usa calcolato") }
                    }
                    TextButton(onClick = { inModifica = null }) { Text("Annulla") }
                }
            }
        )
    }
}
