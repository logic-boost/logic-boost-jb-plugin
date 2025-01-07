package org.logicboost.chat.markdown

import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Color
import javax.swing.text.html.StyleSheet
import com.intellij.openapi.editor.colors.EditorColorsManager

/**
 * Manages stylesheet rules for Markdown rendering using only Swing-supported CSS properties.
 */
object MarkdownStyles {
    private fun colorToHex(color: Color): String {
        return String.format("#%02x%02x%02x", color.red, color.green, color.blue)
    }

    fun createStyleSheet(background: Color): StyleSheet {
        val styleSheet = StyleSheet()
        val editorScheme = EditorColorsManager.getInstance().globalScheme
        val defaultFontSize = editorScheme.editorFontSize
        val editorFontFamily = editorScheme.editorFontName
        val defaultFontFamily = UIUtil.getLabelFont().family

        // Body - basic container
        styleSheet.addRule("""
            body {
                font-family: "$defaultFontFamily";
                font-size: ${defaultFontSize}pt;
                margin: 8px;
                background-color: ${colorToHex(background)};
                color: ${colorToHex(JBColor.foreground())};
            }
        """.trimIndent())

        // Headers
        for (i in 1..6) {
            val size = when (i) {
                1 -> defaultFontSize + 8
                2 -> defaultFontSize + 6
                3 -> defaultFontSize + 4
                4 -> defaultFontSize + 2
                else -> defaultFontSize
            }
            styleSheet.addRule("""
                h$i {
                    font-family: "$defaultFontFamily";
                    font-size: ${size}pt;
                    font-weight: bold;
                    margin: 16px 0 8px 0;
                    color: ${colorToHex(JBColor.foreground())};
                }
            """.trimIndent())
        }

        // Lists
        styleSheet.addRule("""
            ul {
                margin: 8px 0 8px 24px;
                list-style-type: disc;
            }
        """.trimIndent())

        styleSheet.addRule("""
            ol {
                margin: 8px 0 8px 24px;
                list-style-type: decimal;
            }
        """.trimIndent())

        // List items
        styleSheet.addRule("""
            li {
                margin: 4px 0;
            }
        """.trimIndent())

        // Paragraphs
        styleSheet.addRule("""
            p {
                margin: 8px 0;
            }
        """.trimIndent())

        // Code blocks
        styleSheet.addRule("""
            pre {
                font-family: "$editorFontFamily";
                font-size: ${defaultFontSize}pt;
                margin: 8px 0;
                padding: 8px;
                background-color: ${colorToHex(editorScheme.defaultBackground)};
            }
        """.trimIndent())

        // Inline code
        styleSheet.addRule("""
            code {
                font-family: "$editorFontFamily";
                font-size: ${defaultFontSize}pt;
                background-color: ${colorToHex(editorScheme.defaultBackground)};
                padding: 1px 3px;
            }
        """.trimIndent())

        // Links
        styleSheet.addRule("""
            a {
                color: ${colorToHex(editorScheme.getAttributes(com.intellij.openapi.editor.DefaultLanguageHighlighterColors.IDENTIFIER).foregroundColor)};
                text-decoration: underline;
            }
        """.trimIndent())

        // Blockquotes
        styleSheet.addRule("""
            blockquote {
                margin: 8px 0 8px 16px;
                padding: 0 0 0 8px;
                border-left: 3px solid ${colorToHex(JBColor.border())};
                color: ${colorToHex(JBColor.foreground().darker())};
            }
        """.trimIndent())

        // Tables
        styleSheet.addRule("""
            table {
                margin: 8px 0;
                border: 1px solid ${colorToHex(JBColor.border())};
            }
        """.trimIndent())

        // Table headers
        styleSheet.addRule("""
            th {
                padding: 6px;
                background-color: ${colorToHex(background.brighter())};
                border: 1px solid ${colorToHex(JBColor.border())};
                font-weight: bold;
            }
        """.trimIndent())

        // Table cells
        styleSheet.addRule("""
            td {
                padding: 6px;
                border: 1px solid ${colorToHex(JBColor.border())};
            }
        """.trimIndent())

        // Strong text
        styleSheet.addRule("""
            strong {
                font-weight: bold;
            }
        """.trimIndent())

        // Emphasized text
        styleSheet.addRule("""
            em {
                font-style: italic;
            }
        """.trimIndent())

        return styleSheet
    }
}
