package com.desideri.familybalance.estratto

import java.time.LocalDate

/** Movimenti letti da un estratto conto Excel, con gli avvisi (es. valuta diversa dal conto). */
data class EsitoEstrattoExcel(val movimenti: List<MovimentoEstratto>, val avvisi: List<String>)

/**
 * Lettura diretta (senza Gemini) di un estratto conto esportato in Excel dalla banca: si cerca la
 * riga d'intestazione (una colonna "Data…" e una "Importo" o "Addebiti"/"Accrediti") e da lì si
 * leggono i movimenti. Le colonne sono riconosciute dal nome:
 * - date: "Data contabile"/"registrazione", "Data valuta", altre "Data…" come data operazione
 *   ("Non contabilizzato" o celle vuote = data assente);
 * - importo con segno, oppure uscite (Dare/Addebiti/Uscite) e entrate (Avere/Accrediti/Entrate);
 * - descrizione: Descrizione, Causale, Dettaglio, Beneficiario… unite con " · ";
 * - valuta per riga ("Divisa"/"Valuta" senza "data"), altrimenti quella indicata sopra
 *   l'intestazione ("Divisa C/C: EUR") o quella del conto.
 */
object EstrattoExcel {

    /** [righe]: numero di riga del file e celle come testo (date ISO o gg/mm/aaaa, numeri come testo). */
    fun leggi(righe: List<Pair<Int, List<String>>>, valutaConto: String): EsitoEstrattoExcel? {
        val iIntestazione = righe.indexOfFirst { (_, celle) -> eIntestazione(celle) }
        if (iIntestazione < 0) return null
        val intestazione = righe[iIntestazione].second.map { normalizza(it) }

        val colonneData = intestazione.indices.filter { c -> intestazione[c].let { it.startsWith("data") || it.startsWith("date") } }
        val contabile = colonneData.firstOrNull { c -> intestazione[c].let { "contabil" in it || "registr" in it } }
        val valuta = colonneData.firstOrNull { c -> "valuta" in intestazione[c] }
        val operazione = colonneData.firstOrNull { it != contabile && it != valuta }
        val importo = intestazione.indexOfFirst { it.startsWith("importo") || it == "amount" || it.startsWith("ammontare") }.takeIf { it >= 0 }
        val uscite = intestazione.indexOfFirst { h -> PAROLE_USCITE.any { h.startsWith(it) } }.takeIf { it >= 0 }
        val entrate = intestazione.indexOfFirst { h -> PAROLE_ENTRATE.any { h.startsWith(it) } }.takeIf { it >= 0 }
        val descrizioni = intestazione.indices.filter { c -> c !in colonneData && PAROLE_DESCRIZIONE.any { it in intestazione[c] } }
        val divisa = intestazione.indices.firstOrNull { c -> c !in colonneData && (intestazione[c] == "divisa" || intestazione[c] == "valuta") }

        // Valuta indicata sopra l'intestazione (es. "Divisa C/C: | EUR").
        val valutaFile = righe.take(iIntestazione).firstNotNullOfOrNull { (_, celle) ->
            val i = celle.indexOfFirst { normalizza(it).startsWith("divisa") || normalizza(it).startsWith("valuta") }
            if (i < 0) null else celle.drop(i + 1).firstOrNull { CODICE_VALUTA.matches(it.trim()) }?.trim()?.uppercase()
        }

        val movimenti = righe.drop(iIntestazione + 1).mapNotNull { (numero, celle) ->
            fun cella(c: Int?) = c?.let { celle.getOrNull(it)?.trim() }.orEmpty()
            val cent = when {
                importo != null -> centDa(cella(importo))
                else -> {
                    val u = centDa(cella(uscite))?.let { -kotlin.math.abs(it) }
                    val e = centDa(cella(entrate))?.let { kotlin.math.abs(it) }
                    if (u == null && e == null) null else (u ?: 0) + (e ?: 0)
                }
            } ?: return@mapNotNull null
            if (cent == 0L) return@mapNotNull null
            val dOperazione = dataDa(cella(operazione))
            val dContabile = dataDa(cella(contabile))
            val dValuta = dataDa(cella(valuta))
            if (dOperazione == null && dContabile == null && dValuta == null) return@mapNotNull null
            val descrizione = descrizioni.map { cella(it) }.filter { it.isNotBlank() }.distinct().joinToString(" · ")
            MovimentoEstratto(
                valuta = cella(divisa).takeIf { CODICE_VALUTA.matches(it) }?.uppercase() ?: valutaFile ?: valutaConto,
                importoCent = cent,
                descrizione = descrizione,
                dataOperazione = dOperazione,
                dataContabile = dContabile,
                dataValuta = dValuta,
                rigaFile = numero
            )
        }
        val avvisi = buildList {
            val altre = movimenti.map { it.valuta }.filter { it != valutaConto }.distinct()
            if (altre.isNotEmpty()) add("Valuta del file (${altre.joinToString()}) diversa da quella del conto ($valutaConto)")
        }
        return EsitoEstrattoExcel(movimenti, avvisi)
    }

    private val PAROLE_USCITE = listOf("dare", "addebit", "uscit")
    private val PAROLE_ENTRATE = listOf("avere", "accredit", "entrat")
    private val PAROLE_DESCRIZIONE = listOf("descrizion", "causale", "dettagl", "beneficiar", "controparte", "operazione", "note")
    private val CODICE_VALUTA = Regex("[A-Za-z]{3}")

    private fun normalizza(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun eIntestazione(celle: List<String>): Boolean {
        val h = celle.map { normalizza(it) }
        val haData = h.any { it.startsWith("data") || it.startsWith("date") }
        val haImporto = h.any { c -> c.startsWith("importo") || c == "amount" || c.startsWith("ammontare") } ||
            h.any { c -> PAROLE_USCITE.any { c.startsWith(it) } } && h.any { c -> PAROLE_ENTRATE.any { c.startsWith(it) } }
        return haData && haImporto
    }

    /** Importo in centesimi da "−1.234,56", "1234.56", "-70", "€ 12,00"; null se non è un numero. */
    fun centDa(testo: String): Long? {
        var t = testo.trim().replace("−", "-").replace(Regex("[^0-9,.+\\-]"), "")
        if (t.isEmpty() || t.none { it.isDigit() }) return null
        val virgola = t.lastIndexOf(',')
        val punto = t.lastIndexOf('.')
        t = when {
            virgola >= 0 && punto >= 0 && virgola > punto -> t.replace(".", "").replace(',', '.')
            virgola >= 0 && punto >= 0 -> t.replace(",", "")
            virgola >= 0 -> t.replace(',', '.')
            else -> t
        }
        return t.toBigDecimalOrNull()?.movePointRight(2)?.setScale(0, java.math.RoundingMode.HALF_UP)?.toLong()
    }

    /** Data da "30/9/2026", "30.09.26", "30-09-2026" o "2026-09-30"; null altrimenti. */
    fun dataDa(testo: String): LocalDate? {
        val t = testo.trim().substringBefore(' ').substringBefore('T')
        Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})").matchEntire(t)?.let { m ->
            val (a, me, g) = m.destructured
            return runCatching { LocalDate.of(a.toInt(), me.toInt(), g.toInt()) }.getOrNull()
        }
        Regex("(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{2}|\\d{4})").matchEntire(t)?.let { m ->
            val (g, me, a) = m.destructured
            val anno = if (a.length == 2) 2000 + a.toInt() else a.toInt()
            return runCatching { LocalDate.of(anno, me.toInt(), g.toInt()) }.getOrNull()
        }
        return null
    }
}
