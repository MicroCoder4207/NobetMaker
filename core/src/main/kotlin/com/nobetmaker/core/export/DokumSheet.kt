package com.nobetmaker.core.export

import java.util.Locale

/**
 * Nöbet dökümünün çizimi (§14.3): kişi başına bir satır, solda kıdem eğrisi, sağda "nasıl hesaplandı".
 * Liste görselinde takvimin altına, PDF'te ikinci sayfaya çizilir.
 */
object DokumSheet {

    private const val M = 64f
    private const val ROW_H = 66f
    private const val BAND_H = 300f
    private val tr = Locale.forLanguageTag("tr")

    private val ahead = Colors.argb(255, 0xB4, 0x5F, 0x06)
    private val behind = Colors.argb(255, 0x1C, 0x6D, 0xD0)

    /** Dökümün [top]'tan itibaren kapladığı yükseklik. */
    fun height(dokum: Dokum): Float = 46f + 36f + dokum.rows.size * (ROW_H + 6f) + 24f + bandHeight(dokum)

    private fun bandHeight(dokum: Dokum): Float = maxOf(BAND_H, 62f + dokum.notes.size * 46f + 16f)

    fun draw(ops: MutableList<DrawOp>, dokum: Dokum, top: Float, width: Float) {
        val w = width
        ops += DrawOp.Text(M, top, 900f, 30f, "NÖBET DÖKÜMÜ", 22f, Colors.MUTED, align = Align.LEFT, weight = 700, tracking = 0.14f)
        ops += DrawOp.Text(w - M - 900f, top, 900f, 30f, "Herkes aynı kurallarla hesaplanır", 20f, Colors.MUTED, align = Align.RIGHT, weight = 500)

        class Col(val title: String, val x: Float, val w: Float)
        val cols = listOf(
            Col("", M, 260f), Col("ASİSTANLIK", M + 260f, 200f), Col("KATSAYI", M + 460f, 220f), Col("NÖBET", M + 680f, 150f),
            Col("BU AY HAFTA SONU", M + 830f, 290f), Col("CUMARTESİ", M + 1120f, 310f),
            Col("BAYRAM", M + 1430f, 190f), Col("RESMİ TATİL", M + 1620f, w - 2 * M - 1620f),
        )
        val headY = top + 46f
        cols.forEach { c -> ops += DrawOp.Text(c.x + 4f, headY, c.w - 8f, 28f, c.title, 16f, Colors.MUTED, align = Align.LEFT, weight = 700, tracking = 0.1f) }

        dokum.rows.forEachIndexed { i, r ->
            val y = headY + 36f + i * (ROW_H + 6f)
            ops += DrawOp.Rect(M, y, w - 2 * M, ROW_H, fill = if (i % 2 == 0) Colors.CELL else Colors.WHITE, radius = 16f)
            val color = Colors.parse(r.person.color)
            ops += DrawOp.Rect(M + 18f, y + (ROW_H - 24f) / 2, 24f, 24f, fill = color, radius = 12f)
            ops += DrawOp.Text(M + 56f, y, 240f, ROW_H, r.person.name, 28f, Colors.INK, align = Align.LEFT, weight = 700)

            val stage = r.stage
            if (stage == null) {
                ops += DrawOp.Text(cols[1].x + 4f, y, cols[1].w, ROW_H, "—", 25f, Colors.FAINT, align = Align.LEFT, weight = 600)
            } else {
                val main = if (stage == "uzatma") "uzatma" else stage.substringBefore(" yıl") + " yıl"
                val sub = if (stage == "uzatma") "${dokum.curveYears} yıl doldu" else stage.substringAfter("yıl ").trim()
                ops += DrawOp.Text(cols[1].x + 4f, y + 8f, cols[1].w, 30f, main, 25f, Colors.INK, align = Align.LEFT, weight = 600)
                ops += DrawOp.Text(cols[1].x + 4f, y + 36f, cols[1].w, 24f, sub, 18f, Colors.MUTED, align = Align.LEFT, weight = 500)
            }

            ops += DrawOp.Text(cols[2].x + 4f, y + 8f, cols[2].w, 30f, Dokum.coef(r.duty), 25f, Colors.INK, align = Align.LEFT, weight = 600)
            val hardText = if (r.noWeekend) "hafta sonu yok" else "tatil ${Dokum.coef(r.hard)}"
            ops += DrawOp.Text(cols[2].x + 4f, y + 36f, cols[2].w, 24f, hardText, 18f, Colors.MUTED, align = Align.LEFT, weight = 500)

            val count = if (r.fixedCount != null) "${r.count} *" else "${r.count}"
            ops += DrawOp.Text(cols[3].x + 4f, y + 8f, cols[3].w, 30f, count, 27f, Colors.INK, align = Align.LEFT, weight = 800)
            ops += DrawOp.Text(cols[3].x + 4f, y + 36f, cols[3].w, 24f, "${r.hours} saat", 18f, Colors.MUTED, align = Align.LEFT, weight = 500)

            listOf(r.friday to "Cuma", r.saturday to "Cmt", r.sunday to "Pazar").forEachIndexed { k, (has, label) ->
                val px = cols[4].x + 4f + k * 92f
                val py = y + (ROW_H - 38f) / 2
                if (has) {
                    ops += DrawOp.Rect(px, py, 84f, 38f, fill = color, radius = 19f)
                    ops += DrawOp.Text(px, py, 84f, 38f, label, 18f, Colors.onColor(color), weight = 700)
                } else {
                    ops += DrawOp.Rect(px, py, 84f, 38f, stroke = Colors.GRID, strokeWidth = 2f, radius = 19f)
                    ops += DrawOp.Text(px, py, 84f, 38f, label, 18f, Colors.FAINT, weight = 600)
                }
            }

            val satMain = Dokum.saturdayMain(r)
            ops += DrawOp.Text(cols[5].x + 4f, y + 8f, cols[5].w, 30f, satMain, 25f, if (r.noWeekend) Colors.FAINT else Colors.INK, align = Align.LEFT, weight = 600)
            ops += DrawOp.Text(cols[5].x + 4f, y + 36f, cols[5].w, 24f, Dokum.saturdaySub(r, dokum.rotationMonths), 18f, Colors.MUTED, align = Align.LEFT, weight = 500)
            balance(ops, r.bayram, cols[6].x + 4f, y, cols[6].w)
            balance(ops, r.resmi, cols[7].x + 4f, y, cols[7].w)
        }

        val bandY = headY + 36f + dokum.rows.size * (ROW_H + 6f) + 24f
        val bandH = bandHeight(dokum)
        val chartW = 860f
        val curve = dokum.curve
        val notesX = if (curve != null) M + chartW + 40f else M
        if (curve != null) drawCurve(ops, dokum, M, bandY, chartW, bandH)
        val notesW = w - M - notesX
        ops += DrawOp.Text(notesX, bandY + 16f, notesW, 28f, "NASIL HESAPLANDI", 17f, Colors.MUTED, align = Align.LEFT, weight = 700, tracking = 0.1f)
        dokum.notes.forEachIndexed { i, t ->
            val y = bandY + 62f + i * 46f
            ops += DrawOp.Rect(notesX, y + 13f, 8f, 8f, fill = Colors.HEADER, radius = 4f)
            ops += DrawOp.Text(notesX + 22f, y, notesW - 22f, 34f, t, 21f, Colors.INK, align = Align.LEFT, weight = 500)
        }
    }

    private fun balance(ops: MutableList<DrawOp>, v: Double?, x: Float, y: Float, w: Float) {
        val color = when {
            v == null -> Colors.FAINT
            kotlin.math.abs(v) < 0.75 -> Colors.MUTED
            v > 0 -> ahead
            else -> behind
        }
        ops += DrawOp.Text(x, y, w, ROW_H, Dokum.balanceText(v), 22f, color, align = Align.LEFT, weight = 600)
    }

    private fun drawCurve(ops: MutableList<DrawOp>, dokum: Dokum, x: Float, y: Float, w: Float, h: Float) {
        ops += DrawOp.Rect(x, y, w, h, fill = Colors.CELL, radius = 18f)
        ops += DrawOp.Text(x + 24f, y + 16f, w - 48f, 28f, "KIDEM EĞRİSİ · nöbet katsayısı", 17f, Colors.MUTED, align = Align.LEFT, weight = 700, tracking = 0.1f)
        val years = dokum.curveYears
        val maxM = years * 12.0 + 18.0
        val px0 = x + 70f
        val px1 = x + w - 30f
        val py0 = y + h - 50f
        val py1 = y + 70f
        fun gx(m: Double) = (px0 + (px1 - px0) * m / maxM).toFloat()
        fun gy(v: Double) = (py0 - (py0 - py1) * (v - 0.45) / 0.6).toFloat()
        for (v in listOf(0.5, 0.75, 1.0)) {
            ops += DrawOp.Line(px0, gy(v), px1, gy(v), Colors.GRID, 1.5f)
            ops += DrawOp.Text(x + 14f, gy(v) - 12f, 50f, 24f, Dokum.coef(v), 15f, Colors.MUTED, align = Align.RIGHT, weight = 500)
        }
        for (yr in 1..years + 1) {
            val label = if (yr == years + 1) "uzatma" else "$yr. yıl"
            ops += DrawOp.Text(gx((yr - 1) * 12.0 + 6) - 50f, py0 + 10f, 100f, 22f, label, 15f, Colors.MUTED, weight = 600)
            if (yr > 1) ops += DrawOp.Line(gx((yr - 1) * 12.0), py0, gx((yr - 1) * 12.0), py0 + 6f, Colors.GRID, 1.5f)
        }
        var prev = 0.0
        var m = 0.25
        while (m <= maxM) {
            val a = dokum.curveAt(prev)
            val b = dokum.curveAt(m)
            ops += if (kotlin.math.abs(a - b) < 0.05) DrawOp.Line(gx(prev), gy(a), gx(m), gy(b), Colors.HEADER, 4f)
            else DrawOp.Line(gx(m), gy(a), gx(m), gy(b), Colors.argb(255, 0x9A, 0xA4, 0xB5), 2f)
            prev = m
            m += 0.25
        }
        // İsimler üst üste binmesin: her isim, önceki isimlere ve noktalara çarpmayan ilk yere (üst, alt, yanlar) yazılır.
        val points = dokum.rows.mapNotNull { r -> r.curveMonths?.let { r to minOf(it, maxM - 3) } }.sortedBy { it.second }
        // Kutu alanları dıştaki x/y/w/h ile karışmasın diye ayrı adlı.
        class Box(val left: Float, val top: Float, val bw: Float, val bh: Float) {
            fun hits(o: Box) = left < o.left + o.bw && o.left < left + bw && top < o.top + o.bh && o.top < top + bh
        }
        val centers = points.map { (r, mm) ->
            val color = Colors.parse(r.person.color)
            val cx = gx(mm)
            val cy = gy(dokum.curveAt(mm))
            ops += DrawOp.Rect(cx - 11f, cy - 11f, 22f, 22f, fill = color, stroke = Colors.WHITE, strokeWidth = 3f, radius = 11f)
            cx to cy
        }
        val placed = centers.map { (cx, cy) -> Box(cx - 13f, cy - 13f, 26f, 26f) }.toMutableList()
        points.forEachIndexed { i, (r, _) ->
            val (cx, cy) = centers[i]
            val tw = r.person.name.length * 11f + 10f
            val th = 26f
            val options = listOf(
                Box(cx - tw / 2, cy - 42f, tw, th), Box(cx - tw / 2, cy + 14f, tw, th),
                Box(cx + 14f, cy + 6f, tw, th), Box(cx - 14f - tw, cy + 6f, tw, th),
                Box(cx + 14f, cy - 34f, tw, th), Box(cx - 14f - tw, cy - 34f, tw, th),
                Box(cx - tw / 2, cy + 40f, tw, th), Box(cx - tw / 2, cy - 68f, tw, th),
            )
            val box = options.firstOrNull { o -> o.left >= x + 4f && o.left + o.bw <= x + w - 4f && placed.none { it.hits(o) } } ?: options[0]
            placed += box
            ops += DrawOp.Text(box.left, box.top, box.bw, box.bh, r.person.name, 18f, Colors.shade(Colors.parse(r.person.color), 0.8f), weight = 700)
        }
    }

    /** PDF için ikinci sayfa: başlık ve döküm (A4 yatay). */
    fun page(dokum: Dokum, title: String): SheetLayout {
        val w = RosterSheet.WIDTH.toFloat()
        val h = maxOf(RosterSheet.HEIGHT.toFloat(), 230f + height(dokum) + 60f)
        val ops = ArrayList<DrawOp>()
        ops += DrawOp.Rect(0f, 0f, w, h, fill = Colors.WHITE)
        ops += DrawOp.Text(M, 48f, 1300f, 30f, title.uppercase(tr), 22f, Colors.MUTED, align = Align.LEFT, weight = 600, tracking = 0.14f)
        ops += DrawOp.Text(M, 82f, 1600f, 100f, "${dokum.title} · Döküm", 86f, Colors.INK, align = Align.LEFT, weight = 800, tracking = -0.02f)
        draw(ops, dokum, 230f, w)
        ops += DrawOp.Text(M, h - 46f, w - 2 * M, 28f, "NöbetMaker", 19f, Colors.FAINT, align = Align.RIGHT, weight = 600)
        return SheetLayout(RosterSheet.WIDTH, h.toInt(), ops)
    }
}
