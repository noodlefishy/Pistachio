package io.cuttlefish.parsing.macros

import io.cuttlefish.*
import io.cuttlefish.parsing.syntaxTree.*

class MacroSub(
    val rA: RegisterType, val rB: RegisterType, val rC: RegisterType, line: Int, col: Int
) : Statement(line, col) {

    override val size = when {
        rA == rB && rB == rC -> 1 // "sub r1, r1, r1" is just "clr r1"!
        rA != rB -> 3
        else -> 5
    }

    override fun generate(context: ParserContext, address: Short): List<Instruction> {
        return when {
            // Case 1: Self-subtraction (sub r1, r1, r1) -> clr r1
            rA == rB && rB == rC -> listOf(
                Instruction.Add(rA, RegisterType.R0, RegisterType.R0)
            )

            // Case 2: Distinct registers (sub r1, r2, r3) -> 3 instructions
            rA != rB -> listOf(
                Instruction.Nand(rA, rC, rC),
                Instruction.Add(rA, rA, rB),
                Instruction.Addi(rA, rA, 1)
            )

            // Case 3: Cumulative subtraction (sub r1, r1, r2) -> 5 instructions
            else -> listOf(
                Instruction.Nand(rC, rC, rC),
                Instruction.Addi(rC, rC, 1),
                Instruction.Add(rA, rA, rC),
                Instruction.Addi(rC, rC, -1),
                Instruction.Nand(rC, rC, rC)
            )
        }
    }
}