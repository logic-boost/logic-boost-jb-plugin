package org.logicboost.chat.settings

/**
 * Data class representing a Chat LLM configuration.
 */
data class LLMConfig(
    var name: String = "",
    var apiKey: String = "",
    var apiEndpoint: String = "https://api.openai.com/v1",
    var model: String = "gpt-4o-mini",
    var temperature: Double = 0.0,
    var maxTokens: Int = 8000,
    var topP: Double = 1.0,
    var systemPrompt: String = "",
    var isEnabled: Boolean = true,
    var forSmartFunctions: Boolean = false
)


/**
 * Interface defining common functionality for LLM settings configurables.
 */
interface LLMConfigurable {
    /**
     * Updates the UI components from the current configuration
     */
    fun updateUIFromConfig()

    /**
     * Updates the configuration from the current UI state
     */
    fun updateConfigFromUI()
}
