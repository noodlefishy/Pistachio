package io.cuttlefish

object MemoryMapRanges { // 64 KB
    val vectorRange: UIntRange = 0x0000u..0x003Fu // 0,0625  kb | 64w
    val kernelRange: UIntRange = 0x0040u..0x2FFFu// 11,9375 kb | 12 224w
    val userLandRange: UIntRange = 0x3000u..0xFDFFu // 51,5    kb | 52 736w
    val mmioRange: UIntRange = 0xFE00u..0xFFFFu // 0,5     kb | 512w
}