# Story 3.6: TVDB Authentication and Token Lifecycle

Status: ready-for-dev

<!-- Generated through the BMAD create-story workflow rules from approved planning artifacts. -->

## Story

As a media library user,
I want TVDB authentication to work reliably in the background,
so that metadata lookups fail clearly instead of silently corrupting matches.

## Acceptance Criteria

1. Given valid TVDB credentials are configured
   When TVDB-dependent lookup starts
   Then Episort authenticates using TVDB v4 login with project API key and optional subscriber PIN where required
   And subsequent calls use a Bearer token
   And expired tokens are refreshed or reacquired
   And invalid authentication blocks TVDB-dependent organization with a recoverable error
   And API keys, PINs, and bearer tokens are never logged

## Tasks / Subtasks

- [ ] Add adapter/mapper tests using fake TVDB responses; avoid live-network unit tests. (AC: #1)
- [ ] Implement provider DTOs inside `tvdb.dto` and map them to domain objects before planning logic sees them. (AC: #1)
- [ ] Represent recoverable TVDB errors, retry, token refresh, and fallback titles explicitly. (AC: #1)
- [ ] Keep lookup and matching non-mutating and advisory until review validation. (AC: #1)
- [ ] Update README or developer notes only if this story introduces or changes a developer-facing command or setup step. (AC: #1)
- [ ] Run relevant tests and record any manual verification steps in the Dev Agent Record. (AC: #1)

## Dev Notes

### Requirements Coverage

- Epic: 3 - Resolution TVDB et matching media
- Epic goal: Users can resolve series and movies through TVDB, choose correct candidates, retrieve metadata, manage episode orders, specials, homonyms, and fallback titles.
- FRs covered by this epic: FR15, FR16, FR17, FR18, FR19, FR20, FR21, FR22, FR23, FR24, FR25, FR26, FR27, FR31, FR74, FR77
- Story source: `_bmad-output/planning-artifacts/epics.md`, Story 3.6

### Technical Requirements

TVDB is metadata authority. Use Java 21 `HttpClient`, Jackson for JSON mapping, typed errors, and provider-prefixed DTOs. Candidate selection and metadata retrieval do not validate patterns or authorize execution.

### Architecture Compliance

- Responsible packages: `tvdb`, `matching`, `workflow`, `config`.
- Use ports-and-adapters inside the JavaFX desktop process: UI -> workflow services -> domain ports -> infrastructure adapters.
- Domain/planning/filesystem/persistence/TVDB/AI code must not import JavaFX.
- Use typed application errors with `code`, `severity`, `message`, `recoverable`, and optional `details` for recoverable workflow failures.
- Keep secret redaction at logging and diagnostic boundaries.
- Keep scan, match, plan, validation, and execution as separate phases.

### File Structure Requirements

- Production code belongs under `src/main/java/com/episort/...` in the responsible package listed above.
- Tests belong under `src/test/java/com/episort/...` mirroring production package structure.
- UI resources belong under `src/main/resources` or `assets` only when the story introduces UI assets/styles.
- Do not place runtime settings, logs, credentials, SQLite DBs, generated TVDB tokens, or media paths inside the repository or media workspace.

### Testing Requirements

Cover candidate mapping, homonym identity, order modes, fallback titles, one-file-to-one-match constraints, retry/token paths, and no secret logging.

- Test files must end with `Test`.
- Filesystem tests must use temporary directories.
- Do not run tests against real media folders.
- Add or update tests before changing sorting, matching, planning, naming, validation, persistence, or filesystem behavior.

### Previous Story Intelligence

Review `3-5-episode-and-movie-match-proposals.md` before implementation. Reuse its established file locations, test patterns, and decisions; do not duplicate or contradict them.

### Dependency Rules

May depend only on completed earlier stories in this same epic and prior epics. Do not rely on future stories.

### Latest Technical Information

- Gradle Application plugin: use the current Gradle Application plugin behavior for JVM apps and distributions; it implicitly applies Java and Distribution plugins. Source: https://docs.gradle.org/current/userguide/application_plugin.html
- OpenJFX Gradle plugin: architecture selected `org.openjfx.javafxplugin` version `0.1.0`, listed as latest on the Gradle Plugin Portal. Source: https://plugins.gradle.org/plugin/org.openjfx.javafxplugin
- JUnit: use JUnit Jupiter through Gradle; current official user guide checked for JUnit 5.12.0. Source: https://docs.junit.org/5.12.0/user-guide/index.html
- TVDB v4 auth: login returns a token; use API key and subscriber PIN when required, then authorize subsequent calls with the token. Source: https://support.thetvdb.com/kb/faq.php?id=78
- SQLite JDBC: Maven Central currently lists `org.xerial:sqlite-jdbc:3.53.0.0`; pin during the persistence story that first needs SQLite. Source: https://central.sonatype.com/artifact/org.xerial/sqlite-jdbc
- JNA: Maven Central currently lists `net.java.dev.jna:jna:5.18.1`; pin during the Windows Credential Manager story that first needs JNA. Source: https://central.sonatype.com/artifact/net.java.dev.jna/jna


### Anti-Patterns to Avoid

- Do not rename, move, create, or delete media files unless this story is in Epic 6 and the approved operation-plan execution rules are satisfied.
- Do not let JavaFX controllers call TVDB, AI, persistence, or filesystem executors directly.
- Do not infer validation from confidence.
- Do not let AI output validate patterns, approve plans, or authorize filesystem operations.
- Do not log TVDB API keys, subscriber PINs, bearer tokens, raw credentials, or unnecessary private media metadata.
- Do not create broad database schema, infrastructure, or UI surfaces beyond what this story needs.

### References

- `_bmad-output/planning-artifacts/epics.md`
- `_bmad-output/planning-artifacts/prd.md`
- `_bmad-output/planning-artifacts/architecture.md`
- `AGENTS.md`
- Gradle Application plugin: https://docs.gradle.org/current/userguide/application_plugin.html
- OpenJFX Gradle plugin: https://plugins.gradle.org/plugin/org.openjfx.javafxplugin
- JUnit User Guide: https://docs.junit.org/5.12.0/user-guide/index.html
- TVDB API authentication notes: https://support.thetvdb.com/kb/faq.php?id=78
- SQLite JDBC artifact: https://central.sonatype.com/artifact/org.xerial/sqlite-jdbc
- JNA artifact: https://central.sonatype.com/artifact/net.java.dev.jna/jna

## Project Structure Notes

- Follow the architecture document's package-by-responsibility structure.
- If current repository structure differs because earlier stories have not created files yet, create only the minimum directories/files this story needs.
- Record any intentional variance in the Dev Agent Record before marking implementation complete.

## Dev Agent Record

### Agent Model Used

TBD by dev-story agent.

### Debug Log References

### Completion Notes List

### File List
