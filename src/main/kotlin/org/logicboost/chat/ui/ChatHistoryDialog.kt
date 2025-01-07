package org.logicboost.chat.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import org.logicboost.chat.storage.Chat
import org.logicboost.chat.storage.ChatHistoryStorage
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.*
import javax.swing.event.DocumentEvent

class ChatHistoryPanel(
    private val project: Project,
    private val onChatSelected: (Chat) -> Unit,
    private val parentWindow: Window? = null
) : JPanel(BorderLayout()) {
    private val storage = ChatHistoryStorage.getInstance(project)
    private val searchField = JTextField().apply {
        minimumSize = Dimension(width, 40)
        preferredSize = Dimension(width, 40)
    }
    private val chatListPanel = ChatListPanel()

    // Variables for dragging
    private var dragInitialX = 0
    private var dragInitialY = 0

    init {
        preferredSize = Dimension(400, 500)
        setupUI()
        loadLastPosition()
    }

    private fun setupUI() {
        // Header
        val headerPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(2)
            preferredSize = Dimension(width, 24)

            // Make header draggable
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    dragInitialX = e.x
                    dragInitialY = e.y
                }

                override fun mouseReleased(e: MouseEvent) {
                    saveCurrentPosition()
                }
            })

            addMouseMotionListener(object : MouseMotionAdapter() {
                override fun mouseDragged(e: MouseEvent) {
                    parentWindow?.let { window ->
                        val newX = window.x + e.x - dragInitialX
                        val newY = window.y + e.y - dragInitialY
                        window.location = Point(newX, newY)
                    }
                }
            })

            add(JButton(AllIcons.Actions.Close).apply {
                isBorderPainted = false
                isContentAreaFilled = false
                preferredSize = Dimension(20, 20)
                addActionListener {
                    saveCurrentPosition()
                    parentWindow?.dispose()
                }
            }, BorderLayout.EAST)
        }

        // Search
        val searchPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(10, 5, 10, 5)
            add(searchField, BorderLayout.CENTER)
        }

        val topContainer = JPanel(BorderLayout()).apply {
            add(headerPanel, BorderLayout.NORTH)
            add(searchPanel, BorderLayout.CENTER)
        }
        add(topContainer, BorderLayout.NORTH)

        // Chat list
        chatListPanel.onChatSelected = { chat ->
            saveCurrentPosition()
            onChatSelected(chat)
            parentWindow?.dispose()
        }
        chatListPanel.onChatDeleted = { chat ->
            storage.deleteChat(chat.id)
            updateFilteredList()
        }

        searchField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                updateFilteredList()
            }
        })

        add(JBScrollPane(chatListPanel).apply {
            border = JBUI.Borders.empty(0, 5, 5, 5)
        }, BorderLayout.CENTER)

        updateFilteredList()
    }

    private fun saveCurrentPosition() {
        parentWindow?.let { window ->
            PropertiesComponent.getInstance(project).apply {
                setValue(WINDOW_X_KEY, window.x.toString())
                setValue(WINDOW_Y_KEY, window.y.toString())
            }
        }
    }

    private fun loadLastPosition() {
        parentWindow?.let { window ->
            PropertiesComponent.getInstance(project).apply {
                val lastX = getValue(WINDOW_X_KEY)?.toIntOrNull() ?: -1
                val lastY = getValue(WINDOW_Y_KEY)?.toIntOrNull() ?: -1

                if (lastX != -1 && lastY != -1) {
                    // Ensure the window is visible on screen
                    val screenBounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .defaultScreenDevice.defaultConfiguration.bounds

                    val x = lastX.coerceIn(0, screenBounds.width - window.width)
                    val y = lastY.coerceIn(0, screenBounds.height - window.height)

                    window.location = Point(x, y)
                } else {
                    window.setLocationRelativeTo(null)
                }
            }
        }
    }

    private fun updateFilteredList() {
        val searchText = searchField.text.lowercase()
        val filteredChats = storage.getAllChats().filter {
            it.name.lowercase().contains(searchText)
        }
        chatListPanel.updateChats(filteredChats)
    }

    private inner class ChatListPanel : JPanel() {
        var onChatSelected: ((Chat) -> Unit)? = null
        var onChatDeleted: ((Chat) -> Unit)? = null

        init {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(1)
        }

        fun updateChats(chats: List<Chat>) {
            removeAll()
            chats.forEach { chat ->
                add(ChatListItem(chat))
                add(Box.createVerticalStrut(1))
            }
            revalidate()
            repaint()
        }

        private inner class ChatListItem(private val chat: Chat) : JPanel(BorderLayout()) {
            init {
                border = JBUI.Borders.empty(0, 5)
                background = UIManager.getColor("List.background")
                minimumSize = Dimension(width, 40)
                preferredSize = Dimension(width, 40)
                maximumSize = Dimension(Int.MAX_VALUE, 40)

                // Chat name label that triggers selection
                val nameLabel = JButton(chat.name).apply {
                    isContentAreaFilled = false
                    isBorderPainted = false
                    horizontalAlignment = SwingConstants.LEFT
                    addActionListener {
                        onChatSelected?.invoke(chat)
                    }
                }
                add(nameLabel, BorderLayout.CENTER)

                // Delete button
                val deleteButton = JButton(AllIcons.Actions.Close).apply {
                    preferredSize = Dimension(20, 20)
                    isBorderPainted = false
                    isContentAreaFilled = false
                    addActionListener {
                        onChatDeleted?.invoke(chat)
                    }
                }
                add(deleteButton, BorderLayout.EAST)
            }
        }
    }

    companion object {
        private const val WINDOW_X_KEY = "chat.history.window.x"
        private const val WINDOW_Y_KEY = "chat.history.window.y"

        fun show(project: Project, onChatSelected: (Chat) -> Unit) {
            JDialog().apply {
                defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
                isUndecorated = true
                rootPane.border = BorderFactory.createLineBorder(UIManager.getColor("BorderColor"), 1)
                contentPane.add(ChatHistoryPanel(project, onChatSelected, this))
                pack()
                isModal = true
                isVisible = true
            }
        }
    }
}
