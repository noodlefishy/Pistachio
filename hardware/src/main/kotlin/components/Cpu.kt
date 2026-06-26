package io.cuttlefish.components

import io.cuttlefish.*
import io.cuttlefish.backend.Backend
import io.cuttlefish.instructions.*
import kotlinx.coroutines.*

class Cpu(val mmu: MemoryBus) {
    val registers = Registers()
    val alu = Alu()
    var pc: Short = 0 // Starts at 0
    var isHalted = false
    private val backend = Backend()
    // tick() no longer takes an Instruction argument!
    suspend fun tick() {
        if (isHalted) return

        registers.write(RegisterType.RZ, 0)

        // 1. FETCH
        val rawInstruction = mmu.read(pc)

        pc++

        // 2. DECODE
        val instruction = backend.decode(rawInstruction.toUShort())

        if (instruction is Instruction.Jalr &&
            instruction.register1 == RegisterType.RZ &&
            instruction.register2 == RegisterType.RZ &&
            instruction.immediate != 0.toShort()
        ) {
            isHalted = true
            return
        }

        // 3. EXECUTE
        when (instruction) {
            is Instruction.Add -> handlerAdd(instruction)
            is Instruction.Addi -> handlerAddImmediate(instruction)
            is Instruction.Beq -> handlerBeq(instruction)
            is Instruction.Jalr -> handlerJalr(instruction)
            is Instruction.Lui -> handlerLui(instruction)
            is Instruction.Lw -> handleLw(instruction)
            is Instruction.Nand -> handlerNand(instruction)
            is Instruction.Sw -> handleSw(instruction)
        }

        registers.write(RegisterType.RZ, 0)
    }

    suspend fun RegisterType.read(): Short = registers.read(this)
    suspend fun RegisterType.write(a: Short) = registers.write(this, a)
}