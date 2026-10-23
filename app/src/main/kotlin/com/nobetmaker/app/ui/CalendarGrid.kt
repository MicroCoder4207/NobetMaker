package com.nobetmaker.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobetmaker.core.calendar.CalendarService
import com.nobetmaker.core.calendar.trShort
import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.model.DayKind
import com.nobetmaker.core.model.Person
import com.nobetmaker.core.model.Visit
import com.nobetmaker.core.rules.RosterIndex
import com.nobetmaker.core.solver.MonthProblem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class GridDay(
    val date: LocalDate,
    val inMonth: Boolean,
    val main: Person?,
    val other: Person?,
    val locked: Boolean,
    val visit: Boolean,
    val visitUncertain: Boolean,
    val holiday: Boolean,
    val holidayName: String?,
    val weekend: Boolean,
)

private fun weeksOf(month: YearMonth): List<LocalDate> {
    val first = month.atDay(1)
    val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
    val last = month.atEndOfMonth()
    val end = last.plusDays((7 - last.dayOfWeek.value).toLong())
    return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
}

/** Kayıtlı listeden ızgara: resmi ya da gerçekleşen görünüm. */
fun gridFromData(data: AppData, month: YearMonth, actual: Boolean): List<GridDay> {
    val cal = CalendarService(data.dayOverrides, data.settings)
    val roster = RosterIndex(data)
    val persons = data.persons.associateBy { it.id }
    val locks = data.months.flatMap { m -> m.current?.assignments.orEmpty() }.filter { it.locked }.map { it.date }.toSet()
    val editable = data.month(month)?.status == com.nobetmaker.core.model.MonthStatus.TASLAK
    return weeksOf(month).map { d ->
        val info = cal.day(d)
        val off = roster.official[d]?.let { persons[it] }
        val act = roster.actual[d]?.let { persons[it] }
        GridDay(
            date = d,
            inMonth = YearMonth.from(d) == month,
            main = if (actual) act else off,
            other = if (actual) off else act,
            locked = editable && YearMonth.from(d) == month && d in locks,
            visit = cal.hasVisit(d),
            visitUncertain = cal.visit(d) == Visit.BELIRSIZ,
            holiday = cal.isOfficialHoliday(d) || info.kind == DayKind.YILBASI,
            holidayName = info.name,
            weekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY,
        )
    }
}

/** Henüz kaydedilmemiş bir çözümden ızgara (seçenek önizlemesi). */
fun gridFromSolution(data: AppData, pr: MonthProblem, assignment: IntArray): List<GridDay> {
    val cal = CalendarService(data.dayOverrides, data.settings)
    val roster = RosterIndex(data)
    val persons = data.persons.associateBy { it.id }
    return weeksOf(pr.month).map { d ->
        val i = pr.days.indexOf(d)
        val info = cal.day(d)
        GridDay(
            date = d,
            inMonth = i >= 0,
            main = if (i >= 0) pr.persons[assignment[i]] else roster.official[d]?.let { persons[it] },
            other = null,
            locked = i >= 0 && pr.locked[i] != -1,
            visit = cal.hasVisit(d),
            visitUncertain = cal.visit(d) == Visit.BELIRSIZ,
            holiday = cal.isOfficialHoliday(d) || info.kind == DayKind.YILBASI,
            holidayName = info.name,
            weekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY,
        )
    }
}

@Composable
fun CalendarGrid(
    days: List<GridDay>,
    modifier: Modifier = Modifier,
    selected: LocalDate? = null,
    marked: LocalDate? = null,
    cellHeight: Int = 74,
    onDayClick: ((GridDay) -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    // Telefon genişliği (≈360dp) esas; tablette hücre ve yazılar orantılı büyür.
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val scale = (maxWidth.value / 360f).coerceIn(1f, 1.9f)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, scheme.outlineVariant, RoundedCornerShape(12.dp))) {
            Row(Modifier.fillMaxWidth().background(scheme.primary)) {
                DayOfWeek.entries.forEach { dow ->
                    Text(
                        dow.trShort(),
                        modifier = Modifier.weight(1f).padding(vertical = (6 * scale).dp),
                        textAlign = TextAlign.Center,
                        color = scheme.onPrimary,
                        fontSize = (12 * scale).sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            days.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        DayCell(
                            day = day,
                            scale = scale,
                            modifier = Modifier.weight(1f).height((cellHeight * minOf(scale, 1.6f)).dp),
                            selected = day.date == selected,
                            marked = day.date == marked,
                            onClick = if (onDayClick != null && day.inMonth) ({ onDayClick(day) }) else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: GridDay, scale: Float, modifier: Modifier, selected: Boolean, marked: Boolean, onClick: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val bg = when {
        !day.inMonth -> scheme.surface
        day.holiday -> scheme.secondaryContainer.copy(alpha = 0.7f)
        day.weekend -> scheme.surfaceContainer
        else -> scheme.surface
    }
    val border = when {
        marked -> BorderStroke(2.dp, scheme.secondary)
        selected -> BorderStroke(2.dp, scheme.primary)
        else -> BorderStroke(0.5.dp, scheme.outlineVariant)
    }
    Box(
        modifier
            .background(bg)
            .border(border)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(3.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (day.inMonth && day.visit) {
                // Hücrenin kendi işareti olduğu belli olsun diye küçük dolu rozet.
                Text(
                    if (day.visitUncertain) "V?" else "V",
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(scheme.primaryContainer).padding(horizontal = (4 * scale).dp),
                    fontSize = (9 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onPrimaryContainer,
                )
            }
            if (day.locked) Icon(Icons.Default.Lock, contentDescription = "Kilitli", modifier = Modifier.size((10 * scale).dp), tint = scheme.onSurfaceVariant)
            Text(
                day.date.dayOfMonth.toString(),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
                fontSize = (11 * scale).sp,
                fontWeight = if (day.inMonth) FontWeight.Bold else FontWeight.Normal,
                color = if (day.inMonth) scheme.onSurface else scheme.outline,
            )
        }
        Column(Modifier.align(Alignment.Center).padding(top = (6 * scale).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val p = day.main
            if (p != null) {
                if (day.inMonth) {
                    Text(
                        p.name,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(personColor(p.color))
                            .padding(horizontal = (3 * scale).dp, vertical = (2 * scale).dp),
                        fontSize = (10.5 * scale).sp,
                        letterSpacing = 0.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        color = onPersonColor(p.color),
                    )
                } else {
                    Text(p.name, fontSize = (10 * scale).sp, color = scheme.outline, maxLines = 1)
                }
            }
            val o = day.other
            if (day.inMonth && o != null && o.id != p?.id) {
                Text("(${o.name})", fontSize = (9 * scale).sp, color = personColor(o.color), maxLines = 1, fontWeight = FontWeight.SemiBold)
            }
        }
        if (day.inMonth && day.holidayName != null) {
            Text(
                day.holidayName,
                modifier = Modifier.align(Alignment.BottomCenter),
                fontSize = (7 * scale).sp,
                lineHeight = (8 * scale).sp,
                color = scheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
