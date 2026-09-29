package com.desideri.familybalance

import com.desideri.familybalance.data.Operazione
import com.desideri.familybalance.logica.Collegamento
import com.desideri.familybalance.logica.MovimentoRiscontro
import com.desideri.familybalance.logica.RiscontroEstratto
import org.junit.Assert.assertEquals
import org.junit.Test

class RiscontroEstrattoTest {

    @Test
    fun abbina_primaStessaDataPoiVicineEntroCinqueGiorni() {
        val ops = listOf(
            Operazione(id = 1, contoValutaId = 1, data = 100, importoCent = -1_000),
            Operazione(id = 2, contoValutaId = 1, data = 103, importoCent = -1_000),
            Operazione(id = 3, contoValutaId = 1, data = 100, importoCent = -2_000),
            Operazione(id = 4, contoValutaId = 1, data = 120, importoCent = -3_000)
        )
        val movimenti = listOf(
            MovimentoRiscontro(-1_000, 102), // vicina alla 2 (1 giorno) prima che alla 1 (2 giorni)
            MovimentoRiscontro(-1_000, 100), // stessa data della 1
            MovimentoRiscontro(-2_000, 106), // 6 giorni: troppo lontana
            MovimentoRiscontro(-3_000, 116)  // 4 giorni: collegata
        )
        val c = RiscontroEstratto.abbina(ops, movimenti).sortedBy { it.movimento }
        assertEquals(listOf(Collegamento(2, 0), Collegamento(1, 1), Collegamento(4, 3)), c)
    }

    @Test
    fun abbina_escludeLeRigheGiaCollegateAMano() {
        val ops = listOf(Operazione(id = 1, contoValutaId = 1, data = 100, importoCent = -1_000))
        val movimenti = listOf(MovimentoRiscontro(-1_000, 100))
        assertEquals(emptyList<Collegamento>(), RiscontroEstratto.abbina(ops, movimenti, esclusiOperazioni = setOf(1L)))
    }
}
