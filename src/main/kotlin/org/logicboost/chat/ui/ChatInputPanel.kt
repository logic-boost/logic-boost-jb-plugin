package org.logicboost.chat.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.*

class ChatInputPanel(
    project: Project,
    private val onSendMessage: (String) -> Unit,
    private val onClearChat: () -> Unit,
    private val onInterrupt: () -> Unit
) : RoundedPanel() {

    private val inputField = createInputField()
    private val sendButton = JButton("Send").apply {
        isOpaque = false
        background = null
        border = JBUI.Borders.empty(2, 5)
    }

    private val clearButton = JButton("Clear").apply {
        isOpaque = false
        background = null
        border = JBUI.Borders.empty(2, 5)
    }

    private val llmSelector = LLMSelector(project)

    private val interruptButton = InterruptButton(onInterrupt).apply {
        isVisible = false
    }

    private fun createInputField(): JBTextArea {
        return JBTextArea(4, 50).apply {
            lineWrap = true
            wrapStyleWord = true
            margin = JBUI.insets(5, 5)
            isOpaque = false
            border = null
            background = Color(0, 0, 0, 0)
            foreground = UIManager.getColor("TextArea.foreground")

            // Add placeholder text
            text = "Press Enter to send, Shift+Enter for new line"
            foreground = UIManager.getColor("TextArea.inactiveForeground")

            // Add focus listeners for placeholder behavior
            addFocusListener(object : FocusListener {
                override fun focusGained(e: FocusEvent) {
                    if (text == "Press Enter to send, Shift+Enter for new line") {
                        text = ""
                        foreground = UIManager.getColor("TextArea.foreground")
                    }
                }

                override fun focusLost(e: FocusEvent) {
                    if (text.isEmpty()) {
                        text = "Press Enter to send, Shift+Enter for new line"
                        foreground = UIManager.getColor("TextArea.inactiveForeground")
                    }
                }
            })

        }
    }

    init {
        setupUI()
        setupActions()
    }

    private fun setupUI() {
        layout = BorderLayout()
        border = JBUI.Borders.empty(1, 10, 1, 10)

        val inputScrollPane = JBScrollPane(inputField).apply {
            border = JBUI.Borders.empty(5)
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBar.unitIncrement = 16
            isOpaque = false
            background = null
            viewport.isOpaque = false
            viewport.background = null
        }
        add(inputScrollPane, BorderLayout.CENTER)

        val controlPanel = createControlPanel()
        add(controlPanel, BorderLayout.SOUTH)
    }

    private fun createControlPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(5, 0)
            background = null
            isOpaque = false

            add(llmSelector, BorderLayout.WEST)

            val buttonsPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                background = null
                isOpaque = false

                add(interruptButton)
                add(Box.createHorizontalStrut(10))
                add(clearButton)
                add(Box.createHorizontalStrut(10))
                add(sendButton)
            }
            add(buttonsPanel, BorderLayout.EAST)
        }
    }

    private fun setupActions() {
        sendButton.addActionListener { sendMessage() }
        clearButton.addActionListener { onClearChat() }

        inputField.getInputMap(JComponent.WHEN_FOCUSED).apply {
            put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "sendMessage")
        }
        inputField.actionMap.apply {
            put("sendMessage", object : AbstractAction() {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                    if (inputField.text != "Press Enter to send, Shift+Enter for new line") {
                        sendMessage()
                    }
                }
            })
        }

        val insertNewlineAction = InsertNewlineAction(inputField)
        insertNewlineAction.registerCustomShortcutSet(
            CustomShortcutSet(
                KeyboardShortcut(
                    KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK),
                    null
                )
            ),
            inputField
        )
    }

    private fun sendMessage() {
        val message = inputField.text.trim()
        if (message.isNotEmpty() && message != "Press Enter to send, Shift+Enter for new line") {
            onSendMessage(message)
            inputField.text = ""
            inputField.foreground = UIManager.getColor("TextArea.inactiveForeground")
            inputField.text = "Press Enter to send, Shift+Enter for new line"
        }
    }

    override fun setEnabled(enabled: Boolean) {
        inputField.isEnabled = enabled
        sendButton.isEnabled = enabled
        clearButton.isEnabled = enabled
        llmSelector.isEnabled = enabled
    }

    fun showInterruptButton() {
        interruptButton.showButton()
    }

    fun hideInterruptButton() {
        interruptButton.hideButton()
    }

    private class InsertNewlineAction(private val inputField: JBTextArea) : AnAction() {
        override fun actionPerformed(e: AnActionEvent) {
            if (inputField.text == "Press Enter to send, Shift+Enter for new line") {
                inputField.text = ""
                inputField.foreground = UIManager.getColor("TextArea.foreground")
            }
            inputField.insert("\n", inputField.caretPosition)
        }
    }
}
