package io.cuttlefish

import io.cuttlefish.backend.*
import io.cuttlefish.components.*
import io.cuttlefish.config.*
import io.cuttlefish.linking.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import sun.misc.Signal
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
          -d.    <file.lx>                     Parses and decodes a file.bin (prints instructions).
          -x     <file.bin> [length]           Produces a static hex dump of compiled binary. 
          -h, --help                           Show this help menu.
          
        Examples:
          lx -b main.lx math.lx -o program.bin
          lx -i main.lx "program files/lib" 
          lx -i main.lx "program files/lib" --dump
          lx -x out.bin -1
          lx -os kernel.lx main.lx
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

private suspend fun runCpuSafely(
    cpu: Cpu, memory: MemoryBus, shouldDump: Boolean, dumpBaseAddr: UShort, dumpLength: Int
) {
    Signal.handle(Signal("INT")) { _ ->
        runBlocking { throwRuntimeError(cpu, Exception("Keyboard Interrupt"), dumpBaseAddr, dumpLength) }
    }
    try {
        while (!cpu.isHalted) {
            cpu.tick()
        }
        if (shouldDump) {
            printHexDump(memory, dumpBaseAddr, dumpLength)
        }
    } catch (e: Exception) {
        throwRuntimeError(cpu, e, dumpBaseAddr, dumpLength)
    }
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
    val machineCode = Backend().encode(parse)

    val outFile = File(outPath)
    outFile.writeText(machineCode.joinToString("\n"))
}

private fun handleBuild(args: List<String>) {
    if (args.isEmpty()) throw IllegalArgumentException("Missing input files for -b")

    val outIndex = args.indexOf("-o")
    val inputPaths = if (outIndex != -1) args.subList(0, outIndex) else args
    val outPath = if (outIndex != -1 && outIndex + 1 < args.size) args[outIndex + 1] else "out.bin"
    if (inputPaths.isEmpty()) throw IllegalArgumentException("No input files provided")

    val expandedPaths = expandPaths(inputPaths)
    val objects = expandedPaths.map { path -> ObjectExcreter(getFileOrThrow(path)).generate() }

    val baseAddr = MemoryMapRanges.userLandRange.first // 0x3000

    val linker = Linker(*objects.toTypedArray(), baseAddress = baseAddr.toUShort())
    val p1 = linker.passOne()
    val finalBinary = linker.passTwo(p1)

    File(outPath).writeText("@$baseAddr\n" + finalBinary.joinToString("\n"))
}

private suspend fun handleCompileAndRun(args: List<String>) {
    if (args.isEmpty()) throw IllegalArgumentException("Missing input files for -i")
    val shouldDump = args.contains("--dump")

    val outIndex = args.indexOf("-o")
    val outPath = if (outIndex != -1 && outIndex + 1 < args.size) args[outIndex + 1] else "out.hex"

    // Safe argument extraction
    val cleanArgs = args.filterIndexed { index, arg ->
        arg != "--dump" && !(outIndex != -1 && (index == outIndex || index == outIndex + 1))
    }

    val expandedPaths = expandPaths(cleanArgs)
    val objects = expandedPaths.map { path -> ObjectExcreter(getFileOrThrow(path)).generate() }

    val baseAddr = MemoryMapRanges.userLandRange.first // 0x3000

    val linker = Linker(*objects.toTypedArray(), baseAddress = baseAddr.toUShort())
    val p1 = linker.passOne()
    val machineCode = linker.passTwo(p1)

    if (outIndex != -1) {
        File(outPath).writeText("@$baseAddr\n" + machineCode.joinToString("\n"))
    }

    val memory = MemoryBus(PhysicalMemory())
    for ((index, word) in machineCode.withIndex()) {
        memory.ram.internals[(baseAddr + index.toUInt()).toInt()] = word.toShort()
    }

    val cpu = Cpu(memory)
    cpu.pc = baseAddr.toUShort()
    runCpuSafely(cpu, memory, shouldDump, baseAddr.toUShort(), machineCode.size)
}

private suspend fun handleRun(args: List<String>) {
    if (args.isEmpty()) throw IllegalArgumentException("Missing input file for -r")
    val shouldDump = args.contains("--dump")
    val cleanArgs = args.filter { it != "--dump" }

    val file = getFileOrThrow(cleanArgs[0])
    val lines = file.readLines().filter { it.isNotBlank() }
    val baseAddress = if (lines[0].startsWith("@")) lines[0].drop(1).toShort() else 0.toShort()
    val machineCode = (if (lines[0].startsWith("@")) lines.drop(1) else lines).map { it.trim().toUShort() }

    val memory = MemoryBus(PhysicalMemory())
    for ((index, word) in machineCode.withIndex()) {
        memory.write((baseAddress + index).toUShort(), word.toShort())
    }

    val cpu = Cpu(memory)
    cpu.pc = baseAddress.toUShort()
    runCpuSafely(cpu, memory, shouldDump, baseAddress.toUShort(), machineCode.size)
}

private suspend fun handleRunOs(args: List<String>) {
    if (args.size < 2) throw IllegalArgumentException("Missing kernel or main file.\nUsage: lx -os <kernel.lx> <main.lx>")
    val shouldDump = args.contains("--dump")
    val cleanArgs = args.filter { it != "--dump" }

    val kernelFile = getFileOrThrow(cleanArgs[0])
    val mainFile = getFileOrThrow(cleanArgs[1])

    val kernelCode = Backend().encode(Parser(kernelFile, MemoryMapRanges.vectorRange.first.toShort()).decode())
    val mainCode = Backend().encode(Parser(mainFile, MemoryMapRanges.userLandRange.first.toShort()).decode())

    val memory = MemoryBus(PhysicalMemory(65536))

    kernelCode.forEachIndexed { i, word ->
        memory.write(i.toUShort(), word.toShort())
    }
    mainCode.forEachIndexed { i, word ->
        memory.write((MemoryMapRanges.userLandRange.first + i.toUInt()).toUShort(), word.toShort())
    }

    val cpu = Cpu(memory)
    runCpuSafely(cpu, memory, shouldDump, MemoryMapRanges.userLandRange.first.toUShort(), mainCode.size)
}

private fun handleDecode(args: List<String>) {
    if (args.isEmpty()) throw IllegalArgumentException("Missing input file for -d")
    val file = getFileOrThrow(args[0])
    val linesData = file.readLines()
    val linesInfo = if (linesData[0].startsWith("@")) linesData.drop(1) else linesData

    val parse = Backend().decode(linesInfo.map { it.toUShort() })
    parse.forEachIndexed { index, instruction -> println("$index | $instruction") }
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
    val string = printHexDump(memory, baseAddress.toUShort(), length.toInt(), true)!!

    if (outIndex != -1) {
        File(outPath).writeText(string)
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
    System.err.println(" History of ${cpu.history.size} entries:")
    cpu.history.forEach { System.err.println("    $it") }
    System.err.println("==================================================\n")
    printHexDump(cpu.mmu, baseAddr, dumpLength)
    System.err.println("==================================================\n")

    exitProcess(1)
}

suspend fun printHexDump(memory: MemoryBus, startAddress: UShort, length: Int, returnData: Boolean = false): String? {
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
        colsBuilder.append(i.toString().padEnd(5))
    }
    val colsStr = colsBuilder.toString()
    val columnLabels = "$prefix$colsStr| ASCII"

    System.err.println(borderLine)
    System.err.println("$leftDashes$title$rightDashes")
    System.err.println(columnLabels)
    System.err.println(borderLine)

    val alignedStart = start - (start % 8)

    for (addr in alignedStart..end step wordsPerRow) {
        val hexAddr = addr.toString(16).uppercase().padStart(4, '0')
        val wordsHex = java.lang.StringBuilder()
        val asciiChars = java.lang.StringBuilder()

        for (i in 0 until wordsPerRow) {
            val currentAddr = (addr + i).toShort()
            val word = try {
                memory.read(currentAddr.toUShort()).toInt() and 0xFFFF
            } catch (e: Exception) {
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
        System.err.println(lineOutput)
    }

    System.err.println(borderLine)

    return if (returnData) returnD else null
}