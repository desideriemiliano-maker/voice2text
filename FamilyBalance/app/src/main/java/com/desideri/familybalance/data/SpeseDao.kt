package com.desideri.familybalance.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    // Letture dirette (senza Flow) per le operazioni in transazione, es. il ripristino parziale.
    @Query("SELECT * FROM conti")
    suspend fun conti(): List<Conto>

    @Query("SELECT * FROM conti_valuta")
    suspend fun contiValuta(): List<ContoValuta>

    @Query("SELECT * FROM operazioni")
    suspend fun operazioni(): List<Operazione>

    @Query("SELECT * FROM voci")
    suspend fun voci(): List<Voce>

    @Insert
    suspend fun inserisciVoce(voce: Voce): Long

    @Update
    suspend fun aggiornaVoce(voce: Voce)

    @Delete
    suspend fun eliminaVoce(voce: Voce)

    /** Aggiunge [testo] alle note delle operazioni della voce [voceId] (dopo " / " se c'è già una nota). */
    @Query("UPDATE operazioni SET note = CASE WHEN note IS NULL OR TRIM(note) = '' THEN :testo ELSE note || ' / ' || :testo END WHERE voceId = :voceId")
    suspend fun aggiungiANote(voceId: Long, testo: String): Int

    /** Sposta tutte le operazioni della voce [da] sulla voce [a]; restituisce quante ne ha spostate. */
    @Query("UPDATE operazioni SET voceId = :a WHERE voceId = :da")
    suspend fun spostaOperazioniVoce(da: Long, a: Long): Int

    @Query("SELECT COUNT(*) FROM operazioni WHERE voceId = :voceId")
    suspend fun contaOperazioniVoce(voceId: Long): Int

    // --- Operazioni ---
    @Query("SELECT * FROM operazioni ORDER BY data DESC, ordine IS NULL, ordine DESC, id DESC")
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

    // --- Personalizzazioni delle scadenze ricorrenti ---
    @Query("SELECT * FROM previsioni_ricorrenti")
    fun previsioniFlow(): Flow<List<PrevisioneRicorrente>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvaPrevisione(previsione: PrevisioneRicorrente): Long

    @Query("DELETE FROM previsioni_ricorrenti WHERE voceId = :voceId AND mese = :mese")
    suspend fun eliminaPrevisione(voceId: Long, mese: String)

    @Query("DELETE FROM previsioni_ricorrenti WHERE voceId = :voceId")
    suspend fun eliminaPrevisioniVoce(voceId: Long)

    // --- Cambi mensili CHF/EUR ---
    @Query("SELECT * FROM cambi ORDER BY mese")
    fun cambiFlow(): Flow<List<Cambio>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvaCambio(cambio: Cambio)

    @Query("DELETE FROM cambi WHERE mese = :mese")
    suspend fun eliminaCambio(mese: String)

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
