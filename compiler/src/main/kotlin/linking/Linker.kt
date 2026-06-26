package io.cuttlefish.linking

import io.cuttlefish.*
import io.cuttlefish.backend.*
import java.io.*

class Linker(val file: File) {
    val symbolTable = mutableMapOf<String, Short>()
    val parser = Parser(file, 0)


    private fun compile(): Pair<List<UShort>, List<Instruction>> {
        symbolTable += parser.symbolTable
        val parse = parser.decode()
        Parser(file, 0).symbolTable
        return Backend().encode(parse) to parse
    }

    fun link(data: Pair<List<UShort>, List<Instruction>>): ObjectFile {
        val machineCode = data.first
        val instructions = data.second
//ABS_16, ABS_LUI, ABS_LLI, REL_7 }
        val relocations = instructions.map {
            when (it) {
                is Instruction.Addi -> RelocationType.ABS_LLI
                is Instruction.Beq -> RelocationType.REL_7
                is Instruction.DataWord -> RelocationType.ABS_16
                is Instruction.Jalr -> RelocationType.REL_7
                is Instruction.Lui -> RelocationType.ABS_LUI
                is Instruction.Lw -> RelocationType.REL_7
                is Instruction.Sw -> RelocationType.REL_7
                else -> null
            }
        }


        val header = Header(
            fileName = file.path,
            sectionSize = machineCode.size.toUShort(),
            symbolCount = symbolTable.size.toUShort(),
            relocationCount = relocations.size.toUShort(),
        )

//        val serialization = ObjectFile()
    }
}