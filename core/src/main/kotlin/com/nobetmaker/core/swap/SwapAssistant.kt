package com.nobetmaker.core.swap

import com.nobetmaker.core.calendar.CalendarService
import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.rules.Availability
import com.nobetmaker.core.rules.RosterIndex
import com.nobetmaker.core.solver.shortDate
import java.time.LocalDate
import kotlin.math.abs

class SwapStep(val date: LocalDate, val fromId: String, val toId: String)

class SwapOption(val steps: List<SwapStep>, val warnings: List<String>, val title: String)

/**
 * Değişim asistanı (§9.3): bir kişinin vermek istediği güne doğrudan devir, birebir takas ve
 * iki adımlı zincir seçeneklerini, gerçekte tutulan liste üzerinde kural kontrolüyle sıralar.
 */
class SwapAssistant(private val data: AppData) {

    private val cal = CalendarService(data.dayOverrides, data.settings)
    private val avail = Availability(data, cal)
    private val roster = RosterIndex(data)
    private val persons = data.persons.associateBy { it.id }
    private val minGap = data.settings.minGap

    private fun name(id: String) = persons[id]?.name ?: id

    fun warnings(steps: List<SwapStep>): List<String> {
        val map = roster.actual.toMutableMap()
        steps.forEach { map[it.date] = it.toId }
        val out = ArrayList<String>()
        for (s in steps) {
            val p = persons[s.toId] ?: continue
            avail.hardReasons(p, s.date).forEach { out += "${p.name}, ${shortDate(s.date)}: $it" }
            for (k in 1 until minGap) {
                for (d in listOf(s.date.minusDays(k.toLong()), s.date.plusDays(k.toLong()))) {
                    if (map[d] == s.toId) {
                        val word = if (k == 1) "art arda" else "günaşırı"
                        out += "${p.name}: ${shortDate(minOf(d, s.date))} ve ${shortDate(maxOf(d, s.date))} $word"
                    }
                }
            }
        }
        return out.distinct()
    }

    fun options(personId: String, date: LocalDate, limit: Int = 30): List<SwapOption> {
        val result = ArrayList<SwapOption>()
        val others = data.persons.filter { it.id != personId && it.isActiveOn(date) }

        // Doğrudan devir.
        for (y in others) {
            val steps = listOf(SwapStep(date, personId, y.id))
            result += SwapOption(steps, warnings(steps), "${y.name} ${shortDate(date)} nöbetini alır (devir)")
        }

        // Birebir takas.
        val swaps = ArrayList<SwapOption>()
        for (y in others) {
            val theirDays = roster.actual.filter { (d, who) -> who == y.id && d != date && abs(d.toEpochDay() - date.toEpochDay()) <= 35 }.keys
            for (e in theirDays.sorted()) {
                val steps = listOf(SwapStep(date, personId, y.id), SwapStep(e, y.id, personId))
                val w = warnings(steps)
                swaps += SwapOption(steps, w, "${y.name} ${shortDate(date)} alır, ${name(personId)} ${shortDate(e)} alır")
            }
        }
        result += swaps

        // İki adımlı zincir: takas yalnızca X'in başka bir nöbetiyle çakışıyorsa o nöbet de başkasıyla değiştirilir.
        val myDays = roster.actual.filterValues { it == personId }.keys
        for (s in swaps.filter { it.warnings.isNotEmpty() }.take(60)) {
            val e = s.steps[1].date
            val clash = myDays.filter { it != date && abs(it.toEpochDay() - e.toEpochDay()) < minGap }
            if (clash.size != 1) continue
            val f = clash.single()
            val yId = s.steps[0].toId
            for (z in others.filter { it.id != yId }) {
                val zDays = roster.actual.filter { (d, who) -> who == z.id && abs(d.toEpochDay() - f.toEpochDay()) <= 21 }.keys
                for (g in zDays) {
                    val steps = s.steps + listOf(SwapStep(f, personId, z.id), SwapStep(g, z.id, personId))
                    val w = warnings(steps)
                    if (w.isEmpty()) {
                        result += SwapOption(steps, w, "${s.title}; ayrıca ${z.name} ${shortDate(f)} alır, ${name(personId)} ${shortDate(g)} alır")
                    }
                }
            }
        }

        return result
            .sortedWith(compareBy<SwapOption> { it.warnings.size }.thenBy { it.steps.size }
                .thenBy { o -> o.steps.sumOf { abs(it.date.toEpochDay() - date.toEpochDay()) } })
            .distinctBy { o -> o.steps.joinToString { "${it.date}:${it.toId}" } }
            .take(limit)
    }
}
