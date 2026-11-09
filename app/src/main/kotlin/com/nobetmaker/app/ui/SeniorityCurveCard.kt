package com.nobetmaker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.rules.Coefficients
import java.time.LocalDate
import java.util.Locale

/**
 * Kıdem eğrisi (§5.1-9): herkes aynı eğrinin üstünde bir nokta. Nöbet katsayısı asistanlık yılına göre iner;
 * son yılda belirgin düşer, uzatmada 0,50 olur.
 */
@Composable
fun SeniorityCurveCard(data: AppData, today: LocalDate = LocalDate.now()) {
    val coef = Coefficients(data)
    val persons = data.persons.filter { it.isActiveOn(today) && coef.usesCurve(it) }
    if (!data.settings.curve.enabled || persons.isEmpty()) return
    val years = data.settings.curve.years
    val maxM = years * 12.0 + 18.0
    val measurer = rememberTextMeasurer()
    val line = MaterialTheme.colorScheme.onSurface
    val grid = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val summary = persons.joinToString(", ") { p -> "${p.name} ${coef.stageText(p, today)} ${fmt2(coef.on(p, today).duty)}" }
    SectionCard("Kıdem eğrisi") {
        Text("Herkes aynı eğriden geçer: çömezken çok, kıdemliyken az. Katsayı asistanlık yılından hesaplanır.", style = MaterialTheme.typography.bodySmall)
        Column {
            Canvas(Modifier.fillMaxWidth().height(190.dp).semantics { contentDescription = "Kıdem eğrisi: $summary" }) {
                val left = 40.dp.toPx()
                val right = size.width - 8.dp.toPx()
                val top = 26.dp.toPx()
                val bottom = size.height - 26.dp.toPx()
                fun gx(m: Double) = (left + (right - left) * m / maxM).toFloat()
                fun gy(v: Double) = (bottom - (bottom - top) * (v - 0.45) / 0.6).toFloat()
                val small = TextStyle(fontSize = 10.sp, color = muted)
                for (v in listOf(0.5, 0.75, 1.0)) {
                    drawLine(grid, Offset(left, gy(v)), Offset(right, gy(v)), strokeWidth = 1.dp.toPx())
                    val t = measurer.measure(fmt2(v), small)
                    drawText(t, topLeft = Offset(left - t.size.width - 6.dp.toPx(), gy(v) - t.size.height / 2))
                }
                for (yr in 1..years + 1) {
                    val t = measurer.measure(if (yr == years + 1) "uzatma" else "$yr. yıl", small)
                    drawText(t, topLeft = Offset(gx((yr - 1) * 12.0 + 6) - t.size.width / 2, bottom + 6.dp.toPx()))
                }
                val path = Path()
                var m = 0.0
                path.moveTo(gx(0.0), gy(coef.curveAt(0.0).first))
                while (m <= maxM) {
                    path.lineTo(gx(m), gy(coef.curveAt(m).first))
                    m += 0.25
                }
                drawPath(path, line, style = Stroke(width = 3.dp.toPx()))
                val points = persons.map { p -> p to minOf(coef.residencyMonths(p, today) ?: 0.0, maxM - 3) }.sortedBy { it.second }
                val centers = points.map { (p, mm) ->
                    val center = Offset(gx(mm), gy(coef.curveAt(mm).first))
                    drawCircle(Color.White, 8.dp.toPx(), center)
                    drawCircle(personColor(p.color), 6.dp.toPx(), center)
                    center
                }
                // İsimler üst üste binmesin: her isim, önceki isimlere ve noktalara çarpmayan ilk yere yazılır.
                val dot = 9.dp.toPx()
                val gap = 6.dp.toPx()
                val placed = centers.map { Rect(it.x - dot, it.y - dot, it.x + dot, it.y + dot) }.toMutableList()
                points.forEachIndexed { i, (p, _) ->
                    val center = centers[i]
                    val t = measurer.measure(p.name, TextStyle(fontSize = 11.sp, color = personColor(p.color), fontWeight = FontWeight.Bold))
                    val tw = t.size.width.toFloat()
                    val th = t.size.height.toFloat()
                    val options = listOf(
                        Offset(center.x - tw / 2, center.y - th - gap - dot / 2), Offset(center.x - tw / 2, center.y + gap + dot / 2),
                        Offset(center.x + dot + gap / 2, center.y + gap / 2), Offset(center.x - dot - gap / 2 - tw, center.y + gap / 2),
                        Offset(center.x + dot + gap / 2, center.y - th - gap / 2), Offset(center.x - dot - gap / 2 - tw, center.y - th - gap / 2),
                        Offset(center.x - tw / 2, center.y + th + 2 * gap), Offset(center.x - tw / 2, center.y - 2 * th - 2 * gap),
                    ).map { Rect(it, Size(tw, th)) }
                    val box = options.firstOrNull { o -> o.left >= 0f && o.right <= size.width && placed.none { it.overlaps(o) } } ?: options[0]
                    placed += box
                    drawText(t, topLeft = box.topLeft)
                }
            }
        }
    }
}

fun fmt2(v: Double): String = String.format(Locale.ROOT, "%.2f", v).replace('.', ',')
