package com.nobetmaker.core.export

/** Platformdan bağımsız çizim komutları; Android (PNG/PDF) ve masaüstü önizleme aynı düzeni çizer. */
sealed class DrawOp {
    class Rect(
        val x: Float, val y: Float, val w: Float, val h: Float,
        val fill: Int? = null, val stroke: Int? = null, val strokeWidth: Float = 1f, val radius: Float = 0f,
    ) : DrawOp()

    class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: Int, val width: Float = 1f) : DrawOp()

    /** Kutu içine hizalanmış tek satır metin; sığmazsa çizici yazıyı küçültür. */
    class Text(
        val x: Float, val y: Float, val w: Float, val h: Float,
        val text: String, val size: Float, val color: Int,
        val bold: Boolean = false,
        val align: Align = Align.CENTER,
        val vAlign: VAlign = VAlign.MIDDLE,
    ) : DrawOp()
}

enum class Align { LEFT, CENTER, RIGHT }
enum class VAlign { TOP, MIDDLE, BOTTOM }

class SheetLayout(val width: Int, val height: Int, val ops: List<DrawOp>)

object Colors {
    fun parse(hex: String): Int {
        val h = hex.removePrefix("#")
        val v = h.toLong(16).toInt()
        return if (h.length <= 6) v or (0xFF shl 24) else v
    }

    fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b

    /** Rengi beyazla karıştırır (0 = beyaz, 1 = renk). */
    fun tint(color: Int, amount: Float): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        fun mix(c: Int) = (255 + (c - 255) * amount).toInt().coerceIn(0, 255)
        return argb(255, mix(r), mix(g), mix(b))
    }

    /** Rengi koyulaştırır (0 = siyah, 1 = renk). */
    fun shade(color: Int, amount: Float): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return argb(255, (r * amount).toInt(), (g * amount).toInt(), (b * amount).toInt())
    }

    fun hex(color: Int): String = String.format("%06X", color and 0xFFFFFF)

    val INK = argb(255, 0x1F, 0x24, 0x33)
    val MUTED = argb(255, 0x6B, 0x72, 0x80)
    val FAINT = argb(255, 0xB8, 0xBE, 0xC8)
    val GRID = argb(255, 0xD5, 0xD9, 0xE0)
    val WEEKEND = argb(255, 0xF3, 0xF4, 0xF7)
    val HOLIDAY = argb(255, 0xFD, 0xEC, 0xEC)
    val HOLIDAY_INK = argb(255, 0xC6, 0x28, 0x28)
    val WHITE = argb(255, 255, 255, 255)
    val HEADER = argb(255, 0x2B, 0x3A, 0x55)
}
