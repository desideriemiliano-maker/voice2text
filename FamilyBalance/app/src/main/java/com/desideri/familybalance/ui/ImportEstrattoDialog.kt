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
import com.desideri.familybalance.estratto.ImportEstratto
import com.desideri.familybalance.estratto.RigaEstratto
import com.desideri.familybalance.estratto.SceltaEstratto
import com.desideri.familybalance.logica.Associazioni
import com.desideri.familybalance.logica.formattaData

/** Filtri del pannello di import. */
private enum class FiltroImport(val etichetta: String) {
    TUTTI("Tutti"),
    DA_IMPOSTARE("Da impostare"),
    SELEZIONATI("Selezionati"),
    NON_SELEZIONATI("Non selezionati"),
    PIU_TIPI("Più tipi possibili"),
    GIA_PRESENTI("Già presenti/doppioni");

    fun corrisponde(riga: RigaEstratto, stato: StatoRiga): Boolean = when (this) {
        TUTTI -> true
        DA_IMPOSTARE -> stato.includi && (stato.tipo.isBlank() || (stato.spostamento && stato.destinazione == null))
        SELEZIONATI -> stato.includi
        NON_SELEZIONATI -> !stato.includi
        PIU_TIPI -> riga.piuCandidati
        GIA_PRESENTI -> riga.presenza != Presenza.NUOVA
    }
}

/** Scelte in corso per un movimento dell'estratto conto. */
private data class StatoRiga(
    val includi: Boolean = true,
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
            val base = StatoRiga(includi = r.presenza == Presenza.NUOVA)
            r.candidate.singleOrNull()?.let { base.con(it, r, dati) } ?: base
        }.toTypedArray())
    }
    var nuovaAssociazioneRiga by remember { mutableStateOf<Int?>(null) }
    var filtro by remember { mutableStateOf(FiltroImport.TUTTI) }

    val tipi = remember(dati.voci) { (dati.voci.map { it.tipo } + Associazione.TIPO_SPOSTAMENTO).distinct().sortedBy { it.lowercase() } }
    fun valida(stato: StatoRiga) = stato.includi && stato.tipo.isNotBlank() && (!stato.spostamento || stato.destinazione != null)
    val daImportare = stati.count { valida(it) }
    val senzaTipo = stati.count { it.includi && !valida(it) }
    val conto = dati.conti.firstOrNull { it.id == importazione.contoId }?.nome ?: "conto"

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
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Da importare $daImportare" + if (senzaTipo > 0) " · senza tipo $senzaTipo" else "",
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
                                        tipo = if (s.spostamento) Associazione.TIPO_SPOSTAMENTO else s.tipo.trim(),
                                        sottotipo = s.sottotipo.trim().ifEmpty { null },
                                        destinazioneId = if (s.spostamento) s.destinazione else null
                                    )
                                }
                            )
                        },
                        enabled = daImportare > 0
                    ) { Text("Importa") }
                }
                // Filtri: mostrano solo una parte dei movimenti (le scelte fatte restano valide su tutti).
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FiltroImport.entries.forEach { f ->
                        val numero = importazione.righe.indices.count { f.corrisponde(importazione.righe[it], stati[it]) }
                        FilterChip(selected = filtro == f, onClick = { filtro = f }, label = { Text("${f.etichetta} ($numero)") })
                    }
                }
                val visibili = importazione.righe.indices.filter { filtro.corrisponde(importazione.righe[it], stati[it]) }
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
    stato: StatoRiga,
    dati: DatiApp,
    tipi: List<String>,
    onStato: (StatoRiga) -> Unit,
    onNuovaAssociazione: () -> Unit
) {
    val m = riga.movimento
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
                Text(formattaData(m.data.toEpochDay()), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TestoImporto(m.importoCent / 100.0, m.valuta, grassetto = true)
            }
            m.dataContabile?.let {
                Text("Data valuta ${formattaData(m.data.toEpochDay())} · contabile ${formattaData(it.toEpochDay())}", style = MaterialTheme.typography.labelSmall)
            }
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
                CampoAutocompletamento("Tipo", stato.tipo, tipi, { t ->
                    val nuovo = stato.copy(tipo = t, sottotipo = "")
                    onStato(if (nuovo.spostamento) nuovo.copy(destinazione = stato.destinazione ?: destinazionePredefinita(riga, dati)) else nuovo)
                })
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
                    val sottotipi = dati.voci.filter { it.tipo.equals(stato.tipo.trim(), true) }.mapNotNull { it.sottotipo }.distinct().sortedBy { it.lowercase() }
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
    val sottotipi = dati.voci.filter { it.tipo.equals(tipo.trim(), true) }.mapNotNull { it.sottotipo }.distinct().sortedBy { it.lowercase() }
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
