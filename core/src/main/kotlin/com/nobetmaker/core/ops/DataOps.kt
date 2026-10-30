package com.nobetmaker.core.ops

import com.nobetmaker.core.calendar.CalendarService
import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.model.Assignment
import com.nobetmaker.core.model.AssignmentSource
import com.nobetmaker.core.model.ChangeLogEntry
import com.nobetmaker.core.model.DayInfo
import com.nobetmaker.core.model.DayKind
import com.nobetmaker.core.model.DutyRequest
import com.nobetmaker.core.model.EventKind
import com.nobetmaker.core.model.HistoryEvent
import com.nobetmaker.core.model.InformalChange
import com.nobetmaker.core.model.MonthPlan
import com.nobetmaker.core.model.MonthStatus
import com.nobetmaker.core.model.Period
import com.nobetmaker.core.model.Person
import com.nobetmaker.core.model.PersonPalette
import com.nobetmaker.core.model.QuotaEntry
import com.nobetmaker.core.model.RosterVersion
import com.nobetmaker.core.model.Settings
import com.nobetmaker.core.model.Visit
import com.nobetmaker.core.rules.RosterIndex
import com.nobetmaker.core.seed.SeedData
import com.nobetmaker.core.solver.MonthProblem
import com.nobetmaker.core.solver.ProblemBuilder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.serialization.json.Json

object AppJson {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(data: AppData): String = json.encodeToString(AppData.serializer(), data)
    fun decode(text: String): AppData = migrate(json.decodeFromString(AppData.serializer(), text))

    /** Eski sürümlerin kaydettiği veriyi günceller. */
    fun migrate(data: AppData): AppData {
        var d = data
        if (d.schemaVersion < 2) {
            // 0.2.0: Ekim 2026'daki 29 Ekim vizitliydi; sıra hesabı bunu bilmeli.
            val oct29 = LocalDate.of(2026, 10, 29)
            if (d.dayOverrides.none { it.date == oct29 }) {
                d = d.copy(dayOverrides = d.dayOverrides + DayInfo(oct29, DayKind.RESMI_TATIL, "Cumhuriyet Bayramı", eventId = "2026-10-29", actualVisit = Visit.VAR))
            }
        }
        if (d.schemaVersion < 3) {
            // 0.3.0: belirgin renkler; başlangıç verisindeki tahmini katılış tarihleri boşaltılır (kullanıcı girecek);
            // önerisinden farklı girilmiş nöbet sayıları "elle sabit" sayılır.
            val seedJoin = mapOf(
                "betul" to LocalDate.of(2024, 11, 1), "sule" to LocalDate.of(2024, 11, 1),
                "oguz" to LocalDate.of(2025, 4, 1), "asli" to LocalDate.of(2026, 8, 1),
            )
            d = d.copy(
                persons = d.persons.map { p ->
                    p.copy(
                        color = PersonPalette.legacy[p.color.uppercase()] ?: p.color,
                        joinDate = if (p.joinDate != null && seedJoin[p.id] == p.joinDate) null else p.joinDate,
                    )
                },
                months = d.months.map { m -> m.copy(quotas = m.quotas.map { q -> if (q.final != q.suggested) q.copy(manual = true) else q }) },
            )
        }
        if (d.schemaVersion < 4) {
            // 0.4.0: kıdem eğrisi için asistanlık başlangıçları; Nisan 2024 – Ekim 2025 resmi listeleri (eski bayram
            // sayfası kayıtlarının yerini alır); sıra hafızası 6 ay. Kullanıcının girdiği hiçbir şeyin üstüne yazılmaz.
            val seed = SeedData.build()
            val seedPersons = seed.persons.associateBy { it.id }
            val importUntil = YearMonth.of(2025, 11)
            val newMonths = seed.months.filter { sm ->
                YearMonth.of(sm.year, sm.month) < importUntil && d.months.none { it.year == sm.year && it.month == sm.month }
            }
            val importedDates = newMonths.flatMap { m -> (1..YearMonth.of(m.year, m.month).lengthOfMonth()).map { YearMonth.of(m.year, m.month).atDay(it) } }.toSet()
            d = d.copy(
                persons = d.persons.map { p ->
                    val sp = seedPersons[p.id] ?: return@map p
                    p.copy(residencyStart = p.residencyStart ?: sp.residencyStart, joinDate = p.joinDate ?: sp.joinDate)
                },
                months = (d.months + newMonths).sortedWith(compareBy({ it.year }, { it.month })),
                informalChanges = d.informalChanges + seed.informalChanges.filter { c ->
                    c.date in importedDates && d.informalChanges.none { it.date == c.date }
                },
                historyEvents = d.historyEvents.filterNot { it.id in SeedData.legacyHistoryIds },
                settings = if (d.settings.rotationMonths == 3) d.settings.copy(rotationMonths = 6) else d.settings,
            )
        }
        if (d.schemaVersion < 5) {
            // 0.4.1: Fatma asistanlığa Eylül değil Ekim 2023'te başlamış; elle değiştirilmişse dokunulmaz.
            val old = LocalDate.of(2023, 9, 1)
            val new = LocalDate.of(2023, 10, 1)
            d = d.copy(
                persons = d.persons.map { p ->
                    if (p.id != "fatma") p
                    else p.copy(
                        residencyStart = if (p.residencyStart == old) new else p.residencyStart,
                        joinDate = if (p.joinDate == old) new else p.joinDate,
                    )
                },
            )
        }
        if (d.schemaVersion < 6) {
            // 0.4.2: önceden elle sabitlenen sayılar borç yazmıyordu; hoca kararı olarak işaretlenir (§5.4).
            d = d.copy(months = d.months.map { m -> m.copy(quotas = m.quotas.map { q -> if (q.manual) q.copy(decision = true) else q }) })
        }
        if (d.schemaVersion < 7) {
            // 0.4.3: 0.4.2 her elle sayıyı hoca kararı saydı. Denge döneminde öneriden fazla verilen sayı genelde
            // hocanın düşürdüğü kişiden artanı bizim birine vermemizdir: kendi sapmasını taşısın (§5.4).
            val start = YearMonth.from(d.settings.countBalanceStart)
            d = d.copy(
                months = d.months.map { m ->
                    if (YearMonth.of(m.year, m.month) < start) m
                    else m.copy(quotas = m.quotas.map { q -> if (q.manual && q.decision && q.final > q.suggested) q.copy(decision = false) else q })
                },
            )
        }
        if (d.schemaVersion < 8) {
            // 0.4.5: bayramlarda el yazısı kağıt esas (Excel'le çelişen günler) ve 29 Ekim 2025'i Oğuz tuttu. Yalnızca
            // o gün hâlâ eski kişi yazılıysa düzeltilir; elle düzeltilmişse dokunulmaz. Kapanmış ayda yeni sürüm açılır.
            for ((date, old, new) in SeedData.paperCorrections) {
                val plan = d.month(YearMonth.from(date)) ?: continue
                val cur = plan.current ?: continue
                if (cur.personOn(date) != old) continue
                val list = cur.assignments.map { if (it.date == date) it.copy(personId = new) else it }
                val note = "Kağıt listeye göre düzeltildi: ${date.dayOfMonth}. gün"
                val versions = if (plan.status == MonthStatus.TASLAK) plan.versions.dropLast(1) + cur.copy(assignments = list)
                else plan.versions + RosterVersion(cur.number + 1, cur.createdAt, note, list)
                d = d.copy(
                    months = d.months.map { if (it === plan) it.copy(versions = versions) else it },
                    informalChanges = d.informalChanges.filterNot { it.date == date && it.actualPersonId == new },
                )
            }
        }
        return if (d.schemaVersion == AppData.SCHEMA_VERSION) d else d.copy(schemaVersion = AppData.SCHEMA_VERSION)
    }
}

/** Veri üzerindeki bütün değişiklikler; hepsi saf fonksiyon (eski veri değişmez). */
object DataOps {

    fun now(): String = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString()
    fun newId(prefix: String): String = "$prefix-${UUID.randomUUID().toString().take(8)}"

    fun log(data: AppData, action: String, detail: String = ""): AppData =
        data.copy(changeLog = (data.changeLog + ChangeLogEntry(now(), action, detail)).takeLast(500))

    // ------------------------------------------------------------ Aylar

    private fun replaceMonth(data: AppData, plan: MonthPlan): AppData =
        data.copy(months = (data.months.filterNot { it.year == plan.year && it.month == plan.month } + plan)
            .sortedWith(compareBy({ it.year }, { it.month })))

    fun ensureMonth(data: AppData, ym: YearMonth): AppData {
        if (data.month(ym) != null) return data
        val quotas = ProblemBuilder.suggestedQuotas(data, ym)
        return replaceMonth(data, MonthPlan(ym.year, ym.monthValue, MonthStatus.TASLAK, quotas = quotas))
    }

    /** Önerilen kotaları yeniden hesaplar; kullanıcının değiştirmediği kotalar öneriyi izler. */
    private fun manualQuotas(plan: MonthPlan) = plan.quotas.filter { it.manual }.associate { it.personId to it.final }

    /** Yeniden hesaplanan sayılarda "hoca kararı" işaretleri korunur; yeni sabitlenen kişi varsayılan olarak hoca kararıdır. */
    private fun withDecisions(entries: List<QuotaEntry>, flags: Map<String, Boolean>): List<QuotaEntry> =
        entries.map { if (it.manual) it.copy(decision = flags[it.personId] ?: true) else it }

    private fun decisionFlags(plan: MonthPlan) = plan.quotas.filter { it.manual }.associate { it.personId to it.decision }

    /** Önerileri yeniden hesaplar; elle sabitlenenler kalır, kalan günler diğerlerine dağılır. */
    fun refreshQuotas(data: AppData, ym: YearMonth): AppData {
        val plan = data.month(ym) ?: return ensureMonth(data, ym)
        if (plan.status == MonthStatus.KAPANDI) return data
        val fresh = withDecisions(ProblemBuilder.suggestedQuotas(data, ym, manualQuotas(plan)), decisionFlags(plan))
        return if (fresh == plan.quotas) data else replaceMonth(data, plan.copy(quotas = fresh))
    }

    fun resetQuotas(data: AppData, ym: YearMonth): AppData {
        val plan = data.month(ym) ?: return ensureMonth(data, ym)
        return replaceMonth(data, plan.copy(quotas = ProblemBuilder.suggestedQuotas(data, ym)))
    }

    /**
     * Kişinin sayısını elle sabitler; diğerlerinin önerisi kalan günlere göre yeniden dağılır.
     * @param decision ilk sabitlemede hoca kararı mı; null ise önceki işaret (yoksa hoca kararı) kalır.
     */
    fun setQuota(data: AppData, ym: YearMonth, personId: String, value: Int, decision: Boolean? = null): AppData {
        val plan = data.month(ym) ?: return data
        val fixed = manualQuotas(plan) + (personId to value.coerceAtLeast(0))
        val flags = decisionFlags(plan) + listOfNotNull(decision?.let { personId to it })
        val quotas = withDecisions(ProblemBuilder.suggestedQuotas(data, ym, fixed), flags)
        val name = data.person(personId)?.name ?: personId
        return log(replaceMonth(data, plan.copy(quotas = quotas)), "Nöbet sayısı", "$ym: $name ${value.coerceAtLeast(0)}")
    }

    /**
     * Sabitlenen sayının hoca kararı olup olmadığı. Hoca kararıysa kişiye borç ya da alacak yazılmaz; değilse
     * (bizim düzeltmemiz) fark sonraki aylarda o kişiye geri yansır (§5.4).
     */
    fun setQuotaDecision(data: AppData, ym: YearMonth, personId: String, decision: Boolean): AppData {
        val plan = data.month(ym) ?: return data
        val quotas = plan.quotas.map { if (it.personId == personId && it.manual) it.copy(decision = decision) else it }
        val name = data.person(personId)?.name ?: personId
        return log(replaceMonth(data, plan.copy(quotas = quotas)), "Nöbet sayısı", "$ym: $name " + if (decision) "hoca kararı" else "elle düzeltme")
    }

    /** Elle sabitlemeyi kaldırır; kişi yeniden öneriye göre dağıtıma katılır. */
    fun releaseQuota(data: AppData, ym: YearMonth, personId: String): AppData {
        val plan = data.month(ym) ?: return data
        val quotas = withDecisions(ProblemBuilder.suggestedQuotas(data, ym, manualQuotas(plan) - personId), decisionFlags(plan))
        return replaceMonth(data, plan.copy(quotas = quotas))
    }

    fun quotaMap(plan: MonthPlan?): Map<String, Int>? = plan?.quotas?.takeIf { it.isNotEmpty() }?.associate { it.personId to it.final }

    fun currentAssignments(data: AppData, ym: YearMonth): List<Assignment> = data.month(ym)?.current?.assignments.orEmpty()

    /**
     * Ayın resmi listesini değiştirir. Taslakta mevcut sürüm güncellenir; yayınlanmış ayda yeni sürüm açılır.
     */
    fun setAssignments(data: AppData, ym: YearMonth, assignments: List<Assignment>, note: String): AppData {
        val base = if (data.month(ym) == null) ensureMonth(data, ym) else data
        val plan = base.month(ym)!!
        require(plan.status != MonthStatus.KAPANDI) { "Kapanmış ay değiştirilemez" }
        val sorted = assignments.sortedBy { it.date }
        val versions = when {
            plan.versions.isEmpty() -> listOf(RosterVersion(1, now(), note, sorted))
            plan.status == MonthStatus.TASLAK ->
                plan.versions.dropLast(1) + plan.current!!.copy(createdAt = now(), note = note, assignments = sorted)
            else -> plan.versions + RosterVersion(plan.current!!.number + 1, now(), note, sorted)
        }
        return log(replaceMonth(base, plan.copy(versions = versions)), "Resmi liste", "$ym: $note")
    }

    /** Çözücü sonucunu resmi listeye yazar; kilitli günlerin kilidi korunur. */
    fun applySolution(data: AppData, pr: MonthProblem, assignment: IntArray, note: String = "Otomatik oluşturuldu"): AppData {
        val list = assignment.indices.map { i ->
            Assignment(
                date = pr.days[i],
                personId = pr.persons[assignment[i]].id,
                locked = pr.locked[i] != -1,
                source = if (pr.locked[i] != -1) AssignmentSource.ELLE else AssignmentSource.OTOMATIK,
            )
        }
        return setAssignments(data, pr.month, list, note)
    }

    fun assign(data: AppData, ym: YearMonth, date: LocalDate, personId: String?): AppData {
        val current = currentAssignments(data, ym).filterNot { it.date == date }
        val list = if (personId == null) current else current + Assignment(date, personId, locked = true, source = AssignmentSource.ELLE)
        val name = personId?.let { data.person(it)?.name } ?: "boş"
        return setAssignments(data, ym, list, "${date.dayOfMonth}. gün elle: $name")
    }

    fun swap(data: AppData, ym: YearMonth, d1: LocalDate, d2: LocalDate): AppData {
        val current = currentAssignments(data, ym)
        val a = current.firstOrNull { it.date == d1 } ?: return data
        val b = current.firstOrNull { it.date == d2 } ?: return data
        val list = current.map {
            when (it.date) {
                d1 -> it.copy(personId = b.personId, source = AssignmentSource.ELLE)
                d2 -> it.copy(personId = a.personId, source = AssignmentSource.ELLE)
                else -> it
            }
        }
        return setAssignments(data, ym, list, "${d1.dayOfMonth} ↔ ${d2.dayOfMonth} takas")
    }

    /**
     * Herhangi bir günün resmi kaydını elle düzeltir: geçmiş ve kapanmış aylar dahil, kurallara bakılmaz, ayın
     * durumu değişmez. Taslakta geçerli sürüm güncellenir; diğerlerinde yeni sürüm açılır, eski hali sürümlerde
     * kalır. [personId] null ise o günü uzman tutmuş sayılır (asistan yok).
     */
    fun correctDay(data: AppData, date: LocalDate, personId: String?): AppData {
        val ym = YearMonth.from(date)
        val base = if (data.month(ym) != null) data
        else replaceMonth(data, MonthPlan(ym.year, ym.monthValue, if (ym < YearMonth.now()) MonthStatus.KAPANDI else MonthStatus.TASLAK))
        val plan = base.month(ym)!!
        val kept = plan.current?.assignments.orEmpty().filterNot { it.date == date }
        val list = (if (personId == null) kept else kept + Assignment(date, personId, locked = true, source = AssignmentSource.ELLE)).sortedBy { it.date }
        val name = personId?.let { data.person(it)?.name ?: it } ?: "uzman"
        val note = "${date.dayOfMonth}. gün elle düzeltildi: $name"
        val versions = when {
            plan.versions.isEmpty() -> listOf(RosterVersion(1, now(), note, list))
            plan.status == MonthStatus.TASLAK -> plan.versions.dropLast(1) + plan.current!!.copy(createdAt = now(), note = note, assignments = list)
            else -> plan.versions + RosterVersion(plan.current!!.number + 1, now(), note, list)
        }
        // Uzman yazıldıysa ya da gerçekte tutan artık resmi kişiyse o günün takas kaydı anlamını yitirir.
        val informal = base.informalChanges.filterNot { it.date == date && (personId == null || it.actualPersonId == personId) }
        return log(replaceMonth(base.copy(informalChanges = informal), plan.copy(versions = versions)), "Elle düzeltme", "$date: $name")
    }

    /**
     * Günü gerçekte kimin tuttuğunu kaydeder (takas; hesap resmi listeden yapılır). Kapanmış aylarda da çalışır.
     * Resmi kişi seçilirse gayriresmi kayıt kalkar. Resmi kaydı olmayan (uzman) günde değişiklik yapmaz.
     */
    fun recordActual(data: AppData, date: LocalDate, actualId: String): AppData {
        val official = RosterIndex(data).official[date] ?: return data
        val cleared = data.copy(informalChanges = data.informalChanges.filterNot { it.date == date })
        return if (actualId == official) log(cleared, "Gayriresmi değişim geri alındı", date.toString())
        else addInformal(cleared, date, official, actualId, null, null)
    }

    /**
     * Günün türünü elle değiştirir (bayram, arefe, idari izin, resmi tatil, yılbaşı ya da normal). Bayram türleri
     * yakınındaki bayrama bağlanır; takvimdeki türe dönülünce elle kayıt kalkar, vizit işareti korunur.
     */
    fun setDayKind(data: AppData, date: LocalDate, kind: DayKind): AppData {
        val cal = CalendarService(data.dayOverrides, data.settings)
        val current = cal.day(date)
        val builtIn = cal.builtInDay(date)
        fun restored() = builtIn.copy(plannedVisit = current.plannedVisit, actualVisit = current.actualVisit, extraPoints = current.extraPoints, note = current.note)
        val updated = if (kind == DayKind.NORMAL) {
            // Bayrama bitişik hafta sonu kendiliğinden bayram sayılır; "normal" denince olaydan açıkça çıkarılır.
            val plain = current.copy(kind = kind, eventId = null, name = null)
            val linked = CalendarService(data.dayOverrides.filterNot { it.date == date } + plain, data.settings).eventId(date) != null
            when {
                linked -> plain.copy(eventId = "")
                builtIn.kind == DayKind.NORMAL -> restored()
                else -> plain
            }
        } else if (kind == builtIn.kind) {
            restored()
        } else {
            fun bayram(id: String?) = id != null && cal.eventKind(id) == EventKind.BAYRAM
            val near = (1..10L).flatMap { listOf(date.minusDays(it), date.plusDays(it)) }.map { cal.day(it) }.firstOrNull { bayram(it.eventId) }
            when (kind) {
                DayKind.YILBASI -> current.copy(kind = kind, eventId = "${date.year}-yilbasi", name = current.name ?: kind.label)
                DayKind.RESMI_TATIL -> current.copy(kind = kind, eventId = current.eventId?.takeIf { it.isNotEmpty() && !bayram(it) } ?: date.toString(), name = current.name ?: kind.label)
                else -> {
                    // Bayram, arefe, idari izin: yakındaki bayrama bağlanır.
                    val link = if (bayram(current.eventId)) current else near
                    current.copy(kind = kind, eventId = link?.eventId ?: "elle-$date", name = current.name ?: link?.name ?: kind.label)
                }
            }
        }
        val rest = data.dayOverrides.filterNot { it.date == date }
        val list = if (updated == builtIn) rest else rest + updated
        return log(data.copy(dayOverrides = list.sortedBy { it.date }), "Gün türü", "$date: ${kind.label}")
    }

    fun setLock(data: AppData, ym: YearMonth, date: LocalDate, locked: Boolean): AppData {
        val plan = data.month(ym) ?: return data
        val cur = plan.current ?: return data
        val updated = cur.copy(assignments = cur.assignments.map { if (it.date == date) it.copy(locked = locked) else it })
        return replaceMonth(data, plan.copy(versions = plan.versions.dropLast(1) + updated))
    }

    /**
     * [date] dahil o güne kadarki günleri kilitler. Hocalar sayı söylediğinde ya da ay ortasında bir şey
     * değiştiğinde geçmiş günler korunur, yalnızca kalan günler yeniden üretilir.
     */
    fun lockThrough(data: AppData, ym: YearMonth, date: LocalDate): AppData {
        val plan = data.month(ym) ?: return data
        val cur = plan.current ?: return data
        val updated = cur.copy(assignments = cur.assignments.map { if (!it.date.isAfter(date)) it.copy(locked = true) else it })
        return log(replaceMonth(data, plan.copy(versions = plan.versions.dropLast(1) + updated)), "Geçmiş günler kilitlendi", "$ym: ${date.dayOfMonth}. güne kadar")
    }

    fun clearUnlocked(data: AppData, ym: YearMonth): AppData {
        val list = currentAssignments(data, ym).filter { it.locked }
        return setAssignments(data, ym, list, "Kilitsiz günler temizlendi")
    }

    /**
     * Kesinleşmiş listeyi taslağa geri alır (ör. yanlışlıkla kesinleştirildiyse): kesinleşmiş hali sürümlerde kalır,
     * taslak onun kopyasıyla başlar; liste baştan oluşturulabilir, sayılar değişebilir. Defter ve istatistik her
     * zaman geçerli resmi listeden hesaplandığı için değişiklik kendiliğinden yansır.
     */
    fun backToDraft(data: AppData, ym: YearMonth): AppData {
        val plan = data.month(ym) ?: return data
        if (plan.status != MonthStatus.YAYINLANDI) return data
        val cur = plan.current
        val versions = if (cur == null) plan.versions else plan.versions + cur.copy(number = cur.number + 1, createdAt = now(), note = "Taslağa geri alındı")
        return log(replaceMonth(data, plan.copy(status = MonthStatus.TASLAK, versions = versions)), "Ay durumu", "$ym: taslağa geri alındı")
    }

    fun setStatus(data: AppData, ym: YearMonth, status: MonthStatus): AppData {
        val plan = data.month(ym) ?: return data
        return log(replaceMonth(data, plan.copy(status = status)), "Ay durumu", "$ym: ${status.label}")
    }

    // ------------------------------------------------------------ Gayriresmi değişimler

    fun addInformal(data: AppData, date: LocalDate, officialId: String, actualId: String, groupId: String?, note: String?): AppData {
        val change = InformalChange(newId("g"), date, officialId, actualId, groupId, now(), note)
        return log(data.copy(informalChanges = data.informalChanges + change), "Gayriresmi değişim", "$date: $actualId")
    }

    fun removeInformal(data: AppData, id: String): AppData =
        log(data.copy(informalChanges = data.informalChanges.filterNot { it.id == id }), "Gayriresmi değişim silindi", id)

    /** O günün bütün gayriresmi değişimlerini kaldırır (resmi kişiye döner). */
    fun revertInformal(data: AppData, date: LocalDate): AppData =
        log(data.copy(informalChanges = data.informalChanges.filterNot { it.date == date }), "Gayriresmi değişim geri alındı", date.toString())

    // ------------------------------------------------------------ Kişiler, dönemler, istekler

    fun upsertPerson(data: AppData, person: Person): AppData {
        val list = data.persons.filterNot { it.id == person.id } + person
        return log(data.copy(persons = list.sortedBy { it.seniority }), "Kişi", person.name)
    }

    /** Kıdem sırasını verilen id sırasına göre yeniden numaralandırır. */
    fun reorderSeniority(data: AppData, orderedIds: List<String>): AppData {
        val persons = data.persons.map { p -> p.copy(seniority = orderedIds.indexOf(p.id).let { if (it < 0) 999 else it + 1 }) }
        return log(data.copy(persons = persons.sortedBy { it.seniority }), "Kıdem sırası")
    }

    fun deletePerson(data: AppData, id: String): AppData {
        val used = data.months.any { m -> m.versions.any { v -> v.assignments.any { it.personId == id } } }
        require(!used) { "Bu kişinin geçmiş nöbetleri var; silmek yerine ayrılış tarihi girin" }
        return log(data.copy(persons = data.persons.filterNot { it.id == id }), "Kişi silindi", id)
    }

    fun upsertPeriod(data: AppData, period: Period): AppData =
        log(data.copy(periods = (data.periods.filterNot { it.id == period.id } + period).sortedBy { it.start }), "Dönem", period.type.label)

    fun deletePeriod(data: AppData, id: String): AppData = log(data.copy(periods = data.periods.filterNot { it.id == id }), "Dönem silindi", id)

    fun upsertRequest(data: AppData, request: DutyRequest): AppData =
        log(data.copy(requests = data.requests.filterNot { it.id == request.id } + request), "İstek", request.type.label)

    fun deleteRequest(data: AppData, id: String): AppData = log(data.copy(requests = data.requests.filterNot { it.id == id }), "İstek silindi", id)

    // ------------------------------------------------------------ Takvim, geçmiş, ayarlar

    fun upsertDay(data: AppData, day: DayInfo): AppData =
        log(data.copy(dayOverrides = (data.dayOverrides.filterNot { it.date == day.date } + day).sortedBy { it.date }), "Gün", day.date.toString())

    fun resetDay(data: AppData, date: LocalDate): AppData =
        log(data.copy(dayOverrides = data.dayOverrides.filterNot { it.date == date }), "Gün sıfırlandı", date.toString())

    fun upsertHistory(data: AppData, event: HistoryEvent): AppData =
        log(data.copy(historyEvents = data.historyEvents.filterNot { it.id == event.id } + event), "Geçmiş özel gün", event.name)

    fun deleteHistory(data: AppData, id: String): AppData =
        log(data.copy(historyEvents = data.historyEvents.filterNot { it.id == id }), "Geçmiş özel gün silindi", id)

    fun updateSettings(data: AppData, settings: Settings): AppData = log(data.copy(settings = settings), "Ayarlar")
}
