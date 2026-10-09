package com.desideri.familybalance.data

import androidx.room.ColumnInfo
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
 * Voce dell'anagrafica spese, identificata dal [tipo] (il vecchio sottotipo è finito nelle note).
 *
 * - [entrata]: voce che rappresenta un'entrata (stipendio, interessi), conteggiata a parte nel bilancio.
 * - [ricorrente]: spesa ricorrente (ex foglio "Bollette"), attesa ogni [mesiRicorrenza] mesi a
 *   partire da [meseInizio] ("yyyy-MM"); senza [meseInizio] è ricorrente ma senza previsione.
 * - [importoPrevistoCent]: importo atteso (positivo) per la previsione; se null si usa la media
 *   delle ultime occorrenze pagate.
 * - [colore]: colore con cui la voce è evidenziata nelle liste.
 * - [obsoleta]: voce non più in uso: non si propone più come tipo e, se ricorrente, non ha
 *   previsioni nei mesi futuri (le operazioni passate restano).
 */
@Entity(tableName = "voci", indices = [Index(value = ["tipo"], unique = true)])
data class Voce(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipo: String,
    val entrata: Boolean = false,
    val ricorrente: Boolean = false,
    val mesiRicorrenza: Int = 1,
    val meseInizio: String? = null,
    val importoPrevistoCent: Long? = null,
    /** Colore scelto per la voce (ARGB), null = nessuno. */
    val colore: Int? = null,
    @ColumnInfo(defaultValue = "0")
    val obsoleta: Boolean = false
) {
    val descrizione: String get() = tipo
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
    val note: String? = null,
    /**
     * Ordine tra le operazioni dello stesso giorno (crescente = più vecchia), dalla posizione della
     * riga nell'estratto conto o nell'Excel importato; null per quelle inserite a mano.
     */
    val ordine: Long? = null,
    /** Operazione di una spesa ricorrente da non considerare nella media per le stime (es. importo eccezionale). */
    @ColumnInfo(defaultValue = "0")
    val esclusaDaMedia: Boolean = false,
    /**
     * Data a cui imputare l'operazione per la spesa ricorrente, se diversa da [data] (es. addebitata
     * il 1° del mese ma relativa al mese precedente); null = [data].
     */
    val dataRicorrente: Long? = null,
    /**
     * Movimento importato dall'estratto ancora "non contabilizzato" (senza data contabile): registrato
     * con la data dell'import, da sanare con un riscontro successivo (che ne aggiorna la data).
     */
    @ColumnInfo(defaultValue = "0")
    val nonContabilizzata: Boolean = false,
    /**
     * Movimento dell'estratto conto Excel a cui l'operazione è legata dal riscontro: data
     * ("yyyy-MM-dd"), importo e numero progressivo tra i movimenti con la stessa data e lo stesso
     * importo nel file (vedi EstrattoExcel). Unico nel conto/valuta; null se non legata.
     */
    val chiaveEstratto: String? = null
)

/** Giorno che conta per le spese ricorrenti (fuori dall'entità: non è una colonna). */
val Operazione.dataPerRicorrente: Long get() = dataRicorrente ?: data

/**
 * Associazione dell'anagrafica associazioni: se la descrizione di un movimento di un estratto conto
 * contiene [chiave], all'import si propone il [tipo]. Salvata come testo (non come id
 * della voce) così resta valida anche se le voci cambiano. Il tipo
 * [TIPO_SPOSTAMENTO] indica uno spostamento tra conti.
 */
@Entity(tableName = "associazioni")
data class Associazione(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chiave: String,
    val tipo: String
) {
    val destinazione: String get() = tipo

    companion object {
        const val TIPO_SPOSTAMENTO = "Spostamento"
    }
}

/**
 * Cambio CHF→EUR inserito a mano per un mese ("yyyy-MM"): 1 CHF = [chfEur] EUR. Prevale su quello
 * ricavato dagli spostamenti CHF↔EUR del mese (vedi [com.desideri.familybalance.logica.Cambi]).
 */
@Entity(tableName = "cambi")
data class Cambio(
    @PrimaryKey val mese: String,
    val chfEur: Double
)

/**
 * Personalizzazione di una scadenza di una spesa ricorrente: la scadenza della voce [voceId] attesa
 * nel mese [mese] ("yyyy-MM", secondo la ricorrenza dell'anagrafica) ha importo [importoCent]
 * (positivo; null = quello calcolato), data prevista [data] (giorni dall'epoch; null = non nota) ed
 * è eventualmente spostata al mese [spostataA] ("yyyy-MM") solo per questa volta.
 */
@Entity(
    tableName = "previsioni_ricorrenti",
    foreignKeys = [ForeignKey(entity = Voce::class, parentColumns = ["id"], childColumns = ["voceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["voceId", "mese"], unique = true)]
)
data class PrevisioneRicorrente(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val voceId: Long,
    val mese: String,
    val importoCent: Long? = null,
    val data: Long? = null,
    val spostataA: String? = null,
    /** Scadenza annullata per questo mese (nessuna previsione). */
    @ColumnInfo(defaultValue = "0")
    val annullata: Boolean = false,
    /** Scadenza aggiunta dall'utente in un mese fuori dalla ricorrenza (prevista, stimata con la media). */
    @ColumnInfo(defaultValue = "0")
    val aggiunta: Boolean = false
)
