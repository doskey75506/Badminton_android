package com.badminton.club

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.badminton.club.core.DEFAULT_BOARD
import com.badminton.club.core.DuplicateNameException

private fun overallStats(matches: List<com.badminton.club.core.Match>): Map<String, Pair<Int, Int>> {
    val stats = mutableMapOf<String, Pair<Int, Int>>()
    for (m in matches) {
        if (m.scoreA == null) continue
        val winnerTeam = if (m.winner == "A") m.teamA else m.teamB
        for (pid in m.teamA + m.teamB) {
            val (played, wins) = stats[pid] ?: (0 to 0)
            stats[pid] = (played + 1) to (wins + if (pid in winnerTeam) 1 else 0)
        }
    }
    return stats
}

@Composable
fun MembersPage(state: AppState) {
    val revision = state.revision
    val club = state.club

    var name by remember { mutableStateOf("") }
    var genderIdx by remember { mutableStateOf(0) }
    var ratingText by remember { mutableStateOf("1000") }

    val members = remember(revision) { state.storage.loadMembers(club) }
    val stats = remember(revision) { overallStats(state.storage.loadMatches(club)) }

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        item {
            Text(T("p_members"), style = MaterialTheme.typography.headlineSmall)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(T("name")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val genders = listOf(
                        T("gender_male") to "m",
                        T("gender_female") to "f",
                        T("gender_unspecified") to "",
                    )
                    genders.forEachIndexed { i, (label, _) ->
                        val selected = genderIdx == i
                        OutlinedButton(
                            onClick = { genderIdx = i },
                            modifier = Modifier.weight(1f),
                            colors = if (selected) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            },
                        ) {
                            Text(
                                label,
                                fontWeight = if (selected) FontWeight.Bold else null,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = ratingText,
                    onValueChange = { v -> ratingText = v.filter { it.isDigit() || it == '.' }.take(6) },
                    label = { Text(T("initial_rating")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isEmpty()) {
                            state.warning = T("name_empty")
                            state.bump()
                            return@Button
                        }
                        val gender = when (genderIdx) {
                            0 -> "m"
                            1 -> "f"
                            else -> null
                        }
                        val r = ratingText.toDoubleOrNull() ?: 1000.0
                        try {
                            val m = state.storage.addMember(club, trimmed, r, gender)
                            state.flash = T(
                                "member_added",
                                "n" to m.name,
                                "r" to "%.0f".format(m.ratings[DEFAULT_BOARD] ?: 1000.0),
                            )
                            name = ""
                            state.bump()
                        } catch (e: DuplicateNameException) {
                            state.warning = T("name_dup", "n" to trimmed)
                            state.bump()
                        } catch (e: IllegalArgumentException) {
                            state.warning = T("name_empty")
                            state.bump()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(T("add_member")) }
            }
        }

        if (members.isEmpty()) {
            item { Text(T("no_members"), color = MaterialTheme.colorScheme.outline) }
        } else {
            item {
                Text(
                    T("member_list", "n" to members.size),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            val sorted = members.sortedByDescending { it.ratings[DEFAULT_BOARD] ?: 1000.0 }
            items(sorted, key = { it.id }) { m ->
                val (played, wins) = stats[m.id] ?: (0 to 0)
                val gl = when (m.gender) {
                    "m" -> T("gender_male")
                    "f" -> T("gender_female")
                    else -> T("gender_unspecified")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Column(Modifier.weight(3f)) {
                        Text("${m.name}（$gl）", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "${T("rating")} ${(m.ratings[DEFAULT_BOARD] ?: 1000.0).toInt()}",
                        Modifier.weight(2f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "${T("games")} $played",
                        Modifier.weight(1.5f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        T("w_l", "w" to wins, "l" to (played - wins)),
                        Modifier.weight(2f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = {
                        state.storage.deleteMember(club, m.id)
                        state.bump()
                    }) { Text(T("delete")) }
                }
            }
        }
    }
}
