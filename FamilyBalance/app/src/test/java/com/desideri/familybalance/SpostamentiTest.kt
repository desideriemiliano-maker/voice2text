package com.desideri.familybalance

import com.desideri.familybalance.data.Operazione
import com.desideri.familybalance.logica.Spostamenti
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpostamentiTest {

    private val valute = mapOf(1L to "EUR", 2L to "EUR", 3L to "CHF")
    private fun valuta(id: Long) = valute[id]

    @Test
    fun trovaControparte_stessaValutaImportoOpposto() {
        val op = Operazione(id = 10, contoValutaId = 1, data = 100, importoCent = -5_000, trasferimento = true, contoValutaDestId = 2)
        val ops = listOf(
            op,
            Operazione(id = 11, contoValutaId = 2, data = 101, importoCent = 4_000, trasferimento = true),
            Operazione(id = 12, contoValutaId = 2, data = 102, importoCent = 5_000, trasferimento = true, contoValutaDestId = 1),
            Operazione(id = 13, contoValutaId = 2, data = 100, importoCent = 5_000, trasferimento = false)
        )
        assertEquals(12L, Spostamenti.trovaControparte(op, 2, ops, ::valuta)?.id)
    }

    @Test
    fun trovaControparte_cambioValutaPiuVicinaPerData() {
        val op = Operazione(id = 10, contoValutaId = 3, data = 100, importoCent = -30_000, trasferimento = true, contoValutaDestId = 1)
        val ops = listOf(
            op,
            Operazione(id = 11, contoValutaId = 1, data = 104, importoCent = 31_500, trasferimento = true),
            Operazione(id = 12, contoValutaId = 1, data = 100, importoCent = 30_411, trasferimento = true)
        )
        assertEquals(12L, Spostamenti.trovaControparte(op, 1, ops, ::valuta)?.id)
    }

    @Test
    fun trovaControparte_ignoraGiaCollegateELontane() {
        val op = Operazione(id = 10, contoValutaId = 1, data = 100, importoCent = -5_000, trasferimento = true, contoValutaDestId = 2)
        val ops = listOf(
            op,
            Operazione(id = 11, contoValutaId = 2, data = 100, importoCent = 5_000, trasferimento = true, collegataId = 99),
            Operazione(id = 12, contoValutaId = 2, data = 120, importoCent = 5_000, trasferimento = true)
        )
        assertNull(Spostamenti.trovaControparte(op, 2, ops, ::valuta))
    }

    @Test
    fun candidate_collegataPrimaPoiSpostamentiVicini() {
        val op = Operazione(id = 10, contoValutaId = 3, data = 100, importoCent = -30_000, trasferimento = true, contoValutaDestId = 1, collegataId = 14)
        val collegata = Operazione(id = 14, contoValutaId = 1, data = 130, importoCent = 1_000, trasferimento = true, collegataId = 10)
        val ops = listOf(
            op,
            collegata,
            Operazione(id = 11, contoValutaId = 1, data = 101, importoCent = 31_500, trasferimento = false),
            Operazione(id = 12, contoValutaId = 1, data = 103, importoCent = 30_411, trasferimento = true),
            Operazione(id = 13, contoValutaId = 1, data = 100, importoCent = -500, trasferimento = true),
            Operazione(id = 15, contoValutaId = 1, data = 100, importoCent = 900, trasferimento = true, collegataId = 99),
            Operazione(id = 16, contoValutaId = 2, data = 100, importoCent = 900, trasferimento = true)
        )
        assertEquals(listOf(14L, 12L, 11L), Spostamenti.candidate(op, collegata, 1, ops).map { it.id })
    }
}
