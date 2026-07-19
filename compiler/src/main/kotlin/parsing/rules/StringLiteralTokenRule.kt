package io.cuttlefish.parsing.rules

import io.cuttlefish.parsing.StringLiteralToken
import io.cuttlefish.parsing.TokenRule

class StringLiteralTokenRule : TokenRule {
    // Matches anything enclosed in double quotes.
    private val regex = Regex("""^"([^"]*)"""")

    override fun match(source: String, index: Int, line: Int, column: Int): TokenRule.MatchResult? {
        val match = regex.find(source.substring(index)) ?: return null
        if (match.range.first != 0) return null


        val textContent = match.groupValues[1].replace("\\n", "\n")

        return TokenRule.MatchResult(
            StringLiteralToken(textContent, match.value, line, column),
            match.value.length
        )
    }
}