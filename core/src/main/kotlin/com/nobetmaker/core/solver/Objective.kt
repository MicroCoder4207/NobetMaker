package com.nobetmaker.core.solver

import com.nobetmaker.core.calendar.Category
import java.util.Locale
import kotlin.math.abs

enum class PenaltyKind { REQUEST, LONG_HOLIDAY, GAP, SAME_WEEKDAY, SAME_BAYRAM, SHORT_MEMORY, LONG_MEMORY, CONFLICT }

class Penalty(
    val kind: PenaltyKind,
    val person: Int,
    /** İlgili gün (ay içi dizin) ya da -1. */
    val day: Int,
    val amount: Double,
    val text: String,
    val requestId: String? = null,
) {
    /** Aynı cezanın iki durumda karşılaştırılabilmesi için anahtar. */
    val key: String get() = "$kind|$person|$day|${if (kind == PenaltyKind.SHORT_MEMORY || kind == PenaltyKind.LONG_MEMORY) text.substringBefore(':') else text}"
}

private fun fmt(v: Double) = String.format(Locale.ROOT, "%.1f", v)

/**
 * Toplam ceza kişi başı maliyetlerin toplamıdır; her kişinin maliyeti yalnızca kendi nöbet günlerine bağlıdır.
 * Bu sayede iki kişinin günlerini değiştiren bir hamle yalnızca o iki kişinin maliyetini değiştirir.
 */
class Objective(private val pr: MonthProblem) {

    private val wantsByPerson = Array(pr.personCount) { p -> pr.wants.filter { it.p == p } }
    private val clustersByPerson = Array(pr.personCount) { p -> pr.clusters.filter { it.p == p } }
    private val weeklyByPerson = Array(pr.personCount) { p -> pr.weekly.filter { it.p == p } }
    private val dow = IntArray(pr.dayCount) { pr.days[it].dayOfWeek.value - 1 }
    private val shortCats = Category.SHORT_MEMORY
    private val longCats = Category.LONG_MEMORY

    fun personCost(p: Int, has: BooleanArray, collect: ((Penalty) -> Unit)? = null): Double {
        val w = pr.weights
        val name = pr.persons[p].name
        var cost = 0.0
        val D = pr.dayCount
        val pad = MonthProblem.PAD

        // İstenmeyen günler.
        for (i in 0 until D) {
            if (!has[i + pad]) continue
            val sa = pr.softAvoid[p][i]
            if (sa > 0) {
                cost += sa
                collect?.invoke(Penalty(PenaltyKind.REQUEST, p, i, sa, pr.softAvoidLabel[p][i] ?: "İstek", pr.softAvoidRequest[p][i]))
            }
        }
        // İstenen günler.
        for (want in wantsByPerson[p]) {
            if (!has[want.day + pad]) {
                cost += want.weight
                collect?.invoke(Penalty(PenaltyKind.REQUEST, p, want.day, want.weight, want.label, want.requestId))
            }
        }
        // Yoğunlaştırma.
        for (c in clustersByPerson[p]) {
            for (i in 0 until D) {
                if (has[i + pad] && (i < c.from || i > c.to)) {
                    cost += c.weight / 4
                    collect?.invoke(Penalty(PenaltyKind.REQUEST, p, i, c.weight / 4, c.label, c.requestId))
                }
            }
        }
        // Haftalık üst sınır.
        for (wk in weeklyByPerson[p]) {
            var i = wk.from
            while (i <= wk.to) {
                val key = pr.weekKey[i]
                var count = 0
                var j = i
                while (j <= wk.to && pr.weekKey[j] == key) {
                    if (has[j + pad]) count++
                    j++
                }
                if (count > wk.max) {
                    val v = (count - wk.max) * wk.weight
                    cost += v
                    collect?.invoke(Penalty(PenaltyKind.REQUEST, p, i, v, wk.label, wk.requestId))
                }
                i = j
            }
        }

        // Aralıklar ve uzun tatil koruması.
        var prev = -1
        for (x in 0 until pr.extSize) {
            if (!has[x]) continue
            if (prev >= 0 && (pr.isMonthExt(x) || pr.isMonthExt(prev))) {
                val gap = x - prev
                val inCluster = clustersByPerson[p].any { c -> prev - pad >= c.from && x - pad <= c.to }
                val pairText = "${shortDate(pr.dateOfExt(prev))} ve ${shortDate(pr.dateOfExt(x))}"
                when {
                    gap < pr.minGap -> {
                        cost += 1000.0
                        collect?.invoke(Penalty(PenaltyKind.CONFLICT, p, x - pad, 1000.0, "$name: $pairText arasında en az ${pr.minGap} gün yok"))
                    }
                    gap == 3 && !inCluster && w.gap3 > 0 -> {
                        cost += w.gap3
                        collect?.invoke(Penalty(PenaltyKind.GAP, p, x - pad, w.gap3, "$name: $pairText arası 3 gün"))
                    }
                    gap == 4 && !inCluster && w.gap4 > 0 -> {
                        cost += w.gap4
                        collect?.invoke(Penalty(PenaltyKind.GAP, p, x - pad, w.gap4, "$name: $pairText arası 4 gün"))
                    }
                }
            }
            prev = x
            for (y in pr.longBlocks[x]) {
                if (has[y] && (pr.isMonthExt(x) || pr.isMonthExt(y))) {
                    cost += w.longHolidayProtection
                    collect?.invoke(
                        Penalty(
                            PenaltyKind.LONG_HOLIDAY, p, y - pad, w.longHolidayProtection,
                            "$name: ${shortDate(pr.dateOfExt(x))} nöbetinden sonraki uzun tatil bozuluyor (${shortDate(pr.dateOfExt(y))})",
                        ),
                    )
                }
            }
        }

        // Haftanın aynı günü, aynı bayram, defter.
        val weekday = IntArray(7)
        val shortPts = DoubleArray(shortCats.size)
        val longDays = IntArray(longCats.size)
        var bayramKeys: MutableMap<String, Int>? = null
        for (i in 0 until D) {
            if (!has[i + pad]) continue
            weekday[dow[i]]++
            val si = pr.shortIndex[i]
            if (si >= 0) shortPts[si] += pr.points[i]
            val li = pr.longIndex[i]
            if (li >= 0) longDays[li]++
            val bk = pr.bayramKey[i]
            if (bk != null) {
                if (bayramKeys == null) bayramKeys = HashMap()
                bayramKeys[bk] = (bayramKeys[bk] ?: 0) + 1
            }
        }
        for (d in 0 until 7) {
            if (weekday[d] > 2) {
                val v = (weekday[d] - 2) * w.sameWeekday
                cost += v
                collect?.invoke(Penalty(PenaltyKind.SAME_WEEKDAY, p, -1, v, "$name: aynı gün ${weekday[d]} kez"))
            }
        }
        bayramKeys?.forEach { (_, count) ->
            if (count > 1) {
                val v = (count - 1) * w.sameBayram
                cost += v
                collect?.invoke(Penalty(PenaltyKind.SAME_BAYRAM, p, -1, v, "$name: aynı bayramda $count gün"))
            }
        }
        for (ci in shortCats.indices) {
            val dev = pr.shortBase[p][ci] + shortPts[ci] / pr.shortAvg[ci]
            val v = w.shortMemory * dev * dev
            cost += v
            if (collect != null && abs(dev) >= 0.5) {
                val side = if (dev > 0) "önde" else "geride"
                collect(Penalty(PenaltyKind.SHORT_MEMORY, p, -1, v, "${shortCats[ci].label} dengesi: $name ${fmt(abs(dev))} gün $side"))
            }
        }
        for (gi in longCats.indices) {
            val dev = pr.longBase[p][gi] + longDays[gi]
            val v = w.longMemory * dev * dev
            cost += v
            if (collect != null && abs(dev) >= 0.5) {
                val side = if (dev > 0) "önde" else "geride"
                collect(Penalty(PenaltyKind.LONG_MEMORY, p, -1, v, "${longCats[gi].label} dengesi: $name ${fmt(abs(dev))} gün $side"))
            }
        }
        return cost
    }

    fun buildHas(assignment: IntArray): Array<BooleanArray> {
        val has = Array(pr.personCount) { BooleanArray(pr.extSize) }
        for (x in 0 until pr.extSize) {
            if (pr.isMonthExt(x)) continue
            val p = pr.external[x]
            if (p >= 0) has[p][x] = true
        }
        for (i in assignment.indices) {
            val p = assignment[i]
            if (p >= 0) has[p][pr.ext(i)] = true
        }
        return has
    }

    fun total(assignment: IntArray): Double {
        val has = buildHas(assignment)
        return (0 until pr.personCount).sumOf { personCost(it, has[it]) }
    }

    fun penalties(assignment: IntArray): List<Penalty> {
        val has = buildHas(assignment)
        val list = ArrayList<Penalty>()
        for (p in 0 until pr.personCount) personCost(p, has[p]) { list += it }
        return list
    }
}
