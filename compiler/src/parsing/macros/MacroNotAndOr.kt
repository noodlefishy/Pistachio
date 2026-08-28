package io.cuttlefish.parsing.macros

import io.cuttlefish.*
import io.cuttlefish.parsing.syntaxTree.*


class MacroNot(val rA: RegisterType, val rB: RegisterType, line: Int, col: Int) : Statement(line, col) {
    override val size = 1
    override fun generate(context: ParserContext, address: Short): List<Instruction> =
        listOf(Instruction.Nand(rA, rB, rB))
}

class MacroAnd(val rA: RegisterType, val rB: RegisterType, val rC: RegisterType, line: Int, col: Int) :
    Statement(line, col) {
    override val size = 2
    override fun generate(context: ParserContext, address: Short): List<Instruction> =
        listOf(Instruction.Nand(rA, rB, rC), Instruction.Nand(rA, rA, rA))
}

class MacroOr(val rA: RegisterType, val rB: RegisterType, val rC: RegisterType, line: Int, col: Int) :
    Statement(line, col) {

    override val size = if (rB == rC) 2 else 4

    override fun generate(context: ParserContext, address: Short): List<Instruction> {
        return when {
            rB == rC -> {
                // Optimisation: "or r1, r2, r2" is mathematically just a register copy!
                listOf(
                    Instruction.Nand(rA, rB, rB), Instruction.Nand(rA, rA, rA)
                )
            }

            rA == rB -> {
                // "or r1, r1, r2": temporarily negate r2, do math, restore r2
                listOf(
                    Instruction.Nand(rA, rA, rA),
                    Instruction.Nand(rC, rC, rC),
                    Instruction.Nand(rA, rA, rC),
                    Instruction.Nand(rC, rC, rC)
                )
            }

            rA == rC -> {
                // "or r1, r2, r1": swap rB and rC and run the same
                listOf(
                    Instruction.Nand(rA, rA, rA),
                    Instruction.Nand(rB, rB, rB),
                    Instruction.Nand(rA, rA, rB),
                    Instruction.Nand(rB, rB, rB)
                )
            }

            else -> {
                // Distinct registers: "or r1, r2, r3". Temporarily negate r2, restore at end
                listOf(
                    Instruction.Nand(rB, rB, rB),
                    Instruction.Nand(rA, rC, rC),
                    Instruction.Nand(rA, rA, rB),
                    Instruction.Nand(rB, rB, rB)
                )
            }
        }
    }
}


//nand rB, rB, rB      // 1. rB = ~rB      (Temporarily negated)
//nand rA, rC, rC      // 2. rA = ~rC      (Dest is used as a scratchpad)
//nand rA, rA, rB      // 3. rA = NAND(~rC, ~rB) -> rA = rC | rB
//nand rB, rB, rB      // 4. rB = ~~rB     (Perfected restored!)