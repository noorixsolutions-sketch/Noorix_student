package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: EventEntity): Long

    @Query("SELECT * FROM learning_events ORDER BY physicalMs ASC")
    fun getAllEventsFlow(): Flow<List<EventEntity>>

    @Query("SELECT * FROM learning_events ORDER BY physicalMs ASC")
    suspend fun getAllEvents(): List<EventEntity>

    @Query("SELECT * FROM learning_events WHERE studentId = :studentId ORDER BY physicalMs ASC")
    suspend fun getEventsForStudent(studentId: String): List<EventEntity>

    @Query("SELECT COUNT(*) FROM learning_events")
    fun getEventCountFlow(): Flow<Int>

    @Query("DELETE FROM learning_events WHERE studentId = :studentId")
    suspend fun purgeStudentEvents(studentId: String): Int
}

@Dao
interface CoursePackageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackage(pkg: CoursePackageEntity)

    @Query("SELECT * FROM course_packages WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActivePackage(): CoursePackageEntity?

    @Query("SELECT * FROM course_packages WHERE status = 'ACTIVE' LIMIT 1")
    fun getActivePackageFlow(): Flow<CoursePackageEntity?>

    @Query("SELECT * FROM course_packages ORDER BY courseId ASC")
    fun getAllPackagesFlow(): Flow<List<CoursePackageEntity>>

    @Query("SELECT * FROM course_packages WHERE packageId = :packageId LIMIT 1")
    suspend fun getPackageById(packageId: String): CoursePackageEntity?

    @Query("UPDATE course_packages SET status = 'DEPRECATED' WHERE packageId != :activePackageId AND courseId = :courseId")
    suspend fun deprecateOtherVersions(activePackageId: String, courseId: String)

    @Query("UPDATE course_packages SET status = :status WHERE packageId = :packageId")
    suspend fun updateStatus(packageId: String, status: String)

    @Query("DELETE FROM course_packages WHERE packageId = :packageId")
    suspend fun deletePackage(packageId: String)
}

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(item: OfflineSyncQueueEntity): Long

    @Query("SELECT * FROM offline_sync_queue WHERE status = 'PENDING' ORDER BY physicalMs ASC")
    suspend fun getPendingItems(): List<OfflineSyncQueueEntity>

    @Query("SELECT * FROM offline_sync_queue ORDER BY physicalMs DESC")
    fun getAllQueueFlow(): Flow<List<OfflineSyncQueueEntity>>

    @Query("UPDATE offline_sync_queue SET status = :status WHERE eventId = :eventId")
    suspend fun updateStatus(eventId: String, status: String)

    @Query("DELETE FROM offline_sync_queue WHERE status = 'SYNCED'")
    suspend fun clearSynced()
}

@Dao
interface AuditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudit(audit: AuditLogEntity)

    @Query("SELECT * FROM audit_log ORDER BY timestampMs DESC")
    fun getAllAuditsFlow(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_log ORDER BY timestampMs DESC")
    suspend fun getAllAudits(): List<AuditLogEntity>
}

@Dao
interface UserAccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: UserAccountEntity)

    @Query("SELECT * FROM user_accounts WHERE active = 1 LIMIT 1")
    suspend fun getActiveAccount(): UserAccountEntity?

    @Query("DELETE FROM user_accounts")
    suspend fun clear()
}

@Dao
interface ChatMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM agent_chat_messages WHERE agentId = :agentId ORDER BY timestampMs ASC")
    fun getMessagesForAgentFlow(agentId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM agent_chat_messages WHERE agentId = :agentId ORDER BY timestampMs ASC")
    suspend fun getMessagesForAgent(agentId: String): List<ChatMessageEntity>

    @Query("DELETE FROM agent_chat_messages WHERE agentId = :agentId")
    suspend fun clearAgentChat(agentId: String)
}

