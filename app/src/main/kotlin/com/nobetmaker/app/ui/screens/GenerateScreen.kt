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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobetmaker.app.ui.AppViewModel
import com.nobetmaker.app.ui.CalendarGrid
import com.nobetmaker.app.ui.Nav
import com.nobetmaker.app.ui.PersonDot
import com.nobetmaker.app.ui.SectionCard
import com.nobetmaker.app.ui.gridFromSolution
import com.nobetmaker.app.ui.rememberData
import com.nobetmaker.app.ui.title
import com.nobetmaker.core.calendar.Category
import com.nobetmaker.core.solver.Explainer
import com.nobetmaker.core.solver.MonthProblem
import com.nobetmaker.core.solver.Solution
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateScreen(vm: AppViewModel, nav: Nav, month: YearMonth) {
    val gen by vm.generation.collectAsStateWithLifecycle()
    LaunchedEffect(month) {
        if (vm.generationStale(month)) vm.generate(month)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${month.title()} seçenekleri") },
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val g = gen
            when {
                g == null || g.month != month || g.running -> Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.padding(8.dp))
                    Text("Kurallara uygun listeler aranıyor…")
                }
                g.error != null -> SectionCard("Hata") { Text(g.error) }
                g.result == null || g.problem == null -> {}
                g.result.solutions.isEmpty() -> {
                    val d = g.result.diagnostics
                    SectionCard("Bu kurallarla liste çıkmıyor") {
                        d?.problems?.forEach { Text("• $it") }
                        if (!d?.suggestions.isNullOrEmpty()) {
                            Text("Öneriler", fontWeight = FontWeight.SemiBold)
                            d?.suggestions?.forEach { Text("→ $it") }
                        }
                        Text("Art arda ve günaşırı kuralı hiçbir zaman gevşetilmez.", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { nav.prepare(month) }) { Text("Ayı hazırla ekranına dön") }
                }
                else -> {
                    Text(
                        "Kilitli günler korundu. Seçtiğiniz liste taslak olarak kaydedilir; sonra elle düzeltebilirsiniz.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val best = g.result.solutions.first().cost
                    g.result.solutions.forEachIndexed { i, s ->
                        AlternativeCard(vm, g.problem, s, i, s.cost - best) { vm.applyAlternative(i) { nav.calendar() } }
                    }
                    OutlinedButton(onClick = { vm.generate(month, g.seedOffset + 1) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Farklı seçenekler üret")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlternativeCard(vm: AppViewModel, pr: MonthProblem, solution: Solution, index: Int, extraPenalty: Double, onUse: () -> Unit) {
    val data by rememberData(vm)
    val card = remember(solution) { Explainer(pr).scoreCard(solution.assignment) }
    var expanded by remember { mutableStateOf(index == 0) }
    SectionCard(if (index == 0) "Seçenek 1 · en dengeli" else "Seçenek ${index + 1}") {
        val met = card.requestsTotal - card.requestsUnmet.size
        if (index > 0) Text("Seçenek 1'e göre ${"%.0f".format(extraPenalty)} ceza puanı fazla", style = MaterialTheme.typography.bodySmall)
        if (card.requestsTotal > 0) Text("İstekler: $met / ${card.requestsTotal} karşılandı") else Text("Bu ay istek yok", style = MaterialTheme.typography.bodySmall)
        card.requestsUnmet.forEach { Text("✗ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        card.weekendRepeats.forEach { Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        card.longHolidayHits.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall) }
        Text("3 gün aralıklı nöbet: ${card.tightGaps.size}", style = MaterialTheme.typography.bodySmall)
        card.rotationSummary.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        card.rotationMisses.forEach { Text("↺ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (card.sequenceRepeats.isNotEmpty()) {
            Text(
                "↻ Tekrarlanan ikili: " + card.sequenceRepeats.joinToString("; ") { it.removePrefix("Sıra tekrarı: ") },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        card.memoryNotes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            card.perPerson.filter { it.count > 0 }.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PersonDot(line.person, 10)
                    Spacer(Modifier.width(4.dp))
                    val weekend = (line.byCategory[Category.CUMARTESI] ?: 0) + (line.byCategory[Category.PAZAR] ?: 0)
                    Text("${line.person.name} ${line.count} (Cmt+Paz $weekend)", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (expanded) CalendarGrid(gridFromSolution(data, pr, solution.assignment), cellHeight = 58)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Takvimi gizle" else "Takvimi göster") }
            Button(onClick = onUse) { Text("Bunu kullan") }
        }
    }
}
