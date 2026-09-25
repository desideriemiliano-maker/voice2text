package com.desideri.familybalance.data

import android.content.Context

/** Valori del menu Impostazioni e dell'account di backup, salvati in SharedPreferences. */
data class Impostazioni(
    /** Risparmio mensile target in EUR, in centesimi. */
    val targetRisparmioCent: Long = 0,
    /** Tasso di cambio usato per esprimere in EUR gli importi in CHF (1 CHF = x EUR). */
    val cambioChfEur: Double = 1.0,
    val emailBackup: String? = null
)

class Preferenze(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("impostazioni", Context.MODE_PRIVATE)

    fun carica(): Impostazioni = Impostazioni(
        targetRisparmioCent = prefs.getLong(CHIAVE_TARGET, 0),
        cambioChfEur = prefs.getString(CHIAVE_CAMBIO, null)?.toDoubleOrNull() ?: 1.0,
        emailBackup = prefs.getString(CHIAVE_EMAIL_BACKUP, null)
    )

    fun salva(impostazioni: Impostazioni) {
        prefs.edit()
            .putLong(CHIAVE_TARGET, impostazioni.targetRisparmioCent)
            .putString(CHIAVE_CAMBIO, impostazioni.cambioChfEur.toString())
            .putString(CHIAVE_EMAIL_BACKUP, impostazioni.emailBackup)
            .apply()
    }

    private companion object {
        const val CHIAVE_TARGET = "target_risparmio_cent"
        const val CHIAVE_CAMBIO = "cambio_chf_eur"
        const val CHIAVE_EMAIL_BACKUP = "email_backup"
    }
}
