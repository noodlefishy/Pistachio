package io.cuttlefish.parsing

import io.cuttlefish.parsing.rules.*
import java.util.*

class SyntaxRegistry {
    val rules = mutableListOf<TokenRule>()

    init {
        rules += SkipTokenRule()
        rules += LabelTokenRule()

        val loader = ServiceLoader.load(TokenRule::class.java)
        for (otherRule in loader) {
            rules += otherRule
        }
    }


}