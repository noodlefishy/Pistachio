package io.cuttlefish.profiling

import io.cuttlefish.components.Cpu
import kotlin.time.TimeSource

class ExecutionProfiler(
    private val cpu: Cpu,
    symbolMap: Map<String, UShort>
) {
    private val addressToSymbol = Array(65536) { "unmapped" }
    private val hits = mutableMapOf<String, Long>()
    private var totalCycles: Long = 0L
    private val startTime = TimeSource.Monotonic.markNow()

    init {
        val sortedSymbols = symbolMap.entries
            .filter { !it.key.startsWith("_") }
            .sortedBy { it.value }

        for (i in sortedSymbols.indices) {
            val current = sortedSymbols[i]
            val startAddr = current.value.toInt()
            val endAddr = if (i + 1 < sortedSymbols.size) {
                sortedSymbols[i + 1].value.toInt()
            } else {
                startAddr + 4096
            }

            for (addr in startAddr until endAddr.coerceAtMost(65536)) {
                addressToSymbol[addr] = current.key
            }
        }
    }

    fun sample() {
        totalCycles++
        val sym = addressToSymbol[cpu.pc.toInt() and 0xFFFF]
        hits[sym] = (hits[sym] ?: 0L) + 1L
    }

    fun printReport() {
        if (totalCycles == 0L) return

        val duration = startTime.elapsedNow()
        val durationSec = duration.inWholeMilliseconds / 1000.0
        val kips = if (durationSec > 0) (totalCycles / durationSec) / 1000.0 else 0.0

        // Column Width Specifications
        val nameWidth = 32
        val countWidth = 14
        val pctWidth = 8
        val maxBarWidth = 30
        val totalWidth = nameWidth + countWidth + pctWidth + maxBarWidth + 10

        println()
        println("=".repeat(totalWidth))
        println("                        PISTACHIO CPU PROFILER REPORT")
        println("=".repeat(totalWidth))
        println(" Total Cycles: ${String.format("%,d", totalCycles)}  |  Duration: $duration  |  Speed: ${"%.1f".format(kips)} kIPS")
        println("-".repeat(totalWidth))

        val headerName = "Subroutine / Hotspot".padEnd(nameWidth)
        val headerCount = "Cycles".padStart(countWidth)
        val headerPct = "Percent".padStart(pctWidth)
        println(" $headerName  $headerCount   $headerPct   Visual Workload")
        println("-".repeat(totalWidth))

        val grouped = mutableMapOf<String, MutableMap<String, Long>>()

        for ((symbol, count) in hits) {
            if (symbol == "unmapped") continue
            val parent = symbol.substringBefore(".")
            val sub = if (symbol.contains(".")) "." + symbol.substringAfter(".") else "<entry>"

            val subMap = grouped.getOrPut(parent) { mutableMapOf() }
            subMap[sub] = (subMap[sub] ?: 0L) + count
        }

        val sortedParents = grouped.entries.sortedByDescending { it.value.values.sum() }

        var globalTopHotspot = ""
        var globalTopCycles = 0L

        for ((parent, subMap) in sortedParents) {
            val parentTotal = subMap.values.sum()
            val parentPct = (parentTotal.toDouble() / totalCycles.toDouble()) * 100.0
            val barLen = ((parentPct / 100.0) * maxBarWidth).toInt().coerceAtLeast(0)

            // Print Parent Subroutine
            val pName = parent.padEnd(nameWidth)
            val pCountStr = String.format("%,d", parentTotal).padStart(countWidth)
            val pPctStr = "%6.2f%%".format(parentPct).padStart(pctWidth)
            println(" $pName  $pCountStr   $pPctStr   ${"█".repeat(barLen)}")

            // Print Indented Basic Blocks
            if (subMap.size > 1) {
                val sortedSubs = subMap.entries.sortedByDescending { it.value }
                val subList = sortedSubs.toList()
                for (j in subList.indices) {
                    val (subName, subCount) = subList[j]
                    val subPct = (subCount.toDouble() / totalCycles.toDouble()) * 100.0
                    val subBarLen = ((subPct / 100.0) * maxBarWidth).toInt().coerceAtLeast(0)

                    val isLast = (j == subList.size - 1)
                    val branchPrefix = if (isLast) "   └─ " else "   ├─ "

                    // Fixed: 32-character allocation ensures long names don't misalign columns!
                    val labelFormatted = (branchPrefix + subName).padEnd(nameWidth)
                    val sCountStr = String.format("%,d", subCount).padStart(countWidth)
                    val sPctStr = "%6.2f%%".format(subPct).padStart(pctWidth)

                    println(" $labelFormatted  $sCountStr   $sPctStr   ${"█".repeat(subBarLen)}")

                    if (subCount > globalTopCycles) {
                        globalTopCycles = subCount
                        globalTopHotspot = "$parent$subName"
                    }
                }
            }
        }

        println("-".repeat(totalWidth))
        val topPct = "%5.2f%%".format((globalTopCycles.toDouble() / totalCycles.toDouble()) * 100.0)
        println(" Global Hotspot: '$globalTopHotspot' consumed $topPct of all runtime!")
        println("=".repeat(totalWidth))
        println()
    }
}