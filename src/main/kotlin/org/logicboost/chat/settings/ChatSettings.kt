package org.logicboost.chat.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "LogicBoostSettings",
    storages = [Storage("logicboostSettings.xml")]
)
class ChatSettings : PersistentStateComponent<ChatSettings> {
    var llmConfigs: MutableList<LLMConfig> = mutableListOf()
    var selectedLLMName: String = ""
    var llmHelper: LLMConfig = LLMConfig()

    override fun getState(): ChatSettings = this

    override fun loadState(state: ChatSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }


    fun getSmartLLMConfig(): LLMConfig? {
        return llmConfigs.find { it.forSmartFunctions }
    }

    // Main LLM methods
    fun getSelectedLLM(): LLMConfig? {
        return llmConfigs.find { it.name == selectedLLMName && it.isEnabled && !it.forSmartFunctions }
    }

    fun setSelectedLLM(name: String) {
        if (llmConfigs.any { it.name == name && it.isEnabled && !it.forSmartFunctions }) {
            selectedLLMName = name
            notifySettingsChanged()
        }
    }

    // LLM Helper methods
    fun getLLMHelper(): LLMConfig {
        return llmHelper
    }


    // General settings methods
    fun notifySettingsChanged() {
        ApplicationManager.getApplication().messageBus
            .syncPublisher(SettingsChangeNotifier.TOPIC)
            .onSettingsChanged()
    }

    companion object {
        fun getInstance(): ChatSettings = service()
    }
}
