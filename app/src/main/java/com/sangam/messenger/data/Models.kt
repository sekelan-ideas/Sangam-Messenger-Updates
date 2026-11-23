package com.sangam.messenger.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Rows as stored in Supabase. The server only ever sees ciphertext. */
object Models {

    @Serializable
    data class Profile(
        val id: String,
        val username: String,
        val display_name: String,
        @SerialName("created_at") val createdAt: String? = null,
        val avatar_url: String? = null,
        val email: String? = null,
        @SerialName("last_seen_at") val lastSeenAt: String? = null,
        @SerialName("typing_for") val typingFor: String? = null,
        @SerialName("typing_until") val typingUntil: String? = null,
        val status: String? = null
    )

    @Serializable
    data class PreKeyBundleRow(
        val user_id: String,
        val registration_id: Int,
        val identity_key: String,
        val signed_prekey_id: Int,
        val signed_prekey: String,
        val signed_prekey_signature: String,
        val one_time_prekeys: List<String>? = null
    )

    @Serializable
    data class MessageRow(
        val id: String,
        val sender_id: String,
        val recipient_id: String,
        val ciphertext: String,
        val created_at: String,
        val delivered_at: String? = null,
        val read_at: String? = null,
        val deleted: Boolean? = null,
        @SerialName("group_id") val group_id: String? = null,
        @SerialName("client_msg_id") val client_msg_id: String? = null
    )

    /** A group. The server only ever stores the name, an optional
     *  description, and an encrypted photo. */
    @Serializable
    data class GroupRow(
        val id: String,
        val name: String,
        val description: String? = null,
        @SerialName("photo_path") val photoPath: String? = null,
        @SerialName("photo_key") val photoKey: String? = null,
        @SerialName("photo_iv") val photoIv: String? = null,
        @SerialName("created_by") val createdBy: String,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("members_can_add") val membersCanAdd: Boolean = true,
        @SerialName("leaders_only") val leadersOnly: Boolean = false
    )

    /** One phone or tablet signed in to an account, with its own Signal prekey bundle. */
    @Serializable
    data class DeviceRow(
        val id: String,
        @SerialName("user_id") val userId: String,
        val label: String? = null,
        @SerialName("device_key") val deviceKey: Int = 1,
        @SerialName("registration_id") val registrationId: Int? = null,
        @SerialName("identity_key") val identityKey: String? = null,
        @SerialName("signed_prekey_id") val signedPrekeyId: Int? = null,
        @SerialName("signed_prekey") val signedPrekey: String? = null,
        @SerialName("signed_prekey_signature") val signedPrekeySignature: String? = null,
        @SerialName("one_time_prekeys") val oneTimePrekeys: List<String>? = null,
        @SerialName("created_at") val createdAt: String? = null
    )

    /** Ephemeral handshake row so the mobile app can scan a Desktop QR and log it in. */
    @Serializable
    data class DesktopPairingRow(
        val code: String,
        val email: String? = null,
        val token: String? = null,
        @SerialName("created_at") val createdAt: String? = null
    )

    /**
     * One person in a group, with the role they hold. The person who made
     * the group is the Leader; up to two others can be Co-leaders.
     */
    data class MemberUi(
        val id: String,
        val name: String,
        val leader: Boolean = false,
        val coLeader: Boolean = false
    ) {
        val role: String get() = when {
            leader -> "Leader"
            coLeader -> "Co-leader"
            else -> ""
        }
    }

    @Serializable
    data class GroupMemberRow(
        @SerialName("group_id") val groupId: String,
        @SerialName("user_id") val userId: String,
        @SerialName("is_admin") val isAdmin: Boolean = false,
        @SerialName("joined_at") val joinedAt: String? = null
    )

    /** Plaintext envelope that travels inside the Signal Protocol ciphertext. */
    @Serializable
    data class Envelope(
        val t: String,          // "t"=text, "i"=image, "c"=time capsule, "r"=reaction
        val b: String? = null,  // text body / reaction emoji
        val u: String? = null,  // image path / capsule unlock (millis) / reaction target id
        val k: String? = null,  // AES key (base64, images)
        val v: String? = null,  // AES IV (base64, images)
        val q: String? = null,  // reply-to message id
        val p: String? = null,  // short quote of the replied-to message
        val w: String? = null,  // video thumbnail path
        val x: String? = null,  // video thumbnail key (base64)
        val y: String? = null,  // video thumbnail IV (base64)
        val z: Long? = null,    // disappear after this many seconds (absent = keep)
        val o: Long? = null,    // sealed until this time (millis) - a time capsule
        val once: Boolean? = null  // open once, then it is gone
    )

    /** A message written offline, waiting in the local outbox until the network returns. */
    @Serializable
    data class PendingMsg(
        val id: String,
        val text: String,
        val at: Long,
        val replyTo: String? = null,
        val quote: String? = null,
        /** Set when this queued message belongs to a group. */
        val members: List<String>? = null
    )
}

/** A message after local decryption, ready to display. */
data class UiMessage(
    val id: String,
    val peerId: String,
    val mine: Boolean,
    val type: String,
    val text: String?,
    val imagePath: String?,
    val imageKey: String?,
    val imageIv: String?,
    val createdAt: Long,
    val delivered: Boolean,
    val read: Boolean,
    val decryptFailed: Boolean = false,
    val unlockAt: Long? = null,
    val reactionTo: String? = null,
    val replyTo: String? = null,
    val quote: String? = null,
    val edited: Boolean = false,
    val editOf: String? = null,
    val thumbPath: String? = null,
    val thumbKey: String? = null,
    val thumbIv: String? = null,
    /** True while an offline message is still waiting to be sent. */
    val pending: Boolean = false,
    /** Who actually wrote it - shown on incoming bubbles in a group. */
    val senderId: String? = null,
    val senderName: String? = null,
    /** Non-null when this message belongs to a group chat. */
    val groupId: String? = null,
    /** Shared id of every member-copy of one group message. */
    val clientMsgId: String? = null,
    /** Group read/delivered tallies (members who have received / read). */
    val groupDelivered: Int = 0,
    val groupRead: Int = 0,
    val groupTotal: Int = 0,
    /** True when this message arrived over Bluetooth (Nearby mode). */
    val viaNearby: Boolean = false,
    /** True when this photo or video can be opened only once. */
    val viewOnce: Boolean = false
)

/** One conversation inside a backup file. */
@Serializable
data class BackupMessage(
    val mine: Boolean,
    val at: Long,
    val type: String,
    val text: String? = null
)

@Serializable
data class BackupPayload(
    val envelopes: Map<String, String> = emptyMap(),
    val identity: String? = null,
    val registrationId: Int = 0
)

@Serializable
data class BackupRow(
    @SerialName("user_id") val userId: String,
    val blob: String,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class BackupConversation(
    val name: String,
    val username: String = "",
    val msgs: List<BackupMessage> = emptyList()
)

/** Summary row for the chats list. */
@Serializable
data class ChatSummary(
    val peerId: String,
    val peerName: String,
    val peerUsername: String,
    val lastPreview: String,
    val lastAt: Long,
    val mine: Boolean,
    val unread: Int,
    val avatarUrl: String? = null,
    val online: Boolean = false,
    val typing: Boolean = false,
    /** True when this row is a group rather than a one-to-one chat. */
    val isGroup: Boolean = false,
    val memberCount: Int = 0
)

@Serializable
data class AbuseReportRow(
    @SerialName("reporter_id") val reporterId: String,
    val kind: String,
    @SerialName("target_id") val targetId: String,
    val note: String? = null
)

/** One row of public.calls - a ringing, active or finished call. */
@Serializable
data class CallRow(
    val id: String,
    val caller: String,
    val callee: String,
    val kind: String = "audio",
    val status: String = "ringing",
    @SerialName("created_at") val createdAt: String? = null
)

/** What goes into a new call row. */
@Serializable
data class CallInsert(
    val caller: String,
    val callee: String,
    val kind: String
)

/** One 300ms G.711 u-law audio frame for an active call. */
@Serializable
data class CallChunkRow(
    val id: Long = 0,
    @SerialName("call_id") val callId: String,
    @SerialName("from_user") val sender: String,
    val seq: Int,
    @SerialName("payload") val pcmB64: String
)

@Serializable
data class CallChunkInsert(
    @SerialName("call_id") val callId: String,
    @SerialName("from_user") val sender: String,
    val seq: Int,
    @SerialName("payload") val pcmB64: String
)