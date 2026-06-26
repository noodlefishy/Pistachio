package io.cuttlefish

sealed interface Instruction {
    data class Add(val number1: RegisterType, val number2: RegisterType, val destination: RegisterType) : Instruction
    data class Sub(val number1: RegisterType, val number2: RegisterType, val destination: RegisterType) : Instruction
    data class Mul(val number1: RegisterType, val number2: RegisterType, val destination: RegisterType) : Instruction
    data class Div(val number1: RegisterType, val number2: RegisterType, val destination: RegisterType) : Instruction

    data class Lit(val dest: RegisterType, val value: Long) : Instruction
    data class Syscall(val id: RegisterType, val arg1: RegisterType, val arg2: RegisterType) : Instruction
    data object Halt : Instruction
}