package com.desideri.familybalance.backup

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import com.desideri.familybalance.data.AppDatabase
import com.desideri.familybalance.data.Conto
import com.desideri.familybalance.data.ContoValuta
import com.desideri.familybalance.data.Operazione
import com.desideri.familybalance.data.Voce
import java.io.File
import kotlin.math.abs

/** Un conto/valuta presente in un backup, proposto per il ripristino parziale. */
data class ContoBackup(
    val id: Long,
    val nome: String,
    val valuta: String,
    val saldoInizialeCent: Long,
    val operazioni: Int
) {
    val etichetta: String get() = "$nome $valuta"
}

/** Riepilogo di un ripristino parziale. */
data class EsitoRipristinoParziale(
    val ripristinate: Int,
    val sostituite: Int,
    val ricollegate: Int,
    val scollegate: Int,
    val nuoveVoci: Int
)

/**
 * Lettura di un file di backup (database SQLite dell'app, anche di versioni precedenti: le colonne
 * aggiunte dopo, come colore e ordine, possono mancare) senza aprirlo con Room.
 */
class LetturaBackup(file: File) : AutoCloseable {
    private val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)

    private fun Cursor.long(nome: String): Long? = getColumnIndex(nome).takeIf { it >= 0 && !isNull(it) }?.let { getLong(it) }
    private fun Cursor.int(nome: String): Int? = getColumnIndex(nome).takeIf { it >= 0 && !isNull(it) }?.let { getInt(it) }
    private fun Cursor.testo(nome: String): String? = getColumnIndex(nome).takeIf { it >= 0 && !isNull(it) }?.let { getString(it) }

    private fun <T> righe(sql: String, lettura: (Cursor) -> T): List<T> =
        db.rawQuery(sql, null).use { c -> buildList { while (c.moveToNext()) add(lettura(c)) } }

    fun contiValuta(): List<ContoBackup> {
        val conteggi = righe("SELECT contoValutaId, COUNT(*) AS n FROM operazioni GROUP BY contoValutaId") { c ->
            c.long("contoValutaId")!! to c.int("n")!!
        }.toMap()
        return righe(
            "SELECT cv.id AS id, c.nome AS nome, cv.valuta AS valuta, cv.saldoInizialeCent AS saldo " +
                "FROM conti_valuta cv JOIN conti c ON c.id = cv.contoId ORDER BY c.nome COLLATE NOCASE, cv.valuta"
        ) { c ->
            val id = c.long("id")!!
            ContoBackup(id, c.testo("nome").orEmpty(), c.testo("valuta").orEmpty(), c.long("saldo") ?: 0L, conteggi[id] ?: 0)
        }
    }

    fun voci(): List<Voce> = righe("SELECT * FROM voci") { c ->
        Voce(
            id = c.long("id")!!,
            tipo = c.testo("tipo").orEmpty(),
            sottotipo = c.testo("sottotipo"),
            entrata = (c.int("entrata") ?: 0) != 0,
            ricorrente = (c.int("ricorrente") ?: 0) != 0,
            mesiRicorrenza = c.int("mesiRicorrenza") ?: 1,
            meseInizio = c.testo("meseInizio"),
            importoPrevistoCent = c.long("importoPrevistoCent"),
            colore = c.int("colore"),
            obsoleta = (c.int("obsoleta") ?: 0) != 0
        )
    }

    fun operazioni(): List<Operazione> = righe("SELECT * FROM operazioni") { c ->
        Operazione(
            id = c.long("id")!!,
            contoValutaId = c.long("contoValutaId")!!,
            data = c.long("data")!!,
            importoCent = c.long("importoCent")!!,
            voceId = c.long("voceId"),
            trasferimento = (c.int("trasferimento") ?: 0) != 0,
            contoValutaDestId = c.long("contoValutaDestId"),
            collegataId = c.long("collegataId"),
            note = c.testo("note"),
            ordine = c.long("ordine")
        )
    }

    override fun close() = db.close()
}

/**
 * Ripristina da un backup solo le operazioni (e il saldo iniziale) dei conti/valuta scelti,
 * lasciando intatti gli altri. I conti/valuta del backup sono abbinati a quelli attuali per nome
 * del conto e valuta (creati se mancano), le voci per tipo/sottotipo (create se mancano).
 *
 * Collegamenti degli spostamenti:
 * - tra operazioni ripristinate: mantenuti, con i nuovi id;
 * - verso un conto non ripristinato: si cerca nel conto attuale la stessa contro-operazione (stesso
 *   importo, data uguale o entro 7 giorni, senza collegamento o collegata a un'operazione sostituita)
 *   e la si ricollega; se non c'è l'operazione resta non collegata;
 * - le operazioni attuali degli altri conti collegate a operazioni sostituite e non ricollegate
 *   perdono il collegamento (non vengono mai eliminate).
 */
class RipristinoParziale(private val db: AppDatabase) {

    suspend fun esegui(file: File, cvBackupScelti: Set<Long>): EsitoRipristinoParziale {
        val (contiBackup, vociBackup, opsBackup) = LetturaBackup(file).use { Triple(it.contiValuta(), it.voci(), it.operazioni()) }
        val contiBackupPerId = contiBackup.associateBy { it.id }
        val opsBackupPerId = opsBackup.associateBy { it.id }
        val dao = db.dao()

        var ripristinate = 0
        var sostituite = 0
        var ricollegate = 0
        var scollegate = 0
        var nuoveVoci = 0

        db.withTransaction {
            val conti = dao.conti().toMutableList()
            val contiValuta = dao.contiValuta().toMutableList()

            /** Conto/valuta attuale corrispondente a uno del backup; se [crea] lo crea quando manca. */
            suspend fun cvAttuale(idBackup: Long?, crea: Boolean): Long? {
                val b = idBackup?.let { contiBackupPerId[it] } ?: return null
                val conto = conti.firstOrNull { it.nome.equals(b.nome, ignoreCase = true) }
                    ?: if (crea) Conto(nome = b.nome).let { it.copy(id = dao.inserisciConto(it)) }.also { conti += it } else return null
                contiValuta.firstOrNull { it.contoId == conto.id && it.valuta == b.valuta }?.let { return it.id }
                if (!crea) return null
                val nuovo = ContoValuta(contoId = conto.id, valuta = b.valuta, saldoInizialeCent = b.saldoInizialeCent)
                return nuovo.copy(id = dao.inserisciContoValuta(nuovo)).also { contiValuta += it }.id
            }

            // Conti/valuta da ripristinare: backup -> attuale, con il saldo iniziale del backup.
            val destinazioni = HashMap<Long, Long>()
            for (idBackup in cvBackupScelti) {
                val attuale = cvAttuale(idBackup, crea = true) ?: continue
                destinazioni[idBackup] = attuale
                val cv = contiValuta.first { it.id == attuale }
                dao.aggiornaContoValuta(cv.copy(saldoInizialeCent = contiBackupPerId.getValue(idBackup).saldoInizialeCent))
            }

            // Voci: abbinate per tipo/sottotipo, create se mancano.
            val voci = dao.voci().toMutableList()
            val vociBackupPerId = vociBackup.associateBy { it.id }
            suspend fun voceAttuale(idBackup: Long?): Long? {
                val b = idBackup?.let { vociBackupPerId[it] } ?: return null
                voci.firstOrNull { it.tipo.equals(b.tipo, true) && (it.sottotipo ?: "").equals(b.sottotipo ?: "", true) }?.let { return it.id }
                val id = dao.inserisciVoce(b.copy(id = 0))
                voci += b.copy(id = id)
                nuoveVoci++
                return id
            }

            // Operazioni attuali dei conti ripristinati: sostituite (solo quelle, non le contro-operazioni).
            val attuali = dao.operazioni()
            val eliminate = attuali.filter { it.contoValutaId in destinazioni.values }.map { it.id }.toHashSet()
            eliminate.chunked(500).forEach { dao.eliminaOperazioni(it) }
            sostituite = eliminate.size

            // Operazioni del backup da ripristinare, inserite con nuovi id.
            val daRipristinare = opsBackup.filter { it.contoValutaId in destinazioni.keys }
            val nuoviId = HashMap<Long, Long>()
            for (op in daRipristinare) {
                nuoviId[op.id] = dao.inserisciOperazione(
                    op.copy(
                        id = 0,
                        contoValutaId = destinazioni.getValue(op.contoValutaId),
                        voceId = voceAttuale(op.voceId),
                        contoValutaDestId = cvAttuale(op.contoValutaDestId, crea = false),
                        collegataId = null
                    )
                )
            }
            ripristinate = daRipristinare.size

            // Collegamenti degli spostamenti.
            val rimaste = attuali.filter { it.id !in eliminate }.associateBy { it.id }.toMutableMap()
            val ricollegateAttuali = HashSet<Long>()
            for (op in daRipristinare) {
                val collegataBackup = op.collegataId ?: continue
                val nuovoId = nuoviId.getValue(op.id)
                val nuovaCollegata: Long? = nuoviId[collegataBackup] ?: run {
                    // Contro-operazione su un conto non ripristinato: la stessa nel conto attuale.
                    val b = opsBackupPerId[collegataBackup] ?: return@run null
                    val cvB = cvAttuale(b.contoValutaId, crea = false) ?: return@run null
                    rimaste.values
                        .filter {
                            it.contoValutaId == cvB && it.importoCent == b.importoCent && it.trasferimento &&
                                it.id !in ricollegateAttuali && (it.collegataId == null || it.collegataId in eliminate) &&
                                abs(it.data - b.data) <= 7
                        }
                        .minByOrNull { abs(it.data - b.data) }
                        ?.let { x ->
                            val aggiornata = x.copy(collegataId = nuovoId, contoValutaDestId = destinazioni.getValue(op.contoValutaId))
                            dao.aggiornaOperazione(aggiornata)
                            rimaste[x.id] = aggiornata
                            ricollegateAttuali += x.id
                            ricollegate++
                            x.id
                        }
                }
                if (nuovaCollegata != null) {
                    dao.operazione(nuovoId)?.let { dao.aggiornaOperazione(it.copy(collegataId = nuovaCollegata)) }
                }
            }
            // Collegamenti rimasti verso operazioni sostituite: tolti per non lasciare riferimenti orfani.
            for (x in rimaste.values) {
                if (x.collegataId != null && x.collegataId in eliminate && x.id !in ricollegateAttuali) {
                    dao.aggiornaOperazione(x.copy(collegataId = null))
                    scollegate++
                }
            }
        }
        return EsitoRipristinoParziale(ripristinate, sostituite, ricollegate, scollegate, nuoveVoci)
    }
}
