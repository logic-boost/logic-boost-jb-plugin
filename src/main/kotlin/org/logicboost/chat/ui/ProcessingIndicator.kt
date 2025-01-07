package org.logicboost.chat.ui

import com.intellij.ui.AnimatedIcon
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.FontMetrics
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.Timer

class ProcessingIndicator : JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)) {
    private val processingIcon = AnimatedIcon.Default()
    private val processingLabel = JLabel("Processing...", processingIcon, SwingConstants.LEFT)
    private val timerLabel = JLabel("(00:00)", SwingConstants.LEFT)

    private var processingTimer: Timer? = null
    private var elapsedSeconds = 0
    private var dotCount = 0

    init {
        // Make the background transparent
        background = null
        isOpaque = false

        // Calculate the preferred size based on the maximum expected text "Processing..."
        val maxText = "Processing..."
        val fontMetrics: FontMetrics = processingLabel.getFontMetrics(processingLabel.font)
        val textWidth = fontMetrics.stringWidth(maxText)
        val textHeight = fontMetrics.height

        // Get the icon's width and height
        val iconWidth = processingIcon.iconWidth
        val iconHeight = processingIcon.iconHeight

        // Calculate total width: icon + gap + text
        val gap = 4 // Default gap between icon and text in JLabel
        val totalWidth = iconWidth + gap + textWidth

        // Set preferred size considering both icon and text
        processingLabel.preferredSize = Dimension(totalWidth, textHeight)

        // a small padding at the bottom (e.g., 5 pixels)
        border = JBUI.Borders.emptyBottom(5)

        // Add the labels to the panel
        add(processingLabel)
        add(timerLabel)

        // Initially hide the indicators
        hideAll()
    }

    /**
     * Starts the processing indicator and timer.
     */
    fun startProcessing() {
        elapsedSeconds = 0
        dotCount = 0
        processingTimer?.stop()

        showAll()
        startTimer()
    }

    /**
     * Stops the processing indicator and timer, resetting the state.
     */
    fun stopProcessing() {
        processingTimer?.stop()
        processingTimer = null
        elapsedSeconds = 0
        dotCount = 0
        processingLabel.text = "Processing..."
        hideAll()
    }

    /**
     * Initializes and starts the timer that updates the labels every second.
     */
    private fun startTimer() {
        processingTimer = Timer(1000) {
            elapsedSeconds++
            dotCount = (dotCount + 1) % 4  // Cycle through 0 to 3

            // Update the processing label with dots
            val dots = ".".repeat(if (dotCount == 0) 3 else dotCount)
            processingLabel.text = "Processing$dots"

            // Update the timer label with elapsed time in (MM:SS) format
            val timerText = String.format("(%02d:%02d)", elapsedSeconds / 60, elapsedSeconds % 60)
            timerLabel.text = timerText
        }
        processingTimer?.start()
    }

    /**
     * Makes both labels visible.
     */
    private fun showAll() {
        processingLabel.isVisible = true
        timerLabel.isVisible = true
    }

    /**
     * Hides both labels.
     */
    private fun hideAll() {
        processingLabel.isVisible = false
        timerLabel.isVisible = false
    }
}
