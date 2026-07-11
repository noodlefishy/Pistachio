package io.cuttlefish

// 🥕🥕🥕🥕🥕🥕🥕 It's a farm
// 🥕🥕🥕🥕🥕🥕🥕 It's a farm
// 🥕🥕🥕🥕🥕🥕🥕 It's a farm
// 🥕🥕🥕🥕🥕🥕🥕 It's a farm
interface MemoryManagement {
    suspend fun read(address: UShort): Short
    suspend fun write(address: UShort, value: Short)
}