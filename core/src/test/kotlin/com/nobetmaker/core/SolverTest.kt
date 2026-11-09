package com.nobetmaker.core

import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.model.DutyRequest
import com.nobetmaker.core.model.Period
import com.nobetmaker.core.model.PeriodType
import com.nobetmaker.core.model.Priority
import com.nobetmaker.core.model.RequestType
import com.nobetmaker.core.solver.Explainer
import com.nobetmaker.core.solver.MonthProblem
import com.nobetmaker.core.solver.ProblemBuilder
import com.nobetmaker.core.solver.Solver
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SolverTest {

    private fun assertValid(pr: MonthProblem, a: IntArray) {
        assertEquals(pr.dayCount, a.size)
        for (i in a.indices) {
            val p = a[i]
            assertTrue(p >= 0, "gün ${pr.days[i]} boş")
            assertTrue(pr.available[p][i], "${pr.persons[p].name} ${pr.days[i]} uygun değil: ${pr.hardReasons[p][i]}")
        }
        for (p in pr.persons.indices) {
            for (dow in listOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
                val n = a.indices.count { a[it] == p && pr.days[it].dayOfWeek == dow }
                assertTrue(n <= 1, "${pr.persons[p].name}: ayda $n $dow")
            }
            assertEquals(pr.quotas[p], a.count { it == p }, "${pr.persons[p].name} kotası")
            val days = a.indices.filter { a[it] == p }
            days.zipWithNext().forEach { (x, y) -> assertTrue(y - x >= pr.minGap, "${pr.persons[p].name}: ${pr.days[x]} ve ${pr.days[y]}") }
        }
    }

    private fun print(pr: MonthProblem, a: IntArray) {
        for (i in a.indices) println("${pr.days[i]} ${pr.days[i].dayOfWeek.toString().take(3)} ${pr.category[i].label.padEnd(12)} ${pr.persons[a[i]].name}")
        val card = Explainer(pr).scoreCard(a)
        println("toplam ceza=${"%.1f".format(card.total)} karşılanmayan istek=${card.requestsUnmet} uzun tatil=${card.longHolidayHits}")
        card.perPerson.forEach { println("${it.person.name}: ${it.count}/${it.quota} nöbet, ${it.hours} saat, ${it.byCategory}") }
        println("3 gün aralık: ${card.tightGaps.size}, defter: ${card.memoryNotes}")
        card.rotationSummary.forEach { println(it) }
        println("sıra sapmaları: ${card.rotationMisses}")
    }

    @Test
    fun `kasim 2026 kurallara uygun ve hizli uretilir`() {
        val data = TestTeam.data()
        val month = YearMonth.of(2026, 11)
        val pr = ProblemBuilder.build(data, month)
        println("kotalar: " + pr.persons.indices.joinToString { "${pr.persons[it].name}=${pr.quotas[it]}" })
        val t0 = System.currentTimeMillis()
        val result = Solver(pr).solve()
        val ms = System.currentTimeMillis() - t0
        println("süre: $ms ms, alternatif: ${result.solutions.size}")
        assertTrue(result.solutions.isNotEmpty(), "çözüm yok: ${result.diagnostics?.problems}")
        result.solutions.forEach { assertValid(pr, it.assignment) }
        print(pr, result.solutions.first().assignment)

        // Yaren hafta sonu ve tatil tutmaz.
        val yaren = pr.personIndex("yaren")
        val best = result.solutions.first().assignment
        best.indices.filter { best[it] == yaren }.forEach {
            val dow = pr.days[it].dayOfWeek
            assertTrue(dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY)
        }
    }

    @Test
    fun `istekler ve izinler dikkate alinir`() {
        val month = YearMonth.of(2026, 11)
        val d = { day: Int -> LocalDate.of(2026, 11, day) }
        val data = TestTeam.data().copy(
            periods = listOf(
                Period("p1", "oguz", d(9), d(13), PeriodType.YILLIK_IZIN),
                Period("p2", "sule", d(16), d(20), PeriodType.YILLIK_IZIN),
            ),
            requests = listOf(
                DutyRequest("r1", "kenan", RequestType.AVOID_RANGE, Priority.MUMKUNSE, rangeStart = d(14), rangeEnd = d(21)),
                DutyRequest("r2", "fatma", RequestType.AVOID_DATES, Priority.MUMKUNSE, dates = listOf(d(9))),
                DutyRequest("r3", "yaren", RequestType.WANT_DATES, Priority.MUMKUNSE, dates = listOf(d(5), d(19))),
                DutyRequest("r4", "betul", RequestType.AVOID_RANGE, Priority.KESIN, rangeStart = d(1), rangeEnd = d(11)),
            ),
        )
        val pr = ProblemBuilder.build(data, month)
        val result = Solver(pr).solve()
        assertTrue(result.solutions.isNotEmpty(), "çözüm yok: ${result.diagnostics?.problems}")
        val a = result.solutions.first().assignment
        assertValid(pr, a)
        print(pr, a)
        val betul = pr.personIndex("betul")
        assertTrue((0 until 11).none { a[it] == betul }, "Betül'ün kesin isteği")
        val card = Explainer(pr).scoreCard(a)
        assertTrue(card.requestsUnmet.isEmpty(), "karşılanmayan: ${card.requestsUnmet}")
    }

    @Test
    fun `cozumsuz durumda aciklama verilir`() {
        val month = YearMonth.of(2026, 11)
        val d = { day: Int -> LocalDate.of(2026, 11, day) }
        // 3-9 Kasım arası 5 kişi izinde: kalan 2 kişi 7 günü günaşırı olmadan dolduramaz.
        val off = listOf("kenan", "fatma", "betul", "sule", "oguz")
        val data: AppData = TestTeam.data().copy(
            periods = off.mapIndexed { i, id -> Period("p$i", id, d(3), d(9), PeriodType.YILLIK_IZIN) },
        )
        val pr = ProblemBuilder.build(data, month)
        val result = Solver(pr).solve(seeds = 2)
        assertTrue(result.solutions.isEmpty())
        val diag = result.diagnostics!!
        println(diag.problems)
        println(diag.suggestions)
        assertTrue(diag.problems.any { it.contains("doldurulamıyor") }, "aralık uyarısı yok")
        assertTrue(diag.suggestions.none { it.contains("günaşırı", ignoreCase = true) })
    }
}
