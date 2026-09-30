package com.desideri.familybalance

import com.desideri.familybalance.estratto.EstrattoExcel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class EstrattoExcelTest {

    private fun righe(vararg r: List<String>) = r.mapIndexed { i, celle -> (i + 1) to celle }

    @Test
    fun leggeEstrattoConIntestazioneEDivisaSopra() {
        val esito = EstrattoExcel.leggi(
            righe(
                listOf("", "C/C:", "12345"),
                listOf("", "Divisa C/C:", "EUR"),
                listOf("", "Saldo Contabile al:", "30/09/2026", "609.68"),
                listOf("", "Data contabile", "Data valuta", "Descrizione", "Dettaglio", "Importo"),
                listOf("", "Non contabilizzato", "30/9/2026", "Negozio", "Pagamento Con Carta Di Debito", "-122.8"),
                listOf("", "30/9/2026", "28/9/2026", "Rimborso", "Rimborso", "-723.39"),
                listOf("", "29/9/2026", "29/9/2026", "Stipendio", "Bonifico", "2500")
            ),
            "EUR"
        )!!
        assertEquals(3, esito.movimenti.size)
        val primo = esito.movimenti[0]
        assertEquals(-12280L, primo.importoCent)
        assertNull(primo.dataContabile)
        assertEquals(LocalDate.of(2026, 9, 30), primo.dataValuta)
        assertEquals("Negozio · Pagamento Con Carta Di Debito", primo.descrizione)
        assertEquals("Rimborso", esito.movimenti[1].descrizione)
        assertEquals(LocalDate.of(2026, 9, 30), esito.movimenti[1].dataContabile)
        assertEquals(250000L, esito.movimenti[2].importoCent)
        assertEquals(6, primo.rigaFile)
        assertEquals(emptyList<String>(), esito.avvisi)
    }

    @Test
    fun leggeColonneAddebitiAccrediti() {
        val esito = EstrattoExcel.leggi(
            righe(
                listOf("Data operazione", "Causale", "Addebiti", "Accrediti"),
                listOf("2026-03-01", "Affitto", "1.200,00", ""),
                listOf("02.03.26", "Rimborso", "", "35,5")
            ),
            "CHF"
        )!!
        assertEquals(listOf(-120000L, 3550L), esito.movimenti.map { it.importoCent })
        assertEquals(LocalDate.of(2026, 3, 2), esito.movimenti[1].dataOperazione)
        assertEquals("CHF", esito.movimenti[0].valuta)
    }

    @Test
    fun senzaIntestazioneRestituisceNull() {
        assertNull(EstrattoExcel.leggi(righe(listOf("a", "b"), listOf("1", "2")), "EUR"))
    }
}
