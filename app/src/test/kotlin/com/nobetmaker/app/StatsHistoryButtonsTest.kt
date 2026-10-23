package com.nobetmaker.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import com.nobetmaker.core.model.DayKind
import com.nobetmaker.core.model.EventKind
import com.nobetmaker.core.model.MonthStatus
import com.nobetmaker.core.model.Visit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** İstatistik aralıkları, bayram ve tatil kayıtları ve liste öncesi toplu kayıtlar. */
class StatsHistoryButtonsTest : AppUiTest() {

    @Test
    fun istatistikAraliklari() {
        click("İstatistik")
        waitForText("Mayıs 2026 – Ekim 2026")
        click("Son 12 ay")
        waitForText("Kasım 2025 – Ekim 2026")
        click("Son 6 ay")
        waitForText("Mayıs 2026 – Ekim 2026")
        click("Tümü")
        waitForText("Nisan 2024 – Ekim 2026")
        click("Takvim")
        waitForText("Gerçekleşen")
    }

    @Test
    fun gecmisOzelGunEkleDuzenleSil() {
        val today = LocalDate.now()
        val before = data().historyEvents.size
        click("İstatistik")
        click("Bayram ve tatil kayıtları (kim tuttu, düzelt)")
        waitForText("Liste öncesi toplu kayıtlar")
        click("Liste öncesi toplu kayıtlar")
        waitForText("Kayıt ekle")
        click("Kayıt ekle")
        typeInDialog("Ad (ör. 2024 Kurban Bayramı)", "2023 Cumhuriyet Bayramı")
        clickInDialog("Tür: Bayram")
        pickFromMenu("Resmi tatil")
        clickInDialog("Başlangıç")
        pickDay(10, com.nobetmaker.core.calendar.monthNameTr(today.monthValue))
        stepper("Toplam gün", plus = true, inDialog = true)
        stepper("Kenan", plus = true, inDialog = true)
        stepper("Kenan", plus = true, inDialog = true)
        stepper("Aslı", plus = true, inDialog = true)
        shot("t-gecmis-ekle")
        clickInDialog("Kaydet")
        waitForData(what = "geçmiş kayıt") { d -> d.historyEvents.size == before + 1 }
        val e = data().historyEvents.first { it.name == "2023 Cumhuriyet Bayramı" }
        assertEquals(EventKind.RESMI_TATIL, e.kind)
        assertEquals(today.withDayOfMonth(10), e.start)
        assertEquals(5, e.totalDays)
        assertEquals(mapOf("kenan" to 2, "asli" to 1), e.entries.associate { it.personId to it.days })

        iconNextTo("2023 Cumhuriyet Bayramı", "Düzenle")
        stepper("Kenan", plus = false, inDialog = true)
        clickInDialog("Kaydet")
        waitForData(what = "geçmiş düzenleme") { d -> d.historyEvents.first { it.name == "2023 Cumhuriyet Bayramı" }.entries.first { it.personId == "kenan" }.days == 1 }

        iconNextTo("2023 Cumhuriyet Bayramı", "Sil")
        waitForData(what = "geçmiş silme") { it.historyEvents.size == before }
        clickDesc("Geri")
        waitForText("Denge (bugünkü ekip)")
        clickDesc("Geri")
        waitForText("resmi listeye göre")
    }

    @Test
    fun bayramKayitlariGunGunDuzeltilir() {
        val may30 = LocalDate.of(2026, 5, 30) // Kurban Bayramı 2026, Fatma
        val ym = YearMonth.from(may30)
        fun official() = data().month(ym)!!.current!!.personOn(may30)
        assertEquals("fatma", official())
        click("İstatistik")
        click("Bayram ve tatil kayıtları (kim tuttu, düzelt)")
        waitForText("Denge (bugünkü ekip)")
        assertTrue(exists("Kurban Bayramı 2026 · 9 gün"))
        shot("t-bayram-denge")
        // Süzgeç çipleri (aynı metin denge tablosunun başlığında da var).
        rule.onNode(hasText("Bayram") and hasClickAction()).performClick()
        rule.waitForIdle()
        assertTrue(!exists("Cumhuriyet Bayramı 2026", substring = true))
        rule.onNode(hasText("Tümü") and hasClickAction()).performClick()
        rule.waitForIdle()
        assertTrue(exists("Cumhuriyet Bayramı 2026", substring = true))
        click("30 Mayıs Cumartesi")
        waitForText("30 Mayıs 2026 Cumartesi")

        // Resmi kayıt: kapanmış ayda yeni sürüm, ay kapalı kalır.
        clickInDialog("Tutan: Fatma")
        pickFromMenu("Kenan")
        waitForData(what = "resmi düzeltme") { official() == "kenan" && it.month(ym)!!.status == MonthStatus.KAPANDI }
        // Gerçekte tutan (takas).
        clickInDialog("Gerçekte: Resmi kişi tuttu")
        pickFromMenu("Şule")
        waitForData(what = "gerçekte tutan") { d -> d.informalChanges.any { it.date == may30 && it.actualPersonId == "sule" && it.officialPersonId == "kenan" } }
        // Gün türü değişir ve takvimdekine dönülür.
        clickInDialog("Gün türü: Bayram")
        pickFromMenu("Normal")
        waitForData(what = "gün türü normal") { d -> d.dayOverrides.any { it.date == may30 && it.kind == DayKind.NORMAL } }
        clickInDialog("Gün türü: Normal")
        pickFromMenu("Bayram")
        waitForData(what = "takvimdeki tür") { d -> d.dayOverrides.none { it.date == may30 } }
        // Vizit.
        clickInDialog("Var")
        waitForData(what = "vizit") { d -> d.dayOverrides.any { it.date == may30 && it.actualVisit == Visit.VAR } }
        // Uzman.
        clickInDialog("Tutan: Kenan")
        pickFromMenu("Uzman (asistan yok)")
        waitForData(what = "uzman") { official() == null }
        shot("t-bayram-kayitlari")
        clickInDialog("Tamam")
        waitForText("Uzman")
        clickDesc("Geri")
        waitForText("resmi listeye göre")
    }
}
