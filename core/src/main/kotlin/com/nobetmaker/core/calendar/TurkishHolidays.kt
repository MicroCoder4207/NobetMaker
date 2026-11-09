package com.nobetmaker.core.calendar

import com.nobetmaker.core.model.DayInfo
import com.nobetmaker.core.model.DayKind
import java.time.LocalDate

/**
 * Uygulamayla hazır gelen tatiller (§3.1). Dini bayramlar Diyanet takviminden alınmıştır;
 * tabloda olmayan yıllar için bayram günleri elle eklenir.
 */
object TurkishHolidays {

    private data class Religious(val arefe: LocalDate, val days: Int)

    private val ramazan = mapOf(
        2023 to Religious(LocalDate.of(2023, 4, 20), 3),
        2024 to Religious(LocalDate.of(2024, 4, 9), 3),
        2025 to Religious(LocalDate.of(2025, 3, 29), 3),
        2026 to Religious(LocalDate.of(2026, 3, 19), 3),
        2027 to Religious(LocalDate.of(2027, 3, 8), 3),
    )

    private val kurban = mapOf(
        2023 to Religious(LocalDate.of(2023, 6, 27), 4),
        2024 to Religious(LocalDate.of(2024, 6, 15), 4),
        2025 to Religious(LocalDate.of(2025, 6, 5), 4),
        2026 to Religious(LocalDate.of(2026, 5, 26), 4),
        2027 to Religious(LocalDate.of(2027, 5, 15), 4),
    )

    val religiousYears: IntRange = 2023..2027

    /**
     * Bayramı 9 güne çıkaran idari izinler (geçmiş yıllar; yenileri açıklanınca Takvim'den girilir).
     * İdari izne bağlanan arefe tam gün sayılır.
     */
    private val adminLeave: Map<String, List<LocalDate>> = mapOf(
        "2024-ramazan" to listOf(LocalDate.of(2024, 4, 8), LocalDate.of(2024, 4, 9)),
        "2024-kurban" to listOf(LocalDate.of(2024, 6, 20), LocalDate.of(2024, 6, 21)),
        "2025-ramazan" to listOf(LocalDate.of(2025, 4, 2), LocalDate.of(2025, 4, 3), LocalDate.of(2025, 4, 4)),
        "2026-kurban" to listOf(LocalDate.of(2026, 5, 25), LocalDate.of(2026, 5, 26)),
    )

    fun forYear(year: Int): List<DayInfo> {
        val days = LinkedHashMap<LocalDate, DayInfo>()

        fun fixed(month: Int, day: Int, kind: DayKind, name: String, eventId: String, extra: Double = 0.0) {
            val date = LocalDate.of(year, month, day)
            days[date] = DayInfo(date = date, kind = kind, name = name, eventId = eventId, extraPoints = extra)
        }

        fixed(1, 1, DayKind.RESMI_TATIL, "Yılbaşı", "$year-01-01")
        fixed(4, 23, DayKind.RESMI_TATIL, "Ulusal Egemenlik ve Çocuk Bayramı", "$year-04-23")
        fixed(5, 1, DayKind.RESMI_TATIL, "Emek ve Dayanışma Günü", "$year-05-01")
        fixed(5, 19, DayKind.RESMI_TATIL, "Atatürk'ü Anma, Gençlik ve Spor Bayramı", "$year-05-19")
        fixed(7, 15, DayKind.RESMI_TATIL, "Demokrasi ve Millî Birlik Günü", "$year-07-15")
        fixed(8, 30, DayKind.RESMI_TATIL, "Zafer Bayramı", "$year-08-30")
        fixed(10, 28, DayKind.AREFE, "Cumhuriyet Bayramı arifesi", "$year-10-29")
        fixed(10, 29, DayKind.RESMI_TATIL, "Cumhuriyet Bayramı", "$year-10-29")
        fixed(12, 31, DayKind.YILBASI, "Yılbaşı gecesi", "$year-yilbasi", extra = 1.0)

        fun religious(r: Religious?, name: String, eventId: String) {
            if (r == null) return
            val arefeExisting = days[r.arefe]
            days[r.arefe] = DayInfo(
                date = r.arefe,
                kind = DayKind.AREFE,
                name = listOfNotNull("$name arifesi", arefeExisting?.name).joinToString(" / "),
                eventId = eventId,
            )
            for (i in 1..r.days) {
                val date = r.arefe.plusDays(i.toLong())
                // Aynı güne denk gelen resmi tatil ek tatil oluşturmaz; gün bayram sayılır.
                val existing = days[date]
                days[date] = DayInfo(
                    date = date,
                    kind = DayKind.BAYRAM,
                    name = listOfNotNull("$name $i. gün", existing?.name).joinToString(" / "),
                    eventId = eventId,
                )
            }
        }

        religious(ramazan[year], "Ramazan Bayramı", "$year-ramazan")
        religious(kurban[year], "Kurban Bayramı", "$year-kurban")
        for ((eventId, dates) in adminLeave) {
            for (date in dates) {
                if (date.year != year) continue
                val name = days[date]?.name?.let { "$it (idari izin)" } ?: "Bayram idari izni"
                days[date] = DayInfo(date = date, kind = DayKind.IDARI_IZIN, name = name, eventId = eventId)
            }
        }

        return days.values.filter { it.date.year == year }.sortedBy { it.date }
    }
}
