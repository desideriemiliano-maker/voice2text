package com.desideri.familybalance

import com.desideri.familybalance.data.ContoValuta
import com.desideri.familybalance.data.Operazione
import com.desideri.familybalance.data.PrevisioneRicorrente
import com.desideri.familybalance.data.Voce
import com.desideri.familybalance.logica.Calcoli
import com.desideri.familybalance.logica.Cambi
import com.desideri.familybalance.logica.FontePrevisione
import com.desideri.familybalance.logica.StatoMese
import com.desideri.familybalance.logica.testoInCent
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
        val righe = Calcoli.bilancio(conti, listOf(stipendio, spesa, bolletta), ops, Cambi.fisso(1.0), 2000.0, YearMonth.of(2026, 9), mesiFuturi = 1)

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
        // Corrente: saldo attuale + ricorrenti previste (il target non entra); risparmio con lo stipendio di agosto.
        assertEquals(3300.0, settembre.saldoPrevisto!!, 0.001)
        assertEquals(3000.0, settembre.risparmio, 0.001)
        assertEquals(1000.0, settembre.deltaTarget, 0.001)

        val ottobre = righe[2]
        assertEquals(StatoMese.FUTURO, ottobre.stato)
        assertEquals(5200.0, ottobre.saldoPrevisto!!, 0.001)
    }

    @Test
    fun ricorrenti_mesePagatoNonHaPrevisione() {
        val conti = listOf(ContoValuta(id = 1, contoId = 1, valuta = "CHF"))
        val voce = Voce(id = 1, tipo = "Bollette", sottotipo = "Affitto", ricorrente = true, mesiRicorrenza = 1, meseInizio = "2026-01", importoPrevistoCent = 70_000)
        val ops = listOf(Operazione(id = 1, contoValutaId = 1, data = giorno(2026, 9, 3), importoCent = -70_000, voceId = 1))
        val mesi = listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10))
        val risultato = Calcoli.ricorrenti(mesi, listOf(voce), conti, ops, cambi = Cambi.fisso(1.1), oggi = YearMonth.of(2026, 9))

        val settembre = risultato[0].righe.single()
        assertEquals(-770.0, settembre.pagato, 0.001)
        assertNull(settembre.previsto)
        assertEquals(-700.0, risultato[1].righe.single().previsto!!, 0.001)
    }

    @Test
    fun ricorrenti_personalizzazioniEObsolete() {
        val conti = listOf(ContoValuta(id = 1, contoId = 1, valuta = "EUR"))
        val luce = Voce(id = 1, tipo = "Luce", ricorrente = true, mesiRicorrenza = 2, meseInizio = "2026-09", importoPrevistoCent = 10_000)
        val gas = Voce(id = 2, tipo = "Gas", ricorrente = true, mesiRicorrenza = 1, meseInizio = "2026-01", importoPrevistoCent = 5_000, obsoleta = true)
        val mesi = (9..12).map { YearMonth.of(2026, it) }
        // Scadenza di novembre spostata a dicembre con importo 120 e data nota.
        val pers = listOf(PrevisioneRicorrente(voceId = 1, mese = "2026-11", importoCent = 12_000, data = giorno(2026, 12, 10), spostataA = "2026-12"))
        val r = Calcoli.ricorrenti(mesi, listOf(luce, gas), conti, emptyList(), Cambi.fisso(1.0), YearMonth.of(2026, 9), pers)

        assertEquals(-100.0, r[0].righe.single().previsto!!, 0.001)
        assertTrue(r[1].righe.isEmpty())
        assertTrue(r[2].righe.isEmpty())
        val dicembre = r[3].righe.single()
        assertEquals(-120.0, dicembre.previsto!!, 0.001)
        assertEquals(YearMonth.of(2026, 11), dicembre.meseOrigine)
        assertEquals(FontePrevisione.PERSONALIZZATA, dicembre.fonte)
    }

    @Test
    fun ricorrenti_stimaNeiMesiPassatiEScadenzaAnnullata() {
        val conti = listOf(ContoValuta(id = 1, contoId = 1, valuta = "EUR"))
        val voce = Voce(id = 1, tipo = "Luce", ricorrente = true, mesiRicorrenza = 1, meseInizio = "2026-06")
        val ops = listOf(
            Operazione(id = 1, contoValutaId = 1, data = giorno(2026, 6, 10), importoCent = -10_000, voceId = 1),
            Operazione(id = 2, contoValutaId = 1, data = giorno(2026, 7, 10), importoCent = -12_000, voceId = 1)
        )
        val pers = listOf(PrevisioneRicorrente(voceId = 1, mese = "2026-10", annullata = true))
        val mesi = (6..10).map { YearMonth.of(2026, it) }
        val r = Calcoli.ricorrenti(mesi, listOf(voce), conti, ops, Cambi.fisso(1.0), YearMonth.of(2026, 9), pers)

        assertEquals(-100.0, r[0].righe.single().pagato, 0.001)
        // Agosto (passato, nessun pagamento): stima con la media 110.
        assertEquals(-110.0, r[2].righe.single().previsto!!, 0.001)
        assertEquals(-110.0, r[3].righe.single().previsto!!, 0.001)
        val ottobre = r[4].righe.single()
        assertTrue(ottobre.annullata)
        assertNull(ottobre.previsto)
    }

    @Test
    fun ricorrenti_operazioneEsclusaNonEntraNellaMedia() {
        val conti = listOf(ContoValuta(id = 1, contoId = 1, valuta = "EUR"))
        val voce = Voce(id = 1, tipo = "Luce", ricorrente = true, mesiRicorrenza = 1, meseInizio = "2026-06")
        val ops = listOf(
            Operazione(id = 1, contoValutaId = 1, data = giorno(2026, 6, 10), importoCent = -10_000, voceId = 1),
            Operazione(id = 2, contoValutaId = 1, data = giorno(2026, 7, 10), importoCent = -90_000, voceId = 1, esclusaDaMedia = true)
        )
        val r = Calcoli.ricorrenti(listOf(YearMonth.of(2026, 7), YearMonth.of(2026, 9)), listOf(voce), conti, ops, Cambi.fisso(1.0), YearMonth.of(2026, 9))
        // Luglio mostra comunque il pagato; la stima di settembre usa solo giugno.
        assertEquals(-900.0, r[0].righe.single().pagato, 0.001)
        assertEquals(-100.0, r[1].righe.single().previsto!!, 0.001)
    }

    @Test
    fun ricorrenti_dataPerRicorrenteSpostaIlMese() {
        val conti = listOf(ContoValuta(id = 1, contoId = 1, valuta = "EUR"))
        val voce = Voce(id = 1, tipo = "Affitto", ricorrente = true, mesiRicorrenza = 1, meseInizio = "2026-06")
        // Addebitata il 1° luglio ma relativa a giugno.
        val ops = listOf(Operazione(id = 1, contoValutaId = 1, data = giorno(2026, 7, 1), importoCent = -80_000, voceId = 1, dataRicorrente = giorno(2026, 6, 30)))
        val r = Calcoli.ricorrenti(listOf(YearMonth.of(2026, 6), YearMonth.of(2026, 7)), listOf(voce), conti, ops, Cambi.fisso(1.0), YearMonth.of(2026, 9))
        assertEquals(-800.0, r[0].righe.single().pagato, 0.001)
        assertEquals(-800.0, r[1].righe.single().previsto!!, 0.001)
    }

    @Test
    fun ricorrenti_scadenzaAggiuntaFuoriRicorrenza() {
        val conti = listOf(ContoValuta(id = 1, contoId = 1, valuta = "EUR"))
        val voce = Voce(id = 1, tipo = "Tassa", ricorrente = true, mesiRicorrenza = 12, meseInizio = "2026-01", importoPrevistoCent = 30_000)
        val pers = listOf(PrevisioneRicorrente(voceId = 1, mese = "2026-11", aggiunta = true))
        val r = Calcoli.ricorrenti(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 11)), listOf(voce), conti, emptyList(), Cambi.fisso(1.0), YearMonth.of(2026, 9), pers)
        assertTrue(r[0].righe.isEmpty())
        assertEquals(-300.0, r[1].righe.single().previsto!!, 0.001)
    }

    @Test
    fun ricorrenti_giaPagatoEMancante() {
        val conti = listOf(ContoValuta(id = 1, contoId = 1, valuta = "EUR"))
        val luce = Voce(id = 1, tipo = "Luce", ricorrente = true, mesiRicorrenza = 1, meseInizio = "2026-09")
        val gas = Voce(id = 2, tipo = "Gas", ricorrente = true, mesiRicorrenza = 1, meseInizio = "2026-09", importoPrevistoCent = 5_000)
        val mutuo = Voce(id = 3, tipo = "Mutuo", ricorrente = true, mesiRicorrenza = 1, meseInizio = "2026-09")
        // Luce già addebitata, mutuo registrato con data futura, gas solo previsto.
        val ops = listOf(
            Operazione(id = 1, contoValutaId = 1, data = giorno(2026, 9, 5), importoCent = -10_000, voceId = 1),
            Operazione(id = 2, contoValutaId = 1, data = giorno(2026, 9, 28), importoCent = -70_000, voceId = 3)
        )
        val m = Calcoli.ricorrenti(
            listOf(YearMonth.of(2026, 9)), listOf(luce, gas, mutuo), conti, ops, Cambi.fisso(1.0), YearMonth.of(2026, 9),
            alGiorno = giorno(2026, 9, 15)
        ).single()
        assertEquals(-850.0, m.totale, 0.001)
        assertEquals(-800.0, m.totalePagato, 0.001)
        assertEquals(-100.0, m.totaleGiaPagato, 0.001)
        assertEquals(-750.0, m.totaleMancante, 0.001)
    }

    @Test
    fun ricorrenti_residuoTraImportoEOperazioni() {
        val conti = listOf(ContoValuta(id = 1, contoId = 1, valuta = "EUR"))
        val luce = Voce(id = 1, tipo = "Luce", ricorrente = true, mesiRicorrenza = 1, meseInizio = "2026-06", importoPrevistoCent = 10_000)
        val ops = listOf(
            Operazione(id = 1, contoValutaId = 1, data = giorno(2026, 6, 10), importoCent = -6_000, voceId = 1),
            Operazione(id = 2, contoValutaId = 1, data = giorno(2026, 7, 10), importoCent = -15_000, voceId = 1),
            Operazione(id = 3, contoValutaId = 1, data = giorno(2026, 9, 3), importoCent = -6_000, voceId = 1)
        )
        // Luglio con importo impostato a 200: resta il residuo anche nel passato.
        val pers = listOf(PrevisioneRicorrente(voceId = 1, mese = "2026-07", importoCent = 20_000))
        val mesi = listOf(YearMonth.of(2026, 6), YearMonth.of(2026, 7), YearMonth.of(2026, 9))
        val r = Calcoli.ricorrenti(mesi, listOf(luce), conti, ops, Cambi.fisso(1.0), YearMonth.of(2026, 9), pers)

        // Giugno (passato, importo da anagrafica): il pagamento chiude la scadenza.
        assertNull(r[0].righe.single().previsto)
        val luglio = r[1].righe.single()
        assertEquals(-150.0, luglio.pagato, 0.001)
        assertEquals(-50.0, luglio.previsto!!, 0.001)
        assertEquals(-200.0, luglio.importoScadenza!!, 0.001)
        // Settembre (corrente): pagati 60 su 100, restano 40; il totale del mese è 100.
        val settembre = r[2].righe.single()
        assertEquals(-60.0, settembre.pagato, 0.001)
        assertEquals(-40.0, settembre.previsto!!, 0.001)
        assertEquals(-100.0, r[2].totale, 0.001)
    }
}
