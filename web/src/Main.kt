package io.cuttlefish.web

import io.cuttlefish.RegisterType
import io.cuttlefish.backend.smartFeatures.SmartDisassembler
import io.cuttlefish.components.Clock
import io.cuttlefish.components.Cpu
import io.cuttlefish.components.MemoryBus
import io.cuttlefish.components.PhysicalMemory
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.files.FileReader

fun main() {
    if (document.body != null) {
        initWebDebugger()
    } else {
        window.addEventListener("DOMContentLoaded", { initWebDebugger() })
    }
}

val scope = MainScope()
var globalCpu: Cpu? = null
var isRunning = false

@OptIn(ExperimentalWasmJsInterop::class)
fun initWebDebugger() {
    Clock.ALU_CALCULATION_TIME = 0L
    Clock.MEMORY_READ_TIME = 0L
    Clock.MEMORY_WRITE_TIME = 0L
    Clock.REGISTER_READ_TIME = 0L
    Clock.REGISTER_WRITE_TIME = 0L
    Clock.DEVICE_CONSOLE_WRITE_TIME = 0L
    Clock.DEVICE_CONSOLE_READ_TIME = 0L

    val memory = MemoryBus(PhysicalMemory())
    val display = WebCanvasDisplay("screen")
    memory.attach(display)

    globalCpu = Cpu(memory)

    val filePicker = document.getElementById("binPicker") as HTMLInputElement
    val btnStep = document.getElementById("btnStep") as HTMLButtonElement
    val btnRun = document.getElementById("btnRun") as HTMLButtonElement

    filePicker.addEventListener("change", { _: Event ->
        val fileList = filePicker.files ?: return@addEventListener
        val file = fileList.item(0) ?: return@addEventListener

        val reader = FileReader()
        reader.onload = {
            val text = reader.result?.toString() ?: ""
            loadAndRunBinary(text, memory, globalCpu!!)
            btnStep.disabled = false
            btnRun.disabled = false
            updateUI()
        }
        reader.readAsText(file)
    })

    btnStep.addEventListener("click", {
        if (globalCpu?.isHalted == false) {
            isRunning = false
            btnRun.textContent = "Run / Pause (P)"
            btnRun.classList.remove("active")
            scope.launch {
                globalCpu!!.tick()
                updateUI()
            }
        }
    })

    btnRun.addEventListener("click", {
        if (globalCpu?.isHalted == false) {
            isRunning = !isRunning
            if (isRunning) {
                btnRun.textContent = "Pause (P)"
                btnRun.classList.add("active")
                runLoop()
            } else {
                btnRun.textContent = "Run (P)"
                btnRun.classList.remove("active")
                updateUI()
            }
        }
    })

    // Keyboard shortcuts for debugger
    window.addEventListener("keydown", { event ->
        val e = event as KeyboardEvent
        if (e.key.lowercase() == "s" && !isRunning && globalCpu?.isHalted == false) {
            scope.launch { globalCpu!!.tick(); updateUI() }
        }
        if (e.key.lowercase() == "p" && globalCpu?.isHalted == false) {
            btnRun.click()
        }
    })

    updateUI()
}

fun loadAndRunBinary(fileText: String, memory: MemoryBus, cpu: Cpu) {
    val lines = fileText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return

    val baseAddr = if (lines[0].startsWith("@")) lines[0].drop(1).toUInt() else 0x3000u
    val machineWords = (if (lines[0].startsWith("@")) lines.drop(1) else lines).map { it.toUShort() }

    memory.ram.internals.fill(0)
    for ((i, word) in machineWords.withIndex()) {
        memory.ram.internals[(baseAddr + i.toUInt()).toInt()] = word.toShort()
    }

    cpu.isHalted = false
    cpu.pc = baseAddr.toUShort()
    isRunning = false
    document.getElementById("status")?.textContent = "Loaded ${machineWords.size} words."
}

fun runLoop() {
    if (!isRunning || globalCpu == null || globalCpu!!.isHalted) {
        isRunning = false
        document.getElementById("btnRun")?.textContent = "Run"
        document.getElementById("btnRun")?.classList?.remove("active")
        updateUI()
        return
    }

    scope.launch {
        try {
            // Run a batch to keep frame rate up, but not too high so we can watch it
            for (i in 0 until 2000) {
                if (globalCpu!!.isHalted || !isRunning) break
                globalCpu!!.tick()
            }
        } catch (e: Exception) {
            globalCpu!!.isHalted = true
            document.getElementById("status")?.textContent = "CRASH: ${e.message}"
        }

        updateUI()

        if (isRunning && !globalCpu!!.isHalted) {
            window.requestAnimationFrame { runLoop() }
        }
    }
}

fun formatHex(value: Short): String = "0x" + (value.toInt() and 0xFFFF).toString(16).uppercase().padStart(4, '0')
fun formatHexU(value: UShort): String = "0x" + value.toString(16).uppercase().padStart(4, '0')

fun updateUI() {
    val cpu = globalCpu ?: return
    scope.launch {
        // 1. Update CPU Status
        document.getElementById("cpuStatus")?.textContent = if (cpu.isHalted) "HALTED" else "RUNNING"
        document.getElementById("regPC")?.textContent = formatHexU(cpu.pc)
        document.getElementById("regEPC")?.textContent = formatHexU(cpu.epc)

        // 2. Update Registers
        val regGrid = document.getElementById("registersView")
        if (regGrid != null) {
            var html = ""
            for (i in 0..7) {
                val reg = RegisterType.entries[i]
                val v = cpu.registers.read(reg)
                html += "<div class='reg-box'><span class='reg-name'>${reg.name}</span> <span class='reg-val'>${formatHex(v)}</span></div>"
            }
            regGrid.innerHTML = html
        }

        // 3. Update Stack (R6)
        val stackView = document.getElementById("stackView")
        if (stackView != null) {
            val sp = cpu.registers.read(RegisterType.R6).toUShort()
            var html = ""
            // Show 3 words above and 4 words below the stack pointer
            for (offset in 3 downTo -4) {
                val addr = (sp.toInt() + offset).toUShort()
                val isSp = offset == 0
                val v = try { cpu.mmu.read(addr) } catch (e:Exception) { 0 }
                val prefix = if (isSp) "<span style='color:#00ff88'>SP -> </span>" else "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
                html += "<div>$prefix<span class='addr'>${formatHexU(addr)}</span> <span class='hex'>${formatHex(v)}</span></div>"
            }
            stackView.innerHTML = html
        }

        // 4. Update Disassembly Window (PC - 2 to PC + 8)
        val codeView = document.getElementById("codeView")
        if (codeView != null) {
            var html = ""
            var currentAddr = (cpu.pc.toInt() - 2).coerceAtLeast(0).toUShort()

            for (i in 0..10) {
                val isActive = currentAddr == cpu.pc
                val cssClass = if (isActive) "code-row active-pc" else "code-row"

                val rawWord = try { cpu.mmu.read(currentAddr) } catch(e:Exception){ 0 }
                // Use SmartDisassembler to decode the instruction!
                val disasm = SmartDisassembler.disassembleAt(cpu.mmu, currentAddr, emptyMap())

                html += "<div class='$cssClass'><span class='addr'>${formatHexU(currentAddr)}</span> <span class='hex'>${formatHex(rawWord)}</span> <span class='inst'>${disasm.text}</span></div>"

                // If it's a multi-word macro, skip the extra words visually
                currentAddr = (currentAddr + disasm.wordCount.toUInt()).toUShort()
                if (currentAddr >= 0xFFFFu) break
            }
            codeView.innerHTML = html
        }
    }
}