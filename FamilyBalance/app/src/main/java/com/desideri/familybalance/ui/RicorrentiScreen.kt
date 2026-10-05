package com.desideri.familybalance.ui

import com.desideri.familybalance.data.dataPerRicorrente
import androidx.compose.foundation.clickable
import com.desideri.familybalance.logica.ValoreGrafico
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desideri.familybalance.DatiApp
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.data.Operazione
import com.desideri.familybalance.data.Voce
import com.desideri.familybalance.logica.Calcoli
import com.desideri.familybalance.logica.FontePrevisione
import com.desideri.familybalance.logica.MeseRicorrenti
import com.desideri.familybalance.logica.RigaRicorrente
import com.desideri.familybalance.logica.centInTesto
import com.desideri.familybalance.logica.formattaCent
import com.desideri.familybalance.logica.formattaData
import com.desideri.familybalance.logica.formattaImporto
import com.desideri.familybalance.logica.formattaMese
import com.desideri.familybalance.logica.testoInCent
import com.desideri.familybalance.logica.testoInMese
import java.time.YearMonth
import kotlin.math.abs

/** Riga aperta nel dettaglio: la riga ricorrente e il mese in cui è mostrata. */
private data class RigaAperta(val riga: RigaRicorrente, val mese: YearMonth)

/**
 * Spese ricorrenti mese per mese (ex foglio "Bollette"): per i mesi passati quanto pagato, per il
 * mese corrente e i futuri le scadenze previste non ancora pagate. Si filtra per voce (scelta
 * multipla) e si sceglie il periodo; toccando una riga se ne vede il dettaglio (operazioni pagate o
 * come è calcolato il previsto) e si imposta importo/data o si sposta la scadenza.
 */
@Composable
fun RicorrentiScreen(vm: SpeseViewModel) {
    val mesi by vm.ricorrenti.collectAsStateWithLifecycle()
    val periodo by vm.periodoRicorrenti.collectAsStateWithLifecycle()
    val dati by vm.dati.collectAsStateWithLifecycle()
    val oggi = YearMonth.now()
    val stato = rememberLazyListState()
    var filtroVoci by rememberSaveable { mutableStateOf<List<Long>>(emptyList()) }
    var sceltaVoci by remember { mutableStateOf(false) }
    var aperta by remember { mutableStateOf<RigaAperta?>(null) }
    var mostraGrafico by remember { mutableStateOf(false) }
    var mostraCalendario by remember { mutableStateOf(false) }
    val cambi by vm.cambi.collectAsStateWithLifecycle()

    LaunchedEffect(mesi.isNotEmpty()) {
        val indice = mesi.indexOfFirst { it.mese == oggi }
        if (indice >= 0) stato.scrollToItem(indice)
    }

    val vociRicorrenti = remember(dati.voci) { dati.voci.filter { it.ricorrente && !it.entrata }.sortedBy { it.descrizione.lowercase() } }
    val filtrati = if (filtroVoci.isEmpty()) mesi else mesi.map { m -> m.copy(righe = m.righe.filter { it.voce.id in filtroVoci }) }

    if (vociRicorrenti.isEmpty()) {
        Text(
            "Nessuna spesa ricorrente. Segna le voci come ricorrenti in Anagrafica spese (menu ⋮).",
            modifier = Modifier.padding(24.dp)
        )
        return
    }

    // Filtri fissi in alto; sotto scorre la lista dei mesi.
    Column(modifier = Modifier.fillMaxSize()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = if (filtroVoci.isEmpty()) "Tutte" else vociRicorrenti.filter { it.id in filtroVoci }.joinToString(", ") { it.descrizione },
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(if (filtroVoci.size > 1) "Spese ricorrenti (${filtroVoci.size})" else "Spese ricorrenti") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { sceltaVoci = true })
                }
                MenuSezione(
                    listOf(
                        VoceMenuSezione("Grafico", Icons.AutoMirrored.Filled.ShowChart) { mostraGrafico = true },
                        VoceMenuSezione("Calendario ricorrenti", Icons.Filled.CalendarMonth) { mostraCalendario = true }
                    )
                )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SceltaMese("Dal", periodo.first, { vm.impostaPeriodoRicorrenti(it, periodo.second) }, Modifier.weight(1f))
                    SceltaMese("Al", periodo.second, { vm.impostaPeriodoRicorrenti(periodo.first, it) }, Modifier.weight(1f))
                }
            }
        LazyColumn(state = stato, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(filtrati, key = { it.mese.toString() }) { mese ->
                val precedente = filtrati.firstOrNull { it.mese == mese.mese.minusMonths(1) }?.totale
                CardMese(mese, oggi, precedente, onRiga = { aperta = RigaAperta(it, mese.mese) })
            }
        }
    }

    if (sceltaVoci) {
        SceltaVociDialog(
            voci = vociRicorrenti,
            selezionate = filtroVoci.toSet(),
            onConferma = {
                filtroVoci = it.toList()
                sceltaVoci = false
            },
            onAnnulla = { sceltaVoci = false }
        )
    }
    if (mostraGrafico) {
        // Pagamenti delle spese filtrate nel periodo scelto, in EUR al cambio delle Impostazioni.
        val scelte = if (filtroVoci.isEmpty()) vociRicorrenti else vociRicorrenti.filter { it.id in filtroVoci }
        val perVoce = filtroVoci.isNotEmpty() && scelte.size <= 8
        val indice = scelte.withIndex().associate { (i, v) -> v.id to i }
        val serie = if (perVoce) scelte.mapIndexed { i, v -> SerieGrafico(v.descrizione, coloreSerie(i, v.colore)) }
        else listOf(SerieGrafico(if (filtroVoci.isEmpty()) "Tutte le ricorrenti" else "Totale selezionate", coloreSerie(0, null)))
        val valori = dati.operazioni.mapNotNull { op ->
            val i = op.voceId?.let { indice[it] } ?: return@mapNotNull null
            val m = Calcoli.mese(op.data)
            if (op.trasferimento || m < periodo.first || m > periodo.second) return@mapNotNull null
            val eur = cambi.inEuro(op.importoCent, dati.contiValutaPerId[op.contoValutaId]?.valuta ?: "EUR", m)
            ValoreGrafico(if (perVoce) i else 0, op.data, -eur)
        }
        GraficoSpeseDialog(
            titolo = "Spese ricorrenti",
            nota = "Pagamenti di ${formattaMese(periodo.first)} – ${formattaMese(periodo.second)} delle spese filtrate, in EUR" +
                (if (!perVoce && filtroVoci.size > 8) " (più di 8 spese: mostrato il totale)" else "") + ".",
            serie = serie,
            valori = valori,
            onChiudi = { mostraGrafico = false }
        )
    }
    if (mostraCalendario) CalendarioRicorrentiDialog(vm, onChiudi = { mostraCalendario = false })
    aperta?.let { a ->
        // La voce aggiornata (es. mese di partenza cambiato) si prende dai dati correnti.
        val voce = dati.vociPerId[a.riga.voce.id] ?: a.riga.voce
        DettaglioRicorrenteDialog(vm, dati, a.riga.copy(voce = voce), a.mese, onChiudi = { aperta = null })
    }
}

/** Mese con frecce per andare avanti e indietro. */
@Composable
internal fun SceltaMese(etichetta: String, mese: YearMonth, onMese: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        IconButton(onClick = { onMese(mese.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mese precedente") }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(etichetta, style = MaterialTheme.typography.labelSmall)
            Text(formattaMese(mese), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = { onMese(mese.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mese successivo") }
    }
}

@Composable
private fun CardMese(mese: MeseRicorrenti, oggi: YearMonth, totalePrecedente: Double?, onRiga: (RigaRicorrente) -> Unit) {
    val corrente = mese.mese == oggi
    // Le scadenze annullate non si mostrano (si ripristinano dal Calendario ricorrenti).
    val righe = mese.righe.filter { !it.annullata }
    // Mesi passati e futuri chiusi per default; il corrente sempre aperto.
    var espanso by rememberSaveable(mese.mese.toString()) { mutableStateOf(corrente) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (corrente) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(enabled = !corrente) { espanso = !espanso }) {
                if (!corrente) Icon(if (espanso) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (espanso) "Comprimi" else "Espandi")
                Text(
                    formattaMese(mese.mese) + if (corrente) " (corrente)" else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TestoImporto(mese.totale, grassetto = true)
                VariazioneSpesa(mese.totale, totalePrecedente)
            }
            // Dettaglio del totale: effettivo (operazioni del mese), già pagato (fino a oggi) e mancante.
            if (mese.totale != 0.0) {
                Text(
                    "Effettivo ${formattaImporto(mese.totalePagato)} · già pagato ${formattaImporto(mese.totaleGiaPagato)} · " +
                        "mancante ${formattaImporto(mese.totaleMancante)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (espanso) {
            if (righe.isEmpty()) {
                Text("Nessuna spesa ricorrente", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
            righe.forEachIndexed { indice, riga ->
                if (indice == 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onRiga(riga) }.padding(vertical = 4.dp)
                ) {
                    PallinoColore(riga.voce.colore, modifier = Modifier.padding(end = 6.dp), dimensione = 10.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        val nonPagata = riga.previsto != null && mese.mese < oggi
                        Text(
                            when {
                                riga.annullata -> "✕ "
                                nonPagata -> "⚠ "
                                riga.previsto != null -> "⏳ "
                                else -> "✓ "
                            } + (riga.voce.sottotipo ?: riga.voce.tipo),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (riga.annullata) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified
                        )
                        val extra = listOfNotNull(
                            "non pagata".takeIf { nonPagata },
                            riga.dataPrevista?.let { "il ${formattaData(it)}" },
                            riga.meseOrigine?.let { "spostata da ${formattaMese(it)}" },
                            "importo impostato".takeIf { riga.fonte == FontePrevisione.PERSONALIZZATA }
                        )
                        if (riga.previsto != null && extra.isNotEmpty()) {
                            Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (riga.annullata) {
                        Text("annullata", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (riga.previsto != null) {
                        // Con operazioni già collegate si mostra il pagato e il residuo.
                        Text(
                            (if (riga.pagato != 0.0) "pagato ${formattaImporto(riga.pagato)} · resta " else if (mese.mese < oggi) "stima " else "previsto ") +
                                formattaImporto(riga.previsto),
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        TestoImporto(riga.pagato)
                    }
                }
            }
            }
        }
    }
}

/** Scelta di una o più spese ricorrenti da mostrare; nessuna = tutte. */
@Composable
internal fun SceltaVociDialog(
    voci: List<Voce>,
    selezionate: Set<Long>,
    onConferma: (Set<Long>) -> Unit,
    onAnnulla: () -> Unit,
    titolo: String = "Spese ricorrenti"
) {
    val scelte = remember { mutableStateListOf<Long>().apply { addAll(selezionate) } }
    var cerca by remember { mutableStateOf("") }
    val mostrate = voci.filter { cerca.isBlank() || it.descrizione.contains(cerca.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onAnnulla,
        title = { Text(titolo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(value = cerca, onValueChange = { cerca = it }, label = { Text("Cerca") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row {
                    TextButton(onClick = { mostrate.forEach { if (it.id !in scelte) scelte.add(it.id) } }) { Text("Seleziona mostrate") }
                    TextButton(onClick = { scelte.clear() }) { Text("Nessuna") }
                }
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(mostrate, key = { it.id }) { v ->
                        val sel = v.id in scelte
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { if (sel) scelte.remove(v.id) else scelte.add(v.id) }
                        ) {
                            Checkbox(checked = sel, onCheckedChange = { if (it) scelte.add(v.id) else scelte.remove(v.id) })
                            PallinoColore(v.colore, modifier = Modifier.padding(end = 6.dp), dimensione = 10.dp)
                            Text(v.descrizione + if (v.obsoleta) " (obsoleta)" else "", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Text(if (scelte.isEmpty()) "Nessuna selezionata: si mostrano tutte." else "${scelte.size} selezionate", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onConferma(scelte.toSet()) }) { Text("Applica") } },
        dismissButton = { TextButton(onClick = onAnnulla) { Text("Annulla") } }
    )
}

/**
 * Dettaglio di una spesa ricorrente in un mese: le operazioni pagate (toccandole si modificano) o,
 * se ancora da pagare, come è calcolato il previsto, con la possibilità di impostare importo e data
 * o di spostare la scadenza in un altro mese (solo questa volta o facendo ripartire la ricorrenza).
 */
@Composable
internal fun DettaglioRicorrenteDialog(vm: SpeseViewModel, dati: DatiApp, riga: RigaRicorrente, mese: YearMonth, onChiudi: () -> Unit) {
    val voce = riga.voce
    val pagate = remember(dati.operazioni, voce.id, mese) {
        dati.operazioni.filter { it.voceId == voce.id && Calcoli.mese(it.dataPerRicorrente) == mese }.sortedBy { it.data }
    }
    val meseScadenza = riga.meseScadenza ?: mese
    var inModifica by remember { mutableStateOf<Operazione?>(null) }
    // Nuova operazione della spesa nel mese: conto scelto (null = nessuna in corso).
    var nuovaSuConto by remember { mutableStateOf<Long?>(null) }
    var importoCerca by remember(riga) { mutableStateOf(riga.previsto?.let { centInTesto(Math.round(abs(it) * 100)) } ?: "") }
    var daAssociare by remember { mutableStateOf<Operazione?>(null) }
    var confermaElimina by remember { mutableStateOf(false) }
    var contoNuova by remember { mutableStateOf(dati.contiValutaOrdinati.firstOrNull()?.id) }
    var importo by remember(riga) { mutableStateOf(riga.importoScadenza?.let { centInTesto(Math.round(abs(it) * 100)) } ?: "") }
    var data by remember(riga) { mutableStateOf(riga.dataPrevista) }
    var nuovoMese by remember(riga) { mutableStateOf(mese.plusMonths(1)) }
    var mantieni by remember(riga) { mutableStateOf(true) }
    var errore by remember(riga) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text("${voce.descrizione} · ${formattaMese(mese)}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val ricorrenza = (if (voce.mesiRicorrenza == 1) "ogni mese" else "ogni ${voce.mesiRicorrenza} mesi") +
                    (voce.meseInizio?.let { testoInMese(it) }?.let { " da ${formattaMese(it)}" } ?: " (senza mese di partenza)")
                Text("Ricorrenza: $ricorrenza" + if (voce.obsoleta) " · obsoleta" else "", style = MaterialTheme.typography.bodySmall)

                // Stato: effettiva (operazioni sul conto), pianificata (con data prevista) o stimata.
                Text(
                    when {
                        riga.annullata -> "Stato: eliminata da questo mese"
                        riga.previsto == null && pagate.isNotEmpty() -> "Stato: effettiva (operazioni sul conto)"
                        riga.previsto != null && riga.pagato != 0.0 ->
                            "Stato: pagata in parte (${formattaImporto(riga.pagato)} su ${formattaImporto(riga.importoScadenza ?: riga.previsto)})"
                        riga.previsto != null && riga.dataPrevista != null -> "Stato: pianificata per il ${formattaData(riga.dataPrevista)}"
                        riga.previsto != null && riga.fonte == FontePrevisione.MEDIA -> "Stato: stimata con la media"
                        riga.previsto != null -> "Stato: stimata"
                        else -> "Nessuna spesa prevista in questo mese"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                riga.previsto?.let { previsto ->
                    Text("Pianifica: importo e data prevista", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = importo,
                        onValueChange = { importo = it; errore = null },
                        label = { Text("Importo previsto (EUR)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    CampoData("Data prevista", data, { data = it }, Modifier.fillMaxWidth(), consentiVuoto = true)
                    Text(
                        "Una data in un altro mese sposta lì la scadenza (solo questa volta).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val cent = testoInCent(importo)?.let { abs(it) }?.takeIf { it > 0 }
                            if (importo.isNotBlank() && cent == null) {
                                errore = "Importo non valido"
                            } else {
                                // Importo uguale al calcolato: non lo si fissa, così segue le variazioni.
                                val calcolato = if (riga.fonte != FontePrevisione.PERSONALIZZATA) riga.importoScadenza?.let { Math.round(abs(it) * 100) } else null
                                vm.salvaScadenza(voce, meseScadenza, cent?.takeIf { it != calcolato }, data)
                                onChiudi()
                            }
                        }) { Text("Salva") }
                        if (riga.fonte == FontePrevisione.PERSONALIZZATA || riga.dataPrevista != null) {
                            TextButton(onClick = {
                                vm.salvaScadenza(voce, meseScadenza, null, null)
                                onChiudi()
                            }) { Text("Usa calcolato") }
                        }
                    }
                }
                // Spesa generata dalla ricorrenza ma che in questo mese non c'è: si elimina dal mese.
                if (riga.previsto != null) {
                    OutlinedButton(onClick = { confermaElimina = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Elimina da ${formattaMese(mese)} (questo mese non c'è)", color = MaterialTheme.colorScheme.error)
                    }
                }
                if (riga.annullata) {
                    Text("Eliminata da ${formattaMese(meseScadenza)}: le scadenze successive restano come in anagrafica.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = {
                        vm.annullaScadenza(voce, meseScadenza, false)
                        onChiudi()
                    }) { Text("Ripristina") }
                }

                if (pagate.isNotEmpty()) {
                    Text("Operazioni del mese", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    pagate.forEach { op ->
                        Column(modifier = Modifier.fillMaxWidth().clickable { inModifica = op }.padding(vertical = 4.dp)) {
                            Row {
                                Text("${formattaData(op.data)} · ${dati.etichetta(op.contoValutaId)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(
                                    formattaCent(op.importoCent, dati.contiValutaPerId[op.contoValutaId]?.valuta ?: "EUR"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            op.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                            op.dataRicorrente?.let {
                                Text("Imputata alla spesa ricorrente il ${formattaData(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = op.esclusaDaMedia, onCheckedChange = { vm.impostaEsclusaDaMedia(op, it) })
                                Text("Escludi dalla media per le stime", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Text("Tocca un'operazione per modificarla o eliminarla.", style = MaterialTheme.typography.bodySmall)
                    // Il totale delle operazioni diventa l'importo del mese: niente più residuo.
                    val totaleCent = Math.round(abs(riga.pagato) * 100)
                    if (totaleCent > 0 && riga.importoScadenza?.let { Math.round(abs(it) * 100) } != totaleCent) {
                        OutlinedButton(onClick = {
                            vm.salvaScadenza(voce, meseScadenza, totaleCent, riga.dataPrevista)
                            onChiudi()
                        }, modifier = Modifier.fillMaxWidth()) { Text("Imposta l'importo del mese al totale delle operazioni (${formattaImporto(abs(riga.pagato))})") }
                    }
                }
                Text("Aggiungi operazione", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoScelta(
                        etichetta = "Conto",
                        selezionato = dati.contiValutaOrdinati.firstOrNull { it.id == contoNuova },
                        opzioni = dati.contiValutaOrdinati,
                        testo = { dati.etichetta(it.id) },
                        onScelta = { contoNuova = it.id },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { nuovaSuConto = contoNuova }, enabled = contoNuova != null) { Text("Aggiungi") }
                }
                // Scadenza prevista in più (es. futura): stimata con la media, senza conto né operazione.
                if (riga.previsto == null && !riga.annullata) {
                    OutlinedButton(onClick = {
                        vm.aggiungiScadenza(voce, mese)
                        onChiudi()
                    }) { Text("Aggiungi prevista (stimata con la media, senza conto)") }
                }

                // Ricerca per importo di un'operazione già registrata da associare alla spesa.
                Text("Associa un'operazione esistente", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = importoCerca,
                    onValueChange = { importoCerca = it },
                    label = { Text("Importo da cercare") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                val centCerca = testoInCent(importoCerca)?.let { abs(it) }
                if (centCerca != null && centCerca > 0) {
                    val metaMese = mese.atDay(15).toEpochDay()
                    val trovate = dati.operazioni
                        .filter { !it.trasferimento && abs(it.importoCent) == centCerca && it.id !in pagate.map { p -> p.id } }
                        .sortedBy { abs(it.data - metaMese) }
                        .take(20)
                    if (trovate.isEmpty()) Text("Nessuna operazione con questo importo.", style = MaterialTheme.typography.bodySmall)
                    trovate.forEach { op ->
                        Column(modifier = Modifier.fillMaxWidth().clickable { daAssociare = op }.padding(vertical = 4.dp)) {
                            Row {
                                Text("${formattaData(op.data)} · ${dati.etichetta(op.contoValutaId)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(
                                    formattaCent(op.importoCent, dati.contiValutaPerId[op.contoValutaId]?.valuta ?: "EUR"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                (op.voceId?.let { dati.vociPerId[it]?.descrizione } ?: "Senza tipo") + (op.note?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2
                            )
                        }
                    }
                }

                riga.previsto?.let { previsto ->
                    Text(
                        if (riga.pagato != 0.0) "Previsto: ${formattaImporto(riga.importoScadenza ?: previsto)} · pagato ${formattaImporto(riga.pagato)} · resta ${formattaImporto(previsto)}"
                        else "Previsto: ${formattaImporto(previsto)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    riga.meseOrigine?.let { Text("Scadenza di ${formattaMese(it)} spostata in questo mese.", style = MaterialTheme.typography.bodySmall) }
                    when (riga.fonte) {
                        FontePrevisione.PERSONALIZZATA -> Text("Importo impostato per questa scadenza.", style = MaterialTheme.typography.bodySmall)
                        FontePrevisione.ANAGRAFICA -> Text("Importo previsto impostato in Anagrafica spese.", style = MaterialTheme.typography.bodySmall)
                        FontePrevisione.MEDIA -> {
                            Text("Media delle ultime ${riga.mediaSu.size} occorrenze pagate:", style = MaterialTheme.typography.bodySmall)
                            riga.mediaSu.forEach { (m, v) ->
                                Row(modifier = Modifier.padding(start = 12.dp)) {
                                    Text(formattaMese(m), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    Text(formattaImporto(v), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        FontePrevisione.NESSUNA -> Unit
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Sposta la scadenza", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    SceltaMese("Nuovo mese", nuovoMese, { nuovoMese = it })
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { mantieni = true }) {
                        RadioButton(selected = mantieni, onClick = { mantieni = true })
                        Text("Solo questa volta (le successive restano come prima)", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { mantieni = false }) {
                        RadioButton(selected = !mantieni, onClick = { mantieni = false })
                        Text(
                            "Riparti da quel mese: " + (if (voce.mesiRicorrenza == 1) "ogni mese" else "ogni ${voce.mesiRicorrenza} mesi") +
                                " (cambia il mese di partenza in anagrafica)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    OutlinedButton(onClick = {
                        vm.spostaScadenza(voce, meseScadenza, nuovoMese, mantieni)
                        onChiudi()
                    }, enabled = nuovoMese != mese) { Text("Sposta a ${formattaMese(nuovoMese)}") }
                }
                errore?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onChiudi) { Text("Chiudi") } }
    )

    inModifica?.let { op ->
        OperazioneDialog(vm, dati, op.contoValutaId, op, onChiudi = { inModifica = null })
    }
    if (confermaElimina) {
        AlertDialog(
            onDismissRequest = { confermaElimina = false },
            title = { Text("Elimina dal mese") },
            text = {
                Text(
                    "Eliminare ${voce.descrizione} da ${formattaMese(mese)}? La scadenza di questo mese viene annullata, " +
                        "le successive restano come in anagrafica. Si può ripristinare riaprendo il dettaglio."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.annullaScadenza(voce, meseScadenza, true)
                    confermaElimina = false
                    onChiudi()
                }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confermaElimina = false }) { Text("Annulla") } }
        )
    }
    daAssociare?.let { op ->
        AlertDialog(
            onDismissRequest = { daAssociare = null },
            title = { Text("Associa operazione") },
            text = {
                Text(
                    "L'operazione del ${formattaData(op.data)} di ${formattaCent(op.importoCent, dati.contiValutaPerId[op.contoValutaId]?.valuta ?: "EUR")} " +
                        "su ${dati.etichetta(op.contoValutaId)} prenderà il tipo \"${voce.tipo}\" senza sottotipo" +
                        (if (Calcoli.mese(op.data) != mese) " e sarà imputata alla spesa ricorrente di ${formattaMese(mese)}" else "") + "."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.associaARicorrente(op, voce, mese)
                    daAssociare = null
                }) { Text("Associa") }
            },
            dismissButton = { TextButton(onClick = { daAssociare = null }) { Text("Annulla") } }
        )
    }
    nuovaSuConto?.let { cv ->
        // Data proposta: oggi se nel mese, altrimenti il primo del mese (l'importo previsto va inserito a mano).
        val oggi = java.time.LocalDate.now()
        val giorno = if (YearMonth.from(oggi) == mese) oggi else mese.atDay(1)
        OperazioneDialog(
            vm, dati, cv, null, onChiudi = { nuovaSuConto = null },
            tipoIniziale = voce.tipo, sottotipoIniziale = voce.sottotipo, dataIniziale = giorno.toEpochDay()
        )
    }
}
