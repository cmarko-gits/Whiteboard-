package dev.whiteboard.app

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whiteboard.canvas.AssetProvider
import dev.whiteboard.canvas.AsyncAssetProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssetLifecycleTest {
    private fun await(latch: CountDownLatch) = assertTrue("Background asset task timed out", latch.await(5, TimeUnit.SECONDS))

    @Test fun detachEvictsImagesAndReattachLoadsThemAgain() {
        val loads = AtomicInteger()
        val first = CountDownLatch(1)
        val second = CountDownLatch(1)
        val provider = AsyncAssetProvider(
            AssetProvider { _, _ ->
                loads.incrementAndGet()
                Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            }
        ) {
            if (loads.get() == 1) first.countDown() else second.countDown()
        }
        try {
            provider.load("fixture", null)
            await(first)
            assertNotNull(provider.load("fixture", null))
            provider.close()
            provider.start()
            assertNull(provider.load("fixture", null))
            await(second)
            assertEquals(2, loads.get())
        } finally {
            provider.close()
        }
    }

    @Test fun aCancelledOldDecodeCannotOverwriteTheImageFromANewerAttachment() {
        val oldStarted = CountDownLatch(1)
        val releaseOld = CountDownLatch(1)
        val oldFinished = CountDownLatch(1)
        val freshReady = CountDownLatch(1)
        val loads = AtomicInteger()
        val dispatches = AtomicInteger()
        val trackedIo = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                val task = dispatches.incrementAndGet()
                Dispatchers.IO.dispatch(
                    context,
                    Runnable {
                        try {
                            block.run()
                        } finally {
                            if (task == 1) oldFinished.countDown()
                        }
                    }
                )
            }
        }
        val provider = AsyncAssetProvider(
            AssetProvider { _, _ ->
                val old = loads.incrementAndGet() == 1
                if (old) {
                    oldStarted.countDown()
                    check(releaseOld.await(5, TimeUnit.SECONDS))
                }
                Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(if (old) Color.RED else Color.BLUE) }
            },
            trackedIo
        ) { freshReady.countDown() }
        try {
            provider.load("fixture", null)
            await(oldStarted)
            provider.close()
            provider.start()
            provider.load("fixture", null)
            await(freshReady)
            releaseOld.countDown()
            await(oldFinished)
            assertEquals(Color.BLUE, provider.load("fixture", null)!!.getPixel(0, 0))
            assertEquals(2, loads.get())
        } finally {
            releaseOld.countDown()
            provider.close()
        }
    }
}
