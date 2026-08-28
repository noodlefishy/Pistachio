package io.cuttlefish

fun main() {
    val l = Instruction::class.nestedClasses.map { it.objectInstance }.also {
        print(it)
    }
}