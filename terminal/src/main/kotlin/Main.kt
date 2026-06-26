package io.cuttlefish

import io.cuttlefish.backend.*
import io.cuttlefish.components.*
import io.cuttlefish.components.devices.*
import java.io.*

// lc -c $file -o $out (compile)
// lc -i $file (compile + run)
// lc -r $file (run)
suspend fun main(args: Array<String>) {
    val file = File(args[2])
    when (args[1]) { // flag
        "-c" -> {
            val parse = Parser(file).decode()
            val backend = Backend()
            val machineCode = backend.encode(parse)
            if (args[3] == "-o") machineCode.forEach { File(args[4]).appendText(it.toString()) }
            else machineCode.forEach { File(file.nameWithoutExtension).appendText(it.toString()) }
        }

        "-i" -> {
            val parse = Parser(file).decode()
            val backend = Backend()
            val machineCode = backend.encode(parse)

            val memory = MemoryBus(
                PhysicalMemory(1024), DisplayDevice()
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
            val machineCode = File(args[2]).readLines().map { it.toUShort() }
            val memory = MemoryBus(
                PhysicalMemory(1024), DisplayDevice()
            )
            for ((index, word) in machineCode.withIndex()) {
                memory.write(index.toShort(), word.toShort())
            }
            val cpu = Cpu(memory)
            while (!cpu.isHalted) {
                cpu.tick()
            }
        }
    }
}


//suspend fun main() {
//    val parser = Parser(File("main.kar"))
//    val instructions = parser.decode()
//
//    val backend = Backend()
//    val machineCode = backend.encode(instructions)
//    println(machineCode)
////    backend.decode(machineCode).forEach(::println)
//
//    val memory = MemoryBus(
//        PhysicalMemory(1024),
//        DisplayDevice()
//    )
//
//    for ((index, word) in machineCode.withIndex()) {
//        memory.write(index.toShort(), word.toShort())
//    }
//
//    val cpu = Cpu(memory)
//
//    while (!cpu.isHalted) {
//        cpu.tick()
//    }
//    println(cpu.registers)
//}