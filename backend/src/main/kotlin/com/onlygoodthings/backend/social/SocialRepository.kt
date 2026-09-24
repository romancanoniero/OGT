package com.onlygoodthings.backend.social

import com.onlygoodthings.backend.http.optBoolean
import com.onlygoodthings.backend.http.optString
import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.infra.stringOrNull
import com.onlygoodthings.shared.domain.AuthorKind
import com.onlygoodthings.shared.domain.FeedEventKind
import com.onlygoodthings.shared.domain.FeedMode
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.PostMediaItem
import com.onlygoodthings.shared.domain.PostPlacement
import com.onlygoodthings.shared.domain.SocialAnecdote
import com.onlygoodthings.shared.domain.SocialComment
import com.onlygoodthings.shared.domain.SocialLiveCounters
import com.onlygoodthings.shared.domain.SocialPost
import com.onlygoodthings.shared.domain.MoneyLedgerKind
import com.onlygoodthings.shared.domain.isAnecdoteShare
import com.onlygoodthings.shared.domain.postDeepLink
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
        val candidates = (loadCandidates() + loadMatchedFriendNeeds(viewer)).distinctBy { it.postId }
        val ranked = FeedRanker.rank(candidates, viewer, mode, System.currentTimeMillis(), offset, pageSize, family)
        if (ranked.isEmpty()) return emptyList()
        val hydrated = hydratePosts(ranked.map { it.postId }, userId).associateBy { it.id }
        return ranked.mapNotNull { item ->
            hydrated[item.postId]?.copy(discovery = item.discovery)
        }.filter { post ->
            !isAnecdoteShare(post.topic, post.sourceUrl, post.id) || post.authorId != userId
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

    fun publish(
        userId: String,
        body: String,
        topic: String,
        media: List<PostMediaItem>,
        latitude: Double?,
        longitude: Double?,
        protagonistUserId: String?,
        participantUserIds: List<String>,
        honoreeName: String? = null,
        sourceUrl: String? = null,
    ): SocialPost {
        require(body.isNotBlank()) { "El texto no puede estar vacío" }
        require(media.isNotEmpty()) { "Hace falta al menos una foto o un video" }
        val postId = UUID.randomUUID().toString()
        db.withConnection { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    """
                    INSERT INTO social_posts (
                        id, author_kind, author_user_id, body, media_urls, location, topic, placement, honoree_name, source_url
                    ) VALUES (
                        ?::uuid, 'USER', ?::uuid, ?, ?,
                        CASE WHEN ?::float8 IS NULL THEN NULL
                             ELSE ST_SetSRID(ST_MakePoint(?::float8, ?::float8), 4326) END,
                        ?, 'ORGANIC', ?, ?
                    )
                    """.trimIndent(),
                ).use { stmt ->
                    stmt.setString(1, postId)
                    stmt.setString(2, userId)
                    stmt.setString(3, body)
                    stmt.setArray(4, connection.createArrayOf("text", media.map { it.url }.toTypedArray()))
                    if (latitude == null || longitude == null) {
                        stmt.setNull(5, java.sql.Types.DOUBLE)
                        stmt.setNull(6, java.sql.Types.DOUBLE)
                        stmt.setNull(7, java.sql.Types.DOUBLE)
                    } else {
                        stmt.setDouble(5, longitude)
                        stmt.setDouble(6, longitude)
                        stmt.setDouble(7, latitude)
                    }
                    stmt.setString(8, topic)
                    if (honoreeName.isNullOrBlank()) {
                        stmt.setNull(9, java.sql.Types.VARCHAR)
                    } else {
                        stmt.setString(9, honoreeName.trim())
                    }
                    if (sourceUrl.isNullOrBlank()) {
                        stmt.setNull(10, java.sql.Types.VARCHAR)
                    } else {
                        stmt.setString(10, sourceUrl.trim())
                    }
                    stmt.executeUpdate()
                }
                connection.prepareStatement(
                    "INSERT INTO post_people (post_id, user_id, role) VALUES (?::uuid, ?::uuid, 'AUTHOR')",
                ).use { stmt ->
                    stmt.setString(1, postId)
                    stmt.setString(2, userId)
                    stmt.executeUpdate()
                }
                val hero = protagonistUserId?.takeIf { it != userId }
                if (!hero.isNullOrBlank()) {
                    connection.prepareStatement(
                        "INSERT INTO post_people (post_id, user_id, role) VALUES (?::uuid, ?::uuid, 'PROTAGONIST') ON CONFLICT DO NOTHING",
                    ).use { stmt ->
                        stmt.setString(1, postId)
                        stmt.setString(2, hero)
                        stmt.executeUpdate()
                    }
                }
                participantUserIds.distinct().filter { it != userId && it != hero }.forEach { participant ->
                    connection.prepareStatement(
                        "INSERT INTO post_people (post_id, user_id, role) VALUES (?::uuid, ?::uuid, 'PARTICIPANT') ON CONFLICT DO NOTHING",
                    ).use { stmt ->
                        stmt.setString(1, postId)
                        stmt.setString(2, participant)
                        stmt.executeUpdate()
                    }
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
                        stmt.setString(7, item.altText ?: topic)
                        stmt.executeUpdate()
                    }
                }
                connection.commit()
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
        return byId(postId, userId) ?: error("No se pudo leer el post publicado")
    }

    fun follow(followerId: String, followedId: String) {
        require(followerId != followedId) { "No podés seguirte a vos" }
        db.withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO follows (follower_id, followed_id)
                VALUES (?::uuid, ?::uuid)
                ON CONFLICT DO NOTHING
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, followerId)
                stmt.setString(2, followedId)
                stmt.executeUpdate()
            }
        }
    }

    fun unfollow(followerId: String, followedId: String) {
        db.withConnection { connection ->
            connection.prepareStatement(
                "DELETE FROM follows WHERE follower_id = ?::uuid AND followed_id = ?::uuid",
            ).use { stmt ->
                stmt.setString(1, followerId)
                stmt.setString(2, followedId)
                stmt.executeUpdate()
            }
        }
    }

    fun followingIds(userId: String): List<String> = db.withConnection { connection ->
        connection.prepareStatement("SELECT followed_id FROM follows WHERE follower_id = ?::uuid").use { stmt ->
            stmt.setString(1, userId)
            stmt.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }
    }

    fun postsByAuthor(viewerId: String, authorId: String, pageSize: Int, cursor: String?): List<SocialPost> {
        val offset = cursor?.toIntOrNull() ?: 0
        val limit = pageSize.coerceIn(1, 40)
        val ids = db.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT id::text FROM social_posts
                WHERE author_user_id = ?::uuid AND moderation_status = 'VISIBLE'
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, authorId)
                stmt.setInt(2, limit)
                stmt.setInt(3, offset)
                stmt.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(rs.getString(1)) }
                }
            }
        }
        return hydratePosts(ids, viewerId)
    }

    fun search(viewerId: String, query: String, pageSize: Int): com.onlygoodthings.shared.domain.SearchPage {
        val q = query.trim()
        if (q.isBlank()) {
            return com.onlygoodthings.shared.domain.SearchPage(posts = feed(viewerId, FeedMode.HOME, pageSize, null))
        }
        val like = "%$q%"
        val limit = pageSize.coerceIn(1, 40)
        return db.withConnection { connection ->
            val postIds = connection.prepareStatement(
                """
                SELECT p.id::text
                FROM social_posts p
                LEFT JOIN users u ON u.id = p.author_user_id
                WHERE p.moderation_status = 'VISIBLE'
                  AND (
                    p.body ILIKE ?
                    OR COALESCE(p.topic, '') ILIKE ?
                    OR COALESCE(p.honoree_name, '') ILIKE ?
                    OR COALESCE(u.display_name, '') ILIKE ?
                  )
                ORDER BY p.created_at DESC
                LIMIT ?
                """.trimIndent(),
            ).use { stmt ->
                repeat(4) { stmt.setString(it + 1, like) }
                stmt.setInt(5, limit)
                stmt.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(rs.getString(1)) }
                }
            }
            val people = connection.prepareStatement(
                """
                SELECT id::text, display_name, photo_url, community_points, role::text
                FROM users
                WHERE status = 'ACTIVE' AND display_name ILIKE ?
                ORDER BY community_points DESC
                LIMIT 12
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, like)
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            val pts = rs.getInt("community_points")
                            add(
                                com.onlygoodthings.shared.domain.NeighborCard(
                                    userId = rs.getString("id"),
                                    displayName = rs.getString("display_name"),
                                    photoUrl = rs.stringOrNull("photo_url"),
                                    communityPoints = pts,
                                    role = rs.getString("role"),
                                    levelLabel = com.onlygoodthings.shared.domain.communityLevelLabel(pts),
                                ),
                            )
                        }
                    }
                }
            }
            com.onlygoodthings.shared.domain.SearchPage(
                posts = hydratePosts(postIds, viewerId),
                people = people,
            )
        }
    }

    fun updateOwnProfile(userId: String, data: Map<String, Any?>) {
        val name = data.optString("displayName")?.trim().orEmpty()
        val photo = data.optString("photoUrl")?.trim().orEmpty()
        val settings = mapOf(
            "language" to (data.optString("language") ?: "es"),
            "barrio" to (data.optString("barrio") ?: ""),
            "publicProfileVisible" to data.optBoolean("publicProfileVisible", true),
            "showExactMatchLocation" to data.optBoolean("showExactMatchLocation", false),
            "animalAlertPush" to data.optBoolean("animalAlertPush", true),
            "skillAlertPush" to data.optBoolean("skillAlertPush", true),
            "parkingRadarSounds" to data.optBoolean("parkingRadarSounds", true),
            "radarEnabled" to data.optBoolean("radarEnabled", true),
            "carbonSaveMode" to data.optBoolean("carbonSaveMode", false),
        )
        val payload = com.onlygoodthings.backend.http.JsonBody.objectMapper.writeValueAsString(
            mapOf("settings" to settings),
        )
        db.withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE users
                SET display_name = COALESCE(NULLIF(?, ''), display_name),
                    photo_url = COALESCE(NULLIF(?, ''), photo_url),
                    metadata = metadata || ?::jsonb,
                    updated_at = now()
                WHERE id = ?::uuid
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, name)
                stmt.setString(2, photo)
                stmt.setString(3, payload)
                stmt.setString(4, userId)
                stmt.executeUpdate()
            }
        }
    }

    fun neighborCard(viewerId: String, userId: String): com.onlygoodthings.shared.domain.NeighborCard? =
        db.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT u.id::text, u.display_name, u.photo_url, u.community_points, u.role::text, u.invite_code,
                       (SELECT COUNT(*) FROM social_posts p WHERE p.author_user_id = u.id AND p.moderation_status = 'VISIBLE') AS posts,
                       (SELECT COUNT(*) FROM follows f WHERE f.followed_id = u.id) AS followers,
                       (SELECT COUNT(*) FROM follows f WHERE f.follower_id = u.id) AS following,
                       EXISTS (SELECT 1 FROM follows f WHERE f.follower_id = ?::uuid AND f.followed_id = u.id) AS viewer_follows
                FROM users u
                WHERE u.id = ?::uuid AND u.status = 'ACTIVE'
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, viewerId)
                stmt.setString(2, userId)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return@use null
                    val pts = rs.getInt("community_points")
                    val mine = viewerId == userId
                    com.onlygoodthings.shared.domain.NeighborCard(
                        userId = rs.getString("id"),
                        displayName = rs.getString("display_name"),
                        photoUrl = rs.stringOrNull("photo_url"),
                        communityPoints = pts,
                        postsCount = rs.getInt("posts"),
                        followerCount = rs.getInt("followers"),
                        followingCount = rs.getInt("following"),
                        viewerFollows = rs.getBoolean("viewer_follows"),
                        inviteCode = if (mine) rs.getString("invite_code") else null,
                        role = rs.getString("role"),
                        levelLabel = com.onlygoodthings.shared.domain.communityLevelLabel(pts),
                    )
                }
            }
        }

    /**
     * Compra alcance. No suma puntos ni impact_score.
     * [kind] PROMOTED_REACH o AD_IMPRESSION.
     */
    fun spendReach(
        postId: String,
        payerUserId: String,
        amountCents: Long,
        kind: MoneyLedgerKind,
        companyId: String?,
        campaignId: String?,
    ): SocialPost? {
        require(amountCents > 0) { "El monto tiene que ser mayor a 0" }
        val placement = when (kind) {
            MoneyLedgerKind.AD_IMPRESSION -> PostPlacement.AD
            MoneyLedgerKind.PROMOTED_REACH -> PostPlacement.PROMOTED
            MoneyLedgerKind.ACTION_FINANCE -> error("ACTION_FINANCE no cambia el placement del post")
        }
        db.withConnection { connection ->
            connection.autoCommit = false
            try {
                val owner = connection.prepareStatement(
                    "SELECT author_user_id::text, author_company_id::text FROM social_posts WHERE id = ?::uuid",
                ).use { stmt ->
                    stmt.setString(1, postId)
                    stmt.executeQuery().use { rs ->
                        if (!rs.next()) null else rs.getString(1) to rs.getString(2)
                    }
                } ?: error("Post inexistente")
                val owns = owner.first == payerUserId || (companyId != null && owner.second == companyId)
                require(owns) { "Solo el autor puede pagar alcance de este post" }
                connection.prepareStatement(
                    """
                    INSERT INTO money_ledgers (kind, amount_cents, payer_user_id, company_id, post_id, campaign_id, note)
                    VALUES (?::money_ledger_kind, ?, ?::uuid, ?::uuid, ?::uuid, ?::uuid, ?)
                    """.trimIndent(),
                ).use { stmt ->
                    stmt.setString(1, kind.name)
                    stmt.setLong(2, amountCents)
                    stmt.setString(3, payerUserId)
                    if (companyId == null) stmt.setNull(4, java.sql.Types.OTHER) else stmt.setString(4, companyId)
                    stmt.setString(5, postId)
                    if (campaignId == null) stmt.setNull(6, java.sql.Types.OTHER) else stmt.setString(6, campaignId)
                    stmt.setString(7, "Alcance pago. No es impacto OGT.")
                    stmt.executeUpdate()
                }
                connection.prepareStatement(
                    "UPDATE social_posts SET placement = ?::post_placement WHERE id = ?::uuid",
                ).use { stmt ->
                    stmt.setString(1, placement.name)
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
        return byId(postId, payerUserId)
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

    fun comments(postId: String, anecdoteId: String? = null): List<SocialComment> = db.withConnection { connection ->
        nest(loadComments(connection, postId, anecdoteId))
    }

    fun addComment(
        postId: String,
        userId: String,
        body: String,
        parentId: String?,
        anecdoteId: String? = null,
    ): SocialComment {
        val trimmed = body.trim()
        require(trimmed.isNotBlank()) { "El comentario no puede estar vacío" }
        val id = db.withConnection { connection ->
            if (!anecdoteId.isNullOrBlank()) {
                val owner = connection.prepareStatement(
                    "SELECT post_id::text FROM post_anecdotes WHERE id = ?::uuid",
                ).use { stmt ->
                    stmt.setString(1, anecdoteId)
                    stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
                }
                require(owner == postId) { "La anécdota no pertenece a este homenaje" }
            }
            val commentId = UUID.randomUUID().toString()
            connection.prepareStatement(
                """
                INSERT INTO post_comments (id, post_id, author_user_id, parent_comment_id, body, anecdote_id)
                VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?, ?::uuid)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, commentId)
                stmt.setString(2, postId)
                stmt.setString(3, userId)
                if (parentId == null) stmt.setNull(4, java.sql.Types.OTHER) else stmt.setString(4, parentId)
                stmt.setString(5, trimmed)
                if (anecdoteId.isNullOrBlank()) stmt.setNull(6, java.sql.Types.OTHER) else stmt.setString(6, anecdoteId)
                stmt.executeUpdate()
            }
            if (anecdoteId.isNullOrBlank()) {
                connection.prepareStatement(
                    "UPDATE social_posts SET comment_count = comment_count + 1 WHERE id = ?::uuid",
                ).use { stmt ->
                    stmt.setString(1, postId)
                    stmt.executeUpdate()
                }
            } else {
                recountAnecdoteComments(connection, anecdoteId)
            }
            commentId
        }
        recordFeedEvent(userId, postId, FeedEventKind.COMMENT, dwellMs = null)
        return commentById(id) ?: error("No se pudo leer el comentario")
    }

    fun editComment(commentId: String, userId: String, body: String): SocialComment {
        val trimmed = body.trim()
        require(trimmed.isNotBlank()) { "El comentario no puede estar vacío" }
        db.withConnection { connection ->
            val author = connection.prepareStatement(
                "SELECT author_user_id::text FROM post_comments WHERE id = ?::uuid",
            ).use { stmt ->
                stmt.setString(1, commentId)
                stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            } ?: error("Comentario inexistente")
            require(author == userId) { "Solo quien escribió el comentario puede editarlo" }
            connection.prepareStatement(
                "UPDATE post_comments SET body = ?, edited_at = now() WHERE id = ?::uuid",
            ).use { stmt ->
                stmt.setString(1, trimmed)
                stmt.setString(2, commentId)
                stmt.executeUpdate()
            }
        }
        return commentById(commentId) ?: error("No se pudo leer el comentario")
    }

    fun deleteComment(commentId: String, userId: String) {
        db.withConnection { connection ->
            val row = connection.prepareStatement(
                "SELECT author_user_id::text, post_id::text, anecdote_id::text FROM post_comments WHERE id = ?::uuid",
            ).use { stmt ->
                stmt.setString(1, commentId)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) null else Triple(rs.getString(1), rs.getString(2), rs.getString(3))
                }
            } ?: error("Comentario inexistente")
            require(row.first == userId) { "Solo quien escribió el comentario puede borrarlo" }
            connection.prepareStatement("DELETE FROM post_comments WHERE id = ?::uuid").use { stmt ->
                stmt.setString(1, commentId)
                stmt.executeUpdate()
            }
            if (row.third.isNullOrBlank()) {
                connection.prepareStatement(
                    """
                    UPDATE social_posts
                    SET comment_count = (
                        SELECT COUNT(*) FROM post_comments
                        WHERE post_id = ?::uuid AND anecdote_id IS NULL AND moderation_status = 'VISIBLE'
                    )
                    WHERE id = ?::uuid
                    """.trimIndent(),
                ).use { stmt ->
                    stmt.setString(1, row.second)
                    stmt.setString(2, row.second)
                    stmt.executeUpdate()
                }
            } else {
                recountAnecdoteComments(connection, row.third)
            }
        }
    }

    fun addAnecdote(postId: String, userId: String, body: String, sourceUrl: String?): SocialAnecdote {
        val trimmed = body.trim()
        require(trimmed.isNotBlank()) { "La anécdota no puede estar vacía" }
        val id = db.withConnection { connection ->
            val meta = connection.prepareStatement(
                "SELECT honoree_name, COALESCE(topic, '') AS topic FROM social_posts WHERE id = ?::uuid",
            ).use { stmt ->
                stmt.setString(1, postId)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) null else rs.stringOrNull("honoree_name") to rs.getString("topic")
                }
            } ?: error("Homenaje inexistente")
            val homage = !meta.first.isNullOrBlank() || meta.second.contains("Homenaje", ignoreCase = true)
            require(homage) { "Solo se agregan anécdotas a un homenaje" }
            val nextOrder = connection.prepareStatement(
                "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM post_anecdotes WHERE post_id = ?::uuid",
            ).use { stmt ->
                stmt.setString(1, postId)
                stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }
            val anecdoteId = UUID.randomUUID().toString()
            connection.prepareStatement(
                """
                INSERT INTO post_anecdotes (id, post_id, author_user_id, body, source_url, sort_order)
                VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, ?)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, anecdoteId)
                stmt.setString(2, postId)
                stmt.setString(3, userId)
                stmt.setString(4, trimmed)
                if (sourceUrl.isNullOrBlank()) stmt.setNull(5, java.sql.Types.VARCHAR) else stmt.setString(5, sourceUrl.trim())
                stmt.setInt(6, nextOrder)
                stmt.executeUpdate()
            }
            anecdoteId
        }
        recordFeedEvent(userId, postId, FeedEventKind.COMMENT, dwellMs = null)
        return anecdoteById(id, userId) ?: error("No se pudo leer la anécdota")
    }

    fun editAnecdote(anecdoteId: String, userId: String, body: String): SocialAnecdote {
        val trimmed = body.trim()
        require(trimmed.isNotBlank()) { "La anécdota no puede estar vacía" }
        db.withConnection { connection ->
            require(authorOfAnecdote(connection, anecdoteId) == userId) {
                "Solo quien escribió la anécdota puede editarla"
            }
            connection.prepareStatement(
                "UPDATE post_anecdotes SET body = ?, updated_at = now() WHERE id = ?::uuid",
            ).use { stmt ->
                stmt.setString(1, trimmed)
                stmt.setString(2, anecdoteId)
                stmt.executeUpdate()
            }
        }
        return anecdoteById(anecdoteId, userId) ?: error("No se pudo leer la anécdota")
    }

    fun deleteAnecdote(anecdoteId: String, userId: String) {
        db.withConnection { connection ->
            require(authorOfAnecdote(connection, anecdoteId) == userId) {
                "Solo quien escribió la anécdota puede borrarla"
            }
            connection.prepareStatement("DELETE FROM post_anecdotes WHERE id = ?::uuid").use { stmt ->
                stmt.setString(1, anecdoteId)
                stmt.executeUpdate()
            }
        }
    }

    fun clapAnecdote(anecdoteId: String, userId: String): SocialAnecdote {
        val postId = toggleAnecdoteMark(
            anecdoteId = anecdoteId,
            userId = userId,
            table = "anecdote_impacts",
            countColumn = "impact_count",
        )
        recordFeedEvent(userId, postId, FeedEventKind.CLAP, dwellMs = null)
        return anecdoteById(anecdoteId, userId) ?: error("Anécdota inexistente")
    }

    fun heartAnecdote(anecdoteId: String, userId: String): SocialAnecdote {
        val postId = toggleAnecdoteMark(
            anecdoteId = anecdoteId,
            userId = userId,
            table = "anecdote_hearts",
            countColumn = "heart_count",
        )
        recordFeedEvent(userId, postId, FeedEventKind.HEART, dwellMs = null)
        return anecdoteById(anecdoteId, userId) ?: error("Anécdota inexistente")
    }

    fun repostAnecdote(anecdoteId: String, userId: String): SocialPost {
        val anecdote = anecdoteById(anecdoteId, userId) ?: error("Anécdota inexistente")
        val parent = byId(anecdote.postId, userId) ?: error("Homenaje inexistente")
        val media = parent.media.ifEmpty {
            parent.mediaUrls.mapIndexed { index, url ->
                PostMediaItem(id = UUID.randomUUID().toString(), kind = MediaKind.IMAGE, url = url, sortOrder = index)
            }
        }
        require(media.isNotEmpty()) { "El homenaje no tiene foto para el repost" }
        val honoree = parent.honoreeName?.trim()?.takeIf { it.isNotEmpty() }
        val saved = publish(
            userId = userId,
            body = anecdote.body,
            topic = "Anécdota",
            media = media,
            latitude = null,
            longitude = null,
            protagonistUserId = null,
            participantUserIds = emptyList(),
            honoreeName = honoree,
            sourceUrl = postDeepLink(parent.id),
        )
        recordFeedEvent(userId, anecdote.postId, FeedEventKind.SHARE, dwellMs = null)
        return saved
    }

    fun anecdoteById(anecdoteId: String, viewerId: String): SocialAnecdote? = db.withConnection { connection ->
        readAnecdote(connection, anecdoteId, viewerId)
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

    fun editPost(postId: String, userId: String, body: String, topic: String?): SocialPost {
        val trimmed = body.trim()
        require(trimmed.isNotBlank()) { "El texto no puede estar vacío" }
        db.withConnection { connection ->
            val author = connection.prepareStatement(
                "SELECT author_user_id::text FROM social_posts WHERE id = ?::uuid AND moderation_status = 'VISIBLE'",
            ).use { stmt ->
                stmt.setString(1, postId)
                stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            } ?: error("Post inexistente")
            require(author == userId) { "Solo quien publicó puede editar" }
            connection.prepareStatement(
                """
                UPDATE social_posts
                SET body = ?,
                    topic = COALESCE(?, topic),
                    updated_at = now()
                WHERE id = ?::uuid
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, trimmed)
                if (topic.isNullOrBlank()) stmt.setNull(2, java.sql.Types.VARCHAR) else stmt.setString(2, topic.trim())
                stmt.setString(3, postId)
                stmt.executeUpdate()
            }
        }
        return byId(postId, userId) ?: error("No se pudo leer el post")
    }

    fun deletePost(postId: String, userId: String) {
        db.withConnection { connection ->
            val author = connection.prepareStatement(
                "SELECT author_user_id::text FROM social_posts WHERE id = ?::uuid AND moderation_status = 'VISIBLE'",
            ).use { stmt ->
                stmt.setString(1, postId)
                stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            } ?: error("Post inexistente")
            require(author == userId) { "Solo quien publicó puede eliminar" }
            connection.prepareStatement(
                "UPDATE social_posts SET moderation_status = 'REMOVED', updated_at = now() WHERE id = ?::uuid",
            ).use { stmt ->
                stmt.setString(1, postId)
                stmt.executeUpdate()
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
        val offered = connection.prepareStatement(
            "SELECT tag_id::text FROM user_skills WHERE user_id = ?::uuid AND offered",
        ).use { stmt ->
            stmt.setString(1, userId)
            stmt.executeQuery().use { rs ->
                buildSet { while (rs.next()) add(rs.getString(1)) }
            }
        }
        ViewerContext(userId, followed, events, lat, lng, offeredTagIds = offered)
    }

    private fun loadCandidates(): List<FeedCandidate> = db.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT p.id, p.author_user_id, p.author_company_id, COALESCE(p.topic, '') AS topic,
                   p.impact_count, p.comment_count, p.source_url,
                   EXTRACT(EPOCH FROM p.created_at) * 1000 AS created_ms,
                   ST_Y(p.location) AS lat, ST_X(p.location) AS lng,
                   COALESCE(p.achieved_count, 0) AS achieved_count,
                   COALESCE(p.placement::text, 'ORGANIC') AS placement,
                   COALESCE(u.trust_score, 0) AS trust_score,
                   CASE a.urgency
                       WHEN 'CRITICAL' THEN 3 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 1 ELSE 0
                   END AS urgency_rank,
                   (
                       SELECT pp.user_id::text FROM post_people pp
                       WHERE pp.post_id = p.id AND pp.role = 'PROTAGONIST' LIMIT 1
                   ) AS protagonist_id,
                   tn.tag_id::text AS need_tag_id
            FROM social_posts p
            LEFT JOIN users u ON u.id = p.author_user_id
            LEFT JOIN animal_listings a ON a.id = p.listing_id
            LEFT JOIN timebank_needs tn ON tn.post_id = p.id AND tn.open
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
                                achievedCount = rs.getInt("achieved_count"),
                                trustScore = rs.getDouble("trust_score"),
                                urgencyRank = rs.getInt("urgency_rank"),
                                placement = runCatching { PostPlacement.valueOf(rs.getString("placement")) }
                                    .getOrDefault(PostPlacement.ORGANIC),
                                needTagId = rs.stringOrNull("need_tag_id"),
                            ),
                        )
                    }
                }
            }
        }
    }

    /** Pedidos abiertos de gente que seguís y que vos podés cubrir. Entran al feed aunque no estén entre los 200 más nuevos. */
    private fun loadMatchedFriendNeeds(viewer: ViewerContext): List<FeedCandidate> {
        if (viewer.followedIds.isEmpty() || viewer.offeredTagIds.isEmpty()) return emptyList()
        val people = viewer.followedIds.joinToString(",") { "?::uuid" }
        val tags = viewer.offeredTagIds.joinToString(",") { "?::uuid" }
        return db.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT p.id, p.author_user_id, p.author_company_id, COALESCE(p.topic, '') AS topic,
                       p.impact_count, p.comment_count, p.source_url,
                       EXTRACT(EPOCH FROM p.created_at) * 1000 AS created_ms,
                       ST_Y(p.location) AS lat, ST_X(p.location) AS lng,
                       COALESCE(p.achieved_count, 0) AS achieved_count,
                       COALESCE(p.placement::text, 'ORGANIC') AS placement,
                       COALESCE(u.trust_score, 0) AS trust_score,
                       0 AS urgency_rank,
                       p.author_user_id::text AS protagonist_id,
                       n.tag_id::text AS need_tag_id
                FROM timebank_needs n
                JOIN social_posts p ON p.id = n.post_id
                LEFT JOIN users u ON u.id = p.author_user_id
                WHERE n.open
                  AND p.moderation_status = 'VISIBLE'
                  AND p.is_story = FALSE
                  AND n.user_id IN ($people)
                  AND n.tag_id IN ($tags)
                  AND n.user_id <> ?::uuid
                ORDER BY n.created_at DESC
                LIMIT 20
                """.trimIndent(),
            ).use { stmt ->
                var i = 1
                viewer.followedIds.forEach { stmt.setString(i++, it) }
                viewer.offeredTagIds.forEach { stmt.setString(i++, it) }
                stmt.setString(i, viewer.userId)
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
                                    achievedCount = rs.getInt("achieved_count"),
                                    trustScore = rs.getDouble("trust_score"),
                                    urgencyRank = rs.getInt("urgency_rank"),
                                    placement = runCatching { PostPlacement.valueOf(rs.getString("placement")) }
                                        .getOrDefault(PostPlacement.ORGANIC),
                                    needTagId = rs.stringOrNull("need_tag_id"),
                                ),
                            )
                        }
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
                       p.source_url, p.honoree_name,
                       COALESCE(p.achieved_count, 0) AS achieved_count,
                       COALESCE(p.placement::text, 'ORGANIC') AS placement,
                       EXTRACT(EPOCH FROM p.created_at) * 1000 AS created_ms,
                       COALESCE(u.display_name, c.trade_name, c.legal_name) AS author_name,
                       COALESCE(u.photo_url, c.logo_url) AS author_photo,
                       EXISTS (
                           SELECT 1 FROM post_impacts i
                           WHERE i.post_id = p.id AND i.user_id = ?::uuid
                       ) AS viewer_hit,
                       EXISTS (
                           SELECT 1 FROM action_outcomes o
                           WHERE o.company_id = p.author_company_id AND o.verified = TRUE
                       ) AS empresa_que_suma,
                       (
                           SELECT pp.user_id::text FROM post_people pp
                           WHERE pp.post_id = p.id AND pp.role = 'PROTAGONIST' LIMIT 1
                       ) AS protagonist_id,
                       tn.id::text AS need_id,
                       nt.label AS need_label,
                       (
                           SELECT COUNT(*)::int FROM timebank_need_supports s WHERE s.need_id = tn.id
                       ) AS support_count,
                       EXISTS (
                           SELECT 1 FROM timebank_need_supports s
                           WHERE s.need_id = tn.id AND s.user_id = ?::uuid
                       ) AS viewer_supported,
                       EXISTS (
                           SELECT 1 FROM timebank_need_invites i
                           WHERE i.need_id = tn.id AND i.invitee_id = ?::uuid
                       ) AS viewer_invited,
                       EXISTS (
                           SELECT 1 FROM user_skills us
                           WHERE us.user_id = ?::uuid AND us.offered AND us.tag_id = tn.tag_id
                       ) AS matches_my_offer,
                       COALESCE((
                           SELECT array_agg(DISTINCT st.label ORDER BY st.label)
                           FROM (
                               SELECT tn.user_id AS uid
                               UNION
                               SELECT s.user_id FROM timebank_need_supports s WHERE s.need_id = tn.id
                           ) people
                           JOIN user_skills us ON us.user_id = people.uid AND us.offered
                           JOIN skill_tags st ON st.id = us.tag_id AND st.slug <> 'mensaje'
                           WHERE us.tag_id IS DISTINCT FROM tn.tag_id
                       ), ARRAY[]::text[]) AS give_labels
                FROM social_posts p
                LEFT JOIN users u ON u.id = p.author_user_id
                LEFT JOIN companies c ON c.id = p.author_company_id
                LEFT JOIN timebank_needs tn ON tn.post_id = p.id
                LEFT JOIN skill_tags nt ON nt.id = tn.tag_id
                WHERE p.id IN ($placeholders)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, viewerId)
                stmt.setString(2, viewerId)
                stmt.setString(3, viewerId)
                stmt.setString(4, viewerId)
                ids.forEachIndexed { index, id -> stmt.setString(index + 5, id) }
                val posts = stmt.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(rs.toPost()) }
                }
                val media = loadMedia(connection, ids)
                val anecdotes = loadAnecdotes(connection, ids, viewerId)
                posts.map { post ->
                    val items = media[post.id].orEmpty()
                    post.copy(
                        media = items,
                        mediaUrls = items.map { it.url }.ifEmpty { post.mediaUrls },
                        anecdotes = anecdotes[post.id].orEmpty(),
                    )
                }
            }
        }
    }

    private fun loadAnecdotes(
        connection: java.sql.Connection,
        ids: List<String>,
        viewerId: String,
    ): Map<String, List<SocialAnecdote>> {
        if (ids.isEmpty()) return emptyMap()
        val placeholders = ids.joinToString(",") { "?::uuid" }
        val rows = connection.prepareStatement(
            """
            SELECT a.id, a.post_id, a.author_user_id, a.body, a.source_url, a.sort_order,
                   COALESCE(a.impact_count, 0) AS impact_count,
                   COALESCE(a.comment_count, 0) AS comment_count,
                   COALESCE(a.heart_count, 0) AS heart_count,
                   EXTRACT(EPOCH FROM a.created_at) * 1000 AS created_ms,
                   u.display_name,
                   EXISTS (
                       SELECT 1 FROM anecdote_impacts i
                       WHERE i.anecdote_id = a.id AND i.user_id = ?::uuid
                   ) AS viewer_hit,
                   EXISTS (
                       SELECT 1 FROM anecdote_hearts h
                       WHERE h.anecdote_id = a.id AND h.user_id = ?::uuid
                   ) AS viewer_heart
            FROM post_anecdotes a
            JOIN users u ON u.id = a.author_user_id
            WHERE a.post_id IN ($placeholders)
            ORDER BY a.post_id, a.sort_order, a.created_at
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, viewerId)
            stmt.setString(2, viewerId)
            ids.forEachIndexed { index, id -> stmt.setString(index + 3, id) }
            stmt.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.toAnecdote()) }
            }
        }
        val comments = loadCommentsForAnecdotes(connection, rows.map { it.id })
        return rows
            .map { row -> row.copy(comments = nest(comments[row.id].orEmpty())) }
            .groupBy { it.postId }
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

    private fun commentById(commentId: String): SocialComment? = db.withConnection { connection ->
        loadCommentsByIds(connection, listOf(commentId)).firstOrNull()
    }

    private fun loadComments(
        connection: java.sql.Connection,
        postId: String,
        anecdoteId: String?,
    ): List<SocialComment> {
        val sql = if (anecdoteId.isNullOrBlank()) {
            """
            SELECT c.id, c.post_id, c.author_user_id, c.parent_comment_id, c.body, c.anecdote_id,
                   EXTRACT(EPOCH FROM c.created_at) * 1000 AS created_ms,
                   c.edited_at,
                   u.display_name
            FROM post_comments c
            JOIN users u ON u.id = c.author_user_id
            WHERE c.post_id = ?::uuid AND c.moderation_status = 'VISIBLE' AND c.anecdote_id IS NULL
            ORDER BY c.created_at
            """.trimIndent()
        } else {
            """
            SELECT c.id, c.post_id, c.author_user_id, c.parent_comment_id, c.body, c.anecdote_id,
                   EXTRACT(EPOCH FROM c.created_at) * 1000 AS created_ms,
                   c.edited_at,
                   u.display_name
            FROM post_comments c
            JOIN users u ON u.id = c.author_user_id
            WHERE c.post_id = ?::uuid AND c.moderation_status = 'VISIBLE' AND c.anecdote_id = ?::uuid
            ORDER BY c.created_at
            """.trimIndent()
        }
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, postId)
            if (!anecdoteId.isNullOrBlank()) stmt.setString(2, anecdoteId)
            stmt.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.toComment()) }
            }
        }
    }

    private fun loadCommentsByIds(connection: java.sql.Connection, ids: List<String>): List<SocialComment> {
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?::uuid" }
        return connection.prepareStatement(
            """
            SELECT c.id, c.post_id, c.author_user_id, c.parent_comment_id, c.body, c.anecdote_id,
                   EXTRACT(EPOCH FROM c.created_at) * 1000 AS created_ms,
                   c.edited_at,
                   u.display_name
            FROM post_comments c
            JOIN users u ON u.id = c.author_user_id
            WHERE c.id IN ($placeholders)
            """.trimIndent(),
        ).use { stmt ->
            ids.forEachIndexed { index, id -> stmt.setString(index + 1, id) }
            stmt.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.toComment()) }
            }
        }
    }

    private fun loadCommentsForAnecdotes(
        connection: java.sql.Connection,
        anecdoteIds: List<String>,
    ): Map<String, List<SocialComment>> {
        if (anecdoteIds.isEmpty()) return emptyMap()
        val placeholders = anecdoteIds.joinToString(",") { "?::uuid" }
        return connection.prepareStatement(
            """
            SELECT c.id, c.post_id, c.author_user_id, c.parent_comment_id, c.body, c.anecdote_id,
                   EXTRACT(EPOCH FROM c.created_at) * 1000 AS created_ms,
                   c.edited_at,
                   u.display_name
            FROM post_comments c
            JOIN users u ON u.id = c.author_user_id
            WHERE c.anecdote_id IN ($placeholders) AND c.moderation_status = 'VISIBLE'
            ORDER BY c.created_at
            """.trimIndent(),
        ).use { stmt ->
            anecdoteIds.forEachIndexed { index, id -> stmt.setString(index + 1, id) }
            stmt.executeQuery().use { rs ->
                buildMap<String, MutableList<SocialComment>> {
                    while (rs.next()) {
                        val row = rs.toComment()
                        val key = row.anecdoteId ?: continue
                        getOrPut(key) { mutableListOf() } += row
                    }
                }
            }
        }
    }

    private fun readAnecdote(
        connection: java.sql.Connection,
        anecdoteId: String,
        viewerId: String,
    ): SocialAnecdote? {
        val row = connection.prepareStatement(
            """
            SELECT a.id, a.post_id, a.author_user_id, a.body, a.source_url, a.sort_order,
                   COALESCE(a.impact_count, 0) AS impact_count,
                   COALESCE(a.comment_count, 0) AS comment_count,
                   COALESCE(a.heart_count, 0) AS heart_count,
                   EXTRACT(EPOCH FROM a.created_at) * 1000 AS created_ms,
                   u.display_name,
                   EXISTS (
                       SELECT 1 FROM anecdote_impacts i
                       WHERE i.anecdote_id = a.id AND i.user_id = ?::uuid
                   ) AS viewer_hit,
                   EXISTS (
                       SELECT 1 FROM anecdote_hearts h
                       WHERE h.anecdote_id = a.id AND h.user_id = ?::uuid
                   ) AS viewer_heart
            FROM post_anecdotes a
            JOIN users u ON u.id = a.author_user_id
            WHERE a.id = ?::uuid
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, viewerId)
            stmt.setString(2, viewerId)
            stmt.setString(3, anecdoteId)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.toAnecdote() else null }
        } ?: return null
        val comments = loadCommentsForAnecdotes(connection, listOf(row.id))
        return row.copy(comments = nest(comments[row.id].orEmpty()))
    }

    private fun authorOfAnecdote(connection: java.sql.Connection, anecdoteId: String): String =
        connection.prepareStatement(
            "SELECT author_user_id::text FROM post_anecdotes WHERE id = ?::uuid",
        ).use { stmt ->
            stmt.setString(1, anecdoteId)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        } ?: error("Anécdota inexistente")

    private fun recountAnecdoteComments(connection: java.sql.Connection, anecdoteId: String) {
        connection.prepareStatement(
            """
            UPDATE post_anecdotes
            SET comment_count = (
                SELECT COUNT(*) FROM post_comments
                WHERE anecdote_id = ?::uuid AND moderation_status = 'VISIBLE'
            ),
            updated_at = now()
            WHERE id = ?::uuid
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, anecdoteId)
            stmt.setString(2, anecdoteId)
            stmt.executeUpdate()
        }
    }

    private fun toggleAnecdoteMark(
        anecdoteId: String,
        userId: String,
        table: String,
        countColumn: String,
    ): String = db.withConnection { connection ->
        val postId = connection.prepareStatement(
            "SELECT post_id::text FROM post_anecdotes WHERE id = ?::uuid",
        ).use { stmt ->
            stmt.setString(1, anecdoteId)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        } ?: error("Anécdota inexistente")
        val existed = connection.prepareStatement(
            "SELECT 1 FROM $table WHERE anecdote_id = ?::uuid AND user_id = ?::uuid",
        ).use { stmt ->
            stmt.setString(1, anecdoteId)
            stmt.setString(2, userId)
            stmt.executeQuery().use { it.next() }
        }
        if (existed) {
            connection.prepareStatement(
                "DELETE FROM $table WHERE anecdote_id = ?::uuid AND user_id = ?::uuid",
            ).use { stmt ->
                stmt.setString(1, anecdoteId)
                stmt.setString(2, userId)
                stmt.executeUpdate()
            }
        } else {
            connection.prepareStatement(
                "INSERT INTO $table (anecdote_id, user_id) VALUES (?::uuid, ?::uuid) ON CONFLICT DO NOTHING",
            ).use { stmt ->
                stmt.setString(1, anecdoteId)
                stmt.setString(2, userId)
                stmt.executeUpdate()
            }
        }
        connection.prepareStatement(
            """
            UPDATE post_anecdotes
            SET $countColumn = (SELECT COUNT(*) FROM $table WHERE anecdote_id = ?::uuid),
                updated_at = now()
            WHERE id = ?::uuid
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, anecdoteId)
            stmt.setString(2, anecdoteId)
            stmt.executeUpdate()
        }
        postId
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
            placement = runCatching { PostPlacement.valueOf(getString("placement")) }
                .getOrDefault(PostPlacement.ORGANIC),
            achievedCount = runCatching { getInt("achieved_count") }.getOrDefault(0),
            empresaQueSuma = runCatching { getBoolean("empresa_que_suma") }.getOrDefault(false),
            honoreeName = runCatching { stringOrNull("honoree_name") }.getOrNull(),
            needId = runCatching { stringOrNull("need_id") }.getOrNull(),
            needLabel = runCatching { stringOrNull("need_label") }.getOrNull(),
            giveLabels = runCatching {
                (getArray("give_labels")?.array as? Array<*>)?.mapNotNull { it as? String } ?: emptyList()
            }.getOrDefault(emptyList()),
            giveLabel = runCatching {
                (getArray("give_labels")?.array as? Array<*>)?.mapNotNull { it as? String }
                    ?.joinToString(" · ")
                    ?.ifBlank { null }
            }.getOrNull(),
            supportCount = runCatching { getInt("support_count") }.getOrDefault(0),
            viewerSupported = runCatching { getBoolean("viewer_supported") }.getOrDefault(false),
            viewerInvited = runCatching { getBoolean("viewer_invited") }.getOrDefault(false),
            matchesMyOffer = runCatching { getBoolean("matches_my_offer") }.getOrDefault(false),
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
        anecdoteId = runCatching { stringOrNull("anecdote_id") }.getOrNull(),
        edited = runCatching { getObject("edited_at") != null }.getOrDefault(false),
    )

    private fun ResultSet.toAnecdote(): SocialAnecdote = SocialAnecdote(
        id = getString("id"),
        postId = getString("post_id"),
        authorUserId = getString("author_user_id"),
        authorName = getString("display_name") ?: "Alguien de la comunidad",
        body = getString("body"),
        sourceUrl = stringOrNull("source_url"),
        sortOrder = getInt("sort_order"),
        createdAtEpochMs = getLong("created_ms"),
        impactCount = runCatching { getInt("impact_count") }.getOrDefault(0),
        commentCount = runCatching { getInt("comment_count") }.getOrDefault(0),
        heartCount = runCatching { getInt("heart_count") }.getOrDefault(0),
        viewerHasImpacted = runCatching { getBoolean("viewer_hit") }.getOrDefault(false),
        viewerHasHearted = runCatching { getBoolean("viewer_heart") }.getOrDefault(false),
    )
}
