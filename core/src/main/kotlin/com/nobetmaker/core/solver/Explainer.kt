package com.nobetmaker.core.solver

import com.nobetmaker.core.calendar.Category
import com.nobetmaker.core.calendar.RotationGroup
import com.nobetmaker.core.model.Person
import kotlin.math.abs

enum class CandidateStatus { SELECTED, OK, SOFT, HARD }

class CandidateInfo(
    val person: Person,
    val personIndex: Int,
    val status: CandidateStatus,
    val hardReasons: List<String>,
    val softReasons: List<String>,
    /** Bu kişi o güne yazılırsa kendi cezasındaki değişim. */
    val delta: Double,
)

class PersonLine(
    val person: Person,
    val quota: Int,
    val count: Int,
    val hours: Int,
    val byCategory: Map<Category, Int>,
)

class ScoreCard(
    val total: Double,
    val requestsTotal: Int,
    val requestsUnmet: List<String>,
    val longHolidayHits: List<String>,
    val tightGaps: List<String>,
    val conflicts: List<String>,
    /** Ayda 2. Cuma/Cumartesi/Pazar. */
    val weekendRepeats: List<String>,
    /** Bu ayın sırası, ör. "Vizit sırası: Kenan, Fatma, Aslı, Oğuz". */
    val rotationSummary: List<String>,
    /** Sıradan sapmalar, grup başına bir satır (ör. "Pazartesi sırası esnedi: Yaren yerine Şule"). */
    val rotationMisses: List<String>,
    /** Ard arda iki günde aynı ikilinin yeniden dizilmesi. */
    val sequenceRepeats: List<String>,
    val memoryNotes: List<String>,
    val perPerson: List<PersonLine>,
)

/** "Neden?" açıklamaları ve skor kartı (§8.4, §8.5). */
class Explainer(private val pr: MonthProblem) {

    private val obj = Objective(pr)

    fun candidates(assignment: IntArray, day: Int): List<CandidateInfo> {
        val has = obj.buildHas(assignment)
        val x = pr.ext(day)
        val current = assignment[day]
        // Bu güne dokunmayan komşu ikililer; aday bunlardan birini tekrarlarsa sıra tekrarı olur.
        val P = pr.personCount
        val pairs = HashSet<Int>()
        for (k in 0 until pr.dayCount - 1) {
            if (k == day || k + 1 == day) continue
            val u = assignment[k]
            val v = assignment[k + 1]
            if (u >= 0 && v >= 0) pairs += u * P + v
        }
        val before = assignment.getOrNull(day - 1) ?: -1
        val after = assignment.getOrNull(day + 1) ?: -1
        return pr.persons.indices.map { q ->
            val hard = pr.hardReasons[q][day].toMutableList()
            for (k in 1 until pr.minGap) {
                for (y in intArrayOf(x - k, x + k)) {
                    if (y in 0 until pr.extSize && has[q][y]) hard += "${shortDate(pr.dateOfExt(y))} nöbetine çok yakın"
                }
            }
            val with = has[q].copyOf().also { it[x] = true }
            val without = has[q].copyOf().also { it[x] = false }
            val withItems = ArrayList<Penalty>()
            val withoutItems = HashMap<String, Double>()
            val costWith = obj.personCost(q, with) { withItems += it }
            val costWithout = obj.personCost(q, without) { withoutItems[it.key] = (withoutItems[it.key] ?: 0.0) + it.amount }
            val soft = withItems
                .filter { it.kind != PenaltyKind.CONFLICT && it.amount - (withoutItems[it.key] ?: 0.0) > 0.01 }
                .map { it.text }
                .distinct()
                .toMutableList()
            var seq = 0.0
            if (pr.weights.sequenceRepeat > 0.0) {
                if (before >= 0 && before * P + q in pairs) {
                    seq += pr.weights.sequenceRepeat
                    soft += "Sıra tekrarı: ${pr.persons[before].name} → ${pr.persons[q].name} bu ay zaten var"
                }
                if (after >= 0 && q * P + after in pairs) {
                    seq += pr.weights.sequenceRepeat
                    soft += "Sıra tekrarı: ${pr.persons[q].name} → ${pr.persons[after].name} bu ay zaten var"
                }
            }
            val status = when {
                q == current -> CandidateStatus.SELECTED
                hard.isNotEmpty() -> CandidateStatus.HARD
                soft.isNotEmpty() -> CandidateStatus.SOFT
                else -> CandidateStatus.OK
            }
            CandidateInfo(pr.persons[q], q, status, hard.distinct(), soft, costWith - costWithout + seq)
        }.sortedWith(compareBy<CandidateInfo> { it.status.ordinal }.thenBy { it.delta })
    }

    fun scoreCard(assignment: IntArray): ScoreCard {
        val penalties = obj.penalties(assignment)
        val unmet = penalties.filter { it.kind == PenaltyKind.REQUEST }
            .groupBy { it.requestId ?: it.text }
            .map { (_, list) -> list.first().text }
        val memory = penalties.filter { it.kind == PenaltyKind.LONG_MEMORY }
            .sortedByDescending { it.amount }
            .take(3)
            .map { it.text }
        val summary = RotationGroup.entries.mapNotNull { g ->
            val queue = pr.rotationQueue.getOrNull(g.ordinal).orEmpty()
            if (queue.isEmpty()) null else "${g.label} sırası: ${queue.joinToString(", ") { pr.persons[it].name }}"
        }
        val misses = RotationGroup.entries.mapNotNull { g ->
            val diff = pr.persons.indices.associateWith { p ->
                assignment.indices.count { assignment[it] == p && pr.rotationIndex[it] == g.ordinal } - pr.rotationTarget[p][g.ordinal]
            }
            fun names(sign: Int) = diff.filter { it.value * sign > 0 }.entries.joinToString(", ") { (p, d) ->
                pr.persons[p].name + if (abs(d) > 1) " (${abs(d)})" else ""
            }
            val over = names(1)
            val under = names(-1)
            when {
                over.isEmpty() && under.isEmpty() -> null
                under.isEmpty() -> "${g.label} sırası esnedi: $over fazladan yazıldı"
                over.isEmpty() -> "${g.label} sırası esnedi: $under yazılamadı"
                else -> "${g.label} sırası esnedi: $under yerine $over"
            }
        }
        val lines = pr.persons.indices.map { p ->
            val days = assignment.indices.filter { assignment[it] == p }
            PersonLine(
                person = pr.persons[p],
                quota = pr.quotas[p],
                count = days.size,
                hours = days.sumOf { pr.hours[it] },
                byCategory = days.groupingBy { pr.category[it] }.eachCount(),
            )
        }
        return ScoreCard(
            total = penalties.sumOf { it.amount },
            requestsTotal = pr.softRequestIds.size,
            requestsUnmet = unmet,
            longHolidayHits = penalties.filter { it.kind == PenaltyKind.LONG_HOLIDAY }.map { it.text },
            tightGaps = penalties.filter { it.kind == PenaltyKind.GAP && it.text.endsWith("3 gün") }.map { it.text },
            conflicts = penalties.filter { it.kind == PenaltyKind.CONFLICT }.map { it.text },
            weekendRepeats = penalties.filter { it.kind == PenaltyKind.WEEKEND_REPEAT }.map { it.text },
            rotationSummary = summary,
            rotationMisses = misses,
            sequenceRepeats = penalties.filter { it.kind == PenaltyKind.SEQUENCE }.map { it.text },
            memoryNotes = memory,
            perPerson = lines,
        )
    }
}
