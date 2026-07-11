package io.cuttlefish.devices

import io.cuttlefish.MemoryManagement

interface Device: MemoryManagement {
    val deviceId: UShort
    val memoryUsed: UIntRange
}