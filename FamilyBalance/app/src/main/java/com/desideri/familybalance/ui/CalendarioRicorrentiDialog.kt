package com.desideri.familybalance.ui

import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
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
import com.desideri.familybalance.logica.Cambi
import com.desideri.familybalance.logica.RigaRicorrente
import com.desideri.familybalance.logica.formattaImporto
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val FORMATO_NOME_MESE = DateTimeFormatter.ofPattern("MMM", Locale.ITALIAN)
private val LARGHEZZA_MESE: Dp = 56.dp
private val LARGHEZZA_COLONNA: Dp = 96.dp

/** Valore di una cella: importo (positivo), se stimato, se la scadenza è annullata e la riga ricorrente. */
private data class Cella(val importo: Double, val stimato: Boolean, val annullata: Boolean, val riga: RigaRicorrente)

/**
 * Calendario delle spese ricorrenti (menu ⋮): per l'anno scelto una riga per mese e una colonna per
 * ogni spesa ricorrente non obsoleta (filtrabili con scelta multipla). In ogni cella il pagato del
 * mese o, se non ci sono operazioni, l'importo stimato (in corsivo); "✕" le scadenze annullate.
 * Come la sezione Ricorrenti: CHF al cambio delle Impostazioni.
 */
@Composable
fun CalendarioRicorrentiDialog(vm: SpeseViewModel, onChiudi: () -> Unit) {
    val dati by vm.dati.collectAsStateWithLifecycle()
    val impostazioni by vm.impostazioni.collectAsStateWithLifecycle()
    val personalizzazioni by vm.personalizzazioni.collectAsStateWithLifecycle()
    var anno by rememberSaveable { mutableIntStateOf(YearMonth.now().year) }

    val voci = remember(dati.voci) {
        dati.voci.filter { it.ricorrente && !it.entrata && !it.obsoleta }.sortedBy { it.descrizione.lowercase() }
    }
    // Cella aperta nel dettaglio (spesa e mese).
    var aperta by remember { mutableStateOf<Pair<Long, YearMonth>?>(null) }
    var scelte by rememberSaveable { mutableStateOf<List<Long>>(emptyList()) }
    val mostrate = if (scelte.isEmpty()) voci else voci.filter { it.id in scelte }

    val mesi = (1..12).map { YearMonth.of(anno, it) }
    val celle: Map<Pair<Long, YearMonth>, Cella> = remember(dati, impostazioni.cambioChfEur, personalizzazioni, anno) {
        val righe = Calcoli.ricorrenti(mesi, dati.voci, dati.contiValuta, dati.operazioni, Cambi.fisso(impostazioni.cambioChfEur), YearMonth.now(), personalizzazioni)
        buildMap {
            righe.forEach { m ->
                m.righe.groupBy { it.voce.id }.forEach { (voceId, rr) ->
                    val importo = rr.sumOf { abs(it.pagato) + abs(it.previsto ?: 0.0) }
                    put(voceId to m.mese, Cella(importo, rr.any { it.previsto != null }, rr.all { it.annullata }, rr.first()))
                }
            }
        }
    }

    Dialog(onDismissRequest = onChiudi, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize(), tonalElevation = 4.dp) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Calendario ricorrenti", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onChiudi) { Icon(Icons.Filled.Close, contentDescription = "Chiudi") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { anno-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Anno precedente") }
                    Text(anno.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { anno++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Anno successivo") }
                    MenuMultiplo(
                        "Spese", voci.map { it.id }, scelte.ifEmpty { voci.map { it.id } },
                        { id -> voci.firstOrNull { it.id == id }?.descrizione.orEmpty() },
                        onCambia = { nuove -> scelte = if (nuove.size == voci.size) emptyList() else nuove.ifEmpty { scelte } },
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    "Pagato del mese; in corsivo la stima dove non ci sono operazioni; ✕ scadenza annullata. CHF al cambio delle Impostazioni. " +
                        "Tocca una cella per aggiungere, modificare o eliminare la spesa di quel mese.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                if (mostrate.isEmpty()) {
                    Text("Nessuna spesa ricorrente attiva.", modifier = Modifier.padding(16.dp))
                } else {
                    // Intestazione (in alto) e colonna dei mesi (a sinistra) restano ferme: le altre parti
                    // scorrono insieme condividendo gli stati di scorrimento.
                    val orizzontale = rememberScrollState()
                    val verticale = rememberScrollState()
                    val zebra = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    // Righe: 12 mesi e il totale dell'anno.
                    val righe: List<Pair<String, YearMonth?>> =
                        mesi.map { m -> m.format(FORMATO_NOME_MESE).replaceFirstChar { it.uppercase() } to m } + ("Anno" to null)
                    fun sfondo(indice: Int) = if (indice % 2 == 1) zebra else Color.Transparent
                    Column(modifier = Modifier.weight(1f)) {
                        Row {
                            CellaTesto("Mese", LARGHEZZA_MESE, grassetto = true, righe = 2)
                            Row(modifier = Modifier.horizontalScroll(orizzontale)) {
                                mostrate.forEach { CellaTesto(it.descrizione, LARGHEZZA_COLONNA, grassetto = true, righe = 2) }
                                CellaTesto("Totale", LARGHEZZA_COLONNA, grassetto = true, righe = 2)
                            }
                        }
                        Row(modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.verticalScroll(verticale)) {
                                righe.forEachIndexed { i, (nome, m) ->
                                    CellaTesto(nome, LARGHEZZA_MESE, grassetto = m == null || m == YearMonth.now(), sfondo = sfondo(i))
                                }
                            }
                            Column(modifier = Modifier.verticalScroll(verticale).horizontalScroll(orizzontale)) {
                                righe.forEachIndexed { i, (_, m) ->
                                    Row {
                                        if (m != null) {
                                            var totale = 0.0
                                            mostrate.forEach { v ->
                                                val c = celle[v.id to m]
                                                if (c != null && !c.annullata) totale += c.importo
                                                CellaTesto(
                                                    when {
                                                        c == null -> ""
                                                        c.annullata -> "✕"
                                                        else -> formattaImporto(c.importo)
                                                    },
                                                    LARGHEZZA_COLONNA,
                                                    corsivo = c?.stimato == true,
                                                    allineaDestra = true,
                                                    sfondo = sfondo(i),
                                                    onClick = { aperta = v.id to m }
                                                )
                                            }
                                            CellaTesto(if (totale != 0.0) formattaImporto(totale) else "", LARGHEZZA_COLONNA, grassetto = true, allineaDestra = true, sfondo = sfondo(i))
                                        } else {
                                            // Totale dell'anno per spesa.
                                            var complessivo = 0.0
                                            mostrate.forEach { v ->
                                                val somma = mesi.sumOf { mm -> celle[v.id to mm]?.takeIf { !it.annullata }?.importo ?: 0.0 }
                                                complessivo += somma
                                                CellaTesto(if (somma != 0.0) formattaImporto(somma) else "", LARGHEZZA_COLONNA, grassetto = true, allineaDestra = true, sfondo = sfondo(i))
                                            }
                                            CellaTesto(formattaImporto(complessivo), LARGHEZZA_COLONNA, grassetto = true, allineaDestra = true, sfondo = sfondo(i))
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

    aperta?.let { (voceId, mese) ->
        val voce = dati.vociPerId[voceId]
        if (voce == null) {
            aperta = null
        } else {
            // Senza riga (mese fuori ricorrenza): dettaglio vuoto, da cui si può comunque aggiungere.
            val riga = celle[voceId to mese]?.riga?.copy(voce = voce) ?: RigaRicorrente(voce, 0.0, null, meseScadenza = mese)
            DettaglioRicorrenteDialog(vm, dati, riga, mese, onChiudi = { aperta = null })
        }
    }
}

@Composable
private fun CellaTesto(
    testo: String,
    larghezza: Dp,
    grassetto: Boolean = false,
    corsivo: Boolean = false,
    allineaDestra: Boolean = false,
    righe: Int = 1,
    sfondo: Color = Color.Transparent,
    onClick: (() -> Unit)? = null
) {
    Text(
        testo,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (grassetto) FontWeight.Bold else null,
        fontStyle = if (corsivo) FontStyle.Italic else null,
        color = if (corsivo) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        textAlign = if (allineaDestra) TextAlign.End else TextAlign.Start,
        maxLines = righe,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .width(larghezza)
            .background(sfondo)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .height(if (righe > 1) 40.dp else 28.dp)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    )
}
