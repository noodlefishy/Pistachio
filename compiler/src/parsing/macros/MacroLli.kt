package io.cuttlefish.parsing.macros

import io.cuttlefish.Instruction
import io.cuttlefish.RegisterType
import io.cuttlefish.linking.RelocationType
import io.cuttlefish.parsing.syntaxTree.Argument
import io.cuttlefish.parsing.syntaxTree.ImmArg
import io.cuttlefish.parsing.syntaxTree.ParserContext
import io.cuttlefish.parsing.syntaxTree.Statement

class MacroLli(val r1: RegisterType, val arg: Argument, line: Int, col: Int) : Statement(line, col) {
    override val size = 1
    override fun generate(context: ParserContext, address: Short): List<Instruction> {
        val value = resolve(arg, context, address, RelocationType.ABS_LLI)

        val maskedValue = if (arg is ImmArg) {
            (value.toInt() and 0x3F).toShort()
        } else {
            value
        }

        return listOf(Instruction.Addi(r1, r1, maskedValue))
    }
}