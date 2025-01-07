package org.logicboost.chat.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.ToolWindowManager
import org.logicboost.chat.ChatPanel

class EditorChatIntegration : AnAction() {
    companion object {
        private const val TOOL_WINDOW_ID = "LogicBoost AI"
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() ?: false
        e.presentation.isEnabledAndVisible = hasSelection
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.project ?: return
        val selectedText = editor.selectionModel.selectedText ?: return

        // Pass e.dataContext to showChatOptions
        showChatOptions(project, editor, selectedText, e.dataContext)
    }

    /**
     * Displays a popup with chat options based on the selected text.
     *
     * @param project The current project.
     * @param editor The editor instance.
     * @param selectedText The text selected by the user.
     * @param dataContext The data context from the action event.
     */
    private fun showChatOptions(project: Project, editor: Editor, selectedText: String, dataContext: DataContext) {
        val popup = JBPopupFactory.getInstance()
            .createActionGroupPopup(
                "Chat Options",
                createChatActionGroup(project, editor, selectedText),
                dataContext, // Use the passed dataContext instead of 'e.dataContext'
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true
            )

        popup.showInBestPositionFor(editor)
    }

    /**
     * Creates a group of chat-related actions.
     *
     * @param project The current project.
     * @param editor The editor instance.
     * @param selectedText The text selected by the user.
     * @return A DefaultActionGroup containing chat actions.
     */
    private fun createChatActionGroup(project: Project, editor: Editor, selectedText: String): DefaultActionGroup {
        return DefaultActionGroup().apply {
            add(createSendToChatAction(project, selectedText))
            add(createExplainCodeAction(project, selectedText))
            add(createRefactorSuggestionAction(project, selectedText))
        }
    }

    /**
     * Creates an action to send selected text to chat.
     */
    private fun createSendToChatAction(project: Project, text: String): AnAction {
        return object : AnAction("Send to Chat") {
            override fun actionPerformed(e: AnActionEvent) {
                openChatToolWindow(project, "Here's the code I'd like to discuss:\n```\n$text\n```")
            }
        }
    }

    /**
     * Creates an action to explain the selected code.
     */
    private fun createExplainCodeAction(project: Project, text: String): AnAction {
        return object : AnAction("Explain This Code") {
            override fun actionPerformed(e: AnActionEvent) {
                openChatToolWindow(project, "Could you explain this code:\n```\n$text\n```")
            }
        }
    }

    /**
     * Creates an action to suggest refactoring for the selected code.
     */
    private fun createRefactorSuggestionAction(project: Project, text: String): AnAction {
        return object : AnAction("Suggest Refactoring") {
            override fun actionPerformed(e: AnActionEvent) {
                openChatToolWindow(project, "Could you suggest ways to improve or refactor this code:\n```\n$text\n```")
            }
        }
    }

    /**
     * Opens the chat tool window and sends the provided message programmatically.
     *
     * @param project The current project.
     * @param message The message to send to the chat.
     */
    private fun openChatToolWindow(project: Project, message: String) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)
        toolWindow?.activate {
            // Fetch the ChatPanel from the tool window's content
            val chatPanel = toolWindow.contentManager.selectedContent?.component as? ChatPanel
            chatPanel?.sendMessageProgrammatically(message)
        }
    }
}
