package com.badminton.club.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var storage: Storage

    @Before
    fun setUp() {
        storage = Storage(tmp.root)
    }

    @Test
    fun clubLifecycle() {
        val club = storage.createClub("测试俱乐部")
        assertEquals("测试俱乐部", storage.listClubs().single().name)
        try {
            storage.createClub("测试俱乐部")
            fail("expected DuplicateNameException")
        } catch (_: DuplicateNameException) {
        }
        try {
            storage.createClub("   ")
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
        assertEquals(DEFAULT_COURTS, storage.getSettings(club.id).courts)
        storage.setSettings(club.id, Settings(courts = 5))
        assertEquals(5, storage.getSettings(club.id).courts)
    }

    @Test
    fun memberLifecycle() {
        val club = storage.createClub("c")
        val m = storage.addMember(club.id, "张三", 1200.0, gender = "f")
        assertEquals(1200.0, m.ratings.getValue("open_double"), 0.0)
        assertEquals(4, m.ratings.size)
        try {
            storage.addMember(club.id, "张三")
            fail("expected DuplicateNameException")
        } catch (_: DuplicateNameException) {
        }
        assertEquals(m, storage.membersById(club.id).getValue(m.id))
        storage.deleteMember(club.id, m.id)
        assertTrue(storage.loadMembers(club.id).isEmpty())
    }

    @Test
    fun matchRoundtrip() {
        val club = storage.createClub("c")
        val match = storage.addMatch(
            club.id, "2026-10-09", 1,
            listOf("a", "b"), listOf("c", "d"),
            mapOf("a" to "甲", "b" to "乙", "c" to "丙", "d" to "丁"),
            mapOf("a" to 1000.0, "b" to 1000.0, "c" to 1000.0, "d" to 1000.0),
            board = "womens_double",
        )
        assertEquals(0, match.seq)
        val second = storage.addMatch(
            club.id, "2026-10-09", 2,
            listOf("e"), listOf("f"), emptyMap(), emptyMap(),
        )
        assertEquals(1, second.seq)
        assertEquals(2, storage.matchesOn(club.id, "2026-10-09").size)
        assertEquals(2, storage.activeMatches(club.id, "2026-10-09").size)

        storage.finishMatch(
            club.id, match.id, 21, 15,
            mapOf("a" to 1010.0, "b" to 1010.0, "c" to 990.0, "d" to 990.0),
        )
        assertEquals(1, storage.activeMatches(club.id, "2026-10-09").size)
        val finished = storage.matchesOn(club.id, "2026-10-09").first { it.id == match.id }
        assertEquals(21, finished.scoreA)
        assertEquals("A", finished.winner)
        assertEquals(1010.0, finished.ratingsAfter!!.getValue("a"), 0.0)

        try {
            storage.cancelMatch(club.id, match.id)
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
        storage.cancelMatch(club.id, second.id)
        assertEquals(1, storage.matchesOn(club.id, "2026-10-09").size)
    }

    @Test
    fun swapPlayers() {
        val club = storage.createClub("c")
        val match = storage.addMatch(
            club.id, "2026-10-09", 1,
            listOf("a", "b"), listOf("c", "d"),
            mapOf("a" to "甲", "b" to "乙", "c" to "丙", "d" to "丁"),
            mapOf("a" to 1000.0, "b" to 1000.0, "c" to 1000.0, "d" to 1000.0),
        )
        val updated = storage.swapMatchPlayers(
            club.id, match.id, mapOf("a" to "e"),
            mapOf("e" to ("戊" to 1050.0)),
        )
        assertEquals(listOf("e", "b"), updated.teamA)
        assertEquals("戊", updated.names.getValue("e"))
        assertTrue("a" !in updated.names)
        assertEquals(1050.0, updated.ratingsBefore.getValue("e"), 0.0)
        assertEquals(match.id, updated.id)
        assertEquals(match.startedAt, updated.startedAt)
        assertTrue(updated.departed.containsKey("a"))
    }

    @Test
    fun addMatchRecordsNightStartOnce() {
        val club = storage.createClub("c")
        assertTrue(storage.getSettings(club.id).nightStarts.isEmpty())
        storage.addMatch(
            club.id, "2026-10-09", 1,
            listOf("a", "b"), listOf("c", "d"), emptyMap(), emptyMap(),
        )
        val first = storage.getSettings(club.id).nightStarts.getValue("2026-10-09")
        storage.addMatch(
            club.id, "2026-10-09", 2,
            listOf("e"), listOf("f"), emptyMap(), emptyMap(),
        )
        assertEquals(first, storage.getSettings(club.id).nightStarts.getValue("2026-10-09"))
        storage.addMatch(
            club.id, "2026-10-10", 1,
            listOf("e"), listOf("f"), emptyMap(), emptyMap(),
        )
        assertTrue(storage.getSettings(club.id).nightStarts.containsKey("2026-10-10"))
    }

    @Test
    fun attendanceRoundtrip() {
        val club = storage.createClub("c")
        val m1 = storage.addMember(club.id, "甲")
        val m2 = storage.addMember(club.id, "乙")
        storage.setAttendance(club.id, "2026-10-09", listOf(m1.id, m2.id))
        assertEquals(listOf(m1.id, m2.id), storage.getAttendance(club.id, "2026-10-09"))
        storage.deleteMember(club.id, m1.id)
        assertEquals(listOf(m2.id), storage.getAttendance(club.id, "2026-10-09"))
    }
}
