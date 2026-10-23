package com.nobetmaker.app

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nobetmaker.core.model.Period
import com.nobetmaker.core.model.PeriodType
import com.nobetmaker.core.ops.DataOps
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** Liste üretimi ekranı: seçenekler, takvimi göster/gizle, farklı seçenekler, çözüm yoksa. */
class GenerateButtonsTest : AppUiTest() {

    private val kasim = YearMonth.of(2026, 11)

    @Test
    fun seceneklerVeIkinciSecenegiKullan() {
        goToMonth(kasim)
        click("Liste üret")
        waitForText("Seçenek 1", 120_000)
        assertTrue(exists("Seçenek 2", substring = true))

        click("Takvimi gizle")
        assertTrue(!exists("Takvimi gizle"))
        click("Takvimi göster")
        assertTrue(exists("Takvimi gizle"))

        click("Farklı seçenekler üret")
        waitForText("Kurallara uygun listeler aranıyor", 10_000)
        waitForText("Seçenek 1", 120_000)

        rule.onAllNodesWithText("Bunu kullan")[1].apply { performScrollTo(); performClick() }
        waitForData(what = "ikinci seçeneği kaydetme") { it.month(kasim)?.current?.note?.contains("seçenek 2") == true }
        waitForText("Taslak")
    }

    @Test
    fun cozumYoksaHazirlikEkraninaDon() {
        // 15 Kasım'da herkes izinde: hiçbir liste kurulamaz.
        update { d ->
            d.persons.fold(DataOps.ensureMonth(d, kasim)) { acc, p ->
                DataOps.upsertPeriod(acc, Period("t-${p.id}", p.id, kasim.atDay(15), kasim.atDay(15), PeriodType.YILLIK_IZIN))
            }
        }
        goToMonth(kasim)
        click("Liste üret")
        waitForText("Ayı hazırla ekranına dön", 120_000)
        assertTrue("sebep yazmalı", exists("uygun kimse yok", substring = true))
        shot("t-cozum-yok")
        click("Ayı hazırla ekranına dön")
        waitForText("Kasım 2026 hazırlığı")
    }
}
