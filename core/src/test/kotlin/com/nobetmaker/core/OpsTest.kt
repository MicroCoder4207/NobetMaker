package com.nobetmaker.core

import com.nobetmaker.core.model.MonthStatus
import com.nobetmaker.core.ops.AppJson
import com.nobetmaker.core.ops.DataOps
import com.nobetmaker.core.rules.RosterIndex
import com.nobetmaker.core.seed.SeedData
import com.nobetmaker.core.solver.Explainer
import com.nobetmaker.core.solver.ProblemBuilder
import com.nobetmaker.core.solver.Solver
import com.nobetmaker.core.stats.ConflictDetector
import com.nobetmaker.core.stats.StatsService
import com.nobetmaker.core.swap.SwapAssistant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OpsTest {

    @Test
    fun `json gidis donus`() {
        val data = SeedData.build()
        assertEquals(data, AppJson.decode(AppJson.encode(data)))
    }

    @Test
    fun `gercek gecmisle kasim 2026 uretilir ve yayinlanir`() {
        val ym = YearMonth.of(2026, 11)
        var data = DataOps.ensureMonth(SeedData.build(), ym)
        val pr = ProblemBuilder.build(data, ym, quotaOverride = DataOps.quotaMap(data.month(ym)))
        println("kotalar: " + pr.persons.indices.joinToString { "${pr.persons[it].name}=${pr.quotas[it]}" })
        println("uyarılar: " + ConflictDetector.detect(pr))
        val result = Solver(pr).solve()
        assertTrue(result.solutions.isNotEmpty())
        val best = result.solutions.first().assignment
        val card = Explainer(pr).scoreCard(best)
        card.perPerson.forEach { println("${it.person.name}: ${it.count}, ${it.hours} saat, ${it.byCategory}") }
        println("defter: ${card.memoryNotes}; uzun tatil: ${card.longHolidayHits}")

        // 29 Ekim'i (Perşembe) tutan Fatma, uzun tatil koruması gereği 1 Kasım Pazar'a yazılmamalı.
        assertNotEquals(pr.personIndex("fatma"), best[0], "Fatma 1 Kasım'a yazıldı")
        // Ekim'in son günlerinden sonra en az 3 gün aralık.
        val roster = RosterIndex(data)
        for (i in 0 until 3) {
            val p = pr.persons[best[i]].id
            for (k in 1..(3 - i)) {
                val prev = ym.atDay(1).minusDays(k.toLong())
                assertTrue(roster.official[prev] != p || (i + k) >= 3, "${pr.days[i]} ${p} önceki ayla çakışıyor")
            }
        }

        data = DataOps.applySolution(data, pr, best)
        assertEquals(30, data.month(ym)!!.current!!.assignments.size)
        data = DataOps.setStatus(data, ym, MonthStatus.YAYINLANDI)
        val d5 = ym.atDay(5)
        val someone = data.persons.first { it.id != data.month(ym)!!.current!!.personOn(d5) }.id
        data = DataOps.assign(data, ym, d5, someone)
        assertEquals(2, data.month(ym)!!.versions.size, "yayınlanmış ayda değişiklik yeni sürüm açmalı")
    }

    @Test
    fun `degisim asistani secenek bulur`() {
        val data = SeedData.build()
        val date = LocalDate.of(2026, 10, 13) // Kenan
        val options = SwapAssistant(data).options("kenan", date)
        assertTrue(options.isNotEmpty())
        options.take(5).forEach { println("${it.title} — uyarılar: ${it.warnings}") }
        assertTrue(options.first().warnings.isEmpty(), "en iyi seçenek kurallara uymalı")
    }

    @Test
    fun `istatistik hesaplanir`() {
        val data = SeedData.build()
        val stats = StatsService.compute(data, YearMonth.of(2026, 5), YearMonth.of(2026, 10))
        stats.forEach {
            println(
                "${it.person.name}: resmi ${it.officialCount} (${it.officialHours} sa), gerçek ${it.actualCount}, " +
                    "kısa=${it.shortDeviation.mapValues { (_, v) -> "%.1f".format(v) }}, " +
                    "bayram ${it.longDays.entries.joinToString { (k, v) -> "${k.label}=$v/${it.longPresent[k]}" }}",
            )
        }
        assertEquals(7, stats.size)
        assertEquals(184, stats.sumOf { it.officialCount }, "Mayıs–Ekim toplam gün sayısı")
    }
}
