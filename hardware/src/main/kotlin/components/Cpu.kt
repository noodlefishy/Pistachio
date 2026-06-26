package io.cuttlefish.components

import io.cuttlefish.*
import io.cuttlefish.backend.*
import io.cuttlefish.instructions.*

class Cpu(val mmu: MemoryBus) {
    val registers = Registers()
    val alu = Alu()
    var epc: Short = 0 // Exception Program Counter
    var pc: Short = 0 // Program Counter
    var isHalted = false
    var isKernelMode = true        // Flag to track CPU privilege level
    private val backend = Backend()

    // tick() no longer takes an Instruction argument!
    suspend fun tick() {
        if (isHalted) return

        // 1. FETCH
        val rawInstruction = mmu.read(pc)
        pc++

        // 2. DECODE
        val instruction = backend.decode(rawInstruction.toUShort())
        val ku = if (isKernelMode) 'k' else 'u'
        println("| $ku | $pc | STATE = $instruction")


        if (instruction is Instruction.Jalr && instruction.immediate != 0.toShort()) {
            val trapId = instruction.immediate
            if (trapId == 1.toShort()) {
                println("[DEBUG] $registers")
                isHalted = true
                return
            }

            // Trap ID 15: Special RTI/RFE instruction (explained in Phase 3!)
            if (trapId == 15.toShort()) {
                handleRti()
                return
            }

            // Otherwise, it's a standard system call (Trap IDs 2 through 14)
            handleTrap(trapId)
            return
        }

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
    }

    private suspend fun handleTrap(trapId: Short) {
        epc = pc
        isKernelMode = true
        val newAddress = mmu.read(trapId)
        pc = newAddress
    }

    private fun handleRti() {
        pc = epc
        this.isKernelMode = false
    }

    suspend fun RegisterType.read(): Short = registers.read(this)
    suspend fun RegisterType.write(a: Short) = registers.write(this, a)
}