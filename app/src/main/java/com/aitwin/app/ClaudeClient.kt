package com.aitwin.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object ClaudeClient {

  private val client = OkHttpClient()
  private const val URL = "https://api.anthropic.com/v1/messages"
  private const val MODEL = "claude-sonnet-5"

  suspend fun sendMessage(
    apiKey: String,
    systemPrompt: String,
    history: List<ChatMessage>
      ): String = suspendCoroutine { cont ->
    val messagesArr = JSONArray()
    history.forEach { m ->
      messagesArr.put(
        JSONObject()
        .put("role", if (m.role == "user") "user" else "assistant")
        .put("content", m.text)
        )
    }

    val body = JSONObject()
    .put("model", MODEL)
    .put("max_tokens", 1024)
    .put("system", systemPrompt)
    .put("messages", messagesArr)
    .toString()
    .toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
    .url(URL)
    .addHeader("x-api-key", apiKey)
    .addHeader("anthropic-version", "2023-06-01")
    .addHeader("content-type", "application/json")
    .post(body)
    .build()

    client.newCall(request).enqueue(object : okhttp3.Callback {
      override fun onFailure(call: okhttp3.Call, e: IOException) {
        cont.resumeWithException(e)
      }

      override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
        response.use { resp ->
          val raw = resp.body?.string() ?: ""
          if (!resp.isSuccessful) {
            cont.resumeWithException(IOException("API error " + resp.code + ": " + raw))
            return
          }
          try {
            val json = JSONObject(raw)
            val content = json.getJSONArray("content")
            val text = content.getJSONObject(0).getString("text")
            cont.resume(text)
          } catch (e: Exception) {
            cont.resumeWithException(e)
          }
        }
      }
    })
  }
}
