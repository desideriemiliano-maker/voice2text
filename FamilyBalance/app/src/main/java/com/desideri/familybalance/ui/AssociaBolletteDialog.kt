package com.desideri.familybalance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.desideri.familybalance.importazione.AnalisiImport
import com.desideri.familybalance.importazione.CombinazioneBollette
import com.desideri.familybalance.importazione.SceltaBollette
import com.desideri.familybalance.logica.formattaCent
import com.desideri.familybalance.logica.formattaData

private data class Opzione(val valore: String, val etichetta: String)

private val COLLATOR: java.text.Collator = java.text.Collator.getInstance(java.util.Locale.ITALIAN).apply {
    strength = java.text.Collator.PRIMARY
}

/** Ordine alfabetico italiano, senza distinzione di maiuscole e accenti. */
private val ORDINE_ALFABETICO: Comparator<String> = Comparator { a, b -> COLLATOR.compare(a, b) }

/**
 * Seconda fase dell'import da Excel: per ogni combinazione tipo/sottotipo "Bollette" trovata nei
 * conti l'utente sceglie a quale spesa ricorrente del foglio Bollette associarla. Le scelte
 * memorizzate in import precedenti ([memorizzate]) sono già compilate, così come il suggerimento
 * per nome; le combinazioni nuove sono mostrate per prime.
 */
@Composable
fun AssociaBolletteDialog(
    analisi: AnalisiImport,
    memorizzate: Map<String, String>,
    onConferma: (Map<String, String>) -> Unit,
    onSalva: (Map<String, String>) -> Unit,
    onAnnulla: () -> Unit
) {
    val scelte = remember(analisi) {
        mutableStateMapOf<String, String>().apply {
            analisi.combinazioni.forEach { c ->
                (memorizzate[c.chiave] ?: c.suggerimento?.let { SceltaBollette.ricorrente(it) })?.let { put(c.chiave, it) }
            }
        }
    }
    val ordinate = remember(analisi) { analisi.combinazioni.sortedBy { it.chiave in memorizzate } }
    val totale = analisi.combinazioni.size
    val mancanti = analisi.combinazioni.count { it.chiave !in scelte }

    Dialog(onDismissRequest = onAnnulla, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            // Barre di sistema e tastiera escluse: i pulsanti non devono finire sotto la barra di navigazione.
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(16.dp)) {
                Text("Associa le Bollette alle spese ricorrenti", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Le spese ricorrenti sono quelle del foglio Bollette (${analisi.ricorrenti.size}). Per ogni tipo/sottotipo " +
                        "\"Bollette\" dei conti scegli a quale associarlo (quelle senza sottotipo una per una). " +
                        "Quelle lasciate vuote finiscono nella voce generica \"Bollette\", da sistemare poi in " +
                        "Anagrafica spese. \"Salva scelte\" memorizza quanto fatto finora senza importare.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
                // Azioni in alto, sempre visibili anche con elenchi lunghi.
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Associate ${totale - mancanti}/$totale",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onAnnulla) { Text("Annulla") }
                    OutlinedButton(onClick = { onSalva(scelte.toMap()) }) { Text("Salva scelte") }
                }
                Button(
                    onClick = { onConferma(scelte.toMap()) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)
                ) { Text(if (mancanti == 0) "Importa" else "Importa ($mancanti in Bollette generica)") }
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ordinate, key = { it.chiave }) { c ->
                        CardCombinazione(
                            combinazione = c,
                            memorizzata = c.chiave in memorizzate,
                            opzioni = opzioni(c, analisi, memorizzate[c.chiave]),
                            scelta = scelte[c.chiave],
                            onScelta = { scelte[c.chiave] = it }
                        )
                    }
                }
            }
        }
    }
}

private fun opzioni(c: CombinazioneBollette, analisi: AnalisiImport, memorizzata: String?): List<Opzione> {
    // Ricorrenti in ordine alfabetico (senza distinzione di maiuscole/accenti); in fondo le opzioni speciali.
    val lista = analisi.ricorrenti
        .sortedWith(compareBy(ORDINE_ALFABETICO) { it.nome })
        .map { r -> Opzione(SceltaBollette.ricorrente(r.nome), r.nome + if (r.mesi == 1) " · ogni mese" else " · ogni ${r.mesi} mesi") }
        .toMutableList()
    val nomi = analisi.ricorrenti.map { it.nome.lowercase() }.toSet()
    // Scelta memorizzata verso una ricorrente non più presente nel foglio: resta selezionabile.
    memorizzata?.let { SceltaBollette.nomeRicorrente(it) }?.takeIf { it.lowercase() !in nomi }?.let {
        lista += Opzione(SceltaBollette.ricorrente(it), "$it · nuova ricorrente")
    }
    c.sottotipo?.takeIf { it.lowercase() !in nomi && lista.none { o -> o.valore == SceltaBollette.ricorrente(it) } }?.let {
        lista += Opzione(SceltaBollette.ricorrente(it), "Nuova ricorrente «$it»")
    }
    // Senza sottotipo "non ricorrente" coinciderebbe con la voce generica: basta lasciarla vuota.
    if (c.sottotipo != null) lista += Opzione(SceltaBollette.NON_RICORRENTE, "Non ricorrente (${c.tipo} / ${c.sottotipo})")
    return lista
}

@Composable
private fun CardCombinazione(
    combinazione: CombinazioneBollette,
    memorizzata: Boolean,
    opzioni: List<Opzione>,
    scelta: String?,
    onScelta: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (scelta == null) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Tipo: ${combinazione.tipo} · Sottotipo: ${combinazione.sottotipo ?: "—"}", style = MaterialTheme.typography.bodyMedium)
            if (combinazione.singola) {
                // Bollette senza sottotipo: una singola operazione, riconoscibile da data e importo.
                val (valuta, cent) = combinazione.totaliPerValuta.entries.first()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formattaData(combinazione.prima.toEpochDay()),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TestoImporto(cent / 100.0, valuta, grassetto = true)
                }
                Text("Conto: ${combinazione.contoValuta}", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Sottotipo: ${combinazione.sottotipo ?: "—"}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${combinazione.operazioni} operazioni · dal ${formattaData(combinazione.prima.toEpochDay())} al ${formattaData(combinazione.ultima.toEpochDay())} · " +
                        combinazione.totaliPerValuta.entries.joinToString(" + ") { (valuta, cent) -> formattaCent(cent, valuta) },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (scelta == null) {
                Text("Vuota: andrà nella voce generica \"Bollette\"", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (memorizzata) {
                Text("Scelta memorizzata da un import precedente", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            CampoScelta(
                etichetta = "Spesa ricorrente",
                selezionato = opzioni.firstOrNull { it.valore == scelta },
                opzioni = opzioni,
                testo = { it.etichetta },
                onScelta = { onScelta(it.valore) }
            )
        }
    }
}
