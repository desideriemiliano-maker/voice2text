package com.desideri.familybalance.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import com.desideri.familybalance.estratto.Presenza
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.desideri.familybalance.DatiApp
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.data.Associazione
import com.desideri.familybalance.estratto.AggiornamentoData
import com.desideri.familybalance.estratto.ImportEstratto
import com.desideri.familybalance.estratto.RigaEstratto
import com.desideri.familybalance.estratto.SceltaEstratto
import com.desideri.familybalance.estratto.TipoData
import com.desideri.familybalance.logica.Associazioni
import com.desideri.familybalance.logica.formattaData

/** true se il movimento ha un'operazione esistente con data diversa da quella scelta. */
private fun dataDiversa(riga: RigaEstratto, tipoData: TipoData): Boolean =
    riga.esistente != null && riga.esistente.data != riga.movimento.data(tipoData).toEpochDay()

/** Filtri del pannello di import. */
private enum class FiltroImport(val etichetta: String) {
    TUTTI("Tutti"),
    DA_IMPOSTARE("Da impostare"),
    SELEZIONATI("Selezionati"),
    NON_SELEZIONATI("Non selezionati"),
    PIU_TIPI("Più tipi possibili"),
    GIA_PRESENTI("Già presenti/doppioni"),
    DATA_DA_AGGIORNARE("Data da aggiornare");

    fun corrisponde(riga: RigaEstratto, stato: StatoRiga, tipoData: TipoData): Boolean = when (this) {
        TUTTI -> true
        DA_IMPOSTARE -> stato.includi && (stato.tipo.isBlank() || (stato.spostamento && stato.destinazione == null))
        SELEZIONATI -> stato.includi
        NON_SELEZIONATI -> !stato.includi
        PIU_TIPI -> riga.piuCandidati
        GIA_PRESENTI -> riga.presenza != Presenza.NUOVA
        DATA_DA_AGGIORNARE -> dataDiversa(riga, tipoData)
    }
}

/** Scelte in corso per un movimento dell'estratto conto. */
private data class StatoRiga(
    val includi: Boolean = true,
    /** Per i movimenti già presenti: aggiornare la data dell'operazione esistente. */
    val aggiornaData: Boolean = false,
    val tipo: String = "",
    val sottotipo: String = "",
    val destinazione: Long? = null
) {
    val spostamento: Boolean get() = tipo.trim().equals(Associazione.TIPO_SPOSTAMENTO, ignoreCase = true)
}

private fun destinazionePredefinita(riga: RigaEstratto, dati: DatiApp): Long? {
    val propria = dati.contiValutaPerId[riga.contoValutaId]
    val altri = dati.contiValutaOrdinati.filter { it.contoId != propria?.contoId }
    return (altri.firstOrNull { it.valuta == propria?.valuta } ?: altri.firstOrNull())?.id
}

/** Applica un'associazione (tipo/sottotipo o spostamento) allo stato di una riga. */
private fun StatoRiga.con(associazione: Associazione, riga: RigaEstratto, dati: DatiApp): StatoRiga {
    val nuovo = copy(tipo = associazione.tipo, sottotipo = associazione.sottotipo ?: "")
    return if (nuovo.spostamento) nuovo.copy(destinazione = destinazione ?: destinazionePredefinita(riga, dati)) else nuovo
}

/**
 * Seconda fase dell'import di un estratto conto: tutti i movimenti letti (quelli già presenti o
 * possibili doppioni deselezionati), con filtri per vederne una parte, e il tipo
 * preselezionato quando una sola associazione corrisponde alla descrizione (con più associazioni
 * l'utente sceglie tra quelle proposte). Da ogni movimento si può creare una nuova associazione
 * usando la descrizione, o parte di essa, come chiave.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportEstrattoDialog(vm: SpeseViewModel, dati: DatiApp, importazione: ImportEstratto) {
    val stati = remember(importazione) {
        mutableStateListOf(*importazione.righe.map { r ->
            // Già presenti o possibili doppioni partono deselezionati.
            val base = StatoRiga(includi = r.presenza == Presenza.NUOVA, aggiornaData = r.presenza == Presenza.PRESENTE)
            r.candidate.singleOrNull()?.let { base.con(it, r, dati) } ?: base
        }.toTypedArray())
    }
    var nuovaAssociazioneRiga by remember { mutableStateOf<Int?>(null) }
    var filtro by remember { mutableStateOf(FiltroImport.TUTTI) }
    var aggiornamentoFiltro by remember { mutableStateOf(0) }
    // Quale data registrare: ricordata per conto dall'ultimo import.
    var tipoData by remember(importazione) { mutableStateOf(vm.tipoDataEstratto(importazione.contoId)) }

    val tipi = remember(dati.voci) { (dati.vociAttive.map { it.tipo } + Associazione.TIPO_SPOSTAMENTO).distinct().sortedBy { it.lowercase() } }
    fun valida(stato: StatoRiga) = stato.includi && stato.tipo.isNotBlank() && (!stato.spostamento || stato.destinazione != null)
    val daImportare = stati.count { valida(it) }
    val dateDaAggiornare = importazione.righe.indices.filter { stati[it].aggiornaData && dataDiversa(importazione.righe[it], tipoData) }

    /**
     * Operazioni esistenti da aggiornare: i già presenti (e i possibili doppioni confermati con la
     * casella) ricevono l'ordine dell'estratto conto e, se richiesto, la nuova data.
     */
    fun aggiornamenti(): List<AggiornamentoData> = importazione.righe.indices.mapNotNull { i ->
        val r = importazione.righe[i]
        val esistente = r.esistente ?: return@mapNotNull null
        if (r.presenza != Presenza.PRESENTE && !stati[i].aggiornaData) return@mapNotNull null
        val nuovaData = if (i in dateDaAggiornare) r.movimento.data(tipoData) else null
        AggiornamentoData(esistente, nuovaData, r.ordine)
    }
    val senzaTipo = stati.count { it.includi && !valida(it) }
    val conto = importazione.righe.firstOrNull()?.let { dati.etichetta(it.contoValutaId) }
        ?: dati.conti.firstOrNull { it.id == importazione.contoId }?.nome ?: "conto"

    Dialog(onDismissRequest = { vm.annullaImportEstratto() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(16.dp)) {
                Text("Import estratto conto · $conto", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${importazione.righe.size} movimenti letti da Gemini: ${importazione.presenti} già presenti e " +
                        "${importazione.simili} possibili doppioni (deselezionati). Scegli tipo/sottotipo di quelli da " +
                        "importare; quelli senza tipo o deselezionati non vengono importati.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
                // Blocchi non letti o risposte incomplete di Gemini: i movimenti relativi mancano dall'elenco.
                importazione.avvisi.forEach { avviso ->
                    Text("⚠ $avviso", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (importazione.avvisi.isNotEmpty()) {
                    Text(
                        "Dettagli nel Registro Gemini (menu ⋮). Puoi importare questi movimenti e ripetere l'import più tardi: " +
                            "quelli già importati risulteranno \"Già presenti\".",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Da importare $daImportare" + (if (senzaTipo > 0) " · senza tipo $senzaTipo" else "") +
                            if (dateDaAggiornare.isNotEmpty()) " · date da aggiornare ${dateDaAggiornare.size}" else "",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { vm.annullaImportEstratto() }) { Text("Annulla") }
                    Button(
                        onClick = {
                            vm.confermaImportEstratto(
                                importazione.righe.indices.filter { valida(stati[it]) }.map { i ->
                                    val s = stati[i]
                                    SceltaEstratto(
                                        riga = importazione.righe[i],
                                        data = importazione.righe[i].movimento.data(tipoData),
                                        tipo = if (s.spostamento) Associazione.TIPO_SPOSTAMENTO else s.tipo.trim(),
                                        sottotipo = s.sottotipo.trim().ifEmpty { null },
                                        destinazioneId = if (s.spostamento) s.destinazione else null
                                    )
                                },
                                aggiornamenti()
                            )
                        },
                        enabled = daImportare > 0 || aggiornamenti().isNotEmpty()
                    ) { Text("Importa") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Data da registrare:", style = MaterialTheme.typography.labelLarge)
                    TipoData.entries.forEach { t ->
                        val presenti = importazione.righe.count { it.movimento.dataDi(t) != null }
                        FilterChip(
                            selected = tipoData == t,
                            onClick = {
                                tipoData = t
                                vm.salvaTipoDataEstratto(importazione.contoId, t)
                            },
                            label = { Text("${t.etichetta} ($presenti)") }
                        )
                    }
                }
                // Filtri: mostrano solo una parte dei movimenti (le scelte fatte restano valide su tutti).
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FiltroImport.entries.forEach { f ->
                        val numero = importazione.righe.indices.count { f.corrisponde(importazione.righe[it], stati[it], tipoData) }
                        FilterChip(
                            selected = filtro == f,
                            onClick = {
                                filtro = f
                                aggiornamentoFiltro++
                            },
                            label = { Text("${f.etichetta} ($numero)") }
                        )
                    }
                }
                // Elenco filtrato "fotografato" quando si sceglie il filtro: modificare un movimento (es.
                // iniziare a scrivere il tipo in "Da impostare") non lo fa sparire mentre lo si compila.
                // Si ricalcola cambiando filtro o toccando di nuovo quello attivo.
                val visibili = remember(filtro, aggiornamentoFiltro, importazione) {
                    importazione.righe.indices.filter { filtro.corrisponde(importazione.righe[it], stati[it], tipoData) }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { visibili.forEach { stati[it] = stati[it].copy(includi = true) } }, enabled = visibili.isNotEmpty()) {
                        Text("Seleziona visibili")
                    }
                    TextButton(onClick = { visibili.forEach { stati[it] = stati[it].copy(includi = false) } }, enabled = visibili.isNotEmpty()) {
                        Text("Deseleziona visibili")
                    }
                }
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (visibili.isEmpty()) item { Text("Nessun movimento con questo filtro.", modifier = Modifier.padding(8.dp)) }
                    items(visibili, key = { importazione.righe[it].id }) { i ->
                        val riga = importazione.righe[i]
                        val stato = stati[i]
                        CardMovimento(
                            riga = riga,
                            tipoData = tipoData,
                            stato = stato,
                            dati = dati,
                            tipi = tipi,
                            onStato = { stati[i] = it },
                            onNuovaAssociazione = { nuovaAssociazioneRiga = i }
                        )
                    }
                }
            }
        }
    }

    nuovaAssociazioneRiga?.let { i ->
        val riga = importazione.righe[i]
        NuovaAssociazioneDialog(
            descrizione = riga.movimento.descrizione,
            statoIniziale = stati[i],
            tipi = tipi,
            dati = dati,
            onSalva = { associazione ->
                vm.salvaAssociazione(associazione)
                // La nuova chiave vale subito anche per gli altri movimenti ancora senza tipo.
                importazione.righe.forEachIndexed { j, r ->
                    if (j == i || (stati[j].tipo.isBlank() && Associazioni.contiene(r.movimento.descrizione, associazione.chiave))) {
                        stati[j] = stati[j].con(associazione, r, dati)
                    }
                }
                nuovaAssociazioneRiga = null
            },
            onAnnulla = { nuovaAssociazioneRiga = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardMovimento(
    riga: RigaEstratto,
    tipoData: TipoData,
    stato: StatoRiga,
    dati: DatiApp,
    tipi: List<String>,
    onStato: (StatoRiga) -> Unit,
    onNuovaAssociazione: () -> Unit
) {
    val m = riga.movimento
    val valutaConto = dati.contiValutaPerId[riga.contoValutaId]?.valuta ?: m.valuta
    val tipoNoto = tipi.any { it.equals(stato.tipo.trim(), ignoreCase = true) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        // Più tipi possibili: bordo evidenziato finché non se ne sceglie uno.
        border = if (riga.piuCandidati && stato.includi) BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary) else null,
        // Associati (tipo scelto) in evidenza, esclusi in grigio, da associare con il colore normale.
        colors = when {
            !stato.includi -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            stato.tipo.isNotBlank() -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            else -> CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = stato.includi, onCheckedChange = { onStato(stato.copy(includi = it)) })
                Column(modifier = Modifier.weight(1f)) {
                    Text(formattaData(m.data(tipoData).toEpochDay()), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    // Numero progressivo del movimento nel file, per il riscontro con l'Excel.
                    m.rigaFile?.let { Text("movimento n. $it del file", style = MaterialTheme.typography.labelSmall) }
                }
                TestoImporto(m.importoCent / 100.0, valutaConto, grassetto = true)
            }
            // Gemini ha indicato una valuta diversa da quella del conto scelto: l'importo potrebbe
            // essere quello in valuta estera citato nella descrizione.
            if (!m.valuta.equals(valutaConto, ignoreCase = true)) {
                Text(
                    "⚠ Gemini indica la valuta ${m.valuta}, il conto è in $valutaConto: verifica l'importo",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
            // Tutte le date del movimento; se manca quella scelta si usa la prima disponibile.
            Text(
                TipoData.entries.mapNotNull { t -> m.dataDi(t)?.let { "${t.etichetta} ${formattaData(it.toEpochDay())}" } }.joinToString(" · ") +
                    if (m.dataDi(tipoData) == null) " (manca la data ${tipoData.etichetta.lowercase()})" else "",
                style = MaterialTheme.typography.labelSmall
            )
            Text(m.descrizione.ifBlank { "(senza descrizione)" }, style = MaterialTheme.typography.bodyMedium)
            when (riga.presenza) {
                Presenza.PRESENTE -> Text(
                    "Già presente: operazione con stesso importo e stessa data",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Presenza.SIMILE -> Text(
                    "Possibile doppione: stesso importo a ${riga.giorniDistanza} giorni di distanza",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Presenza.NUOVA -> Unit
            }
            riga.esistente?.let { esistente ->
                val nuova = m.data(tipoData).toEpochDay()
                if (esistente.data != nuova) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = stato.aggiornaData, onCheckedChange = { onStato(stato.copy(aggiornaData = it)) })
                        Text(
                            "Aggiorna la data dell'operazione esistente: ${formattaData(esistente.data)} → ${formattaData(nuova)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Text("Operazione esistente con la stessa data", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (stato.includi) {
                if (riga.piuCandidati) {
                    Text(
                        "⚠ Più tipi possibili: scegline uno",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        riga.candidate.forEach { a ->
                            val selezionata = a.tipo.equals(stato.tipo, true) && (a.sottotipo ?: "").equals(stato.sottotipo, true)
                            FilterChip(
                                selected = selezionata,
                                onClick = { onStato(stato.con(a, riga, dati)) },
                                label = { Text("${a.destinazione} («${a.chiave}»)") }
                            )
                        }
                    }
                }
                // Come nella creazione di un'operazione: spostamento tra conti con la sua casella.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = stato.spostamento, onCheckedChange = { attivo ->
                        onStato(
                            if (attivo) stato.copy(tipo = Associazione.TIPO_SPOSTAMENTO, sottotipo = "", destinazione = stato.destinazione ?: destinazionePredefinita(riga, dati))
                            else stato.copy(tipo = "", sottotipo = "")
                        )
                    })
                    Text("Spostamento tra conti", style = MaterialTheme.typography.bodyMedium)
                }
                if (!stato.spostamento) {
                    CampoAutocompletamento("Tipo", stato.tipo, tipi, { t ->
                        val nuovo = stato.copy(tipo = t, sottotipo = "")
                        onStato(if (nuovo.spostamento) nuovo.copy(destinazione = stato.destinazione ?: destinazionePredefinita(riga, dati)) else nuovo)
                    })
                }
                if (stato.spostamento) {
                    val propria = dati.contiValutaPerId[riga.contoValutaId]
                    val altri = dati.contiValutaOrdinati.filter { it.id != riga.contoValutaId && it.contoId != propria?.contoId }
                    CampoScelta(
                        etichetta = if (m.importoCent < 0) "Conto di destinazione" else "Conto di provenienza",
                        selezionato = altri.firstOrNull { it.id == stato.destinazione },
                        opzioni = altri,
                        testo = { dati.etichetta(it.id) },
                        onScelta = { onStato(stato.copy(destinazione = it.id)) }
                    )
                } else {
                    val sottotipi = dati.vociAttive.filter { it.tipo.equals(stato.tipo.trim(), true) }.mapNotNull { it.sottotipo }.distinct().sortedBy { it.lowercase() }
                    CampoAutocompletamento("Sottotipo (opzionale)", stato.sottotipo, sottotipi, { onStato(stato.copy(sottotipo = it)) })
                    if (stato.tipo.isNotBlank() && !tipoNoto) {
                        Text("Tipo nuovo: verrà creato in anagrafica", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                TextButton(onClick = onNuovaAssociazione) { Text("Crea associazione dalla descrizione…") }
            }
        }
    }
}

/** Nuova associazione con chiave presa dalla descrizione (modificabile per tenerne solo una parte). */
@Composable
private fun NuovaAssociazioneDialog(
    descrizione: String,
    statoIniziale: StatoRiga,
    tipi: List<String>,
    dati: DatiApp,
    onSalva: (Associazione) -> Unit,
    onAnnulla: () -> Unit
) {
    var chiave by remember { mutableStateOf(descrizione) }
    var tipo by remember { mutableStateOf(statoIniziale.tipo) }
    var sottotipo by remember { mutableStateOf(statoIniziale.sottotipo) }
    val sottotipi = dati.vociAttive.filter { it.tipo.equals(tipo.trim(), true) }.mapNotNull { it.sottotipo }.distinct().sortedBy { it.lowercase() }
    val valida = chiave.isNotBlank() && tipo.isNotBlank() && Associazioni.contiene(descrizione, chiave)

    AlertDialog(
        onDismissRequest = onAnnulla,
        title = { Text("Nuova associazione") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Descrizione: $descrizione", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = chiave,
                    onValueChange = { chiave = it },
                    label = { Text("Chiave (tutta la descrizione o una parte)") },
                    isError = chiave.isNotBlank() && !Associazioni.contiene(descrizione, chiave),
                    supportingText = { Text("Cancella le parti variabili (date, numeri di carta…)") },
                    modifier = Modifier.fillMaxWidth()
                )
                CampoAutocompletamento("Tipo", tipo, tipi, { tipo = it; sottotipo = "" })
                if (!tipo.trim().equals(Associazione.TIPO_SPOSTAMENTO, true)) {
                    CampoAutocompletamento("Sottotipo (opzionale)", sottotipo, sottotipi, { sottotipo = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSalva(Associazione(chiave = chiave.trim(), tipo = tipo.trim(), sottotipo = sottotipo.trim().ifEmpty { null })) },
                enabled = valida
            ) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onAnnulla) { Text("Annulla") } }
    )
}
