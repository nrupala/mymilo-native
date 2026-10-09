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

@Database(
    entities = [SessionEntity::class, MessageEntity::class, SkillEntity::class, SettingEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class MiloDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao
    abstract fun messages(): MessageDao
    abstract fun skills(): SkillDao
    abstract fun settings(): SettingDao

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

        fun build(context: Context): MiloDatabase =
            Room.databaseBuilder(context, MiloDatabase::class.java, "mymilo.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
    }
}
