package io.cuttlefish

import io.cuttlefish.linking.*
import java.io.*

class Parser(val file: File, val baseAddress: Short) {

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
        input: String, currentAddress: Short, currentScope: String, isRelative: Boolean = false, type: RelocationType
    ): Short {
        val scopedInput = if (input.startsWith(".")) currentScope + input else input

        if (isRelative && symbolTable.containsKey(scopedInput)) {
            val targetAddress = symbolTable[scopedInput]!!
            return (targetAddress - (currentAddress + 1)).toShort()
        }
        if (input.isNumber()) return input.toNumber()

        if (!symbolTable.containsKey(scopedInput)) {
            imports += scopedInput
        }
        relocations += RelocationTable(currentAddress.toUShort(), scopedInput, type)

        return 0x0000
    }

    private fun throwError(message: String, line: SourceLine): Nothing {
        throw CompilationException(file.name, line, message)
    }

    private fun preprocess(): List<SourceLine> {
        val processedLines = mutableListOf<SourceLine>()
        var inCodeBlock = false

        text.forEachIndexed { index, line ->
            val lineNumber = index + 1 // 1-indexed for humans!!

            // 1. Strip standard comments
            val noComment = line.split(delimiters = arrayOf("//"))[0].trim()
            if (noComment.isEmpty()) return@forEachIndexed

            // 2. Preprocess syntax (strip brackets, commas, plus signs, immediate hashes)
            val cleanLine =
                noComment.replace("[", " ").replace("]", " ").replace("+", " ").replace(",", " ").replace("#", " ")
                    .trim()

            if (cleanLine.isEmpty()) return@forEachIndexed

            val tokens = cleanLine.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return@forEachIndexed

            // 3. Handle block comments (/* and */)
            var startIndex = 0
            if (tokens[0].endsWith(":")) {
                startIndex = 1
            }
            if (startIndex >= tokens.size) {
                processedLines.add(SourceLine(tokens, lineNumber, line))
                return@forEachIndexed
            }

            val activeToken = tokens[startIndex].lowercase()
            if (activeToken == "*/") {
                inCodeBlock = false
                return@forEachIndexed
            }
            if (inCodeBlock) return@forEachIndexed
            if (activeToken == "/*") {
                inCodeBlock = true
                return@forEachIndexed
            }

            processedLines.add(SourceLine(tokens, lineNumber, line))
        }
        return processedLines
    }

    fun decode(): List<Instruction> {
        val instructions = mutableListOf<Instruction>()
        val parsedLines = preprocess()

        // --- PASS 1: Symbol Table Building ---
        var currentGlobalScope = ""
        var addressCounter: Short = baseAddress
        for (line in parsedLines) {
            val tokens = line.tokens
            var startIndex = 0

            try {
                if (tokens[0].endsWith(":")) {
                    var labelName = tokens[0].removeSuffix(":")

                    // If it is a global label, update our scope tracker!!
                    if (!labelName.startsWith(".")) {
                        currentGlobalScope = labelName
                    } else {
                        // If it is a local dot-label, secretly prepend the global scope!!
                        // ".loop" becomes "prints.loop" in the dictionary!!
                        labelName = currentGlobalScope + labelName
                    }

                    symbolTable[labelName] = addressCounter
                    startIndex = 1
                }
                if (startIndex < tokens.size) {
                    val opcode = tokens[startIndex].lowercase()
                    when (opcode) {
                        "movi", "push", "pop" -> addressCounter = (addressCounter + 2).toShort()
                        "call" -> addressCounter = (addressCounter + 3).toShort()
                        ".space" -> {
                            val count = tokens[startIndex + 1].toNumber().toInt()
                            addressCounter = (addressCounter + count).toShort()
                        }

                        ".fill" -> {
                            val remainder = tokens.subList(startIndex + 1, tokens.size).joinToString(" ")
                            if (remainder.contains("\"")) {
                                val content =
                                    remainder.substringAfter("\"").substringBeforeLast("\"").replace("\\n", "\n")
                                addressCounter = (addressCounter + content.length + 1).toShort()
                            } else {
                                addressCounter++
                            }
                        }

                        else -> addressCounter++
                    }
                }
            } catch (_: Exception) {
                throwError("Symbol Table Error: Invalid syntax", line)
            }
        }

        // --- PASS 2: Normal Building ---
// --- PASS 2: Normal Building ---
        var currentPC: Short = baseAddress
        currentGlobalScope = ""

        for (line in parsedLines) {
            val tokens = line.tokens
            var startIndex = 0

            // 1. Process labels and update Scope FIRST!!
            if (tokens[0].endsWith(":")) {
                val labelName = tokens[0].removeSuffix(":")

                if (!labelName.startsWith(".")) {
                    currentGlobalScope = labelName
                } else {
//                    labelName = currentGlobalScope + labelName
                }

                startIndex = 1
            }

            if (startIndex >= tokens.size) continue
            try {
                when (val opcode = tokens[startIndex].lowercase()) {
                    "push" -> {
                        instructions += Instruction.Sw(
                            register1 = tokens[startIndex + 1].toRegisterType(),
                            register2 = RegisterType.R6,
                            immediate = 0
                        )
                        instructions += Instruction.Addi(
                            register1 = RegisterType.R6, register2 = RegisterType.R6, immediate = -1
                        )
                        currentPC = (currentPC + 2).toShort()
                    }

                    "pop" -> {
                        instructions += Instruction.Addi(
                            register1 = RegisterType.R6, register2 = RegisterType.R6, immediate = 1
                        )
                        instructions += Instruction.Lw(
                            register1 = tokens[startIndex + 1].toRegisterType(),
                            register2 = RegisterType.R6,
                            immediate = 0
                        )
                        currentPC = (currentPC + 2).toShort()
                    }

                    "syscall" -> {
                        instructions += Instruction.Jalr(
                            RegisterType.R0, RegisterType.R0, immediate = tokens[startIndex + 1].toNumber()
                        )
                        currentPC++
                    }

                    "call" -> {
                        var immStr = tokens[startIndex + 1]
                        if (immStr.startsWith(".")) immStr = currentGlobalScope + immStr // Apply scope!!

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
                        val reg = RegisterType.R7

                        instructions += Instruction.Lui(register1 = reg, immediate = luiPart)
                        instructions += Instruction.Addi(register1 = reg, register2 = reg, immediate = lliPart)
                        instructions += Instruction.Jalr(reg, reg, 0)
                        currentPC = (currentPC + 3).toShort()
                    }

                    "ret" -> {
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
                        val imm = resolveValue(
                            tokens[startIndex + 3], currentPC, currentGlobalScope, type = RelocationType.ABS_LLI
                        )
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
                        val imm = resolveValue(
                            tokens[startIndex + 2], currentPC, currentGlobalScope, type = RelocationType.ABS_LUI
                        )
                        instructions += Instruction.Lui(
                            register1 = tokens[startIndex + 1].toRegisterType(), immediate = imm
                        )
                        currentPC++
                    }

                    "lw" -> {
                        val imm = resolveValue(
                            tokens[startIndex + 3], currentPC, currentGlobalScope, type = RelocationType.REL_7
                        )
                        instructions += Instruction.Lw(
                            register1 = tokens[startIndex + 1].toRegisterType(),
                            register2 = tokens[startIndex + 2].toRegisterType(),
                            immediate = imm
                        )
                        currentPC++
                    }

                    "sw" -> {
                        val imm = resolveValue(
                            tokens[startIndex + 3], currentPC, currentGlobalScope, type = RelocationType.REL_7
                        )
                        instructions += Instruction.Sw(
                            register1 = tokens[startIndex + 1].toRegisterType(),
                            register2 = tokens[startIndex + 2].toRegisterType(),
                            immediate = imm
                        )
                        currentPC++
                    }

                    "beq" -> {
                        val imm = resolveValue(
                            tokens[startIndex + 3],
                            currentPC,
                            currentGlobalScope,
                            isRelative = true,
                            type = RelocationType.REL_7
                        )
                        instructions += Instruction.Beq(
                            register1 = tokens[startIndex + 1].toRegisterType(),
                            register2 = tokens[startIndex + 2].toRegisterType(),
                            immediate = imm
                        )
                        currentPC++
                    }

                    "jalr" -> {
                        val imm = if (startIndex + 3 < tokens.size) resolveValue(
                            tokens[startIndex + 3], currentPC, currentGlobalScope, type = RelocationType.REL_7
                        ) else 0.toShort()
                        instructions += Instruction.Jalr(
                            register1 = tokens[startIndex + 1].toRegisterType(),
                            register2 = tokens[startIndex + 2].toRegisterType(),
                            immediate = imm
                        )
                        currentPC++
                    }

                    "nop" -> {
                        instructions += Instruction.Add(RegisterType.R0, RegisterType.R0, RegisterType.R0)
                        currentPC++
                    }

                    "halt" -> {
                        instructions += Instruction.Jalr(RegisterType.R0, RegisterType.R0, immediate = 1)
                        currentPC++
                    }

                    "lli" -> {
                        val imm = resolveValue(
                            tokens[startIndex + 2], currentPC, currentGlobalScope, type = RelocationType.ABS_LLI
                        )
                        val maskedImm = (imm.toInt() and 0x3F).toShort()
                        instructions += Instruction.Addi(
                            register1 = tokens[startIndex + 1].toRegisterType(),
                            register2 = tokens[startIndex + 1].toRegisterType(),
                            immediate = maskedImm
                        )
                        currentPC++
                    }

                    "movi" -> {
                        var immStr = tokens[startIndex + 2]
                        if (immStr.startsWith(".")) immStr = currentGlobalScope + immStr // Apply scope!!

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
                        val rawInput = tokens[startIndex + 1]
                        val scopedInput = if (rawInput.startsWith(".")) currentGlobalScope + rawInput else rawInput

                        if (parsed.isNumber() || symbolTable.containsKey(scopedInput)) {
                            val value =
                                resolveValue(rawInput, currentPC, currentGlobalScope, type = RelocationType.ABS_16)
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
                        val count = resolveValue(
                            tokens[startIndex + 1], currentPC, currentGlobalScope, type = RelocationType.ABS_16
                        ).toInt()
                        repeat(count) {
                            instructions += Instruction.DataWord(0)
                        }
                        currentPC = (currentPC + count).toShort()
                    }

                    else -> throw Exception("Assembler Error: Unknown instruction or directive '$opcode'")
                }
            } catch (e: Exception) {
                throwError(e.message ?: "Invalid syntax", line)
            }
        }
        return instructions
    }
}

class CompilationException(
    val fileName: String, val sourceLine: SourceLine, val errorMessage: String
) : Exception()

data class SourceLine(
    val tokens: List<String>, val lineNumber: Int, val rawText: String
)