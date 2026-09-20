package com.onlygoodthings.app.data

import com.onlygoodthings.app.media.OgtMediaCache
import com.onlygoodthings.app.platform.OgtPickedMedia
import com.onlygoodthings.shared.data.local.LocalAnimalListing
import com.onlygoodthings.shared.data.local.LocalPostMedia
import com.onlygoodthings.shared.data.local.LocalSocialPost
import com.onlygoodthings.shared.data.local.LocalUser
import com.onlygoodthings.shared.data.local.OgtLocalDatabase
import com.onlygoodthings.shared.data.remote.RestAnimalsRepository
import com.onlygoodthings.shared.domain.GeoPoint
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

private val UuidPattern = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
)

fun String.isUuid(): Boolean = UuidPattern.matches(this)

private val FallbackPoint = GeoPoint(-34.6037, -58.3816)

/** Reenvía una ficha que quedó solo en el dispositivo (`animal-pub-*`). */
suspend fun persistUnpublishedAnimal(
    db: OgtLocalDatabase,
    api: RestAnimalsRepository,
    me: LocalUser,
    post: LocalSocialPost,
): LocalSocialPost = persistAnimalOnServer(db, api, me, post, emptyList(), me.asPoint())

/** Graba o actualiza la ficha en Postgres y alinea los IDs locales con los del servidor. */
suspend fun persistAnimalOnServer(
    db: OgtLocalDatabase,
    api: RestAnimalsRepository,
    me: LocalUser,
    post: LocalSocialPost,
    draft: List<OgtPickedMedia>,
    here: GeoPoint?,
): LocalSocialPost {
    val listing = post.listingId?.let { id -> db.animals.firstOrNull { it.id == id } } ?: return post
    val localMedia = db.mediaOf(post.id).ifEmpty { draft.toStored(post.id, listing.kind) }
    localMedia.forEach { row ->
        OgtMediaCache.ingest(row.url)
        row.posterUrl?.let { OgtMediaCache.ingest(it) }
    }
    val body = animalWriteJson(
        listing,
        post,
        localMedia,
        here ?: me.asPoint() ?: FallbackPoint,
    )
    val remote = if (listing.id.isUuid()) {
        api.update(body)
    } else {
        api.publish(body)
    }
    db.bindServerAnimal(post.id, remote, me.id)
    val live = db.posts.firstOrNull { it.id == remote.postId } ?: post
    val seenRemote = mutableSetOf<String>()
    db.mediaOf(live.id).forEachIndexed { index, row ->
        if (!seenRemote.add(row.url)) return@forEachIndexed
        val previous = localMedia.getOrNull(index)
        if (previous != null) {
            OgtMediaCache.alias(previous.url, row.url)
            previous.posterUrl?.let { old -> row.posterUrl?.let { OgtMediaCache.alias(old, it) } }
        }
    }
    return live
}

private fun LocalUser.asPoint(): GeoPoint? =
    if (latitude != 0.0 || longitude != 0.0) GeoPoint(latitude, longitude) else null

private fun animalWriteJson(
    listing: LocalAnimalListing,
    post: LocalSocialPost,
    media: List<LocalPostMedia>,
    here: GeoPoint?,
) = buildJsonObject {
    if (listing.id.isUuid()) put("listingId", JsonPrimitive(listing.id))
    put("kind", JsonPrimitive(listing.kind))
    put("species", JsonPrimitive(listing.species))
    put("size", JsonPrimitive(listing.size ?: "MEDIUM"))
    put("urgency", JsonPrimitive(listing.urgency))
    put("title", JsonPrimitive(listing.title))
    put("description", JsonPrimitive(listing.description.ifBlank { post.body }))
    put("petName", JsonPrimitive(listing.petName))
    put("ageLabel", JsonPrimitive(listing.ageLabel))
    put("sex", JsonPrimitive(listing.sex))
    put("temperament", JsonPrimitive(listing.temperament))
    put("vaccinated", JsonPrimitive(listing.vaccinated))
    put("sterilized", JsonPrimitive(listing.sterilized))
    put("homeNeeds", JsonPrimitive(listing.homeNeeds))
    put("marks", JsonPrimitive(listing.marks))
    put("lastSeenPlace", JsonPrimitive(listing.lastSeenPlace))
    put("place", JsonPrimitive(listing.place))
    put("radius", JsonPrimitive(listing.alertRadiusM))
    (listing.latitude ?: here?.latitude)?.let { put("latitude", JsonPrimitive(it)) }
    (listing.longitude ?: here?.longitude)?.let { put("longitude", JsonPrimitive(it)) }
    put("media", media.toJson())
}

private fun List<LocalPostMedia>.toJson(): JsonArray = buildJsonArray {
    forEach { item ->
        add(
            buildJsonObject {
                put("kind", JsonPrimitive(item.kind.name))
                put("url", JsonPrimitive(item.url))
                item.posterUrl?.let { put("posterUrl", JsonPrimitive(it)) }
                put("sortOrder", JsonPrimitive(item.sortOrder))
                item.altText?.let { put("altText", JsonPrimitive(it)) }
            },
        )
    }
}

private fun List<OgtPickedMedia>.toStored(postId: String, tag: String): List<LocalPostMedia> =
    mapIndexed { order, item ->
        LocalPostMedia(
            id = "$postId-m$order",
            postId = postId,
            kind = item.kind,
            url = if (item.fromDevice) "file://${item.path}" else "asset://${item.path}",
            posterUrl = item.posterPath?.let { "file://$it" },
            sortOrder = order,
            assetKey = if (item.fromDevice) "" else item.path,
            altText = if (tag == "LOST") "Mascota perdida" else "Adopción",
        )
    }
