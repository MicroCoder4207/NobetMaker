package com.nobetmaker.app

import com.nobetmaker.app.data.Repository
import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.ops.AppJson
import com.nobetmaker.core.ops.DataOps
import com.nobetmaker.core.seed.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.YearMonth

/** Veri dosyasının kaybolmaması için alınan önlemler. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepositoryTest {

    private val context = RuntimeEnvironment.getApplication()
    private val dir = context.filesDir
    private val file = File(dir, "nobetmaker.json")
    private fun rescue() = File(dir, "kurtarma").listFiles().orEmpty().toList()

    @Test
    fun okunamayanDosyaninUstuneYazilmaz() {
        File(dir, "backups").deleteRecursively()
        File(dir, "kurtarma").deleteRecursively()
        file.writeText("{ bozuk")
        val repo = Repository(context)
        assertNotNull(repo.startupNotice)
        val kept = rescue().single { it.name.startsWith("okunamayan-") }
        assertEquals("{ bozuk", kept.readText())
    }

    @Test
    fun okunamayanDosyadaEnYeniYedekAcilir() {
        File(dir, "kurtarma").deleteRecursively()
        val backups = File(dir, "backups").apply { deleteRecursively(); mkdirs() }
        val saved = DataOps.ensureMonth(SeedData.build(), YearMonth.of(2026, 11))
        File(backups, "yedek-20261101-120000.json").writeText(AppJson.encode(saved))
        file.writeText("bozuk")
        val repo = Repository(context)
        assertNotNull(repo.data.value.month(YearMonth.of(2026, 11)))
        assertTrue(repo.startupNotice!!.contains("20261101"))
    }

    @Test
    fun surumYukseltmesindenOnceKopyaAlinir() {
        File(dir, "kurtarma").deleteRecursively()
        val old = AppJson.encode(SeedData.build().copy(schemaVersion = 2))
        file.writeText(old)
        val repo = Repository(context)
        assertEquals(AppData.SCHEMA_VERSION, repo.data.value.schemaVersion)
        assertEquals(old, rescue().single { it.name.startsWith("surum-2-oncesi-") }.readText())
    }

    @Test
    fun topluDegisiklikOncesiHerZamanYedekAlinirVeEskiGunlerSaklanir() {
        File(dir, "kurtarma").deleteRecursively()
        val backups = File(dir, "backups").apply { deleteRecursively(); mkdirs() }
        // 40 gün boyunca günde 3 yedek.
        for (d in 0 until 40) for (h in listOf("08", "12", "18")) {
            val day = java.time.LocalDate.of(2026, 8, 1).plusDays(d.toLong())
            File(backups, "yedek-%04d%02d%02d-${h}0000.json".format(day.year, day.monthValue, day.dayOfMonth)).writeText("{}")
        }
        file.writeText(AppJson.encode(SeedData.build()))
        val repo = Repository(context)
        val before = repo.backups().size
        repo.resetToSeed().getOrThrow()
        val names = repo.backups().map { it.name }
        assertTrue("toplu değişiklikten önce yedek alınmalı", names.first() > "yedek-2026")
        // Son 10 yedek + son 30 günün her birinden bir yedek.
        val days = names.map { it.removePrefix("yedek-").take(8) }.toSet()
        assertTrue("en az 30 gün saklanmalı: ${days.size}", days.size >= 30)
        assertTrue("eski yedekler budanmalı: $before → ${names.size}", names.size <= 10 + 30)
    }
}
