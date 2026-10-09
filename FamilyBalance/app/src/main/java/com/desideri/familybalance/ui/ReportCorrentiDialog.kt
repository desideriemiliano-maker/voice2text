package com.desideri.familybalance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.logica.Calcoli
import com.desideri.familybalance.logica.Classe
import com.desideri.familybalance.logica.formattaImporto
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val NOME_MESE = DateTimeFormatter.ofPattern("MMM", Locale.ITALIAN)
private val L_MESE: Dp = 52.dp
private val L_COLONNA: Dp = 112.dp

/** Id per le operazioni senza tipo (colonna e filtro). */
private const val SENZA_TIPO = 0L

/** Chiave della colonna del totale e nome del report per l'ordine delle colonne. */
private const val COLONNA_TOTALE = -1L
private const val REPORT_CORRENTI = "report_correnti"

/**
 * Report delle spese correnti dell'anno scelto: una riga per mese (più il totale dell'anno), la
 * prima colonna con il totale e una colonna per spesa corrente (filtrabili con scelta multipla).
 * Ogni importo ha tra parentesi la variazione percentuale rispetto al mese prima (gennaio si
 * confronta con dicembre dell'anno precedente). Spese in positivo, CHF come nel Bilancio.
 */
@Composable
fun ReportCorrentiDialog(vm: SpeseViewModel, onChiudi: () -> Unit) {
    val dati by vm.dati.collectAsStateWithLifecycle()
    val cambi by vm.cambi.collectAsStateWithLifecycle()
    var anno by rememberSaveable { mutableIntStateOf(YearMonth.now().year) }
    // null = tutte le spese; lista vuota = nessuna.
    var scelte by rememberSaveable { mutableStateOf<List<Long>?>(null) }

    // Spesa (positiva) per voce e mese, da dicembre dell'anno prima a dicembre dell'anno.
    val mesi = (1..12).map { YearMonth.of(anno, it) }
    val spese: Map<Pair<Long, YearMonth>, Double> = remember(dati, cambi, anno) {
        val da = YearMonth.of(anno - 1, 12)
        val a = YearMonth.of(anno, 12)
        val somme = HashMap<Pair<Long, YearMonth>, Double>()
        for (op in dati.operazioni) {
            val m = Calcoli.mese(op.data)
            if (m < da || m > a) continue
            val voce = op.voceId?.let { dati.vociPerId[it] }
            if (Calcoli.classifica(op, voce) != Classe.CORRENTE) continue
            val k = (voce?.id ?: SENZA_TIPO) to m
            somme[k] = (somme[k] ?: 0.0) - cambi.inEuro(op.importoCent, dati.contiValutaPerId[op.contoValutaId]?.valuta ?: "EUR", m)
        }
        somme
    }
    // Colonne: le spese correnti con movimenti nell'anno, dalla più alta.
    val colonne: List<Pair<Long, String>> = remember(spese, anno) {
        spese.filterKeys { it.second.year == anno }.entries.groupBy({ it.key.first }, { it.value })
            .mapValues { it.value.sum() }.entries.sortedByDescending { it.value }
            .map { (id, _) -> id to (dati.vociPerId[id]?.descrizione ?: "Senza tipo") }
    }
    val mostrate = scelte?.let { s -> colonne.filter { it.first in s } } ?: colonne
    fun valore(id: Long, m: YearMonth) = spese[id to m] ?: 0.0
    fun totale(m: YearMonth) = mostrate.sumOf { valore(it.first, m) }
    fun nome(id: Long) = if (id == COLONNA_TOTALE) "Totale" else colonne.firstOrNull { it.first == id }?.second.orEmpty()

    // Colonne nell'ordine scelto dall'utente (pulsante accanto al filtro), memorizzato e nel backup.
    var ordine by remember { mutableStateOf(vm.ordineColonne(REPORT_CORRENTI)) }
    val colonneTabella = ordinaColonne(listOf(COLONNA_TOTALE) + mostrate.map { it.first }, ordine) { it.toString() }
    var sceltaOrdine by remember { mutableStateOf(false) }

    val scuro = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val coloreAumento = if (scuro) Color(0xFFFF8A80) else Color(0xFFC62828)
    val coloreCalo = if (scuro) Color(0xFF81C784) else Color(0xFF2E7D32)
    val zebra = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val coloreTotali = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)

    /** Variazione percentuale (testo e colore) di [attuale] rispetto a [precedente]; null se non calcolabile. */
    fun delta(attuale: Double, precedente: Double): Pair<String, Color>? {
        if (abs(precedente) < 0.005 || abs(attuale) < 0.005) return null
        val p = ((attuale - precedente) / abs(precedente) * 100).roundToInt()
        return (if (p > 0) "+$p%" else "$p%") to when {
            p > 0 -> coloreAumento
            p < 0 -> coloreCalo
            else -> Color.Unspecified
        }
    }

    Dialog(onDismissRequest = onChiudi, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize(), tonalElevation = 4.dp) {
            Column(modifier = Modifier.fillMaxSize().paddingBarreDialog().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Report spese correnti", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onChiudi) { Icon(Icons.Filled.Close, contentDescription = "Chiudi") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { anno-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Anno precedente") }
                    Text(anno.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { anno++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Anno successivo") }
                    MenuMultiplo(
                        "Spese", colonne.map { it.first }, scelte ?: colonne.map { it.first },
                        { id -> colonne.firstOrNull { it.first == id }?.second.orEmpty() },
                        onCambia = { nuove -> scelte = if (nuove.size == colonne.size) null else nuove },
                        modifier = Modifier.weight(1f),
                        conTutti = true
                    )
                    IconButton(onClick = { sceltaOrdine = true }) { Icon(Icons.Filled.ViewColumn, contentDescription = "Ordine delle colonne") }
                }
                Text(
                    "Spese del mese in EUR (CHF come nel Bilancio); tra parentesi la variazione rispetto al mese prima " +
                        "(rosso: si è speso di più, verde: di meno). Con il pulsante delle colonne ne scegli l'ordine.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                if (mostrate.isEmpty()) {
                    Text(if (colonne.isEmpty()) "Nessuna spesa corrente nel $anno." else "Nessuna spesa selezionata nel filtro.", modifier = Modifier.padding(16.dp))
                } else {
                    // Intestazione e colonna dei mesi ferme; il resto scorre condividendo gli stati di scorrimento.
                    val orizzontale = rememberScrollState()
                    val verticale = rememberScrollState()
                    val righe: List<YearMonth?> = mesi + null
                    fun sfondo(i: Int) = if (i % 2 == 1) zebra else Color.Transparent
                    Column(modifier = Modifier.weight(1f)) {
                        Row {
                            CellaReport("Mese", null, L_MESE, grassetto = true, alta = true)
                            Row(modifier = Modifier.horizontalScroll(orizzontale)) {
                                colonneTabella.forEach { id ->
                                    CellaReport(
                                        nome(id), null, L_COLONNA, grassetto = true, alta = true,
                                        sfondo = if (id == COLONNA_TOTALE) coloreTotali else Color.Transparent
                                    )
                                }
                            }
                        }
                        Row(modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.verticalScroll(verticale)) {
                                righe.forEachIndexed { i, m ->
                                    CellaReport(
                                        m?.format(NOME_MESE)?.replaceFirstChar { it.uppercase() } ?: "Anno", null, L_MESE,
                                        grassetto = m == null || m == YearMonth.now(), alta = true, sfondo = sfondo(i)
                                    )
                                }
                            }
                            Column(modifier = Modifier.verticalScroll(verticale).horizontalScroll(orizzontale)) {
                                righe.forEachIndexed { i, m ->
                                    Row {
                                        colonneTabella.forEach { id ->
                                            val eTotale = id == COLONNA_TOTALE
                                            fun v(mm: YearMonth) = if (eTotale) totale(mm) else valore(id, mm)
                                            val sf = if (eTotale) coloreTotali else sfondo(i)
                                            if (m != null) {
                                                CellaReport(importo(v(m)), delta(v(m), v(m.minusMonths(1))), L_COLONNA, grassetto = eTotale, alta = true, sfondo = sf)
                                            } else {
                                                CellaReport(importo(mesi.sumOf { v(it) }), null, L_COLONNA, grassetto = true, alta = true, sfondo = sf)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (sceltaOrdine) {
        OrdineColonneDialog(
            colonneTabella.map { nome(it) },
            onConferma = { indici ->
                val nuove = indici.map { colonneTabella[it].toString() }
                ordine = nuove + ordine.filter { it !in nuove }
                vm.salvaOrdineColonne(REPORT_CORRENTI, ordine)
                sceltaOrdine = false
            },
            onAnnulla = { sceltaOrdine = false }
        )
    }
}

private fun importo(v: Double) = if (abs(v) < 0.005) "" else formattaImporto(v)

/** Cella della tabella: testo (importo o intestazione) e, sotto, l'eventuale variazione colorata. */
@Composable
internal fun CellaReport(
    testo: String,
    delta: Pair<String, Color>?,
    larghezza: Dp,
    grassetto: Boolean = false,
    alta: Boolean = false,
    sfondo: Color = Color.Transparent,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = modifier
            .width(larghezza)
            .height(if (alta) 40.dp else 28.dp)
            .background(sfondo)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            testo,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (grassetto) FontWeight.Bold else null,
            textAlign = TextAlign.End,
            maxLines = if (delta == null) 2 else 1,
            overflow = TextOverflow.Ellipsis
        )
        delta?.let { (d, colore) -> Text("($d)", style = MaterialTheme.typography.labelSmall, color = colore, maxLines = 1) }
    }
}

/** Le colonne [chiavi] nell'ordine memorizzato [salvato]; quelle nuove in coda nell'ordine di partenza. */
internal fun <T> ordinaColonne(colonne: List<T>, salvato: List<String>, chiave: (T) -> String): List<T> {
    val posizione = salvato.withIndex().associate { (i, k) -> k to i }
    return colonne.withIndex().sortedWith(compareBy({ posizione[chiave(it.value)] ?: Int.MAX_VALUE }, { it.index })).map { it.value }
}

/**
 * Scelta dell'ordine delle colonne di un report: frecce per spostare ogni colonna su o giù.
 * [onConferma] riceve i [titoli] nel nuovo ordine, come indici della lista di partenza.
 */
@Composable
internal fun OrdineColonneDialog(titoli: List<String>, onConferma: (List<Int>) -> Unit, onAnnulla: () -> Unit) {
    var ordine by remember(titoli) { mutableStateOf(titoli.indices.toList()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onAnnulla,
        title = { Text("Ordine delle colonne") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ordine.forEachIndexed { pos, indice ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("${pos + 1}. ${titoli[indice]}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { ordine = ordine.spostato(pos, pos - 1) }, enabled = pos > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Sposta su")
                        }
                        IconButton(onClick = { ordine = ordine.spostato(pos, pos + 1) }, enabled = pos < ordine.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Sposta giù")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConferma(ordine) }) { Text("Applica") } },
        dismissButton = { TextButton(onClick = onAnnulla) { Text("Annulla") } }
    )
}

/** [lista] con l'elemento in posizione [da] spostato in posizione [a]. */
internal fun <T> List<T>.spostato(da: Int, a: Int): List<T> = toMutableList().apply { add(a, removeAt(da)) }
