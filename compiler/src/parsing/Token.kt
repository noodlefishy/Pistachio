package io.cuttlefish.parsing

interface Token {
    val lexeme: String
    val line: Int
    val column: Int
}