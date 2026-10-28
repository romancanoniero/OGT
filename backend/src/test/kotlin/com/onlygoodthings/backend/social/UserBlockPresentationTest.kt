package com.onlygoodthings.backend.social
import com.onlygoodthings.shared.domain.SocialComment
import org.junit.jupiter.api.Test
import kotlin.test.*

class UserBlockPresentationTest {
    @Test fun hidingAnAuthorPreservesUnblockedDescendantsAndTheirOrder() {
        val first=SocialComment("first","post","allowed","Ana",body="Uno",createdAtEpochMs=1)
        val child=SocialComment("child","post","allowed","Ana",parentCommentId="hidden",body="Dos",createdAtEpochMs=2)
        val hidden=SocialComment("hidden","post","blocked","B",body="Oculto",createdAtEpochMs=0,replies=listOf(child))
        val rows=filterBlockedComments(listOf(first,hidden),setOf("blocked"))
        assertEquals(listOf("first","child"),rows.map { it.id });assertNull(rows[1].parentCommentId)
        assertEquals(listOf(first,hidden),filterBlockedComments(listOf(first,hidden),emptySet()))
    }
}
