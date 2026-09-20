package io.cuttlefish.devices

import java.awt.*
import javax.swing.*

class Display : Device {
    override val name: String = "Display"
    override val deviceId: UShort = 2u
    override val memoryUsed: UIntRange = 0xFF03u..0xFF09u

    val width = 64
    val height = 64
    private val pixelData = IntArray(width * height) { 0 }

    private var cursorX = 0
    private var cursorY = 0

    private var frame: JFrame? = null
    private var grid: GridPanel? = null
    private var isWindowOpen = false

    override suspend fun read(address: UShort): Short {
        return when (address.toInt()) {
            0xFF03 -> 0
            0xFF04 -> width.toShort()           // DD_WIDT
            0xFF05 -> height.toShort()          // DD_HIGT
            0xFF06 -> if (isWindowOpen) 1 else 0// DD_STUS
            0xFF07 -> cursorX.toShort()         // DD_X
            0xFF08 -> cursorY.toShort()         // DD_Y
            0xFF09 -> {                         // Read colour at current (X, Y)
                val idx = cursorY * width + cursorX
                if (idx in pixelData.indices) pixelData[idx].toShort() else 0
            }
            else -> 0
        }
    }

    override suspend fun write(address: UShort, value: Short) {
        val valInt = value.toInt() and 0xFFFF
        when (address.toInt()) {
            0xFF03 -> { // DD_CTRL Command
                when (value.toInt()) {
                    0 -> closeWindow()
                    1 -> openWindow()
                    2 -> clearScreen()
                    4 -> refreshScreen()
                }
            }
            0xFF07 -> {
                cursorX = valInt % width
            }
            0xFF08 -> {
                cursorY = valInt % height
            }
            0xFF09 -> { // trigger draw
                val idx = cursorY * width + cursorX
                if (idx in pixelData.indices) {
                    pixelData[idx] = valInt
                }

                cursorX++
                if (cursorX >= width) {
                    cursorX = 0
                    cursorY = (cursorY + 1) % height
                }
            }
        }
    }

    private fun openWindow() {
        if (isWindowOpen) return
        SwingUtilities.invokeLater {
            val f = JFrame("Pixastachio (64x64)")
            f.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE

            val g = GridPanel(pixelData, width, height)
            f.add(g)
            f.pack()
            f.isResizable = false
            f.setLocationRelativeTo(null)
            f.isVisible = true

            this.frame = f
            this.grid = g
            this.isWindowOpen = true
        }
    }

    private fun closeWindow() {
        if (!isWindowOpen) return
        SwingUtilities.invokeLater {
            frame?.dispose()
            frame = null
            grid = null
            isWindowOpen = false
        }
    }

    private fun clearScreen() {
        pixelData.fill(0)
        refreshScreen()
    }

    private fun refreshScreen() {
        SwingUtilities.invokeLater {
            grid?.repaint()
        }
    }
}

private class GridPanel(private val data: IntArray, val w: Int, val h: Int) : JPanel() {
    private val pixelSize = 8 // 64 * 8 = 512x512 window on your desktop

    init {
        preferredSize = Dimension(w * pixelSize, h * pixelSize)
        background = Color.BLACK
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        for (i in data.indices) {
            val x = i % w
            val y = i / w
            val rgb565 = data[i]

            // Extract RGB565
            val r8 = ((rgb565 shr 11 and 0x1F) * 255) / 31
            val g8 = ((rgb565 shr 5 and 0x3F) * 255) / 63
            val b8 = ((rgb565 and 0x1F) * 255) / 31

            g.color = Color(r8, g8, b8)
            g.fillRect(x * pixelSize, y * pixelSize, pixelSize, pixelSize)
        }
    }
}