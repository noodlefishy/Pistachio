package io.cuttlefish.parsing.rules

import io.cuttlefish.RegisterType
import io.cuttlefish.parsing.RegisterToken
import io.cuttlefish.parsing.TokenRule

class RegisterTokenRule : TokenRule {
    private val regex = Regex("""^(?i)(r[0-7])\b""")

    override fun match(source: String, index: Int, line: Int, column: Int): TokenRule.MatchResult? {
        val match = regex.find(source.substring(index)) ?: return null
        if (match.range.first != 0) return null

        val lexeme = match.groupValues[1]

        val registerType = RegisterType.valueOf(lexeme.uppercase())

        return TokenRule.MatchResult(
            RegisterToken(registerType, lexeme, line, column),
            match.value.length
        )
    }
}