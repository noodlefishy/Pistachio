package io.cuttlefish

import io.cuttlefish.backend.Backend
import io.cuttlefish.components.Cpu
import io.cuttlefish.components.MemoryBus
import io.cuttlefish.components.PhysicalMemory
import io.cuttlefish.parsing.Parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.test.assertEquals

abstract class ExecutionTestBase {

    protected suspend fun executeAsm(asm: String): Cpu {
        val file = withContext(Dispatchers.IO) {
            File.createTempFile("test_prog", ".lx")
        }
        file.writeText("$asm\n halt")

        val parser = Parser(file, 0.toShort())
        val instructions = parser.decode()
        val machineCode = Backend().encode(instructions)

        val memory = MemoryBus(PhysicalMemory())
        machineCode.forEachIndexed { i, word ->
            memory.write(i.toUShort(), word.toShort())
        }

        val cpu = Cpu(memory)
        while (!cpu.isHalted) {
            cpu.tick()
        }

        file.delete()
        return cpu
    }

    protected fun assertRegister(cpu: Cpu, reg: RegisterType, expected: Int) {
        val actual = cpu.registers.registerData[reg.ordinal].toInt() and 0xFFFF
        val exp = expected and 0xFFFF
        assertEquals(
            exp,
            actual,
            "Register $reg mismatch. Expected 0x${exp.toString(16)}, got 0x${actual.toString(16)}"
        )
    }
}