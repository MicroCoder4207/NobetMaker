package com.nobetmaker.core

import com.nobetmaker.core.model.DutyRequest
import com.nobetmaker.core.model.MonthStatus
import com.nobetmaker.core.model.Priority
import com.nobetmaker.core.model.RequestType
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
        assertEquals(6, AppJson.decode(legacy).settings.rotationMonths)
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
        // Eski tahmini tarih (Ağustos 2026) silinir, 0.4.0'ın bilinen tarihi (Ocak 2026) yazılır.
        assertEquals(LocalDate.of(2026, 1, 1), m2.person("asli")!!.joinDate)
        assertEquals(LocalDate.of(2020, 1, 1), m2.person("kenan")!!.joinDate, "kullanıcının girdiği tarih korunmalı")
    }

    @Test
    fun `0_3 verisine eski listeler ve asistanlik tarihleri eklenir`() {
        val seed = SeedData.build()
        val since = LocalDate.of(2025, 11, 1)
        val v3 = seed.copy(
            schemaVersion = 3,
            persons = seed.persons.map { p -> if (p.id == "sule") p.copy(residencyStart = LocalDate.of(2024, 9, 1)) else p.copy(residencyStart = null, joinDate = null) },
            months = seed.months.filter { !YearMonth.of(it.year, it.month).atDay(1).isBefore(since) },
            informalChanges = seed.informalChanges.filter { !it.date.isBefore(since) },
            historyEvents = listOf(
                com.nobetmaker.core.model.HistoryEvent(
                    "h-2025-kurban", "2025 Kurban Bayramı", com.nobetmaker.core.model.EventKind.BAYRAM,
                    LocalDate.of(2025, 6, 5), LocalDate.of(2025, 6, 9), 5, listOf(com.nobetmaker.core.model.HistoryEntry("fatma", 2)),
                ),
                com.nobetmaker.core.model.HistoryEvent(
                    "h-kullanici", "Kullanıcının kaydı", com.nobetmaker.core.model.EventKind.RESMI_TATIL,
                    LocalDate.of(2023, 5, 1), null, 1, listOf(com.nobetmaker.core.model.HistoryEntry("yaren", 1)),
                ),
            ),
            settings = seed.settings.copy(rotationMonths = 3),
        )
        val m = AppJson.decode(AppJson.encode(v3))
        assertEquals(seed.months.map { it.year to it.month }, m.months.map { it.year to it.month })
        assertEquals(seed.informalChanges.map { it.date }.sorted(), m.informalChanges.map { it.date }.sorted())
        assertEquals(LocalDate.of(2024, 12, 1), m.person("oguz")!!.residencyStart)
        assertEquals(LocalDate.of(2024, 9, 1), m.person("sule")!!.residencyStart, "kullanıcının girdiği tarih korunmalı")
        assertEquals(LocalDate.of(2023, 12, 1), m.person("yaren")!!.joinDate)
        assertEquals(listOf("h-kullanici"), m.historyEvents.map { it.id }, "eski bayram sayfası kayıtları kalkar, kullanıcınınki kalır")
        assertEquals(6, m.settings.rotationMonths)
        // Uzman günleri boş kalır.
        assertEquals(5, m.month(YearMonth.of(2024, 4))!!.current!!.assignments.size)
        assertEquals(LocalDate.of(2023, 10, 1), m.person("fatma")!!.residencyStart)
    }

    @Test
    fun `0_4_0 verisinde Fatma Ekim 2023 olur`() {
        val seed = SeedData.build()
        val sep = LocalDate.of(2023, 9, 1)
        val v4 = seed.copy(schemaVersion = 4, persons = seed.persons.map { if (it.id == "fatma") it.copy(residencyStart = sep, joinDate = sep) else it })
        val m = AppJson.decode(AppJson.encode(v4))
        assertEquals(LocalDate.of(2023, 10, 1), m.person("fatma")!!.residencyStart)
        assertEquals(LocalDate.of(2023, 10, 1), m.person("fatma")!!.joinDate)
        // Elle girilmiş başka bir tarih korunur.
        val own = seed.copy(schemaVersion = 4, persons = seed.persons.map { if (it.id == "fatma") it.copy(residencyStart = LocalDate.of(2023, 11, 1)) else it })
        assertEquals(LocalDate.of(2023, 11, 1), AppJson.decode(AppJson.encode(own)).person("fatma")!!.residencyStart)
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
        // Son yıla girmemiş dört kişi (Betül, Şule, Oğuz, Aslı) dört cumartesiyi tutar; Kenan ve Fatma artan olmadığı için tutmaz.
        assertEquals(listOf("Aslı", "Betül", "Oğuz", "Şule"), vizitHolders)
        // Kıdemliler bu ay birer pazar tutar, cumartesi tutan çömezler ikişer hafta sonu günü.
        val weekend = best.indices.filter { pr.days[it].dayOfWeek.value >= 6 && pr.rotationIndex[it] >= 0 }.groupingBy { pr.persons[best[it]].name }.eachCount()
        assertTrue((weekend["Kenan"] ?: 0) <= 1 && (weekend["Fatma"] ?: 0) <= 1, "kıdemliye en fazla bir hafta sonu günü: $weekend")
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

        // Döküm: herkesin satırı, kurallar ve eğri; görsel, PDF'in ikinci sayfası ve Excel'in Döküm sayfası.
        val dokum = com.nobetmaker.core.export.DokumBuilder.build(data, ym)
        dokum.rows.forEach { println("döküm ${it.person.name}: ${it.stage} ${it.duty} ${it.hard} sayı=${it.count} vizit=${it.visits} bayram=${it.bayram} resmi=${it.resmi}") }
        dokum.notes.forEach { println("not: $it") }
        assertEquals(7, dokum.rows.size)
        assertEquals("4. yıl 4. ay", dokum.rows.single { it.person.id == "kenan" }.stage)
        assertTrue(dokum.rows.single { it.person.id == "fatma" }.duty > dokum.rows.single { it.person.id == "kenan" }.duty, "Fatma Kenan'dan çok az çömez")
        assertEquals(null, dokum.rows.single { it.person.id == "yaren" }.visits, "Yaren hafta sonu tutmuyor")
        // Cumartesi şeffaf: kural, gelecek ayın kıdemli sırası ve son yıla giren yazılır.
        assertTrue("Artan cumartesi sırası (Aralık 2026): Fatma → Kenan → Betül." in dokum.notes, "notlar: ${dokum.notes}")
        assertTrue("Betül son yıla giriyor; cumartesi sırasına katılır." in dokum.notes)
        assertTrue(dokum.notes.none { it.startsWith("Bu ay cumartesi tutmayan") }, "çömezlerin hepsi cumartesi tuttu")
        assertTrue("Bu ay 13 zor gün, 6 kişi: herkes 2, fazla 1 gün çömezlere." in dokum.notes, "notlar: ${dokum.notes}")
        val row = { id: String -> dokum.rows.single { it.person.id == id } }
        assertEquals(listOf(true, true, false, false), listOf("fatma", "kenan", "betul", "sule").map { row(it).senior })
        assertEquals(listOf(1, 2, 3, null), listOf("fatma", "kenan", "betul", "sule").map { row(it).turn })
        assertEquals("sırada 1. · son 22 Ağu", com.nobetmaker.core.export.Dokum.saturdaySub(row("fatma"), 6))
        assertEquals("sıraya giriyor", com.nobetmaker.core.export.Dokum.saturdayMain(row("betul")))
        val previews = java.io.File("build/previews").apply { mkdirs() }
        val view = com.nobetmaker.core.export.MonthViewBuilder.build(data, ym, showActual = false)
        AwtRenderer.render(com.nobetmaker.core.export.RosterSheet.layout(view, LocalDate.of(2026, 10, 26), dokum), java.io.File(previews, "kasim-2026-dokum.png"))
        AwtRenderer.render(com.nobetmaker.core.export.DokumSheet.page(dokum, view.title), java.io.File(previews, "kasim-2026-dokum-sayfa.png"), 0.6f)
        java.io.File(previews, "kasim-2026.xlsx").outputStream().use { com.nobetmaker.core.export.RosterExcel.write(view, it, dokum) }
        data = DataOps.setStatus(data, ym, MonthStatus.YAYINLANDI)
        val d5 = ym.atDay(5)
        val someone = data.persons.first { it.id != data.month(ym)!!.current!!.personOn(d5) }.id
        data = DataOps.assign(data, ym, d5, someone)
        assertEquals(2, data.month(ym)!!.versions.size, "yayınlanmış ayda değişiklik yeni sürüm açmalı")
    }

    @Test
    fun `kapanmis aydaki bayram gunu elle duzeltilir ve denge degisir`() {
        val seed = SeedData.build()
        val may30 = LocalDate.of(2026, 5, 30)
        val report = com.nobetmaker.core.ledger.HolidayReportBuilder.build(seed, LocalDate.of(2026, 10, 23))
        val kurban = report.events.single { it.name == "Kurban Bayramı 2026" }
        assertEquals((23..31).map { LocalDate.of(2026, 5, it) }, kurban.days.map { it.date })
        assertEquals("fatma", kurban.days.single { it.date == may30 }.official)
        assertEquals("Ramazan Bayramı 2025", report.events.single { it.days.any { d -> d.date == LocalDate.of(2025, 4, 1) } }.name)
        assertTrue(report.events.zipWithNext().all { (a, b) -> !a.start.isBefore(b.start) }, "yeniden eskiye")
        val before = report.balances.getValue("kenan").getValue(com.nobetmaker.core.calendar.Category.BAYRAM)

        // Kapanmış ay: resmi kayıt düzeltilir, yeni sürüm açılır, ay kapalı kalır, denge değişir.
        val versions = seed.month(YearMonth.of(2026, 5))!!.versions.size
        val fixed = DataOps.correctDay(seed, may30, "kenan")
        val plan = fixed.month(YearMonth.of(2026, 5))!!
        assertEquals(MonthStatus.KAPANDI, plan.status)
        assertEquals(versions + 1, plan.versions.size)
        assertEquals("kenan", plan.current!!.personOn(may30))
        val after = com.nobetmaker.core.ledger.HolidayReportBuilder.build(fixed, LocalDate.of(2026, 10, 23))
            .balances.getValue("kenan").getValue(com.nobetmaker.core.calendar.Category.BAYRAM)
        assertEquals(before.held + 1.0, after.held, 1e-9)
        // Uzman: gün asistan hesabından düşer.
        val uzman = DataOps.correctDay(fixed, may30, null)
        assertEquals(null, uzman.month(YearMonth.of(2026, 5))!!.current!!.personOn(may30))
        assertEquals(null, com.nobetmaker.core.ledger.HolidayReportBuilder.build(uzman, LocalDate.of(2026, 10, 23)).events.flatMap { it.days }.single { it.date == may30 }.official)

        // Gerçekte tutan: hesap değişmez, kayıt görünür; resmi kişi seçilince kalkar.
        val swapped = DataOps.recordActual(seed, may30, "sule")
        val day = com.nobetmaker.core.ledger.HolidayReportBuilder.build(swapped, LocalDate.of(2026, 10, 23)).events.flatMap { it.days }.single { it.date == may30 }
        assertEquals("fatma" to "sule", day.official to day.actual)
        assertEquals(seed.informalChanges.filterNot { it.date == may30 }, DataOps.recordActual(swapped, may30, "fatma").informalChanges.filterNot { it.date == may30 })
        assertTrue(DataOps.recordActual(swapped, may30, "fatma").informalChanges.none { it.date == may30 })
        // Gün uzmana çevrilince ya da gerçekte tutan resmi yapılınca takas kaydı kalkar.
        assertTrue(DataOps.correctDay(swapped, may30, null).informalChanges.none { it.date == may30 })
        assertTrue(DataOps.correctDay(swapped, may30, "sule").informalChanges.none { it.date == may30 })
        assertTrue(DataOps.correctDay(swapped, may30, "kenan").informalChanges.any { it.date == may30 })
    }

    @Test
    fun `gun turu elle degisir ve takvimdekine donulur`() {
        val seed = SeedData.build()
        val apr2 = LocalDate.of(2025, 4, 2) // Ramazan Bayramı 2025 idari izni
        val cal = { d: com.nobetmaker.core.model.AppData -> com.nobetmaker.core.calendar.CalendarService(d.dayOverrides, d.settings) }
        assertEquals(com.nobetmaker.core.calendar.Category.BAYRAM, cal(seed).category(apr2))
        val normal = DataOps.setDayKind(seed, apr2, com.nobetmaker.core.model.DayKind.NORMAL)
        assertEquals(com.nobetmaker.core.calendar.Category.HAFTA_ICI, cal(normal).category(apr2))
        // Komşu günler bayramda kalır.
        assertEquals(com.nobetmaker.core.calendar.Category.BAYRAM, cal(normal).category(LocalDate.of(2025, 4, 3)))
        val back = DataOps.setDayKind(normal, apr2, com.nobetmaker.core.model.DayKind.IDARI_IZIN)
        assertTrue(back.dayOverrides.none { it.date == apr2 }, "takvimdeki türe dönünce elle kayıt kalkmalı")
        // Normal bir günü bayrama bağlamak: yakındaki bayramın olayına katılır.
        val apr7 = LocalDate.of(2025, 4, 7)
        val linked = DataOps.setDayKind(seed, apr7, com.nobetmaker.core.model.DayKind.IDARI_IZIN)
        assertEquals("2025-ramazan", cal(linked).eventId(apr7))
        assertEquals(com.nobetmaker.core.calendar.Category.BAYRAM, cal(linked).category(apr7))
        // Bayrama bitişik hafta sonu (türü zaten normal) "normal" denince bayramdan çıkar; "bayram" denince geri girer.
        val may23 = LocalDate.of(2026, 5, 23)
        assertEquals(com.nobetmaker.core.calendar.Category.BAYRAM, cal(seed).category(may23))
        val out = DataOps.setDayKind(seed, may23, com.nobetmaker.core.model.DayKind.NORMAL)
        assertEquals(com.nobetmaker.core.calendar.Category.CUMARTESI, cal(out).category(may23))
        assertEquals(com.nobetmaker.core.calendar.Category.BAYRAM, cal(out).category(LocalDate.of(2026, 5, 25)))
        assertEquals(com.nobetmaker.core.calendar.Category.BAYRAM, cal(DataOps.setDayKind(out, may23, com.nobetmaker.core.model.DayKind.BAYRAM)).category(may23))
        // Takvimde bayram olan cumartesi "normal" olur, "bayram" denince elle kayıt kalkar.
        val may30 = LocalDate.of(2026, 5, 30)
        val sat = DataOps.setDayKind(seed, may30, com.nobetmaker.core.model.DayKind.NORMAL)
        assertEquals(com.nobetmaker.core.calendar.Category.CUMARTESI, cal(sat).category(may30))
        assertTrue(DataOps.setDayKind(sat, may30, com.nobetmaker.core.model.DayKind.BAYRAM).dayOverrides.none { it.date == may30 })
        // Tek başına resmi tatil.
        val oct = LocalDate.of(2025, 10, 30)
        assertEquals(com.nobetmaker.core.calendar.Category.RESMI_TATIL, cal(DataOps.setDayKind(seed, oct, com.nobetmaker.core.model.DayKind.RESMI_TATIL)).category(oct))
    }

    @Test
    fun `hoca karari borc yazmaz kalan gunler dengelenir`() {
        val kasim = YearMonth.of(2026, 11)
        val aralik = YearMonth.of(2026, 12)
        var data = DataOps.ensureMonth(SeedData.build(), kasim)
        // Hoca: Yaren bu ay 1. Varsayılan olarak hoca kararıdır; kalan 29 gün diğerlerine dağılır.
        data = DataOps.setQuota(data, kasim, "yaren", 1)
        val q = data.month(kasim)!!.quotas
        assertTrue(q.single { it.personId == "yaren" }.let { it.manual && it.decision })
        assertEquals(29, q.filter { it.personId != "yaren" }.sumOf { it.final })
        val pr = ProblemBuilder.build(data, kasim, quotaOverride = DataOps.quotaMap(data.month(kasim)))
        data = DataOps.applySolution(data, pr, Solver(pr).solve().solutions.first().assignment)

        val cal = com.nobetmaker.core.calendar.CalendarService(data.dayOverrides, data.settings)
        val avail = com.nobetmaker.core.rules.Availability(data, cal)
        val ledger = com.nobetmaker.core.ledger.CountLedger(data, avail, RosterIndex(data))
        val bal = ledger.balance(kasim, aralik)
        val yaren = bal.getValue("yaren")
        assertEquals(1, yaren.held)
        assertEquals(0.0, yaren.diff, 1e-9, "hoca kararı borç yazmaz")
        assertEquals(listOf(kasim), yaren.decided)
        assertEquals(0.0, bal.values.sumOf { it.diff }, 1e-6, "artan ve eksilen günler diğerleri arasında sıfırlanır")
        // Payından fazla tutan Aralık'ta az, eksik tutan çok önerilir.
        val carry = ledger.carryInto(aralik)
        for ((id, b) in bal) assertEquals(-b.diff, carry[id] ?: 0.0, 1e-9, id)
        val dec = ProblemBuilder.suggestedQuotas(data, aralik).associateBy { it.personId }
        for ((id, b) in bal) assertEquals(-b.diff, dec.getValue(id).carryIn, 1e-9, "devreden $id")

        // Hoca kararı kapatılırsa (bizim düzeltmemiz) fark kişiye yazılır.
        val own = DataOps.setQuotaDecision(data, kasim, "yaren", false)
        val ownBal = com.nobetmaker.core.ledger.CountLedger(own, avail, RosterIndex(own)).balance(kasim, aralik).getValue("yaren")
        assertTrue(ownBal.diff < -0.5, "Yaren payından az tuttu: ${ownBal.diff}")
        // Sayı dengesi başlangıcından önceki aylar dengeye girmez.
        assertTrue(com.nobetmaker.core.ledger.CountLedger(data, avail, RosterIndex(data)).carryInto(kasim).isEmpty())
    }

    @Test
    fun `0_4_1 verisinde elle sabit sayilar hoca karari olur`() {
        val ym = YearMonth.of(2026, 11)
        val base = DataOps.setQuota(DataOps.ensureMonth(SeedData.build(), ym), ym, "yaren", 1)
        val v5 = base.copy(schemaVersion = 5, months = base.months.map { m -> m.copy(quotas = m.quotas.map { it.copy(decision = false) }) })
        val m = AppJson.decode(AppJson.encode(v5))
        assertTrue(m.month(ym)!!.quotas.single { it.personId == "yaren" }.decision)
        assertTrue(m.month(ym)!!.quotas.filter { it.personId != "yaren" }.none { it.decision })
    }

    /** Telefonda oluşturulan Kasım 2026 listesi (Yaren 1 hoca kararı, Aslı 6 bizim verdiğimiz). */
    private fun telefondakiKasim(asliKarar: Boolean): com.nobetmaker.core.model.AppData {
        val kasim = YearMonth.of(2026, 11)
        val gunler = ("kenan asli oguz sule betul fatma asli oguz kenan betul asli sule kenan oguz fatma " +
            "betul sule oguz yaren asli betul sule oguz asli kenan fatma betul sule asli fatma").split(" ")
        var data = DataOps.ensureMonth(SeedData.build(), kasim)
        data = DataOps.setQuota(data, kasim, "yaren", 1, decision = true)
        data = DataOps.setQuota(data, kasim, "asli", 6, decision = asliKarar)
        val list = gunler.mapIndexed { i, id -> com.nobetmaker.core.model.Assignment(kasim.atDay(i + 1), id) }
        return data.copy(
            months = data.months.map { m ->
                if (m.yearMonth == kasim) m.copy(versions = listOf(com.nobetmaker.core.model.RosterVersion(1, "", "telefon", list))) else m
            },
        )
    }

    @Test
    fun `ayni ayda kimse kendinden kidemliden az tutmaz`() {
        val kasim = YearMonth.of(2026, 11)
        val aralik = YearMonth.of(2026, 12)
        for (asliKarar in listOf(false, true)) {
            val data = telefondakiKasim(asliKarar)
            val q = ProblemBuilder.suggestedQuotas(data, aralik).associate { it.personId to it.final }
            // 0.4.2 burada Fatma 5, Betül 4 veriyordu: çömez kıdemliden az.
            assertEquals(mapOf("yaren" to 3, "kenan" to 4, "fatma" to 4, "betul" to 5, "sule" to 5, "oguz" to 5, "asli" to 5), q, "Aslı hoca kararı: $asliKarar")
            val bySeniority = data.persons.sortedBy { it.seniority }.map { q.getValue(it.id) }
            assertEquals(bySeniority.sorted(), bySeniority, "kıdem sırası: $bySeniority")

            // Kasım'da payları yakın olanlar (Kenan–Fatma; henüz son yılda olmayan Betül–Şule–Oğuz) yuvarlama farkını
            // birlikte taşır: kişiye yazılsaydı aynı sırayı koruyan sayılar bir sonraki ay sırayı bozardı.
            val cal = com.nobetmaker.core.calendar.CalendarService(data.dayOverrides, data.settings)
            val bal = com.nobetmaker.core.ledger.CountLedger(data, com.nobetmaker.core.rules.Availability(data, cal), RosterIndex(data)).balance(kasim, aralik)
            val d = { id: String -> bal.getValue(id).diff }
            assertEquals(d("kenan"), d("fatma"), 0.01)
            assertEquals(d("betul"), d("sule"), 0.01)
            assertEquals(d("sule"), d("oguz"), 0.01)
            if (!asliKarar) assertTrue(bal.getValue("asli").diff > 0.5, "Aslı'nın fazlası kendisine yazılır: ${bal.getValue("asli").diff}")
            else assertEquals(0.0, bal.getValue("asli").diff, 1e-9)
        }
    }

    @Test
    fun `telefondaki kasimdan sonra aralikta herkes iki zor gun tutar`() {
        // 0.4.3'te Kenan'a yalnız pazar, Oğuz'a cuma + cumartesi + pazar düşüyordu: cumalar ileriye bakmadan dağıtılıyordu.
        val aralik = YearMonth.of(2026, 12)
        val data = DataOps.ensureMonth(telefondakiKasim(asliKarar = false), aralik)
        val pr = ProblemBuilder.build(data, aralik, quotaOverride = DataOps.quotaMap(data.month(aralik)))
        val groups = listOf(com.nobetmaker.core.calendar.RotationGroup.VIZIT, com.nobetmaker.core.calendar.RotationGroup.CUMA, com.nobetmaker.core.calendar.RotationGroup.PAZAR)
        fun t(id: String) = groups.map { pr.rotationTarget[pr.personIndex(id)][it.ordinal] }
        for (id in listOf("kenan", "fatma", "betul", "sule", "oguz", "asli")) assertEquals(2, t(id).sum(), "$id: ${t(id)}")
        assertEquals(listOf(0, 1, 1), t("kenan"), "Kenan cuma + pazar")
        // Liste de aynı: kimseye üç zor gün yazılmaz.
        val best = Solver(pr).solve().solutions.first().assignment
        val held = best.indices.filter { pr.rotationIndex[it] in groups.map { g -> g.ordinal } }.groupingBy { pr.persons[best[it]].id }.eachCount()
        assertTrue(held.values.all { it <= 2 }, "zor günler: $held")
        assertEquals(2, held["kenan"])
    }

    /** Tablette girilen Kasım istekleri. */
    private fun tabletIstekleri(): List<DutyRequest> {
        fun d(n: Int) = YearMonth.of(2026, 11).atDay(n)
        return listOf(
            DutyRequest("r1", "kenan", RequestType.AVOID_RANGE, Priority.KESIN, rangeStart = d(14), rangeEnd = d(21)),
            DutyRequest("r2", "fatma", RequestType.WANT_DATES, Priority.KESIN, dates = listOf(d(22))),
            DutyRequest("r3", "betul", RequestType.AVOID_DATES, Priority.MUMKUNSE, dates = listOf(d(19), d(20))),
            DutyRequest("r4", "sule", RequestType.AVOID_RANGE, Priority.KESIN, rangeStart = d(6), rangeEnd = d(8)),
            DutyRequest("r5", "oguz", RequestType.AVOID_RANGE, Priority.KESIN, rangeStart = d(4), rangeEnd = d(7)),
            DutyRequest("r6", "asli", RequestType.AVOID_DATES, Priority.KESIN, dates = listOf(d(5), d(6))),
            DutyRequest("r7", "asli", RequestType.AVOID_DATES, Priority.KESIN, dates = listOf(d(13))),
        )
    }

    private fun kasimIstekli(requests: List<DutyRequest>): com.nobetmaker.core.solver.MonthProblem {
        val ym = YearMonth.of(2026, 11)
        var data = DataOps.ensureMonth(SeedData.build(), ym)
        data = DataOps.setQuota(data, ym, "yaren", 1, decision = true)
        data = DataOps.setQuota(data, ym, "asli", 6, decision = false)
        data = requests.fold(data) { acc, r -> DataOps.upsertRequest(acc, r) }
        return ProblemBuilder.build(data, ym, quotaOverride = DataOps.quotaMap(data.month(ym)))
    }

    private fun hardTotals(pr: com.nobetmaker.core.solver.MonthProblem, assignment: IntArray? = null): Map<String, Int> {
        val groups = listOf(com.nobetmaker.core.calendar.RotationGroup.VIZIT, com.nobetmaker.core.calendar.RotationGroup.CUMA, com.nobetmaker.core.calendar.RotationGroup.PAZAR)
        if (assignment == null) return pr.persons.associate { p -> p.id to groups.sumOf { pr.rotationTarget[pr.personIndex(p.id)][it.ordinal] } }
        return assignment.indices.filter { pr.rotationIndex[it] in groups.map { g -> g.ordinal } }.groupingBy { pr.persons[assignment[it]].id }.eachCount()
    }

    @Test
    fun `kesin istek zor gun payini azaltmaz`() {
        // 0.4.4'te Aslı iki cumayı kapatınca payı küçülüyor, sıradaki üçüncü zor gün Betül'e kayıyordu.
        val pr = kasimIstekli(tabletIstekleri())
        assertEquals(mapOf("yaren" to 0, "kenan" to 2, "fatma" to 2, "betul" to 2, "sule" to 2, "oguz" to 2, "asli" to 3), hardTotals(pr))
        assertTrue(pr.requestNotes.isEmpty(), "${pr.requestNotes}")
        val held = hardTotals(pr, Solver(pr).solve().solutions.first().assignment)
        assertEquals(3, held["asli"], "liste: $held")
        assertTrue(held.filterKeys { it != "asli" }.values.all { it <= 2 }, "liste: $held")
    }

    @Test
    fun `istek zor gun dagilimini degistirirse sebebiyle yazilir`() {
        // Aslı bütün cuma ve pazarlarını kapatırsa yalnız cumartesi tutabilir; fazla günler başkasına geçer.
        fun d(n: Int) = YearMonth.of(2026, 11).atDay(n)
        val block = DutyRequest("x", "asli", RequestType.AVOID_DATES, Priority.KESIN, dates = listOf(1, 6, 8, 13, 15, 20, 22, 27, 29).map { d(it) })
        val pr = kasimIstekli(listOf(block))
        assertEquals(1, hardTotals(pr)["asli"])
        val note = pr.requestNotes.single()
        assertTrue(note.startsWith("İstekler zor gün dağılımını değiştirdi: ") && "Aslı 3 → 1" in note && "(sebep: Aslı: " in note, note)
        assertTrue(note in ConflictDetector.detect(pr), "Kontrol sekmesinde de görünür")
    }

    @Test
    fun `izinde sigmayan nobet kisinin kendisine yazilir`() {
        // Oğuz 3–23 Aralık izinde: 5 yerine 4 nöbete sığar. Fark grup arkadaşlarına (Şule, Aslı) bölünüyordu.
        val kasim = YearMonth.of(2026, 11)
        val aralik = YearMonth.of(2026, 12)
        var data = DataOps.ensureMonth(SeedData.build(), kasim)
        val pk = ProblemBuilder.build(data, kasim, quotaOverride = DataOps.quotaMap(data.month(kasim)))
        data = DataOps.applySolution(data, pk, Solver(pk).solve().solutions.first().assignment)
        data = DataOps.ensureMonth(data, aralik)
        data = DataOps.refreshQuotas(DataOps.upsertPeriod(data, com.nobetmaker.core.model.Period("iz", "oguz", aralik.atDay(3), aralik.atDay(23), com.nobetmaker.core.model.PeriodType.YILLIK_IZIN)), aralik)
        assertEquals(4, data.month(aralik)!!.quotas.single { it.personId == "oguz" }.final)
        val pa = ProblemBuilder.build(data, aralik, quotaOverride = DataOps.quotaMap(data.month(aralik)))
        data = DataOps.applySolution(data, pa, Solver(pa).solve().solutions.first().assignment)
        val cal = com.nobetmaker.core.calendar.CalendarService(data.dayOverrides, data.settings)
        val bal = com.nobetmaker.core.ledger.CountLedger(data, com.nobetmaker.core.rules.Availability(data, cal), RosterIndex(data)).balance(aralik, aralik.plusMonths(1))
        assertTrue(bal.getValue("oguz").diff < -0.8, "Oğuz telafi eder: ${bal.getValue("oguz").diff}")
        for (id in listOf("sule", "asli")) assertTrue(kotlin.math.abs(bal.getValue(id).diff) < 0.3, "$id: ${bal.getValue(id).diff}")
    }

    @Test
    fun `0_4_4 verisinde bayram gunleri kagida gore duzeltilir`() {
        // Eski veri: 13 Nisan 2024 Kenan, 19 Haziran 2024 Kenan, 29 Ekim 2025 Yaren. 19 Haziran'ı kullanıcı elle Betül yapmış.
        val seed = SeedData.build()
        fun set(d: com.nobetmaker.core.model.AppData, date: LocalDate, id: String) = d.copy(
            months = d.months.map { m ->
                if (m.yearMonth != YearMonth.from(date)) m
                else m.copy(versions = m.versions.dropLast(1) + m.current!!.copy(assignments = m.current!!.assignments.map { if (it.date == date) it.copy(personId = id) else it }))
            },
        )
        var old = set(seed, LocalDate.of(2024, 4, 13), "kenan")
        old = set(old, LocalDate.of(2024, 6, 19), "betul")
        old = set(old, LocalDate.of(2025, 10, 29), "yaren").copy(schemaVersion = 7)
        val m = AppJson.decode(AppJson.encode(old))
        val r = RosterIndex(m)
        assertEquals("fatma", r.official[LocalDate.of(2024, 4, 13)])
        assertEquals("oguz", r.official[LocalDate.of(2025, 10, 29)])
        assertEquals("betul", r.official[LocalDate.of(2024, 6, 19)], "elle düzeltilen güne dokunulmaz")
        val ekim = m.month(YearMonth.of(2025, 10))!!
        assertEquals(old.month(YearMonth.of(2025, 10))!!.versions.size + 1, ekim.versions.size, "kapanmış ayda yeni sürüm")
        assertEquals("Kağıt listeye göre düzeltildi: 29. gün", ekim.current!!.note)
    }

    @Test
    fun `0_4_2 verisinde ondan fazla verilen sayi hoca karari olmaktan cikar`() {
        val kasim = YearMonth.of(2026, 11)
        val v6 = telefondakiKasim(asliKarar = true).let { it.copy(schemaVersion = 6) }
        val m = AppJson.decode(AppJson.encode(v6))
        val q = m.month(kasim)!!.quotas.associateBy { it.personId }
        assertTrue(q.getValue("yaren").decision, "hocanın düşürdüğü sayı karar olarak kalır")
        assertTrue(q.getValue("asli").manual && !q.getValue("asli").decision, "artanı Aslı'ya biz verdik")
        // Sayı dengesi başlangıcından önceki aylara dokunulmaz.
        val ekim = YearMonth.of(2026, 10)
        val old = DataOps.setQuota(DataOps.ensureMonth(SeedData.build(), ekim), ekim, "asli", 9, decision = true).copy(schemaVersion = 6)
        assertTrue(AppJson.decode(AppJson.encode(old)).month(ekim)!!.quotas.single { it.personId == "asli" }.decision)
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
