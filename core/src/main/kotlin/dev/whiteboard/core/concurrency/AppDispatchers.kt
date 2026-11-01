package dev.whiteboard.core.concurrency

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

interface AppDispatchers {
    val io: CoroutineDispatcher
    val computation: CoroutineDispatcher
}

object DefaultAppDispatchers : AppDispatchers {
    override val io = Dispatchers.IO
    override val computation = Dispatchers.Default
}
