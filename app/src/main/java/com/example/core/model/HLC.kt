package com.example.core.model

import java.util.UUID

/**
 * Hybrid Logical Clock (HLC)
 * Ensures causal ordering of events in distributed offline-first systems.
 */
data class HLC(
    val physicalMs: Long,
    val logicalCounter: Long,
    val nodeId: String
) : Comparable<HLC> {
    
    override fun compareTo(other: HLC): Int {
        if (this.physicalMs != other.physicalMs) {
            return this.physicalMs.compareTo(other.physicalMs)
        }
        if (this.logicalCounter != other.logicalCounter) {
            return this.logicalCounter.compareTo(other.logicalCounter)
        }
        return this.nodeId.compareTo(other.nodeId)
    }

    override fun toString(): String {
        return "$physicalMs-$logicalCounter-$nodeId"
    }

    companion object {
        private var lastHLC: HLC? = null
        private val currentNodeId = UUID.randomUUID().toString()

        @Synchronized
        fun send(currentPhysicalMs: Long): HLC {
            val last = lastHLC
            val nextHLC = if (last == null) {
                HLC(currentPhysicalMs, 0, currentNodeId)
            } else if (currentPhysicalMs > last.physicalMs) {
                HLC(currentPhysicalMs, 0, currentNodeId)
            } else {
                HLC(last.physicalMs, last.logicalCounter + 1, currentNodeId)
            }
            lastHLC = nextHLC
            return nextHLC
        }

        @Synchronized
        fun receive(currentPhysicalMs: Long, incomingHLC: HLC): HLC {
            val last = lastHLC
            val maxPhysical = maxOf(currentPhysicalMs, incomingHLC.physicalMs, last?.physicalMs ?: 0L)
            
            val nextLogical = when {
                maxPhysical == last?.physicalMs && maxPhysical == incomingHLC.physicalMs -> 
                    maxOf(last.logicalCounter, incomingHLC.logicalCounter) + 1
                maxPhysical == last?.physicalMs -> last.logicalCounter + 1
                maxPhysical == incomingHLC.physicalMs -> incomingHLC.logicalCounter + 1
                else -> 0L
            }

            val nextHLC = HLC(maxPhysical, nextLogical, currentNodeId)
            lastHLC = nextHLC
            return nextHLC
        }

        fun parse(hlcString: String): HLC? {
            return try {
                val parts = hlcString.split("-")
                HLC(parts[0].toLong(), parts[1].toLong(), parts[2])
            } catch (e: Exception) {
                null
            }
        }
    }
}
