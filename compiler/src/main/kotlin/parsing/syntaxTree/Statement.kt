package io.cuttlefish.parsing.syntaxTree

import io.cuttlefish.Instruction
import io.cuttlefish.RegisterType
import io.cuttlefish.linking.RelocationTable
import io.cuttlefish.linking.RelocationType


abstract class Statement(val line: Int, val col: Int) {
    abstract val size: Int
    abstract fun generate(context: ParserContext, address: Short): List<Instruction>

    protected fun resolve(arg: Argument, context: ParserContext, address: Short, type: RelocationType): Short {
        return when (arg) {
            is ImmArg -> arg.value
            is SymArg -> {
                val scopedName = context.resolveScopedName(arg.name)

                if (type == RelocationType.REL_7 && context.symbolTable.containsKey(scopedName)) {
                    val target = context.symbolTable[scopedName]!!
                    return (target - (address + 1)).toShort()
                }

                if (!context.symbolTable.containsKey(scopedName)) {
                    if (scopedName !in context.imports) context.imports.add(scopedName)
                }

                context.relocations.add(RelocationTable(address.toUShort(), scopedName, type))
                0 // Return dummy 0, Linker will overwrite it!
            }
        }
    }
}

class RRRStatement(
    val op: String, val r1: RegisterType, val r2: RegisterType, val r3: RegisterType, line: Int, col: Int
) : Statement(line, col) {
    override val size = 1
    override fun generate(context: ParserContext, address: Short) = listOf(
        when (op) {
            "add" -> Instruction.Add(r1, r2, r3)
            "nand" -> Instruction.Nand(r1, r2, r3)
            else -> throw Exception("Unknown RRR opcode: $op")
        }
    )
}

class RRIStatement(
    val op: String, val r1: RegisterType, val r2: RegisterType, val arg: Argument, line: Int, col: Int
) : Statement(line, col) {
    override val size = 1
    override fun generate(context: ParserContext, address: Short): List<Instruction> {
        val relType = if (op == "beq") RelocationType.REL_7 else RelocationType.ABS_LLI
        val value = resolve(arg, context, address, relType)

        return listOf(
            when (op) {
                "addi" -> Instruction.Addi(r1, r2, value)
                "lw" -> Instruction.Lw(r1, r2, value)
                "sw" -> Instruction.Sw(r1, r2, value)
                "beq" -> Instruction.Beq(r1, r2, value)
                "jalr" -> Instruction.Jalr(r1, r2, value)
                else -> throw Exception("Unknown RRI opcode: $op")
            }
        )
    }
}

// --- EXTENSIBLE MACROS ---

class DirectiveFillString(val text: String, line: Int, col: Int) : Statement(line, col) {
    override val size = text.length + 1 // +1 for null terminator
    override fun generate(context: ParserContext, address: Short): List<Instruction> {
        val insts = text.map { Instruction.DataWord(it.code.toShort()) }.toMutableList()
        insts.add(Instruction.DataWord(0))
        return insts
    }
}

class DirectiveSpace(val countArg: Argument, line: Int, col: Int) : Statement(line, col) {
    override val size: Int
    init {
        if (countArg !is ImmArg) throw Exception("Line $line: .space requires an immediate number!")
        size = countArg.value.toInt()
    }
    override fun generate(context: ParserContext, address: Short): List<Instruction> {
        return List(size) { Instruction.DataWord(0) }
    }
}