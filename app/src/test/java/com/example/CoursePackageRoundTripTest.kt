package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.crypto.PackageCanonicalizer
import com.example.core.crypto.PackageVerifier
import com.example.core.crypto.VerificationStatus
import com.example.core.model.CoursePackage
import com.example.data.curriculum.CoursePackageProvider
import com.example.data.local.CoursePackageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test for the serialize/deserialize round-trip.
 *
 * The old code used `JSONObject.optString(key, "")` for nullable fields, which
 * turns JSON null into the literal string "null" (optString only applies its
 * fallback to MISSING keys). A package saved to the DB and read back would
 * silently corrupt parentVersion / deltaBase / prerequisiteConceptId, and the
 * corruption was then papered over by "null"-guards in the canonicalizer and
 * verifier. The first two tests fail against that old behavior.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoursePackageRoundTripTest {

    private fun provider(): CoursePackageProvider =
        CoursePackageProvider(ApplicationProvider.getApplicationContext<Context>())

    private fun packages(): List<CoursePackage> = listOf(
        provider().createMath4024Package(),
        provider().createPhysics5054Package()
    )

    /** Mirrors LearningRepository.installAndActivatePackage's entity build. */
    private fun toEntity(pkg: CoursePackage): CoursePackageEntity {
        val p = provider()
        return CoursePackageEntity(
            packageId = pkg.manifest.packageId,
            courseId = pkg.manifest.courseId,
            version = pkg.manifest.version,
            subjectName = pkg.manifest.subjectName,
            status = "ACTIVE",
            contentHash = pkg.manifest.contentHash,
            manifestJson = p.serializeManifest(pkg),
            nodesJson = p.serializeNodes(pkg.nodes),
            edgesJson = p.serializeEdges(pkg.edges),
            questionsJson = p.serializeQuestions(pkg.questions),
            resourcesJson = p.serializeResources(pkg.resources)
        )
    }

    @Test
    fun `asset JSON nulls parse as real nulls not literal strings`() {
        for (pkg in packages()) {
            assertNull(pkg.manifest.parentVersion)
            assertNull(pkg.manifest.deltaBase)
            assertNotEquals("null", pkg.manifest.parentVersion)
            assertNotEquals("null", pkg.manifest.deltaBase)
            assertTrue(
                "expected at least one null prerequisite in ${pkg.manifest.packageId}",
                pkg.questions.any { it.prerequisiteConceptId == null }
            )
            for (q in pkg.questions) {
                assertNotEquals("null prereq in ${q.id}", "null", q.prerequisiteConceptId)
                assertFalse(
                    "null string in misconceptions of ${q.id}",
                    q.potentialMisconceptions.contains("null")
                )
            }
        }
    }

    @Test
    fun `serialize then deserialize round-trips identically`() {
        for (pkg in packages()) {
            val back = provider().deserializePackage(toEntity(pkg))
            assertEquals(pkg.manifest, back.manifest)
            assertEquals(pkg.nodes, back.nodes)
            assertEquals(pkg.edges, back.edges)
            assertEquals(pkg.questions, back.questions)
            assertEquals(pkg.resources, back.resources)
            assertEquals(pkg, back)
        }
    }

    @Test
    fun `round-tripped package keeps its content hash and verifies`() {
        for (pkg in packages()) {
            val back = provider().deserializePackage(toEntity(pkg))
            assertEquals(pkg.manifest.contentHash, PackageCanonicalizer.contentHashOf(back))
            assertTrue(
                "round-tripped ${pkg.manifest.packageId} must verify: " +
                    PackageVerifier.verifyPackage(back),
                PackageVerifier.verifyPackage(back) is VerificationStatus.Valid
            )
        }
    }
}
