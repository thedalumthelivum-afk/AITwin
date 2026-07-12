@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.aitwin.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme {
        AiTwinApp(TwinRepository(applicationContext))
      }
    }
  }
}

@Composable
fun AiTwinApp(repo: TwinRepository) {
  var showSettings by remember { mutableStateOf(false) }

  val profileName by repo.profileName.collectAsState(initial = "")
  val profileTone by repo.profileTone.collectAsState(initial = "")
  val profileFacts by repo.profileFacts.collectAsState(initial = "")
  val apiKey by repo.apiKey.collectAsState(initial = "")

  val hasProfile = profileName.isNotBlank()

  if (!hasProfile || showSettings) {
    SetupScreen(
      repo = repo,
      initialName = profileName,
      initialTone = profileTone,
      initialFacts = profileFacts,
      initialKey = apiKey,
      onDone = { showSettings = false }
      )
  } else {
    ChatScreen(
      repo = repo,
      profileName = profileName,
      profileTone = profileTone,
      profileFacts = profileFacts,
      apiKey = apiKey,
      onOpenSettings = { showSettings = true }
      )
  }
}

@Composable
fun SetupScreen(
  repo: TwinRepository,
  initialName: String,
  initialTone: String,
  initialFacts: String,
  initialKey: String,
  onDone: () -> Unit
  ) {
  val scope = rememberCoroutineScope()
  var name by remember { mutableStateOf(initialName) }
  var tone by remember { mutableStateOf(initialTone) }
  var facts by remember { mutableStateOf(initialFacts) }
  var key by remember { mutableStateOf(initialKey) }

  Column(
    modifier = Modifier
    .fillMaxSize()
    .padding(20.dp)
    ) {
    Text("உங்கள் AI Twin-ஐ அமைக்கவும்", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
      value = name, onValueChange = { name = it },
      label = { Text("உங்கள் பெயர்") },
      modifier = Modifier.fillMaxWidth()
      )
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
      value = tone, onValueChange = { tone = it },
      label = { Text("உங்கள் பேச்சு தொனி") },
      modifier = Modifier.fillMaxWidth()
      )
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
      value = facts, onValueChange = { facts = it },
      label = { Text("உங்களைப் பற்றிய தகவல்கள்") },
      modifier = Modifier
      .fillMaxWidth()
      .height(140.dp)
      )
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
      value = key, onValueChange = { key = it },
      label = { Text("Anthropic API Key") },
      modifier = Modifier.fillMaxWidth()
      )
    Spacer(Modifier.height(20.dp))

    Button(
      onClick = {
        scope.launch {
          repo.saveProfile(name, tone, facts)
          repo.saveApiKey(key)
          onDone()
        }
      },
      enabled = name.isNotBlank() && key.isNotBlank(),
      modifier = Modifier.fillMaxWidth()
      ) {
      Text("சேமித்து தொடங்கு")
    }
  }
}

@Composable
fun ChatScreen(
  repo: TwinRepository,
  profileName: String,
  profileTone: String,
  profileFacts: String,
  apiKey: String,
  onOpenSettings: () -> Unit
  ) {
  val scope = rememberCoroutineScope()
  val historyJson by repo.chatHistoryJson.collectAsState(initial = "[]")
  var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
  var input by remember { mutableStateOf("") }
  var loading by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  val listState = rememberLazyListState()

  LaunchedEffect(historyJson) {
    if (messages.isEmpty()) {
      messages = repo.parseHistory(historyJson)
    }
  }

  val systemPrompt = remember(profileName, profileTone, profileFacts) {
    "நீ " + profileName + "-ன் தனிப்பட்ட AI Twin. அவரின் பேச்சு நடையிலும் தொனியிலும் பதிலளி. தொனி: " + profileTone + ". அவரைப் பற்றிய தகவல்கள்: " + profileFacts + ". சுருக்கமாகவும் நேர்மையாகவும் உதவிகரமாகவும் பதிலளி."
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(profileName + "'s AI Twin") },
        actions = {
          TextButton(onClick = onOpenSettings) { Text("Settings") }
        }
        )
    }
    ) { padding ->
    Column(
      modifier = Modifier
      .fillMaxSize()
      .padding(padding)
      .padding(12.dp)
      ) {
      LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        items(messages) { m ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (m.role == "user") Arrangement.End else Arrangement.Start
            ) {
            Surface(
              color = if (m.role == "user")
              MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.secondaryContainer,
              modifier = Modifier.padding(4.dp)
              ) {
              Text(m.text, modifier = Modifier.padding(10.dp))
            }
          }
        }
      }

      if (error != null) {
        Text(
          "பிழை: " + error,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(vertical = 4.dp)
          )
      }

      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
        ) {
        OutlinedTextField(
          value = input,
          onValueChange = { input = it },
          modifier = Modifier.weight(1f),
          placeholder = { Text("செய்தி எழுதவும்...") }
          )
        Spacer(Modifier.width(8.dp))
        Button(
          onClick = {
            val text = input.trim()
            if (text.isEmpty() || loading) return@Button
            input = ""
            loading = true
            error = null
            scope.launch {
              val updated = repo.appendMessage(messages, ChatMessage("user", text))
              messages = updated
              try {
                val reply = ClaudeClient.sendMessage(apiKey, systemPrompt, updated)
                messages = repo.appendMessage(messages, ChatMessage("twin", reply))
              } catch (e: Exception) {
                error = e.message
              } finally {
                loading = false
              }
            }
          }
          ) {
          Text(if (loading) "..." else "அனுப்பு")
        }
      }
    }
  }
}
