package io.cuttlefish

import io.cuttlefish.InstructionType.*
import java.io.*
import java.util.Locale.getDefault

class Parser(val file: File) {
    val text = file.readLines()

    fun decode(): List<Instruction> {
        val instructions = mutableListOf<Instruction>()
        for (line in text) {
            val parts = line.split(" ")
            if (parts.isEmpty()) continue
            val type = InstructionType.valueOf(
                parts[0].lowercase()
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() })


            val curr = when (type) {
                Add -> Instruction.Add(parts[1].toRegisterType(), parts[2].toRegisterType(), parts[3].toRegisterType())
                Addi -> {
                    val imm = parts[3].toShort()
                    if (imm !in -64..63) {
                        throw Exception("Assembler Error: Immediate $imm out of range for ADDI (-64 to 63)")
                    }
                    Instruction.Addi(
                        register1 = parts[1].toRegisterType(), register2 = parts[2].toRegisterType(), immediate = imm
                    )
                }

                Nand -> Instruction.Nand(
                    parts[1].toRegisterType(), parts[2].toRegisterType(), parts[3].toRegisterType()
                )

                Lui -> {
                    val imm = parts[3].toShort()
                    if (imm !in 0..1023) {
                        throw Exception("Assembler Error: Immediate $imm out of range for LUI (0 to 1023)")
                    }
                    Instruction.Lui(
                        register1 = parts[1].toRegisterType(), immediate = imm
                    )
                }

                Lw -> {
                    val imm = parts[3].toShort()
                    if (imm !in -64..63) {
                        throw Exception("Assembler Error: Immediate $imm out of range for LW (-64 to 63)")
                    }
                    Instruction.Lw(
                        register1 = parts[1].toRegisterType(), register2 = parts[2].toRegisterType(), immediate = imm
                    )
                }

                Sw -> {
                    val imm = parts[3].toShort()
                    if (imm !in -64..63) {
                        throw Exception("Assembler Error: Immediate $imm out of range for SW (-64 to 63)")
                    }
                    Instruction.Sw(
                        register1 = parts[1].toRegisterType(), register2 = parts[2].toRegisterType(), immediate = imm
                    )
                }

                Beq -> {
                    val imm = parts[3].toShort()
                    if (imm !in -64..63) {
                        throw Exception("Assembler Error: Immediate $imm out of range for BEQ (-64 to 63)")
                    }
                    Instruction.Beq(
                        register1 = parts[1].toRegisterType(), register2 = parts[2].toRegisterType(), immediate = imm
                    )
                }

                Jalr -> {
                    val imm = parts[3].toShort()
                    if (imm !in -64..63) {
                        throw Exception("Assembler Error: Immediate $imm out of range for JALR (-64 to 63)")
                    }
                    Instruction.Jalr(
                        register1 = parts[1].toRegisterType(), register2 = parts[2].toRegisterType(), immediate = imm
                    )
                }
            }



            instructions.add(curr)
        }
        return instructions.toList()
    }

}