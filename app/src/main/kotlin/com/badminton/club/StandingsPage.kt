package com.badminton.club

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.badminton.club.core.BOARDS
import com.badminton.club.core.DEFAULT_BOARD
import com.badminton.club.core.Match
import com.badminton.club.core.genderOk
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem

private fun overallStats(matches: List<Match>): Map<String, Pair<Int, Int>> {
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
fun StandingsPage(state: AppState) {
    val revision = state.revision
    val club = state.club
    var board by remember { mutableStateOf(DEFAULT_BOARD) }
    var expanded by remember { mutableStateOf(false) }

    val members = remember(revision) { state.storage.loadMembers(club) }
    val finished = remember(revision, board) {
        state.storage.loadMatches(club).filter { it.scoreA != null && it.board == board }
    }
    val stats = overallStats(finished)

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        item {
            Text(T("p_standings"), style = MaterialTheme.typography.headlineSmall)
        }
        item {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.padding(vertical = 8.dp)) {
                Text("${T("board")}: ${T("board_$board")}")
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    BOARDS.forEach { b ->
                        DropdownMenuItem(
                            text = { Text(T("board_$b")) },
                            onClick = { board = b; expanded = false },
                        )
                    }
                }
            }
        }

        if (members.isEmpty()) {
            item { Text(T("no_members"), color = MaterialTheme.colorScheme.outline) }
        } else {
            val visible = members.filter { genderOk(it, board) }
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(T("col_name"), Modifier.weight(2f), fontWeight = FontWeight.Bold)
                    Text(T("rating"), Modifier.weight(1.5f), fontWeight = FontWeight.Bold)
                    Text(T("games"), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(T("wins"), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(T("losses"), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(T("win_rate"), Modifier.weight(1.5f), fontWeight = FontWeight.Bold)
                }
            }
            val rows = visible.map { m ->
                val (played, wins) = stats[m.id] ?: (0 to 0)
                Triple(m, played, wins)
            }.sortedByDescending { it.first.ratings[board] ?: 1000.0 }

            items(rows, key = { it.first.id }) { (m, played, wins) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(m.name, Modifier.weight(2f))
                    Text(
                        "%.1f".format(m.ratings[board] ?: 1000.0),
                        Modifier.weight(1.5f),
                    )
                    Text("$played", Modifier.weight(1f))
                    Text("$wins", Modifier.weight(1f))
                    Text("${played - wins}", Modifier.weight(1f))
                    Text(
                        if (played > 0) "%.0f%%".format(wins * 100.0 / played) else "-",
                        Modifier.weight(1.5f),
                    )
                }
            }
        }
    }
}
