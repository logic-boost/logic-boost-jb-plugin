package org.logicboost.chat.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Configurable class for Helper LLM settings.
 */
class HelperSettingsConfigurable : Configurable, LLMConfigurable {
    private var mainPanel: JPanel? = null
    private val settingsPanel = HelperLLMSettingsPanel()

    override fun createComponent(): JComponent {
        mainPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(10)
            add(JBScrollPane(settingsPanel.createPanel()).apply {
                border = JBUI.Borders.empty()
                verticalScrollBar.unitIncrement = 16
            }, BorderLayout.CENTER)
        }
        updateUIFromConfig()
        return mainPanel!!
    }

    override fun updateUIFromConfig() {
        settingsPanel.setConfig(ChatSettings.getInstance().llmHelper)
    }

    override fun updateConfigFromUI() {
        settingsPanel.updateConfig(ChatSettings.getInstance().llmHelper)
    }


    override fun isModified(): Boolean = settingsPanel.isModified()

    override fun apply() {
        updateConfigFromUI()
    }

    override fun reset() {
        updateUIFromConfig()
    }

    override fun disposeUIResources() {
        mainPanel = null
    }

    override fun getDisplayName(): String = "Helper Settings"

    override fun getHelpTopic(): String? = null
}
