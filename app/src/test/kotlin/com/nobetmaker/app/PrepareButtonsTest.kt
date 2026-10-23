package com.nobetmaker.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.nobetmaker.core.model.DutyRequest
import com.nobetmaker.core.model.PeriodType
import com.nobetmaker.core.model.Priority
import com.nobetmaker.core.model.RequestType
import com.nobetmaker.core.ops.DataOps
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ayı hazırla ekranı: dönemler, istekler, nöbet sayıları, kontrol. */
class PrepareButtonsTest : AppUiTest() {

    private val kasim = YearMonth.of(2026, 11)

    private fun openPrepare(tab: String? = null) {
        goToMonth(kasim)
        click("Ayı hazırla")
        waitForText("Kasım 2026 hazırlığı")
        if (tab != null) click(tab)
    }

    private fun pickDate(field: String, day: Int) {
        clickInDialog(field, substring = true)
        pickDay(day, "Kasım")
    }

    @Test
    fun donemEkleDuzenleSil() {
        openPrepare()
        click("Dönem ekle")
        clickInDialog("Vazgeç")
        assertTrue(data().periods.isEmpty())

        click("Dönem ekle")
        clickInDialog("Kişi")
        pickFromMenu("Oğuz")
        clickInDialog("Tür: Yıllık izin")
        pickFromMenu("Kongre / eğitim izni")
        pickDate("Başlangıç", 9)
        pickDate("Bitiş", 12)
        toggleInDialog("Tarihi belirsiz")
        typeInDialog("Not", "Mikrobiyoloji kongresi")
        shot("t-donem-ekle")
        clickInDialog("Kaydet")
        waitForData(what = "dönem kaydı") { d ->
            d.periods.any {
                it.personId == "oguz" && it.type == PeriodType.KONGRE && it.start == kasim.atDay(9) && it.end == kasim.atDay(12) &&
                    it.tentative && it.note == "Mikrobiyoloji kongresi"
            }
        }

        iconNextTo("Oğuz · Kongre", "Düzenle")
        waitForText("Dönemi düzenle")
        toggleInDialog("Tarihi belirsiz")
        toggleInDialog("Nöbet sayısını düşürür")
        toggleInDialog("Bu dönemde nöbet tutar")
        clickInDialog("Kaydet")
        waitForData(what = "dönem düzenleme") { d -> d.periods.single().let { !it.tentative && it.reducesQuota && it.worksDuty } }

        iconNextTo("Oğuz · Kongre", "Sil")
        waitForData(what = "dönem silme") { it.periods.isEmpty() }
    }

    @Test
    fun istekEkleDuzenleSil() {
        openPrepare("İstekler")
        click("İstek ekle")
        clickInDialog("Kişi")
        pickFromMenu("Şule")
        clickInDialog("Öncelik: Mümkünse")
        pickFromMenu("Kesin")
        clickInDialog("4")
        clickInDialog("5")
        clickInDialog("8")
        clickInDialog("8") // ikinci dokunuş seçimi kaldırır
        typeInDialog("Mesajın kendisi (isteğe bağlı)", "4-5 olmasın")
        clickInDialog("Kaydet")
        waitForData(what = "gün isteği") { d ->
            d.requests.any {
                it.personId == "sule" && it.type == RequestType.AVOID_DATES && it.priority == Priority.KESIN &&
                    it.dates == listOf(kasim.atDay(4), kasim.atDay(5)) && it.originalText == "4-5 olmasın"
            }
        }

        // Aralığa çevir: 10–12.
        iconNextTo("Şule:", "Düzenle")
        waitForText("İsteği düzenle")
        clickInDialog("Tür: Şu gün(ler) olmasın")
        pickFromMenu("Şu aralık olmasın")
        clickInDialog("10")
        clickInDialog("12")
        clickInDialog("Kaydet")
        waitForData(what = "aralık isteği") { d ->
            d.requests.single().let { it.type == RequestType.AVOID_RANGE && it.rangeStart == kasim.atDay(10) && it.rangeEnd == kasim.atDay(12) }
        }

        // Bütün ay.
        iconNextTo("Şule:", "Düzenle")
        clickInDialog("Bütün ay")
        clickInDialog("Kaydet")
        waitForData(what = "bütün ay") { d -> d.requests.single().let { it.rangeStart == kasim.atDay(1) && it.rangeEnd == kasim.atEndOfMonth() } }

        // Haftada en fazla 2.
        iconNextTo("Şule:", "Düzenle")
        clickInDialog("Tür: Şu aralık olmasın")
        pickFromMenu("Haftada en fazla")
        stepper("Haftada en fazla", plus = true, inDialog = true)
        clickInDialog("Kaydet")
        waitForData(what = "haftalık üst sınır") { d -> d.requests.single().let { it.type == RequestType.WEEKLY_MAX && it.maxPerWeek == 2 } }

        // Yoğunlaştırma: 2–9 arası.
        iconNextTo("Şule:", "Düzenle")
        clickInDialog("Tür: Haftada en fazla")
        pickFromMenu("Nöbetler şu aralıkta yoğun olsun")
        clickInDialog("2")
        clickInDialog("9")
        clickInDialog("Kaydet")
        waitForData(what = "yoğunlaştırma") { d ->
            d.requests.single().let { it.type == RequestType.CLUSTER && it.rangeStart == kasim.atDay(2) && it.rangeEnd == kasim.atDay(9) }
        }

        // İstek günü (gönüllü) ve vazgeç.
        click("İstek ekle")
        clickInDialog("Kişi")
        pickFromMenu("Aslı")
        clickInDialog("Tür: Şu gün(ler) olmasın")
        pickFromMenu("Şu günü istiyor / gönüllü")
        clickInDialog("21")
        clickInDialog("Vazgeç")
        assertEquals(1, data().requests.size)

        iconNextTo("Şule:", "Sil")
        waitForData(what = "istek silme") { it.requests.isEmpty() }
    }

    @Test
    fun nobetSayilari() {
        openPrepare("Nöbet sayıları")
        waitForText("Toplam")
        fun q(id: String) = data().month(kasim)!!.quotas.first { it.personId == id }
        val yaren = q("yaren").final

        stepper("Yaren", plus = false)
        waitForData(what = "elle azaltma") { q("yaren").final == yaren - 1 && q("yaren").manual }
        assertEquals(30, data().month(kasim)!!.quotas.sumOf { it.final })

        clickDesc("Sabitlemeyi kaldır")
        waitForData(what = "sabitlemeyi kaldırma") { !q("yaren").manual && q("yaren").final == yaren }

        val kenan = q("kenan").final
        stepper("Kenan", plus = true)
        waitForData(what = "elle artırma") { q("kenan").final == kenan + 1 && q("kenan").manual }
        assertEquals(30, data().month(kasim)!!.quotas.sumOf { it.final })

        click("Öneriye dön")
        waitForData(what = "öneriye dönme") { d -> d.month(kasim)!!.quotas.none { it.manual } && q("kenan").final == kenan }
    }

    @Test
    fun kontrolVeListeUret() {
        update { d ->
            listOf("kenan", "fatma").fold(DataOps.ensureMonth(d, kasim)) { acc, id ->
                DataOps.upsertRequest(acc, DutyRequest("t-$id", id, RequestType.WANT_DATES, Priority.KESIN, dates = listOf(kasim.atDay(14))))
            }
        }
        openPrepare("Kontrol")
        waitForText("birden fazla kesin istek var", 30_000)
        shot("t-kontrol")

        click("Liste üret")
        waitForText("Kasım 2026 seçenekleri")
        clickDesc("Geri")
        waitForText("Kasım 2026 hazırlığı")
        clickDesc("Geri")
        waitForText("Takvim")
    }
}
