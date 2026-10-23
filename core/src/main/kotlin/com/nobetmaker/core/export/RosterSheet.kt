package com.nobetmaker.core.export

import com.nobetmaker.core.calendar.trName
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/** WhatsApp görseli ve PDF için aylık takvim düzeni (A4 yatay oranında). */
object RosterSheet {

    const val WIDTH = 2000
    const val HEIGHT = 1414

    private val tr = Locale.forLanguageTag("tr")
    private const val M = 64f
    private const val GAP = 12f
    private const val RADIUS = 18f

    fun layout(view: MonthView, generatedOn: LocalDate? = null): SheetLayout {
        val ops = ArrayList<DrawOp>()
        val w = WIDTH.toFloat()
        ops += DrawOp.Rect(0f, 0f, w, HEIGHT.toFloat(), fill = Colors.WHITE)

        // Başlık: küçük üst satır, büyük ay adı, sağda liste türü.
        ops += DrawOp.Text(M, 48f, 1300f, 30f, view.title.uppercase(tr), 22f, Colors.MUTED, align = Align.LEFT, weight = 600, tracking = 0.14f)
        ops += DrawOp.Text(M, 82f, 1300f, 100f, view.monthTitle, 86f, Colors.INK, align = Align.LEFT, weight = 800, tracking = -0.02f)
        val badge = if (view.showActual) "Gerçekleşen liste" else "Resmi liste"
        val bw = 300f
        ops += DrawOp.Rect(w - M - bw, 92f, bw, 58f, fill = Colors.HEADER, radius = 29f)
        ops += DrawOp.Text(w - M - bw, 92f, bw, 58f, badge, 25f, Colors.WHITE, weight = 700)
        if (generatedOn != null) {
            val date = "%02d.%02d.%d".format(generatedOn.dayOfMonth, generatedOn.monthValue, generatedOn.year)
            ops += DrawOp.Text(w - M - 500f, 160f, 500f, 28f, "Hazırlanma: $date", 20f, Colors.MUTED, align = Align.RIGHT, weight = 500)
        }

        // Gün başlıkları ve hücreler (aralıklı, yuvarlak köşeli kutular).
        val headTop = 212f
        val headH = 40f
        val colW = (w - 2 * M - 6 * GAP) / 7
        for (c in 0 until 7) {
            val x = M + c * (colW + GAP)
            ops += DrawOp.Text(
                x + 16f, headTop, colW - 32f, headH, DayOfWeek.of(c + 1).trName().uppercase(tr), 20f,
                if (c >= 5) Colors.HEADER else Colors.MUTED, align = Align.LEFT, weight = 700, tracking = 0.1f,
            )
        }
        val rows = view.weeks.size
        val cellsTop = headTop + headH + 6f
        val cellsBottom = 1140f
        val rowH = (cellsBottom - cellsTop - (rows - 1) * GAP) / rows
        for ((r, week) in view.weeks.withIndex()) {
            for ((c, cell) in week.withIndex()) {
                drawCell(ops, cell, M + c * (colW + GAP), cellsTop + r * (rowH + GAP), colW, rowH, view.showActual)
            }
        }

        // Nöbet sayıları: kişi başına bir kart.
        val legendTop = cellsBottom + 32f
        ops += DrawOp.Text(M, legendTop, 600f, 28f, "NÖBET SAYILARI", 20f, Colors.MUTED, align = Align.LEFT, weight = 700, tracking = 0.12f)
        val n = view.totals.size.coerceAtLeast(1)
        val chipW = minOf(300f, (w - 2 * M - (n - 1) * GAP) / n)
        val chipY = legendTop + 38f
        val chipH = 84f
        view.totals.forEachIndexed { i, t ->
            val x = M + i * (chipW + GAP)
            val color = Colors.parse(t.person.color)
            ops += DrawOp.Rect(x, chipY, chipW, chipH, fill = Colors.CELL, radius = RADIUS)
            ops += DrawOp.Rect(x + 18f, chipY + (chipH - 26f) / 2, 26f, 26f, fill = color, radius = 13f)
            ops += DrawOp.Text(x + 58f, chipY + 12f, chipW - 70f, 32f, t.person.name, 26f, Colors.INK, align = Align.LEFT, weight = 700)
            ops += DrawOp.Text(x + 58f, chipY + 44f, chipW - 70f, 28f, "${t.count} nöbet · ${t.hours} sa", 21f, Colors.MUTED, align = Align.LEFT, weight = 500)
        }
        if (view.leaves.isNotEmpty()) {
            val y = chipY + chipH + 20f
            ops += DrawOp.Text(M, y, 130f, 30f, "İZİNLER", 20f, Colors.MUTED, align = Align.LEFT, weight = 700, tracking = 0.12f)
            ops += DrawOp.Text(M + 140f, y, w - 2 * M - 140f, 30f, view.leaves.joinToString("   ·   "), 21f, Colors.INK, align = Align.LEFT, weight = 500)
        }
        ops += DrawOp.Text(M, HEIGHT - 46f, w - 2 * M, 28f, "NöbetMaker", 19f, Colors.FAINT, align = Align.RIGHT, weight = 600)
        return SheetLayout(WIDTH, HEIGHT, ops)
    }

    private fun drawCell(ops: MutableList<DrawOp>, cell: DayCell, x: Float, y: Float, cw: Float, ch: Float, showActual: Boolean) {
        val main = if (showActual) cell.actual else cell.official
        val other = if (showActual) cell.official else cell.actual
        val day = cell.date.dayOfMonth.toString()
        if (!cell.inMonth) {
            ops += DrawOp.Rect(x, y, cw, ch, stroke = Colors.CELL_OUTLINE, strokeWidth = 2f, radius = RADIUS)
            ops += DrawOp.Text(x + 16f, y + 12f, 80f, 34f, day, 26f, Colors.FAINT, align = Align.LEFT, vAlign = VAlign.TOP, weight = 600)
            if (main != null) ops += DrawOp.Text(x + 16f, y + ch - 46f, cw - 32f, 30f, main.name, 22f, Colors.FAINT, align = Align.LEFT, weight = 500)
            return
        }
        val bg = when {
            cell.holiday -> Colors.CELL_HOLIDAY
            cell.weekend -> Colors.CELL_WEEKEND
            else -> Colors.CELL
        }
        ops += DrawOp.Rect(x, y, cw, ch, fill = bg, radius = RADIUS)
        if (cell.flagged) ops += DrawOp.Rect(x + 3f, y + 3f, cw - 6f, ch - 6f, stroke = Colors.argb(255, 0xF5, 0x7C, 0x00), strokeWidth = 5f, radius = RADIUS - 3f)

        ops += DrawOp.Text(
            x + 16f, y + 12f, 80f, 34f, day, 30f, if (cell.holiday) Colors.HOLIDAY_INK else Colors.INK,
            align = Align.LEFT, vAlign = VAlign.TOP, weight = 800,
        )
        if (cell.visit) {
            val label = if (cell.visitUncertain) "VİZİT?" else "VİZİT"
            val vw = if (cell.visitUncertain) 98f else 84f
            ops += DrawOp.Rect(x + cw - 14f - vw, y + 13f, vw, 30f, fill = Colors.ACCENT_SOFT, radius = 15f)
            ops += DrawOp.Text(x + cw - 14f - vw, y + 13f, vw, 30f, label, 16f, Colors.HEADER, weight = 800, tracking = 0.06f)
        }

        // Ara satır: tatil adı ya da resmi/gerçek farkı.
        val informal = other != null && main != null && other.id != main.id
        val note = if (informal) (if (showActual) "resmi: ${other!!.name}" else "${other!!.name} tuttu") else null
        when {
            cell.holidayName != null ->
                ops += DrawOp.Text(x + 16f, y + 47f, cw - 32f, 24f, cell.holidayName, 19f, Colors.HOLIDAY_INK, align = Align.LEFT, weight = 600)
            note != null ->
                ops += DrawOp.Text(x + 16f, y + 47f, cw - 32f, 24f, note, 19f, Colors.shade(Colors.parse(other!!.color), 0.75f), align = Align.LEFT, weight = 700)
        }

        if (main != null) {
            val pillH = minOf(62f, ch * 0.40f)
            val pillY = y + ch - pillH - 12f
            val color = Colors.parse(main.color)
            ops += DrawOp.Rect(x + 12f, pillY, cw - 24f, pillH, fill = color, radius = pillH / 2)
            val label = if (informal && cell.holidayName != null && !showActual) "${main.name} (${other!!.name})" else main.name
            ops += DrawOp.Text(x + 24f, pillY, cw - 48f, pillH, label, minOf(34f, pillH * 0.58f), Colors.onColor(color), weight = 700)
        }
    }
}
