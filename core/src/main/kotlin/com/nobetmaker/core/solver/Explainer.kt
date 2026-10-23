package com.nobetmaker.core.solver

import com.nobetmaker.core.calendar.Category
import com.nobetmaker.core.model.Person

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
        return pr.persons.indices.map { q ->
            val hard = pr.hardReasons[q][day].toMutableList()
            for (k in 1 until pr.minGap) {
                for (y in intArrayOf(x - k, x + k)) {
                    if (y in 0 until pr.extSize && has[q][y]) hard += "${shortDate(pr.dateOfExt(y))} nöbetine çok yakın"
                }
            }
            val lockedTo = pr.locked[day]
            if (lockedTo != -1 && lockedTo != q) hard += "Gün ${pr.persons[lockedTo].name} için kilitli"

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
            val status = when {
                q == current -> CandidateStatus.SELECTED
                hard.isNotEmpty() -> CandidateStatus.HARD
                soft.isNotEmpty() -> CandidateStatus.SOFT
                else -> CandidateStatus.OK
            }
            CandidateInfo(pr.persons[q], q, status, hard.distinct(), soft, costWith - costWithout)
        }.sortedWith(compareBy<CandidateInfo> { it.status.ordinal }.thenBy { it.delta })
    }

    fun scoreCard(assignment: IntArray): ScoreCard {
        val penalties = obj.penalties(assignment)
        val unmet = penalties.filter { it.kind == PenaltyKind.REQUEST }
            .groupBy { it.requestId ?: it.text }
            .map { (_, list) -> list.first().text }
        val memory = penalties.filter { it.kind == PenaltyKind.SHORT_MEMORY || it.kind == PenaltyKind.LONG_MEMORY }
            .sortedByDescending { it.amount }
            .take(3)
            .map { it.text }
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
            memoryNotes = memory,
            perPerson = lines,
        )
    }
}
