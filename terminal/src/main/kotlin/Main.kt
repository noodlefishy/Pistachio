package io.cuttlefish

import io.cuttlefish.backend.*
import io.cuttlefish.components.*
import io.cuttlefish.components.devices.*
import java.io.*

suspend fun main() {
    val parser = Parser(File("main.kar"))
    val instructions = parser.decode()

    val backend = Backend()
    val machineCode = backend.encode(instructions)
    println(machineCode)
    println(backend.decode(machineCode).joinToString())


    val memory = MemoryBus(
        PhysicalMemory(1024),
        DisplayDevice()
    )

    for ((index, word) in machineCode.withIndex()) {
        memory.write(index.toShort(), word.toShort())
    }

    val cpu = Cpu(memory)

    while (!cpu.isHalted) {
        cpu.tick()
    }
}