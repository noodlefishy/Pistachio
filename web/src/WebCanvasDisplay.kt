package io.cuttlefish.web

import io.cuttlefish.devices.Device
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.dom.events.KeyboardEvent

class WebCanvasDisplay(canvasId: String) : Device {
    override val name: String = "Web Display"
    override val deviceId: UShort = 2u
    override val memoryUsed: UIntRange = 0xFF03u..0xFF0Bu

    val width = 64
    val height = 64
    private val pixelData = IntArray(width * height)

    private var cursorX = 0
    private var cursorY = 0
    private var gamepadState = 0
    private var lastKey = 0

    private val canvas = document.getElementById(canvasId) as HTMLCanvasElement
    @OptIn(ExperimentalWasmJsInterop::class)
    private val ctx = canvas.getContext("2d") as CanvasRenderingContext2D

    init {
        // Keyboard input for 0xFF0A (Gamepad) & 0xFF0B (Key)
        window.addEventListener("keydown", { event ->
            val e = event as KeyboardEvent
            when (e.code) {
                "ArrowUp", "KeyW"     -> gamepadState = gamepadState or 0x0001
                "ArrowDown", "KeyS"   -> gamepadState = gamepadState or 0x0002
                "ArrowLeft", "KeyA"   -> gamepadState = gamepadState or 0x0004
                "ArrowRight", "KeyD"  -> gamepadState = gamepadState or 0x0008
                "Space", "KeyZ"       -> gamepadState = gamepadState or 0x0010
            }
            if (e.key.length == 1) lastKey = e.key[0].code and 0xFFFF
        })

        window.addEventListener("keyup", { event ->
            val e = event as KeyboardEvent
            when (e.code) {
                "ArrowUp", "KeyW"     -> gamepadState = gamepadState and 0x0001.inv()
                "ArrowDown", "KeyS"   -> gamepadState = gamepadState and 0x0002.inv()
                "ArrowLeft", "KeyA"   -> gamepadState = gamepadState and 0x0004.inv()
                "ArrowRight", "KeyD"  -> gamepadState = gamepadState and 0x0008.inv()
                "Space", "KeyZ"       -> gamepadState = gamepadState and 0x0010.inv()
            }
        })
    }

    override suspend fun read(address: UShort): Short {
        return when (address.toInt()) {
            0xFF04 -> width.toShort()
            0xFF05 -> height.toShort()
            0xFF06 -> 1 // Browser window is always open
            0xFF07 -> cursorX.toShort()
            0xFF08 -> cursorY.toShort()
            0xFF09 -> pixelData[cursorY * width + cursorX].toShort()
            0xFF0A -> gamepadState.toShort()
            0xFF0B -> {
                val k = lastKey.toShort()
                lastKey = 0
                k
            }
            else -> 0
        }
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    override suspend fun write(address: UShort, value: Short) {
        val valInt = value.toInt() and 0xFFFF
        when (address.toInt()) {
            0xFF03 -> {
                when (value.toInt()) {
                    2 -> {
                        pixelData.fill(0)
                        ctx.fillStyle = "#000000".toJsString()
                        ctx.fillRect(0.0, 0.0, width.toDouble(), height.toDouble())
                    }
                    4 -> repaint() // DD_CTRL = 4: Blit to HTML canvas!
                }
            }
            0xFF07 -> cursorX = valInt % width
            0xFF08 -> cursorY = valInt % height
            0xFF09 -> {
                pixelData[cursorY * width + cursorX] = valInt
                cursorX++
                if (cursorX >= width) {
                    cursorX = 0
                    cursorY = (cursorY + 1) % height
                }
            }
        }
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    private fun repaint() {
        // Draw the 64x64 buffer directly to HTML5 canvas
        for (i in pixelData.indices) {
            val x = (i % width).toDouble()
            val y = (i / width).toDouble()
            val col = pixelData[i]

            val r = ((col shr 11 and 0x1F) * 255) / 31
            val g = ((col shr 5 and 0x3F) * 255) / 63
            val b = ((col and 0x1F) * 255) / 31

            ctx.fillStyle = "rgb($r,$g,$b)".toJsString()
            ctx.fillRect(x, y, 1.0, 1.0)
        }
    }
}