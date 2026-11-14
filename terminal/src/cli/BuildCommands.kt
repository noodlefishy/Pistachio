package io.cuttlefish.cli

import io.cuttlefish.MemoryMapRanges
import io.cuttlefish.backend.Backend
import io.cuttlefish.debugging.generateDebugFiles
import io.cuttlefish.parsing.Parser
import java.io.File

fun handleCompile(args: List<String>) {
	val flags = CliFlags.parse(args)
	if (flags.positionalArgs.isEmpty()) throw IllegalArgumentException("Missing input file for -c")

	val file = getFileOrThrow(flags.positionalArgs[0])
	val outPath = flags.outPath ?: "${file.nameWithoutExtension}.bin"

	val parse = Parser(file, 0).decode()
	val machineCode = Backend.encode(parse)

	File(outPath).writeText(machineCode.joinToString("\n"))
}

suspend fun handleBuild(args: List<String>) {
	val flags = CliFlags.parse(args)
	if (flags.positionalArgs.isEmpty()) throw IllegalArgumentException("Missing input files for -b")

	val baseAddr = MemoryMapRanges.userLandRange.first // 0x3000
	val linked = linkUserSources(flags.positionalArgs, baseAddr.toUShort())

	File(flags.resolvedOutPath).writeText("@$baseAddr\n" + linked.words.joinToString("\n"))

	if (flags.isDebug || flags.isDebugF) {
		generateDebugFiles(flags.baseName, baseAddr.toUShort(), linked.words, linked.symbolMap, null, null)
	}
}