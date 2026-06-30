package io.cuttlefish.linking

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.*

class Linker(vararg objectFiles: ObjectFile, baseAddress: UShort = 0x3000u) {
    private val objects = objectFiles.toList()
    private var currentAddress = baseAddress

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

}

val mainFsL = File("/Users/leuw/dev/kotlin/Operating-System/linking tests/main.kar")
val mathsFsL = File("/Users/leuw/dev/kotlin/Operating-System/linking tests/maths.kar")


fun main() {
    val mainO = ObjectExcreter(mainFsL).generate()
    val mathsO = ObjectExcreter(mathsFsL).generate()
    val j = Json { prettyPrint = true }
    File("${mainFsL.nameWithoutExtension}.json").writeText(j.encodeToString(mainO))
    File("${mathsFsL.nameWithoutExtension}.json").writeText(j.encodeToString(mathsO))


    val linker = Linker(mainO, mathsO)

}