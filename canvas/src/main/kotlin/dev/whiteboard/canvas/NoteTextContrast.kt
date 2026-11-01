package dev.whiteboard.canvas

import android.graphics.Color

/** Note text is automatic; user-chosen text object colors remain unchanged. */
internal object NoteTextContrast {
    private val preferred = 0xFF30352D.toInt()

    fun foreground(fill: Int, pageBackground: Int): Int {
        val background = over(fill, over(pageBackground, Color.WHITE))
        val luminance = Color.luminance(background).toDouble()
        val preferredLuminance = Color.luminance(preferred).toDouble()
        val preferredContrast = (maxOf(luminance, preferredLuminance) + 0.05) /
            (minOf(luminance, preferredLuminance) + 0.05)
        if (preferredContrast >= 4.5) return preferred
        val blackContrast = (luminance + 0.05) / 0.05
        val whiteContrast = 1.05 / (luminance + 0.05)
        return if (blackContrast >= whiteContrast) Color.BLACK else Color.WHITE
    }

    private fun over(foreground: Int, opaqueBackground: Int): Int {
        val alpha = Color.alpha(foreground)
        fun channel(front: Int, back: Int) = (front * alpha + back * (255 - alpha) + 127) / 255
        return Color.rgb(
            channel(Color.red(foreground), Color.red(opaqueBackground)),
            channel(Color.green(foreground), Color.green(opaqueBackground)),
            channel(Color.blue(foreground), Color.blue(opaqueBackground))
        )
    }
}
