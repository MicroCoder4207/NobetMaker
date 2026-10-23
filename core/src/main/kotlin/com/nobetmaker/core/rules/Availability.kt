package com.nobetmaker.core.rules

import com.nobetmaker.core.calendar.CalendarService
import com.nobetmaker.core.calendar.Category
import com.nobetmaker.core.calendar.trFormat
import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.model.DutyRequest
import com.nobetmaker.core.model.Period
import com.nobetmaker.core.model.Person
import com.nobetmaker.core.model.Priority
import com.nobetmaker.core.model.RequestType
import com.nobetmaker.core.model.RuleType
import java.time.DayOfWeek
import java.time.LocalDate

/** Sert kurallardan doğan uygunluk ve paylaşım kuralları (§4.1, §6). */
class Availability(data: AppData, private val cal: CalendarService) {

    private val periodsByPerson = data.periods.groupBy { it.personId }
    private val requestsByPerson = data.requests.groupBy { it.personId }

    fun ruleBlock(person: Person, date: LocalDate): String? {
        for (rule in person.rules) {
            if (!rule.appliesOn(date)) continue
            val blocked = when (rule.type) {
                RuleType.NO_SATURDAY -> date.dayOfWeek == DayOfWeek.SATURDAY
                RuleType.NO_SUNDAY -> date.dayOfWeek == DayOfWeek.SUNDAY
                RuleType.NO_HOLIDAY -> cal.isOfficialHoliday(date) ||
                    cal.category(date).let { it == Category.BAYRAM || it == Category.RESMI_TATIL || it == Category.YILBASI }
            }
            if (blocked) return "Kişiye özel kural: ${rule.type.label}"
        }
        return null
    }

    fun blockingPeriod(person: Person, date: LocalDate): Period? =
        periodsByPerson[person.id]?.firstOrNull { it.covers(date) && !it.worksDuty && !it.tentative }

    fun tentativePeriod(person: Person, date: LocalDate): Period? =
        periodsByPerson[person.id]?.firstOrNull { it.covers(date) && !it.worksDuty && it.tentative }

    fun reducesQuotaOn(person: Person, date: LocalDate): Boolean =
        periodsByPerson[person.id]?.any { it.covers(date) && it.reducesQuota } == true

    fun requestsOf(person: Person): List<DutyRequest> = requestsByPerson[person.id].orEmpty()

    /** Bu günü kesin olarak dışlayan sebepler. Boşsa kişi o gün nöbet tutabilir. */
    fun hardReasons(person: Person, date: LocalDate): List<String> {
        val reasons = ArrayList<String>(2)
        if (!person.isActiveOn(date)) reasons += "Ekipte değil"
        blockingPeriod(person, date)?.let { reasons += "${it.type.label} (${periodText(it)})" }
        ruleBlock(person, date)?.let { reasons += it }
        for (r in requestsOf(person)) {
            if (r.priority == Priority.KESIN && avoids(r, date)) reasons += "Kesin istek: ${RequestText.describe(r)}"
        }
        return reasons
    }

    /** Kota hesabında kişinin o gün sayılıp sayılmadığı (izin kotayı düşürmez, §5.2). */
    fun countsForQuota(person: Person, date: LocalDate): Boolean =
        person.isActiveOn(date) && !reducesQuotaOn(person, date)

    /** Adalet defterinde o günün payına ortak olup olmadığı (§6). */
    fun eligibleForShare(person: Person, date: LocalDate): Boolean =
        countsForQuota(person, date) && ruleBlock(person, date) == null

    companion object {
        fun avoids(r: DutyRequest, date: LocalDate): Boolean = when (r.type) {
            RequestType.AVOID_DATES -> date in r.dates
            RequestType.AVOID_RANGE -> r.inRange(date)
            else -> false
        }

        fun periodText(p: Period): String =
            if (p.start == p.end) p.start.trFormat() else "${p.start.trFormat()} – ${p.end.trFormat()}"
    }
}

object RequestText {
    private fun d(date: LocalDate) = "${date.dayOfMonth} ${com.nobetmaker.core.calendar.monthNameTr(date.monthValue)}"

    private fun dates(list: List<LocalDate>) = list.sorted().joinToString(", ") { d(it) }

    private fun range(r: DutyRequest) = if (r.rangeStart != null && r.rangeEnd != null) "${d(r.rangeStart)} – ${d(r.rangeEnd)}" else "?"

    fun describe(r: DutyRequest): String = when (r.type) {
        RequestType.AVOID_DATES -> "${dates(r.dates)} olmasın"
        RequestType.AVOID_RANGE -> "${range(r)} olmasın"
        RequestType.WANT_DATES -> "${dates(r.dates)} istiyor"
        RequestType.WEEKLY_MAX -> "${range(r)} arası haftada en fazla ${r.maxPerWeek ?: 1}"
        RequestType.CLUSTER -> "nöbetler ${range(r)} arasında yoğun olsun"
    }
}
