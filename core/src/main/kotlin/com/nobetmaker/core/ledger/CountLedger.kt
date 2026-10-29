package com.nobetmaker.core.ledger

import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.model.RuleType
import com.nobetmaker.core.rules.Availability
import com.nobetmaker.core.rules.RosterIndex
import java.time.YearMonth

/** Kişinin nöbet sayısı dengesi (§5.4): tutulan − payı; pozitif = payından fazla tutmuş. */
class CountBalance(
    val held: Int,
    val expected: Double,
    /** Sayısı hoca kararıyla belirlenen aylar (bu aylar dengeye girmez). */
    val decided: List<YearMonth>,
) {
    val diff: Double get() = held - expected
}

/**
 * Nöbet sayısı defteri (§5.4). Kayıt tutmaz; her seferinde resmi listeden yeniden hesaplanır, böylece elle
 * yapılan her düzeltme (kapanmış ay dahil) kendiliğinden yansır.
 *
 * Her ay: asistanların tuttuğu günlerden hoca kararıyla belirlenen kişilerin tuttukları düşülür, kalan günler
 * diğerleri arasında o ayki nöbet katsayısıyla paylaştırılır. Hoca kararı olan kişiye borç ya da alacak yazılmaz;
 * fazla ya da eksik tutan diğerleri sonraki aylarda dengelenir.
 */
class CountLedger(
    private val data: AppData,
    private val availability: Availability,
    private val roster: RosterIndex,
) {

    /** Hoca kararıyla belirlenen sayılar: "hoca kararı" işaretli elle sabitlemeler ve "ayda sabit sayı" kuralı. */
    fun decisions(month: YearMonth): Map<String, Int> {
        val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
        val rules = data.persons.mapNotNull { p ->
            p.rules.firstOrNull { it.type == RuleType.MONTHLY_COUNT && it.count != null && days.any { d -> it.appliesOn(d) } }
                ?.let { p.id to it.count!! }
        }.toMap()
        val manual = data.month(month)?.quotas.orEmpty().filter { it.manual && it.decision }.associate { it.personId to it.final }
        return rules + manual
    }

    /** [from] dahil, [untilExclusive] hariç aylardaki denge. */
    fun balance(from: YearMonth, untilExclusive: YearMonth): Map<String, CountBalance> {
        val held = HashMap<String, Int>()
        val expected = HashMap<String, Double>()
        val decided = HashMap<String, MutableList<YearMonth>>()
        var m = from
        while (m < untilExclusive) {
            val days = (1..m.lengthOfMonth()).map { m.atDay(it) }
            val owners = days.mapNotNull { roster.official[it] }
            if (owners.isNotEmpty()) {
                val count = owners.groupingBy { it }.eachCount()
                val fixed = decisions(m)
                val effective = data.persons.associate { p ->
                    p.id to days.sumOf { d -> if (availability.countsForQuota(p, d)) availability.coefficients.on(p, d).duty else 0.0 } / days.size
                }
                val sharing = data.persons.filter { it.id !in fixed && effective.getValue(it.id) > 0.0 }
                val remaining = owners.size - fixed.keys.sumOf { count[it] ?: 0 }
                val sum = sharing.sumOf { effective.getValue(it.id) }
                for ((id, n) in count) held.merge(id, n, Int::plus)
                for (id in fixed.keys) {
                    expected.merge(id, (count[id] ?: 0).toDouble(), Double::plus)
                    decided.getOrPut(id) { ArrayList() } += m
                }
                if (sum > 0.0) for (p in sharing) expected.merge(p.id, remaining * effective.getValue(p.id) / sum, Double::plus)
            }
            m = m.plusMonths(1)
        }
        return (held.keys + expected.keys).associateWith { id ->
            CountBalance(held[id] ?: 0, expected[id] ?: 0.0, decided[id].orEmpty())
        }
    }

    /**
     * Planlanan aya devreden: dengenin tersi (eksik tutan +, fazla tutan −). Bir ayda en fazla ±2 düzeltilir,
     * büyük farklar birkaç aya yayılır.
     */
    fun carryInto(month: YearMonth): Map<String, Double> {
        val start = YearMonth.from(data.settings.countBalanceStart)
        if (month <= start) return emptyMap()
        return balance(start, month).mapValues { (-it.value.diff).coerceIn(-MAX_CARRY, MAX_CARRY) }
    }

    companion object {
        const val MAX_CARRY = 2.0
    }
}
