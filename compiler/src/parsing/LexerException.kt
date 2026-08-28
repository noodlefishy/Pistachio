package io.cuttlefish.parsing

class LexerException(val line: Int, val column: Int, message: String) : Exception(message)