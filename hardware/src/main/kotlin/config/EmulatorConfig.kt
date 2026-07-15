package io.cuttlefish.config

import kotlinx.serialization.*

@Serializable
data class EmulatorConfig(
    val clock: ClockConfig = ClockConfig(), val debug: DebugConfig = DebugConfig()
)

@Serializable
data class ClockConfig(
    val calculationTimeMs: Long = 0L,
    val memoryReadTimeMs: Long = 0L,
    val memoryWriteTimeMs: Long = 0L,
    val registerReadTimeMs: Long = 0L,
    val registerWriteTimeMs: Long = 0L,
    val deviceConsoleWriteTimeMs: Long = 5L,
    val deviceConsoleReadTimeMs: Long = 5L
)

@Serializable
data class DebugConfig(
    val printInstructions: Boolean = false,
    val printRegistersOnHalt: Boolean = true,
    val printState: Boolean = false,
    val printHistory: Boolean = false,
    val printHex16Dump: Boolean = true,
    val use16wordAddressInDump: Boolean = false,
    val mapFile: String = "map.out",
    val useMap: Boolean = true
)

object GlobalConfig {
    var debug = DebugConfig()
}