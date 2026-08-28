package io.cuttlefish.parsing.macros

import io.cuttlefish.*
import io.cuttlefish.parsing.syntaxTree.*

class MacroMov(val rA: RegisterType, val rB: RegisterType, line: Int, col: Int) : Statement(line, col) {
    override val size = 1
    override fun generate(context: ParserContext, address: Short): List<Instruction> =
        listOf(Instruction.Add(rA, RegisterType.R0, rB))
}