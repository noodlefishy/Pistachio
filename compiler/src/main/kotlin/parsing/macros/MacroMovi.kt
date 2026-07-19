package io.cuttlefish.parsing.macros

import io.cuttlefish.Instruction
import io.cuttlefish.RegisterType
import io.cuttlefish.linking.RelocationTable
import io.cuttlefish.linking.RelocationType
import io.cuttlefish.parsing.syntaxTree.Argument
import io.cuttlefish.parsing.syntaxTree.ImmArg
import io.cuttlefish.parsing.syntaxTree.ParserContext
import io.cuttlefish.parsing.syntaxTree.Statement
import io.cuttlefish.parsing.syntaxTree.SymArg

class MacroMovi(val r1: RegisterType, val arg: Argument, line: Int, col: Int) : Statement(line, col) {
    override val size = 2
    override fun generate(context: ParserContext, address: Short): List<Instruction> {
        var luiPart: Short = 0
        var lliPart: Short = 0

        when (arg) {
            is ImmArg -> {
                luiPart = (arg.value.toInt() shr 6).toShort()
                lliPart = (arg.value.toInt() and 0x3F).toShort()
            }
            is SymArg -> {
                val scoped = context.resolveScopedName(arg.name)
                if (!context.symbolTable.containsKey(scoped) && scoped !in context.imports) {
                    context.imports.add(scoped)
                }
                context.relocations.add(RelocationTable(address.toUShort(), scoped, RelocationType.ABS_LUI))
                context.relocations.add(
                    RelocationTable(
                        (address + 1).toShort().toUShort(),
                        scoped,
                        RelocationType.ABS_LLI
                    )
                )
            }
        }
        return listOf(Instruction.Lui(r1, luiPart), Instruction.Addi(r1, r1, lliPart))
    }
}