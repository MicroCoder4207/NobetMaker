package com.nobetmaker.core.export

import com.nobetmaker.core.calendar.CalendarService
import com.nobetmaker.core.calendar.monthNameTr
import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.model.Person
import com.nobetmaker.core.rules.RosterIndex
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class DayCell(
    val date: LocalDate,
    val inMonth: Boolean,
    val official: Person?,
    val actual: Person?,
    val holidayName: String?,
    val holiday: Boolean,
    val weekend: Boolean,
    val visit: Boolean,
    val visitUncertain: Boolean,
    val hours: Int,
    val flagged: Boolean = false,
)

class PersonTotal(val person: Person, val count: Int, val hours: Int, val weekendOrHoliday: Int)

/** Bir ayın görsel/Excel çıktısı için hazırlanmış görünümü (§14). */
class MonthView(
    val month: YearMonth,
    val title: String,
    val showActual: Boolean,
    val weeks: List<List<DayCell>>,
    val totals: List<PersonTotal>,
    val leaves: List<String>,
) {
    val monthTitle: String get() = "${monthNameTr(month.monthValue)} ${month.year}"
    val days: List<DayCell> get() = weeks.flatten().filter { it.inMonth }
}

object MonthViewBuilder {

    fun build(data: AppData, month: YearMonth, showActual: Boolean, flagged: Set<LocalDate> = emptySet()): MonthView {
        val cal = CalendarService(data.dayOverrides, data.settings)
        val roster = RosterIndex(data)
        val persons = data.persons.associateBy { it.id }
        val first = month.atDay(1)
        val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
        val last = month.atEndOfMonth()
        val end = last.plusDays((7 - last.dayOfWeek.value).toLong())

        val weeks = ArrayList<List<DayCell>>()
        var d = start
        while (!d.isAfter(end)) {
            val week = (0 until 7).map { k ->
                val date = d.plusDays(k.toLong())
                val info = cal.day(date)
                DayCell(
                    date = date,
                    inMonth = YearMonth.from(date) == month,
                    official = roster.official[date]?.let { persons[it] },
                    actual = roster.actual[date]?.let { persons[it] },
                    holidayName = info.name,
                    holiday = cal.isOfficialHoliday(date) || info.kind == com.nobetmaker.core.model.DayKind.YILBASI,
                    weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY,
                    visit = cal.hasVisit(date),
                    visitUncertain = cal.visit(date) == com.nobetmaker.core.model.Visit.BELIRSIZ,
                    hours = cal.hours(date),
                    flagged = date in flagged,
                )
            }
            weeks += week
            d = d.plusDays(7)
        }

        val inMonth = weeks.flatten().filter { it.inMonth }
        val shown = { c: DayCell -> if (showActual) c.actual else c.official }
        val totals = data.persons.sortedBy { it.seniority }.mapNotNull { p ->
            val mine = inMonth.filter { shown(it)?.id == p.id }
            if (mine.isEmpty()) null
            else PersonTotal(p, mine.size, mine.sumOf { it.hours }, mine.count { it.weekend || it.holiday })
        }
        val leaves = data.periods
            .filter { !it.end.isBefore(first) && !it.start.isAfter(last) }
            .sortedBy { it.start }
            .mapNotNull { p ->
                val name = persons[p.personId]?.name ?: return@mapNotNull null
                val range = if (p.start == p.end) "${p.start.dayOfMonth} ${monthNameTr(p.start.monthValue)}"
                else if (p.start.month == p.end.month) "${p.start.dayOfMonth}–${p.end.dayOfMonth} ${monthNameTr(p.end.monthValue)}"
                else "${p.start.dayOfMonth} ${monthNameTr(p.start.monthValue)} – ${p.end.dayOfMonth} ${monthNameTr(p.end.monthValue)}"
                "$range → $name (${p.type.label}${if (p.tentative) ", belirsiz" else ""})"
            }
        return MonthView(month, data.settings.excelTitle, showActual, weeks, totals, leaves)
    }
}
