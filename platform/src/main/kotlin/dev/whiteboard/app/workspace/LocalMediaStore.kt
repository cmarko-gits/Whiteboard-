package dev.whiteboard.app.workspace

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.whiteboard.canvas.AssetProvider
import dev.whiteboard.core.concurrency.AppDispatchers
import dev.whiteboard.core.concurrency.DefaultAppDispatchers
import dev.whiteboard.core.model.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Durable copies are owned by the app, never by temporary gallery/provider URIs. */
@Singleton
class LocalMediaStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: AppDispatchers = DefaultAppDispatchers
) : AssetProvider {
    private val root get() = File(context.filesDir, "assets").apply { mkdirs() }
    fun file(assetId: String): File {
        require(MediaElement.ASSET_NAME.matches(assetId))
        return File(root, assetId)
    }
    fun cameraUri(): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
        val photo = File.createTempFile("photo-", ".jpg", dir)
        return FileProvider.getUriForFile(context, "${context.packageName}.files", photo)
    }
    fun validate(assetId: String) {
        val source = file(assetId)
        require(source.length() in 1..MAX_INPUT_BYTES) { "Invalid attachment size" }
        if (assetId.endsWith(".png")) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, options)
            require(options.outWidth in 1..8192 && options.outHeight in 1..8192) { "Invalid image attachment" }
        } else {
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { require(it.pageCount in 1..100) { "Invalid PDF attachment" } }
            }
        }
    }
    fun prune(retained: Set<String>) {
        root.listFiles()?.filter { file ->
            MediaElement.ASSET_NAME.matches(file.name) && file.name !in retained &&
                System.currentTimeMillis() - file.lastModified() > 86_400_000
        }?.forEach { it.delete() }
    }
    suspend fun importImage(uri: Uri): MediaElement = withContext(dispatchers.io) {
        val input = File.createTempFile("image-", ".source", context.cacheDir)
        try {
            copyLimited(uri, input)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(input.absolutePath, options)
            require(options.outWidth > 0 && options.outHeight > 0) { "Unsupported image" }
            var sample = 1
            while (max(options.outWidth, options.outHeight) / sample > 3072) sample *= 2
            val decoded =
                requireNotNull(BitmapFactory.decodeFile(input.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }))
            val orientation = try {
                ExifInterface(input.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } catch (
                _: Exception
            ) {
                ExifInterface.ORIENTATION_NORMAL
            }
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> {
                        postRotate(90f)
                        postScale(-1f, 1f)
                    }
                    ExifInterface.ORIENTATION_TRANSVERSE -> {
                        postRotate(270f)
                        postScale(-1f, 1f)
                    }
                }
            }
            val normalized = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            val assetId = "${newId()}.png"
            val destination = file(assetId)
            try {
                destination.outputStream().use { check(normalized.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                require(destination.length() <= MAX_INPUT_BYTES) { "Image is too large" }
                val width = min(640f, normalized.width.toFloat())
                MediaElement(bounds = Bounds(0f, 0f, width, width * normalized.height / normalized.width), assetId = assetId)
            } catch (
                error: Exception
            ) {
                destination.delete()
                throw error
            } finally {
                if (normalized !== decoded) normalized.recycle()
                decoded.recycle()
            }
        } finally {
            input.delete()
        }
    }

    suspend fun importPdf(uri: Uri): List<Page> = withContext(dispatchers.io) {
        val assetId = "${newId()}.pdf"
        val destination = file(assetId)
        try {
            copyLimited(uri, destination)
            ParcelFileDescriptor.open(destination, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { pdf ->
                    require(pdf.pageCount in 1..100) { "Import supports up to 100 PDF pages" }
                    List(pdf.pageCount) { index ->
                        pdf.openPage(index).use { page ->
                            val bounds = Bounds(0f, 0f, page.width.toFloat(), page.height.toFloat())
                            Page(
                                name = "PDF ${index + 1}",
                                background = Background.PLAIN,
                                elements = listOf(MediaElement(bounds = bounds, assetId = assetId, pdfPage = index))
                            )
                        }
                    }
                }
            }
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    override fun load(assetId: String, pdfPage: Int?): Bitmap? {
        val source = file(assetId)
        if (!source.isFile) return null
        if (pdfPage == null) {
            val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, dimensions)
            var sample = 1
            while (max(dimensions.outWidth, dimensions.outHeight) / sample > 2048) sample *= 2
            return BitmapFactory.decodeFile(source.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        return ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { pdf ->
                if (pdfPage >= pdf.pageCount) return null
                pdf.openPage(pdfPage).use { page ->
                    val scale = min(2f, 1600f / max(page.width, page.height))
                    Bitmap.createBitmap(
                        (page.width * scale).toInt().coerceAtLeast(1),
                        (page.height * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    ).also {
                        it.eraseColor(Color.WHITE)
                        page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    }
    private fun copyLimited(uri: Uri, destination: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_INPUT_BYTES) { "Import supports files up to 16 MB" }
                    output.write(buffer, 0, count)
                }
            }
        } ?: error("Cannot read selected file")
    }
    companion object {
        const val MAX_INPUT_BYTES = 16L * 1024 * 1024
    }
}
