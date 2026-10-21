package com.desideri.familybalance.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Functions
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
    val voci = remember { mutableStateListOf<VoceStipendio>().apply { addAll(vociIniziali) } }
    var nuovaVoce by remember { mutableStateOf("") }
    // Le voci nuove hanno id negativi provvisori, così possono essere addendi di una voce calcolata.
    var prossimoId by remember { mutableStateOf(-1L) }
    fun nuovoId(): Long = prossimoId.also { prossimoId-- }
    var formula by remember { mutableStateOf<Int?>(null) }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = v.descrizione, onValueChange = { voci[i] = v.copy(descrizione = it) },
                            singleLine = true, modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { voci.add(i - 1, voci.removeAt(i)) }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Su") }
                        IconButton(onClick = { voci.add(i + 1, voci.removeAt(i)) }, enabled = i < voci.lastIndex) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Giù") }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = nuovaVoce, onValueChange = { nuovaVoce = it }, label = { Text("Nuova voce") }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        if (nuovaVoce.isNotBlank()) voci += VoceStipendio(id = nuovoId(), tipoId = tipo.id, descrizione = nuovaVoce.trim())
                        nuovaVoce = ""
                    }) { Icon(Icons.Filled.Add, contentDescription = "Aggiungi la voce") }
                }
                if (vociIniziali.any { iniziale -> voci.none { it.id == iniziale.id } }) {
                    Text("Le voci tolte vengono eliminate con i loro importi negli stipendi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val tutte = voci.toList() + listOfNotNull(nuovaVoce.trim().takeIf { it.isNotEmpty() }?.let { VoceStipendio(id = nuovoId(), tipoId = tipo.id, descrizione = it) })
                vm.salvaTipoStipendio(tipo.copy(nome = nome, valuta = valuta, contoValutaId = conto, obsoleto = obsoleto), tutte, onChiudi)
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
    val nomi = v.idAddendi.mapNotNull { id -> voci.firstOrNull { it.id == id }?.descrizione }
    if (nomi.isEmpty()) return "nessuna voce scelta"
    val somma = nomi.joinToString(" + ")
    return v.percentuale?.let { p -> (if (nomi.size > 1) "($somma)" else somma) + " × ${p.toString().removeSuffix(".0").replace('.', ',')}%" } ?: somma
}

/** Formula di una voce: calcolata o no; se calcolata, le voci da sommare e la percentuale facoltativa. */
@Composable
private fun FormulaVoceDialog(voce: VoceStipendio, altre: List<VoceStipendio>, onSalva: (VoceStipendio) -> Unit, onAnnulla: () -> Unit) {
    var calcolata by remember { mutableStateOf(voce.calcolata) }
    val scelte = remember { mutableStateListOf<Long>().apply { addAll(voce.idAddendi) } }
    var percentuale by remember { mutableStateOf(voce.percentuale?.toString()?.removeSuffix(".0")?.replace('.', ',') ?: "") }
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
                    Text("Somma delle voci:", style = MaterialTheme.typography.labelLarge)
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
                        value = percentuale, onValueChange = { percentuale = it; errore = null },
                        label = { Text("Percentuale della somma (facoltativa)") },
                        supportingText = { Text("Vuota = la somma intera; es. 23 = il 23% della somma. Ogni stipendio memorizza la sua percentuale: cambiarla qui vale per gli stipendi nuovi.") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Le voci calcolate non si importano dalla busta paga e non entrano nel totale.", style = MaterialTheme.typography.bodySmall)
                }
                errore?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = percentuale.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.let { it.toDoubleOrNull() ?: return@TextButton run { errore = "Percentuale non valida" } }
                when {
                    !calcolata -> onSalva(voce.copy(calcolata = false, addendi = null, percentuale = null))
                    scelte.isEmpty() -> errore = "Scegli almeno una voce da sommare"
                    else -> onSalva(voce.copy(calcolata = true, addendi = altre.map { it.id }.filter { it in scelte }.joinToString(","), percentuale = p))
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
fun StipendiDialog(vm: SpeseViewModel, onChiudi: () -> Unit) {
    val stipendi by vm.stipendi.collectAsStateWithLifecycle()
    val dati by vm.dati.collectAsStateWithLifecycle()
    val importStipendio by vm.importStipendio.collectAsStateWithLifecycle()
    val tipi = stipendi.tipi.filterNot { it.obsoleto }
    var scheda by rememberSaveable { mutableStateOf(0) }
    val tipo = tipi.getOrNull(scheda.coerceAtMost(tipi.lastIndex.coerceAtLeast(0)))
    var cella by remember { mutableStateOf<Pair<Stipendio, VoceStipendio>?>(null) }
    var meseAperto by remember { mutableStateOf<Stipendio?>(null) }
    var nuovoMese by remember { mutableStateOf(false) }
    val sceltaFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && tipo != null) vm.importaBustaPaga(uri, tipo.id)
    }

    Dialog(onDismissRequest = onChiudi, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize(), tonalElevation = 4.dp) {
            Column(modifier = Modifier.fillMaxSize().paddingBarreDialog().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Stipendi", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onChiudi) { Icon(Icons.Filled.Close, contentDescription = "Chiudi") }
                }
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                        Button(onClick = { sceltaFile.launch(arrayOf("application/pdf", "image/*")) }) {
                            Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text("Importa busta paga")
                        }
                        OutlinedButton(onClick = { nuovoMese = true }) { Text("+ Mese") }
                    }
                    TabellaStipendi(
                        voci = stipendi.vociDi(tipo.id),
                        righe = stipendi.stipendiDi(tipo.id).sortedByDescending { it.mese },
                        importi = stipendi.importiPerStipendio,
                        percentuali = stipendi.percentualiPerStipendio,
                        valuta = tipo.valuta,
                        onCella = { s, v -> cella = s to v },
                        onMese = { meseAperto = it },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    cella?.takeIf { it.second.calcolata }?.let { (s, v) ->
        // Voce calcolata con percentuale: si cambia la percentuale di questo solo stipendio.
        val attuale = stipendi.percentualiPerStipendio[s.id]?.get(v.id) ?: v.percentuale ?: 100.0
        var testo by remember(s, v) { mutableStateOf(testoPercentuale(attuale)) }
        var errore by remember(s, v) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { cella = null },
            title = { Text("${v.descrizione} · ${formattaMese(YearMonth.parse(s.mese))}") },
            text = {
                Column {
                    Text("Percentuale applicata alla somma delle voci in questo stipendio; l'anagrafica e gli altri stipendi non cambiano.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = testo, onValueChange = { testo = it; errore = null }, label = { Text("Percentuale") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
                    )
                    errore?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = testo.trim().replace(',', '.').toDoubleOrNull()
                    if (p == null) errore = "Percentuale non valida" else { vm.impostaPercentualeStipendio(s.id, v.id, p); cella = null }
                }) { Text("Salva") }
            },
            dismissButton = { TextButton(onClick = { cella = null }) { Text("Annulla") } }
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
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
            dismissButton = { TextButton(onClick = { cella = null }) { Text("Annulla") } }
        )
    }
    meseAperto?.let { s ->
        var mese by remember(s) { mutableStateOf(YearMonth.parse(s.mese)) }
        var conferma by remember(s) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { meseAperto = null },
            title = { Text("Stipendio di ${formattaMese(YearMonth.parse(s.mese))}") },
            text = {
                Column {
                    SceltaMese("Mese", mese, { mese = it })
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

/** Tabella degli stipendi: mese fermo a sinistra, colonne delle voci e del totale che scorrono insieme. */
@Composable
private fun TabellaStipendi(
    voci: List<VoceStipendio>,
    righe: List<Stipendio>,
    importi: Map<Long, Map<Long, Long>>,
    percentuali: Map<Long, Map<Long, Double>>,
    valuta: String,
    onCella: (Stipendio, VoceStipendio) -> Unit,
    onMese: (Stipendio) -> Unit,
    modifier: Modifier = Modifier
) {
    if (righe.isEmpty()) {
        Text("Nessuno stipendio registrato: importa una busta paga o aggiungi un mese.", modifier = modifier.padding(16.dp))
        return
    }
    val orizzontale = rememberScrollState()
    val verticale = rememberScrollState()
    val zebra = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val coloreTotali = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    val coloreCalcolate = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
    fun sfondo(i: Int) = if (i % 2 == 1) zebra else Color.Transparent
    fun testo(cent: Long?) = cent?.let { formattaCent(it, valuta) }.orEmpty()
    Column(modifier = modifier) {
        Row {
            CellaReport("Mese", null, L_MESE_STIPENDIO, grassetto = true, alta = true)
            Row(modifier = Modifier.horizontalScroll(orizzontale)) {
                voci.forEach { CellaReport(if (it.calcolata) "ƒ ${it.descrizione}" else it.descrizione, null, L_VOCE_STIPENDIO, grassetto = true, alta = true, sfondo = if (it.calcolata) coloreCalcolate else Color.Transparent) }
                CellaReport("Totale", null, L_VOCE_STIPENDIO, grassetto = true, alta = true, sfondo = coloreTotali)
            }
        }
        Row(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.verticalScroll(verticale)) {
                righe.forEachIndexed { i, s ->
                    CellaReport(
                        formattaMeseBreve(YearMonth.parse(s.mese)), null, L_MESE_STIPENDIO, grassetto = true, alta = true, sfondo = sfondo(i),
                        modifier = Modifier.clickable { onMese(s) }
                    )
                }
            }
            Column(modifier = Modifier.verticalScroll(verticale).horizontalScroll(orizzontale)) {
                righe.forEachIndexed { i, s ->
                    // Valori registrati e calcolati; il totale somma solo le voci non calcolate.
                    val valori = valoriStipendio(voci, importi[s.id].orEmpty(), percentuali[s.id].orEmpty())
                    Row {
                        voci.forEach { v ->
                            if (v.calcolata) {
                                // Con percentuale: si tocca per cambiarla solo in questo stipendio.
                                val perc = if (v.percentuale != null) percentuali[s.id]?.get(v.id) ?: v.percentuale else null
                                CellaReport(
                                    testo(valori[v.id]) + (perc?.let { "\n" + testoPercentuale(it) + "%" } ?: ""), null, L_VOCE_STIPENDIO, alta = true, sfondo = coloreCalcolate,
                                    modifier = if (perc != null) Modifier.clickable { onCella(s, v) } else Modifier
                                )
                            } else {
                                CellaReport(testo(valori[v.id]), null, L_VOCE_STIPENDIO, alta = true, sfondo = sfondo(i), modifier = Modifier.clickable { onCella(s, v) })
                            }
                        }
                        CellaReport(testo(voci.filterNot { it.calcolata }.mapNotNull { valori[it.id] }.sum()), null, L_VOCE_STIPENDIO, grassetto = true, alta = true, sfondo = coloreTotali)
                    }
                }
            }
        }
    }
}

/** Riga modificabile della conferma dell'import: inclusa, voce di destinazione (o nuova) e importo. */
private data class RigaConferma(val inclusa: Boolean, val descrizioneLetta: String, val voceId: Long?, val importo: String, val nomeNuova: String)

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
                add(RigaConferma(l.descrizione.trim().lowercase() !in escluse, l.descrizione, voci.firstOrNull { it.riconosce(l.descrizione) }?.id, centInTesto(l.importoCent), l.descrizione))
            }
        }
    }
    var errore by remember(imp) { mutableStateOf<String?>(null) }
    /** Scelta della voce: una dell'anagrafica o null = nuova voce con la descrizione letta. */
    data class Opzione(val voceId: Long?, val testo: String)

    AlertDialog(
        onDismissRequest = { vm.annullaImportStipendio() },
        title = { Text("Importa busta paga · ${tipo.nome}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (imp.letta.mese == null) Text("Mese non riconosciuto: sceglilo.", color = MaterialTheme.colorScheme.error)
                SceltaMese("Mese dello stipendio", mese, { mese = it })
                Text(
                    "Scegli le voci da includere, la voce dell'anagrafica a cui associarle (o una nuova, con il nome che vuoi) e l'importo (${tipo.valuta}). " +
                        "Il nome letto dalla busta resta associato alla voce: ai prossimi import sarà riconosciuta.",
                    style = MaterialTheme.typography.bodySmall
                )
                righe.forEachIndexed { i, r ->
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = r.inclusa, onCheckedChange = { righe[i] = r.copy(inclusa = it) })
                        Text(r.descrizioneLetta, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                        vm.confermaImportStipendio(tipo.id, mese, convertite.filterNotNull())
                    }
                }
            }) { Text("Importa") }
        },
        dismissButton = { TextButton(onClick = { vm.annullaImportStipendio() }) { Text("Annulla") } }
    )
}
