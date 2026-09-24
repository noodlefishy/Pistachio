package io.cuttlefish.parsing.macros


import io.cuttlefish.*
import io.cuttlefish.parsing.syntaxTree.*

class MacroNop(line: Int, col: Int) : Statement(line, col) {
    override val size = 1
    override fun generate(context: ParserContext, address: Short): List<Instruction> =
        listOf(Instruction.Add(RegisterType.R0, RegisterType.R0, RegisterType.R0))
}