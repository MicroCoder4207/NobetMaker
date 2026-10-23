package com.nobetmaker.core.export

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Hücre biçimi. Renkler "RRGGBB". */
data class CellStyle(
    val bold: Boolean = false,
    val size: Double = 11.0,
    val color: String = "1F2433",
    val fill: String? = null,
    val border: Boolean = false,
    val hAlign: String = "left",
    val vAlign: String = "center",
    val wrap: Boolean = false,
)

/**
 * Dış kütüphane gerektirmeyen küçük bir .xlsx yazıcısı: satır içi metin, sayı, biçim,
 * birleştirilmiş hücre, sütun genişliği, satır yüksekliği ve yatay/tek sayfa baskı ayarı.
 */
class XlsxWorkbook {
    private val sheets = ArrayList<XlsxSheet>()
    private val styles = ArrayList<CellStyle>().apply { add(CellStyle()) }

    fun sheet(name: String): XlsxSheet = XlsxSheet(name.take(31), this).also { sheets += it }

    internal fun styleIndex(style: CellStyle): Int {
        val i = styles.indexOf(style)
        if (i >= 0) return i
        styles += style
        return styles.size - 1
    }

    fun write(out: OutputStream) {
        val zip = ZipOutputStream(out)
        fun entry(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        entry("[Content_Types].xml", contentTypes())
        entry("_rels/.rels", XML + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
        entry("xl/workbook.xml", workbook())
        entry("xl/_rels/workbook.xml.rels", workbookRels())
        entry("xl/styles.xml", stylesXml())
        sheets.forEachIndexed { i, s -> entry("xl/worksheets/sheet${i + 1}.xml", s.xml()) }
        zip.finish()
    }

    private fun contentTypes() = XML + buildString {
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        sheets.indices.forEach {
            append("""<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun workbook() = XML + buildString {
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        sheets.forEachIndexed { i, s -> append("""<sheet name="${esc(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
        append("</sheets></workbook>")
    }

    private fun workbookRels() = XML + buildString {
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        sheets.indices.forEach {
            append("""<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""")
        }
        append("""<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private fun stylesXml(): String {
        val fonts = styles.map { Triple(it.bold, it.size, it.color) }.distinct()
        val fills = styles.mapNotNull { it.fill }.distinct()
        return XML + buildString {
            append("""<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            append("""<fonts count="${fonts.size}">""")
            fonts.forEach { (bold, size, color) ->
                append("<font>")
                if (bold) append("<b/>")
                append("""<sz val="$size"/><color rgb="FF$color"/><name val="Calibri"/><family val="2"/></font>""")
            }
            append("</fonts>")
            append("""<fills count="${fills.size + 2}"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>""")
            fills.forEach { append("""<fill><patternFill patternType="solid"><fgColor rgb="FF$it"/><bgColor indexed="64"/></patternFill></fill>""") }
            append("</fills>")
            append("""<borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border>""")
            append("""<border><left style="thin"><color rgb="FFC8CDD5"/></left><right style="thin"><color rgb="FFC8CDD5"/></right><top style="thin"><color rgb="FFC8CDD5"/></top><bottom style="thin"><color rgb="FFC8CDD5"/></bottom><diagonal/></border></borders>""")
            append("""<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""")
            append("""<cellXfs count="${styles.size}">""")
            styles.forEach { s ->
                val font = fonts.indexOf(Triple(s.bold, s.size, s.color))
                val fill = s.fill?.let { fills.indexOf(it) + 2 } ?: 0
                val border = if (s.border) 1 else 0
                append("""<xf numFmtId="0" fontId="$font" fillId="$fill" borderId="$border" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">""")
                append("""<alignment horizontal="${s.hAlign}" vertical="${s.vAlign}"${if (s.wrap) " wrapText=\"1\"" else ""}/></xf>""")
            }
            append("</cellXfs>")
            append("""<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""")
            append("</styleSheet>")
        }
    }

    companion object {
        internal const val XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
        internal fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }
}

class XlsxSheet internal constructor(val name: String, private val book: XlsxWorkbook) {
    private class Cell(val value: Any, val style: Int)

    private val cells = java.util.TreeMap<Int, java.util.TreeMap<Int, Cell>>()
    private val merges = ArrayList<String>()
    private val colWidths = java.util.TreeMap<Int, Double>()
    private val rowHeights = HashMap<Int, Double>()
    var landscape = false
    var fitToPage = false

    /** Satır ve sütun 0'dan başlar. */
    fun set(row: Int, col: Int, value: Any, style: CellStyle = CellStyle()) {
        cells.getOrPut(row) { java.util.TreeMap() }[col] = Cell(value, book.styleIndex(style))
    }

    fun merge(row1: Int, col1: Int, row2: Int, col2: Int) {
        merges += "${ref(row1, col1)}:${ref(row2, col2)}"
    }

    fun columnWidth(col: Int, width: Double) { colWidths[col] = width }
    fun rowHeight(row: Int, height: Double) { rowHeights[row] = height }

    internal fun xml(): String = XlsxWorkbook.XML + buildString {
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        if (fitToPage) append("""<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>""")
        if (colWidths.isNotEmpty()) {
            append("<cols>")
            colWidths.forEach { (c, w) -> append("""<col min="${c + 1}" max="${c + 1}" width="$w" customWidth="1"/>""") }
            append("</cols>")
        }
        append("<sheetData>")
        val rows = (cells.keys + rowHeights.keys).toSortedSet()
        for (r in rows) {
            append("""<row r="${r + 1}"""")
            rowHeights[r]?.let { append(""" ht="$it" customHeight="1"""") }
            append(">")
            cells[r]?.forEach { (c, cell) ->
                val ref = ref(r, c)
                when (val v = cell.value) {
                    is Number -> append("""<c r="$ref" s="${cell.style}"><v>$v</v></c>""")
                    else -> append("""<c r="$ref" s="${cell.style}" t="inlineStr"><is><t xml:space="preserve">${XlsxWorkbook.esc(v.toString())}</t></is></c>""")
                }
            }
            append("</row>")
        }
        append("</sheetData>")
        if (merges.isNotEmpty()) {
            append("""<mergeCells count="${merges.size}">""")
            merges.forEach { append("""<mergeCell ref="$it"/>""") }
            append("</mergeCells>")
        }
        append("""<pageMargins left="0.4" right="0.4" top="0.5" bottom="0.5" header="0.3" footer="0.3"/>""")
        if (landscape || fitToPage) {
            append("<pageSetup paperSize=\"9\"")
            if (landscape) append(" orientation=\"landscape\"")
            if (fitToPage) append(" fitToWidth=\"1\" fitToHeight=\"1\"")
            append("/>")
        }
        append("</worksheet>")
    }

    companion object {
        fun ref(row: Int, col: Int): String {
            var c = col
            val sb = StringBuilder()
            do {
                sb.insert(0, ('A' + c % 26))
                c = c / 26 - 1
            } while (c >= 0)
            return "$sb${row + 1}"
        }
    }
}
