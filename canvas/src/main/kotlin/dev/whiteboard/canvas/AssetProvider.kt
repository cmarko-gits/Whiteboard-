package dev.whiteboard.canvas

import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.*

fun interface AssetProvider {
    /** Blocking implementations are used on worker threads; the editor wraps them in AsyncAssetProvider. */
    fun load(assetId: String, pdfPage: Int?): Bitmap?
}

class AsyncAssetProvider(
    private val delegate: AssetProvider,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onReady: () -> Unit
) : AssetProvider {
    private val bitmaps = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val lock = Any()
    private val pending = mutableMapOf<String, Long>()
    private val failed = mutableSetOf<String>()
    private var generation = 0L
    private var scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    fun start() = synchronized(lock) {
        if (!scope.isActive) scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    }
    fun close() = synchronized(lock) {
        scope.cancel()
        generation++
        pending.clear()
        failed.clear()
        bitmaps.evictAll()
    }
    override fun load(assetId: String, pdfPage: Int?): Bitmap? {
        val key = "$assetId:$pdfPage"
        val (ticket, requestScope) = synchronized(lock) {
            bitmaps.get(key)?.let { return it }
            if (!scope.isActive || key in pending || key in failed) return null
            pending[key] = generation
            generation to scope
        }
        requestScope.launch {
            try {
                val bitmap = delegate.load(assetId, pdfPage)
                val publish = synchronized(lock) {
                    if (ticket != generation || !isActive) {
                        false
                    } else {
                        if (bitmap != null) bitmaps.put(key, bitmap) else failed.add(key)
                        true
                    }
                }
                if (publish) {
                    withContext(Dispatchers.Main) {
                        if (synchronized(lock) { ticket == generation }) onReady()
                    }
                }
            } catch (
                cancelled: CancellationException
            ) {
                throw cancelled
            } catch (
                _: Exception
            ) {
                synchronized(lock) { if (ticket == generation) failed.add(key) }
            } finally {
                synchronized(lock) { if (pending[key] == ticket) pending.remove(key) }
            }
        }
        return null
    }
}
