@file:Suppress("UnusedImport")

package io.cuttlefish

import io.cuttlefish.cli.*
import io.cuttlefish.linking.LinkerException
import io.cuttlefish.parsing.CompilationException
import java.io.File
import kotlin.system.exitProcess

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
          -d     <file.bin>                    Parses and decodes a file.bin (prints instructions).
          -x     <file.bin> [length]           Produces a static hex dump of compiled binary. 
          
        Options:
          -h, --help                           Show this help menu.
          --dump                               Prints the hex dump to the console on completion.
          --debug                              Generates full debug files based on output name:
                                               (.disasm, .hex, .map, .regs). Works on crashes too!
          --debugf                             Silently generates workspace debug files.
          --profile                            Prints execution profiler report on completion.
          
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
        System.err.println("Referenced in file '${File(e.fileAbsolutePath).name}':")

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