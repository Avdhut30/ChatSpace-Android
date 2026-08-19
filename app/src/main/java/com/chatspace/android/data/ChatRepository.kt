package com.chatspace.android.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.functions.functions
import io.ktor.http.ContentType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.seconds
import android.os.Build
import android.net.Uri
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf

class ChatRepository(private val client: SupabaseClient) {
    val userId: String? get() = client.auth.currentUserOrNull()?.id

    suspend fun signIn(email: String, password: String) = client.auth.signInWith(Email) { this.email = email; this.password = password }
    suspend fun signUp(name: String, username: String, phone: String?, email: String, password: String) =
        client.auth.signUpWith(Email, redirectUrl = "chatspace://auth") {
            this.email = email; this.password = password
            data = buildJsonObject { put("full_name", name); put("username", username); phone?.let { put("phone_number", it) } }
        }
    suspend fun signInGoogle(idToken: String, nonce: String) = client.auth.signInWith(IDToken) {
        this.idToken = idToken
        provider = Google
        this.nonce = nonce
    }
    suspend fun signOut() = client.auth.signOut()
    suspend fun registerPushToken(token: String) {
        requireNotNull(userId)
        client.postgrest.rpc("register_push_token", buildJsonObject {
            put("new_token", token)
            put("new_platform", "android")
            put("new_device_name", "${Build.MANUFACTURER} ${Build.MODEL}")
        })
    }
    suspend fun approveWebLink(qrValue: String) {
        val uri = Uri.parse(qrValue)
        require(uri.scheme == "chatspace" && uri.host == "link") { "This is not a ChatSpace web QR code" }
        val sessionId = uri.getQueryParameter("session").orEmpty()
        val secret = uri.getQueryParameter("secret").orEmpty()
        require(sessionId.matches(Regex("^[0-9a-fA-F-]{36}$")) && secret.length >= 32) { "This QR code is invalid" }

        client.auth.awaitInitialization()
        client.auth.refreshCurrentSession()
        requireNotNull(client.auth.currentSessionOrNull()?.accessToken) {
            "Your login session expired. Sign in again."
        }
        try {
            client.functions.invoke(
                "device-link",
                DeviceLinkApproval(action = "approve", sessionId = sessionId, approvalSecret = secret),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        } catch (error: RestException) {
            val serverMessage = runCatching {
                Json.parseToJsonElement(error.error)
                    .jsonObject["error"]?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            throw IllegalStateException(
                serverMessage ?: when (error.statusCode) {
                    401 -> "Your phone session expired. Sign in again."
                    403 -> "This QR code is not valid for this session."
                    409 -> "This QR code was already used. Create a new one."
                    410 -> "This QR code expired. Create a new one."
                    else -> "Web linking failed. Create a new QR code and retry."
                }
            )
        }
    }

    suspend fun profile(): Profile {
        val uid = requireNotNull(userId)
        val databaseProfile = client.postgrest.rpc(
            "get_profile_identity",
            buildJsonObject { put("check_profile_id", uid) },
        ).decodeList<Profile>().singleOrNull() ?: error("Your profile could not be found")
        if (!databaseProfile.avatarUrl.isNullOrBlank()) return databaseProfile
        val metadata = client.auth.currentUserOrNull()?.userMetadata
        val authAvatar = metadata?.get("avatar_url")?.jsonPrimitive?.contentOrNull
            ?: metadata?.get("picture")?.jsonPrimitive?.contentOrNull
        return if (authAvatar.isNullOrBlank()) databaseProfile else databaseProfile.copy(avatarUrl = authAvatar)
    }

    suspend fun profiles(query: String = ""): List<Profile> = client.from("profiles").select(
        Columns.list("id", "name", "avatar_url", "username"),
    ) {
        if (query.isNotBlank()) filter { or { ilike("name", "%$query%"); ilike("username", "%$query%") } }
        limit(50)
    }.decodeList()

    suspend fun profilesByPhoneContacts(phoneNumbers: List<String>): List<Profile> {
        val uniqueNumbers = phoneNumbers.distinct().take(500)
        if (uniqueNumbers.isEmpty()) return emptyList()
        return client.postgrest.rpc(
            "find_profiles_by_phone_contacts",
            buildJsonObject {
                put("contact_numbers", kotlinx.serialization.json.buildJsonArray {
                    uniqueNumbers.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                })
            },
        ).decodeList()
    }

    suspend fun rooms(): List<Room> {
        val uid = requireNotNull(userId)
        val memberships = client.from("room_members").select().decodeList<Membership>()
        val mine = memberships.filter { it.userId == uid }.map { it.roomId }.toSet()
        if (mine.isEmpty()) return emptyList()
        val rows = runCatching { client.from("room_summaries").select().decodeList<RoomRow>() }
            .getOrElse { client.from("rooms").select().decodeList() }
            .filter { it.id in mine }
        val people = client.from("profiles")
            .select(Columns.list("id", "name", "avatar_url", "username"))
            .decodeList<Profile>()
            .associateBy { it.id }
        return rows.map { row ->
            val roomMembers = memberships.filter { it.roomId == row.id }
            val type = row.roomType ?: when {
                row.description?.startsWith("chatspace-direct:") == true -> "direct"
                row.description == "chatspace-personal:$uid" -> "personal"
                else -> "group"
            }
            val partner = roomMembers.firstOrNull { it.userId != uid }?.let { people[it.userId] }
            Room(row, type, roomMembers.mapNotNull { people[it.userId] }, roomMembers.filter { it.isAdmin }.map { it.userId }.toSet(), if (type == "direct") partner?.name ?: row.name else row.name)
        }.sortedByDescending { it.row.lastMessageCreatedAt ?: it.row.createdAt }
    }

    suspend fun conversationPreferences(): List<ConversationPreference> {
        val uid = requireNotNull(userId)
        return client.from("conversation_preferences").select {
            filter { eq("user_id", uid) }
        }.decodeList()
    }

    suspend fun saveConversationPreference(preference: ConversationPreference) {
        require(preference.userId == requireNotNull(userId)) { "You can only update your own conversations" }
        client.from("conversation_preferences").upsert(preference) {
            onConflict = "user_id,room_id"
        }
    }

    suspend fun messages(roomId: String): Pair<List<Message>, List<MessageLike>> {
        val rows = client.from("messages").select {
            filter { eq("room_id", roomId) }; order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING); limit(250)
        }.decodeList<Message>().asReversed()
        val messages = rows.map { message ->
            if (message.filePath == null) message
            else message.copy(fileUrl = runCatching { signedFileUrl(message.filePath) }.getOrNull())
        }
        val messageIds = messages.map { it.id }.toHashSet()
        val likes = runCatching {
            client.postgrest.rpc(
                "get_room_message_likes",
                buildJsonObject { put("check_room_id", roomId) },
            ).decodeList<MessageLike>()
        }.getOrElse {
            client.from("message_likes").select().decodeList<MessageLike>()
                .filter { reaction -> reaction.messageId in messageIds }
        }
        return messages to likes
    }

    suspend fun send(roomId: String, text: String, replyTo: Long? = null, file: Upload? = null) {
        val uid = requireNotNull(userId)
        client.from("messages").insert(NewMessage(roomId, uid, text.take(1000), replyTo, file?.path, file?.name, file?.type, file?.bytes?.size?.toLong()))
    }
    suspend fun edit(messageId: Long, text: String) = client.from("messages").update({ set("text", text.take(1000)) }) { filter { eq("id", messageId) } }
    suspend fun delete(messageId: Long) = client.from("messages").delete { filter { eq("id", messageId) } }
    suspend fun toggleLike(messageId: Long, liked: Boolean) {
        val uid = requireNotNull(userId)
        if (liked) client.from("message_likes").delete { filter { eq("message_id", messageId); eq("user_id", uid) } }
        else client.from("message_likes").insert(MessageLike(messageId, uid))
    }

    data class Upload(val path: String, val name: String, val type: String, val bytes: ByteArray)
    suspend fun upload(roomId: String, name: String, type: String, bytes: ByteArray): Upload {
        require(bytes.size <= 100 * 1024 * 1024) { "Files must be 100 MB or smaller" }
        val path = "${requireNotNull(userId)}/$roomId/${System.currentTimeMillis()}-${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}"
        client.storage.from("chat-files").upload(path, bytes) { upsert = false; contentType = ContentType.parse(type) }
        return Upload(path, name, type, bytes)
    }
    suspend fun signedFileUrl(path: String) = client.storage.from("chat-files").createSignedUrl(path, 3600.seconds)

    suspend fun uploadAvatar(ownerId: String, name: String, type: String, bytes: ByteArray): String {
        require(type in setOf("image/jpeg", "image/png", "image/webp")) { "Choose a JPEG, PNG, or WebP image" }
        require(bytes.size <= 15 * 1024 * 1024) { "Profile photos must be 15 MB or smaller" }
        client.auth.awaitInitialization()
        client.auth.refreshCurrentSession()
        val uid = requireNotNull(userId) { "Your login session expired. Sign in again." }
        require(ownerId == uid) { "The active account changed. Please retry the upload." }
        val extension = when (type) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
        val path = "$uid/avatar-${System.currentTimeMillis()}.$extension"
        client.storage.from("profile-avatars").upload(path, bytes) { upsert = false; contentType = ContentType.parse(type) }
        val url = client.storage.from("profile-avatars").publicUrl(path)
        runCatching {
            client.postgrest.rpc("update_my_avatar", buildJsonObject { put("new_avatar_url", url) })
        }.getOrElse {
            client.from("profiles").update({ set("avatar_url", url) }) { filter { eq("id", uid) } }
        }
        return url
    }

    suspend fun createGroup(name: String, description: String, memberIds: List<String>): String {
        val uid = requireNotNull(userId)
        val room = client.from("rooms").insert(NewRoom(name, description, uid)) { select(Columns.list("id")) }.decodeSingle<RowId>()
        (memberIds + uid).distinct().forEach { client.from("room_members").insert(NewMembership(room.id, it, it == uid)) }
        return room.id
    }
    suspend fun updateRoom(roomId: String, name: String, description: String) =
        client.from("rooms").update({ set("name", name); set("description", description) }) { filter { eq("id", roomId) } }
    suspend fun setRoomMembers(roomId: String, existing: List<Membership>, selected: Set<String>) {
        val uid = requireNotNull(userId)
        selected.filter { id -> existing.none { it.userId == id } }.forEach { client.from("room_members").insert(NewMembership(roomId, it)) }
        existing.filter { it.userId != uid && it.userId !in selected }.forEach { member ->
            client.from("room_members").delete { filter { eq("room_id", roomId); eq("user_id", member.userId) } }
        }
    }
    suspend fun memberships(roomId: String): List<Membership> = client.from("room_members").select { filter { eq("room_id", roomId) } }.decodeList()
    suspend fun setAdmin(roomId: String, memberId: String, isAdmin: Boolean) =
        client.from("room_members").update({ set("is_admin", isAdmin) }) { filter { eq("room_id", roomId); eq("user_id", memberId) } }
    suspend fun deleteRoom(roomId: String) = client.from("rooms").delete { filter { eq("id", roomId) } }
    suspend fun direct(other: String): String = client.postgrest.rpc("get_or_create_direct_room", buildJsonObject { put("other_user_id", other) }).decodeAs()
    suspend fun updateProfile(name: String, username: String, phone: String?) {
        val uid = requireNotNull(userId)
        client.from("profiles").update({ set("name", name) }) { filter { eq("id", uid) } }
        client.postgrest.rpc("update_my_identity", buildJsonObject {
            put("new_username", username)
            if (phone == null) put("new_phone_number", kotlinx.serialization.json.JsonNull)
            else put("new_phone_number", phone)
        })
    }
    suspend fun access(roomId: String): RoomAccess = client.postgrest.rpc(
        "get_personal_room_access",
        buildJsonObject { put("check_room_id", roomId) },
    ).decodeAs()
    suspend fun unlock(roomId: String, password: String) {
        val unlockedUntil = client.postgrest.rpc(
            "unlock_personal_room",
            buildJsonObject { put("check_room_id", roomId); put("room_password", password) },
        ).decodeAs<JsonElement>()
        require(unlockedUntil !== JsonNull) { "Incorrect password" }
    }
    suspend fun setRoomPassword(roomId: String, password: String) { client.postgrest.rpc("set_personal_room_password", buildJsonObject { put("check_room_id", roomId); put("new_password", password) }) }
    suspend fun lockRoom(roomId: String) { client.postgrest.rpc("lock_personal_room", buildJsonObject { put("check_room_id", roomId) }) }
    suspend fun removeRoomPassword(roomId: String) { client.postgrest.rpc("remove_personal_room_password", buildJsonObject { put("check_room_id", roomId) }) }

    suspend fun stories(): List<Story> {
        val uid = requireNotNull(userId)
        val rows = client.from("stories").select {
            filter { gt("expires_at", java.time.Instant.now().toString()) }
            order("created_at", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
        }.decodeList<Story>()
        val authors = client.from("profiles").select(Columns.list("id", "name", "avatar_url", "username", "created_at")).decodeList<Profile>().associateBy { it.id }
        val views = client.from("story_views").select().decodeList<StoryView>()
        return rows.map { story ->
            val author = authors[story.authorId]
            story.copy(
                authorName = author?.name ?: "Chat member",
                authorAvatar = author?.avatarUrl,
                mediaUrl = story.mediaPath?.let { runCatching { client.storage.from("story-media").createSignedUrl(it, 3600.seconds) }.getOrNull() },
                viewed = views.any { it.storyId == story.id && it.userId == uid },
            )
        }
    }
    suspend fun addTextStory(text: String, color: String) {
        val now = java.time.Instant.now()
        val value = buildJsonObject { put("author_id", requireNotNull(userId)); put("media_type", "text"); put("caption", text.take(500)); put("background_color", color); put("expires_at", now.plusSeconds(86400).toString()) }
        client.from("stories").insert(value)
    }
    suspend fun addMediaStory(caption: String, name: String, type: String, bytes: ByteArray, color: String) {
        require(type.startsWith("image/") || type.startsWith("video/")) { "Choose a photo or video" }
        require(bytes.size <= 100 * 1024 * 1024) { "Story media must be 100 MB or smaller" }
        val uid = requireNotNull(userId)
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "-").takeLast(100)
        val path = "$uid/${java.util.UUID.randomUUID()}-$safeName"
        client.storage.from("story-media").upload(path, bytes) { upsert = false; contentType = ContentType.parse(type) }
        runCatching {
            client.from("stories").insert(buildJsonObject {
                put("author_id", uid); put("media_path", path)
                put("media_type", if (type.startsWith("video/")) "video" else "image")
                if (caption.isBlank()) put("caption", kotlinx.serialization.json.JsonNull) else put("caption", caption.take(500))
                put("background_color", color)
            })
        }.onFailure { client.storage.from("story-media").delete(path); throw it }
    }
    suspend fun markStoryViewed(storyId: String) = client.from("story_views").upsert(StoryView(storyId, requireNotNull(userId))) { onConflict = "story_id,user_id" }
    suspend fun deleteStory(story: Story) {
        client.from("stories").delete { filter { eq("id", story.id) } }
        story.mediaPath?.let { client.storage.from("story-media").delete(it) }
    }
}
