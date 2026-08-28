package io.cuttlefish

enum class Mnemonics {
    Add, Addi, Nand, Lui, Lw, Sw, Beq, Jalr,

    // Pseudos
    Movi, Lli, Push, Pop, Call, Ret, Syscall, Halt, Nop, Sub, Subi, Clr, Not, And, Or, Bne, Mov, Blt, Bgt
}