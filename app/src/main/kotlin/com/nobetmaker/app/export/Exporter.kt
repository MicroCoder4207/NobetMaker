package com.nobetmaker.app.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.nobetmaker.core.export.Align
import com.nobetmaker.core.export.DrawOp
import com.nobetmaker.core.export.MonthViewBuilder
import com.nobetmaker.core.export.RosterExcel
import com.nobetmaker.core.export.RosterSheet
import com.nobetmaker.core.export.SheetLayout
import com.nobetmaker.core.export.VAlign
import com.nobetmaker.core.model.AppData
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/** Ortak çizim düzenini Android tuvaline çizer (PNG ve PDF aynı kodu kullanır). */
object AndroidRenderer {
    fun draw(layout: SheetLayout, canvas: Canvas) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val text = Paint(Paint.ANTI_ALIAS_FLAG)
        for (op in layout.ops) {
            when (op) {
                is DrawOp.Rect -> {
                    val r = RectF(op.x, op.y, op.x + op.w, op.y + op.h)
                    op.fill?.let { fill.color = it; canvas.drawRoundRect(r, op.radius, op.radius, fill) }
                    op.stroke?.let {
                        stroke.color = it
                        stroke.strokeWidth = op.strokeWidth
                        canvas.drawRoundRect(r, op.radius, op.radius, stroke)
                    }
                }
                is DrawOp.Line -> {
                    stroke.color = op.color
                    stroke.strokeWidth = op.width
                    canvas.drawLine(op.x1, op.y1, op.x2, op.y2, stroke)
                }
                is DrawOp.Text -> {
                    text.color = op.color
                    text.typeface = if (op.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    var size = op.size
                    text.textSize = size
                    while (text.measureText(op.text) > op.w && size > 8f) {
                        size -= 1f
                        text.textSize = size
                    }
                    val width = text.measureText(op.text)
                    val fm = text.fontMetrics
                    val x = when (op.align) {
                        Align.LEFT -> op.x
                        Align.CENTER -> op.x + (op.w - width) / 2
                        Align.RIGHT -> op.x + op.w - width
                    }
                    val y = when (op.vAlign) {
                        VAlign.TOP -> op.y - fm.ascent
                        VAlign.MIDDLE -> op.y + (op.h - (fm.descent - fm.ascent)) / 2 - fm.ascent
                        VAlign.BOTTOM -> op.y + op.h - fm.descent
                    }
                    canvas.drawText(op.text, x, y, text)
                }
            }
        }
    }
}

object Exporter {

    private fun exportDir(context: Context) = File(context.cacheDir, "exports").apply { mkdirs() }

    private fun share(context: Context, file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun baseName(data: AppData, month: YearMonth, actual: Boolean): String =
        RosterExcel.fileName(MonthViewBuilder.build(data, month, actual)).removeSuffix(".xlsx")

    fun shareExcel(context: Context, data: AppData, month: YearMonth, actual: Boolean) {
        val view = MonthViewBuilder.build(data, month, actual)
        val file = File(exportDir(context), RosterExcel.fileName(view))
        file.outputStream().use { RosterExcel.write(view, it) }
        share(context, file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", view.monthTitle)
    }

    private fun bitmap(data: AppData, month: YearMonth, actual: Boolean): Bitmap {
        val layout = RosterSheet.layout(MonthViewBuilder.build(data, month, actual), LocalDate.now())
        val bmp = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
        AndroidRenderer.draw(layout, Canvas(bmp))
        return bmp
    }

    fun shareImage(context: Context, data: AppData, month: YearMonth, actual: Boolean) {
        val file = File(exportDir(context), baseName(data, month, actual) + ".png")
        val bmp = bitmap(data, month, actual)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        share(context, file, "image/png", "Nöbet listesi")
    }

    fun sharePdf(context: Context, data: AppData, month: YearMonth, actual: Boolean) {
        val layout = RosterSheet.layout(MonthViewBuilder.build(data, month, actual), LocalDate.now())
        val doc = PdfDocument()
        // A4 yatay: 842 x 595 nokta.
        val page = doc.startPage(PdfDocument.PageInfo.Builder(842, 595, 1).create())
        val scale = minOf(842f / layout.width, 595f / layout.height)
        page.canvas.scale(scale, scale)
        AndroidRenderer.draw(layout, page.canvas)
        doc.finishPage(page)
        val file = File(exportDir(context), baseName(data, month, actual) + ".pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        share(context, file, "application/pdf", "Nöbet listesi")
    }
}
