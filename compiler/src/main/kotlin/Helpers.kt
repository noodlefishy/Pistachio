package io.cuttlefish

fun String.toRegisterType(): RegisterType {
    if (RegisterType.entries.any { it.name.equals(this, ignoreCase = true) }) {
        return RegisterType.entries.find { it.name.equals(this, ignoreCase = true) }!!
    } else {
        throw IllegalStateException("Invalid register name: $this")
    }
}