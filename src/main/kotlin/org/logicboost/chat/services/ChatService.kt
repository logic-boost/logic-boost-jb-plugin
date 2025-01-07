package org.logicboost.chat.services

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import org.logicboost.chat.api.OpenAIClientWrapper
import org.logicboost.chat.settings.ChatSettings
import org.logicboost.chat.settings.LLMConfig

class ChatService(private val project: Project) {
    private val logger = Logger.getInstance(ChatService::class.java)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val openAIDispatcher = Dispatchers.IO.limitedParallelism(4)

    private val scope = CoroutineScope(
        SupervisorJob() +
                openAIDispatcher +
                CoroutineExceptionHandler { _, throwable ->
                    logger.error("Unhandled coroutine exception", throwable)
                }
    )

    @Volatile
    private var client: OpenAIClientWrapper? = null

    @Volatile
    private var lastLLMConfig: LLMConfig? = null

    @Volatile
    private var currentJob: Job? = null

    private val NOTIFICATION_GROUP_ID = "LogicBoost.AI.Notifications"

    // Pre-initialize client when ChatService is first created
    init {
        scope.launch {
            try {
                val config = ChatSettings.getInstance().getSelectedLLM()
                if (config != null && config.isEnabled && config.apiKey.isNotBlank()) {
                    getOrCreateClient(config, project)
                }
            } catch (e: Exception) {
                logger.error("Failed to pre-initialize client", e)
            }
        }
    }

    fun interruptCurrentRequest() {
        logger.info("Interrupting current request")
        currentJob?.cancel()
        client?.interrupt()
    }

    fun sendMessage(
        message: String,
        project: Project,
        onLoading: (Boolean) -> Unit,
        onResponse: (String) -> Unit,
        onInterrupted: () -> Unit = {}
    ) {
        if (message.isBlank()) {
            logger.warn("Attempted to send empty message")
            return
        }

        // Immediate LLM config validation
        val llmConfig = ChatSettings.getInstance().getSelectedLLM()
        if (llmConfig == null || !llmConfig.isEnabled) {
            handleLLMError(project, llmConfig)
            return
        }

        if (llmConfig.apiKey.isBlank()) {
            handleError(IllegalStateException("API key not configured"), project)
            return
        }

        // Reset any previous interruption
        client?.resetInterrupt()

        // Set loading state immediately
        ApplicationManager.getApplication().invokeLater {
            onLoading(true)
        }

        currentJob = scope.launch {
            try {
                val openAIClient = getOrCreateClient(llmConfig, project)
                logger.info("Starting streaming completion for message")

                try {
                    openAIClient.streamCompletion(
                        message = message,
                        temperature = llmConfig.temperature,
                        maxTokens = llmConfig.maxTokens.toLong(),
                        topP = llmConfig.topP
                    ).collect { chunk ->
                        ensureActive() // Check if the coroutine is still active
                        ApplicationManager.getApplication().invokeLater {
                            onResponse(chunk)
                        }
                    }
                } catch (e: Exception) {
                    when {
                        e is CancellationException || e.cause is CancellationException -> {
                            logger.info("Request was interrupted")
                            ApplicationManager.getApplication().invokeLater {
                                onInterrupted()
                            }
                        }

                        else -> {
                            logger.error("Error during message streaming", e)
                            handleError(e, project)
                        }
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    logger.error("Error in sendMessage", e)
                    handleError(e, project)
                }
            } finally {
                currentJob = null
                ApplicationManager.getApplication().invokeLater {
                    onLoading(false)
                }
            }
        }
    }

    private fun handleLLMError(project: Project, config: LLMConfig?) {
        val message = when {
            config == null -> "Please select an LLM in Settings -> Logic-Boost"
            !config.isEnabled -> "Selected LLM is disabled. Please select an enabled LLM."
            else -> "LLM configuration error"
        }

        ApplicationManager.getApplication().invokeLater {
            showErrorNotification(project, message)
        }
    }

    private fun handleError(error: Throwable, project: Project) {
        logger.error("Error in chat service", error)

        val message = when {
            error is IllegalStateException && error.message?.contains("API key") == true ->
                "Please configure your API key in Settings -> Logic-Boost"

            error is IllegalStateException && error.message?.contains("No LLM selected") == true ->
                "Please select an LLM in Settings -> Logic-Boost"

            error is IllegalStateException && error.message?.contains("disabled") == true ->
                "The selected LLM is disabled. Please select a different LLM in Settings -> Logic-Boost"

            else -> "Error: ${error.message}"
        }

        ApplicationManager.getApplication().invokeLater {
            showErrorNotification(project, message)
        }
    }

    private fun showErrorNotification(project: Project, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(content, NotificationType.ERROR)
            .notify(project)
    }

    @Synchronized
    private fun getOrCreateClient(llmConfig: LLMConfig, project: Project): OpenAIClientWrapper {
        // Fast path with double-checked locking
        if (client != null && areLLMConfigsEqual(lastLLMConfig!!, llmConfig)) {
            return client!!
        }

        synchronized(this) {
            // Check again in case another thread created the client
            if (client != null && areLLMConfigsEqual(lastLLMConfig!!, llmConfig)) {
                return client!!
            }

            // Cleanup existing client
            client?.close()

            // Create new client
            return OpenAIClientWrapper.create(
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
                client = it
                lastLLMConfig = llmConfig.copy()
            }
        }
    }

    private fun areLLMConfigsEqual(config1: LLMConfig, config2: LLMConfig): Boolean {
        return config1.apiKey == config2.apiKey &&
                config1.apiEndpoint == config2.apiEndpoint &&
                config1.model == config2.model &&
                config1.systemPrompt == config2.systemPrompt
    }

    fun cleanup() {
        logger.info("Cleaning up ChatService")
        scope.launch {
            try {
                // Cancel any ongoing job
                currentJob?.cancel()
                currentJob = null

                // Close the client
                client?.close()
                client = null
                lastLLMConfig = null

                logger.info("ChatService cleanup completed successfully")
            } catch (e: Exception) {
                logger.error("Error during ChatService cleanup", e)
            }
        }
    }
}
