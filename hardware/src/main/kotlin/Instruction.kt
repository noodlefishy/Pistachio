package io.cuttlefish

sealed interface Instruction {
    data class Add(val number1: RegisterType, val number2: RegisterType, val destination: RegisterType) : Instruction
    data class Sub(val number1: RegisterType, val number2: RegisterType, val destination: RegisterType) : Instruction
    data class Mul(val number1: RegisterType, val number2: RegisterType, val destination: RegisterType) : Instruction
    data class Div(val number1: RegisterType, val number2: RegisterType, val destination: RegisterType) : Instruction
    data class Lit(val destination: RegisterType, val value: Short) : Instruction
    data class Syscall(val id: RegisterType, val arg1: RegisterType, val arg2: RegisterType) : Instruction
    data class Printr(val code: RegisterType): Instruction
    data object Halt : Instruction
}