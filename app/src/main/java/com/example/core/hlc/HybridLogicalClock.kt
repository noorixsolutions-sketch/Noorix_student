package com.example.core.hlc

import java.util.UUID

/**
 * Hybrid Logical Clock (HLC) implementation.
 * Guarantees causal ordering across offline and online distributed events.
 * Format: { physical_ms, logical_counter, node_id }
 */
data class HlcTimestamp(
    val physicalMs: Long,
    val logicalCounter: Int,
    val nodeId: String
) : Comparable<HlcTimestamp> {

    override fun compareTo(other: HlcTimestamp): Int {
        if (this.physicalMs != other.physicalMs) {
            return this.physicalMs.compareTo(other.physicalMs)
        }
        if (this.logicalCounter != other.logicalCounter) {
            return this.logicalCounter.compareTo(other.logicalCounter)
        }
        return this.nodeId.compareTo(other.nodeId)
    }

    override fun toString(): String = "$physicalMs:$logicalCounter:$nodeId"

    companion object {
        fun parse(str: String): HlcTimestamp {
            val parts = str.split(":")
            require(parts.size == 3) { "Invalid HLC string: $str" }
            return HlcTimestamp(
                physicalMs = parts[0].toLong(),
                logicalCounter = parts[1].toInt(),
                nodeId = parts[2]
            )
        }
    }
}

class HybridClock(val nodeId: String = UUID.randomUUID().toString().take(8)) {
    private var lastPhysicalMs: Long = 0L
    private var logicalCounter: Int = 0

    @Synchronized
    fun now(wallTimeMs: Long = System.currentTimeMillis()): HlcTimestamp {
        if (wallTimeMs > lastPhysicalMs) {
            lastPhysicalMs = wallTimeMs
            logicalCounter = 0
        } else {
            logicalCounter++
        }
        return HlcTimestamp(lastPhysicalMs, logicalCounter, nodeId)
    }

    @Synchronized
    fun update(remoteHlc: HlcTimestamp, wallTimeMs: Long = System.currentTimeMillis()): HlcTimestamp {
        val maxPhysical = maxOf(wallTimeMs, maxOf(lastPhysicalMs, remoteHlc.physicalMs))
        if (maxPhysical == lastPhysicalMs && maxPhysical == remoteHlc.physicalMs) {
            logicalCounter = maxOf(logicalCounter, remoteHlc.logicalCounter) + 1
        } else if (maxPhysical == lastPhysicalMs) {
            logicalCounter++
        } else if (maxPhysical == remoteHlc.physicalMs) {
            logicalCounter = remoteHlc.logicalCounter + 1
        } else {
            logicalCounter = 0
        }
        lastPhysicalMs = maxPhysical
        return HlcTimestamp(lastPhysicalMs, logicalCounter, nodeId)
    }
}
