package io.cuttlefish

import io.cuttlefish.components.*
import io.cuttlefish.components.devices.*
import java.io.*

suspend fun main() {
    val parser = Parser(File("main.kar"))
    val memory = MemoryBus(
        PhysicalMemory(1024),
        DisplayDevice()
    )
    val cpu = Cpu(memory)
    for (line in parser.decode()) {
//        println(line)
        cpu.tick(line)
    }
}