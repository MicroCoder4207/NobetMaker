package com.nobetmaker.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import com.nobetmaker.core.model.Period
import com.nobetmaker.core.model.PeriodType
import com.nobetmaker.core.model.Settings
import com.nobetmaker.core.ops.AppJson
import com.nobetmaker.core.ops.DataOps
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Shadows.shadowOf

/** Ayarlar: bütün sayaçlar, başlık, varsayılana dönüş, yedekler, değişiklik geçmişi, başlangıç verisi. */
class SettingsButtonsTest : AppUiTest() {

    private val steppers = listOf(
        "İki nöbet arası en az gün", "Aylık en fazla nöbet", "Sıra hafızası (ay)",
        "Temel", "Nöbet günü tatil", "Vizit", "Ertesi gün tatil", "Uzun hafta sonu indirimi",
        "İş günü nöbeti", "Tatil günü nöbeti", "Arefe nöbeti", "Nöbet ertesi düşüm",
        "Uzun tatil koruması", "\"Mümkünse\" istek", "\"Önemsiz\" istek", "Ayda 2. Cuma/Cumartesi/Pazar",
        "Vizit/Cuma/Pazar/Perşembe sırası", "Pazartesi sırası (Salı dersi)", "Uzun hafıza dengesi",
        "3 gün aralık", "4 gün aralık", "Pzt–Per aynı güne 2. kez", "Aynı ikili sıranın tekrarı", "Aynı bayramda 2. gün",
    )

    @Test
    fun butunSayaclar() {
        click("Ayarlar")
        waitForText("Kurallar")
        for (label in steppers) {
            val before = data().settings
            // Üst sınırdaki sayaçta "+" kapalıdır; önce "−" denenir.
            val atMax = rule.onNode(hasText("+") and hasClickAction() and hasAnySibling(hasText(label))).fetchSemanticsNode()
                .config.contains(SemanticsProperties.Disabled)
            stepper(label, plus = !atMax)
            waitForData(what = "$label ${if (atMax) "−" else "+"}") { it.settings != before }
            stepper(label, plus = atMax)
            waitForData(what = "$label geri") { it.settings == before }
        }
    }

    @Test
    fun baslikVarsayilanVeUzunHafiza() {
        click("Ayarlar")
        type("Excel/görsel başlığı", "Mikrobiyoloji Nöbetleri", clear = true)
        click("Başlığı kaydet")
        waitForData(what = "başlık") { it.settings.excelTitle == "Mikrobiyoloji Nöbetleri" }

        stepper("Uzun tatil koruması", plus = true)
        stepper("Vizit", plus = true)
        stepper("Arefe nöbeti", plus = true)
        waitForData { it.settings.penalties != Settings().penalties }
        click("Puan, saat ve cezaları varsayılana döndür")
        waitForData(what = "varsayılana dönüş") { s -> s.settings.let { it.penalties == Settings().penalties && it.points == Settings().points && it.hours == Settings().hours } }

        click("Uzun hafıza başlangıcı", substring = true)
        pickDay(15, "Ocak")
        waitForData(what = "uzun hafıza başlangıcı") { it.settings.longMemoryStart == LocalDate.of(2023, 1, 15) }
    }

    @Test
    fun yedeklerGecmisVeBaslangicVerisi() {
        click("Ayarlar")
        // İlk değişiklik otomatik yedek alır.
        update { DataOps.upsertPeriod(it, Period("t1", "oguz", LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 7), PeriodType.YILLIK_IZIN)) }
        assertTrue(app.repository.backups().isNotEmpty())

        click("Değişiklik geçmişi")
        waitForText("Kapat")
        clickInDialog("Kapat")

        click("Otomatik yedekler")
        val label = app.repository.backups().first().name.removePrefix("yedek-").removeSuffix(".json")
        clickInDialog(label)
        waitForText("geri yüklensin mi?")
        clickInDialog("Evet")
        waitForData(what = "yedekten dönme") { it.periods.none { p -> p.id == "t1" } }
        waitSnackbarGone()

        update { it.copy(settings = it.settings.copy(maxPerMonth = 4)) }
        click("Başlangıç verisine dön")
        clickInDialog("Vazgeç")
        assertEquals(4, data().settings.maxPerMonth)
        click("Başlangıç verisine dön")
        clickInDialog("Evet")
        waitForData(what = "başlangıç verisi") { it.settings.maxPerMonth == Settings().maxPerMonth }
    }

    @Test
    fun yedegiDisaAktarVeGeriYukle() {
        click("Ayarlar")
        val file = File(rule.activity.cacheDir, "test-yedek.json").apply { delete() }

        click("Yedeği dışa aktar (Drive'a, e-postaya…)")
        val save = shadowOf(rule.activity).nextStartedActivityForResult
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, save.intent.action)
        rule.runOnIdle { shadowOf(rule.activity).receiveResult(save.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))) }
        rule.waitUntil(10_000) { file.length() > 0 }
        val exported = AppJson.decode(file.readText())
        assertEquals(data().persons, exported.persons)

        update { DataOps.upsertPeriod(it, Period("t2", "asli", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3), PeriodType.RAPOR)) }
        waitSnackbarGone()
        click("Yedekten geri yükle")
        val open = shadowOf(rule.activity).nextStartedActivityForResult
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, open.intent.action)
        rule.runOnIdle { shadowOf(rule.activity).receiveResult(open.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))) }
        waitForText("Yedek yüklensin mi?")
        clickInDialog("Evet")
        waitForData(what = "yedekten geri yükleme") { it.periods.none { p -> p.id == "t2" } }
    }
}
