package org.logicboost.chat.actions

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiFile
import org.logicboost.chat.ChatPanel

class ErrorHandler {

    // Companion object to hold constants related to the ErrorHandler class
    companion object {
        // ID for the tool window used by this class
        private const val TOOL_WINDOW_ID = "LogicBoost AI"
    }

    /**
     * Handles errors in the given project, editor, and file.
     * It identifies errors in the editor's markup model and shows fix options.
     *
     * @param project The current project.
     * @param editor The editor where the error occurred.
     * @param file The file being edited.
     */
    fun handleError(project: Project, editor: Editor, file: PsiFile) {
        // Get the markup model for the document in the editor
        val markupModel = DocumentMarkupModel.forDocument(editor.document, project, true)
        // Retrieve all highlighters from the markup model
        val highlights = markupModel.allHighlighters

        // Filter highlighters to find those that are errors and are not valid
        val errors = highlights.filter { highlighter ->
            highlighter is RangeHighlighterEx &&
                    highlighter.layer == HighlighterLayer.ERROR &&
                    !highlighter.isValid
        }

        // If there are any errors, process the first one
        if (errors.isNotEmpty()) {
            val errorInfo = errors.first()
            // Get the text of the error from the document
            val errorText = editor.document.getText(errorInfo.getTextRange())
            // Get the error message from the highlight info
            val errorMessage = (errorInfo.errorStripeTooltip as? HighlightInfo)?.description ?: "Unknown error"

            // Show options to fix the error
            showErrorFixOptions(project, editor, errorText, errorMessage)
        }
    }

    /**
     * Shows options to fix the error by sending a message to the chat panel.
     *
     * @param project The current project.
     * @param editor The editor where the error occurred.
     * @param errorText The text of the error.
     * @param errorMessage The error message.
     */
    private fun showErrorFixOptions(project: Project, editor: Editor, errorText: String, errorMessage: String) {
        // Create a message to send to the chat panel
        val message = """
            I have an error in my code:
            ```
            $errorText
            ```
            Error message: $errorMessage
            
            Could you help me fix this?
        """.trimIndent()

        // Get the tool window by its ID
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)
        // Activate the tool window and send the message to the chat panel
        toolWindow?.activate {
            val chatPanel = toolWindow.contentManager.selectedContent?.component as? ChatPanel
            chatPanel?.sendMessageProgrammatically(message)
        }
    }
}
