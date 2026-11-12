@file:Suppress("UnusedImport")

package io.cuttlefish

import io.cuttlefish.backend.*
import io.cuttlefish.backend.smartFeatures.SmartDisassembler
import io.cuttlefish.components.*
import io.cuttlefish.config.*
import io.cuttlefish.debug.*
import io.cuttlefish.debugging.*
import io.cuttlefish.linking.*
import io.cuttlefish.parsing.*
import io.cuttlefish.profiling.ExecutionProfiler
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import sun.misc.*
import java.io.*
import kotlin.system.*

fun printUsage() {
    println(
        """
        Usage: lx <command> [options]
        
        Commands:
          -c     <file.lx> [-o <out.bin>]      Compile a single source file to machine code.
          -b     <f1.lx> <f2.lx> [-o <out>]    Compile & link multiple source files into a binary.
          -i     <f1.lx> [f2.lx] [...]         Compile, link, and immediately run source files.
          -r     <file.bin>                    Run a pre-compiled machine code file.
          -os    <kernel.lx> <main.lx>         Compile and run an OS kernel with a userland program.
          -t     <file.lx>                     Tokenize and parse a file (prints instructions).
          -d     <file.lx>                     Parses and decodes a file.bin (prints instructions).
          -x     <file.bin> [length]           Produces a static hex dump of compiled binary. 
          
        Options:
          -h, --help                           Show this help menu.
          --dump                               Prints the hex dump to the console on completion.
          --debug                              Generates full debug files based on output name:
                                               (.disasm, .hex, .map, .regs). Works on crashes too!
          
        Examples:
          lx -i main.lx "program files/lib" -o program.bin --debug
          lx -b main.lx math.lx -o program.bin --debug
        """.trimIndent()
    )
}

suspend fun main(args: Array<String>) {
    if (args.isEmpty() || args[0] in listOf("-h", "--help", "help")) {
        printUsage()
        exitProcess(0)
    }
    loadConfig()
    val command = args[0]
    val remainingArgs = args.drop(1)

    try {
        when (command) {
            "-t" -> handleTokenize(remainingArgs)
            "-c" -> handleCompile(remainingArgs)
            "-b" -> handleBuild(remainingArgs)
            "-i" -> handleCompileAndRun(remainingArgs)
            "-r" -> handleRun(remainingArgs)
            "-d" -> handleDecode(remainingArgs)
            "-os" -> handleRunOs(remainingArgs)
            "-x" -> handleHexDumpFile(remainingArgs)
            else -> {
                System.err.println("[ERROR] Unknown command or flag: $command")
                printUsage()
                exitProcess(1)
            }
        }
    } catch (e: CompilationException) {
        System.err.println("\u001B[31m[COMPILER ERROR]\u001B[0m in file '${e.fileName}' on line ${e.sourceLine.lineNumber}:")
        System.err.println("  ${e.sourceLine.lineNumber.toString().padStart(4, ' ')} | ${e.sourceLine.rawText.trim()}")
        System.err.println("  Error: ${e.errorMessage}!!")
        System.err.println()
        exitProcess(1)
    } catch (e: LinkerException) {
        System.err.println("\u001B[31m[LINKER ERROR]\u001B[0m: ${e.errorMessage} '${e.symbolName}'")
        System.err.println("§Referenced in file '${File(e.fileAbsolutePath).name}':")

        val sourceFile = File(e.fileAbsolutePath)
        if (sourceFile.exists()) {
            val lines = sourceFile.readLines()
            val lineIndex = lines.indexOfFirst { it.contains(e.symbolName) }
            if (lineIndex != -1) {
                val lineNumber = lineIndex + 1
                System.err.println("    ${lineNumber.toString().padStart(4, ' ')} | ${lines[lineIndex].trim()}")
            }
        }
        System.err.println("  Error: No linked files or libraries export the symbol '${e.symbolName}'")
        exitProcess(1)
    }
}


private fun getFileOrThrow(path: String): File {
    val file = File(path)
    if (!file.exists()) throw FileNotFoundException("File not found: $path")
    return file
}

private fun expandPaths(inputPaths: List<String>): List<String> {
    val expanded = mutableListOf<String>()
    for (path in inputPaths) {
        val file = File(path)
        if (file.isDirectory) {
            file.list()?.forEach { j ->
                expanded.add("${file.absolutePath}/$j")
            }
        } else {
            expanded.add(path)
        }
    }
    if (expanded.isEmpty()) throw IllegalArgumentException("No input files resolved")
    return expanded
}


// ---------------------------------------------------------
// CLI Handlers
// ---------------------------------------------------------

private fun handleTokenize(args: List<String>) {
    if (args.isEmpty()) throw IllegalArgumentException("Missing input file for -t")
    val file = getFileOrThrow(args[0])

    val parse = Parser(file, 0).decode()
    parse.forEachIndexed { index, instruction -> println("$index | $instruction") }
}

private fun handleCompile(args: List<String>) {
    if (args.isEmpty()) throw IllegalArgumentException("Missing input file for -c")
    val file = getFileOrThrow(args[0])

    var outPath = "${file.nameWithoutExtension}.bin"
    if (args.size >= 3 && args[1] == "-o") {
        outPath = args[2]
    }

    val parse = Parser(file, 0).decode()
    val machineCode = Backend.encode(parse)

    val outFile = File(outPath)
    outFile.writeText(machineCode.joinToString("\n"))
}

private suspend fun handleBuild(args: List<String>) {
    if (args.isEmpty()) throw IllegalArgumentException("Missing input files for -b")

    val isDebug = args.contains("--debug")
    val outIndex = args.indexOf("-o")
    val outPath = if (outIndex != -1 && outIndex + 1 < args.size) args[outIndex + 1] else "out.bin"
    val baseName = outPath.substringBeforeLast(".")

    val cleanArgs = args.filterIndexed { index, arg ->
        arg != "--debug" && !(outIndex != -1 && (index == outIndex || index == outIndex + 1))
    }

    if (cleanArgs.isEmpty()) throw IllegalArgumentException("No input files provided")

    val expandedPaths = expandPaths(cleanArgs)
    val objects = expandedPaths.map { path -> ObjectExcreter(getFileOrThrow(path)).generate() }

    val baseAddr = MemoryMapRanges.userLandRange.first // 0x3000

    val linker = Linker(*objects.toTypedArray(), baseAddressUnsedRightNow = baseAddr.toUShort())
    val p1 = linker.passOne()
    val finalBinary = linker.passTwo(p1)

    File(outPath).writeText("@$baseAddr\n" + finalBinary.joinToString("\n"))

    if (isDebug) {
        generateDebugFiles(baseName, baseAddr.toUShort(), finalBinary.toList(), p1, null, null)
    }
}

private suspend fun handleCompileAndRun(args: List<String>) {
    if (args.isEmpty()) throw IllegalArgumentException("Missing input files for -i")
    val shouldDump = args.contains("--dump")
    val isDebug = args.contains("--debug")
    val isDebugF = args.contains("--debugf")
    val isProfile = args.contains("--profile")


    val outIndex = args.indexOf("-o")
    val outPath = if (outIndex != -1 && outIndex + 1 < args.size) args[outIndex + 1] else "out.bin"
    val baseName = outPath.substringBeforeLast(".")

    // Safe argument extraction
    val cleanArgs = args.filterIndexed { index, arg ->
        arg != "--dump" && arg != "--debug" && arg != "--debugf" && arg != "--profile" && !(outIndex != -1 && (index == outIndex || index == outIndex + 1))
    }

    val expandedPaths = expandPaths(cleanArgs)
    val objects = expandedPaths.map { path -> ObjectExcreter(getFileOrThrow(path)).generate() }

    val baseAddr = MemoryMapRanges.userLandRange.first // 0x3000

    val linker = Linker(*objects.toTypedArray(), baseAddressUnsedRightNow = baseAddr.toUShort())
    val p1 = linker.passOne()
    val machineCode = linker.passTwo(p1)

    // Save binary if -o is specified, OR if we are generating debug files
    if (outIndex != -1 || isDebug) {
        val inPath = if (isDebug || isDebugF) "debug/$outPath.bin" else "$outPath.bin"
        File(inPath).writeText("@$baseAddr\n" + machineCode.joinToString("\n"))
    }

    if (isDebug || isDebugF) {
        generateDebugFiles(baseName, baseAddr.toUShort(), machineCode.toList(), p1, null, null)
    }

    val memory = MemoryBus(PhysicalMemory())
    for ((index, word) in machineCode.withIndex()) {
        memory.ram.internals[(baseAddr + index.toUInt()).toInt()] = word.toShort()
    }

    val cpu = Cpu(memory)
    cpu.pbr = baseAddr.toUShort()
    cpu.pc = 0u
    val debugger = Debugger(cpu, memory, p1)


    val profiler = if (isProfile) ExecutionProfiler(cpu, p1) else null


    runCpuSafely(cpu, memory, debugger, isDebug, shouldDump, baseAddr.toUShort(), machineCode.size, profiler) {
        if (isDebug || isDebugF) generateDebugFiles(
            baseName, baseAddr.toUShort(), machineCode.toList(), null, cpu, memory, debugger
        )
    }
}

private suspend fun handleRun(args: List<String>) {
    if (args.isEmpty()) throw IllegalArgumentException("Missing input file for -r")
    val shouldDump = args.contains("--dump")
    val isDebug = args.contains("--debug")

    val cleanArgs = args.filter { it != "--dump" && it != "--debug" }

    val file = getFileOrThrow(cleanArgs[0])
    val baseName = file.nameWithoutExtension

    val lines = file.readLines().filter { it.isNotBlank() }
    val baseAddress = if (lines[0].startsWith("@")) lines[0].drop(1).toShort() else 0.toShort()
    val machineCode = (if (lines[0].startsWith("@")) lines.drop(1) else lines).map { it.trim().toUShort() }

    if (isDebug) {
        generateDebugFiles(baseName, baseAddress.toUShort(), machineCode, null, null, null)
    }

    val memory = MemoryBus(PhysicalMemory())
    for ((index, word) in machineCode.withIndex()) {
        memory.write((baseAddress + index).toUShort(), word.toShort())
    }

    val cpu = Cpu(memory)
    cpu.pc = baseAddress.toUShort()

    val debugger = Debugger(cpu, memory, mapFile.map { it.value to it.key }.toMap())

    runCpuSafely(cpu, memory, debugger, isDebug, shouldDump, baseAddress.toUShort(), machineCode.size) {
        if (isDebug) generateDebugFiles(
            baseName, baseAddress.toUShort(), machineCode.toList(), null, cpu, memory, debugger
        )
    }
}

private suspend fun handleRunOs(args: List<String>) {
    if (args.size < 2) {
        throw IllegalArgumentException(
            "Missing kernel or main file.\nUsage: lx -os <kernel.lx|bin> <main.lx|bin> [user_libs...] [options]"
        )
    }

    val shouldDump = args.contains("--dump")
    val isDebug = args.contains("--debug")
//    val isDebugF = args.contains("--debugf")
    val isProfile = args.contains("--profile")

    val cleanArgs = args.filter {
        it != "--dump" && it != "--debug" && it != "--debugf" && it != "--profile"
    }

    val kernelPath = cleanArgs[0]
    val userArgs = cleanArgs.drop(1)

    val kernelFile = getFileOrThrow(kernelPath)
    val (kernelCode, kernelSymbols) = if (kernelFile.name.endsWith(".bin")) {
        val lines = kernelFile.readLines().filter { it.isNotBlank() }
        val words = (if (lines[0].startsWith("@")) lines.drop(1) else lines).map { it.trim().toUShort() }
        words to emptyMap<String, UShort>()
    } else {
        // Compile and patch kernel.lx in memory on the fly
        assembleAndLinkKernel(kernelFile)
    }

    val (userCode, userSymbols) = if (userArgs.size == 1 && userArgs[0].endsWith(".bin")) {
        val userFile = getFileOrThrow(userArgs[0])
        val lines = userFile.readLines().filter { it.isNotBlank() }
        val words = (if (lines[0].startsWith("@")) lines.drop(1) else lines).map { it.trim().toUShort() }
        words to emptyMap<String, UShort>()
    } else {
        // Expand userland sources + libraries and link with 3-word bootstrap
        val expandedPaths = expandPaths(userArgs)
        val objects = expandedPaths.map { path -> ObjectExcreter(getFileOrThrow(path)).generate() }
        val linker = Linker(*objects.toTypedArray(), baseAddressUnsedRightNow = 0u)
        val p1 = linker.passOne()
        val machineCode = linker.passTwo(p1).toList()
        machineCode to p1
    }

    val memory = MemoryBus(PhysicalMemory(65536))

    for ((index, word) in kernelCode.withIndex()) {
        memory.ram.internals[index] = word.toShort()
    }

    val userBase = MemoryMapRanges.userLandRange.first.toInt() // 0x3000
    for ((index, word) in userCode.withIndex()) {
        memory.ram.internals[userBase + index] = word.toShort()
    }

    val cpu = Cpu(memory)
    cpu.isKernelMode = true      // Boot into Ring 0
    cpu.pc = 0u                  // Begin at Vector 0x0000
    cpu.pbr = userBase.toUShort() // Partition Base = 0x3000
    cpu.plr = (MemoryMapRanges.userLandRange.last - MemoryMapRanges.userLandRange.first + 1u).toUShort()
    cpu.flags = 0u               // Mask external interrupts until kernel drops to userland

    // Merge symbol tables so debugger (lx-dbg) and stack traces can resolve both spaces
    val combinedSymbols = mutableMapOf<String, UShort>()
    kernelSymbols.forEach { (k, v) -> combinedSymbols["k.$k"] = v; combinedSymbols[k] = v }
    userSymbols.forEach { (k, v) -> if (!combinedSymbols.containsKey(k)) combinedSymbols[k] = v else combinedSymbols["u.$k"] = v }

    val debugger = Debugger(cpu, memory, combinedSymbols)
    val profiler = if (isProfile) ExecutionProfiler(cpu, combinedSymbols) else null

    runCpuSafely(
        cpu = cpu,
        memory = memory,
        debugger = debugger,
        isInteractive = isDebug,
        shouldDump = shouldDump,
        dumpBaseAddr = 0u,
        dumpLength = kernelCode.size + userCode.size + userBase,
        profiler = profiler
    )
}
private fun handleDecode(args: List<String>) {
    if (args.isEmpty()) throw IllegalArgumentException("Missing input file for -d")
    val file = getFileOrThrow(args[0])
    val linesData = file.readLines()
    val baseAddr = if (linesData[0].startsWith("@")) linesData[0].drop(1).toUShort() else 0u
    val linesInfo = if (linesData[0].startsWith("@")) linesData.drop(1) else linesData
    val machineCode = linesInfo.map { it.toUShort() }

    val memory = MemoryBus(PhysicalMemory())
    for ((index, word) in machineCode.withIndex()) {
        memory.ram.internals[(baseAddr + index.toUInt()).toInt()] = word.toShort()
    }

    println("--- Disassembly of ${file.name} ---")
    var currentAddr = baseAddr
    val endAddr = (baseAddr + machineCode.size.toUInt()).toUShort()

    runBlocking {
        while (currentAddr < endAddr) {
            val dis = SmartDisassembler.disassembleAt(memory, currentAddr)
            val hexAddr = "0x" + currentAddr.toString(16).uppercase().padStart(4, '0')
            println("$hexAddr | ${dis.text}")
            currentAddr = (currentAddr + dis.wordCount.toUInt()).toUShort()
        }
    }
}

private suspend fun handleHexDumpFile(args: List<String>) {
    if (args.isEmpty()) throw IllegalArgumentException("Missing input file for -x")
    val file = getFileOrThrow(args[0])

    val outIndex = args.indexOf("-o")
    val outPath = if (outIndex != -1 && outIndex + 1 < args.size) args[outIndex + 1] else "out.hex"

    val lines = file.readLines().filter { it.isNotBlank() }
    val baseAddress = if (lines[0].startsWith('@')) lines[0].drop(1).toShort() else 0.toShort()
    val machineCode = (if (lines[0].startsWith('@')) lines.drop(1) else lines).map { it.trim().toUShort() }
    val memory = MemoryBus(PhysicalMemory())
    for ((index, word) in machineCode.withIndex()) {
        memory.write((baseAddress + index).toShort().toUShort(), word.toShort())
    }
    val length = machineCode.size.toUShort()

    // Set printToConsole to true ONLY if we aren't outputting to a file.
    val string = printHexDump(
        memory, baseAddress.toUShort(), length.toInt(), returnData = true, printToConsole = outIndex == -1
    )!!

    if (outIndex != -1) {
        File(outPath).writeText(string)
        println("Hex dump saved to $outPath")
    }
}

private suspend fun generateDebugFiles(
    baseName: String,
    baseAddr: UShort,
    machineCode: List<UShort>,
    map: Map<String, UShort>?,
    cpu: Cpu?,
    memory: MemoryBus?,
    debugger: Debugger? = null // <-- 1. Add optional debugger parameter

) {
    if (map != null) {
        val json = Json { prettyPrint = true }
        File("debug/$baseName.map").writeText(json.encodeToString(map.map {
            it.key to it.value.toString(16).uppercase().padStart(4, '0')
        }.toMap()))
    }

    // 2. Disassembled Instructions (.disasm)
    if (map != null || cpu == null) {
        // We need a fake memory bus if the cpu isn't provided
        val memToUse = memory ?: MemoryBus(PhysicalMemory()).apply {
            machineCode.forEachIndexed { i, w -> this.ram.internals[(baseAddr + i.toUInt()).toInt()] = w.toShort() }
        }

        val disasmText = StringBuilder()
        var currentAddr = baseAddr
        val endAddr = (baseAddr + machineCode.size.toUInt()).toUShort()

        while (currentAddr < endAddr) {
            val dis = SmartDisassembler.disassembleAt(
                memToUse, currentAddr, map?.map { it.value to it.key }?.toMap() ?: emptyMap()
            )
            val hexAddr = currentAddr.toString(16).uppercase().padStart(4, '0')
            val label = map?.entries?.find { it.value == currentAddr }?.key?.let { "$it:" }?.padEnd(12) ?: "".padEnd(12)

            disasmText.append("0x$hexAddr | $label ${dis.text}\n")
            currentAddr = (currentAddr + dis.wordCount.toUInt()).toUShort()
        }
        File("debug/$baseName.disasm").writeText(disasmText.toString())
    }

    if (memory != null) {
        val hexString = printHexDump(memory, baseAddr, machineCode.size, returnData = true, printToConsole = false)!!
        File("debug/$baseName.hex").writeText(hexString)
    }

    // 4. Register State & Run History (.regs)
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


fun loadConfig() {
    val configFile = File("configurations/Emulator config.json")
    val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    val config = if (configFile.exists()) {
        try {
            json.decodeFromString<EmulatorConfig>(configFile.readText())
        } catch (e: Exception) {
            println("[WARNING] Failed to parse emulator_config.json. Using defaults. (${e.message})")
            EmulatorConfig()
        }
    } else {
        val defaultConfig = EmulatorConfig()
        configFile.writeText(json.encodeToString(defaultConfig))
        defaultConfig
    }

    Clock.applyConfig(config.clock)
    GlobalConfig.debug = config.debug
}

private suspend fun throwRuntimeError(cpu: Cpu, e: Exception, baseAddr: UShort, dumpLength: Int) {
    System.err.println("==================================================")
    System.err.println("          !!! CPU HARDWARE EXCEPTION !!!          ")
    System.err.println("==================================================")
    System.err.println("  Exception: ${e.message}")
    val hexPC = "0x" + (cpu.pc.toInt() and 0xFFFF).toString(16).uppercase().padStart(4, '0')
    System.err.println("  Program Counter: $hexPC")
    System.err.println("--------------------------------------------------")
    System.err.println(" Register Dump:")

    cpu.registers.registerData.forEachIndexed { index, value ->
        val regName = RegisterType.entries[index].name
        val hexVal = "0x" + (value.toInt() and 0xFFFF).toString(16).uppercase().padStart(4, '0')
        System.err.println("    $regName : $value ($hexVal)")
    }
    System.err.println("==================================================\n")
    printHexDump(cpu.mmu, baseAddr, dumpLength, returnData = false, printToConsole = true)
    System.err.println("==================================================\n")

    exitProcess(1)
}

suspend fun printHexDump(
    memory: MemoryBus, startAddress: UShort, length: Int, returnData: Boolean = false, printToConsole: Boolean = true
): String? {
    val start = startAddress.toInt() and 0xFFFF
    val end = (start + length) and 0xFFFF
    var returnD = ""
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

    val colsBuilder = java.lang.StringBuilder()
    for (i in 0 until wordsPerRow) {
        colsBuilder.append(i.toString(radix = 16).uppercase().padEnd(5))
    }
    val colsStr = colsBuilder.toString()
    val columnLabels = "$prefix$colsStr| ASCII"

    val line = "$borderLine\n$leftDashes$title$rightDashes\n$columnLabels\n$borderLine\n"
    returnD += line

    if (printToConsole) System.err.print(line)

    val alignedStart = start - (start % 8)

    for (addr in alignedStart..end step wordsPerRow) {
        val hexAddr = addr.toString(16).uppercase().padStart(4, '0')
        val wordsHex = java.lang.StringBuilder()
        val asciiChars = java.lang.StringBuilder()

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
        returnD += "$lineOutput\n"
        if (printToConsole) System.err.println(lineOutput)
    }

    if (printToConsole) System.err.println(borderLine)

    return if (returnData) returnD else null
}

private suspend fun runCpuSafely(
    cpu: Cpu,
    memory: MemoryBus,
    debugger: Debugger,
    isInteractive: Boolean,
    shouldDump: Boolean,
    dumpBaseAddr: UShort,
    dumpLength: Int,
    profiler: ExecutionProfiler? = null,

    onHaltOrCrash: (suspend (Exception?) -> Unit)? = null
) {
    var crashException: Exception? = null
    Signal.handle(Signal("INT")) { _ ->
        runBlocking {
            if (GlobalConfig.debug.printHex16Dump) {
                profiler?.printReport()
                crashException = Exception("Keyboard Interrupt")
                onHaltOrCrash?.invoke(crashException)
                throwRuntimeError(cpu, crashException!!, dumpBaseAddr, dumpLength)
            }
        }
    }

    try {
        if (isInteractive) {
            println("[lx-dbg] Interactive Debugger attached")
            debugger.interactive()
        } else if (profiler != null) {
            while (!cpu.isHalted) {
                profiler.sample()
                cpu.tick()
            }
            profiler.printReport()
        } else {
            debugger.runContinuously()
        }

        if (shouldDump) {
            printHexDump(memory, dumpBaseAddr, dumpLength)
        }
        onHaltOrCrash?.invoke(null)
        exitProcess(0)

    } catch (e: Exception) {
        crashException = e
        onHaltOrCrash?.invoke(crashException)
        throwRuntimeError(cpu, e, dumpBaseAddr, dumpLength)
    }
}

private fun assembleAndLinkKernel(kernelFile: File): Pair<List<UShort>, Map<String, UShort>> {
    val parser = Parser(kernelFile, 0.toShort())
    val instructions = parser.decode()
    val words = Backend.encode(instructions).toMutableList()

    for ((offset1, name, type) in parser.relocations) {
        val targetAddr = parser.symbolTable[name]?.toUShort()
            ?: throw LinkerException(kernelFile.absolutePath, name, "Unresolved symbol in kernel")
        val offset = offset1.toInt()
        val currentWord = words[offset]

        val patched = when (type) {
            RelocationType.ABS_16 -> targetAddr
            RelocationType.ABS_LUI -> {
                val top10 = ((targetAddr.toInt() ushr 6) and 0x3FF).toUShort()
                currentWord or top10
            }
            RelocationType.ABS_LLI -> {
                val bottom6 = (targetAddr.toInt() and 0x3F).toUShort()
                currentWord or bottom6
            }
            RelocationType.REL_7 -> {
                val offsetVal = targetAddr.toInt() - (offset + 1)
                if (offsetVal !in -64..63) {
                    throw IllegalStateException("Kernel branch target '$name' out of range: $offsetVal")
                }
                (currentWord.toInt() or (offsetVal and 0x7F)).toUShort()
            }
        }
        words[offset] = patched
    }

    val symbolMap = parser.symbolTable.mapValues { it.value.toUShort() }
    return words to symbolMap
}