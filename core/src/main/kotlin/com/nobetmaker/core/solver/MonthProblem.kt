package com.nobetmaker.core.solver

import com.nobetmaker.core.calendar.CalendarService
import com.nobetmaker.core.calendar.Category
import com.nobetmaker.core.calendar.TurkishHolidays
import com.nobetmaker.core.calendar.monthNameTr
import com.nobetmaker.core.ledger.Ledger
import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.model.Assignment
import com.nobetmaker.core.model.DayKind
import com.nobetmaker.core.model.PenaltyWeights
import com.nobetmaker.core.model.Person
import com.nobetmaker.core.model.Priority
import com.nobetmaker.core.model.QuotaEntry
import com.nobetmaker.core.model.RequestType
import com.nobetmaker.core.model.RuleType
import com.nobetmaker.core.quota.QuotaCalculator
import com.nobetmaker.core.quota.QuotaResult
import com.nobetmaker.core.rules.Availability
import com.nobetmaker.core.rules.RequestText
import com.nobetmaker.core.rules.RosterIndex
import java.time.LocalDate
import java.time.YearMonth

class Want(val p: Int, val day: Int, val weight: Double, val label: String, val requestId: String)
class ClusterReq(val p: Int, val from: Int, val to: Int, val weight: Double, val label: String, val requestId: String)
class WeeklyReq(val p: Int, val from: Int, val to: Int, val max: Int, val weight: Double, val label: String, val requestId: String)

/**
 * Bir ayın nöbet problemi. Günler 0..D-1 ile, ay öncesi/sonrası komşu günler "genişletilmiş" dizinle
 * (ext = gün + PAD) tutulur; böylece ay geçişindeki kurallar da denetlenir.
 */
class MonthProblem(
    val month: YearMonth,
    val days: List<LocalDate>,
    val persons: List<Person>,
    val quotas: IntArray,
    val available: Array<BooleanArray>,
    val hardReasons: Array<Array<List<String>>>,
    val locked: IntArray,
    /** [ext] → kişi dizini; yalnızca ay dışındaki günler için anlamlı. */
    val external: IntArray,
    val points: DoubleArray,
    val hours: IntArray,
    val category: Array<Category>,
    val bayramKey: Array<String?>,
    val weekKey: LongArray,
    val softAvoid: Array<DoubleArray>,
    val softAvoidLabel: Array<Array<String?>>,
    val softAvoidRequest: Array<Array<String?>>,
    val wants: List<Want>,
    val clusters: List<ClusterReq>,
    val weekly: List<WeeklyReq>,
    /** [ext] → korunan tatil bloğunun ext dizinleri (Y1). */
    val longBlocks: Array<IntArray>,
    /** [gün] → sıra grubu (RotationGroup.ordinal) ya da -1. */
    val rotationIndex: IntArray,
    /** [kişi][grup] → bu ay o gruptan tutması beklenen gün sayısı. */
    val rotationTarget: Array<IntArray>,
    /** Grup başına bu ayın sırası (kişi dizinleri). */
    val rotationQueue: List<List<Int>>,
    val longBase: Array<DoubleArray>,
    val longIndex: IntArray,
    /** [gün] → uzun hafızadaki ağırlığı (arefe yarım). */
    val longWeight: DoubleArray,
    val weights: PenaltyWeights,
    val minGap: Int,
    val maxPerMonth: Int,
    val quotaResult: QuotaResult?,
    val softRequestIds: Set<String>,
    val warnings: List<String>,
) {
    val dayCount: Int get() = days.size
    val personCount: Int get() = persons.size
    val extSize: Int get() = days.size + 2 * PAD

    fun ext(day: Int): Int = day + PAD
    fun isMonthExt(x: Int): Boolean = x >= PAD && x < PAD + days.size
    fun dateOfExt(x: Int): LocalDate = days.first().plusDays((x - PAD).toLong())
    fun personIndex(id: String): Int = persons.indexOfFirst { it.id == id }

    companion object {
        const val PAD = 10
    }
}

fun shortDate(d: LocalDate): String = "${d.dayOfMonth} ${monthNameTr(d.monthValue)}"

object ProblemBuilder {

    /**
     * @param existing ayın mevcut atamaları; kilitli olanlar korunur
     * @param quotaOverride kesinleşmiş kotalar (kişi id → sayı); yoksa önerilen kotalar kullanılır
     */
    fun build(
        data: AppData,
        month: YearMonth,
        existing: List<Assignment> = emptyList(),
        quotaOverride: Map<String, Int>? = null,
        quotaFixed: Map<String, Int> = emptyMap(),
    ): MonthProblem {
        val settings = data.settings
        val cal = CalendarService(data.dayOverrides, settings)
        val avail = Availability(data, cal)
        val roster = RosterIndex(data)
        val ledger = Ledger(data, cal, avail, roster)
        val warnings = ArrayList<String>()
        val w = settings.penalties

        val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
        val first = days.first()
        val persons = data.persons.filter { p -> days.any { p.isActiveOn(it) } }.sortedBy { it.seniority }
        val P = persons.size
        val D = days.size
        val pad = MonthProblem.PAD
        val E = D + 2 * pad

        val hardReasons = Array(P) { p -> Array(D) { i -> avail.hardReasons(persons[p], days[i]) } }
        val available = Array(P) { p -> BooleanArray(D) { i -> hardReasons[p][i].isEmpty() } }

        // Kilitler: mevcut kilitli atamalar ve "kesin" gün istekleri.
        val locked = IntArray(D) { -1 }
        for (a in existing) {
            if (!a.locked) continue
            val i = days.indexOf(a.date)
            val p = persons.indexOfFirst { it.id == a.personId }
            if (i >= 0 && p >= 0) locked[i] = p
        }
        for (r in data.requests) {
            if (r.type != RequestType.WANT_DATES || r.priority != Priority.KESIN) continue
            val p = persons.indexOfFirst { it.id == r.personId }
            if (p < 0) continue
            for (date in r.dates) {
                val i = days.indexOf(date)
                if (i < 0) continue
                if (locked[i] != -1 && locked[i] != p) {
                    warnings += "${shortDate(date)}: birden fazla kesin istek var (${persons[locked[i]].name}, ${persons[p].name})"
                } else locked[i] = p
            }
        }

        // Komşu aylardaki resmi nöbetler.
        val external = IntArray(E) { -1 }
        for (x in 0 until E) {
            if (x in pad until pad + D) continue
            val date = first.plusDays((x - pad).toLong())
            val who = roster.official[date] ?: continue
            external[x] = persons.indexOfFirst { it.id == who }
        }

        val points = DoubleArray(D) { cal.points(days[it]) }
        val hours = IntArray(D) { cal.hours(days[it]) }
        val category = Array(D) { cal.category(days[it]) }
        val bayramKey = Array(D) { if (category[it] == Category.BAYRAM) cal.longEventKey(days[it]) else null }
        val weekKey = LongArray(D) { Math.floorDiv(days[it].toEpochDay() + 3, 7L) }

        fun weightOf(priority: Priority) = when (priority) {
            Priority.KESIN -> w.requestMumkunse * 3
            Priority.MUMKUNSE -> w.requestMumkunse
            Priority.ONEMSIZ -> w.requestOnemsiz
        }

        val softAvoid = Array(P) { DoubleArray(D) }
        val softAvoidLabel = Array(P) { arrayOfNulls<String>(D) }
        val softAvoidRequest = Array(P) { arrayOfNulls<String>(D) }
        val wants = ArrayList<Want>()
        val clusters = ArrayList<ClusterReq>()
        val weekly = ArrayList<WeeklyReq>()
        val softRequestIds = HashSet<String>()

        fun dayIndexRange(r: com.nobetmaker.core.model.DutyRequest): IntRange? {
            val s = r.rangeStart ?: return null
            val e = r.rangeEnd ?: return null
            val from = days.indexOfFirst { !it.isBefore(s) }
            val to = days.indexOfLast { !it.isAfter(e) }
            return if (from < 0 || to < 0 || from > to) null else from..to
        }

        for (r in data.requests) {
            val p = persons.indexOfFirst { it.id == r.personId }
            if (p < 0 || !r.touches(month)) continue
            val label = "${persons[p].name}: ${RequestText.describe(r)}"
            when (r.type) {
                RequestType.AVOID_DATES, RequestType.AVOID_RANGE -> {
                    if (r.priority == Priority.KESIN) continue
                    softRequestIds += r.id
                    for (i in 0 until D) {
                        if (Availability.avoids(r, days[i])) {
                            softAvoid[p][i] += weightOf(r.priority)
                            softAvoidLabel[p][i] = label
                            softAvoidRequest[p][i] = r.id
                        }
                    }
                }
                RequestType.WANT_DATES -> {
                    if (r.priority == Priority.KESIN) continue
                    softRequestIds += r.id
                    for (date in r.dates) {
                        val i = days.indexOf(date)
                        if (i >= 0) wants += Want(p, i, weightOf(r.priority), label, r.id)
                    }
                }
                RequestType.CLUSTER -> dayIndexRange(r)?.let {
                    softRequestIds += r.id
                    clusters += ClusterReq(p, it.first, it.last, weightOf(r.priority), label, r.id)
                }
                RequestType.WEEKLY_MAX -> dayIndexRange(r)?.let {
                    softRequestIds += r.id
                    weekly += WeeklyReq(p, it.first, it.last, r.maxPerWeek ?: 1, weightOf(r.priority), label, r.id)
                }
            }
        }
        // Belirsiz izinler "mümkünse" istek gibi (§5.3).
        for (p in 0 until P) for (i in 0 until D) {
            val t = avail.tentativePeriod(persons[p], days[i]) ?: continue
            softAvoid[p][i] += w.requestMumkunse
            softAvoidLabel[p][i] = "${persons[p].name}: belirsiz ${t.type.label.lowercase()}"
        }

        val longBlocks = Array(E) { x ->
            val date = first.plusDays((x - pad).toLong())
            cal.longHolidayBlockAfter(date)
                .map { (it.toEpochDay() - first.toEpochDay()).toInt() + pad }
                .filter { it in 0 until E }
                .toIntArray()
        }

        // Kotalar.
        val extHas = Array(P) { BooleanArray(E) }
        for (x in 0 until E) if (external[x] >= 0) extHas[external[x]][x] = true
        val capacity = persons.indices.associate { p ->
            var count = 0
            var last = Int.MIN_VALUE / 2
            for (x in 0 until pad) if (extHas[p][x]) last = x
            for (i in 0 until D) {
                val x = i + pad
                val ok = available[p][i] && (locked[i] == -1 || locked[i] == p) && x - last >= settings.minGap
                if (ok) { count++; last = x }
            }
            persons[p].id to count
        }
        val prevMonth = data.month(month.minusMonths(1))
        val carryIn = prevMonth?.quotas?.associate { it.personId to it.carryOut }.orEmpty()
        // Kişiye özel "ayda sabit sayı" kuralı; elle sabitlenen sayı ondan önce gelir.
        val ruleFixed = persons.mapNotNull { p ->
            val rule = p.rules.firstOrNull { it.type == RuleType.MONTHLY_COUNT && it.count != null && days.any { d -> it.appliesOn(d) } }
            rule?.let { p.id to it.count!! }
        }.toMap()
        val fixed = ruleFixed + quotaFixed
        val computed = QuotaCalculator.compute(
            month, persons, avail, carryIn, capacity, settings.maxPerMonth, fixed = fixed,
            dutyOf = { p, d -> avail.coefficients.on(p, d).duty },
        )
        // Kuraldan gelen sayı önerinin kendisidir; elle sabitlenmiş sayılmaz (kural kalkınca öneriye döner).
        val quotaResult = computed.copy(
            entries = computed.entries.map {
                if (it.personId in ruleFixed && it.personId !in quotaFixed) it.copy(suggested = it.final, manual = false) else it
            },
        )
        val quotaMap: Map<String, Int> = quotaOverride ?: quotaResult.entries.associate { it.personId to it.final }
        val quotas = IntArray(P) { quotaMap[persons[it].id] ?: 0 }
        warnings += quotaResult.warnings
        if (quotas.sum() != D) warnings += "Nöbet sayılarının toplamı (${quotas.sum()}) ayın gün sayısına ($D) eşit değil"

        // Defter.
        val personPos = persons.withIndex().associate { (i, p) -> p.id to i }
        fun dayIdx(d: LocalDate) = (d.toEpochDay() - first.toEpochDay()).toInt()
        val rotation = ledger.rotation(
            persons, first.minusMonths(settings.rotationMonths.toLong()), first, days,
            availableInPlan = { p, d -> available[personPos.getValue(p.id)][dayIdx(d)] },
            wants = { p, d -> wants.any { it.p == personPos.getValue(p.id) && it.day == dayIdx(d) } },
            avoids = { p, d -> softAvoid[personPos.getValue(p.id)][dayIdx(d)] > 0.0 },
        )
        val rotationIndex = IntArray(D) { cal.rotationGroup(days[it], history = false)?.ordinal ?: -1 }
        val rotationTarget = Array(P) { rotation.target.getValue(persons[it].id) }
        val rotationQueue = rotation.queue.map { q -> q.map { personPos.getValue(it) } }
        val long = ledger.longMemory(persons, first, days)
        val longBase = Array(P) { long.base.getValue(persons[it].id) }
        val longIndex = IntArray(D) { Category.LONG_MEMORY.indexOf(category[it]) }
        val longWeight = DoubleArray(D) { cal.longWeight(days[it]) }
        if (month.year !in TurkishHolidays.religiousYears && data.dayOverrides.none { it.date.year == month.year && it.kind == DayKind.BAYRAM }) {
            warnings += "${month.year} dini bayram tarihleri uygulamada yok; açıklanınca Takvim'den bayram günlerini girin"
        }

        return MonthProblem(
            month = month, days = days, persons = persons, quotas = quotas,
            available = available, hardReasons = hardReasons, locked = locked, external = external,
            points = points, hours = hours, category = category, bayramKey = bayramKey, weekKey = weekKey,
            softAvoid = softAvoid, softAvoidLabel = softAvoidLabel, softAvoidRequest = softAvoidRequest,
            wants = wants, clusters = clusters, weekly = weekly, longBlocks = longBlocks,
            rotationIndex = rotationIndex, rotationTarget = rotationTarget, rotationQueue = rotationQueue,
            longBase = longBase, longIndex = longIndex, longWeight = longWeight,
            weights = w, minGap = settings.minGap, maxPerMonth = settings.maxPerMonth,
            quotaResult = quotaResult, softRequestIds = softRequestIds, warnings = warnings,
        )
    }

    /** Önerilen nöbet sayıları; [fixed] verilirse o kişiler sabit kalır, kalan günler diğerlerine dağılır. */
    fun suggestedQuotas(data: AppData, month: YearMonth, fixed: Map<String, Int> = emptyMap()): List<QuotaEntry> =
        build(data, month, quotaFixed = fixed).quotaResult?.entries.orEmpty()
}
