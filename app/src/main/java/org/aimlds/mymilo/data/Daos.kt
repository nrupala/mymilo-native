package org.aimlds.mymilo.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM sessions WHERE dirty = 1")
    suspend fun dirtySessions(): List<SessionEntity>
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY localId ASC")
    fun observeForSession(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY localId ASC")
    suspend fun forSession(sessionId: String): List<MessageEntity>

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)

    @Query("SELECT * FROM messages WHERE dirty = 1 ORDER BY localId ASC")
    suspend fun dirtyMessages(): List<MessageEntity>

    /** Full-text-ish search over local history (offline). */
    @Query(
        "SELECT * FROM messages WHERE content LIKE '%' || :query || '%'" +
            " ORDER BY createdAt DESC LIMIT 50"
    )
    suspend fun search(query: String): List<MessageEntity>
}

@Dao
interface SkillDao {
    @Query("SELECT * FROM skills")
    suspend fun all(): List<SkillEntity>

    @Query("SELECT * FROM skills")
    fun observeAll(): Flow<List<SkillEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(skills: List<SkillEntity>)

    @Query("DELETE FROM skills")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM skills")
    suspend fun count(): Int
}

@Dao
interface SettingDao {
    @Query("SELECT value FROM settings WHERE key = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)
}

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY createdAt ASC")
    fun observeSources(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun source(id: String): SourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSource(source: SourceEntity)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun deleteSource(id: String)

    @Query("SELECT * FROM source_tokens ORDER BY createdAt ASC")
    fun observeTokens(): Flow<List<SourceTokenEntity>>

    @Query("SELECT * FROM source_tokens WHERE sourceId = :sourceId ORDER BY createdAt ASC")
    suspend fun tokensFor(sourceId: String): List<SourceTokenEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertToken(token: SourceTokenEntity)

    @Query("DELETE FROM source_tokens WHERE id = :id")
    suspend fun deleteToken(id: String)

    @Query("DELETE FROM source_tokens WHERE sourceId = :sourceId")
    suspend fun deleteTokensFor(sourceId: String)
}

@Database(
    entities = [
        SessionEntity::class, MessageEntity::class, SkillEntity::class,
        SettingEntity::class, SourceEntity::class, SourceTokenEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class MiloDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao
    abstract fun messages(): MessageDao
    abstract fun skills(): SkillDao
    abstract fun settings(): SettingDao
    abstract fun sources(): SourceDao

    companion object {
        /** v1 → v2 (app v0.7.0): messages gain the sources column.
         *  A real migration — the user's threads are never wiped. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE messages ADD COLUMN sourcesJson TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /** v2 → v3 (app v0.8.0): skills gain the catalogue fields. */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE skills ADD COLUMN category TEXT NOT NULL DEFAULT 'More skills'"
                )
                db.execSQL(
                    "ALTER TABLE skills ADD COLUMN blurb TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE skills ADD COLUMN example TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /** v3 → v4 (Skill Pair Program): skills gain pair data. */
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE skills ADD COLUMN archetype TEXT NOT NULL DEFAULT 'knowledge'"
                )
                db.execSQL(
                    "ALTER TABLE skills ADD COLUMN layoutJson TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /** v4 → v5 (Sources & Vault): sources + their token rows,
         *  and a per-chat brain choice on sessions. New tables are
         *  created empty; existing chats keep brain = aetheris. */
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sources` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, `baseUrl` TEXT NOT NULL, " +
                        "`model` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `source_tokens` (" +
                        "`id` TEXT NOT NULL, `sourceId` TEXT NOT NULL, " +
                        "`label` TEXT NOT NULL, `active` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "ALTER TABLE sessions ADD COLUMN brain TEXT NOT NULL DEFAULT 'aetheris'"
                )
            }
        }

        fun build(context: Context): MiloDatabase =
            Room.databaseBuilder(context, MiloDatabase::class.java, "mymilo.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
    }
}
