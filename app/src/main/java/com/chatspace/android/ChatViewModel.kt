package com.chatspace.android

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chatspace.android.data.ChatRepository
import com.chatspace.android.data.ConversationPreference
import com.chatspace.android.data.Message
import com.chatspace.android.data.MessageLike
import com.chatspace.android.data.Profile
import com.chatspace.android.data.Room
import com.chatspace.android.data.Story
import com.chatspace.android.data.Membership
import com.chatspace.android.data.PresenceState
import com.chatspace.android.data.TypingEvent
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.presenceDataFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.track
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class AppUpdate(
    val version: String,
    val downloadUrl: String,
    val releaseUrl: String,
)

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val assets: List<GitHubReleaseAsset> = emptyList(),
)

@Serializable
private data class GitHubReleaseAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
)

data class ChatUiState(
    val booting: Boolean = true,
    val signedIn: Boolean = false,
    val busy: Boolean = false,
    val profile: Profile? = null,
    val rooms: List<Room> = emptyList(),
    val conversationPreferences: Map<String, ConversationPreference> = emptyMap(),
    val appUpdate: AppUpdate? = null,
    val checkingForUpdate: Boolean = false,
    val stories: List<Story> = emptyList(),
    val selectedRoom: Room? = null,
    val messages: List<Message> = emptyList(),
    val likes: List<MessageLike> = emptyList(),
    val likingMessageIds: Set<Long> = emptySet(),
    val people: List<Profile> = emptyList(),
    val roomAccess: com.chatspace.android.data.RoomAccess? = null,
    val roomMemberships: List<Membership> = emptyList(),
    val onlineUserIds: Set<String> = emptySet(),
    val typingUsers: List<String> = emptyList(),
    val lockedRoom: Room? = null,
    val error: String? = null,
    val notice: String? = null,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val chatSpaceApplication = app as ChatSpaceApplication
    private val supabase = chatSpaceApplication.supabase
    private val repo = ChatRepository(supabase)
    private val mutable = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = mutable.asStateFlow()
    private var refreshJob: Job? = null
    private var realtimeRefreshJob: Job? = null
    private val pendingRealtimeTables = mutableSetOf<String>()
    private var presenceChannel: RealtimeChannel? = null
    private var changesChannel: RealtimeChannel? = null
    private var typingChannel: RealtimeChannel? = null
    private val typingExpiry = mutableMapOf<String, Job>()
    private val realtimeSetupMutex = Mutex()

    init {
        viewModelScope.launch { restore() }
        chatSpaceApplication.pendingNotificationRoom
            .filterNotNull()
            .onEach { roomId ->
                while (mutable.value.booting) delay(100)
                mutable.value.rooms.firstOrNull { it.row.id == roomId }?.let { room ->
                    openRoom(room)
                    chatSpaceApplication.clearNotificationRoom()
                }
            }
            .launchIn(viewModelScope)
    }

    private suspend fun restore() {
        supabase.auth.awaitInitialization()
        val signedIn = supabase.auth.currentSessionOrNull() != null
        if (!signedIn) {
            mutable.value = mutable.value.copy(booting = false, signedIn = false)
            return
        }
        mutable.value = mutable.value.copy(signedIn = true)
        runCatching { refreshAll() }
            .onSuccess { mutable.value = mutable.value.copy(booting = false) }
            .onFailure {
                mutable.value = mutable.value.copy(booting = false)
                showError(it)
            }
    }

    fun signIn(email: String, password: String) = action {
        repo.signIn(email.trim(), password); mutable.value = mutable.value.copy(signedIn = true); refreshAll()
    }
    fun signUp(name: String, username: String, phone: String, email: String, password: String) = action("Check your inbox to confirm your email") {
        require(name.trim().length >= 2) { "Enter your display name" }
        require(Regex("^[a-z0-9_]{3,24}$").matches(username.trim().lowercase())) { "Username must be 3–24 letters, numbers, or underscores" }
        require(password.length >= 8) { "Password must be at least 8 characters" }
        repo.signUp(name.trim(), username.trim().lowercase(), phone.trim().ifBlank { null }, email.trim(), password)
        if (supabase.auth.currentSessionOrNull() != null) { mutable.value = mutable.value.copy(signedIn = true); refreshAll() }
    }
    fun google(idToken: String, nonce: String) = action {
        repo.signInGoogle(idToken, nonce)
        mutable.value = mutable.value.copy(signedIn = true)
        refreshAll()
    }
    fun signOut() = action { stopRefresh(); stopRealtime(); repo.signOut(); mutable.value = ChatUiState(booting = false) }

    fun refresh() = action { refreshAll() }
    fun checkForUpdates(manual: Boolean = false) {
        if (mutable.value.checkingForUpdate) return
        viewModelScope.launch {
            mutable.value = mutable.value.copy(checkingForUpdate = true)
            runCatching { fetchLatestAppUpdate() }
                .onSuccess { update ->
                    mutable.value = mutable.value.copy(
                        checkingForUpdate = false,
                        appUpdate = update,
                        notice = if (manual && update == null) "ChatSpace is up to date" else mutable.value.notice,
                    )
                }
                .onFailure { error ->
                    mutable.value = mutable.value.copy(
                        checkingForUpdate = false,
                        error = if (manual) error.message ?: "Could not check for updates" else mutable.value.error,
                    )
                }
        }
    }
    fun dismissAppUpdate() { mutable.value = mutable.value.copy(appUpdate = null) }

    private suspend fun fetchLatestAppUpdate(): AppUpdate? = withContext(Dispatchers.IO) {
        val connection = URL("https://api.github.com/repos/Avdhut30/ChatSpace/releases/latest")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "ChatSpace-Android/${BuildConfig.VERSION_NAME}")
            val status = connection.responseCode
            if (status !in 200..299) error("Update service is unavailable ($status)")
            val release = Json { ignoreUnknownKeys = true }.decodeFromString<GitHubRelease>(
                connection.inputStream.bufferedReader().use { it.readText() }
            )
            val version = release.tagName.removePrefix("v")
            val asset = release.assets.firstOrNull { it.name == "ChatSpace.apk" }
                ?: return@withContext null
            if (!isNewerVersion(version, BuildConfig.VERSION_NAME)) return@withContext null
            AppUpdate(version, asset.downloadUrl, release.htmlUrl)
        } finally {
            connection.disconnect()
        }
    }

    private fun isNewerVersion(candidate: String, current: String): Boolean {
        val candidateParts = candidate.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val currentParts = current.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        repeat(maxOf(candidateParts.size, currentParts.size)) { index ->
            val candidatePart = candidateParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (candidatePart != currentPart) return candidatePart > currentPart
        }
        return false
    }
    fun approveWebLink(qrValue: String) = action("Web browser connected") {
        repo.approveWebLink(qrValue)
    }
    private suspend fun refreshAll() {
        val profile = repo.profile()
        val rooms = repo.rooms()
        val preferences = runCatching { repo.conversationPreferences() }.getOrDefault(emptyList())
        val stories = runCatching { repo.stories() }.getOrDefault(emptyList())
        mutable.value = mutable.value.copy(
            profile = profile,
            rooms = rooms,
            conversationPreferences = preferences.associateBy { it.roomId },
            stories = stories,
            signedIn = true,
        )
        registerPushToken()
        startRealtime(profile)
        startRefresh()
    }
    private suspend fun startRealtime(profile: Profile) = realtimeSetupMutex.withLock {
        if (presenceChannel == null) {
            val channel = supabase.channel("chatspace:presence:android")
            try {
                channel.presenceDataFlow<PresenceState>().onEach { states -> mutable.value = mutable.value.copy(onlineUserIds = states.map { it.uid }.toSet()) }.launchIn(viewModelScope)
                channel.subscribe(blockUntilSubscribed = true)
                channel.track(PresenceState(profile.id, profile.name, java.time.Instant.now().toString()))
                presenceChannel = channel
            } catch (error: Throwable) {
                runCatching { supabase.realtime.removeChannel(channel) }
                throw error
            }
        }
        if (changesChannel == null) {
            val channel = supabase.channel("chatspace:changes:android")
            try {
                listOf("profiles", "rooms", "room_members", "messages", "message_likes", "stories", "story_views", "conversation_preferences").forEach { table ->
                    channel.postgresChangeFlow<PostgresAction>(schema = "public") { this.table = table }
                        .onEach { refreshRealtime(table) }.launchIn(viewModelScope)
                }
                channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "messages" }
                    .onEach { showRealtimeNotification(it.record) }
                    .launchIn(viewModelScope)
                channel.subscribe(blockUntilSubscribed = true)
                changesChannel = channel
            } catch (error: Throwable) {
                runCatching { supabase.realtime.removeChannel(channel) }
                throw error
            }
        }
    }
    private fun refreshRealtime(table: String) {
        pendingRealtimeTables += table
        if (realtimeRefreshJob?.isActive == true) return

        realtimeRefreshJob = viewModelScope.launch {
            delay(150)
            while (pendingRealtimeTables.isNotEmpty()) {
                val tables = pendingRealtimeTables.toSet()
                pendingRealtimeTables.clear()
                runCatching {
                    val refreshRooms = tables.any { it in setOf("profiles", "rooms", "room_members", "messages") }
                    val refreshMessages = tables.any { it == "messages" || it == "message_likes" }
                    val selected = mutable.value.selectedRoom?.row?.id
                    val refreshedProfile = if ("profiles" in tables) repo.profile() else null
                    val refreshedRooms = if (refreshRooms) repo.rooms() else null
                    val refreshedPreferences = if ("conversation_preferences" in tables) repo.conversationPreferences() else null
                    val refreshedStories = if (tables.any { it == "stories" || it == "story_views" }) repo.stories() else null
                    val pair = if (refreshMessages) selected?.let { repo.messages(it) } else null
                    val latest = mutable.value
                    mutable.value = latest.copy(
                        profile = refreshedProfile ?: latest.profile,
                        rooms = refreshedRooms ?: latest.rooms,
                        conversationPreferences = refreshedPreferences?.associateBy { it.roomId }
                            ?: latest.conversationPreferences,
                        stories = refreshedStories ?: latest.stories,
                        messages = pair?.first ?: latest.messages,
                        likes = pair?.second ?: latest.likes,
                    )
                }
            }
        }
    }
    private fun startRefresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            while (isActive && mutable.value.signedIn) {
                delay(30_000)
                runCatching {
                    val rooms = repo.rooms()
                    val preferences = runCatching { repo.conversationPreferences() }.getOrDefault(emptyList())
                    val selected = mutable.value.selectedRoom?.id
                    val updatedSelected = selected?.let { id -> rooms.find { it.id == id } }
                    val pair = selected?.let { repo.messages(it) }
                    mutable.value = mutable.value.copy(
                        rooms = rooms,
                        conversationPreferences = preferences.associateBy { it.roomId },
                        selectedRoom = updatedSelected ?: mutable.value.selectedRoom,
                        messages = pair?.first ?: mutable.value.messages,
                        likes = pair?.second ?: mutable.value.likes,
                    )
                }
            }
        }
    }
    private fun stopRefresh() { refreshJob?.cancel(); refreshJob = null }
    private fun registerPushToken() {
        if (!PushNotifications.isConfigured) return
        PushNotifications.getToken { token ->
            if (token != null) viewModelScope.launch { runCatching { repo.registerPushToken(token) } }
        }
    }
    private fun showRealtimeNotification(record: Map<String, kotlinx.serialization.json.JsonElement>) {
        if (PushNotifications.isConfigured) return
        val authorId = record["author_id"]?.jsonPrimitive?.contentOrNull ?: return
        val roomId = record["room_id"]?.jsonPrimitive?.contentOrNull ?: return
        if (authorId == repo.userId || mutable.value.selectedRoom?.row?.id == roomId) return
        if (mutable.value.conversationPreferences[roomId]?.isMuted == true) return
        val room = mutable.value.rooms.firstOrNull { it.row.id == roomId }
        val author = room?.members?.firstOrNull { it.id == authorId }?.name ?: "New message"
        val text = record["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: record["file_name"]?.jsonPrimitive?.contentOrNull?.let { "Sent $it" }
            ?: "Sent an attachment"
        PushNotifications.show(getApplication(), author, text, roomId)
    }
    private fun stopRealtime() = viewModelScope.launch {
        realtimeSetupMutex.withLock {
            realtimeRefreshJob?.cancel(); realtimeRefreshJob = null; pendingRealtimeTables.clear()
            typingChannel?.let { supabase.realtime.removeChannel(it) }; typingChannel = null
            changesChannel?.let { supabase.realtime.removeChannel(it) }; changesChannel = null
            presenceChannel?.let { supabase.realtime.removeChannel(it) }; presenceChannel = null
            mutable.value = mutable.value.copy(onlineUserIds = emptySet(), typingUsers = emptyList())
        }
    }

    fun openRoom(room: Room) = action {
        val access = if (room.type == "personal") repo.access(room.id) else null
        if (access?.hasPassword == true && !access.unlocked) {
            mutable.value = mutable.value.copy(lockedRoom = room)
            throw LockedRoomException()
        }
        val (messages, likes) = repo.messages(room.id)
        mutable.value = mutable.value.copy(selectedRoom = room, messages = messages, likes = likes, roomAccess = access)
        startTyping(room)
    }
    private suspend fun startTyping(room: Room) {
        typingChannel?.let { supabase.realtime.removeChannel(it) }
        val channel = supabase.channel("typing:${room.id}")
        channel.broadcastFlow<TypingEvent>("typing").onEach { event ->
            if (event.uid == repo.userId) return@onEach
            typingExpiry.remove(event.uid)?.cancel()
            val current = mutable.value.typingUsers.toMutableSet()
            if (event.typing) current += event.name else current -= event.name
            mutable.value = mutable.value.copy(typingUsers = current.toList())
            if (event.typing) typingExpiry[event.uid] = viewModelScope.launch { delay(1800); mutable.value = mutable.value.copy(typingUsers = mutable.value.typingUsers - event.name) }
        }.launchIn(viewModelScope)
        channel.subscribe(blockUntilSubscribed = true); typingChannel = channel
    }
    fun sendTyping(typing: Boolean) = viewModelScope.launch {
        val profile = mutable.value.profile ?: return@launch
        runCatching { typingChannel?.broadcast("typing", TypingEvent(profile.id, profile.name, typing)) }
    }
    fun closeRoom() { viewModelScope.launch { typingChannel?.let { supabase.realtime.removeChannel(it) }; typingChannel=null }; mutable.value = mutable.value.copy(selectedRoom = null, messages = emptyList(), likes = emptyList(), roomAccess = null, roomMemberships = emptyList(), typingUsers = emptyList()) }
    fun unlock(room: Room, password: String) = action {
        repo.unlock(room.id, password)
        mutable.value = mutable.value.copy(lockedRoom = null)
        openRoomData(room); startTyping(room)
    }
    private suspend fun openRoomData(room: Room) { val pair = repo.messages(room.id); mutable.value = mutable.value.copy(selectedRoom = room, messages = pair.first, likes = pair.second) }

    fun send(text: String, reply: Message? = null, uri: Uri? = null) = action {
        val room = requireNotNull(mutable.value.selectedRoom)
        var upload: ChatRepository.Upload? = null
        if (uri != null) {
            val resolver = getApplication<Application>().contentResolver
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not read file")
            val type = resolver.getType(uri) ?: "application/octet-stream"
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "attachment"
            upload = repo.upload(room.id, name, type, bytes)
        }
        require(text.isNotBlank() || upload != null) { "Write a message or attach a file" }
        repo.send(room.id, text.trim(), reply?.id, upload); openRoomData(room)
    }
    fun sendFile(text: String = "", reply: Message? = null, name: String, type: String, bytes: ByteArray) = action {
        val room = requireNotNull(mutable.value.selectedRoom)
        val upload = repo.upload(room.id, name, type, bytes)
        repo.send(room.id, text.trim(), reply?.id, upload)
        openRoomData(room)
    }
    fun edit(message: Message, text: String) = action { repo.edit(message.id, text); openRoomData(requireNotNull(mutable.value.selectedRoom)) }
    fun delete(message: Message) = action { repo.delete(message.id); openRoomData(requireNotNull(mutable.value.selectedRoom)) }
    fun like(message: Message) {
        if (message.id in mutable.value.likingMessageIds) return
        viewModelScope.launch {
            val userId = repo.userId ?: return@launch
            val previousLikes = mutable.value.likes
            val liked = previousLikes.any { it.messageId == message.id && it.userId == userId }
            val optimisticLikes = if (liked) {
                previousLikes.filterNot { it.messageId == message.id && it.userId == userId }
            } else {
                previousLikes + MessageLike(message.id, userId)
            }
            mutable.value = mutable.value.copy(
                likes = optimisticLikes,
                likingMessageIds = mutable.value.likingMessageIds + message.id,
                error = null,
            )
            runCatching { repo.toggleLike(message.id, liked) }
                .onFailure { error ->
                    mutable.value = mutable.value.copy(
                        likes = previousLikes,
                        error = error.message ?: "Could not update the like",
                    )
                }
            mutable.value = mutable.value.copy(
                likingMessageIds = mutable.value.likingMessageIds - message.id,
            )
        }
    }

    fun loadPeople(query: String = "") = action { mutable.value = mutable.value.copy(people = repo.profiles(query).filter { it.id != repo.userId }) }
    fun updateConversationPreference(
        roomId: String,
        pinned: Boolean? = null,
        archived: Boolean? = null,
        muted: Boolean? = null,
    ) {
        val uid = repo.userId ?: return
        val previous = mutable.value.conversationPreferences[roomId]
            ?: ConversationPreference(userId = uid, roomId = roomId)
        val updated = previous.copy(
            isPinned = pinned ?: previous.isPinned,
            isArchived = archived ?: previous.isArchived,
            isMuted = muted ?: previous.isMuted,
        )
        mutable.value = mutable.value.copy(
            conversationPreferences = mutable.value.conversationPreferences + (roomId to updated),
            error = null,
        )
        viewModelScope.launch {
            runCatching { repo.saveConversationPreference(updated) }
                .onFailure { error ->
                    val restored = mutable.value.conversationPreferences.toMutableMap()
                    if (previous == ConversationPreference(userId = uid, roomId = roomId)) restored.remove(roomId)
                    else restored[roomId] = previous
                    mutable.value = mutable.value.copy(
                        conversationPreferences = restored,
                        error = error.message ?: "Could not update the conversation",
                    )
                }
        }
    }
    fun createGroup(name: String, description: String, members: List<String>) = action { repo.createGroup(name, description, members); refreshAll() }
    fun direct(userId: String) = action { val id = repo.direct(userId); refreshAll(); mutable.value.rooms.find { it.id == id }?.let { openRoomData(it); startTyping(it) } }
    fun saveProfile(name: String, username: String, phone: String) = action { repo.updateProfile(name.trim(), username.trim(), phone.trim().ifBlank { null }); refreshAll() }
    fun uploadAvatar(uri: Uri) = action("Profile photo updated") {
        val profileId = mutable.value.profile?.id ?: error("Your profile is still loading. Try again in a moment")
        val resolver = getApplication<Application>().contentResolver
        val bitmap = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val width = info.size.width
                    val height = info.size.height
                    val largest = maxOf(width, height)
                    if (largest > 2048) {
                        val scale = 2048f / largest
                        decoder.setTargetSize((width * scale).toInt(), (height * scale).toInt())
                    }
                }
            } else {
                resolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                    ?: error("The selected file is not a readable image")
            }
        }.getOrElse { error("Could not process the selected photo: ${it.message ?: "unsupported image"}") }
        val bytes = ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) { "Could not prepare profile photo" }
            output.toByteArray()
        }
        val avatarUrl = runCatching { repo.uploadAvatar(profileId, "avatar.jpg", "image/jpeg", bytes) }
            .getOrElse {
                val detail = it.message.orEmpty().lowercase()
                if (detail.contains("row-level security") || detail.contains("session")) {
                    error("Your login session could not authorize the upload. Sign out, sign in again, and retry.")
                }
                error("Profile photo upload failed. Please retry.")
            }
        mutable.value = mutable.value.copy(profile = mutable.value.profile?.copy(avatarUrl = avatarUrl))
        val refreshedProfile = repo.profile()
        check(refreshedProfile.avatarUrl == avatarUrl) { "The profile photo was uploaded but could not be saved to your profile." }
        mutable.value = mutable.value.copy(profile = refreshedProfile)
        viewModelScope.launch { runCatching { mutable.value = mutable.value.copy(rooms = repo.rooms()) } }
    }
    fun addStory(text: String, color: String) = action { repo.addTextStory(text, color); mutable.value = mutable.value.copy(stories = repo.stories()) }
    fun addMediaStory(text: String, color: String, uri: Uri) = action("Your story is live for 24 hours") {
        val resolver = getApplication<Application>().contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not read story media")
        repo.addMediaStory(text, uri.lastPathSegment ?: "story", resolver.getType(uri) ?: "application/octet-stream", bytes, color)
        mutable.value = mutable.value.copy(stories = repo.stories())
    }
    fun viewStory(story: Story) = action {
        if (story.authorId != repo.userId && !story.viewed) repo.markStoryViewed(story.id)
        mutable.value = mutable.value.copy(stories = repo.stories())
    }
    fun deleteStory(story: Story) = action("Story deleted") { repo.deleteStory(story); mutable.value = mutable.value.copy(stories = repo.stories()) }

    fun loadRoomSettings() = action {
        val room = requireNotNull(mutable.value.selectedRoom)
        mutable.value = mutable.value.copy(people = repo.profiles(), roomMemberships = repo.memberships(room.id), roomAccess = if (room.type == "personal") repo.access(room.id) else null)
    }
    fun updateRoom(name: String, description: String, members: Set<String>) = action("Conversation updated") {
        val room = requireNotNull(mutable.value.selectedRoom)
        repo.updateRoom(room.id, name.trim(), description.trim())
        repo.setRoomMembers(room.id, mutable.value.roomMemberships, members + requireNotNull(repo.userId))
        refreshAll(); mutable.value.rooms.find { it.row.id == room.id }?.let { openRoomData(it) }
    }
    fun toggleAdmin(memberId: String, isAdmin: Boolean) = action {
        val room = requireNotNull(mutable.value.selectedRoom)
        repo.setAdmin(room.id, memberId, isAdmin); loadSettingsData(room)
    }
    fun deleteCurrentRoom() = action("Conversation deleted") {
        val room = requireNotNull(mutable.value.selectedRoom); repo.deleteRoom(room.id)
        mutable.value = mutable.value.copy(selectedRoom = null, messages = emptyList(), likes = emptyList()); refreshAll()
    }
    fun setPersonalPassword(password: String) = action("Personal-space password saved") {
        require(password.length >= 6) { "Password must contain at least 6 characters" }
        val room = requireNotNull(mutable.value.selectedRoom); repo.setRoomPassword(room.id, password)
        mutable.value = mutable.value.copy(roomAccess = repo.access(room.id))
    }
    fun lockPersonalRoom() = action {
        val room = requireNotNull(mutable.value.selectedRoom); repo.lockRoom(room.id)
        mutable.value = mutable.value.copy(selectedRoom = null, messages = emptyList(), likes = emptyList(), roomAccess = null)
    }
    fun removePersonalPassword() = action("Password removed") {
        val room = requireNotNull(mutable.value.selectedRoom); repo.removeRoomPassword(room.id)
        mutable.value = mutable.value.copy(roomAccess = repo.access(room.id))
    }
    private suspend fun loadSettingsData(room: Room) { mutable.value = mutable.value.copy(people = repo.profiles(), roomMemberships = repo.memberships(room.id)) }

    fun clearMessage() { mutable.value = mutable.value.copy(error = null, notice = null) }
    fun dismissUnlock() { mutable.value = mutable.value.copy(lockedRoom = null, error = null) }
    fun reportError(message: String) { mutable.value = mutable.value.copy(busy = false, error = message) }
    private fun action(notice: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            mutable.value = mutable.value.copy(busy = true, error = null, notice = null)
            runCatching { block() }.onSuccess { mutable.value = mutable.value.copy(busy = false, notice = notice) }.onFailure(::showError)
        }
    }
    private fun showError(t: Throwable) {
        val rawMessage = t.message ?: "Something went wrong"
        val safeMessage = when {
            t is LockedRoomException -> "ROOM_LOCKED"
            rawMessage.contains("Authorization=", ignoreCase = true) ||
                rawMessage.contains("row-level security", ignoreCase = true) ->
                "Your login session could not authorize that action. Sign out, sign in again, and retry."
            else -> rawMessage
        }
        mutable.value = mutable.value.copy(busy = false, error = safeMessage)
    }
    private class LockedRoomException : Exception()
    private val Room.id get() = row.id
}
