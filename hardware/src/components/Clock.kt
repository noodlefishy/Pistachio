package io.cuttlefish.components

import io.cuttlefish.config.ClockConfig

object Clock {
    // in ms
    var ALU_CALCULATION_TIME = 10L
    var MEMORY_READ_TIME = 15L
    var MEMORY_WRITE_TIME = 25L
    var REGISTER_READ_TIME = 5L
    var REGISTER_WRITE_TIME = 10L

    var DEVICE_CONSOLE_WRITE_TIME = 10L
    var DEVICE_CONSOLE_READ_TIME = 5L

    fun applyConfig(config: ClockConfig) {
        ALU_CALCULATION_TIME = config.calculationTimeMs
        MEMORY_READ_TIME = config.memoryReadTimeMs
        MEMORY_WRITE_TIME = config.memoryWriteTimeMs
        REGISTER_READ_TIME = config.registerReadTimeMs
        REGISTER_WRITE_TIME = config.registerWriteTimeMs
        DEVICE_CONSOLE_WRITE_TIME = config.deviceConsoleWriteTimeMs
        DEVICE_CONSOLE_READ_TIME = config.deviceConsoleReadTimeMs
    }
}