package dev.whiteboard.canvas

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader

/** Deterministic paper grain in document coordinates; no assets, random state or per-point work. */
internal class PencilTexture {
    private val shader = BitmapShader(
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            val pixels = IntArray(32 * 32) { index ->
                val x = index % 32
                val y = index / 32
                var noise = x * 374761393 + y * 668265263
                noise = (noise xor (noise ushr 13)) * 1274126177
                val alpha = 96 + ((noise xor (noise ushr 16)) and 255) * 159 / 255
                Color.argb(alpha, 255, 255, 255)
            }
            setPixels(pixels, 0, 32, 0, 0, 32, 32)
        },
        Shader.TileMode.REPEAT,
        Shader.TileMode.REPEAT
    )
    private val colors = LinkedHashMap<Int, PorterDuffColorFilter>(16, 0.75f, true)

    fun apply(paint: Paint, color: Int) {
        // InkStyle's color alpha and opacity are already applied by BoardRenderer.configure.
        val opaque = color or 0xFF000000.toInt()
        val tint = colors.getOrPut(opaque) { PorterDuffColorFilter(opaque, PorterDuff.Mode.SRC_IN) }
        if (colors.size > 16) colors.remove(colors.keys.first())
        paint.shader = shader
        paint.colorFilter = tint
    }
}
