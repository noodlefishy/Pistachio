package io.cuttlefish.parsing.rules

import io.cuttlefish.parsing.SymbolReferenceToken
import io.cuttlefish.parsing.TokenRule


class SymbolReferenceTokenRule : TokenRule {
    // Matches standard variable names (eg, main, .loop, string)
    private val regex = Regex("""^(\.?[a-zA-Z_][a-zA-Z0-9_.]*)""")

    override fun match(source: String, index: Int, line: Int, column: Int): TokenRule.MatchResult? {
        val match = regex.find(source.substring(index)) ?: return null
        if (match.range.first != 0) return null

        val lexeme = match.groupValues[1]

        return TokenRule.MatchResult(
            SymbolReferenceToken(lexeme, lexeme, line, column),
            match.value.length
        )
    }
}