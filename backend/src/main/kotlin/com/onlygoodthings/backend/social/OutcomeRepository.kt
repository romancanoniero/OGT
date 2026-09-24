package com.onlygoodthings.backend.social

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.shared.domain.ActionOutcome
import com.onlygoodthings.shared.domain.ActionOutcomeKind
import com.onlygoodthings.shared.domain.CompanyImpactCard
import com.onlygoodthings.shared.domain.VerificationMethod
import java.sql.Connection
import java.util.UUID

/**
 * Único escritor de reputación y puntos.
 * Ningún endpoint de pago debe llamar acá.
 */
class OutcomeSqlRepository(private val db: Database) {

    data class Draft(
        val kind: ActionOutcomeKind,
        val method: VerificationMethod,
        val actorUserId: String,
        val sourceTable: String,
        val sourceId: String,
        val points: Int,
        val beneficiaryUserId: String? = null,
        val postId: String? = null,
        val companyId: String? = null,
        val campaignId: String? = null,
    )

    fun award(draft: Draft): ActionOutcome? = db.withConnection { connection ->
        connection.autoCommit = false
        try {
            val saved = awardOn(connection, draft)
            connection.commit()
            saved
        } catch (error: Exception) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = true
        }
    }

    fun awardOn(connection: Connection, draft: Draft): ActionOutcome? {
        val id = UUID.randomUUID().toString()
        val inserted = connection.prepareStatement(
            """
            INSERT INTO action_outcomes (
                id, kind, verification_method, actor_user_id, beneficiary_user_id,
                post_id, company_id, campaign_id, source_table, source_id, points_awarded
            ) VALUES (
                ?::uuid, ?::action_outcome_kind, ?::verification_method, ?::uuid, ?::uuid,
                ?::uuid, ?::uuid, ?::uuid, ?, ?::uuid, ?
            )
            ON CONFLICT (kind, source_id) WHERE source_id IS NOT NULL DO NOTHING
            RETURNING id, EXTRACT(EPOCH FROM verified_at) * 1000 AS done_ms
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, id)
            stmt.setString(2, draft.kind.name)
            stmt.setString(3, draft.method.name)
            stmt.setString(4, draft.actorUserId)
            if (draft.beneficiaryUserId == null) stmt.setNull(5, java.sql.Types.OTHER) else stmt.setString(5, draft.beneficiaryUserId)
            if (draft.postId == null) stmt.setNull(6, java.sql.Types.OTHER) else stmt.setString(6, draft.postId)
            if (draft.companyId == null) stmt.setNull(7, java.sql.Types.OTHER) else stmt.setString(7, draft.companyId)
            if (draft.campaignId == null) stmt.setNull(8, java.sql.Types.OTHER) else stmt.setString(8, draft.campaignId)
            stmt.setString(9, draft.sourceTable)
            stmt.setString(10, draft.sourceId)
            stmt.setInt(11, draft.points)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) null else rs.getLong("done_ms")
            }
        } ?: return null

        connection.prepareStatement(
            "UPDATE users SET community_points = community_points + ? WHERE id = ?::uuid",
        ).use { stmt ->
            stmt.setInt(1, draft.points)
            stmt.setString(2, draft.actorUserId)
            stmt.executeUpdate()
        }
        draft.postId?.let { postId ->
            connection.prepareStatement(
                "UPDATE social_posts SET achieved_count = achieved_count + 1 WHERE id = ?::uuid",
            ).use { stmt ->
                stmt.setString(1, postId)
                stmt.executeUpdate()
            }
        }
        // Solo cuenta como empresa que suma si financió el hecho, no si pagó un aviso.
        if (draft.companyId != null) {
            connection.prepareStatement(
                "UPDATE companies SET impact_score = impact_score + 1 WHERE id = ?::uuid",
            ).use { stmt ->
                stmt.setString(1, draft.companyId)
                stmt.executeUpdate()
            }
        }
        refreshTrust(connection, draft.actorUserId)
        connection.prepareStatement(
            """
            INSERT INTO domain_events (event_type, aggregate_type, aggregate_id, payload)
            VALUES (?, ?, ?::uuid, jsonb_build_object('points', ?, 'kind', ?))
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, "outcome.${draft.kind.name.lowercase()}")
            stmt.setString(2, draft.sourceTable)
            stmt.setString(3, draft.sourceId)
            stmt.setInt(4, draft.points)
            stmt.setString(5, draft.kind.name)
            stmt.executeUpdate()
        }
        return ActionOutcome(
            id = id,
            kind = draft.kind,
            verificationMethod = draft.method,
            actorUserId = draft.actorUserId,
            beneficiaryUserId = draft.beneficiaryUserId,
            postId = draft.postId,
            companyId = draft.companyId,
            campaignId = draft.campaignId,
            pointsAwarded = draft.points,
            verifiedAtEpochMs = inserted,
        )
    }

    fun companyImpact(companyId: String): CompanyImpactCard? = db.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT c.id, COALESCE(c.trade_name, c.legal_name) AS name, c.impact_score,
                   (
                       SELECT COUNT(*) FROM action_outcomes o
                       WHERE o.company_id = c.id AND o.verified = TRUE
                   ) AS outcomes
            FROM companies c
            WHERE c.id = ?::uuid
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, companyId)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return@withConnection null
                val outcomes = rs.getInt("outcomes")
                CompanyImpactCard(
                    companyId = rs.getString("id"),
                    tradeName = rs.getString("name"),
                    verifiedOutcomes = outcomes,
                    impactScore = rs.getDouble("impact_score"),
                    empresaQueSuma = outcomes > 0,
                )
            }
        }
    }

    private fun refreshTrust(connection: Connection, userId: String) {
        connection.prepareStatement(
            """
            UPDATE users SET trust_score = LEAST(1.0, GREATEST(0.0,
                LN(1 + (
                    SELECT COUNT(*) FROM action_outcomes o
                    WHERE o.actor_user_id = users.id AND o.verified = TRUE
                )) / LN(21)
                - 0.15 * LEAST(1.0, (
                    SELECT COUNT(*) FROM content_reports r
                    JOIN social_posts p ON p.id = r.post_id
                    WHERE p.author_user_id = users.id
                ) / 10.0)
            ))
            WHERE id = ?::uuid
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, userId)
            stmt.executeUpdate()
        }
    }
}
