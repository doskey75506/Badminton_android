package com.badminton.club

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.badminton.club.core.Club
import com.badminton.club.core.Storage

class AppState(storage: Storage, private val prefs: SharedPreferences) {
    val storage = storage
    var lang by mutableStateOf(prefs.getString("lang", "en") ?: "en")

    init {
        Strings.setLanguage(lang)
    }

    var clubId by mutableStateOf<String?>(null)
    var page by mutableStateOf(0)
    var flash by mutableStateOf("")
    var warning by mutableStateOf("")
    var revision by mutableStateOf(0)

    fun bump() {
        revision++
    }

    fun switchLang(value: String) {
        lang = value
        Strings.setLanguage(value)
        prefs.edit().putString("lang", value).apply()
    }

    fun clubs(): List<Club> = storage.listClubs()

    fun ensureClub() {
        val list = clubs()
        if (list.isEmpty()) {
            clubId = null
        } else if (clubId !in list.map { it.id }) {
            clubId = list[0].id
        }
    }

    val club: String get() = clubId ?: ""
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dataDir = java.io.File(filesDir, "data")
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val state = AppState(Storage(dataDir), prefs)
        setContent {
            MaterialTheme {
                BadmintonApp(state)
            }
        }
    }
}

@Composable
fun WelcomeScreen(state: AppState) {
    var name by remember { mutableStateOf("") }
    Box(modifier = Modifier.fillMaxSize()) {
        TextButton(
            onClick = {
                state.switchLang(if (state.lang == "zh") "en" else "zh")
                state.bump()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        ) {
            Text(if (state.lang == "zh") "EN" else "中文")
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(T("welcome"), style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(T("club_name")) },
                singleLine = true,
            )
            Button(onClick = {
                try {
                    val club = state.storage.createClub(name)
                    state.clubId = club.id
                    state.bump()
                } catch (e: com.badminton.club.core.DuplicateNameException) {
                    state.warning = T("club_dup")
                } catch (e: IllegalArgumentException) {
                    state.warning = T("club_name_invalid")
                }
            }) {
                Text(T("create"))
            }
            WarningText(state)
        }
    }
}

@Composable
fun WarningText(state: AppState) {
    if (state.warning.isNotEmpty()) {
        val msg = state.warning
        Text(msg, color = MaterialTheme.colorScheme.error)
    }
}
