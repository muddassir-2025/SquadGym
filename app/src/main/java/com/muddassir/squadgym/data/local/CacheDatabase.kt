package com.muddassir.squadgym.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first cache.
 *
 * Two shapes are cached:
 *  - [CachedMemberEntity] — normalised member rows so lists render instantly and
 *    can be shown while offline (spec §9 / §11).
 *  - [CachedDocumentEntity] — the raw JSON of aggregate responses (dashboard,
 *    attendance page, leaderboard, personal dashboard). Documents are versioned by
 *    a cache key that includes every query parameter, so a filtered view never
 *    shows another filter's data.
 *
 * Cache keys are invalidated on logout (`clearAll`).
 */

@Entity(tableName = "cached_members")
data class CachedMemberEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val email: String,
    val phone: String?,
    val photoUrl: String?,
    val imageVersion: Int,
    val planName: String?,
    val shiftName: String?,
    val membershipStatus: String,
    val endDate: String?,
    val score: Int,
    val archived: Boolean,
    val cachedAt: Long,
)

@Entity(tableName = "cached_documents")
data class CachedDocumentEntity(
    @PrimaryKey val key: String,
    val json: String,
    val cachedAt: Long,
)

@Entity(tableName = "cached_plans")
data class CachedPlanEntity(
    @PrimaryKey val id: String,
    val name: String,
    val durationDays: Int,
    val priceCents: Int?,
    val currency: String,
    val isActive: Boolean,
    val sortOrder: Int,
    val description: String? = null,
)

@Dao
interface CacheDao {
    @Query("SELECT * FROM cached_members ORDER BY fullName COLLATE NOCASE")
    suspend fun members(): List<CachedMemberEntity>

    @Query("SELECT * FROM cached_members ORDER BY fullName COLLATE NOCASE")
    fun observeMembers(): Flow<List<CachedMemberEntity>>

    @Query("SELECT * FROM cached_members WHERE id = :id")
    suspend fun member(id: String): CachedMemberEntity?

    @Upsert
    suspend fun upsertMembers(items: List<CachedMemberEntity>)

    @Query("DELETE FROM cached_members WHERE id IN (:ids)")
    suspend fun deleteMembers(ids: List<String>)

    @Query("SELECT * FROM cached_documents WHERE key = :key")
    suspend fun document(key: String): CachedDocumentEntity?

    @Upsert
    suspend fun putDocument(document: CachedDocumentEntity)

    @Query("SELECT * FROM cached_documents WHERE key = :key")
    fun observeDocument(key: String): Flow<CachedDocumentEntity?>

    @Query("DELETE FROM cached_documents WHERE key = :key")
    suspend fun deleteDocument(key: String)

    @Upsert
    suspend fun upsertPlans(items: List<CachedPlanEntity>)

    @Query("SELECT * FROM cached_plans ORDER BY sortOrder")
    suspend fun plans(): List<CachedPlanEntity>

    @Query("DELETE FROM cached_plans WHERE id NOT IN (:ids)")
    suspend fun deletePlansExcept(ids: List<String>)

    @Query("DELETE FROM cached_members")
    suspend fun clearMembers()

    @Query("DELETE FROM cached_documents")
    suspend fun clearDocuments()

    @Query("DELETE FROM cached_plans")
    suspend fun clearPlans()
}

@Database(
    entities = [CachedMemberEntity::class, CachedDocumentEntity::class, CachedPlanEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class SquadGymDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao

    suspend fun clearAll() {
        cacheDao().clearMembers()
        cacheDao().clearDocuments()
        cacheDao().clearPlans()
    }
}
