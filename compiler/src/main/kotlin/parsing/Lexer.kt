package io.cuttlefish.parsing

class Lexer(private val source: String) {
    private var index = 0
    private var line = 0
    private var column = 0

    fun tokenise(): List<Token> {
        val tokens = mutableListOf<Token>()
        val rules = SyntaxRegistry().rules.toList()

        while (index < source.length) {
            var matched = false

            for (rule in rules) {
                val result = rule.match(source, index, line, column)

                if (result != null) {
                    matched = true

                    if (result.token !is SkipToken) {
                        tokens.add(result.token)
                        // some work function
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
}