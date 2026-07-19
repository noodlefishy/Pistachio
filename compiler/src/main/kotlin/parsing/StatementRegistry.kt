package io.cuttlefish.parsing

import io.cuttlefish.parsing.macros.*
import io.cuttlefish.parsing.syntaxTree.*

typealias StatementBuilder = (TokenReader, Int, Int) -> Statement

object StatementRegistry {

    val builders = mutableMapOf<String, StatementBuilder>(
        // Hardware Instructions
        "add" to { r, line, col -> RRRStatement("add", r.nextReg(), r.nextReg(), r.nextReg(), line, col) },
        "nand" to { r, line, col -> RRRStatement("nand", r.nextReg(), r.nextReg(), r.nextReg(), line, col) },

        "addi" to { r, line, col -> RRIStatement("addi", r.nextReg(), r.nextReg(), r.nextArg(), line, col) },
        "lw" to { r, line, col -> RRIStatement("lw", r.nextReg(), r.nextReg(), r.nextArg(), line, col) },
        "sw" to { r, line, col -> RRIStatement("sw", r.nextReg(), r.nextReg(), r.nextArg(), line, col) },
        "beq" to { r, line, col -> RRIStatement("beq", r.nextReg(), r.nextReg(), r.nextArg(), line, col) },
        "jalr" to { r, line, col -> RRIStatement("jalr", r.nextReg(), r.nextReg(), r.nextArg(), line, col) },

        "lui" to { r, line, col -> RIStatement("lui", r.nextReg(), r.nextArg(), line, col) },

        // Macros
        "or" to { r, line, col -> MacroOr(r.nextReg(), r.nextReg(), r.nextReg(), line, col) },
        "and" to { r, line, col -> MacroAnd(r.nextReg(), r.nextReg(), r.nextReg(), line, col) },

        "not" to { r, line, col -> MacroNot(r.nextReg(), r.nextReg(), line, col) },
        "mov" to { r, line, col -> MacroMov(r.nextReg(), r.nextReg(), line, col) },
        "clr" to { r, line, col -> MacroClr(r.nextReg(), line, col) },
        "bne" to { r, line, col -> MacroBne(r.nextReg(), r.nextReg(), r.nextArg(), line, col) },
        "subi" to { r, line, col -> MacroSubi(r.nextReg(), r.nextReg(), r.nextArg(), line, col) },
        "sub" to { r, line, col -> MacroSub(r.nextReg(), r.nextReg(), r.nextReg(), line, col) },
        "lli" to { r, line, col -> MacroLli(r.nextReg(), r.nextArg(), line, col) },
        "push" to { r, line, col -> MacroPush(r.nextReg(), line, col) },
        "pop" to { r, line, col -> MacroPop(r.nextReg(), line, col) },
        "movi" to { r, line, col -> MacroMovi(r.nextReg(), r.nextArg(), line, col) },
        "call" to { r, line, col -> MacroCall(r.nextArg(), line, col) },
        "ret" to { _, line, col -> MacroRet(line, col) },
        "halt" to { _, line, col -> MacroHalt(line, col) },
        "syscall" to { r, line, col -> MacroSyscall(r.nextArg(), line, col) },

        // Directives
        ".space" to { r, line, col -> DirectiveSpace(r.nextArg(), line, col) },
        ".fill" to { r, line, col ->
            if (r.peek() is StringLiteralToken) DirectiveFillString(r.nextString(), line, col)
            else DirectiveFillImmediate(r.nextArg(), line, col)
        })

    @Suppress("Unused")
    fun register(opcode: String, builder: StatementBuilder) {
        builders[opcode.lowercase()] = builder
    }
}