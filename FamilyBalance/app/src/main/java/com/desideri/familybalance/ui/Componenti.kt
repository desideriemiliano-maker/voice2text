package com.desideri.familybalance.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.desideri.familybalance.logica.formattaData
import com.desideri.familybalance.logica.formattaImporto
import com.desideri.familybalance.ui.tema.coloreImporto

private const val MILLIS_GIORNO = 86_400_000L

/**
 * Campo di testo con suggerimenti filtrati dal testo digitato (usato per tipo/sottotipo e per le
 * scelte tra liste lunghe). Il valore resta libero: la validazione è a carico del chiamante.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoAutocompletamento(
    etichetta: String,
    valore: String,
    opzioni: List<String>,
    onValore: (String) -> Unit,
    modifier: Modifier = Modifier,
    errore: String? = null,
    abilitato: Boolean = true
) {
    var espanso by remember { mutableStateOf(false) }
    val filtrate = remember(valore, opzioni) {
        val esatta = opzioni.any { it.equals(valore, ignoreCase = true) }
        if (valore.isBlank() || esatta) opzioni else opzioni.filter { it.contains(valore.trim(), ignoreCase = true) }
    }
    val mostra = espanso && filtrate.isNotEmpty() && abilitato
    ExposedDropdownMenuBox(expanded = mostra, onExpandedChange = { espanso = it }, modifier = modifier) {
        OutlinedTextField(
            value = valore,
            onValueChange = {
                onValore(it)
                espanso = true
            },
            label = { Text(etichetta) },
            singleLine = true,
            enabled = abilitato,
            isError = errore != null,
            supportingText = errore?.let { { Text(it) } },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (valore.isNotEmpty() && abilitato) {
                        IconButton(onClick = { onValore("") }) { Icon(Icons.Filled.Clear, contentDescription = "Cancella") }
                    }
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = mostra)
                }
            },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, abilitato).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = mostra, onDismissRequest = { espanso = false }) {
            filtrate.take(80).forEach { opzione ->
                DropdownMenuItem(
                    text = { Text(opzione) },
                    onClick = {
                        onValore(opzione)
                        espanso = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

/** Menu a tendina a scelta chiusa. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> CampoScelta(
    etichetta: String,
    selezionato: T?,
    opzioni: List<T>,
    testo: (T) -> String,
    onScelta: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var espanso by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = espanso, onExpandedChange = { espanso = it }, modifier = modifier) {
        OutlinedTextField(
            value = selezionato?.let(testo) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(etichetta) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = espanso) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = espanso, onDismissRequest = { espanso = false }) {
            opzioni.forEach { opzione ->
                DropdownMenuItem(text = { Text(testo(opzione)) }, onClick = {
                    onScelta(opzione)
                    espanso = false
                })
            }
        }
    }
}

/** Pulsante che mostra una data (giorni dall'epoch) e apre il calendario; [consentiVuoto] aggiunge "Nessuna". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoData(
    etichetta: String,
    epochDay: Long?,
    onData: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    consentiVuoto: Boolean = false
) {
    var aperto by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { aperto = true }, modifier = modifier) {
        Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
        Text(if (epochDay != null) "$etichetta ${formattaData(epochDay)}" else "$etichetta —", maxLines = 1)
    }
    if (aperto) {
        val stato = rememberDatePickerState(initialSelectedDateMillis = epochDay?.let { it * MILLIS_GIORNO })
        DatePickerDialog(
            onDismissRequest = { aperto = false },
            confirmButton = {
                TextButton(onClick = {
                    stato.selectedDateMillis?.let { onData(Math.floorDiv(it, MILLIS_GIORNO)) }
                    aperto = false
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    if (consentiVuoto) TextButton(onClick = {
                        onData(null)
                        aperto = false
                    }) { Text("Nessuna") }
                    TextButton(onClick = { aperto = false }) { Text("Annulla") }
                }
            }
        ) {
            DatePicker(state = stato)
        }
    }
}

/** Importo colorato per segno. */
@Composable
fun TestoImporto(valore: Double, valuta: String = "EUR", modifier: Modifier = Modifier, grassetto: Boolean = false) {
    Text(
        formattaImporto(valore, valuta),
        color = coloreImporto(valore),
        fontWeight = if (grassetto) FontWeight.Bold else null,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
    )
}

@Composable
fun DialogConferma(titolo: String, testo: String, conferma: String = "Conferma", onConferma: () -> Unit, onAnnulla: () -> Unit) {
    AlertDialog(
        onDismissRequest = onAnnulla,
        title = { Text(titolo) },
        text = { Text(testo) },
        confirmButton = {
            TextButton(onClick = {
                onConferma()
                onAnnulla()
            }) { Text(conferma) }
        },
        dismissButton = { TextButton(onClick = onAnnulla) { Text("Annulla") } }
    )
}
