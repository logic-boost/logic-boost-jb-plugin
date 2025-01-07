package org.logicboost.chat.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.logicboost.chat.api.OpenAIClientWrapper
import org.logicboost.chat.settings.ChatSettings
import org.logicboost.chat.utils.JsonParserUtils
import java.awt.Point

class CommentAction : AnAction() {

    private val logger = Logger.getInstance(CommentAction::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    @Serializable
    data class CommentResponse(val commentedCode: String)

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() == true
        e.presentation.isEnabled = hasSelection
    }

    /**
     * Handles the action event triggered by the user. Retrieves the editor, project, and selected text from the event.
     * Displays a wait indicator while processing the selected text to add AI-generated comments.
     * Handles any exceptions that occur during the process and ensures the wait indicator is hidden afterward.
     */
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.project ?: return
        val selectedText = editor.selectionModel.selectedText ?: return

        // Example: how you might retrieve the language from the active file (if available):
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        val languageId = psiFile?.language?.id ?: "plaintext"

        // Show a wait indicator in the upper-right corner of the editor
        val waitBalloon = showWaitIndicator(editor, "Generating AI Comments...")

        scope.launch {
            try {
                processAddComment(project, editor, selectedText, languageId)
            } catch (ex: Exception) {
                handleError("Error adding comment", ex)
            } finally {
                // Hide the wait indicator
                ApplicationManager.getApplication().invokeLater {
                    waitBalloon.hide()
                }
            }
        }
    }

    /**
     * Creates and displays a balloon in the upper-right corner of the active editor
     * to indicate that background processing is in progress.
     */
    private fun showWaitIndicator(editor: Editor, message: String): Balloon {
        val balloon = JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder(message, MessageType.INFO, null)
            .setHideOnClickOutside(false)
            .setCloseButtonEnabled(false)
            .setFadeoutTime(0) // Keep balloon visible until manually hidden
            .createBalloon()

        val component = editor.contentComponent
        // Show near the top-right corner
        val xOffset = component.width - 20
        val yOffset = 20
        balloon.show(
            RelativePoint(component, Point(xOffset, yOffset)),
            Balloon.Position.atRight
        )
        return balloon
    }

    /**
     * Main process to request AI-generated comments and apply them to the editor.
     */
    private suspend fun processAddComment(
        project: Project,
        editor: Editor,
        selectedText: String,
        language: String
    ) {
        val client = getClient() ?: throw IllegalStateException("No valid LLM configuration found.")

        // Build a prompt with the known language instead of auto-detecting from snippet
        val message = buildLLMPrompt(language, selectedText)
        val response = client.getSingleCompletion(message)
        logger.debug("Received LLM response: $response")

        val commentedCode = parseResponse(response)

        ApplicationManager.getApplication().invokeLater {
            applyComment(project, editor, commentedCode)
        }
    }

    /**
     * Builds a prompt that uses the given [language] instead of auto-detecting.
     * Instructs the LLM to return a single-line JSON with key "commentedCode".
     */
    private fun buildLLMPrompt(language: String, code: String): String {
        return """
You are an advanced code documentation system. The user states the code is in "$language".

GOT A CODE:
***
$code
***

Your task is to add a comment to the code following the rules:

1. **Comment Style**  
   Use the standard comment style for $language:
   - For class/function docstrings: multi-line or block comment.
   - For in-line logic: single-line comment.

2. **Task**  
   - Add a multi-line comment above each method or function describing its purpose.
   - Add single-line comments inside the body for key logic steps.

3. **Formatting**  
   - Preserve ALL existing indentation and spacing. Only add new comment lines.
   - Do NOT re-indent or reformat existing lines.

4. **Output**  
   - Return exactly one JSON object in a single line
   - Strictly follow this template!!!:
        {"commentedCode":"<code with embedded comments>"}
   - The value must be a valid JSON string with all necessary escapes (\n, \", etc.).
   - No other text or markdown.

        """.trimIndent()
    }

    private fun parseResponse(response: String): String {
        return JsonParserUtils.parseCommentResponse(response)
    }

    /**
     * Applies the commented code, preserving the selected region's boundaries.
     */
    private fun applyComment(project: Project, editor: Editor, commentedCode: String) {
        CommandProcessor.getInstance().executeCommand(project, {
            WriteCommandAction.runWriteCommandAction(project) {
                try {
                    val selectionStart = editor.selectionModel.selectionStart
                    val selectionEnd = editor.selectionModel.selectionEnd
                    editor.document.replaceString(selectionStart, selectionEnd, commentedCode)
                    showNotification(project, "Comment added successfully.", MessageType.INFO)
                } catch (e: Exception) {
                    handleError("Failed to apply comment", e)
                }
            }
        }, "Add AI Generated Comment", null)
    }

    /**
     * Logs and shows a user-facing notification on errors.
     */
    private fun handleError(message: String, error: Exception) {
        logger.error(message, error)
        ApplicationManager.getApplication().invokeLater {
            error.message?.let { errorMessage ->
                showNotification(null, "$message: $errorMessage", MessageType.ERROR)
            }
        }
    }

    private fun showNotification(project: Project?, message: String, type: MessageType) {
        ApplicationManager.getApplication().invokeLater {
            val editor = project?.let {
                com.intellij.openapi.fileEditor.FileEditorManager.getInstance(it).selectedTextEditor
            }

            JBPopupFactory.getInstance()
                .createHtmlTextBalloonBuilder(message, type, null)
                .setFadeoutTime(3000)
                .createBalloon()
                .show(
                    editor?.let {
                        JBPopupFactory.getInstance().guessBestPopupLocation(it)
                    } ?: JBPopupFactory.getInstance().guessBestPopupLocation(
                        com.intellij.openapi.wm.WindowManager.getInstance().getIdeFrame(project)?.component
                            ?: return@invokeLater
                    ),
                    Balloon.Position.above
                )
        }
    }

    /**
     * Retrieves an instance of OpenAIClientWrapper based on the available LLM configurations.
     * First tries to use the LLM configured for smart functions, then falls back to the standard selected LLM.
     * Returns null if no suitable LLM configuration is found or if an error occurs during client creation.
     */
    private fun getClient(): OpenAIClientWrapper? {
        logger.info("Getting OpenAI client")

        // First try to get the smart functions LLM
        val smartLLMConfig = ChatSettings.getInstance().getSmartLLMConfig()

        // If no smart functions LLM is found, fall back to the standard selected LLM
        val llmConfig = if (smartLLMConfig?.isEnabled == true) {
            logger.info("Using Smart Functions LLM configuration")
            smartLLMConfig
        } else {
            logger.info("No Smart Functions LLM found, falling back to standard LLM")
            ChatSettings.getInstance().getSelectedLLM()
        } ?: run {
            logger.warn("No suitable LLM configuration found")
            return null
        }

        return try {
            OpenAIClientWrapper.create(
                OpenAIClientWrapper.Configuration(
                    apiKey = llmConfig.apiKey,
                    endpoint = llmConfig.apiEndpoint,
                    model = llmConfig.model,
                    systemPrompt = llmConfig.systemPrompt,
                    temperature = llmConfig.temperature,
                    maxTokens = llmConfig.maxTokens.toLong(),
                    topP = llmConfig.topP
                )
            ).also {
                logger.info("Successfully created OpenAI client using ${if (llmConfig.forSmartFunctions) "Smart Functions" else "standard"} LLM")
            }
        } catch (e: Exception) {
            logger.error("Error creating OpenAI client", e)
            null
        }
    }
}
