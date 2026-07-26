package io.cuttlefish.debugging


import io.cuttlefish.*
import io.cuttlefish.config.*
import io.cuttlefish.debug.*
import kotlinx.serialization.json.*
import java.io.*


val mapFile = if (GlobalConfig.debug.useMap) {
    try {
        Json.decodeFromString<Map<String, UShort>>(File(GlobalConfig.debug.mapFile).readText())
            .map { it.value to it.key }.toMap()
    } catch (e: Exception) {
        println("[WARNING] Failed to load symbol map: ${e.message}")
        emptyMap()
    }
} else emptyMap()

private val maxLabelLength = (mapFile.values.maxOfOrNull { it.length } ?: 4).coerceAtLeast(4)


suspend fun Debugger.formatInstruction(pc: UShort, delta: Debugger.RegisterDelta?): String {
    val rawLabel = addressToLabelMap[pc] ?: ("0x" + pc.toString(16).uppercase().padStart(4, '0'))
    val col1 = rawLabel.padEnd(16)

    val dis = SmartDisassembler.disassembleAt(memory, pc, addressToLabelMap)
    val col2 = dis.text.padEnd(35)

    val col3 = if (delta != null) {
        val oldHex = (delta.oldValue.toInt() and 0xFFFF).toString(16).padStart(4, '0').uppercase()
        val newHex = (delta.newValue.toInt() and 0xFFFF).toString(16).padStart(4, '0').uppercase()

        val oldDec = ("#" + delta.oldValue).padEnd(7)
        val newDec = ("#" + delta.newValue).padEnd(7)

        val regName = delta.registerType.name.padEnd(3)

        "$regName (0x$oldHex / $oldDec) <- 0x$newHex / $newDec"
    } else {
        "No register change"
    }

    return "$col1 | $col2 | $col3"
}


//fun getLabelOrHex(address: UShort): String {
//    val label = mapFile[address] ?: address.toString(16).uppercase().padStart(4, '0')
//    return label.padEnd(maxLabelLength)
//}