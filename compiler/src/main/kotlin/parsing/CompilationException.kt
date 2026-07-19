package io.cuttlefish.parsing


class CompilationException(
    val fileName: String, val sourceLine: SourceLine, val errorMessage: String
) : Exception(errorMessage)