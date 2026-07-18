package io.cuttlefish.parsing.rules

import io.cuttlefish.parsing.TokenRule

class CommentsAndWhiteSpaceTokenTokenRule: TokenRule {

    private val regularExpression = Regex("""
        ^(\s+|
        //.*|
        /\*[\s\S]*?\*/)
    """.trimIndent())

    override fun match(
        source: String,
        index: Int,
        line: Int,
        column: Int
    ): TokenRule.MatchResult? {
        TODO("Not yet implemented")
    }
}