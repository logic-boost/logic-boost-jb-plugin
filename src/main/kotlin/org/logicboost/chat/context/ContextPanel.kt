package org.logicboost.chat.context

import ContextFile
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.logicboost.chat.ui.RoundedPanel
import java.awt.*
import javax.swing.*

/**
 * ContextPanel is responsible for managing and displaying context files in the chat interface.
 * It provides functionality to add and remove context files, displaying them as interactive chips.
 *
 * @property project The IntelliJ project instance this panel belongs to
 */
class ContextPanel(private val project: Project) : JPanel(), Disposable {

    companion object {
        private const val BUTTON_PADDING_VERTICAL = 2
        private const val BUTTON_PADDING_HORIZONTAL = 8
        private val LOG = logger<ContextPanel>()
    }

    // State management
    private val contextFiles = mutableListOf<ContextFile>()
    private val addButton = createAddContextButton()
    private var fileSelectionPanel: FileSelectionPanel? = null
    private var isSearchPanelVisible = false

    init {
        val connection = ApplicationManager.getApplication().messageBus.connect(this)
        connection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
            updatePanelTheme()
        })

        setupPanel()
        if (!project.isDisposed) {
            checkCurrentFile()
        }
    }

    /**
     * Sets up the initial panel layout and appearance
     */
    private fun setupPanel() {
        layout = FlowLayout(FlowLayout.LEFT, JBUI.scale(5), JBUI.scale(2))
        border = JBUI.Borders.empty(2)
        add(addButton)
    }


    /**
     * Creates the "Add Context" button with custom styling and hover effects
     * @return A styled panel containing the add button components
     */
    private fun createAddContextButton(): JPanel {
        val buttonContent = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false

            // Add icon and label
            add(JBLabel(AllIcons.General.Add))

            add(JBLabel("Add Context").apply {
                font = font.deriveFont(Font.PLAIN, 12f)
                foreground = UIManager.getColor("Label.foreground")
            })
        }

        return RoundedPanel(radius = 10).apply {
            layout = BorderLayout()
            border = JBUI.Borders.empty(BUTTON_PADDING_VERTICAL, BUTTON_PADDING_HORIZONTAL)
            add(buttonContent, BorderLayout.CENTER)
            background = UIManager.getColor("ContextChip.background") ?: JBColor.LIGHT_GRAY

            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseEntered(e: java.awt.event.MouseEvent) {
                    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                }

                override fun mouseExited(e: java.awt.event.MouseEvent) {
                    cursor = Cursor.getDefaultCursor()
                }

                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    toggleFileSelectionPanel()
                }
            })
        }
    }

    /**
     * Creates a visual chip representation of a context file
     * @param contextFile The context file to create a chip for
     * @return A styled panel representing the context file
     */
    private fun createContextChip(contextFile: ContextFile): JPanel {
        val chipContent = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false

            // Add file type icon
            add(JBLabel(FileTypeIconProvider.getIcon(contextFile.virtualFile)).apply {
                border = JBUI.Borders.emptyRight(5)
            })

            // Add file name
            add(JBLabel(contextFile.virtualFile.name).apply {
                font = font.deriveFont(Font.PLAIN, 12f)
                foreground = UIManager.getColor("Label.foreground")
            })

            // Add spacing
            add(Box.createHorizontalStrut(5))

            // Add close button
            add(JButton(AllIcons.Actions.Close).apply {
                isBorderPainted = false
                isContentAreaFilled = false
                preferredSize = JBUI.size(16, 16)
                toolTipText = "Remove Context"
                addActionListener { removeContextFile(contextFile) }
            })
        }

        return RoundedPanel(radius = 10).apply {
            layout = BorderLayout()
            border = JBUI.Borders.empty(BUTTON_PADDING_VERTICAL, BUTTON_PADDING_HORIZONTAL)
            add(chipContent, BorderLayout.CENTER)
            background = UIManager.getColor("ContextChip.background") ?: JBColor.LIGHT_GRAY
        }
    }

    /**
     * Toggles the visibility of the file selection panel
     */
    private fun toggleFileSelectionPanel() {
        isSearchPanelVisible = !isSearchPanelVisible

        if (isSearchPanelVisible) {
            fileSelectionPanel = fileSelectionPanel ?: FileSelectionPanel(project, ::handleFileSelection)

            fileSelectionPanel?.let { panel ->
                if (panel !in components) {
                    add(panel, 0)
                }
                panel.isVisible = true
                panel.updateFileList()
                panel.requestSearchFocus()
            }
        } else {
            fileSelectionPanel?.isVisible = false
        }

        revalidate()
        repaint()
    }

    /**
     * Handles the selection of a file from the file selection panel
     */
    private fun handleFileSelection(file: VirtualFile) {
        try {
            file.inputStream.reader().use { reader ->
                addContextFile(file, reader.readText())
            }
            toggleFileSelectionPanel()
        } catch (e: Exception) {
            LOG.error("Error reading file content", e)
        }
    }

    /**
     * Updates the appearance of the add button based on context state
     */
    private fun updateAddButtonAppearance() {
        (addButton.getComponent(0) as? JPanel)?.let { buttonPanel ->
            (buttonPanel.getComponent(1) as? JBLabel)?.apply {
                isVisible = contextFiles.isEmpty()
            }
        }
    }

    /**
     * Adds a new context file to the panel
     */
    private fun addContextFile(file: VirtualFile, content: String) {
        if (contextFiles.none { it.virtualFile.path == file.path }) {
            contextFiles.add(ContextFile(file, content))
            refreshPanel()
        }
    }

    /**
     * Removes a context file from the panel
     */
    private fun removeContextFile(contextFile: ContextFile) {
        contextFiles.remove(contextFile)
        refreshPanel()
    }

    /**
     * Refreshes the entire panel, rebuilding all components
     */
    private fun refreshPanel() {
        removeAll()
        add(addButton)
        contextFiles.forEach { file ->
            add(createContextChip(file))
        }
        updateAddButtonAppearance()
        revalidate()
        repaint()
    }

    /**
     * Checks for and adds the currently open file as context
     * (with disposal check to avoid AlreadyDisposedException)
     */
    private fun checkCurrentFile() {
        // Always confirm project is not disposed before using FileEditorManager
        if (project.isDisposed) {
            return
        }

        FileEditorManager.getInstance(project).selectedFiles.firstOrNull()?.let { file ->
            if (file.exists() && file.isValid) {
                try {
                    file.inputStream.reader().use { reader ->
                        addContextFile(file, reader.readText())
                    }
                } catch (e: Exception) {
                    LOG.error("Error reading current file", e)
                }
            }
        }
    }

    /**
     * Updates the panel theme based on the current IDE theme
     */
    private fun updatePanelTheme() {
        UIUtil.invokeLaterIfNeeded {
            background = UIManager.getColor("Panel.background")
            fileSelectionPanel?.background = UIManager.getColor("Editor.background")
            refreshPanel()
        }
    }

    /**
     * Returns the combined content of all context files
     */
    fun getContextContent(): String? = if (contextFiles.isEmpty()) null else {
        buildString {
            contextFiles.forEach { file ->
                appendLine("File: ${file.virtualFile.name}")
                appendLine(file.content)
                appendLine()
            }
        }
    }

    /**
     * Called when disposing; ensure child panels are also disposed.
     */
    override fun dispose() {
        fileSelectionPanel?.let(Disposer::dispose)
    }
}
