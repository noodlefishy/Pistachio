package io.cuttlefish.components

import io.cuttlefish.*
import io.cuttlefish.backend.*
import io.cuttlefish.config.*
import io.cuttlefish.instructions.*

class Cpu(val mmu: MemoryBus) {
    val registers = Registers()
    val alu = Alu()
    var epc: UShort = 0u // Exception Program Counter
    var pc: UShort = 0u // Program Counter
    var isHalted = false
    var isKernelMode = true        // Flag to track CPU privilege level
    private val backend = Backend()
    val history = ArrayDeque<String>(50)
    private var registerEdit = registers::oldWrite
    private var oldRegisterEdit: Pair<RegisterType, Short>? = null

    suspend fun tick() {
        val oldR = registers.registerData.copyOf()
        if (isHalted) return
        if (pc in MemoryMapRanges.userLandRange) {
            isKernelMode = false
        }

        if (!isKernelMode && pc !in MemoryMapRanges.userLandRange) {
            val hexAddress = "0x" + pc.toString(16).uppercase().padStart(4, '0')
            throw IllegalStateException("Segmentation Fault!! User-mode programme attempted to execute instruction at protected address $hexAddress")
        }


        // 1. FETCH
        val rawInstruction = mmu.read(pc)
        val currentPc = pc
        pc++

        // 2. DECODE
        val instruction = backend.decode(rawInstruction.toUShort())

        if (GlobalConfig.debug.printInstructions) {
            println("$currentPc | $instruction")
        }

        if (instruction is Instruction.Jalr && instruction.immediate != 0.toShort()) {
            val trapId = instruction.immediate
            if (trapId == 1.toShort()) {

                if (GlobalConfig.debug.printHistory) {
                    history.forEach { println(it) }
                } else if (GlobalConfig.debug.printRegistersOnHalt) {
                    println("[DEBUG] $registers")
                }
                isHalted = true
                return
            }

            // Trap ID 15: Special RTI/RFE instruction (explained in Phase 3!)
            if (trapId == 15.toShort()) {
                handleRti()
                return
            }

            handleTrap(trapId)
            return
        }

        if (history.size == 50) history.removeFirst()
        history.addLast("${pc.toString(16).uppercase().padStart(4, '0')} | $instruction | $registers")


        // 3. EXECUTE
        when (instruction) {
            is Instruction.Add -> handlerAdd(instruction)
            is Instruction.Addi -> handlerAddImmediate(instruction)
            is Instruction.Beq -> handlerBeq(instruction)
            is Instruction.Jalr -> handlerJalr(instruction)
            is Instruction.Lui -> handlerLui(instruction)
            is Instruction.Lw -> handleLw(instruction)
            is Instruction.Nand -> handlerNand(instruction)
            is Instruction.Sw -> handleSw(instruction)
            is Instruction.DataWord -> error("The data-words like .fill and .space shouldn't be there?")
        }
        if (GlobalConfig.debug.printState) {
//            println(registerEdit.get())
//            println(oldRegisterEdit)
            if (registerEdit.get() != oldRegisterEdit) {
                val reg = oldR[registerEdit.get().first.ordinal]
                val newValue = registerEdit.get().second

                // Convert to Int and apply 16-bit mask to handle negative numbers correctly
                val regInt = reg.toInt()
                val newValInt = newValue.toInt()

                val hexPC = pc.toString(16).padStart(4, '0').uppercase()
                val regHex = (regInt and 0xFFFF).toString(16).padStart(4, '0').uppercase()
                val hexValue = (newValInt and 0xFFFF).toString(16).padStart(4, '0').uppercase()

                // Align fields by padding them to a fixed width
                val regName = registerEdit.get().first.toString().padEnd(3)
                val oldDecStr = "#$reg".padEnd(7)
                val newDecStr = "#$newValue".padEnd(7)

                println(
                    "$hexPC | $regName (0x$regHex / $oldDecStr)  <- $newDecStr (0x$hexValue)"
                )
            }
        }


        oldRegisterEdit = registerEdit.get()

    }

    private suspend fun handleTrap(trapId: Short) {
        epc = pc
        isKernelMode = true
        val newAddress = mmu.read(trapId.toUShort())
        pc = newAddress.toUShort()
    }

    private fun handleRti() {
        pc = epc
        this.isKernelMode = false
    }

    suspend fun RegisterType.read(): Short = registers.read(this)
    suspend fun RegisterType.write(a: Short) = registers.write(this, a)
}