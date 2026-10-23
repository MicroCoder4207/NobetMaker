package com.nobetmaker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobetmaker.app.ui.AppViewModel
import com.nobetmaker.app.ui.CalendarGrid
import com.nobetmaker.app.ui.MonthSwitcher
import com.nobetmaker.app.ui.Nav
import com.nobetmaker.app.ui.PersonDot
import com.nobetmaker.app.ui.SectionCard
import com.nobetmaker.app.ui.StatusPill
import com.nobetmaker.app.ui.gridFromData
import com.nobetmaker.app.ui.rememberData
import com.nobetmaker.app.ui.short
import com.nobetmaker.app.ui.title
import com.nobetmaker.core.calendar.CalendarService
import com.nobetmaker.core.model.MonthStatus
import com.nobetmaker.core.ops.DataOps
import com.nobetmaker.core.rules.RosterIndex
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarScreen(vm: AppViewModel, nav: Nav) {
    val data by rememberData(vm)
    val month by vm.month.collectAsStateWithLifecycle()
    val plan = data.month(month)
    val status = plan?.status
    var showActual by rememberSaveable { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var swapFrom by remember { mutableStateOf<LocalDate?>(null) }
    var swapPicking by remember { mutableStateOf(false) }
    var exportOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val days = remember(data, month, showActual) { gridFromData(data, month, showActual) }
    val scheme = MaterialTheme.colorScheme

    val main: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MonthSwitcher(month, vm::setMonth, Modifier.weight(1f))
            when (status) {
                null -> StatusPill("Liste yok", scheme.surfaceContainerHigh, scheme.onSurfaceVariant)
                MonthStatus.TASLAK -> StatusPill("Taslak", scheme.primaryContainer, scheme.onPrimaryContainer)
                MonthStatus.YAYINLANDI -> StatusPill("Yayınlandı", scheme.secondaryContainer, scheme.onSecondaryContainer)
                MonthStatus.KAPANDI -> StatusPill("Kapandı", scheme.surfaceContainerHigh, scheme.onSurfaceVariant)
            }
        }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = !showActual, onClick = { showActual = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Resmi") }
            SegmentedButton(selected = showActual, onClick = { showActual = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Gerçekleşen") }
        }

        if (swapPicking) {
            Surface(color = scheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val text = swapFrom?.let { "${it.short()} ile yer değiştirecek günü seçin" } ?: "Yer değiştirecek ilk günü seçin"
                    Text(text, Modifier.weight(1f), color = scheme.onSecondaryContainer)
                    TextButton(onClick = { swapFrom = null; swapPicking = false }) { Text("Vazgeç") }
                }
            }
        }

        CalendarGrid(
            days = days,
            selected = selectedDay,
            marked = swapFrom,
            onDayClick = { day ->
                val from = swapFrom
                when {
                    from != null -> {
                        if (from != day.date) vm.update("${from.short()} ↔ ${day.date.short()} değiştirildi") { DataOps.swap(it, month, from, day.date) }
                        swapFrom = null
                        swapPicking = false
                    }
                    swapPicking -> swapFrom = day.date
                    else -> selectedDay = day.date
                }
            },
        )
    }
    val side: @Composable ColumnScope.() -> Unit = {
        MonthSummary(vm, showActual)

        val canEdit = status == null || status == MonthStatus.TASLAK
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (status != MonthStatus.KAPANDI) {
                OutlinedButton(onClick = { nav.prepare(month) }) {
                    Icon(Icons.Default.Build, null); Spacer(Modifier.width(6.dp)); Text("Ayı hazırla")
                }
            }
            if (canEdit) {
                Button(onClick = { nav.generate(month) }) {
                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Liste üret")
                }
            }
            if (plan?.current != null) {
                OutlinedButton(onClick = { exportOpen = true }) {
                    Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Paylaş")
                }
            }
            when (status) {
                MonthStatus.TASLAK -> if (plan.current != null) OutlinedButton(onClick = {
                    confirm = "${month.title()} resmi listesi yayınlansın mı? Bundan sonraki resmi değişiklikler yeni sürüm olarak kaydedilir." to {
                        vm.update("Yayınlandı") { DataOps.setStatus(it, month, MonthStatus.YAYINLANDI) }
                    }
                }) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp)); Text("Yayınla") }
                MonthStatus.YAYINLANDI -> OutlinedButton(onClick = {
                    confirm = "${month.title()} kapatılsın mı? Kapanan ay yeniden üretilmez; yanlış bir gün olursa gün ekranından elle düzeltilir. Belirsiz vizitleri önce işaretleyin." to {
                        vm.update("Ay kapatıldı") { DataOps.setStatus(it, month, MonthStatus.KAPANDI) }
                    }
                }) { Text("Ayı kapat") }
                MonthStatus.KAPANDI -> OutlinedButton(onClick = {
                    confirm = "${month.title()} yeniden açılsın mı?" to { vm.update("Ay yeniden açıldı") { DataOps.setStatus(it, month, MonthStatus.YAYINLANDI) } }
                }) { Icon(Icons.Default.Create, null); Spacer(Modifier.width(6.dp)); Text("Yeniden aç") }
                null -> {}
            }
            if (plan?.current != null && status != MonthStatus.KAPANDI) {
                OutlinedButton(onClick = { swapFrom = null; swapPicking = true }) { Text("İki günü değiştir") }
            }
            if (status == MonthStatus.YAYINLANDI) {
                // Hocalar sayı söylediğinde ya da ay ortasında bir şey değiştiğinde: bugüne kadarki günler kilitlenir,
                // kalan günler yeniden üretilir ve yeni sürüm olarak kaydedilir.
                OutlinedButton(onClick = {
                    confirm = "Bugüne kadarki günler kilitlensin ve kalan günler yeniden üretilsin mi? Seçtiğiniz liste yeni sürüm olarak kaydedilir." to {
                        vm.update("Bugüne kadarki günler kilitlendi", onDone = { nav.generate(month) }) {
                            DataOps.lockThrough(it, month, java.time.LocalDate.now())
                        }
                    }
                }) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Kalan günleri yeniden üret") }
            }
        }
    }

    // Tablet ve yatay ekranda takvim solda, özet ve işlemler sağda; telefonda alt alta.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 840.dp) {
            Row(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1.75f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { main() }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    side()
                    Spacer(Modifier.height(24.dp))
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                main()
                side()
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    var candidates by remember { mutableStateOf<List<com.nobetmaker.core.solver.CandidateInfo>?>(null) }
    LaunchedEffect(selectedDay, data) {
        candidates = null
        selectedDay?.let { candidates = loadCandidates(vm, it) }
    }
    selectedDay?.let { date ->
        DaySheet(
            vm = vm,
            date = date,
            candidates = candidates,
            onDismiss = { selectedDay = null },
            onStartSwap = { swapFrom = date; swapPicking = true; selectedDay = null },
        )
    }
    if (exportOpen) ExportSheet(vm, month, onDismiss = { exportOpen = false })
    confirm?.let { (text, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { action(); confirm = null }) { Text("Evet") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun MonthSummary(vm: AppViewModel, showActual: Boolean) {
    val data by rememberData(vm)
    val month by vm.month.collectAsStateWithLifecycle()
    val rows = remember(data, month, showActual) {
        val cal = CalendarService(data.dayOverrides, data.settings)
        val roster = RosterIndex(data)
        val map = if (showActual) roster.actual else roster.official
        val dates = (1..month.lengthOfMonth()).map { month.atDay(it) }
        val quotas = data.month(month)?.quotas.orEmpty().associate { it.personId to it.final }
        data.persons.sortedBy { it.seniority }.mapNotNull { p ->
            val mine = dates.filter { map[it] == p.id }
            val q = quotas[p.id]
            if (mine.isEmpty() && (q == null || q == 0)) null
            else Triple(p, mine, q)
        }.map { (p, mine, q) -> SummaryRow(p, mine.size, q, mine.sumOf { cal.hours(it) }, mine.count { cal.holidayFraction(it) > 0.0 }) }
    }
    if (rows.isEmpty()) return
    SectionCard(if (showActual) "Gerçekleşen" else "Nöbet sayıları") {
        rows.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                PersonDot(r.person)
                Spacer(Modifier.width(8.dp))
                Text(r.person.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                val quota = if (!showActual && r.quota != null && r.quota != r.count) " / hedef ${r.quota}" else ""
                Text("${r.count} nöbet$quota", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(12.dp))
                Text("${r.hours} sa", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private class SummaryRow(val person: com.nobetmaker.core.model.Person, val count: Int, val quota: Int?, val hours: Int, val holidayCount: Int)

@Composable
fun InfoChip(text: String) {
    AssistChip(onClick = {}, label = { Text(text) })
}
