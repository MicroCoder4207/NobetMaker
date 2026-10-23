package com.nobetmaker.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobetmaker.app.ui.AppViewModel
import com.nobetmaker.app.ui.DateField
import com.nobetmaker.app.ui.Nav
import com.nobetmaker.app.ui.SectionCard
import com.nobetmaker.app.ui.rememberData
import com.nobetmaker.core.model.Settings
import com.nobetmaker.core.ops.DataOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.util.Locale

@Composable
fun SettingsScreen(vm: AppViewModel, nav: Nav) {
    val data by rememberData(vm)
    val s = data.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var showLog by remember { mutableStateOf(false) }
    var showBackups by remember { mutableStateOf(false) }
    fun set(transform: (Settings) -> Settings) = vm.update { DataOps.updateSettings(it, transform(it.settings)) }
    val one = { v: Double -> String.format(Locale.ROOT, "%.1f", v) }
    val int = { v: Double -> v.toInt().toString() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            vm.repository.exportTo(context, uri).onSuccess { vm.toast("Yedek kaydedildi") }.onFailure { vm.toast(it.message ?: "Kaydedilemedi") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirm = "Yedek yüklensin mi? Mevcut veri yedektekiyle değiştirilir (önce otomatik yedek alınır)." to {
            scope.launch(Dispatchers.IO) {
                vm.repository.importFrom(context, uri).onSuccess { vm.toast("Yedek yüklendi") }.onFailure { vm.toast(it.message ?: "Yüklenemedi") }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ayarlar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SectionCard("Kurallar") {
            Stepper("İki nöbet arası en az gün", s.minGap.toDouble(), 1.0, 2.0, 6.0, int) { v -> set { it.copy(minGap = v.toInt()) } }
            Text("3 = art arda ve günaşırı yasak", style = MaterialTheme.typography.bodySmall)
            Stepper("Aylık en fazla nöbet", s.maxPerMonth.toDouble(), 1.0, 3.0, 10.0, int) { v -> set { it.copy(maxPerMonth = v.toInt()) } }
            Stepper("Sıra hafızası (ay)", s.rotationMonths.toDouble(), 1.0, 1.0, 12.0, int) { v -> set { it.copy(rotationMonths = v.toInt()) } }
            DateField("Uzun hafıza başlangıcı", s.longMemoryStart, { d -> d?.let { date -> set { it.copy(longMemoryStart = date) } } }, Modifier.fillMaxWidth())
            var title by remember(s.excelTitle) { mutableStateOf(s.excelTitle) }
            OutlinedTextField(
                title, { title = it }, label = { Text("Excel/görsel başlığı") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            if (title != s.excelTitle) TextButton(onClick = { set { it.copy(excelTitle = title) } }) { Text("Başlığı kaydet") }
        }

        SectionCard("Zorluk puanı") {
            val p = s.points
            Stepper("Temel", p.base, 0.1, 0.0, 5.0, one) { v -> set { it.copy(points = p.copy(base = v)) } }
            Stepper("Nöbet günü tatil", p.holiday, 0.1, 0.0, 5.0, one) { v -> set { it.copy(points = p.copy(holiday = v)) } }
            Stepper("Vizit", p.visit, 0.1, 0.0, 5.0, one) { v -> set { it.copy(points = p.copy(visit = v)) } }
            Stepper("Ertesi gün tatil", p.nextDayHoliday, 0.1, 0.0, 5.0, one) { v -> set { it.copy(points = p.copy(nextDayHoliday = v)) } }
            Stepper("Uzun hafta sonu indirimi", p.longWeekendBonus, 0.1, 0.0, 2.0, one) { v -> set { it.copy(points = p.copy(longWeekendBonus = v)) } }
        }

        SectionCard("Nöbet saati") {
            val h = s.hours
            Stepper("İş günü nöbeti", h.workday.toDouble(), 1.0, 8.0, 24.0, int) { v -> set { it.copy(hours = h.copy(workday = v.toInt())) } }
            Stepper("Tatil günü nöbeti", h.holiday.toDouble(), 1.0, 8.0, 24.0, int) { v -> set { it.copy(hours = h.copy(holiday = v.toInt())) } }
            Stepper("Arefe nöbeti", h.arefe.toDouble(), 1.0, 8.0, 24.0, int) { v -> set { it.copy(hours = h.copy(arefe = v.toInt())) } }
            Stepper("Nöbet ertesi düşüm", h.postDutyDeduction.toDouble(), 1.0, 0.0, 12.0, int) { v -> set { it.copy(hours = h.copy(postDutyDeduction = v.toInt())) } }
        }

        SectionCard("Ceza ağırlıkları") {
            val w = s.penalties
            Stepper("Uzun tatil koruması", w.longHolidayProtection, 5.0, 0.0, 300.0, int) { v -> set { it.copy(penalties = w.copy(longHolidayProtection = v)) } }
            Stepper("\"Mümkünse\" istek", w.requestMumkunse, 5.0, 0.0, 300.0, int) { v -> set { it.copy(penalties = w.copy(requestMumkunse = v)) } }
            Stepper("\"Önemsiz\" istek", w.requestOnemsiz, 5.0, 0.0, 300.0, int) { v -> set { it.copy(penalties = w.copy(requestOnemsiz = v)) } }
            Stepper("Ayda 2. Cuma/Cumartesi/Pazar", w.weekendRepeat, 25.0, 0.0, 2000.0, int) { v -> set { it.copy(penalties = w.copy(weekendRepeat = v)) } }
            Stepper("Vizit/Cuma/Pazar/Perşembe sırası", w.rotation, 5.0, 0.0, 300.0, int) { v -> set { it.copy(penalties = w.copy(rotation = v)) } }
            Stepper("Pazartesi sırası (Salı dersi)", w.mondayRotation, 1.0, 0.0, 100.0, int) { v -> set { it.copy(penalties = w.copy(mondayRotation = v)) } }
            Stepper("Uzun hafıza dengesi", w.longMemory, 5.0, 0.0, 300.0, int) { v -> set { it.copy(penalties = w.copy(longMemory = v)) } }
            Stepper("3 gün aralık", w.gap3, 5.0, 0.0, 300.0, int) { v -> set { it.copy(penalties = w.copy(gap3 = v)) } }
            Stepper("4 gün aralık", w.gap4, 1.0, 0.0, 300.0, int) { v -> set { it.copy(penalties = w.copy(gap4 = v)) } }
            Stepper("Pzt–Per aynı güne 2. kez", w.weekdayRepeat, 1.0, 0.0, 100.0, int) { v -> set { it.copy(penalties = w.copy(weekdayRepeat = v)) } }
            Stepper("Aynı ikili sıranın tekrarı", w.sequenceRepeat, 1.0, 0.0, 100.0, int) { v -> set { it.copy(penalties = w.copy(sequenceRepeat = v)) } }
            Stepper("Aynı bayramda 2. gün", w.sameBayram, 5.0, 0.0, 300.0, int) { v -> set { it.copy(penalties = w.copy(sameBayram = v)) } }
            TextButton(onClick = { set { it.copy(points = Settings().points, hours = Settings().hours, penalties = Settings().penalties) } }) {
                Text("Puan, saat ve cezaları varsayılana döndür")
            }
        }

        SectionCard("Veri") {
            OutlinedButton(onClick = { exportLauncher.launch("nobetmaker-yedek-${LocalDate.now()}.json") }, Modifier.fillMaxWidth()) { Text("Yedeği dışa aktar (Drive'a, e-postaya…)") }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }, Modifier.fillMaxWidth()) { Text("Yedekten geri yükle") }
            OutlinedButton(onClick = { showBackups = true }, Modifier.fillMaxWidth()) { Text("Otomatik yedekler") }
            OutlinedButton(onClick = { showLog = true }, Modifier.fillMaxWidth()) { Text("Değişiklik geçmişi") }
            OutlinedButton(onClick = {
                confirm = "Başlangıç verisi (Kasım 2025 – Ekim 2026 geçmişi) yeniden yüklensin mi? Bütün değişiklikler silinir (önce otomatik yedek alınır)." to {
                    scope.launch(Dispatchers.IO) { vm.repository.resetToSeed().onSuccess { vm.toast("Başlangıç verisi yüklendi") } }
                }
            }, Modifier.fillMaxWidth()) { Text("Başlangıç verisine dön") }
        }
        Text("NöbetMaker 0.2.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.padding(16.dp))
    }

    confirm?.let { (text, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { action(); confirm = null }) { Text("Evet") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Vazgeç") } },
        )
    }
    if (showLog) {
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text("Değişiklik geçmişi") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    data.changeLog.takeLast(60).reversed().forEach {
                        Text("${it.at.replace('T', ' ')} · ${it.action}${if (it.detail.isNotBlank()) ": ${it.detail}" else ""}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (data.changeLog.isEmpty()) Text("Henüz değişiklik yok")
                }
            },
            confirmButton = { TextButton(onClick = { showLog = false }) { Text("Kapat") } },
        )
    }
    if (showBackups) {
        val backups = remember { vm.repository.backups() }
        AlertDialog(
            onDismissRequest = { showBackups = false },
            title = { Text("Otomatik yedekler") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (backups.isEmpty()) Text("Henüz otomatik yedek yok")
                    backups.forEach { f: File ->
                        TextButton(onClick = {
                            showBackups = false
                            confirm = "${f.name} geri yüklensin mi?" to {
                                scope.launch(Dispatchers.IO) { vm.repository.restoreBackup(f).onSuccess { vm.toast("Geri yüklendi") } }
                            }
                        }) { Text(f.name.removePrefix("yedek-").removeSuffix(".json")) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBackups = false }) { Text("Kapat") } },
        )
    }
}
