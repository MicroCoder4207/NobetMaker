package com.nobetmaker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nobetmaker.app.NobetApp
import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.ops.DataOps
import com.nobetmaker.core.solver.MonthProblem
import com.nobetmaker.core.solver.ProblemBuilder
import com.nobetmaker.core.solver.SolveResult
import com.nobetmaker.core.solver.Solver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

class Generation(
    val month: YearMonth,
    val seedOffset: Int,
    val running: Boolean,
    /** Üretimin dayandığı veri; veri değiştiyse (ör. yeni istek) sonuç eskimiştir. */
    val source: AppData? = null,
    val problem: MonthProblem? = null,
    val result: SolveResult? = null,
    val error: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val repository = (app as NobetApp).repository
    val data: StateFlow<AppData> = repository.data

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    private val _generation = MutableStateFlow<Generation?>(null)
    val generation: StateFlow<Generation?> = _generation
    private var generationJob: Job? = null

    fun setMonth(ym: YearMonth) {
        _month.value = ym
    }

    fun toast(text: String) {
        messages.tryEmit(text)
    }

    /** Veriyi değiştirir; hata olursa mesaj gösterilir. [onDone] kayıt bittikten sonra ana iş parçacığında çalışır. */
    fun update(success: String? = null, onDone: (() -> Unit)? = null, transform: (AppData) -> AppData) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.update(transform)
                .onSuccess {
                    success?.let { messages.tryEmit(it) }
                    if (onDone != null) withContext(Dispatchers.Main) { onDone() }
                }
                .onFailure { messages.tryEmit(it.message ?: "Beklenmeyen hata") }
        }
    }

    private var cachedProblem: Triple<AppData, YearMonth, MonthProblem>? = null

    /** Gün ekranındaki adaylar ve açıklamalar için ayın problemi (veri değişmedikçe önbellekten). */
    suspend fun problem(ym: YearMonth): MonthProblem = withContext(Dispatchers.Default) {
        val d = data.value
        cachedProblem?.let { (cd, cm, pr) -> if (cd === d && cm == ym) return@withContext pr }
        val pr = ProblemBuilder.build(d, ym, DataOps.currentAssignments(d, ym), DataOps.quotaMap(d.month(ym)))
        cachedProblem = Triple(d, ym, pr)
        pr
    }

    fun generate(ym: YearMonth, seedOffset: Int = 0) {
        generationJob?.cancel()
        _generation.value = Generation(ym, seedOffset, running = true)
        generationJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                // Önerilen sayılar güncel veriye (kıdem eğrisi, izinler, kurallar) göre yenilenir; elle sabitlenenler kalır.
                var d = data.value
                val refreshed = DataOps.refreshQuotas(DataOps.ensureMonth(d, ym), ym)
                if (refreshed !== d) {
                    repository.update { DataOps.refreshQuotas(DataOps.ensureMonth(it, ym), ym) }
                    d = data.value
                }
                val pr = ProblemBuilder.build(d, ym, DataOps.currentAssignments(d, ym), DataOps.quotaMap(d.month(ym)))
                val result = Solver(pr).solve(seedOffset = seedOffset)
                _generation.value = Generation(ym, seedOffset, running = false, source = d, problem = pr, result = result)
            } catch (e: Exception) {
                _generation.value = Generation(ym, seedOffset, running = false, error = e.message ?: "Üretim başarısız")
            }
        }
    }

    fun applyAlternative(index: Int, onDone: () -> Unit) {
        val g = _generation.value ?: return
        val pr = g.problem ?: return
        val solution = g.result?.solutions?.getOrNull(index) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.update { DataOps.applySolution(it, pr, solution.assignment, "Otomatik oluşturuldu (seçenek ${index + 1})") }
                .onSuccess {
                    messages.tryEmit("Liste kaydedildi")
                    withContext(Dispatchers.Main) { onDone() }
                }
                .onFailure { messages.tryEmit(it.message ?: "Kaydedilemedi") }
        }
    }

    /** Bu ay için önceki üretim, verideki son değişiklikleri (istek, izin, kota…) içermiyorsa yeniden üretilmeli. */
    fun generationStale(ym: YearMonth): Boolean {
        val g = _generation.value ?: return true
        if (g.month != ym) return true
        if (g.running) return false
        return g.result == null || g.source !== data.value
    }

    fun clearGeneration() {
        generationJob?.cancel()
        _generation.value = null
    }
}
