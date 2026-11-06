package com.example.domain.registry

import android.content.Context
import com.example.data.local.ParentFamilyGroupEntity
import com.example.data.local.RegisteredStudentEntity
import com.example.data.local.StudentRegistryDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class StudentRegistryRepository(context: Context) {
    private val db = StudentRegistryDatabase.getInstance(context)
    private val studentDao = db.studentRegistryDao()
    private val parentGroupDao = db.parentFamilyGroupDao()

    val activeStudent: Flow<RegisteredStudentEntity?> = studentDao.getActiveStudentFlow()

    suspend fun getActiveStudent(): RegisteredStudentEntity? = studentDao.getActiveStudent()

    suspend fun getStudentsByParentEmail(parentEmail: String): List<RegisteredStudentEntity> {
        val trimmed = parentEmail.trim().lowercase()
        if (trimmed.isBlank()) return emptyList()
        return studentDao.getStudentsByParentEmail(trimmed)
    }

    fun getStudentsByParentEmailFlow(parentEmail: String): Flow<List<RegisteredStudentEntity>> {
        val trimmed = parentEmail.trim().lowercase()
        return studentDao.getStudentsByParentEmailFlow(trimmed)
    }

    fun getAllStudentsFlow(): Flow<List<RegisteredStudentEntity>> = studentDao.getAllStudentsFlow()

    suspend fun getParentGroup(parentEmail: String): ParentFamilyGroupEntity? =
        parentGroupDao.getGroupByParentEmail(parentEmail.trim().lowercase())

    /**
     * Registers a new student:
     * - Uses studentEmail as registration ID if provided; otherwise assigns unique registration number.
     * - Checks if parents have other kids in the database:
     *   - If yes: adds student to existing parent group.
     *   - If no: creates a new parent email based group.
     * - Deactivates any previously active student and marks this one active.
     */
    suspend fun registerStudent(
        name: String,
        parentEmail: String,
        studentEmail: String?,
        age: Int,
        studentClass: String
    ): RegisteredStudentEntity {
        val cleanParentEmail = parentEmail.trim().lowercase()
        val cleanStudentEmail = studentEmail?.trim()?.lowercase()?.takeIf { it.isNotBlank() }

        // Assign registration number (e.g., REG-2026-XXXXX)
        val randomSuffix = (10000..99999).random()
        val generatedRegNumber = "REG-2026-$randomSuffix"

        // If student email is available, that should be used for registration, otherwise registration number
        val registrationId = if (!cleanStudentEmail.isNullOrBlank()) cleanStudentEmail else generatedRegNumber

        // Check if parent group exists or create one
        val existingKids = studentDao.getStudentsByParentEmail(cleanParentEmail)
        val parentGroupId = "group_${cleanParentEmail.replace(Regex("[^a-zA-Z0-9]"), "_")}"

        if (existingKids.isEmpty()) {
            val groupName = "${cleanParentEmail.substringBefore("@").replaceFirstChar { it.uppercase() }}'s Family Group"
            parentGroupDao.insertGroup(
                ParentFamilyGroupEntity(
                    parentEmail = cleanParentEmail,
                    groupName = groupName,
                    createdAtMs = System.currentTimeMillis(),
                    lastUpdatedMs = System.currentTimeMillis()
                )
            )
        } else {
            // Update last updated on parent group
            val existingGroup = parentGroupDao.getGroupByParentEmail(cleanParentEmail)
            if (existingGroup != null) {
                parentGroupDao.insertGroup(existingGroup.copy(lastUpdatedMs = System.currentTimeMillis()))
            }
        }

        // Deactivate previous active students
        studentDao.deactivateAllStudents()

        val studentEntity = RegisteredStudentEntity(
            id = registrationId,
            name = name.trim().ifBlank { "Student" },
            parentEmail = cleanParentEmail,
            studentEmail = cleanStudentEmail,
            registrationNumber = generatedRegNumber,
            age = age,
            studentClass = studentClass.trim(),
            parentGroupId = parentGroupId,
            createdAtMs = System.currentTimeMillis(),
            diagnosticTaken = false,
            diagnosticScorePercent = 0.0,
            diagnosticLevel = "",
            diagnosticFeedback = "",
            active = true
        )

        studentDao.insertStudent(studentEntity)
        return studentEntity
    }

    suspend fun switchActiveStudent(studentId: String) {
        studentDao.deactivateAllStudents()
        studentDao.activateStudent(studentId)
    }

    suspend fun updateDiagnosticResult(
        studentId: String,
        scorePercent: Double,
        level: String,
        feedback: String
    ) {
        studentDao.updateDiagnosticResult(studentId, scorePercent, level, feedback)
    }
}
