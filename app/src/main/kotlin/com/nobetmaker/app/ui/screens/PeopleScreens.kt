package com.nobetmaker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobetmaker.app.ui.AppViewModel
import com.nobetmaker.app.ui.DateField
import com.nobetmaker.app.ui.Nav
import com.nobetmaker.app.ui.PersonDot
import com.nobetmaker.app.ui.SectionCard
import com.nobetmaker.app.ui.personColor
import com.nobetmaker.app.ui.rememberData
import com.nobetmaker.core.calendar.trFormat
import com.nobetmaker.core.model.CoefficientPeriod
import com.nobetmaker.core.model.Person
import com.nobetmaker.core.model.PersonPalette
import com.nobetmaker.core.model.PersonRule
import com.nobetmaker.core.model.RuleType
import com.nobetmaker.core.ops.DataOps
import java.time.LocalDate
import java.util.Locale

@Composable
fun PeopleScreen(vm: AppViewModel, nav: Nav) {
    val data by rememberData(vm)
    val today = LocalDate.now()
    val active = data.persons.filter { it.leaveDate == null || !it.leaveDate!!.isBefore(today) }.sortedBy { it.seniority }
    val left = data.persons.filter { it.leaveDate != null && it.leaveDate!!.isBefore(today) }.sortedBy { it.seniority }

    Scaffold(floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { nav.person(null) }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Kişi ekle") })
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Kişiler", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Kıdem sırası (en kıdemli üstte). Okla sırayı değiştirebilirsiniz.", style = MaterialTheme.typography.bodySmall)
            active.forEachIndexed { i, p ->
                PersonCard(
                    p,
                    onClick = { nav.person(p.id) },
                    onUp = if (i > 0) ({ move(vm, active, i, -1) }) else null,
                    onDown = if (i < active.lastIndex) ({ move(vm, active, i, +1) }) else null,
                )
            }
            if (left.isNotEmpty()) {
                Text("Ayrılanlar", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                left.forEach { PersonCard(it, onClick = { nav.person(it.id) }, onUp = null, onDown = null) }
            }
            Spacer(Modifier.padding(36.dp))
        }
    }
}

private fun move(vm: AppViewModel, list: List<Person>, index: Int, delta: Int) {
    val ids = list.map { it.id }.toMutableList()
    val id = ids.removeAt(index)
    ids.add(index + delta, id)
    vm.update { data -> DataOps.reorderSeniority(data, ids + data.persons.map { it.id }.filterNot { it in ids }) }
}

@Composable
private fun PersonCard(p: Person, onClick: () -> Unit, onUp: (() -> Unit)?, onDown: (() -> Unit)?) {
    val c = p.coefficientOn(LocalDate.now())
    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PersonDot(p, 18)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${p.seniority}. ${p.name}", fontWeight = FontWeight.SemiBold)
                Text(
                    "Nöbet ${fmt(c.duty)} · Zor gün ${fmt(c.hardDay)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                val extra = listOfNotNull(
                    p.joinDate?.let { "Bölüme katılış ${it.trFormat()}" },
                    p.dutyStart?.let { "Nöbete katılış ${it.trFormat()}" },
                    p.leaveDate?.let { "Ayrılış ${it.trFormat()}" },
                    p.rules.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.type.label },
                )
                extra.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Column {
                IconButton(enabled = onUp != null, onClick = { onUp?.invoke() }) { Icon(Icons.Default.KeyboardArrowUp, "Yukarı") }
                IconButton(enabled = onDown != null, onClick = { onDown?.invoke() }) { Icon(Icons.Default.KeyboardArrowDown, "Aşağı") }
            }
        }
    }
}

private fun fmt(v: Double) = String.format(Locale.ROOT, "%.2f", v)

private val palette = PersonPalette.colors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonEditScreen(vm: AppViewModel, nav: Nav, id: String?) {
    val data by rememberData(vm)
    val existing = id?.let { data.person(it) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var color by remember { mutableStateOf(existing?.color ?: (palette.firstOrNull { c -> data.persons.none { it.color == c } } ?: palette.first())) }
    var joinDate by remember { mutableStateOf(existing?.joinDate) }
    var leaveDate by remember { mutableStateOf(existing?.leaveDate) }
    var residency by remember { mutableStateOf(existing?.residencyStart) }
    var dutyStart by remember { mutableStateOf(existing?.dutyStart) }
    var coefficients by remember { mutableStateOf(existing?.coefficients ?: listOf(CoefficientPeriod(from = LocalDate.now().withDayOfMonth(1), duty = 0.6, hardDay = 0.6))) }
    var rules by remember { mutableStateOf(existing?.rules.orEmpty()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Yeni kişi" else existing.name) },
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                actions = {
                    if (existing != null) IconButton(onClick = {
                        vm.update("Silindi") { DataOps.deletePerson(it, existing.id) }
                        nav.back()
                    }) { Icon(Icons.Default.Delete, "Sil") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Ad") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            SectionCard("Renk") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    palette.forEach { c ->
                        Box(
                            Modifier.size(36.dp).background(personColor(c), CircleShape)
                                .border(if (c == color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { color = c },
                        )
                    }
                }
            }
            SectionCard("Tarihler") {
                // Asistanlık başlangıcı ile bölüme katılış varsayılan olarak aynıdır; biri girilince boş ya da eşit olan diğeri de değişir.
                DateField("Asistanlık başlangıcı", residency, { d ->
                    if (joinDate == null || joinDate == residency) joinDate = d
                    residency = d
                }, Modifier.fillMaxWidth(), allowClear = true)
                DateField("Bölüme katılış", joinDate, { d ->
                    if (residency == null || residency == joinDate) residency = d
                    joinDate = d
                }, Modifier.fillMaxWidth(), allowClear = true)
                DateField("Nöbete katılış", dutyStart, { dutyStart = it }, Modifier.fillMaxWidth(), allowClear = true)
                DateField("Ayrılış", leaveDate, { leaveDate = it }, Modifier.fillMaxWidth(), allowClear = true)
                Text(
                    "Nöbete katılış boşsa bölüme katılış esas alınır. Tarihler boşsa adalet hesabı kişinin (ya da kendisinden çömez birinin) ilk kayıtlı nöbetini başlangıç sayar. " +
                        "Kıdem sırası tarihlerden hesaplanmaz; Kişiler ekranından değiştirilir.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            SectionCard("Katsayılar") {
                Text("Nöbet katsayısı kaç nöbet tutacağını, zor gün katsayısı Cumartesi/Cuma/Pazar/bayram payını belirler.", style = MaterialTheme.typography.bodySmall)
                coefficients.sortedBy { it.from ?: LocalDate.MIN }.forEach { c ->
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DateField("Geçerli", c.from, { d -> coefficients = coefficients.map { if (it === c) it.copy(from = d) else it } }, Modifier.weight(1f), allowClear = true)
                                if (coefficients.size > 1) IconButton(onClick = { coefficients = coefficients.filterNot { it === c } }) { Icon(Icons.Default.Delete, "Sil") }
                            }
                            Stepper("Nöbet", c.duty, 0.01, 0.0, 1.5, ::fmt) { v -> coefficients = coefficients.map { if (it === c) it.copy(duty = v) else it } }
                            Stepper("Zor gün", c.hardDay, 0.01, 0.0, 1.5, ::fmt) { v -> coefficients = coefficients.map { if (it === c) it.copy(hardDay = v) else it } }
                        }
                    }
                }
                TextButton(onClick = {
                    val last = coefficients.maxByOrNull { it.from ?: LocalDate.MIN } ?: CoefficientPeriod()
                    coefficients = coefficients + last.copy(from = LocalDate.now().withDayOfMonth(1).plusMonths(1))
                }) { Text("Yeni dönem katsayısı ekle") }
            }
            SectionCard("Kişiye özel kurallar") {
                RuleType.entries.forEach { type ->
                    val rule = rules.firstOrNull { it.type == type }
                    SwitchRow(type.label, rule != null) { on ->
                        rules = if (on) rules + PersonRule(type, from = LocalDate.now()) else rules.filterNot { it.type == type }
                    }
                    if (rule != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DateField("Başlangıç", rule.from, { d -> rules = rules.map { if (it.type == type) it.copy(from = d) else it } }, Modifier.weight(1f), allowClear = true)
                            DateField("Bitiş", rule.to, { d -> rules = rules.map { if (it.type == type) it.copy(to = d) else it } }, Modifier.weight(1f), allowClear = true)
                        }
                    }
                }
            }
            Button(
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val person = Person(
                        id = existing?.id ?: DataOps.newId("k"),
                        name = name.trim(),
                        seniority = existing?.seniority ?: ((data.persons.maxOfOrNull { it.seniority } ?: 0) + 1),
                        color = color,
                        joinDate = joinDate,
                        leaveDate = leaveDate,
                        residencyStart = residency,
                        dutyStart = dutyStart,
                        coefficients = coefficients,
                        rules = rules,
                    )
                    vm.update("Kaydedildi") { DataOps.upsertPerson(it, person) }
                    nav.back()
                },
            ) { Text("Kaydet") }
            Spacer(Modifier.padding(16.dp))
        }
    }
}

