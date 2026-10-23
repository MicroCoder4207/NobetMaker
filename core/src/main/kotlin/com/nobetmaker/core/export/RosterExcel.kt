package com.nobetmaker.core.export

import com.nobetmaker.core.calendar.monthNameTr
import com.nobetmaker.core.calendar.trName
import com.nobetmaker.core.model.Person
import java.io.OutputStream
import java.time.DayOfWeek
import java.util.Locale

/** Hocaya giden resmi liste (§14.1): Takvim, Liste ve Özet sayfaları; puan/defter bilgisi içermez. */
object RosterExcel {

    fun fileName(view: MonthView): String {
        val month = monthNameTr(view.month.monthValue)
            .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g').replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')
            .replace('Ş', 'S').replace('Ç', 'C').replace('Ö', 'O').replace('Ü', 'U').replace('İ', 'I')
        val prefix = if (view.showActual) "Nobet_Listesi_Gerceklesen" else "Nobet_Listesi"
        return "${prefix}_${view.month.year}_${"%02d".format(view.month.monthValue)}_$month.xlsx"
    }

    /** @param dokum verilirse "Döküm" sayfası eklenir (§14.3). */
    fun write(view: MonthView, out: OutputStream, dokum: Dokum? = null) {
        val book = XlsxWorkbook()
        calendarSheet(book, view)
        listSheet(book, view)
        summarySheet(book, view)
        if (dokum != null) dokumSheet(book, dokum)
        book.write(out)
    }

    private val tr = Locale.forLanguageTag("tr")
    private const val INK = "141824"
    private const val MUTED = "6B7280"
    private const val FAINT = "B8BEC8"
    private const val NAVY = "2B3A55"
    private const val NAVY_DARK = "1F2A40"
    private const val CELL = "F4F5F8"
    private const val CELL_WEEKEND = "EAEDF3"
    private const val CELL_HOLIDAY = "FDEBEB"
    private const val HOLIDAY_INK = "C62828"
    private const val LINE = "E3E6EB"

    private val eyebrow = CellStyle(bold = true, size = 9.0, color = MUTED)
    private val title = CellStyle(bold = true, size = 22.0, color = INK)
    private val subtitle = CellStyle(size = 10.0, color = MUTED)
    private val header = CellStyle(bold = true, size = 10.0, color = "FFFFFF", fill = NAVY, hAlign = "center")
    private val headerLeft = header.copy(hAlign = "left", indent = 1)
    private val tableCell = CellStyle(size = 10.5, border = true, borderColor = LINE, borderSides = "b")

    private fun personFill(p: Person) = Colors.hex(Colors.parse(p.color))
    private fun personInk(p: Person) = Colors.hex(Colors.onColor(Colors.parse(p.color)))

    /** Kişi adının yazıldığı dolu renkli hücre. */
    private fun personCell(p: Person, base: CellStyle) = base.copy(bold = true, fill = personFill(p), color = personInk(p))

    private fun calendarSheet(book: XlsxWorkbook, view: MonthView) {
        val sheet = book.sheet("Takvim")
        sheet.landscape = true
        sheet.fitToPage = true
        sheet.showGridLines = false
        for (c in 0 until 7) sheet.columnWidth(c, 21.0)

        sheet.set(0, 0, view.title.uppercase(tr), eyebrow)
        sheet.merge(0, 0, 0, 6)
        sheet.set(1, 0, view.monthTitle, title)
        sheet.merge(1, 0, 1, 4)
        sheet.rowHeight(1, 34.0)
        sheet.set(1, 5, if (view.showActual) "Gerçekleşen liste" else "Resmi liste", CellStyle(bold = true, size = 10.0, color = NAVY, fill = "E2E7F1", hAlign = "center"))
        sheet.merge(1, 5, 1, 6)
        sheet.rowHeight(2, 8.0)

        val headRow = 3
        for (c in 0 until 7) {
            sheet.set(headRow, c, DayOfWeek.of(c + 1).trName().uppercase(tr), header.copy(fill = if (c >= 5) NAVY_DARK else NAVY))
        }
        sheet.rowHeight(headRow, 24.0)

        // Her hafta üç satır: gün numarası, nöbetçi, tatil adı ya da not. Beyaz kenarlıklar günleri kutu gibi ayırır.
        val tile = CellStyle(size = 10.0, border = true, borderColor = "FFFFFF", borderStyle = "medium")
        var row = headRow + 1
        for (week in view.weeks) {
            for ((c, cell) in week.withIndex()) {
                val person = if (view.showActual) cell.actual else cell.official
                val other = if (view.showActual) cell.official else cell.actual
                val bg = when {
                    !cell.inMonth -> null
                    cell.holiday -> CELL_HOLIDAY
                    cell.weekend -> CELL_WEEKEND
                    else -> CELL
                }
                val number = cell.date.dayOfMonth.toString() + if (cell.inMonth && cell.visit) (if (cell.visitUncertain) "   VİZİT?" else "   VİZİT") else ""
                sheet.set(
                    row, c, number,
                    tile.copy(bold = true, fill = bg, color = if (!cell.inMonth) FAINT else if (cell.holiday) HOLIDAY_INK else INK, borderSides = "lrt", indent = 1),
                )
                val nameStyle = tile.copy(size = 12.0, hAlign = "center", borderSides = "lr", fill = bg, color = FAINT)
                when {
                    person == null && cell.specialist -> sheet.set(row + 1, c, "Uzman", nameStyle.copy(color = FAINT))
                    person == null -> sheet.set(row + 1, c, "", nameStyle)
                    !cell.inMonth -> sheet.set(row + 1, c, person.name, nameStyle)
                    else -> sheet.set(row + 1, c, person.name, personCell(person, nameStyle))
                }
                val note = when {
                    !cell.inMonth -> ""
                    cell.holidayName != null -> cell.holidayName
                    other != null && person != null && other.id != person.id -> if (view.showActual) "resmi: ${other.name}" else "${other.name} tuttu"
                    else -> ""
                }
                sheet.set(
                    row + 2, c, note,
                    tile.copy(size = 8.5, hAlign = "center", fill = bg, color = if (cell.holidayName != null) HOLIDAY_INK else MUTED, borderSides = "lrb", bold = cell.holidayName == null && note.isNotEmpty()),
                )
            }
            sheet.rowHeight(row, 19.0)
            sheet.rowHeight(row + 1, 30.0)
            sheet.rowHeight(row + 2, 17.0)
            row += 3
        }

        row++
        sheet.set(row, 0, "NÖBET SAYILARI", eyebrow)
        row++
        sheet.set(row, 0, "Kişi", headerLeft)
        sheet.set(row, 1, "Nöbet", header)
        sheet.set(row, 2, "Saat", header)
        sheet.rowHeight(row, 22.0)
        row++
        for (t in view.totals) {
            sheet.set(row, 0, t.person.name, personCell(t.person, tableCell.copy(indent = 1)))
            sheet.set(row, 1, t.count, tableCell.copy(hAlign = "center"))
            sheet.set(row, 2, t.hours, tableCell.copy(hAlign = "center"))
            sheet.rowHeight(row, 20.0)
            row++
        }
        if (view.leaves.isNotEmpty()) {
            row++
            sheet.set(row, 0, "İZİNLER", eyebrow)
            row++
            for (l in view.leaves) {
                sheet.set(row, 0, l, CellStyle(size = 10.0))
                sheet.merge(row, 0, row, 6)
                row++
            }
        }
    }

    private fun listSheet(book: XlsxWorkbook, view: MonthView) {
        val sheet = book.sheet("Liste")
        sheet.fitToPage = true
        sheet.showGridLines = false
        sheet.frozenRows = 1
        listOf("Tarih", "Gün", "Nöbetçi", "Açıklama").forEachIndexed { c, h -> sheet.set(0, c, h, headerLeft) }
        sheet.rowHeight(0, 24.0)
        sheet.columnWidth(0, 14.0); sheet.columnWidth(1, 14.0); sheet.columnWidth(2, 20.0); sheet.columnWidth(3, 42.0)
        view.days.forEachIndexed { i, cell ->
            val r = i + 1
            val person = if (view.showActual) cell.actual else cell.official
            val other = if (view.showActual) cell.official else cell.actual
            val note = listOfNotNull(
                cell.holidayName,
                if (cell.visit) (if (cell.visitUncertain) "vizit belirsiz" else "vizit") else null,
                if (other != null && person != null && other.id != person.id) {
                    if (view.showActual) "Resmi listede ${other.name}" else "${other.name} tuttu"
                } else null,
            ).joinToString(" · ")
            val fill = if (cell.holiday) CELL_HOLIDAY else if (cell.weekend) CELL_WEEKEND else null
            val base = tableCell.copy(fill = fill, indent = 1, color = if (cell.holiday) HOLIDAY_INK else INK)
            sheet.set(r, 0, "%02d.%02d.%d".format(cell.date.dayOfMonth, cell.date.monthValue, cell.date.year), base)
            sheet.set(r, 1, cell.date.dayOfWeek.trName(), base)
            when {
                person != null -> sheet.set(r, 2, person.name, personCell(person, base.copy(color = INK)))
                cell.specialist -> sheet.set(r, 2, "Uzman", base.copy(color = MUTED))
                else -> sheet.set(r, 2, "", base)
            }
            sheet.set(r, 3, note, base.copy(color = if (cell.holiday) HOLIDAY_INK else MUTED))
            sheet.rowHeight(r, 20.0)
        }
    }

    private fun summarySheet(book: XlsxWorkbook, view: MonthView) {
        val sheet = book.sheet("Özet")
        sheet.fitToPage = true
        sheet.showGridLines = false
        sheet.frozenRows = 1
        sheet.set(0, 0, "Kişi", headerLeft)
        listOf("Toplam nöbet", "Hafta içi", "Hafta sonu / tatil", "Saat").forEachIndexed { i, h -> sheet.set(0, i + 1, h, header) }
        sheet.rowHeight(0, 24.0)
        sheet.columnWidth(0, 18.0)
        for (c in 1..4) sheet.columnWidth(c, 17.0)
        view.totals.forEachIndexed { i, t ->
            val r = i + 1
            val num = tableCell.copy(hAlign = "center")
            sheet.set(r, 0, t.person.name, personCell(t.person, tableCell.copy(indent = 1)))
            sheet.set(r, 1, t.count, num.copy(bold = true))
            sheet.set(r, 2, t.count - t.weekendOrHoliday, num)
            sheet.set(r, 3, t.weekendOrHoliday, num)
            sheet.set(r, 4, t.hours, num)
            sheet.rowHeight(r, 20.0)
        }
        val r = view.totals.size + 1
        val total = CellStyle(bold = true, size = 10.5, fill = CELL_WEEKEND, hAlign = "center")
        sheet.set(r, 0, "Toplam", total.copy(hAlign = "left", indent = 1))
        sheet.set(r, 1, view.totals.sumOf { it.count }, total)
        sheet.set(r, 2, view.totals.sumOf { it.count - it.weekendOrHoliday }, total)
        sheet.set(r, 3, view.totals.sumOf { it.weekendOrHoliday }, total)
        sheet.set(r, 4, view.totals.sumOf { it.hours }, total)
        sheet.rowHeight(r, 22.0)
    }

    private fun dokumSheet(book: XlsxWorkbook, dokum: Dokum) {
        val sheet = book.sheet("Döküm")
        sheet.landscape = true
        sheet.fitToPage = true
        sheet.showGridLines = false
        sheet.frozenRows = 1
        val heads = listOf(
            "Kişi", "Asistanlık", "Nöbet katsayısı", "Zor gün katsayısı", "Nöbet", "Saat",
            "Cuma", "Cumartesi", "Pazar", "Cumartesi (son ${dokum.rotationMonths} ay)", "Cumartesi sırası", "Bayram", "Resmi tatil",
        )
        heads.forEachIndexed { c, h -> sheet.set(0, c, h, if (c <= 1) headerLeft else header) }
        sheet.rowHeight(0, 24.0)
        sheet.columnWidth(0, 16.0); sheet.columnWidth(1, 16.0)
        for (c in 2..12) sheet.columnWidth(c, if (c >= 10) 18.0 else 13.0)
        val num = tableCell.copy(hAlign = "center")
        dokum.rows.forEachIndexed { i, r ->
            val row = i + 1
            sheet.set(row, 0, r.person.name, personCell(r.person, tableCell.copy(indent = 1)))
            sheet.set(row, 1, r.stage ?: "—", tableCell.copy(indent = 1))
            sheet.set(row, 2, Dokum.coef(r.duty), num)
            sheet.set(row, 3, if (r.noWeekend) "yok" else Dokum.coef(r.hard), num)
            sheet.set(row, 4, if (r.fixedCount != null) "${r.count} (sabit)" else "${r.count}", num.copy(bold = true))
            sheet.set(row, 5, r.hours, num)
            listOf(r.friday, r.saturday, r.sunday).forEachIndexed { k, has -> sheet.set(row, 6 + k, if (has) "✓" else "", num) }
            sheet.set(row, 9, r.visits?.toString() ?: "—", num)
            sheet.set(row, 10, Dokum.saturdayMain(r) + (r.turn?.let { " · sırada $it." } ?: ""), num)
            sheet.set(row, 11, Dokum.balanceText(r.bayram), num)
            sheet.set(row, 12, Dokum.balanceText(r.resmi), num)
            sheet.rowHeight(row, 20.0)
        }
        var row = dokum.rows.size + 2
        sheet.set(row, 0, "NASIL HESAPLANDI", eyebrow)
        for (note in dokum.notes) {
            row++
            sheet.set(row, 0, "•  $note", CellStyle(size = 10.5, color = INK))
        }
    }
}
