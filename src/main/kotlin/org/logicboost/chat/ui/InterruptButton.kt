package org.logicboost.chat.ui

import com.intellij.icons.AllIcons
import javax.swing.JButton

class InterruptButton(private val onInterrupt: () -> Unit) : JButton(AllIcons.Actions.Suspend) {
    init {
        toolTipText = "Interrupt current request"
        isFocusable = false
        isVisible = false
        border = null

        addActionListener {
            onInterrupt()
        }
    }

    fun showButton() {
        isVisible = true
        isEnabled = true
    }

    fun hideButton() {
        isVisible = false
        isEnabled = false
    }
}
