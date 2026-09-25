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
    val bloccoBiometrico: Boolean = true
)

class Preferenze(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("impostazioni", Context.MODE_PRIVATE)

    fun carica(): Impostazioni = Impostazioni(
        targetRisparmioCent = prefs.getLong(CHIAVE_TARGET, 0),
        cambioChfEur = prefs.getString(CHIAVE_CAMBIO, null)?.toDoubleOrNull() ?: 1.0,
        emailBackup = prefs.getString(CHIAVE_EMAIL_BACKUP, null),
        bloccoBiometrico = prefs.getBoolean(CHIAVE_BLOCCO_BIOMETRICO, true)
    )

    fun salva(impostazioni: Impostazioni) {
        prefs.edit()
            .putLong(CHIAVE_TARGET, impostazioni.targetRisparmioCent)
            .putString(CHIAVE_CAMBIO, impostazioni.cambioChfEur.toString())
            .putString(CHIAVE_EMAIL_BACKUP, impostazioni.emailBackup)
            .putBoolean(CHIAVE_BLOCCO_BIOMETRICO, impostazioni.bloccoBiometrico)
            .apply()
    }

    private val mappature = context.applicationContext.getSharedPreferences("mappature_bollette", Context.MODE_PRIVATE)

    /**
     * Scelte memorizzate per l'import da Excel: chiave della combinazione tipo/sottotipo "Bollette"
     * -> spesa ricorrente scelta (vedi importazione.SceltaBollette).
     */
    fun caricaMappatureBollette(): Map<String, String> =
        mappature.all.mapNotNull { (chiave, valore) -> (valore as? String)?.let { chiave to it } }.toMap()

    fun salvaMappatureBollette(scelte: Map<String, String>) {
        val editor = mappature.edit()
        scelte.forEach { (chiave, valore) -> editor.putString(chiave, valore) }
        editor.apply()
    }

    private companion object {
        const val CHIAVE_TARGET = "target_risparmio_cent"
        const val CHIAVE_CAMBIO = "cambio_chf_eur"
        const val CHIAVE_EMAIL_BACKUP = "email_backup"
        const val CHIAVE_BLOCCO_BIOMETRICO = "blocco_biometrico"
    }
}
