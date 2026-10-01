package com.desideri.familybalance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.desideri.familybalance.DatiApp
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.StatoColonneExcel
import com.desideri.familybalance.estratto.EstrattoExcel
import com.desideri.familybalance.logica.formattaCent

/** Lettera della colonna Excel (0 = A, 26 = AA). */
private fun lettera(indice: Int): String {
    var n = indice + 1
    val sb = StringBuilder()
    while (n > 0) {
        val r = (n - 1) % 26
        sb.insert(0, 'A' + r)
        n = (n - 1) / 26
    }
    return sb.toString()
}

/**
 * Scelta delle colonne di un estratto Excel per il riscontro: la riga d'intestazione (frecce) e,
 * tra le sue colonne, data, importo (o uscite, con una colonna separata per le entrate) e una o più
 * colonne di descrizione. Si parte dalla scelta memorizzata per il conto (o riconosciuta dai nomi);
 * l'anteprima mostra i primi movimenti letti.
 */
@Composable
fun ColonneExcelDialog(vm: SpeseViewModel, dati: DatiApp, stato: StatoColonneExcel) {
    val righe = stato.righe
    val valuta = dati.contiValutaPerId[stato.contoValutaId]?.valuta ?: "EUR"
    var colonne by remember(stato) { mutableStateOf(stato.proposta) }
    val intestazione = righe[colonne.rigaIntestazione].second
    val numeroColonne = righe.drop(colonne.rigaIntestazione).maxOf { it.second.size }.coerceAtLeast(1)
    val indici = (0 until numeroColonne).toList()
    fun nome(i: Int) = lettera(i) + intestazione.getOrNull(i)?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
    val esito = remember(colonne) { EstrattoExcel.leggi(righe, colonne, valuta) }

    AlertDialog(
        onDismissRequest = { vm.annullaColonneExcel() },
        title = { Text("Riscontro da Excel · ${dati.etichetta(stato.contoValutaId)}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Riga con i nomi delle colonne (i movimenti sono le righe sotto):", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { colonne = EstrattoExcel.proponiSuRiga(righe, colonne.rigaIntestazione - 1) },
                        enabled = colonne.rigaIntestazione > 0
                    ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Riga precedente") }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Riga ${righe[colonne.rigaIntestazione].first}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(
                            intestazione.filter { it.isNotBlank() }.joinToString(" | "),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { colonne = EstrattoExcel.proponiSuRiga(righe, colonne.rigaIntestazione + 1) },
                        enabled = colonne.rigaIntestazione < righe.lastIndex
                    ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Riga successiva") }
                }
                CampoScelta("Data", colonne.data, indici, ::nome, { colonne = colonne.copy(data = it) }, Modifier.fillMaxWidth())
                CampoScelta(
                    if (colonne.entrate == null) "Importo (con segno)" else "Uscite (addebiti)",
                    colonne.importo, indici, ::nome, { colonne = colonne.copy(importo = it) }, Modifier.fillMaxWidth()
                )
                CampoScelta(
                    "Entrate in una colonna separata",
                    colonne.entrate ?: -1, listOf(-1) + indici,
                    { if (it < 0) "No: l'importo ha il segno" else nome(it) },
                    { colonne = colonne.copy(entrate = it.takeIf { i -> i >= 0 }) },
                    Modifier.fillMaxWidth()
                )
                MenuMultiplo(
                    "Descrizione", indici, colonne.descrizioni, ::nome,
                    onCambia = { scelte -> colonne = colonne.copy(descrizioni = scelte.sorted()) },
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider()
                Text(
                    "Movimenti letti: ${esito.movimenti.size}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (esito.movimenti.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                esito.movimenti.take(4).forEach { m ->
                    Row {
                        Text(m.dataOperazione?.let { formattaDataIso(it) }.orEmpty(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.3f))
                        Text(m.descrizione, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.45f))
                        Text(formattaCent(m.importoCent, valuta), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.25f))
                    }
                }
                esito.avvisi.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                Text("La scelta viene ricordata per questo conto.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = { vm.confermaColonneExcel(colonne) }, enabled = esito.movimenti.isNotEmpty()) { Text("Riscontra") }
        },
        dismissButton = { TextButton(onClick = { vm.annullaColonneExcel() }) { Text("Annulla") } }
    )
}

private fun formattaDataIso(d: java.time.LocalDate): String = com.desideri.familybalance.logica.formattaData(d.toEpochDay())
