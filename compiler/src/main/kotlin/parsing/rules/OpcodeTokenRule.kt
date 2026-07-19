package io.cuttlefish.parsing.rules

import io.cuttlefish.parsing.OpcodeToken
import io.cuttlefish.parsing.TokenRule

class OpcodeTokenRule : TokenRule {
    private val validOpcodes = setOf(
        "add", "addi", "nand", "lui", "lw", "sw", "beq", "jalr",
        "movi", "lli", "push", "pop", "call", "ret", "syscall", "halt", "nop",
        ".fill", ".space"
    )

    // Note this matches any word, opcode or not, we check later if it is valid
    private val regex = Regex("""^(?i)(\.?[a-z_]+)\b""")

    override fun match(source: String, index: Int, line: Int, column: Int): TokenRule.MatchResult? {
        val match = regex.find(source.substring(index)) ?: return null
        if (match.range.first != 0) return null

        val lexeme = match.groupValues[1].lowercase()

        if (lexeme !in validOpcodes) return null

        return TokenRule.MatchResult(
            OpcodeToken(lexeme, line, column),
            match.value.length
        )
    }
}