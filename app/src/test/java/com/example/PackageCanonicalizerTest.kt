package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.crypto.PackageCanonicalizer
import com.example.core.crypto.PackageVerifier
import com.example.core.crypto.VerificationStatus
import com.example.data.curriculum.CoursePackageProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Parity test between the Kotlin [PackageCanonicalizer] (app side) and the
 * publisher-side Python canonicalizer in tools/generate_package_signatures.py.
 *
 * The pinned hashes below are the SHA-256 digests of the canonical payloads
 * produced by the PYTHON tool for the shipped assets (verified with
 * `python3 tools/generate_package_signatures.py --verify`). If the Kotlin
 * canonicalizer ever drifts from the Python one, these digests change and the
 * test fails — which would otherwise surface only as "package rejected" at
 * runtime.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PackageCanonicalizerTest {

    private fun provider(): CoursePackageProvider =
        CoursePackageProvider(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun `kotlin canonicalizer matches the python publisher tool for math 4024`() {
        val pkg = provider().createMath4024Package()
        val hash = PackageCanonicalizer.contentHashOf(pkg)

        // Pinned from tools/generate_package_signatures.py (python side):
        assertEquals("219103837bc7760440865b35e5018229923258d73a40a828444cb4962e88d275", hash)
        assertEquals(hash, pkg.manifest.contentHash)
    }

    @Test
    fun `kotlin canonicalizer matches the python publisher tool for physics 5054`() {
        val pkg = provider().createPhysics5054Package()
        val hash = PackageCanonicalizer.contentHashOf(pkg)

        // Pinned from tools/generate_package_signatures.py (python side):
        assertEquals("a70770a35a9b4c8025e8d5645cc3652ac6f9805955c688461fec2e79348ea219", hash)
        assertEquals(hash, pkg.manifest.contentHash)
    }

    @Test
    fun `shipped packages fully verify hash ed25519 and graph integrity`() {
        for (pkg in listOf(provider().createMath4024Package(), provider().createPhysics5054Package())) {
            val status = PackageVerifier.verifyPackage(pkg)
            assertTrue(
                "Expected Valid but got $status for ${pkg.manifest.packageId}",
                status is VerificationStatus.Valid
            )
        }
    }

    @Test
    fun `escaping rules are stable`() {
        assertEquals("a\\\\b", PackageCanonicalizer.esc("a\\b"))
        assertEquals("a\\|b", PackageCanonicalizer.esc("a|b"))
        assertEquals("a\\nb", PackageCanonicalizer.esc("a\nb"))
        assertEquals("a\\rb", PackageCanonicalizer.esc("a\rb"))
        assertEquals("plain", PackageCanonicalizer.esc("plain"))
    }
}
