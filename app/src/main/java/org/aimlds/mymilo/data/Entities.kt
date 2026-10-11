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
    /** Sources & Vault: which brain answers in this chat —
     *  "phone" | "aetheris" | "src:<sourceId>". Chosen per chat,
     *  always visible above the composer. */
    val brain: String = "aetheris",
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
    /** v0.8.0: catalogue fields from the server bundle — where the
     *  skill shelves, what it does in plain words, an example. */
    val category: String = "More skills",
    val blurb: String = "",
    val example: String = "",
    /** Skill Pair Program: which archetype frame renders this
     *  skill's workspace, and its layout block (steps, inputs,
     *  artifact kind) as JSON from the bundle. Defaults keep
     *  unpaired skills on the Knowledge frame. */
    val archetype: String = "knowledge",
    val layoutJson: String = "",
)

/** Simple key-value settings (server URL, device token, sync state). */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/**
 * Sources & Vault: a place Milo can think. Metadata only — the
 * secret itself lives in the Keystore vault, never in Room.
 * Kind: aetheris | openrouter | opencode | custom.
 */
@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    /** OpenAI-compatible base, e.g. https://openrouter.ai/api/v1 */
    val baseUrl: String,
    /** The model this source answers with (user-editable). */
    val model: String,
    val createdAt: Long,
)

/**
 * One named key belonging to a source ("work", "personal", …).
 * Only the label and the active flag live here; the secret is in
 * the vault under "token:<id>". One active key per source.
 */
@Entity(tableName = "source_tokens")
data class SourceTokenEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val label: String,
    val active: Boolean = false,
    val createdAt: Long,
)
