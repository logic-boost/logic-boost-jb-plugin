package org.logicboost.chat.markdown

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import org.logicboost.chat.actions.ChangesAction
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.*
import javax.swing.text.html.HTMLEditorKit

class MarkdownPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val logger = Logger.getInstance(MarkdownPanel::class.java)
    private val markdownParser = MarkdownParser(GFMFlavourDescriptor())
    var shouldLockHeights = false

    // We keep track of code scroll panes so we can disable/enable them
    private val codeScrollPanes = mutableListOf<JBScrollPane>()

    // Main content container (no scroll pane here—there's one in ChatPanel above)
    private val mainContent = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = UIUtil.getPanelBackground()
        alignmentX = Component.LEFT_ALIGNMENT
        border = JBUI.Borders.empty(8)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private var renderScope = CoroutineScope(Dispatchers.Default.limitedParallelism(2) + SupervisorJob())
    private val contentUpdateCounter = AtomicInteger(0)

    init {
        layout = BorderLayout()
        background = UIUtil.getPanelBackground()

        // Just add mainContent; no scroll pane here
        add(mainContent, BorderLayout.CENTER)
    }


    /**
     * Replace current mainContent with new blocks (text or code).
     */
    private fun updateContent(blocks: List<MarkdownBlock>) {
        val tempContent = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = UIUtil.getPanelBackground()
            alignmentX = Component.LEFT_ALIGNMENT
        }

        blocks.forEachIndexed { index, block ->
            val component = when (block) {
                is MarkdownBlock.Text -> createMarkdownBlock(block.html)
                is MarkdownBlock.Code -> createCodeBlock(block.code, block.language, block.showApplyButton)
            }
            tempContent.add(component)

            if (index < blocks.size - 1) {
                tempContent.add(Box.createRigidArea(Dimension(0, 8)))
            }
        }

        SwingUtilities.invokeLater {
            mainContent.removeAll()
            mainContent.add(tempContent)
            mainContent.revalidate()
            mainContent.repaint()
        }
    }


    /**
     * Renders the given markdown string.
     * While updating:
     *  - We disable scroll in newly created code blocks.
     *  - We lock the heights of each text/code block.
     * After final rendering completes, we re-enable code scrolling.
     */
    fun setMarkdownContent(markdown: String) {
        codeScrollPanes.clear()

        val currentUpdate = contentUpdateCounter.incrementAndGet()

        renderScope.launch {
            try {
                if (!isActive || currentUpdate != contentUpdateCounter.get()) return@launch

                val parsedTree = markdownParser.buildMarkdownTreeFromString(markdown)
                val blocks = parseMarkdownBlocks(markdown, parsedTree)

                withContext(Dispatchers.Main) {
                    if (!isDisposed && currentUpdate == contentUpdateCounter.get()) {
                        updateContent(blocks)
                    }
                }
            } catch (e: Exception) {
                logger.error("Error rendering markdown", e)
                withContext(Dispatchers.Main) {
                    if (!isDisposed) {
                        showErrorContent(markdown)
                    }
                }
            }
        }
    }


    /**
     * Create a text (HTML) block and lock its height.
     */
    private fun createMarkdownBlock(html: String): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT

            val textPane = JEditorPane().apply {
                contentType = "text/html"
                isEditable = false
                editorKit = HTMLEditorKit().apply {
                    styleSheet = MarkdownStyles.createStyleSheet(UIUtil.getPanelBackground())
                }
                addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
                text = "<html><body>$html</body></html>"

                border = JBUI.Borders.empty(4)
                background = UIUtil.getPanelBackground()
                putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)

                // Lock the height of this text pane after layout
                if (shouldLockHeights) {
                    lockHeight(this)
                }
            }

            add(textPane, BorderLayout.CENTER)

            // Also lock the panel's own height after layout
            if (shouldLockHeights) {
                lockHeight(this)
            }
        }
    }

    /**
     * Create a code block: a panel with a header, then a code area.
     * While updating, we disable scroll in codeWrapper. After done, we enable it again.
     * We also lock the overall block height.
     */
    private fun createCodeBlock(code: String, language: String, showApplyButton: Boolean): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.empty(4, 0, 4, 0)

            // Header with Copy/Apply buttons
            val headerPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0)).apply {
                background = null
                add(createActionButton("Copy") { copyToClipboard(code) })
                if (showApplyButton) {
                    add(createActionButton("Apply") { applyCode(code) })
                }
            }


            val codePane = JEditorPane().apply {
                contentType = "text/html"
                isEditable = false
                val editorColors = EditorColorsManager.getInstance().globalScheme
                font = Font(editorColors.editorFontName, Font.PLAIN, editorColors.editorFontSize)
                border = JBUI.Borders.empty(8)
                background = editorColors.defaultBackground
                foreground = editorColors.defaultForeground

                val highlightedCode = CodeHighlighter.highlightCode(code, language)
                editorKit = HTMLEditorKit().apply {
                    styleSheet = MarkdownStyles.createStyleSheet(background)
                }

                text = """
                    <html>
                    <head>
                        <style>
                            body {
                                font-family: ${font.family};
                                font-size: ${font.size}pt;
                                line-height: 1.2;
                                margin: 0;
                                padding: 0;
                                background-color: ${CodeHighlighter.colorToHex(background)};
                                color: ${CodeHighlighter.colorToHex(foreground)};
                            }
                            pre {
                                margin: 0;
                                padding: 0;
                                white-space: pre;
                                word-wrap: normal;
                                overflow-x: auto;
                            }
                        </style>
                    </head>
                    <body>
                        <div class="code-block-wrapper">
                            <pre>$highlightedCode</pre>
                        </div>
                    </body>
                    </html>
                """.trimIndent()
            }

            // Use JBScrollPane so we can do codeScrollPanes.add(...)
            val codeScrollPane = JBScrollPane(codePane).apply {
                border = JBUI.Borders.empty()
                viewport.background = codePane.background
                horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
                verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER

            }

            val codeWrapper = JPanel(BorderLayout()).apply {
                border = JBUI.Borders.customLine(UIUtil.getBoundsColor(), 1)
                background = codePane.background
                add(codeScrollPane, BorderLayout.CENTER)
            }

            // We always store the codeScrollPane in our list
            codeScrollPanes.add(codeScrollPane)

            add(headerPanel, BorderLayout.NORTH)
            add(codeWrapper, BorderLayout.CENTER)

            // Lock the overall block height after layout
            if (shouldLockHeights) {
                lockHeight(this)
            }

        }
    }


    private fun lockHeight(component: JComponent) {
        SwingUtilities.invokeLater {
            val currentHeight = component.preferredSize.height
            component.minimumSize = Dimension(0, currentHeight)
            component.maximumSize = Dimension(Int.MAX_VALUE, currentHeight)
            component.preferredSize = Dimension(component.width, currentHeight)
        }
    }

    fun unlockBlockHeights() {
        shouldLockHeights = false
        unlockHeightsRecursively(mainContent)
    }

    private fun unlockHeightsRecursively(container: Container) {
        for (child in container.components) {
            if (child is JComponent) {
                // Remove any previously locked size constraints
                child.minimumSize = null
                child.maximumSize = null
                child.preferredSize = null
            }
            // If this child is also a Container, recurse
            if (child is Container) {
                unlockHeightsRecursively(child)
            }
        }

        container.revalidate()
        container.repaint()
    }


    private fun createActionButton(text: String, action: () -> Unit): JButton {
        return JButton(text).apply {
            addActionListener { action() }
            isFocusable = false
            border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
            foreground = UIUtil.getLabelForeground()
        }
    }

    // -- Parsing & Errors --

    private fun parseMarkdownBlocks(markdown: String, root: ASTNode): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        var lastIndex = 0

        root.children.forEach { node ->
            when (node.type) {
                MarkdownElementTypes.CODE_FENCE, MarkdownElementTypes.CODE_BLOCK -> {
                    val textBefore = markdown.substring(lastIndex, node.startOffset).trim()
                    if (textBefore.isNotEmpty()) {
                        blocks.add(MarkdownBlock.Text(generateHtmlForText(textBefore)))
                    }

                    val (code, language) = when (node.type) {
                        MarkdownElementTypes.CODE_FENCE -> extractCodeFence(markdown, node)
                        else -> extractCodeBlock(markdown, node)
                    }
                    val showApplyButton = language != "bash"
                    blocks.add(MarkdownBlock.Code(code, language, showApplyButton))
                    lastIndex = node.endOffset
                }
            }
        }

        val remainingText = markdown.substring(lastIndex).trim()
        if (remainingText.isNotEmpty()) {
            blocks.add(MarkdownBlock.Text(generateHtmlForText(remainingText)))
        }

        return blocks
    }


    private fun copyToClipboard(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        logger.info("Copied code to clipboard")
    }

    private fun applyCode(code: String) {
        ChangesAction(project, code).performApplyAction()
        logger.info("Applied code changes")
    }

    private fun showErrorContent(markdown: String) {
        SwingUtilities.invokeLater {
            mainContent.removeAll()
            mainContent.add(createMarkdownBlock("<pre>${escapeHtml(markdown)}</pre>"))
            mainContent.revalidate()
            mainContent.repaint()
        }
    }

    private val isDisposed: Boolean get() = !isDisplayable


    private fun extractCodeFence(markdown: String, node: ASTNode): Pair<String, String> {
        var language = ""
        val code = StringBuilder()
        var lastEndOffset = node.startOffset
        var foundFirstContent = false

        node.children.forEach { child ->
            when (child.type.name) {
                "FENCE_LANG" -> {
                    language = child.getTextInNode(markdown).toString().trim()
                    lastEndOffset = child.endOffset
                }

                "CODE_FENCE_CONTENT" -> {
                    if (!foundFirstContent) {
                        foundFirstContent = true
                    } else {
                        val spaceBetween = markdown.substring(lastEndOffset, child.startOffset)
                        if (spaceBetween.contains('\n')) {
                            code.append(spaceBetween)
                        }
                    }
                    code.append(child.getTextInNode(markdown).toString())
                    lastEndOffset = child.endOffset
                }
            }
        }

        val finalCode = if (!code.endsWith("\n")) code.toString() + "\n" else code.toString()
        return finalCode to language
    }

    private fun extractCodeBlock(markdown: String, node: ASTNode): Pair<String, String> {
        return node.getTextInNode(markdown).toString() to ""
    }

    private fun generateHtmlForText(text: String): String {
        val parsedTree = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(text)
        return HtmlGenerator(text, parsedTree, GFMFlavourDescriptor()).generateHtml()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}

// Sealed class for different markdown block types
private sealed class MarkdownBlock {
    data class Text(val html: String) : MarkdownBlock()
    data class Code(val code: String, val language: String, val showApplyButton: Boolean) : MarkdownBlock()
}
