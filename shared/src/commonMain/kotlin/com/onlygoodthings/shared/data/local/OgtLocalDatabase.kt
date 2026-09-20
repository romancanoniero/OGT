package com.onlygoodthings.shared.data.local

import com.onlygoodthings.shared.domain.AuthorKind
import com.onlygoodthings.shared.domain.HonorChannel
import com.onlygoodthings.shared.domain.HonorMentionView
import com.onlygoodthings.shared.domain.HonorStatus
import com.onlygoodthings.shared.domain.MentionCandidate
import com.onlygoodthings.shared.domain.mentionHandle
import com.onlygoodthings.shared.domain.normalizeHonorContact
import com.onlygoodthings.shared.domain.searchMentions
import com.onlygoodthings.shared.domain.titleCasePersonName
import com.onlygoodthings.shared.domain.CompanyProfile
import com.onlygoodthings.shared.domain.FeedCardKind
import com.onlygoodthings.shared.domain.FeedEventKind
import com.onlygoodthings.shared.domain.FeedMode
import com.onlygoodthings.shared.domain.FeedTopicFamily
import com.onlygoodthings.shared.domain.feedCardKind
import com.onlygoodthings.shared.domain.gatheringWhenWhere
import com.onlygoodthings.shared.domain.GeoMath
import com.onlygoodthings.shared.domain.GeoPoint
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.postDeepLink
import com.onlygoodthings.shared.domain.ParkingHandoff
import com.onlygoodthings.shared.domain.ParkingRules
import com.onlygoodthings.shared.domain.ParkingSpot
import com.onlygoodthings.shared.domain.ParkingStatus
import com.onlygoodthings.shared.domain.PostMediaItem
import com.onlygoodthings.shared.domain.PostPersonRole
import com.onlygoodthings.shared.domain.SocialComment
import com.onlygoodthings.shared.domain.SocialLiveCounters
import com.onlygoodthings.shared.domain.AnimalListingDto
import com.onlygoodthings.shared.domain.OgtCrmDefaults
import com.onlygoodthings.shared.domain.SocialPost
import com.onlygoodthings.shared.domain.UserProfile
import com.onlygoodthings.shared.feed.FeedCandidate
import com.onlygoodthings.shared.feed.FeedEventSignal
import com.onlygoodthings.shared.feed.FeedRanker
import com.onlygoodthings.shared.feed.ViewerContext
import com.onlygoodthings.shared.feed.feedTopicFamily
import com.onlygoodthings.shared.realtime.currentEpochMs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Lo que acabo de publicar queda pinneado arriba del río durante esta ventana. */
private const val OWN_PIN_WINDOW_MS = 2 * 3_600_000L

/**
 * Catálogo en memoria que espeja las tablas de `01_init.sql`.
 * Sirve para preview Compose; Postgres sigue siendo la fuente de verdad de plataforma.
 */
class OgtLocalDatabase {
    val users = mutableListOf<LocalUser>()
    val companies = mutableListOf<LocalCompany>()
    val companyAdmins = mutableListOf<Pair<String, String>>()
    val parkingSpots = mutableListOf<LocalParkingSpot>()
    val parkingInterests = mutableListOf<LocalParkingInterest>()
    val parkingHandoffs = mutableListOf<LocalParkingHandoff>()
    var parkingEpoch: Int = 0
        private set

    fun bumpParking() {
        parkingEpoch += 1
    }

    /** Proyección WS: un lugar nuevo o actualizado desde el árbol de sockets. */
    fun upsertRemoteSpot(spot: ParkingSpot) {
        val row = LocalParkingSpot(
            id = spot.id,
            ownerUserId = spot.ownerUserId,
            claimedByUserId = spot.claimedByUserId,
            latitude = spot.location.latitude,
            longitude = spot.location.longitude,
            status = spot.status,
            version = spot.version,
            address = spot.address ?: spot.notes.orEmpty(),
            vehicleLabel = spot.vehicleLabel.orEmpty(),
            etaSeconds = spot.etaSeconds,
            distanceMeters = spot.distanceMeters,
            rewardPoints = spot.rewardPoints,
            notes = spot.notes,
            expiresAtEpochMs = spot.expiresAtEpochMs,
            ownerEtaSeconds = spot.ownerEtaSeconds,
            interestClosesAtEpochMs = spot.interestClosesAtEpochMs ?: 0L,
            ownerWaitDeadlineAtEpochMs = spot.ownerWaitDeadlineAtEpochMs ?: 0L,
            leftoverOpen = spot.leftoverOpen,
            matchingResolved = spot.leftoverOpen || spot.status != ParkingStatus.AVAILABLE,
        )
        val idx = parkingSpots.indexOfFirst { it.id == spot.id }
        if (idx >= 0) parkingSpots[idx] = row else parkingSpots += row
        bumpParking()
    }

    fun expireDueParking(nowEpochMs: Long) {
        var changed = false
        parkingSpots.forEachIndexed { idx, spot ->
            val ttlDue = spot.expiresAtEpochMs > 0L && spot.expiresAtEpochMs <= nowEpochMs
            val waitDue = spot.ownerWaitDeadlineAtEpochMs > 0L &&
                spot.ownerWaitDeadlineAtEpochMs <= nowEpochMs
            if (spot.status in listOf(ParkingStatus.AVAILABLE, ParkingStatus.CLAIMED) && (ttlDue || waitDue)) {
                parkingSpots[idx] = spot.copy(status = ParkingStatus.EXPIRED, version = spot.version + 1)
                changed = true
            }
        }
        if (changed) bumpParking()
    }
    val posts = mutableListOf<LocalSocialPost>()
    var feedEpoch: Int = 0
        private set
    private val _feedTick = MutableStateFlow(0)
    /** Tick Compose-observable: el río se recarga al publicar o al llegar un post por socket. */
    val feedTick: StateFlow<Int> = _feedTick.asStateFlow()

    fun bumpFeed() {
        feedEpoch += 1
        _feedTick.value += 1
    }

    private val _socialTick = MutableStateFlow(0)
    /** Tick Compose-observable: comentarios y contadores en vivo (sockets o local). */
    val socialTick: StateFlow<Int> = _socialTick.asStateFlow()

    fun bumpSocial() {
        _socialTick.value += 1
    }

    fun socialCountersOf(postId: String): SocialLiveCounters? {
        val post = posts.firstOrNull { it.id == postId } ?: return null
        return SocialLiveCounters(
            id = post.id,
            commentCount = post.commentCount,
            impactCount = post.impactCount,
            heartCount = post.heartCount,
        )
    }

    /** Aplica un nodo social: inserta el post si es nuevo o actualiza contadores. */
    fun applySocialLive(live: SocialLiveCounters): Boolean {
        val idx = posts.indexOfFirst { it.id == live.id }
        if (idx < 0) {
            if (!live.canIngest) return false
            posts += LocalSocialPost(
                id = live.id,
                authorKind = AuthorKind.USER,
                authorUserId = live.authorUserId,
                authorCompanyId = null,
                place = live.place.orEmpty(),
                timeLabel = "Ahora",
                tag = live.tag?.trim().orEmpty().ifBlank { "Comunidad" },
                body = live.body!!.trim(),
                impactCount = live.impactCount,
                commentCount = live.commentCount,
                isStory = false,
                storyLabel = null,
                createdAtEpochMs = live.createdAtEpochMs.takeIf { it > 0L } ?: currentEpochMs(),
                listingId = live.listingId,
                honoreeName = live.honoreeName,
                heartCount = live.heartCount,
            )
            bumpFeed()
            bumpSocial()
            return true
        }
        val current = posts[idx]
        val next = current.copy(
            commentCount = maxOf(current.commentCount, live.commentCount),
            impactCount = maxOf(current.impactCount, live.impactCount),
            heartCount = maxOf(current.heartCount, live.heartCount),
        )
        if (next == current) return false
        posts[idx] = next
        bumpSocial()
        return true
    }

    /** Marca un posteo recién grabado para que el Feed lo recargue. */
    fun ingestPublishedPost(post: LocalSocialPost) {
        if (posts.none { it.id == post.id }) posts += post
        val authorId = post.authorUserId
        if (!authorId.isNullOrBlank() &&
            postPeople.none { it.postId == post.id && it.userId == authorId && it.role == PostPersonRole.AUTHOR }
        ) {
            postPeople += LocalPostPerson(post.id, authorId, PostPersonRole.AUTHOR)
        }
        if (!authorId.isNullOrBlank()) {
            recordFeedEvent(authorId, post.id, FeedEventKind.SHARE)
        }
        bumpFeed()
    }

    fun toSocialLive(post: LocalSocialPost): SocialLiveCounters = SocialLiveCounters(
        id = post.id,
        commentCount = post.commentCount,
        impactCount = post.impactCount,
        heartCount = post.heartCount,
        authorUserId = post.authorUserId,
        body = post.body,
        tag = post.tag,
        place = post.place,
        createdAtEpochMs = post.createdAtEpochMs,
        listingId = post.listingId,
        honoreeName = post.honoreeName,
    )

    /** Inserta un comentario remoto sin duplicar el que ya publicamos acá. */
    fun applySocialComment(comment: SocialComment): Boolean {
        if (comments.any { it.id == comment.id }) return false
        comments += LocalComment(
            id = comment.id,
            postId = comment.postId,
            authorUserId = comment.authorUserId,
            body = comment.body,
            timeLabel = "Ahora",
            parentCommentId = comment.parentCommentId,
        )
        val idx = posts.indexOfFirst { it.id == comment.postId }
        if (idx >= 0) {
            val current = posts[idx]
            val count = comments.count { it.postId == comment.postId }
            if (count > current.commentCount) {
                posts[idx] = current.copy(commentCount = count)
            }
        }
        bumpSocial()
        return true
    }

    private var liveNeighborIssued = false

    /** Simula un post remoto de otro vecino. Idempotente por sesión. */
    fun publishLiveFromNeighbor(viewerId: String): LocalSocialPost? {
        if (liveNeighborIssued) return null
        val author = userOrNull(OgtIds.Sofia)?.takeIf { it.id != viewerId }
            ?: users.firstOrNull { it.id != viewerId }
            ?: return null
        if (posts.any { it.id == "post-live-${author.id}" }) return null
        liveNeighborIssued = true
        val now = currentEpochMs()
        val post = LocalSocialPost(
            id = "post-live-${author.id}",
            authorKind = AuthorKind.USER,
            authorUserId = author.id,
            authorCompanyId = null,
            place = author.barrio,
            timeLabel = "Ahora",
            tag = "Huerta comunitaria",
            body = "Dejé plantines de albahaca en la plaza. Si pasan, lleven frasco de vidrio.",
            impactCount = 0,
            commentCount = 0,
            isStory = false,
            storyLabel = null,
            createdAtEpochMs = now,
        )
        posts += post
        postMedia += LocalPostMedia(
            id = "${post.id}-m0",
            postId = post.id,
            kind = MediaKind.IMAGE,
            url = "asset://feed_photo_arboles",
            sortOrder = 0,
            assetKey = "feed_photo_arboles",
            altText = post.tag,
        )
        bumpFeed()
        return post
    }

    val comments = mutableListOf<LocalComment>()
    val adoptRequests = mutableListOf<LocalAdoptRequest>()
    val sightings = mutableListOf<LocalSighting>()
    var lostEpoch: Int = 0
        private set
    fun bumpLost() {
        lostEpoch += 1
    }
    var inboxEpoch: Int = 0
        private set
    fun bumpInbox() {
        inboxEpoch += 1
    }
    val animals = mutableListOf<LocalAnimalListing>()
    val causes = mutableListOf<LocalCause>()
    val skillTags = mutableListOf<LocalSkillTag>()
    val userSkills = mutableListOf<LocalUserSkill>()
    val matches = mutableListOf<LocalTimebankMatch>()
    val helpExchanges = mutableListOf<LocalHelpExchange>()
    val messages = mutableListOf<LocalMessage>()
    val campaigns = mutableListOf<LocalCampaign>()
    val notifications = mutableListOf<LocalNotification>()
    val karma = mutableListOf<LocalKarmaEntry>()
    val rewards = mutableListOf<LocalReward>()
    val mapPins = mutableListOf<LocalMapPin>()
    val stories = mutableListOf<LocalStory>()
    val inbox = mutableListOf<LocalInboxThread>()
    val inviteContacts = mutableListOf<LocalInviteContact>()
    val sponsors = mutableListOf<LocalSponsor>()
    val follows = mutableListOf<LocalFollow>()
    val postPeople = mutableListOf<LocalPostPerson>()
    val honors = mutableListOf<LocalHonorMention>()
    val feedEvents = mutableListOf<LocalFeedEvent>()
    val postMedia = mutableListOf<LocalPostMedia>()
    var noticeEpoch: Int = 0
        private set
    private var honorSeq = 0

    fun bumpNotices() {
        noticeEpoch += 1
    }

    fun user(id: String): LocalUser = users.first { it.id == id }

    fun userOrNull(id: String?): LocalUser? = id?.let { uid -> users.firstOrNull { it.id == uid } }

    fun company(id: String): LocalCompany = companies.first { it.id == id }

    fun post(id: String): LocalSocialPost = posts.first { it.id == id }

    fun match(id: String): LocalTimebankMatch = matches.first { it.id == id }

    fun feedPosts(): List<LocalSocialPost> = posts.filter { !it.isStory }

    fun followedIds(userId: String): Set<String> =
        follows.filter { it.followerId == userId }.map { it.followedId }.toSet()

    fun toMention(user: LocalUser): MentionCandidate = MentionCandidate(
        userId = user.id,
        displayName = user.displayName,
        handle = mentionHandle(user.displayName),
    )

    fun toMention(honor: LocalHonorMention): MentionCandidate = MentionCandidate(
        userId = honor.claimedUserId,
        displayName = honorCreditName(honor),
        handle = mentionHandle(honor.givenName),
        honorId = honor.id,
    )

    fun honorByToken(token: String): LocalHonorMention? =
        honors.firstOrNull { it.claimToken == token }

    fun upsertHonorFromRemote(view: HonorMentionView): LocalHonorMention {
        val existing = honorByToken(view.claimToken)
        val row = (existing ?: LocalHonorMention(
            id = view.id.ifBlank { "honor-${view.claimToken}" },
            issuerUserId = "",
            givenName = view.givenName,
            channel = HonorChannel.WHATSAPP,
            contact = "",
            claimToken = view.claimToken,
            createdAtEpochMs = currentEpochMs(),
        )).copy(
            givenName = view.givenName,
            status = view.status,
            claimedUserId = view.claimedUserId ?: existing?.claimedUserId,
            postId = view.postId ?: existing?.postId,
            issuerName = view.issuerName ?: existing?.issuerName,
            claimedAtEpochMs = if (view.status == HonorStatus.CLAIMED) currentEpochMs() else existing?.claimedAtEpochMs ?: 0L,
        )
        val idx = honors.indexOfFirst { it.claimToken == view.claimToken }
        if (idx >= 0) honors[idx] = row else honors += row
        return row
    }

    fun honorCreditName(honor: LocalHonorMention): String =
        honor.claimedUserId?.let { userOrNull(it)?.displayName } ?: honor.givenName

    fun issueHonor(
        issuerId: String,
        givenName: String,
        channel: HonorChannel,
        contact: String,
        nowEpochMs: Long = currentEpochMs(),
    ): LocalHonorMention {
        val name = titleCasePersonName(givenName.trim())
        require(name.isNotEmpty()) { "La mención de honor necesita un nombre." }
        val normalized = normalizeHonorContact(channel, contact)
        honorSeq += 1
        val token = "${mentionHandle(name).take(8)}${nowEpochMs.toString(36)}$honorSeq"
        val honor = LocalHonorMention(
            id = "honor-$token",
            issuerUserId = issuerId,
            givenName = name,
            channel = channel,
            contact = normalized,
            claimToken = token,
            status = HonorStatus.PENDING,
            createdAtEpochMs = nowEpochMs,
        )
        honors += honor
        return honor
    }

    fun attachHonorToPost(postId: String, honorId: String, role: PostPersonRole) {
        val idx = honors.indexOfFirst { it.id == honorId }
        if (idx < 0) return
        honors[idx] = honors[idx].copy(postId = postId, role = role)
    }

    /**
     * Reivindica el crédito. Si la mención ya estaba pegada a un post, suma [LocalPostPerson].
     * El servidor decide en producción; acá el token es la prueba.
     */
    fun claimHonor(token: String, claimantId: String, nowEpochMs: Long = currentEpochMs()): LocalHonorMention? {
        val idx = honors.indexOfFirst { it.claimToken == token }
        if (idx < 0) return null
        val honor = honors[idx]
        if (honor.status == HonorStatus.CLAIMED) {
            return honor.takeIf { it.claimedUserId == claimantId }
        }
        if (honor.status != HonorStatus.PENDING) return null
        val claimed = honor.copy(
            status = HonorStatus.CLAIMED,
            claimedUserId = claimantId,
            claimedAtEpochMs = nowEpochMs,
        )
        honors[idx] = claimed
        val postId = claimed.postId
        val role = claimed.role
        if (postId != null && role != null && postPeople.none { it.postId == postId && it.userId == claimantId && it.role == role }) {
            postPeople += LocalPostPerson(postId, claimantId, role)
        }
        bumpFeed()
        return claimed
    }

    /** Honor pendiente cuyo contacto coincide con la persona que acaba de entrar. No el emisor. */
    fun exportHonors(): String = honorJson.encodeToString(honors.toList())

    fun importHonors(raw: String) {
        if (raw.isBlank()) return
        val rows = runCatching { honorJson.decodeFromString<List<LocalHonorMention>>(raw) }.getOrDefault(emptyList())
        if (rows.isEmpty()) return
        honors.clear()
        honors += rows
    }

    fun honorOfPost(postId: String, role: PostPersonRole = PostPersonRole.PROTAGONIST): LocalHonorMention? =
        honors.firstOrNull { it.postId == postId && it.role == role }

    fun creditName(post: LocalSocialPost): String {
        honorOfPost(post.id)?.let { return honorCreditName(it) }
        post.honoreeName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val heroId = postPeople.firstOrNull { it.postId == post.id && it.role == PostPersonRole.PROTAGONIST }?.userId
        return userOrNull(heroId)?.displayName ?: authorName(post)
    }

    fun creditPending(post: LocalSocialPost): Boolean =
        honorOfPost(post.id)?.status == HonorStatus.PENDING

    fun inboxOf(userId: String): List<LocalNotification> =
        notifications.filter { it.recipientUserId == null || it.recipientUserId == userId }

    fun unreadNoticeCount(userId: String): Int = inboxOf(userId).count { !it.read }

    fun markNoticeRead(id: String) {
        val idx = notifications.indexOfFirst { it.id == id }
        if (idx < 0 || notifications[idx].read) return
        notifications[idx] = notifications[idx].copy(read = true)
        bumpNotices()
    }

    fun markInboxRead(userId: String) {
        var changed = false
        notifications.forEachIndexed { idx, notice ->
            val mine = notice.recipientUserId == null || notice.recipientUserId == userId
            if (mine && !notice.read) {
                notifications[idx] = notice.copy(read = true)
                changed = true
            }
        }
        if (changed) bumpNotices()
    }

    /**
     * Aviso a usuarias referenciadas (protagonista / acompañó).
     * No notifica a quien publica ni crea usuarios fantasma.
     */
    fun notifyMentionedUsers(
        actorId: String,
        postId: String,
        recipientIds: List<String>,
        nowEpochMs: Long = currentEpochMs(),
    ): List<LocalNotification> {
        val actor = userOrNull(actorId) ?: return emptyList()
        val created = mutableListOf<LocalNotification>()
        recipientIds.distinct().filter { it.isNotBlank() && it != actorId }.forEach { rid ->
            if (notifications.any { it.kind == "MENTION" && it.postId == postId && it.recipientUserId == rid }) return@forEach
            val notice = LocalNotification(
                id = "n-mention-$postId-$rid",
                kind = "MENTION",
                title = "${actor.displayName} te mencionó",
                body = "Contó algo bueno que hiciste. Tocá para verlo en la comunidad.",
                timeLabel = "Ahora",
                urgent = false,
                recipientUserId = rid,
                postId = postId,
                deepLink = postDeepLink(postId),
                createdAtEpochMs = nowEpochMs,
            )
            notifications.add(0, notice)
            created += notice
        }
        if (created.isNotEmpty()) bumpNotices()
        return created
    }

    fun pendingHonorForUser(user: LocalUser): LocalHonorMention? {
        val email = normalizeHonorContact(HonorChannel.EMAIL, user.email.orEmpty())
        val phone = normalizeHonorContact(HonorChannel.SMS, user.phoneE164.orEmpty())
        return honors.firstOrNull { honor ->
            honor.status == HonorStatus.PENDING &&
                honor.issuerUserId != user.id &&
                when (honor.channel) {
                    HonorChannel.EMAIL -> email.isNotEmpty() && honor.contact == email
                    HonorChannel.WHATSAPP, HonorChannel.SMS -> phone.isNotEmpty() && honor.contact == phone
                }
        }
    }

    /** Grafo de confianza + typeahead @, como Instagram. */
    fun mentionPool(viewerId: String): List<MentionCandidate> {
        val graph = followedIds(viewerId) +
            follows.filter { it.followedId == viewerId }.map { it.followerId }
        val ids = (graph.ifEmpty { users.map { it.id } }).filter { it != viewerId }.distinct()
        return ids.mapNotNull { userOrNull(it) }.map(::toMention)
    }

    fun findMentions(viewerId: String, query: String, limit: Int = 8): List<MentionCandidate> =
        searchMentions(query, mentionPool(viewerId), limit)

    fun isFollowing(followerId: String, followedId: String): Boolean =
        follows.any { it.followerId == followerId && it.followedId == followedId }

    fun toggleFollow(followerId: String, followedId: String) {
        if (followerId == followedId) return
        val idx = follows.indexOfFirst { it.followerId == followerId && it.followedId == followedId }
        if (idx >= 0) follows.removeAt(idx) else follows += LocalFollow(followerId, followedId)
    }

    fun postsOfAuthor(userId: String): List<LocalSocialPost> =
        feedPosts().filter { it.authorUserId == userId }.sortedByDescending { it.createdAtEpochMs }

    fun followerCount(userId: String): Int = follows.count { it.followedId == userId }

    fun viewerAliases(viewerId: String): Set<String> {
        val user = userOrNull(viewerId)
        return buildSet {
            add(viewerId)
            user?.aliases()?.let { addAll(it) }
        }
    }

    fun isOwnPost(post: LocalSocialPost, aliases: Set<String> = emptySet()): Boolean {
        val ids = aliases.ifEmpty { viewerAliases(post.authorUserId.orEmpty()) }
        if (post.authorUserId in ids) return true
        if (protagonistOf(post) in ids) return true
        val listing = post.listingId?.let { id -> animals.firstOrNull { it.id == id } }
        return listing != null && listing.reporterUserId in ids
    }

    fun claimPublishedAnimals(aliases: Set<String>, localUserId: String) {
        val known = aliases.filter { it.isNotBlank() }.toSet()
        if (known.isEmpty() || localUserId.isBlank()) return
        var changed = false
        animals.indices.forEach { i ->
            if (animals[i].reporterUserId in known && animals[i].reporterUserId != localUserId) {
                animals[i] = animals[i].copy(reporterUserId = localUserId)
                changed = true
            }
        }
        posts.indices.forEach { i ->
            if (posts[i].authorUserId in known && posts[i].authorUserId != localUserId) {
                posts[i] = posts[i].copy(authorUserId = localUserId)
                changed = true
            }
        }
        if (changed) {
            bumpLost()
            bumpFeed()
            bumpSocial()
        }
    }

    fun unpublishedAnimalPosts(): List<LocalSocialPost> =
        posts.filter { it.id.startsWith("post-pub-") && it.listingId != null }

    fun protagonistOf(post: LocalSocialPost): String? =
        postPeople.firstOrNull { it.postId == post.id && it.role == PostPersonRole.PROTAGONIST }?.userId
            ?: post.authorUserId

    fun rankedFeed(
        userId: String,
        mode: FeedMode = FeedMode.HOME,
        nowEpochMs: Long = currentEpochMs(),
        offset: Int = 0,
        limit: Int = 20,
        family: com.onlygoodthings.shared.domain.FeedTopicFamily? = null,
    ): List<LocalSocialPost> {
        val viewerUser = userOrNull(userId)
        val candidates = feedPosts().map { post ->
            FeedCandidate(
                postId = post.id,
                authorKey = post.authorUserId ?: "company:${post.authorCompanyId.orEmpty()}",
                authorUserId = post.authorUserId,
                protagonistUserId = protagonistOf(post),
                topic = post.tag,
                createdAtEpochMs = post.createdAtEpochMs,
                impactCount = post.impactCount,
                commentCount = post.commentCount,
                latitude = viewerUser?.let { postPlaceOrAuthorLat(post) },
                longitude = viewerUser?.let { postPlaceOrAuthorLng(post) },
                sourceUrl = post.sourceUrl,
            )
        }
        val viewer = ViewerContext(
            userId = userId,
            followedIds = followedIds(userId),
            events = feedEvents.filter { it.viewerId == userId }.map { event ->
                val origin = posts.firstOrNull { it.id == event.postId }
                FeedEventSignal(
                    postId = event.postId,
                    authorUserId = origin?.authorUserId,
                    topic = origin?.tag.orEmpty(),
                    kind = event.kind,
                )
            },
            latitude = viewerUser?.latitude,
            longitude = viewerUser?.longitude,
        )
        val ranked = FeedRanker.rank(candidates, viewer, mode, nowEpochMs, offset, limit, family)
        val byId = feedPosts().associateBy { it.id }
        return ranked.mapNotNull { byId[it.postId] }
    }

    /**
     * Río que ve esta persona: lo propio reciente va primero;
     * lo ajeno entra solo si el ranker lo dejaría en la página.
     */
    fun visibleFeed(
        viewerId: String,
        mode: FeedMode = FeedMode.HOME,
        family: FeedTopicFamily? = null,
        nowEpochMs: Long = currentEpochMs(),
        limit: Int = 40,
    ): List<LocalSocialPost> {
        val ranked = rankedFeed(viewerId, mode, nowEpochMs, 0, limit, family)
        val aliases = viewerAliases(viewerId)
        val pinned = feedPosts()
            .filter { post ->
                isOwnPost(post, aliases) &&
                    (post.listingId != null || nowEpochMs - post.createdAtEpochMs <= OWN_PIN_WINDOW_MS) &&
                    shouldShowInFeed(viewerId, post, mode, family, nowEpochMs)
            }
            .sortedByDescending { it.createdAtEpochMs }
        return (pinned + ranked).distinctBy { it.id }
    }

    fun shouldShowInFeed(
        viewerId: String,
        post: LocalSocialPost,
        mode: FeedMode = FeedMode.HOME,
        family: FeedTopicFamily? = null,
        nowEpochMs: Long = currentEpochMs(),
    ): Boolean {
        if (post.isStory) return false
        if (feedEvents.any { it.viewerId == viewerId && it.postId == post.id && it.kind == FeedEventKind.HIDE }) {
            return false
        }
        if (family != null && feedTopicFamily(post.tag, post.sourceUrl) != family) return false
        if (isOwnPost(post, viewerAliases(viewerId))) return true
        if (mode == FeedMode.FOLLOWING) {
            val mine = setOfNotNull(post.authorUserId, protagonistOf(post))
            return mine.any { it in followedIds(viewerId) }
        }
        return rankedFeed(viewerId, mode, nowEpochMs, 0, 40, family).any { it.id == post.id }
    }

    /**
     * Un post canónico por tipo de card para revisar fichas.
     * No pagina el río completo: perdido, adopción, historia, noticia y comunidad.
     */
    fun reviewCatalogFeed(family: FeedTopicFamily? = null): List<LocalSocialPost> {
        val preferred = listOf(
            OgtIds.PostOliver,
            OgtIds.PostLuna,
            OgtIds.PostPatitas,
            "news-01",
            OgtIds.PostTaller,
        )
        val available = feedPosts()
        val picks = linkedMapOf<FeedCardKind, LocalSocialPost>()
        val ordered = preferred.mapNotNull { id -> available.firstOrNull { it.id == id } } +
            available.sortedByDescending { it.createdAtEpochMs }
        for (post in ordered) {
            val listing = post.listingId?.let { id -> animals.firstOrNull { it.id == id } }
            val kind = feedCardKind(post.tag, listing?.kind, post.sourceUrl)
            if (kind !in picks) picks[kind] = post
            if (picks.size == FeedCardKind.entries.size) break
        }
        val catalog = FeedCardKind.entries.mapNotNull { picks[it] }
        val published = available
            .filter { it.id.startsWith("post-pub-") }
            .sortedByDescending { it.createdAtEpochMs }
        val merged = (published + catalog).distinctBy { it.id }
        if (family == null) return merged
        return merged.filter { feedTopicFamily(it.tag, it.sourceUrl) == family }
    }

    fun isDiscovery(userId: String, post: LocalSocialPost): Boolean {
        val mine = setOfNotNull(post.authorUserId, protagonistOf(post))
        val followed = followedIds(userId)
        return userId !in mine && mine.none { it in followed }
    }

    fun recordFeedEvent(viewerId: String, postId: String, kind: FeedEventKind, dwellMs: Int? = null) {
        feedEvents += LocalFeedEvent(
            id = "ev-${feedEvents.size + 1}",
            viewerId = viewerId,
            postId = postId,
            kind = kind,
            createdAtEpochMs = currentEpochMs(),
            dwellMs = dwellMs,
        )
    }

    private fun postPlaceOrAuthorLat(post: LocalSocialPost): Double? = userOrNull(post.authorUserId)?.latitude

    private fun postPlaceOrAuthorLng(post: LocalSocialPost): Double? = userOrNull(post.authorUserId)?.longitude

    /**
     * Quien confirma que va a la convocatoria avisa a su libreta de contactos.
     * Los contactos pueden enterarse y sumarse.
     */
    fun notifyContactsImGoing(viewer: LocalUser, post: LocalSocialPost): Int {
        val contacts = inviteContacts.toList()
        if (contacts.isEmpty()) return 0
        val names = contacts.joinToString { it.name }
        val whenWhere = gatheringWhenWhere(post.place, post.eventStartsAtEpochMs, currentEpochMs())
            ?: post.place
        notifications += LocalNotification(
            id = "n-rsvp-${notifications.size + 1}",
            kind = "RSVP",
            title = "${viewer.displayName} va a ${post.tag}",
            body = "${viewer.displayName} confirmó asistencia: $whenWhere. Avisamos a $names. ¿Te sumás?",
            timeLabel = "Ahora",
            urgent = false,
        )
        recordFeedEvent(viewer.id, post.id, FeedEventKind.SHARE)
        return contacts.size
    }

    fun commentsOf(postId: String): List<LocalComment> = comments.filter { it.postId == postId }

    fun clapComment(viewerId: String, commentId: String): LocalComment? {
        val idx = comments.indexOfFirst { it.id == commentId }
        if (idx < 0) return null
        val row = comments[idx]
        if (viewerId in row.clapUserIds) return row
        val who = row.clapUserIds + viewerId
        val updated = row.copy(clapCount = who.size, clapUserIds = who)
        comments[idx] = updated
        return updated
    }

    fun clappersOf(commentId: String): List<LocalUser> {
        val row = comments.firstOrNull { it.id == commentId } ?: return emptyList()
        return row.clapUserIds.mapNotNull(::userOrNull)
    }

    /** Publica un comentario si hay texto. Vacío no envía. */
    fun addComment(viewer: LocalUser, postId: String, body: String): LocalComment? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null
        val row = LocalComment(
            id = "c-${comments.size + 1}",
            postId = postId,
            authorUserId = viewer.id,
            body = trimmed,
            timeLabel = "Ahora",
            parentCommentId = null,
        )
        comments += row
        val idx = posts.indexOfFirst { it.id == postId }
        if (idx >= 0) {
            val current = posts[idx]
            posts[idx] = current.copy(commentCount = current.commentCount + 1)
        }
        recordFeedEvent(viewer.id, postId, FeedEventKind.COMMENT)
        bumpSocial()
        return row
    }

    /**
     * Postula a una adopción: guarda el formulario, abre hilo con quien publicó
     * y deja el documento en su casilla para responder.
     */
    fun addAdoptRequest(
        viewer: LocalUser,
        postId: String,
        listingId: String?,
        applicantName: String,
        phone: String,
        address: String,
        homeKind: String,
        otherPets: String,
        otherSpecies: String,
        household: String,
        hoursAway: String,
        experience: String,
        motive: String,
    ): LocalAdoptRequest? {
        if (adoptRequests.any { it.postId == postId && it.applicantUserId == viewer.id }) return null
        val post = posts.firstOrNull { it.id == postId } ?: return null
        val posterId = post.authorUserId ?: return null
        if (posterId == viewer.id) return null
        val listing = listingId?.let { id -> animals.firstOrNull { it.id == id } }
        val pet = listing?.title?.ifBlank { null } ?: post.tag
        val matchId = "match-adopt-$postId-${viewer.id}"
        val row = LocalAdoptRequest(
            id = "adopt-${adoptRequests.size + 1}",
            postId = postId,
            listingId = listingId,
            applicantUserId = viewer.id,
            applicantName = applicantName.trim(),
            phone = phone.trim(),
            address = address.trim(),
            homeKind = homeKind,
            otherPets = otherPets,
            otherSpecies = otherSpecies.trim(),
            household = household.trim(),
            hoursAway = hoursAway,
            experience = experience,
            motive = motive.trim(),
            matchId = matchId,
            createdAtEpochMs = currentEpochMs(),
        )
        adoptRequests += row
        matches += LocalTimebankMatch(
            id = matchId,
            requesterId = viewer.id,
            providerId = posterId,
            offeredLabel = "Adopción",
            requestedLabel = pet,
            matchPercent = 100,
            distanceLabel = address.trim(),
            quote = motive.trim(),
            chatEnabled = true,
        )
        messages += LocalMessage(
            id = "m-adopt-${row.id}",
            matchId = matchId,
            senderId = viewer.id,
            body = "Envié el formulario para adoptar $pet.",
            timeLabel = "Ahora",
            adoptRequestId = row.id,
        )
        inbox.add(
            0,
            LocalInboxThread(
                id = "in-adopt-${row.id}",
                peerUserId = posterId,
                title = user(posterId).displayName,
                tag = "Postulación · $pet",
                preview = "Formulario de adopción enviado",
                timeLabel = "Ahora",
                matchId = matchId,
                group = false,
            ),
        )
        notifications.add(
            0,
            LocalNotification(
                id = "n-adopt-${row.id}",
                kind = "ADOPT",
                title = "Nueva postulación de adopción",
                body = "${viewer.displayName} quiere adoptar $pet. El formulario está en Mensajes.",
                timeLabel = "Ahora",
                urgent = false,
            ),
        )
        bumpInbox()
        return row
    }

    fun adoptRequestOfMatch(matchId: String): LocalAdoptRequest? =
        adoptRequests.firstOrNull { it.matchId == matchId }

    fun sightingsOf(listingId: String): List<LocalSighting> =
        sightings.filter { it.listingId == listingId && it.status != "DISMISSED" }
            .sortedByDescending { it.createdAtEpochMs }

    fun sightingOfMatch(matchId: String): LocalSighting? =
        sightings.firstOrNull { it.matchId == matchId }

    fun mySighting(listingId: String, userId: String): LocalSighting? =
        sightings.firstOrNull { it.listingId == listingId && it.userId == userId }

    /**
     * Vecino marca un avistaje: pin en el mapa + hilo con quien publicó.
     */
    fun addSighting(
        viewer: LocalUser,
        postId: String,
        listingId: String,
        note: String,
        latitude: Double,
        longitude: Double,
        timeLabel: String = "Ahora",
        createdAtEpochMs: Long = currentEpochMs(),
    ): LocalSighting? {
        val existing = mySighting(listingId, viewer.id)
        if (existing != null) return existing
        val post = posts.firstOrNull { it.id == postId } ?: return null
        val listing = animals.firstOrNull { it.id == listingId } ?: return null
        if (listing.resolved) return null
        val posterId = listing.reporterUserId
        if (posterId == viewer.id) return null
        val pet = listing.petName.ifBlank { listing.title }
        val matchId = "match-lost-$listingId-${viewer.id}"
        val row = LocalSighting(
            id = "sight-${sightings.size + 1}",
            listingId = listingId,
            postId = postId,
            userId = viewer.id,
            note = note.trim(),
            latitude = latitude,
            longitude = longitude,
            status = "SEEN",
            matchId = matchId,
            timeLabel = timeLabel,
            createdAtEpochMs = createdAtEpochMs,
        )
        sightings += row
        if (matches.none { it.id == matchId }) {
            matches += LocalTimebankMatch(
                id = matchId,
                requesterId = viewer.id,
                providerId = posterId,
                offeredLabel = "Avistaje",
                requestedLabel = pet,
                matchPercent = 100,
                distanceLabel = listing.lastSeenPlace.ifBlank { listing.place },
                quote = row.note,
                chatEnabled = true,
            )
        }
        messages += LocalMessage(
            id = "m-sight-${row.id}",
            matchId = matchId,
            senderId = viewer.id,
            body = row.note.ifBlank { "Lo vi cerca." },
            timeLabel = timeLabel,
            sightingId = row.id,
        )
        inbox.add(
            0,
            LocalInboxThread(
                id = "in-sight-${row.id}",
                peerUserId = posterId,
                title = user(posterId).displayName,
                tag = "Avistaje · $pet",
                preview = row.note.ifBlank { "Lo vi cerca." },
                timeLabel = timeLabel,
                matchId = matchId,
                group = false,
            ),
        )
        notifications.add(
            0,
            LocalNotification(
                id = "n-sight-${row.id}",
                kind = "LOST",
                title = "Creen haber visto a $pet",
                body = "${viewer.displayName}: ${row.note.ifBlank { "Lo vi cerca." }}",
                timeLabel = timeLabel,
                urgent = true,
            ),
        )
        if (row.wroteNote()) addComment(viewer, post.id, row.note)
        bumpLost()
        bumpInbox()
        return row
    }

    /** Solo quien publicó cierra la alerta. */
    fun resolveLostListing(viewer: LocalUser, listingId: String): LocalAnimalListing? {
        val idx = animals.indexOfFirst { it.id == listingId }
        if (idx < 0) return null
        val row = animals[idx]
        if (row.reporterUserId != viewer.id || row.resolved) return null
        val updated = row.copy(resolved = true)
        animals[idx] = updated
        val pet = row.petName.ifBlank { row.title }
        notifications.add(
            0,
            LocalNotification(
                id = "n-found-$listingId",
                kind = "FOUND",
                title = "$pet ya está en casa",
                body = "${viewer.displayName} cerró la alerta. Gracias a quienes marcaron un avistaje.",
                timeLabel = "Ahora",
                urgent = false,
            ),
        )
        bumpLost()
        bumpInbox()
        return updated
    }

    /**
     * Publica un aviso de mascota. Perdido exige señas y última vista;
     * adopción exige edad, carácter y barrio para que alguien pueda postular.
     */
    fun publishAnimalListing(
        author: LocalUser,
        kind: String,
        petName: String,
        species: String,
        size: String,
        description: String,
        place: String,
        marks: String = "",
        lastSeenPlace: String = "",
        alertRadiusM: Int = OgtCrmDefaults.LOST_ALERT_RADIUS_M,
        latitude: Double? = null,
        longitude: Double? = null,
        ageLabel: String = "",
        sex: String = "",
        temperament: String = "",
        vaccinated: Boolean = false,
        sterilized: Boolean = false,
        homeNeeds: String = "",
    ): LocalSocialPost? {
        val name = titleCasePersonName(petName.trim())
        val story = description.trim()
        val lost = kind == "LOST"
        if (name.isEmpty() || story.isEmpty() || species.isBlank() || size.isBlank()) return null
        if (lost && (marks.trim().isEmpty() || lastSeenPlace.trim().isEmpty())) return null
        if (!lost && (ageLabel.trim().isEmpty() || place.trim().isEmpty() || temperament.trim().isEmpty())) return null
        val now = currentEpochMs()
        val listingId = "animal-pub-$now"
        val postId = "post-pub-$now"
        val seen = lastSeenPlace.trim()
        val barrio = place.trim()
        animals += LocalAnimalListing(
            id = listingId,
            reporterUserId = author.id,
            kind = if (lost) "LOST" else "ADOPTION",
            species = species,
            size = size,
            urgency = if (lost) "HIGH" else "LOW",
            title = if (lost) "Se busca a $name" else listOf(name, ageLabel.trim()).filter { it.isNotBlank() }.joinToString(" · "),
            description = story,
            place = if (lost) seen.ifBlank { barrio } else barrio,
            alertRadiusM = if (lost) alertRadiusM else 0,
            neighborsAlerted = if (lost) 0 else 0,
            resolved = false,
            petName = name,
            marks = marks.trim(),
            lastSeenPlace = seen,
            latitude = latitude,
            longitude = longitude,
            lastSeenAtEpochMs = if (lost) now else 0L,
            ageLabel = ageLabel.trim(),
            sex = sex.trim(),
            temperament = temperament.trim(),
            vaccinated = vaccinated,
            sterilized = sterilized,
            homeNeeds = homeNeeds.trim(),
            createdAtEpochMs = now,
        )
        val post = LocalSocialPost(
            id = postId,
            authorKind = AuthorKind.USER,
            authorUserId = author.id,
            authorCompanyId = null,
            place = if (lost) seen.ifBlank { barrio } else barrio,
            timeLabel = "Ahora",
            tag = if (lost) "Mascota perdida" else "Adopción",
            body = story,
            impactCount = 0,
            commentCount = 0,
            isStory = false,
            storyLabel = null,
            createdAtEpochMs = now,
            listingId = listingId,
        )
        ingestPublishedPost(post)
        bumpLost()
        return post
    }

    /**
     * Actualiza una ficha de adopción propia. El autor no postula: corrige
     * nombre, historia, barrio y lo que una familia necesita para decidir.
     */
    fun updateAnimalListing(
        author: LocalUser,
        postId: String,
        petName: String,
        species: String,
        size: String,
        description: String,
        place: String,
        ageLabel: String = "",
        sex: String = "",
        temperament: String = "",
        vaccinated: Boolean = false,
        sterilized: Boolean = false,
        homeNeeds: String = "",
    ): LocalSocialPost? {
        val name = titleCasePersonName(petName.trim())
        val story = description.trim()
        val barrio = place.trim()
        if (name.isEmpty() || story.isEmpty() || species.isBlank() || size.isBlank()) return null
        if (ageLabel.trim().isEmpty() || barrio.isEmpty() || temperament.trim().isEmpty()) return null
        val postIdx = posts.indexOfFirst { it.id == postId }
        if (postIdx < 0) return null
        val post = posts[postIdx]
        if (post.authorUserId != author.id) return null
        val listingId = post.listingId ?: return null
        val animalIdx = animals.indexOfFirst { it.id == listingId }
        if (animalIdx < 0) return null
        val listing = animals[animalIdx]
        if (listing.reporterUserId != author.id || listing.kind != "ADOPTION") return null
        animals[animalIdx] = listing.copy(
            species = species,
            size = size,
            title = listOf(name, ageLabel.trim()).filter { it.isNotBlank() }.joinToString(" · "),
            description = story,
            place = barrio,
            petName = name,
            ageLabel = ageLabel.trim(),
            sex = sex.trim(),
            temperament = temperament.trim(),
            vaccinated = vaccinated,
            sterilized = sterilized,
            homeNeeds = homeNeeds.trim(),
        )
        val updated = post.copy(body = story, place = barrio)
        posts[postIdx] = updated
        bumpLost()
        bumpFeed()
        bumpSocial()
        return updated
    }

    fun addChatMessage(viewer: LocalUser, matchId: String, body: String): LocalMessage? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null
        if (matches.none { it.id == matchId }) return null
        val row = LocalMessage(
            id = "m-${messages.size + 1}",
            matchId = matchId,
            senderId = viewer.id,
            body = trimmed,
            timeLabel = "Ahora",
        )
        messages += row
        val idx = inbox.indexOfFirst { it.matchId == matchId }
        if (idx >= 0) {
            inbox[idx] = inbox[idx].copy(preview = trimmed, timeLabel = "Ahora")
        }
        bumpInbox()
        return row
    }

    /**
     * Reemplaza los IDs locales `post-pub-*` / `animal-pub-*` por los UUID
     * que devolvió Postgres, sin cambiar el autor local (Mariana sigue siendo Mariana).
     */
    fun bindServerAnimal(localPostId: String, remote: AnimalListingDto, localAuthorId: String) {
        val postIdx = posts.indexOfFirst { it.id == localPostId }
        if (postIdx < 0) {
            upsertRemoteAnimal(remote)
            return
        }
        val old = posts[postIdx]
        val oldListingId = old.listingId
        val animalIdx = oldListingId?.let { id -> animals.indexOfFirst { it.id == id } } ?: -1
        if (animalIdx >= 0) {
            animals[animalIdx] = toLocalListing(remote, localAuthorId)
        } else {
            animals += toLocalListing(remote, localAuthorId)
        }
        posts[postIdx] = old.copy(
            id = remote.postId,
            listingId = remote.listingId,
            authorUserId = localAuthorId,
            body = remote.description,
            place = remote.place,
            tag = if (remote.kind == "LOST") "Mascota perdida" else "Adopción",
        )
        replaceRemoteMedia(localPostId, remote)
        bumpLost()
        bumpFeed()
        bumpSocial()
    }

    /** Hidrata una ficha remota: si el firebase_uid es alguien del catálogo, usamos su id local. */
    fun upsertRemoteAnimal(remote: AnimalListingDto) {
        if (remote.listingId.isBlank() || remote.postId.isBlank()) return
        val reporter = users.firstOrNull {
            it.firebaseUid == remote.reporterFirebaseUid ||
                it.id == remote.reporterUserId ||
                it.firebaseUid == remote.reporterUserId
        }?.id ?: remote.reporterUserId
        val animalIdx = animals.indexOfFirst { it.id == remote.listingId }
        val row = toLocalListing(remote, reporter)
        if (animalIdx >= 0) animals[animalIdx] = row else animals += row
        val postIdx = posts.indexOfFirst { it.id == remote.postId || it.listingId == remote.listingId }
        val post = LocalSocialPost(
            id = remote.postId,
            authorKind = AuthorKind.USER,
            authorUserId = reporter,
            authorCompanyId = null,
            place = remote.place,
            timeLabel = "Ahora",
            tag = if (remote.kind == "LOST") "Mascota perdida" else "Adopción",
            body = remote.description,
            impactCount = 0,
            commentCount = 0,
            isStory = false,
            storyLabel = null,
            createdAtEpochMs = remote.createdAtEpochMs,
            listingId = remote.listingId,
        )
        if (postIdx >= 0) {
            val current = posts[postIdx]
            posts[postIdx] = post.copy(
                impactCount = maxOf(current.impactCount, post.impactCount),
                commentCount = maxOf(current.commentCount, post.commentCount),
                heartCount = current.heartCount,
            )
        } else {
            posts += post
        }
        dropLocalDraftMatching(remote, reporter)
        replaceRemoteMedia(remote.postId, remote)
        bumpLost()
        bumpFeed()
        bumpSocial()
    }

    /** Evita duplicar la ficha local `animal-pub-*` cuando ya llegó el UUID de Postgres. */
    private fun dropLocalDraftMatching(remote: AnimalListingDto, reporterId: String) {
        val aliases = viewerAliases(reporterId)
        val stale = animals.filter { listing ->
            listing.id.startsWith("animal-pub-") &&
                listing.kind == remote.kind &&
                listing.reporterUserId in aliases &&
                listing.petName.equals(remote.petName, ignoreCase = true)
        }
        stale.forEach { listing ->
            val stalePosts = posts.filter { it.listingId == listing.id }
            stalePosts.forEach { post ->
                postMedia.removeAll { it.postId == post.id }
                posts.removeAll { it.id == post.id }
            }
            animals.removeAll { it.id == listing.id }
        }
    }

    private fun toLocalListing(remote: AnimalListingDto, reporterId: String) = LocalAnimalListing(
        id = remote.listingId,
        reporterUserId = reporterId,
        kind = remote.kind,
        species = remote.species,
        size = remote.size,
        urgency = remote.urgency,
        title = remote.title,
        description = remote.description,
        place = remote.place,
        alertRadiusM = remote.alertRadiusM,
        neighborsAlerted = 0,
        resolved = remote.resolved,
        petName = remote.petName,
        marks = remote.marks,
        lastSeenPlace = remote.lastSeenPlace,
        latitude = remote.latitude,
        longitude = remote.longitude,
        lastSeenAtEpochMs = if (remote.kind == "LOST") remote.createdAtEpochMs else 0L,
        ageLabel = remote.ageLabel,
        sex = remote.sex,
        temperament = remote.temperament,
        vaccinated = remote.vaccinated,
        sterilized = remote.sterilized,
        homeNeeds = remote.homeNeeds,
        createdAtEpochMs = remote.createdAtEpochMs,
    )

    private fun replaceRemoteMedia(oldPostId: String, remote: AnimalListingDto) {
        postMedia.removeAll { it.postId == oldPostId || it.postId == remote.postId }
        remote.media.forEach { item ->
            postMedia += LocalPostMedia(
                id = item.id,
                postId = remote.postId,
                kind = item.kind,
                url = item.url,
                posterUrl = item.posterUrl,
                sortOrder = item.sortOrder,
                assetKey = item.assetKey.orEmpty(),
                altText = item.altText,
                durationMs = item.durationMs,
            )
        }
    }

    fun mediaOf(postId: String): List<LocalPostMedia> =
        postMedia.filter { it.postId == postId }.sortedBy { it.sortOrder }

    fun messagesOf(matchId: String): List<LocalMessage> = messages.filter { it.matchId == matchId }

    fun skillsOf(userId: String, offered: Boolean): List<LocalSkillTag> {
        val tagIds = userSkills.filter { it.userId == userId && if (offered) it.offered else it.requested }.map { it.tagId }
        return skillTags.filter { it.id in tagIds }
    }

    /** Aviso de ayuda: qué necesito y qué doy (saber o tarea). Sin horario. */
    fun publishHelpExchange(
        authorId: String,
        need: String,
        give: String,
        note: String? = null,
    ): LocalHelpExchange? {
        val needTrim = need.trim()
        val giveTrim = give.trim()
        if (needTrim.isEmpty() || giveTrim.isEmpty()) return null
        val now = currentEpochMs()
        val row = LocalHelpExchange(
            id = "help-pub-$now",
            authorUserId = authorId,
            need = needTrim,
            give = giveTrim,
            note = note?.trim()?.ifBlank { null },
            createdAtEpochMs = now,
        )
        helpExchanges.add(0, row)
        val author = userOrNull(authorId)
        val extra = row.note?.let { " $it" }.orEmpty()
        ingestPublishedPost(
            LocalSocialPost(
                id = "post-pub-${row.createdAtEpochMs}",
                authorKind = AuthorKind.USER,
                authorUserId = authorId,
                authorCompanyId = null,
                place = author?.barrio.orEmpty(),
                timeLabel = "Ahora",
                tag = "Intercambio de Ayuda",
                body = "Necesito $needTrim. A cambio doy $giveTrim.$extra",
                impactCount = 0,
                commentCount = 0,
                isStory = false,
                storyLabel = null,
                createdAtEpochMs = row.createdAtEpochMs,
            ),
        )
        return row
    }

    fun helpExchangesOf(authorId: String? = null): List<LocalHelpExchange> =
        if (authorId == null) helpExchanges.toList()
        else helpExchanges.filter { it.authorUserId == authorId }

    fun ranking(): List<LocalUser> = users.sortedByDescending { it.communityPoints }

    fun rankOf(userId: String): Int = ranking().indexOfFirst { it.id == userId } + 1

    fun authorName(post: LocalSocialPost): String = when (post.authorKind) {
        AuthorKind.USER -> userOrNull(post.authorUserId)?.displayName ?: "Alguien de la comunidad"
        AuthorKind.COMPANY -> post.authorCompanyId?.let { id ->
            runCatching { company(id) }.getOrNull()?.let { it.tradeName ?: it.legalName }
        } ?: "Una organización"
    }

    fun toProfile(user: LocalUser): UserProfile = UserProfile(
        id = user.id,
        firebaseUid = user.firebaseUid,
        displayName = user.displayName,
        photoUrl = user.photoUrl,
        role = user.role,
        communityPoints = user.communityPoints,
        inviteCode = user.inviteCode,
    )

    fun toCompany(company: LocalCompany): CompanyProfile = CompanyProfile(
        id = company.id,
        legalName = company.legalName,
        tradeName = company.tradeName,
        verified = company.verified,
        campaignBalanceCents = company.campaignBalanceCents,
        impactScore = company.impactScore,
    )

    fun toDomain(post: LocalSocialPost): SocialPost {
        val authorId = post.authorUserId ?: post.authorCompanyId.orEmpty()
        return SocialPost(
            id = post.id,
            authorKind = post.authorKind,
            authorId = authorId,
            authorName = authorName(post),
            authorPhotoUrl = userOrNull(post.authorUserId)?.photoUrl,
            body = post.body,
            mediaUrls = mediaOf(post.id).map { it.url },
            impactCount = post.impactCount,
            commentCount = post.commentCount,
            isStory = post.isStory,
            createdAtEpochMs = post.createdAtEpochMs,
            topic = post.tag,
            protagonistUserId = protagonistOf(post),
            media = mediaOf(post.id).map { row ->
                PostMediaItem(
                    id = row.id,
                    kind = row.kind,
                    url = row.url,
                    posterUrl = row.posterUrl,
                    sortOrder = row.sortOrder,
                    durationMs = row.durationMs,
                    altText = row.altText,
                    assetKey = row.assetKey,
                )
            },
            sourceUrl = post.sourceUrl,
        )
    }

    fun toDomain(comment: LocalComment): SocialComment {
        val author = user(comment.authorUserId)
        return SocialComment(
            id = comment.id,
            postId = comment.postId,
            authorUserId = comment.authorUserId,
            authorName = author.displayName,
            parentCommentId = comment.parentCommentId,
            body = comment.body,
            createdAtEpochMs = 0L,
        )
    }

    fun toDomain(spot: LocalParkingSpot, viewerUserId: String? = null): ParkingSpot = ParkingSpot(
        id = spot.id,
        ownerUserId = spot.ownerUserId,
        claimedByUserId = spot.claimedByUserId,
        location = GeoPoint(spot.latitude, spot.longitude),
        status = spot.status,
        version = spot.version,
        expiresAtEpochMs = spot.expiresAtEpochMs,
        distanceMeters = spot.distanceMeters,
        etaSeconds = spot.etaSeconds,
        rewardPoints = spot.rewardPoints,
        notes = spot.notes,
        address = spot.address,
        vehicleLabel = spot.vehicleLabel,
        ownerEtaSeconds = spot.ownerEtaSeconds,
        interestClosesAtEpochMs = spot.interestClosesAtEpochMs.takeIf { it > 0L },
        ownerWaitDeadlineAtEpochMs = spot.ownerWaitDeadlineAtEpochMs.takeIf { it > 0L },
        leftoverOpen = spot.leftoverOpen,
        interestCount = parkingInterests.count { it.spotId == spot.id },
        viewerInterested = viewerUserId != null &&
            parkingInterests.any { it.spotId == spot.id && it.userId == viewerUserId },
    )

    fun toDomain(handoff: LocalParkingHandoff): ParkingHandoff = ParkingHandoff(
        id = handoff.id,
        parkingSpotId = handoff.parkingSpotId,
        ownerUserId = handoff.ownerUserId,
        claimantUserId = handoff.claimantUserId,
        proximityMeters = handoff.proximityMeters,
        verified = handoff.verified,
        pointsAwarded = handoff.pointsAwarded,
        completedAtEpochMs = handoff.completedAtEpochMs,
    )

    /**
     * Cuatro cesiones de demo alrededor del GPS de la persona, a distancias
     * que cruzan los radios 400 / 500 / 1000 / 2000 m.
     */
    fun placeRadarDemoSpots(origin: GeoPoint) {
        val specs = listOf(
            RadarDemoSpec("spot-radar-220", 220.0, 40.0, OgtIds.ValeriaP, "Murillo al 800", "Fiat Cronos gris · AD 392 KL", "Media cuadra · sale ahora"),
            RadarDemoSpec("spot-radar-450", 450.0, 130.0, OgtIds.Lucas, "Vera y Scalabrini Ortiz", "VW Gol blanco · AC 881 PQ", "Sombra de tilo · 8 min"),
            RadarDemoSpec("spot-radar-850", 850.0, 220.0, OgtIds.Bruno, "Serrano y Córdoba", "Peugeot 208 rojo · AB 104 TR", "Frente a un kiosco"),
            RadarDemoSpec("spot-radar-1600", 1_600.0, 310.0, OgtIds.Sofia, "Parque Centenario, Díaz Vélez", "Toyota Etios azul · AE 220 MN", "Cerca de la reja del parque"),
        )
        val stillGood = specs.all { spec ->
            val row = parkingSpots.firstOrNull { it.id == spec.id } ?: return@all false
            val meters = GeoMath.haversineMeters(origin, GeoPoint(row.latitude, row.longitude))
            kotlin.math.abs(meters - spec.meters) < 50.0
        }
        if (stillGood) return
        parkingSpots.removeAll { it.id.startsWith("spot-radar-") }
        val now = currentEpochMs()
        val ttl = now + 12 * 60_000L
        specs.forEach { spec ->
            val point = GeoMath.destination(origin, spec.meters, spec.bearing)
            parkingSpots += LocalParkingSpot(
                id = spec.id,
                ownerUserId = spec.ownerUserId,
                claimedByUserId = null,
                latitude = point.latitude,
                longitude = point.longitude,
                status = ParkingStatus.AVAILABLE,
                version = 0,
                address = spec.address,
                vehicleLabel = spec.vehicle,
                etaSeconds = GeoMath.walkEtaSeconds(origin, point),
                distanceMeters = spec.meters,
                rewardPoints = ParkingRules.DEFAULT_REWARD_POINTS,
                notes = spec.notes,
                expiresAtEpochMs = ttl,
                ownerLastLat = point.latitude,
                ownerLastLng = point.longitude,
                leftoverOpen = true,
                matchingResolved = true,
            )
        }
        bumpParking()
    }

    /** Fichas propias publicadas (locales o ya grabadas en Postgres). */
    fun exportPublishedAnimals(): String {
        val listingIds = animals.filter { it.id.isPublishedAnimalId() }.map { it.id }.toSet()
        val publishedPosts = posts.filter { post ->
            post.id.startsWith("post-pub-") || post.listingId in listingIds
        }
        val postIds = publishedPosts.map { it.id }.toSet()
        return honorJson.encodeToString(
            PublishedAnimalsSnapshot(
                listings = animals.filter { it.id in listingIds },
                posts = publishedPosts,
                media = postMedia.filter { it.postId in postIds },
            ),
        )
    }

    fun importPublishedAnimals(raw: String) {
        if (raw.isBlank()) return
        val snap = runCatching { honorJson.decodeFromString<PublishedAnimalsSnapshot>(raw) }.getOrNull() ?: return
        snap.listings.forEach { row ->
            val idx = animals.indexOfFirst { it.id == row.id }
            if (idx >= 0) animals[idx] = row else animals += row
        }
        snap.posts.forEach { row ->
            val idx = posts.indexOfFirst { it.id == row.id || (row.listingId != null && it.listingId == row.listingId) }
            if (idx >= 0) posts[idx] = row else posts += row
        }
        snap.media.forEach { row ->
            val idx = postMedia.indexOfFirst { it.id == row.id }
            if (idx >= 0) postMedia[idx] = row else postMedia += row
        }
        if (snap.listings.isNotEmpty()) {
            bumpLost()
            bumpFeed()
            bumpSocial()
        }
    }

    companion object {
        fun seeded(): OgtLocalDatabase = OgtMockSeed.populate(OgtLocalDatabase())
    }
}

private fun String.isPublishedAnimalId(): Boolean =
    startsWith("animal-pub-") || matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"))

private val honorJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private data class RadarDemoSpec(
    val id: String,
    val meters: Double,
    val bearing: Double,
    val ownerUserId: String,
    val address: String,
    val vehicle: String,
    val notes: String,
)
