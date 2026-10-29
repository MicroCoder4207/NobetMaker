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

    /**
     * Kotaları koruyan hamlelerle iyileştirme: iki günün nöbetçilerini değiştirme ya da üç günün
     * nöbetçilerini döndürme (A→B→C→A). Üçlü döndürme, ikili takasla çıkılamayan yerel çukurlardan kurtarır.
     */
    fun anneal(start: IntArray, rng: Random, iterations: Int, t0: Double = 10.0, t1: Double = 0.2): IntArray {
        val a = start.copyOf()
        val has = obj.buildHas(a)
        val costs = DoubleArray(P) { obj.personCost(it, has[it]) }
        var total = costs.sum() + obj.sequenceCost(a)
        var best = a.copyOf()
        var bestTotal = total
        val movable = (0 until D).filter { pr.locked[it] == -1 }
        if (movable.size < 2) return a

        // Sıra tekrarı: ard arda günlerdeki (u, v) ikililerinin sayısı. Bir hamle en fazla 6 komşuluğa dokunur.
        val seqW = pr.weights.sequenceRepeat
        val pairs = IntArray(P * P)
        fun pairAt(k: Int): Int {
            val u = a[k]
            val v = a[k + 1]
            return if (u >= 0 && v >= 0) u * P + v else -1
        }
        for (k in 0 until D - 1) pairAt(k).let { if (it >= 0) pairs[it]++ }
        val touched = IntArray(6)
        var touchedCount = 0
        fun touch(k: Int) {
            if (k < 0 || k >= D - 1) return
            for (t in 0 until touchedCount) if (touched[t] == k) return
            touched[touchedCount++] = k
        }
        /** Komşulukları sayaçtan düşer ya da ekler; sıra cezasındaki değişimi döner. */
        fun shiftPairs(add: Boolean): Double {
            var change = 0.0
            for (t in 0 until touchedCount) {
                val key = pairAt(touched[t])
                if (key < 0) continue
                if (add) {
                    if (pairs[key] >= 1) change += seqW
                    pairs[key]++
                } else {
                    if (pairs[key] >= 2) change -= seqW
                    pairs[key]--
                }
            }
            return change
        }

        // Hamle: days[t] gününün nöbetçisi who[t], yeni nöbetçisi who[(t + 1) % n]; yani who[t], days[t - 1] gününe geçer.
        val days = IntArray(3)
        val who = IntArray(3)
        val newCost = DoubleArray(3)

        /** days[0 until n] için hamleyi dener; accept kabul ederse uygular. */
        fun attempt(n: Int, accept: (Double) -> Boolean): Boolean {
            for (t in 0 until n) {
                who[t] = a[days[t]]
                for (u in 0 until t) if (days[u] == days[t] || who[u] == who[t]) return false
            }
            for (t in 0 until n) {
                val p = who[t]
                val from = pr.ext(days[t])
                has[p][from] = false
                val ok = canPlace(p, days[(t + n - 1) % n], has[p])
                has[p][from] = true
                if (!ok) return false
            }
            var oldCost = 0.0
            for (t in 0 until n) {
                val p = who[t]
                has[p][pr.ext(days[t])] = false
                has[p][pr.ext(days[(t + n - 1) % n])] = true
                newCost[t] = obj.personCost(p, has[p])
                oldCost += costs[p]
            }
            var delta = -oldCost
            for (t in 0 until n) delta += newCost[t]
            touchedCount = 0
            if (seqW > 0.0) {
                for (t in 0 until n) { touch(days[t] - 1); touch(days[t]) }
                delta += shiftPairs(add = false)
            }
            for (t in 0 until n) a[days[t]] = who[(t + 1) % n]
            if (seqW > 0.0) delta += shiftPairs(add = true)
            if (accept(delta)) {
                for (t in 0 until n) costs[who[t]] = newCost[t]
                total += delta
                if (total < bestTotal - 1e-9) {
                    bestTotal = total
                    best = a.copyOf()
                }
                return true
            }
            if (seqW > 0.0) shiftPairs(add = false)
            for (t in 0 until n) a[days[t]] = who[t]
            if (seqW > 0.0) shiftPairs(add = true)
            for (t in 0 until n) {
                val p = who[t]
                has[p][pr.ext(days[(t + n - 1) % n])] = false
                has[p][pr.ext(days[t])] = true
            }
            return false
        }

        for (k in 0 until iterations) {
            val temp = t0 * (t1 / t0).pow(k.toDouble() / iterations)
            val n = if (movable.size >= 3 && rng.nextInt(4) == 0) 3 else 2
            for (t in 0 until n) days[t] = movable[rng.nextInt(movable.size)]
            attempt(n) { delta -> delta <= 0.0 || rng.nextDouble() < exp(-delta / temp) }
        }

        // Cilalama: en iyi listeden başlayıp iyileştiren bütün ikili takas ve üçlü döndürmeleri uygula.
        return polish(best)
    }

    /** Yerel en iyiye kadar iyileştiren ikili takas ve üçlü döndürmeler (rastgelelik yok). */
    fun polish(start: IntArray, maxPasses: Int = 6): IntArray {
        val a = start.copyOf()
        val has = obj.buildHas(a)
        val costs = DoubleArray(P) { obj.personCost(it, has[it]) }
        val movable = (0 until D).filter { pr.locked[it] == -1 }
        val seqW = pr.weights.sequenceRepeat
        var seq = obj.sequenceCost(a)
        val days = IntArray(3)
        val who = IntArray(3)
        val newCost = DoubleArray(3)

        fun tryMove(n: Int): Boolean {
            for (t in 0 until n) {
                who[t] = a[days[t]]
                for (u in 0 until t) if (who[u] == who[t]) return false
            }
            for (t in 0 until n) {
                val p = who[t]
                val from = pr.ext(days[t])
                has[p][from] = false
                val ok = canPlace(p, days[(t + n - 1) % n], has[p])
                has[p][from] = true
                if (!ok) return false
            }
            var delta = 0.0
            for (t in 0 until n) {
                val p = who[t]
                has[p][pr.ext(days[t])] = false
                has[p][pr.ext(days[(t + n - 1) % n])] = true
                newCost[t] = obj.personCost(p, has[p])
                delta += newCost[t] - costs[p]
            }
            for (t in 0 until n) a[days[t]] = who[(t + 1) % n]
            val newSeq = if (seqW > 0.0) obj.sequenceCost(a) else 0.0
            delta += newSeq - seq
            if (delta < -1e-9) {
                for (t in 0 until n) costs[who[t]] = newCost[t]
                seq = newSeq
                return true
            }
            for (t in 0 until n) a[days[t]] = who[t]
            for (t in 0 until n) {
                val p = who[t]
                has[p][pr.ext(days[(t + n - 1) % n])] = false
                has[p][pr.ext(days[t])] = true
            }
            return false
        }

        repeat(maxPasses) {
            var improved = false
            for (x in movable.indices) for (y in x + 1 until movable.size) {
                days[0] = movable[x]; days[1] = movable[y]
                if (tryMove(2)) improved = true
            }
            for (x in movable.indices) for (y in x + 1 until movable.size) for (z in y + 1 until movable.size) {
                days[0] = movable[x]; days[1] = movable[y]; days[2] = movable[z]
                if (tryMove(3)) { improved = true; continue }
                days[1] = movable[z]; days[2] = movable[y]
                if (tryMove(3)) improved = true
            }
            if (!improved) return a
        }
        return a
    }

    fun solve(alternatives: Int = 3, seeds: Int = 64, iterations: Int = 40_000, seedOffset: Int = 0): SolveResult {
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
            if (pr.quotas[p] > count) problems += "${pr.persons[p].name}: ${pr.quotas[p]} nöbet yazılmalı ama en fazla $count nöbete sığıyor"
        }
        if (pr.quotas.sum() != D) problems += "Nöbet sayılarının toplamı ${pr.quotas.sum()}, ayın gün sayısı $D"
        problems += crowdedWindows()

        val quick = Random(7)
        outer@ for (p in 0 until P) {
            for (q in 0 until P) {
                if (p == q || pr.quotas[p] == 0) continue
                val qs = pr.quotas.copyOf()
                qs[p]--; qs[q]++
                if (qs[q] > pr.maxPerMonth) continue
                if (construct(quick, 4_000, qs) != null) {
                    suggestions += "${pr.persons[p].name} nöbet sayısını 1 azaltıp ${pr.persons[q].name} nöbet sayısını 1 artırmak çözüyor"
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
