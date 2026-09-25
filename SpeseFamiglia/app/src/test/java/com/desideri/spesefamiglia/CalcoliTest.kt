package com.desideri.spesefamiglia

import com.desideri.spesefamiglia.data.ContoValuta
import com.desideri.spesefamiglia.data.Operazione
import com.desideri.spesefamiglia.data.Voce
import com.desideri.spesefamiglia.logica.Calcoli
import com.desideri.spesefamiglia.logica.StatoMese
import com.desideri.spesefamiglia.logica.testoInCent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalcoliTest {

    private fun giorno(anno: Int, mese: Int, giorno: Int) = LocalDate.of(anno, mese, giorno).toEpochDay()

    @Test
    fun testoInCent_accettaFormatiItalianiEInglesi() {
        assertEquals(1250L, testoInCent("12,50"))
        assertEquals(123450L, testoInCent("1.234,5"))
        assertEquals(-730L, testoInCent("-7.3"))
        assertNull(testoInCent("abc"))
        assertNull(testoInCent(""))
    }

    @Test
    fun dovuta_rispettaPeriodoEMeseDiPartenza() {
        val voce = Voce(id = 1, tipo = "Bollette", sottotipo = "Gas", ricorrente = true, mesiRicorrenza = 2, meseInizio = "2026-09")
        assertTrue(Calcoli.dovuta(voce, YearMonth.of(2026, 9)))
        assertFalse(Calcoli.dovuta(voce, YearMonth.of(2026, 10)))
        assertTrue(Calcoli.dovuta(voce, YearMonth.of(2026, 11)))
        assertFalse(Calcoli.dovuta(voce, YearMonth.of(2026, 7)))
        assertFalse(Calcoli.dovuta(voce.copy(meseInizio = null), YearMonth.of(2026, 9)))
    }

    @Test
    fun saldi_includonoSpostamenti() {
        val conti = listOf(ContoValuta(id = 1, contoId = 1, valuta = "EUR", saldoInizialeCent = 10_000), ContoValuta(id = 2, contoId = 2, valuta = "EUR"))
        val ops = listOf(
            Operazione(id = 1, contoValutaId = 1, data = 0, importoCent = -3_000, trasferimento = true, contoValutaDestId = 2),
            Operazione(id = 2, contoValutaId = 2, data = 0, importoCent = 3_000, trasferimento = true, contoValutaDestId = 1)
        )
        val saldi = Calcoli.saldiCent(conti, ops)
        assertEquals(7_000L, saldi[1])
        assertEquals(3_000L, saldi[2])
    }

    @Test
    fun bilancio_passatoCorrenteEFuturo() {
        val conti = listOf(ContoValuta(id = 1, contoId = 1, valuta = "EUR", saldoInizialeCent = 100_000))
        val stipendio = Voce(id = 1, tipo = "Stipendio", entrata = true)
        val spesa = Voce(id = 2, tipo = "Spesa")
        val bolletta = Voce(id = 3, tipo = "Bollette", sottotipo = "Luce", ricorrente = true, mesiRicorrenza = 1, meseInizio = "2026-08")
        val ops = listOf(
            Operazione(id = 1, contoValutaId = 1, data = giorno(2026, 8, 1), importoCent = 300_000, voceId = 1),
            Operazione(id = 2, contoValutaId = 1, data = giorno(2026, 8, 5), importoCent = -50_000, voceId = 2),
            Operazione(id = 3, contoValutaId = 1, data = giorno(2026, 8, 10), importoCent = -10_000, voceId = 3)
        )
        val righe = Calcoli.bilancio(conti, listOf(stipendio, spesa, bolletta), ops, 1.0, 2000.0, YearMonth.of(2026, 9), mesiFuturi = 1)

        assertEquals(3, righe.size)
        val agosto = righe[0]
        assertEquals(StatoMese.PASSATO, agosto.stato)
        assertEquals(3000.0, agosto.entrate, 0.001)
        assertEquals(-500.0, agosto.correnti, 0.001)
        assertEquals(-100.0, agosto.ricorrentiPagati, 0.001)
        assertEquals(3400.0, agosto.saldoFine!!, 0.001)
        assertEquals(500.0, agosto.deltaTarget, 0.001)

        val settembre = righe[1]
        assertEquals(StatoMese.CORRENTE, settembre.stato)
        assertEquals(-100.0, settembre.ricorrentiPrevisti, 0.001)
        assertEquals(5300.0, settembre.saldoPrevisto!!, 0.001)

        val ottobre = righe[2]
        assertEquals(StatoMese.FUTURO, ottobre.stato)
        assertEquals(7200.0, ottobre.saldoPrevisto!!, 0.001)
    }

    @Test
    fun ricorrenti_mesePagatoNonHaPrevisione() {
        val conti = listOf(ContoValuta(id = 1, contoId = 1, valuta = "CHF"))
        val voce = Voce(id = 1, tipo = "Bollette", sottotipo = "Affitto", ricorrente = true, mesiRicorrenza = 1, meseInizio = "2026-01", importoPrevistoCent = 70_000)
        val ops = listOf(Operazione(id = 1, contoValutaId = 1, data = giorno(2026, 9, 3), importoCent = -70_000, voceId = 1))
        val mesi = listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10))
        val risultato = Calcoli.ricorrenti(mesi, listOf(voce), conti, ops, cambioChfEur = 1.1, oggi = YearMonth.of(2026, 9))

        val settembre = risultato[0].righe.single()
        assertEquals(-770.0, settembre.pagato, 0.001)
        assertNull(settembre.previsto)
        assertEquals(-700.0, risultato[1].righe.single().previsto!!, 0.001)
    }
}
