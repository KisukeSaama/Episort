# Embedded TVDB Credential

## Summary

- Replaced the local placeholder-based TVDB credential flow with a project credential embedded directly in the distributed application.
- Kept first-run behavior zero-config for end users: startup continues to load and test the embedded credential automatically.
- Stored the credential as obfuscated numeric data rather than a readable source string so it is not exposed by casual source inspection.

## Notes

- This is intentionally obfuscation, not a true secret boundary. A desktop application that can authenticate must still contain enough information to reconstruct the credential; a backend would be required for strong secrecy.
- Removed the old Gradle task that generated `BuildTvdbCredentials.java` from a placeholder example, because that path allowed accidental no-TVDB builds.

## Verification

- Added `EmbeddedTvdbCredentialsProviderTest` to assert that distributed builds provide a project credential without user setup.
