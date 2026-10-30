package com.nobetmaker.core.rules

import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.model.CoefficientPeriod
import com.nobetmaker.core.model.PeriodType
import com.nobetmaker.core.model.Person
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.floor

/**
 * Kişinin bir gündeki katsayısı (§5.1-9). Asistanlık başlangıcı biliniyorsa kıdem eğrisinden gelir;
 * herkes aynı eğriden geçer. Uzun izin (doğum izni) asistanlık süresinden düşülür. Nöbet tutan kişi sayısı
 * azalınca son yıl ve uzatma indirimi yarıya iner. Eğri kapalıysa ya da kişi elle girilmişse kişinin kendi
 * katsayıları kullanılır.
 */
class Coefficients(private val data: AppData) {

    private val curve = data.settings.curve
    private val longLeaves = data.periods.filter { it.type == PeriodType.DOGUM_IZNI }.groupBy { it.personId }
    private val teamSizes = HashMap<YearMonth, Int>()

    /** Son yılın başladığı ay (ör. 4 yıllık asistanlıkta 36). */
    val lastYearStart: Int get() = (curve.years - 1) * 12
    val totalMonths: Int get() = curve.years * 12

    fun usesCurve(person: Person): Boolean = curve.enabled && person.autoCoefficients && person.residencyStart != null

    private fun leaveDays(person: Person, until: LocalDate): Long {
        val start = person.residencyStart ?: return 0
        return longLeaves[person.id].orEmpty().sumOf { p ->
            val s = maxOf(p.start, start)
            val e = minOf(p.end, until)
            if (e.isBefore(s)) 0L else ChronoUnit.DAYS.between(s, e) + 1
        }
    }

    /** Asistanlıkta geçen süre (ay); uzun izinler düşülür. Başlangıç bilinmiyorsa null. */
    fun residencyMonths(person: Person, date: LocalDate): Double? {
        val start = person.residencyStart ?: return null
        if (!date.isAfter(start)) return 0.0
        val days = ChronoUnit.DAYS.between(start, date) - leaveDays(person, date)
        return days.coerceAtLeast(0) / DAYS_PER_MONTH
    }

    /** Eğrinin [months] aydaki (nöbet, zor gün) değeri; küçük ekip düzeltmesi yok. */
    fun curveAt(months: Double): Pair<Double, Double> {
        val l = lastYearStart.toDouble()
        val e = totalMonths.toDouble()
        return when {
            months <= RAMP_START -> 1.0 to 1.0
            months < l -> {
                val t = (months - RAMP_START) / (l - RAMP_START)
                (1.0 - (1.0 - curve.earlyDutyEnd) * t) to (1.0 - (1.0 - curve.earlyHardEnd) * t)
            }
            months < e -> {
                val t = (months - l) / 12.0
                (curve.finalDutyStart + (curve.finalDutyEnd - curve.finalDutyStart) * t) to
                    (curve.finalHardStart + (curve.finalHardEnd - curve.finalHardStart) * t)
            }
            else -> curve.extension to curve.extension
        }
    }

    fun on(person: Person, date: LocalDate): CoefficientPeriod {
        if (!usesCurve(person)) return person.coefficientOn(date)
        val m = residencyMonths(person, date) ?: return person.coefficientOn(date)
        val (duty, hard) = curveAt(m)
        val halve = m >= lastYearStart && teamSize(YearMonth.from(date)) < curve.smallTeam
        fun relief(v: Double) = if (halve) 1.0 - (1.0 - v) / 2 else v
        return CoefficientPeriod(duty = relief(duty), hardDay = relief(hard))
    }

    /** Eğriye göre uzatmada mı (4 yılı doldurmuş); uzatmada cumartesi, pazar ve tatil yazılmaz. */
    fun inExtension(person: Person, date: LocalDate): Boolean =
        usesCurve(person) && (residencyMonths(person, date) ?: 0.0) >= totalMonths

    /** Son yılda (ya da uzatmada) mı; eğri kullanılmıyorsa elle girilen zor gün katsayısı 0,80'in altındaysa kıdemli sayılır. */
    fun isSenior(person: Person, date: LocalDate): Boolean {
        if (!usesCurve(person)) return person.coefficientOn(date).hardDay < 0.80
        val m = residencyMonths(person, date) ?: return false
        return m >= lastYearStart
    }

    /** "3. yıl 11. ay" ya da "uzatma"; başlangıç bilinmiyorsa null. */
    fun stageText(person: Person, date: LocalDate): String? {
        val m = residencyMonths(person, date) ?: return null
        if (m >= totalMonths) return "uzatma"
        val whole = floor(m).toInt()
        return "${whole / 12 + 1}. yıl ${whole % 12 + 1}. ay"
    }

    /** Asistanlıkta [months]. ayın dolduğu tarih (uzun izinler eklenir). */
    fun dateAtMonths(person: Person, months: Int): LocalDate? {
        val start = person.residencyStart ?: return null
        var d = start.plusMonths(months.toLong())
        // İzin günleri kadar ileri kayar; izin aralığı yeni tarihi de etkileyebileceği için birkaç tur.
        repeat(3) { d = start.plusMonths(months.toLong()).plusDays(leaveDays(person, d)) }
        return d
    }

    /** Ayın 15'inde nöbet tutan (ekipte, sayıyı düşüren dönemde olmayan) kişi sayısı. */
    fun teamSize(month: YearMonth): Int = teamSizes.getOrPut(month) {
        val mid = month.atDay(15)
        data.persons.count { p ->
            p.isActiveOn(mid) && data.periods.none { it.personId == p.id && it.covers(mid) && it.reducesQuota }
        }
    }

    companion object {
        const val DAYS_PER_MONTH = 30.4375

        /** İlk aylar (nöbete başlamadan önceki dönem) 1,00; eğri bu aydan sonra inmeye başlar. */
        const val RAMP_START = 6.0
    }
}
