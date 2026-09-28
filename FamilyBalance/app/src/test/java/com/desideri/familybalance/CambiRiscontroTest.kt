package com.desideri.familybalance

import com.desideri.familybalance.data.ContoValuta
import com.desideri.familybalance.data.Operazione
import com.desideri.familybalance.logica.Calcoli
import com.desideri.familybalance.logica.Cambi
import com.desideri.familybalance.logica.FonteCambio
import com.desideri.familybalance.logica.Riscontro
import com.desideri.familybalance.logica.TipoProblema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CambiRiscontroTest {

    private fun giorno(a: Int, m: Int, g: Int) = LocalDate.of(a, m, g).toEpochDay()
    private val valute = mapOf(1L to "CHF", 2L to "EUR", 3L to "EUR")
    private fun valuta(id: Long) = valute[id]

    @Test
    fun cambi_daSpostamentiInseritiRiportatiEAttuale() {
        val ops = listOf(
            Operazione(id = 1, contoValutaId = 1, data = giorno(2026, 3, 5), importoCent = -100_000, trasferimento = true, contoValutaDestId = 2, collegataId = 2),
            Operazione(id = 2, contoValutaId = 2, data = giorno(2026, 3, 5), importoCent = 104_000, trasferimento = true, contoValutaDestId = 1, collegataId = 1),
            Operazione(id = 3, contoValutaId = 1, data = giorno(2026, 3, 20), importoCent = -300_000, trasferimento = true, contoValutaDestId = 2, collegataId = 4),
            Operazione(id = 4, contoValutaId = 2, data = giorno(2026, 3, 20), importoCent = 316_000, trasferimento = true, contoValutaDestId = 1, collegataId = 3)
        )
        val daSpostamenti = Cambi.daSpostamenti(ops, ::valuta)
        assertEquals(1.05, daSpostamenti.getValue(YearMonth.of(2026, 3)), 1e-9)

        val cambi = Cambi(mapOf(YearMonth.of(2026, 5) to 1.10), daSpostamenti, 1.07, YearMonth.of(2026, 9))
        assertEquals(FonteCambio.SPOSTAMENTI, cambi.cambioMese(YearMonth.of(2026, 3)).fonte)
        assertEquals(FonteCambio.INSERITO, cambi.cambioMese(YearMonth.of(2026, 5)).fonte)
        assertEquals(1.05, cambi.chfEur(YearMonth.of(2026, 4)), 1e-9)
        assertEquals(1.10, cambi.chfEur(YearMonth.of(2026, 8)), 1e-9)
        assertEquals(1.05, cambi.chfEur(YearMonth.of(2026, 1)), 1e-9)
        assertEquals(FonteCambio.ATTUALE, cambi.cambioMese(YearMonth.of(2026, 9)).fonte)
    }

    @Test
    fun bilancio_effettoCambioSuiSaldiChf() {
        val conti = listOf(
            ContoValuta(id = 1, contoId = 1, valuta = "CHF", saldoInizialeCent = 100_000),
            ContoValuta(id = 2, contoId = 2, valuta = "EUR", saldoInizialeCent = 50_000)
        )
        val ops = listOf(
            Operazione(id = 1, contoValutaId = 2, data = giorno(2026, 7, 1), importoCent = -1_000),
            Operazione(id = 2, contoValutaId = 2, data = giorno(2026, 8, 1), importoCent = -1_000)
        )
        val cambi = Cambi(mapOf(YearMonth.of(2026, 7) to 1.0, YearMonth.of(2026, 8) to 1.1), emptyMap(), 1.2, YearMonth.of(2026, 9))
        val righe = Calcoli.bilancio(conti, emptyList(), ops, cambi, 0.0, YearMonth.of(2026, 9), mesiFuturi = 0)

        val luglio = righe[0]
        assertEquals(0.0, luglio.effettoCambio, 1e-9)
        assertEquals(1490.0, luglio.saldoFine!!, 1e-9)
        val agosto = righe[1]
        assertEquals(100.0, agosto.effettoCambio, 1e-9)
        assertEquals(1580.0, agosto.saldoFine!!, 1e-9)
        val settembre = righe[2]
        assertEquals(100.0, settembre.effettoCambio, 1e-9)
        assertEquals(1680.0, settembre.saldoFine!!, 1e-9)
    }

    @Test
    fun riscontro_coppieOrfaniCollegamentiEDoppioni() {
        val ops = listOf(
            // coppia da collegare (EUR -> EUR)
            Operazione(id = 1, contoValutaId = 2, data = 100, importoCent = -5_000, trasferimento = true, contoValutaDestId = 3),
            Operazione(id = 2, contoValutaId = 3, data = 101, importoCent = 5_000, trasferimento = true),
            // senza riga corrispondente
            Operazione(id = 3, contoValutaId = 2, data = 200, importoCent = -7_000, trasferimento = true, contoValutaDestId = 3),
            // coppia collegata con importi diversi
            Operazione(id = 4, contoValutaId = 2, data = 300, importoCent = -1_000, trasferimento = true, contoValutaDestId = 3, collegataId = 5),
            Operazione(id = 5, contoValutaId = 3, data = 300, importoCent = 900, trasferimento = true, contoValutaDestId = 2, collegataId = 4),
            // doppione della riga 5 sullo stesso conto
            Operazione(id = 6, contoValutaId = 3, data = 302, importoCent = 900, voceId = 1)
        )
        val problemi = Riscontro.analizza(ops, ::valuta)
        val perTipo = problemi.groupBy { it.tipo }

        val coppia = perTipo.getValue(TipoProblema.DA_COLLEGARE).single()
        assertEquals(1L to 2L, coppia.operazione.id to coppia.altra!!.id)
        assertEquals(listOf(3L), perTipo.getValue(TipoProblema.SENZA_CONTROPARTE).map { it.operazione.id })
        assertEquals("Gli importi sono diversi", perTipo.getValue(TipoProblema.COLLEGAMENTO_ERRATO).single().dettaglio)
        val doppione = perTipo.getValue(TipoProblema.POSSIBILE_DOPPIONE).single()
        assertEquals(5L to 6L, doppione.operazione.id to doppione.altra!!.id)
        assertTrue(problemi.none { it.tipo == TipoProblema.SENZA_CONTROPARTE && it.operazione.id == 6L })
    }
}
