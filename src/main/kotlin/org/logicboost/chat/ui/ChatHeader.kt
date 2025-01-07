package org.logicboost.chat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.Gray
import com.intellij.util.ui.JBUI
import org.logicboost.chat.storage.Chat
import org.logicboost.chat.storage.ChatHistoryStorage
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel

class ChatHeader(
    private val project: Project,
    private val onChatSelected: (Chat) -> Unit
) : JPanel(BorderLayout()) {
    private val storage = ChatHistoryStorage.getInstance(project)

    init {
        border = JBUI.Borders.empty(3)
        isOpaque = false

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0)).apply {
            isOpaque = false

            // New Chat button with icon only
            add(JButton(AllIcons.General.Add).apply {
                text = "Add"
                toolTipText = "New Chat"
                isBorderPainted = false
                isContentAreaFilled = false
                addActionListener {
                    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))
                    val newChat = storage.createChat("Chat $timestamp")
                    onChatSelected(newChat)
                }
            })

            // Add divider panel
            add(JPanel().apply {
                preferredSize = Dimension(1, 20)
                border = BorderFactory.createMatteBorder(0, 1, 0, 0, Gray._128)
                isOpaque = false
            })

            // History button with text and icon
            add(JButton("History", AllIcons.Actions.ListFiles).apply {
                toolTipText = "Chat History"
                isBorderPainted = false
                isContentAreaFilled = false
                addActionListener {
                    ChatHistoryPanel.show(project, onChatSelected)
                }
            })
        }

        add(buttonPanel, BorderLayout.EAST)
    }
}
