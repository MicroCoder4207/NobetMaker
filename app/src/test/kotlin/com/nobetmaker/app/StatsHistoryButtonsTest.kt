package com.nobetmaker.app

import com.nobetmaker.core.model.EventKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** İstatistik aralıkları ve geçmiş özel günler ekranı. */
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
        waitForText("Kasım 2025 – Ekim 2026")
        click("Takvim")
        waitForText("Gerçekleşen")
    }

    @Test
    fun gecmisOzelGunEkleDuzenleSil() {
        val today = LocalDate.now()
        val before = data().historyEvents.size
        click("İstatistik")
        click("Geçmiş özel günler (2023'ten beri)")
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
        waitForText("resmi listeye göre")
    }
}
