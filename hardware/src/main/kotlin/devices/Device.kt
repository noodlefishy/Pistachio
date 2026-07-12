package io.cuttlefish.devices

import io.cuttlefish.MemoryManagement

interface Device: MemoryManagement {
    val name: String
    val deviceId: UShort
    val memoryUsed: UIntRange
}