package io.cuttlefish.parsing.rules

import io.cuttlefish.parsing.*

class CommentsAndWhiteSpaceTokenTokenRule : TokenRule {

    private val regularExpression = Regex("""^(\s+|//.*|/\*[\s\S]*?\*/)""")

    override fun match(
        source: String, index: Int, line: Int, column: Int
    ): TokenRule.MatchResult? {
//        println("match: $source in CommentsAndWhiteSpaceTokenRule")
        val match = regularExpression.find(source.substring(index)) ?: return null
//        println(match)
        if (match.range.first != 0) return null // start of substring must match

        return TokenRule.MatchResult(SkipToken, match.value.length)
    }
}