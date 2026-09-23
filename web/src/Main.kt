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

// --- Debugger State ---
var runUntilTarget: UShort? = null
val breakpoints = mutableSetOf<UShort>()
var viewAddress: UShort? = null
var symbolMap = mapOf<UShort, String>() // Holds our loaded .map file labels

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

    // Handle multiple file uploads (.bin AND .map)
    filePicker.addEventListener("change", { _: Event ->
        val fileList = filePicker.files ?: return@addEventListener
        if (fileList.length == 0) return@addEventListener

        for (i in 0 until fileList.length) {
            val file = fileList.item(i) ?: continue
            val reader = FileReader()
            reader.onload = {
                val text = reader.result?.toString() ?: ""

                if (file.name.endsWith(".map")) {
                    loadSymbolMap(text)
                } else if (file.name.endsWith(".bin")) {
                    loadAndRunBinary(text, memory, globalCpu!!)
                }

                btnStep.disabled = false
                btnRun.disabled = false
                updateUI()
            }
            reader.readAsText(file)
        }
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

    // --- SCROLL BUTTONS ---
    document.getElementById("btnScrollUp")?.addEventListener("click", {
        val curr = viewAddress ?: globalCpu?.pc ?: 0u
        viewAddress = (curr.toInt() - 2).coerceAtLeast(0).toUShort()
        updateUI()
    })
    document.getElementById("btnScrollDown")?.addEventListener("click", {
        val curr = viewAddress ?: globalCpu?.pc ?: 0u
        viewAddress = (curr.toInt() + 2).coerceAtMost(0xFFFF).toUShort()
        updateUI()
    })
    document.getElementById("btnSnapPC")?.addEventListener("click", {
        viewAddress = null
        updateUI()
    })

    // --- COMMAND REPL (lx-dbg) ---
    val cmdInput = document.getElementById("cmdInput") as HTMLInputElement
    val btnCmd = document.getElementById("btnCmd") as HTMLButtonElement

    fun executeCommand(cmdStr: String) {
        val tokens = cmdStr.trim().replace("\t", " ").split(" ").filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return
        val cmd = tokens[0].lowercase()
        val arg = tokens.getOrNull(1)

        fun parseTarget(t: String?): UShort? {
            if (t == null) return null
            if (t.startsWith("0x", ignoreCase = true)) return t.substring(2).toUIntOrNull(16)?.toUShort()
            val asNum = t.toUIntOrNull(10)
            if (asNum != null) return asNum.toUShort()
            // Lookup target by symbol name!
            val entry = symbolMap.entries.find { it.value == t }
            return entry?.key
        }

        when (cmd) {
            "s", "step" -> btnStep.click()
            "r", "c", "run", "continue" -> if (!isRunning) btnRun.click()
            "p", "pause" -> if (isRunning) btnRun.click()
            "u", "until" -> {
                runUntilTarget = parseTarget(arg)
                if (runUntilTarget != null && !isRunning) btnRun.click()
            }
            "b", "break" -> parseTarget(arg)?.let { breakpoints.add(it) }
            "clear" -> parseTarget(arg)?.let { breakpoints.remove(it) }
            "xvon", "x", "mem" -> parseTarget(arg)?.let { viewAddress = it }
        }
        updateUI()
    }

    btnCmd.addEventListener("click", {
        executeCommand(cmdInput.value)
        cmdInput.value = ""
    })
    cmdInput.addEventListener("keydown", { event ->
        val e = event as KeyboardEvent
        if (e.key == "Enter") {
            executeCommand(cmdInput.value)
            cmdInput.value = ""
        }
    })

    // Keyboard shortcuts
    window.addEventListener("keydown", { event ->
        val e = event as KeyboardEvent
        if (document.activeElement != cmdInput) {
            if (e.key.lowercase() == "s" && !isRunning && globalCpu?.isHalted == false) {
                scope.launch { globalCpu!!.tick(); updateUI() }
            }
            if (e.key.lowercase() == "p" && globalCpu?.isHalted == false) {
                btnRun.click()
            }
        }
    })

    updateUI()
}

// Parses the .map JSON file safely without relying on kotlinx.serialization in Wasm
fun loadSymbolMap(jsonText: String) {
    val newMap = mutableMapOf<UShort, String>()

    // Safely strip JSON punctuation without crashing Wasm DCE
    val clean = jsonText.filter { it != '"' && it != '{' && it != '}' && it != '\n' && it != '\r' }

    val entries = clean.split(',')
    for (entry in entries) {
        val parts = entry.split(':')
        if (parts.size == 2) {
            val name = parts[0].trim()
            val hexVal = parts[1].trim().toUShortOrNull(16)
            if (hexVal != null) newMap[hexVal] = name
        }
    }
    symbolMap = newMap
    println("Loaded ${symbolMap.size} symbols from map file.")
}

fun loadAndRunBinary(fileText: String, memory: MemoryBus, cpu: Cpu) {
    val lines = fileText.split('\n')
        .map { it.filter { c -> c != '\r' }.trim() }
        .filter { it.isNotEmpty() }

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
    runUntilTarget = null
    viewAddress = null
    document.getElementById("status")?.textContent = "Loaded ${machineWords.size} words."
}

fun runLoop() {
    if (!isRunning || globalCpu == null || globalCpu!!.isHalted) {
        isRunning = false
        document.getElementById("btnRun")?.textContent = "Run (P)"
        document.getElementById("btnRun")?.classList?.remove("active")
        updateUI()
        return
    }

    scope.launch {
        try {
            for (i in 0 until 5000) {
                if (globalCpu!!.isHalted || !isRunning) break

                if (globalCpu!!.pc in breakpoints || globalCpu!!.pc == runUntilTarget) {
                    isRunning = false
                    runUntilTarget = null
                    break
                }

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

// FORMATTERS
fun formatHex(value: Short): String = "0x" + (value.toInt() and 0xFFFF).toString(16).uppercase().padStart(4, '0')
fun formatHexU(value: UShort): String = "0x" + value.toString(16).uppercase().padStart(4, '0')

// Returns the symbol name if it exists, otherwise the raw hex
fun formatAddr(value: UShort): String {
    val label = symbolMap[value]
    return label ?: formatHexU(value)
}

fun updateUI() {
    val cpu = globalCpu ?: return
    scope.launch {
        document.getElementById("cpuStatus")?.textContent = if (cpu.isHalted) "HALTED" else "RUNNING"
        document.getElementById("regPC")?.textContent = formatAddr(cpu.pc)
        document.getElementById("regEPC")?.textContent = formatAddr(cpu.epc)

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

        val stackView = document.getElementById("stackView")
        if (stackView != null) {
            val sp = cpu.registers.read(RegisterType.R6).toUShort()
            var html = ""
            for (offset in 3 downTo -4) {
                val addr = (sp.toInt() + offset).toUShort()
                val isSp = offset == 0
                val v = try { cpu.mmu.read(addr) } catch (e:Exception) { 0 }
                val prefix = if (isSp) "<b>SP -> </b>" else "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
                html += "<div>$prefix<span class='addr'>${formatAddr(addr)}</span> <span class='hex'>${formatHex(v)}</span></div>"
            }
            stackView.innerHTML = html
        }

        val codeView = document.getElementById("codeView")
        if (codeView != null) {
            var html = ""
            var currentAddr = viewAddress ?: cpu.pc

            for (i in 0..15) {
                val rawWord = try { cpu.mmu.read(currentAddr) } catch(e:Exception){ 0 }
                // Pass the symbolMap so the Smart Disassembler can replace addresses with labels!
                val disasm = SmartDisassembler.disassembleAt(cpu.mmu, currentAddr, symbolMap)

                val nextAddr = (currentAddr + disasm.wordCount.toUInt()).toUShort()
                val isActive = cpu.pc in currentAddr until nextAddr
                val isBreakpoint = currentAddr in breakpoints

                var cssClass = "code-row"
                if (isActive) cssClass += " active-pc"
                if (isBreakpoint) cssClass += " breakpoint"

                val bpMarker = if (isBreakpoint) "🔴" else "&nbsp;&nbsp;"

                html += "<div class='$cssClass'><span class='addr'>$bpMarker ${formatAddr(currentAddr)}</span> <span class='hex'>${formatHex(rawWord)}</span> <span class='inst'>${disasm.text}</span></div>"

                currentAddr = nextAddr
                if (currentAddr >= 0xFFFFu) break
            }
            codeView.innerHTML = html
        }
    }
}