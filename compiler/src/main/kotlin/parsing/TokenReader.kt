package io.cuttlefish.parsing

import io.cuttlefish.RegisterType
import io.cuttlefish.parsing.syntaxTree.Argument
import io.cuttlefish.parsing.syntaxTree.ImmArg
import io.cuttlefish.parsing.syntaxTree.SymArg


class TokenReader(val tokens: List<Token>, var index: Int = 0) {

    fun nextArray(): ArrayLiteralToken {
        val t = tokens.getOrNull(index++)
        if (t is ArrayLiteralToken) return t
        throw SyntaxException("Expected Array Literal { ... }")
    }

    fun hasNext(): Boolean = index < tokens.size

    fun peek(): Token? = tokens.getOrNull(index)

    fun nextReg(): RegisterType {
        val t = tokens.getOrNull(index++)
        if (t is RegisterToken) return t.registerName
        throw SyntaxException("Expected Register (e.g., r1-r7)")
    }

    fun nextArg(): Argument {
        val t = tokens.getOrNull(index++)
        if (t is ImmediateToken) return ImmArg(t.value)
        if (t is SymbolReferenceToken) return SymArg(t.symbolName, t.line, t.column)
        throw SyntaxException("Expected Number or Label")
    }

    fun nextString(): String {
        val t = tokens.getOrNull(index++)
        if (t is StringLiteralToken) return t.text
        throw SyntaxException("Expected String Literal")
    }
}