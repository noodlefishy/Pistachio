package io.cuttlefish.components

import io.cuttlefish.*
import io.cuttlefish.backend.*
import io.cuttlefish.config.*
import io.cuttlefish.devices.ProgrammableInterruptController
import io.cuttlefish.instructions.*

class Cpu(val mmu: MemoryBus) {
    val registers = Registers()
    val alu = Alu()
    var epc: UShort = 0u        // Exception Program Counter
    var pc: UShort = 0u         // Program Counter
    var pbr: UShort = 0x3000u   // Partition Base Register
    var plr: UShort =           // Partition Limit Register
        (MemoryMapRanges.userLandRange.last - MemoryMapRanges.userLandRange.first + 1u).toUShort()
    var isHalted = false
    var isKernelMode = false        // Flag to track CPU privilege level
    var flags: UShort = 1u
    private val backend = Backend
    init {
        mmu.attachCpu(this)
    }
    suspend fun tick() {
        if (isHalted) return

        val pic = mmu.attachedDevices.filterIsInstance<ProgrammableInterruptController>().firstOrNull()
        if (pic != null && (!isKernelMode || (flags.toInt() and 1) != 0)) {
            val dispatched = pic.dispatch(this)
            if (dispatched) return
        }

        if (!isKernelMode) {
            if (pc >= plr) {
                throw SegmentationFault("PC 0x${pc.toString(16)} exceeded partition limit 0x${plr.toString(16)}")
            }
        }

        // 1. FETCH
        val rawInstruction = mmu.read(pc)
        pc++

        // 2. DECODE
        val instruction = backend.decode(rawInstruction.toUShort())

        // Trap Handling
        if (instruction is Instruction.Jalr && instruction.immediate != 0.toShort()) {
            val trapId = instruction.immediate
            @Suppress("UnusedVariable", "unused")
            val trapName = when (trapId.toInt()) { // for the OS
                1 -> "HALT"
                15 -> "RTI"
                else -> "SYSCALL $trapId"
            }

            // Log the Trap to history before returning!

            if (trapId == 1.toShort()) {
                if (GlobalConfig.debug.printRegistersOnHalt) {
                    println("[DEBUG] $registers")
                }
                isHalted = true
                return
            }

            // Trap ID 15: Special RTI/RFE instruction
            if (trapId == 15.toShort()) {
                handleRti()
                return
            }

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
        val newAddress = mmu.read(trapId.toUShort())
        pc = newAddress.toUShort()
    }

    private fun handleRti() {
        pc = epc
        this.isKernelMode = false
        this.flags = (flags.toInt() or 1).toUShort()
    }

    suspend fun RegisterType.read(): Short = registers.read(this)
    suspend fun RegisterType.write(a: Short) = registers.write(this, a)
}