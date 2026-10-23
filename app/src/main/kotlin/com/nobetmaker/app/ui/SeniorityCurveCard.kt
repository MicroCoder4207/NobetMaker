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
                var lastX = -1000f
                var up = true
                for ((p, mm) in points) {
                    val c = personColor(p.color)
                    val center = Offset(gx(mm), gy(coef.curveAt(mm).first))
                    drawCircle(Color.White, 8.dp.toPx(), center)
                    drawCircle(c, 6.dp.toPx(), center)
                    up = if (center.x - lastX < 40.dp.toPx()) !up else true
                    val t = measurer.measure(p.name, TextStyle(fontSize = 11.sp, color = c, fontWeight = FontWeight.Bold))
                    val y = if (up) center.y - t.size.height - 8.dp.toPx() else center.y + 8.dp.toPx()
                    drawText(t, topLeft = Offset(center.x - t.size.width / 2, y))
                    lastX = center.x
                }
            }
        }
    }
}

fun fmt2(v: Double): String = String.format(Locale.ROOT, "%.2f", v).replace('.', ',')
