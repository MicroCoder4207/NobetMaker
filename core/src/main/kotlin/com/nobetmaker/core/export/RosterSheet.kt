package com.nobetmaker.core.export

import com.nobetmaker.core.calendar.trName
import java.time.DayOfWeek
import java.time.LocalDate

/** Kağıt listelerin görsel dilinde aylık takvim düzeni (A4 yatay oranında). */
object RosterSheet {

    const val WIDTH = 2000
    const val HEIGHT = 1414

    fun layout(view: MonthView, generatedOn: LocalDate? = null): SheetLayout {
        val ops = ArrayList<DrawOp>()
        val m = 56f
        val w = WIDTH.toFloat()

        ops += DrawOp.Rect(0f, 0f, w, HEIGHT.toFloat(), fill = Colors.WHITE)

        // Başlık.
        ops += DrawOp.Text(m, 28f, 1200f, 34f, view.title, 26f, Colors.MUTED, align = Align.LEFT)
        ops += DrawOp.Text(m, 62f, 1200f, 84f, view.monthTitle, 66f, Colors.INK, bold = true, align = Align.LEFT)
        val badge = if (view.showActual) "Gerçekleşen (değişimlerle)" else "Resmi liste"
        ops += DrawOp.Rect(w - m - 420f, 74f, 420f, 56f, fill = Colors.WEEKEND, radius = 28f)
        ops += DrawOp.Text(w - m - 420f, 74f, 420f, 56f, badge, 26f, Colors.HEADER, bold = true)

        // Izgara.
        val gridTop = 170f
        val headerH = 54f
        val gridBottom = 1100f
        val rows = view.weeks.size
        val colW = (w - 2 * m) / 7
        val rowH = (gridBottom - gridTop - headerH) / rows

        for (c in 0 until 7) {
            val x = m + c * colW
            val weekend = c >= 5
            ops += DrawOp.Rect(x, gridTop, colW, headerH, fill = if (weekend) Colors.shade(Colors.HEADER, 0.8f) else Colors.HEADER)
            ops += DrawOp.Text(x, gridTop, colW, headerH, DayOfWeek.of(c + 1).trName(), 26f, Colors.WHITE, bold = true)
        }

        for ((r, week) in view.weeks.withIndex()) {
            for ((c, cell) in week.withIndex()) {
                val x = m + c * colW
                val y = gridTop + headerH + r * rowH
                drawCell(ops, cell, x, y, colW, rowH, view.showActual)
            }
        }
        // Çizgiler.
        for (r in 0..rows) {
            val y = gridTop + headerH + r * rowH
            ops += DrawOp.Line(m, y, w - m, y, Colors.GRID, 2f)
        }
        for (c in 0..7) {
            val x = m + c * colW
            ops += DrawOp.Line(x, gridTop, x, gridBottom, Colors.GRID, 2f)
        }

        // Alt bilgi: kişi toplamları ve izinler.
        val bottomTop = gridBottom + 34f
        ops += DrawOp.Text(m, bottomTop, 900f, 36f, "Nöbet sayıları", 28f, Colors.INK, bold = true, align = Align.LEFT)
        val perCol = 4
        view.totals.forEachIndexed { i, t ->
            val col = i / perCol
            val row = i % perCol
            val x = m + col * 470f
            val y = bottomTop + 48f + row * 50f
            val color = Colors.parse(t.person.color)
            ops += DrawOp.Rect(x, y + 6f, 30f, 30f, fill = color, radius = 15f)
            ops += DrawOp.Text(x + 44f, y, 420f, 42f, "${t.person.name} → ${t.count} nöbet · ${t.hours} saat", 26f, Colors.INK, align = Align.LEFT)
        }
        val leaveX = m + 2 * 470f + 40f
        if (view.leaves.isNotEmpty()) {
            ops += DrawOp.Text(leaveX, bottomTop, w - m - leaveX, 36f, "İzinler", 28f, Colors.INK, bold = true, align = Align.LEFT)
            view.leaves.take(5).forEachIndexed { i, s ->
                ops += DrawOp.Text(leaveX, bottomTop + 48f + i * 44f, w - m - leaveX, 40f, s, 24f, Colors.INK, align = Align.LEFT)
            }
        }
        val footer = buildString {
            append("NöbetMaker")
            if (generatedOn != null) append(" · ${generatedOn.dayOfMonth}.${generatedOn.monthValue}.${generatedOn.year}")
        }
        ops += DrawOp.Text(m, HEIGHT - 44f, w - 2 * m, 30f, footer, 20f, Colors.FAINT, align = Align.RIGHT)
        return SheetLayout(WIDTH, HEIGHT, ops)
    }

    private fun drawCell(ops: MutableList<DrawOp>, cell: DayCell, x: Float, y: Float, cw: Float, ch: Float, showActual: Boolean) {
        val bg = when {
            !cell.inMonth -> Colors.WHITE
            cell.holiday -> Colors.HOLIDAY
            cell.weekend -> Colors.WEEKEND
            else -> Colors.WHITE
        }
        ops += DrawOp.Rect(x, y, cw, ch, fill = bg)
        if (cell.flagged) ops += DrawOp.Rect(x + 4f, y + 4f, cw - 8f, ch - 8f, stroke = Colors.argb(255, 0xF5, 0x7C, 0x00), strokeWidth = 6f, radius = 6f)

        ops += DrawOp.Text(
            x + 10f, y + 6f, cw - 22f, 34f, cell.date.dayOfMonth.toString(), 28f,
            if (cell.inMonth) Colors.INK else Colors.FAINT, bold = cell.inMonth, align = Align.RIGHT, vAlign = VAlign.TOP,
        )
        if (cell.inMonth && cell.visit) {
            val bw = if (cell.visitUncertain) 86f else 74f
            ops += DrawOp.Rect(x + 12f, y + 10f, bw, 30f, stroke = Colors.HOLIDAY_INK, strokeWidth = 2f, radius = 15f)
            ops += DrawOp.Text(x + 12f, y + 10f, bw, 30f, if (cell.visitUncertain) "vizit?" else "vizit", 18f, Colors.HOLIDAY_INK, bold = true)
        }

        val main = if (showActual) cell.actual else cell.official
        val other = if (showActual) cell.official else cell.actual
        if (main != null) {
            if (cell.inMonth) {
                val pillH = minOf(62f, ch * 0.42f)
                val pillY = y + ch * 0.30f
                val color = Colors.parse(main.color)
                ops += DrawOp.Rect(x + 18f, pillY, cw - 36f, pillH, fill = Colors.tint(color, 0.42f), radius = pillH / 2)
                ops += DrawOp.Text(x + 18f, pillY, cw - 36f, pillH, main.name, 36f, Colors.INK, bold = true)
                if (other != null && other.id != main.id) {
                    val note = if (showActual) "resmi: ${other.name}" else "(${other.name})"
                    ops += DrawOp.Text(x + 10f, pillY + pillH + 2f, cw - 20f, 32f, note, 24f, Colors.shade(Colors.parse(other.color), 0.65f), bold = true)
                }
            } else {
                ops += DrawOp.Text(x + 10f, y + ch * 0.32f, cw - 20f, 40f, main.name, 24f, Colors.FAINT)
            }
        }
        if (cell.inMonth && cell.holidayName != null) {
            ops += DrawOp.Text(x + 8f, y + ch - 36f, cw - 16f, 30f, cell.holidayName, 19f, Colors.HOLIDAY_INK, bold = true, vAlign = VAlign.BOTTOM)
        }
    }
}
