package com.nobetmaker.core

import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.seed.SeedData
import java.time.YearMonth

/** Kasım 2026'dan önceki başlangıç verisi: Kasım'ı boş ay olarak planlayan testler için (0.4.7'den beri Kasım kesinleşmiş gelir). */
fun seedBeforeKasim(): AppData = SeedData.build().let { d -> d.copy(months = d.months.filter { it.yearMonth < YearMonth.of(2026, 11) }) }
