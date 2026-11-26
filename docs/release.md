# Release runbook

## Artifacts

| Asset | Gradle task | Signed with | Used by |
| --- | --- | --- | --- |
| `tsuyu-X.Y.Z-standalone.apk` | `assembleStandaloneRelease` | release key | GitHub downloads and the in-app updater |
| `tsuyu.apk` | same file | release key | updaters of versions before 0.4, which only know this name |
| `tsuyu-X.Y.Z-store.apk` | `assembleStoreRelease` | release key | F-Droid reference binary |
| `tsuyu-X.Y.Z-store.aab` | `bundleStoreRelease` | Play upload key | Google Play submission |
| `SHA256SUMS` | release workflow | | checksums of the four files |

All come from the same tagged revision and carry the same package, versionName and versionCode. The APKs are universal: one file for every ABI. Gradle writes them as `tsuyu-X.Y.Z-<variant>-release[-unsigned].apk` and `tsuyu-X.Y.Z-store-release.aab`. The in-app updater takes `tsuyu-X.Y.Z-standalone.apk` and falls back to `tsuyu.apk`; keep publishing `tsuyu.apk` until no supported installation predates 0.4.

## Prepare

1. Choose the version `X.Y.Z` or `X.Y.Z-(alpha|beta|rc).N`.
2. Update `version.properties`: `versionName` and the matching `versionCode` (the build prints the expected code when they differ).
3. Run `./gradlew test lint assembleRelease bundleStoreRelease`, then `scripts/check-artifacts.sh app/build/outputs/apk/standalone/release/tsuyu-X.Y.Z-standalone-release-unsigned.apk app/build/outputs/apk/store/release/tsuyu-X.Y.Z-store-release-unsigned.apk app/build/outputs/bundle/storeRelease/tsuyu-X.Y.Z-store-release.aab`.
4. Merge the version bump to `main` like any change.

## Publish

1. Create a signed tag `vX.Y.Z` on the bump commit and push it. Only the owner tags.
2. The `release` workflow, in the `production` environment, checks that the tag matches `version.properties`, builds and signs the artifacts, verifies the APK signers against the release key, the bundle signer against the upload key and the store artifacts against updater code, then publishes the release.
3. A failed run leaves at most a draft: fix the cause and run the workflow again for the same tag. A published release is never overwritten; publish a newer version instead.

## Signing secrets

The `production` environment needs two sets:

- release key: `TSUYU_KEYSTORE_BASE64`, `TSUYU_STORE_PASSWORD`, `TSUYU_KEY_ALIAS`, `TSUYU_KEY_PASSWORD`;
- Play upload key: `TSUYU_UPLOAD_KEYSTORE_BASE64`, `TSUYU_UPLOAD_STORE_PASSWORD`, `TSUYU_UPLOAD_KEY_ALIAS`, `TSUYU_UPLOAD_KEY_PASSWORD`.

Expected certificates are kept outside the secrets, so swapped or wrong keystores are detected:

- `.github/signing/release-certificate.sha256`: SHA-256 of the release certificate, already used by published releases;
- the `TSUYU_UPLOAD_CERT_SHA256` variable (repository or `production` environment): SHA-256 of the Play upload certificate.

The workflow fails before building when any value is missing, when a keystore does not hold its expected certificate, or when both certificates are the same.

## Verify locally

- `apksigner verify --print-certs <apk>` prints the release certificate digest for both APKs.
- `keytool -printcert -jarfile tsuyu-X.Y.Z-store.aab` prints the upload certificate.
- `sha256sum -c SHA256SUMS` in the download folder.
