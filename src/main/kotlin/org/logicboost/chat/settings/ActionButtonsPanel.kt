package org.logicboost.chat.settings

import com.intellij.util.ui.JBUI
import java.awt.FlowLayout
import javax.swing.*

/**
 * Utility class to create a panel with Add, Duplicate, and Delete buttons.
 */
class ActionButtonsPanel(
    private val onAdd: () -> Unit,
    private val onDuplicate: () -> Unit,
    private val onDelete: () -> Unit
) : JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)) {

    val addButton: JButton = JButton("Add").apply {
        toolTipText = "Add new LLM configuration"
        addActionListener { onAdd() }
    }

    val duplicateButton: JButton = JButton("Duplicate").apply {
        toolTipText = "Clone selected LLM configuration"
        isEnabled = false
        addActionListener { onDuplicate() }
    }

    val deleteButton: JButton = JButton("Delete").apply {
        toolTipText = "Delete selected LLM configuration"
        isEnabled = false
        addActionListener { onDelete() }
    }

    init {
        border = JBUI.Borders.empty(10, 0, 0, 0)
        add(addButton)
        add(duplicateButton)
        add(deleteButton)
    }

    /**
     * Enables or disables the Duplicate and Delete buttons based on the selection state.
     */
    fun setSelectionState(hasSelection: Boolean) {
        duplicateButton.isEnabled = hasSelection
        deleteButton.isEnabled = hasSelection
    }
}
