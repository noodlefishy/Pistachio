package io.cuttlefish

import Linker
import LinkerException
import io.cuttlefish.backend.*
import io.cuttlefish.components.*
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

    val inputPathsAdjustedForDirectories = mutableListOf<String>()

    for (path in inputPaths) {
        if (File(path).isDirectory) {
            val list = File(path).list()
            if (list != null) {
                for (j in list) {
                    inputPathsAdjustedForDirectories.add("${File(path).absolutePath}/$j")
                }
            }
        } else {
            inputPathsAdjustedForDirectories.add(path)
        }
    }

    val objects = inputPathsAdjustedForDirectories.map { path ->
        val file = getFileOrThrow(path)
        ObjectExcreter(file).generate()
    }
    val baseAddr = MemoryMapRanges.userLandRange.first // 0x3000

    val linker = Linker(*objects.toTypedArray(), baseAddress = baseAddr.toUShort())
    val p1 = linker.passOne()
    val finalBinary = linker.passTwo(p1)

    val outFile = File(outPath)
    outFile.writeText("@$baseAddr\n" + finalBinary.joinToString("\n"))
}

private suspend fun handleCompileAndRun(args: List<String>) {
    if (args.isEmpty()) throw IllegalArgumentException("Missing input files for -i")
    val shouldDump = args.contains("--dump")
    val cleanArgs = args.filter { it != "--dump" }


    val inputPathsAdjustedForDirectories = mutableListOf<String>()

    // Parse files and directories recursively exactly like handleBuild!!
    for (path in cleanArgs) {
        val file = File(path)
        if (file.isDirectory) {
            val list = file.list()
            if (list != null) {
                for (j in list) {
                    inputPathsAdjustedForDirectories.add("${file.absolutePath}/$j")
                }
            }
        } else {
            inputPathsAdjustedForDirectories.add(path)
        }
    }

    if (inputPathsAdjustedForDirectories.isEmpty()) {
        throw IllegalArgumentException("No input files resolved")
    }

    val objects = inputPathsAdjustedForDirectories.map { path ->
        val file = getFileOrThrow(path)
        ObjectExcreter(file).generate()
    }

    val baseAddr = MemoryMapRanges.userLandRange.first // 0x3000

    val linker = Linker(*objects.toTypedArray(), baseAddress = baseAddr.toUShort())
    val p1 = linker.passOne()
    val machineCode = linker.passTwo(p1)

    val memory = MemoryBus(PhysicalMemory())
    for ((index, word) in machineCode.withIndex()) {
        memory.write((baseAddr + index.toUInt()).toUShort(), word.toShort())
    }

    val cpu = Cpu(memory)
    cpu.pc = baseAddr.toUShort()
    try {
        while (!cpu.isHalted) {
            cpu.tick()
        }
        if (shouldDump) {
            printHexDump(memory, baseAddr.toUShort(), machineCode.size)

        }
    } catch (e: Exception) {
        throwRuntimeError(cpu, e, baseAddr.toUShort(), machineCode)
    }
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
    try {
        while (!cpu.isHalted) {
            cpu.tick()
        }
        if (shouldDump) {
            printHexDump(memory, baseAddress.toUShort(), machineCode.size)

        }
    } catch (e: Exception) {
        throwRuntimeError(cpu, e, baseAddress.toUShort(), machineCode.toTypedArray())
    }
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
    try {
        while (!cpu.isHalted) {
            cpu.tick()
        }
        if (shouldDump) {
            printHexDump(memory,  MemoryMapRanges.userLandRange.first.toUShort(), mainCode.size)

        }
    } catch (e: Exception) {
        throwRuntimeError(cpu, e, MemoryMapRanges.userLandRange.first.toUShort(), mainCode.toTypedArray())
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


private suspend fun handleHexDumpFile(args: List<String>) {
    if (args.isEmpty()) throw IllegalArgumentException("Missing input file for -x")
    val file = getFileOrThrow(args[0])
    val lines = file.readLines().filter { it.isNotBlank() }
    val baseAddress = if (lines[0].startsWith('@')) lines[0].drop(1).toShort() else 0.toShort()
    val machineCode = (if (lines[0].startsWith('@')) lines.drop(1) else lines).map { it.trim().toUShort() }
    val memory = MemoryBus(PhysicalMemory())
    for ((index, word) in machineCode.withIndex()) {
        memory.write((baseAddress + index).toShort().toUShort(), word.toShort())
    }

    val length = if (args.size >= 2) args[1].toUShort() else machineCode.size.toUShort()
    printHexDump(memory, baseAddress.toUShort(), length.toInt())


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

private suspend fun throwRuntimeError(cpu: Cpu, e: Exception, baseAddr: UShort, machineCode: Array<UShort>) {
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
    printHexDump(cpu.mmu, baseAddr, machineCode.size)
    System.err.println("==================================================\n")


    exitProcess(1)
}


suspend fun printHexDump(memory: MemoryBus, startAddress: UShort, length: Int) {
    val start = startAddress.toInt() and 0xFFFF
    val end = (start + length) and 0xFFFF

    System.err.println("------------------------------------------ POST HEX DUMP 0000 ------------------------------------------")
    System.err.println("ADDR  | 0    1    2    3    4    5    6    7    8    9    10   11   12   13   14   15 | ASCII")
    System.err.println("--------------------------------------------------------------------------------------------------------")

    val alignedStart = start - (start % 8)

    for (addr in alignedStart..end step 16) {
        val hexAddr = addr.toString(16).uppercase().padStart(4, '0')
        val wordsHex = StringBuilder()
        val asciiChars = StringBuilder()

        for (i in 0 until 16) {
            val currentAddr = (addr + i).toShort()
            val word = try {
                memory.read(currentAddr.toUShort()).toInt() and 0xFFFF
            } catch (e: Exception) {
                0x0000 // Return zero if memory address is unreadable
            }

            wordsHex.append(word.toString(16).uppercase().padStart(4, '0')).append(" ")


            asciiChars.append(if (word in 32..126) word.toChar() else '.')
//            val highByte = (word ushr 8) and 0xFF
//            val lowByte = word and 0xFF
//
//            asciiChars.append(if (highByte in 32..126) highByte.toChar() else '.')
//            asciiChars.append(if (lowByte in 32..126) lowByte.toChar() else '.')
        }

        System.err.println("$hexAddr: $wordsHex| $asciiChars")
    }
    System.err.println("-------------------------------------------------------")
}
