# Course package signing

How official course content is signed offline and verified on device.

## Trust model

- The publisher's Ed25519 **private** key lives **only** in the publisher
  environment. It is derived from a seed and never ships in the APK.
- The APK embeds the publisher **public** key alone, in exactly one place:
  `CoursePackagePublicKey` (`core/crypto`). `PackageVerifier` delegates to it.
- Consequence: a tampered package can never be re-signed on device. At worst
  an attacker can delete content (fail closed), never forge it.

## Key inventory

| Item                                 | Location                             | Ships in APK |
|--------------------------------------|--------------------------------------|--------------|
| Seed `NoorixCurriculumPublisherKey2025` | Publisher environment only         | No           |
| Private key (seed-derived)           | Publisher environment only           | No           |
| Public key (base64)                  | `CoursePackagePublicKey.BASE64`      | Yes          |
| Ed25519 math (offline)               | `tools/ed25519.py` (pure Python)     | No           |
| Ed25519 verify (on device)           | Google Tink `Ed25519Verify`          | Yes (dep)    |

## Signing a release (publisher side)

```bash
# Regenerate + sign the bundled assets (writes contentHash + signature):
python3 tools/sign_course_package.py

# Verify the committed assets without rewriting them (wire into CI, below):
python3 tools/sign_course_package.py --verify
```

The script owns the package DATA plus the canonical byte layout
(CANONICAL FORMAT v1): `contentHash = SHA-256(canonical)`, and the Ed25519
signature is over the canonical bytes (not over the hash string).

## Verification chain (on device)

`PackageVerifier.verifyPackage` enforces, in order:

1. Schema version gate (`1.0`).
2. Recompute the deterministic canonical payload (`PackageCanonicalizer`).
3. `SHA-256(canonical)` must equal `manifest.contentHash`.
4. Ed25519 signature over the canonical bytes must verify against
   `CoursePackagePublicKey`.
5. Graph integrity: every edge endpoint, question target/prerequisite and
   resource concept must reference an existing node.

Anything failing any step is rejected at install time
(`LearningRepository.installAndActivatePackage`) and the rejection is written
to the audit log. The student banner only ever says the package couldn't be
verified (`UiCopy.formatInstallResult`) — hashes and signatures are never
rendered.

## Byte-agreement contract

`PackageCanonicalizer` (Kotlin) and `tools/sign_course_package.py` (Python)
MUST agree byte-for-byte. This is pinned three ways:

- `PackageCanonicalizerTest` pins both packages' content hashes and the
  escaping rules.
- `CoursePackageRoundTripTest` pins serialize/deserialize round-trip
  identity (including JSON-null handling) and hash stability.
- CI enforcement — add this step to `.github/workflows/build-apk.yml`, before the build:

  ```yaml
      - name: Verify course package signatures
        run: python3 tools/sign_course_package.py --verify
  ```

  so drift on either side fails the build.

## Rotation

To rotate the publisher key: generate a new seed, re-sign all packages with
the new private key, update `CoursePackagePublicKey.BASE64`, and ship an app
update. Old packages fail step 4 under the new key and are rejected; the app
falls back to the newly bundled assets.
