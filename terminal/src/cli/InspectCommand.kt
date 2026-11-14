package io.cuttlefish.cli

import io.cuttlefish.backend.smartFeatures.SmartDisassembler
import io.cuttlefish.components.MemoryBus
import io.cuttlefish.components.PhysicalMemory
import io.cuttlefish.debugging.printHexDump
import io.cuttlefish.parsing.Parser
import kotlinx.coroutines.runBlocking
import java.io.File

fun handleTokenize(args: List<String>) {
	if (args.isEmpty()) throw IllegalArgumentException("Missing input file for -t")
	val file = getFileOrThrow(args[0])

	val parse = Parser(file, 0).decode()
	parse.forEachIndexed { index, instruction -> println("$index | $instruction") }
}

fun handleDecode(args: List<String>) {
	if (args.isEmpty()) throw IllegalArgumentException("Missing input file for -d")
	val file = getFileOrThrow(args[0])
	val binary = readBinaryImage(file)

	val memory = MemoryBus(PhysicalMemory())
	for ((index, word) in binary.words.withIndex()) {
		memory.ram.internals[(binary.baseAddress + index.toUInt()).toInt()] = word.toShort()
	}

	println("--- Disassembly of ${file.name} ---")
	var currentAddr = binary.baseAddress
	val endAddr = (binary.baseAddress + binary.words.size.toUInt()).toUShort()

	runBlocking {
		while (currentAddr < endAddr) {
			val dis = SmartDisassembler.disassembleAt(memory, currentAddr)
			val hexAddr = "0x" + currentAddr.toString(16).uppercase().padStart(4, '0')
			println("$hexAddr | ${dis.text}")
			currentAddr = (currentAddr + dis.wordCount.toUInt()).toUShort()
		}
	}
}

suspend fun handleHexDumpFile(args: List<String>) {
	val flags = CliFlags.parse(args)
	if (flags.positionalArgs.isEmpty()) throw IllegalArgumentException("Missing input file for -x")

	val file = getFileOrThrow(flags.positionalArgs[0])
	val binary = readBinaryImage(file)

	val memory = MemoryBus(PhysicalMemory())
	for ((index, word) in binary.words.withIndex()) {
		memory.ram.internals[(binary.baseAddress + index.toUInt()).toInt()] = word.toShort()
	}

	val dump = printHexDump(
		memory = memory,
		startAddress = binary.baseAddress,
		length = binary.words.size,
		returnData = true,
		printToConsole = flags.outPath == null
	)!!

	if (flags.outPath != null) {
		File(flags.outPath).writeText(dump)
		println("Hex dump saved to ${flags.outPath}")
	}
}