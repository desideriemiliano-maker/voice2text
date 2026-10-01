package com.desideri.familybalance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.desideri.familybalance.logica.PuntoGrafico
import com.desideri.familybalance.logica.RigaBilancio
import com.desideri.familybalance.logica.StatoMese
import com.desideri.familybalance.logica.TipoGrafico
import java.time.format.DateTimeFormatter

/** Voci del bilancio che si possono mostrare nel grafico (importi EUR con segno). */
private enum class VoceBilancio(val etichetta: String, val valore: (RigaBilancio) -> Double?) {
    SALDO("Saldo totale", { r -> if (r.stato == StatoMese.PASSATO) r.saldoFine else r.saldoPrevisto ?: r.saldoFine }),
    ENTRATE("Stipendio / interessi", { it.entrate }),
    CORRENTI("Spese correnti", { it.correnti }),
    RICORRENTI("Spese ricorrenti", { it.ricorrentiTotali }),
    RISPARMIO("Risparmio", { it.risparmio }),
    DELTA("Rispetto al target", { it.deltaTarget }),
    CAMBIO("Effetto cambio", { it.effettoCambio })
}

private val FORMATO_MESE = DateTimeFormatter.ofPattern("MM/yy")

/**
 * Grafico dell'evoluzione del bilancio nel periodo scelto: il saldo totale (a fine mese per i
 * passati, previsto per corrente e futuri) e le singole voci, a scelta multipla, un punto per mese.
 */
@Composable
fun GraficoBilancioDialog(righe: List<RigaBilancio>, onChiudi: () -> Unit) {
    var scelte by rememberSaveable { mutableStateOf(listOf(VoceBilancio.SALDO)) }
    var tipo by rememberSaveable { mutableStateOf(TipoGrafico.LINEA) }
    var tendenza by rememberSaveable { mutableStateOf(false) }

    val voci = VoceBilancio.entries.filter { it in scelte }
    val serie = voci.map { SerieGrafico(it.etichetta, coloreSerie(it.ordinal, null)) }
    val punti = remember(righe, voci) {
        righe.map { r -> PuntoGrafico(r.mese.format(FORMATO_MESE), voci.map { it.valore(r) }) }
    }

    Dialog(onDismissRequest = onChiudi, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize(), tonalElevation = 4.dp) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Andamento del bilancio", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onChiudi) { Icon(Icons.Filled.Close, contentDescription = "Chiudi") }
                }
                Text(
                    "Un punto per mese del periodo scelto, in EUR. Saldo: a fine mese per i mesi passati, previsto per il corrente e i futuri.",
                    style = MaterialTheme.typography.bodySmall
                )
                MenuMultiplo(
                    "Voci", VoceBilancio.entries, scelte, { it.etichetta },
                    onCambia = { scelte = it.ifEmpty { listOf(VoceBilancio.SALDO) } },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    conTutti = true
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp)
                ) {
                    TipoGrafico.entries.forEach { t -> FilterChip(selected = tipo == t, onClick = { tipo = t }, label = { Text(t.etichetta) }) }
                    Spacer(Modifier.width(8.dp))
                    Text("Tendenza", style = MaterialTheme.typography.labelMedium)
                    Switch(checked = tendenza, onCheckedChange = { tendenza = it })
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)
                ) {
                    serie.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).background(s.colore, CircleShape))
                            Spacer(Modifier.width(4.dp))
                            Text(s.nome, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                GraficoSerie(punti, serie, tipo, tendenza, "EUR", Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}
