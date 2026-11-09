package com.nobetmaker.core

import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.model.CoefficientPeriod
import com.nobetmaker.core.model.Person
import com.nobetmaker.core.model.PersonRule
import com.nobetmaker.core.model.RuleType
import java.time.LocalDate

object TestTeam {
    fun persons(): List<Person> = listOf(
        Person(
            "yaren", "Yaren", 1, "#E91E63", coefficients = listOf(CoefficientPeriod(duty = 0.5, hardDay = 0.5)),
            rules = listOf(RuleType.NO_SATURDAY, RuleType.NO_SUNDAY, RuleType.NO_HOLIDAY).map { PersonRule(it, from = LocalDate.of(2026, 7, 6)) },
        ),
        Person("kenan", "Kenan", 2, "#2196F3", coefficients = listOf(CoefficientPeriod(duty = 0.8, hardDay = 0.65))),
        Person("fatma", "Fatma", 3, "#FF9800", coefficients = listOf(CoefficientPeriod(duty = 0.9, hardDay = 0.7))),
        Person("betul", "Betül", 4, "#4CAF50", coefficients = listOf(CoefficientPeriod(duty = 0.97, hardDay = 0.94))),
        Person("sule", "Şule", 5, "#FFC107", coefficients = listOf(CoefficientPeriod(duty = 0.98, hardDay = 0.96))),
        Person("oguz", "Oğuz", 6, "#9C27B0", coefficients = listOf(CoefficientPeriod(duty = 0.99, hardDay = 0.98))),
        Person("asli", "Aslı", 7, "#00BCD4", joinDate = LocalDate.of(2026, 8, 1), coefficients = listOf(CoefficientPeriod(duty = 1.0, hardDay = 1.0))),
    )

    fun data(): AppData = AppData(persons = persons())
}
