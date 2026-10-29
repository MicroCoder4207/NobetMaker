package com.nobetmaker.app

import android.app.Activity
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import com.nobetmaker.core.model.MonthStatus
import com.nobetmaker.core.model.Visit
import com.nobetmaker.core.ops.DataOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/** Takvim ekranı, gün ayrıntısı, iki günü değiştirme, ay durumu ve paylaşım düğmeleri. */
class CalendarButtonsTest : AppUiTest() {

    private val kasim = YearMonth.of(2026, 11)
    private val ekim = YearMonth.of(2026, 10)

    private fun official(date: LocalDate) = data().month(YearMonth.from(date))?.current?.personOn(date)
    private fun name(id: String?) = data().person(id ?: "")?.name

    /** Yazılabilir ilk aday ("Uygun" yoksa "Uyarılı"). */
    private fun clickCandidate() {
        rule.waitUntil(30_000) { exists("Uygun") || exists("Uyarılı") }
        click(if (exists("Uygun")) "Uygun" else "Uyarılı")
    }

    @Test
    fun ayDegistirmeVeResmiGerceklesen() {
        waitForText("Ekim 2026")
        clickDesc("Önceki ay")
        waitForText("Eylül 2026")
        clickDesc("Sonraki ay")
        clickDesc("Sonraki ay")
        waitForText("Kasım 2026")

        // Gayriresmi değişimi olan bir ay: resmi görünümde "(gerçekte tutan)", gerçekleşende "(resmi kişi)" yazar.
        val change = data().informalChanges.first()
        goToMonth(YearMonth.from(change.date))
        val actual = name(change.actualPersonId)!!
        val off = name(change.officialPersonId)!!
        assertTrue("Resmi görünümde ($actual) görünmeli", exists("($actual)"))
        click("Gerçekleşen")
        assertTrue("Gerçekleşen görünümde ($off) görünmeli", exists("($off)"))
        click("Resmi")
        assertTrue(exists("($actual)"))
    }

    @Test
    fun taslakAydaGunIslemleri() {
        draftMonth(kasim)
        goToMonth(kasim)
        val day = kasim.atDay(10)

        click("10")
        waitForText("Kim tutabilir?")
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Yazılı").fetchSemanticsNodes().isNotEmpty() }

        // Vizit işareti.
        click("Var")
        waitForData(what = "vizit işareti") { d -> d.dayOverrides.any { it.date == day && it.plannedVisit == Visit.VAR } }
        click("Yok")
        waitForData(what = "vizit kaldırma") { d -> d.dayOverrides.any { it.date == day && it.plannedVisit == Visit.YOK } }
        click("Belirsiz")
        waitForData(what = "vizit belirsiz") { d -> d.dayOverrides.any { it.date == day && it.plannedVisit == Visit.BELIRSIZ } }

        // Kilit.
        rule.onNode(isToggleable() and hasText("Kilitle", substring = true)).performClick()
        waitForData(what = "kilit") { d -> d.month(kasim)!!.current!!.assignments.first { it.date == day }.locked }

        // Uygun bir adayı yazma.
        val before = official(day)
        clickCandidate()
        waitForData(what = "elle atama") { official(day) != before }
        waitSnackbarGone()

        // Gün ayrıntısından "Başka günle takasla".
        click("10")
        waitForText("Kim tutabilir?")
        val a = official(day)
        val b = official(kasim.atDay(14))
        click("Başka günle takasla")
        waitForText("ile takaslanacak günü seçin")
        click("14")
        waitForData(what = "iki gün değişimi") { official(day) == b && official(kasim.atDay(14)) == a }
    }

    @Test
    fun taslakAydaKuralaAykiriYineDeYaz() {
        draftMonth(kasim)
        goToMonth(kasim)
        val day = kasim.atDay(7) // Cumartesi: Yaren'e kural gereği yazılmaz
        val before = official(day)
        click("7")
        waitForText("Kim tutabilir?")
        rule.waitUntil(30_000) { exists("Uygun değil") }
        click("Uygun değil")
        waitForText("Kurala aykırı")
        click("Vazgeç")
        assertEquals(before, official(day))
        click("Uygun değil")
        waitForText("Kurala aykırı")
        click("Yine de yaz")
        waitForData(what = "kural dışı atama") { official(day) != before }
    }

    @Test
    fun kapanmisAydaDuzeltmeUzmanVeGunTuru() {
        val eylul = YearMonth.of(2026, 9)
        val day = eylul.atDay(15)
        assertEquals(MonthStatus.KAPANDI, data().month(eylul)!!.status)
        val versions = data().month(eylul)!!.versions.size
        val before = official(day)
        goToMonth(eylul)
        click("15")
        waitForText("Ay kapandı; yine de düzeltebilirsiniz")
        clickCandidate()
        waitForText("Ay kapandı.")
        click("Resmi kayıt")
        waitForData(what = "kapanmış ayda düzeltme") { d ->
            d.month(eylul)!!.versions.size == versions + 1 && d.month(eylul)!!.status == MonthStatus.KAPANDI
        }
        assertNotEquals(before, official(day))
        waitSnackbarGone()

        // Uzman tuttu: resmi kayıtta asistan kalmaz.
        click("Uzman tuttu (asistan yok)")
        waitForText("Asistanların bayram ve sıra hesabından düşer")
        click("Düzelt")
        waitForData(what = "uzman") { official(day) == null }
        waitSnackbarGone()

        // Gün türü elle değişir.
        click("Gün türü: Normal")
        pickFromMenu("Resmi tatil")
        waitForData(what = "gün türü") { d -> d.dayOverrides.any { it.date == day && it.kind == com.nobetmaker.core.model.DayKind.RESMI_TATIL } }
        shot("t-kapanmis-ay-duzeltme")
    }

    @Test
    fun ikiGunuDegistirVeVazgec() {
        draftMonth(kasim)
        goToMonth(kasim)
        val d3 = kasim.atDay(3)
        val d6 = kasim.atDay(6)
        val a = official(d3)
        val b = official(d6)

        click("İki günü takasla")
        waitForText("Takaslanacak ilk günü seçin")
        click("Vazgeç")
        assertTrue(!exists("Takaslanacak ilk günü seçin"))

        click("İki günü takasla")
        click("3")
        waitForText("ile takaslanacak günü seçin")
        click("6")
        waitForData(what = "iki günü değiştir") { official(d3) == b && official(d6) == a }
    }

    @Test
    fun kalanGunleriYenidenUret() {
        // Yayınlanmış Ekim: bugüne kadarki günler kilitlenir, kalanlar yeniden üretilir (yeni sürüm).
        update { d ->
            val plan = d.month(ekim)!!
            val cur = plan.current!!
            DataOps.setStatus(d, ekim, MonthStatus.YAYINLANDI).let { x ->
                x.copy(months = x.months.map { m -> if (m.year == ekim.year && m.month == ekim.monthValue) m.copy(versions = m.versions.dropLast(1) + cur.copy(assignments = cur.assignments.map { it.copy(locked = false) })) else m })
            }
        }
        goToMonth(ekim)
        click("Kalan günleri yeniden oluştur")
        click("Vazgeç")
        assertTrue(data().month(ekim)!!.current!!.assignments.none { it.locked })
        click("Kalan günleri yeniden oluştur")
        click("Evet")
        val until = minOf(LocalDate.now(), ekim.atEndOfMonth())
        waitForData(what = "geçmiş günleri kilitleme") { d ->
            d.month(ekim)!!.current!!.assignments.all { it.locked == !it.date.isAfter(until) }
        }
        waitForText("Ekim 2026 seçenekleri")
    }

    @Test
    fun yayinlaKapatYenidenAc() {
        draftMonth(kasim)
        goToMonth(kasim)
        fun status() = data().month(kasim)!!.status

        click("Listeyi kesinleştir")
        click("Vazgeç")
        assertEquals(MonthStatus.TASLAK, status())

        clickAndConfirm("Listeyi kesinleştir")
        waitForData(what = "yayınlama") { status() == MonthStatus.YAYINLANDI }
        waitSnackbarGone()

        // Yanlışlıkla kesinleştirildiyse taslağa geri alınır; kesinleşmiş hali sürümlerde kalır.
        val versions = data().month(kasim)!!.versions.size
        clickAndConfirm("Taslağa geri al")
        waitForData(what = "taslağa geri alma") { status() == MonthStatus.TASLAK && it.month(kasim)!!.versions.size == versions + 1 }
        waitSnackbarGone()
        clickAndConfirm("Listeyi kesinleştir")
        waitForData(what = "yeniden kesinleştirme") { status() == MonthStatus.YAYINLANDI }
        waitSnackbarGone()

        clickAndConfirm("Listeyi kapat")
        waitForData(what = "ayı kapatma") { status() == MonthStatus.KAPANDI }
        waitSnackbarGone()

        clickAndConfirm("Listeyi yeniden aç")
        waitForData(what = "yeniden açma") { status() == MonthStatus.YAYINLANDI }
    }

    @Test
    fun sayilarDegisinceListeYenidenOlusturulur() {
        draftMonth(kasim)
        // Hoca kararı: Yaren bu ay 1; liste eski sayılarla kaldı.
        update { DataOps.setQuota(it, kasim, "yaren", 1) }
        goToMonth(kasim)
        waitForText("Liste nöbet sayılarıyla uyuşmuyor")
        assertTrue(exists("(1 olmalı)", substring = true))
        shot("t-sayi-uyusmazligi")
        click("Listeyi yeni sayılarla oluştur")
        waitForText("Seçenek 1", 120_000)
        click("Bu listeyi kullan")
        waitForData(what = "yeni sayılarla liste") { d -> d.month(kasim)!!.current!!.assignments.count { it.personId == "yaren" } == 1 }
    }

    @Test
    fun yayinlanmisAydaGayriresmiVeResmiDegisiklik() {
        goToMonth(ekim)
        val day = ekim.atDay(20)
        val off = official(day)
        val versions = data().month(ekim)!!.versions.size

        click("20")
        waitForText("Kim tutabilir?")
        clickCandidate()
        waitForText("Liste kesinleşti")
        click("Gerçekte tutan")
        waitForData(what = "gayriresmi değişim") { d -> d.informalChanges.any { it.date == day && it.officialPersonId == off } }
        assertEquals("resmi liste değişmemeli", off, official(day))
        waitSnackbarGone()

        // Aynı günü aç: "Resmi kişiye döndür".
        click("20")
        waitForText("Gerçekte tutan:")
        click("Resmi kişiye döndür")
        waitForData(what = "resmiye döndürme") { d -> d.informalChanges.none { it.date == day } }
        waitSnackbarGone()

        // Resmi değişiklik yeni sürüm açar.
        click("20")
        waitForText("Kim tutabilir?")
        clickCandidate()
        waitForText("Liste kesinleşti")
        click("Resmi kayıt")
        waitForData(what = "resmi değişiklik") { d -> d.month(ekim)!!.versions.size == versions + 1 }
        assertNotEquals(off, official(day))
    }

    @Test
    fun degisimAsistani() {
        goToMonth(ekim)
        val changes = data().informalChanges.size
        click("13")
        waitForText("Değişim asistanı")
        click("Değişim asistanı")
        rule.waitUntil(30_000) { exists("Uygula") || exists("Seçenek bulunamadı") }
        shot("t-degisim-asistani")
        assertTrue("değişim asistanı seçenek bulmalı", exists("Uygula"))
        click("Uygula")
        waitForData(what = "değişim asistanı") { it.informalChanges.size > changes }
    }

    @Test
    fun paylasDugmeleri() {
        goToMonth(ekim)
        val dir = File(rule.activity.applicationContext.cacheDir, "exports")
        dir.deleteRecursively()
        val buttons = listOf(
            "Excel – resmi liste (hocaya)" to "Nobet_Listesi_2026_10_Ekim.xlsx",
            "Görsel – resmi liste (WhatsApp)" to "Nobet_Listesi_2026_10_Ekim.png",
            "Görsel – gerçekleşen" to "Nobet_Listesi_Gerceklesen_2026_10_Ekim.png",
            "PDF – resmi liste (çıktı için)" to null, // PdfDocument'in yerel kodu Robolectric'te yok
            "Excel – gerçekleşen" to "Nobet_Listesi_Gerceklesen_2026_10_Ekim.xlsx",
        )
        for ((label, file) in buttons) {
            click("Paylaş")
            waitForText(label)
            click(label)
            if (file != null) {
                assertTrue("$label dosyası oluşmalı", File(dir, file).isFile)
                // Paylaşım penceresi dosyayla açılmalı.
                val chooser = org.robolectric.Shadows.shadowOf(rule.activity).nextStartedActivity
                    ?: org.robolectric.Shadows.shadowOf(rule.activity.application).nextStartedActivity
                assertEquals("$label paylaşım penceresi", android.content.Intent.ACTION_CHOOSER, chooser?.action)
                @Suppress("DEPRECATION")
                val send = chooser!!.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!
                @Suppress("DEPRECATION")
                assertTrue("$label dosya eki", send.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM).toString().endsWith(file))
            }
            waitSnackbarGone()
        }
    }
}
