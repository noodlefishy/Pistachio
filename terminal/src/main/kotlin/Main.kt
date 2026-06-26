package io.cuttlefish

import io.cuttlefish.backend.Backend
import io.cuttlefish.components.*
import io.cuttlefish.components.devices.*
import java.io.File

suspend fun main() {
    val parser = Parser(File("main.kar"))
    val instructions = parser.decode()

    val backend = Backend()
    val machineCode = backend.encode(instructions)

    val memory = MemoryBus(
        PhysicalMemory(1024),
        DisplayDevice()
    )

    for ((index, word) in machineCode.withIndex()) {
        memory.write(index.toShort(), word.toShort())
    }

    val cpu = Cpu(memory)

    // 3. Realistic Execution Loop: Run until a halt condition triggers
    while (!cpu.isHalted) {
        cpu.tick()
    }
}