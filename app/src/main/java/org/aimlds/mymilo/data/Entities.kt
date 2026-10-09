package org.aimlds.mymilo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local data model — the phone is the primary store.
 * Everything here works fully offline; the server is a sync peer
 * and an escalation target for heavy work.
 */

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** True if created offline and not yet pushed to the server. */
    val dirty: Boolean = false,
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val sessionId: String,
    val role: String, // user | assistant | system
    val content: String,
    val createdAt: Long,
    /** Where the reply came from: local-tool | local-model | server. */
    val origin: String = "server",
    val dirty: Boolean = false,
    /** v0.7.0: the turn's sources as JSON (server v0.40.0 `sources`). */
    val sourcesJson: String = "",
)

/** Skills are dual-homed: synced from the server, matched locally. */
@Entity(tableName = "skills")
data class SkillEntity(
    @PrimaryKey val name: String,
    val description: String,
    /** Comma-separated trigger phrases (lowercase). */
    val triggers: String,
    /** The skill's instruction body, fed to whichever model answers. */
    val content: String,
)

/** Simple key-value settings (server URL, device token, sync state). */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
