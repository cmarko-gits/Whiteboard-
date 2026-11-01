package dev.whiteboard.core

import dev.whiteboard.core.model.*
import org.junit.Assert.*
import org.junit.Test

class MediaGeometryTest {
    @Test fun `cropping preserves scale and reset restores original bounds`() {
        val original = MediaElement(bounds = Bounds(0f, 0f, 400f, 200f), assetId = "${newId()}.png")
        val cropped = original.cropped(Bounds(0.25f, 0f, 0.75f, 1f))
        assertEquals(Bounds(100f, 0f, 300f, 200f), cropped.bounds)
        assertEquals(original.bounds, cropped.cropped(Bounds(0f, 0f, 1f, 1f)).bounds)
    }

    @Test fun `rotated image has correct export bounds`() {
        val image = MediaElement(bounds = Bounds(0f, 0f, 200f, 100f), assetId = "${newId()}.png", rotation = 90)
        assertEquals(100f, image.renderBounds.width, 0.001f)
        assertEquals(200f, image.renderBounds.height, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `asset references cannot escape storage`() {
        MediaElement(bounds = Bounds(0f, 0f, 10f, 10f), assetId = "../../outside.png")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `crop cannot be empty`() {
        MediaElement(bounds = Bounds(0f, 0f, 10f, 10f), assetId = "${newId()}.png", crop = Bounds(0.5f, 0f, 0.5f, 1f))
    }
}
