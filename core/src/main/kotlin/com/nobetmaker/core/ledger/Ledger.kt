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
    /** Tutulan gün (arefe yarım). */
    val days: Map<String, DoubleArray>,
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
     * Vizit, Cuma, Pazar, Perşembe ve Pazartesi sırası. Geçmişte payından az tutan önce gelir; eşitlikte en uzun
     * süredir tutmayan, o da eşitse çömez. Vizitte eşitlikte önce çömez gelir (alışılmış düzen).
     * Planlanan ayın günleri bu sırayla dağıtılır.
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
        // Geçmiş günlerin payı o gün ekipte olan herkes arasında dağılır (sonradan ayrılanlar dahil); yoksa
        // ayrılan birinin tuttuğu günler kalanlara borç gibi görünürdü.
        val everyone = (persons + data.persons).distinctBy { it.id }
        val expected = everyone.associate { it.id to DoubleArray(k) }
        val count = everyone.associate { it.id to IntArray(k) }
        val last = everyone.associate { it.id to arrayOfNulls<LocalDate>(k) }

        fun shares(date: LocalDate, group: RotationGroup, among: Collection<Person>): List<Pair<String, Double>> {
            val weights = among.mapNotNull { p ->
                if (!availability.eligibleForShare(p, date)) null
                else p.id to (if (group.equalShare) 1.0 else availability.coefficients.on(p, date).hardDay)
            }.filter { it.second > 0.0 }
            val total = weights.sumOf { it.second }
            return if (total <= 0.0) emptyList() else weights.map { (id, w) -> id to w / total }
        }

        var d = windowStart
        while (d.isBefore(historyEnd)) {
            val who = roster.official[d]
            val group = if (who != null) cal.rotationGroup(d, history = true) else null
            if (group != null) {
                for ((id, share) in shares(d, group, everyone)) expected.getValue(id)[group.ordinal] += share
                count[who]?.let { it[group.ordinal]++ }
                last[who]?.set(group.ordinal, d)
            }
            d = d.plusDays(1)
        }
        val debt = everyone.associate { p -> p.id to DoubleArray(k) { expected.getValue(p.id)[it] - count.getValue(p.id)[it] } }

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
                val byDebt = compareByDescending<Person> { Math.round(work.getValue(it.id) * 2) / 2.0 }
                val order = if (group == RotationGroup.VIZIT) {
                    byDebt.thenByDescending { it.seniority }.thenBy { lastWork[it.id] ?: LocalDate.MIN }
                } else {
                    byDebt.thenBy { lastWork[it.id] ?: LocalDate.MIN }.thenByDescending { it.seniority }
                }
                val pick = pool.sortedWith(order).first()
                target.getValue(pick.id)[group.ordinal]++
                usedWeekdays.getOrPut(pick.id) { HashSet() } += day.dayOfWeek
                work[pick.id] = work.getValue(pick.id) - 1.0
                lastWork[pick.id] = day
                pick.id
            }
        }
        return RotationState(debt, count, last, target, queue)
    }

    private class Event(val kind: EventKind, val dates: List<LocalDate>, val days: Map<String, Double>, val present: Set<String>)

    /**
     * Bayram, resmi tatil ve yılbaşı dengesi (§6). Her olayın günleri, o gün ekipte olan asistanlar arasında
     * o günkü zor gün katsayısıyla paylaştırılır; yalnızca asistanların tuttuğu günler paylaşılır (uzman günleri
     * hesaba girmez). Arefe gibi yarım günler yarım sayılır. Planlanan ayın tatilleri de beklentiye eklenir.
     */
    fun longMemory(
        persons: List<Person>,
        historyEnd: LocalDate,
        planDays: List<LocalDate>,
    ): LongMemoryState {
        val cats = Category.LONG_MEMORY
        val k = cats.size
        val start = data.settings.longMemoryStart
        val firstRosterDate = roster.official.keys.minOrNull()
        val coef = availability.coefficients
        // Olayın payı o gün ekipte olan herkes arasında dağılır (sonradan ayrılanlar dahil).
        val everyone = (persons + data.persons).distinctBy { it.id }

        val events = ArrayList<Event>()
        // Tam listesi olan günlerden türeyen olaylar.
        val grouped = LinkedHashMap<Pair<Category, String>, MutableList<LocalDate>>()
        roster.official.keys.filter { !it.isBefore(start) && it.isBefore(historyEnd) }.sorted().forEach { date ->
            val cat = cal.category(date)
            if (cat in cats) grouped.getOrPut(cat to cal.longEventKey(date)) { ArrayList() } += date
        }
        for ((key, dates) in grouped) {
            val days = HashMap<String, Double>()
            for (d in dates) days.merge(roster.official.getValue(d), cal.longWeight(d), Double::plus)
            val present = everyone.filter { p -> days.containsKey(p.id) || dates.any { availability.eligibleForShare(p, it) } }
            events += Event(key.first.eventKind!!, dates, days, present.map { it.id }.toSet())
        }
        // Elle girilmiş eski olaylar (yalnızca tam liste dönemi öncesi).
        for (h in data.historyEvents) {
            if (h.start != null && h.start.isBefore(start)) continue
            if (h.start != null && firstRosterDate != null && !h.start.isBefore(firstRosterDate)) continue
            val days = h.entries.associate { it.personId to it.days.toDouble() }
            val present = everyone.filter { p ->
                (days[p.id] ?: 0.0) > 0.0 ||
                    (h.start?.let { availability.eligibleForShare(p, it) } ?: (availability.presenceStart(p) == null))
            }
            events += Event(h.kind, listOfNotNull(h.start), days, present.map { it.id }.toSet())
        }

        val days = everyone.associate { it.id to DoubleArray(k) }
        val present = everyone.associate { it.id to IntArray(k) }
        val expected = everyone.associate { it.id to DoubleArray(k) }

        fun share(gi: Int, total: Double, among: List<Person>, date: LocalDate?) {
            val weights = among.associate { p -> p.id to (date?.let { coef.on(p, it).hardDay } ?: coef.on(p, historyEnd).hardDay) }
            val sum = weights.values.sum()
            if (sum <= 0.0) return
            for (p in among) {
                present.getValue(p.id)[gi]++
                expected.getValue(p.id)[gi] += total * weights.getValue(p.id) / sum
            }
        }

        for (e in events) {
            val gi = cats.indexOfFirst { it.eventKind == e.kind }
            for ((id, n) in e.days) days[id]?.let { it[gi] += n }
            share(gi, e.days.values.sum(), everyone.filter { it.id in e.present }, e.dates.firstOrNull())
        }

        // Planlanan ayın olayları: günler henüz atanmadı ama beklentiye girer.
        planDays.mapNotNull { d ->
            val gi = cats.indexOf(cal.category(d))
            if (gi < 0) null else Triple(gi, cal.longEventKey(d), d)
        }.groupBy { it.first to it.second }.forEach { (key, items) ->
            val dates = items.map { it.third }
            val among = persons.filter { p -> dates.any { availability.eligibleForShare(p, it) } }
            share(key.first, dates.sumOf { cal.longWeight(it) }, among, dates.first())
        }

        val base = everyone.associate { p ->
            p.id to DoubleArray(k) { days.getValue(p.id)[it] - expected.getValue(p.id)[it] }
        }
        return LongMemoryState(cats, base, days, present, expected)
    }
}
