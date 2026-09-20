package io.cuttlefish.parsing.syntaxTree

import io.cuttlefish.linking.RelocationTable


class ParserContext {
    val symbolTable = mutableMapOf<String, Short>()
    val imports = mutableListOf<String>()
    val relocations = mutableListOf<RelocationTable>()
    var currentGlobalScope = ""

}