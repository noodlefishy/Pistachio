package io.cuttlefish


data class DisassembledInstruction(
    val text: String,
    val wordCount: UByte,
    val rawWords: List<UShort>,
)


object SmartDisassembler {

    suspend fun disassembleAt(
        memory: MemoryManagement, address: UShort, symbolMap: Map<UShort, String> = emptyMap()
    ): DisassembledInstruction {

        val rawWords = listOf(
            try {
                memory.read(address).toUShort()
            } catch (_: Exception) {
                0u
            }, try {
                memory.read((address + 1u).toUShort()).toUShort()
            } catch (_: Exception) {
                0u
            }, try {
                memory.read((address + 2u).toUShort()).toUShort()
            } catch (_: Exception) {
                0u
            }
        )
        val decodedWindow = rawWords.map { io.cuttlefish.backend.Backend.decode(it) }

        for (pattern in SmartDisassemblerRegistry.patterns) {
            val match = pattern.match(decodedWindow, address, symbolMap)
            if (match != null) {
                return match.copy(rawWords = rawWords.take(match.wordCount.toInt()))
            }
        }

        return DisassembledInstruction(decodedWindow[0].toString(), 1u, listOf(rawWords[0]))
    }
}
