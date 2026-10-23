package com.desideri.familybalance.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.key
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desideri.familybalance.ImportStipendio
import com.desideri.familybalance.RigaImportStipendio
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.data.Stipendio
import com.desideri.familybalance.data.TipoStipendio
import com.desideri.familybalance.data.Valute
import com.desideri.familybalance.data.VoceStipendio
import com.desideri.familybalance.data.valoriStipendio
import com.desideri.familybalance.data.calcoliStipendio
import com.desideri.familybalance.data.dipendenzeVoce
import com.desideri.familybalance.data.livelliVoci
import com.desideri.familybalance.data.numeroNelMese
import com.desideri.familybalance.logica.centInTesto
import com.desideri.familybalance.logica.formattaCent
import com.desideri.familybalance.logica.formattaMese
import com.desideri.familybalance.logica.formattaMeseBreve
import com.desideri.familybalance.logica.testoInCent
import java.time.YearMonth

// --- Anagrafica stipendi ---

/** Anagrafica stipendi: tipologie con nome, valuta, conto di accredito, obsolescenza e voci. */
@Composable
fun AnagraficaStipendiScreen(vm: SpeseViewModel, onIndietro: () -> Unit) {
    val stipendi by vm.stipendi.collectAsStateWithLifecycle()
    val dati by vm.dati.collectAsStateWithLifecycle()
    var inModifica by remember { mutableStateOf<TipoStipendio?>(null) }

    Scaffold(
        topBar = { BarraIndietro("Anagrafica stipendi", onIndietro) },
        floatingActionButton = {
            FloatingActionButton(onClick = { inModifica = TipoStipendio(nome = "", valuta = Valute.EUR) }) { Icon(Icons.Filled.Add, contentDescription = "Nuovo stipendio") }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (stipendi.tipi.isEmpty()) item { Text("Nessuno stipendio. Aggiungine uno con il pulsante +.") }
            items(stipendi.tipi, key = { it.id }) { tipo ->
                Card(modifier = Modifier.fillMaxWidth().clickable { inModifica = tipo }) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(tipo.nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            tipo.valuta + (tipo.contoValutaId?.let { " · accredito su ${dati.etichetta(it)}" } ?: "") + if (tipo.obsoleto) " · obsoleto" else "",
                            style = MaterialTheme.typography.bodySmall
                        )
                        val voci = stipendi.vociDi(tipo.id)
                        Text(
                            if (voci.isEmpty()) "Nessuna voce" else voci.joinToString(" · ") { it.descrizione },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    inModifica?.let { tipo ->
        TipoStipendioDialog(
            vm = vm,
            tipo = tipo,
            vociIniziali = stipendi.vociDi(tipo.id),
            onChiudi = { inModifica = null }
        )
    }
}

@Composable
private fun TipoStipendioDialog(vm: SpeseViewModel, tipo: TipoStipendio, vociIniziali: List<VoceStipendio>, onChiudi: () -> Unit) {
    val dati by vm.dati.collectAsStateWithLifecycle()
    var nome by remember { mutableStateOf(tipo.nome) }
    var valuta by remember { mutableStateOf(tipo.valuta) }
    var conto by remember { mutableStateOf(tipo.contoValutaId) }
    var obsoleto by remember { mutableStateOf(tipo.obsoleto) }
    var voceRiscontro by remember { mutableStateOf(tipo.voceRiscontroId) }
    val voci = remember { mutableStateListOf<VoceStipendio>().apply { addAll(vociIniziali) } }
    var nuovaVoce by remember { mutableStateOf("") }
    // Le voci nuove hanno id negativi provvisori, così possono essere addendi di una voce calcolata.
    var prossimoId by remember { mutableStateOf(-1L) }
    fun nuovoId(): Long = prossimoId.also { prossimoId-- }
    var formula by remember { mutableStateOf<Int?>(null) }
    // Riordino delle voci trascinando la maniglia: scambio con la vicina oltre metà altezza.
    var trascinata by remember { mutableStateOf<Long?>(null) }
    var spostamento by remember { mutableStateOf(0f) }
    val altezze = remember { HashMap<Long, Int>() }
    val spazioPx = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.toPx() }
    fun trascina(id: Long, dy: Float) {
        spostamento += dy
        val i = voci.indexOfFirst { it.id == id }
        val sotto = voci.getOrNull(i + 1)
        val sopra = voci.getOrNull(i - 1)
        if (sotto != null && spostamento > (altezze[sotto.id] ?: 0) / 2f) {
            voci.add(i + 1, voci.removeAt(i))
            spostamento -= (altezze[sotto.id] ?: 0) + spazioPx
        } else if (sopra != null && spostamento < -(altezze[sopra.id] ?: 0) / 2f) {
            voci.add(i - 1, voci.removeAt(i))
            spostamento += (altezze[sopra.id] ?: 0) + spazioPx
        }
    }
    var confermaElimina by remember { mutableStateOf(false) }
    val contiValuta = dati.contiValutaOrdinati.filter { it.valuta == valuta }

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(if (tipo.id == 0L) "Nuovo stipendio" else "Modifica stipendio") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = nome, onValueChange = { nome = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Valuta", style = MaterialTheme.typography.labelMedium)
                    Valute.TUTTE.forEach { v ->
                        FilterChip(selected = valuta == v, onClick = {
                            valuta = v
                            if (conto?.let { dati.contiValutaPerId[it]?.valuta } != v) conto = null
                        }, label = { Text(v) })
                    }
                }
                CampoScelta(
                    etichetta = "Conto di accredito",
                    selezionato = contiValuta.firstOrNull { it.id == conto },
                    opzioni = contiValuta,
                    testo = { dati.etichetta(it.id) },
                    onScelta = { conto = it.id },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Obsoleto", modifier = Modifier.weight(1f))
                    Switch(checked = obsoleto, onCheckedChange = { obsoleto = it })
                }
                HorizontalDivider()
                Text("Voci", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                voci.forEachIndexed { i, v ->
                    key(v.id) {
                        Column(
                            modifier = Modifier
                                .then(if (trascinata == v.id) Modifier.zIndex(1f).graphicsLayer { translationY = spostamento } else Modifier)
                                .onSizeChanged { altezze[v.id] = it.height }
                        ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Maniglia: trascinandola si sposta la voce.
                                    Icon(
                                        Icons.Filled.DragIndicator, contentDescription = "Trascina per riordinare",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 4.dp).pointerInput(v.id) {
                                            detectDragGestures(
                                                onDragStart = { trascinata = v.id; spostamento = 0f },
                                                onDrag = { change, delta -> change.consume(); trascina(v.id, delta.y) },
                                                onDragEnd = { trascinata = null; spostamento = 0f },
                                                onDragCancel = { trascinata = null; spostamento = 0f }
                                            )
                                        }
                                    )
                                    OutlinedTextField(
                                        value = v.descrizione, onValueChange = { voci[i] = v.copy(descrizione = it) },
                                        singleLine = true, modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { formula = i }) {
                                        Icon(
                                            Icons.Filled.Functions, contentDescription = "Formula",
                                            tint = if (v.calcolata) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { voci.removeAt(i) }) { Icon(Icons.Filled.Delete, contentDescription = "Togli la voce") }
                                }
                                // Nomi con cui la voce compare nelle buste paga (ricordati dagli import).
                                if (v.listaNomiImport.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                                        Text(
                                            "In busta paga: " + v.listaNomiImport.joinToString(", "),
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { voci[i] = v.copy(nomiImport = null) }) { Text("Dimentica") }
                                    }
                                }
                                if (v.calcolata) {
                                    Text(
                                        "= " + testoFormula(v, voci),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = nuovaVoce, onValueChange = { nuovaVoce = it }, label = { Text("Nuova voce") }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        if (nuovaVoce.isNotBlank()) voci += VoceStipendio(id = nuovoId(), tipoId = tipo.id, descrizione = nuovaVoce.trim())
                        nuovaVoce = ""
                    }) { Icon(Icons.Filled.Add, contentDescription = "Aggiungi la voce") }
                }
                CampoScelta(
                    etichetta = "Voce per il riscontro stipendi",
                    selezionato = voci.firstOrNull { it.id == voceRiscontro },
                    opzioni = voci.filter { it.descrizione.isNotBlank() },
                    testo = { it.descrizione },
                    onScelta = { voceRiscontro = it.id },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Confrontata con gli accrediti sul conto nel riscontro stipendi (es. il netto).", style = MaterialTheme.typography.bodySmall)
                if (vociIniziali.any { iniziale -> voci.none { it.id == iniziale.id } }) {
                    Text("Le voci tolte vengono eliminate con i loro importi negli stipendi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val tutte = voci.toList() + listOfNotNull(nuovaVoce.trim().takeIf { it.isNotEmpty() }?.let { VoceStipendio(id = nuovoId(), tipoId = tipo.id, descrizione = it) })
                vm.salvaTipoStipendio(tipo.copy(nome = nome, valuta = valuta, contoValutaId = conto, obsoleto = obsoleto, voceRiscontroId = voceRiscontro), tutte, onChiudi)
            }) { Text("Salva") }
        },
        dismissButton = {
            Row {
                if (tipo.id != 0L) TextButton(onClick = { confermaElimina = true }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onChiudi) { Text("Annulla") }
            }
        }
    )
    formula?.let { i ->
        val voce = voci.getOrNull(i)
        if (voce == null) formula = null else FormulaVoceDialog(voce, voci.filter { it.id != voce.id }, onSalva = { voci[i] = it; formula = null }, onAnnulla = { formula = null })
    }
    if (confermaElimina) {
        AlertDialog(
            onDismissRequest = { confermaElimina = false },
            title = { Text("Eliminare «${tipo.nome}»?") },
            text = { Text("Vengono eliminati anche tutti gli stipendi registrati di questo tipo.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.eliminaTipoStipendio(tipo)
                    confermaElimina = false
                    onChiudi()
                }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confermaElimina = false }) { Text("Annulla") } }
        )
    }
}

private fun testoPercentuale(p: Double): String = p.toString().removeSuffix(".0").replace('.', ',')

/** Formula di una voce calcolata in chiaro: "A + B" o "(A + B) × 23%". */
private fun testoFormula(v: VoceStipendio, voci: List<VoceStipendio>): String {
    val nomi = v.idAddendi.mapNotNull { id -> voci.firstOrNull { it.id == id }?.descrizione } +
        listOfNotNull(v.valoreFissoCent?.let { formattaCent(it) })
    if (nomi.isEmpty()) return "nessuna voce scelta"
    val somma = nomi.joinToString(" + ")
    val base = v.percentuale?.let { p -> (if (nomi.size > 1) "($somma)" else somma) + " × ${p.toString().removeSuffix(".0").replace('.', ',')}%" } ?: somma
    val formula = if (v.invertiSegno) "−(" + base + ")" else base
    return formula + (v.arrotondaDecimali?.let { d ->
        ", arrotondato a " + (if (d == 1) "1 decimale" else "$d decimali") + if (v.arrotondaCinque) " (a 0 o 5)" else ""
    } ?: "")
}

/** Formula di una voce: calcolata o no; se calcolata, le voci da sommare e la percentuale facoltativa. */
@Composable
private fun FormulaVoceDialog(voce: VoceStipendio, altre: List<VoceStipendio>, onSalva: (VoceStipendio) -> Unit, onAnnulla: () -> Unit) {
    var calcolata by remember { mutableStateOf(voce.calcolata) }
    val scelte = remember { mutableStateListOf<Long>().apply { addAll(voce.idAddendi) } }
    var percentuale by remember { mutableStateOf(voce.percentuale?.toString()?.removeSuffix(".0")?.replace('.', ',') ?: "") }
    var fisso by remember { mutableStateOf(voce.valoreFissoCent?.let { centInTesto(it) } ?: "") }
    var arrotonda by remember { mutableStateOf(voce.arrotondaDecimali != null) }
    var decimali by remember { mutableStateOf(voce.arrotondaDecimali ?: 1) }
    var aCinque by remember { mutableStateOf(voce.arrotondaCinque) }
    var invertiSegno by remember { mutableStateOf(voce.invertiSegno) }
    var errore by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onAnnulla,
        title = { Text("Formula · ${voce.descrizione}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Voce calcolata", modifier = Modifier.weight(1f))
                    Switch(checked = calcolata, onCheckedChange = { calcolata = it })
                }
                if (calcolata) {
                    Text("Somma di voci e valore fisso:", style = MaterialTheme.typography.labelLarge)
                    if (altre.isEmpty()) Text("Aggiungi prima altre voci.", style = MaterialTheme.typography.bodySmall)
                    altre.forEach { a ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                            if (a.id in scelte) scelte.remove(a.id) else scelte.add(a.id)
                        }) {
                            Checkbox(checked = a.id in scelte, onCheckedChange = { if (it) scelte.add(a.id) else scelte.remove(a.id) })
                            Text(a.descrizione + if (a.calcolata) " (calcolata)" else "")
                        }
                    }
                    OutlinedTextField(
                        value = fisso, onValueChange = { fisso = it; errore = null },
                        label = { Text("Valore fisso da sommare (facoltativo)") },
                        supportingText = { Text("Aggiunto alla somma delle voci, prima della percentuale; negativo per sottrarlo.") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), visualTransformation = SeparatoreMigliaia,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = percentuale, onValueChange = { percentuale = it; errore = null },
                        label = { Text("Percentuale della somma (facoltativa)") },
                        supportingText = { Text("Vuota = la somma intera; es. 23 = il 23% della somma. Ogni stipendio memorizza la sua percentuale: cambiarla qui vale per gli stipendi nuovi.") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cambia segno al risultato")
                            Text("Es. una trattenuta calcolata da voci positive.", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = invertiSegno, onCheckedChange = { invertiSegno = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Arrotonda il risultato", modifier = Modifier.weight(1f))
                        Switch(checked = arrotonda, onCheckedChange = { arrotonda = it })
                    }
                    if (arrotonda) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Cifre decimali", style = MaterialTheme.typography.labelMedium)
                            listOf(0, 1).forEach { d -> FilterChip(selected = decimali == d, onClick = { decimali = d }, label = { Text(d.toString()) }) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { aCinque = !aCinque }) {
                            Checkbox(checked = aCinque, onCheckedChange = { aCinque = it })
                            Text("Ultima cifra a 0 o 5")
                        }
                        Text(
                            when {
                                decimali == 1 && aCinque -> "Es. 7,62 → 7,60 e 7,63 → 7,65"
                                decimali == 1 -> "Es. 7,64 → 7,6 e 7,68 → 7,7"
                                aCinque -> "Es. 7,24 → 7,00 e 7,25 → 7,50"
                                else -> "Es. 7,49 → 7 e 7,50 → 8"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text("Le voci calcolate non si importano dalla busta paga.", style = MaterialTheme.typography.bodySmall)
                }
                errore?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = percentuale.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.let { it.toDoubleOrNull() ?: return@TextButton run { errore = "Percentuale non valida" } }
                val f = fisso.trim().takeIf { it.isNotEmpty() }?.let { testoInCent(it) ?: return@TextButton run { errore = "Valore fisso non valido" } }
                when {
                    !calcolata -> onSalva(voce.copy(calcolata = false, addendi = null, percentuale = null, arrotondaDecimali = null, arrotondaCinque = false, invertiSegno = false, valoreFissoCent = null))
                    scelte.isEmpty() && f == null -> errore = "Scegli almeno una voce da sommare o un valore fisso"
                    else -> onSalva(
                        voce.copy(
                            calcolata = true, addendi = altre.map { it.id }.filter { it in scelte }.joinToString(","), percentuale = p,
                            arrotondaDecimali = if (arrotonda) decimali else null,
                            arrotondaCinque = arrotonda && aCinque,
                            invertiSegno = invertiSegno,
                            valoreFissoCent = f
                        )
                    )
                }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onAnnulla) { Text("Annulla") } }
    )
}

// --- Stipendi registrati ---

private val L_MESE_STIPENDIO = 64.dp
private val L_VOCE_STIPENDIO = 112.dp

/**
 * Stipendi (menu ⋮): una scheda per ogni stipendio non obsoleto dell'anagrafica, con la tabella
 * degli stipendi registrati (un mese per riga, una colonna per voce). Toccando una cella se ne
 * modifica l'importo, toccando il mese lo si cambia o si elimina lo stipendio; "Importa busta paga"
 * legge una busta paga con Gemini.
 */
@Composable
fun StipendiScreen(vm: SpeseViewModel) {
    val stipendi by vm.stipendi.collectAsStateWithLifecycle()
    val dati by vm.dati.collectAsStateWithLifecycle()
    val importStipendio by vm.importStipendio.collectAsStateWithLifecycle()
    val tipi = stipendi.tipi.filterNot { it.obsoleto }
    var scheda by rememberSaveable { mutableStateOf(0) }
    val tipo = tipi.getOrNull(scheda.coerceAtMost(tipi.lastIndex.coerceAtLeast(0)))
    var cella by remember { mutableStateOf<Pair<Stipendio, VoceStipendio>?>(null) }
    var meseAperto by remember { mutableStateOf<Stipendio?>(null) }
    var dettaglio by remember { mutableStateOf<Stipendio?>(null) }
    // Cella calcolata toccata: prima il dettaglio dei suoi calcoli, da lì la modifica.
    var dettaglioCella by remember { mutableStateOf<Pair<Stipendio, VoceStipendio>?>(null) }
    var nuovoMese by remember { mutableStateOf(false) }
    val sceltaFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && tipo != null) vm.importaBustaPaga(uri, tipo.id)
    }
    val contesto = androidx.compose.ui.platform.LocalContext.current
    // Busta paga da allegare a uno stipendio già registrato.
    var daAllegare by remember { mutableStateOf<Stipendio?>(null) }
    val sceltaAllegato = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val s = daAllegare
        daAllegare = null
        if (uri != null && s != null) vm.allegaBustaPaga(s, uri)
    }

    // Anno mostrato nella tabella (predefinito quello corrente).
    var anno by rememberSaveable { mutableStateOf(YearMonth.now().year) }
    var mostraFiltroColonne by rememberSaveable { mutableStateOf(false) }
    var mostraInfo by remember { mutableStateOf(false) }
    var mostraGrafico by remember { mutableStateOf(false) }

            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                if (tipo == null) {
                    Text("Nessuno stipendio in anagrafica (menu ⋮ › Anagrafica stipendi).", modifier = Modifier.padding(16.dp))
                } else {
                    ScrollableTabRow(selectedTabIndex = tipi.indexOf(tipo), edgePadding = 0.dp) {
                        tipi.forEachIndexed { i, t -> Tab(selected = t.id == tipo.id, onClick = { scheda = i }, text = { Text(t.nome) }) }
                    }
                    Text(
                        tipo.valuta + (tipo.contoValutaId?.let { " · accredito su ${dati.etichetta(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)
                    )
                    // Colonne mostrate: le nascoste si ricordano solo se scelto (anche le voci nuove sono mostrate).
                    var nascoste by remember(tipo.id) { mutableStateOf(vm.colonneNascosteStipendio(tipo.id) ?: emptySet()) }
                    var ricordaColonne by remember(tipo.id) { mutableStateOf(vm.colonneNascosteStipendio(tipo.id) != null) }
                    // Ordine delle colonne scelto per questo stipendio (le voci nuove in coda), ricordato e nel backup.
                    var ordine by remember(tipo.id) { mutableStateOf(vm.ordineColonne("stipendi_${tipo.id}")) }
                    var sceltaOrdine by remember(tipo.id) { mutableStateOf(false) }
                    val vociTipo = ordinaColonne(stipendi.vociDi(tipo.id), ordine) { it.id.toString() }
                    // Colonne rientrate: si vedono solo espandendo la colonna non rientrata che le precede (la «madre»).
                    var rientrate by remember(tipo.id) { mutableStateOf(vm.colonneRientrateStipendio(tipo.id)) }
                    var espanse by remember(tipo.id) { mutableStateOf(vm.colonneEspanseStipendio(tipo.id)) }
                    var sceltaRientri by remember(tipo.id) { mutableStateOf(false) }
                    val madri = HashMap<Long, Long>()
                    run {
                        var madre: Long? = null
                        vociTipo.forEach { v -> val m = madre; if (v.id in rientrate && m != null) madri[v.id] = m else madre = v.id }
                    }
                    val conFigli = madri.values.toSet()
                    // Anno e azioni sulla stessa riga; la scelta delle colonne compare con l'icona del filtro.
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        IconButton(onClick = { anno-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Anno precedente") }
                        Text(anno.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { anno++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Anno successivo") }
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        IconButton(onClick = { sceltaFile.launch(arrayOf("application/pdf", "image/*")) }) {
                            Icon(Icons.Filled.UploadFile, contentDescription = "Importa busta paga", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { nuovoMese = true }) { Icon(Icons.Filled.Add, contentDescription = "Aggiungi un mese") }
                        IconButton(onClick = { mostraGrafico = true }) { Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Grafico") }
                        IconButton(onClick = { mostraFiltroColonne = !mostraFiltroColonne }) {
                            Icon(
                                Icons.Filled.FilterList, contentDescription = "Colonne mostrate",
                                tint = if (mostraFiltroColonne || nascoste.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { mostraInfo = true }) { Icon(Icons.Filled.Info, contentDescription = "Informazioni") }
                    }
                    if (mostraFiltroColonne)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                        MenuMultiplo(
                            "Colonne", vociTipo, vociTipo.filter { it.id !in nascoste }, { it.descrizione },
                            onCambia = { scelte ->
                                nascoste = vociTipo.map { it.id }.toSet() - scelte.map { it.id }.toSet()
                                if (ricordaColonne) vm.salvaColonneNascosteStipendio(tipo.id, nascoste)
                            },
                            modifier = Modifier.weight(1f),
                            conTutti = true
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                            ricordaColonne = !ricordaColonne
                            vm.salvaColonneNascosteStipendio(tipo.id, if (ricordaColonne) nascoste else null)
                        }) {
                            Checkbox(checked = ricordaColonne, onCheckedChange = {
                                ricordaColonne = it
                                vm.salvaColonneNascosteStipendio(tipo.id, if (it) nascoste else null)
                            })
                            Text("Ricorda", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { sceltaOrdine = true }) { Icon(Icons.Filled.ViewColumn, contentDescription = "Ordine delle colonne") }
                        IconButton(onClick = { sceltaRientri = true }) { Icon(Icons.AutoMirrored.Filled.FormatIndentIncrease, contentDescription = "Colonne rientrate") }
                    }
                    if (sceltaOrdine) {
                        OrdineColonneDialog(
                            vociTipo.map { it.descrizione },
                            onConferma = { indici ->
                                ordine = indici.map { vociTipo[it].id.toString() }
                                vm.salvaOrdineColonne("stipendi_${tipo.id}", ordine)
                                sceltaOrdine = false
                            },
                            onAnnulla = { sceltaOrdine = false }
                        )
                    }
                    if (sceltaRientri) {
                        val scelte = remember { mutableStateListOf<Long>().apply { addAll(rientrate) } }
                        AlertDialog(
                            onDismissRequest = { sceltaRientri = false },
                            title = { Text("Colonne rientrate") },
                            text = {
                                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                    Text(
                                        "Una colonna rientrata si vede solo espandendo, con l'icona nell'intestazione, la colonna non rientrata che la precede.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    vociTipo.forEachIndexed { i, v ->
                                        val attiva = i > 0
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth().clickable(enabled = attiva) { if (v.id in scelte) scelte.remove(v.id) else scelte.add(v.id) }
                                                .padding(start = if (v.id in scelte && attiva) 24.dp else 0.dp)
                                        ) {
                                            Checkbox(checked = v.id in scelte && attiva, enabled = attiva, onCheckedChange = { if (it) scelte.add(v.id) else scelte.remove(v.id) })
                                            Text((if (v.id in scelte && attiva) "↳ " else "") + v.descrizione)
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    rientrate = scelte.toSet()
                                    vm.salvaColonneRientrateStipendio(tipo.id, rientrate)
                                    sceltaRientri = false
                                }) { Text("OK") }
                            },
                            dismissButton = { TextButton(onClick = { sceltaRientri = false }) { Text("Annulla") } }
                        )
                    }
                    TabellaStipendi(
                        voci = vociTipo.filter { v -> v.id !in nascoste && (madri[v.id]?.let { m -> m in espanse && m !in nascoste } ?: true) },
                        rientrate = madri.keys,
                        conFigli = conFigli,
                        espanse = espanse,
                        onEspandi = { id ->
                            espanse = if (id in espanse) espanse - id else espanse + id
                            vm.salvaColonneEspanseStipendio(tipo.id, espanse)
                        },
                        righe = stipendi.stipendiDi(tipo.id).filter { it.mese.startsWith("$anno-") }
                            .sortedWith(compareByDescending<Stipendio> { it.mese }.thenBy { it.id }),
                        importi = stipendi.importiPerStipendio,
                        percentuali = stipendi.percentualiPerStipendio,
                        sostituzioni = stipendi.sostituzioniPerStipendio,
                        tutteLeVoci = vociTipo,
                        manuali = stipendi.manualiPerStipendio,
                        valuta = tipo.valuta,
                        onCella = { s, v -> if (v.calcolata) dettaglioCella = s to v else cella = s to v },
                        onMese = { meseAperto = it },
                        onDettaglio = { dettaglio = it },
                        // Intestazione trascinata: la voce va al posto di quella su cui è rilasciata (ordine completo, anche con colonne nascoste).
                        onSposta = { daId, aId ->
                            val ids = vociTipo.map { it.id }.toMutableList()
                            val arrivo = ids.indexOf(aId)
                            ids.remove(daId)
                            ids.add(arrivo.coerceIn(0, ids.size), daId)
                            ordine = ids.map { it.toString() }
                            vm.salvaOrdineColonne("stipendi_${tipo.id}", ordine)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (mostraGrafico) {
                        val righeTipo = stipendi.stipendiDi(tipo.id)
                        GraficoStipendiDialog(
                            titolo = "Stipendi · ${tipo.nome}",
                            voci = vociTipo,
                            stipendi = righeTipo,
                            valoriPerStipendio = righeTipo.associate { s ->
                                s.id to valoriStipendio(
                                    vociTipo, stipendi.importiPerStipendio[s.id].orEmpty(),
                                    stipendi.percentualiPerStipendio[s.id].orEmpty(), stipendi.sostituzioniPerStipendio[s.id].orEmpty()
                                )
                            },
                            valuta = tipo.valuta,
                            annoIniziale = anno,
                            pausaRadarMs = vm.impostazioni.collectAsStateWithLifecycle().value.pausaRadarMs,
                            onChiudi = { mostraGrafico = false }
                        )
                    }
                }
            }

    if (mostraInfo) {
        AlertDialog(
            onDismissRequest = { mostraInfo = false },
            icon = { Icon(Icons.Filled.Info, contentDescription = null) },
            title = { Text("Stipendi") },
            text = {
                Text(
                    "Celle in giallo: valori modificati a mano. Tieni premuta un'intestazione e trascinala per spostare la colonna. " +
                        "📄 accanto al mese: busta paga salvata su Drive (si apre dal popup del mese). Tocca una cella per modificarla, il mese per cambiarlo o eliminarlo, un totale per il suo dettaglio; tieni premuto il mese per il dettaglio dei calcoli (una cella calcolata mostra i suoi, con «Modifica» per cambiarli). Icone: importa busta paga, aggiungi un mese, grafico (andamento o radar animato), " +
                        "colonne mostrate (con ordine e colonne rientrate, che si aprono con la freccia nell'intestazione della colonna precedente)."
                )
            },
            confirmButton = { TextButton(onClick = { mostraInfo = false }) { Text("OK") } }
        )
    }
    cella?.takeIf { it.second.calcolata }?.let { (s, v) ->
        // Voce calcolata: si modificano i valori di partenza in questo stipendio (le voci sommate non
        // calcolate e la percentuale), con l'anteprima del risultato.
        val vociTipo = stipendi.vociDi(s.tipoId)
        val importiMese = stipendi.importiPerStipendio[s.id].orEmpty()
        val percentualiMese = stipendi.percentualiPerStipendio[s.id].orEmpty()
        val sostituzioniMese = stipendi.sostituzioniPerStipendio[s.id].orEmpty()
        val addendi = v.idAddendi.mapNotNull { id -> vociTipo.firstOrNull { it.id == id } }
        // Valori delle voci calcolate senza le sostituzioni di questa voce (il loro valore "proprio").
        val valoriPropri = valoriStipendio(vociTipo, importiMese, percentualiMese, sostituzioniMese - v.id)
        // Voci sommate calcolate: valore usato solo da questa voce (sostitutivo o il proprio).
        val testiCalcolate = remember(s, v) {
            androidx.compose.runtime.mutableStateMapOf<Long, String>().apply {
                addendi.filter { it.calcolata }.forEach { a ->
                    put(a.id, (sostituzioniMese[v.id]?.get(a.id) ?: valoriPropri[a.id])?.let { centInTesto(it) } ?: "")
                }
            }
        }
        /** Sostituzioni scritte: solo dove il valore è diverso da quello proprio della voce sommata. */
        fun sostituzioniScritte(): Map<Long, Long> = testiCalcolate.mapNotNull { (id, t) ->
            testoInCent(t)?.takeIf { it != valoriPropri[id] }?.let { id to it }
        }.toMap()
        val testi = remember(s, v) {
            androidx.compose.runtime.mutableStateMapOf<Long, String>().apply {
                addendi.filterNot { it.calcolata }.forEach { a -> put(a.id, importiMese[a.id]?.let { centInTesto(it) } ?: "") }
            }
        }
        var testoPerc by remember(s, v) { mutableStateOf(testoPercentuale(percentualiMese[v.id] ?: v.percentuale ?: 100.0)) }
        var errore by remember(s, v) { mutableStateOf<String?>(null) }
        // Anteprima con i valori scritti (quelli non validi restano esclusi).
        val anteprimaImporti = importiMese.toMutableMap().apply {
            testi.forEach { (id, t) -> val c = testoInCent(t); if (t.isBlank()) remove(id) else if (c != null) put(id, c) }
        }
        val anteprimaPerc = percentualiMese.toMutableMap().apply {
            if (v.percentuale != null) testoPerc.trim().replace(',', '.').toDoubleOrNull()?.let { put(v.id, it) }
        }
        val anteprima = valoriStipendio(vociTipo, anteprimaImporti, anteprimaPerc, (sostituzioniMese - v.id) + (v.id to sostituzioniScritte()))
        AlertDialog(
            onDismissRequest = { cella = null },
            title = { Text("${v.descrizione} · ${formattaMese(YearMonth.parse(s.mese))}") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("= " + testoFormula(v, vociTipo), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text("Valori di questo stipendio; l'anagrafica e gli altri stipendi non cambiano.", style = MaterialTheme.typography.bodySmall)
                    addendi.forEach { a ->
                        if (a.calcolata) {
                            // Valore usato solo da questa voce: la colonna «${a.descrizione}» non cambia.
                            OutlinedTextField(
                                value = testiCalcolate[a.id].orEmpty(), onValueChange = { testiCalcolate[a.id] = it; errore = null },
                                label = { Text("${a.descrizione} (calcolata, solo per questa voce)") }, singleLine = true,
                                supportingText = { valoriPropri[a.id]?.let { p -> Text("Valore proprio: ${formattaCent(p, tipo?.valuta ?: "EUR")}") } },
                                // Valore diverso da quello proprio: si torna all'originale.
                                trailingIcon = {
                                    val proprio = valoriPropri[a.id]
                                    if (testoInCent(testiCalcolate[a.id].orEmpty()) != proprio) {
                                        IconButton(onClick = { testiCalcolate[a.id] = proprio?.let { centInTesto(it) } ?: "" }) {
                                            Icon(Icons.Filled.Restore, contentDescription = "Torna al valore proprio")
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), visualTransformation = SeparatoreMigliaia, modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            OutlinedTextField(
                                value = testi[a.id].orEmpty(), onValueChange = { testi[a.id] = it; errore = null },
                                label = { Text(a.descrizione) }, singleLine = true,
                                // Modificato a mano: si torna al valore importato.
                                trailingIcon = {
                                    val importato = stipendi.importatiPerStipendio[s.id]?.get(a.id)
                                    if (importato != null && testoInCent(testi[a.id].orEmpty()) != importato) {
                                        IconButton(onClick = { testi[a.id] = centInTesto(importato) }) {
                                            Icon(Icons.Filled.Restore, contentDescription = "Torna al valore importato")
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), visualTransformation = SeparatoreMigliaia, modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    if (v.percentuale != null) {
                        OutlinedTextField(
                            value = testoPerc, onValueChange = { testoPerc = it; errore = null }, label = { Text("Percentuale") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                        )
                    }
                    HorizontalDivider()
                    Row {
                        Text("Risultato", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(anteprima[v.id]?.let { formattaCent(it, tipo?.valuta ?: "EUR") } ?: "—", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    errore?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val importi = testi.mapValues { (_, t) -> if (t.isBlank()) null else testoInCent(t) ?: Long.MIN_VALUE }
                    val perc = if (v.percentuale != null) testoPerc.trim().replace(',', '.').toDoubleOrNull() else null
                    when {
                        importi.values.any { it == Long.MIN_VALUE } -> errore = "Ci sono importi non validi"
                        testiCalcolate.values.any { it.isNotBlank() && testoInCent(it) == null } -> errore = "Ci sono importi non validi"
                        v.percentuale != null && perc == null -> errore = "Percentuale non valida"
                        else -> {
                            val sost = sostituzioniScritte()
                            vm.salvaInputCalcolata(
                                s.id, importi.filter { (id, c) -> c != importiMese[id] },
                                perc?.takeIf { it != (percentualiMese[v.id] ?: v.percentuale) }?.let { v.id to it },
                                if (sost != sostituzioniMese[v.id].orEmpty()) v.id to sost else null
                            )
                            cella = null
                        }
                    }
                }) { Text("Salva") }
            },
            dismissButton = {
                Row {
                    // Voci sommate cambiate a mano o percentuale cambiata: si torna ai valori importati e all'anagrafica.
                    val daRipristinare = addendi.filter { stipendi.importatiPerStipendio[s.id]?.containsKey(it.id) == true }.map { it.id }
                    val percentualeManuale = v.id in stipendi.manualiPerStipendio[s.id].orEmpty()
                    if (daRipristinare.isNotEmpty() || percentualeManuale) {
                        TextButton(onClick = {
                            vm.ripristinaImportati(s.id, daRipristinare, if (percentualeManuale) listOf(v) else emptyList())
                            cella = null
                        }) { Text("Ripristina importati") }
                    }
                    TextButton(onClick = { cella = null }) { Text("Annulla") }
                }
            }
        )
    }
    cella?.takeIf { !it.second.calcolata }?.let { (s, v) ->
        var testo by remember(s, v) { mutableStateOf(stipendi.importiPerStipendio[s.id]?.get(v.id)?.let { centInTesto(it) } ?: "") }
        var errore by remember(s, v) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { cella = null },
            title = { Text("${v.descrizione} · ${formattaMese(YearMonth.parse(s.mese))}") },
            text = {
                Column {
                    OutlinedTextField(
                        value = testo, onValueChange = { testo = it; errore = null },
                        label = { Text("Importo (${tipo?.valuta ?: ""}, negativo per le trattenute)") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), visualTransformation = SeparatoreMigliaia,
                        modifier = Modifier.fillMaxWidth()
                    )
                    errore?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (testo.isBlank()) {
                        vm.impostaImportoStipendio(s.id, v.id, null); cella = null
                    } else {
                        val cent = testoInCent(testo)
                        if (cent == null) errore = "Importo non valido" else { vm.impostaImportoStipendio(s.id, v.id, cent); cella = null }
                    }
                }) { Text("Salva") }
            },
            dismissButton = {
                Row {
                    // Valore cambiato a mano: si può tornare a quello importato.
                    stipendi.importatiPerStipendio[s.id]?.get(v.id)?.let { importato ->
                        TextButton(onClick = { vm.ripristinaImportati(s.id, listOf(v.id)); cella = null }) {
                            Text("Ripristina ${formattaCent(importato, tipo?.valuta ?: "EUR")}")
                        }
                    }
                    TextButton(onClick = { cella = null }) { Text("Annulla") }
                }
            }
        )
    }
    meseAperto?.let { s ->
        var mese by remember(s) { mutableStateOf(YearMonth.parse(s.mese)) }
        var conferma by remember(s) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { meseAperto = null },
            title = { Text("Stipendio di ${formattaMese(YearMonth.parse(s.mese))}" + (numeroNelMese(s, stipendi.stipendiDi(s.tipoId))?.let { " ($it)" } ?: "")) },
            text = {
                Column {
                    SceltaMese("Mese", mese, { mese = it })
                    OutlinedButton(onClick = { dettaglio = s; meseAperto = null }, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(Icons.Filled.Functions, contentDescription = null)
                        Text("Dettaglio dei calcoli", modifier = Modifier.padding(start = 6.dp))
                    }
                    // Busta paga importata salvata su Drive: si apre con il link.
                    s.bustaPagaLink?.let { link ->
                        OutlinedButton(onClick = {
                            vm.apriFileDrive(link, "busta.pdf")
                        }) {
                            Icon(Icons.Filled.UploadFile, contentDescription = null)
                            Text("Apri la busta paga", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                    OutlinedButton(onClick = {
                        daAllegare = s
                        meseAperto = null
                        sceltaAllegato.launch(arrayOf("application/pdf", "image/*"))
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(if (s.bustaPagaLink == null) "Allega la busta paga" else "Sostituisci la busta paga", modifier = Modifier.padding(start = 6.dp))
                    }
                    if (conferma) Text("Tocca di nuovo «Elimina» per eliminare lo stipendio con tutti i suoi importi.", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(onClick = { vm.cambiaMeseStipendio(s, mese); meseAperto = null }) { Text("Salva") } },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        if (conferma) { vm.eliminaStipendio(s); meseAperto = null } else conferma = true
                    }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { meseAperto = null }) { Text("Annulla") }
                }
            }
        )
    }
    dettaglio?.let { s ->
        stipendi.tipi.firstOrNull { it.id == s.tipoId }?.let { t ->
            DettaglioCalcoliDialog(
                s, ordinaColonne(stipendi.vociDi(t.id), vm.ordineColonne("stipendi_${t.id}")) { it.id.toString() },
                stipendi.importiPerStipendio[s.id].orEmpty(), stipendi.percentualiPerStipendio[s.id].orEmpty(),
                stipendi.sostituzioniPerStipendio[s.id].orEmpty(), stipendi.manualiPerStipendio[s.id].orEmpty(), t.valuta,
                onChiudi = { dettaglio = null },
                numero = numeroNelMese(s, stipendi.stipendiDi(t.id))
            )
        }
    }
    dettaglioCella?.let { (s, v) ->
        stipendi.tipi.firstOrNull { it.id == s.tipoId }?.let { t ->
            DettaglioCalcoliDialog(
                s, ordinaColonne(stipendi.vociDi(t.id), vm.ordineColonne("stipendi_${t.id}")) { it.id.toString() },
                stipendi.importiPerStipendio[s.id].orEmpty(), stipendi.percentualiPerStipendio[s.id].orEmpty(),
                stipendi.sostituzioniPerStipendio[s.id].orEmpty(), stipendi.manualiPerStipendio[s.id].orEmpty(), t.valuta,
                onChiudi = { dettaglioCella = null },
                voce = v,
                onModifica = { cella = s to v; dettaglioCella = null },
                numero = numeroNelMese(s, stipendi.stipendiDi(t.id))
            )
        }
    }
    if (nuovoMese && tipo != null) {
        var mese by remember { mutableStateOf(YearMonth.now()) }
        AlertDialog(
            onDismissRequest = { nuovoMese = false },
            title = { Text("Nuovo stipendio · ${tipo.nome}") },
            text = { SceltaMese("Mese", mese, { mese = it }) },
            confirmButton = { TextButton(onClick = { vm.aggiungiStipendio(tipo.id, mese); nuovoMese = false }) { Text("Aggiungi") } },
            dismissButton = { TextButton(onClick = { nuovoMese = false }) { Text("Annulla") } }
        )
    }
    importStipendio?.let { imp ->
        stipendi.tipi.firstOrNull { it.id == imp.tipoId }?.let { t ->
            ConfermaImportStipendioDialog(vm, t, stipendi.vociDi(t.id), imp)
        }
    }
}

/**
 * Dettaglio dei calcoli di uno stipendio, dall'alto in basso: prima le voci registrate, poi le
 * calcolate a partire da quelle con meno dipendenze, con somma, percentuale, arrotondamento e segno.
 */
@Composable
private fun DettaglioCalcoliDialog(
    s: Stipendio,
    voci: List<VoceStipendio>,
    importi: Map<Long, Long>,
    percentuali: Map<Long, Double>,
    sostituzioni: Map<Long, Map<Long, Long>>,
    manuali: Set<Long>,
    valuta: String,
    onChiudi: () -> Unit,
    /** Solo questa voce calcolata e quelle da cui dipende (null = tutto lo stipendio). */
    voce: VoceStipendio? = null,
    onModifica: (() -> Unit)? = null,
    /** Numero dello stipendio tra quelli dello stesso mese (null se è l'unico). */
    numero: Int? = null
) {
    val incluse = voce?.let { dipendenzeVoce(voci, it.id) }
    // Le voci vuote (senza importo o senza valore calcolato) non si mostrano.
    val calcoli = calcoliStipendio(voci, importi, percentuali, sostituzioni)
        .filter { (incluse == null || it.voce.id in incluse) && it.passi != null }
        .map { c -> c.copy(addendi = c.addendi.filter { it.valore != null }) }
    val registrate = voci.filter { !it.calcolata && importi[it.id] != null && (incluse == null || it.id in incluse) }
    fun testo(cent: Long?) = cent?.let { formattaCent(it, valuta) } ?: "—"
    @Composable
    fun Riga(etichetta: String, valore: String, grassetto: Boolean = false, nota: String? = null) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(etichetta, style = MaterialTheme.typography.bodyMedium, fontWeight = if (grassetto) FontWeight.Bold else null)
                nota?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(valore, style = MaterialTheme.typography.bodyMedium, fontWeight = if (grassetto) FontWeight.Bold else null, modifier = Modifier.padding(start = 8.dp))
        }
    }
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text((voce?.let { "${it.descrizione} · " } ?: "Calcoli · ") + formattaMese(YearMonth.parse(s.mese)) + (numero?.let { " ($it)" } ?: "")) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Voci registrate", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                if (registrate.isEmpty()) Text("Nessun importo registrato.", style = MaterialTheme.typography.bodySmall)
                registrate.forEach { v -> Riga(v.descrizione, testo(importi[v.id]), nota = if (v.id in manuali) "modificato a mano" else null) }
                calcoli.forEach { c ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Text("ƒ ${c.voce.descrizione}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    c.addendi.forEachIndexed { i, a ->
                        Riga(
                            (if (i == 0) "" else "+ ") + a.voce.descrizione, testo(a.valore),
                            nota = if (a.sostituito) "valore modificato solo per questa voce" else null
                        )
                    }
                    c.voce.valoreFissoCent?.let { f -> Riga((if (c.addendi.isEmpty()) "" else "+ ") + "Valore fisso", testo(f)) }
                    val p = c.passi
                    if (c.somma == null || p == null) {
                        Riga("= Nessun valore", "—", grassetto = true)
                    } else {
                        val ultimo = when {
                            c.voce.invertiSegno -> 3
                            c.voce.arrotondaDecimali != null -> 2
                            c.percentuale != null -> 1
                            else -> 0
                        }
                        Riga("= Somma", testo(c.somma), grassetto = ultimo == 0)
                        c.percentuale?.let { Riga("× ${testoPercentuale(it)}%", testo(p.conPercentuale), grassetto = ultimo == 1) }
                        c.voce.arrotondaDecimali?.let { d ->
                            Riga(
                                "Arrotondato a " + (if (d == 1) "1 decimale" else "$d decimali") + if (c.voce.arrotondaCinque) " (0 o 5)" else "",
                                testo(p.arrotondato), grassetto = ultimo == 2
                            )
                        }
                        if (c.voce.invertiSegno) Riga("Cambio di segno", testo(p.risultato), grassetto = true)
                    }
                    if (c.voce.id in manuali) Text("Valori di partenza modificati a mano.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onChiudi) { Text("Chiudi") } },
        dismissButton = { onModifica?.let { m -> TextButton(onClick = m) { Text("Modifica") } } }
    )
}

/**
 * Dettaglio della riga dei totali: con [voce] i valori mese per mese e la loro somma; senza, il
 * totale di ogni voce, prima le registrate e poi le calcolate da quelle con meno dipendenze.
 */
@Composable
private fun DettaglioTotaliDialog(
    voce: VoceStipendio?,
    voci: List<VoceStipendio>,
    righe: List<Stipendio>,
    valoriPerStipendio: Map<Long, Map<Long, Long>>,
    valuta: String,
    onChiudi: () -> Unit
) {
    fun testo(cent: Long?) = cent?.let { formattaCent(it, valuta) } ?: "—"
    fun totale(v: VoceStipendio) = righe.mapNotNull { valoriPerStipendio[it.id]?.get(v.id) }.takeIf { it.isNotEmpty() }?.sum()
    @Composable
    fun Riga(etichetta: String, valore: String, grassetto: Boolean = false, nota: String? = null) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(etichetta, style = MaterialTheme.typography.bodyMedium, fontWeight = if (grassetto) FontWeight.Bold else null)
                nota?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(valore, style = MaterialTheme.typography.bodyMedium, fontWeight = if (grassetto) FontWeight.Bold else null, modifier = Modifier.padding(start = 8.dp))
        }
    }
    val mesi = righe.sortedWith(compareBy<Stipendio> { it.mese }.thenBy { it.id })
    fun nomeMese(s: Stipendio) = formattaMese(YearMonth.parse(s.mese)) + (numeroNelMese(s, righe)?.let { " ($it)" } ?: "")
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text((voce?.let { "${it.descrizione} · " } ?: "Totali · ") + mesi.firstOrNull()?.mese?.take(4).orEmpty()) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (voce != null) {
                    if (voce.calcolata) Text("ƒ " + testoFormula(voce, voci), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val conValore = mesi.filter { valoriPerStipendio[it.id]?.get(voce.id) != null }
                    if (conValore.isEmpty()) Text("Nessun valore nell'anno.", style = MaterialTheme.typography.bodySmall)
                    conValore.forEachIndexed { i, s ->
                        Riga((if (i == 0) "" else "+ ") + nomeMese(s), testo(valoriPerStipendio[s.id]?.get(voce.id)))
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Riga("= Totale", testo(totale(voce)), grassetto = true)
                } else {
                    val livelli = livelliVoci(voci)
                    // Le voci senza nessun valore nell'anno non si mostrano.
                    val ordinate = voci.filter { totale(it) != null }.sortedBy { livelli[it.id] ?: 0 }
                    Text("Voci registrate", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    ordinate.filter { !it.calcolata }.forEach { v -> Riga(v.descrizione, testo(totale(v))) }
                    val calcolate = ordinate.filter { it.calcolata }
                    if (calcolate.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        Text("Voci calcolate", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Totale dei valori calcolati mese per mese (percentuali e arrotondamenti si applicano a ogni stipendio).",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        calcolate.forEach { v -> Riga("ƒ " + v.descrizione, testo(totale(v)), grassetto = true, nota = testoFormula(v, voci)) }
                    }
                    Text("Tocca il totale di una colonna per il dettaglio mese per mese.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onChiudi) { Text("Chiudi") } }
    )
}

/** Tabella degli stipendi: mese fermo a sinistra, colonne delle voci che scorrono insieme. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabellaStipendi(
    voci: List<VoceStipendio>,
    rientrate: Set<Long>,
    conFigli: Set<Long>,
    espanse: Set<Long>,
    onEspandi: (Long) -> Unit,
    righe: List<Stipendio>,
    importi: Map<Long, Map<Long, Long>>,
    percentuali: Map<Long, Map<Long, Double>>,
    sostituzioni: Map<Long, Map<Long, Map<Long, Long>>>,
    tutteLeVoci: List<VoceStipendio>,
    manuali: Map<Long, Set<Long>>,
    valuta: String,
    onCella: (Stipendio, VoceStipendio) -> Unit,
    onMese: (Stipendio) -> Unit,
    onDettaglio: (Stipendio) -> Unit,
    onSposta: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (righe.isEmpty()) {
        Text("Nessuno stipendio registrato nell'anno: importa una busta paga o aggiungi un mese.", modifier = modifier.padding(16.dp))
        return
    }
    val orizzontale = rememberScrollState()
    val verticale = rememberScrollState()
    val zebra = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val coloreCalcolate = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
    // Celle modificate a mano: giallo (più tenue con il tema scuro).
    val coloreManuale = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0x66B59B00) else Color(0xFFFFF3B0)
    fun sfondo(i: Int) = if (i % 2 == 1) zebra else Color.Transparent
    fun testo(cent: Long?) = cent?.let { formattaCent(it, valuta) }.orEmpty()
    // Valori di ogni stipendio (registrati e calcolati, con tutte le voci anche nascoste).
    val valoriPerStipendio = righe.associate { s ->
        s.id to valoriStipendio(tutteLeVoci, importi[s.id].orEmpty(), percentuali[s.id].orEmpty(), sostituzioni[s.id].orEmpty())
    }
    val coloreTotali = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    // Dettaglio dei totali: di tutte le voci (voceTotale null) o mese per mese di una voce.
    var dettaglioTotale by remember { mutableStateOf(false) }
    var voceTotale by remember { mutableStateOf<VoceStipendio?>(null) }
    if (dettaglioTotale) {
        DettaglioTotaliDialog(voceTotale, tutteLeVoci, righe, valoriPerStipendio, valuta, onChiudi = { dettaglioTotale = false })
    }
    Column(modifier = modifier) {
        Row {
            CellaReport("Mese", null, L_MESE_STIPENDIO, grassetto = true, alta = true)
            Row(modifier = Modifier.horizontalScroll(orizzontale)) {
                voci.forEachIndexed { c, v ->
                    key(v.id) {
                        IntestazioneTrascinabile(
                            (if (v.id in rientrate) "↳ " else "") + (if (v.calcolata) "ƒ ${v.descrizione}" else v.descrizione), c, voci.size, L_VOCE_STIPENDIO,
                            sfondo = if (v.calcolata) coloreCalcolate else Color.Transparent,
                            onSposta = { da, a -> onSposta(voci[da].id, voci[a].id) },
                            espansa = if (v.id in conFigli) v.id in espanse else null,
                            onEspandi = { onEspandi(v.id) }
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.verticalScroll(verticale)) {
                righe.forEachIndexed { i, s ->
                    CellaReport(
                        formattaMeseBreve(YearMonth.parse(s.mese)) + (numeroNelMese(s, righe)?.let { " ($it)" } ?: "") + (if (s.bustaPagaLink != null) " 📄" else ""),
                        null, L_MESE_STIPENDIO, grassetto = true, alta = true, sfondo = sfondo(i),
                        modifier = Modifier.combinedClickable(onClick = { onMese(s) }, onLongClick = { onDettaglio(s) })
                    )
                }
            }
            Column(modifier = Modifier.verticalScroll(verticale).horizontalScroll(orizzontale)) {
                righe.forEachIndexed { i, s ->
                    val valori = valoriPerStipendio[s.id].orEmpty()
                    Row {
                        voci.forEach { v ->
                            if (v.calcolata) {
                                // Con percentuale: si tocca per cambiarla solo in questo stipendio.
                                val perc = if (v.percentuale != null) percentuali[s.id]?.get(v.id) ?: v.percentuale else null
                                CellaReport(
                                    testo(valori[v.id]) + (perc?.let { "\n" + testoPercentuale(it) + "%" } ?: ""), null, L_VOCE_STIPENDIO, alta = true,
                                    sfondo = if (v.id in manuali[s.id].orEmpty()) coloreManuale else coloreCalcolate,
                                    modifier = Modifier.clickable { onCella(s, v) }
                                )
                            } else {
                                CellaReport(
                                    testo(valori[v.id]), null, L_VOCE_STIPENDIO, alta = true,
                                    sfondo = if (v.id in manuali[s.id].orEmpty()) coloreManuale else sfondo(i),
                                    modifier = Modifier.clickable { onCella(s, v) }
                                )
                            }
                        }
                    }
                }
            }
        }
        // Totali dell'anno mostrato per ogni colonna, fermi in fondo.
        Row {
            CellaReport(
                "Totale", null, L_MESE_STIPENDIO, grassetto = true, alta = true, sfondo = coloreTotali,
                modifier = Modifier.clickable { voceTotale = null; dettaglioTotale = true }
            )
            Row(modifier = Modifier.horizontalScroll(orizzontale)) {
                voci.forEach { v ->
                    val valori = righe.mapNotNull { valoriPerStipendio[it.id]?.get(v.id) }
                    CellaReport(
                        if (valori.isEmpty()) "" else testo(valori.sum()), null, L_VOCE_STIPENDIO, grassetto = true, alta = true, sfondo = coloreTotali,
                        modifier = Modifier.clickable { voceTotale = v; dettaglioTotale = true }
                    )
                }
            }
        }
    }
}

/** Riga modificabile della conferma dell'import: inclusa, voce di destinazione (o nuova) e importo. */
/** [nuova]: voce mai vista negli import precedenti (né associata a una voce né esclusa). */
private data class RigaConferma(val inclusa: Boolean, val descrizioneLetta: String, val voceId: Long?, val importo: String, val nomeNuova: String, val nuova: Boolean = false)

/**
 * Conferma dell'import di una busta paga: mese (modificabile) e, per ogni voce letta, se includerla,
 * a quale voce dell'anagrafica associarla (o crearne una nuova con la descrizione letta) e l'importo.
 */
@Composable
private fun ConfermaImportStipendioDialog(vm: SpeseViewModel, tipo: TipoStipendio, voci: List<VoceStipendio>, imp: ImportStipendio) {
    var mese by remember(imp) { mutableStateOf(imp.letta.mese ?: YearMonth.now().minusMonths(1)) }
    // Voci escluse all'ultimo import (se si era scelto di ricordare la selezione).
    val escluse = remember(imp) { vm.vociEscluseStipendio(tipo.id) }
    var ricorda by remember(imp) { mutableStateOf(vm.ricordaSelezioneStipendio()) }
    val righe = remember(imp) {
        mutableStateListOf<RigaConferma>().apply {
            imp.letta.voci.forEach { l ->
                // Voce riconosciuta per descrizione o per un nome con cui è stata importata in passato.
                val riconosciuta = voci.firstOrNull { it.riconosce(l.descrizione) }
                val esclusa = l.descrizione.trim().lowercase() in escluse
                add(
                    RigaConferma(
                        !esclusa, l.descrizione, riconosciuta?.id, centInTesto(l.importoCent), l.descrizione,
                        nuova = riconosciuta == null && !esclusa
                    )
                )
            }
        }
    }
    var errore by remember(imp) { mutableStateOf<String?>(null) }
    // Stipendi già registrati nel mese: se ne aggiorna uno (predefinito l'ultimo) o se ne aggiunge un altro.
    val tuttiStipendi by vm.stipendi.collectAsStateWithLifecycle()
    val delMese = tuttiStipendi.stipendiDi(tipo.id).filter { it.mese == mese.toString() }.sortedBy { it.id }
    var destinazione by remember(imp, mese) { mutableStateOf(delMese.lastOrNull()?.id) }
    /** Scelta della voce: una dell'anagrafica o null = nuova voce con la descrizione letta. */
    data class Opzione(val voceId: Long?, val testo: String)

    AlertDialog(
        onDismissRequest = { vm.annullaImportStipendio() },
        title = { Text("Importa busta paga · ${tipo.nome}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (imp.letta.mese == null) Text("Mese non riconosciuto: sceglilo.", color = MaterialTheme.colorScheme.error)
                SceltaMese("Mese dello stipendio", mese, { mese = it })
                if (delMese.isNotEmpty()) {
                    Text("C'è già uno stipendio in questo mese:", style = MaterialTheme.typography.labelLarge)
                    delMese.forEachIndexed { i, s ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { destinazione = s.id }) {
                            RadioButton(selected = destinazione == s.id, onClick = { destinazione = s.id })
                            Text(
                                "Aggiorna lo stipendio" + (if (delMese.size > 1) " n. ${i + 1}" else "") +
                                    " (${tuttiStipendi.importiPerStipendio[s.id].orEmpty().size} voci)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { destinazione = null }) {
                        RadioButton(selected = destinazione == null, onClick = { destinazione = null })
                        Text("Aggiungi come altro stipendio dello stesso mese", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    "Scegli le voci da includere, la voce dell'anagrafica a cui associarle (o una nuova, con il nome che vuoi) e l'importo (${tipo.valuta}). " +
                        "Il nome letto dalla busta resta associato alla voce: ai prossimi import sarà riconosciuta. " +
                        "Le voci mai viste negli import precedenti sono evidenziate come NUOVA.",
                    style = MaterialTheme.typography.bodySmall
                )
                righe.forEachIndexed { i, r ->
                    HorizontalDivider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = if (r.nuova) Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.tertiaryContainer) else Modifier.fillMaxWidth()
                    ) {
                        Checkbox(checked = r.inclusa, onCheckedChange = { righe[i] = r.copy(inclusa = it) })
                        Text(r.descrizioneLetta, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        // Voce mai vista negli import precedenti.
                        if (r.nuova) Text("NUOVA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(end = 8.dp))
                    }
                    if (r.inclusa) {
                        val opzioni = voci.filterNot { it.calcolata }.map { Opzione(it.id, it.descrizione) } + Opzione(null, "Nuova voce")
                        CampoScelta(
                            etichetta = "Voce",
                            selezionato = opzioni.firstOrNull { it.voceId == r.voceId },
                            opzioni = opzioni,
                            testo = { it.testo },
                            onScelta = { righe[i] = r.copy(voceId = it.voceId) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (r.voceId == null) {
                            // Nome della nuova voce: può essere diverso da quello della busta, che resta ricordato.
                            OutlinedTextField(
                                value = r.nomeNuova, onValueChange = { righe[i] = r.copy(nomeNuova = it); errore = null },
                                label = { Text("Nome della nuova voce") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        OutlinedTextField(
                            value = r.importo, onValueChange = { righe[i] = r.copy(importo = it); errore = null },
                            label = { Text("Importo") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), visualTransformation = SeparatoreMigliaia,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                val stessaVoce = righe.filter { it.inclusa }.groupBy { it.voceId ?: it.nomeNuova.trim().lowercase() }.any { it.value.size > 1 }
                if (stessaVoce) Text("Più righe sulla stessa voce: i loro importi verranno sommati.", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { ricorda = !ricorda }) {
                    Checkbox(checked = ricorda, onCheckedChange = { ricorda = it })
                    Text("Ricorda la selezione per il prossimo import (le voci escluse saranno proposte già deselezionate)", style = MaterialTheme.typography.bodySmall)
                }
                errore?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val scelte = righe.filter { it.inclusa }
                val convertite = scelte.map { r ->
                    testoInCent(r.importo)?.let { RigaImportStipendio(r.voceId, r.nomeNuova.trim().takeIf { _ -> r.voceId == null }, it, r.descrizioneLetta) }
                }
                when {
                    scelte.isEmpty() -> errore = "Nessuna voce inclusa"
                    scelte.any { it.voceId == null && it.nomeNuova.isBlank() } -> errore = "Dai un nome alle nuove voci"
                    convertite.any { it == null } -> errore = "Ci sono importi non validi"
                    else -> {
                        vm.ricordaSelezioneStipendio(tipo.id, ricorda, scelte.map { it.descrizioneLetta }, righe.filterNot { it.inclusa }.map { it.descrizioneLetta })
                        vm.confermaImportStipendio(tipo.id, mese, convertite.filterNotNull(), destinazione)
                    }
                }
            }) { Text("Importa") }
        },
        dismissButton = { TextButton(onClick = { vm.annullaImportStipendio() }) { Text("Annulla") } }
    )
}

/**
 * Intestazione di colonna che si sposta tenendola premuta e trascinandola: al rilascio [onSposta]
 * riceve la posizione di partenza e quella di arrivo (colonne di [larghezza] fissa).
 */
@Composable
private fun IntestazioneTrascinabile(
    testo: String,
    indice: Int,
    numeroColonne: Int,
    larghezza: androidx.compose.ui.unit.Dp,
    sfondo: Color,
    onSposta: (Int, Int) -> Unit,
    /** Con colonne rientrate dopo di sé: se sono mostrate (null = nessuna rientrata). */
    espansa: Boolean? = null,
    onEspandi: () -> Unit = {}
) {
    var spostamento by remember { mutableStateOf(0f) }
    var trascinata by remember { mutableStateOf(false) }
    val sposta by androidx.compose.runtime.rememberUpdatedState(onSposta)
    val indiceAttuale by androidx.compose.runtime.rememberUpdatedState(indice)
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .zIndex(if (trascinata) 1f else 0f)
            .graphicsLayer { translationX = spostamento }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { trascinata = true },
                    onDrag = { change, delta -> change.consume(); spostamento += delta.x },
                    onDragEnd = {
                        val passi = kotlin.math.round(spostamento / larghezza.toPx()).toInt()
                        val arrivo = (indiceAttuale + passi).coerceIn(0, numeroColonne - 1)
                        trascinata = false
                        spostamento = 0f
                        if (arrivo != indiceAttuale) sposta(indiceAttuale, arrivo)
                    },
                    onDragCancel = { trascinata = false; spostamento = 0f }
                )
            }
    ) {
        CellaReport(
            testo, null, larghezza, grassetto = true, alta = true,
            sfondo = if (trascinata) MaterialTheme.colorScheme.primaryContainer else sfondo
        )
        // Icona per mostrare o nascondere le colonne rientrate che seguono.
        espansa?.let { aperta ->
            Icon(
                if (aperta) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                contentDescription = if (aperta) "Nascondi le colonne rientrate" else "Mostra le colonne rientrate",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopStart).size(22.dp).clickable(onClick = onEspandi)
            )
        }
    }
}
