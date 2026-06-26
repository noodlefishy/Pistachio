package io.cuttlefish


interface MemoryManagement {
    suspend fun read(address: Short): Short
    suspend fun write(address: Short, value: Short)
}