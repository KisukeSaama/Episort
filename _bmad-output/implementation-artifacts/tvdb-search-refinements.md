# TVDB search refinements

Date: 2026-08-06

## Scope

- Add an optional four-digit year to manual TVDB identity search.
- Add an optional exact TVDB ID lookup that works without a title.
- Preserve the existing manual identity review and both filesystem validation gates.

## Implementation

- Introduced `TvdbSearchCriteria` so title, year, and TVDB ID have one validated contract.
- Text search forwards TVDB's official `year` parameter; an entered TVDB ID takes priority
  and resolves the corresponding series and movie resources directly.
- Cache keys include the year or exact ID, preventing refined searches from reusing a broader
  cached result.
- The manual-match dialog reuses standard text fields for compact year and ID controls, accepts
  Enter from every field, and keeps validation/loading feedback inside the dialog.
- Added complete French and English strings and documented the controls in the design system.

## Verification

- Automated coverage verifies year query encoding, exact series lookup by ID, criteria
  validation, and year-sensitive cache keys.
- Manual verification: open a manual TVDB match, search a duplicated title with an optional
  year, then clear the title and search the same identity by its numeric TVDB ID.
