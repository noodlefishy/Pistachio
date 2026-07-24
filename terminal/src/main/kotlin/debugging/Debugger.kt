package io.cuttlefish.debug

import io.cuttlefish.*
import io.cuttlefish.backend.*
import io.cuttlefish.components.*
import io.cuttlefish.debugging.formatInstruction

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


    suspend fun run() {
        while (!cpu.isHalted) {
            executeStep()
        }
    }


    private fun findRegisterDelta(pre: Array<Short>, post: Array<Short>): RegisterDelta? {
        for (i in pre.indices) {
            if (pre[i] != post[i]) return RegisterDelta(
                registerType = RegisterType.entries[i],
                oldValue = pre[i],
                newValue = post[i]
            )
        }
        return null
    }

    fun formatTrace(pc: UShort, inst: Instruction, delta: RegisterDelta?) {
        formatInstruction(pc, inst,delta)
    }

    private fun decipherLabel(label: UShort): String? {
        val labels: Map<UShort, String> =
            symbolMap.map { it.value to it.key }.toMap()
        return labels[label]
    }

    suspend fun peekNextInstruction(): Instruction {
        return Backend.decode(memory.read(cpu.pc).toUShort())
    }
}