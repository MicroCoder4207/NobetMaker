package com.nobetmaker.core.export

import com.nobetmaker.core.calendar.monthNameTr
import com.nobetmaker.core.calendar.trName
import java.io.OutputStream
import java.time.DayOfWeek

/** Hocaya giden resmi liste (§14.1): Takvim, Liste ve Özet sayfaları; puan/defter bilgisi içermez. */
object RosterExcel {

    fun fileName(view: MonthView): String {
        val month = monthNameTr(view.month.monthValue)
            .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g').replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')
            .replace('Ş', 'S').replace('Ç', 'C').replace('Ö', 'O').replace('Ü', 'U').replace('İ', 'I')
        val prefix = if (view.showActual) "Nobet_Listesi_Gerceklesen" else "Nobet_Listesi"
        return "${prefix}_${view.month.year}_${"%02d".format(view.month.monthValue)}_$month.xlsx"
    }

    fun write(view: MonthView, out: OutputStream) {
        val book = XlsxWorkbook()
        calendarSheet(book, view)
        listSheet(book, view)
        summarySheet(book, view)
        book.write(out)
    }

    private val title = CellStyle(bold = true, size = 18.0, color = "1F2433")
    private val subtitle = CellStyle(size = 11.0, color = "6B7280")
    private val header = CellStyle(bold = true, color = "FFFFFF", fill = "2B3A55", border = true, hAlign = "center")
    private val headerWeekend = header.copy(fill = "22304A")

    private fun calendarSheet(book: XlsxWorkbook, view: MonthView) {
        val sheet = book.sheet("Takvim")
        sheet.landscape = true
        sheet.fitToPage = true
        sheet.set(0, 0, view.title, subtitle)
        sheet.merge(0, 0, 0, 6)
        sheet.set(1, 0, view.monthTitle + if (view.showActual) " (gerçekleşen)" else "", title)
        sheet.merge(1, 0, 1, 6)
        sheet.rowHeight(1, 28.0)
        for (c in 0 until 7) {
            sheet.columnWidth(c, 19.0)
            sheet.set(3, c, DayOfWeek.of(c + 1).trName(), if (c >= 5) headerWeekend else header)
        }
        var row = 4
        for (week in view.weeks) {
            for ((c, cell) in week.withIndex()) {
                val person = if (view.showActual) cell.actual else cell.official
                val other = if (view.showActual) cell.official else cell.actual
                val lines = ArrayList<String>()
                lines += cell.date.dayOfMonth.toString()
                if (person != null) {
                    lines += person.name + if (other != null && other.id != person.id && !view.showActual) " (${other.name})" else ""
                }
                if (cell.inMonth && cell.holidayName != null) lines += cell.holidayName
                val fill = when {
                    !cell.inMonth -> null
                    person != null -> Colors.hex(Colors.tint(Colors.parse(person.color), 0.40f))
                    cell.holiday -> "FDECEC"
                    cell.weekend -> "F3F4F7"
                    else -> null
                }
                val style = CellStyle(
                    bold = cell.inMonth && person != null,
                    size = 11.0,
                    color = if (cell.inMonth) (if (cell.holiday) "C62828" else "1F2433") else "B8BEC8",
                    fill = fill,
                    border = true,
                    hAlign = "center",
                    vAlign = "center",
                    wrap = true,
                )
                sheet.set(row, c, if (cell.inMonth) lines.joinToString("\n") else lines.first(), style)
            }
            sheet.rowHeight(row, 54.0)
            row++
        }
        row++
        sheet.set(row, 0, "Kişi", header)
        sheet.set(row, 1, "Nöbet", header)
        sheet.set(row, 2, "Saat", header)
        row++
        for (t in view.totals) {
            sheet.set(row, 0, t.person.name, CellStyle(bold = true, border = true, fill = Colors.hex(Colors.tint(Colors.parse(t.person.color), 0.40f))))
            sheet.set(row, 1, t.count, CellStyle(border = true, hAlign = "center"))
            sheet.set(row, 2, t.hours, CellStyle(border = true, hAlign = "center"))
            row++
        }
        if (view.leaves.isNotEmpty()) {
            row++
            sheet.set(row, 0, "İzinler", CellStyle(bold = true))
            row++
            for (l in view.leaves) {
                sheet.set(row, 0, l)
                sheet.merge(row, 0, row, 6)
                row++
            }
        }
    }

    private fun listSheet(book: XlsxWorkbook, view: MonthView) {
        val sheet = book.sheet("Liste")
        listOf("Tarih", "Gün", "Nöbetçi", "Açıklama").forEachIndexed { c, h -> sheet.set(0, c, h, header) }
        sheet.columnWidth(0, 14.0); sheet.columnWidth(1, 12.0); sheet.columnWidth(2, 22.0); sheet.columnWidth(3, 40.0)
        view.days.forEachIndexed { i, cell ->
            val r = i + 1
            val person = if (view.showActual) cell.actual else cell.official
            val other = if (view.showActual) cell.official else cell.actual
            val note = listOfNotNull(
                cell.holidayName,
                if (other != null && person != null && other.id != person.id) {
                    if (view.showActual) "Resmi listede ${other.name}" else "${other.name} tuttu"
                } else null,
            ).joinToString(" · ")
            val fill = if (cell.holiday) "FDECEC" else if (cell.weekend) "F3F4F7" else null
            val base = CellStyle(border = true, fill = fill)
            sheet.set(r, 0, "%02d.%02d.%d".format(cell.date.dayOfMonth, cell.date.monthValue, cell.date.year), base)
            sheet.set(r, 1, cell.date.dayOfWeek.trName(), base)
            sheet.set(r, 2, person?.name ?: "", base.copy(bold = true))
            sheet.set(r, 3, note, base)
        }
    }

    private fun summarySheet(book: XlsxWorkbook, view: MonthView) {
        val sheet = book.sheet("Özet")
        listOf("Kişi", "Toplam nöbet", "Hafta içi", "Hafta sonu / tatil", "Saat").forEachIndexed { c, h -> sheet.set(0, c, h, header) }
        sheet.columnWidth(0, 18.0)
        for (c in 1..4) sheet.columnWidth(c, 16.0)
        view.totals.forEachIndexed { i, t ->
            val r = i + 1
            sheet.set(r, 0, t.person.name, CellStyle(bold = true, border = true))
            sheet.set(r, 1, t.count, CellStyle(border = true, hAlign = "center"))
            sheet.set(r, 2, t.count - t.weekendOrHoliday, CellStyle(border = true, hAlign = "center"))
            sheet.set(r, 3, t.weekendOrHoliday, CellStyle(border = true, hAlign = "center"))
            sheet.set(r, 4, t.hours, CellStyle(border = true, hAlign = "center"))
        }
        val r = view.totals.size + 1
        sheet.set(r, 0, "Toplam", CellStyle(bold = true, border = true, fill = "F3F4F7"))
        sheet.set(r, 1, view.totals.sumOf { it.count }, CellStyle(bold = true, border = true, hAlign = "center", fill = "F3F4F7"))
        sheet.set(r, 2, view.totals.sumOf { it.count - it.weekendOrHoliday }, CellStyle(bold = true, border = true, hAlign = "center", fill = "F3F4F7"))
        sheet.set(r, 3, view.totals.sumOf { it.weekendOrHoliday }, CellStyle(bold = true, border = true, hAlign = "center", fill = "F3F4F7"))
        sheet.set(r, 4, view.totals.sumOf { it.hours }, CellStyle(bold = true, border = true, hAlign = "center", fill = "F3F4F7"))
    }
}
