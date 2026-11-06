package com.example.domain.registry

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudentRegistryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repo = StudentRegistryRepository(context)

    @Test
    fun `register student without student email assigns registration number`() = runTest {
        val student = repo.registerStudent(
            name = "Ali Raza",
            parentEmail = "parent.ali@example.com",
            studentEmail = null,
            age = 13,
            studentClass = "Grade 8"
        )

        assertNotNull(student)
        assertEquals("Ali Raza", student.name)
        assertEquals("parent.ali@example.com", student.parentEmail)
        assertNull(student.studentEmail)
        assertTrue(student.id.startsWith("REG-2026-"))
        assertTrue(student.registrationNumber.startsWith("REG-2026-"))
        assertEquals(student.id, student.registrationNumber)

        val active = repo.getActiveStudent()
        assertEquals(student.id, active?.id)
    }

    @Test
    fun `register student with student email uses student email as registration id`() = runTest {
        val student = repo.registerStudent(
            name = "Zainab Fatima",
            parentEmail = "family.fatima@example.com",
            studentEmail = "zainab@school.edu",
            age = 15,
            studentClass = "Grade 10"
        )

        assertNotNull(student)
        assertEquals("zainab@school.edu", student.id)
        assertEquals("zainab@school.edu", student.studentEmail)
        assertTrue(student.registrationNumber.startsWith("REG-2026-"))
    }

    @Test
    fun `multiple kids under same parent are linked to same parent group`() = runTest {
        val parentEmail = "parent.khan@example.com"

        val kid1 = repo.registerStudent(
            name = "Hamza Khan",
            parentEmail = parentEmail,
            studentEmail = null,
            age = 14,
            studentClass = "Grade 9"
        )

        val kid2 = repo.registerStudent(
            name = "Sara Khan",
            parentEmail = parentEmail,
            studentEmail = "sara@school.edu",
            age = 16,
            studentClass = "Grade 11"
        )

        val siblings = repo.getStudentsByParentEmail(parentEmail)
        assertEquals(2, siblings.size)
        assertTrue(siblings.any { it.name == "Hamza Khan" })
        assertTrue(siblings.any { it.name == "Sara Khan" })

        val group = repo.getParentGroup(parentEmail)
        assertNotNull(group)
        assertEquals(parentEmail, group?.parentEmail)
    }
}
