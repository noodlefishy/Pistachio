package io.cuttlefish

import java.io.*

class Parser(file: File, val baseAddress: Short) {
    private val text = file.readLines()
    private val symbolTable = mutableMapOf<String, Short>()

    private fun String.toNumber(): Short {
        val intVal = when {
            this.startsWith("0x", ignoreCase = true) -> this.substring(2).toInt(16)
            this.startsWith("-0x", ignoreCase = true) -> ("-" + this.substring(3)).toInt(16)
            this.startsWith("0") && this.length > 1 -> this.toInt(8)
            this.startsWith("-0") && this.length > 2 -> ("-" + this.substring(2)).toInt(8)
            else -> this.toInt(10)
        }
        return intVal.toShort()
    }

    private fun resolveValue(input: String, currentAddress: Short, isRelative: Boolean = false): Short {
        if (symbolTable.containsKey(input)) {
            val targetAddress = symbolTable[input]!!
            return if (isRelative) {
                // BEQ is PC for (tokens in parsedLines) -Relative: Target - (PC + 1)
                (targetAddress - (currentAddress + 1)).toShort()
            } else {
                targetAddress
            }
        }
        return input.toNumber()
    }

    fun decode(): List<Instruction> {
        val instructions = mutableListOf<Instruction>()

        val parsedLines = text.map { line ->
            val noComment = line.split("#","//")[0].trim()
            noComment.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        }.filter { it.isNotEmpty() }

        var addressCounter: Short = baseAddress
        for (tokens in parsedLines) {
            var startIndex = 0

            if (tokens[0].endsWith(":")) {
                val labelName = tokens[0].removeSuffix(":")
                symbolTable[labelName] = addressCounter
                startIndex = 1
            }

            if (startIndex < tokens.size) {
                val opcode = tokens[startIndex].lowercase()
                when (opcode) {
                    "movi" -> addressCounter = (addressCounter + 2).toShort() // LUI + ADDI
                    ".space" -> {
                        val count = tokens[startIndex + 1].toNumber().toInt()
                        addressCounter = (addressCounter + count).toShort()
                    }

                    else -> addressCounter++ // Every other instruction and .fill takes 1 word
                }
            }
        }

        var currentPC: Short = baseAddress
        for (tokens in parsedLines) {
            val startIndex = if (tokens[0].endsWith(":")) 1 else 0

            // If the line was *only* a label with nothing else, skip generation
            if (startIndex >= tokens.size) continue

            when (val opcode = tokens[startIndex].lowercase()) {
                "add" -> {
                    instructions += Instruction.Add(
                        register1 = tokens[startIndex + 1].toRegisterType(),
                        register2 = tokens[startIndex + 2].toRegisterType(),
                        register3 = tokens[startIndex + 3].toRegisterType()
                    )
                    currentPC++
                }

                "addi" -> {
                    val imm = resolveValue(tokens[startIndex + 3], currentPC)
                    instructions += Instruction.Addi(
                        register1 = tokens[startIndex + 1].toRegisterType(),
                        register2 = tokens[startIndex + 2].toRegisterType(),
                        immediate = imm
                    )
                    currentPC++
                }

                "nand" -> {
                    instructions += Instruction.Nand(
                        register1 = tokens[startIndex + 1].toRegisterType(),
                        register2 = tokens[startIndex + 2].toRegisterType(),
                        register3 = tokens[startIndex + 3].toRegisterType()
                    )
                    currentPC++
                }

                "lui" -> {
                    val imm = resolveValue(tokens[startIndex + 2], currentPC)
                    instructions += Instruction.Lui(
                        register1 = tokens[startIndex + 1].toRegisterType(),
                        immediate = imm
                    )
                    currentPC++
                }

                "lw" -> {
                    val imm = resolveValue(tokens[startIndex + 3], currentPC)
                    instructions += Instruction.Lw(
                        register1 = tokens[startIndex + 1].toRegisterType(),
                        register2 = tokens[startIndex + 2].toRegisterType(),
                        immediate = imm
                    )
                    currentPC++
                }

                "sw" -> {
                    val imm = resolveValue(tokens[startIndex + 3], currentPC)
                    instructions += Instruction.Sw(
                        register1 = tokens[startIndex + 1].toRegisterType(),
                        register2 = tokens[startIndex + 2].toRegisterType(),
                        immediate = imm
                    )
                    currentPC++
                }

                "beq" -> {
                    val imm = resolveValue(tokens[startIndex + 3], currentPC, isRelative = true)
                    instructions += Instruction.Beq(
                        register1 = tokens[startIndex + 1].toRegisterType(),
                        register2 = tokens[startIndex + 2].toRegisterType(),
                        immediate = imm
                    )
                    currentPC++
                }

                "jalr" -> {
                    // Jalr can have an immediate for Syscalls, or default to 0
                    val imm = if (startIndex + 3 < tokens.size) resolveValue(
                        tokens[startIndex + 3],
                        currentPC
                    ) else 0.toShort()
                    instructions += Instruction.Jalr(
                        register1 = tokens[startIndex + 1].toRegisterType(),
                        register2 = tokens[startIndex + 2].toRegisterType(),
                        immediate = imm
                    )
                    currentPC++
                }

                // --- Pseudo-Instructions ---
                "nop" -> {
                    instructions += Instruction.Add(RegisterType.R0, RegisterType.R0, RegisterType.R0)
                    currentPC++
                }

                "halt" -> {
                    instructions += Instruction.Jalr(RegisterType.R0, RegisterType.R0, immediate = 1)
                    currentPC++
                }

                "lli" -> {
                    val imm = resolveValue(tokens[startIndex + 2], currentPC)
                    val maskedImm = (imm.toInt() and 0x3F).toShort() // Bottom 6 bits
                    instructions += Instruction.Addi(
                        register1 = tokens[startIndex + 1].toRegisterType(),
                        register2 = tokens[startIndex + 1].toRegisterType(),
                        immediate = maskedImm
                    )
                    currentPC++
                }

                "movi" -> {
                    val imm = resolveValue(tokens[startIndex + 2], currentPC)
                    val luiPart = (imm.toInt() shr 6).toShort() // Top 10 bits
                    val lliPart = (imm.toInt() and 0x3F).toShort() // Bottom 6 bits
                    val reg = tokens[startIndex + 1].toRegisterType()

                    instructions += Instruction.Lui(register1 = reg, immediate = luiPart)
                    instructions += Instruction.Addi(register1 = reg, register2 = reg, immediate = lliPart)
                    currentPC = (currentPC + 2).toShort()
                }

                ".fill" -> {
                    val value = resolveValue(tokens[startIndex + 1], currentPC)
                    instructions += Instruction.DataWord(value)
                    currentPC++
                }

                ".space" -> {
                    val count = resolveValue(tokens[startIndex + 1], currentPC).toInt()
                    repeat(count) {
                        instructions += Instruction.DataWord(0)
                    }
                    currentPC = (currentPC + count).toShort()
                }

                else -> throw Exception("Assembler Error: Unknown instruction or directive '$opcode'")
            }
        }
        return instructions
    }
}