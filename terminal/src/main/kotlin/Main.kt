package io.cuttlefish

import Linker
import io.cuttlefish.backend.*
import io.cuttlefish.components.*
import io.cuttlefish.components.devices.*
import io.cuttlefish.config.*
import io.cuttlefish.linking.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.*
import kotlin.system.*

fun printUsage() {
    println(
        """
        Usage: lx <command> [options]
        
        Commands:
          -c     <file.lx> [-o <out.bin>]      Compile a single source file to machine code.
          -b     <f1.lx> <f2.lx> [-o <out>]    Compile & link multiple source files into a binary.
          -i     <file.lx>                     Compile and immediately run a source file.
          -r     <file.bin>                    Run a pre-compiled machine code file.
          -os    <kernel.lx> <main.lx>         Compile and run an OS kernel with a userland program.
          -t     <file.lx>                     Tokenize and parse a file (prints instructions).
          -d.    <file.lx>                     Parses and decodes a file.bin (prints instructions).
          -h, --help                           Show this help menu.
          
        Examples:
          lx -b main.lx math.lx -o program.bin
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

    when (command) {
        "-t" -> handleTokenize(remainingArgs)
        "-c" -> handleCompile(remainingArgs)
        "-b" -> handleBuild(remainingArgs)
        "-i" -> handleCompileAndRun(remainingArgs)
        "-r" -> handleRun(remainingArgs)
        "-d" -> handleDecode(remainingArgs)
        "-os" -> handleRunOs(remainingArgs)
        else -> {
            System.err.println("[ERROR] Unknown command or flag: $command")
            printUsage()
            exitProcess(1)
        }
    }
}


private fun getFileOrThrow(path: String): File {
    val file = File(path)
    if (!file.exists()) throw FileNotFoundException("File not found: $path")
    return file
}

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

    val objects = inputPaths.map { path ->
        val file = getFileOrThrow(path)
        ObjectExcreter(file).generate()
    }
    val baseAddr = MemoryMapRanges.userLandRange.first // 0x3000

    val linker = Linker(*objects.toTypedArray(), baseAddress = baseAddr.toUShort())
    val p1 = linker.passOne()
    val finalBinary = linker.passTwo(p1)

    val outFile = File(outPath)
    outFile.writeText("@$baseAddr\n" + finalBinary.joinToString("\n"))
//    outFile.writeText(finalBinary.joinToString("\n"))
}

private suspend fun handleCompileAndRun(args: List<String>) {
    if (args.isEmpty()) throw IllegalArgumentException("Missing input file for -i")
    val file = getFileOrThrow(args[0])

    val parse = Parser(file, 0)
    if (parse.relocations.isNotEmpty()) {
        val missing = parse.relocations.map { it.name }.distinct().joinToString(", ")
        throw IllegalStateException("Unresolved External Symbols: [$missing]. Single-file execution (-i) cannot run programmes with missing dependencies. :C")
    }

    val machineCode = Backend().encode(parse.decode())

    val memory = MemoryBus(PhysicalMemory())
    for ((index, word) in machineCode.withIndex()) {
        memory.write(index.toShort(), word.toShort())
    }

    val cpu = Cpu(memory)
    while (!cpu.isHalted) {
        cpu.tick()
    }
}

private suspend fun handleRun(args: List<String>) {
    if (args.isEmpty()) throw IllegalArgumentException("Missing input file for -r")
    val file = getFileOrThrow(args[0])

    val lines = file.readLines().filter { it.isNotBlank() }
    val baseAddress = if (lines[0].startsWith("@")) lines[0].drop(1).toShort() else 0.toShort()
    val machineCode = (if (lines[0].startsWith("@")) lines.drop(1) else lines).map { it.trim().toUShort() }

    val memory = MemoryBus(PhysicalMemory())
    for ((index, word) in machineCode.withIndex()) {
        memory.write((baseAddress + index).toShort(), word.toShort())
    }
    val cpu = Cpu(memory)
    cpu.pc = baseAddress
    while (!cpu.isHalted) {
        cpu.tick()
    }
}

private suspend fun handleRunOs(args: List<String>) {
    if (args.size < 2) throw IllegalArgumentException("Missing kernel or main file.\nUsage: lx -os <kernel.lx> <main.lx>")

    val kernelFile = getFileOrThrow(args[0])
    val mainFile = getFileOrThrow(args[1])

    val kernelCode = Backend().encode(Parser(kernelFile, MemoryMapRanges.vectorRange.first.toShort()).decode())
    val mainCode = Backend().encode(Parser(mainFile, MemoryMapRanges.userLandRange.first.toShort()).decode())

    val memory = MemoryBus(PhysicalMemory(65536))

    kernelCode.forEachIndexed { i, word ->
        memory.write(i.toShort(), word.toShort())
    }
    mainCode.forEachIndexed { i, word ->
        memory.write((MemoryMapRanges.userLandRange.first + i).toShort(), word.toShort())
    }

    val cpu = Cpu(memory)
    while (!cpu.isHalted) {
        cpu.tick()
    }
}

private fun handleDecode(args: List<String>) {
    if (args.isEmpty()) throw IllegalArgumentException("Missing input file for -d")
    val file = getFileOrThrow(args[0])
    val linesData = file.readLines()
    val linesInfo = if (linesData[0].startsWith("@")) linesData.drop(1) else linesData

    val parse = Backend().decode(linesInfo.map { it.toUShort() })
    parse.forEachIndexed { index, instruction -> println("$index | $instruction") }
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