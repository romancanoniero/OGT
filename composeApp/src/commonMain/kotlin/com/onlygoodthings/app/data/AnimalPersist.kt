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
import com.onlygoodthings.shared.domain.PostMediaItem
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
    isCurrentOwner: () -> Boolean,
    upload: suspend (List<OgtPickedMedia>, String) -> List<PostMediaItem>,
): LocalSocialPost = persistAnimalOnServer(db, api, me, post, emptyList(), me.asPoint(), isCurrentOwner, upload)

/** Graba o actualiza la ficha en Postgres y alinea los IDs locales con los del servidor. */
suspend fun persistAnimalOnServer(
    db: OgtLocalDatabase,
    api: RestAnimalsRepository,
    me: LocalUser,
    post: LocalSocialPost,
    draft: List<OgtPickedMedia>,
    here: GeoPoint?,
    isCurrentOwner: () -> Boolean,
    upload: suspend (List<OgtPickedMedia>, String) -> List<PostMediaItem>,
): LocalSocialPost {
    check(isCurrentOwner()) { "La sesión cambió. Volvé a intentarlo." }
    val listing = post.listingId?.let { id -> db.animals.firstOrNull { it.id == id } } ?: return post
    val localMedia = db.mediaOf(post.id).ifEmpty { draft.toStored(post.id, listing.kind) }
    localMedia.forEach { row ->
        OgtMediaCache.ingest(row.url)
        row.posterUrl?.let { OgtMediaCache.ingest(it) }
    }
    val uploadedMedia = uploadAnimalMedia(localMedia) { items, alt ->
        check(isCurrentOwner()) { "La sesión cambió. Volvé a intentarlo." }
        upload(items, alt)
    }
    check(isCurrentOwner()) { "La sesión cambió. Volvé a intentarlo." }
    val body = animalWriteJson(
        listing,
        post,
        uploadedMedia,
        here ?: me.asPoint() ?: FallbackPoint,
    )
    val remote = if (listing.id.isUuid()) {
        api.update(body)
    } else {
        api.publish(body)
    }
    check(isCurrentOwner()) { "La sesión cambió. Volvé a intentarlo." }
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

/** Upload device files before publishing; retain existing remote photos when editing. */
internal suspend fun uploadAnimalMedia(
    media: List<LocalPostMedia>,
    upload: suspend (List<OgtPickedMedia>, String) -> List<PostMediaItem>,
): List<LocalPostMedia> = media.map { row ->
    if (row.url.startsWith("https://") || row.url.startsWith("http://")) return@map row
    require(row.url.startsWith("file://") || row.url.startsWith("content://") || row.url.startsWith("/")) {
        "Elegí la foto de la mascota desde la galería."
    }
    val item = OgtPickedMedia(
        id = row.id, kind = row.kind, path = row.url.removePrefix("file://"), fromDevice = true,
        posterPath = row.posterUrl?.removePrefix("file://"), preparedForUpload = true,
    )
    val saved = upload(listOf(item), row.altText.orEmpty()).single()
    require(saved.url.startsWith("https://") || saved.url.startsWith("http://")) { "No se pudo subir la foto." }
    row.copy(url = saved.url, posterUrl = saved.posterUrl, assetKey = "")
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
    listing.adoptionDetails?.let { put("adoptionDetails", com.onlygoodthings.shared.protocol.WsCodec.json.encodeToJsonElement(com.onlygoodthings.shared.domain.AdoptionDetails.serializer(), it)) }
    put("attributionUrl", JsonPrimitive(post.attributionUrl.orEmpty()))
    put("radius", JsonPrimitive(listing.alertRadiusM))
    (listing.latitude ?: here?.latitude)?.let { put("latitude", JsonPrimitive(it)) }
    (listing.longitude ?: here?.longitude)?.let { put("longitude", JsonPrimitive(it)) }
    put("media", media.toJson())
}

private fun List<LocalPostMedia>.toJson(): JsonArray = buildJsonArray {
    forEach { item ->
        add(
            buildJsonObject {
                put("id", JsonPrimitive(item.id))
                put("kind", JsonPrimitive(item.kind.name))
                put("url", JsonPrimitive(item.url))
                item.posterUrl?.let { put("posterUrl", JsonPrimitive(it)) }
                put("sortOrder", JsonPrimitive(item.sortOrder))
                item.altText?.let { put("altText", JsonPrimitive(it)) }
                item.durationMs?.let { put("durationMs", JsonPrimitive(it)) }
                item.frameX?.let { put("frameX", JsonPrimitive(it)) }
                item.frameY?.let { put("frameY", JsonPrimitive(it)) }
                item.frameZoom?.let { put("frameZoom", JsonPrimitive(it)) }
            },
        )
    }
}

private fun List<OgtPickedMedia>.toStored(postId: String, tag: String): List<LocalPostMedia> =
    mapIndexed { order, item ->
        val remote = !item.fromDevice && (item.path.startsWith("https://") || item.path.startsWith("http://"))
        LocalPostMedia(
            id = "$postId-m$order",
            postId = postId,
            kind = item.kind,
            url = if (remote) item.path else if (item.fromDevice) "file://${item.path}" else "asset://${item.path}",
            posterUrl = item.posterPath?.let { if (remote) it else "file://$it" },
            sortOrder = order,
            assetKey = if (item.fromDevice || remote) "" else item.path,
            altText = if (tag == "LOST") "Mascota perdida" else "Adopción",
        )
    }
