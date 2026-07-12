package com.aitwin.app

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

val Context.dataStore by preferencesDataStore(name = "ai_twin_store")

object Keys {
  val PROFILE_NAME = stringPreferencesKey("profile_name")
  val PROFILE_TONE = stringPreferencesKey("profile_tone")
  val PROFILE_FACTS = stringPreferencesKey("profile_facts")
  val API_KEY = stringPreferencesKey("api_key")
  val CHAT_HISTORY = stringPreferencesKey("chat_history")
}

data class ChatMessage(val role: String, val text: String)

class TwinRepository(private val context: Context) {

  val profileName: Flow<String> = context.dataStore.data.map { it[Keys.PROFILE_NAME] ?: "" }
  val profileTone: Flow<String> = context.dataStore.data.map { it[Keys.PROFILE_TONE] ?: "" }
  val profileFacts: Flow<String> = context.dataStore.data.map { it[Keys.PROFILE_FACTS] ?: "" }
  val apiKey: Flow<String> = context.dataStore.data.map { it[Keys.API_KEY] ?: "" }
  val chatHistoryJson: Flow<String> = context.dataStore.data.map { it[Keys.CHAT_HISTORY] ?: "[]" }

  suspend fun saveProfile(name: String, tone: String, facts: String) {
    context.dataStore.edit {
      it[Keys.PROFILE_NAME] = name
      it[Keys.PROFILE_TONE] = tone
      it[Keys.PROFILE_FACTS] = facts
    }
  }

  suspend fun saveApiKey(key: String) {
    context.dataStore.edit { it[Keys.API_KEY] = key }
  }

  suspend fun appendMessage(current: List<ChatMessage>, msg: ChatMessage): List<ChatMessage> {
    val updated = current + msg
    val arr = JSONArray()
    updated.takeLast(60).forEach { m ->
      arr.put(JSONObject().put("role", m.role).put("text", m.text))
    }
    context.dataStore.edit { it[Keys.CHAT_HISTORY] = arr.toString() }
    return updated
  }

  fun parseHistory(json: String): List<ChatMessage> {
    val arr = JSONArray(json)
    val list = mutableListOf<ChatMessage>()
    for (i in 0 until arr.length()) {
      val o = arr.getJSONObject(i)
      list.add(ChatMessage(o.getString("role"), o.getString("text")))
    }
    return list
  }

  suspend fun clearHistory() {
    context.dataStore.edit { it[Keys.CHAT_HISTORY] = "[]" }
  }
}
