@file:UseSerializers(LocalDateSerializer::class)

package com.nobetmaker.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate
import java.time.YearMonth

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

/** Uygulamanın bütün verisi tek bir belgede tutulur (bkz. docs/TASARIM.md §13). */
@Serializable
data class AppData(
    val schemaVersion: Int = AppData.SCHEMA_VERSION,
    val persons: List<Person> = emptyList(),
    val dayOverrides: List<DayInfo> = emptyList(),
    val periods: List<Period> = emptyList(),
    val requests: List<DutyRequest> = emptyList(),
    val months: List<MonthPlan> = emptyList(),
    val informalChanges: List<InformalChange> = emptyList(),
    val historyEvents: List<HistoryEvent> = emptyList(),
    val settings: Settings = Settings(),
    val changeLog: List<ChangeLogEntry> = emptyList(),
) {
    fun person(id: String): Person? = persons.firstOrNull { it.id == id }
    fun month(ym: YearMonth): MonthPlan? = months.firstOrNull { it.year == ym.year && it.month == ym.monthValue }

    companion object {
        const val SCHEMA_VERSION = 7
    }
}

// ---------------------------------------------------------------- Kişiler

@Serializable
data class Person(
    val id: String,
    val name: String,
    /** 1 = en kıdemli. Tarihlerden türetilmez (K21). */
    val seniority: Int,
    val color: String,
    /**
     * Bölüme katılış. Nöbete katılış boşsa kişi bu tarihten önce listeye yazılmaz. Adalet hesabına girmez;
     * bölüme katılınca hemen nöbete girilmiyor (§2.2).
     */
    val joinDate: LocalDate? = null,
    val leaveDate: LocalDate? = null,
    /** Asistanlık başlangıcı (başka hastanede de olabilir). Kıdem eğrisi bu tarihten hesaplanır (§5.1-9). */
    val residencyStart: LocalDate? = null,
    /**
     * Nöbete katılış; kişi bu tarihten önce listeye yazılmaz ve adalet hesabında bu tarihten itibaren ekipte
     * sayılır. Boşsa kayıtlardaki ilk nöbetinden çıkarılır.
     */
    val dutyStart: LocalDate? = null,
    /** Elle girilen katsayılar; [autoCoefficients] açıksa ve asistanlık başlangıcı biliniyorsa kullanılmaz. */
    val coefficients: List<CoefficientPeriod> = listOf(CoefficientPeriod()),
    val rules: List<PersonRule> = emptyList(),
    /** Katsayılar kıdem eğrisinden gelir (§5.1-9); kapalıysa [coefficients] kullanılır. */
    val autoCoefficients: Boolean = true,
) {
    /** Nöbet döneminin başlangıcı: nöbete katılış, yoksa bölüme katılış. */
    val dutyFrom: LocalDate? get() = dutyStart ?: joinDate

    fun isActiveOn(date: LocalDate): Boolean =
        (dutyFrom == null || !date.isBefore(dutyFrom)) && (leaveDate == null || !date.isAfter(leaveDate))

    fun coefficientOn(date: LocalDate): CoefficientPeriod =
        coefficients.filter { it.from == null || !it.from.isAfter(date) }
            .maxByOrNull { it.from ?: LocalDate.MIN }
            ?: coefficients.firstOrNull()
            ?: CoefficientPeriod()
}

@Serializable
data class CoefficientPeriod(
    val from: LocalDate? = null,
    val duty: Double = 1.0,
    val hardDay: Double = 1.0,
)

@Serializable
enum class RuleType(val label: String) {
    NO_SATURDAY("Cumartesi yok"),
    NO_SUNDAY("Pazar yok"),
    NO_HOLIDAY("Resmi tatil, bayram ve arefe yok"),
    /** Hocaların söylediği sabit sayı; her ayın nöbet sayısı bu sayıya sabitlenir, kalan günler diğerlerine dağılır. */
    MONTHLY_COUNT("Ayda sabit nöbet sayısı"),
}

@Serializable
data class PersonRule(
    val type: RuleType,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    /** [RuleType.MONTHLY_COUNT] için aylık nöbet sayısı. */
    val count: Int? = null,
) {
    fun appliesOn(date: LocalDate): Boolean =
        (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to))
}

// ---------------------------------------------------------------- Takvim

@Serializable
enum class DayKind(val label: String) {
    NORMAL("Normal"),
    RESMI_TATIL("Resmi tatil"),
    BAYRAM("Bayram"),
    AREFE("Arefe (yarım gün)"),
    IDARI_IZIN("İdari izin / köprü"),
    YILBASI("Yılbaşı (31 Aralık)"),
}

@Serializable
enum class Visit(val label: String) { VAR("Var"), YOK("Yok"), BELIRSIZ("Belirsiz") }

@Serializable
data class DayInfo(
    val date: LocalDate,
    val kind: DayKind = DayKind.NORMAL,
    val name: String? = null,
    /** Aynı bayramın günlerini gruplar (ör. "2026-kurban"). */
    val eventId: String? = null,
    val plannedVisit: Visit? = null,
    val actualVisit: Visit? = null,
    val extraPoints: Double = 0.0,
    val note: String? = null,
)

// ---------------------------------------------------------------- Dönemler

@Serializable
enum class PeriodType(val label: String, val defaultWorksDuty: Boolean, val defaultReducesQuota: Boolean) {
    YILLIK_IZIN("Yıllık izin", false, false),
    KONGRE("Kongre / eğitim izni", false, false),
    RAPOR("Rapor", false, false),
    HALK_SAGLIGI("Halk sağlığı rotasyonu", false, true),
    DIS_ROTASYON("Dış rotasyon", true, false),
    DOGUM_IZNI("Doğum izni / uzun izin", false, true),
    NOBET_MUAFIYETI("Nöbet muafiyeti", false, true),
    SINAV("Sınav", false, true),
    DIGER("Diğer", false, false),
}

@Serializable
data class Period(
    val id: String,
    val personId: String,
    val start: LocalDate,
    val end: LocalDate,
    val type: PeriodType,
    val worksDuty: Boolean = type.defaultWorksDuty,
    val reducesQuota: Boolean = type.defaultReducesQuota,
    /** Belirsiz dönem sert kural değil, "mümkünse" istek gibi uygulanır. */
    val tentative: Boolean = false,
    val note: String? = null,
) {
    fun covers(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

// ---------------------------------------------------------------- İstekler

@Serializable
enum class RequestType(val label: String) {
    AVOID_DATES("Şu gün(ler) olmasın"),
    AVOID_RANGE("Şu aralık olmasın"),
    WANT_DATES("Şu günü istiyor / gönüllü"),
    WEEKLY_MAX("Haftada en fazla"),
    CLUSTER("Nöbetler şu aralıkta yoğun olsun"),
}

@Serializable
enum class Priority(val label: String) {
    KESIN("Kesin"),
    MUMKUNSE("Mümkünse"),
    ONEMSIZ("Sıkışırsa önemli değil"),
}

@Serializable
data class DutyRequest(
    val id: String,
    val personId: String,
    val type: RequestType,
    val priority: Priority,
    val dates: List<LocalDate> = emptyList(),
    val rangeStart: LocalDate? = null,
    val rangeEnd: LocalDate? = null,
    val maxPerWeek: Int? = null,
    val originalText: String? = null,
) {
    fun inRange(date: LocalDate): Boolean =
        rangeStart != null && rangeEnd != null && !date.isBefore(rangeStart) && !date.isAfter(rangeEnd)

    /** İsteğin ilgilendiği günler; ay ekranında listelemek için. */
    fun touches(month: YearMonth): Boolean {
        val start = month.atDay(1)
        val end = month.atEndOfMonth()
        if (dates.any { !it.isBefore(start) && !it.isAfter(end) }) return true
        if (rangeStart != null && rangeEnd != null) return !rangeEnd.isBefore(start) && !rangeStart.isAfter(end)
        return false
    }
}

// ---------------------------------------------------------------- Aylık plan

@Serializable
enum class MonthStatus(val label: String) { TASLAK("Taslak"), YAYINLANDI("Kesinleşti"), KAPANDI("Kapandı") }

@Serializable
enum class AssignmentSource { OTOMATIK, ELLE }

@Serializable
data class Assignment(
    val date: LocalDate,
    val personId: String,
    val locked: Boolean = false,
    val source: AssignmentSource = AssignmentSource.OTOMATIK,
)

@Serializable
data class RosterVersion(
    val number: Int,
    val createdAt: String,
    val note: String? = null,
    val assignments: List<Assignment> = emptyList(),
) {
    fun personOn(date: LocalDate): String? = assignments.firstOrNull { it.date == date }?.personId
}

@Serializable
data class QuotaEntry(
    val personId: String,
    val suggested: Int,
    val final: Int,
    val carryIn: Double = 0.0,
    val carryOut: Double = 0.0,
    /** Elle sabitlendi; kalan günler diğerlerine algoritmayla dağıtılır. */
    val manual: Boolean = false,
    /**
     * Hoca kararı: kişinin bu ayki sayısı kararla belirlendi, ona borç ya da alacak yazılmaz (§5.4). Kapalıysa
     * elle sabitleme bizim düzeltmemizdir; fark sonraki aylarda o kişiye geri yansır.
     */
    val decision: Boolean = false,
)

@Serializable
data class MonthPlan(
    val year: Int,
    val month: Int,
    val status: MonthStatus = MonthStatus.TASLAK,
    val quotas: List<QuotaEntry> = emptyList(),
    /** Resmi liste sürümleri; sonuncusu geçerli olandır. */
    val versions: List<RosterVersion> = emptyList(),
) {
    val yearMonth: YearMonth get() = YearMonth.of(year, month)
    val current: RosterVersion? get() = versions.maxByOrNull { it.number }
}

// ---------------------------------------------------------------- Gayriresmi değişimler

@Serializable
data class InformalChange(
    val id: String,
    val date: LocalDate,
    val officialPersonId: String,
    val actualPersonId: String,
    /** Aynı takasın parçalarını birbirine bağlar. */
    val groupId: String? = null,
    val createdAt: String,
    val note: String? = null,
)

// ---------------------------------------------------------------- Geçmiş özel günler

@Serializable
enum class EventKind(val label: String) { BAYRAM("Bayram"), RESMI_TATIL("Resmi tatil"), YILBASI("Yılbaşı") }

@Serializable
data class HistoryEntry(
    val personId: String,
    val days: Int,
    val dates: List<LocalDate> = emptyList(),
)

/** Tam listesi olmayan eski dönemler için elle girilen bayram/tatil kaydı (§16). */
@Serializable
data class HistoryEvent(
    val id: String,
    val name: String,
    val kind: EventKind,
    val start: LocalDate? = null,
    val end: LocalDate? = null,
    val totalDays: Int,
    val entries: List<HistoryEntry> = emptyList(),
    val note: String? = null,
)

// ---------------------------------------------------------------- Ayarlar

@Serializable
data class PointWeights(
    val base: Double = 1.0,
    val holiday: Double = 0.5,
    val visit: Double = 1.0,
    val nextDayHoliday: Double = 0.5,
    val longWeekendBonus: Double = 0.3,
)

@Serializable
data class HourWeights(
    val workday: Int = 16,
    val holiday: Int = 24,
    val arefe: Int = 20,
    val postDutyDeduction: Int = 8,
)

@Serializable
data class PenaltyWeights(
    val longHolidayProtection: Double = 80.0,
    val requestMumkunse: Double = 60.0,
    val requestOnemsiz: Double = 10.0,
    /** Cuma/Pazar/Perşembe sırasından sapma (sapma² ile çarpılır). */
    val rotation: Double = 60.0,
    /** Cumartesi (vizit) sırasından sapma; en çok tartışma çıkaran gün olduğu için zor esner. */
    val visitRotation: Double = 150.0,
    /** Pazartesi sırasından sapma (Salı dersini hep aynı kişi kaçırmasın); en kolay esneyen kural. */
    val mondayRotation: Double = 3.0,
    /** Aynı kişiye ayda 2. Cuma, Cumartesi ya da Pazar; neredeyse kesin kural. */
    val weekendRepeat: Double = 400.0,
    val longMemory: Double = 30.0,
    val gap3: Double = 15.0,
    val gap4: Double = 5.0,
    /** Aynı kişiye ayda Sal–Per'den aynı güne 2. kez. */
    val weekdayRepeat: Double = 5.0,
    /** Aynı kişiye ayda 2. pazartesi (Salı dersini iki kez kaçırır). */
    val mondayRepeat: Double = 30.0,
    /** Ard arda iki günün aynı ikiliyle (ör. Aslı → Oğuz) ayda bir kez daha dizilmesi. */
    val sequenceRepeat: Double = 8.0,
    val sameBayram: Double = 40.0,
    /** Daha önce 31 Aralık tutana yeniden yılbaşı ("mümkünse" bir daha tutmasın). */
    val yilbasiRepeat: Double = 120.0,
)

@Serializable
data class Settings(
    val points: PointWeights = PointWeights(),
    val hours: HourWeights = HourWeights(),
    val penalties: PenaltyWeights = PenaltyWeights(),
    val minGap: Int = 3,
    val maxPerMonth: Int = 6,
    /** Vizit/Cuma/Pazar/Perşembe/Pazartesi sırasının geriye baktığı ay sayısı. */
    val rotationMonths: Int = 6,
    val longMemoryStart: LocalDate = LocalDate.of(2023, 1, 1),
    /** Nöbet sayısı dengesinin başladığı ay (uygulamanın ilk listesi); öncesi elle yapılmış eski düzen sayılır. */
    val countBalanceStart: LocalDate = LocalDate.of(2026, 11, 1),
    val excelTitle: String = "Tıbbi Mikrobiyoloji Asistan Nöbet Listesi",
    val curve: SeniorityCurve = SeniorityCurve(),
    /** Liste görseline, PDF'e ve Excel'e nöbet dökümü eklenir (§14.3). */
    val exportSummary: Boolean = true,
)

/**
 * Kıdem eğrisi (§5.1-9): katsayı asistanlık yılından hesaplanır, herkes aynı eğriden geçer.
 * İlk yıllarda nöbet katsayısı 1,00'dan [earlyDutyEnd]'e yavaşça iner; son yılda [finalDutyStart]'tan
 * [finalDutyEnd]'e; uzatmada [extension] olur ve cumartesi, pazar, tatil yazılmaz.
 */
@Serializable
data class SeniorityCurve(
    val enabled: Boolean = true,
    val years: Int = 4,
    val earlyDutyEnd: Double = 0.97,
    val earlyHardEnd: Double = 0.94,
    val finalDutyStart: Double = 0.82,
    val finalDutyEnd: Double = 0.78,
    val finalHardStart: Double = 0.67,
    val finalHardEnd: Double = 0.63,
    val extension: Double = 0.50,
    /** Nöbet tutan kişi sayısı bunun altına düşünce son yıl ve uzatma indirimi yarıya iner (çömezler ezilmesin). */
    val smallTeam: Int = 5,
)

@Serializable
data class ChangeLogEntry(
    val at: String,
    val action: String,
    val detail: String,
)
