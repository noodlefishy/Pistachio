package io.cuttlefish


fun Boolean.toShort(): Short {
    return if (this) {
        1
    } else 0
}
