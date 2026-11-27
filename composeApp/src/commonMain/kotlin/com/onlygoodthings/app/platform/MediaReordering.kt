package com.onlygoodthings.app.platform

/** Visible thumbnail centers, in the same coordinate space as the dragged pointer. */
internal data class MediaDropSlot(val id: String, val center: Float)

internal fun mediaDropIndex(ids: List<String>, draggedId: String, pointer: Float, slots: List<MediaDropSlot>): Int? {
    val from = ids.indexOf(draggedId)
    if (from < 0 || !pointer.isFinite()) return null
    val visible = slots.filter { it.id != draggedId && it.id in ids && it.center.isFinite() }.sortedBy { it.center }
    if (visible.isEmpty()) return from
    val before = visible.firstOrNull { pointer <= it.center }
    val insertion = if (before != null) ids.indexOf(before.id) else ids.indexOf(visible.last().id) + 1
    return (insertion - if (insertion > from) 1 else 0).coerceIn(0, ids.lastIndex)
}

/** Move the existing object: never replace its edited copy, poster, audio choice or ID. */
internal fun movePickedMedia(items: MutableList<OgtPickedMedia>, id: String, destination: Int): Boolean {
    val from = items.indexOfFirst { it.id == id }
    if (from < 0) return false
    val to = destination.coerceIn(0, items.lastIndex)
    if (from == to) return false
    items.add(to, items.removeAt(from))
    return true
}
