package com.desideri.familybalance.ui

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
import androidx.compose.foundation.lazy.itemsIndexed
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
 * Seconda fase dell'import di un estratto conto: i movimenti non ancora presenti, con il tipo
 * preselezionato quando una sola associazione corrisponde alla descrizione (con più associazioni
 * l'utente sceglie tra quelle proposte). Da ogni movimento si può creare una nuova associazione
 * usando la descrizione, o parte di essa, come chiave.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportEstrattoDialog(vm: SpeseViewModel, dati: DatiApp, importazione: ImportEstratto) {
    val stati = remember(importazione) {
        mutableStateListOf(*importazione.righe.map { r ->
            r.candidate.singleOrNull()?.let { StatoRiga().con(it, r, dati) } ?: StatoRiga()
        }.toTypedArray())
    }
    var nuovaAssociazioneRiga by remember { mutableStateOf<Int?>(null) }

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
                    "${importazione.totali} movimenti letti da Gemini, ${importazione.saltate} già presenti (saltati), " +
                        "${importazione.righe.size} da verificare. Scegli tipo/sottotipo di ognuno; quelli senza tipo o " +
                        "deselezionati non vengono importati.",
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
                LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(importazione.righe, key = { _, r -> r.id }) { i, riga ->
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
        colors = if (!stato.includi) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = stato.includi, onCheckedChange = { onStato(stato.copy(includi = it)) })
                Text(formattaData(m.data.toEpochDay()), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TestoImporto(m.importoCent / 100.0, m.valuta, grassetto = true)
            }
            Text(m.descrizione.ifBlank { "(senza descrizione)" }, style = MaterialTheme.typography.bodyMedium)
            if (riga.giaPresenti > 1) {
                Text(
                    "Attenzione: già ${riga.giaPresenti} operazioni con stessa data e importo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (stato.includi) {
                if (riga.candidate.size > 1) {
                    Text("Più associazioni corrispondono:", style = MaterialTheme.typography.labelMedium)
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
