package com.nobetmaker.core.seed

import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.model.Assignment
import com.nobetmaker.core.model.CoefficientPeriod
import com.nobetmaker.core.model.DayInfo
import com.nobetmaker.core.model.DayKind
import com.nobetmaker.core.model.EventKind
import com.nobetmaker.core.model.HistoryEntry
import com.nobetmaker.core.model.HistoryEvent
import com.nobetmaker.core.model.InformalChange
import com.nobetmaker.core.model.MonthPlan
import com.nobetmaker.core.model.MonthStatus
import com.nobetmaker.core.model.Person
import com.nobetmaker.core.model.PersonRule
import com.nobetmaker.core.model.RosterVersion
import com.nobetmaker.core.model.RuleType
import java.time.LocalDate
import java.time.YearMonth

/**
 * Uygulamanın ilk açılışta yüklediği başlangıç verisi: ekip ve kağıt listelerden aktarılan
 * Kasım 2025 – Ekim 2026 geçmişi (§16). Kağıttaki "A (B)" yazımı: resmide A, tutan B.
 * [flagged] okunması belirsiz ya da doğrulanması gereken günlerdir.
 */
object SeedData {

    private val ids = mapOf(
        "Yaren" to "yaren", "Kenan" to "kenan", "Fatma" to "fatma", "Betül" to "betul",
        "Şule" to "sule", "Oğuz" to "oguz", "Aslı" to "asli",
    )

    fun persons(): List<Person> = listOf(
        Person(
            "yaren", "Yaren", 1, "#E91E63",
            coefficients = listOf(CoefficientPeriod(duty = 0.50, hardDay = 0.50)),
            rules = listOf(RuleType.NO_SATURDAY, RuleType.NO_SUNDAY, RuleType.NO_HOLIDAY)
                .map { PersonRule(it, from = LocalDate.of(2026, 7, 6)) },
        ),
        Person("kenan", "Kenan", 2, "#2196F3", coefficients = listOf(CoefficientPeriod(duty = 0.80, hardDay = 0.65))),
        Person("fatma", "Fatma", 3, "#FF9800", coefficients = listOf(CoefficientPeriod(duty = 0.90, hardDay = 0.70))),
        Person(
            "betul", "Betül", 4, "#4CAF50", joinDate = LocalDate.of(2024, 11, 1),
            coefficients = listOf(CoefficientPeriod(duty = 0.97, hardDay = 0.94)),
        ),
        Person(
            "sule", "Şule", 5, "#FFC107", joinDate = LocalDate.of(2024, 11, 1),
            coefficients = listOf(CoefficientPeriod(duty = 0.98, hardDay = 0.96)),
        ),
        Person(
            "oguz", "Oğuz", 6, "#9C27B0", joinDate = LocalDate.of(2025, 4, 1),
            coefficients = listOf(CoefficientPeriod(duty = 0.99, hardDay = 0.98)),
        ),
        Person(
            "asli", "Aslı", 7, "#00BCD4", joinDate = LocalDate.of(2026, 8, 1),
            coefficients = listOf(CoefficientPeriod(duty = 1.00, hardDay = 1.00)),
        ),
    )

    /** Ayın resmi listesi, 1'inden başlayarak gün gün. */
    val rosters: Map<YearMonth, String> = linkedMapOf(
        YearMonth.of(2025, 11) to "Kenan Yaren Betül Fatma Şule Kenan Betül Oğuz Şule Yaren Betül Oğuz Fatma Şule Betül Fatma Şule Oğuz Fatma Yaren Oğuz Fatma Yaren Kenan Şule Oğuz Betül Fatma Şule Kenan",
        YearMonth.of(2025, 12) to "Oğuz Betül Şule Fatma Yaren Betül Şule Kenan Betül Oğuz Şule Betül Fatma Yaren Oğuz Şule Kenan Yaren Fatma Kenan Betül Oğuz Yaren Kenan Betül Şule Oğuz Fatma Betül Şule Fatma",
        YearMonth.of(2026, 1) to "Yaren Şule Betül Yaren Oğuz Betül Kenan Fatma Yaren Şule Betül Kenan Oğuz Fatma Betül Oğuz Fatma Şule Oğuz Fatma Yaren Şule Fatma Oğuz Kenan Betül Yaren Fatma Oğuz Şule Kenan",
        YearMonth.of(2026, 2) to "Fatma Oğuz Şule Fatma Kenan Betül Oğuz Şule Betül Oğuz Kenan Betül Fatma Kenan Oğuz Şule Betül Kenan Fatma Şule Betül Kenan Oğuz Fatma Şule Oğuz Fatma Şule",
        YearMonth.of(2026, 3) to "Betül Fatma Oğuz Şule Kenan Betül Şule Fatma Kenan Yaren Oğuz Betül Fatma Oğuz Şule Betül Oğuz Fatma Yaren Betül Oğuz Kenan Şule Yaren Oğuz Şule Kenan Fatma Yaren Kenan Şule",
        YearMonth.of(2026, 4) to "Oğuz Kenan Yaren Betül Şule Oğuz Betül Şule Oğuz Betül Fatma Kenan Yaren Şule Kenan Fatma Şule Oğuz Betül Şule Yaren Fatma Şule Oğuz Kenan Fatma Betül Oğuz Fatma Betül",
        YearMonth.of(2026, 5) to "Kenan Oğuz Betül Fatma Şule Oğuz Kenan Şule Oğuz Fatma Yaren Şule Fatma Oğuz Betül Şule Oğuz Kenan Betül Oğuz Şule Yaren Betül Kenan Oğuz Betül Yaren Oğuz Şule Fatma Oğuz",
        YearMonth.of(2026, 6) to "Yaren Şule Fatma Oğuz Betül Kenan Şule Oğuz Fatma Yaren Betül Fatma Şule Oğuz Kenan Betül Yaren Şule Kenan Oğuz Betül Şule Oğuz Betül Kenan Şule Fatma Yaren Betül Oğuz",
        YearMonth.of(2026, 7) to "Yaren Betül Oğuz Şule Yaren Kenan Şule Betül Oğuz Şule Betül Kenan Oğuz Fatma Şule Kenan Fatma Oğuz Betül Fatma Kenan Oğuz Yaren Fatma Kenan Oğuz Şule Betül Yaren Şule Fatma",
        YearMonth.of(2026, 8) to "Oğuz Aslı Betül Fatma Kenan Oğuz Yaren Betül Kenan Şule Yaren Fatma Aslı Kenan Şule Betül Yaren Oğuz Betül Kenan Oğuz Fatma Şule Oğuz Betül Şule Fatma Aslı Kenan Fatma Şule",
        YearMonth.of(2026, 9) to "Aslı Kenan Fatma Aslı Şule Betül Yaren Aslı Şule Betül Fatma Aslı Kenan Oğuz Yaren Betül Aslı Kenan Betül Oğuz Şule Betül Oğuz Yaren Şule Oğuz Fatma Şule Oğuz Fatma",
        YearMonth.of(2026, 10) to "Şule Kenan Oğuz Aslı Yaren Oğuz Fatma Aslı Oğuz Şule Fatma Aslı Kenan Betül Oğuz Fatma Kenan Betül Oğuz Şule Yaren Kenan Betül Aslı Şule Fatma Betül Aslı Fatma Şule Betül",
    )

    /** Gerçekte tutan (tarih → kişi). */
    val informal: List<Pair<LocalDate, String>> = listOf(
        LocalDate.of(2025, 11, 4) to "Yaren",
        LocalDate.of(2025, 11, 23) to "Fatma",
        LocalDate.of(2026, 1, 1) to "Fatma",
        LocalDate.of(2026, 1, 13) to "Yaren",
        LocalDate.of(2026, 1, 27) to "Oğuz",
        LocalDate.of(2026, 1, 29) to "Şule",
        LocalDate.of(2026, 1, 30) to "Oğuz",
        LocalDate.of(2026, 2, 1) to "Oğuz",
        LocalDate.of(2026, 3, 20) to "Fatma",
        LocalDate.of(2026, 3, 28) to "Betül",
        LocalDate.of(2026, 4, 9) to "Fatma",
        LocalDate.of(2026, 4, 11) to "Oğuz",
        LocalDate.of(2026, 4, 16) to "Oğuz",
        LocalDate.of(2026, 4, 18) to "Fatma",
        LocalDate.of(2026, 5, 2) to "Betül",
        LocalDate.of(2026, 5, 3) to "Kenan",
        LocalDate.of(2026, 6, 15) to "Fatma",
    )

    /** Okunması belirsiz ya da doğrulanması gereken günler. */
    val flagged: Set<LocalDate> = setOf(
        LocalDate.of(2025, 11, 3), LocalDate.of(2025, 11, 4), LocalDate.of(2025, 11, 18), LocalDate.of(2025, 11, 23),
        LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 7), LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 29), LocalDate.of(2026, 1, 30),
        LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 15), LocalDate.of(2026, 2, 18), LocalDate.of(2026, 2, 25),
        LocalDate.of(2026, 3, 14), LocalDate.of(2026, 3, 19), LocalDate.of(2026, 3, 20), LocalDate.of(2026, 3, 28),
        LocalDate.of(2026, 4, 9), LocalDate.of(2026, 4, 11), LocalDate.of(2026, 4, 16), LocalDate.of(2026, 4, 18),
        LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 3), LocalDate.of(2026, 5, 9), LocalDate.of(2026, 5, 25),
        LocalDate.of(2026, 8, 30),
    )

    /** 9 günlük Kurban Bayramı tatili için idari izin günleri (doğrulanacak). */
    fun dayOverrides(): List<DayInfo> = listOf(
        DayInfo(LocalDate.of(2026, 5, 25), DayKind.IDARI_IZIN, "Kurban Bayramı idari izin", eventId = "2026-kurban"),
        DayInfo(LocalDate.of(2026, 5, 26), DayKind.IDARI_IZIN, "Kurban Bayramı arifesi (idari izin)", eventId = "2026-kurban"),
    )

    /** Bayram sayfasından: yalnızca bugünkü ekibin günleri (kalanları uzmanlar ya da ayrılanlar tuttu). */
    fun historyEvents(): List<HistoryEvent> = listOf(
        HistoryEvent(
            "h-2024-ramazan", "2024 Ramazan Bayramı", EventKind.BAYRAM, LocalDate.of(2024, 4, 9), LocalDate.of(2024, 4, 12), 10,
            listOf(HistoryEntry("yaren", 2), HistoryEntry("fatma", 1)),
        ),
        HistoryEvent(
            "h-2024-kurban", "2024 Kurban Bayramı", EventKind.BAYRAM, LocalDate.of(2024, 6, 15), LocalDate.of(2024, 6, 19), 10,
            listOf(HistoryEntry("fatma", 2), HistoryEntry("yaren", 1)),
        ),
        HistoryEvent(
            "h-2025-ramazan", "2025 Ramazan Bayramı", EventKind.BAYRAM, LocalDate.of(2025, 3, 29), LocalDate.of(2025, 4, 1), 10,
            listOf(HistoryEntry("sule", 3), HistoryEntry("fatma", 3), HistoryEntry("yaren", 1), HistoryEntry("betul", 1), HistoryEntry("kenan", 1)),
        ),
        HistoryEvent(
            "h-2025-kurban", "2025 Kurban Bayramı", EventKind.BAYRAM, LocalDate.of(2025, 6, 5), LocalDate.of(2025, 6, 9), 5,
            listOf(HistoryEntry("fatma", 2), HistoryEntry("kenan", 1), HistoryEntry("betul", 1), HistoryEntry("oguz", 1)),
        ),
    )

    fun build(): AppData {
        val months = rosters.map { (ym, line) ->
            val names = line.split(' ').filter { it.isNotBlank() }
            require(names.size == ym.lengthOfMonth()) { "$ym: ${names.size} isim, ${ym.lengthOfMonth()} gün" }
            val assignments = names.mapIndexed { i, name ->
                Assignment(ym.atDay(i + 1), ids[name] ?: error("Bilinmeyen isim: $name"), locked = true)
            }
            val status = if (ym < YearMonth.of(2026, 10)) MonthStatus.KAPANDI else MonthStatus.YAYINLANDI
            MonthPlan(
                ym.year, ym.monthValue, status,
                versions = listOf(RosterVersion(1, "${ym.atDay(1)}T00:00", "Kağıt listeden aktarıldı", assignments)),
            )
        }
        val official = months.flatMap { it.current!!.assignments }.associate { it.date to it.personId }
        val changes = informal.mapIndexed { i, (date, name) ->
            InformalChange(
                id = "seed-$i", date = date,
                officialPersonId = official[date] ?: error("$date resmi listede yok"),
                actualPersonId = ids.getValue(name),
                createdAt = "${date}T12:00",
                note = "Kağıt listeden aktarıldı",
            )
        }
        return AppData(
            persons = persons(),
            dayOverrides = dayOverrides(),
            months = months,
            informalChanges = changes,
            historyEvents = historyEvents(),
        )
    }
}
