package com.nobetmaker.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobetmaker.app.ui.AppViewModel
import com.nobetmaker.app.ui.ChoiceField
import com.nobetmaker.app.ui.Nav
import com.nobetmaker.app.ui.PersonDot
import com.nobetmaker.app.ui.SectionCard
import com.nobetmaker.app.ui.rememberData
import com.nobetmaker.app.ui.short
import com.nobetmaker.core.calendar.CalendarService
import com.nobetmaker.core.calendar.Category
import com.nobetmaker.core.calendar.trFormat
import com.nobetmaker.core.calendar.trName
import com.nobetmaker.core.export.Dokum
import com.nobetmaker.core.ledger.HolidayBalance
import com.nobetmaker.core.ledger.HolidayDay
import com.nobetmaker.core.ledger.HolidayReport
import com.nobetmaker.core.ledger.HolidayReportBuilder
import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.model.DayKind
import com.nobetmaker.core.model.Visit
import com.nobetmaker.core.ops.DataOps
import com.nobetmaker.core.rules.RosterIndex
import java.time.LocalDate
import java.util.Locale

/**
 * Bayram ve tatil kayıtları (§6.2): ilk listeden bu yana her bayram, resmi tatil ve yılbaşı günü kimin tuttuğu,
 * bugünkü ekibin dengesi ve her günün elle düzeltilmesi. Kayıtlar kapanmış aylarda da düzeltilebilir.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HolidayScreen(vm: AppViewModel, nav: Nav) {
    val data by rememberData(vm)
    val report = remember(data) { HolidayReportBuilder.build(data) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<LocalDate?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bayram ve tatil kayıtları") },
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "${report.since.trFormat()} – ${report.until.trFormat()} arası her bayram, resmi tatil ve yılbaşı günü. Bir güne dokunun: " +
                    "kimin tuttuğunu, gün türünü ve viziti düzeltin; denge hemen yeniden hesaplanır. Kapanmış aylar da düzeltilebilir.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Resmi kayıt hesaba girer; \"gerçekte tutan\" (takas) girmez. Arefe yarım gün sayılır; uzmanın tuttuğu gün asistan hesabına girmez. " +
                    "Her günün payı o gün ekipte olanlar arasında o günkü katsayıyla bölünür.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BalanceCard(report)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Tümü") })
                for (c in Category.LONG_MEMORY) {
                    FilterChip(selected = filter == c.name, onClick = { filter = c.name }, label = { Text(c.label) })
                }
            }
            val events = report.events.filter { filter == null || it.category.name == filter }
            events.forEach { e ->
                SectionCard("${e.name} · ${dayCount(e.days.sumOf { it.weight })}") {
                    e.days.forEach { d -> HolidayDayRow(data, d) { editing = d.date } }
                }
            }
            OutlinedButton(onClick = { nav.history() }) { Text("Liste öncesi toplu kayıtlar") }
            Spacer(Modifier.padding(16.dp))
        }
    }
    editing?.let { HolidayDayDialog(vm, it) { editing = null } }
}

private fun dayCount(v: Double): String = (if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.ROOT, "%.1f", v).replace('.', ',')) + " gün"

private fun num(v: Double): String = String.format(Locale.ROOT, "%.1f", v).replace('.', ',')

@Composable
private fun BalanceCard(report: HolidayReport) {
    SectionCard("Denge (bugünkü ekip)") {
        Text("Her hücrede: durum, altında tutulan / payı (gün).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row {
            Spacer(Modifier.weight(1.2f))
            for (c in Category.LONG_MEMORY) Text(c.label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
        report.persons.forEach { p ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1.2f), verticalAlignment = Alignment.CenterVertically) {
                    PersonDot(p)
                    Spacer(Modifier.width(6.dp))
                    Text(p.name, fontWeight = FontWeight.SemiBold)
                }
                for (c in Category.LONG_MEMORY) {
                    val b = report.balances[p.id]?.get(c)
                    Column(Modifier.weight(1f)) {
                        if (p.id in report.exempt || b == null) {
                            Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(Dokum.balanceText(b.diff), color = balanceColor(b), style = MaterialTheme.typography.labelLarge)
                            Text("${num(b.held)} / ${num(b.expected)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun balanceColor(b: HolidayBalance): Color = when {
    kotlin.math.abs(b.diff) < 0.75 -> MaterialTheme.colorScheme.onSurfaceVariant
    b.diff > 0 -> Color(0xFFB45F06)
    else -> Color(0xFF1C6DD0)
}

@Composable
private fun HolidayDayRow(data: AppData, d: HolidayDay, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val official = d.official?.let { data.person(it) }
    val actual = d.actual?.let { data.person(it) }
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = MaterialTheme.shapes.medium, color = scheme.surfaceContainer) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${d.date.short()} ${d.date.dayOfWeek.trName()}", fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(
                        d.kind.takeIf { it != DayKind.NORMAL }?.label ?: "Hafta sonu",
                        "yarım gün".takeIf { d.weight < 1.0 },
                        "vizit ${d.visit.label.lowercase(Locale.forLanguageTag("tr"))}",
                        "türü elle".takeIf { d.edited },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (official != null) {
                        PersonDot(official)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(official?.name ?: "Uzman", fontWeight = FontWeight.SemiBold, color = if (official == null) scheme.onSurfaceVariant else scheme.onSurface)
                }
                if (actual != null) Text("gerçekte ${actual.name}", style = MaterialTheme.typography.bodySmall, color = scheme.secondary)
            }
        }
    }
}

/** Bir tatil gününün kaydı: resmi kişi (ya da uzman), gerçekte tutan, gün türü ve vizit. */
@Composable
fun HolidayDayDialog(vm: AppViewModel, date: LocalDate, onDismiss: () -> Unit) {
    val data by rememberData(vm)
    val cal = remember(data) { CalendarService(data.dayOverrides, data.settings) }
    val roster = remember(data) { RosterIndex(data) }
    val official = roster.official[date]
    val actual = roster.actual[date]?.takeIf { it != official }
    // Seçenekler kişi id'leri; "" = uzman (resmi kayıtta) ya da resmi kişi (gerçekte tutanda).
    val ids = listOf("") + data.persons.filter { it.isActiveOn(date) || it.id == official || it.id == actual }.sortedBy { it.seniority }.map { it.id }
    val info = cal.day(date)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${date.trFormat()} ${date.dayOfWeek.trName()}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Resmi kayıt (hesaba girer)", style = MaterialTheme.typography.labelLarge)
                ChoiceField("Tutan", ids, official ?: "", { data.person(it)?.name ?: "Uzman (asistan yok)" }, { id ->
                    if (id != (official ?: "")) vm.update("Resmi kayıt düzeltildi") { DataOps.correctDay(it, date, id.ifEmpty { null }) }
                }, Modifier.fillMaxWidth())
                Text("Gerçekte tutan (takas, hesaba girmez)", style = MaterialTheme.typography.labelLarge)
                if (official == null) {
                    Text("Resmi kayıt uzman; önce resmi kaydı seçin.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    ChoiceField("Gerçekte", ids, actual ?: "", { data.person(it)?.name ?: "Resmi kişi tuttu" }, { id ->
                        vm.update("Gerçekte tutan kaydedildi") { if (id.isEmpty()) DataOps.revertInformal(it, date) else DataOps.recordActual(it, date, id) }
                    }, Modifier.fillMaxWidth())
                }
                ChoiceField("Gün türü", DayKind.entries, info.kind, { it.label }, { k ->
                    // Bayrama bitişik hafta sonunun türü zaten "normal"dir; yine de seçilince olaydan çıkarılır.
                    if (k != info.kind || cal.category(date) in Category.LONG_MEMORY) vm.update("Gün türü: ${k.label}") { DataOps.setDayKind(it, date, k) }
                }, Modifier.fillMaxWidth())
                Text("Vizit", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Visit.entries.forEach { v ->
                        FilterChip(
                            selected = cal.visit(date) == v,
                            onClick = {
                                val past = date.isBefore(LocalDate.now())
                                vm.update { DataOps.upsertDay(it, if (past) info.copy(actualVisit = v) else info.copy(plannedVisit = v)) }
                            },
                            label = { Text(v.label) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tamam") } },
    )
}
