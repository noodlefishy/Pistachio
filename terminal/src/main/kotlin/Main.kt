package io.cuttlefish

import io.cuttlefish.components.*
import kotlinx.coroutines.*
import java.io.*

fun main() {
    val parser = Parser(File("main.kar"))
    val cpu = Cpu {}
    for (line in parser.decode()) {
        runBlocking { cpu.tick(line) }
    }
}