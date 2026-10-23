package com.desideri.familybalance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.data.Voce
import com.desideri.familybalance.logica.formattaData
import com.desideri.familybalance.logica.formattaCent
import com.desideri.familybalance.logica.formattaMese
import com.desideri.familybalance.logica.Calcoli
import com.desideri.familybalance.data.dataPerRicorrente
import kotlin.math.abs
import java.time.YearMonth

private enum class AzioneCondivisione { SCELTA, STIPENDIO, RICORRENTE }

/**
 * File condiviso con l'app (Condividi › FamilyBalance): si sceglie se è la busta paga di uno
 * stipendio (importata come dal pulsante della sezione) o la bolletta/ricevuta di una spesa
 * ricorrente. Per la ricevuta Gemini legge causale e data: se la causale contiene una parola
 * chiave di una spesa ricorrente la spesa è proposta, altrimenti la si sceglie a mano e la causale
 * (modificabile) si può aggiungere alle sue parole chiave; il mese è quello della data letta.
 */
@Composable
fun CondivisioneDialog(vm: SpeseViewModel) {
    val condiviso by vm.fileCondiviso.collectAsStateWithLifecycle()
    val f = condiviso ?: return
    val dati by vm.dati.collectAsStateWithLifecycle()
    val stipendi by vm.stipendi.collectAsStateWithLifecycle()
    var azione by remember(f.file) { mutableStateOf(AzioneCondivisione.SCELTA) }
    val tipi = stipendi.tipi.filterNot { it.obsoleto }
    val ricorrenti = dati.voci.filter { it.ricorrente && !it.entrata && !it.obsoleta }.sortedBy { it.descrizione.lowercase() }

    // Ricevuta letta: spese riconosciute dalla causale, scelta, mese e parola chiave da aggiungere.
    val letta = f.letta
    val riconosciute = letta?.let { l -> ricorrenti.filter { l.causale.isNotBlank() && it.riconosce(l.causale) } }.orEmpty()
    var manuale by remember(letta) { mutableStateOf(riconosciute.isEmpty()) }
    var voce by remember(letta) { mutableStateOf<Voce?>(riconosciute.firstOrNull()) }
    var mese by remember(letta) { mutableStateOf(letta?.data?.let { YearMonth.from(it) } ?: YearMonth.now()) }
    var parola by remember(letta) { mutableStateOf(letta?.causale.orEmpty()) }
    var aggiungiParola by remember(letta) { mutableStateOf(letta?.causale?.isNotBlank() == true) }
    var errore by remember(f.file) { mutableStateOf<String?>(null) }

    // Un solo stipendio: si importa subito per quello.
    LaunchedEffect(azione) {
        if (azione == AzioneCondivisione.STIPENDIO && tipi.size == 1) vm.importaCondivisoComeBustaPaga(tipi.first().id)
        if (azione == AzioneCondivisione.RICORRENTE && f.letta == null && !f.inLettura) vm.leggiRicevutaCondivisa()
    }

    AlertDialog(
        onDismissRequest = { if (!f.inLettura) vm.annullaFileCondiviso() },
        title = { Text("File condiviso") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(f.nome, style = MaterialTheme.typography.bodySmall)
                when (azione) {
                    AzioneCondivisione.SCELTA -> {
                        Text("A cosa si riferisce?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = {
                            if (tipi.isEmpty()) errore = "Nessuno stipendio in anagrafica" else azione = AzioneCondivisione.STIPENDIO
                        }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Payments, contentDescription = null)
                            Text("Busta paga di uno stipendio", modifier = Modifier.padding(start = 8.dp))
                        }
                        OutlinedButton(onClick = {
                            if (ricorrenti.isEmpty()) errore = "Nessuna spesa ricorrente in anagrafica" else azione = AzioneCondivisione.RICORRENTE
                        }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.EventRepeat, contentDescription = null)
                            Text("Bolletta o ricevuta di una spesa ricorrente", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    AzioneCondivisione.STIPENDIO -> {
                        Text("Per quale stipendio è la busta paga?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        tipi.forEach { t ->
                            OutlinedButton(onClick = { vm.importaCondivisoComeBustaPaga(t.id) }, modifier = Modifier.fillMaxWidth()) { Text(t.nome) }
                        }
                    }
                    AzioneCondivisione.RICORRENTE -> when {
                        f.inLettura || letta == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                            Text("Lettura di causale e data con Gemini…")
                        }
                        else -> {
                            val letta = letta!!
                            Text(
                                "Causale: " + letta.causale.ifBlank { "non riconosciuta" } +
                                    (letta.importoCent?.let { " · ${formattaCent(it)}" } ?: "") +
                                    (letta.data?.let { " · eseguita il ${formattaData(it.toEpochDay())}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (!manuale) {
                                Text(
                                    if (riconosciute.size == 1) "Riconosciuta dalle parole chiave:" else "Riconosciute dalle parole chiave (scegli):",
                                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                                )
                                riconosciute.forEach { v ->
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { voce = v }) {
                                        RadioButton(selected = voce?.id == v.id, onClick = { voce = v })
                                        Text(v.descrizione)
                                    }
                                }
                                TextButton(onClick = { manuale = true; voce = null }) { Text("Scegli un'altra spesa") }
                            } else {
                                if (riconosciute.isEmpty()) Text("Nessuna spesa riconosciuta dalle parole chiave: sceglila.", style = MaterialTheme.typography.bodySmall)
                                CampoScelta(
                                    etichetta = "Spesa ricorrente",
                                    selezionato = voce,
                                    opzioni = ricorrenti,
                                    testo = { it.descrizione },
                                    onScelta = { voce = it; errore = null },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { aggiungiParola = !aggiungiParola }) {
                                    Checkbox(checked = aggiungiParola, onCheckedChange = { aggiungiParola = it })
                                    Text("Aggiungi una parola chiave alla spesa")
                                }
                                if (aggiungiParola) {
                                    OutlinedTextField(
                                        value = parola, onValueChange = { parola = it },
                                        label = { Text("Parola chiave") },
                                        supportingText = { Text("Le prossime ricevute con questo testo nella causale saranno riconosciute.") },
                                        singleLine = true, modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            // Spese della voce nei due mesi prima e dopo quello letto: si propone il mese con lo stesso importo.
                            voce?.let { v ->
                                val centro = letta.data?.let { YearMonth.from(it) } ?: YearMonth.now()
                                val vicini = (-2L..2L).map { centro.plusMonths(it) }.map { m ->
                                    m to dati.operazioni.filter { it.voceId == v.id && !it.trasferimento && Calcoli.mese(it.dataPerRicorrente) == m }.sumOf { abs(it.importoCent) }
                                }
                                val stessoImporto = letta.importoCent?.let { imp -> vicini.firstOrNull { (_, c) -> c != 0L && abs(c - imp) <= 1 }?.first }
                                LaunchedEffect(v.id, stessoImporto) { if (stessoImporto != null) mese = stessoImporto }
                                Text("Spese di ${v.descrizione} vicino a ${formattaMese(centro)}:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                vicini.forEach { (m, cent) ->
                                    val uguale = m == stessoImporto
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { mese = m }) {
                                        RadioButton(selected = mese == m, onClick = { mese = m })
                                        Text(formattaMese(m), modifier = Modifier.weight(1f), fontWeight = if (uguale) FontWeight.Bold else null)
                                        Text(
                                            if (cent == 0L) "—" else formattaCent(cent) + if (uguale) " = importo" else "",
                                            color = if (uguale) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (uguale) FontWeight.Bold else null
                                        )
                                    }
                                }
                            }
                            SceltaMese("Anno e mese della spesa", mese, { mese = it }, Modifier.fillMaxWidth())
                        }
                    }
                }
                errore?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (azione == AzioneCondivisione.RICORRENTE && letta != null && !f.inLettura) {
                TextButton(onClick = {
                    val v = voce
                    if (v == null) errore = "Scegli la spesa ricorrente"
                    else vm.allegaCondivisoARicorrente(v, mese, parola.takeIf { manuale && aggiungiParola })
                }) { Text("Allega") }
            }
        },
        dismissButton = { if (!f.inLettura) TextButton(onClick = { vm.annullaFileCondiviso() }) { Text("Annulla") } }
    )
}
