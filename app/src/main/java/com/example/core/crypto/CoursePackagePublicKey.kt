package com.example.core.crypto

import java.util.Base64

/**
 * Noorix curriculum publisher Ed25519 PUBLIC key (single source of truth).
 *
 * Generated offline: python3 tools/sign_course_package.py
 * Seed: "NoorixCurriculumPublisherKey2025" (NOT shipped in the APK).
 *
 * Only this public key ships in the app; the private key never leaves the
 * publisher environment, so a tampered package can never be re-signed on
 * device. See docs/course-package-signing.md.
 */
object CoursePackagePublicKey {

    const val BASE64 = "pzvLXgi6p9ugtgcEQhJ0od14BkfjXFJEeCau0rpGmJA="

    val bytes: ByteArray by lazy {
        Base64.getDecoder().decode(BASE64)
    }
}
