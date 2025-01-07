package org.logicboost.chat.settings

import com.intellij.util.messages.Topic

/**
 * Interface for listening to settings changes.
 */
interface SettingsChangeListener {
    fun onSettingsChanged()
}

/**
 * Notifier object for settings change events.
 */
object SettingsChangeNotifier {
    val TOPIC = Topic.create("LogicBoost Settings Change", SettingsChangeListener::class.java)
}
