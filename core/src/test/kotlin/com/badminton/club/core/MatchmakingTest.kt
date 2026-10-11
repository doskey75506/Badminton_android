package com.badminton.club.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class MatchmakingTest {
    private val now: LocalDateTime = LocalDateTime.of(2026, 10, 9, 21, 0, 0)

    private fun makeMembers(ratings: Map<String, Double>, gender: String? = null): Map<String, Member> =
        ratings.mapValues { (pid, r) ->
            Member(
                id = pid,
                name = pid,
                gender = gender,
                ratings = BOARDS.associateWith { r },
            )
        }

    private val equal = makeMembers(
        listOf("A", "B", "C", "D", "E", "F").associateWith { 1000.0 }
    )

    private fun match(
        seq: Int,
        teamA: List<String>,
        teamB: List<String>,
        started: LocalDateTime? = null,
        finished: LocalDateTime? = null,
        score: Pair<Int, Int> = 21 to 15,
    ) = Match(
        id = "m$seq",
        date = "2026-10-09",
        seq = seq,
        court = 1,
        teamA = teamA,
        teamB = teamB,
        scoreA = score.first,
        scoreB = score.second,
        winner = if (score.first > score.second) "A" else "B",
        startedAt = started?.toString(),
        finishedAt = finished?.toString(),
    )

    private fun lit(v: String) = v

    @Test
    fun requiresFourPlayers() {
        assertNull(suggestMatch(equal, listOf("A", "B", "C"), emptyList(), emptyList(), now))
    }

    @Test
    fun picksLongestRestedPlayers() {
        val tonight = listOf(
            match(
                0,
                listOf("A", "B"),
                listOf("C", "D"),
                started = now.minusMinutes(10),
                finished = now.minusMinutes(1),
            )
        )
        val result = suggestMatch(
            equal, listOf("A", "B", "C", "D", "E", "F"), emptyList(), tonight, now
        )!!
        val quad = result.teamA + result.teamB
        assertTrue(quad.contains("E") && quad.contains("F"))
        assertEquals(600.0, result.stats.getValue("E").rest, 0.001)
        assertEquals(60.0, result.stats.getValue("A").rest, 0.001)
    }

    @Test
    fun playerStatsCountsAndRest() {
        val tonight = listOf(
            match(
                0, listOf("A", "B"), listOf("C", "D"),
                started = now.minusMinutes(30), finished = now.minusMinutes(20),
            ),
            match(
                1, listOf("A", "B"), listOf("E", "F"),
                started = now.minusMinutes(10), finished = now.minusMinutes(2),
            ),
        )
        val (played, rest) = playerStats(listOf("A", "B", "C", "D", "E", "F"), tonight, now)
        assertEquals(2, played.getValue("A"))
        assertEquals(1, played.getValue("C"))
        assertEquals(1, played.getValue("E"))
        assertEquals(120.0, rest.getValue("A"), 0.001)
        assertEquals(1200.0, rest.getValue("C"), 0.001)
        assertEquals(120.0, rest.getValue("E"), 0.001)
    }

    @Test
    fun freshNightPrefersBalance() {
        val members = makeMembers(
            mapOf("A" to 800.0, "B" to 820.0, "C" to 1200.0, "D" to 1180.0)
        )
        val result = suggestMatch(
            members, listOf("A", "B", "C", "D"), emptyList(), emptyList(), now
        )!!
        for (team in listOf(result.teamA, result.teamB)) {
            val ratings = team.map { members.getValue(it).ratings.getValue("open_double") }.sorted()
            assertTrue(ratings[0] < 1000 && ratings[1] > 1000)
        }
        assertTrue(result.balance <= 20.0)
    }

    @Test
    fun avoidsRepeatedPartner() {
        val history = listOf(
            match(
                0, listOf("A", "B"), listOf("C", "D"),
                started = now.minusDays(1), finished = now.minusDays(1).plusMinutes(20),
            )
        )
        val result = suggestMatch(
            equal, listOf("A", "B", "C", "D", "E", "F"), history, emptyList(), now
        )!!
        val (partners, _) = pairCounts(history)
        for (team in listOf(result.teamA, result.teamB)) {
            assertEquals(0, partners[team.sorted()] ?: 0)
        }
    }

    @Test
    fun excludedQuadsAreSkipped() {
        val ids = listOf("A", "B", "C", "D", "E", "F")
        val first = suggestMatch(equal, ids, emptyList(), emptyList(), now)!!
        val excluded = setOf((first.teamA + first.teamB).toSet())
        val second = suggestMatch(equal, ids, emptyList(), emptyList(), now, exclude = excluded)
        assertNotNull(second)
        assertTrue((second!!.teamA + second.teamB).toSet() !in excluded)
    }

    @Test
    fun multiCourtSuggestionsAreDisjoint() {
        val eight = makeMembers(listOf("A", "B", "C", "D", "E", "F", "G", "H").associateWith { 1000.0 })
        val out = suggestMatches(
            eight, listOf("A", "B", "C", "D", "E", "F", "G", "H"),
            emptyList(), emptyList(), now,
            listOf(1 to "open_double", 2 to "open_double"),
        )
        assertEquals(setOf(1, 2), out.keys)
        val quad1 = out.getValue(1).teamA.toSet() + out.getValue(1).teamB
        val quad2 = out.getValue(2).teamA.toSet() + out.getValue(2).teamB
        assertTrue(quad1.intersect(quad2).isEmpty())
        assertEquals(4, quad1.size)
        assertEquals(4, quad2.size)
    }

    @Test
    fun multiCourtStopsWhenPoolExhausted() {
        val six = makeMembers(listOf("A", "B", "C", "D", "E", "F").associateWith { 1000.0 })
        val out = suggestMatches(
            six, listOf("A", "B", "C", "D", "E", "F"),
            emptyList(), emptyList(), now,
            listOf(1 to "open_double", 2 to "open_double"),
        )
        assertEquals(setOf(1), out.keys)
    }

    @Test
    fun singlesPicksTwoPlayers() {
        val members = makeMembers(mapOf("A" to 1000.0, "B" to 1000.0, "C" to 1000.0, "D" to 1000.0))
        val result = suggestMatch(
            members, listOf("A", "B", "C", "D"), emptyList(), emptyList(), now,
            board = "open_single",
        )!!
        assertEquals(1, result.teamA.size)
        assertEquals(1, result.teamB.size)
        assertEquals("open_single", result.board)
    }

    @Test
    fun singlesRequiresTwoPlayers() {
        val members = makeMembers(mapOf("A" to 1000.0))
        assertNull(
            suggestMatch(members, listOf("A"), emptyList(), emptyList(), now, board = "open_single")
        )
    }

    @Test
    fun womensBoardFiltersGender() {
        val members = makeMembers(
            listOf("A", "B", "C", "D", "E", "F").associateWith { 1000.0 }
        ).mapValues { (pid, m) ->
            when (pid) {
                in listOf("A", "B", "C", "D") -> m.copy(gender = "f")
                else -> m.copy(gender = "m")
            }
        }
        val out = suggestMatches(
            members, listOf("A", "B", "C", "D", "E", "F"),
            emptyList(), emptyList(), now,
            listOf(1 to "womens_double"),
        )
        assertTrue(1 in out)
        val picked = out.getValue(1).teamA.toSet() + out.getValue(1).teamB
        assertTrue(picked.all { it in setOf("A", "B", "C", "D") })
    }

    @Test
    fun mixedBoardsSharePool() {
        val members = makeMembers(listOf("A", "B", "C", "D", "E", "F", "G", "H").associateWith { 1000.0 })
            .mapValues { (pid, m) ->
                if (pid in listOf("A", "B", "C", "D")) m.copy(gender = "f") else m
            }
        val out = suggestMatches(
            members, listOf("A", "B", "C", "D", "E", "F", "G", "H"),
            emptyList(), emptyList(), now,
            listOf(1 to "womens_double", 2 to "open_double"),
        )
        assertEquals(setOf(1, 2), out.keys)
        val q1 = out.getValue(1).teamA.toSet() + out.getValue(1).teamB
        val q2 = out.getValue(2).teamA.toSet() + out.getValue(2).teamB
        assertTrue(q1.all { it in setOf("A", "B", "C", "D") })
        assertTrue(q1.intersect(q2).isEmpty())
        assertEquals(4, q2.size)
    }

    @Test
    fun substitutesInsufficientPool() {
        assertNull(
            suggestSubstitutes(
                equal, listOf("E"), listOf("A", "B"), listOf("C", "D"), listOf("A", "B"),
                emptyList(), emptyList(), now, "open_double",
            )
        )
    }

    @Test
    fun substitutesFillsOnlyOutgoingSlots() {
        val result = suggestSubstitutes(
            equal, listOf("E", "F"), listOf("A", "B"), listOf("C", "D"), listOf("B"),
            emptyList(), emptyList(), now, "open_double",
        )!!
        assertEquals(setOf("B"), result.keys)
        assertTrue(result.getValue("B") in setOf("E", "F"))
    }

    @Test
    fun substitutesMultipleSlots() {
        val result = suggestSubstitutes(
            equal, listOf("E", "F"), listOf("A", "B"), listOf("C", "D"), listOf("A", "C"),
            emptyList(), emptyList(), now, "open_double",
        )!!
        assertEquals(setOf("A", "C"), result.keys)
        assertEquals(setOf("E", "F"), result.values.toSet())
        assertTrue(result.getValue("A") != result.getValue("C"))
    }

    @Test
    fun substitutesPicksLongestRested() {
        val tonight = listOf(
            match(
                0, listOf("E", "F"), listOf("A", "B"),
                started = now.minusMinutes(10), finished = now.minusMinutes(5),
            ),
            match(
                1, listOf("E", "C"), listOf("A", "D"),
                started = now.minusMinutes(4), finished = now.minusMinutes(1),
            ),
        )
        val result = suggestSubstitutes(
            equal, listOf("E", "F"), listOf("A", "B"), listOf("C", "D"), listOf("A"),
            emptyList(), tonight, now, "open_double",
        )!!
        assertEquals(mapOf("A" to "F"), result)
    }

    @Test
    fun substitutesPrefersBalance() {
        val members = makeMembers(
            mapOf(
                "A" to 800.0, "B" to 800.0, "C" to 1200.0,
                "D" to 1200.0, "E" to 1050.0, "F" to 950.0,
            )
        )
        val result = suggestSubstitutes(
            members, listOf("E", "F"), listOf("A", "B"), listOf("C", "D"), listOf("A"),
            emptyList(), emptyList(), now, "open_double",
        )!!
        assertEquals(mapOf("A" to "E"), result)
    }

    @Test
    fun combinationsAndPermutationsBasics() {
        assertEquals(
            listOf(listOf("A", "B"), listOf("A", "C"), listOf("B", "C")),
            combinations(listOf("A", "B", "C"), 2),
        )
        assertEquals(6, permutations(listOf("A", "B", "C"), 2).size)
        assertEquals(
            listOf(listOf("A", "B"), listOf("A", "C"), listOf("B", "A")),
            permutations(listOf("A", "B", "C"), 2).take(3),
        )
    }

    @Test
    fun restNeverNegative() {
        val future = match(
            0, listOf("A", "B"), listOf("C", "D"),
            started = now.plusMinutes(5), finished = now.plusMinutes(10),
        )
        val (_, rest) = playerStats(listOf("A"), listOf(future), now)
        assertEquals(0.0, rest.getValue("A"), 0.001)
        assertEquals(0, ChronoUnit.SECONDS.between(now, now))
    }

    @Test
    fun restAnchorsToSessionStartWhenMatchesDeleted() {
        val session = now.minusMinutes(5)
        val (_, rest) = playerStats(listOf("A", "B"), emptyList(), now, sessionStart = session)
        assertEquals(300.0, rest.getValue("A"), 0.001)
        assertEquals(300.0, rest.getValue("B"), 0.001)
    }

    @Test
    fun sessionStartBeatsFirstStart() {
        val session = now.minusMinutes(10)
        val active = Match(
            id = "m0", date = "2026-10-09", seq = 0, court = 1,
            teamA = listOf("E"), teamB = listOf("F"),
            startedAt = now.minusMinutes(2).toString(),
        )
        val (_, rest) = playerStats(listOf("A", "B"), listOf(active), now, sessionStart = session)
        assertEquals(600.0, rest.getValue("A"), 0.001)
    }

    @Test
    fun lastFinishStillWinsOverSessionStart() {
        val session = now.minusMinutes(30)
        val finishedMatch = match(
            0, listOf("A", "B"), listOf("C", "D"),
            started = now.minusMinutes(10), finished = now.minusMinutes(3),
        )
        val (_, rest) = playerStats(listOf("A"), listOf(finishedMatch), now, sessionStart = session)
        assertEquals(180.0, rest.getValue("A"), 0.001)
    }

    @Test
    fun departedPlayersRestFromSubOutTime() {
        val active = Match(
            id = "m0", date = "2026-10-09", seq = 0, court = 1,
            teamA = listOf("E", "B"), teamB = listOf("C", "D"),
            startedAt = now.minusMinutes(20).toString(),
            departed = mapOf("A" to now.minusMinutes(4).toString()),
        )
        val (_, rest) = playerStats(listOf("A"), listOf(active), now)
        assertEquals(240.0, rest.getValue("A"), 0.001)
    }

    @Test
    fun departedBeatsEarlierFinish() {
        val earlier = match(
            0, listOf("A", "B"), listOf("C", "D"),
            started = now.minusMinutes(30), finished = now.minusMinutes(25),
        )
        val active = Match(
            id = "m1", date = "2026-10-09", seq = 1, court = 1,
            teamA = listOf("E", "F"), teamB = listOf("C", "D"),
            startedAt = now.minusMinutes(10).toString(),
            departed = mapOf("A" to now.minusMinutes(1).toString()),
        )
        val (_, rest) = playerStats(listOf("A"), listOf(earlier, active), now)
        assertEquals(60.0, rest.getValue("A"), 0.001)
    }
}
