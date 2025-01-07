package org.logicboost.chat.model

import com.intellij.util.xmlb.annotations.Tag

@Tag("ChatMessage")
data class ChatMessage(
    @Tag var role: String = "",
    @Tag var content: String = "",
    @Tag var id: String = java.util.UUID.randomUUID().toString()
)
