import io.cuttlefish.Instruction
import io.cuttlefish.RegisterType
import io.cuttlefish.linking.RelocationType
import io.cuttlefish.parsing.syntaxTree.Argument
import io.cuttlefish.parsing.syntaxTree.ParserContext
import io.cuttlefish.parsing.syntaxTree.Statement


class MacroBlt(
    val rA: RegisterType, val rB: RegisterType, val target: Argument, line: Int, col: Int
) : Statement(line, col) {
    override val size = 18 // it was 12, but to achieve 0 side effects it had to be 18

    override fun generate(context: ParserContext, address: Short): List<Instruction> {
        // dynamically picks 2 temp registers that are NEITHER rA NOR rB
        val tempRegs = listOf(
            RegisterType.R1, RegisterType.R2, RegisterType.R3,
            RegisterType.R4, RegisterType.R5
        ).filter { it != rA && it != rB }

        val t1 = tempRegs[0]
        val t2 = tempRegs[1]

        // unconditional branch is located at (address + 15)
        val targetOffset = resolve(target, context, (address + 15).toShort(), RelocationType.REL_7)

        return listOf(
            // Push t1 & t2 to Stack (4 words) 
            Instruction.Sw(t1, RegisterType.R6, 0),
            Instruction.Addi(RegisterType.R6, RegisterType.R6, 1),
            Instruction.Sw(t2, RegisterType.R6, 0),
            Instruction.Addi(RegisterType.R6, RegisterType.R6, 1),

            // Compute t1 = rA - rB (3 words) 
            Instruction.Nand(t1, rB, rB),
            Instruction.Add(t1, t1, rA),
            Instruction.Addi(t1, t1, 1),

            // Isolate Sign Bit (0x8000) into t1 (3 words) 
            Instruction.Lui(t2, 512),               // t2 = 0x8000 ($SBIT)
            Instruction.Nand(t1, t1, t2),
            Instruction.Nand(t1, t1, t1),           // t1 = (rA < rB) ? 0x8000 : 0

            // Restore t2 from Stack (2 words) 
            Instruction.Addi(RegisterType.R6, RegisterType.R6, -1),
            Instruction.Lw(t2, RegisterType.R6, 0), // t2 restored!

            // Evaluate branch (1 word) 
            // If t1 == 0 (rA >= rB), skip 3 instructions directly to the NOT-TAKEN path!
            Instruction.Beq(t1, RegisterType.R0, 3),

            //  TAKEN PATH (rA < rB) (3 words) 
            Instruction.Addi(RegisterType.R6, RegisterType.R6, -1),
            Instruction.Lw(t1, RegisterType.R6, 0), // t1 restored!
            Instruction.Beq(RegisterType.R0, RegisterType.R0, targetOffset), // Jump to target!

            //  NOT TAKEN PATH (rA >= rB) (2 words) 
            Instruction.Addi(RegisterType.R6, RegisterType.R6, -1),
            Instruction.Lw(t1, RegisterType.R6, 0)  // t1 restored!
        )
    }
}
