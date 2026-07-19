package io.cuttlefish.parsing

import io.cuttlefish.RegisterType
import io.cuttlefish.MagicValues
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LexerTest {

    @Test
    fun testRegisterTokenising() {
        val input = "r1 R6 r0"
        val lexer = Lexer(input)
        val tokens = lexer.tokenise()

        assertEquals(3, tokens.size, "Expected exactly 3 register tokens.")

        assertTrue(tokens[0] is RegisterToken)
        assertEquals(RegisterType.R1, (tokens[0] as RegisterToken).registerName)

        assertTrue(tokens[1] is RegisterToken)
        assertEquals(RegisterType.R6, (tokens[1] as RegisterToken).registerName)

        assertTrue(tokens[2] is RegisterToken)
        assertEquals(RegisterType.R0, (tokens[2] as RegisterToken).registerName)
    }

    @Test
    fun testOpcodeAndDirectiveTokenising() {
        val input = "add .fill movi .space"
        val lexer = Lexer(input)
        val tokens = lexer.tokenise()

        assertEquals(4, tokens.size)

        assertTrue(tokens[0] is MnemonicToken)
        assertEquals("add", tokens[0].lexeme)

        assertTrue(tokens[1] is MnemonicToken)
        assertEquals(".fill", tokens[1].lexeme)

        assertTrue(tokens[2] is MnemonicToken)
        assertEquals("movi", tokens[2].lexeme)

        assertTrue(tokens[3] is MnemonicToken)
        assertEquals(".space", tokens[3].lexeme)
    }

    @Test
    fun testNumberAndMagicTokenising() {
        // Tests decimal, positive hex, negative hex, and $SBIT magic value
        val input = "10 0x1A -0x10 \$SBIT"
        val lexer = Lexer(input)
        val tokens = lexer.tokenise()

        assertEquals(4, tokens.size)

        // Decimal: 10
        assertTrue(tokens[0] is ImmediateToken)
        assertEquals(10.toShort(), (tokens[0] as ImmediateToken).value)

        // Hex: 0x1A (26)
        assertTrue(tokens[1] is ImmediateToken)
        assertEquals(26.toShort(), (tokens[1] as ImmediateToken).value)

        // Negative Hex: -0x10 (-16)
        assertTrue(tokens[2] is ImmediateToken)
        assertEquals((-16).toShort(), (tokens[2] as ImmediateToken).value)

        // Magic: $SBIT (-32768)
        assertTrue(tokens[3] is ImmediateToken)
        assertEquals(MagicValues.SBIT.value, (tokens[3] as ImmediateToken).value)
    }

    @Test
    fun testStringLiteralTokenising() {
        val input = "\"Hello World\\n\""
        val lexer = Lexer(input)
        val tokens = lexer.tokenise()

        assertEquals(1, tokens.size)
        assertTrue(tokens[0] is StringLiteralToken)

        // Ensure quotes are stripped out but text is preserved!
        assertEquals("Hello World\\n", (tokens[0] as StringLiteralToken).text)
    }

    @Test
    fun testSymbolReferenceTokenising() {
        val input = "my_var .local_symbol stack"
        val lexer = Lexer(input)
        val tokens = lexer.tokenise()

        assertEquals(3, tokens.size)

        assertTrue(tokens[0] is SymbolReferenceToken)
        assertEquals("my_var", (tokens[0] as SymbolReferenceToken).symbolName)

        assertTrue(tokens[1] is SymbolReferenceToken)
        assertEquals(".local_symbol", (tokens[1] as SymbolReferenceToken).symbolName)

        assertTrue(tokens[2] is SymbolReferenceToken)
        assertEquals("stack", (tokens[2] as SymbolReferenceToken).symbolName)
    }

    @Test
    fun testComplexAssemblyLineTokenisation() {
        // Test an actual line of assembly with brackets, commas, plus signs, and offsets
        val input = "sw r2, [r6 + 10] // Save r2 to stack with offset"
        val lexer = Lexer(input)
        val tokens = lexer.tokenise()

        // Expected tokens after skipping: "sw", "r2", "r6", "10"
        assertEquals(4, tokens.size, "Commas, brackets, plus signs, and comments should be skipped.")

        assertTrue(tokens[0] is MnemonicToken)
        assertEquals("sw", tokens[0].lexeme)

        assertTrue(tokens[1] is RegisterToken)
        assertEquals(RegisterType.R2, (tokens[1] as RegisterToken).registerName)

        assertTrue(tokens[2] is RegisterToken)
        assertEquals(RegisterType.R6, (tokens[2] as RegisterToken).registerName)

        assertTrue(tokens[3] is ImmediateToken)
        assertEquals(10.toShort(), (tokens[3] as ImmediateToken).value)
    }
}