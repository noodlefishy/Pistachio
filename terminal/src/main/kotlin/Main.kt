package io.cuttlefish

import Linker
import io.cuttlefish.backend.*
import io.cuttlefish.components.*
import io.cuttlefish.components.devices.*
import io.cuttlefish.linking.*
import java.io.*
import kotlin.system.*

fun printUsage() {
    println(
        """
        Cuttlefish OS / RiSC-16 Toolchain
        =================================
        Usage: lc <command> [options]
        
        Commands:
          -c     <file.lx> [-o <out.bin>]      Compile a single source file to machine code.
          -build <f1.lx> <f2.lx> [-o <out>]   Compile & link multiple source files into a binary.
          -i     <file.lx>                     Compile and immediately run a source file.
          -r     <file.bin>                     Run a pre-compiled machine code file.
          -os    <kernel.lx> <main.lx>        Compile and run an OS kernel with a userland program.
          -t     <file.lx>                     Tokenize and parse a file (prints instructions).
          -h, --help                            Show this help menu.
          
        Examples:
          lc -build main.lx math.lx -o prog.bin
          lc -os kernel.lx main.lx
        """.trimIndent()
    )
}

suspend fun main(args: Array<String>) {
    if (args.isEmpty() || args[0] in listOf("-h", "--help", "help")) {
        printUsage()
        exitProcess(0)
    }

    val command = args[0]
    val remainingArgs = args.drop(1)

    try {
        when (command) {
            "-t" -> handleTokenize(remainingArgs)
            "-c" -> handleCompile(remainingArgs)
            "-build" -> handleBuild(remainingArgs)
            "-i" -> handleCompileAndRun(remainingArgs)
            "-r" -> handleRun(remainingArgs)
            "-os" -> handleRunOs(remainingArgs)
            else -> {
                System.err.println("[ERROR] Unknown command or flag: $command")
                printUsage()
                exitProcess(1)
            }
        }
    } catch (e: Exception) {
        System.err.println("\n[FATAL ERROR] ${e.message}")
        exitProcess(1)
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
    println("--- Tokens for ${file.name} ---")
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
    // Joined with \n so the `-r` command can read back the lines individually
    outFile.writeText(machineCode.joinToString("\n"))
    println("[SUCCESS] Compiled '${file.name}' -> '${outFile.name}'")
}

private fun handleBuild(args: List<String>) {
    if (args.isEmpty()) throw IllegalArgumentException("Missing input files for -build")

    val outIndex = args.indexOf("-o")
    val inputPaths = if (outIndex != -1) args.subList(0, outIndex) else args
    val outPath = if (outIndex != -1 && outIndex + 1 < args.size) args[outIndex + 1] else "out.bin"

    if (inputPaths.isEmpty()) throw IllegalArgumentException("No input files provided.")

    val objects = inputPaths.map { path ->
        val file = getFileOrThrow(path)
        ObjectExcreter(file).generate()
    }

    // Default to the Userland start address
    val linker = Linker(*objects.toTypedArray(), baseAddress = MemoryMapRanges.userLandRange.first.toUShort())
    val p1 = linker.passOne()
    val finalBinary = linker.passTwo(p1)

    val outFile = File(outPath)
    outFile.writeText(finalBinary.joinToString("\n"))
    println("[SUCCESS] Linked ${inputPaths.size} object(s) into '$outPath'")
}

private suspend fun handleCompileAndRun(args: List<String>) {
    if (args.isEmpty()) throw IllegalArgumentException("Missing input file for -i")
    val file = getFileOrThrow(args[0])

    val parse = Parser(file, 0).decode()
    val machineCode = Backend().encode(parse)

    val memory = MemoryBus(PhysicalMemory(), DisplayDevice())
    for ((index, word) in machineCode.withIndex()) {
        memory.write(index.toShort(), word.toShort())
    }

    println("--- Booting ${file.name} ---")
    val cpu = Cpu(memory)
    while (!cpu.isHalted) {
        cpu.tick()
    }
    println("\n--- System Halted ---")
}

private suspend fun handleRun(args: List<String>) {
    if (args.isEmpty()) throw IllegalArgumentException("Missing input file for -r")
    val file = getFileOrThrow(args[0])

    val machineCode = file.readLines()
        .filter { it.isNotBlank() }
        .map { it.trim().toUShort() }

    val memory = MemoryBus(PhysicalMemory(), DisplayDevice())
    for ((index, word) in machineCode.withIndex()) {
        memory.write(index.toShort(), word.toShort())
    }

    println("--- Booting Binary ${file.name} ---")
    val cpu = Cpu(memory)
    while (!cpu.isHalted) {
        cpu.tick()
    }
    println("\n--- System Halted ---")
}

private suspend fun handleRunOs(args: List<String>) {
    if (args.size < 2) throw IllegalArgumentException("Missing kernel or main file.\nUsage: lc -os <kernel.lx> <main.lx>")

    val kernelFile = getFileOrThrow(args[0])
    val mainFile = getFileOrThrow(args[1])

    val kernelCode = Backend().encode(Parser(kernelFile, MemoryMapRanges.vectorRange.first.toShort()).decode())
    val mainCode = Backend().encode(Parser(mainFile, MemoryMapRanges.userLandRange.first.toShort()).decode())

    val memory = MemoryBus(PhysicalMemory(65536), DisplayDevice())

    // Flash Kernel into Address 0x0000+
    kernelCode.forEachIndexed { i, word ->
        memory.write(i.toShort(), word.toShort())
    }
    // Flash Main into Address 0x3000+
    mainCode.forEachIndexed { i, word ->
        memory.write((MemoryMapRanges.userLandRange.first + i).toShort(), word.toShort())
    }

    println("--- Booting OS (${kernelFile.name} + ${mainFile.name}) ---")
    val cpu = Cpu(memory)
    while (!cpu.isHalted) {
        cpu.tick()
    }
    println("\n--- System Halted ---")
}