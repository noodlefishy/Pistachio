package io.cuttlefish.devices

class PackingAccelerator : Device {
    override val name: String = "Packing Accelerator"
    override val deviceId: UShort = 3u
    override val memoryUsed: UIntRange = 0xFF60u..0xFF6Cu

    private val vectorA = ShortArray(4) { 0 }
    private val vectorB = ShortArray(4) { 0 }
    private val vectorR = ShortArray(4) { 0 }


    override suspend fun read(address: UShort): Short {
        return when (val addressInt = address.toInt()) {
            in 0xFF60..0xFF63 -> vectorA[addressInt - 0xFF60]
            in 0xFF64..0xFF67 -> vectorB[addressInt - 0xFF64]
            0xFF68 -> 0 // For writing 🙄
            in 0xFF69..0xFF6C -> vectorR[addressInt - 0xFF69]
            else -> 0

        }
    }

    override suspend fun write(address: UShort, value: Short) {
        when (val addressInt = address.toInt()) {
            in 0xFF60..0xFF63 -> vectorA[addressInt - 0xFF60] = value
            in 0xFF64..0xFF67 -> vectorB[addressInt - 0xFF64] = value
            0xFF68 -> packCommand(addressInt)
            in 0xFF69..0xFF6C -> vectorR[addressInt - 0xFF69] = value
        }
    }

    private fun packCommand(command: Int) {
        // Imagine it's parallel, to the VM this is bloody magical
        when (command) {
            1 -> { // PADD: parallel 16-bit addition (4 elements in parallel)
                for (i in 0..3) {
                    vectorR[i] = (vectorA[i] + vectorB[i]).toShort()
                }
            }

            2 -> { // PSUB: parallel 16-bit subtraction
                for (i in 0..3) {
                    vectorR[i] = (vectorA[i] - vectorB[i]).toShort()
                }
            }

            3 -> { // PMUL: parallel 16-bit multiplication
                for (i in 0..3) {
                    vectorR[i] = (vectorA[i] * vectorB[i]).toShort()
                }
            }

            4 -> { // PDOT: 4D vector dot product (A0*B0 + A1*B1 + A2*B2 + A3*B3)
                var sum = 0
                for (i in 0..3) {
                    sum += vectorA[i] * vectorB[i]
                }
                vectorR[0] = sum.toShort()
                vectorR[1] = 0
                vectorR[2] = 0
                vectorR[3] = 0
            }

            5 -> { // PADD8: 8x 8-Bit SWAR addition
                for (i in 0..3) {
                    val aHi = (vectorA[i].toInt() ushr 8) and 0xFF
                    val aLo = vectorA[i].toInt() and 0xFF
                    val bHi = (vectorB[i].toInt() ushr 8) and 0xFF
                    val bLo = vectorB[i].toInt() and 0xFF

                    val resHi = (aHi + bHi) and 0xFF
                    val resLo = (aLo + bLo) and 0xFF

                    vectorR[i] = ((resHi shl 8) or resLo).toShort()
                }
            }
        }
    }

}