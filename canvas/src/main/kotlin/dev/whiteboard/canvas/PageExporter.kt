package dev.whiteboard.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import dev.whiteboard.core.model.*
import java.io.OutputStream
import kotlin.math.*

class PageExporter(private val assets: AssetProvider? = null) {
    fun bitmap(page: Page, maxDimension: Int = 4096): Bitmap {
        require(maxDimension in 64..8192)
        val bounds = (page.contentBounds ?: Bounds(0f, 0f, 960f, 640f)).expand(32f)
        val scale = min(2f, min(maxDimension / max(bounds.width, bounds.height), sqrt(16_000_000f / max(1f, bounds.width * bounds.height))))
        val width = ceil(bounds.width * scale).toInt().coerceAtLeast(1)
        val height = ceil(bounds.height * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.scale(scale, scale)
            canvas.translate(-bounds.left, -bounds.top)
            BoardRenderer(assets, strictAssets = true).drawPage(canvas, page, bounds, scale)
            return bitmap
        } catch (error: Exception) {
            bitmap.recycle()
            throw error
        }
    }
    fun png(page: Page, output: OutputStream) {
        val bitmap = bitmap(page)
        try {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Export failed" }
        } finally {
            bitmap.recycle()
        }
    }
}
