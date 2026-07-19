package io.cuttlefish

import io.cuttlefish.*
import kotlin.test.Test

class AluExecutionTest : ExecutionTestBase() {
    @Test
    suspend fun testAddAndSub() {
        val asm = """
            movi r1, 150
            movi r2, 50
            add  r3, r1, r2  // 150 + 50 = 200
            sub  r4, r1, r2  // 150 - 50 = 100
        """
        val cpu = executeAsm(asm)
        assertRegister(cpu, RegisterType.R3, 200)
        assertRegister(cpu, RegisterType.R4, 100)

        // Assert non-destructive behaviour! r1 and r2 should still be intact!
        assertRegister(cpu, RegisterType.R1, 150)
        assertRegister(cpu, RegisterType.R2, 50)
    }
}