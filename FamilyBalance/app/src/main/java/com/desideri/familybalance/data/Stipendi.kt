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
    val percentuale: Double? = null
) {
    val idAddendi: List<Long> get() = addendi.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }
}

/**
 * Importi (centesimi) di tutte le [voci] di uno stipendio: quelli registrati ([importi], per id
 * voce) e quelli delle voci calcolate, anche a partire da altre voci calcolate (i riferimenti
 * circolari valgono zero). Senza nessun addendo valorizzato la voce calcolata resta vuota.
 */
fun valoriStipendio(voci: List<VoceStipendio>, importi: Map<Long, Long>): Map<Long, Long> {
    val perId = voci.associateBy { it.id }
    val risultato = HashMap<Long, Long?>()
    fun valore(id: Long, inCorso: Set<Long>): Long? {
        if (id in risultato) return risultato[id]
        val v = perId[id] ?: return null
        if (!v.calcolata) return importi[id]
        if (id in inCorso) return null
        val parti = v.idAddendi.mapNotNull { valore(it, inCorso + id) }
        val somma = if (parti.isEmpty()) null else parti.sum()
        val calcolato = somma?.let { s -> v.percentuale?.let { Math.round(s * it / 100.0) } ?: s }
        risultato[id] = calcolato
        return calcolato
    }
    return voci.mapNotNull { v -> valore(v.id, emptySet())?.let { v.id to it } }.toMap()
}

/** Uno stipendio registrato (es. importato da una busta paga) di una tipologia, per il [mese] "yyyy-MM". */
@Entity(
    tableName = "stipendi",
    foreignKeys = [ForeignKey(entity = TipoStipendio::class, parentColumns = ["id"], childColumns = ["tipoId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["tipoId", "mese"], unique = true)]
)
data class Stipendio(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipoId: Long,
    val mese: String
)

/** Importo (centesimi, con segno) di una voce in uno stipendio. */
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
    val importoCent: Long
)

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

    @Query("SELECT * FROM stipendi WHERE tipoId = :tipoId AND mese = :mese")
    suspend fun stipendio(tipoId: Long, mese: String): Stipendio?

    @Insert
    suspend fun inserisciStipendio(stipendio: Stipendio): Long

    @Update
    suspend fun aggiornaStipendio(stipendio: Stipendio)

    @Delete
    suspend fun eliminaStipendio(stipendio: Stipendio)

    /** Importo di una voce in uno stipendio: sostituisce quello che c'era. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvaImporto(importo: ImportoStipendio)

    @Query("DELETE FROM stipendi_importi WHERE stipendioId = :stipendioId AND voceId = :voceId")
    suspend fun eliminaImporto(stipendioId: Long, voceId: Long)
}
