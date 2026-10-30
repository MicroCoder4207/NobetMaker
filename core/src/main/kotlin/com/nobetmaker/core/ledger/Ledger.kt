package com.nobetmaker.core.ledger

import com.nobetmaker.core.calendar.CalendarService
import com.nobetmaker.core.calendar.Category
import com.nobetmaker.core.calendar.RotationGroup
import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.model.EventKind
import com.nobetmaker.core.model.Person
import com.nobetmaker.core.rules.Availability
import com.nobetmaker.core.rules.RosterIndex
import java.time.DayOfWeek
import java.time.LocalDate

/** Sıra durumu (§6); diziler [RotationGroup.entries] sırasındadır. */
class RotationState(
    /** Beklenen − tutulan; pozitif = sırası gelmiş. */
    val debt: Map<String, DoubleArray>,
    val count: Map<String, IntArray>,
    val last: Map<String, Array<LocalDate?>>,
    /** Planlanan ayda kişinin bu gruptan tutması beklenen gün sayısı. */
    val target: Map<String, IntArray>,
    /** Planlanan ayın sırası: grup başına, günlere sırayla verilecek kişi id'leri. */
    val queue: List<List<String>>,
    /** Zor günler (cuma, cumartesi, pazar) toplamında beklenen − tutulan; pozitif = geride. */
    val hardDebt: Map<String, Double> = emptyMap(),
    /** Zor günler toplamında tutulan gün sayısı (sıra hafızası içinde). */
    val hardCount: Map<String, Int> = emptyMap(),
)

/** Uzun hafızalı kategorilerin (Yılbaşı, Bayram, Resmi tatil) durumu; dizi sırası [Category.LONG_MEMORY]. */
class LongMemoryState(
    val categories: List<Category>,
    /** Tutulan gün − beklenen gün; plan günlerinin beklentisi dahil. */
    val base: Map<String, DoubleArray>,
    /** Tutulan gün (arefe yarım). */
    val days: Map<String, DoubleArray>,
    val presentEvents: Map<String, IntArray>,
    val expected: Map<String, DoubleArray>,
)

/** Adalet defteri (§6). Kayıt tutmaz; her seferinde resmi listeden yeniden hesaplanır. */
class Ledger(
    private val data: AppData,
    private val cal: CalendarService,
    private val availability: Availability,
    private val roster: RosterIndex,
) {

    /**
     * Vizit, Cuma, Pazar, Perşembe ve Pazartesi sırası. Geçmişte payından az tutan önce gelir; eşitlikte en uzun
     * süredir tutmayan, o da eşitse çömez. Vizit (cumartesi) kıdem sırasıyla gider: son yıla girmemiş herkes ayda
     * birer tutar (aralarında borç, eşitlikte çömez önce); artan cumartesiler son yıldakilere sırayla, en uzun
     * süredir cumartesi tutmayan önce.
     *
     * Zor günler (cuma, cumartesi, pazar; §6.1): hafta sonu tutan herkes ayda eşit sayıda (çoğu ay 2) tutar. Fazla
     * gün önce çömezlere, eksik gün önce kıdemliden; kendi kademesinde en geride olan önce. Kimseye aynı haftanın
     * gününden ikincisi, cumartesi tutan kıdemliye pazar ve cumartesi tutana üçüncü zor gün yazılmaz; ay buna
     * yetmiyorsa (ör. 14 zor gün) en az esneyen dağılım seçilir. Denge kademe içinde tutulur: çömezlerin günü
     * çömezler, son yıldakilerin günü son yıldakiler arasında eşit paylaşılır. Hafta sonu tutmayan (uzatma)
     * zor gün düzenine girmez.
     */
    fun rotation(
        persons: List<Person>,
        windowStart: LocalDate,
        historyEnd: LocalDate,
        planDays: List<LocalDate>,
        availableInPlan: (Person, LocalDate) -> Boolean,
        wants: (Person, LocalDate) -> Boolean = { _, _ -> false },
        avoids: (Person, LocalDate) -> Boolean = { _, _ -> false },
        /** Kişinin istekleri dışında kalan nöbet sayısı; dolan kişiye sıradan gün verilmez (sıkışırsa esner). */
        room: (Person) -> Int = { Int.MAX_VALUE },
    ): RotationState {
        val groups = RotationGroup.entries
        val k = groups.size
        // Geçmiş günlerin payı o gün ekipte olan herkes arasında dağılır (sonradan ayrılanlar dahil); yoksa
        // ayrılan birinin tuttuğu günler kalanlara borç gibi görünürdü.
        val everyone = (persons + data.persons).distinctBy { it.id }
        val byId = everyone.associateBy { it.id }
        val expected = everyone.associate { it.id to DoubleArray(k) }
        val count = everyone.associate { it.id to IntArray(k) }
        val last = everyone.associate { it.id to arrayOfNulls<LocalDate>(k) }

        fun equalShares(date: LocalDate, among: Collection<Person>): List<Pair<String, Double>> {
            val ids = among.filter { availability.eligibleForShare(it, date) }.map { it.id }
            return ids.map { it to 1.0 / ids.size }
        }

        val hardGroups = setOf(RotationGroup.VIZIT, RotationGroup.CUMA, RotationGroup.PAZAR)
        fun senior(p: Person, date: LocalDate) = availability.coefficients.isSenior(p, date)
        // O haftanın cumartesi ya da pazarını tutabilen; uzatmadaki gibi ikisi de kapalıysa zor gün düzenine girmez.
        fun weekendPeer(p: Person, date: LocalDate): Boolean {
            if (!availability.eligibleForShare(p, date)) return false
            val sat = when (date.dayOfWeek) {
                DayOfWeek.FRIDAY -> date.plusDays(1)
                DayOfWeek.SUNDAY -> date.minusDays(1)
                else -> date
            }
            return availability.ruleBlock(p, sat) == null || availability.ruleBlock(p, sat.plusDays(1)) == null
        }
        // Günü tutanın kademesindeki (çömez ya da son yıl) hafta sonu tutabilenler.
        fun tier(holder: Person, date: LocalDate) =
            everyone.filter { weekendPeer(it, date) && senior(it, date) == senior(holder, date) }

        val hardBalance = everyone.associate { it.id to doubleArrayOf(0.0) }
        val hardHeld = everyone.associate { it.id to intArrayOf(0) }
        var d = windowStart
        while (d.isBefore(historyEnd)) {
            val who = roster.official[d]
            val group = if (who != null) cal.rotationGroup(d, history = true) else null
            if (group != null) {
                count[who]?.let { it[group.ordinal]++ }
                last[who]?.set(group.ordinal, d)
                if (group == RotationGroup.VIZIT || group !in hardGroups) {
                    for ((id, share) in equalShares(d, everyone)) expected.getValue(id)[group.ordinal] += share
                }
                if (group in hardGroups) {
                    hardHeld[who]?.let { it[0]++ }
                    val holder = byId[who]
                    if (holder != null && weekendPeer(holder, d)) {
                        val peers = tier(holder, d)
                        for (p in peers) {
                            hardBalance.getValue(p.id)[0] += 1.0 / peers.size
                            if (group != RotationGroup.VIZIT) expected.getValue(p.id)[group.ordinal] += 1.0 / peers.size
                        }
                        hardBalance.getValue(holder.id)[0] -= 1.0
                    }
                }
            }
            d = d.plusDays(1)
        }
        val debt = everyone.associate { p -> p.id to DoubleArray(k) { expected.getValue(p.id)[it] - count.getValue(p.id)[it] } }

        val target = persons.associate { it.id to IntArray(k) }
        val left = persons.associate { it.id to room(it) }.toMutableMap()
        fun daysOf(group: RotationGroup) = planDays.filter { cal.rotationGroup(it, history = false) == group }
        fun give(id: String, group: RotationGroup, wanted: Boolean) {
            if (!wanted) left[id] = left.getValue(id) - 1
            target.getValue(id)[group.ordinal]++
        }

        // Cumartesi, perşembe ve pazartesi: gün gün, sırası gelene.
        fun dayByDay(group: RotationGroup): List<String> {
            val days = daysOf(group)
            val candidates = persons.filter { p -> days.any { availableInPlan(p, it) && availability.eligibleForShare(p, it) } }
            if (days.isEmpty() || candidates.isEmpty()) return emptyList()
            val work = candidates.associate { it.id to debt.getValue(it.id)[group.ordinal] }.toMutableMap()
            val lastWork = candidates.associate { it.id to last.getValue(it.id)[group.ordinal] }.toMutableMap()
            val usedWeekdays = HashMap<String, MutableSet<DayOfWeek>>()
            return days.map { day ->
                for ((id, share) in equalShares(day, candidates)) work[id] = work.getValue(id) + share
                // İstek sıradan önce gelir. Aynı kişiye ayda haftanın aynı gününden ikincisi verilmez;
                // mümkün değilse kural esner.
                val wanting = candidates.filter { wants(it, day) && availableInPlan(it, day) }
                val fresh = candidates.filter { day.dayOfWeek !in usedWeekdays[it.id].orEmpty() }
                val pool = wanting
                    .ifEmpty { fresh.filter { left.getValue(it.id) > 0 && availableInPlan(it, day) && !avoids(it, day) } }
                    .ifEmpty { fresh.filter { availableInPlan(it, day) && !avoids(it, day) } }
                    .ifEmpty { fresh.filter { availableInPlan(it, day) } }
                    .ifEmpty { fresh }
                    .ifEmpty { candidates }
                val byDebt = compareByDescending<Person> { Math.round(work.getValue(it.id) * 2) / 2.0 }
                val order = if (group == RotationGroup.VIZIT) {
                    // Cumartesi önce son yıla girmemişlere: onlar borca göre; son yıldakiler kalanları sırayla
                    // (en uzun süredir tutmayan önce).
                    val juniors = byDebt.thenByDescending<Person> { it.seniority }.thenBy { lastWork[it.id] ?: LocalDate.MIN }
                    val seniors = compareBy<Person> { lastWork[it.id] ?: LocalDate.MIN }.thenByDescending { it.seniority }
                    Comparator<Person> { a, b ->
                        val sa = senior(a, day)
                        val sb = senior(b, day)
                        when {
                            sa != sb -> sa.compareTo(sb)
                            sa -> seniors.compare(a, b)
                            else -> juniors.compare(a, b)
                        }
                    }
                } else {
                    byDebt.thenBy { lastWork[it.id] ?: LocalDate.MIN }.thenByDescending { it.seniority }
                }
                val pick = pool.sortedWith(order).first()
                give(pick.id, group, pick in wanting)
                usedWeekdays.getOrPut(pick.id) { HashSet() } += day.dayOfWeek
                work[pick.id] = work.getValue(pick.id) - 1.0
                lastWork[pick.id] = day
                pick.id
            }
        }

        // Cuma ve pazar birlikte: önce kişi başına kaç zor gün, sonra hangi gün türü, en son hangi tarih.
        fun weekend(): Pair<List<String>, List<String>> {
            val fri = daysOf(RotationGroup.CUMA)
            val sun = daysOf(RotationGroup.PAZAR)
            val hard = daysOf(RotationGroup.VIZIT) + fri + sun
            fun can(p: Person, day: LocalDate) = availableInPlan(p, day) && weekendPeer(p, day)
            val people = persons.filter { p -> hard.any { weekendPeer(p, it) } }
            if (fri.isEmpty() && sun.isEmpty()) return emptyList<String>() to emptyList()
            val mid = planDays[planDays.size / 2]
            val junior = people.associate { it.id to !senior(it, mid) }
            // Pay yalnızca ekipte olmaya bağlı (ayın ortasında başlayan ya da ayrılan az alır). İzin ve istek payı
            // azaltmaz (K22): yalnızca hangi günleri tutabileceğini belirler, sığmayan pay defterde kalır.
            val share = people.associate { p -> p.id to hard.count { weekendPeer(p, it) }.toDouble() / hard.size }
            val held = people.associate { it.id to target.getValue(it.id)[RotationGroup.VIZIT.ordinal] }.toMutableMap()
            val hasSat = people.associate { it.id to (target.getValue(it.id)[RotationGroup.VIZIT.ordinal] > 0) }
            val behind = { p: Person -> Math.round(hardBalance.getValue(p.id)[0] * 2) / 2.0 }
            val lastHard = people.associate { p -> p.id to hardGroups.mapNotNull { last.getValue(p.id)[it.ordinal] }.maxOrNull() }
            // Az tutan önce; eşitse çömez önce (fazla gün çömeze, eksik gün kıdemliden), sonra kademesinde geride olan.
            val order = compareBy<Person> { held.getValue(it.id) / share.getValue(it.id) }
                .thenBy { if (junior.getValue(it.id)) 0 else 1 }
                .thenByDescending { behind(it) }
                .thenBy { lastHard[it.id] ?: LocalDate.MIN }
                .thenByDescending { it.seniority }

            // İstek sıradan önce gelir.
            val assigned = HashMap<LocalDate, String>()
            for (day in fri + sun) {
                val wanting = persons.filter { wants(it, day) && availableInPlan(it, day) }
                val w = wanting.filter { it.id in held }.minWithOrNull(order) ?: wanting.firstOrNull() ?: continue
                assigned[day] = w.id
                if (w.id in held) held[w.id] = held.getValue(w.id) + 1
            }
            val hasF = people.associate { p -> p.id to fri.count { assigned[it] == p.id } }
            val hasS = people.associate { p -> p.id to sun.count { assigned[it] == p.id } }
            val openF = fri.filter { it !in assigned }
            val openS = sun.filter { it !in assigned }

            // 1) Kişi başına kaç zor gün: tek tek, sırası gelene.
            val units = people.associate { it.id to 0 }.toMutableMap()
            fun ideal(p: Person): Int {
                val f = if (hasF.getValue(p.id) == 0 && openF.any { can(p, it) }) 1 else 0
                val s = if (hasS.getValue(p.id) == 0 && openS.any { can(p, it) } && !(hasSat.getValue(p.id) && !junior.getValue(p.id))) 1 else 0
                return if (hasSat.getValue(p.id)) minOf(f + s, maxOf(0, 1 - hasF.getValue(p.id) - hasS.getValue(p.id))) else f + s
            }
            fun most(p: Person) = openF.count { can(p, it) } + openS.count { can(p, it) }
            repeat(openF.size + openS.size) {
                val pick = people.filter { units.getValue(it.id) < ideal(it) && left.getValue(it.id) > units.getValue(it.id) }.minWithOrNull(order)
                    ?: people.filter { units.getValue(it.id) < most(it) && left.getValue(it.id) > units.getValue(it.id) }.minWithOrNull(order)
                    ?: people.filter { units.getValue(it.id) < most(it) }.minWithOrNull(order)
                    ?: return@repeat
                units[pick.id] = units.getValue(pick.id) + 1
                held[pick.id] = held.getValue(pick.id) + 1
            }

            // 2) Gün türü: aynı günden ikincisi, cumartesi tutan kıdemliye pazar en az olacak şekilde (küçük arama).
            val takers = people.filter { units.getValue(it.id) > 0 }
            fun cost(p: Person, f: Int): Double {
                val s = units.getValue(p.id) - f
                val totalF = hasF.getValue(p.id) + f
                val totalS = hasS.getValue(p.id) + s
                var c = 100.0 * (maxOf(0, totalF - 1) + maxOf(0, totalS - 1))
                if (f > openF.count { can(p, it) }) c += 1000.0
                if (s > openS.count { can(p, it) }) c += 1000.0
                if (hasSat.getValue(p.id) && !junior.getValue(p.id) && totalS > 0) c += 30.0
                // Kendi cuma sırası pazardan gerideyse cumayı tercih eder.
                c -= 0.01 * f * (debt.getValue(p.id)[RotationGroup.CUMA.ordinal] - debt.getValue(p.id)[RotationGroup.PAZAR.ordinal])
                return c
            }
            val maxF = openF.size
            val inf = Double.MAX_VALUE / 4
            var best = DoubleArray(maxF + 1) { if (it == 0) 0.0 else inf }
            val choice = ArrayList<IntArray>()
            for (p in takers) {
                val next = DoubleArray(maxF + 1) { inf }
                val pick = IntArray(maxF + 1) { -1 }
                for (used in 0..maxF) {
                    if (best[used] >= inf) continue
                    for (f in 0..minOf(units.getValue(p.id), maxF - used)) {
                        val v = best[used] + cost(p, f)
                        if (v < next[used + f]) { next[used + f] = v; pick[used + f] = f }
                    }
                }
                choice += pick
                best = next
            }
            val totalUnits = takers.sumOf { units.getValue(it.id) }
            // Pazar sayısı aşılmasın: cuma sayısı en az (toplam − açık pazar).
            var endF = (maxOf(0, totalUnits - openS.size)..minOf(maxF, totalUnits)).filter { best[it] < inf }.minByOrNull { best[it] } ?: 0
            val wantF = HashMap<String, Int>()
            for (i in takers.indices.reversed()) {
                val f = choice[i][endF].coerceAtLeast(0)
                wantF[takers[i].id] = f
                endF -= f
            }
            val wantS = takers.associate { it.id to units.getValue(it.id) - wantF.getValue(it.id) }.toMutableMap()

            // 3) Tarih: seçeneği az olan önce, sonra o günün kendi sırası.
            fun place(days: List<LocalDate>, open: List<LocalDate>, want: MutableMap<String, Int>, group: RotationGroup): List<String> {
                val lastWork = persons.associate { it.id to last.getValue(it.id)[group.ordinal] }.toMutableMap()
                return days.map { day ->
                    assigned[day]?.let { id -> give(id, group, wanted = true); return@map id }
                    val rest = open.filter { it >= day && it !in assigned }
                    val designated = people.filter { (want[it.id] ?: 0) > 0 && can(it, day) }
                    val pool = designated.filter { !avoids(it, day) }.ifEmpty { designated }
                    val pick = pool.minWithOrNull(
                        compareBy<Person> { p -> rest.count { can(p, it) } }
                            .thenByDescending { Math.round(debt.getValue(it.id)[group.ordinal] * 2) / 2.0 }
                            .thenBy { lastWork[it.id] ?: LocalDate.MIN }
                            .thenByDescending { it.seniority },
                    ) ?: run {
                        // Sırası gelen kimse o gün olamıyorsa tutabilenlerden en az zor gün tutan.
                        val any = persons.filter { availableInPlan(it, day) && availability.eligibleForShare(it, day) }
                        val pool2 = any.filter { !avoids(it, day) }.ifEmpty { any }.ifEmpty { persons }
                        val p = pool2.filter { it.id in held }.minWithOrNull(order) ?: pool2.maxBy { it.seniority }
                        if (p.id in held) held[p.id] = held.getValue(p.id) + 1
                        p
                    }
                    if (pick.id in want) want[pick.id] = want.getValue(pick.id) - 1
                    give(pick.id, group, wanted = false)
                    lastWork[pick.id] = day
                    pick.id
                }
            }
            return place(fri, openF, wantF, RotationGroup.CUMA) to place(sun, openS, wantS, RotationGroup.PAZAR)
        }

        val queues = arrayOfNulls<List<String>>(k)
        queues[RotationGroup.VIZIT.ordinal] = dayByDay(RotationGroup.VIZIT)
        val (fridays, sundays) = weekend()
        queues[RotationGroup.CUMA.ordinal] = fridays
        queues[RotationGroup.PAZAR.ordinal] = sundays
        queues[RotationGroup.PERSEMBE.ordinal] = dayByDay(RotationGroup.PERSEMBE)
        queues[RotationGroup.PAZARTESI.ordinal] = dayByDay(RotationGroup.PAZARTESI)
        return RotationState(
            debt, count, last, target, queues.map { it.orEmpty() },
            hardDebt = everyone.associate { it.id to hardBalance.getValue(it.id)[0] },
            hardCount = everyone.associate { it.id to hardHeld.getValue(it.id)[0] },
        )
    }

    private class Event(val kind: EventKind, val dates: List<LocalDate>, val days: Map<String, Double>, val present: Set<String>)

    /**
     * Bayram, resmi tatil ve yılbaşı dengesi (§6). Her olayın günleri, o gün ekipte olan asistanlar arasında
     * o günkü zor gün katsayısıyla paylaştırılır; yalnızca asistanların tuttuğu günler paylaşılır (uzman günleri
     * hesaba girmez). Arefe gibi yarım günler yarım sayılır. Planlanan ayın tatilleri de beklentiye eklenir.
     */
    fun longMemory(
        persons: List<Person>,
        historyEnd: LocalDate,
        planDays: List<LocalDate>,
    ): LongMemoryState {
        val cats = Category.LONG_MEMORY
        val k = cats.size
        val start = data.settings.longMemoryStart
        val firstRosterDate = roster.official.keys.minOrNull()
        val coef = availability.coefficients
        // Olayın payı o gün ekipte olan herkes arasında dağılır (sonradan ayrılanlar dahil).
        val everyone = (persons + data.persons).distinctBy { it.id }

        val events = ArrayList<Event>()
        // Tam listesi olan günlerden türeyen olaylar.
        val grouped = LinkedHashMap<Pair<Category, String>, MutableList<LocalDate>>()
        roster.official.keys.filter { !it.isBefore(start) && it.isBefore(historyEnd) }.sorted().forEach { date ->
            val cat = cal.category(date)
            if (cat in cats) grouped.getOrPut(cat to cal.longEventKey(date)) { ArrayList() } += date
        }
        for ((key, dates) in grouped) {
            val days = HashMap<String, Double>()
            for (d in dates) days.merge(roster.official.getValue(d), cal.longWeight(d), Double::plus)
            val present = everyone.filter { p -> days.containsKey(p.id) || dates.any { availability.eligibleForShare(p, it) } }
            events += Event(key.first.eventKind!!, dates, days, present.map { it.id }.toSet())
        }
        // Elle girilmiş eski olaylar (yalnızca tam liste dönemi öncesi).
        for (h in data.historyEvents) {
            if (h.start != null && h.start.isBefore(start)) continue
            if (h.start != null && firstRosterDate != null && !h.start.isBefore(firstRosterDate)) continue
            val days = h.entries.associate { it.personId to it.days.toDouble() }
            val present = everyone.filter { p ->
                (days[p.id] ?: 0.0) > 0.0 ||
                    (h.start?.let { availability.eligibleForShare(p, it) } ?: (availability.presenceStart(p) == null))
            }
            events += Event(h.kind, listOfNotNull(h.start), days, present.map { it.id }.toSet())
        }

        val days = everyone.associate { it.id to DoubleArray(k) }
        val present = everyone.associate { it.id to IntArray(k) }
        val expected = everyone.associate { it.id to DoubleArray(k) }

        fun share(gi: Int, total: Double, among: List<Person>, date: LocalDate?) {
            val weights = among.associate { p -> p.id to (date?.let { coef.on(p, it).hardDay } ?: coef.on(p, historyEnd).hardDay) }
            val sum = weights.values.sum()
            if (sum <= 0.0) return
            for (p in among) {
                present.getValue(p.id)[gi]++
                expected.getValue(p.id)[gi] += total * weights.getValue(p.id) / sum
            }
        }

        for (e in events) {
            val gi = cats.indexOfFirst { it.eventKind == e.kind }
            for ((id, n) in e.days) days[id]?.let { it[gi] += n }
            share(gi, e.days.values.sum(), everyone.filter { it.id in e.present }, e.dates.firstOrNull())
        }

        // Planlanan ayın olayları: günler henüz atanmadı ama beklentiye girer.
        planDays.mapNotNull { d ->
            val gi = cats.indexOf(cal.category(d))
            if (gi < 0) null else Triple(gi, cal.longEventKey(d), d)
        }.groupBy { it.first to it.second }.forEach { (key, items) ->
            val dates = items.map { it.third }
            val among = persons.filter { p -> dates.any { availability.eligibleForShare(p, it) } }
            share(key.first, dates.sumOf { cal.longWeight(it) }, among, dates.first())
        }

        val base = everyone.associate { p ->
            p.id to DoubleArray(k) { days.getValue(p.id)[it] - expected.getValue(p.id)[it] }
        }
        return LongMemoryState(cats, base, days, present, expected)
    }
}
