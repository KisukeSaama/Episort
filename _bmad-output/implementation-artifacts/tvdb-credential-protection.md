# TVDB credential protection

Date: 2026-07-27

## Scope

- Replace plaintext TVDB credential persistence on Windows with user-scoped DPAPI encryption.
- Migrate the legacy `tvdb-credentials.properties` file atomically on first load.
- Keep credential values out of the persisted wrapper and object string representations.
- Preserve the existing credential store interface and application workflow.

## Implementation

- `WindowsDpapiCredentialProtector` calls `CryptProtectData` and `CryptUnprotectData`
  through the existing JNA dependency.
- `FileTvdbCredentialStore` serializes credentials into a versioned binary payload,
  encrypts it with DPAPI, and stores only the format identifier and Base64 ciphertext.
- Legacy `apiKey` and `subscriberPin` properties are read once and immediately
  replaced with the encrypted format.
- Sensitive byte arrays and native buffers are cleared after use.
- Secure persistent storage fails closed on unsupported operating systems; the
  existing environment-variable provider remains available there.
- An unavailable platform vault now resolves as absent optional credentials at
  scan time. Local inventory continues without TVDB enrichment instead of
  being mislabeled as `INPUT_FOLDER_INVALID`.
- TVDB availability is checked once during application startup and rendered as
  a passive Active/Inactive status in Settings. The former manual retest button
  was removed, and cache feedback no longer overwrites the connection signal.

## Verification

- Added unit coverage for encrypted round trips, absence of plaintext values,
  legacy migration, missing files, and redacted object rendering.
- Added a Windows-only integration test using the real current-user DPAPI service.
- Added presentation tests for the localized startup status and for the absence
  of credential/error-detail leakage.
- Added regression coverage for unavailable secure storage, embedded credential
  priority and successful persistent credential resolution.
- Migrated and reloaded the current user-profile credential file successfully.
- Full Gradle test suite passes.

## Security boundary

DPAPI protects credentials at rest against another Windows account and offline file
copying. Code already running as the same Windows user can still ask DPAPI to decrypt
the value; protecting against a compromised user session is outside the desktop
application's trust boundary.
