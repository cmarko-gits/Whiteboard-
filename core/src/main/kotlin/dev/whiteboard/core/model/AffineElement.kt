package dev.whiteboard.core.model

/** A rectangular object in a local frame. Groups and stroke polylines remain flat document geometry. */
data class AffineElement(val source: Element, val placement: AffineTransform2D) : Element {
    init {
        require(source is Shape || source is Note || source is TextElement || source is MediaElement)
        require(source !is MediaElement || source.rotation == 0)
        require(source.bounds.center.x == 0f && source.bounds.center.y == 0f)
    }
    override val id get() = source.id
    override val bounds = placement.map(source.bounds)
    init {
        if (source is Shape) bounds.expand(source.style.width / 2)
    }
    override fun duplicate() = copy(source = source.duplicate())
    override fun transform(target: Bounds): AffineElement {
        val sx = if (bounds.width > 0f) target.width.toDouble() / bounds.width else 1.0
        val sy = if (bounds.height > 0f) target.height.toDouble() / bounds.height else 1.0
        val remap = AffineTransform2D.translation(-bounds.center.x.toDouble(), -bounds.center.y.toDouble())
            .then(AffineTransform2D.scale(sx, sy))
            .then(AffineTransform2D.translation(target.center.x.toDouble(), target.center.y.toDouble()))
        return copy(placement = placement.then(remap))
    }

    companion object {
        fun fromDocument(source: Element, additional: AffineTransform2D = AffineTransform2D.IDENTITY): AffineElement {
            val b = source.bounds
            val halfWidth = b.width / 2
            val halfHeight = b.height / 2
            val local = Bounds(
                if (halfWidth > 0f) -halfWidth else 0f,
                if (halfHeight > 0f) -halfHeight else 0f,
                halfWidth,
                halfHeight
            )
            val centered = when (source) {
                is Shape -> source.copy(bounds = local)
                is Note -> source.copy(bounds = local)
                is TextElement -> source.copy(bounds = local)
                is MediaElement -> source.copy(bounds = local, rotation = 0)
                else -> throw IllegalArgumentException("Only rectangular content can have an affine frame")
            }
            val frame = AffineTransform2D.translation(b.center.x.toDouble(), b.center.y.toDouble())
            val oriented = if (source is MediaElement) {
                frame.then(
                    AffineTransform2D.rotation(source.rotation.toDouble(), b.center)
                )
            } else {
                frame
            }
            return AffineElement(centered, oriented.then(additional))
        }
    }
}

/** Editable content retains the same ID; placement stays outside content/style mutations. */
fun Element.content(): Element = if (this is AffineElement) source else this
fun Element.withContent(updated: Element): Element {
    require(updated.id == id)
    return if (this is AffineElement) AffineElement.fromDocument(updated, placement) else updated
}
