package com.nobetmaker.core.solver

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.random.Random

class Solution(val assignment: IntArray, val cost: Double)

class Diagnostics(val problems: List<String>, val suggestions: List<String>)

class SolveResult(val solutions: List<Solution>, val diagnostics: Diagnostics?)

/**
 * Liste üretimi (§8): geri izlemeli yerleştirme ile kurallara uyan bir başlangıç,
 * ardından kotaları bozmayan takaslarla benzetimli tavlama.
 */
class Solver(private val pr: MonthProblem) {

    private val obj = Objective(pr)
    private val D = pr.dayCount
    private val P = pr.personCount

    fun canPlace(p: Int, i: Int, has: BooleanArray): Boolean {
        if (!pr.available[p][i]) return false
        val l = pr.locked[i]
        if (l != -1 && l != p) return false
        val x = pr.ext(i)
        for (k in 1 until pr.minGap) {
            val a = x - k
            val b = x + k
            if (a >= 0 && has[a]) return false
            if (b < pr.extSize && has[b]) return false
        }
        return true
    }

    /** Kurallara uyan ilk liste; bulunamazsa null. */
    fun construct(rng: Random, nodeLimit: Int = 20_000, quotas: IntArray = pr.quotas, locks: IntArray = pr.locked): IntArray? {
        val a = IntArray(D) { -1 }
        val has = obj.buildHas(a)
        val rem = quotas.copyOf()
        for (i in 0 until D) {
            val l = locks[i]
            if (l >= 0) {
                a[i] = l
                has[l][pr.ext(i)] = true
                rem[l]--
            }
        }
        if (rem.any { it < 0 }) return null
        if (rem.sum() != a.count { it == -1 }) return null
        val baseCost = DoubleArray(P) { obj.personCost(it, has[it]) }
        var nodes = 0

        fun placeOk(p: Int, i: Int): Boolean {
            if (!pr.available[p][i]) return false
            if (locks[i] != -1 && locks[i] != p) return false
            val x = pr.ext(i)
            for (k in 1 until pr.minGap) {
                if (x - k >= 0 && has[p][x - k]) return false
                if (x + k < pr.extSize && has[p][x + k]) return false
            }
            return true
        }

        fun dfs(): Boolean {
            if (++nodes > nodeLimit) return false
            var bestDay = -1
            var bestCands: List<Int> = emptyList()
            for (i in 0 until D) {
                if (a[i] != -1) continue
                val cands = (0 until P).filter { rem[it] > 0 && placeOk(it, i) }
                if (cands.isEmpty()) return false
                val better = bestDay == -1 || cands.size < bestCands.size ||
                    (cands.size == bestCands.size && pr.points[i] > pr.points[bestDay] + 1e-9) ||
                    (cands.size == bestCands.size && pr.points[i] == pr.points[bestDay] && rng.nextBoolean())
                if (better) {
                    bestDay = i
                    bestCands = cands
                }
            }
            if (bestDay == -1) return true

            // Her kişinin kalan kotası, hâlâ yerleşebileceği gün sayısını aşmamalı.
            val placeable = IntArray(P)
            for (i in 0 until D) if (a[i] == -1) for (p in 0 until P) if (rem[p] > 0 && placeOk(p, i)) placeable[p]++
            for (p in 0 until P) if (rem[p] > placeable[p]) return false

            val x = pr.ext(bestDay)
            val scored = bestCands.map { p ->
                has[p][x] = true
                val after = obj.personCost(p, has[p])
                has[p][x] = false
                val urgency = rem[p].toDouble() / max(1, placeable[p])
                p to (after - baseCost[p] - 40.0 * urgency + rng.nextDouble() * 3.0)
            }.sortedBy { it.second }

            for ((p, _) in scored) {
                a[bestDay] = p
                has[p][x] = true
                rem[p]--
                val saved = baseCost[p]
                baseCost[p] = obj.personCost(p, has[p])
                if (dfs()) return true
                baseCost[p] = saved
                rem[p]++
                has[p][x] = false
                a[bestDay] = -1
                if (nodes > nodeLimit) return false
            }
            return false
        }

        return if (dfs()) a else null
    }

    /** Kotaları koruyan takaslarla iyileştirme. */
    fun anneal(start: IntArray, rng: Random, iterations: Int): IntArray {
        val a = start.copyOf()
        val has = obj.buildHas(a)
        val costs = DoubleArray(P) { obj.personCost(it, has[it]) }
        var total = costs.sum()
        var best = a.copyOf()
        var bestTotal = total
        val movable = (0 until D).filter { pr.locked[it] == -1 }
        if (movable.size < 2) return a
        val t0 = 40.0
        val t1 = 0.2
        for (k in 0 until iterations) {
            val temp = t0 * (t1 / t0).pow(k.toDouble() / iterations)
            val i = movable[rng.nextInt(movable.size)]
            val j = movable[rng.nextInt(movable.size)]
            if (i == j) continue
            val p = a[i]
            val q = a[j]
            if (p == q) continue
            val xi = pr.ext(i)
            val xj = pr.ext(j)

            has[q][xj] = false
            val okQ = canPlace(q, i, has[q])
            has[q][xj] = true
            if (!okQ) continue
            has[p][xi] = false
            val okP = canPlace(p, j, has[p])
            has[p][xi] = true
            if (!okP) continue

            has[p][xi] = false; has[p][xj] = true
            has[q][xj] = false; has[q][xi] = true
            val cp = obj.personCost(p, has[p])
            val cq = obj.personCost(q, has[q])
            val delta = cp + cq - costs[p] - costs[q]
            if (delta <= 0.0 || rng.nextDouble() < exp(-delta / temp)) {
                a[i] = q; a[j] = p
                costs[p] = cp; costs[q] = cq
                total += delta
                if (total < bestTotal - 1e-9) {
                    bestTotal = total
                    best = a.copyOf()
                }
            } else {
                has[p][xj] = false; has[p][xi] = true
                has[q][xi] = false; has[q][xj] = true
            }
        }
        return best
    }

    fun solve(alternatives: Int = 3, seeds: Int = 8, iterations: Int = 30_000, seedOffset: Int = 0): SolveResult {
        val found = ArrayList<Solution>()
        for (s in 0 until seeds) {
            val rng = Random(seedOffset * 1000 + s)
            val start = construct(rng) ?: continue
            val improved = anneal(start, rng, iterations)
            found += Solution(improved, obj.total(improved))
        }
        if (found.isEmpty()) return SolveResult(emptyList(), diagnose())
        val sorted = found.sortedBy { it.cost }
        val chosen = ArrayList<Solution>()
        for (minDiff in intArrayOf(4, 1)) {
            for (s in sorted) {
                if (chosen.size >= alternatives) break
                if (chosen.none { it === s } && chosen.all { diff(it.assignment, s.assignment) >= minDiff }) chosen += s
            }
        }
        return SolveResult(chosen, null)
    }

    private fun diff(a: IntArray, b: IntArray) = a.indices.count { a[it] != b[it] }

    /** Çözüm yoksa sebepler ve en ucuz gevşetmeler (§8.6). Art arda/günaşırı kuralı asla önerilmez. */
    fun diagnose(): Diagnostics {
        val problems = ArrayList<String>()
        val suggestions = ArrayList<String>()
        for (i in 0 until D) {
            if (pr.locked[i] != -1) continue
            if ((0 until P).none { pr.available[it][i] }) {
                val reasons = (0 until P).joinToString("; ") { "${pr.persons[it].name}: ${pr.hardReasons[it][i].joinToString(", ")}" }
                problems += "${shortDate(pr.days[i])} için uygun kimse yok ($reasons)"
            }
        }
        val emptyHas = obj.buildHas(pr.locked.copyOf())
        for (p in 0 until P) {
            var count = 0
            var last = Int.MIN_VALUE / 2
            for (x in 0 until MonthProblem.PAD) if (emptyHas[p][x]) last = x
            for (i in 0 until D) {
                val x = pr.ext(i)
                if (pr.locked[i] == p) { count++; last = x; continue }
                if (pr.available[p][i] && pr.locked[i] == -1 && x - last >= pr.minGap) { count++; last = x }
            }
            if (pr.quotas[p] > count) problems += "${pr.persons[p].name}: kota ${pr.quotas[p]} ama en fazla $count nöbete sığıyor"
        }
        if (pr.quotas.sum() != D) problems += "Kotaların toplamı ${pr.quotas.sum()}, ayın gün sayısı $D"
        problems += crowdedWindows()

        val quick = Random(7)
        outer@ for (p in 0 until P) {
            for (q in 0 until P) {
                if (p == q || pr.quotas[p] == 0) continue
                val qs = pr.quotas.copyOf()
                qs[p]--; qs[q]++
                if (qs[q] > pr.maxPerMonth) continue
                if (construct(quick, 4_000, qs) != null) {
                    suggestions += "${pr.persons[p].name} kotasını 1 azaltıp ${pr.persons[q].name} kotasını 1 artırmak çözüyor"
                    if (suggestions.size >= 5) break@outer
                }
            }
        }
        for (i in 0 until D) {
            if (pr.locked[i] == -1 || suggestions.size >= 8) continue
            val locks = pr.locked.copyOf()
            locks[i] = -1
            if (construct(quick, 4_000, pr.quotas, locks) != null) {
                suggestions += "${shortDate(pr.days[i])} kilidini (${pr.persons[pr.locked[i]].name}) kaldırmak çözüyor"
            }
        }
        if (suggestions.isEmpty()) suggestions += "Kesin isteklerden birini \"mümkünse\"ye indirmeyi ya da bir izni belirsiz yapmayı deneyin"
        return Diagnostics(problems, suggestions)
    }

    /**
     * Uygun kişilerin, en az aralık kuralını bozmadan dolduramadığı en kısa tarih aralıkları.
     * Erken uyarı için de kullanılır (§8.6).
     */
    fun crowdedWindows(maxLength: Int = 14): List<String> {
        val has = obj.buildHas(IntArray(D) { -1 })
        fun coverable(p: Int, s: Int, e: Int): Int {
            var count = 0
            var last = Int.MIN_VALUE / 2
            for (x in pr.ext(s) - pr.minGap + 1 until pr.ext(s)) if (x >= 0 && has[p][x]) last = x
            for (i in s..e) {
                val x = pr.ext(i)
                val ok = pr.available[p][i] && (pr.locked[i] == -1 || pr.locked[i] == p) && x - last >= pr.minGap
                if (ok) { count++; last = x }
            }
            return count
        }
        val found = ArrayList<IntRange>()
        for (s in 0 until D) {
            for (e in s until minOf(D, s + maxLength)) {
                val need = e - s + 1
                val cover = (0 until P).sumOf { coverable(it, s, e) }
                if (cover < need) {
                    if (found.none { s <= it.last && e >= it.first }) found += s..e
                    break
                }
            }
        }
        return found.take(4).map { r ->
            val names = (0 until P).filter { p -> r.any { pr.available[p][it] } }.joinToString(", ") { pr.persons[it].name }
            val range = if (r.first == r.last) shortDate(pr.days[r.first]) else "${shortDate(pr.days[r.first])} – ${shortDate(pr.days[r.last])}"
            "$range arasındaki ${r.last - r.first + 1} gün, uygun kişilerle ($names) art arda/günaşırı olmadan doldurulamıyor"
        }
    }
}
