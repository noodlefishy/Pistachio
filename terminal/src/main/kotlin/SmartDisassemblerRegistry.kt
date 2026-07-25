package io.cuttlefish

object SmartDisassemblerRegistry {

    val patterns = listOf<MacroPattern>(

        object : MacroPattern {
            override val size: Int = 3

            override fun match(
                window: List<Instruction>, address: UShort, symbolMap: Map<UShort, String>
            ): DisassembledInstruction? {
                val i1 = window.getOrNull(0) as? Instruction.Lui ?: return null
                val i2 = window.getOrNull(1) as? Instruction.Addi ?: return null
                val i3 = window.getOrNull(2) as? Instruction.Jalr ?: return null


                if (i1.register1 == RegisterType.R7 && i2.register1 == RegisterType.R7 && i2.register2 == RegisterType.R7 && i3.register2 == RegisterType.R7 && i3.immediate == 0.toShort()) {

                    val targetAddr = ((i1.immediate.toInt() shl 6) or (i2.immediate.toInt() and 0x3F)).toUShort()
                    val label = symbolMap[targetAddr] ?: "0x${targetAddr.toString(16).uppercase()}"
                    return DisassembledInstruction("call $label", 3u, emptyList()) // Raw words injected later
                }
                return null
            }
        },
        object : MacroPattern {
            override val size = 2
            override fun match(window: List<Instruction>, address: UShort, symbolMap: Map<UShort, String>): DisassembledInstruction? {
                val i1 = window.getOrNull(0) as? Instruction.Sw ?: return null
                val i2 = window.getOrNull(1) as? Instruction.Addi ?: return null

                // PUSH Pattern
                if (i1.register2 == RegisterType.R6 && i1.immediate == 0.toShort() && i2.register1 == RegisterType.R6 && i2.register2 == RegisterType.R6 && i2.immediate == 1.toShort()) {
                    return DisassembledInstruction("push ${i1.register1}", 2u, emptyList())
                }
                return null
            }
        },

    )
}
