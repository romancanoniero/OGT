package com.onlygoodthings.backend.social

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.infra.stringOrNull
import com.onlygoodthings.shared.domain.HonorChannel
import com.onlygoodthings.shared.domain.HonorMentionView
import com.onlygoodthings.shared.domain.HonorStatus
import com.onlygoodthings.shared.domain.PostPersonRole
import com.onlygoodthings.shared.domain.mentionHandle
import com.onlygoodthings.shared.domain.titleCasePersonName
import com.onlygoodthings.shared.domain.normalizeHonorContact
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

class HonorSqlRepository(private val db: Database) {

    fun issue(
        issuerId: String,
        givenName: String,
        channel: HonorChannel,
        contact: String,
        claimToken: String? = null,
    ): HonorMentionView {
        val name = titleCasePersonName(givenName.trim())
        require(name.isNotEmpty()) { "La mención de honor necesita un nombre." }
        val normalized = normalizeHonorContact(channel, contact)
        require(normalized.isNotEmpty()) { "Falta el contacto de la invitación." }
        val token = claimToken?.trim()?.takeIf { it.length >= 6 }
            ?: "${mentionHandle(name).take(8)}${UUID.randomUUID().toString().replace("-", "").take(10)}"
        return db.withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO honor_mentions (issuer_user_id, given_name, channel, contact, claim_token)
                VALUES (?::uuid, ?, ?::honor_channel, ?, ?)
                RETURNING id, given_name, claim_token, status, claimed_user_id, post_id
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, issuerId)
                stmt.setString(2, name)
                stmt.setString(3, channel.name)
                stmt.setString(4, normalized)
                stmt.setString(5, token)
                stmt.executeQuery().use { rs ->
                    check(rs.next())
                    rs.toView(connection)
                }
            }
        }
    }

    fun peek(token: String): HonorMentionView? = db.withConnection { connection ->
        loadByToken(connection, token)
    }

    fun attach(issuerId: String, token: String, postId: String, role: PostPersonRole): HonorMentionView? =
        db.withConnection { connection ->
            connection.autoCommit = false
            try {
                val honor = loadByToken(connection, token) ?: return@withConnection null
                if (honor.status != HonorStatus.PENDING && honor.claimedUserId == null) {
                    connection.rollback()
                    return@withConnection honor
                }
                connection.prepareStatement(
                    """
                    UPDATE honor_mentions
                    SET post_id = ?::uuid, role = ?::post_person_role
                    WHERE claim_token = ? AND issuer_user_id = ?::uuid
                    """.trimIndent(),
                ).use { stmt ->
                    stmt.setString(1, postId)
                    stmt.setString(2, role.name)
                    stmt.setString(3, token)
                    stmt.setString(4, issuerId)
                    if (stmt.executeUpdate() == 0) {
                        connection.rollback()
                        return@withConnection null
                    }
                }
                if (honor.status == HonorStatus.PENDING) {
                    connection.prepareStatement(
                        """
                        INSERT INTO post_people (post_id, honor_id, role)
                        VALUES (?::uuid, ?::uuid, ?::post_person_role)
                        ON CONFLICT DO NOTHING
                        """.trimIndent(),
                    ).use { stmt ->
                        stmt.setString(1, postId)
                        stmt.setString(2, honor.id)
                        stmt.setString(3, role.name)
                        stmt.executeUpdate()
                    }
                }
                connection.commit()
                loadByToken(connection, token)
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }

    fun claim(token: String, claimantId: String): HonorMentionView? = db.withConnection { connection ->
        connection.autoCommit = false
        try {
            val current = loadByToken(connection, token) ?: return@withConnection null
            if (current.status == HonorStatus.CLAIMED) {
                connection.rollback()
                return@withConnection current.takeIf { it.claimedUserId == claimantId }
            }
            if (current.status != HonorStatus.PENDING) {
                connection.rollback()
                return@withConnection null
            }
            connection.prepareStatement(
                """
                UPDATE honor_mentions
                SET status = 'CLAIMED', claimed_user_id = ?::uuid, claimed_at = now()
                WHERE claim_token = ? AND status = 'PENDING'
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, claimantId)
                stmt.setString(2, token)
                stmt.executeUpdate()
            }
            connection.prepareStatement(
                """
                UPDATE post_people
                SET user_id = ?::uuid, honor_id = NULL
                WHERE honor_id = ?::uuid
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, claimantId)
                stmt.setString(2, current.id)
                stmt.executeUpdate()
            }
            connection.commit()
            loadByToken(connection, token)
        } catch (error: Exception) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = true
        }
    }

    private fun loadByToken(connection: Connection, token: String): HonorMentionView? {
        connection.prepareStatement(
            """
            SELECT id, given_name, claim_token, status, claimed_user_id, post_id
            FROM honor_mentions
            WHERE claim_token = ?
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, token)
            return stmt.executeQuery().use { rs -> if (rs.next()) rs.toView(connection) else null }
        }
    }

    private fun ResultSet.toView(connection: Connection): HonorMentionView {
        val issuerId = runCatching {
            connection.prepareStatement(
                """
                SELECT u.display_name
                FROM honor_mentions h JOIN users u ON u.id = h.issuer_user_id
                WHERE h.claim_token = ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, getString("claim_token"))
                stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }
        }.getOrNull()
        val claimedId = stringOrNull("claimed_user_id")
        val credit = claimedId?.let { uid ->
            connection.prepareStatement("SELECT display_name FROM users WHERE id = ?::uuid").use { stmt ->
                stmt.setString(1, uid)
                stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }
        }
        return HonorMentionView(
            id = getString("id"),
            givenName = credit ?: getString("given_name"),
            issuerName = issuerId,
            status = HonorStatus.valueOf(getString("status")),
            claimToken = getString("claim_token"),
            claimedUserId = claimedId,
            postId = stringOrNull("post_id"),
        )
    }
}

fun claimHonorsMatchingContact(connection: Connection, userId: String, email: String?, phone: String?) {
    val mail = email?.trim()?.lowercase().orEmpty()
    val digits = phone.orEmpty().filter { it.isDigit() }
    if (mail.isEmpty() && digits.length < 8) return
    connection.prepareStatement(
        """
        SELECT id, claim_token FROM honor_mentions
        WHERE status = 'PENDING'
          AND issuer_user_id <> ?::uuid
          AND (
            (channel = 'EMAIL' AND contact = ?)
            OR (channel IN ('WHATSAPP', 'SMS') AND contact = ?)
          )
        """.trimIndent(),
    ).use { stmt ->
        stmt.setString(1, userId)
        stmt.setString(2, mail)
        stmt.setString(3, digits)
        stmt.executeQuery().use { rs ->
            val ids = mutableListOf<Pair<String, String>>()
            while (rs.next()) ids += rs.getString("id") to rs.getString("claim_token")
            ids.forEach { (honorId, _) ->
                connection.prepareStatement(
                    """
                    UPDATE honor_mentions
                    SET status = 'CLAIMED', claimed_user_id = ?::uuid, claimed_at = now()
                    WHERE id = ?::uuid AND status = 'PENDING'
                    """.trimIndent(),
                ).use { update ->
                    update.setString(1, userId)
                    update.setString(2, honorId)
                    update.executeUpdate()
                }
                connection.prepareStatement(
                    """
                    UPDATE post_people
                    SET user_id = ?::uuid, honor_id = NULL
                    WHERE honor_id = ?::uuid
                    """.trimIndent(),
                ).use { people ->
                    people.setString(1, userId)
                    people.setString(2, honorId)
                    people.executeUpdate()
                }
            }
        }
    }
}
