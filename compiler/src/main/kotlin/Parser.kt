package io.cuttlefish

import io.cuttlefish.Instruction.*
import io.cuttlefish.InstructionType.*
import java.io.*
import java.util.Locale
import java.util.Locale.getDefault

class Parser(val file: File) {
    val text = file.readLines()

    fun decode(): List<Instruction> {
        val instructions = mutableListOf<Instruction>()
        for (line in text) {
            val parts = line.split(" ")
            if (parts.isEmpty()) continue
            val type = InstructionType.valueOf(
                parts[0].lowercase()
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() })
            val curr = when (type) {
                Add -> Add(
                    parts[1].toRegisterType(), parts[2].toRegisterType(), parts[3].toRegisterType()
                )

                Sub -> Sub(
                    parts[1].toRegisterType(), parts[2].toRegisterType(), parts[3].toRegisterType()
                )

                Mul -> Mul(
                    parts[1].toRegisterType(), parts[2].toRegisterType(), parts[3].toRegisterType()
                )

                Div -> Div(
                    parts[1].toRegisterType(), parts[2].toRegisterType(), parts[3].toRegisterType()
                )

                Lit -> Lit(parts[1].toRegisterType(), parts[2].toShort())

                Syscall -> Syscall(
                    parts[1].toRegisterType(), parts[2].toRegisterType(), parts[3].toRegisterType()
                )

                Halt -> throw NotImplementedError()
                Printr -> Printr(parts[1].toRegisterType())
            }


            instructions.add(curr)
        }
        return instructions.toList()
    }

}