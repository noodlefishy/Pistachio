package io.cuttlefish.debug

import io.cuttlefish.*
import io.cuttlefish.backend.*
import io.cuttlefish.components.*
import io.cuttlefish.debugging.*

class Debugger(val cpu: Cpu, val memory: MemoryBus) {
    val symbolMap: Map<String, UShort> = mapOf()

    private val addressToLabelMap: Map<UShort, String> = symbolMap.entries.associate { it.value to it.key }
    val history = ArrayDeque<String>(50)
    val breakPoints = mutableSetOf<UShort>()

    data class RegisterDelta(val registerType: RegisterType, val oldValue: Short, val newValue: Short)

    private suspend fun executeStep() {
        if (cpu.isHalted) return

        val prePc = cpu.pc
        val preRegisters = cpu.registers.registerData.copyOf()
        val rawInstruction = memory.read(prePc)
        val decodedInstruction = Backend.decode(rawInstruction.toUShort())

        cpu.tick()



        val postRegisters = cpu.registers.registerData.copyOf()
        // TODO create log entry formats for history


    }

    private fun decipherLabel(label: UShort): String? {
        val labels: Map<UShort, String> =
            symbolMap.map<String, UShort, Pair<UShort, String>> { it.value to it.key }.toMap()
        return labels[label]
    }

}