package io.cuttlefish.parsing

import io.cuttlefish.*
import io.cuttlefish.parsing.macros.*
import io.cuttlefish.parsing.syntaxTree.*
import java.io.*

// --- 3. The Core Parser ---

class Parser(val file: File, val baseAddress: Short) {

    private val rawSource = file.readText()
    private val ctx = ParserContext(baseAddress)

    val symbolTable get() = ctx.symbolTable
    val imports get() = ctx.imports
    val relocations get() = ctx.relocations

    private fun throwCompileError(message: String, line: Int, col: Int): Nothing {
        throw CompilationException(file.name, SourceLine(emptyList()), message)
    }

    fun decode(): List<Instruction> {
        // 1. Lexing
        val lexer = Lexer(rawSource)
        val tokens = lexer.tokenise()

        // 2. Syntax Analysis (Tokens -> AST Statements)
        val statements = mutableListOf<Statement>()

        // Group tokens by Line
        val lines = mutableListOf<List<Token>>()
        var currentLine = mutableListOf<Token>()
        for (t in tokens) {
            if (t is EndOfLineToken) {
                if (currentLine.isNotEmpty()) lines.add(currentLine)
                currentLine = mutableListOf()
            } else {
                currentLine.add(t)
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)

        // Parse each line into Statements
        for (lineTokens in lines) {
            var i = 0
            val first = lineTokens[i]

            // Labels
            if (first is LabelDefToken) {
                var labelName = first.labelName
                if (!labelName.startsWith(".")) ctx.currentGlobalScope = labelName
                else labelName = ctx.currentGlobalScope + labelName

                // Assign label to pending size calculation
                ctx.symbolTable[labelName] = 0 // Placeholder, updated in Pass 1
                i++
            }

            if (i >= lineTokens.size) continue

            val opToken = lineTokens[i]
            if (opToken !is MnemonicToken) throwCompileError(
                "Expected instruction, got '${opToken.lexeme}'", opToken.line, opToken.column
            )

            i++

            // Helper to safely extract arguments
            fun nextReg(): RegisterType {
                val t = lineTokens.getOrNull(i++)
                if (t is RegisterToken) return t.registerName
                throwCompileError("Expected Register", opToken.line, opToken.column)
            }

            fun nextArg(): Argument {
                val t = lineTokens.getOrNull(i++)
                if (t is ImmediateToken) return ImmArg(t.value)
                if (t is SymbolReferenceToken) return SymArg(t.symbolName, t.line, t.column)
                throwCompileError("Expected Number or Label", opToken.line, opToken.column)
            }

            // Map token sequence to Statements!
            val stmt = when (opToken.lexeme) {
                "add", "nand" -> RRRStatement(
                    opToken.lexeme, nextReg(), nextReg(), nextReg(), opToken.line, opToken.column
                )

                "addi", "lw", "sw", "beq", "jalr" -> RRIStatement(
                    opToken.lexeme, nextReg(), nextReg(), nextArg(), opToken.line, opToken.column
                )

                "push" -> MacroPush(nextReg(), opToken.line, opToken.column)
                "pop" -> MacroPop(nextReg(), opToken.line, opToken.column)
                "movi" -> MacroMovi(nextReg(), nextArg(), opToken.line, opToken.column)
                // Add your other macros here (call, ret, halt, etc.)

                ".space" -> DirectiveSpace(nextArg(), opToken.line, opToken.column)
                ".fill" -> {
                    val t = lineTokens.getOrNull(i)
                    if (t is StringLiteralToken) DirectiveFillString(t.text, opToken.line, opToken.column)
                    else {
                        i++; DirectiveFillImmediate(nextArg(), opToken.line, opToken.column)
                    }
                }

                else -> throwCompileError("Unsupported instruction '${opToken.lexeme}'", opToken.line, opToken.column)
            }
            statements.add(stmt)
        }

        var pcCounter = baseAddress
        ctx.currentGlobalScope = "" // Reset scope

        for (lineTokens in lines) {
            val first = lineTokens[0]
            if (first is LabelDefToken) {
                val labelName = if (first.labelName.startsWith(".")) ctx.currentGlobalScope + first.labelName else {
                    ctx.currentGlobalScope = first.labelName
                    first.labelName
                }
                ctx.symbolTable[labelName] = pcCounter
            }
            val stmtMatch = statements.find { it.line == first.line }
            if (stmtMatch != null) pcCounter = (pcCounter + stmtMatch.size).toShort()
        }

        val finalInstructions = mutableListOf<Instruction>()
        var currentGenAddress = baseAddress

        for (stmt in statements) {
            val generated = stmt.generate(ctx, currentGenAddress)
            finalInstructions.addAll(generated)
            currentGenAddress = (currentGenAddress + stmt.size).toShort()
        }

        return finalInstructions
    }
}