package com.badminton.club.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
    fun unscoredFinishKeepsHistoryAndRatingsButFreesCourt() {
        val club = storage.createClub("c")
        val member = storage.addMember(club.id, "甲", 1200.0)
        val before = storage.loadMembers(club.id)
        val match = storage.addMatch(club.id, "2026-11-08", 1,
            listOf(member.id), listOf("b"), mapOf(member.id to "甲"), mapOf(member.id to 1200.0))
        storage.finishMatchUnscored(club.id, match.id)
        val finished = storage.loadMatches(club.id).single()
        assertTrue(finished.finishedAt != null)
        assertEquals(match.copy(finishedAt = finished.finishedAt), finished)
        assertEquals(before, storage.loadMembers(club.id))
        assertTrue(storage.activeMatches(club.id, match.date).isEmpty())
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            storage.cancelMatch(club.id, match.id)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            storage.swapMatchPlayers(club.id, match.id, mapOf(member.id to "e"), mapOf("e" to ("乙" to 1000.0)))
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            storage.finishMatchUnscored(club.id, match.id)
        }
        val restored = Storage(tmp.newFolder("unscored"))
        restored.importBytes(storage.exportBytes())
        assertEquals(finished, restored.loadMatches(club.id).single())
        assertTrue(restored.activeMatches(club.id, match.date).isEmpty())
    }

    @Test
    fun unscoredFinishRejectsMissingAndScoredMatches() {
        val club = storage.createClub("c")
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            storage.finishMatchUnscored(club.id, "missing")
        }
        val match = storage.addMatch(club.id, "2026-11-08", 1,
            listOf("a"), listOf("b"), emptyMap(), emptyMap())
        storage.finishMatch(club.id, match.id, 21, 10, emptyMap())
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            storage.finishMatchUnscored(club.id, match.id)
        }
        assertEquals(21, storage.loadMatches(club.id).single().scoreA)
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

    @Test
    fun exportImportRoundtrip() {
        val club = storage.createClub("测试俱乐部")
        val m = storage.addMember(club.id, "张三", 1200.0, gender = "f")
        storage.setSettings(club.id, Settings(courts = 5))
        storage.setAttendance(club.id, "2026-10-09", listOf(m.id))
        storage.addMatch(
            club.id, "2026-10-09", 1,
            listOf("a"), listOf("b"), emptyMap(), emptyMap(),
        )

        val bytes = storage.exportBytes()
        val fresh = Storage(tmp.newFolder("fresh"))
        fresh.importBytes(bytes)

        assertEquals(listOf("测试俱乐部"), fresh.listClubs().map { it.name })
        assertEquals(5, fresh.getSettings(club.id).courts)
        assertEquals(listOf("张三"), fresh.loadMembers(club.id).map { it.name })
        assertEquals(
            1200.0,
            fresh.membersById(club.id).getValue(m.id).ratings.getValue("open_double"),
            0.0,
        )
        assertEquals(listOf(m.id), fresh.getAttendance(club.id, "2026-10-09"))
        assertEquals(1, fresh.loadMatches(club.id).size)
        assertEquals(listOf("测试俱乐部"), storage.listClubs().map { it.name })
    }

    @Test
    fun importFullyOverwritesExistingData() {
        val src = Storage(tmp.newFolder("src"))
        val club = src.createClub("来源")
        src.addMember(club.id, "甲")

        val dstDir = tmp.newFolder("dst")
        val dst = Storage(dstDir)
        val stale = dst.createClub("本机旧数据")
        dst.addMember(stale.id, "路人")

        dst.importBytes(src.exportBytes())

        assertEquals(listOf("来源"), dst.listClubs().map { it.name })
        assertEquals(listOf("甲"), dst.loadMembers(club.id).map { it.name })
        assertTrue(dst.loadMembers(stale.id).isEmpty())
        assertTrue(!File(dstDir, "clubs/${stale.id}").exists())
    }

    @Test
    fun importRejectsInvalidBackupAndKeepsData() {
        val keeper = storage.createClub("keep")

        val badZip = ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { zos ->
                zos.putNextEntry(ZipEntry("foo.txt"))
                zos.write("hello".toByteArray())
                zos.closeEntry()
            }
        }.toByteArray()

        val target = Storage(tmp.newFolder("target"))
        target.createClub("mine")
        for (bad in listOf(ByteArray(0), "not a zip".toByteArray(), badZip)) {
            try {
                target.importBytes(bad)
                fail("expected IllegalArgumentException")
            } catch (_: IllegalArgumentException) {
            }
        }
        assertEquals(listOf("mine"), target.listClubs().map { it.name })

        target.importBytes(storage.exportBytes())
        assertEquals(listOf("keep"), target.listClubs().map { it.name })
        assertEquals(listOf(keeper.id), target.listClubs().map { it.id })
    }

    @Test
    fun importsIosUnscoredBackupAndExportsCompatibleRoundtrip() {
        val bytes = javaClass.getResourceAsStream("/ios-unscored-backup.zip")!!.use { it.readBytes() }
        val imported = Storage(tmp.newFolder("ios"))
        imported.importBytes(bytes)
        val club = imported.listClubs().single()
        assertEquals("iOS 兼容测试", club.name)
        val members = imported.loadMembers(club.id)
        assertEquals(4, members.size)
        assertTrue(members.all { it.ratings.values.all { r -> r == 1200.0 } })
        val date = "2026-11-08"
        assertEquals(members.map { it.id }.reversed(), imported.getAttendance(club.id, date))
        val match = imported.loadMatches(club.id).single()
        assertTrue(match.finishedAt != null)
        assertEquals(null, match.scoreA)
        assertEquals(null, match.winner)
        assertEquals(null, match.ratingsAfter)
        assertTrue(imported.activeMatches(club.id, date).isEmpty())
        val scored = imported.addMatch(club.id, date, 1, match.teamA, match.teamB, match.names, match.ratingsBefore)
        imported.finishMatch(club.id, scored.id, 21, 15, emptyMap())
        // Used by the Swift compatibility smoke check; stays in ignored build output.
        File("build/ios-roundtrip.zip").writeBytes(imported.exportBytes())
    }

    @Test
    fun exportWithoutDataFails() {
        try {
            storage.exportBytes()
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }
}
