package io.cuttlefish.debug

import io.cuttlefish.*
import io.cuttlefish.backend.*
import io.cuttlefish.components.*
import io.cuttlefish.debugging.*

class Debugger(val cpu: Cpu, val memory: MemoryBus, val symbolMap: Map<String, UShort>,val baseAddress: UShort = 0x3000u) {
    val addressToLabelMap: Map<UShort, String> = symbolMap.entries.associate { it.value to it.key }
    val historyX = ArrayDeque<String>(50)
    val breakPoints = mutableSetOf<UShort>()
    var lastCommand = "s"


    data class RegisterDelta(val registerType: RegisterType, val oldValue: Short, val newValue: Short)

    suspend fun executeStep() {
        if (cpu.isHalted) return

        val prePc = cpu.pc
        val preRegisters = cpu.registers.registerData.copyOf()
        val rawInstruction = memory.read(prePc)
        val decodedInstruction = Backend.decode(rawInstruction.toUShort())

        cpu.tick()


        val postRegisters = cpu.registers.registerData.copyOf()

        val delta = findRegisterDelta(preRegisters, postRegisters)
        val traceString = formatTrace(prePc, decodedInstruction, delta)

        if (historyX.size >= 50) historyX.removeFirst()
        historyX.addFirst(traceString)

    }


    suspend fun runContinuously() {
        while (!cpu.isHalted) {
            if (cpu.pc in breakPoints) {
                println(
                    "\n[DEBUG] Breakpoint hit at ${decipherLabel(cpu.pc)}"
                )
            }
            executeStep()
        }
    }


    private fun findRegisterDelta(pre: Array<Short>, post: Array<Short>): RegisterDelta? {
        for (i in pre.indices) {
            if (pre[i] != post[i]) return RegisterDelta(
                registerType = RegisterType.entries[i], oldValue = pre[i], newValue = post[i]
            )
        }
        return null
    }

    fun formatTrace(pc: UShort, inst: Instruction, delta: RegisterDelta?): String {
        return formatInstruction(pc, inst, delta)
    }

    fun decipherLabel(label: UShort): String {
        return addressToLabelMap[label] ?: "0x${cpu.pc.toString(16).padStart(4, '0')}"
    }

    suspend fun peekNextInstruction(): Instruction {
        return Backend.decode(memory.read(cpu.pc).toUShort())
    }

    fun resolveTarget(target: String?): UShort? {
        if (target == null) return null
        if (target.startsWith("0x", ignoreCase = true)) {
            return target.substring(2).toUIntOrNull(16)?.toUShort()
        }
        return symbolMap[target]
    }
}