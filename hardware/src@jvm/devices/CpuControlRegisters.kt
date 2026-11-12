package devices

import io.cuttlefish.components.Cpu
import io.cuttlefish.devices.Device

class CpuControlRegisters(val cpu: Cpu) : Device {
	override val name: String = "Cpu Control Registers"
	override val deviceId: UShort = 0u
	override val memoryUsed: UIntRange = 0xFFE0u..0xFFE3u
	override suspend fun read(address: UShort): Short {
		return when (address) {
			AddressNameType.CPU_EPC.value -> cpu.epc.toShort()
			AddressNameType.CPU_PBR.value -> cpu.pbr.toShort()
			AddressNameType.CPU_PLR.value -> cpu.plr.toShort()
			AddressNameType.CPU_FLAGS.value -> cpu.flags.toShort()
			else -> throw IllegalStateException("Should be impossible")
		}
	}

	override suspend fun write(address: UShort, value: Short) {
		when (address) {
			AddressNameType.CPU_EPC.value -> cpu.epc = value.toUShort()
			AddressNameType.CPU_PBR.value -> cpu.pbr = value.toUShort()
			AddressNameType.CPU_PLR.value -> cpu.plr = value.toUShort()
			AddressNameType.CPU_FLAGS.value -> cpu.flags = value.toUShort()
			else -> throw IllegalStateException("Should be impossible")
		}
	}


	enum class AddressNameType(val value: UShort) {
		CPU_EPC(0XFFE0U),
		CPU_PBR(0XFFE1U),
		CPU_PLR(0XFFE2U),
		CPU_FLAGS(0XFFE3U),
	}
}