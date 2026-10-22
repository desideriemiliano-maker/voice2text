package com.desideri.familybalance.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Tipologia di stipendio dell'anagrafica stipendi: [nome], [valuta], conto/valuta di accredito
 * ([contoValutaId], null = non indicato) e se è [obsoleto] (non più mostrato tra gli stipendi).
 * Le voci che lo compongono sono in [VoceStipendio].
 */
@Entity(tableName = "stipendi_tipi")
data class TipoStipendio(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val valuta: String,
    val contoValutaId: Long? = null,
    val obsoleto: Boolean = false
)

/**
 * Una voce di una tipologia di stipendio (es. "Retribuzione", "Netto"), nell'ordine [ordine]. Una
 * voce [calcolata] non ha importi propri: vale la somma delle voci [addendi] (id separati da
 * virgola), moltiplicata per [percentuale] / 100 se indicata.
 */
@Entity(
    tableName = "stipendi_voci",
    foreignKeys = [ForeignKey(entity = TipoStipendio::class, parentColumns = ["id"], childColumns = ["tipoId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tipoId")]
)
data class VoceStipendio(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipoId: Long,
    val descrizione: String,
    val ordine: Int = 0,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val calcolata: Boolean = false,
    val addendi: String? = null,
    val percentuale: Double? = null,
    /** Nomi con cui la voce compare nelle buste paga (separati da "|"), per riconoscerla agli import. */
    val nomiImport: String? = null,
    /** Voce calcolata: cifre decimali a cui arrotondare il risultato (null = nessun arrotondamento). */
    val arrotondaDecimali: Int? = null,
    /** Arrotondamento all'ultima cifra 0 o 5 (es. con 1 decimale: 7,62 → 7,60 e 7,63 → 7,65). */
    @androidx.room.ColumnInfo(defaultValue = "0")
    val arrotondaCinque: Boolean = false,
    /** Voce calcolata: il risultato cambia segno (es. una trattenuta calcolata da competenze positive). */
    @androidx.room.ColumnInfo(defaultValue = "0")
    val invertiSegno: Boolean = false,
    /** Voce calcolata: valore fisso (centesimi, con segno) aggiunto alla somma delle voci; null = nessuno. */
    val valoreFissoCent: Long? = null
) {
    val idAddendi: List<Long> get() = addendi.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }
    val listaNomiImport: List<String> get() = nomiImport.orEmpty().split('|').map { it.trim() }.filter { it.isNotEmpty() }

    /** true se [nome] (letto dalla busta paga) è questa voce: per descrizione o per uno dei nomi di import. */
    fun riconosce(nome: String): Boolean =
        !calcolata && (descrizione.equals(nome.trim(), true) || listaNomiImport.any { it.equals(nome.trim(), true) })

    /** La voce con [nome] tra i nomi di import (se non c'è già e non è la descrizione). */
    fun conNomeImport(nome: String): VoceStipendio =
        if (riconosce(nome)) this else copy(nomiImport = (listaNomiImport + nome.trim()).joinToString("|"))
}

/**
 * Importi (centesimi) di tutte le [voci] di uno stipendio: quelli registrati ([importi], per id
 * voce) e quelli delle voci calcolate, anche a partire da altre voci calcolate (i riferimenti
 * circolari valgono zero). Senza nessun addendo valorizzato la voce calcolata resta vuota.
 */
/** [cent] arrotondato a [decimali] cifre (0 o 1), all'ultima cifra 0 o 5 se [aCinque]; metà verso l'alto. */
fun arrotonda(cent: Long, decimali: Int, aCinque: Boolean): Long {
    val passo = (if (decimali == 0) 100L else 10L) / (if (aCinque) 2 else 1)
    return java.math.BigDecimal(cent).divide(java.math.BigDecimal(passo)).setScale(0, java.math.RoundingMode.HALF_UP).toLong() * passo
}

fun valoriStipendio(
    voci: List<VoceStipendio>,
    importi: Map<Long, Long>,
    percentuali: Map<Long, Double> = emptyMap(),
    /** Voce calcolata -> (voce sommata -> valore usato solo da lei). */
    sostituzioni: Map<Long, Map<Long, Long>> = emptyMap()
): Map<Long, Long> {
    val perId = voci.associateBy { it.id }
    val risultato = HashMap<Long, Long?>()
    fun valore(id: Long, inCorso: Set<Long>): Long? {
        if (id in risultato) return risultato[id]
        val v = perId[id] ?: return null
        if (!v.calcolata) return importi[id]
        if (id in inCorso) return null
        val parti = v.idAddendi.mapNotNull { a -> sostituzioni[id]?.get(a) ?: valore(a, inCorso + id) }
        // Somma delle voci più l'eventuale valore fisso (vuota se non c'è nessuno dei due).
        val somma = if (parti.isEmpty() && v.valoreFissoCent == null) null else parti.sum() + (v.valoreFissoCent ?: 0L)
        // La percentuale dello stipendio, se memorizzata, prevale su quella dell'anagrafica.
        val percentuale = if (v.percentuale != null) percentuali[id] ?: v.percentuale else null
        val calcolato = somma?.let { passiFormula(v, it, percentuale).risultato }
        risultato[id] = calcolato
        return calcolato
    }
    return voci.mapNotNull { v -> valore(v.id, emptySet())?.let { v.id to it } }.toMap()
}

/** Valori intermedi di una voce calcolata dopo la somma: percentuale, arrotondamento e segno. */
data class PassiFormula(val conPercentuale: Long, val arrotondato: Long, val risultato: Long)

fun passiFormula(v: VoceStipendio, somma: Long, percentuale: Double?): PassiFormula {
    val conPercentuale = percentuale?.let { Math.round(somma * it / 100.0) } ?: somma
    // Arrotondamento a [arrotondaDecimali] cifre (con 1: 7,64 → 7,6 e 7,68 → 7,7), o all'ultima
    // cifra 0 o 5 con [arrotondaCinque] (con 1: 7,62 → 7,60 e 7,63 → 7,65).
    val arrotondato = v.arrotondaDecimali?.takeIf { it in 0..1 }?.let { d -> arrotonda(conPercentuale, d, v.arrotondaCinque) } ?: conPercentuale
    return PassiFormula(conPercentuale, arrotondato, if (v.invertiSegno) -arrotondato else arrotondato)
}

/**
 * Livello di dipendenza di ogni voce: 0 per le voci non calcolate, per le calcolate 1 più il
 * livello massimo delle voci sommate (i riferimenti circolari non contano).
 */
fun livelliVoci(voci: List<VoceStipendio>): Map<Long, Int> {
    val perId = voci.associateBy { it.id }
    val livelli = HashMap<Long, Int>()
    fun livello(id: Long, inCorso: Set<Long>): Int {
        livelli[id]?.let { return it }
        val v = perId[id] ?: return 0
        if (!v.calcolata) return 0
        if (id in inCorso) return 0
        val l = 1 + (v.idAddendi.maxOfOrNull { livello(it, inCorso + id) } ?: 0)
        livelli[id] = l
        return l
    }
    return voci.associate { it.id to livello(it.id, emptySet()) }
}

/** La voce [id] e tutte quelle da cui dipende, direttamente o tramite altre voci calcolate. */
fun dipendenzeVoce(voci: List<VoceStipendio>, id: Long): Set<Long> {
    val perId = voci.associateBy { it.id }
    val trovate = LinkedHashSet<Long>()
    fun visita(v: Long) {
        if (!trovate.add(v)) return
        perId[v]?.takeIf { it.calcolata }?.idAddendi?.forEach { visita(it) }
    }
    visita(id)
    return trovate
}

/** Numero (da 1) di [s] tra gli stipendi dello stesso mese in [stipendi]; null se è l'unico. */
fun numeroNelMese(s: Stipendio, stipendi: List<Stipendio>): Int? {
    val delMese = stipendi.filter { it.tipoId == s.tipoId && it.mese == s.mese }.sortedBy { it.id }
    return if (delMese.size > 1) delMese.indexOfFirst { it.id == s.id } + 1 else null
}

/** Un addendo nel calcolo: la voce, il valore usato e se è sostituito solo per la voce calcolata. */
data class AddendoCalcolo(val voce: VoceStipendio, val valore: Long?, val sostituito: Boolean)

/** Dettaglio del calcolo di una voce calcolata in uno stipendio. */
data class CalcoloVoce(
    val voce: VoceStipendio,
    val livello: Int,
    val addendi: List<AddendoCalcolo>,
    val somma: Long?,
    val percentuale: Double?,
    val passi: PassiFormula?
)

/** I calcoli delle voci calcolate di uno stipendio, prima quelle con meno dipendenze (a parità, nell'ordine di [voci]). */
fun calcoliStipendio(
    voci: List<VoceStipendio>,
    importi: Map<Long, Long>,
    percentuali: Map<Long, Double> = emptyMap(),
    sostituzioni: Map<Long, Map<Long, Long>> = emptyMap()
): List<CalcoloVoce> {
    val valori = valoriStipendio(voci, importi, percentuali, sostituzioni)
    val livelli = livelliVoci(voci)
    val perId = voci.associateBy { it.id }
    return voci.filter { it.calcolata }.map { v ->
        val addendi = v.idAddendi.mapNotNull { a ->
            perId[a]?.let { av ->
                val sost = sostituzioni[v.id]?.get(a)
                AddendoCalcolo(av, sost ?: valori[a], sost != null)
            }
        }
        val parti = addendi.mapNotNull { it.valore }
        val somma = if (parti.isEmpty() && v.valoreFissoCent == null) null else parti.sum() + (v.valoreFissoCent ?: 0L)
        val percentuale = if (v.percentuale != null) percentuali[v.id] ?: v.percentuale else null
        CalcoloVoce(v, livelli[v.id] ?: 1, addendi, somma, percentuale, somma?.let { passiFormula(v, it, percentuale) })
    }.sortedBy { it.livello }
}

/**
 * Uno stipendio registrato (es. importato da una busta paga) di una tipologia, per il [mese]
 * "yyyy-MM"; nello stesso mese ce ne possono essere più d'uno (es. tredicesima a parte).
 */
@Entity(
    tableName = "stipendi",
    foreignKeys = [ForeignKey(entity = TipoStipendio::class, parentColumns = ["id"], childColumns = ["tipoId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["tipoId", "mese"])]
)
data class Stipendio(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipoId: Long,
    val mese: String,
    /** Operazione di accredito sul conto associata dal riscontro stipendi; null = non associato. */
    val operazioneId: Long? = null
)

/**
 * Importo (centesimi, con segno) di una voce in uno stipendio. Per una voce calcolata con percentuale
 * [percentuale] è quella usata in questo stipendio (può cambiare da uno stipendio all'altro) e
 * [importoCent] il valore calcolato con essa.
 */
@Entity(
    tableName = "stipendi_importi",
    foreignKeys = [
        ForeignKey(entity = Stipendio::class, parentColumns = ["id"], childColumns = ["stipendioId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = VoceStipendio::class, parentColumns = ["id"], childColumns = ["voceId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["stipendioId", "voceId"], unique = true), Index("voceId")]
)
data class ImportoStipendio(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stipendioId: Long,
    val voceId: Long,
    val importoCent: Long,
    val percentuale: Double? = null,
    /** Valore (o percentuale) cambiato a mano nella tabella, invece che importato o calcolato. */
    @androidx.room.ColumnInfo(defaultValue = "0")
    val manuale: Boolean = false,
    /** Valore importato dalla busta paga (o presente prima della modifica a mano), per ripristinarlo. */
    val importoImportatoCent: Long? = null,
    /**
     * Voce calcolata: valori usati al posto di quelli delle voci sommate calcolate, solo per questa
     * voce in questo stipendio ("idVoce:centesimi" separati da virgola).
     */
    val sostituzioni: String? = null
) {
    val mappaSostituzioni: Map<Long, Long>
        get() = sostituzioni.orEmpty().split(',').mapNotNull { p ->
            val (id, cent) = p.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            (id.trim().toLongOrNull() ?: return@mapNotNull null) to (cent.trim().toLongOrNull() ?: return@mapNotNull null)
        }.toMap()

    companion object {
        fun testoSostituzioni(m: Map<Long, Long>): String? = m.entries.joinToString(",") { "${it.key}:${it.value}" }.ifEmpty { null }
    }
}

@Dao
interface StipendiDao {
    @Query("SELECT * FROM stipendi_tipi ORDER BY nome COLLATE NOCASE")
    fun tipiFlow(): Flow<List<TipoStipendio>>

    @Query("SELECT * FROM stipendi_voci ORDER BY tipoId, ordine, id")
    fun vociFlow(): Flow<List<VoceStipendio>>

    @Query("SELECT * FROM stipendi ORDER BY mese")
    fun stipendiFlow(): Flow<List<Stipendio>>

    @Query("SELECT * FROM stipendi_importi")
    fun importiFlow(): Flow<List<ImportoStipendio>>

    @Insert
    suspend fun inserisciTipo(tipo: TipoStipendio): Long

    @Update
    suspend fun aggiornaTipo(tipo: TipoStipendio)

    @Delete
    suspend fun eliminaTipo(tipo: TipoStipendio)

    @Query("SELECT * FROM stipendi_voci WHERE tipoId = :tipoId ORDER BY ordine, id")
    suspend fun voci(tipoId: Long): List<VoceStipendio>

    @Insert
    suspend fun inserisciVoce(voce: VoceStipendio): Long

    @Update
    suspend fun aggiornaVoce(voce: VoceStipendio)

    @Delete
    suspend fun eliminaVoce(voce: VoceStipendio)

    @Query("SELECT * FROM stipendi WHERE tipoId = :tipoId AND mese = :mese ORDER BY id")
    suspend fun stipendiDelMese(tipoId: Long, mese: String): List<Stipendio>

    @Insert
    suspend fun inserisciStipendio(stipendio: Stipendio): Long

    @Update
    suspend fun aggiornaStipendio(stipendio: Stipendio)

    @Delete
    suspend fun eliminaStipendio(stipendio: Stipendio)

    /** Importo di una voce in uno stipendio: sostituisce quello che c'era. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvaImporto(importo: ImportoStipendio)

    @Query("SELECT * FROM stipendi_importi WHERE stipendioId = :stipendioId")
    suspend fun importi(stipendioId: Long): List<ImportoStipendio>

    @Query("SELECT * FROM stipendi")
    suspend fun tuttiStipendi(): List<Stipendio>

    @Query("SELECT * FROM stipendi WHERE id = :id")
    suspend fun stipendioPerId(id: Long): Stipendio?

    @Query("DELETE FROM stipendi_importi WHERE stipendioId = :stipendioId AND voceId = :voceId")
    suspend fun eliminaImporto(stipendioId: Long, voceId: Long)
}
