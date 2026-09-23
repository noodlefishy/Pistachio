package io.cuttlefish.web
import io.cuttlefish.components.Clock
import io.cuttlefish.components.Cpu
import io.cuttlefish.components.MemoryBus
import io.cuttlefish.components.PhysicalMemory
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.files.FileReader

fun main() {
    if (document.body != null) {
        initWebVm()
    } else {
        window.addEventListener("DOMContentLoaded", { initWebVm() })
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
fun initWebVm() {
    // FIX: Disable all artificial hardware delays on the web so it runs at full speed!
    Clock.ALU_CALCULATION_TIME = 0L
    Clock.MEMORY_READ_TIME = 0L
    Clock.MEMORY_WRITE_TIME = 0L
    Clock.REGISTER_READ_TIME = 0L
    Clock.REGISTER_WRITE_TIME = 0L
    Clock.DEVICE_CONSOLE_WRITE_TIME = 0L
    Clock.DEVICE_CONSOLE_READ_TIME = 0L

    println("Pistachio VM Web Engine Ready!")

    val memory = MemoryBus(PhysicalMemory())
    val display = WebCanvasDisplay("screen")
    memory.attach(display)

    val cpu = Cpu(memory)

    val filePicker = document.getElementById("binPicker") as HTMLInputElement
    val statusText = document.getElementById("status")
    val canvas = document.getElementById("screen") as HTMLCanvasElement

    filePicker.addEventListener("change", { _: Event ->
        val fileList = filePicker.files ?: return@addEventListener
        val file = fileList.item(0) ?: return@addEventListener

        statusText?.textContent = "Loading ${file.name}..."

        val reader = FileReader()
        reader.onload = {
            val text = reader.result?.toString() ?: ""
            loadAndRunBinary(text, memory, cpu)
            statusText?.textContent = "Running ${file.name} — Click canvas to type!"
            canvas.focus()
        }
        reader.readAsText(file)
    })
}
fun loadAndRunBinary(fileText: String, memory: MemoryBus, cpu: Cpu) {
    val lines = fileText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return

    // Parse base address (@12288 or @0x3000)
    val baseAddr = if (lines[0].startsWith("@")) {
        lines[0].drop(1).toUInt()
    } else 0x3000u

    val machineWords = (if (lines[0].startsWith("@")) lines.drop(1) else lines).map { it.toUShort() }

    // Clear RAM and load binary
    memory.ram.internals.fill(0)
    for ((i, word) in machineWords.withIndex()) {
        memory.ram.internals[(baseAddr + i.toUInt()).toInt()] = word.toShort()
    }

    // Reset CPU
    cpu.isHalted = false
    cpu.pc = baseAddr.toUShort()

    println("Binary loaded (${machineWords.size} words). Starting CPU at 0x${baseAddr.toString(16).uppercase()}...")

    runWebCpuSafely(cpu)
}

fun runWebCpuSafely(cpu: Cpu) {
    val scope = MainScope()

    fun stepBatch(time: Double) {
        if (!cpu.isHalted) {
            scope.launch {
                try {
                    // Run a batch of instructions per animation frame
                    for (i in 0 until 10000) {
                        if (cpu.isHalted) break
                        cpu.tick()
                    }
                } catch (e: Exception) {
                    cpu.isHalted = true
                    println("CPU Execution Halted: ${e.message}")
                    document.getElementById("status")?.textContent = "CPU Halted: ${e.message}"
                }

                if (!cpu.isHalted) {
                    window.requestAnimationFrame(::stepBatch)
                }
            }
        }
    }

    window.requestAnimationFrame(::stepBatch)
}