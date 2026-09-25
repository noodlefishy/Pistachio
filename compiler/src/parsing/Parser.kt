package io.cuttlefish.parsing

import io.cuttlefish.*
import io.cuttlefish.parsing.syntaxTree.*
import java.io.*

class Parser(val file: File, val baseAddress: Short) {

    private val rawSource = file.readText()
    private val rawLines = file.readLines() // Used for accurate Error reporting!
    private val ctx = ParserContext()

    val symbolTable get() = ctx.symbolTable
    val imports get() = ctx.imports
    val relocations get() = ctx.relocations

    private fun throwCompileError(message: String, line: Int): Nothing {
        val rawText = rawLines.getOrElse(line - 1) { "" }
        throw CompilationException(file.name, SourceLine(line, rawText), message)
    }

    fun decode(): List<Instruction> {
        // 1. Lexing
        val tokens = try {
            val lexer = Lexer(rawSource)
            lexer.tokenise()
        } catch (e: LexerException) {
            throwCompileError(e.message ?: "Lexer error", e.line)
        }

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
            if (opToken !is MnemonicToken) throwCompileError(
                "Expected instruction, got '${opToken.lexeme}'",
                opToken.line
            )

            reader.index++

            try {
                val builder = StatementRegistry.builders[opToken.lexeme]
                    ?: throwCompileError("Unsupported instruction or macro '${opToken.lexeme}'", opToken.line)

                val stmt = builder(reader, opToken.line, opToken.column)
                stmt.scope = ctx.currentGlobalScope
                statements.add(stmt)

            } catch (e: SyntaxException) {
                throwCompileError(e.message ?: "Syntax Error", opToken.line)
            }
        }

        // =====================================================================
        // 3. PASS 1: Iterative Branch Relaxation Loop (THE CHANGE!)
        // =====================================================================
        var needsRelaxation = true
        var iterations = 0
        val maxIterations = 50

        while (needsRelaxation) {
            needsRelaxation = false
            iterations++
            if (iterations > maxIterations) {
                throwCompileError("Infinite branch relaxation loop detected! Sizing could not stabilize.", 1)
            }

            // Step 3a: Assign PCs to labels and statements based on current sizes
            var pcCounter = baseAddress
            ctx.currentGlobalScope = ""
            val stmtAddresses = mutableMapOf<Statement, Short>()

            for (lineTokens in lines) {
                val first = lineTokens[0]
                if (first is LabelDefToken) {
                    val labelName = if (first.labelName.startsWith(".")) {
                        ctx.currentGlobalScope + first.labelName
                    } else {
                        ctx.currentGlobalScope = first.labelName
                        first.labelName
                    }
                    ctx.symbolTable[labelName] = pcCounter
                }
                val stmtMatch = statements.find { it.line == first.line }
                if (stmtMatch != null) {
                    stmtAddresses[stmtMatch] = pcCounter
                    pcCounter = (pcCounter + stmtMatch.size).toShort()
                }
            }

            // Step 3b: Inspect all SmartBranchStatements. If any target is out of 7-bit range, RELAX IT!
            for (stmt in statements) {
                if (stmt is SmartBranchStatement && !stmt.isLong) {
                    val targetName = stmt.getScopedTargetName()
                    val targetAddr = ctx.symbolTable[targetName]
                    val stmtAddr = stmtAddresses[stmt] ?: continue

                    if (targetAddr != null) {
                        val offset = targetAddr - (stmtAddr + 1)
                        // If outside [-64, 63], expand to 5-word trampoline!
                        if (offset !in -64..63) {
                            stmt.isLong = true
                            needsRelaxation = true // Size changed! Recompute symbol table!
                        }
                    }
                }
            }
        }

        val finalInstructions = mutableListOf<Instruction>()
        var currentGenAddress = baseAddress

        for (stmt in statements) {
            val generated = stmt.generate(ctx, currentGenAddress)
            finalInstructions.addAll(generated)
            currentGenAddress = (currentGenAddress + generated.size).toShort()
        }

        return finalInstructions
    }
}