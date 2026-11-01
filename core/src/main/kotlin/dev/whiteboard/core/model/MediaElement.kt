package dev.whiteboard.core.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class MediaElement(
    override val id: String = newId(),
    override val bounds: Bounds,
    val assetId: String,
    val pdfPage: Int? = null,
    val rotation: Int = 0,
    val crop: Bounds = Bounds(0f, 0f, 1f, 1f)
) : Element {
    init {
        require(ASSET_NAME.matches(assetId))
        require(pdfPage == null || pdfPage >= 0)
        require((pdfPage != null) == assetId.endsWith(".pdf"))
        require(rotation in 0..359)
        require(crop.left >= 0 && crop.top >= 0 && crop.right <= 1 && crop.bottom <= 1 && crop.width > 0 && crop.height > 0)
    }
    override fun transform(target: Bounds): Element = if (rotation == 0) {
        copy(bounds = target)
    } else {
        AffineElement.fromDocument(this).transform(target)
    }
    override fun duplicate() = copy(id = newId())
    fun cropped(target: Bounds): MediaElement {
        val localX = bounds.left + ((target.left + target.right) / 2 - crop.left) / crop.width * bounds.width
        val localY = bounds.top + ((target.top + target.bottom) / 2 - crop.top) / crop.height * bounds.height
        val angle = Math.toRadians(rotation.toDouble())
        val dx = localX - bounds.center.x
        val dy = localY - bounds.center.y
        val centerX = bounds.center.x + (dx * cos(angle) - dy * sin(angle)).toFloat()
        val centerY = bounds.center.y + (dx * sin(angle) + dy * cos(angle)).toFloat()
        val width = bounds.width * target.width / crop.width
        val height = bounds.height * target.height / crop.height
        return copy(crop = target, bounds = Bounds(centerX - width / 2, centerY - height / 2, centerX + width / 2, centerY + height / 2))
    }
    val rotatedBounds: Bounds get() {
        val angle = Math.toRadians(rotation.toDouble())
        val width = (abs(cos(angle)) * bounds.width + abs(sin(angle)) * bounds.height).toFloat()
        val height = (abs(sin(angle)) * bounds.width + abs(cos(angle)) * bounds.height).toFloat()
        return Bounds(bounds.center.x - width / 2, bounds.center.y - height / 2, bounds.center.x + width / 2, bounds.center.y + height / 2)
    }
    companion object {
        val ASSET_NAME = Regex("[a-f0-9-]{36}\\.(png|pdf)")
    }
}

fun Board.referencedAssets(): Set<String> = pages.flatMap { it.elements }.referencedAssets()
