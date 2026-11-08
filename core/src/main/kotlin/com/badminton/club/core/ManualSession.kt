package com.badminton.club.core

/** Session-only queue. Attendance order also determines the order of returning players. */
data class ManualSession(
    val entered: Boolean = false,
    val candidateCount: Int = 6,
    val queue: List<String> = emptyList(),
    val picks: List<String?> = List(4) { null },
) {
    fun enter(attendees: List<String>, n: Int) = ManualSession(
        entered = true, candidateCount = n.coerceIn(4, 30), queue = attendees.distinct(),
    )

    fun candidates(onCourt: Set<String>) = queue.filter { it !in onCourt }.take(candidateCount)

    fun place(id: String, slot: Int, onCourt: Set<String>): ManualSession {
        if (id !in candidates(onCourt) || slot !in picks.indices) return this
        val updated = picks.toMutableList()
        val previous = updated.indexOf(id)
        if (previous >= 0) updated[previous] = updated[slot]
        updated[slot] = id
        return copy(picks = updated)
    }

    fun remove(id: String) = copy(picks = picks.map { if (it == id) null else it })

    fun start(onCourt: Set<String>, members: Map<String, Member>, board: String): ManualSession {
        val ids = picks.filterNotNull()
        require(ids.size == 4 && ids.distinct().size == 4 && ids.all { it in candidates(onCourt) })
        require(board.endsWith("double") && ids.all { genderOk(members[it], board) })
        return copy(queue = queue - ids.toSet(), picks = List(4) { null })
    }

    fun requeue(ids: List<String>, attendance: List<String>): ManualSession {
        if (!entered) return this
        return copy(
            queue = (queue - ids.toSet()) + attendance.filter { it in ids },
            picks = picks.map { if (it in ids) null else it },
        )
    }

    /** Preserve the live queue across page switches while removing deleted/playing members. */
    fun reconcile(attendance: List<String>, onCourt: Set<String>): ManualSession {
        if (!entered) return this
        val waiting = attendance.filter { it !in onCourt }
        val updated = queue.filter { it in waiting } + waiting.filter { it !in queue }
        return copy(queue = updated, picks = picks.map { if (it in updated.take(candidateCount)) it else null })
    }

    fun syncAttendance(attendance: List<String>, onCourt: Set<String>): ManualSession {
        if (!entered) return this
        val waiting = attendance.filter { it !in onCourt }
        return copy(queue = waiting, picks = picks.map { if (it in waiting.take(candidateCount)) it else null })
    }
}
