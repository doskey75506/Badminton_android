package com.badminton.club

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.badminton.club.core.Member
import kotlin.math.roundToInt

/** All drag coordinates use the same root space, including dialogs and wrapped rows. */
internal class PegDragScope {
    val targets = mutableMapOf<String, Rect>()
    var dragged by mutableStateOf<String?>(null)
    var position by mutableStateOf(Offset.Zero)
    var origin = Offset.Zero
    val target: String? get() {
        val hit = targets.entries.filter { it.value.contains(position) }
            .minByOrNull { it.value.width * it.value.height }?.key
        if (hit != "attending") return hit
        // Gaps between wrapped pegs are insertion positions too, not just the tail.
        return targets.entries.filter { it.key.startsWith("member:") }.minByOrNull {
            val rect = it.value
            val dx = (position.x - position.x.coerceIn(rect.left, rect.right))
            val dy = (position.y - position.y.coerceIn(rect.top, rect.bottom))
            dx * dx + dy * dy
        }?.key ?: hit
    }
    val after: Boolean get() = target?.let { position.x > targets.getValue(it).center.x } ?: false

    fun targetModifier(key: String) = Modifier.composed {
        DisposableEffect(key) { onDispose { targets.remove(key) } }
        Modifier.onGloballyPositioned { targets[key] = it.boundsInRoot() }
    }

    fun sourceModifier(id: String, onDrop: (String, String, Boolean) -> Unit) = Modifier.composed {
        val bounds = remember { arrayOf(Rect.Zero) }
        val latestDrop by rememberUpdatedState(onDrop)
        Modifier.onGloballyPositioned { bounds[0] = it.boundsInRoot() }
            .pointerInput(id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        dragged = id
                        position = bounds[0].topLeft + offset
                    },
                    onDrag = { change, amount -> change.consume(); position += amount },
                    onDragCancel = { dragged = null },
                    onDragEnd = {
                        target?.let { latestDrop(id, it, after) }
                        dragged = null
                    },
                )
            }
    }

}

@Composable
internal fun PegDragHost(
    members: Map<String, Member>,
    content: @Composable (PegDragScope) -> Unit,
) {
    val drag = remember { PegDragScope() }
    Box(Modifier.fillMaxWidth().onGloballyPositioned { drag.origin = it.boundsInRoot().topLeft }) {
        content(drag)
        drag.dragged?.let { id ->
            Peg(
                name = members[id]?.name ?: id,
                modifier = Modifier.offset {
                    IntOffset((drag.position.x - drag.origin.x).roundToInt() - 27.dp.roundToPx(),
                        (drag.position.y - drag.origin.y).roundToInt() - 35.dp.roundToPx())
                },
                selected = true,
            )
        }
    }
}

@Composable
internal fun Peg(name: String, modifier: Modifier = Modifier, court: Int? = null, selected: Boolean = false) {
    Box(modifier.size(66.dp, 116.dp), contentAlignment = Alignment.TopCenter) {
        Canvas(Modifier.fillMaxWidth().height(24.dp)) {
            drawLine(Color(0xFF96703F), Offset(0f, 15.dp.toPx()), Offset(size.width, 15.dp.toPx()), 4.dp.toPx())
        }
        Column(
            Modifier.padding(top = 3.dp).size(54.dp, 104.dp)
                .background(Brush.linearGradient(listOf(Color(0xFFE8D9AD), Color(0xFFC9B37A))), RoundedCornerShape(8.dp))
                .border(if (selected) 2.dp else 1.dp, if (selected) Color(0xFF386C5F) else Color(0xFF9A8054), RoundedCornerShape(8.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(26.dp))
            repeat(3) {
                Box(Modifier.padding(vertical = 1.dp).size(46.dp, 3.dp).background(Color(0xFF9DA1A8), RoundedCornerShape(2.dp)))
            }
            Box(Modifier.weight(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                Text(name, color = Color(0xFF4D3D24), style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 3)
            }
        }
        if (court != null) Text(court.toString(), Modifier.align(Alignment.TopEnd)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)).padding(horizontal = 5.dp),
            color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AttendancePanel(
    state: AppState,
    members: Map<String, Member>,
    checked: List<String>,
    onCourt: Set<String>,
    courtByPlayer: Map<String, Int>,
    dateIso: String,
    restrictToNonPlaying: Boolean = false,
) {
    val latestChecked by rememberUpdatedState(checked)
    val latestOnCourt by rememberUpdatedState(onCourt)
    val update by rememberUpdatedState<(String, String, Boolean) -> Unit>({ id, target, after ->
        if (target != "member:$id" && !(restrictToNonPlaying && id in latestOnCourt)) {
            val updated = latestChecked.toMutableList().apply { remove(id) }
            if (target != "absent") {
                val targetId = target.removePrefix("member:")
                val at = updated.indexOf(targetId)
                updated.add(if (at < 0) updated.size else at + if (after) 1 else 0, id)
            }
            state.storage.setAttendance(state.club, dateIso, updated)
            state.manual = state.manual.syncAttendance(updated, latestOnCourt)
            state.bump()
        }
    })
    Text(T("attendance"), style = MaterialTheme.typography.titleMedium)
    PegDragHost(members) { drag ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val absent = members.values.filter { it.id !in checked }.sortedBy { it.name }.map { it.id }
            listOf("absent" to absent, "attending" to checked).forEach { (area, ids) ->
                Text(T(if (area == "absent") "area_absent" else "area_attending") + " (${ids.size})")
                FlowRow(
                    Modifier.fillMaxWidth().heightIn(min = 76.dp)
                        .then(drag.targetModifier(area))
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .border(1.dp, if (drag.target == area) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (ids.isEmpty()) Canvas(Modifier.fillMaxWidth().height(56.dp)) {
                        drawLine(Color(0xFF96703F), Offset(0f, 15.dp.toPx()), Offset(size.width, 15.dp.toPx()), 4.dp.toPx())
                    }
                    ids.forEach { id ->
                        key(id) {
                            val locked = restrictToNonPlaying && id in onCourt
                            val targetKey = "member:$id"
                            var modifier: Modifier = Modifier
                            if (area == "attending") modifier = modifier.then(drag.targetModifier(targetKey))
                            if (!locked) modifier = modifier
                                .then(drag.sourceModifier(id) { pid, target, after -> update(pid, target, after) })
                                .clickable { update(id, if (id in checked) "absent" else "attending", false) }
                            Box(modifier) {
                                Peg(members[id]?.name ?: id, court = courtByPlayer[id],
                                    modifier = Modifier.alpha(if (drag.dragged == id) 0.3f else 1f))
                                if (drag.dragged != null && drag.target == targetKey) {
                                    Box(Modifier.align(if (drag.after) Alignment.CenterEnd else Alignment.CenterStart)
                                        .width(3.dp).height(108.dp).background(MaterialTheme.colorScheme.primary))
                                }
                            }
                            DisposableEffect(id, area) {
                                onDispose { drag.targets.remove(targetKey) }
                            }
                        }
                    }
                }
            }
            Text(T("attendance_hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}
