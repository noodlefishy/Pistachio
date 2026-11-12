package io.cuttlefish.components

import io.cuttlefish.*
import io.cuttlefish.backend.InterruptCapableDevice
import io.cuttlefish.backend.InterruptController
import io.cuttlefish.devices.*
import io.cuttlefish.instructions.SegmentationFault

class MemoryBus(val ram: PhysicalMemory) : MemoryManagement {
	val addressMap = mutableMapOf<UShort, Device>()
	val attachedDevices: MutableSet<Device> = mutableSetOf()
	var cpuAwareness: Cpu? = null
	var pic: InterruptController? = null
		private set

	init {
//        registerDevice(Console())
//        registerDevice(Display())
//        registerDevice(PackingAccelerator())
	}

	override suspend fun read(address: UShort): Short {
		val device = addressMap[address]

		if (device != null) {
			return device.read(address)
		}
		if (!cpuAwareness!!.isKernelMode) {
			if (address >= cpuAwareness!!.plr) throw SegmentationFault(
				"Address $address out of bounds" + "(${address + cpuAwareness!!.pbr}/0x$${
					(address + cpuAwareness!!.pbr).toString(16)
				}"
			)

			return ram.read((address + cpuAwareness!!.pbr).toUShort())
		} else {
			return ram.read(address)
		}


	}

	override suspend fun write(address: UShort, value: Short) {
		val device = addressMap[address]
		if (device != null) {
			device.write(address, value)
			return
		}
		if (!cpuAwareness!!.isKernelMode) {
			if (address >= cpuAwareness!!.plr) throw SegmentationFault(
				"Address $address out of bounds" + "(${address + cpuAwareness!!.pbr}/0x$${
					(address + cpuAwareness!!.pbr).toString(16)
				}"
			)

			ram.write((address + cpuAwareness!!.pbr).toUShort(), value)
		} else {
			ram.write(address, value)
		}
	}

	fun attach(device: Device) {
		registerDevice(device)
		if (device is InterruptController) {
			this.pic = device
			for (d in attachedDevices) {
				if (d is InterruptCapableDevice) {
					d.attachInterruptController(device)
				}
			}
		} else if (device is InterruptCapableDevice) {
			pic?.let { device.attachInterruptController(it) }
		}
	}

	fun raiseIrq(irq: Int): Boolean = pic?.raiseIrq(irq) ?: false

	fun attachCpu(cpu: Cpu) {
		cpuAwareness = cpu
	}

	private fun registerDevice(device: Device) {
		attachedDevices += device
		for (address in device.memoryUsed) {
			addressMap[address.toUShort()] = device
		}
	}
}
