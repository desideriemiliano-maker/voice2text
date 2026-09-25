package com.desideri.familybalance.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeseDao {
    // --- Conti ---
    @Query("SELECT * FROM conti ORDER BY nome COLLATE NOCASE")
    fun contiFlow(): Flow<List<Conto>>

    @Query("SELECT * FROM conti_valuta")
    fun contiValutaFlow(): Flow<List<ContoValuta>>

    @Query("SELECT * FROM conti_valuta WHERE contoId = :contoId")
    suspend fun contiValutaDelConto(contoId: Long): List<ContoValuta>

    @Insert
    suspend fun inserisciConto(conto: Conto): Long

    @Update
    suspend fun aggiornaConto(conto: Conto)

    @Delete
    suspend fun eliminaConto(conto: Conto)

    @Insert
    suspend fun inserisciContoValuta(contoValuta: ContoValuta): Long

    @Update
    suspend fun aggiornaContoValuta(contoValuta: ContoValuta)

    @Delete
    suspend fun eliminaContoValuta(contoValuta: ContoValuta)

    @Query("SELECT COUNT(*) FROM operazioni WHERE contoValutaId = :id OR contoValutaDestId = :id")
    suspend fun contaOperazioniContoValuta(id: Long): Int

    // --- Voci ---
    @Query("SELECT * FROM voci ORDER BY tipo COLLATE NOCASE, sottotipo COLLATE NOCASE")
    fun vociFlow(): Flow<List<Voce>>

    @Query("SELECT * FROM voci")
    suspend fun voci(): List<Voce>

    @Insert
    suspend fun inserisciVoce(voce: Voce): Long

    @Update
    suspend fun aggiornaVoce(voce: Voce)

    @Delete
    suspend fun eliminaVoce(voce: Voce)

    /** Sposta tutte le operazioni della voce [da] sulla voce [a]; restituisce quante ne ha spostate. */
    @Query("UPDATE operazioni SET voceId = :a WHERE voceId = :da")
    suspend fun spostaOperazioniVoce(da: Long, a: Long): Int

    @Query("SELECT COUNT(*) FROM operazioni WHERE voceId = :voceId")
    suspend fun contaOperazioniVoce(voceId: Long): Int

    // --- Operazioni ---
    @Query("SELECT * FROM operazioni ORDER BY data DESC, id DESC")
    fun operazioniFlow(): Flow<List<Operazione>>

    @Query("SELECT * FROM operazioni WHERE id = :id")
    suspend fun operazione(id: Long): Operazione?

    @Insert
    suspend fun inserisciOperazione(operazione: Operazione): Long

    @Insert
    suspend fun inserisciOperazioni(operazioni: List<Operazione>)

    @Update
    suspend fun aggiornaOperazione(operazione: Operazione)

    @Query("DELETE FROM operazioni WHERE id IN (:ids)")
    suspend fun eliminaOperazioni(ids: List<Long>)

    // --- Associazioni (import estratto conto) ---
    @Query("SELECT * FROM associazioni ORDER BY chiave COLLATE NOCASE")
    fun associazioniFlow(): Flow<List<Associazione>>

    @Query("SELECT * FROM associazioni")
    suspend fun associazioni(): List<Associazione>

    @Insert
    suspend fun inserisciAssociazione(associazione: Associazione): Long

    @Insert
    suspend fun inserisciAssociazioni(associazioni: List<Associazione>)

    @Update
    suspend fun aggiornaAssociazione(associazione: Associazione)

    @Delete
    suspend fun eliminaAssociazione(associazione: Associazione)

    /** Operazioni con quella data e quell'importo sul conto/valuta (per non reimportare doppioni). */
    @Query("SELECT COUNT(*) FROM operazioni WHERE contoValutaId = :contoValutaId AND data = :data AND importoCent = :importoCent")
    suspend fun contaOperazioniUguali(contoValutaId: Long, data: Long, importoCent: Long): Int

    // --- Svuotamento (import da Excel) ---
    @Query("DELETE FROM operazioni")
    suspend fun svuotaOperazioni()

    @Query("DELETE FROM voci")
    suspend fun svuotaVoci()

    @Query("DELETE FROM conti_valuta")
    suspend fun svuotaContiValuta()

    @Query("DELETE FROM conti")
    suspend fun svuotaConti()
}
