package dev.whiteboard.app

import android.os.Debug
import org.json.JSONObject

/** Approximate process counters are diagnostic observations, not an atomic allocation meter. */
internal data class RuntimeAllocationSnapshot(val allocatedBytes: Long?, val gcCount: Long?) {
    init {
        require(allocatedBytes == null || allocatedBytes >= 0)
        require(gcCount == null || gcCount >= 0)
    }
    fun json(): JSONObject = JSONObject()
        .put("allocatedBytes", allocatedBytes ?: JSONObject.NULL).put("gcCount", gcCount ?: JSONObject.NULL)

    companion object {
        fun read(): RuntimeAllocationSnapshot {
            val stats = Debug.getRuntimeStats()
            return RuntimeAllocationSnapshot(
                stats["art.gc.bytes-allocated"]?.toLongOrNull()?.takeIf { it >= 0 },
                stats["art.gc.gc-count"]?.toLongOrNull()?.takeIf { it >= 0 }
            )
        }
    }
}

internal data class RuntimeAllocationSample(val before: RuntimeAllocationSnapshot, val after: RuntimeAllocationSnapshot) {
    val rawDeltaBytes: Long? = before.allocatedBytes?.let { first -> after.allocatedBytes?.minus(first) }
    val status: String = when {
        rawDeltaBytes == null || before.gcCount == null || after.gcCount == null -> "unavailable"
        before.gcCount != after.gcCount -> "gc-overlap"
        rawDeltaBytes < 0 -> "counter-regressed"
        else -> "observed"
    }
    val usableBytes: Long? get() = rawDeltaBytes.takeIf { status == "observed" }
    fun json(): JSONObject = JSONObject().put("before", before.json()).put("after", after.json())
        .put("rawDeltaBytes", rawDeltaBytes ?: JSONObject.NULL).put("status", status)
}
