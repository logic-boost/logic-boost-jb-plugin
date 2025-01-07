package org.logicboost.chat.context

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.GridLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.UIManager
import javax.swing.event.DocumentEvent
import kotlin.io.path.Path
import kotlin.io.path.extension

/**
 * Panel responsible for file search and selection functionality.
 */
class FileSelectionPanel(
    private val project: Project,
    private val onFileSelected: (VirtualFile) -> Unit
) : JPanel(BorderLayout()), Disposable {
    private val logger = Logger.getInstance(FileSelectionPanel::class.java);

    companion object {
        private const val DEBOUNCE_DELAY_MS = 300L
        private const val MAX_RESULTS = 8
        private const val PANEL_WIDTH = 400
        private const val PANEL_HEIGHT = 300
        private const val SEARCH_FIELD_HEIGHT = 30
    }

    private val searchAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val fileSearchField = createSearchField()
    private val fileListPanel = createFileListPanel()

    init {
        setupPanel()
        setupSearchListener()
        updateFileList()
    }

    private fun setupPanel() {
        background = UIManager.getColor("Editor.background")
        border = JBUI.Borders.empty(10)
        preferredSize = JBUI.size(PANEL_WIDTH, PANEL_HEIGHT)

        add(fileSearchField, BorderLayout.NORTH)
        add(JBScrollPane(fileListPanel).apply {
            border = JBUI.Borders.empty(5, 0)
            verticalScrollBar.unitIncrement = 16
        }, BorderLayout.CENTER)
    }

    private fun createSearchField() = JBTextField().apply {
        emptyText.text = "Search for files..."
        preferredSize = JBUI.size(PANEL_WIDTH - 20, SEARCH_FIELD_HEIGHT)
    }

    private fun createFileListPanel() = JPanel(GridLayout(0, 1, 2, 2)).apply {
        background = UIManager.getColor("Editor.background")
        border = JBUI.Borders.empty(5)
    }

    private fun setupSearchListener() {
        fileSearchField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                scheduleSearch()
            }
        })
    }

    private fun scheduleSearch() {
        searchAlarm.cancelAllRequests()
        searchAlarm.addRequest({
            val searchText = fileSearchField.text
            ApplicationManager.getApplication().invokeLater {
                updateFileList(searchText)
            }
        }, DEBOUNCE_DELAY_MS)
    }

    fun updateFileList(searchQuery: String = "") {
        fileListPanel.removeAll()

        val files = if (searchQuery.isEmpty()) {
            getNearbyFiles()
        } else {
            searchFiles(searchQuery)
        }

        files.take(MAX_RESULTS).forEach { file ->
            fileListPanel.add(createFileListItem(file))
        }

        fileListPanel.revalidate()
        fileListPanel.repaint()
    }

    private fun getNearbyFiles(): List<VirtualFile> {
        val openFiles = FileEditorManager.getInstance(project).openFiles.toList()
        val currentDir = openFiles.firstOrNull()?.parent
        val nearbyFiles = mutableListOf<VirtualFile>()

        nearbyFiles.addAll(openFiles)

        currentDir?.children?.filter {
            it.isValid && !it.isDirectory && it !in nearbyFiles
        }?.let(nearbyFiles::addAll)

        return nearbyFiles.distinct()
    }

    private fun searchFiles(query: String): List<VirtualFile> {
        val projectRoot = ProjectRootManager.getInstance(project).contentRoots.firstOrNull()
            ?: return emptyList()

        return searchFilesRecursively(projectRoot, query.lowercase(Locale.getDefault()))
    }

    private fun searchFilesRecursively(
        root: VirtualFile,
        query: String,
        results: MutableList<VirtualFile> = mutableListOf()
    ): List<VirtualFile> {
        if (!root.isValid || results.size >= MAX_RESULTS) return results

        if (!root.isDirectory && root.nameMatches(query)) {
            results.add(root)
            return results
        }

        if (root.isDirectory) {
            root.children.forEach { child ->
                if (results.size < MAX_RESULTS) {
                    searchFilesRecursively(child, query, results)
                }
            }
        }

        return results
    }

    private fun VirtualFile.nameMatches(query: String): Boolean {
        return name.lowercase(Locale.getDefault()).contains(query) ||
                Path(path).extension.lowercase(Locale.getDefault()).contains(query)
    }

    private fun createFileListItem(file: VirtualFile): JPanel = JPanel(BorderLayout()).apply {
        background = UIManager.getColor("List.background")
        border = JBUI.Borders.empty(5)

        val icon = FileTypeIconProvider.getIcon(file)
        add(JBLabel(file.name, icon, SwingConstants.LEFT).apply {
            font = font.deriveFont(12f)
        }, BorderLayout.WEST)

        add(JBLabel(file.parent?.name ?: "", SwingConstants.RIGHT).apply {
            foreground = UIManager.getColor("Label.foreground")
            font = font.deriveFont(10f)
        }, BorderLayout.EAST)

        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(createFileItemMouseListener(file))
    }

    private fun createFileItemMouseListener(file: VirtualFile) = object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
            onFileSelected(file)
        }

        override fun mouseEntered(e: MouseEvent) {
            (e.source as? JPanel)?.background = UIManager.getColor("List.selectionBackground")
        }

        override fun mouseExited(e: MouseEvent) {
            (e.source as? JPanel)?.background = UIManager.getColor("List.background")
        }
    }

    fun requestSearchFocus() {
        fileSearchField.requestFocusInWindow()
    }

    override fun dispose() {
        Disposer.dispose(searchAlarm)
    }
}
