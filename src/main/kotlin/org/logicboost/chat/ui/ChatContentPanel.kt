package org.logicboost.chat.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import org.logicboost.chat.markdown.MarkdownPanel
import org.logicboost.chat.model.ChatMessage
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import javax.swing.UIManager

class ChatContentPanel(
    private val project: Project
) : JPanel(BorderLayout()) {

    val chatHistory = MarkdownPanel(project)

    init {
        val panelBackground = UIManager.getColor("Panel.background")
        background = panelBackground
        isOpaque = true
        setupUI()
    }

    private fun setupUI() {
        val panelBackground = UIManager.getColor("Panel.background")

        val wrapperPanel = JPanel(BorderLayout()).apply {
            background = panelBackground
            isOpaque = true
            add(chatHistory, BorderLayout.CENTER)
        }

        val chatScrollPane = JBScrollPane(wrapperPanel).apply {
            border = JBUI.Borders.empty(0, 5)
            verticalScrollBar.unitIncrement = 16
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            background = panelBackground
            viewport.background = panelBackground
            isOpaque = true
            viewport.isOpaque = true
        }

        add(chatScrollPane, BorderLayout.CENTER)
    }

    fun updateContent(messages: List<ChatMessage>) {
        val markdownContent = messages.joinToString("\n\n") { message ->
            when (message.role) {
                "user" -> "**You**: ${message.content}"
                "assistant" -> "**LogicBoost AI**:\n${message.content}"
                else -> message.content
            }
        }

        chatHistory.setMarkdownContent(markdownContent)
    }

}
