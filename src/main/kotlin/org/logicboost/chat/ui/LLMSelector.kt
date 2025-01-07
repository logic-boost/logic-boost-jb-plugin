package org.logicboost.chat.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.JBUI
import org.logicboost.chat.settings.ChatSettings
import org.logicboost.chat.settings.SettingsChangeListener
import org.logicboost.chat.settings.SettingsChangeNotifier
import java.awt.BorderLayout
import javax.swing.JPanel

class LLMSelector(private val project: Project) : JPanel(), Disposable {
    private val comboBox = ComboBox<String>()
    private val connection = ApplicationManager.getApplication().messageBus.connect()
    private var isUpdating = false

    init {
        layout = BorderLayout()
        border = JBUI.Borders.empty()

        // Make the main panel transparent
        isOpaque = false
        background = null

        comboBox.apply {
            isSwingPopup = false
            preferredSize = JBUI.size(150, 25)
            // Make combo box transparent
            isOpaque = false
            background = null
        }

        add(comboBox, BorderLayout.CENTER)

        setupSubscription()
        refreshLLMList()

        comboBox.addActionListener {
            if (isUpdating) return@addActionListener
            val selected = comboBox.selectedItem as? String
            if (selected != null) {
                ChatSettings.getInstance().setSelectedLLM(selected)
            }
        }
    }

    private fun setupSubscription() {
        ApplicationManager.getApplication().invokeLater {
            connection.subscribe(SettingsChangeNotifier.TOPIC, object : SettingsChangeListener {
                override fun onSettingsChanged() {
                    refreshLLMList()
                }
            })
        }
    }

    private fun refreshLLMList() {
        val selectedItem = comboBox.selectedItem
        isUpdating = true
        try {
            comboBox.removeAllItems()

            val settings = ChatSettings.getInstance()
            settings.llmConfigs
                .filter { it.isEnabled }
                .forEach { comboBox.addItem(it.name) }

            when {
                settings.selectedLLMName.isNotBlank() -> comboBox.selectedItem = settings.selectedLLMName
                selectedItem != null -> comboBox.selectedItem = selectedItem
                else -> comboBox.selectedIndex = -1
            }
        } finally {
            isUpdating = false
        }
    }

    override fun dispose() {
        connection.dispose()
    }
}
