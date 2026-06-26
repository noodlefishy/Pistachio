package io.cuttlefish.backend

import io.cuttlefish.*

class Backend(instructions: List<Instruction>) {


    fun encodeRRR(valueAdjust: UShort, single: Instruction): UShort {
        var value = valueAdjust
        fun register(start: Int, end: Int, reg: RegisterType): UShort {
            val regA = reg.ordinal
            val preMerge = (regA shl (start - (start - end))).toUShort()
            return preMerge
        }

        val x = when (single) {
            is Instruction.Add -> {
                // 12-10
//                println(value.toString(2).padStart(16, '0'))
                value = value or register(12, 10, single.register1)
//                println(value.toString(2).padStart(16, '0'))
                value = value or register(9, 7, single.register2)
                value = value or register(6, 4, single.register3)
                value
            }

            is Instruction.Addi -> TODO()
            is Instruction.Beq -> TODO()
            is Instruction.Jalr -> TODO()
            is Instruction.Lui -> TODO()
            is Instruction.Lw -> TODO()
            is Instruction.Nand -> TODO()
            is Instruction.Sw -> TODO()
        }
        return x
    }


    fun encode(single: Instruction) {
        var value: UShort
        val opcode = InstructionType.entries.find { it.name == single::class.simpleName }!!.ordinal
        value = (opcode shl (15 - (15 - 13))).toUShort()
        encodeRRR(value, single)


    }

    fun decodeRRR(single: UShort) {

    }


    fun decode(single: UShort) {
        decodeRRR(single)
    }

}


fun main() {
    val ins = Instruction.Add(RegisterType.R1, RegisterType.R2, RegisterType.R3)
    val b = Backend(listOf())
    b.encode(ins)
}