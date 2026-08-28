package io.cuttlefish.parsing

interface TokenRule {

    fun match(source: String, index: Int, line: Int, column: Int): MatchResult?


    data class MatchResult(
        val token: Token,
        val charactersConsumed: Int
    )
}