package io.cuttlefish.parsing.syntaxTree

sealed class Argument
data class ImmArg(val value: Short) : Argument()
data class SymArg(val name: String, val line: Int, val col: Int) : Argument()