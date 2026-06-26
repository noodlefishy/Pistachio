package io.cuttlefish

import io.cuttlefish.backend.*
import io.cuttlefish.components.*
import io.cuttlefish.components.devices.Console
import io.cuttlefish.components.devices.DisplayDevice
import io.cuttlefish.linking.*
import java.io.*
import kotlin.system.*

suspend fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println(
            $$"""
            [USAGE] lc -c $file -o $out (compile)
            [USAGE] lc -i $file (compile + run)
            [USAGE] lc -r $file (run)
            [USAGE] lc -os $kernel $file.                                              
        """.trimIndent()
        )
        exitProcess(0)
    }
    val file = File(args[1])
    when (args[0]) { // flag
        "-t" -> {
            val parse = Parser(file, 0).decode()
            parse.forEachIndexed { index, instruction -> println("$index | $instruction") }
        }


        "-c" -> {
            val parse = Parser(file, 0).decode()
            val backend = Backend()
            val machineCode = backend.encode(parse)
            val ff = if (args.size >= 3 && args[2] == "-o") File(args[3]) else File("${file.nameWithoutExtension}.bin")
            ff.writeText("")
            machineCode.forEach { ff.appendText(it.toString() + '\n') }
        }

        "-i" -> {
            val parse = Parser(file, 0).decode() // TODO, make 0 base intentional
            val backend = Backend()
            val machineCode = backend.encode(parse)

            val memory = MemoryBus(
                PhysicalMemory(), Console()
            )
            for ((index, word) in machineCode.withIndex()) {
                memory.write(index.toShort(), word.toShort())
            }
            val cpu = Cpu(memory)
            while (!cpu.isHalted) {
                cpu.tick()
            }
        }

        "-r" -> {
            val machineCode = File(args[1]).readLines().map { it.toUShort() }
            val memory = MemoryBus(
                PhysicalMemory(), Console()
            )
            for ((index, word) in machineCode.withIndex()) {
                memory.write(index.toShort(), word.toShort())
            }
            val cpu = Cpu(memory)
            while (!cpu.isHalted) {
                cpu.tick()
            }
        }

        "-os" -> {
            val kernelFile = File(args[1]) // kernel.kar
            val mainFile = File(args[2])   // main.kar
            val kernelCode = Backend().encode(Parser(kernelFile, 0x0000.toShort()).decode())
            val mainCode = Backend().encode(Parser(mainFile, MemoryMapRanges.userLandRange.first.toShort()).decode())
            val memory = MemoryBus(PhysicalMemory(65536), Console())
            // Flash Kernel into 0x0000+
            kernelCode.forEachIndexed { i, word -> memory.write(i.toShort(), word.toShort()) }
            // Flash Main into 0x1000+
            mainCode.forEachIndexed { i, word ->
                memory.write(
                    (MemoryMapRanges.userLandRange.first + i).toShort(), word.toShort()
                )
            }
            val cpu = Cpu(memory)
            while (!cpu.isHalted) {
                cpu.tick()
            }
        }

        "-l" -> {
            val sourceFiles = mutableListOf<File>()
            var outFile = File("unnamed.out")

            var i = 1
            while (i < args.size) {
                if (args[i] == "-o" && i + 1 < args.size) {
                    outFile = File(args[i + 1])
                    i += 2
                } else {
                    sourceFiles.add(File(args[i]))
                    i++
                }
            }
            val objectFiles = sourceFiles.map {
                ObjectExcreter(it).generate()
            }.toTypedArray()
            val linker = Linker(*objectFiles, baseAddress = MemoryMapRanges.userLandRange.first.toUShort())
//            val binary = linker.link()
//            outFile.writeText("") // Clear file
//            binary.forEach { outFile.appendText(it.toString() + "\n") }
        }


    }
}