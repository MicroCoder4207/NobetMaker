package com.nobetmaker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobetmaker.app.ui.AppViewModel
import com.nobetmaker.app.ui.Nav
import com.nobetmaker.app.ui.PersonDot
import com.nobetmaker.app.ui.SectionCard
import com.nobetmaker.app.ui.rememberData
import com.nobetmaker.app.ui.title
import com.nobetmaker.core.calendar.Category
import com.nobetmaker.core.stats.StatsService
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(vm: AppViewModel, nav: Nav) {
    val data by rememberData(vm)
    var range by rememberSaveable { mutableIntStateOf(6) }
    val last = data.months.filter { it.current != null }.maxByOrNull { it.yearMonth }?.yearMonth ?: YearMonth.now()
    val first = data.months.filter { it.current != null }.minByOrNull { it.yearMonth }?.yearMonth ?: last
    val from = if (range == 0) first else maxOf(first, last.minusMonths((range - 1).toLong()))
    val stats = remember(data, from, last) { StatsService.compute(data, from, last) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("İstatistik", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(6 to "Son 6 ay", 12 to "Son 12 ay", 0 to "Tümü").forEach { (v, label) ->
                FilterChip(selected = range == v, onClick = { range = v }, label = { Text(label) })
            }
        }
        Text("${from.title()} – ${last.title()} · resmi listeye göre", style = MaterialTheme.typography.bodySmall)
        Text(
            "Denge: + önde (payından fazla tutmuş), − geride. Kısa hafıza son 6 ay; bayram, yılbaşı ve resmi tatil 2023'ten beri.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        stats.forEach { s ->
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PersonDot(s.person, 16)
                    Spacer(Modifier.width(8.dp))
                    Text(s.person.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("${s.officialCount} nöbet · ${s.officialHours} sa")
                }
                if (s.actualCount != s.officialCount || s.actualHours != s.officialHours) {
                    Text("Gerçekte: ${s.actualCount} nöbet · ${s.actualHours} sa", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
                val c = s.officialByCategory
                Text(
                    listOf(Category.CUMARTESI, Category.PAZAR, Category.CUMA, Category.PERSEMBE, Category.BAYRAM, Category.RESMI_TATIL, Category.YILBASI)
                        .joinToString(" · ") { "${it.label} ${c[it] ?: 0}" },
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.shortDeviation.forEach { (cat, v) -> BalanceChip(cat.label, v) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.longDeviation.forEach { (cat, v) ->
                        val days = s.longDays[cat] ?: 0
                        val present = s.longPresent[cat] ?: 0
                        BalanceChip("${cat.label} $days gün / $present", v)
                    }
                }
                if (s.requestsTotal > 0) Text("İstek karşılanma: ${s.requestsMet} / ${s.requestsTotal}", style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedButton(onClick = { nav.history() }) { Text("Geçmiş özel günler (2023'ten beri)") }
        Spacer(Modifier.padding(16.dp))
    }
}

@Composable
private fun BalanceChip(label: String, value: Double) {
    val (bg, fg) = when {
        value >= 0.5 -> Color(0xFFFDECEC) to Color(0xFFB3261E)
        value <= -0.5 -> Color(0xFFE3ECFB) to Color(0xFF1F4E99)
        else -> MaterialTheme.colorScheme.surfaceContainer to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val sign = if (value > 0.05) "+" else if (value < -0.05) "−" else "±"
    Surface(color = bg, shape = MaterialTheme.shapes.small) {
        Text(
            "$label $sign${String.format(Locale.ROOT, "%.1f", abs(value))}",
            color = fg, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
