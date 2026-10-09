package com.desideri.familybalance.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.desideri.familybalance.DatiApp
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.StatoRiscontroEstratto
import com.desideri.familybalance.data.Operazione
import com.desideri.familybalance.estratto.TipoData
import com.desideri.familybalance.logica.Collegamento
import com.desideri.familybalance.logica.MovimentoRiscontro
import com.desideri.familybalance.logica.RiscontroEstratto
import com.desideri.familybalance.logica.formattaCent
import com.desideri.familybalance.logica.formattaData
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val FORMATO_GIORNO = DateTimeFormatter.ofPattern("dd/MM")
private val FORMATO_ANNO = DateTimeFormatter.ofPattern("yyyy")

/** Cosa mostrare nel riscontro. */
private enum class FiltroRiscontro(val etichetta: String) {
    TUTTE("Tutte"),
    ASSOCIATE("Associate"),
    NON_ASSOCIATE("Non associate"),
    CONTINUE("Continue"),
    TRATTEGGIATE("Tratteggiate")
}

/** Chiavi delle carte per le posizioni: operazione del conto o movimento dell'estratto. */
/** Mesi mostrati per volta e giorni di sovrapposizione selezionabili tra un periodo e l'altro. */
private const val MESI_PERIODO = 2L
private val SOVRAPPOSIZIONI = listOf(0, 5, 10)

/** Periodo mostrato: due mesi a partire da [inizio], o tutto l'estratto con [inizio] null. */
private data class PeriodoRiscontro(val inizio: YearMonth?) {
    val etichetta: String
        get() = inizio?.let { i ->
            val fine = i.plusMonths(MESI_PERIODO - 1)
            val nome = { m: YearMonth -> m.format(DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.ITALIAN)) }
            if (i.year == fine.year) "${i.format(DateTimeFormatter.ofPattern("MMM", java.util.Locale.ITALIAN))}–${nome(fine)}" else "${nome(i)}–${nome(fine)}"
        } ?: "Tutto l'estratto"
}

private fun chiaveOp(id: Long) = "o$id"
private fun chiaveMov(indice: Int) = "m$indice"

/** Distanza del punto [p] dal segmento [a]-[b]. */
private fun distanza(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val l2 = ab.x * ab.x + ab.y * ab.y
    if (l2 == 0f) return (p - a).getDistance()
    val t = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / l2).coerceIn(0f, 1f)
    return (p - Offset(a.x + ab.x * t, a.y + ab.y * t)).getDistance()
}

/**
 * Riscontro tra le operazioni di un conto/valuta e i movimenti di un estratto conto letto con
 * Gemini: tre colonne (data, operazione del conto, movimento dell'estratto) e una linea tra le
 * coppie con lo stesso importo e date entro [RiscontroEstratto.GIORNI] giorni (continua se la data
 * coincide, tratteggiata se è vicina; di un altro colore quelle create a mano). Toccando una linea
 * la si elimina; tenendo premuta una riga non collegata e trascinandola su una dell'altra colonna
 * se ne crea una nuova. "Aggiorna date" porta le date delle operazioni a quelle dell'estratto per
 * le coppie tratteggiate; "Crea operazioni" registra i movimenti mancanti sul conto.
 */
@Composable
fun RiscontroEstrattoDialog(vm: SpeseViewModel, dati: DatiApp, stato: StatoRiscontroEstratto, onChiudi: () -> Unit) {
    val valuta = dati.contiValutaPerId[stato.contoValutaId]?.valuta ?: "EUR"
    val tipiDisponibili = TipoData.entries.filter { t -> stato.movimenti.any { it.dataDi(t) != null } }.ifEmpty { TipoData.entries }
    var tipoData by remember {
        mutableStateOf(vm.tipoDataEstratto(stato.contoId).takeIf { it in tipiDisponibili } ?: tipiDisponibili.first())
    }
    val movimenti = remember(stato, tipoData) {
        stato.movimenti.map { MovimentoRiscontro(it.importoCent, it.data(tipoData).toEpochDay()) }
    }
    val opsConto = remember(dati.operazioni, stato) { dati.operazioni.filter { it.contoValutaId == stato.contoValutaId } }
    val perIdOp = remember(opsConto) { opsConto.associateBy { it.id } }

    // Collegamenti creati a mano e automatici tolti dall'utente; gli altri si calcolano.
    val manuali = remember(stato) { mutableStateListOf<Collegamento>() }
    val rimossi = remember(stato) { mutableStateListOf<Pair<Long, Int>>() }
    val automatici = remember(opsConto, movimenti, manuali.toList(), rimossi.toList()) {
        RiscontroEstratto.abbina(
            opsConto, movimenti,
            esclusiOperazioni = manuali.map { it.operazioneId }.toSet(),
            esclusiMovimenti = manuali.map { it.movimento }.toSet()
        ).filter { (it.operazioneId to it.movimento) !in rimossi }
    }
    val collegamenti = manuali.filter { it.operazioneId in perIdOp } + automatici
    val opCollegate = collegamenti.associateBy { it.operazioneId }
    val movCollegati = collegamenti.associateBy { it.movimento }

    // Periodo dell'estratto: operazioni del conto in quel periodo (più quelle collegate appena fuori).
    val da = movimenti.minOf { it.data }
    val a = movimenti.maxOf { it.data }
    var filtro by remember { mutableStateOf(FiltroRiscontro.TUTTE) }
    fun mostrato(c: Collegamento?): Boolean {
        if (filtro == FiltroRiscontro.TUTTE) return true
        if (filtro == FiltroRiscontro.NON_ASSOCIATE) return c == null
        if (c == null) return false
        val stessaData = perIdOp[c.operazioneId]?.data == movimenti[c.movimento].data
        return when (filtro) {
            FiltroRiscontro.CONTINUE -> stessaData
            FiltroRiscontro.TRATTEGGIATE -> !stessaData
            else -> true
        }
    }
    // Periodi di due mesi dall'ultimo mese dell'estratto all'indietro (il più recente per primo). Il
    // modello (collegamenti, mancanti, date da aggiornare) resta sull'intero estratto: il periodo
    // limita solo le righe mostrate, con qualche giorno di sovrapposizione ai bordi.
    val periodi = remember(da, a) {
        val primo = YearMonth.from(LocalDate.ofEpochDay(da))
        generateSequence(YearMonth.from(LocalDate.ofEpochDay(a)).minusMonths(MESI_PERIODO - 1)) { it.minusMonths(MESI_PERIODO) }
            .takeWhile { !it.plusMonths(MESI_PERIODO - 1).isBefore(primo) }
            .map { PeriodoRiscontro(it) }
            .toList() + PeriodoRiscontro(null)
    }
    var periodo by remember(stato) { mutableStateOf(periodi.first()) }
    var sovrapposizione by remember { mutableIntStateOf(5) }
    val finestra: LongRange = periodo.inizio?.let { i ->
        (i.atDay(1).toEpochDay() - sovrapposizione)..(i.plusMonths(MESI_PERIODO - 1).atEndOfMonth().toEpochDay() + sovrapposizione)
    } ?: (da..a)
    // Una riga è nel periodo se la sua data lo è o se è collegata a una riga del periodo (le linee restano intere).
    fun opNelPeriodo(op: Operazione) = op.data in finestra || opCollegate[op.id]?.let { movimenti[it.movimento].data in finestra } == true
    fun movNelPeriodo(i: Int) = movimenti[i].data in finestra || movCollegati[i]?.let { perIdOp[it.operazioneId]?.data?.let { d -> d in finestra } } == true
    val opsVisibili = opsConto.filter { (it.data in da..a || it.id in opCollegate) && opNelPeriodo(it) && mostrato(opCollegate[it.id]) }
    val movVisibili = movimenti.indices.filter { movNelPeriodo(it) && mostrato(movCollegati[it]) }
    val date = (opsVisibili.map { it.data } + movVisibili.map { movimenti[it].data }).distinct().sortedDescending()
    val opsPerData = opsVisibili.groupBy { it.data }
    val movPerData = movVisibili.groupBy { movimenti[it].data }
    // Solo le carte mostrate contano per linee e trascinamento (le posizioni delle nascoste restano in memoria).
    val chiaviVisibili = opsVisibili.map { chiaveOp(it.id) }.toSet() + movVisibili.map { chiaveMov(it) }

    val daAggiornare = collegamenti.mapNotNull { c ->
        val op = perIdOp[c.operazioneId] ?: return@mapNotNull null
        // Un movimento ancora non contabilizzato non ha una data vera: non sana l'operazione.
        if (stato.movimenti[c.movimento].nonContabilizzato) return@mapNotNull null
        val d = movimenti[c.movimento].data
        if (op.data != d) op.id to d else null
    }.toMap()
    val mancanti = movimenti.indices.filter { it !in movCollegati }
    val senzaRiscontro = opsConto.count { it.data in da..a && it.id !in opCollegate }

    val posizioni = remember { mutableStateMapOf<String, Rect>() }
    var origine by remember { mutableStateOf(Offset.Zero) }
    var area by remember { mutableStateOf(Rect.Zero) }
    val scorrimento = rememberScrollState()
    var trascinata by remember { mutableStateOf<String?>(null) }
    var dito by remember { mutableStateOf(Offset.Zero) }
    /** Importo (in centesimi) della riga con chiave [k]: operazione del conto o movimento dell'estratto. */
    fun importoDi(k: String): Long? =
        if (k.startsWith("o")) perIdOp[k.drop(1).toLong()]?.importoCent else movimenti.getOrNull(k.drop(1).toInt())?.importoCent
    // Si può collegare solo a una riga dell'altra colonna con lo stesso importo.
    val bersaglio = trascinata?.let { t ->
        val altraColonna = if (t.startsWith("o")) "m" else "o"
        val importo = importoDi(t)
        posizioni.entries.firstOrNull { (k, r) -> k.startsWith(altraColonna) && k in chiaviVisibili && r.contains(dito) && importoDi(k) == importo }?.key
    }
    var inModifica by remember { mutableStateOf<Operazione?>(null) }
    var confermaDate by remember { mutableStateOf(false) }
    var confermaAssocia by remember { mutableStateOf(false) }
    var confermaCrea by remember { mutableStateOf(false) }
    var esitoImporto by remember { mutableStateOf<RiscontroEstratto.EsitoPerImporto?>(null) }

    val colAuto = MaterialTheme.colorScheme.primary
    val colManuale = MaterialTheme.colorScheme.tertiary
    val colTrascina = MaterialTheme.colorScheme.secondary

    /** Segmenti delle linee (coordinate del contenuto) con il loro collegamento. */
    fun linee(): List<Pair<Collegamento, Pair<Offset, Offset>>> = collegamenti.mapNotNull { c ->
        if (chiaveOp(c.operazioneId) !in chiaviVisibili || chiaveMov(c.movimento) !in chiaviVisibili) return@mapNotNull null
        val r1 = posizioni[chiaveOp(c.operazioneId)]?.translate(-origine) ?: return@mapNotNull null
        val r2 = posizioni[chiaveMov(c.movimento)]?.translate(-origine) ?: return@mapNotNull null
        c to (r1.centerRight to r2.centerLeft)
    }

    fun collega(k1: String, k2: String) {
        val op = (if (k1.startsWith("o")) k1 else k2).drop(1).toLong()
        val mov = (if (k1.startsWith("m")) k1 else k2).drop(1).toInt()
        if (op in opCollegate || mov in movCollegati) return
        if (perIdOp[op]?.importoCent != movimenti.getOrNull(mov)?.importoCent) return
        manuali += Collegamento(op, mov, manuale = true)
    }

    // Esito dell'estrazione: icona info se tutto a posto, avviso se ci sono problemi o dati incompleti.
    val problemi = stato.avvisi + listOfNotNull(
        stato.movimenti.count { it.descrizione.isBlank() }.takeIf { it > 0 }?.let { "$it movimenti senza descrizione" }
    )
    var mostraInfo by remember { mutableStateOf(false) }

    ScorrimentoAutomatico(trascinata != null, { dito }, { area }, scorrimento)
    LaunchedEffect(periodo, sovrapposizione, filtro) { scorrimento.scrollTo(0) }

    // decorFitsSystemWindows = false + paddingBarreDialog: il popup resta dentro lo schermo visibile.
    Dialog(onDismissRequest = onChiudi, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize(), tonalElevation = 4.dp) {
            Column(modifier = Modifier.fillMaxSize().paddingBarreDialog().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Riscontro estratto · ${dati.etichetta(stato.contoValutaId)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    // Esito della lettura e istruzioni: si leggono toccando l'icona.
                    IconButton(onClick = { mostraInfo = true }) {
                        Icon(
                            if (problemi.isEmpty()) Icons.Filled.Info else Icons.Filled.Warning,
                            contentDescription = if (problemi.isEmpty()) "Informazioni" else "Avvisi",
                            tint = if (problemi.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = onChiudi) { Icon(Icons.Filled.Close, contentDescription = "Chiudi") }
                }
                // Periodo mostrato (due mesi) con frecce e scelta diretta; sovrapposizione ai bordi.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    val indice = periodi.indexOf(periodo)
                    IconButton(onClick = { periodo = periodi[indice + 1] }, enabled = indice + 1 < periodi.size - 1) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Periodo precedente")
                    }
                    CampoScelta("Periodo", periodo, periodi, { it.etichetta }, { periodo = it }, Modifier.weight(1f))
                    IconButton(onClick = { periodo = periodi[indice - 1] }, enabled = indice in 1 until periodi.size - 1) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Periodo successivo")
                    }
                }
                if (periodo.inizio != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Sovrapposizione", style = MaterialTheme.typography.labelMedium)
                        SOVRAPPOSIZIONI.forEach { g ->
                            FilterChip(selected = sovrapposizione == g, onClick = { sovrapposizione = g }, label = { Text(if (g == 0) "Nessuna" else "$g gg") })
                        }
                    }
                }
                // Le tre azioni sulla stessa riga, ciascuna con un popup di conferma che la spiega.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    OutlinedButton(
                        onClick = { confermaDate = true }, enabled = daAggiornare.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.weight(1f)
                    ) { Text("Aggiorna (${daAggiornare.size})", maxLines = 1) }
                    OutlinedButton(
                        onClick = { confermaAssocia = true }, enabled = mancanti.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.weight(1f)
                    ) { Text("Associa", maxLines = 1) }
                    Button(
                        onClick = { confermaCrea = true }, enabled = mancanti.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.weight(1f)
                    ) { Text("Crea (${mancanti.size})", maxLines = 1) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Text("Mostra", style = MaterialTheme.typography.labelMedium)
                    FiltroRiscontro.entries.forEach { f -> FilterChip(selected = filtro == f, onClick = { filtro = f }, label = { Text(f.etichetta) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Text("Data estratto", style = MaterialTheme.typography.labelMedium)
                    tipiDisponibili.forEach { t -> FilterChip(selected = tipoData == t, onClick = { tipoData = t }, label = { Text(t.etichetta) }) }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp)) {
                    Text("Data", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
                    Text("Sul conto", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    Text("Nell'estratto", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
                HorizontalDivider()
                Box(modifier = Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { area = Rect(it.positionInRoot(), it.size.toSize()) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scorrimento, enabled = trascinata == null)
                            .onGloballyPositioned { origine = it.positionInRoot() }
                            .pointerInput(collegamenti) {
                                detectTapGestures { punto ->
                                    linee().map { (c, s) -> c to distanza(punto, s.first, s.second) }
                                        .filter { it.second <= 16.dp.toPx() }
                                        .minByOrNull { it.second }
                                        ?.first?.let { c ->
                                            if (c.manuale) manuali.remove(c) else rimossi += c.operazioneId to c.movimento
                                        }
                                }
                            }
                            .drawWithContent {
                                drawContent()
                                for ((c, s) in linee()) {
                                    val op = perIdOp[c.operazioneId] ?: continue
                                    val stessaData = op.data == movimenti[c.movimento].data
                                    drawLine(
                                        color = if (c.manuale) colManuale else colAuto,
                                        start = s.first,
                                        end = s.second,
                                        strokeWidth = 2.dp.toPx(),
                                        pathEffect = if (stessaData) null else PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                                    )
                                }
                                trascinata?.let { t ->
                                    val r = posizioni[t]?.translate(-origine) ?: return@let
                                    val p = dito - origine
                                    drawLine(colTrascina, r.center, p, strokeWidth = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
                                    drawCircle(colTrascina, radius = 8.dp.toPx(), center = p)
                                }
                            }
                    ) {
                        date.forEach { giorno ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                                val d = LocalDate.ofEpochDay(giorno)
                                Column(modifier = Modifier.width(44.dp).padding(top = 4.dp)) {
                                    Text(d.format(FORMATO_GIORNO), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text(d.format(FORMATO_ANNO), style = MaterialTheme.typography.labelSmall)
                                }
                                Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    opsPerData[giorno].orEmpty().forEach { op ->
                                        val chiave = chiaveOp(op.id)
                                        val descrizione = (if (op.nonContabilizzata) "⏳ non contabilizzata · " else "") +
                                            (op.note ?: op.voceId?.let { dati.vociPerId[it]?.descrizione } ?: if (op.trasferimento) "Spostamento" else "")
                                        CartaRiscontro(
                                            importo = formattaCent(op.importoCent, valuta),
                                            descrizione = descrizione,
                                            collegata = op.id in opCollegate,
                                            evidenziata = bersaglio == chiave,
                                            onPosizione = { posizioni[chiave] = it },
                                            onTocco = { inModifica = op },
                                            onInizio = { p -> posizioni[chiave]?.let { r -> trascinata = chiave; dito = r.topLeft + p } },
                                            onTrascina = { delta -> dito += delta },
                                            onFine = { val t = trascinata; val b = bersaglio; trascinata = null; if (t != null && b != null) collega(t, b) },
                                            onAnnulla = { trascinata = null }
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    movPerData[giorno].orEmpty().forEach { i ->
                                        val chiave = chiaveMov(i)
                                        val m = stato.movimenti[i]
                                        CartaRiscontro(
                                            importo = formattaCent(m.importoCent, valuta),
                                            descrizione = (if (m.nonContabilizzato) "⏳ non contabilizzato · " else "") + m.descrizione,
                                            collegata = i in movCollegati,
                                            evidenziata = bersaglio == chiave,
                                            onPosizione = { posizioni[chiave] = it },
                                            onTocco = {},
                                            onInizio = { p -> posizioni[chiave]?.let { r -> trascinata = chiave; dito = r.topLeft + p } },
                                            onTrascina = { delta -> dito += delta },
                                            onFine = { val t = trascinata; val b = bersaglio; trascinata = null; if (t != null && b != null) collega(t, b) },
                                            onAnnulla = { trascinata = null }
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    }

    esitoImporto?.let { e ->
        AlertDialog(
            onDismissRequest = { esitoImporto = null },
            title = { Text("Associa per importo") },
            text = {
                Text(
                    "Associate: ${e.collegamenti.size}\n" +
                        "Non associate: ${e.piuCandidati + e.senzaCandidati}\n" +
                        "  · con più candidati (saltate): ${e.piuCandidati}\n" +
                        "  · senza operazioni con lo stesso importo: ${e.senzaCandidati}\n\n" +
                        "Le nuove associazioni hanno il colore di quelle fatte a mano: toccando una linea la elimini."
                )
            },
            confirmButton = { TextButton(onClick = { esitoImporto = null }) { Text("OK") } }
        )
    }

    if (mostraInfo) {
        AlertDialog(
            onDismissRequest = { mostraInfo = false },
            icon = {
                Icon(
                    if (problemi.isEmpty()) Icons.Filled.Info else Icons.Filled.Warning, contentDescription = null,
                    tint = if (problemi.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            },
            title = { Text(if (problemi.isEmpty()) "Esito della lettura" else "Lettura con avvisi") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Letti ${stato.movimenti.size} movimenti. Estratto intero: ${collegamenti.size} collegate · ${mancanti.size} mancanti sul conto · " +
                            "$senzaRiscontro del conto non nell'estratto. Nel periodo: ${opsVisibili.size} sul conto, ${movVisibili.size} nell'estratto."
                    )
                    problemi.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text(
                        "Linea continua: stessa data; tratteggiata: data vicina; colorata diversa: collegata a mano. " +
                            "Tocca una linea per eliminarla; tieni premuta una riga non collegata e trascinala su una con lo stesso importo per collegarla.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = { mostraInfo = false }) { Text("OK") } }
        )
    }
    if (confermaAssocia) {
        AlertDialog(
            onDismissRequest = { confermaAssocia = false },
            title = { Text("Associa") },
            text = {
                Text(
                    "Collega ogni movimento dell'estratto ancora non collegato (${mancanti.size}) all'operazione del conto con lo stesso " +
                        "importo, cercata nel periodo dell'estratto con 15 giorni di margine. Se ci sono più operazioni possibili il movimento " +
                        "viene saltato. I collegamenti creati hanno il colore di quelli manuali e si possono togliere toccando la linea; " +
                        "nulla viene salvato sul conto."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confermaAssocia = false
                    val esito = RiscontroEstratto.abbinaPerImporto(
                        opsConto.filter { it.data in (da - 15)..(a + 15) }, movimenti,
                        esclusiOperazioni = opCollegate.keys, esclusiMovimenti = movCollegati.keys
                    )
                    manuali += esito.collegamenti
                    esitoImporto = esito
                }) { Text("Associa") }
            },
            dismissButton = { TextButton(onClick = { confermaAssocia = false }) { Text("Annulla") } }
        )
    }
    if (confermaCrea) {
        AlertDialog(
            onDismissRequest = { confermaCrea = false },
            title = { Text("Crea") },
            text = {
                Text(
                    "Registra sul conto ${dati.etichetta(stato.contoValutaId)} i ${mancanti.size} movimenti dell'estratto che non sono collegati a " +
                        "nessuna operazione, con la data (${tipoData.etichetta.lowercase()}) dell'estratto. Prima del salvataggio si apre l'elenco " +
                        "dove scegliere il tipo di ogni operazione e deselezionare quelle da non creare."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confermaCrea = false
                    vm.salvaTipoDataEstratto(stato.contoId, tipoData)
                    vm.creaDaEstratto(mancanti)
                }) { Text("Continua") }
            },
            dismissButton = { TextButton(onClick = { confermaCrea = false }) { Text("Annulla") } }
        )
    }
    if (confermaDate) {
        AlertDialog(
            onDismissRequest = { confermaDate = false },
            title = { Text("Aggiorna") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Le ${daAggiornare.size} operazioni del conto collegate a un movimento dell'estratto con data diversa (linee tratteggiate) " +
                            "prendono la data (${tipoData.etichetta.lowercase()}) dell'estratto. Importi e descrizioni non cambiano:"
                    )
                    daAggiornare.forEach { (id, nuova) ->
                        val op = perIdOp[id] ?: return@forEach
                        Text(
                            "${formattaCent(op.importoCent, valuta)}: ${formattaData(op.data)} → ${formattaData(nuova)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.aggiornaDateDaEstratto(daAggiornare)
                    confermaDate = false
                }) { Text("Aggiorna") }
            },
            dismissButton = { TextButton(onClick = { confermaDate = false }) { Text("Annulla") } }
        )
    }
    inModifica?.let { op ->
        OperazioneDialog(vm, dati, op.contoValutaId, op, onChiudi = { inModifica = null })
    }
}

/** Una riga (operazione del conto o movimento dell'estratto): importo e descrizione; le non collegate evidenziate. */
@Composable
private fun CartaRiscontro(
    importo: String,
    descrizione: String,
    collegata: Boolean,
    evidenziata: Boolean,
    onPosizione: (Rect) -> Unit,
    onTocco: () -> Unit,
    onInizio: (Offset) -> Unit,
    onTrascina: (Offset) -> Unit,
    onFine: () -> Unit,
    onAnnulla: () -> Unit
) {
    val colori = MaterialTheme.colorScheme
    val inizio by rememberUpdatedState(onInizio)
    val trascina by rememberUpdatedState(onTrascina)
    val fine by rememberUpdatedState(onFine)
    val annulla by rememberUpdatedState(onAnnulla)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = when {
            evidenziata -> colori.primaryContainer
            collegata -> colori.surfaceVariant
            else -> colori.errorContainer
        },
        border = if (evidenziata) BorderStroke(2.dp, colori.secondary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onPosizione(Rect(it.positionInRoot(), it.size.toSize())) }
            .pointerInput(collegata) {
                if (!collegata) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { inizio(it) },
                        onDrag = { change, delta -> change.consume(); trascina(delta) },
                        onDragEnd = { fine() },
                        onDragCancel = { annulla() }
                    )
                }
            }
            .clickable(onClick = onTocco)
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)) {
            Text(importo, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            if (descrizione.isNotBlank()) {
                Text(descrizione, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
