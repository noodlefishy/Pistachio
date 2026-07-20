package io.cuttlefish.debug

import io.cuttlefish.components.*

class Debugger(val cpu: Cpu, val memory: MemoryBus) {
    val symbolMap: Map<String, UShort> = mapOf()
    val labelMap: Map<UShort, String> = symbolMap.map { it.value to it.key }.toMap()
    val history = ArrayDeque<String>(50)
    val breakPoints = mutableSetOf<UShort>()
}