package com.nobetmaker.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobetmaker.app.ui.AppViewModel
import com.nobetmaker.app.ui.PersonDot
import com.nobetmaker.app.ui.rememberData
import com.nobetmaker.core.calendar.CalendarService
import com.nobetmaker.core.calendar.trFormat
import com.nobetmaker.core.calendar.trName
import com.nobetmaker.core.model.MonthStatus
import com.nobetmaker.core.model.Visit
import com.nobetmaker.core.ops.DataOps
import com.nobetmaker.core.rules.RosterIndex
import com.nobetmaker.core.solver.CandidateInfo
import com.nobetmaker.core.solver.CandidateStatus
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DaySheet(vm: AppViewModel, date: LocalDate, candidates: List<CandidateInfo>?, onDismiss: () -> Unit, onStartSwap: () -> Unit) {
    val data by rememberData(vm)
    val ym = YearMonth.from(date)
    val plan = data.month(ym)
    val status = plan?.status
    val cal = remember(data) { CalendarService(data.dayOverrides, data.settings) }
    val roster = remember(data) { RosterIndex(data) }
    val info = cal.day(date)
    val official = roster.official[date]?.let { data.person(it) }
    val actual = roster.actual[date]?.let { data.person(it) }
    val locked = plan?.current?.assignments?.firstOrNull { it.date == date }?.locked == true
    var choice by remember { mutableStateOf<CandidateInfo?>(null) }
    var swapOpen by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("${date.trFormat()} ${date.dayOfWeek.trName()}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoChip(cal.category(date).label)
                InfoChip("Zorluk ${String.format(Locale.ROOT, "%.1f", cal.points(date))}")
                InfoChip("${cal.hours(date)} saat")
                info.name?.let { InfoChip(it) }
            }

            Text("Vizit", style = MaterialTheme.typography.labelLarge)
            val pastDay = date.isBefore(LocalDate.now())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Visit.entries.forEach { v ->
                    FilterChip(
                        selected = cal.visit(date) == v,
                        onClick = {
                            val base = cal.day(date)
                            val updated = if (pastDay) base.copy(actualVisit = v) else base.copy(plannedVisit = v)
                            vm.update { DataOps.upsertDay(it, updated) }
                        },
                        label = { Text(v.label) },
                    )
                }
            }
            if (pastDay && cal.day(date).actualVisit == null && cal.visit(date) == Visit.BELIRSIZ) {
                Text("Gün geçti: vizit olup olmadığını işaretlerseniz defter düzelir.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Resmi: ${official?.name ?: "—"}", fontWeight = FontWeight.SemiBold)
                    if (actual != null && actual.id != official?.id) Text("Gerçekte tutan: ${actual.name}", color = MaterialTheme.colorScheme.secondary)
                }
                if (actual != null && actual.id != official?.id && status != MonthStatus.KAPANDI) {
                    TextButton(onClick = { vm.update("Resmi kişiye döndü") { DataOps.revertInformal(it, date) } }) { Text("Resmiye döndür") }
                }
            }
            if (status == null || status == MonthStatus.TASLAK) {
                SwitchRow("Kilitle (yeniden üretimde korunur)", locked, enabled = official != null) { on -> vm.update { d -> DataOps.setLock(d, ym, date, on) } }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (official != null && status != MonthStatus.KAPANDI) OutlinedButton(onClick = onStartSwap) { Text("Başka günle değiştir") }
                if (status == MonthStatus.YAYINLANDI && actual != null) OutlinedButton(onClick = { swapOpen = true }) { Text("Değişim asistanı") }
            }

            HorizontalDivider()
            Text("Kim tutabilir?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val list = candidates
            if (list == null) {
                CircularProgressIndicator(Modifier.padding(8.dp))
            } else {
                list.forEach { c ->
                    CandidateRow(c) {
                        when (status) {
                            MonthStatus.KAPANDI -> vm.toast("Kapanmış ay değiştirilemez")
                            null, MonthStatus.TASLAK -> if (c.status == CandidateStatus.HARD) vm.toast(c.hardReasons.joinToString("; "))
                            else if (c.status != CandidateStatus.SELECTED) vm.update("${c.person.name} yazıldı") { DataOps.assign(it, ym, date, c.person.id) }
                            MonthStatus.YAYINLANDI -> if (c.status != CandidateStatus.SELECTED || actual?.id != c.person.id) choice = c
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    choice?.let { c ->
        AlertDialog(
            onDismissRequest = { choice = null },
            title = { Text("${c.person.name} için") },
            text = {
                Text(
                    "Liste yayınlandı. Resmi listeyi mi değiştirmek istiyorsunuz (yeni sürüm açılır), yoksa yalnızca gerçekte kimin tuttuğunu mu kaydediyorsunuz?" +
                        if (c.status == CandidateStatus.HARD) "\n\nResmi listede bu seçim kurallara aykırı: ${c.hardReasons.joinToString("; ")}" else "",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val off = official?.id
                    if (off != null) vm.update("Gayriresmi değişim kaydedildi") { DataOps.addInformal(it, date, off, c.person.id, null, null) }
                    choice = null
                }) { Text("Gerçekte tutan") }
            },
            dismissButton = {
                TextButton(
                    enabled = c.status != CandidateStatus.HARD,
                    onClick = {
                        vm.update("Resmi liste güncellendi (yeni sürüm)") { DataOps.assign(it, ym, date, c.person.id) }
                        choice = null
                    },
                ) { Text("Resmi değişiklik") }
            },
        )
    }
    if (swapOpen && actual != null) SwapSheet(vm, actual.id, date, onDismiss = { swapOpen = false })
}

@Composable
private fun CandidateRow(c: CandidateInfo, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (label, color) = when (c.status) {
        CandidateStatus.SELECTED -> "Yazılı" to scheme.primary
        CandidateStatus.OK -> "Uygun" to Color(0xFF2E7D32)
        CandidateStatus.SOFT -> "Uyarılı" to Color(0xFFB26A00)
        CandidateStatus.HARD -> "Uygun değil" to scheme.error
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = scheme.surfaceContainer,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PersonDot(c.person)
                Spacer(Modifier.width(8.dp))
                Text(c.person.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(label, color = color, style = MaterialTheme.typography.labelLarge)
            }
            (c.hardReasons + c.softReasons).take(4).forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/** Gün ekranındaki adaylar; ana ekranda hesaplanıp alt sayfaya verilir. */
suspend fun loadCandidates(vm: AppViewModel, date: LocalDate): List<CandidateInfo> {
    val ym = YearMonth.from(date)
    val pr = vm.problem(ym)
    val roster = RosterIndex(vm.data.value)
    val assignment = IntArray(pr.dayCount) { i -> roster.official[pr.days[i]]?.let { id -> pr.personIndex(id) } ?: -1 }
    return com.nobetmaker.core.solver.Explainer(pr).candidates(assignment, pr.days.indexOf(date))
}
