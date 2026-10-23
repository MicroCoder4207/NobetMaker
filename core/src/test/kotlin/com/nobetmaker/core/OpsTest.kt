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
    fun `eski surum verisi guncellenir`() {
        val oct29 = LocalDate.of(2026, 10, 29)
        val seed = SeedData.build()
        val old = seed.copy(schemaVersion = 1, dayOverrides = seed.dayOverrides.filter { it.date != oct29 })
        val migrated = AppJson.decode(AppJson.encode(old))
        assertEquals(com.nobetmaker.core.model.AppData.SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals(com.nobetmaker.core.model.Visit.VAR, migrated.dayOverrides.single { it.date == oct29 }.actualVisit)
        // 0.1.0 ayarlarında olmayan alanlar varsayılan değerini alır, kaldırılan alanlar sorun çıkarmaz.
        val legacy = AppJson.encode(old).replace("\"rotationMonths\": 3", "\"shortMemoryMonths\": 6")
        assertEquals(3, AppJson.decode(legacy).settings.rotationMonths)
        // 0.2.x: eski renkler yenilerine, başlangıç verisindeki katılış tarihleri boşa çevrilir.
        val v2 = old.copy(persons = old.persons.map { p ->
            when (p.id) {
                "asli" -> p.copy(color = "#00BCD4", joinDate = LocalDate.of(2026, 8, 1))
                "kenan" -> p.copy(color = "#2196F3", joinDate = LocalDate.of(2020, 1, 1))
                else -> p
            }
        })
        val m2 = AppJson.decode(AppJson.encode(v2))
        assertEquals(com.nobetmaker.core.model.PersonPalette.colors[6], m2.person("asli")!!.color)
        assertEquals(null, m2.person("asli")!!.joinDate)
        assertEquals(LocalDate.of(2020, 1, 1), m2.person("kenan")!!.joinDate, "kullanıcının girdiği tarih korunmalı")
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
        card.rotationSummary.forEach { println(it) }
        println("sıra sapmaları: ${card.rotationMisses}; hafta sonu tekrarı: ${card.weekendRepeats}; ikili tekrarı: ${card.sequenceRepeats}")
        for (i in best.indices) println("${pr.days[i]} ${pr.days[i].dayOfWeek.toString().take(3)} ${pr.persons[best[i]].name}")
        com.nobetmaker.core.solver.Objective(pr).penalties(best).forEach { println("ceza ${it.kind} ${"%.1f".format(it.amount)} ${it.text}") }
        println("toplam: ${result.solutions.map { "%.1f".format(it.cost) }}")

        // Ayda en fazla 1 Cuma, 1 Cumartesi, 1 Pazar; vizitler sıradaki kişilere.
        assertTrue(card.weekendRepeats.isEmpty(), "hafta sonu tekrarı: ${card.weekendRepeats}")
        val vizitQueue = pr.rotationQueue[com.nobetmaker.core.calendar.RotationGroup.VIZIT.ordinal].map { pr.persons[it].name }.sorted()
        val vizitHolders = best.indices.filter { pr.rotationIndex[it] == com.nobetmaker.core.calendar.RotationGroup.VIZIT.ordinal }
            .map { pr.persons[best[it]].name }.sorted()
        assertEquals(vizitQueue, vizitHolders, "vizitler sıraya göre dağılmalı")
        assertTrue("Kenan" in vizitHolders && "Fatma" in vizitHolders, "Eylül'de vizit tutmayan Kenan ve Fatma Kasım'da sırada olmalı")
        // Liste haftadan haftaya aynı sırayla tekrar etmemeli (ör. 2-3-4 ve 9-10-11 Aslı-Oğuz-Şule).
        val triples = (0 until best.size - 2).map { Triple(best[it], best[it + 1], best[it + 2]) }
        assertEquals(triples.size, triples.distinct().size, "üçlü sıra tekrarı var")
        val mondays = best.indices.filter { pr.days[it].dayOfWeek == java.time.DayOfWeek.MONDAY }.map { best[it] }
        assertEquals(mondays.size, mondays.distinct().size, "aynı kişiye iki Pazartesi")

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
    fun `elle degisen nobet sayisi digerlerine dagilir`() {
        val ym = YearMonth.of(2026, 11)
        var data = DataOps.ensureMonth(SeedData.build(), ym)
        val before = data.month(ym)!!.quotas.associate { it.personId to it.final }
        assertEquals(30, before.values.sum())
        // Yaren bu ay tutmayacak: 0'a indirilince 2 günü diğerlerine dağılmalı.
        data = DataOps.setQuota(data, ym, "yaren", 0)
        val after = data.month(ym)!!.quotas.associateBy { it.personId }
        println("önce: $before, sonra: ${after.mapValues { it.value.final }}")
        assertEquals(0, after.getValue("yaren").final)
        assertTrue(after.getValue("yaren").manual)
        assertEquals(before.getValue("yaren"), after.getValue("yaren").suggested)
        assertEquals(30, after.values.sumOf { it.final })
        assertTrue(after.filterKeys { it != "yaren" }.all { (id, q) -> q.final >= before.getValue(id) && !q.manual })
        // Öneriler yeniden hesaplanınca sabit kalır; bırakılınca eski haline döner.
        data = DataOps.refreshQuotas(data, ym)
        assertEquals(0, data.month(ym)!!.quotas.first { it.personId == "yaren" }.final)
        data = DataOps.releaseQuota(data, ym, "yaren")
        assertEquals(before, data.month(ym)!!.quotas.associate { it.personId to it.final })
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
                    "sıra=${it.rotationCount.entries.joinToString { (g, n) -> "${g.label} $n (${"%+.1f".format(it.rotationBalance[g])})" }}, " +
                    "bayram ${it.longDays.entries.joinToString { (k, v) -> "${k.label}=$v/${it.longPresent[k]}" }}",
            )
        }
        assertEquals(7, stats.size)
        assertEquals(184, stats.sumOf { it.officialCount }, "Mayıs–Ekim toplam gün sayısı")
    }
}
