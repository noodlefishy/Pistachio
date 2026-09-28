package io.cuttlefish.devices

import io.cuttlefish.MemoryManagement
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class ProgrammableIntervalTimer : Device {
    override val name: String = "Programmable Interval Timer"
    override val deviceId: UShort = 4u
    override val memoryUsed: UIntRange = 0xFF10u..0xFF13u

    private var ctrl: Short = 0
    private var reload: Short = 0
    private var status: Short = 0

    private var startTime: TimeMark? = null

    override suspend fun read(address: UShort): Short {
        updateState()
        return when (address.toInt()) {
            0xFF10 -> ctrl
            0xFF11 -> reload
            0xFF12 -> calculateCurrent()
            0xFF13 -> {
                // Reading the status register automatically clears the flag!
                val currentStatus = status
                status = 0
                currentStatus
            }
            else -> 0
        }
    }

    override suspend fun write(address: UShort, value: Short) {
        when (address.toInt()) {
            0xFF10 -> {
                val wasEnabled = (ctrl.toInt() and 1) != 0
                val isEnabled = (value.toInt() and 1) != 0
                ctrl = value

                // If it was just turned ON, mark the starting time!
                if (!wasEnabled && isEnabled) {
                    startTime = TimeSource.Monotonic.markNow()
                    status = 0
                }
            }
            0xFF11 -> reload = value
            // 0xFF12 is Read-Only (Current Countdown)
            0xFF13 -> status = 0 // Manual clear
        }
    }

    private fun updateState() {
        val isEnabled = (ctrl.toInt() and 1) != 0
        if (!isEnabled || startTime == null) return

        val elapsed = startTime!!.elapsedNow().inWholeMilliseconds
        if (elapsed >= reload.toLong() && reload > 0) {
            // Set Bit 0 of status to 1 (Timer Expired!)
            status = (status.toInt() or 1).toShort()

            val isPeriodic = (ctrl.toInt() and 2) != 0
            if (isPeriodic) {
                // Periodic Mode: Auto-restart the timer
                startTime = TimeSource.Monotonic.markNow()
            } else {
                // One-Shot Mode: Disable the timer automatically
                ctrl = (ctrl.toInt() and 1.inv()).toShort()
            }
        }
    }

    private fun calculateCurrent(): Short {
        val isEnabled = (ctrl.toInt() and 1) != 0
        if (!isEnabled || startTime == null) return reload

        val elapsed = startTime!!.elapsedNow().inWholeMilliseconds
        val remaining = reload.toLong() - elapsed
        return if (remaining < 0) 0 else remaining.toShort()
    }
}