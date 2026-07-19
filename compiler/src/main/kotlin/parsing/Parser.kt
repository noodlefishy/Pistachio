package io.cuttlefish.parsing

import io.cuttlefish.*
import io.cuttlefish.parsing.syntaxTree.*
import java.io.*


class Parser(val file: File, val baseAddress: Short) {

    private val rawSource = file.readText()
    private val rawLines = file.readLines() // Used for accurate Error reporting!
    private val ctx = ParserContext(baseAddress)

    val symbolTable get() = ctx.symbolTable
    val imports get() = ctx.imports
    val relocations get() = ctx.relocations

    @Suppress("unused")
    private fun throwCompileError(message: String, line: Int, col: Int): Nothing {
        val rawText = rawLines.getOrElse(line - 1) { "" }
        throw CompilationException(file.name, SourceLine(line, rawText), message)
    }

    fun decode(): List<Instruction> {
        // 1. Lexing
        val lexer = Lexer(rawSource)
        val tokens = lexer.tokenise()

        // 2. Syntax Analysis (Tokens -> AST Statements)
        val statements = mutableListOf<Statement>()
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
//            var i = 0
//            val first = lineTokens[i]
            val reader = TokenReader(lineTokens)
            val first = reader.peek() ?: continue


            // Labels
            if (first is LabelDefToken) {
                var labelName = first.labelName
                if (!labelName.startsWith(".")) ctx.currentGlobalScope = labelName
                else labelName = ctx.currentGlobalScope + labelName

                ctx.symbolTable[labelName] = 0 // Placeholder size, updated in Pass 1
                reader.index++
            }

            if (!reader.hasNext()) continue

            val opToken = lineTokens[reader.index]
            if (opToken !is MnemonicToken) throwCompileError("Expected instruction, got '${opToken.lexeme}'", opToken.line, opToken.column)

            reader.index++

            try {
                val builder = StatementRegistry.builders[opToken.lexeme]
                    ?: throwCompileError("Unsupported instruction or macro '${opToken.lexeme}'", opToken.line, opToken.column)

                val stmt = builder(reader, opToken.line, opToken.column)
                statements.add(stmt)

            } catch (e: SyntaxException) {
                throwCompileError(e.message ?: "Syntax Error", opToken.line, opToken.column)
            }
        }

        var pcCounter = baseAddress
        ctx.currentGlobalScope = ""

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