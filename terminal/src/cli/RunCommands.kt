package io.cuttlefish.cli

import io.cuttlefish.MemoryMapRanges
import io.cuttlefish.components.Cpu
import io.cuttlefish.components.MemoryBus
import io.cuttlefish.components.PhysicalMemory
import io.cuttlefish.debug.Debugger
import io.cuttlefish.debugging.generateDebugFiles
import io.cuttlefish.debugging.mapFile
import io.cuttlefish.debugging.runCpuSafely
import io.cuttlefish.profiling.ExecutionProfiler
import java.io.File

suspend fun handleCompileAndRun(args: List<String>) {
	val flags = CliFlags.parse(args)
	if (flags.positionalArgs.isEmpty()) throw IllegalArgumentException("Missing input files for -i")

	val baseAddr = MemoryMapRanges.userLandRange.first // 0x3000
	val linked = linkUserSources(flags.positionalArgs, baseAddr.toUShort())

	if (flags.outPath != null || flags.isDebug) {
		val binPath = if (flags.isDebug || flags.isDebugF) "debug/${flags.resolvedOutPath}.bin" else "${flags.resolvedOutPath}.bin"
		File(binPath).apply { parentFile?.mkdirs() }
			.writeText("@$baseAddr\n" + linked.words.joinToString("\n"))
	}

	if (flags.isDebug || flags.isDebugF) {
		generateDebugFiles(flags.baseName, baseAddr.toUShort(), linked.words, linked.symbolMap, null, null)
	}

	val memory = MemoryBus(PhysicalMemory())
	for ((index, word) in linked.words.withIndex()) {
		memory.ram.internals[(baseAddr + index.toUInt()).toInt()] = word.toShort()
	}

	val cpu = Cpu(memory).apply {
		pbr = baseAddr.toUShort()
		pc = 0u
	}
	val debugger = Debugger(cpu, memory, linked.symbolMap)
	val profiler = if (flags.isProfile) ExecutionProfiler(cpu, linked.symbolMap) else null

	runCpuSafely(cpu, memory, debugger, flags.isDebug, flags.shouldDump, baseAddr.toUShort(), linked.words.size, profiler) {
		if (flags.isDebug || flags.isDebugF) {
			generateDebugFiles(flags.baseName, baseAddr.toUShort(), linked.words, null, cpu, memory, debugger)
		}
	}
}

suspend fun handleRun(args: List<String>) {
	val flags = CliFlags.parse(args)
	if (flags.positionalArgs.isEmpty()) throw IllegalArgumentException("Missing input file for -r")

	val file = getFileOrThrow(flags.positionalArgs[0])
	val baseName = file.nameWithoutExtension
	val binary = readBinaryImage(file)

	if (flags.isDebug || flags.isDebugF) {
		generateDebugFiles(baseName, binary.baseAddress, binary.words, null, null, null)
	}

	val memory = MemoryBus(PhysicalMemory())
	for ((index, word) in binary.words.withIndex()) {
		memory.ram.internals[(binary.baseAddress + index.toUInt()).toInt()] = word.toShort()
	}

	val cpu = Cpu(memory).apply {
		pbr = binary.baseAddress
		pc = 0u
	}
	val symbols = mapFile.map { it.value to it.key }.toMap()
	val debugger = Debugger(cpu, memory, symbols)
	val profiler = if (flags.isProfile) ExecutionProfiler(cpu, symbols) else null

	runCpuSafely(cpu, memory, debugger, flags.isDebug, flags.shouldDump, binary.baseAddress, binary.words.size, profiler) {
		if (flags.isDebug || flags.isDebugF) {
			generateDebugFiles(baseName, binary.baseAddress, binary.words, null, cpu, memory, debugger)
		}
	}
}

suspend fun handleRunOs(args: List<String>) {
	var flags = CliFlags.parse(args)
	if (flags.positionalArgs.size < 2) {
		throw IllegalArgumentException(
			"Missing kernel or main file.\nUsage: lx -os <kernel.lx|bin> <main.lx|bin> [user_libs...] [options]"
		)
	}

	val kernelFile = getFileOrThrow(flags.positionalArgs[0])
	val userArgs = flags.positionalArgs.drop(1)

	val kernel = if (kernelFile.name.endsWith(".bin")) {
		LinkedProgram(readBinaryImage(kernelFile).words, emptyMap())
	} else {
		assembleAndLinkKernel(kernelFile)
	}

	val user = if (userArgs.size == 1 && userArgs[0].endsWith(".bin")) {
		LinkedProgram(readBinaryImage(getFileOrThrow(userArgs[0])).words, emptyMap())
	} else {
		linkUserSources(userArgs, baseAddress = 0u)
	}

	val memory = MemoryBus(PhysicalMemory(65536))
	for ((index, word) in kernel.words.withIndex()) {
		memory.ram.internals[index] = word.toShort()
	}

	val userBase = MemoryMapRanges.userLandRange.first.toInt() // 0x3000
	for ((index, word) in user.words.withIndex()) {
		memory.ram.internals[userBase + index] = word.toShort()
	}

	val cpu = Cpu(memory).apply {
		isKernelMode = true
		pc = 0u
		pbr = userBase.toUShort()
		plr = (MemoryMapRanges.userLandRange.last - MemoryMapRanges.userLandRange.first + 1u).toUShort()
	}
	cpu.flags = 0u

	val combinedSymbols = mutableMapOf<String, UShort>()
	kernel.symbolMap.forEach { (k, v) ->
		combinedSymbols["k.$k"] = v
		combinedSymbols[k] = v
	}
	user.symbolMap.forEach { (k, v) ->
		if (!combinedSymbols.containsKey(k)) combinedSymbols[k] = v else combinedSymbols["u.$k"] = v
	}

	val debugger = Debugger(cpu, memory, combinedSymbols)
	val profiler = if (flags.isProfile) ExecutionProfiler(cpu, combinedSymbols) else null

	runCpuSafely(
		cpu = cpu,
		memory = memory,
		debugger = debugger,
		isInteractive = flags.isDebug,
		shouldDump = flags.shouldDump,
		dumpBaseAddr = 0u,
		dumpLength = kernel.words.size + user.words.size + userBase,
		profiler = profiler
	)
}