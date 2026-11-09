package com.nobetmaker.core.rules

import com.nobetmaker.core.model.AppData
import java.time.LocalDate

/** Bütün ayların resmi listesi ve gayriresmi değişimlerle oluşan "gerçekte tutulan" liste (§9). */
class RosterIndex(data: AppData) {

    val official: Map<LocalDate, String> =
        data.months.flatMap { it.current?.assignments.orEmpty() }.associate { it.date to it.personId }

    val actual: Map<LocalDate, String>

    init {
        val map = official.toMutableMap()
        data.informalChanges.sortedBy { it.createdAt }.forEach { map[it.date] = it.actualPersonId }
        actual = map
    }

    fun officialDutiesOf(personId: String): List<LocalDate> = official.filterValues { it == personId }.keys.sorted()

    fun actualDutiesOf(personId: String): List<LocalDate> = actual.filterValues { it == personId }.keys.sorted()
}
