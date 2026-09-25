package com.desideri.familybalance.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Valute gestite: ogni conto può averne una o entrambe. */
object Valute {
    const val EUR = "EUR"
    const val CHF = "CHF"
    val TUTTE = listOf(EUR, CHF)
}

/** Conto corrente (es. HelloBank, LGT). I saldi sono per valuta, vedi [ContoValuta]. */
@Entity(tableName = "conti")
data class Conto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String
)

/**
 * Una valuta di un conto con il suo saldo iniziale: è l'unità a cui appartengono le operazioni
 * (es. "LGT CHF" e "LGT EUR" sono due righe distinte dello stesso conto). Importi in centesimi.
 */
@Entity(
    tableName = "conti_valuta",
    foreignKeys = [ForeignKey(entity = Conto::class, parentColumns = ["id"], childColumns = ["contoId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["contoId", "valuta"], unique = true)]
)
data class ContoValuta(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contoId: Long,
    val valuta: String,
    val saldoInizialeCent: Long = 0
)

/**
 * Voce dell'anagrafica spese: tipo e sottotipo opzionale (null = solo tipo).
 *
 * - [entrata]: voce che rappresenta un'entrata (stipendio, interessi), conteggiata a parte nel bilancio.
 * - [ricorrente]: spesa ricorrente (ex foglio "Bollette"), attesa ogni [mesiRicorrenza] mesi a
 *   partire da [meseInizio] ("yyyy-MM"); senza [meseInizio] è ricorrente ma senza previsione.
 * - [importoPrevistoCent]: importo atteso (positivo) per la previsione; se null si usa la media
 *   delle ultime occorrenze pagate.
 */
@Entity(tableName = "voci", indices = [Index(value = ["tipo", "sottotipo"], unique = true)])
data class Voce(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipo: String,
    val sottotipo: String? = null,
    val entrata: Boolean = false,
    val ricorrente: Boolean = false,
    val mesiRicorrenza: Int = 1,
    val meseInizio: String? = null,
    val importoPrevistoCent: Long? = null
) {
    val descrizione: String get() = if (sottotipo.isNullOrBlank()) tipo else "$tipo / $sottotipo"
}

/**
 * Operazione su un conto/valuta. [data] in giorni dall'epoch (LocalDate.toEpochDay), [importoCent]
 * con segno (negativo = uscita).
 *
 * Se [trasferimento] (lo "Spostamento" dell'Excel) non c'è una voce ma il conto/valuta di
 * destinazione [contoValutaDestId]; [collegataId] punta alla contro-operazione registrata
 * sull'altro conto quando lo spostamento è stato inserito dall'app (le operazioni importate
 * dall'Excel hanno già entrambe le righe, non collegate).
 */
@Entity(
    tableName = "operazioni",
    foreignKeys = [ForeignKey(entity = ContoValuta::class, parentColumns = ["id"], childColumns = ["contoValutaId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("contoValutaId"), Index("voceId"), Index("data")]
)
data class Operazione(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contoValutaId: Long,
    val data: Long,
    val importoCent: Long,
    val voceId: Long? = null,
    val trasferimento: Boolean = false,
    val contoValutaDestId: Long? = null,
    val collegataId: Long? = null,
    val note: String? = null
)
