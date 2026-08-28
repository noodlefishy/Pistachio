package io.cuttlefish

sealed interface Instruction {
    data class Add(val register1: RegisterType, val register2: RegisterType, val register3: RegisterType) : Instruction {
        override fun toString(): String = "add $register1 $register2 $register3"
    }

    data class Addi(val register1: RegisterType, val register2: RegisterType, val immediate: Short) : Instruction {
        override fun toString(): String = "addi $register1 $register2 $immediate"
    }

    data class Nand(val register1: RegisterType, val register2: RegisterType, val register3: RegisterType) : Instruction {
        override fun toString(): String = "nand $register1 $register2 $register3"
    }

    data class Lui(val register1: RegisterType, val immediate: Short) : Instruction {
        override fun toString(): String = "lui $register1 $immediate"
    }

    data class Lw(val register1: RegisterType, val register2: RegisterType, val immediate: Short) : Instruction {
        override fun toString(): String = "lw $register1 $register2 $immediate"
    }

    data class Sw(val register1: RegisterType, val register2: RegisterType, val immediate: Short) : Instruction {
        override fun toString(): String = "sw $register1 $register2 $immediate"
    }

    data class Beq(val register1: RegisterType, val register2: RegisterType, val immediate: Short) : Instruction {
        override fun toString(): String = "beq $register1 $register2 $immediate"
    }

    data class Jalr(val register1: RegisterType, val register2: RegisterType, val immediate: Short) : Instruction {
        override fun toString(): String = "jalr $register1 $register2 $immediate"
    }

    data class DataWord(val value: Short) : Instruction {
        override fun toString(): String = ".fill $value"
    }
}