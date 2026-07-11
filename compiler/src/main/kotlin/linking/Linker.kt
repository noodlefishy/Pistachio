import io.cuttlefish.*
import io.cuttlefish.backend.*
import io.cuttlefish.components.*
import io.cuttlefish.linking.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.*

class Linker(vararg objectFiles: ObjectFile, baseAddress: UShort = 0x3000u) {
    private val objects = objectFiles.toList()
    private var currentAddress = baseAddress
    private val startAddress = baseAddress

    private val fileBaseAddresses = mutableMapOf<File, UShort>() // Future me. `passTwo` will lose context

    init {
        checkDuplicates()
    }

    val groupedByFile = objects.groupBy { File(it.header.fileName) }.map { it.key to it.value.first() }.toMap()

    private fun assignLayout(): Map<File, UShort> {
        currentAddress = (currentAddress + 3u).toUShort() // Reserve 3 words space for the bootstrap instructions
        for (file in groupedByFile) {
            fileBaseAddresses[file.key] = currentAddress
            currentAddress = (currentAddress + file.value.payload.size.toUShort()).toUShort()
        }
        return fileBaseAddresses
    }

    private fun generateSymbolTable(assignedLayout: Map<File, UShort>): Map<String, UShort> {
        val global: MutableMap<String, UShort> = mutableMapOf()
        for ((file: File, objectFile: ObjectFile) in groupedByFile) {
            for (symbol in objectFile.symbolTables) {
                // Ignore imports so we don't accidentally overwrite the true address with the importer's layout address
                if (symbol.type == SymbolType.Import) continue
                global[symbol.name] = (assignedLayout[file]!! + symbol.offset).toUShort()
            }
        }
        return global
    }

    fun passOne(): Map<String, UShort> {
        val layout = assignLayout()
        val absolute = generateSymbolTable(layout)
        return absolute
    }

    private fun checkDuplicates() {
        val totalObjectsWithoutFiles = objects.flatMap { it.symbolTables }

        val mainExports = totalObjectsWithoutFiles.filter { (it.name == "main") && it.type == SymbolType.Export }
        if (mainExports.size != 1) {
            println(totalObjectsWithoutFiles)
            throw IllegalStateException($$$$$$$$$$$"Incorrect main function configuration ${}")
            // multi dollar interpolation is just an ahh feature
            // This was picked over union types 💔
        }

        val exportedSymbols = mutableSetOf<String>()
        for (symbol in totalObjectsWithoutFiles) {
            if (symbol.type == SymbolType.Export) {
                if (symbol.name in exportedSymbols) {
                    throw IllegalStateException("Duplicate Symbol ${symbol.name}")
                }
                exportedSymbols.add(symbol.name)
            }
        }
    }


    private fun allocateOutputBuffer() =
        Array<UShort>(3 + objects.map { it.payload.size }.fold(0) { acc, i -> acc + i }) { 0xFFFFu }

    // Warning! `copyRawPayloads` depends on the labels to be in the right order for everything to work out
    private fun copyRawPayloads(buffer: Array<UShort>): Array<UShort> {
        var arrayPointer = 3 // Start copying after the bootstrap sequence
        for (obj in groupedByFile.values) {
            for (byte in obj.payload) {
                buffer[arrayPointer] = byte
                arrayPointer++
            }
        }
        return buffer
    }

    private fun relocation(
        buffer: Array<UShort>, labelAddresses: Map<String, UShort>
    ) {
        for ((file, obj) in groupedByFile) {
            val fileBaseAddress = fileBaseAddresses[file]
                ?: throw IllegalStateException("File layout not assigned for ${file.name} in $fileBaseAddresses")
            for (relocatable in obj.relocationTable) { // O(n^2) type shit
                val targetAbsoluteAddress = labelAddresses[relocatable.name]
                    ?: throw IllegalStateException("Unresolved External Symbol Error: ${relocatable.name}")
                val instructionAbsoluteAddress = fileBaseAddress + relocatable.offset
                val indexInBuffer = instructionAbsoluteAddress - startAddress
                val instruction = buffer[indexInBuffer.toInt()]

                val patchedInstruction = when (relocatable.type) {
                    RelocationType.ABS_16 -> {
                        targetAbsoluteAddress
                    }

                    RelocationType.ABS_LUI -> {
                        val top10 = ((targetAbsoluteAddress.toInt() ushr 6) and 0x3FF).toUShort()
                        instruction or top10
                    }

                    RelocationType.ABS_LLI -> {
                        val bottom6 = (targetAbsoluteAddress.toInt() and 0x3F).toUShort()
                        instruction or bottom6
                    }

                    RelocationType.REL_7 -> {
                        val offset = targetAbsoluteAddress.toInt() - (instructionAbsoluteAddress.toInt() + 1)
                        if (offset !in -64..63) {
                            throw IllegalStateException("Branch Target Out of Range Error: $offset")
                        }
                        val bottom7 = (offset and 0x7F).toUShort()
                        instruction or bottom7
                    }
                }
                buffer[indexInBuffer.toInt()] = patchedInstruction

            }
        }
    }

    fun passTwo(labelAddresses: Map<String, UShort>): Array<UShort> {
        val emptyOutPutBuffer = allocateOutputBuffer()
        val buffer = copyRawPayloads(emptyOutPutBuffer)
        relocation(buffer, labelAddresses)
        bootStrap(buffer, labelAddresses)

        return buffer
    }


    private fun bootStrap(buffer: Array<UShort>, labelAddresses: Map<String, UShort>) {
        val mainAddress = labelAddresses["main"]
        if (mainAddress != null) {
            val luiPart = ((mainAddress.toInt() ushr 6) and 0x3FF).toUShort()
            val lliPart = (mainAddress.toInt() and 0x3F).toUShort()

            // movi r7, main
            buffer[0] = (0x7C00 or luiPart.toInt()).toUShort() // lui r7, <main_high>
            buffer[1] = (0x3F80 or lliPart.toInt()).toUShort() // addi r7, r7, <main_low>
            // jalr r0, r7, 0
            buffer[2] = 0xE380.toUShort()
        } else {
            // fallBack
            val halt = Backend().encode(listOf(Instruction.Jalr(RegisterType.R0, RegisterType.R0, immediate = 1)))[0]
            buffer[0] = halt
            buffer[1] = halt
            buffer[2] = halt
        }
    }
}

val mainFsL = File("/Users/leuw/dev/kotlin/Operating-System/linking tests/main.kar")
val mathsFsL = File("/Users/leuw/dev/kotlin/Operating-System/linking tests/maths.kar")

suspend fun main() {
    val mainO = ObjectExcreter(mainFsL).generate()
    val mathsO = ObjectExcreter(mathsFsL).generate()
    val j = Json { prettyPrint = true }

    File("${mainFsL.absolutePath}.json").writeText(j.encodeToString(mainO))
    File("${mathsFsL.absolutePath}.json").writeText(j.encodeToString(mathsO))

    val linker = Linker(mainO, mathsO, baseAddress = 0u)
    val p1 = linker.passOne()
    println(p1)
    val p2 = linker.passTwo(p1)
    p2.forEach(::println)
    println("-----")
    val memory = MemoryBus(PhysicalMemory())
    for ((index, word) in p2.withIndex()) {
        memory.write(index.toUShort(), word.toShort())
    }
    val cpu = Cpu(memory)
    while (!cpu.isHalted) {
        cpu.tick()
    }
}