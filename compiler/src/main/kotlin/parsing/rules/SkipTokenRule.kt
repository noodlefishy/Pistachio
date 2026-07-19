package io.cuttlefish.parsing.rules

import io.cuttlefish.parsing.*

class SkipTokenRule : TokenRule {
    // Now ignores spaces, comments, brackets, commas, plus signs, AND hashes
    private val regularExpression = Regex("""^([ \t+]+|//.*|/\*[\s\S]*?\*/|[\[\],+#])""")

    override fun match(
        source: String, index: Int, line: Int, column: Int
    ): TokenRule.MatchResult? {
        val match = regularExpression.find(source.substring(index)) ?: return null
        if (match.range.first != 0) return null // start of substring must match

        return TokenRule.MatchResult(SkipToken, match.value.length)
    }
}