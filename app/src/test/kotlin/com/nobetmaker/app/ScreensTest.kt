package com.nobetmaker.app

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nobetmaker.app.export.Exporter
import com.nobetmaker.core.model.DutyRequest
import com.nobetmaker.core.model.Priority
import com.nobetmaker.core.model.RequestType
import com.nobetmaker.core.ops.DataOps
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import java.io.File
import java.time.YearMonth

/** Ana akış, tablet görünümü ve dışa aktarma; ekran görüntüleri app/build/screens altına yazılır. */
class ScreensTest : AppUiTest() {

    @Test
    fun anaAkis() {
        waitForText("Ekim 2026")
        shot("01-ekim-resmi")
        click("Gerçekleşen")
        shot("02-ekim-gerceklesen")
        click("Resmi")

        rule.onNodeWithContentDescription("Sonraki ay").performClick()
        waitForText("Kasım 2026")
        shot("03-kasim-bos")

        click("Ayı hazırla")
        waitForText("Kasım 2026 hazırlığı")
        shot("04-hazirla-donemler")
        click("İstekler")
        shot("05-hazirla-istekler")
        click("Nöbet sayıları")
        waitForText("Toplam")
        shot("06-hazirla-kota")
        click("Kontrol")
        waitForText("Sorun görünmüyor")
        shot("07-hazirla-kontrol")

        click("Liste üret")
        waitForText("Seçenek 1", 120_000)
        shot("08-secenekler")
        click("Bunu kullan")
        waitForText("Taslak")
        shot("09-kasim-taslak")

        // Liste üretildikten sonra girilen kesin istek, yeniden üretimde uygulanmalı (önceki sonuç kullanılmamalı).
        val kasim = YearMonth.of(2026, 11)
        val range = kasim.atDay(4)..kasim.atDay(7)
        rule.activityRule.scenario.onActivity { activity ->
            (activity.application as NobetApp).repository.update {
                DataOps.upsertRequest(
                    it,
                    DutyRequest("test-oguz", "oguz", RequestType.AVOID_RANGE, Priority.KESIN, rangeStart = range.start, rangeEnd = range.endInclusive),
                )
            }
        }
        // "Liste kaydedildi" bildirimi düğmenin üstünü kapatıyor; kapanmasını bekle.
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Liste kaydedildi").fetchSemanticsNodes().isEmpty() }
        click("Liste üret")
        waitForText("Seçenek 1", 120_000)
        click("Bunu kullan")
        waitForText("Taslak")
        rule.activityRule.scenario.onActivity { activity ->
            val plan = (activity.application as NobetApp).repository.data.value.month(kasim)!!
            val oguz = plan.current!!.assignments.filter { it.personId == "oguz" }.map { it.date }
            assertTrue("Oğuz kesin isteğe rağmen yazıldı: $oguz", oguz.none { it in range })
        }

        rule.onNodeWithText("13").performClick()
        waitForText("Kim tutabilir?")
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Yazılı", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        shot("10-gun-detay")
        back()

        click("Kişiler")
        waitForText("Kıdem sırası")
        shot("11-kisiler")
        click("İstatistik")
        waitForText("resmi listeye göre", 30_000)
        shot("12-istatistik")
        click("Ayarlar")
        shot("13-ayarlar")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-hdpi")
    fun tablet() {
        waitForText("Ekim 2026")
        shot("t1-tablet-takvim")
        rule.onNodeWithContentDescription("Sonraki ay").performClick()
        waitForText("Kasım 2026")
        click("Ayı hazırla")
        waitForText("Kasım 2026 hazırlığı")
        click("Nöbet sayıları")
        waitForText("Toplam")
        shot("t2-tablet-nobet-sayilari")
    }

    @Test
    fun disaAktarma() {
        waitForText("Ekim 2026")
        rule.activityRule.scenario.onActivity { activity ->
            val data = (activity.application as NobetApp).repository.data.value
            val month = YearMonth.of(2026, 10)
            Exporter.shareExcel(activity, data, month, actual = false)
            Exporter.shareImage(activity, data, month, actual = false)
            // PdfDocument'in yerel kodu Robolectric'te yok; PDF gerçek cihazda üretilir.
            runCatching { Exporter.sharePdf(activity, data, month, actual = false) }
            val dir = File(activity.cacheDir, "exports")
            val files = dir.listFiles().orEmpty().map { it.name }
            println("dışa aktarılan: $files")
            assertTrue(files.any { it.endsWith(".xlsx") })
            assertTrue(files.any { it.endsWith(".png") })
            dir.listFiles()?.forEach { it.copyTo(File(out, "export-" + it.name), overwrite = true) }
        }
    }
}
