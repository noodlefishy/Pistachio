package io.cuttlefish.instructions

import io.cuttlefish.*
import io.cuttlefish.components.*

suspend fun Cpu.handlerAdd(instruction: Instruction.Add) {
    val number1 = registers.read(instruction.number1)
    val number2 = registers.read(instruction.number2)
    val result = alu.add(number1, number2)
    registers.write(instruction.destination, result)
}

suspend fun Cpu.handlerSub(instruction: Instruction.Sub) {
    val number1 = registers.read(instruction.number1)
    val number2 = registers.read(instruction.number2)
    val result = alu.sub(number1, number2)
    registers.write(instruction.destination, result)
}

suspend fun Cpu.handlerMul(instruction: Instruction.Mul) {
    val number1 = registers.read(instruction.number1)
    val number2 = registers.read(instruction.number2)
    val result = alu.mul(number1, number2)
    registers.write(instruction.destination, result)
}

suspend fun Cpu.handlerDiv(instruction: Instruction.Div) {
    val number1 = registers.read(instruction.number1)
    val number2 = registers.read(instruction.number2)
    val result = alu.div(number1, number2)
    registers.write(instruction.destination, result)
}