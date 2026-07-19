package io.cuttlefish.parsing

class Lexer(private val source: String) {
    private var index = 0
    private var line = 1
    private var column = 1

    fun tokenise(): List<Token> {
        val tokens = mutableListOf<Token>()
        val rules = SyntaxRegistry().rules.toList()

        while (index < source.length) {
            var matched = false

            for (rule in rules) {
                val result = rule.match(source, index, line, column)
                if (result != null) {
                    matched = true
                    advance(result.charactersConsumed)
                    if (result.token !is SkipToken) {
                        tokens.add(result.token)

                        break
                    }
                }
            }
            if (!matched) {
                val errorCharacter = source[index]
                throw IllegalArgumentException("Unexpected character '$errorCharacter' at line $line, column $column")
            }
        }
        return tokens
    }

    private fun advance(count: Int) {
        for (i in 0 until count) {
            if (index >= source.length) break
            val char = source[index]
            index++

            if (char == '\n') {
                line++
                column = 1
            } else column++
        }
    }
}