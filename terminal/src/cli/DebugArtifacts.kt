package io.cuttlefish.debugging

import io.cuttlefish.RegisterType
import io.cuttlefish.backend.smartFeatures.SmartDisassembler
import io.cuttlefish.components.Cpu
import io.cuttlefish.components.MemoryBus
import io.cuttlefish.components.PhysicalMemory
import io.cuttlefish.config.GlobalConfig
import io.cuttlefish.debug.Debugger
import kotlinx.serialization.json.Json
import java.io.File

suspend fun generateDebugFiles(
	baseName: String,
	baseAddr: UShort,
	machineCode: List<UShort>,
	map: Map<String, UShort>?,
	cpu: Cpu?,
	memory: MemoryBus?,
	debugger: Debugger? = null
) {
	File("debug").mkdirs()

	if (map != null) {
		val json = Json { prettyPrint = true }
		File("debug/$baseName.map").writeText(
			json.encodeToString(map.mapValues { it.value.toString(16).uppercase().padStart(4, '0') })
		)
	}

	if (map != null || cpu == null) {
		val memToUse = memory ?: MemoryBus(PhysicalMemory()).apply {
			machineCode.forEachIndexed { i, w -> ram.internals[(baseAddr + i.toUInt()).toInt()] = w.toShort() }
		}

		val disasmText = StringBuilder()
		var currentAddr = baseAddr
		val endAddr = (baseAddr + machineCode.size.toUInt()).toUShort()
		val reverseMap = map?.map { it.value to it.key }?.toMap() ?: emptyMap()

		while (currentAddr < endAddr) {
			val dis = SmartDisassembler.disassembleAt(memToUse, currentAddr, reverseMap)
			val hexAddr = currentAddr.toString(16).uppercase().padStart(4, '0')
			val label = reverseMap[currentAddr]?.let { "$it:" }?.padEnd(12) ?: "".padEnd(12)

			disasmText.append("0x$hexAddr | $label ${dis.text}\n")
			currentAddr = (currentAddr + dis.wordCount.toUInt()).toUShort()
		}
		File("debug/$baseName.disasm").writeText(disasmText.toString())
	}

	if (memory != null) {
		val hexString = printHexDump(memory, baseAddr, machineCode.size, returnData = true, printToConsole = false)!!
		File("debug/$baseName.hex").writeText(hexString)
	}

	if (cpu != null) {
		val regsText = StringBuilder()
		val pcHex = "0x" + (cpu.pc.toInt() and 0xFFFF).toString(16).uppercase().padStart(4, '0')
		regsText.append("--- Final CPU State ---\n")
		regsText.append("PC  : ${cpu.pc} ($pcHex)\n")
		regsText.append("Halted: ${cpu.isHalted}\n\n")

		regsText.append("--- Registers ---\n")
		cpu.registers.registerData.forEachIndexed { index, value ->
			val regName = RegisterType.entries[index].name
			val hexVal = "0x" + (value.toInt() and 0xFFFF).toString(16).uppercase().padStart(4, '0')
			regsText.append("$regName : $value ($hexVal)\n")
		}
		File("debug/$baseName.regs").writeText(regsText.toString())
	}

	if (debugger != null && debugger.historyX.isNotEmpty()) {
		val historyText = StringBuilder()
		historyText.append("--- Execution History Trace (${debugger.historyX.size} steps) ---\n")
		debugger.historyX.reversed().forEach { historyText.append(it).append("\n") }
		File("debug/$baseName.history").writeText(historyText.toString())
	}
}

suspend fun printHexDump(
	memory: MemoryBus,
	startAddress: UShort,
	length: Int,
	returnData: Boolean = false,
	printToConsole: Boolean = true
): String? {
	val start = startAddress.toInt() and 0xFFFF
	val end = (start + length) and 0xFFFF
	val output = StringBuilder()
	val use16 = GlobalConfig.debug.printHex16Dump
	val word16 = GlobalConfig.debug.use16wordAddressInDump

	val wordsPerRow = if (use16) 16 else 8
	val asciiWidth = if (word16) wordsPerRow else wordsPerRow * 2
	val prefix = "ADDR: "
	val separator = "| "
	val totalLineWidth = prefix.length + (wordsPerRow * 5) + separator.length + asciiWidth

	val title = " POST HEX DUMP 0x00FU "
	val dashCount = (totalLineWidth - title.length).coerceAtLeast(0)
	val leftDashes = "-".repeat(dashCount / 2)
	val rightDashes = "-".repeat(dashCount - (dashCount / 2))
	val borderLine = "-".repeat(totalLineWidth)

	val colsStr = (0 until wordsPerRow).joinToString("") { it.toString(16).uppercase().padEnd(5) }
	val header = "$borderLine\n$leftDashes$title$rightDashes\n$prefix$colsStr| ASCII\n$borderLine\n"
	output.append(header)

	if (printToConsole) System.err.print(header)

	val alignedStart = start - (start % 8)

	for (addr in alignedStart..end step wordsPerRow) {
		val hexAddr = addr.toString(16).uppercase().padStart(4, '0')
		val wordsHex = StringBuilder()
		val asciiChars = StringBuilder()

		for (i in 0 until wordsPerRow) {
			val currentAddr = (addr + i).toShort()
			val word = try {
				memory.read(currentAddr.toUShort()).toInt() and 0xFFFF
			} catch (_: Exception) {
				0x0000
			}

			wordsHex.append(word.toString(16).uppercase().padStart(4, '0')).append(" ")

			if (word16) {
				asciiChars.append(if (word in 32..126) word.toChar() else '.')
			} else {
				val highByte = (word ushr 8) and 0xFF
				val lowByte = word and 0xFF
				asciiChars.append(if (highByte in 32..126) highByte.toChar() else '.')
				asciiChars.append(if (lowByte in 32..126) lowByte.toChar() else '.')
			}
		}

		val lineOutput = "$hexAddr: $wordsHex| $asciiChars"
		output.append(lineOutput).append('\n')
		if (printToConsole) System.err.println(lineOutput)
	}

	if (printToConsole) System.err.println(borderLine)

	return if (returnData) output.toString() else null
}