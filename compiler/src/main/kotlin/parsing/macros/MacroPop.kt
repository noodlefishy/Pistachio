package io.cuttlefish.parsing.macros

import io.cuttlefish.Instruction
import io.cuttlefish.RegisterType
import io.cuttlefish.parsing.syntaxTree.ParserContext
import io.cuttlefish.parsing.syntaxTree.Statement

class MacroPop(val r1: RegisterType, line: Int, col: Int) : Statement(line, col) {
    override val size = 2
    override fun generate(context: ParserContext, address: Short) = listOf(
        Instruction.Addi(RegisterType.R6, RegisterType.R6, -1),
        Instruction.Lw(r1, RegisterType.R6, 0)
    )
}