package com.onlygoodthings.app.data

import kotlinx.coroutines.*
import kotlin.test.*

class FeedAnnounceTest {
    @Test fun missingSocketAcknowledgementCannotKeepAConfirmedPublicationWaiting() = runBlocking {
        var attempted = false
        var stopped = false
        withTimeout(2_000) {
            announceConfirmedPost(timeoutMillis = 30) {
                attempted = true
                try { awaitCancellation() } finally { stopped = true }
            }
        }
        assertTrue(attempted)
        assertTrue(stopped)
    }

    @Test fun normalAnnouncementIsSentExactlyOnce() = runBlocking {
        var sends = 0
        announceConfirmedPost { sends++ }
        assertEquals(1, sends)
    }

    @Test fun failedAnnouncementDoesNotTurnAConfirmedPostIntoAFailedDraft() = runBlocking {
        announceConfirmedPost { error("Socket disconnected") }
    }

    @Test fun leavingTheScreenStillCancelsTheAnnouncement() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var stopped = false
        val job = launch {
            announceConfirmedPost {
                entered.complete(Unit)
                try { awaitCancellation() } finally { stopped = true }
            }
        }
        entered.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(stopped)
    }

    @Test fun callerCancellationIsNotReportedAsASuccessfulAnnouncement() = runBlocking<Unit> {
        assertFailsWith<CancellationException> {
            announceConfirmedPost { throw CancellationException("Screen closed") }
        }
    }
}
