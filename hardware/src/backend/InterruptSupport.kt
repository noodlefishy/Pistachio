package io.cuttlefish.backend

import io.cuttlefish.devices.Device

interface InterruptController {
	fun raiseIrq(irq: Int): Boolean
	fun clearIrq(irq: Int)
	fun isIrqPending(irq: Int): Boolean
	fun isIrqUnmasked(irq: Int): Boolean
	fun getLine(irq: Int): IrqLine = IrqLine(this, irq)
}

class IrqLine(val controller: InterruptController, val line: Int) {
	fun raise(): Boolean = controller.raiseIrq(line)
	fun clear() = controller.clearIrq(line)
	val isPending: Boolean get() = controller.isIrqPending(line)
	val isUnmasked: Boolean get() = controller.isIrqUnmasked(line)
	fun pulse(): Boolean = raise()
}

interface InterruptCapableDevice : Device {
	fun attachInterruptController(controller: InterruptController)
}