package com.nobetmaker.core

import com.nobetmaker.core.calendar.CalendarService
import com.nobetmaker.core.export.MonthViewBuilder
import com.nobetmaker.core.export.RosterExcel
import com.nobetmaker.core.export.RosterSheet
import com.nobetmaker.core.rules.RosterIndex
import com.nobetmaker.core.seed.SeedData
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeedDataTest {

    private val data = SeedData.build()
    private val roster = RosterIndex(data)
    private val cal = CalendarService(data.dayOverrides, data.settings)

    private fun counts(ym: YearMonth): Map<String, Int> =
        (1..ym.lengthOfMonth()).map { roster.official.getValue(ym.atDay(it)) }.groupingBy { it }.eachCount()

    private fun hours(ym: YearMonth, map: Map<LocalDate, String>): Map<String, Int> =
        (1..ym.lengthOfMonth()).map { ym.atDay(it) }.groupBy { map.getValue(it) }.mapValues { (_, d) -> d.sumOf { cal.hours(it) } }

    @Test
    fun `aylik toplamlar kagittakiyle ayni`() {
        val paper = mapOf(
            YearMonth.of(2025, 11) to mapOf("yaren" to 4, "kenan" to 4, "fatma" to 6, "betul" to 5, "sule" to 6, "oguz" to 5),
            YearMonth.of(2025, 12) to mapOf("yaren" to 4, "kenan" to 4, "fatma" to 5, "betul" to 7, "sule" to 6, "oguz" to 5),
            YearMonth.of(2026, 1) to mapOf("yaren" to 5, "kenan" to 4, "fatma" to 6, "betul" to 5, "sule" to 5, "oguz" to 6),
            YearMonth.of(2026, 3) to mapOf("yaren" to 4, "kenan" to 5, "fatma" to 5, "betul" to 5, "sule" to 6, "oguz" to 6),
            YearMonth.of(2026, 6) to mapOf("yaren" to 4, "kenan" to 4, "fatma" to 4, "betul" to 6, "sule" to 6, "oguz" to 6),
            YearMonth.of(2026, 7) to mapOf("yaren" to 4, "kenan" to 5, "fatma" to 5, "betul" to 5, "sule" to 6, "oguz" to 6),
            YearMonth.of(2026, 8) to mapOf("yaren" to 3, "kenan" to 5, "fatma" to 5, "betul" to 5, "sule" to 5, "oguz" to 5, "asli" to 3),
            YearMonth.of(2026, 9) to mapOf("yaren" to 3, "kenan" to 3, "fatma" to 4, "betul" to 5, "sule" to 5, "oguz" to 5, "asli" to 5),
            YearMonth.of(2026, 10) to mapOf("yaren" to 2, "kenan" to 4, "fatma" to 5, "betul" to 5, "sule" to 5, "oguz" to 5, "asli" to 5),
        )
        for ((ym, expected) in paper) assertEquals(expected, counts(ym), "$ym")
    }

    @Test
    fun `bayramlar el yazisi kagitla ayni`() {
        // Bayramlarda el yazısı kağıt esas; Excel'le çelişen günler ona göre düzeltildi. Arefe tam gün sayıldı.
        fun bayram(from: LocalDate, to: LocalDate): Map<String, Int> =
            generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
                .filter { cal.category(it) == com.nobetmaker.core.calendar.Category.BAYRAM }
                .mapNotNull { roster.official[it] }.groupingBy { it }.eachCount()
        assertEquals(mapOf("yaren" to 2, "fatma" to 1), bayram(LocalDate.of(2024, 4, 1), LocalDate.of(2024, 4, 30)))
        assertEquals(mapOf("fatma" to 2, "yaren" to 1), bayram(LocalDate.of(2024, 6, 1), LocalDate.of(2024, 6, 30)))
        assertEquals(mapOf("sule" to 3, "fatma" to 3, "yaren" to 1, "betul" to 1, "kenan" to 1), bayram(LocalDate.of(2025, 3, 25), LocalDate.of(2025, 4, 10)))
        assertEquals(mapOf("fatma" to 2, "kenan" to 1, "betul" to 1, "oguz" to 1), bayram(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30)))
        // Tek günler: 29 Ekim 2025 Oğuz, 31 Aralık 2024 Betül, 31 Aralık 2025 Fatma.
        assertEquals("oguz", roster.official[LocalDate.of(2025, 10, 29)])
        assertEquals("betul", roster.official[LocalDate.of(2024, 12, 31)])
        assertEquals("fatma", roster.official[LocalDate.of(2025, 12, 31)])
    }

    @Test
    fun `saatler kagittaki hesapla ayni`() {
        // Aralık 2025: kağıtta Fatma 72; kural 31 Aralık'ı 16 saydığı için 80 (bilinçli fark, §19).
        assertEquals(
            mapOf("yaren" to 48, "kenan" to 48, "fatma" to 80, "betul" to 88, "sule" to 64, "oguz" to 56),
            hours(YearMonth.of(2025, 12), roster.official),
        )
        // Ocak 2025 kağıdındaki saatler (boş günleri uzmanlar tuttu); 1 Ocak tatil, ertesi iş günü: 16.
        val jan25 = YearMonth.of(2025, 1)
        assertEquals(
            mapOf("yaren" to 72, "kenan" to 64, "fatma" to 104, "betul" to 88),
            (1..31).map { jan25.atDay(it) }.filter { it in roster.official }
                .groupBy { roster.official.getValue(it) }.mapValues { (_, d) -> d.sumOf { cal.hours(it) } },
        )
        // Ocak 2026 resmi: Yaren 64, Kenan 56, Fatma 72, Betül 64, Şule 80, Oğuz 72.
        assertEquals(
            mapOf("yaren" to 64, "kenan" to 56, "fatma" to 72, "betul" to 64, "sule" to 80, "oguz" to 72),
            hours(YearMonth.of(2026, 1), roster.official),
        )
    }

    @Test
    fun `resmi listede art arda ve gunasiri yok`() {
        // Eski listelerde Fatma'nın isteğe bağlı tuttuğu iki istisna (hafta sonu art arda; 15 Temmuz'u Yaren tuttu).
        val known = setOf(
            "Fatma: (2024-11-30, 2024-12-01)",
            "Fatma: (2025-07-13, 2025-07-15)",
        )
        val violations = data.persons.flatMap { p ->
            roster.officialDutiesOf(p.id).zipWithNext().filter { (a, b) -> b.toEpochDay() - a.toEpochDay() < 3 }
                .map { "${p.name}: $it" }
        }.filterNot { it in known }
        assertTrue(violations.isEmpty(), violations.joinToString())
    }

    @Test
    fun `aylarin gorselleri ve excel uretilir`() {
        val out = File("build/previews").apply { mkdirs() }
        for (ym in SeedData.rosters.keys) {
            val view = MonthViewBuilder.build(data, ym, showActual = false, flagged = SeedData.flagged)
            AwtRenderer.render(RosterSheet.layout(view), File(out, "gecmis-$ym.png"), 0.6f)
        }
        val view = MonthViewBuilder.build(data, YearMonth.of(2026, 10), showActual = false)
        File(out, RosterExcel.fileName(view)).outputStream().use { RosterExcel.write(view, it) }
        assertTrue(File(out, "gecmis-2026-10.png").exists())
    }
}
