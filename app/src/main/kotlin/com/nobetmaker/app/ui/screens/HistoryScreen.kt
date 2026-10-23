package com.nobetmaker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nobetmaker.app.ui.AppViewModel
import com.nobetmaker.app.ui.ChoiceField
import com.nobetmaker.app.ui.DateField
import com.nobetmaker.app.ui.EmptyNote
import com.nobetmaker.app.ui.Nav
import com.nobetmaker.app.ui.rememberData
import com.nobetmaker.core.calendar.trFormat
import com.nobetmaker.core.model.EventKind
import com.nobetmaker.core.model.HistoryEntry
import com.nobetmaker.core.model.HistoryEvent
import com.nobetmaker.core.model.Person
import com.nobetmaker.core.ops.DataOps
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: AppViewModel, nav: Nav) {
    val data by rememberData(vm)
    var editing by remember { mutableStateOf<HistoryEvent?>(null) }
    var creating by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Geçmiş özel günler") },
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Tam listesi olmayan dönemlerin (2023 – Ekim 2025) bayram, yılbaşı ve resmi tatil nöbetleri. Yalnızca bugünkü ekibin günlerini girin; kalanları uzmanlar ya da ayrılanlar tutmuş olabilir.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = { creating = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Kayıt ekle") }
            val list = data.historyEvents.sortedByDescending { it.start ?: LocalDate.MIN }
            if (list.isEmpty()) EmptyNote("Kayıt yok")
            list.forEach { e ->
                ItemCard(
                    person = null,
                    title = e.name,
                    lines = listOfNotNull(
                        "${e.kind.label} · ${e.totalDays} gün" + (e.start?.let { " · ${it.trFormat()}" } ?: ""),
                        e.entries.joinToString(", ") { en -> "${data.person(en.personId)?.name ?: en.personId} ${en.days}" }.ifBlank { null },
                        e.note,
                    ),
                    onEdit = { editing = e },
                    onDelete = { vm.update("Silindi") { DataOps.deleteHistory(it, e.id) } },
                )
            }
        }
    }
    if (creating || editing != null) {
        HistoryDialog(
            persons = data.persons.sortedBy { it.seniority },
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { e -> vm.update("Kaydedildi") { DataOps.upsertHistory(it, e) }; creating = false; editing = null },
        )
    }
}

@Composable
private fun HistoryDialog(persons: List<Person>, initial: HistoryEvent?, onDismiss: () -> Unit, onSave: (HistoryEvent) -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var kind by remember { mutableStateOf(initial?.kind ?: EventKind.BAYRAM) }
    var start by remember { mutableStateOf(initial?.start) }
    var total by remember { mutableIntStateOf(initial?.totalDays ?: 4) }
    var days by remember { mutableStateOf<Map<String, Int>>(initial?.entries?.associate { it.personId to it.days } ?: emptyMap()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Kayıt ekle" else "Kaydı düzenle") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Ad (ör. 2024 Kurban Bayramı)") }, modifier = Modifier.fillMaxWidth())
                ChoiceField("Tür", EventKind.entries, kind, { it.label }, { kind = it })
                DateField("Başlangıç", start, { start = it })
                Stepper("Toplam gün", total.toDouble(), 1.0, 1.0, 12.0, { it.toInt().toString() }) { total = it.toInt() }
                Text("Kim kaç gün tuttu?", style = MaterialTheme.typography.labelLarge)
                persons.forEach { p ->
                    Stepper(p.name, (days[p.id] ?: 0).toDouble(), 1.0, 0.0, 12.0, { it.toInt().toString() }) { v ->
                        days = days + (p.id to v.toInt())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && start != null, onClick = {
                onSave(
                    HistoryEvent(
                        id = initial?.id ?: DataOps.newId("h"), name = name.trim(), kind = kind, start = start, end = start,
                        totalDays = total, entries = days.filterValues { it > 0 }.map { (id, n) -> HistoryEntry(id, n) },
                    ),
                )
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
