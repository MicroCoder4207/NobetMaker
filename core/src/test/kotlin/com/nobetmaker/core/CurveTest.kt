package com.nobetmaker.core

import com.nobetmaker.core.calendar.CalendarService
import com.nobetmaker.core.calendar.Category
import com.nobetmaker.core.calendar.RotationGroup
import com.nobetmaker.core.ledger.Ledger
import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.model.Assignment
import com.nobetmaker.core.model.CoefficientPeriod
import com.nobetmaker.core.model.MonthPlan
import com.nobetmaker.core.model.MonthStatus
import com.nobetmaker.core.model.Period
import com.nobetmaker.core.model.PeriodType
import com.nobetmaker.core.model.Person
import com.nobetmaker.core.model.PersonRule
import com.nobetmaker.core.model.RosterVersion
import com.nobetmaker.core.model.RuleType
import com.nobetmaker.core.ops.DataOps
import com.nobetmaker.core.rules.Availability
import com.nobetmaker.core.rules.Coefficients
import com.nobetmaker.core.rules.RosterIndex
import com.nobetmaker.core.seed.SeedData
import com.nobetmaker.core.solver.ProblemBuilder
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Kıdem eğrisi, ekipte sayılma, tatil/vizit ayrımı ve uzun hafıza (0.4.0). */
class CurveTest {

    private val seed = SeedData.build()
    private val nov15 = LocalDate.of(2026, 11, 15)

    private fun near(expected: Double, actual: Double, tol: Double = 0.005) =
        assertTrue(kotlin.math.abs(expected - actual) <= tol, "beklenen $expected, gelen $actual")

    @Test
    fun `kidem egrisi yil ilerledikce iner ve herkes ayni egriden gecer`() {
        val coef = Coefficients(seed)
        val p = { id: String -> seed.person(id)!! }
        near(0.808, coef.on(p("kenan"), nov15).duty)
        near(0.658, coef.on(p("kenan"), nov15).hardDay)
        assertTrue(coef.on(p("fatma"), nov15).duty > coef.on(p("kenan"), nov15).duty, "Fatma iki ay çömez, çok az yüksek")
        assertTrue(coef.on(p("fatma"), nov15).duty - coef.on(p("kenan"), nov15).duty < 0.01)
        near(0.971, coef.on(p("betul"), nov15).duty, 0.002)
        // Betül Aralık'ta son yılına giriyor.
        near(0.82, coef.on(p("betul"), LocalDate.of(2026, 12, 2)).duty, 0.002)
        assertEquals(LocalDate.of(2026, 12, 1), coef.dateAtMonths(p("betul"), coef.lastYearStart))
        assertEquals(LocalDate.of(2027, 8, 1), coef.dateAtMonths(p("kenan"), coef.totalMonths))
        // Aslı ilk yılında; Yaren uzatmada (cumartesi, pazar, tatil yok).
        near(0.996, coef.on(p("asli"), nov15).duty, 0.002)
        assertEquals(0.5, coef.on(p("yaren"), nov15).duty)
        assertEquals("uzatma", coef.stageText(p("yaren"), nov15))
        assertEquals("1. yıl 11. ay", coef.stageText(p("asli"), nov15))
        assertTrue(coef.inExtension(p("yaren"), nov15))
        // Elle girilen katsayı eğrinin yerine geçer.
        val manual = p("fatma").copy(autoCoefficients = false)
        assertEquals(0.90, coef.on(manual, nov15).duty)
    }

    @Test
    fun `dogum izni asistanlik suresinden duser ve kucuk ekipte indirim yariya iner`() {
        val canay = Person(
            "canay", "Canay", 6, "#94D82D", residencyStart = LocalDate.of(2024, 9, 1),
        )
        val leave = Period("izin", "canay", LocalDate.of(2025, 5, 1), LocalDate.of(2026, 10, 31), PeriodType.DOGUM_IZNI)
        val data = AppData(persons = listOf(canay), periods = listOf(leave))
        val coef = Coefficients(data)
        // Eylül 2024 → Kasım 2026: 26 ay, 18 ayı izin → ~8 ay.
        val m = coef.residencyMonths(canay, nov15)!!
        assertTrue(m in 7.5..9.0, "izin düşülmeli: $m")

        // Kasım 2026'da yalnızca 4 kişi kalsaydı Kenan'ın son yıl indirimi yarıya inerdi.
        val small = seed.copy(persons = seed.persons.filter { it.id in setOf("kenan", "sule", "oguz", "asli") })
        val k = Coefficients(small).on(small.person("kenan")!!, nov15)
        near(1.0 - (1.0 - 0.808) / 2, k.duty)
    }

    @Test
    fun `bolume katilis tek basina ekipte saymaz, kaydi olmayan borcsuz baslar`() {
        val cal = CalendarService(seed.dayOverrides, seed.settings)
        val avail = Availability(seed, cal)
        // Aslı Ocak 2026'da bölüme katıldı ama ilk nöbeti Ağustos 2026.
        assertEquals(LocalDate.of(2026, 8, 2), avail.presenceStart(seed.person("asli")!!))
        assertEquals(LocalDate.of(2025, 6, 4), avail.presenceStart(seed.person("oguz")!!))
        assertEquals(LocalDate.of(2025, 3, 25), avail.presenceStart(seed.person("sule")!!))
        // Yeni gelen (kaydı yok, bölüme katılışı geçmişte): kayıtların bittiği günün ertesinden.
        val yeni = Person("yeni", "Yeni", 8, "#343A40", joinDate = LocalDate.of(2026, 1, 1), residencyStart = LocalDate.of(2026, 1, 1))
        val withNew = seed.copy(persons = seed.persons + yeni)
        val a2 = Availability(withNew, cal)
        assertEquals(LocalDate.of(2026, 11, 1), a2.presenceStart(yeni))
        // Nöbete katılış girilmişse o esas.
        val a3 = Availability(withNew.copy(persons = seed.persons + yeni.copy(dutyStart = LocalDate.of(2026, 12, 1))), cal)
        assertEquals(LocalDate.of(2026, 12, 1), a3.presenceStart(yeni.copy(dutyStart = LocalDate.of(2026, 12, 1))))
    }

    @Test
    fun `yeni baslayana ilk ay vizit yazilmaz`() {
        val yeni = Person("yeni", "Yeni", 8, "#343A40", residencyStart = LocalDate.of(2026, 5, 1), dutyStart = LocalDate.of(2026, 11, 1))
        val data = seed.copy(persons = seed.persons + yeni)
        val avail = Availability(data, CalendarService(data.dayOverrides, data.settings))
        val sat = LocalDate.of(2026, 11, 7)
        assertTrue("İlk nöbet ayı: vizit yok" in avail.hardReasons(yeni, sat))
        assertTrue(avail.hardReasons(yeni, LocalDate.of(2026, 11, 8)).isEmpty(), "pazar yazılabilir")
        assertFalse(avail.eligibleForShare(yeni, sat), "ilk ayın viziti borç da yazmaz")
        assertTrue(avail.hardReasons(yeni, LocalDate.of(2026, 12, 5)).isEmpty(), "ikinci ay vizit yazılabilir")
    }

    @Test
    fun `tatil gunleri sira grubuna girmez, idari izin bayrama dahil`() {
        val cal = CalendarService(seed.dayOverrides, seed.settings)
        // 29 Ekim 2026 vizitliydi ama yalnızca resmi tatil sayılır.
        assertNull(cal.rotationGroup(LocalDate.of(2026, 10, 29), history = true))
        assertEquals(Category.RESMI_TATIL, cal.category(LocalDate.of(2026, 10, 29)))
        // Bayram tatiline denk gelen cumartesi vizit sırasına girmez.
        assertNull(cal.rotationGroup(LocalDate.of(2026, 5, 23), history = false))
        assertEquals(RotationGroup.VIZIT, cal.rotationGroup(LocalDate.of(2026, 11, 7), history = false))
        // 2025 Ramazan: 2–4 Nisan idari izin, 9 günlük tatilin parçası.
        for (d in 2..4) {
            val date = LocalDate.of(2025, 4, d)
            assertEquals(Category.BAYRAM, cal.category(date), "$date")
            assertEquals(1.0, cal.holidayFraction(date))
        }
        assertEquals(Category.BAYRAM, cal.category(LocalDate.of(2024, 6, 21)))
        // Arefe yarım sayılır, yılbaşı gecesi bir.
        assertEquals(0.5, cal.longWeight(LocalDate.of(2026, 10, 28)))
        assertEquals(1.0, cal.longWeight(LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun `vizitte esitlikte once comez`() {
        val team = (1..6).map { i ->
            Person("p$i", "K$i", i, "#343A40", autoCoefficients = false, coefficients = listOf(CoefficientPeriod(duty = 1.0, hardDay = 1.0)))
        }
        val data = AppData(persons = team)
        val cal = CalendarService(data.dayOverrides, data.settings)
        val avail = Availability(data, cal)
        val ym = YearMonth.of(2026, 11)
        val days = (1..ym.lengthOfMonth()).map { ym.atDay(it) }
        val state = Ledger(data, cal, avail, RosterIndex(data)).rotation(team, ym.minusMonths(6).atDay(1), ym.atDay(1), days, { _, _ -> true })
        assertEquals(listOf("p6", "p5", "p4", "p3"), state.queue[RotationGroup.VIZIT.ordinal])
    }

    @Test
    fun `cumartesi once son yila girmeyenlere kalanlar kidemlilerde sirayla`() {
        fun person(id: String, seniority: Int, start: LocalDate) =
            Person(id, id, seniority, "#343A40", residencyStart = start, dutyStart = LocalDate.of(2025, 9, 1))
        val team = listOf(
            person("k1", 1, LocalDate.of(2023, 7, 1)),
            person("k2", 2, LocalDate.of(2023, 8, 1)),
            person("c1", 3, LocalDate.of(2024, 6, 1)),
            person("c2", 4, LocalDate.of(2024, 9, 1)),
            person("c3", 5, LocalDate.of(2025, 1, 1)),
            person("c4", 6, LocalDate.of(2025, 3, 1)),
        )
        fun month(ym: YearMonth, vararg days: Pair<Int, String>) = MonthPlan(
            ym.year, ym.monthValue, MonthStatus.KAPANDI,
            versions = listOf(RosterVersion(1, "", null, days.map { (d, id) -> Assignment(ym.atDay(d), id) })),
        )
        // k1 en son 17 Ekim'de, k2 22 Ağustos'ta cumartesi tuttu; ikisi de son yılda.
        val data = AppData(persons = team, months = listOf(month(YearMonth.of(2026, 8), 22 to "k2"), month(YearMonth.of(2026, 10), 17 to "k1")))
        val cal = CalendarService(data.dayOverrides, data.settings)
        val avail = Availability(data, cal)
        assertTrue(avail.coefficients.isSenior(team[0], LocalDate.of(2027, 1, 2)))
        assertFalse(avail.coefficients.isSenior(team[2], LocalDate.of(2027, 1, 2)))
        val ledger = Ledger(data, cal, avail, RosterIndex(data))
        fun state(ym: YearMonth) = ledger.rotation(team, ym.minusMonths(6).atDay(1), ym.atDay(1), (1..ym.lengthOfMonth()).map { ym.atDay(it) }, { _, _ -> true })

        // Şubat 2027: 4 cumartesi, 4 çömez; kıdemliye cumartesi düşmez.
        assertEquals(listOf("c4", "c3", "c2", "c1"), state(YearMonth.of(2027, 2)).queue[RotationGroup.VIZIT.ordinal])
        // Ocak 2027: 5 cumartesi; artan biri en uzun süredir tutmayan kıdemliye (k2) gider.
        val jan = state(YearMonth.of(2027, 1))
        assertEquals(listOf("c4", "c3", "c2", "c1", "k2"), jan.queue[RotationGroup.VIZIT.ordinal])
        // Cumartesi tutan kıdemliye aynı ay pazar verilmez; kalan pazar diğer kıdemliye gider.
        val sundays = jan.queue[RotationGroup.PAZAR.ordinal]
        assertFalse("k2" in sundays, "k2 aynı ay hem cumartesi hem pazar: $sundays")
        assertTrue("k1" in sundays, "pazarlar: $sundays")
    }

    @Test
    fun `zor gunler toplam olarak dengelenir`() {
        // Kasım 2026: cumartesiler çömezlere gider; cumartesi tutmayan Kenan ve Fatma cuma ve pazarı önce alır.
        val ym = YearMonth.of(2026, 11)
        val data = DataOps.ensureMonth(seed, ym)
        val pr = ProblemBuilder.build(data, ym, quotaOverride = DataOps.quotaMap(data.month(ym)))
        fun target(id: String, g: RotationGroup) = pr.rotationTarget[pr.personIndex(id)][g.ordinal]
        for (id in listOf("kenan", "fatma")) {
            assertEquals(0, target(id, RotationGroup.VIZIT), "$id cumartesi")
            assertEquals(2, target(id, RotationGroup.CUMA) + target(id, RotationGroup.PAZAR), "$id cuma + pazar")
        }
        // 13 zor gün, cumartesi ve pazar tutabilen 6 kişi: en fazla bir kişiye üçü birden düşer.
        val triple = pr.persons.count { p ->
            listOf(RotationGroup.VIZIT, RotationGroup.CUMA, RotationGroup.PAZAR).all { g -> target(p.id, g) > 0 }
        }
        assertTrue(triple <= 1, "üç zor günlü kişi sayısı $triple")
    }

    @Test
    fun `bayram dengesi o gunku katsayiyla ve 2024'ten itibaren`() {
        val cal = CalendarService(seed.dayOverrides, seed.settings)
        val avail = Availability(seed, cal)
        val persons = seed.persons.filter { it.isActiveOn(nov15) }
        val long = Ledger(seed, cal, avail, RosterIndex(seed)).longMemory(persons, LocalDate.of(2026, 11, 1), emptyList())
        val bi = long.categories.indexOf(Category.BAYRAM)
        val bal = persons.associate { it.id to long.base.getValue(it.id)[bi] }
        // Analizdeki tablo: Kenan ve Şule geride, Oğuz ve Fatma önde.
        assertTrue(bal.getValue("kenan") < -1.2, "Kenan ${bal["kenan"]}")
        assertTrue(bal.getValue("sule") < -0.8, "Şule ${bal["sule"]}")
        assertTrue(bal.getValue("oguz") > 1.5, "Oğuz ${bal["oguz"]}")
        assertTrue(bal.getValue("fatma") > 1.0, "Fatma ${bal["fatma"]}")
        // 2025 Kurban'ın arefesi (5 Haziran, Fatma) yarım gün.
        assertEquals(7.5, long.days.getValue("fatma")[bi])
    }

    @Test
    fun `ayrilanin gunleri kalanlara borc yazmaz`() {
        val cal = CalendarService(seed.dayOverrides, seed.settings)
        fun bayram(data: AppData, id: String): Double {
            val avail = Availability(data, cal)
            val persons = data.persons.filter { it.isActiveOn(nov15) }
            val long = Ledger(data, cal, avail, RosterIndex(data)).longMemory(persons, LocalDate.of(2026, 11, 1), emptyList())
            return long.base.getValue(id)[long.categories.indexOf(Category.BAYRAM)]
        }
        val left = seed.copy(persons = seed.persons.map { if (it.id == "kenan") it.copy(leaveDate = LocalDate.of(2026, 10, 31)) else it })
        near(bayram(seed, "sule"), bayram(left, "sule"), 1e-9)
        near(bayram(seed, "oguz"), bayram(left, "oguz"), 1e-9)
    }

    @Test
    fun `ayda sabit sayi kurali nobet sayisini sabitler`() {
        val ym = YearMonth.of(2026, 11)
        val data = seed.copy(
            persons = seed.persons.map { if (it.id == "kenan") it.copy(rules = listOf(PersonRule(RuleType.MONTHLY_COUNT, from = LocalDate.of(2026, 11, 1), count = 3))) else it },
        )
        val pr = ProblemBuilder.build(data, ym)
        val entries = pr.quotaResult!!.entries.associateBy { it.personId }
        assertEquals(3, entries.getValue("kenan").final)
        assertFalse(entries.getValue("kenan").manual, "kuraldan gelen sayı elle sabitlenmiş sayılmaz")
        assertEquals(30, entries.values.sumOf { it.final })
        // Kural ayın dışındaysa etkisi yok.
        val later = seed.copy(persons = data.persons.map { if (it.id == "kenan") it.copy(rules = listOf(PersonRule(RuleType.MONTHLY_COUNT, from = LocalDate.of(2027, 1, 1), count = 3))) else it })
        assertEquals(4, ProblemBuilder.build(later, ym).quotaResult!!.entries.single { it.personId == "kenan" }.final)
    }

    @Test
    fun `yayinlanmis ayda gecmis gunler kilitlenir`() {
        val ym = YearMonth.of(2026, 10)
        val locked = DataOps.lockThrough(seed, ym, LocalDate.of(2026, 10, 15))
        val a = locked.month(ym)!!.current!!.assignments
        assertTrue(a.filter { it.date.dayOfMonth <= 15 }.all { it.locked })
    }
}
