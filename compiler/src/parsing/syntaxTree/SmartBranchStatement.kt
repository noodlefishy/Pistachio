package io.cuttlefish.parsing.syntaxTree

import io.cuttlefish.Instruction
import io.cuttlefish.RegisterType
import io.cuttlefish.linking.RelocationTable
import io.cuttlefish.linking.RelocationType

class SmartBranchStatement(
    val rA: RegisterType,
    val rB: RegisterType,
    val target: Argument,
    line: Int,
    col: Int
) : Statement(line, col) {

    var isLong = false

    // An unconditional jump (beq r0 r0) only needs 3 words; conditional needs 5
    override val size: Int
        get() = if (!isLong) {
            1
        } else {
            if (rA == RegisterType.R0 && rB == RegisterType.R0) 3 else 5
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
        // SHORT BRANCH (1 Word)
        // -------------------------------------------------------------
        if (!isLong) {
            val offset = resolve(target, context, address, RelocationType.REL_7)
            return listOf(Instruction.Beq(rA, rB, offset))
        }

        // -------------------------------------------------------------
        // LONG TRAMPOLINE: Unconditional Jump (beq r0 r0 target) -> 3 Words
        // -------------------------------------------------------------
        if (rA == RegisterType.R0 && rB == RegisterType.R0) {
            context.relocations.add(RelocationTable(address.toUShort(), scopedName, RelocationType.ABS_LUI))
            context.relocations.add(
                RelocationTable(
                    (address + 1).toShort().toUShort(),
                    scopedName,
                    RelocationType.ABS_LLI
                )
            )

            return listOf(
                Instruction.Lui(RegisterType.R7, 0),
                Instruction.Addi(RegisterType.R7, RegisterType.R7, 0),
                Instruction.Jalr(RegisterType.R0, RegisterType.R7, 0)
            )
        }

        // -------------------------------------------------------------
        // LONG TRAMPOLINE: Conditional Jump (beq rA rB target) -> 5 Words
        // -------------------------------------------------------------
        val luiAddr = (address + 2).toShort()
        val lliAddr = (address + 3).toShort()

        context.relocations.add(RelocationTable(luiAddr.toUShort(), scopedName, RelocationType.ABS_LUI))
        context.relocations.add(RelocationTable(lliAddr.toUShort(), scopedName, RelocationType.ABS_LLI))

        return listOf(
            Instruction.Beq(rA, rB, 1),                           // Skip the next line if condition met
            Instruction.Beq(RegisterType.R0, RegisterType.R0, 3), // Skip over the long jump if condition failed
            Instruction.Lui(RegisterType.R7, 0),                  // Patched by Linker
            Instruction.Addi(RegisterType.R7, RegisterType.R7, 0),// Patched by Linker
            Instruction.Jalr(RegisterType.R0, RegisterType.R7, 0) // Fire long jump!
        )
    }
}