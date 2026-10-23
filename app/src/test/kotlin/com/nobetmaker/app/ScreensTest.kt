package com.nobetmaker.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nobetmaker.app.export.Exporter
import com.nobetmaker.core.model.DutyRequest
import com.nobetmaker.core.model.Priority
import com.nobetmaker.core.model.RequestType
import com.nobetmaker.core.ops.DataOps
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.YearMonth

/**
 * Uygulamayı JVM üzerinde (Robolectric) açar, ana akışı dolaşır ve ekran görüntüleri alır.
 * Görüntüler app/build/screens altına yazılır.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h880dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreensTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val out = File("build/screens").apply { mkdirs() }

    /** Pencerenin bütün görünümlerini (alt sayfalar ve diyaloglar dahil) yazılım tuvaline çizer. */
    private fun shot(name: String) {
        rule.waitForIdle()
        rule.activityRule.scenario.onActivity { activity ->
            val root = activity.window.decorView.rootView
            val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            root.draw(canvas)
            // Alt sayfa (ModalBottomSheet) ve diyaloglar ayrı pencerelerdedir.
            WindowManagerGlobalHelper.otherRoots(root).forEach { v ->
                val loc = IntArray(2)
                v.getLocationOnScreen(loc)
                canvas.save()
                canvas.translate(loc[0].toFloat(), loc[1].toFloat())
                v.draw(canvas)
                canvas.restore()
            }
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun waitForText(text: String, timeout: Long = 60_000) {
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun click(text: String) {
        rule.onAllNodesWithText(text).onFirst().performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun clickNoScroll(text: String) {
        rule.onAllNodesWithText(text).onFirst().performClick()
        rule.waitForIdle()
    }

    private fun back() {
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    @Test
    fun anaAkis() {
        waitForText("Ekim 2026")
        shot("01-ekim-resmi")
        clickNoScroll("Gerçekleşen")
        shot("02-ekim-gerceklesen")
        clickNoScroll("Resmi")

        rule.onNodeWithContentDescription("Sonraki ay").performClick()
        waitForText("Kasım 2026")
        shot("03-kasim-bos")

        click("Ayı hazırla")
        waitForText("Kasım 2026 hazırlığı")
        shot("04-hazirla-donemler")
        clickNoScroll("İstekler")
        shot("05-hazirla-istekler")
        clickNoScroll("Kota")
        waitForText("Toplam")
        shot("06-hazirla-kota")
        clickNoScroll("Kontrol")
        waitForText("Sorun görünmüyor")
        shot("07-hazirla-kontrol")

        clickNoScroll("Liste üret")
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

        clickNoScroll("Kişiler")
        waitForText("Kıdem sırası")
        shot("11-kisiler")
        clickNoScroll("İstatistik")
        waitForText("resmi listeye göre", 30_000)
        shot("12-istatistik")
        clickNoScroll("Ayarlar")
        shot("13-ayarlar")
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

/** Robolectric'te açık olan diğer pencere köklerini (diyalog, alt sayfa) bulur. */
private object WindowManagerGlobalHelper {
    @Suppress("UNCHECKED_CAST")
    fun otherRoots(main: View): List<View> = runCatching {
        val cls = Class.forName("android.view.WindowManagerGlobal")
        val instance = cls.getMethod("getInstance").invoke(null)
        val field = cls.getDeclaredField("mViews").apply { isAccessible = true }
        (field.get(instance) as List<View>).filter { it !== main && it.isShown && it.width > 0 }
    }.getOrDefault(emptyList())
}
