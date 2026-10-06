package com.desideri.familybalance

import com.desideri.familybalance.estratto.ColonneExcel
import com.desideri.familybalance.estratto.EstrattoExcel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class EstrattoExcelTest {

    private fun righe(vararg r: List<String>) = r.mapIndexed { i, celle -> (i + 1) to celle }

    private val estratto = righe(
        listOf("", "C/C:", "12345"),
        listOf("", "Divisa C/C:", "EUR"),
        listOf("", "Data contabile", "Data valuta", "Descrizione", "Dettaglio", "Importo"),
        listOf("", "Non contabilizzato", "30/9/2026", "Negozio", "Pagamento Con Carta", "-122.8"),
        listOf("", "30/9/2026", "28/9/2026", "Rimborso", "Rimborso", "-723.39"),
        listOf("", "29/9/2026", "29/9/2026", "Stipendio", "Bonifico", "2500")
    )

    @Test
    fun proponeLeColonneDallIntestazione() {
        val c = EstrattoExcel.proponi(estratto, null)!!
        assertEquals(ColonneExcel(rigaIntestazione = 2, data = 1, importo = 5, entrate = null, descrizioni = listOf(3, 4)), c)
    }

    @Test
    fun leggeConLeColonneScelteESaltaLeRigheSenzaData() {
        // Data valuta: tutte e tre le righe hanno la data.
        val esito = EstrattoExcel.leggi(estratto, ColonneExcel(2, data = 2, importo = 5, descrizioni = listOf(3, 4)), "EUR")
        assertEquals(listOf(-12280L, -72339L, 250000L), esito.movimenti.map { it.importoCent })
        assertEquals("Negozio · Pagamento Con Carta", esito.movimenti[0].descrizione)
        assertEquals("Rimborso", esito.movimenti[1].descrizione)
        assertEquals(LocalDate.of(2026, 9, 28), esito.movimenti[1].dataOperazione)
        assertEquals(4, esito.movimenti[0].rigaFile)
        // Data contabile: "Non contabilizzato" non è una data: la riga resta, con la data di oggi e il flag.
        val oggi = LocalDate.of(2026, 10, 6)
        val conContabile = EstrattoExcel.leggi(estratto, ColonneExcel(2, data = 1, importo = 5), "EUR", oggi).movimenti
        assertEquals(3, conContabile.size)
        assertEquals(true, conContabile[0].nonContabilizzato)
        assertEquals(oggi, conContabile[0].dataOperazione)
        assertEquals(false, conContabile[1].nonContabilizzato)
    }

    @Test
    fun laSceltaMemorizzataPrevaleSuiNomi() {
        val salvate = EstrattoExcel.daSalvare(estratto, ColonneExcel(2, data = 2, importo = 5, descrizioni = listOf(4)))
        assertEquals(ColonneExcel(2, data = 2, importo = 5, entrate = null, descrizioni = listOf(4)), EstrattoExcel.proponi(estratto, salvate))
    }

    @Test
    fun colonneAddebitiAccrediti() {
        val r = righe(
            listOf("Data operazione", "Causale", "Addebiti", "Accrediti"),
            listOf("2026-03-01", "Affitto", "1.200,00", ""),
            listOf("02.03.26", "Rimborso", "", "35,5")
        )
        val c = EstrattoExcel.proponi(r, null)!!
        assertEquals(ColonneExcel(0, data = 0, importo = 2, entrate = 3, descrizioni = listOf(1)), c)
        val esito = EstrattoExcel.leggi(r, c, "CHF")
        assertEquals(listOf(-120000L, 3550L), esito.movimenti.map { it.importoCent })
        assertEquals("CHF", esito.movimenti[0].valuta)
    }

    @Test
    fun foglioVuoto() {
        assertNull(EstrattoExcel.proponi(righe(listOf("", "")), null))
    }
}
