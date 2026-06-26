package io.cuttlefish

sealed interface Instruction {
    data class Add(val register1: RegisterType, val register2: RegisterType, val register3: RegisterType): Instruction
    data class Addi(val register1: RegisterType, val register2: RegisterType, val immediate: Short): Instruction
    data class Nand(val register1: RegisterType, val register2: RegisterType, val register3: RegisterType): Instruction
    data class Lui(val register1: RegisterType, val immediate: Short): Instruction
    data class Lw(val register1: RegisterType, val register2: RegisterType, val immediate: Short): Instruction
    data class Sw(val register1: RegisterType, val register2: RegisterType, val immediate: Short): Instruction
    data class Beq(val register1: RegisterType, val register2: RegisterType, val immediate: Short): Instruction
    data class Jalr(val register1: RegisterType, val register2: RegisterType): Instruction
}