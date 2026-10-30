package com.nobetmaker.core.export

import com.nobetmaker.core.calendar.CalendarService
import com.nobetmaker.core.calendar.Category
import com.nobetmaker.core.calendar.RotationGroup
import com.nobetmaker.core.calendar.monthNameTr
import com.nobetmaker.core.ledger.Ledger
import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.model.Person
import com.nobetmaker.core.model.RuleType
import com.nobetmaker.core.model.SeniorityCurve
import com.nobetmaker.core.rules.Availability
import com.nobetmaker.core.rules.Coefficients
import com.nobetmaker.core.rules.RosterIndex
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** Döküm satırı: kişinin bu ayki sayısı ve nedenleri (§14.3). */
class DokumRow(
    val person: Person,
    /** "3. yıl 11. ay", "uzatma" ya da (başlangıç bilinmiyorsa) null. */
    val stage: String?,
    val duty: Double,
    val hard: Double,
    /** Cumartesi ve pazar tutmuyor (kişiye özel kural ya da uzatma). */
    val noWeekend: Boolean,
    val count: Int,
    val hours: Int,
    val friday: Boolean,
    val saturday: Boolean,
    val sunday: Boolean,
    /** Son [Dokum.rotationMonths] ayda tuttuğu vizit; hafta sonu tutmuyorsa null. */
    val visits: Int?,
    /** Son yılda (cumartesiyi artanlardan, sırayla tutar). */
    val senior: Boolean,
    /** Son [Dokum.rotationMonths] aydaki son cumartesisi. */
    val lastSaturday: LocalDate?,
    /** Gelecek ayın artan cumartesi sırasındaki yeri (1'den); son yılda değilse null. */
    val turn: Int?,
    /** Tutulan − payı (gün); tatil tutmuyorsa null. */
    val bayram: Double?,
    val resmi: Double?,
    /** Elle sabitlenen ya da kuraldan gelen nöbet sayısı. */
    val fixedCount: Int?,
    /** Kıdem eğrisindeki yeri (ay); eğri kullanılmıyorsa null. */
    val curveMonths: Double?,
)

class Dokum(
    val month: YearMonth,
    val rotationMonths: Int,
    val rows: List<DokumRow>,
    val notes: List<String>,
    val curve: SeniorityCurve?,
    /** Eğri üzerindeki nokta için (ay → nöbet katsayısı). */
    val curveAt: (Double) -> Double,
    val curveYears: Int,
) {
    val title: String get() = "${monthNameTr(month.monthValue)} ${month.year}"

    companion object {
        fun balanceText(v: Double?): String = when {
            v == null -> "—"
            kotlin.math.abs(v) < 0.75 -> "dengede"
            v > 0 -> "${Math.round(v)} gün önde"
            else -> "${Math.round(-v)} gün geride"
        }

        fun coef(v: Double): String = String.format(Locale.ROOT, "%.2f", v).replace('.', ',')

        /** Zor günlerin hafta sonu tutanlara bölünüşü (§6.1): "herkes 2, fazla 2 gün çömezlere". */
        fun hardSplit(days: Int, people: Int): String {
            val base = days / people
            val rem = days % people
            return when {
                rem == 0 -> "herkes $base"
                rem * 2 <= people -> "herkes $base, fazla $rem gün çömezlere"
                else -> "herkes ${base + 1}, eksik ${people - rem} gün kıdemliden"
            }
        }

        /** Cumartesi sütunu: "ayda bir", "sırayla", "sıraya giriyor" ya da hafta sonu tutmuyorsa "—". */
        fun saturdayMain(r: DokumRow): String = when {
            r.noWeekend -> "—"
            r.senior -> "sırayla"
            r.turn != null -> "sıraya giriyor"
            else -> "ayda bir"
        }

        fun saturdaySub(r: DokumRow, months: Int): String = when {
            r.noWeekend -> "hafta sonu yok"
            r.turn != null -> "sırada ${r.turn}." + (r.lastSaturday?.let { " · son ${shortDate(it)}" } ?: "")
            r.visits != null -> "son $months ay: ${r.visits}"
            else -> ""
        }

        fun shortDate(d: LocalDate): String = "${d.dayOfMonth} ${monthNameTr(d.monthValue).take(3)}"
    }
}

/** Ayın listesine eklenen nöbet dökümü: herkesin sayısı, sırası ve bayram/tatil dengesi neden böyle (§14.3). */
object DokumBuilder {

    fun build(data: AppData, month: YearMonth): Dokum {
        val cal = CalendarService(data.dayOverrides, data.settings)
        val avail = Availability(data, cal)
        val coef = avail.coefficients
        val roster = RosterIndex(data)
        val ledger = Ledger(data, cal, avail, roster)
        val first = month.atDay(1)
        val last = month.atEndOfMonth()
        val mid = month.atDay(15)
        val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
        val persons = data.persons.filter { p -> days.any { p.isActiveOn(it) } }.sortedByDescending { it.seniority }
        val months = data.settings.rotationMonths

        val long = ledger.longMemory(persons, last.plusDays(1), emptyList())
        val bayramIdx = long.categories.indexOf(Category.BAYRAM)
        val resmiIdx = long.categories.indexOf(Category.RESMI_TATIL)

        val plan = data.month(month)
        val manual = plan?.quotas.orEmpty().filter { it.manual }.associate { it.personId to it.final }
        val ruled = persons.mapNotNull { p ->
            p.rules.firstOrNull { it.type == RuleType.MONTHLY_COUNT && it.count != null && days.any { d -> it.appliesOn(d) } }
                ?.let { p.id to it.count!! }
        }.toMap()

        val visitFrom = month.minusMonths((months - 1).toLong()).atDay(1)
        fun isVisit(d: LocalDate) = cal.rotationGroup(d, history = d.isBefore(first)) == RotationGroup.VIZIT
        // Gelecek ayın artan cumartesi sırası: son yıldakiler, en uzun süredir cumartesi tutmayan önce (Ledger ile aynı).
        val next = month.plusMonths(1)
        val nextFirst = next.atDay(1)
        val queueFrom = next.minusMonths(months.toLong()).atDay(1)
        fun lastVisit(p: Person, from: LocalDate): LocalDate? {
            var d = last
            while (!d.isBefore(from)) {
                if (roster.official[d] == p.id && isVisit(d)) return d
                d = d.minusDays(1)
            }
            return null
        }
        val nextSat = nextSaturday(nextFirst)
        val seniorQueue = data.persons
            .filter { p ->
                p.isActiveOn(nextFirst) && coef.isSenior(p, nextFirst) && avail.ruleBlock(p, nextSat) == null
            }
            .sortedWith(compareBy<Person> { lastVisit(it, queueFrom) ?: LocalDate.MIN }.thenByDescending { it.seniority })
        val rows = persons.map { p ->
            val mine = days.filter { roster.official[it] == p.id }
            fun has(dow: DayOfWeek) = mine.any { it.dayOfWeek == dow && cal.category(it).memory != com.nobetmaker.core.calendar.Memory.LONG }
            val noWeekend = avail.ruleBlock(p, nextSaturday(mid)) != null && avail.ruleBlock(p, nextSaturday(mid).plusDays(1)) != null
            val noHoliday = p.rules.any { it.type == RuleType.NO_HOLIDAY && it.appliesOn(mid) } || coef.inExtension(p, mid)
            var visits = 0
            var d = visitFrom
            while (!d.isAfter(last)) {
                if (roster.official[d] == p.id && isVisit(d)) visits++
                d = d.plusDays(1)
            }
            val c = coef.on(p, mid)
            DokumRow(
                person = p,
                stage = coef.stageText(p, mid),
                duty = c.duty,
                hard = c.hardDay,
                noWeekend = noWeekend,
                count = mine.size,
                hours = mine.sumOf { cal.hours(it) },
                friday = has(DayOfWeek.FRIDAY),
                saturday = has(DayOfWeek.SATURDAY),
                sunday = has(DayOfWeek.SUNDAY),
                visits = if (noWeekend) null else visits,
                senior = coef.isSenior(p, mid) && !noWeekend,
                lastSaturday = lastVisit(p, queueFrom),
                turn = seniorQueue.indexOfFirst { it.id == p.id }.takeIf { it >= 0 && !noWeekend }?.plus(1),
                bayram = if (noHoliday) null else long.base.getValue(p.id)[bayramIdx],
                resmi = if (noHoliday) null else long.base.getValue(p.id)[resmiIdx],
                fixedCount = manual[p.id] ?: ruled[p.id],
                curveMonths = if (coef.usesCurve(p)) coef.residencyMonths(p, mid) else null,
            )
        }

        val notes = ArrayList<String>()
        val curve = data.settings.curve
        if (curve.enabled && rows.any { it.curveMonths != null }) {
            notes += "Nöbet sayısı asistanlık yılından: 1–${curve.years - 1}. yıl 1,00 → ${Dokum.coef(curve.earlyDutyEnd)}, " +
                "${curve.years}. yıl ${Dokum.coef(curve.finalDutyStart)} → ${Dokum.coef(curve.finalDutyEnd)}, uzatma ${Dokum.coef(curve.extension)}."
            if (coef.teamSize(month) < curve.smallTeam) notes += "Ekip ${curve.smallTeam} kişiden az olduğu için son yıl indirimi yarıya indi."
        } else {
            notes += "Nöbet sayısı herkesin katsayısına göre dağıtılır."
        }
        notes += "Cumartesi: son yıla girmemiş herkes ayda bir tutar; artanlar son yıldakilere sırayla gider."
        if (seniorQueue.isNotEmpty()) {
            notes += "Artan cumartesi sırası (${monthNameTr(next.monthValue)} ${next.year}): " + seniorQueue.joinToString(" → ") { it.name } + "."
            val joining = seniorQueue.filter { !coef.isSenior(it, mid) }.sortedBy { it.seniority }
            if (joining.isNotEmpty()) notes += "${joinNames(joining.map { it.name })} son yıla giriyor; cumartesi sırasına katılır."
        }
        // Zor günler (§6.1): hafta sonu tutan herkes eşit; fazlası çömeze, eksiği kıdemliden.
        val hardGroups = setOf(RotationGroup.VIZIT, RotationGroup.CUMA, RotationGroup.PAZAR)
        val hardDays = days.count { cal.rotationGroup(it, history = false) in hardGroups }
        val weekenders = rows.count { !it.noWeekend }
        notes += "Zor günler (cuma, cumartesi, pazar): hafta sonu tutan herkes eşit sayıda tutar; fazla gün önce çömezlere, eksik gün önce kıdemliden, kademesinde sırayla."
        if (hardDays > 0 && weekenders > 0) notes += "Bu ay $hardDays zor gün, $weekenders kişi: ${Dokum.hardSplit(hardDays, weekenders)}."
        notes += "Cumartesi tutan kıdemliye pazar, kimseye aynı ay üçü birden yazılmaz; zor gün yetmezse en az esneyen dağılım seçilir."
        notes += "Hoca kararı olan ay o kişi hafta sonu sırasına girmez, zor gün defterine de yazılmaz. Uzatmada cuma, cumartesi, pazar ve tatil yok."
        if (days.any { cal.category(it) == Category.YILBASI }) notes += "31 Aralık'ı daha önce tutan mümkünse bir daha tutmaz."
        notes += "Pazartesi ve perşembe son $months aya göre herkese eşit sırayla döner."
        val satDays = days.filter { it.dayOfWeek == DayOfWeek.SATURDAY && cal.rotationGroup(it, history = false) == RotationGroup.VIZIT }
        if (satDays.isNotEmpty() && plan?.current != null) {
            val without = rows.filter { r ->
                !r.noWeekend && !r.senior && !r.saturday && satDays.any { avail.hardReasons(r.person, it).isEmpty() || roster.official[it] == r.person.id }
            }.sortedBy { it.person.seniority }
            if (without.isNotEmpty()) {
                notes += "Bu ay cumartesi tutmayan ${joinNames(without.map { it.person.name })} gelecek ay önce yazılır."
            }
        }
        val since = maxOf(roster.official.keys.minOrNull() ?: data.settings.longMemoryStart, data.settings.longMemoryStart)
        notes += "Bayram, resmi tatil ve yılbaşı ilk listeden (${monthNameTr(since.monthValue)} ${since.year}) beri sayılır; geride olan önce yazılır."
        notes += "Yeni başlayana ilk ay vizit yazılmaz. Takaslar hesabı değiştirmez."
        // Hoca kararı borç yazmaz; bizim elle verdiğimiz sayının farkı sonraki aylarda dengelenir (§5.4).
        val decided = rows.filter { r -> r.person.id in ruled || plan?.quotas.orEmpty().any { it.personId == r.person.id && it.manual && it.decision } }
            .sortedBy { it.person.seniority }
        if (decided.isNotEmpty()) {
            notes += "Hoca kararı: " + decided.joinToString(", ") { "${it.person.name} ${it.fixedCount}" } + "; kalan günler diğerlerine katsayıyla dağıtıldı."
        }
        val ownFixed = rows.filter { it.fixedCount != null && it !in decided }.sortedBy { it.person.seniority }
        if (ownFixed.isNotEmpty()) {
            notes += "Elle verilen sayı: " + ownFixed.joinToString(", ") { "${it.person.name} ${it.fixedCount}" } + " (fark sonraki aylarda dengelenir)."
        }
        val countStart = YearMonth.from(data.settings.countBalanceStart)
        if (month >= countStart) {
            notes += "Nöbet sayıları ${monthNameTr(countStart.monthValue)} ${countStart.year} listesinden beri dengelenir: fazla tutan sonraki ay az tutar."
        }
        notes += "Aynı ay içinde kimse kendinden kıdemliden az tutmaz; artan nöbet önce en çömeze."

        return Dokum(
            month = month,
            rotationMonths = months,
            rows = rows,
            notes = notes,
            curve = if (curve.enabled) curve else null,
            curveAt = { m -> coef.curveAt(m).first },
            curveYears = curve.years,
        )
    }

    private fun nextSaturday(d: LocalDate): LocalDate = d.plusDays(((DayOfWeek.SATURDAY.value - d.dayOfWeek.value + 7) % 7).toLong())

    private fun joinNames(names: List<String>): String =
        if (names.size <= 1) names.joinToString() else names.dropLast(1).joinToString(", ") + " ve " + names.last()
}
