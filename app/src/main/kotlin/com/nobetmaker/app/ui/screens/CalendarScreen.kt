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
                MonthStatus.YAYINLANDI -> StatusPill("Kesinleşti", scheme.secondaryContainer, scheme.onSecondaryContainer)
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
                    val text = swapFrom?.let { "${it.short()} ile takaslanacak günü seçin" } ?: "Takaslanacak ilk günü seçin"
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
                        if (from != day.date) vm.update("${from.short()} ↔ ${day.date.short()} takaslandı") { DataOps.swap(it, month, from, day.date) }
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

        // Liste nöbet sayılarıyla uyuşmuyorsa (ör. hoca kararından sonra sayı değişti) yeniden oluşturmaya çağır.
        val regenerateRest = {
            confirm = "Bugüne kadarki günler korunsun ve kalan günler yeniden oluşturulsun mu? Seçtiğiniz liste yeni sürüm olarak saklanır." to {
                vm.update("Bugüne kadarki günler korundu", onDone = { nav.generate(month) }) {
                    DataOps.lockThrough(it, month, java.time.LocalDate.now())
                }
            }
        }
        val mismatch = remember(data, month) { quotaMismatch(data, month) }
        if (!showActual && mismatch.isNotEmpty() && status != MonthStatus.KAPANDI) {
            Surface(color = scheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Liste nöbet sayılarıyla uyuşmuyor: " + mismatch.joinToString(", ") { (p, count, quota) -> "${p.name} $count ($quota olmalı)" } + ".",
                        color = scheme.onErrorContainer,
                    )
                    if (status == MonthStatus.YAYINLANDI) {
                        Button(onClick = regenerateRest) { Text("Kalan günleri yeni sayılarla oluştur") }
                    } else {
                        Button(onClick = { nav.generate(month) }) { Text("Listeyi yeni sayılarla oluştur") }
                    }
                }
            }
        }

        val canEdit = status == null || status == MonthStatus.TASLAK
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (status != MonthStatus.KAPANDI) {
                OutlinedButton(onClick = { nav.prepare(month) }) {
                    Icon(Icons.Default.Build, null); Spacer(Modifier.width(6.dp)); Text("Ay hazırlığı")
                }
            }
            if (canEdit) {
                Button(onClick = { nav.generate(month) }) {
                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Liste oluştur")
                }
            }
            if (plan?.current != null) {
                OutlinedButton(onClick = { exportOpen = true }) {
                    Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Paylaş")
                }
            }
            if (plan?.current != null && status != MonthStatus.KAPANDI) {
                OutlinedButton(onClick = { swapFrom = null; swapPicking = true }) { Text("İki günü takasla") }
            }
            when (status) {
                MonthStatus.TASLAK -> if (plan.current != null) OutlinedButton(onClick = {
                    confirm = "${month.title()} listesi kesinleşsin mi? Bundan sonraki değişiklikler yeni sürüm olarak saklanır. Yanlışlıkla kesinleştirirseniz \"Taslağa geri al\" ile dönebilirsiniz." to {
                        vm.update("Liste kesinleşti") { DataOps.setStatus(it, month, MonthStatus.YAYINLANDI) }
                    }
                }) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp)); Text("Listeyi kesinleştir") }
                MonthStatus.YAYINLANDI -> {
                    // Hocalar sayı söylediğinde ya da ay ortasında bir şey değiştiğinde: bugüne kadarki günler korunur,
                    // kalan günler yeniden oluşturulur ve yeni sürüm olarak kaydedilir.
                    OutlinedButton(onClick = regenerateRest) {
                        Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Kalan günleri yeniden oluştur")
                    }
                    OutlinedButton(onClick = {
                        confirm = "${month.title()} listesi taslağa geri alınsın mı? Listeyi baştan oluşturabilir, sayıları değiştirebilirsiniz. Kesinleşmiş hali sürümlerde saklanır." to {
                            vm.update("Taslağa geri alındı") { DataOps.backToDraft(it, month) }
                        }
                    }) { Icon(Icons.Default.Create, null); Spacer(Modifier.width(6.dp)); Text("Taslağa geri al") }
                    OutlinedButton(onClick = {
                        confirm = "${month.title()} listesi kapatılsın mı? Kapanan liste yeniden oluşturulmaz; yanlış bir gün olursa gün ekranından elle düzeltilir. Belirsiz vizitleri önce işaretleyin." to {
                            vm.update("Liste kapatıldı") { DataOps.setStatus(it, month, MonthStatus.KAPANDI) }
                        }
                    }) { Text("Listeyi kapat") }
                }
                MonthStatus.KAPANDI -> OutlinedButton(onClick = {
                    confirm = "${month.title()} listesi yeniden açılsın mı?" to { vm.update("Liste yeniden açıldı") { DataOps.setStatus(it, month, MonthStatus.YAYINLANDI) } }
                }) { Icon(Icons.Default.Create, null); Spacer(Modifier.width(6.dp)); Text("Listeyi yeniden aç") }
                null -> {}
            }
        }
        Text(
            "İstatistik, sıra ve bayram dengesi her zaman geçerli resmi listeden hesaplanır; listede yaptığınız her değişiklik kendiliğinden yansır.",
            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
        )
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
                Text("${r.count} nöbet", style = MaterialTheme.typography.bodyMedium)
                if (!showActual && r.quota != null && r.quota != r.count) {
                    Text(" (${r.quota} olmalı)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.width(12.dp))
                Text("${r.hours} saat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Resmi listedeki sayısı nöbet sayıları sekmesindeki sayıyla uyuşmayan kişiler: (kişi, listede, olması gereken). */
private fun quotaMismatch(data: com.nobetmaker.core.model.AppData, month: java.time.YearMonth): List<Triple<com.nobetmaker.core.model.Person, Int, Int>> {
    val plan = data.month(month) ?: return emptyList()
    val cur = plan.current ?: return emptyList()
    val counts = cur.assignments.groupingBy { it.personId }.eachCount()
    return plan.quotas.mapNotNull { q ->
        val p = data.person(q.personId) ?: return@mapNotNull null
        val n = counts[q.personId] ?: 0
        if (n != q.final) Triple(p, n, q.final) else null
    }.sortedBy { it.first.seniority }
}

private class SummaryRow(val person: com.nobetmaker.core.model.Person, val count: Int, val quota: Int?, val hours: Int, val holidayCount: Int)

@Composable
fun InfoChip(text: String) {
    AssistChip(onClick = {}, label = { Text(text) })
}
