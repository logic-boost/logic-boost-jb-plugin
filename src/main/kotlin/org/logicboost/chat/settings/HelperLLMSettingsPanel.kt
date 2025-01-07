package org.logicboost.chat.settings

import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

/**
 * Panel for Helper LLM specific settings.
 */
class HelperLLMSettingsPanel : BaseSettingsPanel<LLMConfig>() {
    private var currentConfig: LLMConfig? = null

    private val apiEndpointField: JTextField = UIComponentFactory.createTextField(
        toolTip = "API endpoint URL",
        columns = 30
    )
    private val completionCheckbox: JCheckBox = JCheckBox("Enable Code Completion").apply {
        toolTipText = "Enable AI-powered code completion suggestions"
    }
    private val commentsCheckbox: JCheckBox = JCheckBox("Enable Code Comments").apply {
        toolTipText = "Enable AI-generated code documentation"
    }

    fun createPanel(): JComponent {
        return JPanel(GridBagLayout()).apply {
            val gbc = GridBagConstraints().apply {
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.NORTHWEST
                weightx = 1.0
                gridx = 0
                gridy = 0
            }

            // API Configuration
            add(createSettingsGroup("API Configuration") {
                addLabeledComponent("API Key:", apiKeyField, it)
                addLabeledComponent("API Endpoint:", apiEndpointField, it)
                addLabeledComponent("Model:", modelField, it)
            }, gbc)

            // Model Parameters
            gbc.gridy++
            add(createSettingsGroup("Model Parameters") {
                addLabeledComponent("Temperature:", temperatureSpinner, it)
                addLabeledComponent("Max Tokens:", maxTokensSpinner, it)
                addLabeledComponent("Top P:", topPSpinner, it)
            }, gbc)

            // System Prompt
            gbc.gridy++
            add(createSettingsGroup("System Prompt") {
                add(UIComponentFactory.createScrollPane(systemPromptArea), it)
            }, gbc)

            // Preview
            gbc.gridy++

            // Enable/Disable toggle
            gbc.gridy++
            add(enabledCheckbox, gbc)

            // Add vertical glue
            gbc.gridy++
            gbc.weighty = 1.0
            gbc.fill = GridBagConstraints.BOTH
            add(Box.createVerticalGlue(), gbc)
        }
    }

    override fun setConfig(config: LLMConfig?) {
        currentConfig = config
        config?.let {
            apiKeyField.text = it.apiKey
            apiEndpointField.text = it.apiEndpoint
            modelField.text = it.model
            temperatureSpinner.value = it.temperature
            maxTokensSpinner.value = it.maxTokens
            topPSpinner.value = it.topP
            systemPromptArea.text = it.systemPrompt
            enabledCheckbox.isSelected = it.isEnabled
            this.isEnabled = true
        } ?: run {
            this.isEnabled = false
            clearFields()
        }
    }

    private fun clearFields() {
        apiKeyField.text = ""
        apiEndpointField.text = ""
        modelField.text = ""
        temperatureSpinner.value = 0
        maxTokensSpinner.value = 8000
        topPSpinner.value = 1.0
        systemPromptArea.text = ""
        enabledCheckbox.isSelected = false
    }

    override fun updateConfig(config: LLMConfig) {
        config.apiKey = String(apiKeyField.password).trim()  // Trim to handle whitespace
        config.apiEndpoint = apiEndpointField.text.trim()
        config.model = modelField.text.trim()
        config.temperature = temperatureSpinner.value as Double
        config.maxTokens = maxTokensSpinner.value as Int
        config.topP = topPSpinner.value as Double
        config.systemPrompt = systemPromptArea.text.trim()
        config.isEnabled = enabledCheckbox.isSelected
    }

    override fun isModified(): Boolean = currentConfig?.let { config ->
        config.apiKey != String(apiKeyField.password) ||
                config.apiEndpoint != apiEndpointField.text ||
                config.model != modelField.text ||
                config.temperature != temperatureSpinner.value as Double ||
                config.maxTokens != maxTokensSpinner.value as Int ||
                config.topP != topPSpinner.value as Double ||
                config.systemPrompt != systemPromptArea.text ||
                config.isEnabled != enabledCheckbox.isSelected
    } ?: false
}
