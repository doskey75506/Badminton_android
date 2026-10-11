package com.badminton.club.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class DuplicateNameException(message: String) : IllegalArgumentException(message)

class Storage(val dataDir: File) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val clubsFile get() = File(dataDir, "clubs.json")
    private fun clubDir(clubId: String) = File(dataDir, "clubs/$clubId")
    private fun clubFile(clubId: String, name: String) = File(clubDir(clubId), "$name.json")
    private fun matchesFile(clubId: String) = File(clubDir(clubId), "matches.json")

    private inline fun <reified T> load(path: File, default: T): T {
        if (!path.exists()) return default
        return try {
            json.decodeFromString(path.readText())
        } catch (_: Exception) {
            default
        }
    }

    private inline fun <reified T> save(path: File, data: T) {
        path.parentFile?.mkdirs()
        val tmp = File(path.parentFile, path.name + ".tmp")
        tmp.writeText(json.encodeToString(data))
        tmp.renameTo(path)
    }

    fun listClubs(): List<Club> = load(clubsFile, emptyList())

    fun createClub(name: String): Club {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("club name cannot be empty")
        val clubs = listClubs()
        if (clubs.any { it.name == trimmed }) {
            throw DuplicateNameException("club already exists")
        }
        val club = Club(
            id = UUID.randomUUID().toString().take(10),
            name = trimmed,
            createdAt = nowIso(),
        )
        save(clubsFile, clubs + club)
        return club
    }

    fun getSettings(clubId: String): Settings =
        load(clubFile(clubId, "settings"), Settings())

    fun setSettings(clubId: String, settings: Settings) =
        save(clubFile(clubId, "settings"), settings)

    fun loadMembers(clubId: String): List<Member> = load(clubFile(clubId, "members"), emptyList())

    fun saveMembers(clubId: String, members: List<Member>) =
        save(clubFile(clubId, "members"), members)

    fun addMember(clubId: String, name: String, rating: Double? = null, gender: String? = null): Member {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("member name cannot be empty")
        val members = loadMembers(clubId)
        if (members.any { it.name == trimmed }) {
            throw DuplicateNameException("member already exists")
        }
        val r = rating ?: DEFAULT_RATING
        val member = Member(
            id = UUID.randomUUID().toString().take(10),
            name = trimmed,
            gender = gender,
            ratings = BOARDS.associateWith { r },
            createdAt = nowIso(),
        )
        saveMembers(clubId, members + member)
        return member
    }

    fun deleteMember(clubId: String, memberId: String) {
        saveMembers(clubId, loadMembers(clubId).filter { it.id != memberId })
        val attendance = loadAttendance(clubId).mapValues { (_, ids) -> ids.filter { it != memberId } }
        saveAttendance(clubId, attendance)
    }

    fun membersById(clubId: String): Map<String, Member> =
        loadMembers(clubId).associateBy { it.id }

    fun loadMatches(clubId: String): List<Match> = load(matchesFile(clubId), emptyList())

    fun saveMatches(clubId: String, matches: List<Match>) =
        save(matchesFile(clubId), matches)

    fun addMatch(
        clubId: String,
        date: String,
        court: Int,
        teamA: List<String>,
        teamB: List<String>,
        names: Map<String, String>,
        ratingsBefore: Map<String, Double>,
        board: String = DEFAULT_BOARD,
    ): Match {
        val matches = loadMatches(clubId)
        val sameDay = matches.filter { it.date == date }
        val seq = (sameDay.maxOfOrNull { it.seq } ?: -1) + 1
        val now = nowIso()
        val settings = getSettings(clubId)
        if (settings.nightStarts[date] == null) {
            setSettings(clubId, settings.copy(nightStarts = settings.nightStarts + (date to now)))
        }
        val match = Match(
            id = UUID.randomUUID().toString().take(10),
            date = date,
            seq = seq,
            court = court,
            board = board,
            teamA = teamA,
            teamB = teamB,
            names = names,
            ratingsBefore = ratingsBefore,
            startedAt = now,
        )
        saveMatches(clubId, matches + match)
        return match
    }

    fun finishMatch(
        clubId: String,
        matchId: String,
        scoreA: Int,
        scoreB: Int,
        ratingsAfter: Map<String, Double>,
    ) {
        val matches = loadMatches(clubId).map { m ->
            if (m.id == matchId) {
                m.copy(
                    scoreA = scoreA,
                    scoreB = scoreB,
                    winner = if (scoreA > scoreB) "A" else "B",
                    ratingsAfter = ratingsAfter,
                    finishedAt = nowIso(),
                )
            } else m
        }
        saveMatches(clubId, matches)
    }

    fun swapMatchPlayers(
        clubId: String,
        matchId: String,
        swaps: Map<String, String>,
        info: Map<String, Pair<String, Double>>,
    ): Match {
        val matches = loadMatches(clubId)
        val idx = matches.indexOfFirst { it.id == matchId }
        require(idx >= 0) { "match not found" }
        val m = matches[idx]
        require(m.scoreA == null) { "cannot swap a finished match" }
        require(swaps.isNotEmpty()) { "no players to swap" }
        for (outPid in swaps.keys) {
            require(outPid in m.teamA || outPid in m.teamB) { "player not in match" }
        }
        for (inPid in swaps.values) {
            require(inPid !in m.teamA && inPid !in m.teamB) { "player already in match" }
        }
        var updated = m
        val leftAt = nowIso()
        for ((outPid, inPid) in swaps) {
            val newA = if (outPid in updated.teamA) {
                updated.teamA.map { if (it == outPid) inPid else it }
            } else updated.teamA
            val newB = if (outPid in updated.teamB) {
                updated.teamB.map { if (it == outPid) inPid else it }
            } else updated.teamB
            val newNames = updated.names.toMutableMap()
            newNames.remove(outPid)
            val newRatings = updated.ratingsBefore.toMutableMap()
            newRatings.remove(outPid)
            val (name, rating) = info.getValue(inPid)
            newNames[inPid] = name
            newRatings[inPid] = rating
            updated = updated.copy(
                teamA = newA,
                teamB = newB,
                names = newNames,
                ratingsBefore = newRatings,
                departed = updated.departed + (outPid to leftAt),
            )
        }
        saveMatches(clubId, matches.map { if (it.id == matchId) updated else it })
        return updated
    }

    fun cancelMatch(clubId: String, matchId: String) {
        val matches = loadMatches(clubId)
        val target = matches.firstOrNull { it.id == matchId }
            ?: throw IllegalArgumentException("match not found")
        require(target.scoreA == null) { "cannot cancel a finished match" }
        saveMatches(clubId, matches.filter { it.id != matchId })
    }

    fun activeMatches(clubId: String, date: String): List<Match> =
        loadMatches(clubId).filter { it.date == date && it.scoreA == null }

    fun matchesOn(clubId: String, date: String): List<Match> =
        loadMatches(clubId).filter { it.date == date }

    fun loadAttendance(clubId: String): Map<String, List<String>> =
        load(clubFile(clubId, "attendance"), emptyMap())

    fun saveAttendance(clubId: String, attendance: Map<String, List<String>>) =
        save(clubFile(clubId, "attendance"), attendance)

    fun getAttendance(clubId: String, date: String): List<String> =
        loadAttendance(clubId)[date] ?: emptyList()

    fun setAttendance(clubId: String, date: String, memberIds: List<String>) {
        val attendance = loadAttendance(clubId).toMutableMap()
        attendance[date] = memberIds
        saveAttendance(clubId, attendance)
    }

    companion object {
        private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

        fun nowIso(): String = LocalDateTime.now().format(FORMATTER)
    }
}
