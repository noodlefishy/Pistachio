package io.cuttlefish.linking

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.*

class Linker(vararg objectFiles: ObjectFile, baseAddress: UShort = 0x3000u) {
    private val objects = objectFiles.toList()
    private var currentAddress = baseAddress
    private val startAddress = baseAddress

    private val fileBaseAddresses = mutableMapOf<File, UShort>() // Future me. `passTwo` will lose context

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


    private fun allocateOutputBuffer() =
        Array<UShort>(objects.map { it.payload.size }.fold(0) { acc, i -> acc + i }) { 0xFFFFu }

    // Warning! `copyRawPayloads` depends on the labels to be in the right order for everything to work out
    private fun copyRawPayloads(buffer: Array<UShort>): Array<UShort> {
        var arrayPointer = 0
        for (obj in groupedByFile.values) {
            for (byte in obj.payload) {
                buffer[arrayPointer] = byte
                arrayPointer++
            }
        }
        return buffer
    }

    private fun relocation(
        buffer: Array<UShort>, labelAddresses: Map<String, UShort>
    ) {
        for ((file, obj) in groupedByFile) {
            val fileBaseAddress = fileBaseAddresses[file]!!
            for (relocatable in obj.relocationTable) { // O(n^2) type shit
                val targetAbsoluteAddress = labelAddresses[relocatable.name]!!
                val instructionAbsoluteAddress = fileBaseAddress + relocatable.offset
                val indexInBuffer = instructionAbsoluteAddress - startAddress
                val instruction  = buffer[indexInBuffer.toInt()]



            }
        }

    }

    /**
     * SGM -> {main=12288, useless=12296, math_add=12297}
     * BFR -> [Lkotlin.UShort;@cd2dae5
     * RLT -> [RelocationTable(offset=4, name=math_add, type=ABS_LUI), RelocationTable(offset=5, name=math_add, type=ABS_LLI)]
     */


    fun passTwo(labelAddresses: Map<String, UShort>) {
//        println("RT $relocationTable")
        val emptyOutPutBuffer = allocateOutputBuffer()
        val buffer = copyRawPayloads(emptyOutPutBuffer)
//        println(buffer.joinToString("\n"))
        relocation(buffer, labelAddresses)

    }

}

val mainFsL = File("/Users/leuw/dev/kotlin/Operating-System/linking tests/main.kar")
val mathsFsL = File("/Users/leuw/dev/kotlin/Operating-System/linking tests/maths.kar")


fun main() {
    val mainO = ObjectExcreter(mainFsL).generate()
    val mathsO = ObjectExcreter(mathsFsL).generate()
    val j = Json { prettyPrint = true }
//    println(mainFsL.absolutePath)
    File("${mainFsL.absolutePath}.json").writeText(j.encodeToString(mainO))
    File("${mathsFsL.absolutePath}.json").writeText(j.encodeToString(mathsO))

    val linker = Linker(mainO, mathsO)
    val p1 = linker.passOne()
    println(p1)
    val p2 = linker.passTwo(p1)

}
// FBA main  = 12288
// FBA maths = 12297