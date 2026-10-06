package com.desideri.familybalance.estratto

import java.time.LocalDate

/** Movimenti letti da un estratto conto Excel, con gli avvisi (es. valuta diversa dal conto). */
data class EsitoEstrattoExcel(val movimenti: List<MovimentoEstratto>, val avvisi: List<String>)

/**
 * Colonne scelte dall'utente per leggere un estratto Excel (indici 0-based delle celle):
 * [rigaIntestazione] è l'indice (in `righe`) della riga con i nomi delle colonne, i movimenti sono
 * le righe successive. Con [entrate] l'[importo] è la colonna delle uscite (Dare/Addebiti) e
 * [entrate] quella degli accrediti; senza, [importo] ha già il segno.
 */
data class ColonneExcel(
    val rigaIntestazione: Int,
    val data: Int,
    val importo: Int,
    val entrate: Int? = null,
    val descrizioni: List<Int> = emptyList()
)

/**
 * Scelta delle colonne memorizzata per conto, per nome di colonna (così regge anche se l'ordine
 * cambia): vedi [EstrattoExcel.proponi].
 */
data class ColonneSalvate(val data: String, val importo: String, val entrate: String?, val descrizioni: List<String>)

/**
 * Lettura diretta (senza AI) di un estratto conto esportato in Excel dalla banca, con le colonne
 * (data, importo, descrizione) scelte dall'utente su una riga d'intestazione. [proponi] suggerisce
 * la scelta: quella memorizzata per il conto se i nomi delle colonne ci sono ancora, altrimenti
 * una riconosciuta dai nomi ("Data…", "Importo" o "Addebiti"/"Accrediti", "Descrizione"…).
 */
object EstrattoExcel {

    /** Una proposta di colonne per [righe] (numero di riga del file e celle come testo); null se il foglio è vuoto. */
    fun proponi(righe: List<Pair<Int, List<String>>>, salvate: ColonneSalvate?): ColonneExcel? {
        if (righe.none { r -> r.second.any { it.isNotBlank() } }) return null
        // Riga con le colonne memorizzate, poi una che sembra un'intestazione, poi la prima non vuota.
        salvate?.let { s ->
            righe.indices.firstNotNullOfOrNull { i -> daSalvate(righe, i, s) }?.let { return it }
        }
        val i = righe.indexOfFirst { (_, celle) -> eIntestazione(celle) }
            .takeIf { it >= 0 } ?: righe.indexOfFirst { r -> r.second.any { it.isNotBlank() } }
        return proponiSuRiga(righe, i)
    }

    /** Proposta di colonne con [indice] come riga d'intestazione (nomi riconosciuti o prime colonne). */
    fun proponiSuRiga(righe: List<Pair<Int, List<String>>>, indice: Int): ColonneExcel {
        val h = righe[indice].second.map { normalizza(it) }
        val date = h.indices.filter { h[it].startsWith("data") || h[it].startsWith("date") }
        val data = date.firstOrNull { "contabil" in h[it] || "registr" in h[it] } ?: date.firstOrNull() ?: 0
        val importo = h.indexOfFirst { it.startsWith("importo") || it == "amount" || it.startsWith("ammontare") }.takeIf { it >= 0 }
        val uscite = h.indexOfFirst { c -> PAROLE_USCITE.any { c.startsWith(it) } }.takeIf { it >= 0 }
        val entrate = h.indexOfFirst { c -> PAROLE_ENTRATE.any { c.startsWith(it) } }.takeIf { it >= 0 }
        val descrizioni = h.indices.filter { c -> c !in date && PAROLE_DESCRIZIONE.any { it in h[c] } }
        return ColonneExcel(
            rigaIntestazione = indice,
            data = data,
            importo = importo ?: uscite ?: h.indices.lastOrNull { it != data } ?: 0,
            entrate = if (importo == null && uscite != null) entrate else null,
            descrizioni = descrizioni
        )
    }

    /** La scelta come nomi di colonna, da memorizzare per il conto. */
    fun daSalvare(righe: List<Pair<Int, List<String>>>, c: ColonneExcel): ColonneSalvate {
        val h = righe[c.rigaIntestazione].second
        fun nome(i: Int) = h.getOrNull(i).orEmpty().trim()
        return ColonneSalvate(nome(c.data), nome(c.importo), c.entrate?.let(::nome), c.descrizioni.map(::nome))
    }

    private fun daSalvate(righe: List<Pair<Int, List<String>>>, indice: Int, s: ColonneSalvate): ColonneExcel? {
        val h = righe[indice].second.map { normalizza(it) }
        fun trova(nome: String) = h.indexOf(normalizza(nome)).takeIf { it >= 0 && nome.isNotBlank() }
        val data = trova(s.data) ?: return null
        val importo = trova(s.importo) ?: return null
        return ColonneExcel(indice, data, importo, s.entrate?.let(::trova), s.descrizioni.mapNotNull(::trova))
    }

    /** I movimenti delle righe dopo l'intestazione con le colonne [c]; si saltano le righe senza data o importo. */
    fun leggi(righe: List<Pair<Int, List<String>>>, c: ColonneExcel, valutaConto: String, oggi: LocalDate = LocalDate.now()): EsitoEstrattoExcel {
        // Valuta indicata sopra l'intestazione (es. "Divisa C/C: | EUR").
        val valutaFile = righe.take(c.rigaIntestazione).firstNotNullOfOrNull { (_, celle) ->
            val i = celle.indexOfFirst { normalizza(it).startsWith("divisa") || normalizza(it).startsWith("valuta") }
            if (i < 0) null else celle.drop(i + 1).firstOrNull { CODICE_VALUTA.matches(it.trim()) }?.trim()?.uppercase()
        }
        val movimenti = righe.drop(c.rigaIntestazione + 1).mapNotNull { (numero, celle) ->
            fun cella(i: Int?) = i?.let { celle.getOrNull(it)?.trim() }.orEmpty()
            val cent = if (c.entrate == null) centDa(cella(c.importo)) else {
                val u = centDa(cella(c.importo))?.let { -kotlin.math.abs(it) }
                val e = centDa(cella(c.entrate))?.let { kotlin.math.abs(it) }
                if (u == null && e == null) null else (u ?: 0) + (e ?: 0)
            }
            if (cent == null || cent == 0L) return@mapNotNull null
            // Data non leggibile (es. "Non contabilizzato"): il movimento si tiene con la data di oggi.
            val testoData = cella(c.data)
            val letta = dataDa(testoData)
            if (letta == null && testoData.isBlank()) return@mapNotNull null
            val data = letta ?: oggi
            MovimentoEstratto(
                valuta = valutaFile ?: valutaConto,
                importoCent = cent,
                descrizione = c.descrizioni.map { cella(it) }.filter { it.isNotBlank() }.distinct().joinToString(" · "),
                dataOperazione = data,
                rigaFile = numero,
                nonContabilizzato = letta == null
            )
        }
        val avvisi = buildList {
            movimenti.count { it.nonContabilizzato }.takeIf { it > 0 }?.let {
                add("$it movimenti non ancora contabilizzati: registrati con la data di oggi, da sanare con un riscontro successivo")
            }
            if (valutaFile != null && valutaFile != valutaConto) add("Valuta del file ($valutaFile) diversa da quella del conto ($valutaConto)")
        }
        return EsitoEstrattoExcel(movimenti, avvisi)
    }

    private val PAROLE_USCITE = listOf("dare", "addebit", "uscit")
    private val PAROLE_ENTRATE = listOf("avere", "accredit", "entrat")
    private val PAROLE_DESCRIZIONE = listOf("descrizion", "causale", "dettagl", "beneficiar", "controparte", "note")
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
