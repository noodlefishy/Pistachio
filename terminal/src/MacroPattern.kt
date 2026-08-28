package io.cuttlefish

interface MacroPattern {
    val size: Int
    fun match(
        window: List<Instruction>,
        address: UShort,
        symbolMap: Map<UShort, String>
    ): DisassembledInstruction?
}