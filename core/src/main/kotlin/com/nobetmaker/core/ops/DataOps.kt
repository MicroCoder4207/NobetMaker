package com.nobetmaker.core.ops

import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.model.Assignment
import com.nobetmaker.core.model.AssignmentSource
import com.nobetmaker.core.model.ChangeLogEntry
import com.nobetmaker.core.model.DayInfo
import com.nobetmaker.core.model.DayKind
import com.nobetmaker.core.model.DutyRequest
import com.nobetmaker.core.model.HistoryEvent
import com.nobetmaker.core.model.InformalChange
import com.nobetmaker.core.model.MonthPlan
import com.nobetmaker.core.model.MonthStatus
import com.nobetmaker.core.model.Period
import com.nobetmaker.core.model.Person
import com.nobetmaker.core.model.QuotaEntry
import com.nobetmaker.core.model.RosterVersion
import com.nobetmaker.core.model.Settings
import com.nobetmaker.core.model.Visit
import com.nobetmaker.core.solver.MonthProblem
import com.nobetmaker.core.solver.ProblemBuilder
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID

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
    fun refreshQuotas(data: AppData, ym: YearMonth): AppData {
        val plan = data.month(ym) ?: return ensureMonth(data, ym)
        if (plan.status == MonthStatus.KAPANDI) return data
        val fresh = ProblemBuilder.suggestedQuotas(data, ym)
        val merged = fresh.map { f ->
            val old = plan.quotas.firstOrNull { it.personId == f.personId }
            if (old != null && old.final != old.suggested) f.copy(final = old.final) else f
        }
        return replaceMonth(data, plan.copy(quotas = merged))
    }

    fun resetQuotas(data: AppData, ym: YearMonth): AppData {
        val plan = data.month(ym) ?: return ensureMonth(data, ym)
        return replaceMonth(data, plan.copy(quotas = ProblemBuilder.suggestedQuotas(data, ym)))
    }

    fun setQuota(data: AppData, ym: YearMonth, personId: String, value: Int): AppData {
        val plan = data.month(ym) ?: return data
        val exists = plan.quotas.any { it.personId == personId }
        val quotas = if (exists) plan.quotas.map { if (it.personId == personId) it.copy(final = value.coerceAtLeast(0)) else it }
        else plan.quotas + QuotaEntry(personId, 0, value.coerceAtLeast(0))
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
    fun applySolution(data: AppData, pr: MonthProblem, assignment: IntArray, note: String = "Otomatik üretildi"): AppData {
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

    fun setLock(data: AppData, ym: YearMonth, date: LocalDate, locked: Boolean): AppData {
        val plan = data.month(ym) ?: return data
        val cur = plan.current ?: return data
        val updated = cur.copy(assignments = cur.assignments.map { if (it.date == date) it.copy(locked = locked) else it })
        return replaceMonth(data, plan.copy(versions = plan.versions.dropLast(1) + updated))
    }

    fun clearUnlocked(data: AppData, ym: YearMonth): AppData {
        val list = currentAssignments(data, ym).filter { it.locked }
        return setAssignments(data, ym, list, "Kilitsiz günler temizlendi")
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
