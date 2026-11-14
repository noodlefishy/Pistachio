package io.cuttlefish.debugging

import io.cuttlefish.RegisterType
import io.cuttlefish.components.Cpu
import io.cuttlefish.components.MemoryBus
import io.cuttlefish.config.GlobalConfig
import io.cuttlefish.debug.Debugger
import io.cuttlefish.profiling.ExecutionProfiler
import kotlinx.coroutines.runBlocking
import sun.misc.Signal
import kotlin.system.exitProcess

suspend fun runCpuSafely(
	cpu: Cpu,
	memory: MemoryBus,
	debugger: Debugger,
	isInteractive: Boolean,
	shouldDump: Boolean,
	dumpBaseAddr: UShort,
	dumpLength: Int,
	profiler: ExecutionProfiler? = null,
	onHaltOrCrash: (suspend (Exception?) -> Unit)? = null
) {
	var crashException: Exception? = null
	Signal.handle(Signal("INT")) { _ ->
		runBlocking {
			if (GlobalConfig.debug.printHex16Dump) {
				profiler?.printReport()
				crashException = Exception("Keyboard Interrupt")
				onHaltOrCrash?.invoke(crashException)
				throwRuntimeError(cpu, crashException!!, dumpBaseAddr, dumpLength)
			}
		}
	}

	try {
		when {
			isInteractive -> {
				println("[lx-dbg] Interactive Debugger attached")
				debugger.interactive()
			}
			profiler != null -> {
				while (!cpu.isHalted) {
					profiler.sample()
					cpu.tick()
				}
				profiler.printReport()
			}
			else -> debugger.runContinuously()
		}

		if (shouldDump) {
			printHexDump(memory, dumpBaseAddr, dumpLength)
		}
		onHaltOrCrash?.invoke(null)
		exitProcess(0)
	} catch (e: Exception) {
		crashException = e
		onHaltOrCrash?.invoke(crashException)
		throwRuntimeError(cpu, e, dumpBaseAddr, dumpLength)
	}
}

suspend fun throwRuntimeError(cpu: Cpu, e: Exception, baseAddr: UShort, dumpLength: Int) {
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
	printHexDump(cpu.mmu, baseAddr, dumpLength, returnData = false, printToConsole = true)
	System.err.println("==================================================\n")

	exitProcess(1)
}