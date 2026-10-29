package com.nobetmaker.core.ledger

import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.model.Person
import com.nobetmaker.core.model.RuleType
import com.nobetmaker.core.rules.Availability
import com.nobetmaker.core.rules.RosterIndex
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

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
 *
 * Kıdem kuralı (§5.5) yüzünden payları birbirine yakın (farkı [NEAR] altında) ve kıdemce ardışık kişiler o ay
 * eşit ya da çömez lehine birer fazla tutar; aralarındaki yuvarlama farkı kişiye değil gruba yazılır: grubun toplam
 * sapması üyelere payları oranında bölünür. Böylece sonraki ay yine aynı sıra korunur, grup olarak da denge kayar.
 * Elle verilen (hoca kararı olmayan) sayı ve ayın bir kısmında olmayan kişi kendi sapmasını taşır.
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
                if (sum > 0.0) {
                    val share = sharing.associate { it.id to remaining * effective.getValue(it.id) / sum }
                    val own = data.month(m)?.quotas.orEmpty().filter { it.manual && !it.decision }.map { it.personId }.toSet()
                    for (group in groups(sharing, share, own, days)) {
                        val dev = group.sumOf { (count[it] ?: 0) - share.getValue(it) }
                        val total = group.sumOf { share.getValue(it) }
                        for (id in group) expected.merge(id, (count[id] ?: 0) - dev * share.getValue(id) / total, Double::plus)
                    }
                }
            }
            m = m.plusMonths(1)
        }
        return (held.keys + expected.keys).associateWith { id ->
            CountBalance(held[id] ?: 0, expected[id] ?: 0.0, decided[id].orEmpty())
        }
    }

    /**
     * Sapmayı birlikte taşıyan gruplar: ayın tamamında olağan katılan, elle sayı verilmemiş kişiler kıdem sırasıyla
     * dizilir; payı bir öncekine [NEAR]'dan yakın olan aynı gruba girer. Diğerleri tek başına.
     */
    private fun groups(sharing: List<Person>, share: Map<String, Double>, own: Set<String>, days: List<LocalDate>): List<List<String>> {
        val (ranked, alone) = sharing.sortedBy { it.seniority }
            .partition { p -> p.id !in own && days.all { d -> availability.countsForQuota(p, d) } }
        val out = ArrayList<MutableList<String>>()
        for (p in ranked) {
            val last = out.lastOrNull()
            if (last != null && abs(share.getValue(p.id) - share.getValue(last.last())) < NEAR) last += p.id else out += mutableListOf(p.id)
        }
        return out + alone.map { listOf(it.id) }
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

        /** Payları bundan yakın olan ardışık kişiler sapmayı birlikte taşır. */
        const val NEAR = 0.5
    }
}
