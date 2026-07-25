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


fun Debugger.formatInstruction(pc: UShort, inst: Instruction, delta: Debugger.RegisterDelta?): String {
    val opStr: String
    val argsStr: String
    var annotation = ""

    when (inst) {
        is Instruction.Add -> {
            opStr = "add"
            argsStr = "${inst.register1}, ${inst.register2}, ${inst.register3}"
        }

        is Instruction.Addi -> {
            opStr = "addi"
            argsStr = "${inst.register1}, ${inst.register2}, #${inst.immediate}"
        }

        is Instruction.Nand -> {
            opStr = "nand"
            argsStr = "${inst.register1}, ${inst.register2}, ${inst.register3}"
        }

        is Instruction.Lui -> {
            opStr = "lui"
            argsStr = "${inst.register1}, #${inst.immediate}"
        }

        is Instruction.Lw -> {
            opStr = "lw"
            argsStr = "${inst.register1}, [${inst.register2} + ${inst.immediate}]"
        }

        is Instruction.Sw -> {
            opStr = "sw"
            argsStr = "${inst.register1}, [${inst.register2} + ${inst.immediate}]"
        }

        is Instruction.Beq -> {
            opStr = "beq"
            argsStr = "${inst.register1}, ${inst.register2}, #${inst.immediate}"
            // Calculate absolute target address for PC-relative branches!!
            val target = (pc.toInt() + 1 + inst.immediate.toInt()) and 0xFFFF
            val hexTarget = target.toString(16).uppercase().padStart(4, '0')
            annotation = "// branch to ${addressToLabelMap.toMap().getOrDefault(target.toUShort(), null) ?: "0x$hexTarget"}"
        }

        is Instruction.Jalr -> {
            opStr = "jalr"
            argsStr = "${inst.register1}, ${inst.register2}, #${inst.immediate}"
            if (inst.immediate != 0.toShort()) {
                val trapName = when (inst.immediate.toInt()) {
                    1 -> "halt"
                    15 -> "rti"
                    else -> "syscall ${inst.immediate}"
                }
                annotation = "; trap: $trapName"
            }
        }

        is Instruction.DataWord -> {
            opStr = ".fill"
            argsStr = "#${inst.value}"
        }
    }

    val paddedOp = opStr.padEnd(8)
    val paddedArgs = argsStr.padEnd(25)
    val comment = if (annotation.isNotEmpty()) "  $annotation" else ""
    val instructionColumn = "$paddedOp $paddedArgs$comment".padEnd(55)
    val deltaColumn = if (delta != null) {
        val oldHex = (delta.oldValue.toInt() and 0xFFFF).toString(16).padStart(4, '0').uppercase()
        val newHex = (delta.newValue.toInt() and 0xFFFF).toString(16).padStart(4, '0').uppercase()

        val oldDec = delta.oldValue.toString().padEnd(6)
        val newDec = delta.newValue.toString().padEnd(6)

        val regName = delta.registerType.name.padEnd(3)

        "$regName (0x$oldHex / #$oldDec) <- 0x$newHex / #$newDec"
    } else {
        "No register change"
    }

    return "${decipherLabel(pc)} | $instructionColumn | $deltaColumn"
}


//fun getLabelOrHex(address: UShort): String {
//    val label = mapFile[address] ?: address.toString(16).uppercase().padStart(4, '0')
//    return label.padEnd(maxLabelLength)
//}