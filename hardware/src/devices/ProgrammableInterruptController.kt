package io.cuttlefish.devices

import io.cuttlefish.backend.InterruptController
import io.cuttlefish.components.Cpu


class ProgrammableInterruptController(var cpu: Cpu? = null) : Device, InterruptController {
    override val name: String = "Programmable Interrupt Controller"
    override val deviceId: UShort = 5u
    override val memoryUsed: UIntRange = 0xFF20u..0xFF23u

    var mask: UShort = 0x00FFu

    var pending: UShort = 0u
        private set

    var vectorBase: UShort = 0x0008u

    var activeHighMask: Boolean = true

    override suspend fun read(address: UShort): Short {
        return when (address.toInt()) {
            0xFF20 -> mask.toShort()
            0xFF21 -> pending.toShort()
            0xFF22 -> 0 // Write-only
            0xFF23 -> vectorBase.toShort()
            else -> 0
        }
    }

    override suspend fun write(address: UShort, value: Short) {
        when (address.toInt()) {
            0xFF20 -> mask = value.toUShort()
            0xFF22 -> {
                val irq = value.toInt() and 0xFFFF
                if (irq in 0..7) {
                    clearIrq(irq)
                } else {
                    pending = (pending.toInt() and value.toInt().inv()).toUShort()
                }
            }
            0xFF23 -> vectorBase = value.toUShort()
        }
    }

    override fun isIrqUnmasked(irq: Int): Boolean {
        if (irq !in 0..7) return false
        val bit = 1 shl irq
        return if (activeHighMask) {
            (mask.toInt() and bit) != 0
        } else {
            (mask.toInt() and bit) == 0
        }
    }

    override fun isIrqPending(irq: Int): Boolean {
		return irq in 0..7 && (pending.toInt() and (1 shl irq)) != 0
	}

    override fun raiseIrq(irq: Int): Boolean {
        if (irq !in 0..7) throw IllegalArgumentException("impossible irq line :$irq")
        if (!isIrqUnmasked(irq)) return false

        pending = (pending.toInt() or (1 shl irq)).toUShort()
        return true
    }


    override fun clearIrq(irq: Int) {
        if (irq in 0..7) {
            pending = (pending.toInt() and (1 shl irq).inv()).toUShort()
        }
    }


    fun getHighestPriorityPendingIrq(): Int? {
        for (i in 0..7) {
            if (isIrqPending(i) && isIrqUnmasked(i)) {
                return i
            }
        }
        return null
    }

    suspend fun dispatch(targetCpu: Cpu? = this.cpu): Boolean {
        val cpu = targetCpu ?: return false
        if (cpu.isHalted) return false

        if (cpu.isKernelMode && (cpu.flags.toInt() and 1) == 0) {
            return false
        }

        val irq = getHighestPriorityPendingIrq() ?: return false

        cpu.epc = cpu.pc
        cpu.isKernelMode = true
        cpu.flags = (cpu.flags.toInt() and 1.inv()).toUShort()

        val target = (vectorBase.toInt() + irq).toUShort()
        val handlerAddress = cpu.mmu.read(target).toUShort()
        cpu.pc = if (handlerAddress != 0u.toUShort()) handlerAddress else target

        return true
    }

    companion object {
        const val IRQ_PIT_TIMER: Int = 0
        const val IRQ_TERMINAL_CONSOLE: Int = 1
        const val IRQ_DISPLAY_VSYNC: Int = 2
        const val IRQ_BLOCK_STORAGE: Int = 3
    }
}
