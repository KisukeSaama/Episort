# Section heading refinement

Date: 2026-07-28

## Scope

- Remove the decorative `//` prefix from visible section names.
- Preserve the current dark workstation layout, panel geometry, spacing model,
  typography families and single orange accent.
- Apply the refinement consistently in both French and English.

## Implementation

- Section translation values now contain only their semantic labels.
- `.section-heading`, `.section-heading-accent`, `.sidebar-section-label` and
  `.page-title` use a 1 px orange keyline for hierarchy instead of punctuation.
- Primary screen and detail headings use a brighter label while secondary
  headings remain muted.
- Informational banners use a compact bullet marker rather than the same
  decorative slash motif.
- The shared component rules in `docs/design-system.md` describe the new
  anatomy and remain the source of truth.

## Verification

- `UiTextTest` covers French and English section labels and rejects a
  decorative slash prefix.
- Manual UI verification: inspect Scan, History, Settings, About and the plan
  review at the normal desktop width; confirm labels stay aligned, the 1 px
  keyline remains visible, and no panel geometry or workflow control moves.
