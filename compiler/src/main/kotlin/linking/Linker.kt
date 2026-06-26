package io.cuttlefish.linking

import java.io.*

class Linker(vararg objectFiles: ObjectFile, baseAddress: UShort = 0x3000u) {
    private val objects = objectFiles.toList()
    private var currentAddress = baseAddress
    private val initialBaseAddress = baseAddress

    init {
        checkDuplicates()
    }

    val groupedByFile = objects.groupBy { File(it.header.fileName) }.map { it.key to it.value.first() }.toMap()
    val mainF = getMainFile()

    private fun assignLayout(): Map<File, UShort> {
        val fileBaseAddresses = mutableMapOf<File, UShort>()
        for (file in groupedByFile) {
//            println("${file.key.nameWithoutExtension} lives at $currentAddress")
            fileBaseAddresses[file.key] = currentAddress
            currentAddress = (currentAddress + file.value.payload.size.toUShort()).toUShort()
        }
        return fileBaseAddresses
    }

    private fun generateSymbolTable(assignedLayout: Map<File, UShort>): Map<String, UShort> {
        val global: MutableMap<String, UShort> = mutableMapOf()
        for ((file: File, objectFile: ObjectFile) in groupedByFile) {
            for (symbol in objectFile.symbolTables) {
                global[symbol.name] = assignedLayout[file]!!
                if (symbol.type == SymbolType.Import) continue
                global[symbol.name] = (assignedLayout[file]!! + symbol.offset).toUShort()
            }
        }
        return global
    }


    fun passOne(): Map<String, UShort> {
        val layout = assignLayout()
        val absolute = generateSymbolTable(layout)
        return absolute
    }

    private fun checkDuplicates() {
        val combined = mutableSetOf<String>()
        val totalObjectsWithoutFiles = objects.flatMap { it.symbolTables }
        if (totalObjectsWithoutFiles.none { it.name == "main" }) throw IllegalStateException($$$$$$$$$$$"Incorrect main function configuration ${}")
        if (totalObjectsWithoutFiles.filter { it.name == "main" }.size != 1 && totalObjectsWithoutFiles.filter { it.name == "main" }[0].type == SymbolType.Export) {
            throw IllegalStateException($$$$$$$$$$$"Incorrect main function configuration ${}")
            // multi dollar interpolation is just an ahh feature
            // This was picked over union types 💔
        }
        for (symbol in objects.flatMap { it.symbolTables }) {
            val embedName = "${if (symbol.type == SymbolType.Export) "e" else "i"}${symbol.name}"
            if (embedName !in combined) {
                combined += embedName
            } else throw IllegalStateException("Duplicate Symbol ${symbol.name}")
        }
    }

    private fun getMainFile(): File {
        val grouped = groupedByFile

        for ((file, objectFile) in grouped.entries) {
            objectFile.symbolTables.forEach { symbol ->
                if (symbol.type == SymbolType.Export && symbol.name == "main") {
                    return file
                }
            }
        }
        throw IllegalStateException($$$$$$$$$$$"Incorrect main function configuration ${}")
    }

    fun link(): List<UShort> {
        currentAddress = initialBaseAddress
        val layout = assignLayout()
        val absoluteSymbols = generateSymbolTable(layout)
        val finalMainMachineCode = mutableListOf<UShort>()

        for (payload in objects) { // 2a
            finalMainMachineCode.addAll(payload.payload)
        }
        for ((file, objectFile) in groupedByFile) {
            val fileBase = layout[file]!!

            for (relocatedSymbol in objectFile.relocationTable) {
                val targetAddress = absoluteSymbols[relocatedSymbol.name]
                    ?: throw IllegalStateException("Unresolved Symbol ${relocatedSymbol.name}")

                val instructionAddress = (fileBase + relocatedSymbol.offset).toUShort()
                val outputIndex = (instructionAddress - initialBaseAddress).toInt()

                val unpatchedInstructionAddress = finalMainMachineCode[outputIndex]
                finalMainMachineCode[outputIndex] = applyRelocation(
                    instruction = unpatchedInstructionAddress,
                    type = relocatedSymbol.type,
                    targetAddress = targetAddress,
                    instructionAddress = instructionAddress
                )
            }
        }
        return finalMainMachineCode

    }


    private fun applyRelocation(
        instruction: UShort, type: RelocationType, targetAddress: UShort, instructionAddress: UShort
    ): UShort {
        when (type) {
            RelocationType.ABS_16 -> return targetAddress
            RelocationType.ABS_LUI -> {
                val upper10 = (targetAddress.toInt() shr 6) and 0x3FF
                val maskedInst = instruction.toInt() and 0xFC00
                return (maskedInst or upper10).toUShort()
            }

            RelocationType.ABS_LLI -> {
                val lower6 = targetAddress.toInt() and 0x3F
                val maskedInst = instruction.toInt() and 0xFF80
                return (maskedInst or lower6).toUShort()
            }

            RelocationType.REL_7 -> {
                val offset = targetAddress.toInt() - (instructionAddress.toInt() + 1)
                if (offset !in -64..63) {
                    throw IllegalStateException("Linker Error: Branch target out of range. Offset $offset")
                }
                val maskedInst = instruction.toInt() and 0xFF80
                return (maskedInst or (offset and 0x7F)).toUShort()

            }
        }
    }

}


fun main() {
    val linker = Linker(
        ObjectExcreter(testLinkMainFile).generate(), ObjectExcreter(testLinkMathsFile).generate()
    )
    println(linker.passOne())

    val binary = linker.link()
    binary.forEachIndexed { i, word ->
        val address = 0x3000 + i
        println("0x${address.toString(16).uppercase()}: 0x${word.toString(16).padStart(4, '0').uppercase()}")
    }

}