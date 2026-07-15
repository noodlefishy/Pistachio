package io.cuttlefish.linking

import io.cuttlefish.*
import io.cuttlefish.backend.*
import java.io.*

class ObjectExcreter(val file: File) {
    fun generate(): ObjectFile {
        val parser = Parser(file, 0)
        val instructions: List<Instruction> = parser.decode()
        val machineCode: List<UShort> = Backend().encode(instructions)
        val symbols: MutableList<SymbolTable> = mutableListOf()

        for ((name: String, address: Short) in parser.symbolTable) {
            // a naming convention to only import if it is not _ or . :3, very smart, old C did this!
            if (!name.startsWith(".") && !name.startsWith("_")) { symbols += SymbolTable(name = name, type = SymbolType.Export, offset = address.toUShort()) }
//            symbols += SymbolTable(name = name, type = SymbolType.Export, offset = address.toUShort())
        }

        parser.imports.forEach { importName: String ->
            symbols += SymbolTable(name = importName, type = SymbolType.Import, offset = 0u)
        }

        val header = Header(
            fileName = file.absolutePath,
            sectionSize = (machineCode.size + 1).toUShort() ,
            symbolCount = symbols.size.toUShort(),
            relocationCount = parser.relocations.size.toUShort(),
        )

        return ObjectFile(
            header = header,
            payload = machineCode + 0u, // Add a nop for security. there was a very bad stack collision bug
            symbolTables = symbols,
            relocationTable = parser.relocations
        )
    }
}