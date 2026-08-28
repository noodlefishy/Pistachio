package io.cuttlefish

object SmartDisassemblerRegistry {

    val patterns = listOf<MacroPattern>(

        // ==========================================
        // 5-WORD PATTERNS
        // ==========================================

        // SUB (In-place: sub rA, rA, rC)
        object : MacroPattern {
            override val size = 5
            override fun match(
                window: List<Instruction>, address: UShort, symbolMap: Map<UShort, String>
            ): DisassembledInstruction? {
                val i1 = window.getOrNull(0) as? Instruction.Nand ?: return null
                val i2 = window.getOrNull(1) as? Instruction.Addi ?: return null
                val i3 = window.getOrNull(2) as? Instruction.Add ?: return null
                val i4 = window.getOrNull(3) as? Instruction.Addi ?: return null
                val i5 = window.getOrNull(4) as? Instruction.Nand ?: return null

                if (i1.register1 == i1.register2 && i1.register2 == i1.register3 && i2.register1 == i1.register1 && i2.register2 == i1.register1 && i2.immediate == 1.toShort() && i3.register3 == i1.register1 && i4.register1 == i1.register1 && i4.register2 == i1.register1 && i4.immediate == (-1).toShort() && i5.register1 == i1.register1 && i5.register2 == i1.register1 && i5.register3 == i1.register1) {
                    return DisassembledInstruction(
                        "sub ${i3.register1}, ${i3.register2}, ${i1.register1}", 5u, emptyList()
                    )
                }
                return null
            }
        },


        // OR (Distinct registers: or rA, rB, rC)
        object : MacroPattern {
            override val size = 4
            override fun match(
                window: List<Instruction>, address: UShort, symbolMap: Map<UShort, String>
            ): DisassembledInstruction? {
                val i1 = window.getOrNull(0) as? Instruction.Nand ?: return null
                val i2 = window.getOrNull(1) as? Instruction.Nand ?: return null
                val i3 = window.getOrNull(2) as? Instruction.Nand ?: return null
                val i4 = window.getOrNull(3) as? Instruction.Nand ?: return null

                if (i1.register1 == i1.register2 && i1.register1 == i1.register3 && // ~rB
                    i2.register2 == i2.register3 && // ~rC
                    i3.register1 == i2.register1 && i3.register2 == i3.register1 && i3.register3 == i1.register1 && // NAND(~rC, ~rB)
                    i4.register1 == i1.register1 && i4.register2 == i1.register1 && i4.register3 == i1.register1
                ) { // restore ~rB
                    return DisassembledInstruction(
                        "or ${i3.register1}, ${i1.register1}, ${i2.register2}", 4u, emptyList()
                    )
                }
                return null
            }
        },


        // SUB (Distinct registers: sub rA, rB, rC)
        object : MacroPattern {
            override val size = 3
            override fun match(
                window: List<Instruction>, address: UShort, symbolMap: Map<UShort, String>
            ): DisassembledInstruction? {
                val i1 = window.getOrNull(0) as? Instruction.Nand ?: return null
                val i2 = window.getOrNull(1) as? Instruction.Add ?: return null
                val i3 = window.getOrNull(2) as? Instruction.Addi ?: return null

                if (i1.register2 == i1.register3 && i2.register1 == i1.register1 && i2.register2 == i1.register1 && i3.register1 == i1.register1 && i3.register2 == i1.register1 && i3.immediate == 1.toShort()) {
                    return DisassembledInstruction(
                        "sub ${i1.register1}, ${i2.register3}, ${i1.register2}", 3u, emptyList()
                    )
                }
                return null
            }
        },

        // CALL (call target)
        object : MacroPattern {
            override val size = 3
            override fun match(
                window: List<Instruction>, address: UShort, symbolMap: Map<UShort, String>
            ): DisassembledInstruction? {
                val i1 = window.getOrNull(0) as? Instruction.Lui ?: return null
                val i2 = window.getOrNull(1) as? Instruction.Addi ?: return null
                val i3 = window.getOrNull(2) as? Instruction.Jalr ?: return null

                if (i1.register1 == RegisterType.R7 && i2.register1 == RegisterType.R7 && i2.register2 == RegisterType.R7 && i3.register1 == RegisterType.R7 && i3.register2 == RegisterType.R7 && i3.immediate == 0.toShort()) {

                    val targetAddr = ((i1.immediate.toInt() shl 6) or (i2.immediate.toInt() and 0x3F)).toUShort()
                    val label = symbolMap[targetAddr] ?: "0x${targetAddr.toString(16).uppercase()}"
                    return DisassembledInstruction("call $label", 3u, emptyList())
                }
                return null
            }
        },

        // ==========================================
        // 2-WORD PATTERNS
        // ==========================================

        // MOVI (movi rA, #imm/label)
        object : MacroPattern {
            override val size = 2
            override fun match(
                window: List<Instruction>, address: UShort, symbolMap: Map<UShort, String>
            ): DisassembledInstruction? {
                val i1 = window.getOrNull(0) as? Instruction.Lui ?: return null
                val i2 = window.getOrNull(1) as? Instruction.Addi ?: return null

                if (i1.register1 == i2.register1 && i2.register1 == i2.register2) {
                    val val16 = ((i1.immediate.toInt() shl 6) or (i2.immediate.toInt() and 0x3F)).toUShort()
                    val targetStr = symbolMap[val16] ?: "0x${val16.toString(16).uppercase()} (#${val16.toShort()})"
                    return DisassembledInstruction("movi ${i1.register1}, $targetStr", 2u, emptyList())
                }
                return null
            }
        },

        // PUSH (push rA)
        object : MacroPattern {
            override val size = 2
            override fun match(
                window: List<Instruction>, address: UShort, symbolMap: Map<UShort, String>
            ): DisassembledInstruction? {
                val i1 = window.getOrNull(0) as? Instruction.Sw ?: return null
                val i2 = window.getOrNull(1) as? Instruction.Addi ?: return null

                if (i1.register2 == RegisterType.R6 && i1.immediate == 0.toShort() && i2.register1 == RegisterType.R6 && i2.register2 == RegisterType.R6 && i2.immediate == 1.toShort()) {
                    return DisassembledInstruction("push ${i1.register1}", 2u, emptyList())
                }
                return null
            }
        },

        // POP (pop rA)
        object : MacroPattern {
            override val size = 2
            override fun match(
                window: List<Instruction>, address: UShort, symbolMap: Map<UShort, String>
            ): DisassembledInstruction? {
                val i1 = window.getOrNull(0) as? Instruction.Addi ?: return null
                val i2 = window.getOrNull(1) as? Instruction.Lw ?: return null

                if (i1.register1 == RegisterType.R6 && i1.register2 == RegisterType.R6 && i1.immediate == (-1).toShort() && i2.register2 == RegisterType.R6 && i2.immediate == 0.toShort()) {
                    return DisassembledInstruction("pop ${i2.register1}", 2u, emptyList())
                }
                return null
            }
        },

        // BNE (bne rA, rB, target)
        object : MacroPattern {
            override val size = 2
            override fun match(
                window: List<Instruction>, address: UShort, symbolMap: Map<UShort, String>
            ): DisassembledInstruction? {
                val i1 = window.getOrNull(0) as? Instruction.Beq ?: return null
                val i2 = window.getOrNull(1) as? Instruction.Beq ?: return null

                if (i1.immediate == 1.toShort() && i2.register1 == RegisterType.R0 && i2.register2 == RegisterType.R0) {
                    val targetAddr = ((address.toInt() + 2) + i2.immediate.toInt()).toUShort()
                    val label = symbolMap[targetAddr] ?: "0x${targetAddr.toString(16).uppercase()}"
                    return DisassembledInstruction("bne ${i1.register1}, ${i1.register2}, $label", 2u, emptyList())
                }
                return null
            }
        },

        // AND (and rA, rB, rC)
        object : MacroPattern {
            override val size = 2
            override fun match(
                window: List<Instruction>, address: UShort, symbolMap: Map<UShort, String>
            ): DisassembledInstruction? {
                val i1 = window.getOrNull(0) as? Instruction.Nand ?: return null
                val i2 = window.getOrNull(1) as? Instruction.Nand ?: return null

                if (i2.register1 == i1.register1 && i2.register2 == i1.register1 && i2.register3 == i1.register1) {
                    return DisassembledInstruction(
                        "and ${i1.register1}, ${i1.register2}, ${i1.register3}", 2u, emptyList()
                    )
                }
                return null
            }
        },


        object : MacroPattern {
            override val size = 1
            override fun match(
                window: List<Instruction>, address: UShort, symbolMap: Map<UShort, String>
            ): DisassembledInstruction? {
                val i1 = window.getOrNull(0) ?: return null

                when (i1) {
                    is Instruction.Add -> {
                        if (i1.register1 == RegisterType.R0 && i1.register2 == RegisterType.R0 && i1.register3 == RegisterType.R0) return DisassembledInstruction(
                            "nop", 1u, emptyList()
                        )

                        if (i1.register2 == RegisterType.R0 && i1.register3 == RegisterType.R0) return DisassembledInstruction(
                            "clr ${i1.register1}", 1u, emptyList()
                        )

                        if (i1.register3 == RegisterType.R0 && i1.register1 != RegisterType.R0) return DisassembledInstruction(
                            "mov ${i1.register1}, ${i1.register2}", 1u, emptyList()
                        )
                    }

                    is Instruction.Jalr -> {
                        if (i1.register1 == RegisterType.R0 && i1.register2 == RegisterType.R0 && i1.immediate == 1.toShort()) return DisassembledInstruction(
                            "halt", 1u, emptyList()
                        )

                        if (i1.register1 == RegisterType.R0 && i1.register2 == RegisterType.R7 && i1.immediate == 0.toShort()) return DisassembledInstruction(
                            "ret", 1u, emptyList()
                        )

                        if (i1.register1 == RegisterType.R0 && i1.register2 == RegisterType.R0 && i1.immediate > 1.toShort()) return DisassembledInstruction(
                            "syscall ${i1.immediate}", 1u, emptyList()
                        )
                    }

                    is Instruction.Nand -> {
                        if (i1.register2 == i1.register3) return DisassembledInstruction(
                            "not ${i1.register1}, ${i1.register2}", 1u, emptyList()
                        )
                    }

                    is Instruction.Addi -> {
                        // Subi translation (if addi has a negative immediate, show it as subi)
                        if (i1.immediate < 0) {
                            return DisassembledInstruction(
                                "subi ${i1.register1}, ${i1.register2}, #${-i1.immediate}", 1u, emptyList()
                            )
                        }
                    }

                    else -> return null
                }
                return null
            }
        })
}