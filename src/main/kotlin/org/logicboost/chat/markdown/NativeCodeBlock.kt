package org.logicboost.chat.markdown

import com.intellij.icons.AllIcons
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBUI
import org.logicboost.chat.actions.ChangesAction
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JPanel

/**
 * NativeCodeBlock is responsible for rendering code blocks with syntax highlighting.
 */
class NativeCodeBlock(
    private val project: Project,
    code: String,
    language: String
) : JPanel(BorderLayout()), Disposable {

    private val editor: EditorEx = createEditor(code, language)

    init {
        // Create header panel with copy button
        val headerPanel = NonOpaquePanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(2)
            preferredSize = Dimension(Int.MAX_VALUE, 30)

            val copyButton = JButton(AllIcons.Actions.Copy).apply {
                toolTipText = "Copy to clipboard"
                putClientProperty("JButton.buttonType", "tool")
                isBorderPainted = false
                isContentAreaFilled = false
                addActionListener {
                    val selection = StringSelection(editor.document.text)
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
                }
            }

            val applyButton = JButton("Apply", AllIcons.Actions.Execute).apply {
                toolTipText = "Apply changes from AI"
                putClientProperty(
                    "JButton.buttonType",
                    "toolbarButton"
                )  // Changed to toolbarButton for better visibility
                isBorderPainted = false  // Make border visible
                isContentAreaFilled = false  // Make button background visible
                addActionListener {
                    // Instantiate and invoke the action handler
                    val handler = ChangesAction(project, editor.document.text)
                    handler.performApplyAction()
                }
            }

            // Update the buttonPanel implementation for better spacing:
            val buttonPanel = JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
                isOpaque = false
                add(copyButton, BorderLayout.WEST)
                add(Box.createHorizontalStrut(JBUI.scale(8)), BorderLayout.CENTER)
                add(applyButton, BorderLayout.EAST)
            }

            add(buttonPanel, BorderLayout.EAST)
        }

        add(headerPanel, BorderLayout.NORTH)
        add(editor.component, BorderLayout.CENTER)

        border = JBUI.Borders.compound(
            JBUI.Borders.empty(0, 4, 8, 4),
            JBUI.Borders.customLine(editor.colorsScheme.defaultBackground.darker(), 1)
        )
    }

    /**
     * Maps common language identifiers to IntelliJ-recognized language names.
     *
     * @param language The language identifier from the Markdown code block.
     * @return The corresponding IntelliJ language name.
     */
    private fun getLanguageByIdentifier(language: String): Language? {
        return when (language.lowercase().trim()) {
            // JVM Languages
            "kotlin", "kt" -> Language.findLanguageByID("kotlin")
            "java" -> Language.findLanguageByID("JAVA")
            "groovy" -> Language.findLanguageByID("Groovy")
            "scala" -> Language.findLanguageByID("Scala")
            "clojure" -> Language.findLanguageByID("Clojure")

            // Web Technologies
            "javascript", "js" -> Language.findLanguageByID("JavaScript")
            "typescript", "ts" -> Language.findLanguageByID("TypeScript")
            "jsx" -> Language.findLanguageByID("JSX")
            "tsx" -> Language.findLanguageByID("TypeScript JSX")
            "html" -> Language.findLanguageByID("HTML")
            "xml" -> Language.findLanguageByID("XML")
            "css" -> Language.findLanguageByID("CSS")
            "scss", "sass" -> Language.findLanguageByID("SCSS")
            "less" -> Language.findLanguageByID("LESS")
            "vue" -> Language.findLanguageByID("Vue.js")
            "angular" -> Language.findLanguageByID("Angular2Template")

            // Scripting Languages
            "python", "py" -> Language.findLanguageByID("Python")
            "ruby", "rb" -> Language.findLanguageByID("Ruby")
            "perl" -> Language.findLanguageByID("Perl")
            "php" -> Language.findLanguageByID("PHP")
            "shell", "sh", "bash" -> Language.findLanguageByID("Shell Script")
            "powershell", "ps1" -> Language.findLanguageByID("PowerShell")

            // Database Languages
            "sql" -> Language.findLanguageByID("SQL")
            "plsql" -> Language.findLanguageByID("PL/SQL")
            "mongodb" -> Language.findLanguageByID("MongoDB")
            "cassandra" -> Language.findLanguageByID("CQL")

            // Configuration & Markup
            "json" -> Language.findLanguageByID("JSON")
            "yaml", "yml" -> Language.findLanguageByID("yaml")
            "toml" -> Language.findLanguageByID("TOML")
            "markdown", "md" -> Language.findLanguageByID("Markdown")
            "properties" -> Language.findLanguageByID("Properties")
            "gradle" -> Language.findLanguageByID("Groovy")

            // Systems Programming
            "c" -> Language.findLanguageByID("C")
            "cpp", "c++" -> Language.findLanguageByID("C++")
            "objectivec", "objc" -> Language.findLanguageByID("ObjC")
            "swift" -> Language.findLanguageByID("Swift")
            "rust" -> Language.findLanguageByID("rust")
            "go", "golang" -> Language.findLanguageByID("go")

            // Mobile Development
            "dart" -> Language.findLanguageByID("Dart")
            "kotlin-native" -> Language.findLanguageByID("KotlinNative")

            // Game Development
            "hlsl" -> Language.findLanguageByID("HLSL")
            "glsl" -> Language.findLanguageByID("GLSL")
            "unity" -> Language.findLanguageByID("ShaderLab")

            // Enterprise Languages
            "apex" -> Language.findLanguageByID("Apex")
            "coldfusion" -> Language.findLanguageByID("ColdFusion")

            // Build Tools
            "cmake" -> Language.findLanguageByID("CMake")
            "makefile" -> Language.findLanguageByID("Makefile")
            "dockerfile" -> Language.findLanguageByID("Dockerfile")

            // Template Languages
            "velocity" -> Language.findLanguageByID("VTL")
            "freemarker" -> Language.findLanguageByID("FreeMarker")
            "thymeleaf" -> Language.findLanguageByID("Thymeleaf")

            else -> null
        }
    }

    /**
     * Creates and configures the editor component.
     *
     * @param code The code to display in the editor.
     * @param language The programming language of the code for syntax highlighting.
     * @return A configured EditorEx instance.
     */
    private fun createEditor(code: String, language: String): EditorEx {
        val document = EditorFactory.getInstance().createDocument(code)

        return (EditorFactory.getInstance().createEditor(document, project) as EditorEx).apply {
            settings.apply {
                isLineNumbersShown = true // Disable line numbers
                isLineMarkerAreaShown = true // Disable the line marker area
                isFoldingOutlineShown = false // Disable code folding
                isRightMarginShown = false
                additionalColumnsCount = 0
                additionalLinesCount = 0
                isUseSoftWraps = true
                setTabSize(4)
            }

            // Set colors scheme
            colorsScheme = EditorColorsManager.getInstance().globalScheme
            backgroundColor = colorsScheme.defaultBackground

            // Set read-only and hide cursor
            isViewer = true
            settings.isCaretRowShown = false

            // Create a light virtual file with the appropriate language
            val lang = getLanguageByIdentifier(language)
            val virtualFile = if (lang != null) {
                LightVirtualFile("temp." + language.lowercase(), lang, document.text)
            } else {
                LightVirtualFile("temp.txt", PlainTextFileType.INSTANCE, document.text)
            }

            // Set up syntax highlighting using the virtual file
            highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, virtualFile)

            // Refresh the highlighter
            highlighter.setText(document.text)

            // Calculate editor height dynamically
            val lineCount = document.lineCount
            val lineHeight = this.lineHeight
            val padding = JBUI.scale(10) // Adjust padding as needed
            val maxHeight = JBUI.scale(500) // You can make this configurable

            val calculatedHeight = lineCount * lineHeight + padding
            val preferredHeight = if (calculatedHeight < maxHeight) calculatedHeight else maxHeight

            component.preferredSize = Dimension(Int.MAX_VALUE, preferredHeight)
        }
    }

    override fun dispose() {
        EditorFactory.getInstance().releaseEditor(editor)
    }
}
