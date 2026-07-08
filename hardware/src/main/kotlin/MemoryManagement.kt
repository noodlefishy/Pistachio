package io.cuttlefish

// 🥕🥕🥕🥕🥕🥕🥕 It's a farm
// 🥕🥕🥕🥕🥕🥕🥕 It's a farm
// 🥕🥕🥕🥕🥕🥕🥕 It's a farm
// 🥕🥕🥕🥕🥕🥕🥕 It's a farm
interface MemoryManagement {
    suspend fun read(address: Short): Short
    suspend fun write(address: Short, value: Short)
}