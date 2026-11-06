package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "registered_students")
data class RegisteredStudentEntity(
    @PrimaryKey val id: String, // studentEmail if available, otherwise registrationNumber
    val name: String,
    val parentEmail: String,
    val studentEmail: String? = null,
    val registrationNumber: String,
    val age: Int,
    val studentClass: String,
    val parentGroupId: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val diagnosticTaken: Boolean = false,
    val diagnosticScorePercent: Double = 0.0,
    val diagnosticLevel: String = "", // "Foundational", "Developing", "Proficient", "Advanced"
    val diagnosticFeedback: String = "",
    val active: Boolean = true
)

@Entity(tableName = "parent_family_groups")
data class ParentFamilyGroupEntity(
    @PrimaryKey val parentEmail: String,
    val groupName: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val lastUpdatedMs: Long = System.currentTimeMillis()
)
