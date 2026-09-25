package com.desideri.familybalance.logica

import com.desideri.familybalance.data.Associazione

/** Regole dell'anagrafica associazioni (chiave nella descrizione -> tipo/sottotipo). */
object Associazioni {

    private fun compatta(testo: String) = testo.trim().replace(Regex("\\s+"), " ")

    /**
     * true se [descrizione] contiene [chiave] (senza distinzione di maiuscole e spazi multipli) come
     * "parola intera": i caratteri subito prima e dopo non devono essere lettere o cifre, così una
     * chiave corta come "5" non corrisponde a qualunque descrizione che contenga la cifra 5.
     */
    fun contiene(descrizione: String, chiave: String): Boolean {
        val testo = compatta(descrizione).lowercase()
        val k = compatta(chiave).lowercase()
        if (k.isEmpty()) return false
        var da = 0
        while (true) {
            val i = testo.indexOf(k, da)
            if (i < 0) return false
            val prima = testo.getOrNull(i - 1)
            val dopo = testo.getOrNull(i + k.length)
            val inizioOk = prima == null || !prima.isLetterOrDigit() || !k.first().isLetterOrDigit()
            val fineOk = dopo == null || !dopo.isLetterOrDigit() || !k.last().isLetterOrDigit()
            if (inizioOk && fineOk) return true
            da = i + 1
        }
    }

    /**
     * Associazioni la cui chiave compare in [descrizione], una per destinazione (tipo/sottotipo):
     * se ne resta una sola la si preseleziona, se sono più d'una l'utente sceglie.
     */
    fun candidate(descrizione: String, associazioni: List<Associazione>): List<Associazione> =
        associazioni.filter { contiene(descrizione, it.chiave) }
            .sortedByDescending { it.chiave.length }
            .distinctBy { it.tipo.lowercase() to (it.sottotipo ?: "").lowercase() }

    /**
     * Legge un elenco incollato, una associazione per riga nel formato "Chiave (Tipo)" o
     * "Chiave (Tipo / Sottotipo)": conta l'ultima coppia di parentesi a fine riga, così la chiave
     * può contenere a sua volta parentesi (es. "Pagamento Utenze ( Servizi Pubblici, ... ) (Bollette)").
     */
    fun leggiElenco(testo: String): List<Associazione> =
        testo.lines().mapNotNull { riga ->
            val m = Regex("^(.*)\\(([^()]*)\\)\\s*$").find(riga.trim()) ?: return@mapNotNull null
            val chiave = compatta(m.groupValues[1])
            val destinazione = m.groupValues[2].split("/", limit = 2).map { compatta(it) }
            val tipo = destinazione[0]
            if (chiave.isEmpty() || tipo.isEmpty()) return@mapNotNull null
            Associazione(chiave = chiave, tipo = tipo, sottotipo = destinazione.getOrNull(1)?.ifEmpty { null })
        }
}
