package io.cuttlefish.parsing.macros

import MacroBlt
import io.cuttlefish.*
import io.cuttlefish.parsing.syntaxTree.*

class MacroBgt(
    val rA: RegisterType, val rB: RegisterType, val target: Argument, line: Int, col: Int
) : Statement(line, col) {
    override val size = 13

    override fun generate(context: ParserContext, address: Short): List<Instruction> {
        // rA > rB is identical to rB < rA! Swap rA and rB and run MacroBlt!
        val blt = MacroBlt(rB, rA, target, line, col).apply { this.scope = this@MacroBgt.scope }
        return blt.generate(context, address)
    }
}