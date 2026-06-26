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


    fun encode(single: Instruction): UShort {
        var value: UShort
        val opcode = InstructionType.entries.find { it.name == single::class.simpleName }!!.ordinal
        value = (opcode shl (15 - (15 - 13))).toUShort()
        return encodeRRR(value, single)


    }

    fun decodeRRR(single: UShort): Instruction {
        var value = single
        val instructionTypeCode = (single.toUInt() shr (15 - (15 - 13))).toUShort()
        value = (value.toUInt() shl (3)).toUShort()
        val instructionType = InstructionType.entries.find { it.ordinal == instructionTypeCode.toInt() }!!

        val r1 = (value.toUInt() shl (3 * 0)).toUShort()
        val r2 = (value.toUInt() shl (3 * 1)).toUShort()
        val r3 = (value.toUInt() shl (3 * 2)).toUShort()

        val x = when (instructionType) {
            InstructionType.Add -> {
                Instruction.Add(
                    RegisterType.entries[r1.toInt() shr 13],
                    RegisterType.entries[r2.toInt() shr 13],
                    RegisterType.entries[r3.toInt() shr 13]

                )
            }

            else -> TODO()
        }
        return x


    }


    fun decode(single: UShort): Instruction {
        return decodeRRR(single)
    }

}


fun main() {
    val ins = Instruction.Add(RegisterType.RZ, RegisterType.PC, RegisterType.SP)
    val b = Backend(listOf())
    val s = b.encode(ins)
    val d = b.decode(s)
    println(d)
}