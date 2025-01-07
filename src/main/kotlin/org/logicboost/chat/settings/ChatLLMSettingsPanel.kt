package org.logicboost.chat.settings

import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

/**
 * Panel for Chat LLM specific settings.
 */
class ChatLLMSettingsPanel : BaseSettingsPanel<LLMConfig>() {
    private var mainPanel: JPanel? = null
    private var currentConfig: LLMConfig? = null

    private val apiEndpointField: JTextField = UIComponentFactory.createTextField(
        toolTip = "Enter the API endpoint URL",
        columns = 30
    )

    private val testConnectionButton: JButton = JButton("Test Connection").apply {
        addActionListener { testConnection() }
    }

    private val forSmartFunctionsCheckbox = JCheckBox("Use for Smart Functions").apply {
        toolTipText = "Enable this LLM for smart function processing (only one LLM can be enabled for smart functions)"
        addActionListener {
            if (isSelected) {
                // Disable smart functions for all other LLMs
                ChatSettings.getInstance().llmConfigs.forEach { config ->
                    if (config.name != currentConfig?.name) {
                        config.forSmartFunctions = false
                    }
                }
            }
        }
    }

    fun createPanel(): JComponent {
        mainPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(10)
            add(createConfigurationForm(), BorderLayout.CENTER)
            add(statusLabel, BorderLayout.SOUTH)
        }

        // Initialize with empty state
        setConfig(null)

        return mainPanel!!
    }

    private fun createConfigurationForm(): JPanel {
        return JPanel(GridBagLayout()).apply {
            val gbc = GridBagConstraints().apply {
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.NORTHWEST
                weightx = 1.0
                gridx = 0
                gridy = 0
            }

            // API Settings Group
            add(createSettingsGroup("API Settings") {
                addLabeledComponent("Name:", nameField, it)
                addLabeledComponent("API Key:", apiKeyField, it)
                addLabeledComponent("API Endpoint:", apiEndpointField, it)
                addLabeledComponent("", testConnectionButton, it)
            }, gbc)

            // Model Settings Group
            gbc.gridy++
            add(createSettingsGroup("Model Settings") {
                addLabeledComponent("Model:", modelField, it)
                addLabeledComponent("Temperature:", temperatureSpinner, it)
                addLabeledComponent("Max Tokens:", maxTokensSpinner, it)
                addLabeledComponent("Top P:", topPSpinner, it)
            }, gbc)

            // System Prompt Group
            gbc.gridy++
            add(createSettingsGroup("System Prompt") {
                add(UIComponentFactory.createScrollPane(systemPromptArea), it)
            }, gbc)

            // Configuration Options Group
            gbc.gridy++
            add(createSettingsGroup("Configuration Options") {
                val optionsPanel = JPanel(GridBagLayout())
                optionsPanel.add(enabledCheckbox, GridBagConstraints().apply {
                    gridx = 0
                    gridy = 0
                    anchor = GridBagConstraints.WEST
                })
                optionsPanel.add(Box.createHorizontalStrut(20), GridBagConstraints().apply {
                    gridx = 1
                    gridy = 0
                })
                optionsPanel.add(forSmartFunctionsCheckbox, GridBagConstraints().apply {
                    gridx = 2
                    gridy = 0
                    anchor = GridBagConstraints.WEST
                })
                add(optionsPanel, it)

                // Add description label for Smart Functions
                val descriptionLabel =
                    JLabel("<html><i>Smart Functions: This LLM will be used exclusively for processing smart function tasks.</i></html>")
                descriptionLabel.foreground = UIManager.getColor("Label.infoForeground")
                descriptionLabel.border = JBUI.Borders.empty(5, 0, 0, 0)
                it.gridy++
                add(descriptionLabel, it)
            }, gbc)

            // Add vertical glue for proper spacing
            gbc.gridy++
            gbc.weighty = 1.0
            gbc.fill = GridBagConstraints.BOTH
            add(Box.createVerticalGlue(), gbc)
        }
    }

    override fun setConfig(config: LLMConfig?) {
        currentConfig = config
        mainPanel?.let { panel ->
            panel.isVisible = config != null

            if (config != null) {
                nameField.text = config.name
                apiKeyField.text = config.apiKey
                apiEndpointField.text = config.apiEndpoint
                modelField.text = config.model
                temperatureSpinner.value = config.temperature
                maxTokensSpinner.value = config.maxTokens
                topPSpinner.value = config.topP
                systemPromptArea.text = config.systemPrompt
                enabledCheckbox.isSelected = config.isEnabled
                forSmartFunctionsCheckbox.isSelected = config.forSmartFunctions

                // Enable all components
                setComponentsEnabled(true)
            } else {
                clearFields()
                setComponentsEnabled(false)
            }
        }
    }

    private fun setComponentsEnabled(enabled: Boolean) {
        nameField.isEnabled = enabled
        apiKeyField.isEnabled = enabled
        apiEndpointField.isEnabled = enabled
        modelField.isEnabled = enabled
        temperatureSpinner.isEnabled = enabled
        maxTokensSpinner.isEnabled = enabled
        topPSpinner.isEnabled = enabled
        systemPromptArea.isEnabled = enabled
        enabledCheckbox.isEnabled = enabled
        forSmartFunctionsCheckbox.isEnabled = enabled
        testConnectionButton.isEnabled = enabled
    }

    private fun clearFields() {
        nameField.text = ""
        apiKeyField.text = ""
        apiEndpointField.text = ""
        modelField.text = ""
        temperatureSpinner.value = 0
        maxTokensSpinner.value = 8000
        topPSpinner.value = 1.0
        systemPromptArea.text = ""
        enabledCheckbox.isSelected = false
        forSmartFunctionsCheckbox.isSelected = false
        statusLabel.text = " "
    }

    override fun updateConfig(config: LLMConfig) {
        config.apply {
            name = nameField.text.trim()
            apiKey = String(apiKeyField.password).trim()
            apiEndpoint = apiEndpointField.text.trim()
            model = modelField.text.trim()
            temperature = temperatureSpinner.value as Double
            maxTokens = maxTokensSpinner.value as Int
            topP = topPSpinner.value as Double
            systemPrompt = systemPromptArea.text.trim()
            isEnabled = enabledCheckbox.isSelected
            forSmartFunctions = forSmartFunctionsCheckbox.isSelected
        }
    }

    override fun isModified(): Boolean = currentConfig?.let { config ->
        config.name != nameField.text ||
                config.apiKey != String(apiKeyField.password) ||
                config.apiEndpoint != apiEndpointField.text ||
                config.model != modelField.text ||
                config.temperature != temperatureSpinner.value as Double ||
                config.maxTokens != maxTokensSpinner.value as Int ||
                config.topP != topPSpinner.value as Double ||
                config.systemPrompt != systemPromptArea.text ||
                config.isEnabled != enabledCheckbox.isSelected ||
                config.forSmartFunctions != forSmartFunctionsCheckbox.isSelected
    } ?: false

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        setComponentsEnabled(enabled && currentConfig != null)
    }

    private fun testConnection() {
        // TODO: Implement connection test logic
        statusLabel.text = "Testing connection..."
    }

    fun dispose() {
        mainPanel = null
        currentConfig = null
    }
}
