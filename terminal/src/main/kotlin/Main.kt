package io.cuttlefish

// lc -c $file -o $out (compile)
// lc -i $file (compile + run)
// lc -r $file (run)
suspend fun main(args: Array<String>) {
    when (args[1]) { // flag
        "-c" -> {

        }

    }

}


//suspend fun main() {
//    val parser = Parser(File("main.kar"))
//    val instructions = parser.decode()
//
//    val backend = Backend()
//    val machineCode = backend.encode(instructions)
//    println(machineCode)
////    backend.decode(machineCode).forEach(::println)
//
//    val memory = MemoryBus(
//        PhysicalMemory(1024),
//        DisplayDevice()
//    )
//
//    for ((index, word) in machineCode.withIndex()) {
//        memory.write(index.toShort(), word.toShort())
//    }
//
//    val cpu = Cpu(memory)
//
//    while (!cpu.isHalted) {
//        cpu.tick()
//    }
//    println(cpu.registers)
//}