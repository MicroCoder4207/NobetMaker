package com.nobetmaker.core.quota

import com.nobetmaker.core.model.Person
import com.nobetmaker.core.model.QuotaEntry
import com.nobetmaker.core.rules.Availability
import java.time.YearMonth
import kotlin.math.floor

data class QuotaResult(
    val entries: List<QuotaEntry>,
    /** Kişinin ham hakkı (küsurat dahil), açıklama için. */
    val raw: Map<String, Double>,
    val warnings: List<String>,
)

/** Kim kaç nöbet tutar (§5.1). */
object QuotaCalculator {

    /**
     * @param carryIn önceki aydan devreden küsurat
     * @param capacity kişinin bu ay en fazla kaç nöbete sığabileceği (uygun günler ve en az aralık kuralına göre)
     * @param fixed elle sabitlenen sayılar; kalan günler diğerlerine aynı kurallarla dağıtılır
     * @param dutyOf kişinin o günkü nöbet katsayısı (kıdem eğrisi ya da elle girilen)
     * @param regular ayın tamamında olağan katılan kişi; kıdem kuralı (§5.5) yalnızca bunlar arasında uygulanır
     */
    fun compute(
        month: YearMonth,
        persons: List<Person>,
        availability: Availability,
        carryIn: Map<String, Double>,
        capacity: Map<String, Int>,
        maxPerMonth: Int,
        fixedTotal: Int = month.lengthOfMonth(),
        fixed: Map<String, Int> = emptyMap(),
        dutyOf: (Person, java.time.LocalDate) -> Double = { p, d -> p.coefficientOn(d).duty },
        regular: (Person) -> Boolean = { true },
    ): QuotaResult {
        if (fixed.isNotEmpty()) {
            // Önce kimse sabitlenmemiş gibi öneri (gösterim için), sonra kalan günlerin diğerlerine dağıtımı.
            val full = compute(month, persons, availability, carryIn, capacity, maxPerMonth, fixedTotal, dutyOf = dutyOf, regular = regular)
            val rest = persons.filter { it.id !in fixed }
            val remaining = (fixedTotal - fixed.values.sum()).coerceAtLeast(0)
            val part = compute(month, rest, availability, carryIn, capacity, maxPerMonth, remaining, dutyOf = dutyOf, regular = regular)
            val entries = persons.map { p ->
                val v = fixed[p.id]
                if (v != null) full.entries.first { it.personId == p.id }.copy(final = v, manual = true)
                else part.entries.first { it.personId == p.id }
            }
            val warnings = part.warnings.toMutableList()
            if (fixed.values.sum() > fixedTotal) warnings += "Elle girilen sayılar (${fixed.values.sum()}) ayın gün sayısını ($fixedTotal) aşıyor"
            return QuotaResult(entries, full.raw, warnings)
        }
        val n = month.lengthOfMonth()
        val days = (1..n).map { month.atDay(it) }
        val warnings = ArrayList<String>()

        val effective = persons.associate { p ->
            p.id to days.sumOf { d -> if (availability.countsForQuota(p, d)) dutyOf(p, d) else 0.0 } / n
        }
        val participants = persons.filter { (effective[it.id] ?: 0.0) > 0.0 }
        val sumEff = participants.sumOf { effective.getValue(it.id) }
        if (participants.isEmpty() || sumEff <= 0.0) {
            return QuotaResult(persons.map { QuotaEntry(it.id, 0, 0) }, emptyMap(), listOf("Bu ay nöbet tutacak kimse yok"))
        }

        val raw = participants.associate { p ->
            p.id to fixedTotal * effective.getValue(p.id) / sumEff + (carryIn[p.id] ?: 0.0)
        }

        // Kıdem kuralı (§5.5): aynı ay içinde kimse kendinden kıdemliden az tutmaz. Olağan katılanlar kıdem
        // sırasına dizilir; sırayı bozan haklar (devreden yüzünden) havuzlanıp eşitlenir.
        val ranked = participants.filter(regular).sortedBy { it.seniority }
        val adjusted = raw.toMutableMap()
        for ((members, value) in pool(ranked.map { it.id to raw.getValue(it.id) })) for (id in members) adjusted[id] = value

        // Toplam tam olarak fixedTotal olacak şekilde yuvarla; eşitlikte fazla nöbet çömeze gider.
        val base = participants.associate { it.id to maxOf(0, floor(adjusted.getValue(it.id)).toInt()) }.toMutableMap()
        var diff = fixedTotal - base.values.sum()
        val byFracDesc = participants.sortedWith(
            compareByDescending<Person> { adjusted.getValue(it.id) - floor(adjusted.getValue(it.id)) }.thenByDescending { it.seniority },
        )
        var idx = 0
        while (diff > 0) {
            val p = byFracDesc[idx % byFracDesc.size]
            base[p.id] = base.getValue(p.id) + 1
            diff--; idx++
        }
        val byFracAsc = byFracDesc.reversed()
        idx = 0
        var guard = 0
        while (diff < 0 && guard++ < 1000) {
            val p = byFracAsc[idx % byFracAsc.size]
            if (base.getValue(p.id) > 0) {
                base[p.id] = base.getValue(p.id) - 1
                diff++
            }
            idx++
        }

        // Aynı tabandakiler kendi toplamlarını eşit paylaşır, artanlar en çömeze: sıra bozulmaz.
        val floors = ranked.associate { it.id to maxOf(0, floor(adjusted.getValue(it.id)).toInt()) }
        for ((_, group) in ranked.groupBy { floors.getValue(it.id) }) {
            val total = group.sumOf { base.getValue(it.id) }
            val each = total / group.size
            val extra = total % group.size
            group.forEachIndexed { i, p -> base[p.id] = each + if (i >= group.size - extra) 1 else 0 }
        }

        // Üst sınır ve fiziksel sığma; aşan kısım o ay diğerlerine dağılır, borç yazılmaz (§5.1-7).
        val final = base.toMutableMap()
        val capped = HashSet<String>()
        var excess = 0
        for (p in participants) {
            val cap = minOf(maxPerMonth, capacity[p.id] ?: maxPerMonth)
            if (final.getValue(p.id) > cap) {
                excess += final.getValue(p.id) - cap
                final[p.id] = cap
                capped += p.id
                warnings += "${p.name}: en fazla $cap nöbete sığıyor, fazlası diğerlerine dağıtıldı"
            }
        }
        while (excess > 0) {
            val open = participants.filter { it.id !in capped && final.getValue(it.id) < minOf(maxPerMonth, capacity[it.id] ?: maxPerMonth) }
            // Önce kıdem sırasını bozmayan biri (kendinden çömezlerin hiçbirini geçmez); yoksa sığan herhangi biri.
            val keepsOrder = open.filter { p ->
                p !in ranked || ranked.none { j -> j.seniority > p.seniority && final.getValue(j.id) < final.getValue(p.id) + 1 }
            }
            val receiver = keepsOrder.ifEmpty { open }
                .maxWithOrNull(compareBy<Person> { adjusted.getValue(it.id) - final.getValue(it.id) }.thenBy { it.seniority })
            if (receiver == null) {
                warnings += "Toplam kapasite yetersiz: $excess nöbet kimseye sığmıyor"
                break
            }
            final[receiver.id] = final.getValue(receiver.id) + 1
            excess--
        }

        val entries = persons.map { p ->
            val q = final[p.id] ?: 0
            val carryOut = when {
                p.id !in raw -> 0.0
                p.id in capped -> 0.0
                else -> raw.getValue(p.id) - base.getValue(p.id)
            }
            QuotaEntry(p.id, suggested = q, final = q, carryIn = carryIn[p.id] ?: 0.0, carryOut = carryOut)
        }
        return QuotaResult(entries, raw, warnings)
    }

    /**
     * Kıdem sırasına dizilmiş (en kıdemli önce) haklarda azalan çiftleri havuzlar: komşu bloklar ortalamada birleşir,
     * sonuç kıdemliden çömeze azalmayan olur (izotonik regresyon). Dönüş: (blok üyeleri, blok değeri).
     */
    fun pool(values: List<Pair<String, Double>>): List<Pair<List<String>, Double>> {
        val blocks = ArrayList<Triple<MutableList<String>, Double, Int>>()
        for ((id, v) in values) {
            blocks += Triple(mutableListOf(id), v, 1)
            while (blocks.size >= 2) {
                val b = blocks[blocks.size - 1]
                val a = blocks[blocks.size - 2]
                if (a.second / a.third <= b.second / b.third + 1e-9) break
                blocks.removeAt(blocks.size - 1)
                blocks[blocks.size - 1] = Triple((a.first + b.first).toMutableList(), a.second + b.second, a.third + b.third)
            }
        }
        return blocks.map { (ids, sum, n) -> ids to sum / n }
    }
}
