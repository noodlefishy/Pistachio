package io.cuttlefish.cli

import io.cuttlefish.backend.Backend
import io.cuttlefish.linking.Linker
import io.cuttlefish.linking.LinkerException
import io.cuttlefish.linking.ObjectExcreter
import io.cuttlefish.linking.RelocationType
import io.cuttlefish.parsing.Parser
import java.io.File

data class BinaryImage(
	val baseAddress: UShort,
	val words: List<UShort>
)

data class LinkedProgram(
	val words: List<UShort>,
	val symbolMap: Map<String, UShort>
)

fun readBinaryImage(file: File): BinaryImage {
	val lines = file.readLines().filter { it.isNotBlank() }
	if (lines.isEmpty()) throw IllegalArgumentException("Binary file '${file.name}' is empty")

	val hasHeader = lines[0].startsWith("@")
	val baseAddr = if (hasHeader) lines[0].drop(1).trim().toUShort() else 0u
	val words = (if (hasHeader) lines.drop(1) else lines).map { it.trim().toUShort() }
	return BinaryImage(baseAddr, words)
}

fun linkUserSources(inputPaths: List<String>, baseAddress: UShort = 0u): LinkedProgram {
	val expandedPaths = expandPaths(inputPaths)
	val objects = expandedPaths.map { path -> ObjectExcreter(getFileOrThrow(path)).generate() }
	val linker = Linker(*objects.toTypedArray(), baseAddressUnsedRightNow = baseAddress)
	val symbolMap = linker.passOne()
	val machineCode = linker.passTwo(symbolMap).toList()
	return LinkedProgram(machineCode, symbolMap)
}

fun assembleAndLinkKernel(kernelFile: File): LinkedProgram {
	val parser = Parser(kernelFile, 0.toShort())
	val instructions = parser.decode()
	val words = Backend.encode(instructions).toMutableList()

	for ((offset1, name, type) in parser.relocations) {
		val targetAddr = parser.symbolTable[name]?.toUShort()
			?: throw LinkerException(kernelFile.absolutePath, name, "Unresolved symbol in kernel")
		val offset = offset1.toInt()
		val currentWord = words[offset]

		val patched = when (type) {
			RelocationType.ABS_16 -> targetAddr
			RelocationType.ABS_LUI -> {
				val top10 = ((targetAddr.toInt() ushr 6) and 0x3FF).toUShort()
				currentWord or top10
			}
			RelocationType.ABS_LLI -> {
				val bottom6 = (targetAddr.toInt() and 0x3F).toUShort()
				currentWord or bottom6
			}
			RelocationType.REL_7 -> {
				val offsetVal = targetAddr.toInt() - (offset + 1)
				if (offsetVal !in -64..63) {
					throw IllegalStateException("Kernel branch target '$name' out of range: $offsetVal")
				}
				(currentWord.toInt() or (offsetVal and 0x7F)).toUShort()
			}
		}
		words[offset] = patched
	}

	val symbolMap = parser.symbolTable.mapValues { it.value.toUShort() }
	return LinkedProgram(words, symbolMap)
}