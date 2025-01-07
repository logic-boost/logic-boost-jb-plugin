package org.logicboost.chat.actions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.logicboost.chat.api.OpenAIClientWrapper
import org.logicboost.chat.settings.ChatSettings
import org.logicboost.chat.utils.JsonParserUtils
import java.awt.Point
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * This class handles code comparison and replacement operations:
 * 1. Retrieving the AI-suggested code from the source editor
 * 2. Retrieving the target code from the main IDE editor
 * 3. Sending a prompt to the LLM for code comparison and change instructions
 * 4. Receiving and parsing JSON instructions for code modifications
 * 5. Highlighting proposed changes in the target editor
 * 6. Showing a confirmation popup with an "Allow" button
 * 7. Applying the approved changes to the target editor
 */
class ChangesAction(
    private val project: Project,
    private val sourceCode: String
) {
    private val logger = Logger.getInstance(ChangesAction::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var popupRef: JBPopup? = null
    private var targetEditor: Editor? = getTargetEditor()

    /**
     * Data class representing a single code change operation
     * @property action The type of change ('replace', 'insert', or 'delete')
     * @property start_line The 1-based line number where the change begins
     * @property end_line Optional end line for 'replace' or 'delete' actions
     * @property new_code The new code to insert or use as replacement
     * @property explanation Description of why this change is being made
     */
    @Serializable
    data class CodeChange(
        val action: String,
        val start_line: Int,
        val end_line: Int? = null,
        val new_code: String,
        val explanation: String? = null
    )

    /**
     * Main entry point to perform the code comparison and modification action.
     */
    fun performApplyAction() {
        logger.info("Starting apply action")

        val targetEditor = this.targetEditor ?: return
        val suggestedCode = sourceCode
        val targetCode = targetEditor.document.text

        // Validate user inputs, if needed
        if (!validateInputs(targetCode)) return

        // Launch the process in a background coroutine
        scope.launch {
            var progressBalloon: Balloon? = null
            try {
                // Show a "processing" indicator in the UI thread
                ApplicationManager.getApplication().invokeLater {
                    progressBalloon = showWaitIndicator(targetEditor, "Processing changes...")
                }

                // Run the main logic
                processChanges(suggestedCode, targetCode, targetEditor)
            } catch (e: Exception) {
                handleError("Error performing apply action", e)
            } finally {
                // Hide the indicator once done or on error
                ApplicationManager.getApplication().invokeLater {
                    progressBalloon?.hide()
                }
            }
        }
    }

    /**
     * Retrieves the currently selected editor from the IDE.
     * @return The active editor or null if none is selected
     */
    private fun getTargetEditor(): Editor? {
        return FileEditorManager.getInstance(project).selectedTextEditor?.also {
            logger.info("Found target editor")
        } ?: run {
            showNotification(
                "No target file editor found. Please open the file you want to modify.",
                MessageType.WARNING
            )
            null
        }
    }

    /**
     * Validates the input code before processing.
     * (Currently a placeholder for future validation checks.)
     * @param targetCode The code to validate
     * @return Boolean indicating if the input is valid
     */
    private fun validateInputs(targetCode: String): Boolean {
        // TODO: Additional input validation can be added here
        return true
    }

    /**
     * Processes code changes by sending them to the LLM and handling the response.
     * @param suggestedCode The AI-suggested code
     * @param targetCode The current code in the target editor
     * @param targetEditor The editor where changes will be applied
     */
    private suspend fun processChanges(suggestedCode: String, targetCode: String, targetEditor: Editor) {
        val client = getClient() ?: throw IllegalStateException("No valid LLM configuration found.")

        // Build a prompt for the LLM
        val message = buildLLMPrompt(suggestedCode, targetCode)
        val response = client.getSingleCompletion(message)
        logger.debug("Received LLM response: $response")

        // Parse the response into a list of changes
        val changes = parseLLMResponse(response)
        logger.info("Parsed ${changes.size} changes from LLM response")

        // Now skip or clamp changes that have invalid line numbers
        val validChanges = validateLineNumbers(targetEditor, changes)

        if (validChanges.isEmpty()) {
            // If all changes were invalid and we skipped them, show a warning
            showNotification("All suggested changes were invalid for this file.", MessageType.WARNING)
            return
        }

        // Optional: detect overlapping or conflicting changes if desired
        if (hasConflicts(validChanges)) {
            showNotification("Potential overlapping or conflicting changes detected.", MessageType.WARNING)
            // We'll still proceed, but in real usage you might handle merges here.
        }

        // Highlight changes and show a confirmation popup
        ApplicationManager.getApplication().invokeLater {
            highlightChanges(targetEditor, validChanges)
            showPopup(targetEditor, validChanges)
        }
    }

    /**
     * A concise LLM prompt requesting structured JSON change instructions.
     */
    private fun buildLLMPrompt(editorCode: String, activeFileCode: String): String {
        val numberedActiveFileCode = activeFileCode
            .lines()
            .mapIndexed { index, line -> "${index + 1}\t$line" }
            .joinToString("\n")

        val currentFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        val fileExtension = currentFile?.extension ?: ""

        return """
            You are an advanced code analysis and integration system that merges code from a "new code" snippet into an existing codebase.

            - Output only a strict JSON array (no extra commentary).
            - Allowed actions: "replace", "insert", or "delete".
            - Each change object must have:
                {
                  "action": "replace"|"insert"|"delete",
                  "start_line": <int>,
                  "end_line": <int|null>,
                  "new_code": <string>,
                  "explanation": <string>
                }
            - Verify line numbers exist in the existing code.
            - Maintain the existing file's style (language: $fileExtension).
            - Provide a clear explanation for each change.

            EXISTING CODE (with line numbers):
            $numberedActiveFileCode

            NEW CODE:
            $editorCode
        """.trimIndent()
    }

    /**
     * Parses the JSON response from the LLM into a list of CodeChange objects.
     * @param response The raw response string from the LLM
     * @return List of parsed CodeChange objects
     */
    private fun parseLLMResponse(response: String): List<CodeChange> {
        return JsonParserUtils.parseCodeChanges(response)
    }



    /**
     * Validates that the line numbers are within the document bounds.
     * If a change is out of range, we skip it (remove it from the list).
     *
     * Alternatively, you could clamp out-of-range changes, or extend the file.
     */
    private fun validateLineNumbers(editor: Editor, changes: List<CodeChange>): List<CodeChange> {
        val lineCount = editor.document.lineCount
        val validChanges = mutableListOf<CodeChange>()

        for (change in changes) {
            val startLine = change.start_line
            val endLine = change.end_line ?: change.start_line

            // The logic can differ by action type:
            // - "insert" can go up to lineCount+1 (append at the end).
            // - "replace" or "delete" must be within 1..lineCount, and end_line >= start_line.
            val isStartValid = if (change.action.equals("insert", ignoreCase = true)) {
                startLine in 1..(lineCount + 1)
            } else {
                startLine in 1..lineCount
            }

            val isEndValid = when (change.action.lowercase()) {
                "replace", "delete" -> (endLine >= startLine && endLine <= lineCount)
                else -> true // For insert or unknown
            }

            if (isStartValid && isEndValid) {
                validChanges.add(change)
            } else {
                logger.warn("Skipping invalid change: $change (file has $lineCount lines)")
            }
        }

        if (validChanges.size < changes.size) {
            logger.warn("Some changes were skipped due to invalid line references.")
        }

        return validChanges
    }

    /**
     * Optionally detect if changes overlap. This is a simplified approach:
     * - We look at each pair of changes and see if line ranges conflict.
     */
    private fun hasConflicts(changes: List<CodeChange>): Boolean {
        val sorted = changes.sortedBy { it.start_line }
        for (i in 0 until sorted.size - 1) {
            val current = sorted[i]
            val next = sorted[i + 1]
            val currentEnd = current.end_line ?: current.start_line

            // Overlap if next.start_line <= currentEnd (and both are replace/delete)
            // Insert can be a bit different, but we keep it simple
            if (next.start_line <= currentEnd) {
                return true
            }
        }
        return false
    }

    /**
     * Highlights the proposed changes in the editor.
     * @param editor The target editor
     * @param changes The list of changes to highlight
     */
    private fun highlightChanges(editor: Editor, changes: List<CodeChange>) {
        logger.info("Highlighting ${changes.size} changes in editor")
        val markupModel = editor.markupModel

        // Clear existing highlights
        markupModel.allHighlighters.forEach { markupModel.removeHighlighter(it) }

        // Apply new highlights
        changes.forEach { change ->
            val startOffset = getEditorOffset(editor, change.start_line)
            val endOffset = if (change.end_line != null) {
                getEditorOffset(editor, change.end_line, start = false)
            } else {
                getEditorOffset(editor, change.start_line, start = false)
            }

            markupModel.addRangeHighlighter(
                startOffset,
                endOffset,
                0,
                TextAttributes(UIUtil.getLabelForeground(), JBColor.YELLOW, null, null, 0),
                com.intellij.openapi.editor.markup.HighlighterTargetArea.EXACT_RANGE
            )
        }
    }

    /**
     * Shows a confirmation popup for the proposed changes.
     * @param editor The target editor
     * @param changes The list of changes to apply
     */
    private fun showPopup(editor: Editor, changes: List<CodeChange>) {
        logger.info("Showing confirmation popup")
        val panel = createPopupPanel(editor, changes)

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, null)
            .setRequestFocus(true)
            .setTitle("Review and Apply Changes")
            .createPopup()

        popupRef = popup
        showPopupInEditor(editor, popup)
    }

    /**
     * Creates the panel for the confirmation popup.
     * @param editor The target editor
     * @param changes The list of changes to apply
     * @return The created JPanel
     */
    private fun createPopupPanel(editor: Editor, changes: List<CodeChange>): JPanel {
        val label = JLabel("Apply these changes to the file?")
        val allowButton = JButton("Allow").apply {
            addActionListener {
                applyChanges(editor, changes)
                popupRef?.cancel()
            }
        }
        return JPanel().apply {
            background = JBColor.PanelBackground
            add(label)
            add(allowButton)
        }
    }

    /**
     * Shows the popup in the editor window.
     * @param editor The target editor
     * @param popup The popup to show
     */
    private fun showPopupInEditor(editor: Editor, popup: JBPopup) {
        val editorComponent = editor.contentComponent
        val visibleRect = editorComponent.visibleRect
        val showPoint = Point(visibleRect.x + visibleRect.width - 200, visibleRect.y + 20)
        popup.show(RelativePoint(editorComponent, showPoint))
    }

    /**
     * Applies the approved changes to the editor.
     * @param editor The target editor
     * @param changes The list of changes to apply
     */
    private fun applyChanges(editor: Editor, changes: List<CodeChange>) {
        logger.info("Starting to apply ${changes.size} changes")

        CommandProcessor.getInstance().executeCommand(project, {
            WriteCommandAction.runWriteCommandAction(project) {
                try {
                    applyChangesInEditor(editor, changes)
                    showNotification("Changes applied successfully.", MessageType.INFO)
                } catch (e: Exception) {
                    handleError("Failed to apply changes", e)
                }
            }
        }, "Apply AI Suggested Changes", null)
    }

    /**
     * Applies the changes to the editor document with proper formatting and boundary checks.
     */
    private fun applyChangesInEditor(editor: Editor, changes: List<CodeChange>) {
        val doc = editor.document

        // Guess the line separator by scanning text, or default to system line separator
        val lineSeparator = guessLineSeparator(doc)
        val effectiveNewline = if (lineSeparator.isEmpty()) System.lineSeparator() else lineSeparator

        // Apply changes from bottom to top to avoid offset shifting
        for (change in changes.asReversed()) {
            when (change.action.lowercase()) {
                "replace" -> {
                    // Ensure end_line exists
                    val endLine = change.end_line ?: continue
                    logger.info("Applying replace change at lines ${change.start_line}-$endLine")

                    val startOffset = getEditorOffset(editor, change.start_line)
                    val endOffset = getEditorOffset(editor, endLine, start = false)
                    doc.replaceString(startOffset, endOffset, change.new_code)
                }

                "insert" -> {
                    logger.info("Applying insert change at line ${change.start_line}")
                    val startOffset = getEditorOffset(editor, change.start_line)
                    // Optionally check if new_code already ends with a newline
                    val insertion = if (
                        change.new_code.endsWith("\n") ||
                        change.new_code.endsWith("\r\n")
                    ) {
                        change.new_code
                    } else {
                        change.new_code + effectiveNewline
                    }
                    doc.insertString(startOffset, insertion)
                }

                "delete" -> {
                    // For delete, ensure end_line is set (or assume same as start_line)
                    val endLine = change.end_line ?: change.start_line
                    logger.info("Applying delete change at lines ${change.start_line}-$endLine")

                    val startOffset = getEditorOffset(editor, change.start_line)
                    val endOffset = getEditorOffset(editor, endLine, start = false)
                    // Remove trailing newline if not the last line
                    val adjustedEndOffset = if (endOffset < doc.textLength) endOffset + 1 else endOffset
                    doc.deleteString(startOffset, adjustedEndOffset)
                }

                else -> logger.warn("Unknown change action: ${change.action}")
            }
        }

        cleanupAfterChanges(editor)
    }

    /**
     * Attempts to detect whether the document uses "\n" or "\r\n" by scanning its content.
     * If no "\r\n" pattern is found, assumes "\n".
     */
    private fun guessLineSeparator(doc: Document): String {
        val text = doc.immutableCharSequence
        return if ("\r\n" in text) "\r\n" else "\n"
    }

    /**
     * Cleans up the editor state after applying changes.
     * @param editor The target editor
     */
    private fun cleanupAfterChanges(editor: Editor) {
        editor.selectionModel.removeSelection()
        editor.markupModel.allHighlighters.forEach {
            editor.markupModel.removeHighlighter(it)
        }
    }

    /**
     * Converts a 1-based line number to editor offset.
     * @param editor The target editor
     * @param lineNumber The 1-based line number
     * @param start Whether to get the start or end offset of the line
     * @return The calculated editor offset
     */
    private fun getEditorOffset(editor: Editor, lineNumber: Int, start: Boolean = true): Int {
        val doc = editor.document
        // If lineNumber is beyond the last line, clamp to doc.textLength (append at EOF).
        if (lineNumber > doc.lineCount) {
            return doc.textLength
        }
        val zeroBasedLine = (lineNumber - 1).coerceIn(0, doc.lineCount - 1)
        return if (start) {
            doc.getLineStartOffset(zeroBasedLine)
        } else {
            doc.getLineEndOffset(zeroBasedLine)
        }
    }

    /**
     * Handles errors by logging them and showing notifications to the user.
     * @param message The error message to display
     * @param error The exception that occurred
     */
    private fun handleError(message: String, error: Exception) {
        logger.error(message, error)
        ApplicationManager.getApplication().invokeLater {
            showNotification("$message: ${error.message}", MessageType.ERROR)
        }
    }

    /**
     * Shows a notification balloon in the editor.
     * @param message The message to show
     * @param type The type of message (affects the balloon's appearance)
     */
    private fun showNotification(message: String, type: MessageType) {
        logger.info("Showing notification: $message")
        ApplicationManager.getApplication().invokeLater {
            JBPopupFactory.getInstance()
                .createHtmlTextBalloonBuilder(message, type, null)
                .setFadeoutTime(5000)
                .createBalloon()
                .showInCenterOf(this.targetEditor?.contentComponent)
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

    /**
     * Creates and displays a balloon near the top-right corner of the active editor
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
        val xOffset = component.width - 20  // near the right edge
        val yOffset = 20                    // near the top
        balloon.show(
            RelativePoint(component, Point(xOffset, yOffset)),
            Balloon.Position.atRight
        )
        return balloon
    }
}
