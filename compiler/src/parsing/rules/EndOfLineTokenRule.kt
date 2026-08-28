package io.cuttlefish.parsing.rules

import io.cuttlefish.parsing.EndOfLineToken
import io.cuttlefish.parsing.TokenRule

class EndOfLineTokenRule: TokenRule {
    private val regex = Regex("""^(\r?\n)""")

    override fun match(source: String, index: Int, line: Int, column: Int): TokenRule.MatchResult? {
        val match = regex.find(source.substring(index)) ?: return null
        if (match.range.first != 0) return null

        return TokenRule.MatchResult(EndOfLineToken, match.value.length)
    }
}