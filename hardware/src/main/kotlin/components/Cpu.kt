package io.cuttlefish.components

import io.cuttlefish.*
import io.cuttlefish.instructions.*

class Cpu(/* val mmu: MemoryManagement , */ val onSyscall: suspend (Instruction.Syscall) -> Unit) {
    val registers = Registers()
    val alu = Alu()
    var pc: Int = 0

    suspend fun tick(instruction: Instruction) {

        registers.write(RegisterType.RZ, 0)

        when (instruction) {
            is Instruction.Add -> TODO()
            is Instruction.Addi -> TODO()
            is Instruction.Beq -> TODO()
            is Instruction.Jalr -> TODO()
            is Instruction.Lui -> TODO()
            is Instruction.Lw -> TODO()
            is Instruction.Nand -> TODO()
            is Instruction.Sw -> TODO()
        }



        pc++
        registers.write(RegisterType.RZ, 0)
    }

    suspend fun RegisterType.read(): Short = registers.read(this)
    suspend fun RegisterType.write(a: Short) = registers.write(this, a)
}



