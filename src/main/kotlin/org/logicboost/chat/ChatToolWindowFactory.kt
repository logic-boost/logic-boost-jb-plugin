package org.logicboost.chat

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import org.logicboost.chat.storage.ChatHistoryStorage

class ChatToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val storage = ChatHistoryStorage.getInstance(project)
        // Get the most recent chat or create a new one
        val initialChat = storage.getAllChats().firstOrNull() ?: storage.createChat("New Chat")
        val chatPanel = ChatPanel(project, initialChat)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(chatPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
