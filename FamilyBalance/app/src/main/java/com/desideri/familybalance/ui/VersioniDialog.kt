package com.desideri.familybalance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.desideri.familybalance.BuildConfig
import com.desideri.familybalance.changelog.CHANGELOG
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val FORMATO_DATA_VISUALIZZATA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Riconosce un prefisso convenzionale ("feat:", "fix:", ...) a inizio messaggio e lo separa dal testo. */
private fun classificaMessaggio(messaggio: String): Pair<String?, String> {
    val match = Regex("^([\\wÀ-ÿ]+)\\s*:\\s*").find(messaggio) ?: return null to messaggio
    val prefisso = match.groupValues[1]
    return prefisso to messaggio.substring(match.range.last + 1).trim()
}

/** Icona ed etichetta da mostrare per il [prefisso] di un gruppo di commit. */
private fun iconaEEtichettaPrefisso(prefisso: String?): Pair<String, String> = when {
    prefisso == null -> "•" to "Generale"
    prefisso.equals("feat", ignoreCase = true) -> "⭐" to "Novità"
    prefisso.equals("fix", ignoreCase = true) -> "🐛" to "Correzioni"
    prefisso.equals("chore", ignoreCase = true) -> "🔧" to "Manutenzione"
    else -> "🔧" to prefisso
}

/**
 * Dialog "Versioni": mostra la versione corrente e l'intera cronologia dei commit (generata a
 * build-time in Changelog.kt, vedi generaChangelog in app/build.gradle.kts), raggruppata per data
 * (solo la più recente parte espansa) e, al suo interno, per tipo di commit. Stesso stile e stessa
 * logica usati in WorkoutAnalyzer (VersionInfoDialog) e Voice2Text (VersioniDialog).
 */
@Composable
fun VersioniDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp).heightIn(max = 480.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Versione ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Chiudi")
                    }
                }
                Text(
                    "Storico delle modifiche, raggruppato per data.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                if (CHANGELOG.isEmpty()) {
                    Text("Nessuno storico disponibile.", style = MaterialTheme.typography.bodySmall)
                } else {
                    val vociOrdinate = remember { CHANGELOG.sortedByDescending { it.versionCode } }
                    val gruppiPerData = remember(vociOrdinate) { vociOrdinate.groupBy { it.data } }

                    var dateEspanse by remember(gruppiPerData) {
                        mutableStateOf(setOfNotNull(gruppiPerData.keys.firstOrNull()))
                    }

                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        gruppiPerData.forEach { (data, voci) ->
                            val espansa = data in dateEspanse
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            dateEspanse = if (espansa) dateEspanse - data else dateEspanse + data
                                        }
                                ) {
                                    Text(
                                        etichettaData(data),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp).weight(1f)
                                    )
                                    Icon(
                                        if (espansa) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = if (espansa) "Riduci" else "Espandi"
                                    )
                                }
                            }

                            if (espansa) {
                                val classificati = voci.map { classificaMessaggio(it.messaggio) }
                                val perPrefisso = classificati.groupBy({ it.first }, { it.second })

                                perPrefisso.forEach { (prefisso, messaggi) ->
                                    item {
                                        val (icona, etichetta) = iconaEEtichettaPrefisso(prefisso)
                                        Text(
                                            "$icona $etichetta",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 2.dp)
                                        )
                                    }
                                    items(messaggi) { messaggio ->
                                        Text("• $messaggio", modifier = Modifier.padding(start = 16.dp, bottom = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Chiudi") }
                }
            }
        }
    }
}

private fun etichettaData(dataIso: String): String = try {
    LocalDate.parse(dataIso).format(FORMATO_DATA_VISUALIZZATA)
} catch (_: Exception) {
    dataIso
}
