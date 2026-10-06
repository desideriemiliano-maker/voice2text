package com.desideri.familybalance.ui

import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.AlertDialog
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
import com.desideri.familybalance.logica.formattaMese
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val FORMATO_NOME_MESE = DateTimeFormatter.ofPattern("MMM", Locale.ITALIAN)
private val LARGHEZZA_MESE: Dp = 56.dp
private val LARGHEZZA_COLONNA: Dp = 104.dp
private val FORMATO_GIORNO_MESE = DateTimeFormatter.ofPattern("dd/MM")

/** Stato di una spesa ricorrente in un mese. */
private enum class StatoSpesa { STIMATA, PIANIFICATA, EFFETTIVA }

/**
 * Valore di una cella: [effettivo] (operazioni sul conto), [previsto] (calcolato o impostato dove non
 * ci sono operazioni) e [giaPagato] (operazioni con data fino a oggi),
 * entrambi positivi, lo stato, la data prevista (se pianificata), se la scadenza è annullata e la
 * riga ricorrente.
 */
private data class Cella(
    val effettivo: Double,
    val previsto: Double,
    val giaPagato: Double,
    val stato: StatoSpesa,
    val data: Long?,
    val annullata: Boolean,
    val riga: RigaRicorrente,
    /** Data da mostrare: pagamento o prevista (null = n.d.). */
    val dataMostrata: Long? = null
) {
    /** Il totale: l'effettivo e, se manca, il valore calcolato o impostato. */
    val importo: Double get() = effettivo + previsto
}

/**
 * Calendario delle spese ricorrenti (menu ⋮): per l'anno scelto una riga per mese e una colonna per
 * ogni spesa ricorrente non obsoleta (filtrabili con scelta multipla). In ogni cella il pagato del
 * mese o, se non ci sono operazioni, l'importo stimato (in corsivo); "✕" le scadenze annullate.
 * Come la sezione Ricorrenti: CHF come nel Bilancio (passati al cambio del mese, corrente e futuri a quello delle Impostazioni).
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
    // Cella stimata o pianificata da eliminare (pressione lunga), in attesa di conferma.
    var daEliminare by remember { mutableStateOf<Pair<Long, YearMonth>?>(null) }
    // Totali espansi: effettivo e quanto manca, oltre al totale.
    var totaliEspansi by rememberSaveable { mutableStateOf(false) }
    // null = tutte le spese (anche quelle aggiunte in seguito); lista vuota = nessuna.
    var scelte by rememberSaveable { mutableStateOf<List<Long>?>(null) }
    val mostrate = scelte?.let { s -> voci.filter { it.id in s } } ?: voci

    val mesi = (1..12).map { YearMonth.of(anno, it) }
    val cambi by vm.cambi.collectAsStateWithLifecycle()
    val celle: Map<Pair<Long, YearMonth>, Cella> = remember(dati, cambi, personalizzazioni, anno) {
        val righe = Calcoli.ricorrenti(mesi, dati.voci, dati.contiValuta, dati.operazioni, cambi, YearMonth.now(), personalizzazioni, LocalDate.now().toEpochDay())
        buildMap {
            righe.forEach { m ->
                m.righe.groupBy { it.voce.id }.forEach { (voceId, rr) ->
                    val effettivo = rr.sumOf { abs(it.pagato) }
                    val previsto = rr.sumOf { abs(it.previsto ?: 0.0) }
                    val giaPagato = rr.sumOf { abs(it.giaPagato) }
                    val data = rr.firstNotNullOfOrNull { r -> r.dataPrevista.takeIf { r.previsto != null } }
                    val stato = when {
                        rr.none { it.previsto != null } -> StatoSpesa.EFFETTIVA
                        data != null -> StatoSpesa.PIANIFICATA
                        else -> StatoSpesa.STIMATA
                    }
                    put(voceId to m.mese, Cella(effettivo, previsto, giaPagato, stato, data, rr.all { it.annullata }, rr.first(), (rr.firstOrNull { it.previsto != null } ?: rr.first()).data))
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
                        "Spese", voci.map { it.id }, scelte ?: voci.map { it.id },
                        { id -> voci.firstOrNull { it.id == id }?.descrizione.orEmpty() },
                        onCambia = { nuove -> scelte = if (nuove.size == voci.size) null else nuove },
                        modifier = Modifier.weight(1f),
                        conTutti = true
                    )
                }
                Text(
                    "Sotto l'importo la data del pagamento o quella prevista (n.d. se non ricavabile). Effettiva (operazioni sul conto) in nero; pianificata in verde; stimata con la media in arancio; " +
                        "✕ annullata. Il totale usa l'effettivo e, dove manca, il valore calcolato; tocca \"Totale\" per il dettaglio. " +
                        "CHF come nel Bilancio (passati al cambio del mese, corrente e futuri a quello delle Impostazioni). Tocca una cella per pianificare, aggiungere, modificare o eliminare la spesa di quel mese; " +
                        "tieni premuta una spesa stimata o pianificata per eliminarla da quel mese.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                if (mostrate.isEmpty()) {
                    Text(
                        if (voci.isEmpty()) "Nessuna spesa ricorrente attiva." else "Nessuna spesa selezionata nel filtro.",
                        modifier = Modifier.padding(16.dp)
                    )
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
                    val coloreTotali = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    // Colori per stato (più chiari col tema scuro): effettiva nero, pianificata verde, stimata arancio scuro.
                    val scuro = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                    val colorePianificata = if (scuro) Color(0xFF81C784) else Color(0xFF2E7D32)
                    val coloreStimata = if (scuro) Color(0xFFFFA64D) else Color(0xFFC25400)
                    Column(modifier = Modifier.weight(1f)) {
                        Row {
                            CellaTesto("Mese", LARGHEZZA_MESE, grassetto = true, righe = 2)
                            Row(modifier = Modifier.horizontalScroll(orizzontale)) {
                                CellaTesto(
                                    if (totaliEspansi) "Totale ◂" else "Totale ▸", LARGHEZZA_COLONNA, grassetto = true, righe = 2,
                                    sfondo = coloreTotali, onClick = { totaliEspansi = !totaliEspansi }
                                )
                                if (totaliEspansi) {
                                    CellaTesto("Effettivo", LARGHEZZA_COLONNA, grassetto = true, righe = 2, sfondo = coloreTotali)
                                    CellaTesto("Già pagato", LARGHEZZA_COLONNA, grassetto = true, righe = 2, sfondo = coloreTotali)
                                    CellaTesto("Mancante", LARGHEZZA_COLONNA, grassetto = true, righe = 2, sfondo = coloreTotali)
                                }
                                mostrate.forEach { CellaTesto(it.descrizione, LARGHEZZA_COLONNA, grassetto = true, righe = 2) }
                            }
                        }
                        Row(modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.verticalScroll(verticale)) {
                                righe.forEachIndexed { i, (nome, m) ->
                                    CellaTesto(nome, LARGHEZZA_MESE, grassetto = m == null || m == YearMonth.now(), righe = 2, sfondo = sfondo(i))
                                }
                            }
                            Column(modifier = Modifier.verticalScroll(verticale).horizontalScroll(orizzontale)) {
                                righe.forEachIndexed { i, (_, m) ->
                                    Row {
                                        // Solo le celle mostrate e non annullate contano nei totali.
                                        val contate = (if (m != null) listOf(m) else mesi).flatMap { mm ->
                                            mostrate.mapNotNull { v -> celle[v.id to mm]?.takeIf { !it.annullata } }
                                        }
                                        CelleTotali(contate.sumOf { it.effettivo }, contate.sumOf { it.previsto }, contate.sumOf { it.giaPagato }, totaliEspansi, coloreTotali)
                                        if (m != null) {
                                            mostrate.forEach { v ->
                                                val c = celle[v.id to m]
                                                CellaTesto(
                                                    // Importo e, sotto, la data (pagamento o prevista; n.d. se non ricavabile).
                                                    when {
                                                        c == null -> ""
                                                        c.annullata -> "✕"
                                                        else -> formattaImporto(c.importo) + "\n" +
                                                            (c.dataMostrata?.let { LocalDate.ofEpochDay(it).format(FORMATO_GIORNO_MESE) } ?: "n.d.")
                                                    },
                                                    LARGHEZZA_COLONNA,
                                                    righe = 2,
                                                    corsivo = c != null && c.stato == StatoSpesa.STIMATA && !c.annullata,
                                                    colore = when {
                                                        c == null || c.annullata -> null
                                                        c.stato == StatoSpesa.PIANIFICATA -> colorePianificata
                                                        c.stato == StatoSpesa.STIMATA -> coloreStimata
                                                        else -> MaterialTheme.colorScheme.onSurface
                                                    },
                                                    allineaDestra = true,
                                                    sfondo = sfondo(i),
                                                    onClick = { aperta = v.id to m },
                                                    onLongClick = if (c != null && c.stato != StatoSpesa.EFFETTIVA && !c.annullata) ({ daEliminare = v.id to m }) else null
                                                )
                                            }
                                        } else {
                                            // Totale dell'anno per spesa.
                                            mostrate.forEach { v ->
                                                val somma = mesi.sumOf { mm -> celle[v.id to mm]?.takeIf { !it.annullata }?.importo ?: 0.0 }
                                                CellaTesto(if (somma != 0.0) formattaImporto(somma) else "", LARGHEZZA_COLONNA, grassetto = true, allineaDestra = true, righe = 2, sfondo = sfondo(i))
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

    daEliminare?.let { (voceId, mese) ->
        val voce = dati.vociPerId[voceId]
        val cella = celle[voceId to mese]
        if (voce == null || cella == null) {
            daEliminare = null
        } else {
            val meseScadenza = cella.riga.meseScadenza ?: mese
            AlertDialog(
                onDismissRequest = { daEliminare = null },
                title = { Text("Elimina dal mese") },
                text = {
                    Text(
                        "Eliminare ${voce.descrizione} (previsto ${formattaImporto(cella.importo)}) da ${formattaMese(mese)}? " +
                            "La scadenza di questo mese viene annullata, le successive restano come in anagrafica. " +
                            "Si può ripristinare toccando la cella."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.annullaScadenza(voce, meseScadenza, true)
                        daEliminare = null
                    }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { daEliminare = null }) { Text("Annulla") } }
            )
        }
    }
}

/**
 * Colonne dei totali all'inizio della riga: il totale (effettivo e, dove manca, previsto) e, se
 * [espansi], l'effettivo, il già pagato (operazioni fino a oggi) e quanto manca al totale.
 */
@Composable
private fun CelleTotali(effettivo: Double, previsto: Double, giaPagato: Double, espansi: Boolean, sfondo: Color) {
    val totale = effettivo + previsto
    val mancante = totale - giaPagato
    CellaTesto(if (totale != 0.0) formattaImporto(totale) else "", LARGHEZZA_COLONNA, grassetto = true, allineaDestra = true, righe = 2, sfondo = sfondo)
    if (espansi) {
        CellaTesto(if (effettivo != 0.0) formattaImporto(effettivo) else "", LARGHEZZA_COLONNA, allineaDestra = true, righe = 2, sfondo = sfondo)
        CellaTesto(if (giaPagato != 0.0) formattaImporto(giaPagato) else "", LARGHEZZA_COLONNA, allineaDestra = true, righe = 2, sfondo = sfondo)
        CellaTesto(if (mancante != 0.0) formattaImporto(mancante) else "", LARGHEZZA_COLONNA, corsivo = true, allineaDestra = true, righe = 2, sfondo = sfondo)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CellaTesto(
    testo: String,
    larghezza: Dp,
    grassetto: Boolean = false,
    corsivo: Boolean = false,
    colore: Color? = null,
    allineaDestra: Boolean = false,
    righe: Int = 1,
    sfondo: Color = Color.Transparent,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    Text(
        testo,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (grassetto) FontWeight.Bold else null,
        fontStyle = if (corsivo) FontStyle.Italic else null,
        color = colore ?: if (corsivo) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        textAlign = if (allineaDestra) TextAlign.End else TextAlign.Start,
        maxLines = righe,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .width(larghezza)
            .background(sfondo)
            .then(if (onClick != null) Modifier.combinedClickable(onLongClick = onLongClick, onClick = onClick) else Modifier)
            .height(if (righe > 1) 40.dp else 28.dp)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    )
}
