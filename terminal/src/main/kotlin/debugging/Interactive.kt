package io.cuttlefish.debugging

import io.cuttlefish.*
import io.cuttlefish.debug.*
import kotlin.system.*

suspend fun Debugger.interactive() {
    while (!cpu.isHalted) {
        print("(lx-dbg) > ")

        var input = readlnOrNull()?.trim() ?: break

        // The "Empty Enter" trick: repeat the last command
        if (input.isEmpty()) {
            input = lastCommand
        } else {
            lastCommand = input
        }

        val tokens = input.split(Regex("\\s+"))
        val command = tokens[0].lowercase()
        val arg = tokens.getOrNull(1)

        when (command) {
            "s", "step" -> {
                val count = arg?.toIntOrNull() ?: 1
                for (i in 0 until count) {
                    if (cpu.isHalted) break
                    executeStep()
                    println("\t${history.last()}")
                }
            }

            "u", "until" -> {
                val target = resolveTarget(arg)
                if (target == null) {
                    println("Usage: until <label or hex>")
                    continue
                }
                println("Running until 0x${target.toString(16).uppercase()}...")
                while (!cpu.isHalted && cpu.pc != target) {
                    if (cpu.pc in breakPoints) break // Still respect other breakpoints
                    executeStep()
                }
            }

            "c", "continue" -> {
                executeStep()
                runContinuously()
                if (cpu.isHalted) println("\n[DEBUG] CPU Halted")
                return
            }

            "b", "break" -> {
                val target = resolveTarget(arg)
                if (target != null) {
                    breakPoints.add(target)
                    println("Breakpoint set at 0x${target.toString(16).uppercase()}")
                } else {
                    println("Usage: break <label or hex>")
                }
            }

            "clear" -> {
                val target = resolveTarget(arg)
                if (target != null && breakPoints.remove(target)) {
                    println("Breakpoint cleared.")
                } else {
                    println("Breakpoint not found.")
                }
            }

            "bk", "breakpoints" -> {
                println("Active Breakpoints:")
                if (breakPoints.isEmpty()) println("  (none)")
                breakPoints.forEach { println("  - 0x${it.toString(16).uppercase()} (${addressToLabelMap[it] ?: ""})") }
            }

            "regs" -> {
                println("--- Registers ---")
                cpu.registers.registerData.forEachIndexed { index, value ->
                    val hexVal = (value.toInt() and 0xFFFF).toString(16).uppercase().padStart(4, '0')
                    println("  ${RegisterType.entries[index].name} : $value (0x$hexVal)")
                }
            }

            "trace", "t" -> {
                val count = arg?.toIntOrNull() ?: 10
                println("--- Last $count Steps ---")
                history.takeLast(count).forEach { println("  $it") }
            }

            "mem", "x" -> {
                val target = resolveTarget(arg) ?: cpu.pc
                val count = tokens.getOrNull(2)?.toIntOrNull() ?: 8
//                printHexDump(memory,baseAddress,)
                println("--- Memory Dump ---")
                for (i in 0 until count) {
                    val addr = (target + i.toUInt()).toUShort()
                    val word = try {
                        memory.read(addr)
                    } catch (_: Exception) {
                        0.toShort()
                    }
                    val hexWord = (word.toInt() and 0xFFFF).toString(16).uppercase().padStart(4, '0')
                    println("\t0x${addr.toString(16).uppercase()}: $hexWord")
                }
            }

            "q", "quit" -> {
                exitProcess(0)
            }

            else -> println("Unknown command: $command. Try: step [n], until <target>, run, break <target>, regs, trace, mem, quit")
        }
    }
    if (cpu.isHalted) println("\n[DEBUG] CPU Halted.")
}
