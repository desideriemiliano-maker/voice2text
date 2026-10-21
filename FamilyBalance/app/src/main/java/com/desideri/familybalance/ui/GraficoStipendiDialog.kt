package com.desideri.familybalance.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.desideri.familybalance.data.Stipendio
import com.desideri.familybalance.data.VoceStipendio
import com.desideri.familybalance.logica.PuntoGrafico
import com.desideri.familybalance.logica.TipoGrafico
import com.desideri.familybalance.logica.formattaCent
import com.desideri.familybalance.logica.formattaMese
import com.desideri.familybalance.logica.formattaMeseBreve
import kotlinx.coroutines.launch
import java.time.YearMonth
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Grafico degli stipendi di una tipologia, con voci e periodo selezionabili: andamento nel tempo
 * (linea, area, istogramma…) o radar con una dimensione per voce, animato mese per mese.
 * [valoriPerStipendio]: per stipendio, i valori (centesimi) di tutte le voci, anche calcolate.
 */
@Composable
fun GraficoStipendiDialog(
    titolo: String,
    voci: List<VoceStipendio>,
    stipendi: List<Stipendio>,
    valoriPerStipendio: Map<Long, Map<Long, Long>>,
    valuta: String,
    annoIniziale: Int,
    onChiudi: () -> Unit
) {
    val mesiDisponibili = stipendi.map { YearMonth.parse(it.mese) }.sorted()
    // Periodo predefinito: l'anno mostrato nella tabella, se ha stipendi, altrimenti tutto.
    val nellAnno = mesiDisponibili.filter { it.year == annoIniziale }
    var da by remember { mutableStateOf((nellAnno.ifEmpty { mesiDisponibili }).firstOrNull() ?: YearMonth.now()) }
    var a by remember { mutableStateOf((nellAnno.ifEmpty { mesiDisponibili }).lastOrNull() ?: YearMonth.now()) }
    var scelte by remember { mutableStateOf(voci.filter { v -> stipendi.any { valoriPerStipendio[it.id]?.get(v.id) != null } }.take(6)) }
    var radar by rememberSaveable { mutableStateOf(false) }
    var tipo by rememberSaveable { mutableStateOf(TipoGrafico.LINEA) }
    var tendenza by rememberSaveable { mutableStateOf(false) }
    var scalaComune by rememberSaveable { mutableStateOf(false) }

    val vociScelte = voci.filter { it in scelte }
    val righe = stipendi.filter { YearMonth.parse(it.mese) in da..a }.sortedBy { it.mese }
    val serie = vociScelte.mapIndexed { i, v -> SerieGrafico(v.descrizione, coloreSerie(i, null)) }

    Dialog(onDismissRequest = onChiudi, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize(), tonalElevation = 4.dp) {
            Column(modifier = Modifier.fillMaxSize().paddingBarreDialog().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(titolo, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onChiudi) { Icon(Icons.Filled.Close, contentDescription = "Chiudi") }
                }
                MenuMultiplo("Voci", voci, scelte, { it.descrizione }, onCambia = { scelte = it }, modifier = Modifier.fillMaxWidth(), conTutti = true)
                Row {
                    SceltaMese("Dal", da, { da = it; if (a < it) a = it }, Modifier.weight(1f))
                    SceltaMese("Al", a, { a = it; if (da > it) da = it }, Modifier.weight(1f))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(selected = radar, onClick = { radar = true }, label = { Text("Radar") })
                    TipoGrafico.entries.forEach { t -> FilterChip(selected = !radar && tipo == t, onClick = { radar = false; tipo = t }, label = { Text(t.etichetta) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (radar) {
                        Text("Stessa scala per tutte le voci", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        Switch(checked = scalaComune, onCheckedChange = { scalaComune = it })
                    } else {
                        Text("Tendenza", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        Switch(checked = tendenza, onCheckedChange = { tendenza = it })
                    }
                }
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                when {
                    vociScelte.isEmpty() || righe.isEmpty() ->
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("Scegli almeno una voce e un periodo con stipendi.", style = MaterialTheme.typography.bodyMedium)
                        }
                    radar -> RadarStipendi(vociScelte, righe, valoriPerStipendio, serie, valuta, scalaComune, Modifier.fillMaxWidth().weight(1f))
                    else -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp)
                        ) {
                            serie.forEach { s ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(12.dp).background(s.colore, CircleShape))
                                    Spacer(Modifier.width(4.dp))
                                    Text(s.nome, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        val punti = righe.map { s ->
                            PuntoGrafico(
                                formattaMeseBreve(YearMonth.parse(s.mese)),
                                vociScelte.map { v -> valoriPerStipendio[s.id]?.get(v.id)?.let { it / 100.0 } }
                            )
                        }
                        GraficoSerie(punti, serie, tipo, tendenza, valuta, Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Radar: un asse per voce, il poligono di un mese. Con ▶ il poligono si trasforma da un mese al
 * successivo; lo slider sceglie il mese. Ogni asse va da zero al massimo (in valore assoluto) della
 * voce nel periodo, o al massimo di tutte le voci con [scalaComune]; il contorno tratteggiato è il
 * primo mese del periodo.
 */
@Composable
private fun RadarStipendi(
    voci: List<VoceStipendio>,
    righe: List<Stipendio>,
    valoriPerStipendio: Map<Long, Map<Long, Long>>,
    serie: List<SerieGrafico>,
    valuta: String,
    scalaComune: Boolean,
    modifier: Modifier
) {
    // Valori assoluti (centesimi) per mese e voce; senza valore vale zero.
    val valori = righe.map { s -> voci.map { v -> abs(valoriPerStipendio[s.id]?.get(v.id) ?: 0L).toDouble() } }
    val massimi = voci.indices.map { i -> valori.maxOf { it[i] } }
    val massimoComune = massimi.maxOrNull() ?: 0.0
    val n = righe.size
    val progresso = remember(righe, voci) { Animatable((n - 1).toFloat()) }
    var inRiproduzione by remember(righe, voci) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(inRiproduzione) {
        if (inRiproduzione && n > 1) {
            if (progresso.value >= n - 1) progresso.snapTo(0f)
            progresso.animateTo((n - 1).toFloat(), tween(((n - 1 - progresso.value) * 900).toInt().coerceAtLeast(1), easing = LinearEasing))
        }
        inRiproduzione = false
    }
    val p = progresso.value.coerceIn(0f, (n - 1).coerceAtLeast(0).toFloat())
    val i0 = floor(p).toInt().coerceIn(0, n - 1)
    val i1 = (i0 + 1).coerceAtMost(n - 1)
    val f = (p - i0).toDouble()
    val correnti = voci.indices.map { k -> valori[i0][k] * (1 - f) + valori[i1][k] * f }
    val meseCorrente = righe[if (f >= 0.5) i1 else i0]
    fun scala(k: Int) = (if (scalaComune) massimoComune else massimi[k]).takeIf { it > 0 } ?: 1.0

    val coloreGriglia = MaterialTheme.colorScheme.outlineVariant
    val coloreTesto = MaterialTheme.colorScheme.onSurfaceVariant
    val colore = MaterialTheme.colorScheme.primary
    val misuraTesto = rememberTextMeasurer()

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { inRiproduzione = !inRiproduzione }, enabled = n > 1) {
                Icon(if (inRiproduzione) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (inRiproduzione) "Pausa" else "Riproduci")
            }
            Text(formattaMese(YearMonth.parse(meseCorrente.mese)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (n > 1) {
            Slider(
                value = p, valueRange = 0f..(n - 1).toFloat(),
                onValueChange = { v -> inRiproduzione = false; scope.launch { progresso.snapTo(v) } }
            )
        }
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val centro = Offset(size.width / 2, size.height / 2)
            val raggio = minOf(size.width, size.height) / 2 * 0.72f
            val assi = voci.size.coerceAtLeast(1)
            fun angolo(k: Int) = -PI / 2 + 2 * PI * k / assi
            fun punto(k: Int, r: Double) = Offset(
                centro.x + (raggio * r * cos(angolo(k))).toFloat(),
                centro.y + (raggio * r * sin(angolo(k))).toFloat()
            )
            // Griglia: anelli al 25/50/75/100% e un asse per voce con il nome.
            listOf(0.25, 0.5, 0.75, 1.0).forEach { r ->
                val anello = Path()
                (0 until assi).forEach { k -> val q = punto(k, r); if (k == 0) anello.moveTo(q.x, q.y) else anello.lineTo(q.x, q.y) }
                anello.close()
                drawPath(anello, coloreGriglia, style = Stroke(1.dp.toPx()))
            }
            (0 until assi).forEach { k ->
                drawLine(coloreGriglia, centro, punto(k, 1.0), strokeWidth = 1.dp.toPx())
                val etichetta = misuraTesto.measure(voci[k].descrizione, style = TextStyle(fontSize = 11.sp, color = serie[k].colore, fontWeight = FontWeight.SemiBold))
                val q = punto(k, 1.12)
                val x = (q.x - etichetta.size.width / 2f).coerceIn(0f, (size.width - etichetta.size.width).coerceAtLeast(0f))
                val y = (q.y - etichetta.size.height / 2f).coerceIn(0f, (size.height - etichetta.size.height).coerceAtLeast(0f))
                drawText(etichetta, topLeft = Offset(x, y))
            }
            fun poligono(v: List<Double>): Path = Path().apply {
                v.forEachIndexed { k, x -> val q = punto(k, (x / scala(k)).coerceIn(0.0, 1.0)); if (k == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) }
                close()
            }
            if (assi >= 2) {
                // Primo mese del periodo, come riferimento.
                drawPath(
                    poligono(valori.first()), coloreTesto,
                    style = Stroke(1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f))
                )
                val attuale = poligono(correnti)
                drawPath(attuale, colore.copy(alpha = 0.25f))
                drawPath(attuale, colore, style = Stroke(2.dp.toPx()))
            }
            correnti.forEachIndexed { k, x -> drawCircle(serie[k].colore, radius = 4.dp.toPx(), center = punto(k, (x / scala(k)).coerceIn(0.0, 1.0))) }
        }
        // Valori del mese mostrato (quelli registrati, con il segno).
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp)
        ) {
            voci.forEachIndexed { k, v ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(serie[k].colore, CircleShape))
                        Spacer(Modifier.width(4.dp))
                        Text(v.descrizione, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        valoriPerStipendio[meseCorrente.id]?.get(v.id)?.let { formattaCent(it, valuta) } ?: "—",
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Text(
            (if (scalaComune) "Assi sulla stessa scala (il valore più alto del periodo)." else "Ogni asse va da zero al valore più alto della voce nel periodo.") +
                " Valori assoluti; tratteggiato il primo mese.",
            style = MaterialTheme.typography.bodySmall, color = coloreTesto, modifier = Modifier.padding(top = 4.dp)
        )
    }
}
