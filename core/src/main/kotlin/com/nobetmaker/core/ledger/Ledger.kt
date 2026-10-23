package com.nobetmaker.core.ledger

import com.nobetmaker.core.calendar.CalendarService
import com.nobetmaker.core.calendar.Category
import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.model.EventKind
import com.nobetmaker.core.model.Person
import com.nobetmaker.core.rules.Availability
import com.nobetmaker.core.rules.RosterIndex
import java.time.LocalDate

/** Kısa hafızalı kategorilerin (Cumartesi, Pazar, Cuma, Perşembe) durumu; dizi sırası [Category.SHORT_MEMORY]. */
class ShortMemoryState(
    val categories: List<Category>,
    /** Gerçekleşen − hedef, gün biriminde; plan günleri hedefe dahil, gerçekleşene dahil değil. */
    val base: Map<String, DoubleArray>,
    val average: DoubleArray,
    val actualPoints: Map<String, DoubleArray>,
    val targetPoints: Map<String, DoubleArray>,
    val counts: Map<String, IntArray>,
)

/** Uzun hafızalı kategorilerin (Yılbaşı, Bayram, Resmi tatil) durumu; dizi sırası [Category.LONG_MEMORY]. */
class LongMemoryState(
    val categories: List<Category>,
    /** Tutulan gün − beklenen gün; plan günlerinin beklentisi dahil. */
    val base: Map<String, DoubleArray>,
    val days: Map<String, IntArray>,
    val presentEvents: Map<String, IntArray>,
    val expected: Map<String, DoubleArray>,
)

/** Adalet defteri (§6). Kayıt tutmaz; her seferinde resmi listeden yeniden hesaplanır. */
class Ledger(
    private val data: AppData,
    private val cal: CalendarService,
    private val availability: Availability,
    private val roster: RosterIndex,
) {

    fun shortMemory(
        persons: List<Person>,
        windowStart: LocalDate,
        historyEnd: LocalDate,
        planDays: List<LocalDate>,
    ): ShortMemoryState {
        val cats = Category.SHORT_MEMORY
        val k = cats.size
        val actual = persons.associate { it.id to DoubleArray(k) }
        val target = persons.associate { it.id to DoubleArray(k) }
        val counts = persons.associate { it.id to IntArray(k) }
        val sum = DoubleArray(k)
        val n = IntArray(k)

        fun share(date: LocalDate, ci: Int, cat: Category, pts: Double) {
            val weights = persons.mapNotNull { p ->
                if (!availability.eligibleForShare(p, date)) null
                else p.id to (if (cat.equalShare) 1.0 else p.coefficientOn(date).hardDay)
            }.filter { it.second > 0.0 }
            val total = weights.sumOf { it.second }
            if (total <= 0.0) return
            for ((id, w) in weights) target.getValue(id)[ci] += pts * w / total
            sum[ci] += pts
            n[ci]++
        }

        var d = windowStart
        while (d.isBefore(historyEnd)) {
            val cat = cal.category(d)
            val ci = cats.indexOf(cat)
            val who = roster.official[d]
            if (ci >= 0 && who != null) {
                val pts = cal.points(d)
                share(d, ci, cat, pts)
                actual[who]?.let { it[ci] += pts; counts.getValue(who)[ci]++ }
            }
            d = d.plusDays(1)
        }
        for (day in planDays) {
            val cat = cal.category(day)
            val ci = cats.indexOf(cat)
            if (ci >= 0) share(day, ci, cat, cal.points(day))
        }

        val avg = DoubleArray(k) { if (n[it] > 0) sum[it] / n[it] else 1.0 }
        val base = persons.associate { p ->
            p.id to DoubleArray(k) { (actual.getValue(p.id)[it] - target.getValue(p.id)[it]) / avg[it] }
        }
        return ShortMemoryState(cats, base, avg, actual, target, counts)
    }

    private class Event(val kind: EventKind, val start: LocalDate?, val dates: List<LocalDate>, val days: Map<String, Int>, val manual: Boolean)

    fun longMemory(
        persons: List<Person>,
        historyEnd: LocalDate,
        planDays: List<LocalDate>,
        coefficientDate: LocalDate,
    ): LongMemoryState {
        val cats = Category.LONG_MEMORY
        val k = cats.size
        val start = data.settings.longMemoryStart
        val firstRosterDate = roster.official.keys.minOrNull()

        val events = ArrayList<Event>()
        // Tam listesi olan günlerden türeyen olaylar.
        val grouped = LinkedHashMap<Pair<Category, String>, MutableList<LocalDate>>()
        roster.official.keys.filter { !it.isBefore(start) && it.isBefore(historyEnd) }.sorted().forEach { date ->
            val cat = cal.category(date)
            if (cat in cats) grouped.getOrPut(cat to cal.longEventKey(date)) { ArrayList() } += date
        }
        for ((key, dates) in grouped) {
            val days = dates.groupingBy { roster.official.getValue(it) }.eachCount()
            events += Event(key.first.eventKind!!, dates.first(), dates, days, manual = false)
        }
        // Elle girilmiş eski olaylar (yalnızca tam liste dönemi öncesi).
        for (h in data.historyEvents) {
            if (h.start != null && h.start.isBefore(start)) continue
            if (h.start != null && firstRosterDate != null && !h.start.isBefore(firstRosterDate)) continue
            events += Event(h.kind, h.start, emptyList(), h.entries.associate { it.personId to it.days }, manual = true)
        }

        val days = persons.associate { it.id to IntArray(k) }
        val present = persons.associate { it.id to IntArray(k) }

        fun presentAt(p: Person, e: Event): Boolean {
            if ((e.days[p.id] ?: 0) > 0) return true
            if (e.manual) {
                val s = e.start ?: return p.joinDate == null
                return availability.eligibleForShare(p, s)
            }
            return e.dates.any { availability.eligibleForShare(p, it) }
        }

        for (e in events) {
            val gi = cats.indexOfFirst { it.eventKind == e.kind }
            for (p in persons) {
                if (presentAt(p, e)) {
                    present.getValue(p.id)[gi]++
                    days.getValue(p.id)[gi] += e.days[p.id] ?: 0
                }
            }
        }

        // Planlanan ayın olayları: günler henüz atanmadı ama beklentiye girer.
        val planDaysPerGroup = IntArray(k)
        val planGroups = planDays.mapNotNull { d ->
            val cat = cal.category(d)
            val gi = cats.indexOf(cat)
            if (gi < 0) null else Triple(gi, cal.longEventKey(d), d)
        }
        planGroups.forEach { planDaysPerGroup[it.first]++ }
        planGroups.groupBy { it.first to it.second }.forEach { (key, items) ->
            for (p in persons) {
                if (items.any { availability.eligibleForShare(p, it.third) }) present.getValue(p.id)[key.first]++
            }
        }

        val expected = persons.associate { it.id to DoubleArray(k) }
        for (gi in 0 until k) {
            val totalDays = persons.sumOf { days.getValue(it.id)[gi] } + planDaysPerGroup[gi]
            val weight = persons.sumOf { present.getValue(it.id)[gi] * it.coefficientOn(coefficientDate).hardDay }
            if (weight <= 0.0) continue
            val rate = totalDays / weight
            for (p in persons) {
                expected.getValue(p.id)[gi] = rate * present.getValue(p.id)[gi] * p.coefficientOn(coefficientDate).hardDay
            }
        }
        val base = persons.associate { p ->
            p.id to DoubleArray(k) { days.getValue(p.id)[it] - expected.getValue(p.id)[it] }
        }
        return LongMemoryState(cats, base, days, present, expected)
    }
}
