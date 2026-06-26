package io.cuttlefish

import kotlin.experimental.*

fun main() {
    val imm: Short = 16_123
    val lower6Mask: Short = 0x3F
    val lowerPart = (imm and lower6Mask)
    val upperPart = (imm.toInt() shr 6).toShort()
    println("Lower = $lowerPart, Upper = $upperPart")
    println((upperPart.toInt() shl 6).toShort() + lowerPart)


}