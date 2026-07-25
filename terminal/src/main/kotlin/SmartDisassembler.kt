package io.cuttlefish



data class DisassembledInstruction(
    val text: String,
    val wordCount: UByte,
    val rawWords: List<UShort>,
)

object SmartDisassembler{

    suspend fun disassembleAt(
        memory: MemoryManagement,
        address: UShort,
        symbolMap: Map<UShort, String>
    ) {}
}