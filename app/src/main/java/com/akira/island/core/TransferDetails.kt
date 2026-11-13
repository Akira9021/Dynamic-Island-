package com.akira.island.core

enum class TransferDirection { DOWN, UP }

/** Metadata supplied by one operation's notification, never a TrafficStats estimate. */
data class TransferDetails(val bytesPerSecond: Double? = null, val direction: TransferDirection? = null,
    val etaSeconds: Long? = null)

data class NetworkRate(val incoming: Double, val outgoing: Double)
