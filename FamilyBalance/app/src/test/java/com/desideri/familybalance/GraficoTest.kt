package com.desideri.familybalance

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
}
