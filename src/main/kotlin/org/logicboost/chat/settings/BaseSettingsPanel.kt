package org.logicboost.chat.settings

import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

/**
 * Abstract base class for settings panels, encapsulating common UI components and logic.
 */
abstract class BaseSettingsPanel<T> : JPanel(GridBagLayout()) {
    protected val nameField: JBTextField = UIComponentFactory.createTextField(
        toolTip = "Enter a unique name for this configuration",
        columns = 20
    )
    protected val apiKeyField = UIComponentFactory.createPasswordField("Enter your API key")
    protected val modelField = UIComponentFactory.createTextField("Enter the model name")
    protected val temperatureSpinner = UIComponentFactory.createSpinner(
        SpinnerNumberModel(0.7, 0.0, 2.0, 0.1),
        "Set the temperature for response randomness (0.0 - 2.0)",
        columns = 4
    )
    protected val maxTokensSpinner = UIComponentFactory.createSpinner(
        SpinnerNumberModel(8000, 1, 32000, 1000),
        "Set the maximum number of tokens for response",
        columns = 6
    )
    protected val topPSpinner = UIComponentFactory.createSpinner(
        SpinnerNumberModel(1.0, 0.0, 1.0, 0.1),
        "Set the top P value for response diversity",
        columns = 4
    )
    protected val systemPromptArea = UIComponentFactory.createTextArea(
        rows = 5,
        columns = 30,
        toolTip = "Enter the system prompt"
    )
    protected val enabledCheckbox = JCheckBox("Enabled").apply {
        toolTipText = "Enable or disable this configuration"
    }
    protected val statusLabel = JLabel(" ")

    init {
        border = JBUI.Borders.empty(10)
    }

    /**
     * Creates a titled settings group.
     */
    protected fun createSettingsGroup(title: String, init: JPanel.(GridBagConstraints) -> Unit): JPanel {
        return JPanel(GridBagLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                JBUI.Borders.empty(10)
            )
            val gbc = GridBagConstraints().apply {
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.NORTHWEST
                weightx = 1.0
                gridx = 0
                gridy = 0
            }
            this.init(gbc)
        }
    }

    /**
     * Adds a labeled component to the panel.
     */
    protected fun JPanel.addLabeledComponent(label: String, component: JComponent, gbc: GridBagConstraints) {
        gbc.gridy++
        gbc.insets = JBUI.insets(0, 0, 2, 0)
        add(JLabel(label), gbc)

        gbc.gridy++
        gbc.insets = JBUI.insets(0, 0, 8, 0)
        add(component, gbc)
    }

    /**
     * Abstract methods to be implemented by subclasses.
     */
    abstract fun setConfig(config: T?)
    abstract fun updateConfig(config: T)
    abstract fun isModified(): Boolean
}
