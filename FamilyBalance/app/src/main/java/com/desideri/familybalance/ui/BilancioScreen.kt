package com.desideri.familybalance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import java.time.LocalDate
import java.time.YearMonth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.clickable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.logica.RigaBilancio
import com.desideri.familybalance.logica.StatoMese
import com.desideri.familybalance.logica.formattaImporto
import com.desideri.familybalance.logica.formattaMese
import com.desideri.familybalance.ui.tema.coloreImporto
import java.util.Locale
import kotlin.math.abs

/**
 * Bilancio nel periodo scelto (Dal/Al, fisso in alto), in ordine cronologico: il mese corrente separato tra spese correnti, ricorrenti e stipendio/interessi; per i
 * mesi futuri il saldo previsto (saldo precedente + target di risparmio − ricorrenti previste);
 * per i mesi passati saldo a fine mese e scostamento del risparmio dal target.
 */
@Composable
fun BilancioScreen(vm: SpeseViewModel) {
    val righe by vm.bilancio.collectAsStateWithLifecycle()
    val periodo by vm.periodoBilancio.collectAsStateWithLifecycle()
    val impostazioni by vm.impostazioni.collectAsStateWithLifecycle()
    val dati by vm.dati.collectAsStateWithLifecycle()
    val stato = rememberLazyListState()
    var mostraGrafico by remember { mutableStateOf(false) }
    var mostraReport by remember { mutableStateOf(false) }
    var dettaglio by remember { mutableStateOf<Pair<YearMonth, ComponenteBilancio>?>(null) }
    val ricorrenti by vm.ricorrentiMeseCorrente.collectAsStateWithLifecycle()
    // Giorno dello stipendio (eventualmente il lunedì dopo, se cade nel weekend) e ricorrenti del mese
    // ancora previste con data prima di quel giorno; la data è quella pianificata o, se manca, il
    // giorno del mese dell'ultimo pagamento della stessa spesa (senza nessuno dei due: inclusa).
    val primaStipendio = remember(ricorrenti, dati.operazioni, impostazioni.giornoStipendio, impostazioni.stipendioGiornoLavorativo) {
        val mese = YearMonth.now()
        var giorno = mese.atDay(impostazioni.giornoStipendio.coerceIn(1, mese.lengthOfMonth()))
        if (impostazioni.stipendioGiornoLavorativo) {
            while (giorno.dayOfWeek == java.time.DayOfWeek.SATURDAY || giorno.dayOfWeek == java.time.DayOfWeek.SUNDAY) giorno = giorno.plusDays(1)
        }
        val righe = ricorrenti.filter { it.previsto != null }.mapNotNull { r ->
            val data = r.dataPrevista?.let { LocalDate.ofEpochDay(it) } ?: dati.operazioni.filter { it.voceId == r.voce.id }.maxByOrNull { it.data }
                ?.let { mese.atDay(LocalDate.ofEpochDay(it.data).dayOfMonth.coerceAtMost(mese.lengthOfMonth())) }
            if (data == null || data < giorno) Triple(r.voce.descrizione, r.previsto ?: 0.0, data) else null
        }.sortedBy { it.third ?: LocalDate.MIN }
        RicorrentiPrimaStipendio(giorno, righe)
    }

    // All'apertura la lista parte dal mese corrente (i passati sono sopra, i futuri sotto).
    LaunchedEffect(righe.isNotEmpty()) {
        val indice = righe.indexOfFirst { it.stato == StatoMese.CORRENTE }
        if (indice >= 0) stato.scrollToItem(indice)
    }

    // Periodo fisso in alto; sotto scorre la lista dei mesi in ordine cronologico.
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SceltaMese("Dal", periodo.first, { vm.impostaPeriodoBilancio(it, periodo.second) }, Modifier.weight(1f))
                SceltaMese("Al", periodo.second, { vm.impostaPeriodoBilancio(periodo.first, it) }, Modifier.weight(1f))
                MenuSezione(
                    listOf(
                        VoceMenuSezione("Grafico", Icons.AutoMirrored.Filled.ShowChart, abilitata = righe.isNotEmpty()) { mostraGrafico = true },
                        VoceMenuSezione("Report", Icons.Filled.TableChart) { mostraReport = true }
                    )
                )
            }
            // Con conti in CHF un cambio attuale di 1 è quasi certamente da impostare.
            if (impostazioni.cambioChfEur == 1.0 && dati.contiValuta.any { it.valuta == "CHF" }) {
                Text(
                    "Il cambio attuale CHF/EUR in Impostazioni è 1: i saldi CHF del mese corrente valgono 1:1 in EUR " +
                        "(e l'effetto cambio rispetto al mese prima risulta falsato). Imposta il cambio in Impostazioni (menu ⋮).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (impostazioni.targetRisparmioCent == 0L) {
                Text(
                    "Imposta il target di risparmio mensile in Impostazioni (menu ⋮) per le previsioni.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        CompositionLocalProvider(LocalDettaglioBilancio provides { m, c -> dettaglio = m to c }) {
        LazyColumn(state = stato, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            if (righe.isEmpty()) item { Text("Nessun mese nel periodo scelto.", style = MaterialTheme.typography.bodyMedium) }
            items(righe, key = { it.mese.toString() }) { r ->
                when (r.stato) {
                    StatoMese.CORRENTE -> CardMeseCorrente(r, primaStipendio)
                    StatoMese.FUTURO -> CardMeseFuturo(r)
                    StatoMese.PASSATO -> CardMesePassato(r)
                }
            }
        }
        }
    }
    if (mostraGrafico) GraficoBilancioDialog(righe, onChiudi = { mostraGrafico = false })
    if (mostraReport) ReportBilancioDialog(vm, onChiudi = { mostraReport = false })
    dettaglio?.let { (m, c) -> DettaglioBilancioDialog(vm, m, c, onChiudi = { dettaglio = null }) }
}

@Composable
private fun RigaValore(etichetta: String, valore: Double, grassetto: Boolean = false, dettaglio: Pair<YearMonth, ComponenteBilancio>? = null) {
    val apri = LocalDettaglioBilancio.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .then(if (dettaglio != null) Modifier.clickable { apri(dettaglio.first, dettaglio.second) } else Modifier)
            .padding(vertical = 1.dp)
    ) {
        Text(etichetta, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), fontWeight = if (grassetto) FontWeight.Bold else null)
        TestoImporto(valore, grassetto = grassetto)
        // Gli importi calcolati dalle operazioni si toccano per vederne il dettaglio.
        if (dettaglio != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Dettaglio", modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun CardMeseCorrente(r: RigaBilancio, primaStipendio: RicorrentiPrimaStipendio?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Bilancio attuale · ${formattaMese(r.mese)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            r.saldoIniziale?.let { RigaValore("Saldo iniziale (fine mese precedente)", it, grassetto = true) }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            RigaValore(
                if (r.stipendioStimato) "Stipendio / interessi (stimato)" else "Stipendio / interessi",
                r.stipendio ?: 0.0,
                dettaglio = r.mese to ComponenteBilancio.ENTRATE
            )
            if (r.stipendioStimato) {
                Text("Non ancora entrato: media degli ultimi mesi (Impostazioni).", style = MaterialTheme.typography.bodySmall)
            }
            RigaValore("Spese correnti", r.correnti, dettaglio = r.mese to ComponenteBilancio.CORRENTI)
            RigaValore("Risparmio", r.risparmio, dettaglio = r.mese to ComponenteBilancio.RISPARMIO)
            RigaValore("Delta target risparmio (${formattaImporto(r.target)})", r.deltaTarget)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            RigaValore("Spese ricorrenti", r.ricorrentiTotali, dettaglio = r.mese to ComponenteBilancio.RICORRENTI)
            if (r.ricorrentiPrevisti != 0.0) {
                Text(
                    "di cui pagate ${formattaImporto(r.ricorrentiPagati)}, ancora previste ${formattaImporto(r.ricorrentiPrevisti)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            // Ricorrenti ancora da pagare con scadenza prima dell'arrivo dello stipendio.
            if (primaStipendio != null && r.stipendioStimato) {
                RigaValore("Da pagare prima dello stipendio (${primaStipendio.giorno.format(FORMATO_GIORNO)})", primaStipendio.righe.sumOf { it.second })
                if (primaStipendio.righe.isNotEmpty()) {
                    Text(
                        primaStipendio.righe.joinToString(" · ") { (nome, v, giorno) -> "$nome ${giorno?.format(FORMATO_GIORNO)?.let { "($it) " }.orEmpty()}${formattaImporto(v)}" },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            // Saldo iniziale + residuo = saldo a fine mese.
            val totaleSpese = r.correnti + r.ricorrentiTotali
            RigaValore("Totale spese", totaleSpese, grassetto = true, dettaglio = r.mese to ComponenteBilancio.SPESE)
            RigaValore("Residuo (stipendio − spese)", (r.stipendio ?: 0.0) + totaleSpese, grassetto = true, dettaglio = r.mese to ComponenteBilancio.RESIDUO)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            // Saldo reale di oggi (somma dei conti), prima della previsione di fine mese.
            r.saldoFine?.let { RigaValore("Saldo attuale", it, grassetto = true) }
            r.saldoFinale?.let { RigaValore("Saldo a fine mese", it, grassetto = true) }
            Text(
                "Saldo attuale: somma dei conti oggi. Saldo a fine mese = saldo iniziale + residuo; è il punto di partenza dei mesi successivi.",
                style = MaterialTheme.typography.bodySmall
            )
            RigheCambio(r)
        }
    }
}

private val FORMATO_GIORNO = java.time.format.DateTimeFormatter.ofPattern("dd/MM")

/** Giorno dello stipendio del mese corrente e ricorrenti ancora da pagare prima (nome, importo, data prevista). */
data class RicorrentiPrimaStipendio(val giorno: LocalDate, val righe: List<Triple<String, Double, LocalDate?>>)

/** Intestazione di un mese collassabile: mese, saldo finale e (se c'è) il delta risparmio. */
@Composable
private fun TestaMese(r: RigaBilancio, espanso: Boolean, delta: Double?, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Icon(if (espanso) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (espanso) "Comprimi" else "Espandi")
        Text(formattaMese(r.mese), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            r.saldoFinale?.let { TestoImporto(it, grassetto = true) }
            // Variazione del saldo rispetto alla fine del mese prima.
            val finale = r.saldoFinale
            val iniziale = r.saldoFinalePrecedente
            if (finale != null && iniziale != null) {
                val variazione = finale - iniziale
                Text(
                    "Δ mese prima ${if (variazione > 0) "+" else ""}${formattaImporto(variazione)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = coloreImporto(variazione)
                )
            }
            delta?.let {
                Text(
                    "Δ risparmio ${if (it > 0) "+" else ""}${formattaImporto(it)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = coloreImporto(it)
                )
            }
        }
    }
}

@Composable
private fun CardMeseFuturo(r: RigaBilancio) {
    var espanso by rememberSaveable(r.mese.toString()) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            TestaMese(r, espanso, null) { espanso = !espanso }
            if (espanso) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                r.saldoIniziale?.let { RigaValore("Saldo iniziale", it) }
                RigaValore("Risparmio previsto (target)", r.target)
                RigaValore("Spese ricorrenti", r.ricorrentiTotali)
                r.saldoFinale?.let { RigaValore("Saldo finale", it, grassetto = true) }
            }
        }
    }
}

@Composable
private fun CardMesePassato(r: RigaBilancio) {
    var espanso by rememberSaveable(r.mese.toString()) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            TestaMese(r, espanso, r.deltaTarget) { espanso = !espanso }
            if (espanso) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                r.saldoIniziale?.let { RigaValore("Saldo iniziale", it) }
                RigaValore("Stipendio / interessi", r.stipendio ?: 0.0, dettaglio = r.mese to ComponenteBilancio.ENTRATE)
                RigaValore("Spese correnti", r.correnti, dettaglio = r.mese to ComponenteBilancio.CORRENTI)
                RigaValore("Risparmio", r.risparmio, dettaglio = r.mese to ComponenteBilancio.RISPARMIO)
                RigaValore("Delta risparmio (target ${formattaImporto(r.target)})", r.deltaTarget, grassetto = true)
                RigaValore("Spese ricorrenti", r.ricorrentiPagati, dettaglio = r.mese to ComponenteBilancio.RICORRENTI)
                // Totale delle spese del mese e quanto resta dello stipendio.
                val totaleSpese = r.correnti + r.ricorrentiPagati
                RigaValore("Totale spese", totaleSpese, grassetto = true, dettaglio = r.mese to ComponenteBilancio.SPESE)
                RigaValore("Residuo (stipendio − spese)", (r.stipendio ?: 0.0) + totaleSpese, grassetto = true, dettaglio = r.mese to ComponenteBilancio.RESIDUO)
                r.saldoFinale?.let { RigaValore("Saldo finale", it, grassetto = true) }
                r.saldoFine?.let {
                    Text(
                        "Saldo finale = saldo iniziale + residuo. Saldo reale dei conti a fine mese: ${formattaImporto(it)} " +
                            "(può differire per effetto cambio o spostamenti verso conti fuori dall'app).",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                RigheCambio(r)
            }
        }
    }
}

/** Effetto del cambio CHF/EUR sul saldo del mese (solo se c'è). */
@Composable
private fun RigheCambio(r: RigaBilancio) {
    if (abs(r.effettoCambio) < 0.005 && abs(r.cambioSpostamenti) < 0.005) return
    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
    Text(
        "Cambio del mese: 1 CHF = ${String.format(Locale.ITALY, "%.4f", r.cambioChfEur)} EUR",
        style = MaterialTheme.typography.bodySmall
    )
    if (abs(r.effettoCambio) >= 0.005) RigaValore("Effetto cambio sui saldi CHF", r.effettoCambio)
    if (abs(r.cambioSpostamenti) >= 0.005) RigaValore("Cambio applicato negli spostamenti", r.cambioSpostamenti)
}
