package io.cuttlefish.linking

import java.io.*

class Linker(vararg objectFiles: ObjectFile, val base: UShort = 0x3000u) {
    private val objects = objectFiles.toList()

    init {
        checkDuplicates()
    }

    val mainF = getMainFile()

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

    fun getMainFile(): File {
        val grouped = objects.groupBy { File(it.header.fileName) }.map { it.key to it.value.first() }.toMap()

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


fun main() {
    val linker = Linker(
        ObjectExcreter(testLinkMainFile).generate(), ObjectExcreter(testLinkMathsFile).generate()
    )
    println(linker.getMainFile())
}