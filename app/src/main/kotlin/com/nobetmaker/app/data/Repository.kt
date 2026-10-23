package com.nobetmaker.app.data

import android.content.Context
import android.net.Uri
import com.nobetmaker.core.model.AppData
import com.nobetmaker.core.ops.AppJson
import com.nobetmaker.core.seed.SeedData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bütün veri tek bir JSON dosyasında (§15). Yazma atomiktir.
 *
 * Veri kaybına karşı:
 * - Değişikliklerden önce en fazla 15 dakikada bir anlık yedek alınır; son 10 yedek ve son 30 günün
 *   her biri için günün son yedeği saklanır.
 * - Yedekten dönme, yedek yükleme ve başlangıç verisine dönmeden önce her zaman yedek alınır.
 * - Yeni sürüm eski veriyi güncellemeden önce dosyanın olduğu gibi bir kopyasını "kurtarma" klasörüne koyar.
 * - Dosya okunamazsa üstüne yazılmaz: kopyası "kurtarma" klasörüne alınır, en yeni okunabilen yedek açılır.
 * Kurtarma klasöründeki dosyalar hiç silinmez.
 */
class Repository(context: Context) {

    private val file = File(context.filesDir, "nobetmaker.json")
    private val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
    private val rescueDir = File(context.filesDir, "kurtarma").apply { mkdirs() }
    private val lock = Any()

    /** Açılışta kullanıcıya gösterilecek uyarı (ör. veri okunamadı, yedekten açıldı). */
    var startupNotice: String? = null
        private set

    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data

    private fun load(): AppData {
        if (file.exists()) {
            val text = runCatching { file.readText() }.getOrNull()
            val decoded = text?.let { t -> runCatching { AppJson.decode(t) }.getOrNull() }
            if (text != null && decoded != null) {
                val stored = Regex("\"schemaVersion\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                if (stored < AppData.SCHEMA_VERSION) keepRescueCopy("surum-$stored-oncesi")
                return decoded
            }
            keepRescueCopy("okunamayan")
            for (b in backups()) {
                val fromBackup = runCatching { AppJson.decode(b.readText()) }.getOrNull() ?: continue
                startupNotice = "Kayıtlı veri okunamadı; ${b.name.removePrefix("yedek-").removeSuffix(".json")} yedeği açıldı. Okunamayan dosya kurtarma klasöründe saklandı."
                return fromBackup
            }
            startupNotice = "Kayıtlı veri ve yedekler okunamadı; başlangıç verisi açıldı. Eski dosya kurtarma klasöründe saklandı."
        }
        return SeedData.build().also { write(it, snapshot = false) }
    }

    private fun stamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())

    private fun keepRescueCopy(label: String) {
        runCatching { file.copyTo(File(rescueDir, "$label-${stamp()}.json"), overwrite = true) }
    }

    fun update(transform: (AppData) -> AppData): Result<AppData> = synchronized(lock) {
        runCatching {
            val updated = transform(_data.value)
            if (updated !== _data.value) {
                write(updated, snapshot = true)
                _data.value = updated
            }
            updated
        }
    }

    private fun write(data: AppData, snapshot: Boolean) {
        if (snapshot && file.exists()) snapshotIfDue()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.outputStream().use { out ->
            out.write(AppJson.encode(data).toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun snapshotIfDue() {
        val latest = backups().firstOrNull()
        if (latest != null && System.currentTimeMillis() - latest.lastModified() < 15 * 60 * 1000) return
        snapshotNow()
    }

    private fun snapshotNow() {
        if (!file.exists()) return
        file.copyTo(File(backupDir, "yedek-${stamp()}.json"), overwrite = true)
        prune()
    }

    /** Son 10 yedek ve son 30 günün her birinden günün en yeni yedeği kalır. */
    private fun prune() {
        val all = backups()
        val keep = all.take(10).toMutableSet()
        all.groupBy { it.name.removePrefix("yedek-").take(8) }.values.take(30).forEach { keep += it.first() }
        all.filterNot { it in keep }.forEach { it.delete() }
    }

    fun backups(): List<File> =
        backupDir.listFiles { f -> f.name.endsWith(".json") }?.sortedByDescending { it.name }.orEmpty()

    /** Sürüm yükseltmesinden önceki ve okunamayan dosyaların kopyaları (hiç silinmez). */
    fun rescueCopies(): List<File> =
        rescueDir.listFiles { f -> f.name.endsWith(".json") }?.sortedByDescending { it.lastModified() }.orEmpty()

    /** Bütün veriyi değiştiren işlemler: önce her zaman yedek alınır. */
    private fun replaceAll(transform: () -> AppData): Result<AppData> = synchronized(lock) {
        runCatching {
            val updated = transform()
            snapshotNow()
            write(updated, snapshot = false)
            _data.value = updated
            updated
        }
    }

    fun restoreBackup(backup: File): Result<AppData> = replaceAll { AppJson.decode(backup.readText()) }

    fun exportTo(context: Context, uri: Uri): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { it.write(AppJson.encode(_data.value).toByteArray(Charsets.UTF_8)) }
            ?: error("Dosya açılamadı")
    }

    fun importFrom(context: Context, uri: Uri): Result<AppData> {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("Dosya açılamadı")
        }.getOrElse { return Result.failure(it) }
        val decoded = runCatching { AppJson.decode(text) }.getOrElse { return Result.failure(IllegalArgumentException("Bu bir NöbetMaker yedeği değil")) }
        return replaceAll { decoded }
    }

    fun resetToSeed(): Result<AppData> = replaceAll { SeedData.build() }
}
