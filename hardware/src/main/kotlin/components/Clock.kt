package io.cuttlefish.components


object Clock {
    // in ms
    const val ALU_CALCULATION_TIME = 1L
    const val MEMORY_READ_TIME = 1L
    const val MEMORY_WRITE_TIME = 1L
    const val REGISTER_READ_TIME = 1L
    const val REGISTER_WRITE_TIME = 1L

    const val DEVICE_CONSOLE_WRITE_TIME = MEMORY_WRITE_TIME + 10L
    const val DEVICE_CONSOLE_READ_TIME = MEMORY_READ_TIME + 5L

}