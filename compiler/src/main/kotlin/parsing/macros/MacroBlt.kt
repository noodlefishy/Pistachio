package io.cuttlefish.parsing.macros

import io.cuttlefish.*
import io.cuttlefish.linking.*
import io.cuttlefish.parsing.syntaxTree.*

// --- BLT (Branch if Less Than: rA < rB) ---

class MacroBlt(
    val rA: RegisterType, val rB: RegisterType, val target: Argument, line: Int, col: Int
) : Statement(line, col) {
    override val size = 13

    override fun generate(context: ParserContext, address: Short): List<Instruction> {
        val tempReg = listOf(
            RegisterType.R1, RegisterType.R2, RegisterType.R3,
            RegisterType.R4, RegisterType.R5
        ).first { it != rA && it != rB }

        // The unconditional branch instruction is located at (address + 12)
        val targetOffset = resolve(target, context, (address + 12).toShort(), RelocationType.REL_7)

        return listOf(
            // Save tempReg to Stack (2 words)
            Instruction.Sw(tempReg, RegisterType.R6, 0),
            Instruction.Addi(RegisterType.R6, RegisterType.R6, 1),

            // Compute tempReg = rA - rB (3 words)
            Instruction.Nand(tempReg, rB, rB),
            Instruction.Add(tempReg, tempReg, rA),
            Instruction.Addi(tempReg, tempReg, 1),

            // Isolate Sign Bit (0x8000) into tempReg (3 words)
            Instruction.Lui(RegisterType.R7, 512),                          // 512 << 6 = 0x8000 ($SBIT)
            Instruction.Nand(tempReg, tempReg, RegisterType.R7),
            Instruction.Nand(tempReg, tempReg, tempReg),

            // Copy result to R7 and restore tempReg (3 words)
            Instruction.Add(RegisterType.R7, tempReg, RegisterType.R0),     // mov r7, tempReg
            Instruction.Addi(RegisterType.R6, RegisterType.R6, -1),         // pop tempReg
            Instruction.Lw(tempReg, RegisterType.R6, 0),

            // Branch if r7 is non-zero (2 words)
            Instruction.Beq(RegisterType.R7, RegisterType.R0, 1),           // Skip jump if result is 0 (rA >= rB)
            Instruction.Beq(RegisterType.R0, RegisterType.R0, targetOffset) // Unconditional jump to target
        )
    }
}