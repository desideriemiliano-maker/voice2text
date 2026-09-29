package com.desideri.familybalance.logica

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Tipi di grafico (come in WorkoutAnalyzer). */
enum class TipoGrafico(val etichetta: String) {
    LINEA("Linea"),
    AREA("Area"),
    ISTOGRAMMA("Istogramma"),
    PUNTI("Punti")
}

/** Come raggruppare i valori: uno per operazione, somma per mese o per anno. */
enum class Raggruppamento(val etichetta: String) {
    VALORE("Valore"),
    MESE("Mese"),
    ANNO("Anno")
}

/** Come combinare i valori di uno stesso periodo: somma (movimenti) o ultimo (saldi). */
enum class Aggregazione { SOMMA, ULTIMO }

/** Un valore da rappresentare: serie ([serie] = indice), giorno (epochDay) e importo. */
data class ValoreGrafico(val serie: Int, val data: Long, val valore: Double)

/** Un punto dell'asse X con un valore (o nessuno) per serie. */
data class PuntoGrafico(val etichetta: String, val valori: List<Double?>)

object Grafico {

    private val FORMATO_GIORNO = DateTimeFormatter.ofPattern("dd/MM/yy")
    private val FORMATO_MESE = DateTimeFormatter.ofPattern("MM/yy")

    /**
     * Punti del grafico per [numeroSerie] serie. Con [Aggregazione.SOMMA] (movimenti): per
     * [Raggruppamento.VALORE] un punto per valore (valorizzato solo nella sua serie), per mese/anno
     * la somma di ogni serie nel periodo (null se non ha valori). Con [Aggregazione.ULTIMO] (saldi):
     * un punto per giorno, mese o anno con l'ultimo valore di ogni serie nel periodo, riportando il
     * precedente dove una serie non ne ha. Per mese/anno ci sono tutti i periodi tra il primo e l'ultimo.
     */
    fun punti(
        valori: List<ValoreGrafico>,
        numeroSerie: Int,
        raggruppamento: Raggruppamento,
        aggregazione: Aggregazione = Aggregazione.SOMMA
    ): List<PuntoGrafico> {
        if (valori.isEmpty()) return emptyList()
        val ordinati = valori.sortedBy { it.data }
        if (raggruppamento == Raggruppamento.VALORE && aggregazione == Aggregazione.SOMMA) {
            return ordinati.map { v ->
                PuntoGrafico(LocalDate.ofEpochDay(v.data).format(FORMATO_GIORNO), List(numeroSerie) { if (it == v.serie) v.valore else null })
            }
        }
        // Periodi (chiave ordinabile ed etichetta) in cui raggruppare.
        val periodi: List<Pair<Long, String>> = when (raggruppamento) {
            Raggruppamento.VALORE -> ordinati.map { it.data }.distinct().map { it to LocalDate.ofEpochDay(it).format(FORMATO_GIORNO) }
            Raggruppamento.MESE -> {
                val mesi = ordinati.map { YearMonth.from(LocalDate.ofEpochDay(it.data)) }
                generateSequence(mesi.first()) { it.plusMonths(1) }.takeWhile { it <= mesi.last() }
                    .map { (it.year * 12L + it.monthValue) to it.format(FORMATO_MESE) }.toList()
            }
            Raggruppamento.ANNO -> {
                val anni = ordinati.map { LocalDate.ofEpochDay(it.data).year }
                (anni.first()..anni.last()).map { it.toLong() to it.toString() }
            }
        }
        fun chiave(v: ValoreGrafico): Long {
            val d = LocalDate.ofEpochDay(v.data)
            return when (raggruppamento) {
                Raggruppamento.VALORE -> v.data
                Raggruppamento.MESE -> d.year * 12L + d.monthValue
                Raggruppamento.ANNO -> d.year.toLong()
            }
        }
        val perPeriodo = ordinati.groupBy(::chiave)
        val precedenti = arrayOfNulls<Double>(numeroSerie)
        return periodi.map { (k, etichetta) ->
            val gruppo = perPeriodo[k].orEmpty().groupBy { it.serie }
            val valoriPunto = List(numeroSerie) { s ->
                val lista = gruppo[s]
                when (aggregazione) {
                    Aggregazione.SOMMA -> lista?.sumOf { it.valore }
                    Aggregazione.ULTIMO -> (lista?.last()?.valore ?: precedenti[s]).also { precedenti[s] = it }
                }
            }
            PuntoGrafico(etichetta, valoriPunto)
        }
    }

    /** Retta di regressione (pendenza, intercetta) sui punti (indice, valore); piatta se non calcolabile. */
    fun regressione(punti: List<Pair<Int, Double>>): Pair<Double, Double> {
        if (punti.isEmpty()) return 0.0 to 0.0
        if (punti.size == 1) return 0.0 to punti[0].second
        val n = punti.size
        val mediaX = punti.sumOf { it.first.toDouble() } / n
        val mediaY = punti.sumOf { it.second } / n
        val varX = punti.sumOf { (it.first - mediaX) * (it.first - mediaX) }
        if (varX == 0.0) return 0.0 to mediaY
        val pendenza = punti.sumOf { (it.first - mediaX) * (it.second - mediaY) } / varX
        return pendenza to (mediaY - pendenza * mediaX)
    }

    /** Fino a 5 indici distribuiti uniformemente (primo e ultimo compresi) per le etichette dell'asse X. */
    fun indiciEtichette(numeroPunti: Int): List<Int> {
        if (numeroPunti <= 1) return listOf(0)
        val numero = minOf(numeroPunti, 5)
        return (0 until numero).map { i -> i * (numeroPunti - 1) / (numero - 1) }.distinct()
    }
}
