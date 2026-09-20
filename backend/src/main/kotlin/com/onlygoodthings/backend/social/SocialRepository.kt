package com.onlygoodthings.backend.social

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.infra.stringOrNull
import com.onlygoodthings.shared.domain.AuthorKind
import com.onlygoodthings.shared.domain.FeedEventKind
import com.onlygoodthings.shared.domain.FeedMode
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.PostMediaItem
import com.onlygoodthings.shared.domain.SocialComment
import com.onlygoodthings.shared.domain.SocialLiveCounters
import com.onlygoodthings.shared.domain.SocialPost
import com.onlygoodthings.shared.feed.FeedCandidate
import com.onlygoodthings.shared.feed.FeedEventSignal
import com.onlygoodthings.shared.feed.FeedRanker
import com.onlygoodthings.shared.feed.ViewerContext
import java.sql.ResultSet
import java.util.UUID

class SocialSqlRepository(private val db: Database) {

    fun feed(
        userId: String,
        mode: FeedMode,
        pageSize: Int,
        cursor: String?,
        family: com.onlygoodthings.shared.domain.FeedTopicFamily? = null,
    ): List<SocialPost> {
        val offset = cursor?.toIntOrNull() ?: 0
        val viewer = loadViewer(userId)
        val candidates = loadCandidates()
        val ranked = FeedRanker.rank(candidates, viewer, mode, System.currentTimeMillis(), offset, pageSize, family)
        if (ranked.isEmpty()) return emptyList()
        val hydrated = hydratePosts(ranked.map { it.postId }, userId).associateBy { it.id }
        return ranked.mapNotNull { item ->
            hydrated[item.postId]?.copy(discovery = item.discovery)
        }
    }

    fun recordFeedEvent(viewerId: String, postId: String, kind: FeedEventKind, dwellMs: Int?) {
        db.withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO feed_events (viewer_id, post_id, kind, dwell_ms)
                VALUES (?::uuid, ?::uuid, ?::feed_event_kind, ?)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, viewerId)
                stmt.setString(2, postId)
                stmt.setString(3, kind.name)
                if (dwellMs == null) stmt.setNull(4, java.sql.Types.INTEGER) else stmt.setInt(4, dwellMs)
                stmt.executeUpdate()
            }
        }
    }

    fun clap(postId: String, userId: String): SocialPost? {
        db.withConnection { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    "INSERT INTO post_impacts (post_id, user_id) VALUES (?::uuid, ?::uuid) ON CONFLICT DO NOTHING",
                ).use { stmt ->
                    stmt.setString(1, postId)
                    stmt.setString(2, userId)
                    stmt.executeUpdate()
                }
                connection.prepareStatement(
                    """
                    UPDATE social_posts
                    SET impact_count = (SELECT COUNT(*) FROM post_impacts WHERE post_id = ?::uuid)
                    WHERE id = ?::uuid
                    """.trimIndent(),
                ).use { stmt ->
                    stmt.setString(1, postId)
                    stmt.setString(2, postId)
                    stmt.executeUpdate()
                }
                connection.commit()
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
        recordFeedEvent(userId, postId, FeedEventKind.CLAP, dwellMs = null)
        return byId(postId, userId)
    }

    fun comments(postId: String): List<SocialComment> = db.withConnection { connection ->
        val flat = connection.prepareStatement(
            """
            SELECT c.id, c.post_id, c.author_user_id, c.parent_comment_id, c.body,
                   EXTRACT(EPOCH FROM c.created_at) * 1000 AS created_ms,
                   u.display_name
            FROM post_comments c
            JOIN users u ON u.id = c.author_user_id
            WHERE c.post_id = ?::uuid AND c.moderation_status = 'VISIBLE'
            ORDER BY c.created_at
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, postId)
            stmt.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.toComment()) }
            }
        }
        nest(flat)
    }

    fun addComment(postId: String, userId: String, body: String, parentId: String?): SocialComment =
        db.withConnection { connection ->
            val id = UUID.randomUUID().toString()
            connection.prepareStatement(
                """
                INSERT INTO post_comments (id, post_id, author_user_id, parent_comment_id, body)
                VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, postId)
                stmt.setString(3, userId)
                if (parentId == null) stmt.setNull(4, java.sql.Types.OTHER) else stmt.setString(4, parentId)
                stmt.setString(5, body)
                stmt.executeUpdate()
            }
            connection.prepareStatement(
                "UPDATE social_posts SET comment_count = comment_count + 1 WHERE id = ?::uuid",
            ).use { stmt ->
                stmt.setString(1, postId)
                stmt.executeUpdate()
            }
            SocialComment(id, postId, userId, "yo", parentId, body, System.currentTimeMillis())
        }.also {
            recordFeedEvent(userId, postId, FeedEventKind.COMMENT, dwellMs = null)
        }

    fun countersOf(postId: String): SocialLiveCounters? = db.withConnection { connection ->
        connection.prepareStatement(
            "SELECT id, comment_count, impact_count FROM social_posts WHERE id = ?::uuid",
        ).use { stmt ->
            stmt.setString(1, postId)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return@withConnection null
                SocialLiveCounters(
                    id = rs.getString("id"),
                    commentCount = rs.getInt("comment_count"),
                    impactCount = rs.getInt("impact_count"),
                )
            }
        }
    }

    fun report(reporterId: String, postId: String, reason: String, details: String?) {
        db.withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO content_reports (reporter_id, post_id, reason, details)
                VALUES (?::uuid, ?::uuid, ?, ?)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, reporterId)
                stmt.setString(2, postId)
                stmt.setString(3, reason)
                stmt.setString(4, details)
                stmt.executeUpdate()
            }
        }
    }

    private fun byId(postId: String, viewerId: String): SocialPost? =
        hydratePosts(listOf(postId), viewerId).firstOrNull()

    private fun loadViewer(userId: String): ViewerContext = db.withConnection { connection ->
        var lat: Double? = null
        var lng: Double? = null
        connection.prepareStatement(
            "SELECT ST_Y(home_location) AS lat, ST_X(home_location) AS lng FROM users WHERE id = ?::uuid",
        ).use { stmt ->
            stmt.setString(1, userId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    lat = rs.optDouble("lat")
                    lng = rs.optDouble("lng")
                }
            }
        }
        val followed = connection.prepareStatement(
            "SELECT followed_id FROM follows WHERE follower_id = ?::uuid",
        ).use { stmt ->
            stmt.setString(1, userId)
            stmt.executeQuery().use { rs ->
                buildSet { while (rs.next()) add(rs.getString(1)) }
            }
        }
        val events = connection.prepareStatement(
            """
            SELECT e.post_id, e.kind, p.author_user_id, COALESCE(p.topic, '') AS topic
            FROM feed_events e
            JOIN social_posts p ON p.id = e.post_id
            WHERE e.viewer_id = ?::uuid AND e.created_at > now() - INTERVAL '90 days'
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, userId)
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            FeedEventSignal(
                                postId = rs.getString("post_id"),
                                authorUserId = rs.getString("author_user_id"),
                                topic = rs.getString("topic"),
                                kind = FeedEventKind.valueOf(rs.getString("kind")),
                            ),
                        )
                    }
                }
            }
        }
        ViewerContext(userId, followed, events, lat, lng)
    }

    private fun loadCandidates(): List<FeedCandidate> = db.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT p.id, p.author_user_id, p.author_company_id, COALESCE(p.topic, '') AS topic,
                   p.impact_count, p.comment_count, p.source_url,
                   EXTRACT(EPOCH FROM p.created_at) * 1000 AS created_ms,
                   ST_Y(p.location) AS lat, ST_X(p.location) AS lng,
                   (
                       SELECT pp.user_id::text FROM post_people pp
                       WHERE pp.post_id = p.id AND pp.role = 'PROTAGONIST' LIMIT 1
                   ) AS protagonist_id
            FROM social_posts p
            WHERE p.moderation_status = 'VISIBLE' AND p.is_story = FALSE
            ORDER BY p.created_at DESC
            LIMIT 200
            """.trimIndent(),
        ).use { stmt ->
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val authorUser = rs.getString("author_user_id")
                        val companyId = rs.getString("author_company_id")
                        add(
                            FeedCandidate(
                                postId = rs.getString("id"),
                                authorKey = authorUser ?: "company:${companyId.orEmpty()}",
                                authorUserId = authorUser,
                                protagonistUserId = rs.getString("protagonist_id") ?: authorUser,
                                topic = rs.getString("topic"),
                                createdAtEpochMs = rs.getLong("created_ms"),
                                impactCount = rs.getInt("impact_count"),
                                commentCount = rs.getInt("comment_count"),
                                latitude = rs.optDouble("lat"),
                                longitude = rs.optDouble("lng"),
                                sourceUrl = rs.stringOrNull("source_url"),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun hydratePosts(ids: List<String>, viewerId: String): List<SocialPost> {
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?::uuid" }
        return db.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT p.id, p.author_kind, p.author_user_id, p.author_company_id, p.body, p.media_urls,
                       p.impact_count, p.comment_count, p.is_story, COALESCE(p.topic, '') AS topic,
                       p.source_url,
                       EXTRACT(EPOCH FROM p.created_at) * 1000 AS created_ms,
                       COALESCE(u.display_name, c.trade_name, c.legal_name) AS author_name,
                       COALESCE(u.photo_url, c.logo_url) AS author_photo,
                       EXISTS (
                           SELECT 1 FROM post_impacts i
                           WHERE i.post_id = p.id AND i.user_id = ?::uuid
                       ) AS viewer_hit,
                       (
                           SELECT pp.user_id::text FROM post_people pp
                           WHERE pp.post_id = p.id AND pp.role = 'PROTAGONIST' LIMIT 1
                       ) AS protagonist_id
                FROM social_posts p
                LEFT JOIN users u ON u.id = p.author_user_id
                LEFT JOIN companies c ON c.id = p.author_company_id
                WHERE p.id IN ($placeholders)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, viewerId)
                ids.forEachIndexed { index, id -> stmt.setString(index + 2, id) }
                val posts = stmt.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(rs.toPost()) }
                }
                val media = loadMedia(connection, ids)
                posts.map { post ->
                    val items = media[post.id].orEmpty()
                    post.copy(
                        media = items,
                        mediaUrls = items.map { it.url }.ifEmpty { post.mediaUrls },
                    )
                }
            }
        }
    }

    private fun loadMedia(connection: java.sql.Connection, ids: List<String>): Map<String, List<PostMediaItem>> {
        if (ids.isEmpty()) return emptyMap()
        val placeholders = ids.joinToString(",") { "?::uuid" }
        return connection.prepareStatement(
            """
            SELECT id, post_id, kind, url, poster_url, sort_order, duration_ms, alt_text
            FROM post_media
            WHERE post_id IN ($placeholders)
            ORDER BY post_id, sort_order
            """.trimIndent(),
        ).use { stmt ->
            ids.forEachIndexed { index, id -> stmt.setString(index + 1, id) }
            stmt.executeQuery().use { rs ->
                buildMap<String, MutableList<PostMediaItem>> {
                    while (rs.next()) {
                        val postId = rs.getString("post_id")
                        val item = PostMediaItem(
                            id = rs.getString("id"),
                            kind = MediaKind.valueOf(rs.getString("kind")),
                            url = rs.getString("url"),
                            posterUrl = rs.stringOrNull("poster_url"),
                            sortOrder = rs.getInt("sort_order"),
                            durationMs = (rs.getObject("duration_ms") as? Number)?.toInt(),
                            altText = rs.stringOrNull("alt_text"),
                        )
                        getOrPut(postId) { mutableListOf() } += item
                    }
                }
            }
        }
    }

    private fun nest(flat: List<SocialComment>): List<SocialComment> {
        val byParent = flat.groupBy { it.parentCommentId }
        fun kids(parentId: String?): List<SocialComment> =
            (byParent[parentId] ?: emptyList()).map { it.copy(replies = kids(it.id)) }
        return kids(null)
    }

    private fun ResultSet.toPost(): SocialPost {
        val kind = AuthorKind.valueOf(getString("author_kind"))
        val authorId = when (kind) {
            AuthorKind.USER -> getString("author_user_id")
            AuthorKind.COMPANY -> getString("author_company_id")
        }
        val media = (getArray("media_urls")?.array as? Array<*>)?.mapNotNull { it as? String } ?: emptyList()
        return SocialPost(
            id = getString("id"),
            authorKind = kind,
            authorId = authorId,
            authorName = getString("author_name") ?: "Comunidad",
            authorPhotoUrl = stringOrNull("author_photo"),
            body = getString("body"),
            mediaUrls = media,
            impactCount = getInt("impact_count"),
            commentCount = getInt("comment_count"),
            isStory = getBoolean("is_story"),
            createdAtEpochMs = getLong("created_ms"),
            viewerHasImpacted = runCatching { getBoolean("viewer_hit") }.getOrDefault(false),
            topic = runCatching { getString("topic") }.getOrNull().orEmpty(),
            protagonistUserId = runCatching { getString("protagonist_id") }.getOrNull(),
            sourceUrl = runCatching { stringOrNull("source_url") }.getOrNull(),
        )
    }

    private fun ResultSet.optDouble(column: String): Double? =
        (getObject(column) as? Number)?.toDouble()

    private fun ResultSet.toComment(): SocialComment = SocialComment(
        id = getString("id"),
        postId = getString("post_id"),
        authorUserId = getString("author_user_id"),
        authorName = getString("display_name"),
        parentCommentId = stringOrNull("parent_comment_id"),
        body = getString("body"),
        createdAtEpochMs = getLong("created_ms"),
    )
}
