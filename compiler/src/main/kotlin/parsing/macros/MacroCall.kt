package io.cuttlefish.parsing.macros

import io.cuttlefish.Instruction
import io.cuttlefish.RegisterType
import io.cuttlefish.parsing.syntaxTree.Argument
import io.cuttlefish.parsing.syntaxTree.ParserContext
import io.cuttlefish.parsing.syntaxTree.Statement

class MacroCall(val arg: Argument, line: Int, col: Int) : Statement(line, col) {
    override val size = 3
    override fun generate(context: ParserContext, address: Short): List<Instruction> {
        // Automatically translates to: movi r7, arg -> jalr r0, r7, 0
        val movi = MacroMovi(RegisterType.R7, arg, line, col).generate(context, address)
        val jump = Instruction.Jalr(RegisterType.R0, RegisterType.R7, 0)
        return movi + jump
    }
}