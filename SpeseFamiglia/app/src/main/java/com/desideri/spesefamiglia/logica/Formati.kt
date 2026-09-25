package com.desideri.spesefamiglia.logica

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

private val LOCALE_IT: Locale = Locale.ITALY
private val FORMATO_DATA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", LOCALE_IT)
private val FORMATO_MESE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", LOCALE_IT)
private val FORMATO_MESE_BREVE: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/yyyy", LOCALE_IT)

private fun formatoNumero(): DecimalFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(LOCALE_IT))

/** "1.234,56 €" / "-12,00 CHF" da un importo in centesimi. */
fun formattaCent(cent: Long, valuta: String = "EUR"): String = formattaImporto(cent / 100.0, valuta)

fun formattaImporto(valore: Double, valuta: String = "EUR"): String {
    val simbolo = if (valuta == "EUR") "€" else valuta
    return "${formatoNumero().format(valore)} $simbolo"
}

/** Importo in centesimi senza simbolo, adatto a essere rimesso in un campo di testo ("12,50"). */
fun centInTesto(cent: Long): String = BigDecimal(cent).movePointLeft(2).toPlainString().replace('.', ',')

/**
 * Interpreta un importo scritto dall'utente ("12,50", "1.234,5", "-7.3") in centesimi; null se
 * non valido.
 */
fun testoInCent(testo: String): Long? {
    var t = testo.trim().replace(" ", "").replace("€", "")
    if (t.isEmpty()) return null
    t = if (t.contains(',') && t.contains('.')) t.replace(".", "").replace(',', '.') else t.replace(',', '.')
    return try {
        BigDecimal(t).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    } catch (e: Exception) {
        null
    }
}

fun euroInCent(valore: Double): Long = (valore * 100).roundToLong()

fun formattaData(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(FORMATO_DATA)

fun formattaMese(mese: YearMonth): String = mese.format(FORMATO_MESE).replaceFirstChar { it.uppercase() }

fun formattaMeseBreve(mese: YearMonth): String = mese.format(FORMATO_MESE_BREVE)

/** "MM/yyyy" o "yyyy-MM" in YearMonth; null se non valido. */
fun testoInMese(testo: String): YearMonth? {
    val t = testo.trim()
    return try {
        if (t.contains('/')) YearMonth.parse(t, FORMATO_MESE_BREVE) else YearMonth.parse(t)
    } catch (e: Exception) {
        null
    }
}
