package parsing

import io.cuttlefish.parsing.Lexer
import org.junit.jupiter.api.Assertions.*
import kotlin.test.Test

class LexerTest {

    @Test
    fun testOnlyWhitespaceAndCommentsReturnEmpty() {
        val text = """
            // Magical single line comment
            
            addi r1 r2 #10 // That is some normal code
            
            /* Insane multiline comment!!
                look! Im on many lines!!!
            */
        """.trimIndent()

        val lexer = Lexer(text)
        val tokens = lexer.tokenise()

        println(tokens)


    }
}