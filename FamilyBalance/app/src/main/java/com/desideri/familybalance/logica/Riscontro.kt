package com.desideri.familybalance.logica

import com.desideri.familybalance.data.Operazione
import kotlin.math.abs

enum class TipoProblema(val titolo: String, val spiegazione: String) {
    DA_COLLEGARE(
        "Da collegare",
        "Spostamenti con la riga corrispondente sull'altro conto, ma non collegati tra loro."
    ),
    SENZA_CONTROPARTE(
        "Senza riga corrispondente",
        "Spostamenti di cui non si trova la riga sull'altro conto: manca, ha importo/data diversi o è registrata con un altro tipo."
    ),
    COLLEGAMENTO_ERRATO(
        "Collegamenti incoerenti",
        "Spostamenti collegati a una riga che non esiste più, su un altro conto o con importo non corrispondente."
    )
}

/** Un problema trovato dal riscontro: [operazione] e, per coppie e collegamenti, l'[altra] riga. */
data class ProblemaSpostamento(
    val tipo: TipoProblema,
    val operazione: Operazione,
    val altra: Operazione? = null,
    val dettaglio: String? = null,
    /** Per gli spostamenti senza riga corrispondente: operazioni dello stesso conto e importo a pochi giorni (possibili doppioni). */
    val simili: List<Operazione> = emptyList()
) {
    fun coinvolge(contoValutaId: Long): Boolean = operazione.contoValutaId == contoValutaId || altra?.contoValutaId == contoValutaId
}

/**
 * Riscontro degli spostamenti tra conti: ogni spostamento dovrebbe avere sull'altro conto la riga
 * speculare, collegata. Le righe importate dall'Excel (e quelle degli estratti conto con cambio di
 * valuta) nascono non collegate: qui si propongono le coppie da collegare e si segnalano gli
 * spostamenti rimasti senza riga corrispondente e i collegamenti incoerenti.
 */
object Riscontro {

    /** Distanza massima in giorni delle operazioni con lo stesso importo proposte come possibili doppioni. */
    const val GIORNI_SIMILI = 5L

    fun analizza(operazioni: List<Operazione>, valutaDi: (Long) -> String?): List<ProblemaSpostamento> {
        val perId = operazioni.associateBy { it.id }
        val problemi = ArrayList<ProblemaSpostamento>()

        // Collegamenti esistenti: coerenti tra loro? (ogni coppia controllata una volta)
        for (op in operazioni) {
            val idCollegata = op.collegataId ?: continue
            val altra = perId[idCollegata]
            if (altra == null) {
                problemi += ProblemaSpostamento(TipoProblema.COLLEGAMENTO_ERRATO, op, null, "La riga collegata non esiste più")
                continue
            }
            if (altra.collegataId == op.id && altra.id < op.id) continue
            val difetto = when {
                altra.collegataId != op.id -> "Il collegamento non è reciproco"
                !op.trasferimento || !altra.trasferimento -> "Una delle due righe non è uno spostamento"
                altra.contoValutaId == op.contoValutaId -> "Le due righe sono sullo stesso conto"
                op.contoValutaDestId != altra.contoValutaId || altra.contoValutaDestId != op.contoValutaId ->
                    "Il conto di destinazione non è quello della riga collegata"
                (op.importoCent > 0) == (altra.importoCent > 0) -> "Le due righe hanno lo stesso segno"
                valutaDi(op.contoValutaId) == valutaDi(altra.contoValutaId) && op.importoCent != -altra.importoCent ->
                    "Gli importi sono diversi"
                else -> null
            }
            if (difetto != null) problemi += ProblemaSpostamento(TipoProblema.COLLEGAMENTO_ERRATO, op, altra, difetto)
        }

        // Spostamenti non collegati: coppie da collegare (ognuna usata una volta) o senza riga corrispondente.
        val nonCollegati = operazioni.filter { it.trasferimento && it.collegataId == null }.sortedWith(compareBy({ it.data }, { it.id }))
        val usate = HashSet<Long>()
        val trasferimentiPerConto = operazioni.filter { it.trasferimento }.groupBy { it.contoValutaId }
        val perContoEImporto = operazioni.groupBy { it.contoValutaId to it.importoCent }
        fun cerca(op: Operazione, contoValutaId: Long?): Operazione? {
            val disponibili = trasferimentiPerConto[contoValutaId].orEmpty().filter { it.id !in usate }
            return Spostamenti.trovaControparte(op, contoValutaId, disponibili, valutaDi)
        }
        for (op in nonCollegati) {
            if (op.id in usate) continue
            val sulConto = cerca(op, op.contoValutaDestId)
            // Se non c'è sul conto di destinazione indicato, la si cerca sugli altri conti (destinazione sbagliata).
            val altrove = if (sulConto == null) {
                trasferimentiPerConto.keys.filter { it != op.contoValutaId && it != op.contoValutaDestId }
                    .mapNotNull { cerca(op, it) }
                    .filter { valutaDi(it.contoValutaId) == valutaDi(op.contoValutaId) }
                    .singleOrNull()
            } else null
            val trovata = sulConto ?: altrove
            if (trovata != null) {
                usate += op.id
                usate += trovata.id
                problemi += ProblemaSpostamento(
                    TipoProblema.DA_COLLEGARE, op, trovata,
                    if (altrove != null) "Trovata su un conto diverso dalla destinazione indicata" else null
                )
            } else {
                val simili = perContoEImporto[op.contoValutaId to op.importoCent].orEmpty()
                    .filter { it.id != op.id && abs(it.data - op.data) <= GIORNI_SIMILI }
                    .sortedWith(compareBy({ abs(it.data - op.data) }, { it.id }))
                problemi += ProblemaSpostamento(
                    TipoProblema.SENZA_CONTROPARTE, op, null,
                    if (op.contoValutaDestId == null) "Manca il conto di destinazione" else null,
                    simili
                )
            }
        }

        return problemi
    }
}
