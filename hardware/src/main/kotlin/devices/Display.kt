package io.cuttlefish.devices

import java.awt.*
import javax.swing.*

class Display : Device {
    override val name: String = "Display"
    override val deviceId: UShort = 2u
    override val memoryUsed: UIntRange = 0xFF03u..0xFF4Eu

    private val pixelData = IntArray(8 * 8)
    private var frame: JFrame? = null
    private var grid: GridPanel? = null
    private var isWindowOpen = false

    override suspend fun read(address: UShort): Short {
        return when (val addr = address.toInt()) {
            0xFF03 -> 0 // Control register read returns 0
            0xFF04 -> 8 // DD_WIDT: 8 pixels wide
            0xFF05 -> 8 // DD_HIGT: 8 pixels high
            0xFF06 -> if (isWindowOpen) 1 else 0 // DD_STUS: Window open status
            in 0xFF0F..0xFF4E -> pixelData[addr - 0xFF0F].toShort() // Read pixel RGB565 colour
            else -> 0
        }
    }

    override suspend fun write(address: UShort, value: Short) {
        val valInt = value.toInt() and 0xFFFF
        when (val addr = address.toInt()) {
            0xFF03 -> { // DD_CTRL Command
                when (value.toInt()) {
                    0 -> closeWindow()
                    1 -> openWindow()
                    2 -> clearScreen()
                    4 -> refreshScreen()
                }
            }

            in 0xFF0F..0xFF4E -> {
                // Silently update array in memory (NO INSTANT REPAINT!)
                pixelData[addr - 0xFF0F] = valInt
            }
        }
    }

    private fun openWindow() {
        if (isWindowOpen) return
        SwingUtilities.invokeLater {
            val f = JFrame("Pixastachio")
            f.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE

            val g = GridPanel(pixelData)
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

class GridPanel(private val data: IntArray) : JPanel() {
    private val pixelSize = 50

    init {
        preferredSize = Dimension(8 * pixelSize, 8 * pixelSize)
        background = Color.BLACK
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        for (i in data.indices) {
            val x = i % 8
            val y = i / 8

            val rgb565 = data[i] and 0xFFFF

            // Extract RGB565 components
            val r5 = (rgb565 shr 11) and 0x1F
            val g6 = (rgb565 shr 5) and 0x3F
            val b5 = rgb565 and 0x1F

            // Scale to 8-bit RGB (0-255)
            val r8 = (r5 * 255) / 31
            val g8 = (g6 * 255) / 63
            val b8 = (b5 * 255) / 31

            g.color = Color(r8, g8, b8)
            g.fillRect(x * pixelSize, y * pixelSize, pixelSize, pixelSize)

            g.color = Color(40, 40, 40)
            g.drawRect(x * pixelSize, y * pixelSize, pixelSize, pixelSize)
        }
    }
}