package com.badminton.club.core

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

const val BALANCE_TOLERANCE = 100.0
const val REST_TOLERANCE = 60.0

private fun parse(ts: String?): LocalDateTime? =
    if (ts == null) null else LocalDateTime.parse(ts)

fun restSeconds(
    availableIds: List<String>,
    tonightMatches: List<Match>,
    now: LocalDateTime,
    sessionStart: LocalDateTime? = null,
): Map<String, Double> {
    var firstStart: LocalDateTime? = null
    val leftCourt = mutableMapOf<String, LocalDateTime>()
    fun markLeft(pid: String, at: LocalDateTime) {
        if (leftCourt[pid] == null || at > leftCourt[pid]!!) leftCourt[pid] = at
    }
    for (m in tonightMatches.sortedBy { it.seq }) {
        val started = parse(m.startedAt)
        if (started != null && (firstStart == null || started < firstStart)) {
            firstStart = started
        }
        val finished = parse(m.finishedAt)
        if (finished != null) {
            for (pid in m.teamA + m.teamB) markLeft(pid, finished)
        }
        for ((pid, ts) in m.departed) {
            val t = parse(ts) ?: continue
            markLeft(pid, t)
        }
    }
    val rest = mutableMapOf<String, Double>()
    for (pid in availableIds) {
        val t0 = leftCourt[pid] ?: sessionStart ?: firstStart ?: now
        rest[pid] = max(0.0, Duration.between(t0, now).toSeconds().toDouble())
    }
    return rest
}

fun playerStats(
    availableIds: List<String>,
    tonightMatches: List<Match>,
    now: LocalDateTime,
    sessionStart: LocalDateTime? = null,
): Pair<Map<String, Int>, Map<String, Double>> {
    val played = availableIds.associateWith { 0 }.toMutableMap()
    for (m in tonightMatches) {
        for (pid in m.teamA + m.teamB) {
            if (played.containsKey(pid)) played[pid] = played[pid]!! + 1
        }
    }
    return played to restSeconds(availableIds, tonightMatches, now, sessionStart)
}

fun pairCounts(allMatches: List<Match>): Pair<Map<List<String>, Int>, Map<List<String>, Int>> {
    val partners = mutableMapOf<List<String>, Int>()
    val opponents = mutableMapOf<List<String>, Int>()
    for (m in allMatches) {
        for (team in listOf(m.teamA, m.teamB)) {
            val sorted = team.sorted()
            for (i in sorted.indices) {
                for (j in i + 1 until sorted.size) {
                    val key = listOf(sorted[i], sorted[j])
                    partners[key] = (partners[key] ?: 0) + 1
                }
            }
        }
        for (a in m.teamA) {
            for (b in m.teamB) {
                val key = listOf(a, b).sorted()
                opponents[key] = (opponents[key] ?: 0) + 1
            }
        }
    }
    return partners to opponents
}

fun genderOk(member: Member?, board: String): Boolean {
    if (!board.startsWith("womens")) return true
    return member?.gender == "f"
}

private fun teamRating(members: Map<String, Member>, team: List<String>, board: String): Double =
    team.sumOf { members[it]?.ratings?.get(board) ?: DEFAULT_RATING }

private fun pairingsOf(quad: List<String>): List<Pair<List<String>, List<String>>> {
    val (a, b, c, d) = quad
    return listOf(
        listOf(a, b) to listOf(c, d),
        listOf(a, c) to listOf(b, d),
        listOf(a, d) to listOf(b, c),
    )
}

fun suggestMatch(
    members: Map<String, Member>,
    availableIds: List<String>,
    allMatches: List<Match>,
    tonightMatches: List<Match>,
    now: LocalDateTime,
    exclude: Set<Set<String>> = emptySet(),
    board: String = DEFAULT_BOARD,
    sessionStart: LocalDateTime? = null,
): Suggestion? {
    val isSingle = board.endsWith("single")
    val need = if (isSingle) 2 else 4
    if (availableIds.size < need) return null

    val (played, rest) = playerStats(availableIds, tonightMatches, now, sessionStart)
    val (partners, opponents) = pairCounts(allMatches)
    val topSum = rest.values.sortedDescending().take(need).sum()
    val restSpan = REST_TOLERANCE * need

    var best: List<Comparable<*>>? = null
    var bestTeamA: List<String> = emptyList()
    var bestTeamB: List<String> = emptyList()
    var bestBalance = 0.0

    for (group in combinations(availableIds.sorted(), need)) {
        if (group.toSet() in exclude) continue
        val groupRest = group.sumOf { rest[it] ?: 0.0 }
        val restBucket = ((topSum - groupRest) / restSpan).toInt()

        var bestPairingKey: List<Comparable<*>>? = null
        var pairTeamA: List<String> = emptyList()
        var pairTeamB: List<String> = emptyList()
        var pairBalance = 0.0

        val pairings = if (isSingle) {
            listOf(listOf(group[0]) to listOf(group[1]))
        } else {
            pairingsOf(group)
        }

        for ((teamA, teamB) in pairings) {
            val balance = abs(
                teamRating(members, teamA, board) - teamRating(members, teamB, board)
            )
            val balanceBucket = ceil(balance / BALANCE_TOLERANCE).toInt()
            val pairRep =
                (partners[teamA.sorted()] ?: 0) + (partners[teamB.sorted()] ?: 0)
            var oppRep = 0
            for (a in teamA) {
                for (b in teamB) {
                    oppRep += opponents[listOf(a, b).sorted()] ?: 0
                }
            }
            val key = listOf<Any>(
                balanceBucket, pairRep, oppRep, teamA, teamB
            ) as List<Comparable<*>>
            if (bestPairingKey == null || compareKeys(key, bestPairingKey) < 0) {
                bestPairingKey = key
                pairTeamA = teamA
                pairTeamB = teamB
                pairBalance = balance
            }
        }

        @Suppress("UNCHECKED_CAST")
        val fullKey = listOf(restBucket) as List<Comparable<*>> +
            (bestPairingKey as List<Comparable<*>>)

        if (best == null || compareKeys(fullKey, best) < 0) {
            best = fullKey
            bestTeamA = pairTeamA
            bestTeamB = pairTeamB
            bestBalance = pairBalance
        }
    }

    if (best == null) return null
    val stats = availableIds.associateWith {
        PlayerStats(played[it] ?: 0, rest[it] ?: 0.0)
    }
    return Suggestion(bestTeamA, bestTeamB, bestBalance, stats, board)
}

fun suggestMatches(
    members: Map<String, Member>,
    availableIds: List<String>,
    allMatches: List<Match>,
    tonightMatches: List<Match>,
    now: LocalDateTime,
    courtsBoards: List<Pair<Int, String>>,
    sessionStart: LocalDateTime? = null,
): Map<Int, Suggestion> {
    val out = mutableMapOf<Int, Suggestion>()
    var pool = availableIds.toList()
    for ((court, board) in courtsBoards) {
        val genderPool = pool.filter { genderOk(members[it], board) }
        val suggestion = suggestMatch(
            members, genderPool, allMatches, tonightMatches, now,
            board = board, sessionStart = sessionStart,
        ) ?: continue
        out[court] = suggestion
        val used = suggestion.teamA.toSet() + suggestion.teamB.toSet()
        pool = pool.filter { it !in used }
    }
    return out
}

fun suggestSubstitutes(
    members: Map<String, Member>,
    freeIds: List<String>,
    teamA: List<String>,
    teamB: List<String>,
    outgoing: List<String>,
    allMatches: List<Match>,
    tonightMatches: List<Match>,
    now: LocalDateTime,
    board: String,
    sessionStart: LocalDateTime? = null,
): Map<String, String>? {
    val outgoingSet = outgoing.toSet()
    val slots = mutableListOf<Pair<String, Int>>()
    for ((i, p) in teamA.withIndex()) {
        if (p in outgoingSet) slots.add("a" to i)
    }
    for ((i, p) in teamB.withIndex()) {
        if (p in outgoingSet) slots.add("b" to i)
    }
    val slotPids = (teamA + teamB).filter { it in outgoingSet }
    val free = freeIds.toList()
    val need = slots.size
    if (need == 0 || free.size < need) return null

    val (_, rest) = playerStats(free, tonightMatches, now, sessionStart)
    val (partners, opponents) = pairCounts(allMatches)
    val topSum = free.map { rest[it] ?: 0.0 }.sortedDescending().take(need).sum()
    val restSpan = REST_TOLERANCE * need
    val rating = free.associateWith { members[it]?.ratings?.get(board) ?: DEFAULT_RATING }
    val keptA = teamA.filter { it !in outgoingSet }
        .sumOf { members[it]?.ratings?.get(board) ?: DEFAULT_RATING }
    val keptB = teamB.filter { it !in outgoingSet }
        .sumOf { members[it]?.ratings?.get(board) ?: DEFAULT_RATING }

    var best: List<Comparable<*>>? = null
    var bestCombo: List<String> = emptyList()

    for (combo in permutations(free, need)) {
        val newA = teamA.toMutableList()
        val newB = teamB.toMutableList()
        for ((k, slot) in slots.withIndex()) {
            if (slot.first == "a") newA[slot.second] = combo[k]
            else newB[slot.second] = combo[k]
        }
        val addA = slots.filter { it.first == "a" }.mapIndexed { k, _ -> combo[k] }
        val addB = slots.filter { it.first == "b" }.mapIndexed { k, _ -> combo[k] }
        val comboRest = combo.sumOf { rest[it] ?: 0.0 }
        val restBucket = ((topSum - comboRest) / restSpan).toInt()
        val balance = abs(
            keptA + addA.sumOf { rating[it]!! } - keptB - addB.sumOf { rating[it]!! }
        )
        val balanceBucket = ceil(balance / BALANCE_TOLERANCE).toInt()
        val pairRep =
            (partners[newA.sorted()] ?: 0) + (partners[newB.sorted()] ?: 0)
        var oppRep = 0
        for (a in newA) {
            for (b in newB) {
                oppRep += opponents[listOf(a, b).sorted()] ?: 0
            }
        }
        val key = listOf<Any>(
            restBucket, balanceBucket, pairRep, oppRep, combo
        ) as List<Comparable<*>>
        if (best == null || compareKeys(key, best) < 0) {
            best = key
            bestCombo = combo
        }
    }

    if (best == null) return null
    val result = mutableMapOf<String, String>()
    for ((k, pid) in slotPids.withIndex()) {
        result[pid] = bestCombo[k]
    }
    return result
}

private fun compareKeys(a: List<Comparable<*>>, b: List<Comparable<*>>): Int {
    for (i in a.indices) {
        val cmp = compareValues(a[i], b[i])
        if (cmp != 0) return cmp
    }
    return 0
}

@Suppress("UNCHECKED_CAST")
private fun compareValues(a: Any?, b: Any?): Int {
    if (a is List<*> && b is List<*>) {
        for (i in a.indices) {
            if (i >= b.size) return 1
            val cmp = compareValues(a[i], b[i])
            if (cmp != 0) return cmp
        }
        return a.size.compareTo(b.size)
    }
    return (a as Comparable<Any?>).compareTo(b)
}

internal fun <T> combinations(items: List<T>, k: Int): List<List<T>> {
    if (k == 0) return listOf(emptyList())
    if (items.size < k) return emptyList()
    val result = mutableListOf<List<T>>()
    fun rec(start: Int, acc: MutableList<T>) {
        if (acc.size == k) {
            result.add(acc.toList())
            return
        }
        for (i in start until items.size) {
            acc.add(items[i])
            rec(i + 1, acc)
            acc.removeAt(acc.size - 1)
        }
    }
    rec(0, mutableListOf())
    return result
}

internal fun <T> permutations(items: List<T>, k: Int): List<List<T>> {
    val result = mutableListOf<List<T>>()
    fun rec(used: BooleanArray, acc: MutableList<T>) {
        if (acc.size == k) {
            result.add(acc.toList())
            return
        }
        for (i in items.indices) {
            if (used[i]) continue
            used[i] = true
            acc.add(items[i])
            rec(used, acc)
            acc.removeAt(acc.size - 1)
            used[i] = false
        }
    }
    rec(BooleanArray(items.size), mutableListOf())
    return result
}
