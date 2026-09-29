package com.desideri.familybalance.ui

import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.desideri.familybalance.logica.Aggregazione
import com.desideri.familybalance.logica.Grafico
import com.desideri.familybalance.logica.Raggruppamento
import com.desideri.familybalance.logica.TipoGrafico
import com.desideri.familybalance.logica.ValoreGrafico
import com.desideri.familybalance.logica.formattaImporto
import kotlin.math.abs
import kotlin.math.round

/** Una serie del grafico: nome in legenda e colore. */
data class SerieGrafico(val nome: String, val colore: Color)

/** Colori di ripiego per le serie senza un colore proprio. */
private val COLORI_SERIE = listOf(
    Color(0xFF1E88E5), Color(0xFFE53935), Color(0xFF43A047), Color(0xFFFB8C00),
    Color(0xFF8E24AA), Color(0xFF00ACC1), Color(0xFF6D4C41), Color(0xFFD81B60)
)

fun coloreSerie(indice: Int, colore: Int?): Color = colore?.let { Color(it) } ?: COLORI_SERIE[indice % COLORI_SERIE.size]

/**
 * Grafico a schermo intero dell'andamento delle spese (ripreso da WorkoutAnalyzer): tipo di
 * grafico (linea, area, istogramma, punti), raggruppamento per valore, mese o anno, linea di
 * tendenza e, toccando il grafico, il dettaglio del punto. [valori] sono già filtrati dal
 * chiamante (le spese in positivo, le entrate in negativo); [nota] spiega cosa è rappresentato.
 */
@Composable
fun GraficoSpeseDialog(
    titolo: String,
    nota: String,
    serie: List<SerieGrafico>,
    valori: List<ValoreGrafico>,
    valuta: String = "EUR",
    aggregazione: Aggregazione = Aggregazione.SOMMA,
    onChiudi: () -> Unit
) {
    var tipo by rememberSaveable { mutableStateOf(TipoGrafico.LINEA) }
    var raggruppamento by rememberSaveable { mutableStateOf(Raggruppamento.MESE) }
    var tendenza by rememberSaveable { mutableStateOf(true) }
    val punti = remember(valori, serie.size, raggruppamento, aggregazione) { Grafico.punti(valori, serie.size, raggruppamento, aggregazione) }

    Dialog(onDismissRequest = onChiudi, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize(), tonalElevation = 4.dp) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(titolo, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onChiudi) { Icon(Icons.Filled.Close, contentDescription = "Chiudi") }
                }
                Text(nota, style = MaterialTheme.typography.bodySmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp)
                ) {
                    TipoGrafico.entries.forEach { t -> FilterChip(selected = tipo == t, onClick = { tipo = t }, label = { Text(t.etichetta) }) }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    Text("Per", style = MaterialTheme.typography.labelMedium)
                    Raggruppamento.entries.forEach { r -> FilterChip(selected = raggruppamento == r, onClick = { raggruppamento = r }, label = { Text(r.etichetta) }) }
                    Spacer(Modifier.width(8.dp))
                    Text("Tendenza", style = MaterialTheme.typography.labelMedium)
                    Switch(checked = tendenza, onCheckedChange = { tendenza = it })
                }
                // Legenda.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)
                ) {
                    serie.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).background(s.colore, CircleShape))
                            Spacer(Modifier.width(4.dp))
                            Text(s.nome, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                GraficoSerie(punti, serie, tipo, tendenza, valuta, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

/** Il grafico: asse Y con gli importi (zero sempre visibile), asse X con fino a 5 etichette. */
@Composable
private fun GraficoSerie(
    punti: List<com.desideri.familybalance.logica.PuntoGrafico>,
    serie: List<SerieGrafico>,
    tipo: TipoGrafico,
    tendenza: Boolean,
    valuta: String,
    modifier: Modifier
) {
    if (punti.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Nessun valore da mostrare con i filtri attuali.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val tutti = punti.flatMap { p -> p.valori.filterNotNull() }
    val minimo = minOf(0.0, tutti.minOrNull() ?: 0.0)
    val massimo = maxOf(0.0, tutti.maxOrNull() ?: 0.0).let { if (it == minimo) minimo + 1.0 else it }
    val tendenze = remember(punti, serie.size) {
        List(serie.size) { s -> Grafico.regressione(punti.mapIndexedNotNull { i, p -> p.valori[s]?.let { i to it } }) }
    }
    val coloreGriglia = MaterialTheme.colorScheme.outlineVariant
    val coloreTesto = MaterialTheme.colorScheme.onSurfaceVariant
    val sfondoTooltip = MaterialTheme.colorScheme.surfaceContainerHigh
    val bordoTooltip = MaterialTheme.colorScheme.outline
    val testoTooltip = MaterialTheme.colorScheme.onSurface
    var selezionato by remember(punti) { mutableStateOf<Int?>(null) }
    val misuraTesto = rememberTextMeasurer()
    fun breve(v: Double) = formattaImporto(v, valuta).replace(",00", "")

    Column(modifier) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.width(64.dp).fillMaxHeight(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.SpaceBetween) {
                Text(breve(massimo), style = MaterialTheme.typography.labelSmall, color = coloreTesto)
                Text(breve((massimo + minimo) / 2), style = MaterialTheme.typography.labelSmall, color = coloreTesto)
                Text(breve(minimo), style = MaterialTheme.typography.labelSmall, color = coloreTesto)
            }
            Spacer(Modifier.width(4.dp))
            Canvas(
                Modifier.weight(1f).fillMaxHeight().pointerInput(punti) {
                    detectTapGestures { o ->
                        val passo = if (punti.size > 1) size.width.toFloat() / (punti.size - 1) else size.width.toFloat()
                        val i = round(o.x / passo).toInt().coerceIn(0, punti.size - 1)
                        selezionato = if (selezionato == i) null else i
                    }
                }
            ) {
                val larghezza = size.width
                val altezza = size.height
                val passo = if (punti.size > 1) larghezza / (punti.size - 1) else 0f
                val margine = altezza * 0.05f
                fun y(v: Double): Float = (altezza - margine) - ((v - minimo) / (massimo - minimo)).toFloat() * (altezza - 2 * margine)
                fun x(i: Int): Float = if (punti.size > 1) i * passo else larghezza / 2

                listOf(minimo, (massimo + minimo) / 2, massimo).forEach { v ->
                    drawLine(coloreGriglia, Offset(0f, y(v)), Offset(larghezza, y(v)), strokeWidth = 1.dp.toPx())
                }
                if (minimo < 0.0) drawLine(coloreTesto, Offset(0f, y(0.0)), Offset(larghezza, y(0.0)), strokeWidth = 1.dp.toPx())

                val larghezzaGruppo = (if (punti.size > 1) passo else larghezza / 3) * 0.7f
                val larghezzaBarra = larghezzaGruppo / serie.size.coerceAtLeast(1)
                serie.forEachIndexed { s, info ->
                    val coordinate = punti.mapIndexedNotNull { i, p -> p.valori[s]?.let { i to Offset(x(i), y(it)) } }
                    when (tipo) {
                        TipoGrafico.LINEA, TipoGrafico.AREA -> {
                            // Con il raggruppamento per valore ogni punto ha una sola serie: si
                            // collegano i punti successivi della stessa serie.
                            for (j in 0 until coordinate.size - 1) {
                                val (_, a) = coordinate[j]
                                val (_, b) = coordinate[j + 1]
                                if (tipo == TipoGrafico.AREA) {
                                    val base = y(0.0)
                                    drawPath(Path().apply {
                                        moveTo(a.x, base); lineTo(a.x, a.y); lineTo(b.x, b.y); lineTo(b.x, base); close()
                                    }, color = info.colore.copy(alpha = 0.18f))
                                }
                                drawLine(info.colore, a, b, strokeWidth = 2.dp.toPx())
                            }
                            coordinate.forEach { (_, o) -> drawCircle(info.colore, radius = 3.dp.toPx(), center = o) }
                        }
                        TipoGrafico.PUNTI -> coordinate.forEach { (_, o) -> drawCircle(info.colore, radius = 4.dp.toPx(), center = o) }
                        TipoGrafico.ISTOGRAMMA -> {
                            val base = y(0.0)
                            coordinate.forEach { (i, o) ->
                                val xb = x(i) - larghezzaGruppo / 2 + s * larghezzaBarra
                                drawRect(info.colore, topLeft = Offset(xb, minOf(o.y, base)), size = Size(larghezzaBarra * 0.85f, abs(base - o.y)))
                            }
                        }
                    }
                    if (tendenza && coordinate.size >= 2) {
                        val (pendenza, intercetta) = tendenze[s]
                        drawLine(
                            info.colore.copy(alpha = 0.6f),
                            Offset(0f, y(intercetta)),
                            Offset(larghezza, y(intercetta + pendenza * (punti.size - 1))),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                        )
                    }
                }

                selezionato?.let { i ->
                    val xs = x(i)
                    drawLine(coloreGriglia, Offset(xs, 0f), Offset(xs, altezza), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f))
                    val righe = mutableListOf(punti[i].etichetta)
                    serie.forEachIndexed { s, info ->
                        punti[i].valori[s]?.let { v ->
                            righe += "${info.nome}: ${formattaImporto(v, valuta)}"
                            drawCircle(info.colore, radius = 5.dp.toPx(), center = Offset(xs, y(v)))
                            drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(xs, y(v)))
                        }
                    }
                    val testo = misuraTesto.measure(righe.joinToString("\n"), style = TextStyle(fontSize = 12.sp, color = testoTooltip))
                    val pad = 6.dp.toPx()
                    val w = testo.size.width + pad * 2
                    val h = testo.size.height + pad * 2
                    val xt = (xs - w / 2).coerceIn(0f, (larghezza - w).coerceAtLeast(0f))
                    val yt = 4.dp.toPx()
                    drawRoundRect(sfondoTooltip, Offset(xt, yt), Size(w, h), CornerRadius(6.dp.toPx()))
                    drawRoundRect(bordoTooltip, Offset(xt, yt), Size(w, h), CornerRadius(6.dp.toPx()), style = Stroke(1.dp.toPx()))
                    drawText(testo, topLeft = Offset(xt + pad, yt + pad))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(start = 68.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Grafico.indiciEtichette(punti.size).forEach { i ->
                Text(punti[i].etichetta, style = MaterialTheme.typography.labelSmall, color = coloreTesto, maxLines = 1, modifier = Modifier.rotate(-90f))
            }
        }
        Text("Tocca il grafico per il dettaglio di un punto.", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Normal, color = coloreTesto)
    }
}
