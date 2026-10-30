package com.nobetmaker.core.rules

import com.nobetmaker.core.calendar.CalendarService
import com.nobetmaker.core.calendar.Category
import com.nobetmaker.core.calendar.trFormat
import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.model.DutyRequest
import com.nobetmaker.core.model.MonthStatus
import com.nobetmaker.core.model.Period
import com.nobetmaker.core.model.Person
import com.nobetmaker.core.model.Priority
import com.nobetmaker.core.model.RequestType
import com.nobetmaker.core.model.RuleType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Sert kurallardan doğan uygunluk ve paylaşım kuralları (§4.1, §6). */
class Availability(data: AppData, private val cal: CalendarService) {

    private val periodsByPerson = data.periods.groupBy { it.personId }
    private val requestsByPerson = data.requests.groupBy { it.personId }
    val coefficients = Coefficients(data)

    /** Kişinin kayıtlardaki (taslak olmayan aylar ve geçmiş olaylar) ilk nöbeti. */
    private val firstRecord = HashMap<String, LocalDate>()

    /** Kayıtlardaki son nöbet günü. */
    private val recordedEnd: LocalDate? = run {
        var end: LocalDate? = null
        for (m in data.months) {
            if (m.status == MonthStatus.TASLAK) continue
            for (a in m.current?.assignments.orEmpty()) {
                firstRecord.merge(a.personId, a.date) { x, y -> minOf(x, y) }
                if (end == null || a.date.isAfter(end)) end = a.date
            }
        }
        for (h in data.historyEvents) {
            val s = h.start ?: continue
            for (e in h.entries) if (e.days > 0) firstRecord.merge(e.personId, s) { x, y -> minOf(x, y) }
        }
        end
    }

    /**
     * Adalet hesabında kişinin ekipte sayıldığı ilk gün (§2.2). Nöbete katılış girilmişse o tarihtir;
     * girilmemişse kişinin kayıtlardaki ilk nöbeti. Hiç kaydı olmayan kişi borçsuz başlar: kayıtların bittiği
     * günün ertesinden (ya da daha sonraysa bölüme katılıştan) itibaren sayılır. Bölüme katılış tek başına
     * ekipte sayılmak için yetmez; bölüme katılınca hemen nöbete girilmiyor.
     */
    private val presence: Map<String, LocalDate?> = data.persons.associate { p ->
        p.id to (p.dutyStart ?: firstRecord[p.id] ?: listOfNotNull(p.joinDate, recordedEnd?.plusDays(1)).maxOrNull())
    }

    fun presenceStart(person: Person): LocalDate? = if (person.id in presence) presence[person.id] else person.dutyFrom

    /**
     * Kişinin nöbete başladığı ay: nöbete katılış ya da kayıtlardaki ilk nöbet. Hiç kaydı yoksa (ve kayıtlar
     * varsa) henüz başlamamıştır; planlanan ay ilk ayıdır.
     */
    fun firstDutyMonth(person: Person): YearMonth? =
        (person.dutyStart ?: firstRecord[person.id])?.let { YearMonth.from(it) }

    private fun isFirstDutyMonth(person: Person, date: LocalDate): Boolean {
        val first = firstDutyMonth(person) ?: return recordedEnd?.let { date.isAfter(it) } ?: false
        return YearMonth.from(date) == first
    }

    private fun isHolidayCategory(date: LocalDate): Boolean =
        cal.category(date).let { it == Category.BAYRAM || it == Category.RESMI_TATIL || it == Category.YILBASI }

    fun ruleBlock(person: Person, date: LocalDate): String? {
        for (rule in person.rules) {
            if (!rule.appliesOn(date)) continue
            val blocked = when (rule.type) {
                RuleType.NO_SATURDAY -> date.dayOfWeek == DayOfWeek.SATURDAY
                RuleType.NO_SUNDAY -> date.dayOfWeek == DayOfWeek.SUNDAY
                RuleType.NO_HOLIDAY -> cal.isOfficialHoliday(date) || isHolidayCategory(date)
                RuleType.MONTHLY_COUNT -> false
            }
            if (blocked) return "Kişiye özel kural: ${rule.type.label}"
        }
        // Uzatmada hafta sonu (cuma dahil; zor gün düzeni, §6.1) ve tatil yazılmaz.
        if (coefficients.inExtension(person, date) &&
            (cal.isWeekend(date) || date.dayOfWeek == DayOfWeek.FRIDAY || cal.isOfficialHoliday(date) || isHolidayCategory(date))
        ) return "Uzatmada: cuma, cumartesi, pazar ve tatil yok"
        return null
    }

    fun blockingPeriod(person: Person, date: LocalDate): Period? =
        periodsByPerson[person.id]?.firstOrNull { it.covers(date) && !it.worksDuty && !it.tentative }

    fun tentativePeriod(person: Person, date: LocalDate): Period? =
        periodsByPerson[person.id]?.firstOrNull { it.covers(date) && !it.worksDuty && it.tentative }

    fun reducesQuotaOn(person: Person, date: LocalDate): Boolean =
        periodsByPerson[person.id]?.any { it.covers(date) && it.reducesQuota } == true

    fun requestsOf(person: Person): List<DutyRequest> = requestsByPerson[person.id].orEmpty()

    /**
     * Bu günü kesin olarak dışlayan sebepler. Boşsa kişi o gün nöbet tutabilir.
     * @param requests kesin "olmasın" istekleri de sayılsın mı (isteklerin etkisini ölçmek için kapatılır)
     */
    fun hardReasons(person: Person, date: LocalDate, requests: Boolean = true): List<String> {
        val reasons = ArrayList<String>(2)
        if (!person.isActiveOn(date)) reasons += "Ekipte değil"
        blockingPeriod(person, date)?.let { reasons += "${it.type.label} (${periodText(it)})" }
        ruleBlock(person, date)?.let { reasons += it }
        if (firstMonthVisit(person, date)) reasons += "İlk nöbet ayı: vizit yok"
        if (requests) for (r in requestsOf(person)) {
            if (r.priority == Priority.KESIN && avoids(r, date)) reasons += "Kesin istek: ${RequestText.describe(r)}"
        }
        return reasons
    }

    /** Kota hesabında kişinin o gün sayılıp sayılmadığı (izin kotayı düşürmez, §5.2). */
    fun countsForQuota(person: Person, date: LocalDate): Boolean =
        person.isActiveOn(date) && !reducesQuotaOn(person, date)

    /** Adalet defterinde o günün payına ortak olup olmadığı (§6). */
    fun eligibleForShare(person: Person, date: LocalDate): Boolean =
        countsForQuota(person, date) && ruleBlock(person, date) == null &&
            presenceStart(person).let { it == null || !date.isBefore(it) } &&
            !firstMonthVisit(person, date)

    /** İlk nöbet ayındaki vizit günü: yazılmaz ve borç da yazmaz. */
    private fun firstMonthVisit(person: Person, date: LocalDate): Boolean =
        cal.hasVisit(date) && !isHolidayCategory(date) && isFirstDutyMonth(person, date)

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
