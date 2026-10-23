package com.desideri.familybalance.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * PDF (bolletta o ricevuta) allegato a una spesa ricorrente ([voceId]) per il [mese] "yyyy-MM":
 * il file è su Google Drive, [nome] è il nome del file e [link] l'indirizzo per aprirlo.
 */
@Entity(
    tableName = "allegati_ricorrenti",
    foreignKeys = [ForeignKey(entity = Voce::class, parentColumns = ["id"], childColumns = ["voceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["voceId", "mese"])]
)
data class AllegatoRicorrente(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val voceId: Long,
    val mese: String,
    val nome: String,
    val link: String
)

@Dao
interface AllegatiDao {
    @Query("SELECT * FROM allegati_ricorrenti ORDER BY mese, id")
    fun allegatiFlow(): Flow<List<AllegatoRicorrente>>

    @Insert
    suspend fun inserisci(allegato: AllegatoRicorrente): Long

    @Delete
    suspend fun elimina(allegato: AllegatoRicorrente)
}
