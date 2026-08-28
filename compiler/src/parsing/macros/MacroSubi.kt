package io.cuttlefish.parsing.macros

import io.cuttlefish.*
import io.cuttlefish.parsing.syntaxTree.*

class MacroSubi(
    val rA: RegisterType, val rB: RegisterType, val imm: Argument, line: Int, col: Int
) : Statement(line, col) {

    // The compiler automatically calculates the exact binary size!
    override val size = 1

    override fun generate(context: ParserContext, address: Short): List<Instruction> {
//        TODO("Should have error checking") // Should NOT  have error checking <3
        return listOf(Instruction.Addi(rA, rB, (-(imm as ImmArg).value).toShort()))
    }
}