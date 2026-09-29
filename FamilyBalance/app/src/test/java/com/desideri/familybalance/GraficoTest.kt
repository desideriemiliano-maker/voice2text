package com.desideri.familybalance

import com.desideri.familybalance.logica.Aggregazione
import com.desideri.familybalance.logica.Grafico
import com.desideri.familybalance.logica.Raggruppamento
import com.desideri.familybalance.logica.ValoreGrafico
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class GraficoTest {

    private fun giorno(a: Int, m: Int, g: Int) = LocalDate.of(a, m, g).toEpochDay()

    private val valori = listOf(
        ValoreGrafico(0, giorno(2026, 1, 10), 10.0),
        ValoreGrafico(1, giorno(2026, 1, 20), 5.0),
        ValoreGrafico(0, giorno(2026, 3, 5), 7.0),
        ValoreGrafico(0, giorno(2027, 2, 1), 1.0)
    )

    @Test
    fun perValore_unPuntoPerValore() {
        val punti = Grafico.punti(valori, 2, Raggruppamento.VALORE)
        assertEquals(4, punti.size)
        assertEquals(listOf(null, 5.0), punti[1].valori)
    }

    @Test
    fun perMese_sommeConMesiIntermedi() {
        val punti = Grafico.punti(valori, 2, Raggruppamento.MESE)
        assertEquals(14, punti.size)
        assertEquals(listOf(10.0, 5.0), punti[0].valori)
        assertEquals(listOf(null, null), punti[1].valori)
        assertEquals("03/26", punti[2].etichetta)
    }

    @Test
    fun perAnno_eRegressione() {
        val punti = Grafico.punti(valori, 2, Raggruppamento.ANNO)
        assertEquals(listOf(17.0, 5.0), punti[0].valori)
        assertEquals(listOf(1.0, null), punti[1].valori)
        val (pendenza, intercetta) = Grafico.regressione(listOf(0 to 1.0, 1 to 3.0, 2 to 5.0))
        assertEquals(2.0, pendenza, 1e-9)
        assertEquals(1.0, intercetta, 1e-9)
    }

    @Test
    fun ultimo_riportaIlSaldoPrecedente() {
        val saldi = listOf(
            ValoreGrafico(0, giorno(2026, 1, 5), 100.0),
            ValoreGrafico(0, giorno(2026, 1, 25), 80.0),
            ValoreGrafico(0, giorno(2026, 3, 2), 150.0)
        )
        val punti = Grafico.punti(saldi, 1, Raggruppamento.MESE, Aggregazione.ULTIMO)
        assertEquals(listOf(80.0, 80.0, 150.0), punti.map { it.valori[0] })
        assertEquals(3, Grafico.punti(saldi, 1, Raggruppamento.VALORE, Aggregazione.ULTIMO).size)
    }

    @Test
    fun perSettimana_saldoUltimoEUsciteSommate() {
        // Lunedì 5/1/2026 e giovedì 8/1 nella stessa settimana; lunedì 19/1 due settimane dopo.
        val v = listOf(
            ValoreGrafico(0, giorno(2026, 1, 5), 100.0), ValoreGrafico(1, giorno(2026, 1, 5), 10.0),
            ValoreGrafico(0, giorno(2026, 1, 8), 90.0), ValoreGrafico(1, giorno(2026, 1, 8), 10.0),
            ValoreGrafico(0, giorno(2026, 1, 19), 70.0), ValoreGrafico(1, giorno(2026, 1, 19), 20.0)
        )
        val punti = Grafico.puntiPerPeriodo(v, listOf(Aggregazione.ULTIMO, Aggregazione.SOMMA), Raggruppamento.SETTIMANA)
        assertEquals(3, punti.size)
        assertEquals(listOf(90.0, 20.0), punti[0].valori)
        assertEquals(listOf(90.0, null), punti[1].valori)
        assertEquals(listOf(70.0, 20.0), punti[2].valori)
    }
}
