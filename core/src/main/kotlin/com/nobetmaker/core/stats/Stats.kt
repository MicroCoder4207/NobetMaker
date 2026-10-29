package com.nobetmaker.core.stats

import com.nobetmaker.core.calendar.CalendarService
import com.nobetmaker.core.calendar.Category
import com.nobetmaker.core.calendar.RotationGroup
import com.nobetmaker.core.ledger.CountBalance
import com.nobetmaker.core.ledger.CountLedger
import com.nobetmaker.core.ledger.Ledger
import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.model.Person
import com.nobetmaker.core.model.Priority
import com.nobetmaker.core.model.RequestType
import com.nobetmaker.core.rules.Availability
import com.nobetmaker.core.rules.RosterIndex
import com.nobetmaker.core.solver.MonthProblem
import com.nobetmaker.core.solver.Solver
import com.nobetmaker.core.solver.shortDate
import java.time.LocalDate
import java.time.YearMonth

class PersonStats(
    val person: Person,
    val officialCount: Int,
    val actualCount: Int,
    val officialHours: Int,
    val actualHours: Int,
    val officialByCategory: Map<Category, Int>,
    /** Sıra hafızasında (son aylar) tutulan gün sayısı. */
    val rotationCount: Map<RotationGroup, Int>,
    /** Tutulan − beklenen; pozitif = payından fazla tutmuş. */
    val rotationBalance: Map<RotationGroup, Double>,
    val longDays: Map<Category, Double>,
    val longPresent: Map<Category, Int>,
    /** Uzun hafıza sapması (gün). */
    val longDeviation: Map<Category, Double>,
    val requestsTotal: Int,
    val requestsMet: Int,
    /** Zor günler (cuma, cumartesi, pazar) toplamı, sıra hafızası içinde. */
    val hardCount: Int = 0,
    /** Zor gün toplamında tutulan − payı; pozitif = payından fazla. */
    val hardBalance: Double = 0.0,
    /** Nöbet sayısı dengesi (sayı dengesi başlangıcından ya da aralığın başından itibaren, hoca kararları hariç). */
    val countBalance: CountBalance? = null,
)

/** İstatistik ekranı (§12-8). */
object StatsService {

    fun compute(data: AppData, from: YearMonth, to: YearMonth): List<PersonStats> {
        val cal = CalendarService(data.dayOverrides, data.settings)
        val avail = Availability(data, cal)
        val roster = RosterIndex(data)
        val ledger = Ledger(data, cal, avail, roster)
        val start = from.atDay(1)
        val endExclusive = to.plusMonths(1).atDay(1)
        val persons = data.persons.filter { p ->
            p.leaveDate == null || !p.leaveDate.isBefore(start)
        }.sortedBy { it.seniority }

        val countFrom = maxOf(from, YearMonth.from(data.settings.countBalanceStart))
        val counts = if (countFrom > to) emptyMap() else CountLedger(data, avail, roster).balance(countFrom, to.plusMonths(1))
        val rotation = ledger.rotation(persons, endExclusive.minusMonths(data.settings.rotationMonths.toLong()), endExclusive, emptyList(), availableInPlan = { _, _ -> true })
        val long = ledger.longMemory(persons, endExclusive, emptyList())

        fun inRange(d: LocalDate) = !d.isBefore(start) && d.isBefore(endExclusive)

        return persons.map { p ->
            val off = roster.official.filter { (d, who) -> who == p.id && inRange(d) }.keys
            val act = roster.actual.filter { (d, who) -> who == p.id && inRange(d) }.keys
            var total = 0
            var met = 0
            for (r in data.requests.filter { it.personId == p.id && it.priority != Priority.KESIN }) {
                val dates = when (r.type) {
                    RequestType.AVOID_DATES, RequestType.WANT_DATES -> r.dates
                    else -> generateSequence(r.rangeStart) { d -> d.plusDays(1).takeIf { r.rangeEnd != null && !it.isAfter(r.rangeEnd) } }.toList()
                }.filter { inRange(it) && roster.official.containsKey(it) }
                if (dates.isEmpty()) continue
                total++
                val ok = when (r.type) {
                    RequestType.AVOID_DATES, RequestType.AVOID_RANGE -> dates.none { roster.official[it] == p.id }
                    RequestType.WANT_DATES -> dates.any { roster.official[it] == p.id }
                    RequestType.CLUSTER -> true
                    RequestType.WEEKLY_MAX -> dates.groupBy { Math.floorDiv(it.toEpochDay() + 3, 7L) }
                        .all { (_, days) -> days.count { roster.official[it] == p.id } <= (r.maxPerWeek ?: 1) }
                }
                if (ok) met++
            }
            PersonStats(
                person = p,
                officialCount = off.size,
                actualCount = act.size,
                officialHours = off.sumOf { cal.hours(it) },
                actualHours = act.sumOf { cal.hours(it) },
                officialByCategory = off.groupingBy { cal.category(it) }.eachCount(),
                rotationCount = RotationGroup.entries.associateWith { rotation.count.getValue(p.id)[it.ordinal] },
                rotationBalance = RotationGroup.entries.associateWith { -rotation.debt.getValue(p.id)[it.ordinal] },
                longDays = long.categories.withIndex().associate { (i, c) -> c to long.days.getValue(p.id)[i] },
                longPresent = long.categories.withIndex().associate { (i, c) -> c to long.presentEvents.getValue(p.id)[i] },
                longDeviation = long.categories.withIndex().associate { (i, c) -> c to long.base.getValue(p.id)[i] },
                requestsTotal = total,
                requestsMet = met,
                hardCount = rotation.hardCount[p.id] ?: 0,
                hardBalance = -(rotation.hardDebt[p.id] ?: 0.0),
                countBalance = counts[p.id],
            )
        }
    }
}

/** Liste üretilmeden önceki kontroller (§7.3, §8.6 erken uyarı). */
object ConflictDetector {

    fun detect(pr: MonthProblem): List<String> {
        val out = ArrayList<String>()
        out += pr.warnings
        for (i in 0 until pr.dayCount) {
            if (pr.locked[i] != -1) continue
            val hard = pr.persons.indices.filter { pr.available[it][i] }
            if (hard.isEmpty()) {
                out += "${shortDate(pr.days[i])}: uygun kimse yok"
                continue
            }
            val free = hard.filter { pr.softAvoid[it][i] == 0.0 }
            if (free.isEmpty()) {
                val names = hard.joinToString(", ") { pr.persons[it].name }
                out += "${shortDate(pr.days[i])}: uygun herkes ($names) bu günü istemiyor; biri esnemeli"
            }
        }
        pr.wants.groupBy { it.day }.filter { (_, w) -> w.map { it.p }.distinct().size > 1 }.forEach { (day, w) ->
            out += "${shortDate(pr.days[day])}: aynı günü birden fazla kişi istiyor (${w.map { pr.persons[it.p].name }.distinct().joinToString(", ")})"
        }
        out += Solver(pr).crowdedWindows()
        return out.distinct()
    }
}
