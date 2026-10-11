package com.badminton.club

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.badminton.club.core.Club
import com.badminton.club.core.DuplicateNameException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadmintonApp(state: AppState) {
    val revision = state.revision
    val clubs = remember(revision) { state.clubs() }
    state.ensureClub()

    if (clubs.isEmpty()) {
        WelcomeScreen(state)
        return
    }

    var showNewClub by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.flash, state.revision) {
        if (state.flash.isNotEmpty()) {
            val msg = state.flash
            state.flash = ""
            snackbarHostState.showSnackbar(msg)
        }
    }
    LaunchedEffect(state.warning, state.revision) {
        if (state.warning.isNotEmpty()) {
            val msg = state.warning
            state.warning = ""
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (showNewClub) {
        NewClubDialog(
            onDismiss = { showNewClub = false },
            onCreate = { name ->
                try {
                    val club = state.storage.createClub(name)
                    state.clubId = club.id
                    state.bump()
                    showNewClub = false
                } catch (e: DuplicateNameException) {
                    state.warning = T("club_dup")
                    state.bump()
                } catch (e: IllegalArgumentException) {
                    state.warning = T("club_name_invalid")
                    state.bump()
                }
            },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(clubs.firstOrNull { it.id == state.club }?.name ?: "")
                },
                actions = {
                    ClubMenu(state, clubs)
                    TextButton(onClick = { showNewClub = true }) {
                        Text(T("new_club"))
                    }
                    TextButton(onClick = {
                        state.switchLang(if (state.lang == "zh") "en" else "zh")
                        state.bump()
                    }) {
                        Text(if (state.lang == "zh") "EN" else "中文")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                val icons = listOf("📋", "👥", "🏆", "🕓", "❓")
                val labels = listOf(
                    T("p_tonight"),
                    T("p_members"),
                    T("p_standings"),
                    T("p_history"),
                    T("p_help"),
                )
                labels.forEachIndexed { i, label ->
                    NavigationBarItem(
                        selected = state.page == i,
                        onClick = { state.page = i; state.bump() },
                        icon = { Text(icons[i]) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            when (state.page) {
                0 -> TonightPage(state)
                1 -> MembersPage(state)
                2 -> StandingsPage(state)
                3 -> HistoryPage(state)
                4 -> HelpPage(state)
            }
        }
    }
}

@Composable
private fun ClubMenu(state: AppState, clubs: List<Club>) {
    var expanded by remember { mutableStateOf(false) }
    val current = clubs.firstOrNull { it.id == state.club }?.name ?: ""
    OutlinedButton(onClick = { expanded = true }) {
        Text("${T("club")}: $current ▾")
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            clubs.forEach { club ->
                DropdownMenuItem(
                    text = { Text(club.name) },
                    onClick = {
                        state.clubId = club.id
                        expanded = false
                        state.bump()
                    },
                )
            }
        }
    }
}

@Composable
private fun NewClubDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(T("new_club")) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(T("club_name")) },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { onCreate(name) }) { Text(T("create")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(T("cancel")) }
        },
    )
}
