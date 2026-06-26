package io.cuttlefish.components

import io.cuttlefish.*
import io.cuttlefish.instructions.*

class Cpu(val mmu: MemoryManagement, val onSyscall: (Cpu, Instruction.Syscall) -> Unit) {
    val registers = Registers()
    val alu = Alu()
    var pc: Int = 0

    suspend fun tick(instruction: Instruction) {
        when (instruction) {
            is Instruction.Add -> handlerAdd(instruction)
            is Instruction.Div -> handlerDiv(instruction)
            is Instruction.Mul -> handlerMul(instruction)
            is Instruction.Sub -> handlerSub(instruction)
            is Instruction.Syscall -> onSyscall(this, instruction)
            is Instruction.Halt -> { /* Handled by OS */
            }

            is Instruction.Lit -> handlerLit(instruction)

        }
        pc++
    }
}