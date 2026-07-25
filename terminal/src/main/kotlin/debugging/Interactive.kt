package io.cuttlefish.debugging

import io.cuttlefish.debug.*

suspend fun Debugger.interactive() {
    while (!cpu.isHalted) {
        val nextInstruction = peekNextInstruction()
        println("\n[DEBUG] -->${decipherLabel(cpu.pc)} $nextInstruction")
        print("(lx-dbg) > ")

        val input = readlnOrNull() ?: continue
        if (input.isEmpty()) continue

        val tokens = input.split(' ')

        when (val command = tokens.first().lowercase()) {
            "s","step" -> {
                executeStep()
                println("\t${history.last()}")
            }
            "c","continue" -> {
                executeStep();runContinuously()
                if (cpu.isHalted) continue
            }
        }
    }
}