package io.cuttlefish.parsing

import io.cuttlefish.*

data class MnemonicToken(
    override val lexeme: String, override val line: Int, override val column: Int
) : Token

data class RegisterToken(
    val registerName: RegisterType, override val lexeme: String, override val line: Int, override val column: Int
) : Token

data class ImmediateToken(
    val value: Short, override val lexeme: String, override val line: Int, override val column: Int
) : Token

data class LabelDefToken(
    val labelName: String, override val lexeme: String, override val line: Int, override val column: Int,
) : Token

data class SymbolReferenceToken(
    val symbolName: String, override val lexeme: String, override val line: Int, override val column: Int
) : Token

data class StringLiteralToken(
    val text: String, override val lexeme: String, override val line: Int, override val column: Int
) : Token


// Special token to indicate comments or whitespace to be skipped
object SkipToken : Token {
    override val lexeme: String = ""
    override val line: Int = 0
    override val column: Int = 0
}