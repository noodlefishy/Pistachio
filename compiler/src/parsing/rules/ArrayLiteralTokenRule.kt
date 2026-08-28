package io.cuttlefish.parsing.rules

import io.cuttlefish.*
import io.cuttlefish.parsing.*
import io.cuttlefish.parsing.syntaxTree.Argument
import io.cuttlefish.parsing.syntaxTree.ImmArg
import io.cuttlefish.parsing.syntaxTree.SymArg

class ArrayLiteralTokenRule : TokenRule {
    // Matches anything enclosed in curly braces { ... }
    private val regex = Regex("""^\{([^}]*)}""")

    override fun match(source: String, index: Int, line: Int, column: Int): TokenRule.MatchResult? {
        val match = regex.find(source.substring(index)) ?: return null
        if (match.range.first != 0) return null

        val content = match.groupValues[1].trim()

        // Split inner items by spaces or commas!
        val rawItems = content.split(Regex("""[\s,]+""")).filter { it.isNotEmpty() }
        val parsedElements = mutableListOf<Argument>()

        for (item in rawItems) {
            val arg = parseItemToArgument(item, line, column)
            parsedElements.add(arg)
        }

        return TokenRule.MatchResult(
            ArrayLiteralToken(parsedElements, match.value, line, column),
            match.value.length
        )
    }

    private fun parseItemToArgument(item: String, line: Int, col: Int): Argument {
        val num = parseNumberOrNull(item)
        return if (num != null) {
            ImmArg(num)
        } else {
            SymArg(item, line, col) // Allows label names inside array literals too!
        }
    }

    private fun parseNumberOrNull(str: String): Short? {
        return try {
            when {
                str.startsWith("0x", ignoreCase = true) -> str.substring(2).toInt(16).toShort()
                str.startsWith("-0x", ignoreCase = true) -> ("-" + str.substring(3)).toInt(16).toShort()
                str.startsWith("$") -> {
                    val magicName = str.removePrefix("$").uppercase()
                    MagicValues.entries.find { it.name == magicName }?.value
                }
                str.startsWith("0") && str.length > 1 -> str.toInt(8).toShort()
                str.startsWith("-0") && str.length > 2 -> ("-" + str.substring(2)).toInt(8).toShort()
                else -> str.toInt(10).toShort()
            }
        } catch (_: Exception) {
            null
        }
    }
}