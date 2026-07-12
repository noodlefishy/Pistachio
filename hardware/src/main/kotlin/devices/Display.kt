package io.cuttlefish.devices

import java.awt.*
import javax.swing.*

class Display : Device {
    private var frame: JFrame? = null // Start as null to prevent instant Swing thread startup!!
    private var grid: GridPanel? = null

    override val name = "Display"
    override val deviceId: UShort = 2u
    override val memoryUsed: UIntRange = 0xFF03u..0xFF4Eu // Fixed boundary to prevent crash!!

    override suspend fun read(address: UShort): Short {
        println(address.toString(16))
        TODO("Not yet implemented")
    }

    override suspend fun write(address: UShort, value: Short) {
        when (address.toInt()) {
            0xFF03 -> {
                when (value) {
                    0.toShort() -> {
                        frame?.dispose()
                        frame = null
                        grid = null
                    }

                    1.toShort() -> {
                        if (frame == null) {
                            frame = JFrame("Pixastachio")
                        }
                        grid = frameInit(frame!!)
                        grid!!.repaint()
                    }

                    2.toShort() -> {
                        pixelData.fill(0)
                        grid?.repaint()
                    }

                    4.toShort() -> {
                        grid?.repaint()
                    }
                }
            }

            in 0xFF0F..0xFF4E -> {
                if (frame == null) {
                    frame = JFrame("Pixastachio")
                    grid = frameInit(frame!!)
                }

                pixelData[(address.toInt() - 0xFF0F)] = value.toInt()
                grid?.repaint()
            }
        }
    }
}

private val pixelData = IntArray(8 * 8)

class GridPanel(private val data: IntArray) : JPanel() {
    private val pixelSize = 50

    init {
        this.preferredSize = Dimension(8 * pixelSize, 8 * pixelSize)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        for (i in data.indices) {
            val x = i % 8
            val y = i / 8


            val rgb565 = data[i] and 0xFFFF


            val r5 = (rgb565 shr 11) and 0x1F // Red: Top 5 bits
            val g6 = (rgb565 shr 5) and 0x3F // Green: Middle 6 bits
            val b5 = rgb565 and 0x1F // Blue: Bottom 5 bits


            val r8 = (r5 * 255) / 31
            val g8 = (g6 * 255) / 63
            val b8 = (b5 * 255) / 31

            g.color = Color(r8, g8, b8)

            g.fillRect(x * pixelSize, y * pixelSize, pixelSize, pixelSize)


            g.color = Color.LIGHT_GRAY
            g.drawRect(x * pixelSize, y * pixelSize, pixelSize, pixelSize)
        }
    }
}


fun frameInit(frame: JFrame): GridPanel {
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    val grid = GridPanel(pixelData)
    frame.add(grid)

    frame.pack()
    frame.isResizable = false
    frame.setLocationRelativeTo(null)

    frame.isVisible = true
    return grid
}