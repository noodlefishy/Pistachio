package io.cuttlefish.parsing.macros

import io.cuttlefish.*
import io.cuttlefish.parsing.syntaxTree.*

class MacroSyscall(val arg: Argument, line: Int, col: Int) : Statement(line, col) {
    override val size = 1
    override fun generate(context: ParserContext, address: Short): List<Instruction> {
        return listOf(Instruction.Jalr(RegisterType.R0, RegisterType.R0, (arg as ImmArg).value))
    }
}

