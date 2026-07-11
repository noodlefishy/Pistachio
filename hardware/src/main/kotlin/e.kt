package io.cuttlefish

import java.awt.*
import javax.swing.*

data class RenderObject(val x: Int, val y: Int, val color: Color)

class GameCanvas : JPanel() {
    private val objectsToDraw = java.util.concurrent.CopyOnWriteArrayList<RenderObject>()

    init {
        preferredSize = Dimension(640, 640)
        background = Color.black

    }

    fun addShape(x: Int, y: Int, color: Color) {
        objectsToDraw.add(RenderObject(x, y, color))
        repaint()
    }

    fun clearCanvas() {
        objectsToDraw.clear()
        repaint()
    }

    private fun drawGrid(g: Graphics2D) {
        g.color = Color.WHITE

        for (xx in 1..8) {
            g.drawLine(
                xx * 80, 0, xx * 80, 640
            )

            g.drawLine(
                0, xx * 80, 640, xx * 80
            )
        }
    }


    fun drawGameObject(g: Graphics2D, obj: RenderObject) {
        g.color = obj.color
        g.fillOval(obj.x, obj.y, 40, 40)

        g.color = Color.WHITE
        g.drawOval(obj.x, obj.y, 40, 40)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        drawGrid(g as Graphics2D)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        for (obj in objectsToDraw) {
            drawGameObject(g, obj)
        }
    }
}

fun main() {
    val frame = JFrame("Public Draw Function Demo")
    val canvas = GameCanvas()

    frame.add(canvas)
    frame.pack()
    frame.isResizable = false
    frame.setLocationRelativeTo(null)
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.isVisible = true

    // Example of an external process using the canvas public functions
    canvas.addShape(250, 180, Color.GREEN)
    canvas.addShape(400, 80, Color.ORANGE)


}
