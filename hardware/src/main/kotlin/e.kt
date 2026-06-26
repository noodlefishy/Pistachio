package io.cuttlefish

import kotlinx.coroutines.*

fun main() {
    println("[1] Start!")

    runBlocking {
        forgottenWork()
    }
    println("[3] I forgot")
}

suspend fun forgottenWork() {
    println("[2] What should i do?")
    delay(1000L) // thinking
    println("[4] I remember")
}