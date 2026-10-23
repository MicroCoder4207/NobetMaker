package com.nobetmaker.core.ledger

import com.nobetmaker.core.calendar.CalendarService
import com.nobetmaker.core.calendar.Category
import com.nobetmaker.core.calendar.RotationGroup
import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.model.EventKind
import com.nobetmaker.core.model.Person
import com.nobetmaker.core.rules.Availability
import com.nobetmaker.core.rules.RosterIndex
import java.time.LocalDate

/** Sıra durumu (§6); diziler [RotationGroup.entries] sırasındadır. */
class RotationState(
    /** Beklenen − tutulan; pozitif = sırası gelmiş. */
    val debt: Map<String, DoubleArray>,
    val count: Map<String, IntArray>,
    val last: Map<String, Array<LocalDate?>>,
    /** Planlanan ayda kişinin bu gruptan tutması beklenen gün sayısı. */
    val target: Map<String, IntArray>,
    /** Planlanan ayın sırası: grup başına, günlere sırayla verilecek kişi id'leri. */
    val queue: List<List<String>>,
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

    /**
     * Vizit, Cuma, Pazar ve Perşembe sırası. Geçmişte payından az tutan önce gelir; eşitlikte en uzun
     * süredir tutmayan, o da eşitse çömez. Planlanan ayın günleri bu sırayla dağıtılır.
     */
    fun rotation(
        persons: List<Person>,
        windowStart: LocalDate,
        historyEnd: LocalDate,
        planDays: List<LocalDate>,
        availableInPlan: (Person, LocalDate) -> Boolean,
        wants: (Person, LocalDate) -> Boolean = { _, _ -> false },
        avoids: (Person, LocalDate) -> Boolean = { _, _ -> false },
    ): RotationState {
        val groups = RotationGroup.entries
        val k = groups.size
        val expected = persons.associate { it.id to DoubleArray(k) }
        val count = persons.associate { it.id to IntArray(k) }
        val last = persons.associate { it.id to arrayOfNulls<LocalDate>(k) }

        fun shares(date: LocalDate, group: RotationGroup, among: Collection<Person>): List<Pair<String, Double>> {
            val weights = among.mapNotNull { p ->
                if (!availability.eligibleForShare(p, date)) null
                else p.id to (if (group.equalShare) 1.0 else p.coefficientOn(date).hardDay)
            }.filter { it.second > 0.0 }
            val total = weights.sumOf { it.second }
            return if (total <= 0.0) emptyList() else weights.map { (id, w) -> id to w / total }
        }

        var d = windowStart
        while (d.isBefore(historyEnd)) {
            val who = roster.official[d]
            val group = if (who != null) cal.rotationGroup(d, history = true) else null
            if (group != null) {
                for ((id, share) in shares(d, group, persons)) expected.getValue(id)[group.ordinal] += share
                count[who]?.let { it[group.ordinal]++ }
                last[who]?.set(group.ordinal, d)
            }
            d = d.plusDays(1)
        }
        val debt = persons.associate { p -> p.id to DoubleArray(k) { expected.getValue(p.id)[it] - count.getValue(p.id)[it] } }

        val target = persons.associate { it.id to IntArray(k) }
        val queue = groups.map { group ->
            val days = planDays.filter { cal.rotationGroup(it, history = false) == group }
            val candidates = persons.filter { p -> days.any { availableInPlan(p, it) && availability.eligibleForShare(p, it) } }
            if (days.isEmpty() || candidates.isEmpty()) return@map emptyList()
            val work = candidates.associate { it.id to debt.getValue(it.id)[group.ordinal] }.toMutableMap()
            val lastWork = candidates.associate { it.id to last.getValue(it.id)[group.ordinal] }.toMutableMap()
            val usedWeekdays = HashMap<String, MutableSet<java.time.DayOfWeek>>()
            days.map { day ->
                for ((id, share) in shares(day, group, candidates)) work[id] = work.getValue(id) + share
                // İstek sıradan önce gelir. Aynı kişiye ayda haftanın aynı gününden ikincisi verilmez;
                // mümkün değilse kural esner.
                val wanting = candidates.filter { wants(it, day) && availableInPlan(it, day) }
                val fresh = candidates.filter { day.dayOfWeek !in usedWeekdays[it.id].orEmpty() }
                val pool = wanting
                    .ifEmpty { fresh.filter { availableInPlan(it, day) && !avoids(it, day) } }
                    .ifEmpty { fresh.filter { availableInPlan(it, day) } }
                    .ifEmpty { fresh }
                    .ifEmpty { candidates }
                val pick = pool.sortedWith(
                    compareByDescending<Person> { Math.round(work.getValue(it.id) * 2) / 2.0 }
                        .thenBy { lastWork[it.id] ?: LocalDate.MIN }
                        .thenByDescending { it.seniority },
                ).first()
                target.getValue(pick.id)[group.ordinal]++
                usedWeekdays.getOrPut(pick.id) { HashSet() } += day.dayOfWeek
                work[pick.id] = work.getValue(pick.id) - 1.0
                lastWork[pick.id] = day
                pick.id
            }
        }
        return RotationState(debt, count, last, target, queue)
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
