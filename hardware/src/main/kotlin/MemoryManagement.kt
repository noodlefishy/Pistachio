package io.cuttlefish


interface MemoryManagement {
    fun read(address: Short): Short
    fun write(address: Short, value: Short)
}