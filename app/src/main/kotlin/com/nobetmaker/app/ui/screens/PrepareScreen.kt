package com.nobetmaker.app.ui.screens

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobetmaker.app.ui.AppViewModel
import com.nobetmaker.app.ui.ChoiceField
import com.nobetmaker.app.ui.DateField
import com.nobetmaker.app.ui.EmptyNote
import com.nobetmaker.app.ui.MonthDayPicker
import com.nobetmaker.app.ui.Nav
import com.nobetmaker.app.ui.PersonDot
import com.nobetmaker.app.ui.PersonField
import com.nobetmaker.app.ui.SectionCard
import com.nobetmaker.app.ui.rememberData
import com.nobetmaker.app.ui.short
import com.nobetmaker.app.ui.title
import com.nobetmaker.core.model.DutyRequest
import com.nobetmaker.core.model.MonthStatus
import com.nobetmaker.core.model.Period
import com.nobetmaker.core.model.PeriodType
import com.nobetmaker.core.model.Person
import com.nobetmaker.core.model.QuotaEntry
import com.nobetmaker.core.model.Priority
import com.nobetmaker.core.model.RequestType
import com.nobetmaker.core.ops.DataOps
import com.nobetmaker.core.rules.Availability
import com.nobetmaker.core.rules.RequestText
import com.nobetmaker.core.stats.ConflictDetector
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrepareScreen(vm: AppViewModel, nav: Nav, month: YearMonth) {
    val data by rememberData(vm)
    LaunchedEffect(month) { if (data.month(month) == null) vm.update { DataOps.ensureMonth(it, month) } }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Dönemler", "İstekler", "Nöbet sayıları", "Kontrol")
    val status = data.month(month)?.status
    val editable = status == null || status == MonthStatus.TASLAK
    // Kesinleşmiş ayda da sayılar değişebilir (hoca kararı); liste "Kalan günleri yeniden oluştur" ile güncellenir.
    val quotaEditable = status != MonthStatus.KAPANDI

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${month.title()} hazırlığı") },
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            )
        },
        bottomBar = {
            if (editable) {
                Surface(tonalElevation = 3.dp) {
                    Button(onClick = { nav.generate(month) }, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Liste oluştur") }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Sekme adları tek satırda kalsın diye genişlik içeriğe göre; dar telefonda kaydırılabilir.
            PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }) {
                        Text(t, Modifier.padding(horizontal = 12.dp, vertical = 14.dp), maxLines = 1, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (tab) {
                    0 -> PeriodsTab(vm, month)
                    1 -> RequestsTab(vm, month)
                    2 -> QuotaTab(vm, month, quotaEditable, published = status == MonthStatus.YAYINLANDI)
                    else -> CheckTab(vm, month)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Dönemler

@Composable
private fun PeriodsTab(vm: AppViewModel, month: YearMonth) {
    val data by rememberData(vm)
    var editing by remember { mutableStateOf<Period?>(null) }
    var creating by remember { mutableStateOf(false) }
    val from = month.atDay(1).minusDays(7)
    val to = month.atEndOfMonth().plusDays(7)
    val list = data.periods.filter { !it.end.isBefore(from) && !it.start.isAfter(to) }.sortedBy { it.start }

    Text("İzin, kongre, rapor ve rotasyonlar. Yıllık izin ve kongre nöbet sayısını düşürmez.", style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { creating = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Dönem ekle") }
    if (list.isEmpty()) EmptyNote("Bu ay için dönem yok")
    list.forEach { p ->
        val person = data.person(p.personId)
        ItemCard(
            person = person,
            title = "${person?.name ?: "?"} · ${p.type.label}",
            lines = listOfNotNull(
                Availability.periodText(p),
                if (p.tentative) "Belirsiz (mümkünse istek gibi)" else null,
                if (p.worksDuty) "Bu dönemde nöbet tutar" else null,
                if (p.reducesQuota) "Nöbet sayısını düşürür" else null,
                p.note,
            ),
            onEdit = { editing = p },
            onDelete = { vm.update("Dönem silindi") { DataOps.deletePeriod(it, p.id) } },
        )
    }
    if (creating || editing != null) {
        PeriodDialog(
            persons = data.persons.filter { it.leaveDate == null || !it.leaveDate!!.isBefore(month.atDay(1)) },
            initial = editing,
            month = month,
            onDismiss = { creating = false; editing = null },
            onSave = { p -> vm.update("Kaydedildi") { DataOps.upsertPeriod(it, p) }; creating = false; editing = null },
        )
    }
}

@Composable
private fun PeriodDialog(persons: List<Person>, initial: Period?, month: YearMonth, onDismiss: () -> Unit, onSave: (Period) -> Unit) {
    var personId by remember { mutableStateOf(initial?.personId) }
    var type by remember { mutableStateOf(initial?.type ?: PeriodType.YILLIK_IZIN) }
    var start by remember { mutableStateOf(initial?.start ?: month.atDay(1)) }
    var end by remember { mutableStateOf(initial?.end ?: month.atDay(5)) }
    var tentative by remember { mutableStateOf(initial?.tentative ?: false) }
    var works by remember { mutableStateOf(initial?.worksDuty ?: type.defaultWorksDuty) }
    var reduces by remember { mutableStateOf(initial?.reducesQuota ?: type.defaultReducesQuota) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Dönem ekle" else "Dönemi düzenle") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PersonField("Kişi", persons, personId, { personId = it })
                ChoiceField("Tür", PeriodType.entries, type, { it.label }, {
                    type = it; works = it.defaultWorksDuty; reduces = it.defaultReducesQuota
                })
                DateField("Başlangıç", start, { d -> d?.let { start = it; if (end.isBefore(it)) end = it } })
                DateField("Bitiş", end, { d -> d?.let { end = it } })
                SwitchRow("Tarihi belirsiz", tentative) { tentative = it }
                SwitchRow("Bu dönemde nöbet tutar", works) { works = it }
                SwitchRow("Nöbet sayısını düşürür", reduces) { reduces = it }
                OutlinedTextField(note, { note = it }, label = { Text("Not") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = personId != null && !end.isBefore(start), onClick = {
                onSave(
                    Period(
                        id = initial?.id ?: DataOps.newId("d"), personId = personId!!, start = start, end = end, type = type,
                        worksDuty = works, reducesQuota = reduces, tentative = tentative, note = note.ifBlank { null },
                    ),
                )
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

// ------------------------------------------------------------------ İstekler

@Composable
private fun RequestsTab(vm: AppViewModel, month: YearMonth) {
    val data by rememberData(vm)
    var editing by remember { mutableStateOf<DutyRequest?>(null) }
    var creating by remember { mutableStateOf(false) }
    val list = data.requests.filter { it.touches(month) }.sortedBy { data.person(it.personId)?.seniority ?: 99 }

    Text("WhatsApp'taki istekleri buraya girin. Kesin istekler kural gibi uygulanır.", style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { creating = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("İstek ekle") }
    if (list.isEmpty()) EmptyNote("Bu ay için istek yok")
    list.forEach { r ->
        val person = data.person(r.personId)
        ItemCard(
            person = person,
            title = "${person?.name ?: "?"}: ${RequestText.describe(r)}",
            lines = listOfNotNull(r.priority.label, r.originalText?.let { "“$it”" }),
            onEdit = { editing = r },
            onDelete = { vm.update("İstek silindi") { DataOps.deleteRequest(it, r.id) } },
        )
    }
    if (creating || editing != null) {
        RequestDialog(
            persons = data.persons.filter { it.leaveDate == null || !it.leaveDate!!.isBefore(month.atDay(1)) },
            initial = editing,
            month = month,
            onDismiss = { creating = false; editing = null },
            onSave = { r -> vm.update("Kaydedildi") { DataOps.upsertRequest(it, r) }; creating = false; editing = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RequestDialog(persons: List<Person>, initial: DutyRequest?, month: YearMonth, onDismiss: () -> Unit, onSave: (DutyRequest) -> Unit) {
    var personId by remember { mutableStateOf(initial?.personId) }
    var type by remember { mutableStateOf(initial?.type ?: RequestType.AVOID_DATES) }
    var priority by remember { mutableStateOf(initial?.priority ?: Priority.MUMKUNSE) }
    var dates by remember { mutableStateOf(initial?.dates.orEmpty()) }
    var start by remember { mutableStateOf(initial?.rangeStart ?: month.atDay(1)) }
    var end by remember { mutableStateOf(initial?.rangeEnd ?: month.atDay(7)) }
    var maxPerWeek by remember { mutableIntStateOf(initial?.maxPerWeek ?: 1) }
    var pickingEnd by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(initial?.originalText.orEmpty()) }
    val usesDates = type == RequestType.AVOID_DATES || type == RequestType.WANT_DATES
    val valid = personId != null && (if (usesDates) dates.isNotEmpty() else !end.isBefore(start))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "İstek ekle" else "İsteği düzenle") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PersonField("Kişi", persons, personId, { personId = it })
                ChoiceField("Tür", RequestType.entries, type, { it.label }, { type = it })
                ChoiceField("Öncelik", Priority.entries, priority, { it.label }, { priority = it })
                if (usesDates) {
                    Text("Günlere dokunarak seçin; tekrar dokununca kalkar.", style = MaterialTheme.typography.bodySmall)
                    MonthDayPicker(month, selected = { it in dates }, onTap = { d -> dates = if (d in dates) dates - d else dates + d })
                    Text(
                        if (dates.isEmpty()) "Gün seçilmedi" else "Seçilen: " + dates.sorted().joinToString(", ") { it.short() },
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text(
                        if (pickingEnd) "Bitiş gününe dokunun" else "Başlangıç gününe, sonra bitiş gününe dokunun",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    MonthDayPicker(
                        month,
                        selected = { it == start || it == end },
                        inRange = { it.isAfter(start) && it.isBefore(end) },
                        onTap = { d ->
                            if (!pickingEnd || d.isBefore(start)) {
                                start = d; end = d; pickingEnd = true
                            } else {
                                end = d; pickingEnd = false
                            }
                        },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (start == end) start.short() else "${start.short()} – ${end.short()}",
                            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                        )
                        TextButton(onClick = { start = month.atDay(1); end = month.atEndOfMonth(); pickingEnd = false }) { Text("Bütün ay") }
                    }
                    if (type == RequestType.WEEKLY_MAX) {
                        Stepper("Haftada en fazla", maxPerWeek.toDouble(), 1.0, 1.0, 3.0, { it.toInt().toString() }) { maxPerWeek = it.toInt() }
                    }
                }
                OutlinedTextField(text, { text = it }, label = { Text("Mesajın kendisi (isteğe bağlı)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(
                    DutyRequest(
                        id = initial?.id ?: DataOps.newId("i"), personId = personId!!, type = type, priority = priority,
                        dates = if (usesDates) dates.sorted() else emptyList(),
                        rangeStart = if (usesDates) null else start, rangeEnd = if (usesDates) null else end,
                        maxPerWeek = if (type == RequestType.WEEKLY_MAX) maxPerWeek else null,
                        originalText = text.ifBlank { null },
                    ),
                )
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

// ------------------------------------------------------------------ Nöbet sayıları

@Composable
private fun QuotaTab(vm: AppViewModel, month: YearMonth, editable: Boolean, published: Boolean) {
    val data by rememberData(vm)
    LaunchedEffect(data.periods, data.persons) { if (editable) vm.update { DataOps.refreshQuotas(it, month) } }
    val plan = data.month(month) ?: return
    val total = plan.quotas.sumOf { it.final }
    val days = month.lengthOfMonth()
    // İlk kez sabitlenen kişi: hoca kararı mı, bizim değişikliğimiz mi?
    var asking by remember { mutableStateOf<Pair<Person, Int>?>(null) }
    fun change(p: Person, q: QuotaEntry, value: Int) {
        if (q.manual) vm.update { DataOps.setQuota(it, month, p.id, value) } else asking = p to value
    }
    Text(
        "Uygulama kıdem katsayılarına ve önceki aylardaki sayı dengesine göre önerir. Aynı ay içinde kimse kendinden kıdemliden az tutmaz; " +
            "artan nöbet önce en çömeze gider. Birinin sayısını değiştirdiğinizde o kişi sabitlenir, kalan günler diğerlerine aynı kurallarla yeniden dağıtılır.",
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        "Hoca kararı: o kişiye borç ya da alacak yazılmaz; artan ya da eksilen günler diğerlerine paylaştırılır ve sonraki aylarda dengelenir. " +
            "Sayıyı kendiniz değiştirdiyseniz \"Hoca kararı\"nı kapatın; fark sonraki aylarda o kişiye geri yansır.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    asking?.let { (p, value) ->
        val old = plan.quotas.firstOrNull { it.personId == p.id }?.final ?: 0
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text("Hoca kararı mı?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${p.name}: $old → $value")
                    Text("Hoca kararı: bu kişiye borç ya da alacak yazılmaz; fark diğerlerine dağılır ve sonraki aylarda onlar arasında dengelenir.", style = MaterialTheme.typography.bodySmall)
                    Text("Biz değiştirdik: fark sonraki aylarda bu kişiye geri yansır.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { asking = null; vm.update { DataOps.setQuota(it, month, p.id, value, decision = false) } }) { Text("Biz değiştirdik") }
                    TextButton(onClick = { asking = null; vm.update { DataOps.setQuota(it, month, p.id, value, decision = true) } }) { Text("Hoca kararı") }
                }
            },
            dismissButton = { TextButton(onClick = { asking = null }) { Text("Vazgeç") } },
        )
    }
    if (published) {
        Text(
            "Liste kesinleşti. Sayıyı değiştirdikten sonra takvimde \"Kalan günleri yeniden oluştur\"a basın; geçmiş günler korunur.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
        )
    }
    SectionCard {
        plan.quotas.mapNotNull { q -> data.person(q.personId)?.let { it to q } }.sortedBy { it.first.seniority }.forEach { (p, q) ->
            Row(Modifier.semantics { isTraversalGroup = true }, verticalAlignment = Alignment.CenterVertically) {
                PersonDot(p)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontWeight = FontWeight.SemiBold)
                    val carry = when {
                        q.carryIn >= 0.05 -> " · önceki aylardan eksik ${fmt1(q.carryIn)}"
                        q.carryIn <= -0.05 -> " · önceki aylardan fazla ${fmt1(-q.carryIn)}"
                        else -> ""
                    }
                    val detail = when {
                        q.manual && q.decision -> "Hoca kararı · öneri ${q.suggested}"
                        q.manual -> "Elle verildi · öneri ${q.suggested}"
                        else -> "Önerilen$carry"
                    }
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = if (q.manual) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (q.manual && editable) {
                        FilterChip(
                            selected = q.decision,
                            onClick = { vm.update { DataOps.setQuotaDecision(it, month, p.id, !q.decision) } },
                            label = { Text("Hoca kararı") },
                        )
                    }
                }
                if (q.manual && editable) {
                    IconButton(onClick = { vm.update { DataOps.releaseQuota(it, month, p.id) } }) { Icon(Icons.Default.Lock, "Sabitlemeyi kaldır", tint = MaterialTheme.colorScheme.primary) }
                }
                IconButton(enabled = editable && q.final > 0, onClick = { change(p, q, q.final - 1) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
                Text("${q.final}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(enabled = editable, onClick = { change(p, q, q.final + 1) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
        }
    }
    val color = if (total == days) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Text("Toplam $total / $days gün", color = color, fontWeight = FontWeight.SemiBold)
    if (editable) OutlinedButton(onClick = { vm.update("Önerilen sayılara dönüldü") { DataOps.resetQuotas(it, month) } }) { Text("Önerilen sayılara dön") }
}

private fun fmt1(v: Double): String = String.format(java.util.Locale.ROOT, "%.1f", v).replace('.', ',')

// ------------------------------------------------------------------ Kontrol

@Composable
private fun CheckTab(vm: AppViewModel, month: YearMonth) {
    val data by rememberData(vm)
    var issues by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(data, month) {
        issues = null
        issues = ConflictDetector.detect(vm.problem(month))
    }
    Text("Liste oluşturmadan önce çakışan istekler ve doldurulamayacak günler.", style = MaterialTheme.typography.bodySmall)
    val list = issues
    when {
        list == null -> CircularProgressIndicator()
        list.isEmpty() -> SectionCard { Text("Sorun görünmüyor ✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
        else -> list.forEach { SectionCard { Text("⚠ $it") } }
    }
}

// ------------------------------------------------------------------ Ortak

@Composable
fun ItemCard(person: Person?, title: String, lines: List<String>, onEdit: () -> Unit, onDelete: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (person != null) { PersonDot(person); Spacer(Modifier.width(10.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Düzenle") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Sil") }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    // Satırın tamamı dokunulabilir; ekran okuyucu da etiketi ve anahtarı tek öğe olarak okur.
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, enabled = enabled, onCheckedChange = null)
    }
}

@Composable
fun Stepper(label: String, value: Double, step: Double, min: Double, max: Double, format: (Double) -> String, onChange: (Double) -> Unit) {
    Row(Modifier.semantics { isTraversalGroup = true }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        IconButton(enabled = value - step >= min - 1e-9, onClick = { onChange(((value - step) * 1000).let { Math.round(it) / 1000.0 }) }) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(format(value), fontWeight = FontWeight.Bold)
        IconButton(enabled = value + step <= max + 1e-9, onClick = { onChange(((value + step) * 1000).let { Math.round(it) / 1000.0 }) }) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}
