package org.logicboost.chat.markdown

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap

object CodeHighlighter {
    private val colorScheme = EditorColorsManager.getInstance().globalScheme
    private val highlighterCache = ConcurrentHashMap<String, SyntaxHighlighter>()

    private val languageMap = mapOf(
        // JVM Languages
        "kotlin" to "Kotlin",
        "kt" to "Kotlin",
        "java" to "JAVA",
        "groovy" to "Groovy",
        "scala" to "Scala",
        "clojure" to "Clojure",

        // Web Technologies
        "javascript" to "JavaScript",
        "js" to "JavaScript",
        "typescript" to "TypeScript",
        "ts" to "TypeScript",
        "html" to "HTML",
        "css" to "CSS",
        "scss" to "SCSS",
        "sass" to "SASS",
        "less" to "LESS",
        "vue" to "Vue.js",
        "jsx" to "JSX",
        "tsx" to "TypeScript JSX",

        // Native Development
        "c" to "C",
        "cpp" to "C++",
        "objc" to "Objective-C",
        "swift" to "Swift",
        "go" to "Go",
        "rust" to "Rust",

        // Scripting Languages
        "python" to "Python",
        "py" to "Python",
        "ruby" to "Ruby",
        "rb" to "Ruby",
        "php" to "PHP",
        "perl" to "Perl",
        "bash" to "Shell Script",
        "sh" to "Shell Script",
        "powershell" to "PowerShell",
        "ps1" to "PowerShell",

        // Database
        "sql" to "SQL",
        "mysql" to "MySQL",
        "postgresql" to "PostgreSQL",
        "mongodb" to "MongoDB",

        // Configuration & Markup
        "xml" to "XML",
        "yaml" to "YAML",
        "yml" to "YAML",
        "toml" to "TOML",
        "json" to "JSON",
        "markdown" to "Markdown",
        "md" to "Markdown",

        // Other Languages
        "r" to "R",
        "matlab" to "MATLAB",
        "dart" to "Dart",
        "elm" to "Elm",
        "erlang" to "Erlang",
        "haskell" to "Haskell",
        "f#" to "F#",
        "fsharp" to "F#",
        "lua" to "Lua"
    )

    fun highlightCode(code: String, language: String): String {
        // Get the current color scheme each time we highlight
        val colorScheme = EditorColorsManager.getInstance().globalScheme

        val normalizedLang = language.lowercase()
        val jetbrainsLang = languageMap[normalizedLang] ?: "Plain Text"

        val highlighter = highlighterCache.getOrPut(normalizedLang) {
            val fileType = FileTypeManager.getInstance().findFileTypeByName(jetbrainsLang)
                ?: PlainTextFileType.INSTANCE

            SyntaxHighlighterFactory.getSyntaxHighlighter(fileType, null, null)
                ?: return escapeHtml(code)
        }

        val lexer = highlighter.highlightingLexer
        lexer.start(code)
        val result = StringBuilder(code.length * 2)

        while (lexer.tokenType != null) {
            val tokenText = code.substring(lexer.tokenStart, lexer.tokenEnd)
            val attributes = highlighter.getTokenHighlights(lexer.tokenType)
                .mapNotNull { colorScheme.getAttributes(it) }
                .firstOrNull()

            if (attributes != null) {
                val styles = mutableListOf<String>()

                // Add foreground color if present
                attributes.foregroundColor?.let {
                    styles.add("color: ${colorToHex(it)}")
                }

                // Add background color if present
                attributes.backgroundColor?.let {
                    styles.add("background-color: ${colorToHex(it)}")
                }

                // Add font weight if bold
                if (attributes.fontType and 1 != 0) {
                    styles.add("font-weight: bold")
                }

                // Add font style if italic
                if (attributes.fontType and 2 != 0) {
                    styles.add("font-style: italic")
                }

                // Create span with all applicable styles
                if (styles.isNotEmpty()) {
                    result.append("<span style=\"${styles.joinToString("; ")}\">")
                        .append(escapeHtml(tokenText))
                        .append("</span>")
                } else {
                    result.append(escapeHtml(tokenText))
                }
            } else {
                result.append(escapeHtml(tokenText))
            }

            lexer.advance()
        }

        return result.toString()
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#x27;")

    public fun colorToHex(color: Color): String =
        "#%02x%02x%02x".format(color.red, color.green, color.blue)
}
