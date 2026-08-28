package io.cuttlefish.parsing.rules

import io.cuttlefish.MagicValues
import io.cuttlefish.parsing.ImmediateToken
import io.cuttlefish.parsing.TokenRule

class ImmediateTokenRule : TokenRule {
    // Matches Hex (0x), Decimals (10), Negatives (-), Octals (0), and Magic ($)
    private val regex = Regex("""^(-?0x[0-9a-fA-F]+|-?[0-9]+|\$[A-Za-z_]+)\b""")

    override fun match(source: String, index: Int, line: Int, column: Int): TokenRule.MatchResult? {
        val match = regex.find(source.substring(index)) ?: return null
        if (match.range.first != 0) return null

        val lexeme = match.groupValues[1]

        try {
            val shortValue = parseToShort(lexeme)
            return TokenRule.MatchResult(
                ImmediateToken(shortValue, lexeme, line, column),
                match.value.length
            )
        } catch (e: Exception) {
            // If it matches the regex but is too big for a 16-bit Short, fail gracefully
            throw IllegalArgumentException("Number '$lexeme' out of bounds for 16-bit at line $line, col $column")
        }
    }

    private fun parseToShort(str: String): Short {
        return when {
            str.startsWith("0x", ignoreCase = true) -> str.substring(2).toInt(16).toShort()
            str.startsWith("-0x", ignoreCase = true) -> ("-" + str.substring(3)).toInt(16).toShort()
            str.startsWith("$") -> {
                val magicName = str.removePrefix("$").uppercase()
                MagicValues.entries.find { it.name == magicName }?.value
                    ?: throw IllegalArgumentException("Unknown magic value '$str'")
            }
            str.startsWith("0") && str.length > 1 -> str.toInt(8).toShort()
            str.startsWith("-0") && str.length > 2 -> ("-" + str.substring(2)).toInt(8).toShort()
            else -> str.toInt(10).toShort()
        }
    }
}