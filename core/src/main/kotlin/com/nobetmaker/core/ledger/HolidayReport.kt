package com.nobetmaker.core.ledger

import com.nobetmaker.core.calendar.CalendarService
import com.nobetmaker.core.calendar.Category
import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.model.DayKind
import com.nobetmaker.core.model.Person
import com.nobetmaker.core.model.RuleType
import com.nobetmaker.core.model.Visit
import com.nobetmaker.core.rules.Availability
import com.nobetmaker.core.rules.RosterIndex
import java.time.LocalDate

/** Bir tatil günü: kim tuttu (resmi ve gerçekte), arefe yarım, gün türü elle mi değişti. */
class HolidayDay(
    val date: LocalDate,
    val kind: DayKind,
    /** Dengede sayılan gün (arefe 0,5). */
    val weight: Double,
    /** Resmi kayıttaki kişi; null = uzman. */
    val official: String?,
    /** Takasla gerçekte tutan; resmiyle aynıysa null. */
    val actual: String?,
    val visit: Visit,
    /** Gün türü takvimdekinden farklı (elle değiştirildi). */
    val edited: Boolean,
)

class HolidayEvent(val category: Category, val name: String, val days: List<HolidayDay>) {
    val start: LocalDate get() = days.first().date
}

/** Kişinin bir kategorideki dengesi: tutulan − payı (pozitif = önde). */
class HolidayBalance(val held: Double, val expected: Double) {
    val diff: Double get() = held - expected
}

class HolidayReport(
    val since: LocalDate,
    val until: LocalDate,
    /** Yeniden eskiye. */
    val events: List<HolidayEvent>,
    val persons: List<Person>,
    val balances: Map<String, Map<Category, HolidayBalance>>,
    /** Tatil tutmayanlar (kişiye özel kural ya da uzatma); dengeleri gösterilmez. */
    val exempt: Set<String>,
)

/**
 * Bayram ve tatil kayıtları (§6.2): ilk listeden bu yana her bayram, resmi tatil ve yılbaşı günü, o günü kimin
 * tuttuğu ve bugünkü ekibin dengesi. Hesap, bayram dengesiyle aynı yerden (resmi liste) yapılır; ekranda bir gün
 * düzeltildiğinde denge de değişir.
 */
object HolidayReportBuilder {

    fun build(data: AppData, today: LocalDate = LocalDate.now()): HolidayReport {
        val cal = CalendarService(data.dayOverrides, data.settings)
        val avail = Availability(data, cal)
        val roster = RosterIndex(data)
        val listed = data.months.filter { it.current != null }.map { it.yearMonth }
        val since = maxOf(data.settings.longMemoryStart, listed.minOrNull()?.atDay(1) ?: data.settings.longMemoryStart)
        val until = listed.maxOrNull()?.atEndOfMonth() ?: today

        val grouped = LinkedHashMap<Pair<Category, String>, MutableList<HolidayDay>>()
        var d = since
        while (!d.isAfter(until)) {
            val cat = cal.category(d)
            if (cat in Category.LONG_MEMORY) {
                val official = roster.official[d]
                val day = cal.day(d)
                grouped.getOrPut(cat to cal.longEventKey(d)) { ArrayList() } += HolidayDay(
                    date = d,
                    kind = day.kind,
                    weight = cal.longWeight(d),
                    official = official,
                    actual = roster.actual[d]?.takeIf { it != official },
                    visit = cal.visit(d),
                    edited = day.kind != cal.builtInDay(d).kind,
                )
            }
            d = d.plusDays(1)
        }
        val events = grouped.map { (key, days) -> HolidayEvent(key.first, eventName(cal, key.first, days), days) }.sortedByDescending { it.start }

        val persons = data.persons.filter { it.isActiveOn(minOf(today, until)) }.sortedByDescending { it.seniority }
        val long = Ledger(data, cal, avail, roster).longMemory(persons, until.plusDays(1), emptyList())
        val balances = persons.associate { p ->
            p.id to long.categories.withIndex().associate { (i, c) ->
                c to HolidayBalance(long.days.getValue(p.id)[i], long.expected.getValue(p.id)[i])
            }
        }
        val ref = minOf(today, until)
        val exempt = persons.filter { p ->
            p.rules.any { it.type == RuleType.NO_HOLIDAY && it.appliesOn(ref) } || avail.coefficients.inExtension(p, ref)
        }.map { it.id }.toSet()
        return HolidayReport(since, until, events, persons, balances, exempt)
    }

    private fun eventName(cal: CalendarService, category: Category, days: List<HolidayDay>): String {
        val year = days.first().date.year
        val names = days.mapNotNull { cal.day(it.date).name }
        val base = when (category) {
            Category.BAYRAM -> names.firstOrNull { it.contains("Bayramı") && !it.contains("idari") }
                ?.substringBefore(" arifesi")?.replace(Regex(" \\d+\\. gün.*"), "")
            else -> names.firstOrNull { !it.endsWith("arifesi") } ?: names.firstOrNull()
        } ?: category.label
        return "$base $year"
    }
}
