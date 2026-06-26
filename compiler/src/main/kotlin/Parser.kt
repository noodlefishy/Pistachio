package io.cuttlefish

import io.cuttlefish.InstructionType.*
import java.io.*
import java.util.Locale.getDefault
import kotlin.experimental.*

class Parser(val file: File) {
    val text = file.readLines()

    fun decode(): List<Instruction> {
        val instructions = mutableListOf<Instruction>()
        for (line in text) {
            val parts = line.split(" ")
            if (parts.isEmpty()) continue
            val type = valueOf(
                parts[0].lowercase()
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() })

            when (type) {
                Add -> {
                    instructions += Instruction.Add(
                        register1 = parts[1].toRegisterType(),
                        register2 = parts[2].toRegisterType(),
                        register3 = parts[3].toRegisterType()
                    )
                }

                Addi -> {
                    val imm = parts[3].toShort()
                    if (imm !in -64..63) {
                        throw Exception("Assembler Error: Immediate $imm out of range for ADDI (-64 to 63)")
                    }
                    instructions += Instruction.Addi(
                        register1 = parts[1].toRegisterType(), register2 = parts[2].toRegisterType(), immediate = imm
                    )
                }

                Nand -> {
                    instructions += Instruction.Nand(
                        register1 = parts[1].toRegisterType(),
                        register2 = parts[2].toRegisterType(),
                        register3 = parts[3].toRegisterType()
                    )
                }

                Lui -> {
                    val imm = parts[3].toShort()
                    if (imm !in 0..1023) {
                        throw Exception("Assembler Error: Immediate $imm out of range for LUI (0 to 1023)")
                    }
                    instructions += Instruction.Lui(
                        register1 = parts[1].toRegisterType(), immediate = imm
                    )
                }

                Lw -> {
                    val imm = parts[3].toShort()
                    if (imm !in -64..63) {
                        throw Exception("Assembler Error: Immediate $imm out of range for LW (-64 to 63)")
                    }
                    instructions += Instruction.Lw(
                        register1 = parts[1].toRegisterType(), register2 = parts[2].toRegisterType(), immediate = imm
                    )
                }

                Sw -> {
                    val imm = parts[3].toShort()
                    if (imm !in -64..63) {
                        throw Exception("Assembler Error: Immediate $imm out of range for SW (-64 to 63)")
                    }
                    instructions += Instruction.Sw(
                        register1 = parts[1].toRegisterType(), register2 = parts[2].toRegisterType(), immediate = imm
                    )
                }

                Beq -> {
                    val imm = parts[3].toShort()
                    if (imm !in -64..63) {
                        throw Exception("Assembler Error: Immediate $imm out of range for BEQ (-64 to 63)")
                    }
                    instructions += Instruction.Beq(
                        register1 = parts[1].toRegisterType(), register2 = parts[2].toRegisterType(), immediate = imm
                    )
                }

                Jalr -> {
                    val imm = parts[3].toShort()
                    if (imm !in -64..63) {
                        throw Exception("Assembler Error: Immediate $imm out of range for JALR (-64 to 63)")
                    }
                    instructions += Instruction.Jalr(
                        register1 = parts[1].toRegisterType(), register2 = parts[2].toRegisterType(), immediate = imm
                    )
                }
                // Pseudos!
                Nop -> {
                    instructions += Instruction.Add(
                        register1 = RegisterType.RZ, register2 = RegisterType.RZ, register3 = RegisterType.RZ
                    )
                }

                Halt -> {
                    instructions += Instruction.Jalr(
                        register1 = RegisterType.RZ, register2 = RegisterType.RZ, immediate = 1
                    )
                }

                LLi -> {
                    // addi regA, regA, (imm & 0x3F)
                    val imm = parts[3].toShort()
                    if (imm !in -64..63) {
                        throw Exception("Assembler Error: Immediate $imm out of range for JALR (-64 to 63)")
                    }
                    instructions += Instruction.Addi(
                        register1 = parts[1].toRegisterType(), register2 = parts[1].toRegisterType(), immediate = imm
                    )

                }

                Movi -> {

                    val imm = parts[3].toShort()
                    if (imm !in Short.MIN_VALUE..Short.MAX_VALUE) {
                        throw Exception("Assembler Error: Immediate $imm out of range for Movi (${Short.MIN_VALUE} to ${Short.MAX_VALUE})")
                    }
                    val lower6Mask: Short = 0x3F
                    val lui = (imm.toInt() shr 6).toShort()
                    val lli = (imm and lower6Mask)
                    // movi RegA imm
                    instructions += Instruction.Lui(
                        register1 = parts[1].toRegisterType(), immediate = lui
                    )
                    instructions += Instruction.Addi( // pseudo of LLI
                        register1 = parts[1].toRegisterType(), register2 = parts[1].toRegisterType(), immediate = lli
                    )

                }


            }


        }
        return instructions.toList()
    }

}