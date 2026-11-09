package com.nobetmaker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobetmaker.app.export.Exporter
import com.nobetmaker.app.ui.AppViewModel
import com.nobetmaker.app.ui.rememberData
import com.nobetmaker.app.ui.short
import com.nobetmaker.core.ops.DataOps
import com.nobetmaker.core.rules.RosterIndex
import com.nobetmaker.core.swap.SwapAssistant
import com.nobetmaker.core.swap.SwapOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapSheet(vm: AppViewModel, personId: String, date: LocalDate, onDismiss: () -> Unit) {
    val data by rememberData(vm)
    var options by remember { mutableStateOf<List<SwapOption>?>(null) }
    LaunchedEffect(personId, date) {
        options = withContext(Dispatchers.Default) { SwapAssistant(data).options(personId, date) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "${data.person(personId)?.name} ${date.short()} nöbetini veriyor",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            )
            Text(
                "Değişimler gayriresmi kaydedilir; resmi liste ve adalet defteri değişmez.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val list = options
            if (list == null) CircularProgressIndicator()
            else if (list.isEmpty()) Text("Seçenek bulunamadı")
            else list.forEach { o ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(o.title, fontWeight = FontWeight.SemiBold)
                        if (o.warnings.isEmpty()) Text("Kurallara uygun", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                        o.warnings.forEach { Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(onClick = {
                                val group = DataOps.newId("takas")
                                vm.update("Değişim kaydedildi") { d ->
                                    val roster = RosterIndex(d)
                                    o.steps.fold(d) { acc, s ->
                                        DataOps.addInformal(acc, s.date, roster.official[s.date] ?: s.fromId, s.toId, group, "Değişim asistanı")
                                    }
                                }
                                onDismiss()
                            }) { Text("Uygula") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(vm: AppViewModel, month: YearMonth, onDismiss: () -> Unit) {
    val data by rememberData(vm)
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Paylaş", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            fun run(block: () -> Unit) {
                runCatching(block).onFailure { vm.toast(it.message ?: "Dışa aktarılamadı") }
                onDismiss()
            }
            Button(onClick = { run { Exporter.shareExcel(context, data, month, actual = false) } }, Modifier.fillMaxWidth()) { Text("Excel – resmi liste (hocaya)") }
            OutlinedButton(onClick = { run { Exporter.shareImage(context, data, month, actual = false) } }, Modifier.fillMaxWidth()) { Text("Görsel – resmi liste (WhatsApp)") }
            OutlinedButton(onClick = { run { Exporter.shareImage(context, data, month, actual = true) } }, Modifier.fillMaxWidth()) { Text("Görsel – gerçekleşen") }
            OutlinedButton(onClick = { run { Exporter.sharePdf(context, data, month, actual = false) } }, Modifier.fillMaxWidth()) { Text("PDF – resmi liste (çıktı için)") }
            OutlinedButton(onClick = { run { Exporter.shareExcel(context, data, month, actual = true) } }, Modifier.fillMaxWidth()) { Text("Excel – gerçekleşen") }
        }
    }
}
