package com.nobetmaker.core

import com.nobetmaker.core.export.Align
import com.nobetmaker.core.export.DrawOp
import com.nobetmaker.core.export.SheetLayout
import com.nobetmaker.core.export.VAlign
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.Line2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Testlerde düzeni PNG olarak görmek için masaüstü çizici (Android çizicisinin eşi). */
object AwtRenderer {
    /** Uygulamadaki Inter yazı tipleri; bulunamazsa DejaVu Sans. */
    private val fonts: Map<Int, Font> = run {
        val dir = listOf(File("../app/src/main/res/font"), File("app/src/main/res/font")).firstOrNull { it.isDirectory }
        mapOf(400 to "regular", 500 to "medium", 600 to "semibold", 700 to "bold", 800 to "extrabold").mapNotNull { (w, n) ->
            val f = dir?.let { File(it, "inter_$n.ttf") }?.takeIf { it.isFile } ?: return@mapNotNull null
            w to Font.createFont(Font.TRUETYPE_FONT, f)
        }.toMap()
    }

    private fun font(weight: Int, bold: Boolean, size: Float, tracking: Float): Font {
        val base = fonts.entries.minByOrNull { kotlin.math.abs(it.key - weight) }?.value
            ?: Font("DejaVu Sans", if (bold || weight >= 600) Font.BOLD else Font.PLAIN, 1)
        return base.deriveFont(mapOf(java.awt.font.TextAttribute.SIZE to size, java.awt.font.TextAttribute.TRACKING to tracking))
    }

    fun render(layout: SheetLayout, file: File, scale: Float = 1f) {
        val img = BufferedImage((layout.width * scale).toInt(), (layout.height * scale).toInt(), BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.scale(scale.toDouble(), scale.toDouble())
        for (op in layout.ops) {
            when (op) {
                is DrawOp.Rect -> {
                    val shape = RoundRectangle2D.Float(op.x, op.y, op.w, op.h, op.radius * 2, op.radius * 2)
                    op.fill?.let { g.color = Color(it, true); g.fill(shape) }
                    op.stroke?.let { g.color = Color(it, true); g.stroke = BasicStroke(op.strokeWidth); g.draw(shape) }
                }
                is DrawOp.Line -> {
                    g.color = Color(op.color, true)
                    g.stroke = BasicStroke(op.width)
                    g.draw(Line2D.Float(op.x1, op.y1, op.x2, op.y2))
                }
                is DrawOp.Text -> {
                    var size = op.size
                    var font = font(op.weight, op.bold, size, op.tracking)
                    var fm = g.getFontMetrics(font)
                    while (fm.stringWidth(op.text) > op.w && size > 8f) {
                        size -= 1f
                        font = font(op.weight, op.bold, size, op.tracking)
                        fm = g.getFontMetrics(font)
                    }
                    g.font = font
                    g.color = Color(op.color, true)
                    val tw = fm.stringWidth(op.text)
                    val x = when (op.align) {
                        Align.LEFT -> op.x
                        Align.CENTER -> op.x + (op.w - tw) / 2
                        Align.RIGHT -> op.x + op.w - tw
                    }
                    val y = when (op.vAlign) {
                        VAlign.TOP -> op.y + fm.ascent
                        VAlign.MIDDLE -> op.y + (op.h - (fm.ascent + fm.descent)) / 2 + fm.ascent
                        VAlign.BOTTOM -> op.y + op.h - fm.descent
                    }
                    g.drawString(op.text, x, y)
                }
            }
        }
        g.dispose()
        file.parentFile.mkdirs()
        ImageIO.write(img, "png", file)
    }
}
