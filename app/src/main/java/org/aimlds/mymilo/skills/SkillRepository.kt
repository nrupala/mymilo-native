package org.aimlds.mymilo.skills

import org.aimlds.mymilo.data.SkillDao
import org.aimlds.mymilo.data.SkillEntity
import org.aimlds.mymilo.network.MiloApiClient

/**
 * Skills are dual-homed: the server is the source of truth, the app
 * keeps a synced copy in Room and matches triggers fully on-device.
 * No network round-trip to decide which skill applies.
 */
class SkillRepository(
    private val skillDao: SkillDao,
    private val api: MiloApiClient,
) {
    /** In-memory trigger index, rebuilt from Room on load/sync. */
    @Volatile
    private var index: List<SkillEntity> = emptyList()

    suspend fun loadFromDb() {
        index = skillDao.all()
    }

    /** Download the server bundle and replace the local copy. */
    suspend fun syncFromServer(): Int {
        val bundle = api.service().skillsBundle()
        val skills = bundle.skills.orEmpty().map { dto ->
            SkillEntity(
                name = dto.name,
                description = dto.description ?: "",
                triggers = dto.triggers.orEmpty().joinToString(",") { it.lowercase() },
                content = dto.content ?: "",
            )
        }
        if (skills.isNotEmpty()) {
            skillDao.clear()
            skillDao.upsertAll(skills)
            index = skills
        }
        return skills.size
    }

    /** First skill whose trigger appears in the message (or null). */
    fun match(message: String): SkillEntity? {
        val lowered = message.lowercase()
        for (skill in index) {
            for (trigger in skill.triggers.split(",")) {
                val t = trigger.trim()
                if (t.isNotEmpty() && lowered.contains(t)) return skill
            }
        }
        return null
    }

    fun count(): Int = index.size
}
