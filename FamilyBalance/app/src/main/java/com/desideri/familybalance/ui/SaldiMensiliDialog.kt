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
import java.time.YearMonth

/** Un mese della tabella dei saldi (centesimi nella valuta del conto). */
private data class SaldoMese(val mese: YearMonth, val iniziale: Long, val entrate: Long, val uscite: Long) {
    val finale: Long get() = iniziale + entrate + uscite
}

/**
 * Saldi mese per mese di un conto/valuta: saldo precedente, totale entrate, totale uscite (tutte le
 * operazioni, spostamenti compresi) e saldo finale, dal primo mese con operazioni al corrente.
 */
@Composable
fun SaldiMensiliDialog(dati: DatiApp, contoValutaId: Long, onChiudi: () -> Unit) {
    val cv = dati.contiValutaPerId[contoValutaId] ?: return
    val mesi: List<SaldoMese> = remember(dati.operazioni, contoValutaId) {
        val ops = dati.operazioni.filter { it.contoValutaId == contoValutaId }
        if (ops.isEmpty()) return@remember emptyList<SaldoMese>()
        val perMese = ops.groupBy { Calcoli.mese(it.data) }
        val ultimo = maxOf(YearMonth.now(), perMese.keys.max())
        var saldo = cv.saldoInizialeCent
        generateSequence(perMese.keys.min()) { it.plusMonths(1) }.takeWhile { it <= ultimo }.map { m ->
            val del = perMese[m].orEmpty()
            SaldoMese(m, saldo, del.filter { it.importoCent > 0 }.sumOf { it.importoCent }, del.filter { it.importoCent < 0 }.sumOf { it.importoCent })
                .also { saldo = it.finale }
        }.toList()
    }
    val stato = rememberLazyListState()
    var mostraGrafico by remember { mutableStateOf(false) }
    LaunchedEffect(mesi.size) { if (mesi.isNotEmpty()) stato.scrollToItem(mesi.lastIndex) }
    val zebra = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)

    Dialog(onDismissRequest = onChiudi, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize(), tonalElevation = 4.dp) {
            Column(modifier = Modifier.fillMaxSize().paddingBarreDialog().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Saldi mensili · ${dati.etichetta(contoValutaId)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { mostraGrafico = true }, enabled = mesi.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Grafico dei saldi")
                    }
                    IconButton(onClick = onChiudi) { Icon(Icons.Filled.Close, contentDescription = "Chiudi") }
                }
                Text(
                    "Tutte le operazioni del conto, spostamenti compresi, in ${cv.valuta}.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Riga("Mese", "Saldo prec.", "Entrate", "Uscite", "Saldo fine", intestazione = true)
                HorizontalDivider()
                if (mesi.isEmpty()) Text("Nessuna operazione sul conto.", modifier = Modifier.padding(16.dp))
                LazyColumn(state = stato, modifier = Modifier.weight(1f)) {
                    itemsIndexed(mesi, key = { _, m -> m.mese.toString() }) { i, m ->
                        Riga(
                            formattaMeseBreve(m.mese),
                            formattaCent(m.iniziale, cv.valuta),
                            formattaCent(m.entrate, cv.valuta),
                            formattaCent(m.uscite, cv.valuta),
                            formattaCent(m.finale, cv.valuta),
                            sfondo = if (i % 2 == 1) zebra else Color.Transparent,
                            evidenzia = m.mese == YearMonth.now()
                        )
                    }
                }
            }
        }
    }

    if (mostraGrafico) {
        // Saldo a fine giorno, entrate e uscite (positive) delle singole operazioni, nella valuta del conto.
        val conto = remember(dati.operazioni, contoValutaId) {
            val ops = dati.operazioni.filter { it.contoValutaId == contoValutaId }.sortedBy { it.data }
            var saldo = cv.saldoInizialeCent
            val saldi = ops.groupBy { it.data }.toSortedMap().map { (giorno, del) ->
                saldo += del.sumOf { it.importoCent }
                giorno to saldo / 100.0
            }
            ContoGrafico(
                dati.etichetta(contoValutaId), saldi,
                ops.filter { it.importoCent > 0 }.map { it.data to it.importoCent / 100.0 },
                ops.filter { it.importoCent < 0 }.map { it.data to -it.importoCent / 100.0 }
            )
        }
        GraficoContiDialog(
            titolo = "Saldi · ${dati.etichetta(contoValutaId)}",
            nota = "Tutte le operazioni del conto, spostamenti compresi, in ${cv.valuta}. Saldo a fine periodo, entrate e uscite sommate nel periodo.",
            conti = listOf(conto),
            valuta = cv.valuta,
            onChiudi = { mostraGrafico = false }
        )
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
