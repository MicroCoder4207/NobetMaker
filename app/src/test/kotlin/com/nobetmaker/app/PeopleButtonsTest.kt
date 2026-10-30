package com.nobetmaker.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.nobetmaker.core.model.PersonPalette
import com.nobetmaker.core.model.RuleType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kişiler ekranı: kıdem sırası, kişi ekleme, düzenleme, silme. */
class PeopleButtonsTest : AppUiTest() {

    private fun seniority(id: String) = data().person(id)!!.seniority

    @Test
    fun kidemSirasiOklari() {
        click("Kişiler")
        waitForText("Kıdem sırası")
        assertTrue("kıdem eğrisi kartı", exists("Kıdem eğrisi"))
        assertTrue("kişi kartında katsayı kaynağı", exists("(kıdem eğrisi)", substring = true))
        // Son kartın okları yüzen "Kişi ekle" düğmesinin altında kalabiliyor; düğmeye doğrudan tıklama eylemiyle basılır.
        rule.onNode(hasContentDescription("Yukarı") and hasAnyAncestor(hasText("7. Aslı"))).performSemanticsAction(SemanticsActions.OnClick)
        waitForData(what = "yukarı taşıma") { seniority("asli") == 6 && seniority("oguz") == 7 }
        rule.onNode(hasContentDescription("Aşağı") and hasAnyAncestor(hasText("6. Aslı"))).performSemanticsAction(SemanticsActions.OnClick)
        waitForData(what = "aşağı taşıma") { seniority("asli") == 7 && seniority("oguz") == 6 }
    }

    @Test
    fun sabitSayiKurali() {
        click("Kişiler")
        waitForText("2. Kenan")
        click("2. Kenan")
        waitForText("Kişiye özel kurallar")
        assertTrue("eğri önizlemesi", exists("Yıl ilerledikçe kendiliğinden değişir", substring = true))
        toggle("Ayda sabit nöbet sayısı")
        stepper("Aylık nöbet", plus = false)
        click("Kaydet")
        waitForData(what = "sabit sayı kuralı") { d ->
            d.person("kenan")!!.rules.singleOrNull { it.type == RuleType.MONTHLY_COUNT }?.count == 3
        }
    }

    @Test
    fun kisiEkleDuzenleSil() {
        val today = LocalDate.now()
        val monthName = com.nobetmaker.core.calendar.monthNameTr(today.monthValue)
        click("Kişiler")
        click("Kişi ekle")
        waitForText("Yeni kişi")
        clickDesc("Geri")
        waitForText("Kıdem sırası")
        click("Kişi ekle")
        waitForText("Yeni kişi")
        type("Ad", "Canay")
        clickDesc("Renk 9")
        click("Asistanlık başlangıcı")
        pickDay(1, monthName)
        click("Nöbete katılış")
        pickDay(15, monthName)
        click("Ayrılış")
        pickDay(28, monthName)
        // Asistanlık başlangıcı girilince katsayılar kıdem eğrisinden gelir; elle girmek için kapatılır.
        assert(exists("Yıl ilerledikçe kendiliğinden değişir", substring = true)) { "eğri önizlemesi yok" }
        toggle("Kıdem eğrisinden hesapla")
        stepper("Nöbet", plus = true)
        stepper("Tatil", plus = false)
        click("Yeni dönem katsayısı ekle")
        toggle("Cumartesi yok")
        click("Bitiş")
        pickDay(28, monthName)
        shot("t-kisi-ekle")
        click("Kaydet")
        waitForData(what = "kişi ekleme") { d -> d.persons.any { it.name == "Canay" } }
        val canay = data().persons.first { it.name == "Canay" }
        assertEquals(PersonPalette.colors[8], canay.color)
        assertEquals("asistanlık başlangıcı", today.withDayOfMonth(1), canay.residencyStart)
        assertEquals("bölüme katılış varsayılan olarak aynı", today.withDayOfMonth(1), canay.joinDate)
        assertEquals(today.withDayOfMonth(15), canay.dutyStart)
        assertEquals(2, canay.coefficients.size)
        assertEquals(today.withDayOfMonth(28), canay.leaveDate)
        assertEquals(0.61, canay.coefficients.minByOrNull { it.from ?: LocalDate.MIN }!!.duty, 1e-9)
        assertEquals(0.59, canay.coefficients.minByOrNull { it.from ?: LocalDate.MIN }!!.hardDay, 1e-9)
        assertEquals(listOf(RuleType.NO_SATURDAY), canay.rules.map { it.type })
        assertEquals(today.withDayOfMonth(28), canay.rules.single().to)
        assertEquals(8, canay.seniority)
        assertEquals(false, canay.autoCoefficients)

        // Düzenle: kuralı kapat, ikinci katsayı dönemini sil.
        waitForText("8. Canay")
        waitSnackbarGone()
        click("8. Canay")
        waitForText("Kişiye özel kurallar")
        click("Ayrılış", substring = true)
        clickInDialog("Temizle")
        toggle("Cumartesi yok")
        rule.onAllNodesWithContentDescription("Sil").fetchSemanticsNodes().size.let { n -> assertEquals("üst çubuk + 2 katsayı", 3, n) }
        // Katsayı dönemlerinin silme düğmeleri kaydırılan içerikte; üst çubuktaki "Sil" kişiyi siler.
        rule.onAllNodes(hasContentDescription("Sil") and hasAnyAncestor(hasScrollAction()))[1].apply { performScrollTo(); performClick() }
        click("Kaydet")
        waitForData(what = "kişi düzenleme") { d ->
            d.persons.first { it.name == "Canay" }.let { it.rules.isEmpty() && it.coefficients.size == 1 && it.leaveDate == null }
        }

        // Sil.
        waitSnackbarGone()
        click("8. Canay")
        waitForText("Kişiye özel kurallar")
        clickDesc("Sil")
        waitForData(what = "kişi silme") { d -> d.persons.none { it.name == "Canay" } }
        waitForText("Kıdem sırası")
    }
}
