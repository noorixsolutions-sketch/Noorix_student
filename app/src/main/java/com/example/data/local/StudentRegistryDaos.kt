package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentRegistryDao {
    @Query("SELECT * FROM registered_students WHERE active = 1 ORDER BY createdAtMs DESC LIMIT 1")
    fun getActiveStudentFlow(): Flow<RegisteredStudentEntity?>

    @Query("SELECT * FROM registered_students WHERE active = 1 ORDER BY createdAtMs DESC LIMIT 1")
    suspend fun getActiveStudent(): RegisteredStudentEntity?

    @Query("SELECT * FROM registered_students WHERE id = :id LIMIT 1")
    suspend fun getStudentById(id: String): RegisteredStudentEntity?

    @Query("SELECT * FROM registered_students WHERE parentEmail = :parentEmail ORDER BY createdAtMs ASC")
    suspend fun getStudentsByParentEmail(parentEmail: String): List<RegisteredStudentEntity>

    @Query("SELECT * FROM registered_students WHERE parentEmail = :parentEmail ORDER BY createdAtMs ASC")
    fun getStudentsByParentEmailFlow(parentEmail: String): Flow<List<RegisteredStudentEntity>>

    @Query("SELECT * FROM registered_students ORDER BY createdAtMs DESC")
    fun getAllStudentsFlow(): Flow<List<RegisteredStudentEntity>>

    @Query("SELECT * FROM registered_students ORDER BY createdAtMs DESC")
    suspend fun getAllStudents(): List<RegisteredStudentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: RegisteredStudentEntity)

    @Update
    suspend fun updateStudent(student: RegisteredStudentEntity)

    @Query("UPDATE registered_students SET active = 0")
    suspend fun deactivateAllStudents()

    @Query("UPDATE registered_students SET active = 1 WHERE id = :id")
    suspend fun activateStudent(id: String)

    @Query("UPDATE registered_students SET diagnosticTaken = 1, diagnosticScorePercent = :score, diagnosticLevel = :level, diagnosticFeedback = :feedback WHERE id = :id")
    suspend fun updateDiagnosticResult(id: String, score: Double, level: String, feedback: String)

    @Query("DELETE FROM registered_students WHERE id = :id")
    suspend fun deleteStudent(id: String)
}

@Dao
interface ParentFamilyGroupDao {
    @Query("SELECT * FROM parent_family_groups WHERE parentEmail = :parentEmail LIMIT 1")
    suspend fun getGroupByParentEmail(parentEmail: String): ParentFamilyGroupEntity?

    @Query("SELECT * FROM parent_family_groups ORDER BY createdAtMs DESC")
    fun getAllGroupsFlow(): Flow<List<ParentFamilyGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: ParentFamilyGroupEntity)
}
