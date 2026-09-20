package com.onlygoodthings.backend.modules

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.infra.doubleOrNull
import com.onlygoodthings.backend.infra.stringOrNull
import com.onlygoodthings.shared.domain.AnimalListingDto
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.PostMediaItem
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

data class AnimalWrite(
    val kind: String,
    val species: String,
    val size: String,
    val urgency: String,
    val title: String,
    val description: String,
    val petName: String,
    val ageLabel: String,
    val sex: String,
    val temperament: String,
    val vaccinated: Boolean,
    val sterilized: Boolean,
    val homeNeeds: String,
    val marks: String,
    val lastSeenPlace: String,
    val place: String,
    val latitude: Double,
    val longitude: Double,
    val alertRadiusM: Int,
    val media: List<PostMediaItem>,
)

class AnimalSqlRepository(private val db: Database) {

    fun publish(reporterId: String, write: AnimalWrite): AnimalListingDto = db.withConnection { connection ->
        connection.autoCommit = false
        try {
            val listingId = UUID.randomUUID().toString()
            val postId = UUID.randomUUID().toString()
            insertListing(connection, listingId, reporterId, write)
            insertPost(connection, postId, listingId, reporterId, write)
            replaceMedia(connection, postId, write.media, write.title)
            connection.commit()
            loadByListing(connection, listingId) ?: error("No se pudo leer la ficha publicada")
        } catch (error: Exception) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = true
        }
    }

    fun update(reporterId: String, listingId: String, write: AnimalWrite): AnimalListingDto =
        db.withConnection { connection ->
            connection.autoCommit = false
            try {
                val owner = connection.prepareStatement(
                    "SELECT reporter_user_id::text FROM animal_listings WHERE id = ?::uuid",
                ).use { stmt ->
                    stmt.setString(1, listingId)
                    stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
                } ?: error("Ficha inexistente")
                if (owner != reporterId) error("Solo el autor puede editar esta ficha")
                connection.prepareStatement(
                    """
                    UPDATE animal_listings SET
                        species = ?::animal_species,
                        size = ?::animal_size,
                        urgency = ?::urgency_level,
                        title = ?,
                        description = ?,
                        photo_urls = ?,
                        location = ST_SetSRID(ST_MakePoint(?, ?), 4326),
                        alert_radius_m = ?,
                        pet_name = ?,
                        age_label = ?,
                        sex = ?,
                        temperament = ?,
                        vaccinated = ?,
                        sterilized = ?,
                        home_needs = ?,
                        marks = ?,
                        last_seen_place = ?,
                        place_label = ?
                    WHERE id = ?::uuid
                    """.trimIndent(),
                ).use { stmt ->
                    bindListingBody(stmt, write, start = 1)
                    stmt.setString(20, listingId)
                    stmt.executeUpdate()
                }
                val postId = connection.prepareStatement(
                    "SELECT id::text FROM social_posts WHERE listing_id = ?::uuid ORDER BY created_at DESC LIMIT 1",
                ).use { stmt ->
                    stmt.setString(1, listingId)
                    stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
                } ?: run {
                    val created = UUID.randomUUID().toString()
                    insertPost(connection, created, listingId, reporterId, write)
                    created
                }
                connection.prepareStatement(
                    """
                    UPDATE social_posts
                    SET body = ?, topic = ?, location = ST_SetSRID(ST_MakePoint(?, ?), 4326), media_urls = ?
                    WHERE id = ?::uuid
                    """.trimIndent(),
                ).use { stmt ->
                    stmt.setString(1, write.description)
                    stmt.setString(2, topicOf(write.kind))
                    stmt.setDouble(3, write.longitude)
                    stmt.setDouble(4, write.latitude)
                    stmt.setArray(5, connection.createArrayOf("text", write.media.map { it.url }.toTypedArray()))
                    stmt.setString(6, postId)
                    stmt.executeUpdate()
                }
                replaceMedia(connection, postId, write.media, write.title)
                connection.commit()
                loadByListing(connection, listingId) ?: error("No se pudo leer la ficha editada")
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }

    fun open(): List<AnimalListingDto> = db.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT a.id AS listing_id, p.id AS post_id,
                   a.reporter_user_id::text AS reporter_id, u.firebase_uid,
                   a.kind::text, a.species::text, COALESCE(a.size::text, 'MEDIUM') AS size,
                   a.urgency::text, a.title, a.description, a.place_label,
                   a.alert_radius_m, a.resolved, a.pet_name, a.age_label, a.sex,
                   a.temperament, a.vaccinated, a.sterilized, a.home_needs,
                   a.marks, a.last_seen_place,
                   ST_Y(a.location) AS lat, ST_X(a.location) AS lng,
                   EXTRACT(EPOCH FROM a.created_at) * 1000 AS created_ms
            FROM animal_listings a
            JOIN users u ON u.id = a.reporter_user_id
            LEFT JOIN social_posts p ON p.listing_id = a.id
            WHERE a.resolved = FALSE
            ORDER BY a.created_at DESC
            LIMIT 80
            """.trimIndent(),
        ).use { stmt ->
            stmt.executeQuery().use { rs ->
                val rows = buildList { while (rs.next()) add(rs.toDto()) }
                val media = loadMedia(connection, rows.map { it.postId }.filter { it.isNotBlank() })
                rows.map { it.copy(media = media[it.postId].orEmpty()) }
            }
        }
    }

    fun homeLocation(userId: String): Pair<Double, Double>? = db.withConnection { connection ->
        connection.prepareStatement(
            "SELECT ST_Y(home_location) AS lat, ST_X(home_location) AS lng FROM users WHERE id = ?::uuid",
        ).use { stmt ->
            stmt.setString(1, userId)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) null
                else {
                    val lat = rs.doubleOrNull("lat")
                    val lng = rs.doubleOrNull("lng")
                    if (lat != null && lng != null) lat to lng else null
                }
            }
        }
    }

    private fun insertListing(connection: Connection, listingId: String, reporterId: String, write: AnimalWrite) {
        connection.prepareStatement(
            """
            INSERT INTO animal_listings (
                id, reporter_user_id, kind, species, size, urgency, title, description,
                photo_urls, location, alert_radius_m, pet_name, age_label, sex, temperament,
                vaccinated, sterilized, home_needs, marks, last_seen_place, place_label
            ) VALUES (
                ?::uuid, ?::uuid, ?::animal_listing_kind, ?::animal_species, ?::animal_size,
                ?::urgency_level, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, listingId)
            stmt.setString(2, reporterId)
            stmt.setString(3, write.kind)
            bindListingBody(stmt, write, start = 4)
            stmt.executeUpdate()
        }
    }

    private fun insertPost(
        connection: Connection,
        postId: String,
        listingId: String,
        reporterId: String,
        write: AnimalWrite,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO social_posts (
                id, author_kind, author_user_id, body, media_urls, location, topic, listing_id
            ) VALUES (
                ?::uuid, 'USER', ?::uuid, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?::uuid
            )
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, postId)
            stmt.setString(2, reporterId)
            stmt.setString(3, write.description)
            stmt.setArray(4, connection.createArrayOf("text", write.media.map { it.url }.toTypedArray()))
            stmt.setDouble(5, write.longitude)
            stmt.setDouble(6, write.latitude)
            stmt.setString(7, topicOf(write.kind))
            stmt.setString(8, listingId)
            stmt.executeUpdate()
        }
        connection.prepareStatement(
            "INSERT INTO post_people (post_id, user_id, role) VALUES (?::uuid, ?::uuid, 'AUTHOR')",
        ).use { stmt ->
            stmt.setString(1, postId)
            stmt.setString(2, reporterId)
            stmt.executeUpdate()
        }
    }

    private fun replaceMedia(connection: Connection, postId: String, media: List<PostMediaItem>, alt: String) {
        connection.prepareStatement("DELETE FROM post_media WHERE post_id = ?::uuid").use { stmt ->
            stmt.setString(1, postId)
            stmt.executeUpdate()
        }
        media.take(10).forEachIndexed { order, item ->
            connection.prepareStatement(
                """
                INSERT INTO post_media (id, post_id, kind, url, poster_url, sort_order, alt_text)
                VALUES (?::uuid, ?::uuid, ?::media_kind, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, UUID.randomUUID().toString())
                stmt.setString(2, postId)
                stmt.setString(3, item.kind.name)
                stmt.setString(4, item.url)
                stmt.setString(5, item.posterUrl)
                stmt.setInt(6, order)
                stmt.setString(7, item.altText ?: alt)
                stmt.executeUpdate()
            }
        }
    }

    private fun bindListingBody(stmt: java.sql.PreparedStatement, write: AnimalWrite, start: Int) {
        var i = start
        stmt.setString(i++, write.species)
        stmt.setString(i++, write.size)
        stmt.setString(i++, write.urgency)
        stmt.setString(i++, write.title)
        stmt.setString(i++, write.description)
        stmt.setArray(i++, stmt.connection.createArrayOf("text", write.media.map { it.url }.toTypedArray()))
        stmt.setDouble(i++, write.longitude)
        stmt.setDouble(i++, write.latitude)
        stmt.setInt(i++, write.alertRadiusM)
        stmt.setString(i++, write.petName)
        stmt.setString(i++, write.ageLabel)
        stmt.setString(i++, write.sex)
        stmt.setString(i++, write.temperament)
        stmt.setBoolean(i++, write.vaccinated)
        stmt.setBoolean(i++, write.sterilized)
        stmt.setString(i++, write.homeNeeds)
        stmt.setString(i++, write.marks)
        stmt.setString(i++, write.lastSeenPlace)
        stmt.setString(i, write.place)
    }

    private fun loadByListing(connection: Connection, listingId: String): AnimalListingDto? {
        return connection.prepareStatement(
            """
            SELECT a.id AS listing_id, p.id AS post_id,
                   a.reporter_user_id::text AS reporter_id, u.firebase_uid,
                   a.kind::text, a.species::text, COALESCE(a.size::text, 'MEDIUM') AS size,
                   a.urgency::text, a.title, a.description, a.place_label,
                   a.alert_radius_m, a.resolved, a.pet_name, a.age_label, a.sex,
                   a.temperament, a.vaccinated, a.sterilized, a.home_needs,
                   a.marks, a.last_seen_place,
                   ST_Y(a.location) AS lat, ST_X(a.location) AS lng,
                   EXTRACT(EPOCH FROM a.created_at) * 1000 AS created_ms
            FROM animal_listings a
            JOIN users u ON u.id = a.reporter_user_id
            LEFT JOIN social_posts p ON p.listing_id = a.id
            WHERE a.id = ?::uuid
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, listingId)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return null
                val row = rs.toDto()
                val media = loadMedia(connection, listOfNotNull(row.postId.takeIf { it.isNotBlank() }))
                row.copy(media = media[row.postId].orEmpty())
            }
        }
    }

    private fun loadMedia(connection: Connection, postIds: List<String>): Map<String, List<PostMediaItem>> {
        if (postIds.isEmpty()) return emptyMap()
        val placeholders = postIds.joinToString(",") { "?::uuid" }
        return connection.prepareStatement(
            """
            SELECT id, post_id, kind, url, poster_url, sort_order, duration_ms, alt_text
            FROM post_media
            WHERE post_id IN ($placeholders)
            ORDER BY post_id, sort_order
            """.trimIndent(),
        ).use { stmt ->
            postIds.forEachIndexed { index, id -> stmt.setString(index + 1, id) }
            stmt.executeQuery().use { rs ->
                buildMap<String, MutableList<PostMediaItem>> {
                    while (rs.next()) {
                        val postId = rs.getString("post_id")
                        getOrPut(postId) { mutableListOf() } += PostMediaItem(
                            id = rs.getString("id"),
                            kind = MediaKind.valueOf(rs.getString("kind")),
                            url = rs.getString("url"),
                            posterUrl = rs.stringOrNull("poster_url"),
                            sortOrder = rs.getInt("sort_order"),
                            durationMs = (rs.getObject("duration_ms") as? Number)?.toInt(),
                            altText = rs.stringOrNull("alt_text"),
                        )
                    }
                }
            }
        }
    }

    private fun ResultSet.toDto(): AnimalListingDto = AnimalListingDto(
        listingId = getString("listing_id"),
        postId = getString("post_id").orEmpty(),
        reporterUserId = getString("reporter_id"),
        reporterFirebaseUid = getString("firebase_uid"),
        kind = getString("kind"),
        species = getString("species"),
        size = getString("size"),
        urgency = getString("urgency"),
        title = getString("title"),
        description = getString("description"),
        place = getString("place_label").orEmpty(),
        alertRadiusM = getInt("alert_radius_m"),
        resolved = getBoolean("resolved"),
        petName = getString("pet_name").orEmpty(),
        ageLabel = getString("age_label").orEmpty(),
        sex = getString("sex").orEmpty(),
        temperament = getString("temperament").orEmpty(),
        vaccinated = getBoolean("vaccinated"),
        sterilized = getBoolean("sterilized"),
        homeNeeds = getString("home_needs").orEmpty(),
        marks = getString("marks").orEmpty(),
        lastSeenPlace = getString("last_seen_place").orEmpty(),
        latitude = doubleOrNull("lat"),
        longitude = doubleOrNull("lng"),
        createdAtEpochMs = getLong("created_ms"),
    )

    private fun topicOf(kind: String): String = if (kind == "LOST") "Mascota perdida" else "Adopción"
}
