package com.desideri.familybalance.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import com.desideri.familybalance.DatiApp
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desideri.familybalance.SpeseViewModel

enum class Sezione(val titolo: String, val icona: ImageVector) {
    CONTI("Conti", Icons.Filled.AccountBalance),
    RICORRENTI("Ricorrenti", Icons.Filled.EventRepeat),
    BILANCIO("Bilancio", Icons.AutoMirrored.Filled.ShowChart)
}

/** Schermate aperte sopra le sezioni principali (menu ⋮ o dettaglio di un conto). */
sealed interface Schermata {
    data class Operazioni(val contoValutaId: Long) : Schermata
    data object AnagraficaSpese : Schermata
    data object AnagraficaConti : Schermata
    data object AnagraficaAssociazioni : Schermata
    data object Impostazioni : Schermata
    data object Backup : Schermata
    data object MappaSpostamenti : Schermata
    data object Cambi : Schermata
}

@Composable
fun SpeseApp(vm: SpeseViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messaggi.collect { snackbar.showSnackbar(it) } }

    var sezione by rememberSaveable { mutableStateOf(Sezione.CONTI) }
    var pila by remember { mutableStateOf(listOf<Schermata>()) }
    fun apri(s: Schermata) {
        pila = pila + s
    }
    fun chiudi() {
        pila = pila.dropLast(1)
    }
    BackHandler(enabled = pila.isNotEmpty()) { chiudi() }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val corrente = pila.lastOrNull()) {
            null -> SchermataPrincipale(vm, sezione, onSezione = { sezione = it }, onApri = ::apri)
            is Schermata.Operazioni -> OperazioniScreen(vm, corrente.contoValutaId, onIndietro = ::chiudi)
            Schermata.AnagraficaSpese -> AnagraficaSpeseScreen(vm, onIndietro = ::chiudi)
            Schermata.AnagraficaConti -> AnagraficaContiScreen(vm, onIndietro = ::chiudi)
            Schermata.AnagraficaAssociazioni -> AnagraficaAssociazioniScreen(vm, onIndietro = ::chiudi)
            Schermata.Impostazioni -> ImpostazioniScreen(vm, onIndietro = ::chiudi)
            Schermata.Backup -> BackupScreen(vm, onIndietro = ::chiudi)
            Schermata.MappaSpostamenti -> MappaSpostamentiScreen(vm, onIndietro = ::chiudi)
            Schermata.Cambi -> CambiScreen(vm, onIndietro = ::chiudi)
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchermataPrincipale(vm: SpeseViewModel, sezione: Sezione, onSezione: (Sezione) -> Unit, onApri: (Schermata) -> Unit) {
    var menuAperto by remember { mutableStateOf(false) }
    var mostraVersioni by remember { mutableStateOf(false) }
    var mostraRegistro by remember { mutableStateOf(false) }
    var confermaImport by remember { mutableStateOf(false) }
    val importazioneInCorso by vm.importazioneInCorso.collectAsStateWithLifecycle()
    val analisiImport by vm.analisiImport.collectAsStateWithLifecycle()
    val importEstratto by vm.importEstratto.collectAsStateWithLifecycle()
    val testoAttesa by vm.testoAttesa.collectAsStateWithLifecycle()
    val dati by vm.dati.collectAsStateWithLifecycle()
    // File dell'estratto conto scelto, in attesa della scelta del conto su cui importarlo.
    var fileEstratto by remember { mutableStateOf<Uri?>(null) }
    val sceltaEstratto = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) fileEstratto = uri
    }
    // File dell'estratto conto da riscontrare, in attesa della scelta del conto.
    var fileRiscontro by remember { mutableStateOf<Uri?>(null) }
    val sceltaRiscontro = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) fileRiscontro = uri
    }
    val riscontroEstratto by vm.riscontroEstratto.collectAsStateWithLifecycle()

    val sceltaExcel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importaExcel(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(sezione.titolo) },
                actions = {
                    Box {
                        IconButton(onClick = { menuAperto = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Menu") }
                        DropdownMenu(expanded = menuAperto, onDismissRequest = { menuAperto = false }) {
                            VoceMenu("Anagrafica spese", Icons.AutoMirrored.Filled.List) { menuAperto = false; onApri(Schermata.AnagraficaSpese) }
                            VoceMenu("Anagrafica conti", Icons.Filled.AccountBalanceWallet) { menuAperto = false; onApri(Schermata.AnagraficaConti) }
                            VoceMenu("Anagrafica associazioni", Icons.Filled.Link) { menuAperto = false; onApri(Schermata.AnagraficaAssociazioni) }
                            HorizontalDivider()
                            VoceMenu("Importa estratto conto", Icons.AutoMirrored.Filled.ReceiptLong) {
                                menuAperto = false
                                sceltaEstratto.launch(arrayOf("*/*"))
                            }
                            VoceMenu("Riscontro estratto conto", Icons.Filled.FactCheck) {
                                menuAperto = false
                                sceltaRiscontro.launch(arrayOf("*/*"))
                            }
                            VoceMenu("Importa da Excel", Icons.Filled.FileOpen) { menuAperto = false; confermaImport = true }
                            HorizontalDivider()
                            VoceMenu("Riscontro spostamenti", Icons.AutoMirrored.Filled.CompareArrows) { menuAperto = false; onApri(Schermata.MappaSpostamenti) }
                            VoceMenu("Cambi CHF/EUR", Icons.Filled.CurrencyExchange) { menuAperto = false; onApri(Schermata.Cambi) }
                            HorizontalDivider()
                            VoceMenu("Backup Google Drive", Icons.Filled.CloudUpload) { menuAperto = false; onApri(Schermata.Backup) }
                            HorizontalDivider()
                            VoceMenu("Impostazioni", Icons.Filled.Settings) { menuAperto = false; onApri(Schermata.Impostazioni) }
                            VoceMenu("Registro Gemini", Icons.Filled.History) { menuAperto = false; mostraRegistro = true }
                            VoceMenu("Versioni", Icons.Filled.Info) { menuAperto = false; mostraVersioni = true }
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Sezione.entries.forEach { s ->
                    NavigationBarItem(
                        selected = s == sezione,
                        onClick = { onSezione(s) },
                        icon = { Icon(s.icona, contentDescription = null) },
                        label = { Text(s.titolo) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (sezione) {
                Sezione.CONTI -> ContiScreen(vm, onApriConto = { onApri(Schermata.Operazioni(it)) }, onAnagraficaConti = { onApri(Schermata.AnagraficaConti) })
                Sezione.RICORRENTI -> RicorrentiScreen(vm)
                Sezione.BILANCIO -> BilancioScreen(vm)
            }
        }
    }

    if (mostraVersioni) VersioniDialog(onDismiss = { mostraVersioni = false })
    if (mostraRegistro) RegistroDialog(onDismiss = { mostraRegistro = false })

    if (confermaImport) {
        DialogConferma(
            titolo = "Importa da Excel",
            testo = "Scegli il file Excel delle spese (fogli HelloBank, LGT, Bollette, Impostazioni). " +
                "Le spese ricorrenti vengono dal foglio Bollette; prima di importare potrai scegliere a quale " +
                "ricorrente associare le operazioni di tipo Bollette. Tutti i conti, le voci e le operazioni " +
                "presenti nell'app verranno SOSTITUITI dal contenuto del file.",
            conferma = "Scegli file",
            onConferma = {
                sceltaExcel.launch(
                    arrayOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.ms-excel",
                        "application/octet-stream"
                    )
                )
            },
            onAnnulla = { confermaImport = false }
        )
    }

    analisiImport?.let { analisi ->
        val memorizzate = remember(analisi) { vm.mappatureBollette() }
        AssociaBolletteDialog(
            analisi = analisi,
            memorizzate = memorizzate,
            onConferma = { vm.confermaImport(it) },
            onSalva = { vm.salvaScelteImport(it) },
            onAnnulla = { vm.annullaImport() }
        )
    }

    fileEstratto?.let { uri ->
        SceltaContoDialog(
            dati = dati,
            onScelto = { contoValutaId ->
                fileEstratto = null
                vm.importaEstratto(uri, contoValutaId)
            },
            onAnnulla = { fileEstratto = null }
        )
    }

    fileRiscontro?.let { uri ->
        SceltaContoDialog(
            dati = dati,
            onScelto = { contoValutaId ->
                fileRiscontro = null
                vm.riscontraEstratto(uri, contoValutaId)
            },
            onAnnulla = { fileRiscontro = null }
        )
    }

    riscontroEstratto?.let { RiscontroEstrattoDialog(vm, dati, it, onChiudi = { vm.chiudiRiscontroEstratto() }) }

    importEstratto?.let { ImportEstrattoDialog(vm, dati, it) }

    if (importazioneInCorso) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Importazione in corso") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 16.dp))
                    Text(testoAttesa)
                }
            }
        )
    }
}

/**
 * Scelta del conto corrente E della valuta su cui importare l'estratto conto (es. "LGT CHF"): tutti
 * i movimenti del file vanno lì, senza affidarsi alla valuta letta da Gemini.
 */
@Composable
private fun SceltaContoDialog(dati: DatiApp, onScelto: (Long) -> Unit, onAnnulla: () -> Unit) {
    val contiValuta = dati.contiValutaOrdinati
    var scelto by remember { mutableStateOf(contiValuta.singleOrNull()?.id) }
    AlertDialog(
        onDismissRequest = onAnnulla,
        title = { Text("Importa estratto conto nel conto") },
        text = {
            Column {
                if (contiValuta.isEmpty()) Text("Nessun conto configurato: crealo in Anagrafica conti.")
                contiValuta.forEach { cv ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { scelto = cv.id }
                    ) {
                        RadioButton(selected = scelto == cv.id, onClick = { scelto = cv.id })
                        Text(dati.etichetta(cv.id))
                    }
                }
                Text(
                    "Scegli anche la valuta: tutti i movimenti del file verranno registrati su quel conto/valuta. " +
                        "Il file viene inviato a Gemini per leggerne i movimenti.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = { scelto?.let(onScelto) }, enabled = scelto != null) { Text("Importa") } },
        dismissButton = { TextButton(onClick = onAnnulla) { Text("Annulla") } }
    )
}

@Composable
private fun VoceMenu(testo: String, icona: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(testo) }, leadingIcon = { Icon(icona, contentDescription = null) }, onClick = onClick)
}

/** Barra superiore delle schermate secondarie, con freccia indietro. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarraIndietro(titolo: String, onIndietro: () -> Unit, azioni: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(titolo, maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = onIndietro) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro") }
        },
        actions = { azioni() }
    )
}
