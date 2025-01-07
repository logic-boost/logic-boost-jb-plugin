package org.logicboost.chat

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.VisibleForTesting
import org.logicboost.chat.context.ContextPanel
import org.logicboost.chat.model.ChatMessage
import org.logicboost.chat.services.ChatService
import org.logicboost.chat.settings.ChatSettings
import org.logicboost.chat.storage.Chat
import org.logicboost.chat.storage.ChatHistoryStorage
import org.logicboost.chat.ui.ChatContentPanel
import org.logicboost.chat.ui.ChatHeader
import org.logicboost.chat.ui.ChatInputPanel
import org.logicboost.chat.ui.ProcessingIndicator
import java.awt.BorderLayout
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.UIManager

/**
 * ChatPanel is a JPanel that serves as the main interface for the chat feature.
 * It handles the UI components, user interactions, and communication with the chat service.
 *
 * @param project The current IntelliJ project.
 * @param currentChat The current chat session, if any.
 */
class ChatPanel(
    private val project: Project,
    private var currentChat: Chat? = null
) : JPanel(BorderLayout()) {
    private val logger = Logger.getInstance(ChatPanel::class.java)
    private val storage = ChatHistoryStorage.getInstance(project)
    private val chatService = ChatService(project)

    private val chatHeader = ChatHeader(project) { chat -> switchToChat(chat) }
    private val contentPanel = ChatContentPanel(project)
    private var contextPanel = ContextPanel(project)
    private val processingIndicator = ProcessingIndicator()
    private var inputPanel = ChatInputPanel(
        project,
        onSendMessage = { message -> sendMessageInternal(message) },
        onClearChat = { clearChat() },
        onInterrupt = { interruptCurrentRequest() }
    )

    private var currentAssistantMessage: ChatMessage? = null
    private var chunkBuffer = StringBuilder()
    private var chunkCounter = 0
    private val chunkUpdateInterval = 3
    private var isProcessingResponse = false

    private val themeListener = LafManagerListener {
        SwingUtilities.invokeLater {
            // Force complete redraw of the panel
            removeAll()
            setupUI()
            showInitialContent()
            revalidate()
            repaint()
        }
    }

    init {
        // Register for theme change notifications
        ApplicationManager.getApplication().messageBus.connect()
            .subscribe(LafManagerListener.TOPIC, themeListener)

        setupUI()
        showInitialContent()
    }

    /**
     * Sets up the UI components of the ChatPanel.
     */
    private fun setupUI() {
        background = UIManager.getColor("Panel.background")
        isOpaque = true

        // Remove any existing components
        removeAll()

        // Add header
        add(chatHeader, BorderLayout.NORTH)

        // Create and add center panel with content and bottom section
        val centerPanel = JPanel(BorderLayout()).apply {
            background = UIManager.getColor("Panel.background")
            isOpaque = true
            add(contentPanel, BorderLayout.CENTER)
            add(createBottomSection(), BorderLayout.SOUTH) // Create fresh bottom section
        }
        add(centerPanel, BorderLayout.CENTER)
    }

    /**
     * Creates the bottom section of the ChatPanel, which includes the context panel and input panel.
     *
     * @return A JPanel containing the context and input sections.
     */
    private fun createBottomSection(): JPanel {
        // Create fresh instances of contextPanel and inputPanel
        contextPanel = ContextPanel(project)
        inputPanel = ChatInputPanel(
            project,
            onSendMessage = { message -> sendMessageInternal(message) },
            onClearChat = { clearChat() },
            onInterrupt = { interruptCurrentRequest() }
        )

        return JPanel(BorderLayout()).apply {
            background = UIManager.getColor("Panel.background")
            isOpaque = true
            border = JBUI.Borders.empty(0, 10, 10, 10)

            // Context section
            val contextSection = JPanel(BorderLayout()).apply {
                background = UIManager.getColor("Panel.background")
                isOpaque = true
                add(contextPanel, BorderLayout.CENTER)
                border = JBUI.Borders.empty(0, 0, 5, 0)
            }
            add(contextSection, BorderLayout.NORTH)

            // Input section with processing indicator
            val inputSection = JPanel(BorderLayout()).apply {
                background = UIManager.getColor("Panel.background")
                isOpaque = true
                add(processingIndicator, BorderLayout.NORTH)
                add(inputPanel, BorderLayout.CENTER)
            }
            add(inputSection, BorderLayout.CENTER)
        }
    }

    /**
     * Interrupts the current chat request.
     */
    @VisibleForTesting
    private fun interruptCurrentRequest() {
        logger.info("Interrupting current request")
        chatService.interruptCurrentRequest()
        processingIndicator.stopProcessing()
        inputPanel.hideInterruptButton()
        inputPanel.setEnabled(true)
    }

    /**
     * Sends a message to the chat service.
     *
     * @param message The message to be sent.
     */
    private fun sendMessageInternal(message: String) {
        if (message.isEmpty()) return

        contentPanel.chatHistory.shouldLockHeights = true

        inputPanel.setEnabled(false)
        inputPanel.showInterruptButton()

        val settings = ChatSettings.getInstance()
        if (settings.getSelectedLLM() == null) {
            JOptionPane.showMessageDialog(
                this,
                "Please select an LLM in Settings -> Logic-Boost before sending messages.",
                "No LLM Selected",
                JOptionPane.WARNING_MESSAGE
            )
            inputPanel.setEnabled(true)
            inputPanel.hideInterruptButton()
            return
        }

        ensureCurrentChat()

        val contextContent = contextPanel.getContextContent()
        addUserMessage(message)

        chunkCounter = 0
        chunkBuffer.clear()
        isProcessingResponse = true

        processingIndicator.startProcessing()

        currentAssistantMessage = ChatMessage(role = "assistant", content = "")
        currentChat?.let { chat ->
            chat.messages.add(currentAssistantMessage!!)
            storage.updateChat(chat)
        }

        SwingUtilities.invokeLater { updateChatContent() }

        // Create a formatted chat history string
        val chatHistory = currentChat?.messages
            ?.dropLast(1) // Drop the empty assistant message we just added
            ?.joinToString("\n\n") { msg ->
                when (msg.role) {
                    "user" -> "User: ${msg.content}"
                    "assistant" -> "Assistant: ${msg.content}"
                    else -> "${msg.role}: ${msg.content}"
                }
            } ?: ""

        // Combine context, history, and current message
        val fullMessage = buildString {
            if (contextContent != null) {
                append("Context:\n")
                append(contextContent)
                append("\n\n")
            }
            if (chatHistory.isNotEmpty()) {
                append("Chat History:\n")
                append(chatHistory)
                append("\n\n")
            }
            append("User Query: ")
            append(message)
        }

        chatService.sendMessage(
            message = fullMessage,
            project = project,
            onLoading = { isLoading ->
                SwingUtilities.invokeLater {
                    handleLoadingState(isLoading)
                }
            },
            onResponse = { chunk ->
                SwingUtilities.invokeLater {
                    appendAssistantChunk(chunk)
                }
            },
            onInterrupted = {
                SwingUtilities.invokeLater {
                    handleInterruption()
                }
            }
        )
    }

    /**
     * Handles the loading state of the chat service.
     *
     * @param isLoading True if the service is loading, false otherwise.
     */
    private fun handleLoadingState(isLoading: Boolean) {
        if (!isLoading) {
            isProcessingResponse = false
            processingIndicator.stopProcessing()
            inputPanel.hideInterruptButton()

            currentChat?.let { chat ->
                currentAssistantMessage?.let { assistantMessage ->
                    assistantMessage.content += chunkBuffer.toString()
                    storage.updateChat(chat)
                }
            }
            updateChatContent()
            inputPanel.setEnabled(true)

            SwingUtilities.invokeLater {
                this.contentPanel.chatHistory.unlockBlockHeights()
            }
        }
    }

    /**
     * Handles the interruption of the chat service.
     */
    private fun handleInterruption() {
        isProcessingResponse = false
        processingIndicator.stopProcessing()
        inputPanel.hideInterruptButton()
        inputPanel.setEnabled(true)

        currentChat?.let { chat ->
            currentAssistantMessage?.let { assistantMessage ->
                assistantMessage.content += chunkBuffer.toString() + "\n[Request interrupted]"
                storage.updateChat(chat)
            }
        }
        updateChatContent()
    }

    /**
     * Appends a chunk of the assistant's response to the current assistant message.
     *
     * @param chunk The chunk of the assistant's response.
     */
    private fun appendAssistantChunk(chunk: String) {
        logger.info("Appending chunk: '$chunk'")
        currentAssistantMessage?.let { assistantMessage ->
            chunkBuffer.append(chunk)
            chunkCounter++

            if (chunkCounter >= chunkUpdateInterval || !isProcessingResponse) {
                assistantMessage.content += chunkBuffer.toString()
                chunkBuffer.clear()
                chunkCounter = 0

                currentChat?.let { chat ->
                    storage.updateChat(chat)
                }

                updateChatContent()
            }
        }
    }

    /**
     * Adds a user message to the current chat.
     *
     * @param message The user's message.
     */
    private fun addUserMessage(message: String) {
        val userMessage = ChatMessage(role = "user", content = message)
        currentChat?.let { chat ->
            chat.messages.add(userMessage)
            storage.updateChat(chat)
        }
    }

    /**
     * Ensures that there is a current chat session. If not, it creates a new one.
     */
    private fun ensureCurrentChat() {
        if (currentChat == null) {
            currentChat = storage.createChat("New Chat")
        }
    }

    /**
     * Clears the current chat session.
     */
    @VisibleForTesting
    private fun clearChat() {
        currentChat?.let { chat ->
            chat.messages.clear()
            storage.updateChat(chat)
            showWelcomeMessage()
        }
    }

    /**
     * Shows a welcome message if the current chat session is empty.
     */
    private fun showWelcomeMessage() {
        if (currentChat?.messages?.isEmpty() == true) {
            val welcomeMessage = ChatMessage(
                role = "assistant",
                content = "Hello! I'm your AI coding assistant. How can I help you today?\n\n"
            )
            currentChat?.let { chat ->
                chat.messages.add(welcomeMessage)
                storage.updateChat(chat)
                updateChatContent()
            }
        }
    }

    /**
     * Updates the content of the chat panel to reflect the current chat session.
     */
    private fun updateChatContent() {
        currentChat?.let { chat ->
            contentPanel.updateContent(chat.messages)
        }
    }

    /**
     * Switches to a different chat session.
     *
     * @param chat The chat session to switch to.
     */
    private fun switchToChat(chat: Chat) {
        currentChat = chat
        updateChatContent()
    }

    /**
     * Shows the initial content of the chat panel.
     */
    private fun showInitialContent() {
        updateChatContent()
        if (currentChat?.messages?.isEmpty() == true) {
            showWelcomeMessage()
        }
    }

    /**
     * Sends a message programmatically to the chat service.
     *
     * @param message The message to be sent.
     */
    fun sendMessageProgrammatically(message: String) {
        sendMessageInternal(message)
    }

    /**
     * Cleans up resources used by the ChatPanel.
     */
    fun cleanup() {
        logger.info("Cleaning up ChatPanel")
        try {
            // Interrupt any ongoing request
            interruptCurrentRequest()

            // Cleanup chat service
            chatService.cleanup()

            logger.info("ChatPanel cleanup completed successfully")
        } catch (e: Exception) {
            logger.error("Error during ChatPanel cleanup", e)
        }
    }
}
