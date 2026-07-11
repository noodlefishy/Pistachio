package io.cuttlefish

import io.cuttlefish.linking.*
import java.io.*

class Parser(file: File, val baseAddress: Short) {

    private val text = file.readLines()
    val symbolTable = mutableMapOf<String, Short>()

    val imports = mutableListOf<String>()
    val relocations = mutableListOf<RelocationTable>()


    private fun String.isNumber(): Boolean {
        return try {
            this.toNumber(); true
        } catch (_: NumberFormatException) {
            false
        }
    }

    private fun String.toNumber(): Short {
        val intVal = when {
            this.startsWith("0x", ignoreCase = true) -> this.substring(2).toInt(16)
            this.startsWith("-0x", ignoreCase = true) -> ("-" + this.substring(3)).toInt(16)
            this.startsWith("0") && this.length > 1 -> this.toInt(8)
            this.startsWith("-0") && this.length > 2 -> ("-" + this.substring(2)).toInt(8)
            this.startsWith("$") -> {
                val magic = this.removePrefix("$")
                MagicValues.entries.firstOrNull { it.name == magic }?.value
                    ?: throw IllegalArgumentException("$this is not a predefined magic value!")
            }

            else -> this.toInt(10)
        }
        return intVal.toShort()
    }

    private fun resolveValue(
        input: String, currentAddress: Short, isRelative: Boolean = false, type: RelocationType
    ): Short {
        if (isRelative && symbolTable.containsKey(input)) {
            val targetAddress = symbolTable[input]!!
            return (targetAddress - (currentAddress + 1)).toShort()
        }
        if (input.isNumber()) return input.toNumber()

        if (!symbolTable.containsKey(input)) {
            imports += input
        }
        relocations += RelocationTable(currentAddress.toUShort(), input, type)

        return 0x0000
    }

    fun decode(): List<Instruction> {
        val instructions = mutableListOf<Instruction>()

        val parsedLines = text.map { line ->
            val noComment = line.split(delimiters = arrayOf("//", "#"))[0].trim()
            noComment.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        }.filter { it.isNotEmpty() }

        var addressCounter: Short = baseAddress
        for (tokens in parsedLines) { // symbol building
            var startIndex = 0

            if (tokens[0].endsWith(":")) {
                val labelName = tokens[0].removeSuffix(":")
                symbolTable[labelName] = addressCounter
                startIndex = 1
            }
            // Only add if new pseudo is 1+ instructions long
            if (startIndex < tokens.size) {
                val opcode = tokens[startIndex].lowercase()
                when (opcode) {
                    "movi", "push", "pop" -> addressCounter = (addressCounter + 2).toShort() // LUI + ADDI
                    "call" -> addressCounter = (addressCounter + 3).toShort() // LUI + ADDI + JALR
                    ".space" -> {
                        val count = tokens[startIndex + 1].toNumber().toInt()
                        addressCounter = (addressCounter + count).toShort()
                    }

                    ".fill" -> {
                        val remainder = tokens.subList(startIndex + 1, tokens.size).joinToString(" ")
                        if (remainder.contains("\"")) {
                            val content = remainder.substringAfter("\"").substringBeforeLast("\"").replace("\\n", "\n")
                            addressCounter = (addressCounter + content.length + 1).toShort()
                        } else {
                            addressCounter++
                        }
                    }

                    else -> addressCounter++ // Every other instruction and NOT .fill takes 1 word
                }
            }
        }

        var currentPC: Short = baseAddress
        for (tokens in parsedLines) { // normal building
            val startIndex = if (tokens[0].endsWith(":")) 1 else 0
            // If the line was *only* a label with nothing else, skip generation
            if (startIndex >= tokens.size) continue

            when (val opcode = tokens[startIndex].lowercase()) {
                "push" -> {
                    // push rX  sw rX, r6, 0
                    //          addi r6, r6, 1
                    instructions += Instruction.Sw(
                        register1 = tokens[startIndex + 1].toRegisterType(), register2 = RegisterType.R6, immediate = 0
                    )
                    instructions += Instruction.Addi(
                        register1 = RegisterType.R6, register2 = RegisterType.R6, immediate = -1
                    )
                    currentPC = (currentPC + 2).toShort()
                }

                "pop" -> {
                    // pop rX  addi r6, r6, -1
                    //         lw rX, r6, 0
                    instructions += Instruction.Addi(
                        register1 = RegisterType.R6, register2 = RegisterType.R6, immediate = 1
                    )
                    instructions += Instruction.Lw(
                        register1 = tokens[startIndex + 1].toRegisterType(), register2 = RegisterType.R6, immediate = 0
                    )
                    currentPC = (currentPC + 2).toShort()
                }

                "syscall" -> {
                    // syscall $id
                    instructions += Instruction.Jalr(
                        RegisterType.R0, RegisterType.R0, immediate = tokens[startIndex + 1].toNumber()
                    )
                    currentPC++
                }


                "call" -> {
                    val immStr = tokens[startIndex + 1]
                    var imm: Short = 0
                    if (immStr.isNumber()) {
                        imm = immStr.toNumber()
                    } else {
                        if (!symbolTable.containsKey(immStr)) {
                            imports += immStr
                        }
                        relocations += (RelocationTable(currentPC.toUShort(), immStr, RelocationType.ABS_LUI))
                        relocations += (RelocationTable(
                            (currentPC + 1).toShort().toUShort(), immStr, RelocationType.ABS_LLI // addi
                        ))
                    }

                    val luiPart = (imm.toInt() shr 6).toShort()
                    val lliPart = (imm.toInt() and 0x3F).toShort()
                    val reg = RegisterType.R7

                    instructions += Instruction.Lui(register1 = reg, immediate = luiPart)
                    instructions += Instruction.Addi(register1 = reg, register2 = reg, immediate = lliPart)
                    instructions += Instruction.Jalr(reg, reg, 0)
                    currentPC = (currentPC + 3).toShort()
                }
                "ret" -> {
                    // ret (usually R7)
                    instructions += Instruction.Jalr(RegisterType.R0, RegisterType.R7, 0)
                    currentPC++
                }


                "add" -> {
                    instructions += Instruction.Add(
                        register1 = tokens[startIndex + 1].toRegisterType(),
                        register2 = tokens[startIndex + 2].toRegisterType(),
                        register3 = tokens[startIndex + 3].toRegisterType()
                    )
                    currentPC++
                }

                "addi" -> {
                    val imm = resolveValue(tokens[startIndex + 3], currentPC, type = RelocationType.ABS_LLI)
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
                    val imm = resolveValue(tokens[startIndex + 2], currentPC, type = RelocationType.ABS_LUI)
                    instructions += Instruction.Lui(
                        register1 = tokens[startIndex + 1].toRegisterType(), immediate = imm
                    )
                    currentPC++
                }

                "lw" -> {
                    val imm = resolveValue(tokens[startIndex + 3], currentPC, type = RelocationType.REL_7)
                    instructions += Instruction.Lw(
                        register1 = tokens[startIndex + 1].toRegisterType(),
                        register2 = tokens[startIndex + 2].toRegisterType(),
                        immediate = imm
                    )
                    currentPC++
                }

                "sw" -> {
                    val imm = resolveValue(tokens[startIndex + 3], currentPC, type = RelocationType.REL_7)
                    instructions += Instruction.Sw(
                        register1 = tokens[startIndex + 1].toRegisterType(),
                        register2 = tokens[startIndex + 2].toRegisterType(),
                        immediate = imm
                    )
                    currentPC++
                }

                "beq" -> {
                    val imm =
                        resolveValue(tokens[startIndex + 3], currentPC, isRelative = true, type = RelocationType.REL_7)
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
                        tokens[startIndex + 3], currentPC, type = RelocationType.REL_7
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
                    val imm = resolveValue(tokens[startIndex + 2], currentPC, type = RelocationType.ABS_LLI)
                    val maskedImm = (imm.toInt() and 0x3F).toShort() // Bottom 6 bits
                    instructions += Instruction.Addi(
                        register1 = tokens[startIndex + 1].toRegisterType(),
                        register2 = tokens[startIndex + 1].toRegisterType(),
                        immediate = maskedImm
                    )
                    currentPC++
                }

                "movi" -> {
                    val immStr = tokens[startIndex + 2]
                    var imm: Short = 0
                    if (immStr.isNumber()) {
                        imm = immStr.toNumber()
                    } else {
                        if (!symbolTable.containsKey(immStr)) {
                            imports += immStr
                        }
                        relocations += (RelocationTable(currentPC.toUShort(), immStr, RelocationType.ABS_LUI))
                        relocations += (RelocationTable(
                            (currentPC + 1).toShort().toUShort(), immStr, RelocationType.ABS_LLI
                        ))
                    }

                    val luiPart = (imm.toInt() shr 6).toShort()
                    val lliPart = (imm.toInt() and 0x3F).toShort()
                    val reg = tokens[startIndex + 1].toRegisterType()

                    instructions += Instruction.Lui(register1 = reg, immediate = luiPart)
                    instructions += Instruction.Addi(register1 = reg, register2 = reg, immediate = lliPart)
                    currentPC = (currentPC + 2).toShort()
                }
                ".fill" -> {
                    val parsed = tokens.subList(startIndex + 1, tokens.size).joinToString(" ")
                    if (parsed.all { it.isDigit() } || symbolTable.containsKey(tokens[startIndex + 1])) {
                        val value = resolveValue(tokens[startIndex + 1], currentPC, type = RelocationType.ABS_16)
                        instructions += Instruction.DataWord(value)
                        currentPC++

                    } else if (parsed.toCharArray().count { it == '"' } == 2) {
                        val newChars = parsed.removeSuffix("\"").removePrefix("\"").replace("\\n", "\n")
                        for (char in newChars) {
                            instructions += Instruction.DataWord(char.code.toShort())
                            currentPC++
                        }
                        instructions += Instruction.DataWord(0)
                        currentPC++
                    } else {
                        println(parsed)
                        error("")
                    }


                }

                ".space" -> {
                    val count = resolveValue(tokens[startIndex + 1], currentPC, type = RelocationType.ABS_16).toInt()
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