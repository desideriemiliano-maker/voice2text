package com.desideri.familybalance

import com.desideri.familybalance.data.Operazione
import com.desideri.familybalance.logica.Duplicati
import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicatiTest {
    private fun op(id: Long, giorno: Long, cent: Long) = Operazione(id = id, contoValutaId = 1, data = giorno, importoCent = cent)

    @Test
    fun raggruppaStessoImportoEntroIGiorni() {
        val ops = listOf(
            op(1, 100, -5000), op(2, 102, -5000), op(3, 104, -5000), // catena: 100, 102, 104
            op(4, 120, -5000),                                     // troppo lontana
            op(5, 101, -7000), op(6, 110, -7000),                  // 9 giorni: no
            op(7, 200, 1000), op(8, 200, 1000)                     // stesso giorno
        )
        val g = Duplicati.gruppi(ops, 3)
        assertEquals(listOf(listOf(7L, 8L), listOf(1L, 2L, 3L)), g.map { gr -> gr.map { it.id } })
    }

    @Test
    fun conZeroGiorniSoloLoStessoGiorno() {
        val ops = listOf(op(1, 100, -5000), op(2, 101, -5000), op(3, 101, -5000))
        assertEquals(listOf(listOf(2L, 3L)), Duplicati.gruppi(ops, 0).map { gr -> gr.map { it.id } })
    }
}
