package com.nobetmaker.core.seed

import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.model.Assignment
import com.nobetmaker.core.model.CoefficientPeriod
import com.nobetmaker.core.model.DayInfo
import com.nobetmaker.core.model.DayKind
import com.nobetmaker.core.model.HistoryEvent
import com.nobetmaker.core.model.InformalChange
import com.nobetmaker.core.model.MonthPlan
import com.nobetmaker.core.model.MonthStatus
import com.nobetmaker.core.model.Person
import com.nobetmaker.core.model.PersonPalette
import com.nobetmaker.core.model.PersonRule
import com.nobetmaker.core.model.RosterVersion
import com.nobetmaker.core.model.RuleType
import com.nobetmaker.core.model.Visit
import java.time.LocalDate
import java.time.YearMonth

/**
 * Uygulamanın ilk açılışta yüklediği başlangıç verisi: ekip ve Nisan 2024 – Ekim 2026 resmi listeleri (§16).
 * Nisan 2024 – Mart 2025 hastanenin Excel çalışma planlarından, sonrası kağıt listelerden aktarıldı.
 * Bayramlarda el yazısı kağıt esastır; Excel'le çelişen günler ona göre düzeltildi ([paperCorrections]).
 * "-" asistanın tutmadığı gündür (o dönem uzmanlar tuttu; 21 Nisan 2025'i doğum iznindeki Canay tuttu).
 * Kağıttaki "A (B)" yazımı: resmide A, tutan B. [flagged] okunması belirsiz ya da doğrulanması gereken günlerdir.
 */
object SeedData {

    private val ids = mapOf(
        "Yaren" to "yaren", "Kenan" to "kenan", "Fatma" to "fatma", "Betül" to "betul",
        "Şule" to "sule", "Oğuz" to "oguz", "Aslı" to "asli",
    )

    fun persons(): List<Person> = listOf(
        // Yaren asistanlığa 2021'de başka hastanede başladı (ay tahmini: 4 yılı Nisan 2025'te doldu, o ay cumartesiden
        // çıktı), Aralık 2023'te geldi; şimdi uzatmada.
        Person(
            "yaren", "Yaren", 1, PersonPalette.colors[0],
            joinDate = LocalDate.of(2023, 12, 1), residencyStart = LocalDate.of(2021, 4, 1),
            coefficients = listOf(CoefficientPeriod(duty = 0.50, hardDay = 0.50)),
            rules = listOf(RuleType.NO_SATURDAY, RuleType.NO_SUNDAY, RuleType.NO_HOLIDAY)
                .map { PersonRule(it, from = LocalDate.of(2026, 7, 6)) },
        ),
        Person(
            "kenan", "Kenan", 2, PersonPalette.colors[1],
            joinDate = LocalDate.of(2023, 8, 1), residencyStart = LocalDate.of(2023, 8, 1),
            coefficients = listOf(CoefficientPeriod(duty = 0.80, hardDay = 0.65)),
        ),
        Person(
            "fatma", "Fatma", 3, PersonPalette.colors[2],
            joinDate = LocalDate.of(2023, 10, 1), residencyStart = LocalDate.of(2023, 10, 1),
            coefficients = listOf(CoefficientPeriod(duty = 0.90, hardDay = 0.70)),
        ),
        Person(
            "betul", "Betül", 4, PersonPalette.colors[3],
            joinDate = LocalDate.of(2023, 12, 1), residencyStart = LocalDate.of(2023, 12, 1),
            coefficients = listOf(CoefficientPeriod(duty = 0.97, hardDay = 0.94)),
        ),
        Person(
            "sule", "Şule", 5, PersonPalette.colors[4],
            joinDate = LocalDate.of(2024, 8, 1), residencyStart = LocalDate.of(2024, 8, 1),
            coefficients = listOf(CoefficientPeriod(duty = 0.98, hardDay = 0.96)),
        ),
        Person(
            "oguz", "Oğuz", 6, PersonPalette.colors[5],
            joinDate = LocalDate.of(2024, 12, 1), residencyStart = LocalDate.of(2024, 12, 1),
            coefficients = listOf(CoefficientPeriod(duty = 0.99, hardDay = 0.98)),
        ),
        Person(
            "asli", "Aslı", 7, PersonPalette.colors[6],
            joinDate = LocalDate.of(2026, 1, 1), residencyStart = LocalDate.of(2026, 1, 1),
            coefficients = listOf(CoefficientPeriod(duty = 1.00, hardDay = 1.00)),
        ),
    )

    /**
     * El yazısı bayram kağıdı ve kullanıcının bildirdiği düzeltmeler (tarih, Excel'deki, doğrusu). Listelere işlendi;
     * eski sürümden gelen veride de 0.4.5 göçüyle düzeltilir (o gün hâlâ eski kişi yazılıysa).
     */
    val paperCorrections: List<Triple<LocalDate, String, String>> = listOf(
        Triple(LocalDate.of(2024, 4, 13), "kenan", "fatma"), // Ramazan 2024: 2 Yaren + 1 Fatma
        Triple(LocalDate.of(2024, 6, 19), "kenan", "yaren"), // Kurban 2024: 2 Fatma + 1 Yaren
        Triple(LocalDate.of(2025, 10, 29), "yaren", "oguz"), // 29 Ekim 2025'i Oğuz tuttu
    )

    /** Ayın resmi listesi, 1'inden başlayarak gün gün. */
    val rosters: Map<YearMonth, String> = linkedMapOf(
        YearMonth.of(2024, 4) to "- - - - - - Yaren - - Yaren - - Fatma - - - - - - - - - - - - - - - Yaren Kenan",
        YearMonth.of(2024, 5) to "- - - Kenan - - - - Kenan - Yaren - - - - - Kenan Fatma - - Fatma Yaren - - Fatma - - - Yaren - Fatma",
        YearMonth.of(2024, 6) to "- Yaren - - - Yaren Kenan Fatma - - - - Fatma - - - Fatma - Yaren - Fatma - - Fatma - - - Yaren Kenan -",
        YearMonth.of(2024, 7) to "Yaren - - - Yaren Kenan - - - - - - Kenan - - - - Kenan Betül Fatma Yaren - - - Fatma - Betül Fatma - - Fatma",
        YearMonth.of(2024, 8) to "- - Betül Kenan - - - - Fatma Yaren Betül - - Kenan - - Fatma - Yaren Fatma - - Kenan Fatma - - Fatma - Yaren Betül Fatma",
        YearMonth.of(2024, 9) to "- Yaren Fatma Kenan Betül - Yaren Betül - - - - Yaren Betül - - Fatma Betül - Kenan Fatma - - Fatma Kenan - Betül Fatma Kenan -",
        YearMonth.of(2024, 10) to "Yaren - - Fatma Betül - Fatma Kenan - - Betül Fatma Yaren Kenan Betül - - - Yaren Kenan Fatma Betül - - - Fatma Kenan Yaren Betül Fatma -",
        YearMonth.of(2024, 11) to "Kenan Yaren - Betül Fatma - - Yaren Fatma - Yaren Fatma - - Betül Kenan - Fatma Betül Kenan - Fatma Betül - Kenan Betül Yaren - Betül Fatma",
        YearMonth.of(2024, 12) to "Fatma Betül Yaren - - Kenan Fatma - Betül Fatma - - Fatma Kenan - Betül Yaren Kenan - Yaren Fatma - Betül Fatma - - Fatma Betül - Yaren Betül",
        YearMonth.of(2025, 1) to "Kenan Yaren Fatma Betül Kenan - Fatma Betül Kenan Yaren Fatma Betül - - - Betül Fatma Kenan Yaren - Betül Yaren Fatma Betül Yaren Fatma - - - Betül Fatma",
        YearMonth.of(2025, 2) to "Yaren Kenan Betül - Kenan Yaren Fatma Betül Yaren - Betül Yaren Fatma Betül Kenan Fatma - - Fatma Betül Yaren Fatma Betül - - Betül Kenan Fatma",
        YearMonth.of(2025, 3) to "Betül Yaren - - Fatma Kenan Betül Fatma Kenan - - Betül Yaren Fatma Kenan Betül - - Fatma Betül Kenan Yaren Fatma - Şule Yaren Kenan Fatma Şule Yaren Fatma",
        YearMonth.of(2025, 4) to "Betül Şule Fatma Kenan Şule Fatma Kenan Yaren Betül Şule Fatma Betül Yaren Şule Betül Yaren Kenan Şule Fatma Kenan - Fatma Betül Yaren Fatma Kenan Betül Fatma Yaren Şule",
        YearMonth.of(2025, 5) to "Kenan Betül Fatma Şule Yaren Fatma Betül Şule Fatma Betül Yaren Fatma Şule Betül Fatma Kenan Şule Yaren Kenan Şule Fatma Betül Kenan Fatma Yaren Kenan Betül Yaren Şule Fatma Kenan",
        YearMonth.of(2025, 6) to "Şule Fatma Kenan Oğuz Fatma Kenan Oğuz Fatma Betül Oğuz Fatma Yaren Şule Kenan Betül Fatma Şule Kenan Fatma Yaren Betül Fatma Yaren Oğuz Şule Betül Oğuz Fatma Şule Betül",
        YearMonth.of(2025, 7) to "Oğuz Şule Fatma Kenan Oğuz Betül Fatma Oğuz Yaren Betül Şule Kenan Fatma Oğuz Fatma Yaren Şule Kenan Fatma Oğuz Betül Fatma Şule Kenan Fatma Betül Yaren Şule Fatma Betül Yaren",
        YearMonth.of(2025, 8) to "Oğuz Fatma Şule Oğuz Betül Şule Fatma Betül Kenan Yaren Fatma Betül Oğuz Fatma Kenan Betül Fatma Yaren Kenan Fatma Yaren Oğuz Fatma Betül Kenan Şule Betül Oğuz Kenan Şule Oğuz",
        YearMonth.of(2025, 9) to "Fatma Betül Kenan Şule Fatma Betül Şule Fatma Yaren Kenan Betül Fatma Şule Yaren Oğuz Betül Şule Yaren Kenan Oğuz Fatma Şule Betül Fatma Oğuz Kenan Fatma Betül Oğuz Fatma",
        YearMonth.of(2025, 10) to "Kenan Yaren Şule Oğuz Fatma Kenan Betül Şule Oğuz Kenan Betül Yaren Fatma Oğuz Yaren Şule Betül Fatma Şule Oğuz Betül Fatma Yaren Şule Kenan Oğuz Şule Fatma Oğuz Betül Fatma",
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
        LocalDate.of(2025, 5, 18) to "Şule",
        LocalDate.of(2025, 7, 15) to "Yaren",
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
        // Ekim'de 6 vizit vardı: 5 Cumartesi ve 29 Ekim.
        DayInfo(LocalDate.of(2026, 10, 29), DayKind.RESMI_TATIL, "Cumhuriyet Bayramı", eventId = "2026-10-29", actualVisit = Visit.VAR),
    )

    /**
     * Elle girilen eski bayram kayıtları. Nisan 2024'ten beri resmi listeler olduğu için gerekmiyor;
     * eski sürümlerin bayram sayfasından girdiği kayıtlar göçte kaldırılır ([legacyHistoryIds]).
     */
    fun historyEvents(): List<HistoryEvent> = emptyList()

    val legacyHistoryIds = setOf("h-2024-ramazan", "h-2024-kurban", "h-2025-ramazan", "h-2025-kurban")

    fun build(): AppData {
        val months = rosters.map { (ym, line) ->
            val names = line.split(' ').filter { it.isNotBlank() }
            require(names.size == ym.lengthOfMonth()) { "$ym: ${names.size} isim, ${ym.lengthOfMonth()} gün" }
            val assignments = names.mapIndexedNotNull { i, name ->
                if (name == "-") null else Assignment(ym.atDay(i + 1), ids[name] ?: error("Bilinmeyen isim: $name"), locked = true)
            }
            val status = if (ym < YearMonth.of(2026, 10)) MonthStatus.KAPANDI else MonthStatus.YAYINLANDI
            val source = if (ym < YearMonth.of(2025, 4) && ym != YearMonth.of(2025, 1)) "Excel çalışma planından aktarıldı" else "Kağıt listeden aktarıldı"
            MonthPlan(
                ym.year, ym.monthValue, status,
                versions = listOf(RosterVersion(1, "${ym.atDay(1)}T00:00", source, assignments)),
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
