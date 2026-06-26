package io.cuttlefish.components

import io.cuttlefish.*
import io.cuttlefish.instructions.*
import kotlinx.coroutines.*

class Cpu(val mmu: MemoryBus     /* val mmu: MemoryManagement , val onSyscall: suspend (Instruction) -> Unit */) {
    val registers = Registers()
    val alu = Alu()
    var pc: Short = runBlocking { RegisterType.PC.read() }

    suspend fun tick(instruction: Instruction) {

        registers.write(RegisterType.RZ, 0)

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



        pc++
        registers.write(RegisterType.RZ, 0)
    }

    suspend fun RegisterType.read(): Short = registers.read(this)
    suspend fun RegisterType.write(a: Short) = registers.write(this, a)
}



