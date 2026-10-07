package com.desideri.familybalance.data

import android.content.Context

/** Valori del menu Impostazioni e dell'account di backup, salvati in SharedPreferences. */
data class Impostazioni(
    /** Risparmio mensile target in EUR, in centesimi. */
    val targetRisparmioCent: Long = 0,
    /** Tasso di cambio usato per esprimere in EUR gli importi in CHF (1 CHF = x EUR). */
    val cambioChfEur: Double = 1.0,
    val emailBackup: String? = null,
    /** Richiede impronta/volto/PIN del dispositivo all'apertura dell'app. */
    val bloccoBiometrico: Boolean = true,
    /** Quanti backup su Google Drive conservare: dopo ogni backup i più vecchi vengono eliminati. */
    val backupDaMantenere: Int = 3,
    /** Minuti in background dopo cui, al ritorno, si richiede di nuovo lo sblocco. */
    val minutiBlocco: Int = 3,
    /** Giorni entro cui due operazioni dello stesso importo sullo stesso conto sono possibili duplicati. */
    val giorniDuplicati: Int = 3,
    /** Conferma del controllo duplicati: fino a oggi meno questi giorni. */
    val giorniConfermaDuplicati: Int = 7,
    /** Intorno (giorni) in cui mostrare, per ogni possibile duplicato, le altre operazioni dello stesso importo. */
    val giorniIntornoDuplicati: Int = 10,
    /** Mesi per la media dello stipendio del mese corrente quando non è ancora arrivato. */
    val mesiMediaStipendio: Int = 5,
    /** Giorno del mese in cui arriva lo stipendio. */
    val giornoStipendio: Int = 20,
    /** Se il giorno dello stipendio cade nel weekend si considera il lunedì successivo. */
    val stipendioGiornoLavorativo: Boolean = true
)

class Preferenze(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("impostazioni", Context.MODE_PRIVATE)

    fun carica(): Impostazioni = Impostazioni(
        targetRisparmioCent = prefs.getLong(CHIAVE_TARGET, 0),
        cambioChfEur = prefs.getString(CHIAVE_CAMBIO, null)?.toDoubleOrNull() ?: 1.0,
        emailBackup = prefs.getString(CHIAVE_EMAIL_BACKUP, null),
        bloccoBiometrico = prefs.getBoolean(CHIAVE_BLOCCO_BIOMETRICO, true),
        backupDaMantenere = prefs.getInt(CHIAVE_BACKUP_DA_MANTENERE, 3),
        minutiBlocco = prefs.getInt(CHIAVE_MINUTI_BLOCCO, 3),
        giorniDuplicati = prefs.getInt(CHIAVE_GIORNI_DUPLICATI, 3),
        giorniConfermaDuplicati = prefs.getInt(CHIAVE_GIORNI_CONFERMA_DUPLICATI, 7),
        giorniIntornoDuplicati = prefs.getInt(CHIAVE_GIORNI_INTORNO_DUPLICATI, 10),
        mesiMediaStipendio = prefs.getInt(CHIAVE_MESI_MEDIA_STIPENDIO, 5),
        giornoStipendio = prefs.getInt(CHIAVE_GIORNO_STIPENDIO, 20),
        stipendioGiornoLavorativo = prefs.getBoolean(CHIAVE_STIPENDIO_LAVORATIVO, true)
    )

    /** Con [subito] la scrittura è sincrona (es. prima di riavviare l'app dopo un ripristino). */
    fun salva(impostazioni: Impostazioni, subito: Boolean = false) {
        val editor = prefs.edit()
            .putLong(CHIAVE_TARGET, impostazioni.targetRisparmioCent)
            .putString(CHIAVE_CAMBIO, impostazioni.cambioChfEur.toString())
            .putString(CHIAVE_EMAIL_BACKUP, impostazioni.emailBackup)
            .putBoolean(CHIAVE_BLOCCO_BIOMETRICO, impostazioni.bloccoBiometrico)
            .putInt(CHIAVE_BACKUP_DA_MANTENERE, impostazioni.backupDaMantenere)
            .putInt(CHIAVE_MINUTI_BLOCCO, impostazioni.minutiBlocco)
            .putInt(CHIAVE_GIORNI_DUPLICATI, impostazioni.giorniDuplicati)
            .putInt(CHIAVE_GIORNI_CONFERMA_DUPLICATI, impostazioni.giorniConfermaDuplicati)
            .putInt(CHIAVE_GIORNI_INTORNO_DUPLICATI, impostazioni.giorniIntornoDuplicati)
            .putInt(CHIAVE_MESI_MEDIA_STIPENDIO, impostazioni.mesiMediaStipendio)
            .putInt(CHIAVE_GIORNO_STIPENDIO, impostazioni.giornoStipendio)
            .putBoolean(CHIAVE_STIPENDIO_LAVORATIVO, impostazioni.stipendioGiornoLavorativo)
        if (subito) editor.commit() else editor.apply()
    }

    /** Data da registrare negli import di estratti conto, ricordata per conto. */
    fun caricaTipoDataEstratto(contoId: Long): com.desideri.familybalance.estratto.TipoData =
        prefs.getString("tipo_data_estratto_$contoId", null)
            ?.let { nome -> com.desideri.familybalance.estratto.TipoData.entries.firstOrNull { it.name == nome } }
            ?: com.desideri.familybalance.estratto.TipoData.OPERAZIONE

    fun salvaTipoDataEstratto(contoId: Long, tipo: com.desideri.familybalance.estratto.TipoData) {
        prefs.edit().putString("tipo_data_estratto_$contoId", tipo.name).apply()
    }

    /** Ordine delle colonne del report [report] (chiavi delle colonne), vuoto se mai cambiato. */
    fun caricaOrdineColonne(report: String): List<String> =
        prefs.getString(PREFISSO_ORDINE + report, null)?.split('|')?.filter { it.isNotEmpty() }.orEmpty()

    fun salvaOrdineColonne(report: String, chiavi: List<String>) {
        prefs.edit().putString(PREFISSO_ORDINE + report, chiavi.joinToString("|")).apply()
    }

    /** Giorno (epochDay) fino a cui il controllo duplicati del conto/valuta è confermato, null se mai. */
    fun duplicatiConfermatiFino(contoValutaId: Long): Long? =
        prefs.getString(PREFISSO_DUPLICATI + contoValutaId, null)?.toLongOrNull()

    fun salvaDuplicatiConfermatiFino(contoValutaId: Long, giorno: Long?) {
        prefs.edit().apply { if (giorno == null) remove(PREFISSO_DUPLICATI + contoValutaId) else putString(PREFISSO_DUPLICATI + contoValutaId, giorno.toString()) }.apply()
    }

    /** Preferenze da includere nel backup oltre alle Impostazioni: ordine delle colonne e conferme dei duplicati. */
    fun altrePerBackup(): Map<String, String> =
        prefs.all.filterKeys { k -> PREFISSI_BACKUP.any { k.startsWith(it) } }.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    /** Ripristina le preferenze [valori] (come da [altrePerBackup]); scrittura sincrona. */
    fun ripristinaAltre(valori: Map<String, String>) {
        val editor = prefs.edit()
        valori.filterKeys { k -> PREFISSI_BACKUP.any { k.startsWith(it) } }.forEach { (k, v) -> editor.putString(k, v) }
        editor.commit()
    }

    /** Colonne scelte per il riscontro da Excel del conto/valuta [contoValutaId]. */
    fun caricaColonneExcel(contoValutaId: Long): com.desideri.familybalance.estratto.ColonneSalvate? =
        prefs.getString("colonne_excel_$contoValutaId", null)?.let { testo ->
            runCatching {
                val j = org.json.JSONObject(testo)
                val descr = j.optJSONArray("descrizioni")
                com.desideri.familybalance.estratto.ColonneSalvate(
                    data = j.getString("data"),
                    importo = j.getString("importo"),
                    entrate = j.optString("entrate").ifBlank { null },
                    descrizioni = (0 until (descr?.length() ?: 0)).map { descr!!.getString(it) }
                )
            }.getOrNull()
        }

    fun salvaColonneExcel(contoValutaId: Long, c: com.desideri.familybalance.estratto.ColonneSalvate) {
        val j = org.json.JSONObject()
            .put("data", c.data)
            .put("importo", c.importo)
            .put("entrate", c.entrate.orEmpty())
            .put("descrizioni", org.json.JSONArray(c.descrizioni))
        prefs.edit().putString("colonne_excel_$contoValutaId", j.toString()).apply()
    }

    private companion object {
        const val PREFISSO_ORDINE = "ordine_colonne_"
        val PREFISSI_BACKUP = listOf(PREFISSO_ORDINE, "duplicati_fino_")
        const val CHIAVE_TARGET = "target_risparmio_cent"
        const val CHIAVE_CAMBIO = "cambio_chf_eur"
        const val CHIAVE_EMAIL_BACKUP = "email_backup"
        const val CHIAVE_BLOCCO_BIOMETRICO = "blocco_biometrico"
        const val CHIAVE_BACKUP_DA_MANTENERE = "backup_da_mantenere"
        const val CHIAVE_MINUTI_BLOCCO = "minuti_blocco"
        const val CHIAVE_GIORNI_DUPLICATI = "giorni_duplicati"
        const val CHIAVE_GIORNI_CONFERMA_DUPLICATI = "giorni_conferma_duplicati"
        const val CHIAVE_GIORNI_INTORNO_DUPLICATI = "giorni_intorno_duplicati"
        const val CHIAVE_MESI_MEDIA_STIPENDIO = "mesi_media_stipendio"
        const val CHIAVE_GIORNO_STIPENDIO = "giorno_stipendio"
        const val CHIAVE_STIPENDIO_LAVORATIVO = "stipendio_giorno_lavorativo"
        const val PREFISSO_DUPLICATI = "duplicati_fino_"
    }
}
