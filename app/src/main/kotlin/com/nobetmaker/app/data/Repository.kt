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
 * Bütün veri tek bir JSON dosyasında (§15). Yazma atomiktir; değişikliklerden önce en fazla
 * 15 dakikada bir anlık yedek alınır ve son 10 yedek saklanır.
 */
class Repository(context: Context) {

    private val file = File(context.filesDir, "nobetmaker.json")
    private val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
    private val lock = Any()

    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data

    private fun load(): AppData {
        if (file.exists()) {
            runCatching { return AppJson.decode(file.readText()) }
            backups().forEach { b -> runCatching { return AppJson.decode(b.readText()) } }
        }
        return SeedData.build().also { write(it, snapshot = false) }
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
        val name = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        file.copyTo(File(backupDir, "yedek-$name.json"), overwrite = true)
        backups().drop(10).forEach { it.delete() }
    }

    fun backups(): List<File> =
        backupDir.listFiles { f -> f.name.endsWith(".json") }?.sortedByDescending { it.name }.orEmpty()

    fun restoreBackup(backup: File): Result<AppData> = update { AppJson.decode(backup.readText()) }

    fun exportTo(context: Context, uri: Uri): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { it.write(AppJson.encode(_data.value).toByteArray(Charsets.UTF_8)) }
            ?: error("Dosya açılamadı")
    }

    fun importFrom(context: Context, uri: Uri): Result<AppData> {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("Dosya açılamadı")
        }.getOrElse { return Result.failure(it) }
        val decoded = runCatching { AppJson.decode(text) }.getOrElse { return Result.failure(IllegalArgumentException("Bu bir NöbetMaker yedeği değil")) }
        return update { decoded }
    }

    fun resetToSeed(): Result<AppData> = update { SeedData.build() }
}
