package io.cuttlefish.parsing

import io.cuttlefish.parsing.rules.*
import java.util.*

class SyntaxRegistry {
    val rules = mutableListOf<TokenRule>()

    init {
        // DO NOT ever change this order ✨
        rules += SkipTokenRule()
        rules += EndOfLineTokenRule()
        rules += StringLiteralTokenRule()
        rules += LabelTokenRule()
        rules += RegisterTokenRule()
        rules += ImmediateTokenRule()
        rules += MnemonicTokenRule()
        rules += SymbolReferenceTokenRule()

        val loader = ServiceLoader.load(TokenRule::class.java)
        for (otherRule in loader) {
            rules += otherRule
        }
    }


}