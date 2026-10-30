package com.nobetmaker.core.calendar

import com.nobetmaker.core.model.DayInfo
import com.nobetmaker.core.model.DayKind
import com.nobetmaker.core.model.EventKind
import com.nobetmaker.core.model.Settings
import com.nobetmaker.core.model.Visit
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

enum class Memory { SHORT, LONG, NONE }

/** Günün tek kategorisi; öncelik sırası tanım sırasıdır (§3.3). */
enum class Category(val label: String, val memory: Memory) {
    YILBASI("Yılbaşı", Memory.LONG),
    BAYRAM("Bayram", Memory.LONG),
    RESMI_TATIL("Resmi tatil", Memory.LONG),
    CUMARTESI("Cumartesi", Memory.SHORT),
    PAZAR("Pazar", Memory.SHORT),
    CUMA("Cuma", Memory.SHORT),
    PERSEMBE("Perşembe", Memory.SHORT),
    HAFTA_ICI("Hafta içi", Memory.NONE);

    val eventKind: EventKind?
        get() = when (this) {
            YILBASI -> EventKind.YILBASI
            BAYRAM -> EventKind.BAYRAM
            RESMI_TATIL -> EventKind.RESMI_TATIL
            else -> null
        }

    companion object {
        val LONG_MEMORY = entries.filter { it.memory == Memory.LONG }
    }
}

/**
 * Sırayla dönen gün grupları (§6). Tatil günleri (bayram, resmi tatil, yılbaşı) hiçbir gruba girmez; yalnızca
 * kendi uzun hafızasında sayılır, böylece tatilde vizit olup olmadığının bilinmemesi hesabı etkilemez.
 * Vizit = tatil olmayan vizitli gün (cumartesi); kıdem sırasıyla gider: son yıla girmemiş herkes ayda birer,
 * artanlar son yıldakilere sırayla (Ledger.rotation). Cuma ve Pazar cumartesiyle birlikte zor gün olarak dağılır:
 * hafta sonu tutan herkes eşit (§6.1). Perşembe eşit döner. Pazartesi: ertesi gün Salı dersi var ve nöbetçi dersi kaçırıyor; herkese eşit döner ama en kolay
 * esneyen sıradır.
 */
enum class RotationGroup(val label: String) {
    VIZIT("Vizit"),
    CUMA("Cuma"),
    PAZAR("Pazar"),
    PERSEMBE("Perşembe"),
    PAZARTESI("Pazartesi"),
}

/** Takvimle ilgili bütün hesaplar: tatil, vizit, zorluk puanı, saat, kategori (§3). */
class CalendarService(overrides: List<DayInfo>, private val settings: Settings) {

    private val overrideMap = overrides.associateBy { it.date }
    private val builtInByYear = HashMap<Int, Map<LocalDate, DayInfo>>()
    private val eventKinds = HashMap<String, EventKind>()

    init {
        overrides.forEach { registerEventKind(it) }
    }

    private fun registerEventKind(day: DayInfo) {
        val id = day.eventId?.takeIf { it.isNotEmpty() } ?: return
        val kind = when (day.kind) {
            DayKind.BAYRAM -> EventKind.BAYRAM
            DayKind.YILBASI -> EventKind.YILBASI
            else -> null
        }
        if (kind == EventKind.BAYRAM || kind == EventKind.YILBASI) eventKinds[id] = kind
        else eventKinds.putIfAbsent(id, EventKind.RESMI_TATIL)
    }

    private fun builtIn(date: LocalDate): DayInfo? =
        builtInByYear.getOrPut(date.year) {
            TurkishHolidays.forYear(date.year).onEach { registerEventKind(it) }.associateBy { it.date }
        }[date]

    fun builtInDay(date: LocalDate): DayInfo = builtIn(date) ?: DayInfo(date)

    // Takvimdeki tatiller her zaman önce yüklenir: elle değiştirilmiş bir gün bayramın olayına bağlıysa olayın türü bilinsin.
    fun day(date: LocalDate): DayInfo = builtIn(date).let { b -> overrideMap[date] ?: b ?: DayInfo(date) }

    fun isOverridden(date: LocalDate): Boolean = overrideMap.containsKey(date)

    fun isWeekend(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    /** 1 = tam tatil, 0.5 = arefe, 0 = iş günü. */
    fun holidayFraction(date: LocalDate): Double {
        if (isWeekend(date)) return 1.0
        return when (day(date).kind) {
            DayKind.RESMI_TATIL, DayKind.BAYRAM, DayKind.IDARI_IZIN -> 1.0
            DayKind.AREFE -> 0.5
            DayKind.NORMAL, DayKind.YILBASI -> 0.0
        }
    }

    fun isWorkday(date: LocalDate): Boolean = holidayFraction(date) == 0.0

    fun isOfficialHoliday(date: LocalDate): Boolean = when (day(date).kind) {
        DayKind.RESMI_TATIL, DayKind.BAYRAM, DayKind.AREFE, DayKind.IDARI_IZIN -> true
        else -> false
    }

    fun defaultVisit(date: LocalDate): Visit {
        val d = day(date)
        return when {
            d.kind == DayKind.YILBASI -> Visit.YOK
            date.dayOfWeek == DayOfWeek.SATURDAY -> Visit.VAR
            d.kind == DayKind.RESMI_TATIL || d.kind == DayKind.BAYRAM || d.kind == DayKind.IDARI_IZIN ->
                if (date.monthValue == 1 && date.dayOfMonth == 1) Visit.VAR else Visit.BELIRSIZ
            else -> Visit.YOK
        }
    }

    fun visit(date: LocalDate): Visit = day(date).let { it.actualVisit ?: it.plannedVisit } ?: defaultVisit(date)

    /** Belirsiz vizit planlamada "var" sayılır (§3.1). */
    fun hasVisit(date: LocalDate): Boolean = visit(date) != Visit.YOK

    /** Zorluk puanı formülü (§3.2). */
    fun points(date: LocalDate): Double {
        val w = settings.points
        val today = holidayFraction(date)
        val next = holidayFraction(date.plusDays(1))
        val afterNext = holidayFraction(date.plusDays(2))
        var p = w.base + today * w.holiday
        if (hasVisit(date)) p += w.visit
        p += next * w.nextDayHoliday
        if (next == 0.0) p -= afterNext * w.longWeekendBonus
        return p + day(date).extraPoints
    }

    /** Nöbet saati (§3.4). */
    fun hours(date: LocalDate): Int {
        val h = settings.hours
        val duration = when (holidayFraction(date)) {
            1.0 -> h.holiday
            0.0 -> h.workday
            else -> h.arefe
        }
        val deduction = (h.postDutyDeduction * (1.0 - holidayFraction(date.plusDays(1)))).roundToInt()
        return duration - deduction
    }

    fun eventKind(eventId: String): EventKind? = eventKinds[eventId]

    /**
     * Günün bağlı olduğu tatil olayı. Bayrama bitişik hafta sonu ve idari izin günleri bayrama dahildir.
     * Boş olay kimliği, günün elle olaydan çıkarıldığını gösterir.
     */
    fun eventId(date: LocalDate): String? {
        day(date).eventId?.let { return it.ifEmpty { null } }
        if (holidayFraction(date) < 1.0) return null
        for (dir in longArrayOf(-1, 1)) {
            var d = date
            for (step in 1..10) {
                d = d.plusDays(dir)
                if (holidayFraction(d) < 1.0) break
                val id = day(d).eventId
                if (id != null && eventKind(id) == EventKind.BAYRAM) return id
            }
        }
        return null
    }

    fun category(date: LocalDate): Category {
        val d = day(date)
        if (d.kind == DayKind.YILBASI) return Category.YILBASI
        val e = eventId(date)
        if (e != null && eventKind(e) == EventKind.BAYRAM) return Category.BAYRAM
        if (isOfficialHoliday(date)) return Category.RESMI_TATIL
        return when (date.dayOfWeek) {
            DayOfWeek.SATURDAY -> Category.CUMARTESI
            DayOfWeek.SUNDAY -> Category.PAZAR
            DayOfWeek.FRIDAY -> Category.CUMA
            DayOfWeek.THURSDAY -> Category.PERSEMBE
            else -> Category.HAFTA_ICI
        }
    }

    /**
     * Geçmişte yalnızca kesinleşmiş vizit sayılır; planlamada belirsiz vizit var kabul edilir.
     * Tatil günleri sıraya girmez (yalnızca uzun hafızada sayılır).
     */
    fun rotationGroup(date: LocalDate, history: Boolean): RotationGroup? {
        val cat = category(date)
        if (cat.memory == Memory.LONG) return null
        val v = visit(date)
        if (if (history) v == Visit.VAR else v != Visit.YOK) return RotationGroup.VIZIT
        return when (cat) {
            Category.CUMA -> RotationGroup.CUMA
            Category.PAZAR -> RotationGroup.PAZAR
            Category.PERSEMBE -> RotationGroup.PERSEMBE
            // Salı tatilse ders yok, Pazartesi sıradan bir gündür.
            Category.HAFTA_ICI -> if (date.dayOfWeek == DayOfWeek.MONDAY && holidayFraction(date.plusDays(1)) == 0.0) RotationGroup.PAZARTESI else null
            else -> null
        }
    }

    /**
     * Uzun hafızada günün ağırlığı: arefe gibi yarım günler yarım sayılır; yılbaşı gecesi (iş günü) ve tam
     * tatiller bir.
     */
    fun longWeight(date: LocalDate): Double = holidayFraction(date).let { if (it > 0.0 && it < 1.0) it else 1.0 }

    /** Uzun hafızada olayları ayırt etmek için anahtar. */
    fun longEventKey(date: LocalDate): String = eventId(date) ?: "gun-$date"

    /**
     * Uzun tatil koruması (Y1): nöbetin ertesi günü iş günüyse ve hemen arkasından tatil başlıyorsa,
     * o tatil bloğunun günleri.
     */
    fun longHolidayBlockAfter(date: LocalDate): List<LocalDate> {
        if (holidayFraction(date.plusDays(1)) != 0.0) return emptyList()
        val block = ArrayList<LocalDate>()
        var d = date.plusDays(2)
        while (holidayFraction(d) == 1.0 && block.size < 10) {
            block += d
            d = d.plusDays(1)
        }
        return block
    }

    fun label(date: LocalDate): String? = day(date).name
}

private val trDayNames = arrayOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
private val trDayShort = arrayOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
private val trMonthNames = arrayOf(
    "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
    "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık",
)

fun DayOfWeek.trName(): String = trDayNames[value - 1]
fun DayOfWeek.trShort(): String = trDayShort[value - 1]
fun monthNameTr(month: Int): String = trMonthNames[month - 1]
fun LocalDate.trFormat(): String = "$dayOfMonth ${monthNameTr(monthValue)} $year"
