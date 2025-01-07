package org.logicboost.chat.settings

import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import javax.swing.*

/**
 * Utility class to create common UI components.
 */
object UIComponentFactory {

    fun createPasswordField(toolTip: String? = null): JPasswordField {
        return JPasswordField().apply {
            toolTipText = toolTip
        }
    }

    fun createTextField(toolTip: String? = null, columns: Int = 20): JBTextField {
        return JBTextField().apply {
            toolTipText = toolTip
            this.columns = columns
        }
    }

    fun createSpinner(
        model: SpinnerNumberModel,
        toolTip: String? = null,
        columns: Int? = null
    ): JSpinner {
        return JSpinner(model).apply {
            toolTipText = toolTip
            if (columns != null) {
                (editor as? JSpinner.DefaultEditor)?.textField?.columns = columns
            }
        }
    }

    fun createTextArea(rows: Int, columns: Int, toolTip: String? = null): JTextArea {
        return JTextArea(rows, columns).apply {
            lineWrap = true
            wrapStyleWord = true
            this.toolTipText = toolTip
        }
    }

    fun createScrollPane(component: JComponent): JScrollPane {
        return JScrollPane(component).apply {
            border = JBUI.Borders.empty()
            verticalScrollBar.unitIncrement = 16
        }
    }
}
