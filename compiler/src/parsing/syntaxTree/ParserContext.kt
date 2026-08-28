package io.cuttlefish.parsing.syntaxTree

import io.cuttlefish.linking.RelocationTable


class ParserContext(val baseAddress: Short) {
    val symbolTable = mutableMapOf<String, Short>()
    val imports = mutableListOf<String>()
    val relocations = mutableListOf<RelocationTable>()
    var currentGlobalScope = ""

    fun resolveScopedName(name: String): String {
        return if (name.startsWith(".")) currentGlobalScope + name else name
    }
}