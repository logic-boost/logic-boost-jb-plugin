package org.logicboost.chat.storage

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import org.logicboost.chat.model.ChatMessage
import java.time.Instant

@Tag("Chat")
data class Chat(
    @Attribute var id: String = java.util.UUID.randomUUID().toString(),
    @Attribute var name: String = "",
    @Attribute var createdAt: Long = Instant.now().epochSecond,
    @XCollection(style = XCollection.Style.v2)
    var messages: MutableList<ChatMessage> = mutableListOf()
)

@Service(Service.Level.PROJECT)
@State(
    name = "ChatHistoryStorage",
    storages = [Storage("logicboost-chat-history.xml")]
)
class ChatHistoryStorage : PersistentStateComponent<ChatHistoryStorage> {
    @MapAnnotation(
        surroundWithTag = false,
        surroundKeyWithTag = false,
        surroundValueWithTag = false,
        entryTagName = "Chat"
    )
    var chats: MutableMap<String, Chat> = mutableMapOf()

    override fun getState(): ChatHistoryStorage = this

    override fun loadState(state: ChatHistoryStorage) {
        chats.clear()
        chats.putAll(state.chats)
    }

    fun createChat(name: String): Chat {
        val chat = Chat(name = name)
        chats[chat.id] = chat
        return chat
    }

    fun deleteChat(chatId: String) {
        chats.remove(chatId)
    }

    fun getAllChats(): List<Chat> = chats.values.sortedByDescending { it.createdAt }

    fun getChat(chatId: String): Chat? = chats[chatId]

    fun updateChat(chat: Chat) {
        chats[chat.id] = chat
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): ChatHistoryStorage =
            project.getService(ChatHistoryStorage::class.java)
    }
}
