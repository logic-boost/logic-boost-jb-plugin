package org.logicboost.chat.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.ui.SearchTextField
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import javax.swing.*
import java.awt.*
import javax.swing.event.DocumentEvent

/**
 * Main configurable class integrating Chat and Helper settings panels.
 */
class ChatSettingsConfigurable : Configurable, LLMConfigurable {
    private var mainPanel: JPanel? = null
    private val llmListModel = DefaultListModel<String>()
    private lateinit var llmList: JList<String>
    private lateinit var searchField: SearchTextField
    private lateinit var statusLabel: JLabel

    private lateinit var actionButtonsPanel: ActionButtonsPanel
    private lateinit var helperConfigurablePanel: HelperSettingsConfigurable
    private val chatSettingsPanel = ChatLLMSettingsPanel()

    private var currentConfig: LLMConfig? = null

    /**
     * Creates the main UI component for the settings configurable.
     * Initializes the UI state and returns the main panel.
     *
     * @return the main panel containing the settings UI
     */
    override fun createComponent(): JComponent {
        mainPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(10)
            add(createTabbedPane(), BorderLayout.CENTER)
        }

        // Initialize UI state
        refreshLLMList()
        actionButtonsPanel.setSelectionState(false)
        updateConfigPanelState(false)

        return mainPanel!!
    }

    /**
     * Creates a tabbed pane with tabs for Chat LLM Settings and Helper Settings.
     *
     * @return the tabbed pane containing the settings panels
     */
    private fun createTabbedPane(): JComponent {
        helperConfigurablePanel = HelperSettingsConfigurable()
        statusLabel = JLabel(" ")

        return JBTabbedPane().apply {
            addTab("Chat LLM Settings", createChatSettingsTab())
            addTab("Helper Settings", helperConfigurablePanel.createComponent())
        }
    }

    /**
     * Creates the tab for Chat LLM Settings with proper split pane behavior.
     */
    private fun createChatSettingsTab(): JComponent {
        val rightPanel = JPanel(BorderLayout()).apply {
            add(chatSettingsPanel.createPanel(), BorderLayout.CENTER)
            add(statusLabel, BorderLayout.SOUTH)
        }

        return JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
            leftComponent = createLLMListPanel()
            rightComponent = rightPanel
            dividerLocation = 480  // Match the preferredSize width of the left panel
            resizeWeight = 0.0  // Keep left component size fixed during resize
        }
    }

    /**
     * Creates the LLM list panel with proper sizing to fit all buttons.
     */
    private fun createLLMListPanel(): JComponent {
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(10)
            preferredSize = Dimension(480, -1)  // Increased width to accommodate all buttons
            minimumSize = Dimension(480, -1)    // Ensure minimum width is maintained

            // Search field
            searchField = SearchTextField().apply {
                textEditor.document.addDocumentListener(object : DocumentAdapter() {
                    override fun textChanged(e: DocumentEvent) {
                        filterLLMList(textEditor.text)
                    }
                })
            }
            add(searchField, BorderLayout.NORTH)

            // LLM List
            llmList = JList(llmListModel).apply {
                selectionMode = ListSelectionModel.SINGLE_SELECTION
                cellRenderer = createLLMListRenderer()
                visibleRowCount = 8
                addListSelectionListener { e ->
                    if (!e.valueIsAdjusting) {
                        val hasSelection = !isSelectionEmpty
                        actionButtonsPanel.setSelectionState(hasSelection)
                        if (hasSelection) {
                            updateSelectedConfig()
                            updateConfigPanelState(true)
                        } else {
                            currentConfig = null
                            updateConfigPanelState(false)
                        }
                    }
                }
            }

            val listScrollPane = JScrollPane(llmList).apply {
                verticalScrollBar.unitIncrement = 16
                // Ensure scroll pane also maintains minimum width
                minimumSize = Dimension(480, -1)
            }
            add(listScrollPane, BorderLayout.CENTER)

            // Action buttons
            actionButtonsPanel = ActionButtonsPanel(
                onAdd = { addNewLLM() },
                onDuplicate = { duplicateLLM() },
                onDelete = { deleteLLM() }
            )
            add(actionButtonsPanel, BorderLayout.SOUTH)
        }
    }

    /**
     * Creates a simplified list cell renderer for the LLM list.
     */
    private fun createLLMListRenderer(): ListCellRenderer<String> {
        return object : JPanel(), ListCellRenderer<String> {
            private val nameLabel = JLabel()
            private val statusIcon = JLabel()

            init {
                layout = BorderLayout(5, 0)
                border = JBUI.Borders.empty(5)
                isOpaque = true
                add(statusIcon, BorderLayout.WEST)
                add(nameLabel, BorderLayout.CENTER)
            }

            override fun getListCellRendererComponent(
                list: JList<out String>?,
                value: String?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val config = ChatSettings.getInstance().llmConfigs.find { it.name == value }

                nameLabel.text = value
                statusIcon.icon = when {
                    config?.isEnabled == true -> UIManager.getIcon("OptionPane.informationIcon")
                    else -> UIManager.getIcon("OptionPane.errorIcon")
                }

                background = if (isSelected) list?.selectionBackground else list?.background
                foreground = if (isSelected) list?.selectionForeground else list?.foreground

                return this
            }
        }
    }

    /**
     * Filters the LLM list based on the search text.
     * Updates the list model with filtered configurations.
     *
     * @param searchText the text to filter the LLM list by
     */
    private fun filterLLMList(searchText: String) {
        val selectedValue = llmList.selectedValue
        llmListModel.clear()

        val filteredConfigs = if (searchText.isEmpty()) {
            ChatSettings.getInstance().llmConfigs
        } else {
            ChatSettings.getInstance().llmConfigs.filter {
                it.name.contains(searchText, ignoreCase = true) ||
                        it.model.contains(searchText, ignoreCase = true)
            }
        }
        filteredConfigs.forEach { llmListModel.addElement(it.name) }

        // Restore selection if item still exists in filtered list
        if (selectedValue != null && llmListModel.contains(selectedValue)) {
            llmList.setSelectedValue(selectedValue, true)
        } else {
            llmList.clearSelection()
            updateSelectedConfig()
        }
    }

    /**
     * Adds a new LLM configuration.
     * Prompts the user to enter a name for the new LLM and adds it to the list.
     */
    private fun addNewLLM() {
        val name = JOptionPane.showInputDialog(mainPanel, "Enter LLM Name:")
        if (!name.isNullOrBlank()) {
            val newConfig = LLMConfig(name = name)
            ChatSettings.getInstance().llmConfigs.add(newConfig)
            refreshLLMList()
            llmList.setSelectedValue(name, true)
        }
    }

    /**
     * Duplicates the currently selected LLM configuration.
     * Creates a copy of the selected LLM with a unique name and adds it to the list.
     */
    private fun duplicateLLM() {
        currentConfig?.let { config ->
            var newName = "${config.name} (copy)"
            var counter = 1
            while (ChatSettings.getInstance().llmConfigs.any { it.name == newName }) {
                newName = "${config.name} (copy ${counter++})"
            }

            val newConfig = config.copy(name = newName)
            ChatSettings.getInstance().llmConfigs.add(newConfig)
            refreshLLMList()
            llmList.setSelectedValue(newName, true)
        }
    }

    /**
     * Deletes the currently selected LLM configuration.
     * Prompts the user for confirmation and removes the selected LLM from the list.
     */
    private fun deleteLLM() {
        val selectedName = llmList.selectedValue
        if (selectedName != null) {
            val result = JOptionPane.showConfirmDialog(
                mainPanel,
                "Are you sure you want to delete '$selectedName'?",
                "Confirm Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            )

            if (result == JOptionPane.YES_OPTION) {
                ChatSettings.getInstance().llmConfigs.removeIf { it.name == selectedName }
                refreshLLMList()
                updateConfigPanelState(false)
            }
        }
    }

    /**
     * Updates the currently selected LLM configuration.
     * Sets the current configuration to the selected LLM and updates the chat settings panel.
     */
    private fun updateSelectedConfig() {
        val selectedName = llmList.selectedValue
        currentConfig = ChatSettings.getInstance().llmConfigs.find { it.name == selectedName }
        chatSettingsPanel.setConfig(currentConfig)
    }

    /**
     * Updates the state of the configuration panel.
     * Enables or disables the panel based on whether an LLM is selected.
     *
     * @param enabled true if the panel should be enabled, false otherwise
     */
    private fun updateConfigPanelState(enabled: Boolean) {
        chatSettingsPanel.setConfig(if (enabled) currentConfig else null)
        statusLabel.text = if (enabled) " " else "Select an LLM configuration"
    }

    /**
     * Refreshes the LLM list.
     * Updates the list model with the current LLM configurations.
     */
    private fun refreshLLMList() {
        val selectedValue = llmList.selectedValue
        llmListModel.clear()
        ChatSettings.getInstance().llmConfigs.forEach {
            llmListModel.addElement(it.name)
        }

        // Select first element by default if nothing was previously selected
        if (selectedValue != null && llmListModel.contains(selectedValue)) {
            llmList.setSelectedValue(selectedValue, true)
        } else if (llmListModel.size() > 0) {
            llmList.selectedIndex = 0  // Select first element
            updateSelectedConfig()
            updateConfigPanelState(true)
        } else {
            llmList.clearSelection()
            updateConfigPanelState(false)
        }
    }

    /**
     * Updates the UI from the current configuration.
     * Refreshes the LLM list and updates the selected configuration.
     */
    override fun updateUIFromConfig() {
        refreshLLMList()
        updateSelectedConfig()
        helperConfigurablePanel.updateUIFromConfig()
    }

    /**
     * Updates the configuration from the UI.
     * Applies changes made in the chat settings panel to the current configuration.
     */
    override fun updateConfigFromUI() {
        currentConfig?.let { config ->
            chatSettingsPanel.updateConfig(config)
            refreshLLMList()
            llmList.setSelectedValue(config.name, true)
        }
    }

    /**
     * Checks if the settings have been modified.
     * Returns true if either the chat settings panel or the helper settings panel has been modified.
     *
     * @return true if the settings have been modified, false otherwise
     */
    override fun isModified(): Boolean {
        return chatSettingsPanel.isModified() || helperConfigurablePanel.isModified()
    }

    /**
     * Applies the current settings.
     * Updates the configuration from the UI, applies changes in the helper settings panel,
     * and notifies that the settings have changed.
     */
    override fun apply() {
        updateConfigFromUI()
        helperConfigurablePanel.apply()
        ChatSettings.getInstance().notifySettingsChanged()
        statusLabel.text = "Settings saved successfully"
    }

    /**
     * Resets the settings to their last saved state.
     * Updates the UI from the current configuration and resets the helper settings panel.
     */
    override fun reset() {
        updateUIFromConfig()
        helperConfigurablePanel.reset()
        statusLabel.text = " "
    }

    /**
     * Returns the display name for the settings configurable.
     *
     * @return the display name "LogicBoost AI Settings"
     */
    override fun getDisplayName(): String = "LogicBoost AI Settings"

    /**
     * Returns the help topic for the settings configurable.
     * Currently returns null as no help topic is specified.
     *
     * @return the help topic (null in this case)
     */
    override fun getHelpTopic(): String? = null

    /**
     * Disposes of the UI resources.
     * Clears the main panel and disposes of the helper settings panel resources.
     */
    override fun disposeUIResources() {
        mainPanel = null
        helperConfigurablePanel.disposeUIResources()
    }
}
