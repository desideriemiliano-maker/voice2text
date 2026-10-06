package com.desideri.familybalance.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.desideri.familybalance.DatiApp
import com.desideri.familybalance.data.Operazione
import com.desideri.familybalance.data.dataPerRicorrente
import com.desideri.familybalance.logica.Calcoli
import com.desideri.familybalance.logica.Cambi
import com.desideri.familybalance.logica.Classe
import com.desideri.familybalance.logica.PuntoGrafico
import com.desideri.familybalance.logica.RigaRicorrente
import com.desideri.familybalance.logica.TipoGrafico
import com.desideri.familybalance.logica.formattaMese
import java.time.LocalDate
import java.time.YearMonth

/** Una serie del grafico del mese: nome e importi (positivi = spesa) per giorno; [finoAl] limita il tracciato. */
data class SerieGiorni(val nome: String, val perGiorno: Map<Int, Double>, val finoAl: Int? = null)

/**
 * Grafico di un mese: sulle X i giorni, sulle Y il cumulato delle spese di ogni serie. Si tocca il
 * tracciato per i valori del giorno e si apre a schermo pieno (come gli altri grafici).
 */
@Composable
fun GraficoMeseDialog(titolo: String, nota: String, mese: YearMonth, serie: List<SerieGiorni>, onChiudi: () -> Unit) {
    val giorni = 1..mese.lengthOfMonth()
    val cumulati = serie.map { s ->
        var somma = 0.0
        giorni.map { g ->
            somma += s.perGiorno[g] ?: 0.0
            if (s.finoAl != null && g > s.finoAl) null else somma
        }
    }
    val punti = giorni.mapIndexed { i, g -> PuntoGrafico(g.toString(), cumulati.map { it[i] }) }
    val serieGrafico = serie.mapIndexed { i, s -> SerieGrafico(s.nome, coloreSerie(i, null)) }
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text("$titolo · ${formattaMese(mese)}") },
        text = {
            Column {
                Text(nota, style = MaterialTheme.typography.bodySmall)
                serie.forEachIndexed { i, s ->
                    Text("● ${s.nome}: ${com.desideri.familybalance.logica.formattaImporto(cumulati[i].lastOrNull { it != null } ?: 0.0)}",
                        style = MaterialTheme.typography.labelMedium, color = serieGrafico[i].colore)
                }
                GraficoSerie(punti, serieGrafico, TipoGrafico.LINEA, false, "EUR", Modifier.fillMaxWidth().height(320.dp))
            }
        },
        confirmButton = { TextButton(onClick = onChiudi) { Text("Chiudi") } }
    )
}

private fun eur(dati: DatiApp, cambi: Cambi, op: Operazione, mese: YearMonth) =
    cambi.inEuro(op.importoCent, dati.contiValutaPerId[op.contoValutaId]?.valuta ?: "EUR", mese)

/** Fino a che giorno disegnare le spese effettive: oggi per il mese corrente, tutto il mese altrimenti. */
private fun finoAOggi(mese: YearMonth): Int? = if (mese == YearMonth.now()) LocalDate.now().dayOfMonth else null

/**
 * Serie delle spese ricorrenti del mese: le pagate (operazioni delle spese di [righe] imputate al
 * mese, nel giorno della data per la spesa ricorrente) e il totale con le ancora previste, nel
 * giorno della data prevista (ultimo giorno se n.d.).
 */
fun serieRicorrenti(dati: DatiApp, cambi: Cambi, mese: YearMonth, righe: List<RigaRicorrente>): List<SerieGiorni> {
    val voci = righe.map { it.voce.id }.toSet()
    val pagate = HashMap<Int, Double>()
    dati.operazioni.filter { !it.trasferimento && it.voceId in voci && Calcoli.mese(it.dataPerRicorrente) == mese }.forEach { op ->
        val g = LocalDate.ofEpochDay(op.dataPerRicorrente).dayOfMonth
        pagate[g] = (pagate[g] ?: 0.0) - eur(dati, cambi, op, mese)
    }
    val conPreviste = HashMap(pagate)
    righe.filter { it.previsto != null && !it.annullata }.forEach { r ->
        val g = r.dataStimata?.let { LocalDate.ofEpochDay(it).dayOfMonth } ?: mese.lengthOfMonth()
        conPreviste[g] = (conPreviste[g] ?: 0.0) - (r.previsto ?: 0.0)
    }
    return listOf(SerieGiorni("Pagate", pagate, finoAOggi(mese)), SerieGiorni("Con le previste", conPreviste))
}

/** Serie delle spese correnti del mese (eventualmente solo delle voci [voci]; 0 = senza tipo). */
fun serieCorrenti(dati: DatiApp, cambi: Cambi, mese: YearMonth, voci: Set<Long>? = null): List<SerieGiorni> {
    val perGiorno = HashMap<Int, Double>()
    dati.operazioni.filter { op ->
        val voce = op.voceId?.let { dati.vociPerId[it] }
        Calcoli.mese(op.data) == mese && Calcoli.classifica(op, voce) == Classe.CORRENTE && (voci == null || (voce?.id ?: 0L) in voci)
    }.forEach { op ->
        val g = LocalDate.ofEpochDay(op.data).dayOfMonth
        perGiorno[g] = (perGiorno[g] ?: 0.0) - eur(dati, cambi, op, mese)
    }
    return listOf(SerieGiorni("Spese correnti", perGiorno, finoAOggi(mese)))
}
