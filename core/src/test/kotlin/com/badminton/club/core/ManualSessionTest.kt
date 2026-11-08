package com.badminton.club.core

import org.junit.Assert.*
import org.junit.Test

class ManualSessionTest {
    private val attendees = listOf("g", "c", "a", "f", "d", "b", "e", "h")
    private val members = attendees.associateWith { Member(it, it, gender = if (it == "a") "m" else "f") }

    @Test
    fun entersInAttendanceOrderAndLimitsCandidatesToFreePlayers() {
        val session = ManualSession().enter(attendees, 6)
        assertEquals(attendees, session.queue)
        assertEquals(listOf("c", "a", "f", "d", "b", "e"), session.candidates(setOf("g")))
        assertEquals(4, session.enter(attendees, 1).candidateCount)
        assertEquals(30, session.enter(attendees, 90).candidateCount)
    }

    @Test
    fun anyFourOfTopNCanStartWithoutPickingHead() {
        var session = ManualSession().enter(attendees, 6)
        listOf("a", "f", "d", "b").forEachIndexed { slot, id -> session = session.place(id, slot, emptySet()) }
        val started = session.start(emptySet(), members, DEFAULT_BOARD)
        assertEquals(listOf("g", "c", "e", "h"), started.queue)
        assertTrue(started.picks.all { it == null })
    }

    @Test
    fun slotDragSwapsPlayersAndRejectsWaitingOrPlayingCandidates() {
        val session = ManualSession().enter(attendees, 4)
            .place("g", 0, emptySet()).place("c", 1, emptySet())
        val swapped = session.place("g", 1, emptySet())
        assertEquals(listOf("c", "g", null, null), swapped.picks)
        assertEquals(swapped, swapped.place("h", 2, emptySet()))
        assertEquals(swapped, swapped.place("g", 2, setOf("g")))
    }

    @Test
    fun returningPlayersFollowAttendanceOrderAtQueueTail() {
        val session = ManualSession().enter(listOf("e", "h"), 6)
        val returned = session.requeue(listOf("b", "d", "a", "f", "gone"), attendees)
        assertEquals(listOf("e", "h", "a", "f", "d", "b"), returned.queue)
        assertEquals(returned, returned.requeue(listOf("b", "d", "a", "f"), attendees))
    }

    @Test
    fun attendanceEditsResyncWaitingQueueAndClearIneligiblePicks() {
        val session = ManualSession().enter(attendees, 4).place("g", 0, emptySet()).place("c", 1, emptySet())
        val updated = session.syncAttendance(listOf("e", "c", "h", "f", "a"), setOf("h"))
        assertEquals(listOf("e", "c", "f", "a"), updated.queue)
        assertEquals(listOf(null, "c", null, null), updated.picks)
    }

    @Test
    fun pageChangesRemoveDeletedMembersWithoutResettingLiveQueueOrder() {
        val session = ManualSession().enter(attendees, 6)
            .copy(queue = listOf("e", "h", "g", "c", "a", "f", "d", "b"))
            .place("g", 0, emptySet())
        val updated = session.reconcile(attendees - "g", setOf("c"))
        assertEquals(listOf("e", "h", "a", "f", "d", "b"), updated.queue)
        assertTrue(updated.picks.all { it == null })
    }

    @Test
    fun startRejectsStalePicksSinglesAndWrongGender() {
        var session = ManualSession().enter(attendees, 4)
        attendees.take(4).forEachIndexed { slot, id -> session = session.place(id, slot, emptySet()) }
        assertThrows(IllegalArgumentException::class.java) { session.start(setOf("g"), members, DEFAULT_BOARD) }
        assertThrows(IllegalArgumentException::class.java) { session.start(emptySet(), members, "womens_double") }
        assertThrows(IllegalArgumentException::class.java) { session.start(emptySet(), members, "open_single") }
        assertThrows(IllegalArgumentException::class.java) { session.remove("g").start(emptySet(), members, DEFAULT_BOARD) }
    }
}
