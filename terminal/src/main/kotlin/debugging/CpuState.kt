package io.cuttlefish.debugging

import io.cuttlefish.*
@Deprecated("", level = DeprecationLevel.ERROR)
data class CpuState(
    val pcCurrent: UShort,
    val memoryCurrent: Short,
    val instructionCurrent: Instruction,
    val registersCurrent: Map<RegisterType, Short>,
    //
    val pcNext: UShort,
    val memoryNext: Short,
    val instructionNext: Instruction,

)