package org.logicboost.chat.ui

import com.intellij.ui.JBColor
import java.awt.*
import java.awt.geom.RoundRectangle2D
import javax.swing.JPanel
import javax.swing.UIManager

/**
 * A reusable panel with rounded corners.
 */
open class RoundedPanel(
    private val radius: Int = 10,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    strokeWidth: Float = 1f
) : JPanel() {

    private val bgColor: Color = backgroundColor ?: (UIManager.getColor("Editor.background") ?: JBColor.background())
    private val bColor: Color = borderColor ?: (UIManager.getColor("Component.borderColor") ?: JBColor.border())
    private val stroke: Float = strokeWidth

    init {
        isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Create the rounded rectangle shape
        val roundRect = RoundRectangle2D.Float(
            1f, 1f,
            width.toFloat() - 2f,
            height.toFloat() - 2f,
            radius.toFloat(),
            radius.toFloat()
        )

        // Fill the background
        g2.color = bgColor
        g2.fill(roundRect)

        // Draw the border
        g2.color = bColor
        g2.stroke = BasicStroke(stroke)
        g2.draw(roundRect)

        g2.dispose()
        super.paintComponent(g)
    }
}
