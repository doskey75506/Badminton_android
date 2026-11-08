package com.badminton.club

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.badminton.club.core.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ManualPanel(
    state: AppState,
    members: Map<String, Member>,
    checked: List<String>,
    onCourt: Set<String>,
    activeList: List<Match>,
    nCourts: Int,
    dateIso: String,
    onAdjust: () -> Unit,
    onEnd: (Match) -> Unit,
) {
    val session = state.manual
    var nText by remember { mutableStateOf(session.candidateCount.toString()) }
    val activeByCourt = activeList.associateBy { it.court }
    val freeCourt = (1..nCourts).firstOrNull { it !in activeByCourt }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!session.entered) {
            Text(T("manual_enter_hint"), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(nText, { nText = it.filter(Char::isDigit).take(2) }, label = { Text(T("manual_n")) }, singleLine = true)
            Button(onClick = {
                state.manual = session.enter(checked.filter { it !in onCourt }, nText.toIntOrNull() ?: 6)
                state.manualContext = state.club to dateIso
                // Manual selection always has four slots; choose doubles for free courts.
                (1..nCourts).filter { it !in activeByCourt }.forEach {
                    if (!(boardSelection[it] ?: DEFAULT_BOARD).endsWith("double")) boardSelection[it] = DEFAULT_BOARD
                }
                state.bump()
            }, enabled = checked.count { it !in onCourt } >= 4) { Text(T("manual_enter")) }
            return@Column
        }
        val queue = session.queue.filter { it !in onCourt }
        val candidates = session.candidates(onCourt)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(T("manual_queue_len", "n" to queue.size), Modifier.weight(1f))
            OutlinedButton(onClick = onAdjust) { Text(T("adjust")) }
            OutlinedButton(onClick = { state.resetManual(); state.bump() }) { Text(T("manual_exit")) }
        }
        val latestOnCourt by rememberUpdatedState(onCourt)
        val place by rememberUpdatedState<(String, String, Boolean) -> Unit>({ id, target, _ ->
            target.removePrefix("slot:").toIntOrNull()?.let { slot ->
                state.manual = state.manual.place(id, slot, latestOnCourt)
                state.bump()
            }
        })
        PegDragHost(members) { drag ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(T("manual_candidates", "n" to session.candidateCount), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    queue.forEach { id ->
                        key(id) {
                            val candidate = id in candidates
                            Peg(members[id]?.name ?: id, selected = id in session.picks,
                                modifier = Modifier.alpha(if (candidate) 1f else 0.4f).then(
                                    if (candidate) drag.sourceModifier(id) { pid, target, after -> place(pid, target, after) }
                                        .clickable {
                                            val current = state.manual
                                            state.manual = if (id in current.picks) current.remove(id)
                                            else current.picks.indexOf(null).let { slot -> current.place(id, slot, onCourt) }
                                            state.bump()
                                        } else Modifier))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    (1..maxOf(nCourts, activeByCourt.keys.maxOrNull() ?: 0)).forEach { court ->
                        val active = activeByCourt[court]
                        Card(Modifier.width(172.dp).clickable(enabled = active != null) { active?.let(onEnd) }) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${T("court")} $court")
                                if (active == null) {
                                    BoardPicker(court, doublesOnly = true)
                                    Box(Modifier.fillMaxWidth().height(260.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                        Text(T("court_free"))
                                    }
                                } else {
                                    Text(T("board_${active.board}"), style = MaterialTheme.typography.labelSmall)
                                    Column(Modifier.fillMaxWidth().height(260.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))) {
                                        listOf(active.teamA, active.teamB).forEachIndexed { index, team ->
                                            Column(Modifier.fillMaxWidth().weight(1f).background(
                                                if (index == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer),
                                                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                                team.forEach { id ->
                                                    Text(active.names[id] ?: id)
                                                    Text((active.ratingsBefore[id] ?: DEFAULT_RATING).toInt().toString(), style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                            if (index == 0) HorizontalDivider(thickness = 2.dp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Text(T("manual_pick_hint"), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(2) { team ->
                        Column(Modifier.weight(1f)) {
                            Text(T(if (team == 0) "team_a" else "team_b"))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                repeat(2) { player ->
                                    val slot = team * 2 + player
                                    val id = session.picks[slot]
                                    val target = "slot:$slot"
                                    Box(Modifier.weight(1f).height(120.dp).then(drag.targetModifier(target))
                                        .border(if (drag.target == target) 2.dp else 1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                        .then(if (id != null) drag.sourceModifier(id) { pid, key, after -> place(pid, key, after) }
                                            .clickable { state.manual = state.manual.remove(id); state.bump() } else Modifier),
                                        contentAlignment = Alignment.Center) {
                                        if (id != null) Peg(members[id]?.name ?: id)
                                        else Text(T("manual_drop"), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Button(onClick = {
            val court = freeCourt ?: return@Button
            val board = boardSelection[court]?.takeIf { it.endsWith("double") } ?: DEFAULT_BOARD
            val ids = state.manual.picks.filterNotNull()
            if (!ids.all { genderOk(members[it], board) }) {
                state.warning = T("manual_gender")
            } else {
                val next = state.manual.start(onCourt, members, board)
                state.storage.addMatch(state.club, dateIso, court, ids.take(2), ids.drop(2),
                    ids.associateWith { members[it]?.name ?: it },
                    ids.associateWith { members[it]?.ratings?.get(board) ?: DEFAULT_RATING }, board)
                state.manual = next
                state.flash = T("match_started")
            }
            state.bump()
        }, enabled = session.picks.none { it == null } && freeCourt != null) { Text(T("manual_start")) }
        if (freeCourt == null) Text(T("manual_no_court"))
        else if (queue.size < 4) Text(T(if (activeList.isEmpty()) "manual_not_enough" else "manual_waiting"))
    }
}
