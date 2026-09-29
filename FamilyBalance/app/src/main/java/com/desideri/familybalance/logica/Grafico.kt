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

/** Un valore da rappresentare: serie ([serie] = indice), giorno (epochDay) e importo. */
data class ValoreGrafico(val serie: Int, val data: Long, val valore: Double)

/** Un punto dell'asse X con un valore (o nessuno) per serie. */
data class PuntoGrafico(val etichetta: String, val valori: List<Double?>)

object Grafico {

    private val FORMATO_GIORNO = DateTimeFormatter.ofPattern("dd/MM/yy")
    private val FORMATO_MESE = DateTimeFormatter.ofPattern("MM/yy")

    /**
     * Punti del grafico per [numeroSerie] serie: con [Raggruppamento.VALORE] un punto per valore
     * (in ordine di data, valorizzato solo nella sua serie); per mese/anno la somma dei valori di
     * ogni serie nel periodo, con tutti i periodi tra il primo e l'ultimo (null dove una serie
     * non ha valori).
     */
    fun punti(valori: List<ValoreGrafico>, numeroSerie: Int, raggruppamento: Raggruppamento): List<PuntoGrafico> {
        if (valori.isEmpty()) return emptyList()
        val ordinati = valori.sortedBy { it.data }
        return when (raggruppamento) {
            Raggruppamento.VALORE -> ordinati.map { v ->
                PuntoGrafico(LocalDate.ofEpochDay(v.data).format(FORMATO_GIORNO), List(numeroSerie) { if (it == v.serie) v.valore else null })
            }
            Raggruppamento.MESE -> {
                val perMese = ordinati.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.data)) }
                val primo = perMese.keys.min()
                val ultimo = perMese.keys.max()
                generateSequence(primo) { it.plusMonths(1) }.takeWhile { it <= ultimo }.map { m ->
                    PuntoGrafico(m.format(FORMATO_MESE), somme(perMese[m].orEmpty(), numeroSerie))
                }.toList()
            }
            Raggruppamento.ANNO -> {
                val perAnno = ordinati.groupBy { LocalDate.ofEpochDay(it.data).year }
                (perAnno.keys.min()..perAnno.keys.max()).map { a ->
                    PuntoGrafico(a.toString(), somme(perAnno[a].orEmpty(), numeroSerie))
                }
            }
        }
    }

    private fun somme(valori: List<ValoreGrafico>, numeroSerie: Int): List<Double?> {
        val perSerie = valori.groupBy { it.serie }
        return List(numeroSerie) { s -> perSerie[s]?.sumOf { it.valore } }
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
