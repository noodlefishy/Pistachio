package io.cuttlefish.backend.smartFeatures

import io.cuttlefish.Instruction

interface MacroPattern {
    val size: Int
    fun match(
        window: List<Instruction>,
        address: UShort,
        symbolMap: Map<UShort, String>
    ): DisassembledInstruction?
}