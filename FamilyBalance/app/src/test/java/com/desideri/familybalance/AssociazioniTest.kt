package com.desideri.familybalance

import com.desideri.familybalance.data.Associazione
import com.desideri.familybalance.logica.Associazioni
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssociazioniTest {

    @Test
    fun contiene_ignoraMaiuscoleESpaziERichiedeParolaIntera() {
        assertTrue(Associazioni.contiene("PAGAMENTO POS  ESSELUNGA NOVEDRATE PB 12/03", "Esselunga Novedrate Pb"))
        assertTrue(Associazioni.contiene("Parcheggio 5 Como", "5"))
        assertFalse(Associazioni.contiene("Importo 25,00", "5"))
        assertFalse(Associazioni.contiene("CONADCITY", "Conad"))
        assertTrue(Associazioni.contiene("Carta Revolut**5782* addebito", "Revolut**5782*"))
    }

    @Test
    fun candidate_unaPerDestinazione() {
        val associazioni = listOf(
            Associazione(chiave = "Cinelandia Spa", tipo = "Scarpe"),
            Associazione(chiave = "Cinelandia Spa", tipo = "Cinema"),
            Associazione(chiave = "Mc Donald's", tipo = "Ristorante"),
            Associazione(chiave = "Donald", tipo = "Ristorante")
        )
        assertEquals(2, Associazioni.candidate("POS CINELANDIA SPA", associazioni).size)
        assertEquals(1, Associazioni.candidate("Mc Donald's Grandate", associazioni).size)
        assertTrue(Associazioni.candidate("Altro", associazioni).isEmpty())
    }

    @Test
    fun leggiElenco_usaUltimeParentesi() {
        val elenco = Associazioni.leggiElenco(
            """
            211 (Parcheggio)
            Pagamento Utenze ( Servizi Pubblici, Luce, Gas, Telefono, Ecc.) (Bollette)
            Esselunga (Spesa / Supermercato)
            riga senza parentesi
            """.trimIndent()
        )
        assertEquals(3, elenco.size)
        assertEquals("211", elenco[0].chiave)
        assertEquals("Parcheggio", elenco[0].tipo)
        assertNull(elenco[0].sottotipo)
        assertEquals("Pagamento Utenze ( Servizi Pubblici, Luce, Gas, Telefono, Ecc.)", elenco[1].chiave)
        assertEquals("Bollette", elenco[1].tipo)
        assertEquals("Supermercato", elenco[2].sottotipo)
    }
}
