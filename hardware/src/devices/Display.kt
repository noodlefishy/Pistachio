package io.cuttlefish.devices

import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import javax.swing.*

class Display : Device {
    override val name: String = "Display"
    override val deviceId: UShort = 2u

    override val memoryUsed: UIntRange = 0xFF03u..0xFF0Bu

    val width = 64
    val height = 64
    private val pixelData = IntArray(width * height) { 0 }

    private var cursorX = 0
    private var cursorY = 0

    @Volatile private var isWindowOpen = false
    @Volatile private var gamepadState = 0
    @Volatile private var lastKey = 0

    private var frame: JFrame? = null
    private var grid: GridPanel? = null

    override suspend fun read(address: UShort): Short {
        return when (address.toInt()) {
            0xFF03 -> 0
            0xFF04 -> width.toShort()
            0xFF05 -> height.toShort()
            0xFF06 -> if (isWindowOpen) 1 else 0
            0xFF07 -> cursorX.toShort()
            0xFF08 -> cursorY.toShort()
            0xFF09 -> {
                val idx = cursorY * width + cursorX
                if (idx in pixelData.indices) pixelData[idx].toShort() else 0
            }
            0xFF0A -> gamepadState.toShort() // Read live held keys!
            0xFF0B -> {                      // Read single key (clears on read)
                val k = lastKey.toShort()
                lastKey = 0
                k
            }
            else -> 0
        }
    }

    override suspend fun write(address: UShort, value: Short) {
        val valInt = value.toInt() and 0xFFFF
        when (address.toInt()) {
            0xFF03 -> {
                when (value.toInt()) {
                    0 -> closeWindow()
                    1 -> openWindow()
                    2 -> clearScreen()
                    4 -> refreshScreen()
                }
            }
            0xFF07 -> cursorX = valInt % width
            0xFF08 -> cursorY = valInt % height
            0xFF09 -> {
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

        val initWindow = {
            val f = JFrame("Pixastachio (64x64)")
            f.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
            f.contentPane.background = Color.BLACK
            f.background = Color.BLACK

            f.addWindowListener(object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent) { isWindowOpen = false }
                override fun windowClosed(e: WindowEvent) { isWindowOpen = false }
            })

            f.addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    when (e.keyCode) {
                        KeyEvent.VK_UP, KeyEvent.VK_W       -> gamepadState = gamepadState or 0x0001
                        KeyEvent.VK_DOWN, KeyEvent.VK_S     -> gamepadState = gamepadState or 0x0002
                        KeyEvent.VK_LEFT, KeyEvent.VK_A     -> gamepadState = gamepadState or 0x0004
                        KeyEvent.VK_RIGHT, KeyEvent.VK_D    -> gamepadState = gamepadState or 0x0008
                        KeyEvent.VK_SPACE, KeyEvent.VK_Z    -> gamepadState = gamepadState or 0x0010
                        KeyEvent.VK_SHIFT, KeyEvent.VK_X    -> gamepadState = gamepadState or 0x0020
                        KeyEvent.VK_ENTER, KeyEvent.VK_ESCAPE -> gamepadState = gamepadState or 0x0040
                    }
                    if (e.keyChar != KeyEvent.CHAR_UNDEFINED) {
                        lastKey = e.keyChar.code and 0xFFFF
                    }
                }

                override fun keyReleased(e: KeyEvent) {
                    when (e.keyCode) {
                        KeyEvent.VK_UP, KeyEvent.VK_W       -> gamepadState = gamepadState and 0x0001.inv()
                        KeyEvent.VK_DOWN, KeyEvent.VK_S     -> gamepadState = gamepadState and 0x0002.inv()
                        KeyEvent.VK_LEFT, KeyEvent.VK_A     -> gamepadState = gamepadState and 0x0004.inv()
                        KeyEvent.VK_RIGHT, KeyEvent.VK_D    -> gamepadState = gamepadState and 0x0008.inv()
                        KeyEvent.VK_SPACE, KeyEvent.VK_Z    -> gamepadState = gamepadState and 0x0010.inv()
                        KeyEvent.VK_SHIFT, KeyEvent.VK_X    -> gamepadState = gamepadState and 0x0020.inv()
                        KeyEvent.VK_ENTER, KeyEvent.VK_ESCAPE -> gamepadState = gamepadState and 0x0040.inv()
                    }
                }
            })

            val g = GridPanel(pixelData, width, height)
            f.add(g)
            f.pack()
            f.isResizable = false
            f.setLocationRelativeTo(null)
            f.isVisible = true
            f.isFocusable = true
            f.requestFocusInWindow()

            this.frame = f
            this.grid = g
            this.isWindowOpen = true
        }

        if (SwingUtilities.isEventDispatchThread()) {
            initWindow()
        } else {
            SwingUtilities.invokeAndWait(initWindow)
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
    private val pixelScale = 8
    private val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    private val imgBuffer = (img.raster.dataBuffer as DataBufferInt).data

    init {
        preferredSize = Dimension(w * pixelScale, h * pixelScale)
        background = Color.BLACK
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        for (i in data.indices) {
            val rgb565 = data[i]
            val r = ((rgb565 shr 11 and 0x1F) * 255) / 31
            val g8 = ((rgb565 shr 5 and 0x3F) * 255) / 63
            val b = ((rgb565 and 0x1F) * 255) / 31
            imgBuffer[i] = (r shl 16) or (g8 shl 8) or b
        }

        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        g2d.drawImage(img, 0, 0, w * pixelScale, h * pixelScale, null)
    }
}