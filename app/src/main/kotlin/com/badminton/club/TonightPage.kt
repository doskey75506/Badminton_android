package com.badminton.club

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.badminton.club.core.BOARDS
import com.badminton.club.core.DEFAULT_BOARD
import com.badminton.club.core.MAX_COURTS
import com.badminton.club.core.MIN_COURTS
import com.badminton.club.core.Match
import com.badminton.club.core.Member
import com.badminton.club.core.applyResult
import com.badminton.club.core.genderOk
import com.badminton.club.core.playerStats
import com.badminton.club.core.suggestMatches
import com.badminton.club.core.suggestSubstitutes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

private fun fmtTime(iso: String?): String {
    if (iso == null) return "-"
    return LocalDateTime.parse(iso).format(timeFmt)
}

private fun fmtDuration(m: Match): String {
    val s = m.startedAt ?: return "-"
    val f = m.finishedAt ?: return "-"
    val seconds = java.time.Duration.between(LocalDateTime.parse(s), LocalDateTime.parse(f)).seconds
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

private fun joinDeltas(deltas: List<Pair<String, Double>>): String {
    val sep = if (Strings.lang == "zh") "，" else ", "
    return deltas.joinToString(sep) { (n, d) -> "$n ${"%+.1f".format(d)}" }
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TonightPage(state: AppState) {
    val revision = state.revision
    val club = state.club

    var dateIso by remember { mutableStateOf(LocalDate.now().toString()) }
    val date = LocalDate.parse(dateIso)

    val settings = remember(revision) { state.storage.getSettings(club) }
    val sessionStart = remember(settings) {
        settings.nightStarts[dateIso]?.let { LocalDateTime.parse(it) }
    }
    var courtsText by remember(settings.courts) { mutableStateOf(settings.courts.toString()) }
    val nCourts = (courtsText.toIntOrNull() ?: settings.courts).coerceIn(MIN_COURTS, MAX_COURTS)

    val members = remember(revision) { state.storage.membersById(club) }
    val tonight = remember(revision) { state.storage.matchesOn(club, dateIso) }
    val activeList = remember(revision) { state.storage.activeMatches(club, dateIso) }
    val activeByCourt = activeList.associateBy { it.court }
    val onCourt = activeList.flatMap { it.teamA + it.teamB }.toSet()

    val currentAttendance = remember(revision, dateIso) {
        state.storage.getAttendance(club, dateIso).toSet()
    }
    val checkedIds = remember(currentAttendance) {
        mutableStateOf(currentAttendance.toMutableSet())
    }
    val checked = remember(revision, dateIso) {
        if (members.isEmpty()) emptyList()
        else state.storage.getAttendance(club, dateIso)
    }
    val available = checked.filter { it !in onCourt }
    val now = LocalDateTime.now()
    val ordered = members.values.sortedBy { it.name }

    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        dateIso = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().toString()
                    }
                    showDatePicker = false
                    state.bump()
                }) { Text(T("ok")) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Text("${T("date")}: $dateIso")
                }
                OutlinedTextField(
                    value = courtsText,
                    onValueChange = { v ->
                        courtsText = v.filter { it.isDigit() }.take(2)
                        val n = courtsText.toIntOrNull()
                        if (n != null && n in MIN_COURTS..MAX_COURTS && n != settings.courts) {
                            state.storage.setSettings(club, settings.copy(courts = n))
                            state.bump()
                        }
                    },
                    label = { Text(T("courts")) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item { Text(T("attendance"), style = MaterialTheme.typography.titleMedium) }

        if (members.isEmpty()) {
            item { Text(T("no_members_tonight"), color = MaterialTheme.colorScheme.outline) }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ordered.chunked(3).forEach { rowMembers ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            rowMembers.forEach { m ->
                                val r = "%.0f".format(m.ratings[DEFAULT_BOARD] ?: 1000.0)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Checkbox(
                                        checked = m.id in checkedIds.value,
                                        onCheckedChange = { on ->
                                            if (on) checkedIds.value.add(m.id)
                                            else checkedIds.value.remove(m.id)
                                            state.storage.setAttendance(
                                                club, dateIso, checkedIds.value.toList()
                                            )
                                            state.bump()
                                        },
                                    )
                                    Text(
                                        T("member_chip", "n" to m.name, "r" to r),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (checked.isNotEmpty()) {
            val (played, rest) = playerStats(checked, tonight, now, sessionStart)
            val activeCourt = activeByCourt.values.flatMap { m ->
                (m.teamA + m.teamB).map { it to m.court }
            }.toMap()

            item {
                Text(
                    T("attendance_summary", "n" to checked.size, "on" to onCourt.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            item {
                Column {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(T("col_name"), Modifier.weight(2f), fontWeight = FontWeight.Bold)
                        Text(T("col_games"), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(T("col_rest"), Modifier.weight(1.5f), fontWeight = FontWeight.Bold)
                        Text(T("col_court"), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    }
                    checked.sortedByDescending { rest[it] ?: 0.0 }.forEach { pid ->
                        val name = members[pid]?.name ?: pid
                        val courtLabel = activeCourt[pid]?.toString() ?: T("rest")
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(name, Modifier.weight(2f))
                            Text("${played[pid] ?: 0}", Modifier.weight(1f))
                            Text("%.1f".format((rest[pid] ?: 0.0) / 60), Modifier.weight(1.5f))
                            Text(courtLabel, Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        val needGen = (1..nCourts).filter { it !in activeByCourt }
        item {
            Button(
                onClick = {
                    val boards = needGen.map { c -> c to (boardSelection[c] ?: DEFAULT_BOARD) }
                    val sugs = suggestMatches(
                        members, available, state.storage.loadMatches(club), tonight, now, boards,
                        sessionStart = sessionStart,
                    )
                    if (sugs.isEmpty()) {
                        state.warning = T("not_enough_players")
                    } else {
                        for ((court, pairing) in sugs) {
                            val ids = pairing.teamA + pairing.teamB
                            val names = ids.associateWith { members[it]?.name ?: it }
                            val before = ids.associateWith { members[it]?.ratings?.get(pairing.board) ?: 1000.0 }
                            state.storage.addMatch(
                                club, dateIso, court,
                                pairing.teamA, pairing.teamB, names, before,
                                board = pairing.board,
                            )
                        }
                        state.flash = T("match_started")
                    }
                    state.bump()
                },
                enabled = needGen.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                Text(T("gen_all", "n" to needGen.size))
            }
        }

        val maxCourt = maxOf(nCourts, activeByCourt.keys.maxOrNull() ?: 0)
        val courtRange = 1..maxCourt

        items(courtRange.toList()) { courtNo ->
            CourtCard(
                state = state,
                courtNo = courtNo,
                active = activeByCourt[courtNo],
                members = members,
                dateIso = dateIso,
                checked = checked,
                onCourt = onCourt,
                tonight = tonight,
                revision = revision,
                sessionStart = sessionStart,
            )
        }
    }
}

private val boardSelection = mutableStateMapOf<Int, String>()

@Composable
private fun CourtCard(
    state: AppState,
    courtNo: Int,
    active: Match?,
    members: Map<String, Member>,
    dateIso: String,
    checked: List<String>,
    onCourt: Set<String>,
    tonight: List<Match>,
    revision: Int,
    sessionStart: LocalDateTime?,
) {
    val club = state.club
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("${T("court")} $courtNo", fontWeight = FontWeight.Bold)
            if (active != null) {
                ActiveMatch(
                    state = state,
                    active = active,
                    members = members,
                    dateIso = dateIso,
                    checked = checked,
                    onCourt = onCourt,
                    tonight = tonight,
                    sessionStart = sessionStart,
                )
            } else {
                BoardPicker(courtNo)
                Text(
                    T("court_free"),
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun BoardPicker(courtNo: Int) {
    var expanded by remember { mutableStateOf(false) }
    val selected = boardSelection[courtNo] ?: DEFAULT_BOARD
    val label = T("board_$selected")
    OutlinedButton(onClick = { expanded = true }) {
        Text("${T("board")}: $label")
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            BOARDS.forEach { b ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(T("board_$b")) },
                    onClick = {
                        boardSelection[courtNo] = b
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ActiveMatch(
    state: AppState,
    active: Match,
    members: Map<String, Member>,
    dateIso: String,
    checked: List<String>,
    onCourt: Set<String>,
    tonight: List<Match>,
    sessionStart: LocalDateTime?,
) {
    val club = state.club
    val revision = state.revision
    val subTicked = remember(revision, active.id) { mutableStateOf(setOf<String>()) }

    Text(
        T("board_${active.board}"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
    )
    Text(
        T("running_at", "t" to fmtTime(active.startedAt)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(T("team_a"), style = MaterialTheme.typography.labelMedium)
            active.teamA.forEach { pid -> PlayerRow(active, pid, members, subTicked) }
            val totalA = active.teamA.sumOf { active.ratingsBefore[it] ?: 0.0 }
            if (active.ratingsBefore.isNotEmpty()) {
                Text(
                    T("total_rating", "r" to totalA.toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(T("team_b"), style = MaterialTheme.typography.labelMedium)
            active.teamB.forEach { pid -> PlayerRow(active, pid, members, subTicked) }
            val totalB = active.teamB.sumOf { active.ratingsBefore[it] ?: 0.0 }
            if (active.ratingsBefore.isNotEmpty()) {
                Text(
                    T("total_rating", "r" to totalB.toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }

    val balance = kotlin.math.abs(
        active.teamA.sumOf { active.ratingsBefore[it] ?: 0.0 } -
            active.teamB.sumOf { active.ratingsBefore[it] ?: 0.0 }
    )
    if (active.ratingsBefore.isNotEmpty()) {
        Text(
            T("balance", "n" to balance.toInt()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }

    ScoreForm(state, active, members)

    val onCourtNow = active.teamA + active.teamB
    val othersUsed = onCourt - onCourtNow.toSet()
    val board = active.board
    val pool = checked.filter {
        it !in othersUsed && it !in onCourtNow && genderOk(members[it], board)
    }
    val ticked = subTicked.value.filter { it in onCourtNow }

    if (pool.size < maxOf(ticked.size, 1)) {
        Text(
            T("no_spare"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                val mapping = suggestSubstitutes(
                    members, pool, active.teamA, active.teamB, ticked,
                    state.storage.loadMatches(club), tonight, LocalDateTime.now(), board,
                    sessionStart = sessionStart,
                )
                if (mapping != null) {
                    val info = mapping.values.associateWith {
                        (members[it]?.name ?: it) to (members[it]?.ratings?.get(board) ?: 1000.0)
                    }
                    state.storage.swapMatchPlayers(club, active.id, mapping, info)
                    val sep = if (Strings.lang == "zh") "、" else ", "
                    state.flash = T(
                        "swapped",
                        "out" to mapping.keys.joinToString(sep) { members[it]?.name ?: it },
                        "new" to mapping.values.joinToString(sep) { members[it]?.name ?: it },
                    )
                    subTicked.value = emptySet()
                } else {
                    state.warning = T("no_spare")
                }
                state.bump()
            },
            enabled = ticked.isNotEmpty() && pool.size >= ticked.size,
            modifier = Modifier.weight(1f),
        ) { Text(T("swap")) }
        OutlinedButton(
            onClick = {
                state.storage.cancelMatch(club, active.id)
                state.flash = T("match_cancelled")
                state.bump()
            },
            modifier = Modifier.weight(1f),
        ) { Text(T("cancel_match")) }
    }
}

@Composable
private fun PlayerRow(
    active: Match,
    pid: String,
    members: Map<String, Member>,
    subTicked: androidx.compose.runtime.MutableState<Set<String>>,
) {
    val name = active.names[pid] ?: members[pid]?.name ?: pid
    val rating = active.ratingsBefore[pid]
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = pid in subTicked.value,
            onCheckedChange = { on ->
                subTicked.value = if (on) subTicked.value + pid else subTicked.value - pid
            },
        )
        Text(
            if (rating != null) "$name — ${rating.toInt()}" else name,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ScoreForm(state: AppState, active: Match, members: Map<String, Member>) {
    var scoreA by remember(active.id, state.revision) { mutableStateOf("") }
    var scoreB by remember(active.id, state.revision) { mutableStateOf("") }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        OutlinedTextField(
            value = scoreA,
            onValueChange = { scoreA = it.filter { c -> c.isDigit() }.take(2) },
            label = { Text("A") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = scoreB,
            onValueChange = { scoreB = it.filter { c -> c.isDigit() }.take(2) },
            label = { Text("B") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = {
            val a = scoreA.toIntOrNull()
            val b = scoreB.toIntOrNull()
            if (a == null || b == null) {
                state.warning = T("score_invalid")
                state.bump()
                return@Button
            }
            if (a == b) {
                state.warning = T("score_tie")
                state.bump()
                return@Button
            }
            val after = try {
                applyResult(active.ratingsBefore, active.teamA, active.teamB, a, b)
            } catch (e: Exception) {
                state.warning = T("score_range")
                state.bump()
                return@Button
            }
            state.storage.finishMatch(state.club, active.id, a, b, after)
            val allMembers = state.storage.loadMembers(state.club).toMutableList()
            for (m in allMembers) {
                val delta = after[m.id]
                if (delta != null) {
                    m.ratings[active.board]?.let {
                        val idx = allMembers.indexOf(m)
                        allMembers[idx] = m.copy(
                            ratings = m.ratings + (active.board to delta)
                        )
                    }
                }
            }
            state.storage.saveMembers(state.club, allMembers)
            val deltas = after.map { (pid, r) ->
                (active.names[pid] ?: pid) to (r - (active.ratingsBefore[pid] ?: r))
            }.sortedByDescending { it.second }
            val winner = if (a > b) T("team_a") else T("team_b")
            state.flash = T(
                "score_saved",
                "a" to a, "b" to b, "w" to winner, "changes" to joinDeltas(deltas),
            )
            state.bump()
        }) { Text(T("submit_score")) }
    }
}
