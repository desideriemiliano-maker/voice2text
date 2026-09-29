package com.desideri.familybalance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.desideri.familybalance.logica.Aggregazione
import com.desideri.familybalance.logica.Grafico
import com.desideri.familybalance.logica.Raggruppamento
import com.desideri.familybalance.logica.TipoGrafico
import com.desideri.familybalance.logica.ValoreGrafico

/** Valore da mostrare per un conto: il saldo (ultimo del periodo) o le entrate/uscite (somma del periodo). */
enum class Misura(val etichetta: String, val aggregazione: Aggregazione) {
    SALDO("Saldo", Aggregazione.ULTIMO),
    ENTRATE("Entrate", Aggregazione.SOMMA),
    USCITE("Uscite", Aggregazione.SOMMA)
}

/**
 * Un conto (o il totale) per il grafico: [saldi] a fine giorno, [entrate] e [uscite] (positive)
 * delle singole operazioni, tutti come (giorno, importo) nella valuta del grafico.
 */
data class ContoGrafico(
    val nome: String,
    val saldi: List<Pair<Long, Double>>,
    val entrate: List<Pair<Long, Double>>,
    val uscite: List<Pair<Long, Double>>
) {
    fun valori(m: Misura) = when (m) {
        Misura.SALDO -> saldi
        Misura.ENTRATE -> entrate
        Misura.USCITE -> uscite
    }
}

/**
 * Grafico a schermo intero dell'andamento dei conti: si scelgono (scelta multipla) i valori da
 * mostrare tra saldo, entrate e uscite e i conti (compreso il totale), il tipo di grafico e il
 * raggruppamento per valore (giorno), settimana, mese o anno; il saldo è quello a fine periodo,
 * entrate e uscite la somma del periodo.
 */
@Composable
fun GraficoContiDialog(titolo: String, nota: String, conti: List<ContoGrafico>, valuta: String = "EUR", onChiudi: () -> Unit) {
    var misure by rememberSaveable { mutableStateOf(listOf(Misura.SALDO)) }
    var contiScelti by rememberSaveable { mutableStateOf(conti.indices.toList()) }
    var tipo by rememberSaveable { mutableStateOf(TipoGrafico.LINEA) }
    var raggruppamento by rememberSaveable { mutableStateOf(Raggruppamento.MESE) }
    var tendenza by rememberSaveable { mutableStateOf(false) }

    // Una serie per conto e valore scelti.
    val combinazioni = contiScelti.sorted().flatMap { c -> Misura.entries.filter { it in misure }.map { m -> c to m } }
    val serie = combinazioni.mapIndexed { i, (c, m) ->
        val nome = when {
            misure.size == 1 -> conti[c].nome
            conti.size == 1 -> m.etichetta
            else -> "${conti[c].nome} · ${m.etichetta}"
        }
        SerieGrafico(nome, coloreSerie(i, null))
    }
    val punti = remember(conti, combinazioni, raggruppamento) {
        val valori = combinazioni.flatMapIndexed { i, (c, m) -> conti[c].valori(m).map { (giorno, v) -> ValoreGrafico(i, giorno, v) } }
        Grafico.puntiPerPeriodo(valori, combinazioni.map { it.second.aggregazione }, raggruppamento)
    }

    Dialog(onDismissRequest = onChiudi, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize(), tonalElevation = 4.dp) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(titolo, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onChiudi) { Icon(Icons.Filled.Close, contentDescription = "Chiudi") }
                }
                Text(nota, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    MenuMultiplo(
                        "Valori", Misura.entries, misure, { it.etichetta },
                        onCambia = { misure = it.ifEmpty { listOf(Misura.SALDO) } },
                        modifier = Modifier.weight(1f)
                    )
                    if (conti.size > 1) {
                        MenuMultiplo(
                            "Conti", conti.indices.toList(), contiScelti, { conti[it].nome },
                            onCambia = { contiScelti = it.ifEmpty { listOf(conti.lastIndex) } },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp)
                ) {
                    TipoGrafico.entries.forEach { t -> FilterChip(selected = tipo == t, onClick = { tipo = t }, label = { Text(t.etichetta) }) }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    Text("Per", style = MaterialTheme.typography.labelMedium)
                    Raggruppamento.entries.forEach { r -> FilterChip(selected = raggruppamento == r, onClick = { raggruppamento = r }, label = { Text(r.etichetta) }) }
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
                GraficoSerie(punti, serie, tipo, tendenza, valuta, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

/** Menu a tendina a scelta multipla: le voci restano aperte mentre si spuntano. */
@Composable
internal fun <T> MenuMultiplo(
    etichetta: String,
    opzioni: List<T>,
    selezionate: List<T>,
    testo: (T) -> String,
    onCambia: (List<T>) -> Unit,
    modifier: Modifier = Modifier
) {
    var aperto by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = opzioni.filter { it in selezionate }.joinToString(", ", transform = testo),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(etichetta) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(Modifier.matchParentSize().clickable { aperto = true })
        DropdownMenu(expanded = aperto, onDismissRequest = { aperto = false }) {
            opzioni.forEach { o ->
                val scelta = o in selezionate
                DropdownMenuItem(
                    text = { Text(testo(o)) },
                    leadingIcon = { Checkbox(checked = scelta, onCheckedChange = null) },
                    onClick = { onCambia(if (scelta) selezionate - o else selezionate + o) }
                )
            }
        }
    }
}
