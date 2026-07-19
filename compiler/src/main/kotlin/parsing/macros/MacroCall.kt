package io.cuttlefish.parsing.macros

import io.cuttlefish.*
import io.cuttlefish.parsing.syntaxTree.*

class MacroCall(val arg: Argument, line: Int, col: Int) : Statement(line, col) {
    override val size = 3
    override fun generate(context: ParserContext, address: Short): List<Instruction> {
        // Automatically translates to: movi r7, arg -> jalr r0, r7, 0
        val movi = MacroMovi(RegisterType.R7, arg, line, col).apply { this.scope = this@MacroCall.scope }
        val jump = Instruction.Jalr(RegisterType.R7, RegisterType.R7, 0)
        return movi.generate(context, address) + jump
    }
}