package io.cuttlefish.parsing.syntaxTree

import io.cuttlefish.Instruction
import io.cuttlefish.RegisterType
import io.cuttlefish.linking.RelocationTable
import io.cuttlefish.linking.RelocationType

class SmartBranchStatement(
    val op: String, // "beq" or "bne"
    val rA: RegisterType,
    val rB: RegisterType,
    val target: Argument,
    line: Int,
    col: Int
) : Statement(line, col) {

    var isLong = false

    override val size: Int
        get() = if (!isLong) {
            if (op == "bne") 2 else 1
        } else {
            when (op) {
                "beq" if rA == RegisterType.R0 && rB == RegisterType.R0 -> {
                    3 // Unconditional jump (beq r0 r0)
                }
                "bne" -> {
                    4 // BNE long trampoline
                }
                else -> {
                    5 // BEQ conditional long trampoline
                }
            }
        }

    fun getScopedTargetName(): String {
        return when (target) {
            is SymArg -> resolveScopedName(target.name)
            is ImmArg -> ""
        }
    }

    override fun generate(context: ParserContext, address: Short): List<Instruction> {
        val scopedName = getScopedTargetName()

        // -------------------------------------------------------------
        // SHORT BRANCH (Target is within [-64, 63])
        // -------------------------------------------------------------
        if (!isLong) {
            if (op == "beq") {
                val offset = resolve(target, context, address, RelocationType.REL_7)
                return listOf(Instruction.Beq(rA, rB, offset))
            } else {
                // bne short: beq rA rB 1; beq r0 r0 offset
                val skipInstruction = Instruction.Beq(rA, rB, 1)
                val targetOffset = resolve(target, context, (address + 1).toShort(), RelocationType.REL_7)
                val jumpInstruction = Instruction.Beq(RegisterType.R0, RegisterType.R0, targetOffset)
                return listOf(skipInstruction, jumpInstruction)
            }
        }

        // -------------------------------------------------------------
        // LONG TRAMPOLINE: Unconditional Jump (beq r0 r0 target) -> 3 Words
        // -------------------------------------------------------------
        if (op == "beq" && rA == RegisterType.R0 && rB == RegisterType.R0) {
            context.relocations.add(RelocationTable(address.toUShort(), scopedName, RelocationType.ABS_LUI))
            context.relocations.add(
                RelocationTable((address + 1).toShort().toUShort(), scopedName, RelocationType.ABS_LLI)
            )

            return listOf(
                Instruction.Lui(RegisterType.R7, 0),
                Instruction.Addi(RegisterType.R7, RegisterType.R7, 0),
                Instruction.Jalr(RegisterType.R0, RegisterType.R7, 0)
            )
        }

        // -------------------------------------------------------------
        // LONG TRAMPOLINE: BNE (bne rA rB target) -> 4 Words
        // -------------------------------------------------------------
        if (op == "bne") {
            val luiAddr = (address + 1).toShort()
            val lliAddr = (address + 2).toShort()

            context.relocations.add(RelocationTable(luiAddr.toUShort(), scopedName, RelocationType.ABS_LUI))
            context.relocations.add(RelocationTable(lliAddr.toUShort(), scopedName, RelocationType.ABS_LLI))

            return listOf(
                Instruction.Beq(rA, rB, 3),                            // If equal, skip over the long jump!
                Instruction.Lui(RegisterType.R7, 0),                   // Patched by Linker
                Instruction.Addi(RegisterType.R7, RegisterType.R7, 0), // Patched by Linker
                Instruction.Jalr(RegisterType.R0, RegisterType.R7, 0)  // Long jump!
            )
        }

        // -------------------------------------------------------------
        // LONG TRAMPOLINE: BEQ Conditional (beq rA rB target) -> 5 Words
        // -------------------------------------------------------------
        val luiAddr = (address + 2).toShort()
        val lliAddr = (address + 3).toShort()

        context.relocations.add(RelocationTable(luiAddr.toUShort(), scopedName, RelocationType.ABS_LUI))
        context.relocations.add(RelocationTable(lliAddr.toUShort(), scopedName, RelocationType.ABS_LLI))

        return listOf(
            Instruction.Beq(rA, rB, 1),                           // Skip next line if condition met
            Instruction.Beq(RegisterType.R0, RegisterType.R0, 3), // Skip long jump if condition failed
            Instruction.Lui(RegisterType.R7, 0),                  // Patched by Linker
            Instruction.Addi(RegisterType.R7, RegisterType.R7, 0),// Patched by Linker
            Instruction.Jalr(RegisterType.R0, RegisterType.R7, 0) // Fire long jump!
        )
    }
}