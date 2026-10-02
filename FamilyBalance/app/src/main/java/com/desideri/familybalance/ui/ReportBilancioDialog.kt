package com.desideri.familybalance.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.logica.Calcoli
import com.desideri.familybalance.logica.formattaImporto
import com.desideri.familybalance.logica.formattaMeseBreve
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt

private val L_PERIODO: Dp = 60.dp
private val L_VALORE: Dp = 108.dp

/** Come raggruppare il report del bilancio. */
private enum class AggregazioneReport(val etichetta: String) { ANNO("Anno"), MESE("Mese") }

/** Una riga del report: periodo e valori in EUR con segno (spese negative). */
private data class RigaReport(
    val etichetta: String,
    val saldo: Double?,
    val entrate: Double,
    val correnti: Double,
    val ricorrenti: Double
) {
    val risparmio: Double get() = entrate + correnti
}

/**
 * Report del bilancio dalla prima operazione al mese corrente, per anno (default) o per mese:
 * saldo totale a fine periodo, entrate, spese correnti, spese ricorrenti e risparmio (entrate +
 * correnti, come nel Bilancio), ciascuno con la variazione percentuale rispetto alla riga prima.
 */
@Composable
fun ReportBilancioDialog(vm: SpeseViewModel, onChiudi: () -> Unit) {
    val dati by vm.dati.collectAsStateWithLifecycle()
    val cambi by vm.cambi.collectAsStateWithLifecycle()
    val impostazioni by vm.impostazioni.collectAsStateWithLifecycle()
    val personalizzazioni by vm.personalizzazioni.collectAsStateWithLifecycle()
    var aggregazione by rememberSaveable { mutableStateOf(AggregazioneReport.ANNO) }

    val mesi = remember(dati, cambi, impostazioni.targetRisparmioCent, personalizzazioni) {
        Calcoli.bilancio(
            dati.contiValuta, dati.voci, dati.operazioni, cambi, impostazioni.targetRisparmioCent / 100.0,
            YearMonth.now(), mesiFuturi = 0, personalizzazioni = personalizzazioni
        )
    }
    val righe = remember(mesi, aggregazione) {
        when (aggregazione) {
            AggregazioneReport.MESE -> mesi.map { RigaReport(formattaMeseBreve(it.mese), it.saldoFine, it.entrate, it.correnti, it.ricorrentiTotali) }
            AggregazioneReport.ANNO -> mesi.groupBy { it.mese.year }.toSortedMap().map { (anno, rr) ->
                RigaReport(anno.toString(), rr.last().saldoFine, rr.sumOf { it.entrate }, rr.sumOf { it.correnti }, rr.sumOf { it.ricorrentiTotali })
            }
        }
    }

    val scuro = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val meglio = if (scuro) Color(0xFF81C784) else Color(0xFF2E7D32)
    val peggio = if (scuro) Color(0xFFFF8A80) else Color(0xFFC62828)
    val zebra = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)

    /** Variazione % di [attuale] rispetto a [precedente]: verde se il valore sale (per le spese, negative, se si spende meno). */
    fun delta(attuale: Double?, precedente: Double?): Pair<String, Color>? {
        if (attuale == null || precedente == null || abs(precedente) < 0.005) return null
        val p = ((attuale - precedente) / abs(precedente) * 100).roundToInt()
        return (if (p > 0) "+$p%" else "$p%") to when {
            p > 0 -> meglio
            p < 0 -> peggio
            else -> Color.Unspecified
        }
    }
    fun importo(v: Double?) = v?.let { formattaImporto(it) }.orEmpty()

    Dialog(onDismissRequest = onChiudi, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize(), tonalElevation = 4.dp) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Report del bilancio", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onChiudi) { Icon(Icons.Filled.Close, contentDescription = "Chiudi") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Per", style = MaterialTheme.typography.labelMedium)
                    AggregazioneReport.entries.forEach { a ->
                        FilterChip(selected = aggregazione == a, onClick = { aggregazione = a }, label = { Text(a.etichetta) })
                    }
                }
                Text(
                    "Dalla prima operazione al mese corrente, in EUR (CHF come nel Bilancio). Saldo: totale a fine periodo; " +
                        "risparmio: entrate + spese correnti. Tra parentesi la variazione rispetto alla riga prima " +
                        "(verde: migliora, rosso: peggiora).",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                if (righe.isEmpty()) {
                    Text("Nessuna operazione.", modifier = Modifier.padding(16.dp))
                } else {
                    // Intestazione e colonna dei periodi ferme; l'ultima riga (più recente) visibile all'apertura.
                    val orizzontale = rememberScrollState()
                    val verticale = rememberScrollState()
                    LaunchedEffect(righe.size, aggregazione) { verticale.scrollTo(verticale.maxValue) }
                    fun sfondo(i: Int) = if (i % 2 == 1) zebra else Color.Transparent
                    val titoli = listOf("Saldo", "Entrate", "Correnti", "Ricorrenti", "Risparmio")
                    Column(modifier = Modifier.weight(1f)) {
                        Row {
                            CellaReport(aggregazione.etichetta, null, L_PERIODO, grassetto = true, alta = true)
                            Row(modifier = Modifier.horizontalScroll(orizzontale)) {
                                titoli.forEach { CellaReport(it, null, L_VALORE, grassetto = true, alta = true) }
                            }
                        }
                        Row(modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.verticalScroll(verticale)) {
                                righe.forEachIndexed { i, r -> CellaReport(r.etichetta, null, L_PERIODO, grassetto = true, alta = true, sfondo = sfondo(i)) }
                            }
                            Column(modifier = Modifier.verticalScroll(verticale).horizontalScroll(orizzontale)) {
                                righe.forEachIndexed { i, r ->
                                    val p = righe.getOrNull(i - 1)
                                    Row {
                                        CellaReport(importo(r.saldo), delta(r.saldo, p?.saldo), L_VALORE, grassetto = true, alta = true, sfondo = sfondo(i))
                                        CellaReport(importo(r.entrate), delta(r.entrate, p?.entrate), L_VALORE, alta = true, sfondo = sfondo(i))
                                        CellaReport(importo(r.correnti), delta(r.correnti, p?.correnti), L_VALORE, alta = true, sfondo = sfondo(i))
                                        CellaReport(importo(r.ricorrenti), delta(r.ricorrenti, p?.ricorrenti), L_VALORE, alta = true, sfondo = sfondo(i))
                                        CellaReport(importo(r.risparmio), delta(r.risparmio, p?.risparmio), L_VALORE, grassetto = true, alta = true, sfondo = sfondo(i))
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
