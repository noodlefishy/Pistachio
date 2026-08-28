package io.cuttlefish.parsing.macros

import io.cuttlefish.*
import io.cuttlefish.linking.*
import io.cuttlefish.parsing.syntaxTree.*

// bne r0 r1 address
class MacroBne(val rA: RegisterType, val rB: RegisterType, val target: Argument, line: Int, col: Int) :
    Statement(line, col) {
    override val size = 2

    override fun generate(context: ParserContext, address: Short): List<Instruction> {
        val skipInstruction = Instruction.Beq(rA, rB, 1)
        val targetOffset = resolve(target, context, (address + 1).toShort(), RelocationType.REL_7)
        val jumpInstruction = Instruction.Beq(RegisterType.R0, RegisterType.R0, targetOffset)

        return listOf(skipInstruction, jumpInstruction)
    }
}