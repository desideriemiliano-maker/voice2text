package com.desideri.familybalance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.onGloballyPositioned
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
 * Campo di testo con suggerimenti filtrati dal testo digitato (usato per il tipo e per le
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

/** Colori selezionabili per le voci di spesa (ARGB). */
val PALETTE_COLORI: List<Int> = listOf(
    0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF5E35B1, 0xFF3949AB, 0xFF1E88E5,
    0xFF00ACC1, 0xFF00897B, 0xFF43A047, 0xFF7CB342, 0xFFFDD835, 0xFFFB8C00,
    0xFF6D4C41, 0xFF757575
).map { it.toInt() }

/** Pallino del colore di una voce; niente se la voce non ha colore. */
@Composable
fun PallinoColore(colore: Int?, modifier: Modifier = Modifier, dimensione: Dp = 12.dp) {
    if (colore == null) return
    Box(modifier = modifier.size(dimensione).clip(CircleShape).background(Color(colore)))
}

/** Scelta del colore di una voce tra [PALETTE_COLORI], o nessuno. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelettoreColore(colore: Int?, onColore: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Colore", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(32.dp).clip(CircleShape)
                    .border(if (colore == null) 3.dp else 1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onColore(null) }
            ) { Text("–", style = MaterialTheme.typography.labelLarge) }
            PALETTE_COLORI.forEach { c ->
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                        .border(if (colore == c) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { onColore(c) }
                )
            }
        }
    }
}

/**
 * Scorrimento automatico durante un trascinamento: finché [attivo] e il dito ([dito], coordinate
 * della finestra) è vicino al bordo alto o basso di [area], la lista scorre, più veloce quanto più
 * il dito è vicino al bordo (anche se il dito resta fermo).
 */
@Composable
fun ScorrimentoAutomatico(attivo: Boolean, dito: () -> androidx.compose.ui.geometry.Offset, area: () -> androidx.compose.ui.geometry.Rect, scorrimento: androidx.compose.foundation.ScrollState) {
    val densita = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.LaunchedEffect(attivo) {
        val fascia = with(densita) { 72.dp.toPx() }
        val massimo = with(densita) { 18.dp.toPx() }
        while (attivo) {
            val y = dito().y
            val r = area()
            val passo = when {
                y < r.top + fascia -> -massimo * ((r.top + fascia - y) / fascia).coerceIn(0.2f, 1f)
                y > r.bottom - fascia -> massimo * ((y - (r.bottom - fascia)) / fascia).coerceIn(0.2f, 1f)
                else -> 0f
            }
            if (passo != 0f) scorrimento.dispatchRawDelta(passo)
            kotlinx.coroutines.delay(16)
        }
    }
}

/** Una voce del menu ⋮ di una sezione. */
data class VoceMenuSezione(val testo: String, val icona: androidx.compose.ui.graphics.vector.ImageVector, val abilitata: Boolean = true, val onClick: () -> Unit)

/** Menu ⋮ di una sezione (es. accanto al filtro): le voci [voci] con la loro icona. */
@Composable
fun MenuSezione(voci: List<VoceMenuSezione>) {
    var aperto by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        androidx.compose.material3.IconButton(onClick = { aperto = true }) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.MoreVert, contentDescription = "Altre azioni")
        }
        androidx.compose.material3.DropdownMenu(expanded = aperto, onDismissRequest = { aperto = false }) {
            voci.forEach { v ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(v.testo) },
                    leadingIcon = { androidx.compose.material3.Icon(v.icona, contentDescription = null) },
                    enabled = v.abilitata,
                    onClick = { aperto = false; v.onClick() }
                )
            }
        }
    }
}

/**
 * Variazione percentuale di una spesa (importi negativi) rispetto al mese prima, tra parentesi:
 * rossa se si è speso di più, verde se di meno; niente se non calcolabile.
 */
@Composable
fun VariazioneSpesa(attuale: Double, precedente: Double?, modifier: Modifier = Modifier) {
    if (precedente == null || kotlin.math.abs(precedente) < 0.005 || kotlin.math.abs(attuale) < 0.005) return
    val p = Math.round((kotlin.math.abs(attuale) - kotlin.math.abs(precedente)) / kotlin.math.abs(precedente) * 100).toInt()
    val scuro = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Text(
        "(${if (p > 0) "+" else ""}$p%)",
        style = MaterialTheme.typography.labelMedium,
        color = when {
            p > 0 -> if (scuro) androidx.compose.ui.graphics.Color(0xFFFF8A80) else androidx.compose.ui.graphics.Color(0xFFC62828)
            p < 0 -> if (scuro) androidx.compose.ui.graphics.Color(0xFF81C784) else androidx.compose.ui.graphics.Color(0xFF2E7D32)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier.padding(start = 4.dp)
    )
}

/**
 * Barre di sistema lette dalla finestra dell'activity: nei Dialog a schermo intero gli inset non
 * sempre arrivano (la tabella finiva sotto i pulsanti di navigazione), quindi si usano anche questi.
 */
private val LocalBarreActivity = androidx.compose.runtime.staticCompositionLocalOf { androidx.compose.foundation.layout.PaddingValues(0.dp) }

/** Da mettere alla radice dell'activity: rende disponibili gli inset a [paddingBarreDialog]. */
@Composable
fun ConBarreDiSistema(content: @Composable () -> Unit) {
    val barre = WindowInsets.systemBars.asPaddingValues()
    androidx.compose.runtime.CompositionLocalProvider(LocalBarreActivity provides barre, content = content)
}

/**
 * Padding per il contenuto di un Dialog a schermo intero: in alto le barre del dialog; in basso il
 * maggiore tra l'inset del dialog, quello dell'activity e la parte della finestra del dialog che
 * scende sotto il bordo utile dello schermo (sopra i pulsanti di navigazione), misurata con le
 * posizioni reali delle finestre: su alcuni telefoni il dialog è spostato sotto la barra di stato
 * e la sua parte bassa finiva fuori schermo o sotto i pulsanti.
 */
@Composable
fun Modifier.paddingBarreDialog(): Modifier {
    val dialog = WindowInsets.systemBars.asPaddingValues()
    val activity = LocalBarreActivity.current
    val direzione = androidx.compose.ui.platform.LocalLayoutDirection.current
    val view = androidx.compose.ui.platform.LocalView.current
    val densita = androidx.compose.ui.platform.LocalDensity.current
    var sporgenzaPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val sporgenza = with(densita) { sporgenzaPx.toDp() }
    return this
        .onGloballyPositioned {
            val decor = trovaActivity(view.context)?.window?.decorView ?: return@onGloballyPositioned
            val radice = view.rootView
            val posDialog = IntArray(2).also { radice.getLocationOnScreen(it) }
            val posActivity = IntArray(2).also { decor.getLocationOnScreen(it) }
            val navigazione = androidx.core.view.ViewCompat.getRootWindowInsets(decor)
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
            val limite = posActivity[1] + decor.height - navigazione
            val nuova = (posDialog[1] + radice.height - limite).coerceAtLeast(0)
            if (nuova != sporgenzaPx) sporgenzaPx = nuova
        }
        .padding(
            start = dialog.calculateStartPadding(direzione),
            top = dialog.calculateTopPadding(),
            end = dialog.calculateEndPadding(direzione),
            bottom = maxOf(dialog.calculateBottomPadding(), activity.calculateBottomPadding(), sporgenza)
        )
}

private fun trovaActivity(context: android.content.Context): android.app.Activity? {
    var c: android.content.Context? = context
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}
