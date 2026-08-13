package com.chatspace.android.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Profile(
    val id: String,
    val name: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val username: String = "",
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class RoomRow(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("room_type") val roomType: String? = null,
    @SerialName("last_message_id") val lastMessageId: Long? = null,
    @SerialName("last_message_text") val lastMessageText: String? = null,
    @SerialName("last_message_created_at") val lastMessageCreatedAt: String? = null,
    @SerialName("last_author_name") val lastAuthorName: String? = null,
    @SerialName("last_file_name") val lastFileName: String? = null,
    @SerialName("last_file_type") val lastFileType: String? = null,
    @SerialName("last_file_size") val lastFileSize: Long? = null,
)

@Serializable
data class Membership(
    @SerialName("room_id") val roomId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("is_admin") val isAdmin: Boolean = false,
)

data class Room(
    val row: RoomRow,
    val type: String,
    val members: List<Profile>,
    val admins: Set<String>,
    val displayName: String,
)

@Serializable
data class ConversationPreference(
    @SerialName("user_id") val userId: String,
    @SerialName("room_id") val roomId: String,
    @SerialName("is_pinned") val isPinned: Boolean = false,
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("is_muted") val isMuted: Boolean = false,
)

@Serializable
data class Message(
    val id: Long,
    @SerialName("room_id") val roomId: String,
    @SerialName("author_id") val authorId: String,
    val text: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("edited_at") val editedAt: String? = null,
    @SerialName("reply_to") val replyTo: Long? = null,
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("file_type") val fileType: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
    @Transient val fileUrl: String? = null,
)

@Serializable data class MessageLike(@SerialName("message_id") val messageId: Long, @SerialName("user_id") val userId: String)

@Serializable
data class Story(
    val id: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("media_path") val mediaPath: String? = null,
    @SerialName("media_type") val mediaType: String = "text",
    val caption: String? = null,
    @SerialName("background_color") val backgroundColor: String = "#2563EB",
    @SerialName("created_at") val createdAt: String,
    @SerialName("expires_at") val expiresAt: String,
    @Transient val authorName: String = "Chat member",
    @Transient val authorAvatar: String? = null,
    @Transient val mediaUrl: String? = null,
    @Transient val viewed: Boolean = false,
)

@Serializable data class NewMessage(@SerialName("room_id") val roomId: String, @SerialName("author_id") val authorId: String, val text: String, @SerialName("reply_to") val replyTo: Long? = null, @SerialName("file_path") val filePath: String? = null, @SerialName("file_name") val fileName: String? = null, @SerialName("file_type") val fileType: String? = null, @SerialName("file_size") val fileSize: Long? = null)
@Serializable data class StoryView(@SerialName("story_id") val storyId: String, @SerialName("user_id") val userId: String)
@Serializable data class PresenceState(val uid: String, val name: String, @SerialName("online_at") val onlineAt: String)
@Serializable data class TypingEvent(val uid: String, val name: String, val typing: Boolean)
@Serializable data class NewRoom(val name: String, val description: String?, @SerialName("created_by") val createdBy: String, @SerialName("room_type") val roomType: String = "group")
@Serializable data class NewMembership(@SerialName("room_id") val roomId: String, @SerialName("user_id") val userId: String, @SerialName("is_admin") val isAdmin: Boolean = false)
@Serializable data class RowId(val id: String)
@Serializable data class DirectRoomParams(@SerialName("other_user_id") val otherUserId: String)
@Serializable data class RoomAccessParams(@SerialName("check_room_id") val roomId: String)
@Serializable data class UnlockParams(@SerialName("check_room_id") val roomId: String, val password: String)
@Serializable data class RoomAccess(@SerialName("hasPassword") val hasPassword: Boolean = false, val unlocked: Boolean = true, @SerialName("unlockedUntil") val unlockedUntil: String? = null)
@Serializable data class IdentityUpdate(@SerialName("new_name") val name: String, @SerialName("new_username") val username: String, @SerialName("new_phone_number") val phone: String? = null)
@Serializable data class PushToken(val token: String, @SerialName("user_id") val userId: String, val platform: String = "android", @SerialName("device_name") val deviceName: String? = null)
@Serializable data class DeviceLinkApproval(val action: String = "approve", @SerialName("session_id") val sessionId: String, @SerialName("approval_secret") val approvalSecret: String)
