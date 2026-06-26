package io.cuttlefish

import io.cuttlefish.Instruction.*
import io.cuttlefish.InstructionType.*
import java.io.*

class Parser(val file: File) {
    val text = file.readLines()

    fun decode(): List<Instruction> {
        val instructions = mutableListOf<Instruction>()
        for (line in text) {
            val parts = line.split(" ")
            if (parts.isEmpty()) continue
            val type = InstructionType.valueOf(parts[0].lowercase())
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

                Halt -> TODO()
            }
            instructions += curr
        }
        return instructions.toList()
    }

}