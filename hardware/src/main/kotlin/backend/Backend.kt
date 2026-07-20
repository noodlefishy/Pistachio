package io.cuttlefish.backend

import io.cuttlefish.*
import io.cuttlefish.instructions.*

object Backend {
    private enum class InstructionType {
        Add, Addi, Nand, Lui, Lw, Sw, Beq, Jalr
    }

    private fun registerEncode(start: Int, end: Int, reg: RegisterType): UShort {
        val regA = reg.ordinal
        val preMerge = (regA shl (start - (start - end))).toUShort()
        return preMerge
    }

    private fun encodeRRI(valueAdjust: UShort, single: Instruction): UShort {
        var value = valueAdjust
        val x = when (single) {
            is Instruction.Addi -> {
                value = value or registerEncode(12, 10, single.register1)
                value = value or registerEncode(9, 7, single.register2)
                value = value or (single.immediate.toUShort() and 0x7Fu) // 7-bit immediate
                value
            }

            is Instruction.Beq -> {
                value = value or registerEncode(12, 10, single.register1)
                value = value or registerEncode(9, 7, single.register2)
                value = value or (single.immediate.toUShort() and 0x7Fu) // 7-bit immediate
                value
            }

            is Instruction.Jalr -> {
                value = value or registerEncode(12, 10, single.register1)
                value = value or registerEncode(9, 7, single.register2)
                value = value or (single.immediate.toUShort() and 0x7Fu) // 7-bit immediate
                value
            }

            is Instruction.Lw -> {
                value = value or registerEncode(12, 10, single.register1)
                value = value or registerEncode(9, 7, single.register2)
                value = value or (single.immediate.toUShort() and 0x7Fu) // 7-bit immediate
                value
            }

            is Instruction.Sw -> {
                value = value or registerEncode(12, 10, single.register1)
                value = value or registerEncode(9, 7, single.register2)
                value = value or (single.immediate.toUShort() and 0x7Fu) // 7-bit immediate
                value
            }

            else -> error("The instruction $single is not an RRI-Format instruction")
        }
        return x
    }

    private fun encodeRI(valueAdjust: UShort, single: Instruction): UShort {
        var value = valueAdjust
        val x = when (single) {
            is Instruction.Lui -> {
                value = value or registerEncode(12, 10, single.register1)
                value = value or (single.immediate.toUShort() and 0x3FFu) // 10-bit immediate
                value
            }

            else -> error("The instruction $single is not an RI-Format instruction")
        }
        return x
    }

    private fun encodeRRR(valueAdjust: UShort, single: Instruction): UShort {
        var value = valueAdjust

        val x = when (single) {
            is Instruction.Add -> {
                value = value or registerEncode(12, 10, single.register1)
                value = value or registerEncode(9, 7, single.register2)
                value = value or registerEncode(9, 7, single.register2)
                value = value or registerEncode(2, 0, single.register3) // (6, 4) to (2, 0) !!
                value
            }

            is Instruction.Nand -> {
                value = value or registerEncode(12, 10, single.register1)
                value = value or registerEncode(9, 7, single.register2)
                value = value or registerEncode(2, 0, single.register3) // I feel felt my brain decompose
                // Note for future you, you originally assumed it was (6, 4) for Reg C. Sigh, you misread
                // Luckily
                value
            }

            else -> error("The instruction $single is not an RRR-Format instruction")
        }
        return x
    }

    private fun encode(single: Instruction): UShort {
        if (single is Instruction.DataWord) return single.value.toUShort()

        var value: UShort
        val opcode = InstructionType.entries.find { it.name == single::class.simpleName }!!.ordinal
        value = (opcode shl (15 - (15 - 13))).toUShort()
        return when (InstructionType.entries.find { it.ordinal == opcode }) {
            InstructionType.Add -> encodeRRR(value, single)
            InstructionType.Addi -> encodeRRI(value, single)
            InstructionType.Nand -> encodeRRR(value, single)
            InstructionType.Lui -> encodeRI(value, single)
            InstructionType.Lw -> encodeRRI(value, single)
            InstructionType.Sw -> encodeRRI(value, single)
            InstructionType.Beq -> encodeRRI(value, single)
            InstructionType.Jalr -> encodeRRI(value, single)
            null -> error("The instruction is null???")
        }


    }

    private fun decodeRRR(single: UShort): Instruction {
        val instructionTypeCode = (single.toUInt() shr (15 - (15 - 13))).toUShort()
        val instructionType = InstructionType.entries.find { it.ordinal == instructionTypeCode.toInt() }!!

        val r1 = (single.toUInt() shr 10 and 0x7u).toUShort()
        val r2 = (single.toUInt() shr 7 and 0x7u).toUShort()
        val r3 = (single.toUInt() and 0x7u).toUShort() // Sigh

        val x = when (instructionType) {
            InstructionType.Add -> {
                Instruction.Add(
                    RegisterType.entries[r1.toInt()], RegisterType.entries[r2.toInt()], RegisterType.entries[r3.toInt()]
                )
            }

            InstructionType.Nand -> {
                Instruction.Nand(
                    RegisterType.entries[r1.toInt()], RegisterType.entries[r2.toInt()], RegisterType.entries[r3.toInt()]
                )
            }

            else -> error("The instruction $single is not an RRR-Format instruction")
        }
        return x
    }

    private fun decodeRRI(single: UShort): Instruction {
        val instructionTypeCode = (single.toUInt() shr (15 - (15 - 13))).toUShort()
        val instructionType = InstructionType.entries.find { it.ordinal == instructionTypeCode.toInt() }!!

        val r1 = (single.toUInt() shr 10 and 0x7u).toUShort()
        val r2 = (single.toUInt() shr 7 and 0x7u).toUShort()
        val imm = (single.toUInt() and 0x7Fu).toShort() // 7-bit immediate

        val x = when (instructionType) {
            InstructionType.Addi -> {
                Instruction.Addi(
                    RegisterType.entries[r1.toInt()], RegisterType.entries[r2.toInt()], signExtend7(imm)
                )
            }

            InstructionType.Beq -> {
                Instruction.Beq(
                    RegisterType.entries[r1.toInt()], RegisterType.entries[r2.toInt()], signExtend7(imm)
                )
            }

            InstructionType.Jalr -> {
                Instruction.Jalr(
                    RegisterType.entries[r1.toInt()], RegisterType.entries[r2.toInt()], signExtend7(imm)
                )
            }

            InstructionType.Lw -> {
                Instruction.Lw(
                    RegisterType.entries[r1.toInt()], RegisterType.entries[r2.toInt()], signExtend7(imm)
                )
            }

            InstructionType.Sw -> {
                Instruction.Sw(
                    RegisterType.entries[r1.toInt()], RegisterType.entries[r2.toInt()], signExtend7(imm)
                )
            }

            else -> error("The instruction $single is not an RRI-Format instruction")
        }
        return x
    }

    private fun decodeRI(single: UShort): Instruction {
        val instructionTypeCode = (single.toUInt() shr (15 - (15 - 13))).toUShort()
        val instructionType = InstructionType.entries.find { it.ordinal == instructionTypeCode.toInt() }!!

        val r1 = (single.toUInt() shr 10 and 0x7u).toUShort()
        val imm = (single.toUInt() and 0x3FFu).toShort() // 10-bit immediate

        val x = when (instructionType) {
            InstructionType.Lui -> {
                Instruction.Lui(
                    RegisterType.entries[r1.toInt()], imm
                )
            }

            else -> error("The instruction $single is not an RI-Format instruction")
        }
        return x
    }

    fun decode(single: UShort): Instruction {
        val opcode = (single.toUInt() shr (15 - (15 - 13))).toUShort()
        return when (InstructionType.entries.find { it.ordinal == opcode.toInt() }) {
            InstructionType.Add -> decodeRRR(single)
            InstructionType.Addi -> decodeRRI(single)
            InstructionType.Nand -> decodeRRR(single)
            InstructionType.Lui -> decodeRI(single)
            InstructionType.Lw -> decodeRRI(single)
            InstructionType.Sw -> decodeRRI(single)
            InstructionType.Beq -> decodeRRI(single)
            InstructionType.Jalr -> decodeRRI(single)


            null -> error("The instruction is null???")
        }
    }

    fun encode(instructions: List<Instruction>): List<UShort> = instructions.map { encode(it) }

    fun decode(instructions: List<UShort>): List<Instruction> = instructions.map { decode(it) }
}