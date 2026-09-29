package com.desideri.familybalance.logica

import com.desideri.familybalance.data.Operazione
import kotlin.math.abs

/** Collegamento tra un'operazione del conto ([operazioneId]) e un movimento dell'estratto ([movimento], indice). */
data class Collegamento(val operazioneId: Long, val movimento: Int, val manuale: Boolean = false)

/** Un movimento dell'estratto ridotto a ciò che serve per il riscontro: importo e data scelta. */
data class MovimentoRiscontro(val importoCent: Long, val data: Long)

/**
 * Riscontro tra le operazioni di un conto e i movimenti di un estratto conto: si abbinano, uno a
 * uno, operazioni e movimenti con lo stesso importo e date entro [GIORNI] giorni, prima quelli con
 * la stessa data e poi i più vicini.
 */
object RiscontroEstratto {

    const val GIORNI = 5L

    fun abbina(
        operazioni: List<Operazione>,
        movimenti: List<MovimentoRiscontro>,
        esclusiOperazioni: Set<Long> = emptySet(),
        esclusiMovimenti: Set<Int> = emptySet()
    ): List<Collegamento> {
        val perImporto = operazioni.filter { it.id !in esclusiOperazioni }.groupBy { it.importoCent }
        val usate = HashSet<Long>()
        val risultato = ArrayList<Collegamento>()
        val abbinati = HashSet<Int>(esclusiMovimenti)
        for (distanza in 0L..GIORNI) {
            movimenti.forEachIndexed { i, m ->
                if (i in abbinati) return@forEachIndexed
                val op = perImporto[m.importoCent].orEmpty()
                    .filter { it.id !in usate && abs(it.data - m.data) == distanza }
                    .minByOrNull { it.id } ?: return@forEachIndexed
                usate += op.id
                abbinati += i
                risultato += Collegamento(op.id, i)
            }
        }
        return risultato
    }
}
