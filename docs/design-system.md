# Episort — Design System

Hyprland-rice inspired desktop UI. Dark glassmorphism, neon-orange accent,
tiling-window-manager layout. JavaFX implementation lives in
`src/main/resources/styles/app.css` and `src/main/java/com/episort/ui/AppShell.java`.

This document is the **source of truth** for the visual language. When adding
new screens or components, reuse these tokens and component classes — do not
introduce parallel styles.

---

## 1. Design principles

1. **Dark glass over near-black.** Surfaces are translucent panels floating
   on a near-black radial-gradient base. Never use opaque mid-grey panels.
2. **One accent.** Orange (`#f97316`) is the only chromatic accent. Status
   colors (green / red) only appear on real status signals, never as decoration.
3. **Tiling layout, no chrome.** Fixed sidebar + top bar + content area. No
   window-frame skeuomorphism, no gradients-as-decoration, no shadows on text.
4. **Mono for system, sans for prose.** Section headings, card titles, codes,
   paths and metric values use JetBrains Mono. Body copy and titles use Inter.
5. **No invented data.** Every label and value must come from a real
   view-model signal or display `—`. Placeholder text is allowed only when the
   feature is genuinely empty (e.g. table placeholder).
6. **Hover glow, not hover fill.** Interactive elements gain a subtle
   `dropshadow` glow on hover, not a heavy background swap.

---

## 2. Tokens

### 2.1 Color

| Token                  | Value                                | Usage                                       |
| ---------------------- | ------------------------------------ | ------------------------------------------- |
| `bg.base.start`        | `#07080b`                            | Top of the root gradient                    |
| `bg.base.end`          | `#0a0c11`                            | Bottom of the root gradient                 |
| `bg.glow.orange`       | `rgba(249, 115, 22, 0.10)`           | Top-left ambient glow (radial)              |
| `bg.glow.cyan`         | `rgba(56, 189, 248, 0.06)`           | Bottom-right ambient glow (radial)          |
| `surface.panel`        | `rgba(20, 23, 30, 0.55)`             | Cards, widgets, settings sections, table    |
| `surface.bar`          | `rgba(13, 15, 20, 0.55)`             | Top bar                                     |
| `surface.sidebar`      | `rgba(13, 15, 20, 0.72)`             | Left sidebar                                |
| `surface.input`        | `rgba(20, 23, 30, 0.85)`             | Text fields, combo boxes                    |
| `border.subtle`        | `rgba(249, 115, 22, 0.16)`           | Default panel border                        |
| `border.bar`           | `rgba(249, 115, 22, 0.10)`           | Top bar / sidebar separator                 |
| `border.input`         | `rgba(249, 115, 22, 0.25)`           | Inputs at rest                              |
| `border.focus`         | `#f97316`                            | Focused inputs / active states              |
| `accent`               | `#f97316`                            | Primary accent (single)                     |
| `accent.hover`         | `#fb923c`                            | Hovered accent foreground                   |
| `accent.glow`          | `rgba(249, 115, 22, 0.45)`           | Focus / hover glow                          |
| `text.primary`         | `#f5f7fb`                            | Headings, card values, body emphasis        |
| `text.secondary`       | `#b8c0cc`                            | Body text, default nav-item                 |
| `text.muted`           | `#8a93a3`                            | Subtitles, descriptions                     |
| `text.faint`           | `#767e8d`                            | Section labels, hints, footers              |
| `status.good`          | `#4ade80`                            | Verified / OK signals                       |
| `status.warn`          | `#fb923c`                            | Setup-required, blocking-but-recoverable    |
| `status.error`         | `#f87171`                            | Failed / unrecoverable                      |

> **Rule.** Never reach for hex values in new CSS. Reuse the class names
> (`.card`, `.banner`, `.status-pill`, `.button.primary`, etc.) which already
> bind these tokens. Hex literals only belong inside `app.css`.

### 2.2 Typography

| Token             | Family                                                            | Used by                                              |
| ----------------- | ----------------------------------------------------------------- | ---------------------------------------------------- |
| `font.sans`       | `"Inter", "Segoe UI", system-ui, "Helvetica Neue", sans-serif`   | Default `*` font, body, titles, controls             |
| `font.mono`       | `"JetBrains Mono", "Cascadia Mono", "Consolas", monospace`        | `.mono`, all system labels, paths, codes, headings   |

Contrast: `text.faint` is the lightest a label may go. Every colour in the
table above clears WCAG AA (4.5:1) against the real translucent panel, not
against the nominal background. Disabled controls are the only exemption.

#### The scale

Six steps, each at least 1.27× the one below it. There is nothing between
them: two roles that need to feel different at the same step are separated by
weight and colour, never by half a pixel.

| Step | Ratio | Role                                                              |
| ---- | ----- | ----------------------------------------------------------------- |
| 10   | —     | **Caption.** Mono, 700, `text.faint`. System labels: section       |
|      |       | headings, card titles, column headers, field labels, status pills, |
|      |       | badges, meta lines.                                                |
| 13   | 1.30  | **Body.** Everything meant to be read: prose, table cells, paths,  |
|      |       | inputs, buttons, list items, hints.                                |
| 17   | 1.31  | **Heading.** 700. Dialog and settings-section titles, metric card  |
|      |       | values outside the grid, empty-state titles, icon glyphs.          |
| 22   | 1.29  | **Title.** 800. Page headings, metric-grid values, overlay titles, |
|      |       | the sidebar brand.                                                 |
| 28   | 1.27  | **Display glyph.** Detail-panel empty-state mark.                  |
| 36   | 1.29  | **Display glyph.** Table empty-state mark.                         |

Anything smaller than 10 or between two steps is a bug. The previous scale ran
10 · 10.5 · 11 · 11.5 · 12 · 12.5 · 13 · 13.5 · 14 · 15 · 16 · 18 · 20 · 22 ·
23 · 28 · 36: seventeen sizes, six of them inside a 3 px band, which is not a
hierarchy but noise.

#### Weight and colour carry the rest

| Weight | Meaning                                                              |
| ------ | -------------------------------------------------------------------- |
| 400    | Body prose and input text.                                            |
| 600    | Interactive labels that are not buttons: nav items, filter chips.     |
| 700    | Captions, buttons, headings, any emphasised body line.                |
| 800    | The 22 step, plus `.button.validate-action` — the one primary action  |
|        | per screen outweighs the other buttons at the same size.              |

At the body step, the ladder is colour: `text.primary` for the value being
read, `text.secondary` for supporting prose, `text.muted` for hints. The
workspace path is body-size but `text.secondary`, because it is context rather
than the thing being read.

### 2.3 Spacing

Padding and gaps are limited to a small scale. Reuse these values rather than
inventing new ones.

| Value | Where it is used                                                    |
| ----- | ------------------------------------------------------------------- |
| 2     | Badge / status-pill vertical padding, sidebar item gap              |
| 4     | Page eyebrow → title rhythm, sidebar item spacing                   |
| 6     | Metric card content rhythm, input vertical padding, table cell (v)  |
| 8     | Card content rhythm, badge horizontal padding, workflow strip gap   |
| 10    | Banner spacing, section heading → content, action rows              |
| 12    | Top bar padding (v) and gap, search box padding (h), settings gap   |
| 14    | Sidebar padding (h), nav item padding (h), metric grid gap, panels  |
| 16    | Card padding (h), banner padding (h)                                |
| 18    | Detail panel padding, screen vertical rhythm, metric card (h)       |
| 20    | Sidebar top padding, settings section padding (v)                   |
| 22    | Top bar padding (h), settings section padding (h)                   |
| 26    | Screen outer padding (v)                                            |
| 30    | Screen outer padding (h)                                            |

Overlays and empty states run wider on purpose: 24 / 28 for the loader and
prerequisite cards, 36 / 48 for the vertical breathing room of the detail-panel
and table empty states.

Nothing outside this list. Values like 11, 13, or 9 crept in one component at
a time and were folded back into their neighbours; a new one needs a reason
written down here.

### 2.4 Radius, border, elevation

| Token                | Value                                                                              | Usage                                |
| -------------------- | ---------------------------------------------------------------------------------- | ------------------------------------ |
| `radius.sm`          | `8px`                                                                              | Buttons, inputs, nav items           |
| `radius.md`          | `10px`                                                                             | Top search field                     |
| `radius.lg`          | `14px`                                                                             | Cards, widgets, table, settings      |
| `radius.pill`        | `999px`                                                                            | Status pill, status dots             |
| `border.width`       | `1px`                                                                              | All borders. Never thicker.          |
| `elevation.panel`    | `dropshadow(gaussian, rgba(0,0,0,0.55), 22, 0.05, 0, 6)`                           | Cards, widgets, table, settings      |
| `elevation.glow.acc` | `dropshadow(gaussian, rgba(249,115,22,0.68), 18, 0, 0, 0)`                         | Focused search inputs                |
| `elevation.glow.btn` | `dropshadow(gaussian, rgba(249,115,22,0.55), 16, 0, 0, 0)`                         | Hovered button                       |
| `elevation.glow.pri` | `dropshadow(gaussian, rgba(249,115,22,0.65), 18, 0, 0, 0)`                         | Hovered primary button               |
| `elevation.dot.good` | `dropshadow(gaussian, rgba(74,222,128,0.55), 8, 0, 0, 0)`                          | `.dot-good`                          |
| `elevation.dot.warn` | `dropshadow(gaussian, rgba(251,146,60,0.55), 8, 0, 0, 0)`                          | `.dot-warn`                          |
| `elevation.dot.err`  | `dropshadow(gaussian, rgba(248,113,113,0.55), 8, 0, 0, 0)`                         | `.dot-error`                         |

---

## 3. Layout

```
┌─────────────────────────────────────────────────────────────────────────┐
│ TOP BAR (12 × 22 padding)                                               │
│  [workspace-chip]  ⌕ [search………]  ⟶  [pill] [Load ▾] [reset] [rescan] [PRIMARY]│
├──────────┬──────────────────────────────────────────────────────────────┤
│ SIDEBAR  │ CONTENT (ScrollPane)                                         │
│ 230px    │   .screen-root (22 × 26 padding, 14 vertical gap)            │
│ fixed    │     • heading // SCAN | // HISTORIQUE | …                    │
│          │     • workflow progress / banner (screen state)               │
│ brand    │     • metric-grid (FlowPane, 16 gap, wraps)                  │
│ NAVI     │     • body:                                                  │
│ Scan     │         wide:    HBox(table, detail-panel 360px)             │
│ History  │         medium:  VBox(table, detail-panel)                   │
│ Settings │                                                              │
└──────────┴──────────────────────────────────────────────────────────────┘
```

- Root: `BorderPane` with `.app-shell.theme-dark`.
- Sidebar: `min/pref/max width = 220 / 230 / 230` at every width. There is no
  icon-only mode: three entries never justify hiding their labels.
- Top bar: `HBox` filling width; height driven by content. Hosts the
  workspace chip, search, status pill, the load menu, the secondary scan
  actions, and the **single primary action** for the current screen. Language
  selection lives in Settings, not here.
- Content: each screen's root is wrapped in a `ScrollPane.content-scroll`
  inside the `viewHost` `StackPane`. Sidebar + top bar stay pinned.

### 3.1 Breakpoints

| Width            | Sidebar        | Detail panel                | Metric cards     |
| ---------------- | -------------- | --------------------------- | ---------------- |
| ≥ 1200 px (wide) | 230 px, labels | Right of the table, 360 px  | One row, no wrap |
| < 1200 px        | 230 px, labels | Below the table, full-width | Wrap as needed   |

`AppShell.STACK_BREAKPOINT` (1200) is the single breakpoint in the shell.

---

## 4. Components

Each component is documented as **Anatomy → Class → Rules**.

### 4.1 Sidebar

- **Anatomy:** brand row → section labels (`.sidebar-section-label`) → nav
  items (`.nav-item`) → flexible spacer (no footer with invented metadata).
- **Classes:** `.sidebar`, `.sidebar-brand`, `.sidebar-brand-name`,
  `.sidebar-section-label`, `.nav-item`, `.nav-item.active`,
  `.nav-item-icon`, `.nav-item-label`.
- **Rules:**
  - Sidebar lists exactly **three** surfaces: **Scan**, **History**,
    **Settings**. Do not add other entries without removing one — keep the
    surface count to three until the navigation model evolves.
  - Do not put a "Safe Mode" card or any other persistent status card in
    the sidebar. The safety model is communicated by in-screen workflow
    progress, banners (§4.10), and pill statuses (§4.2), not by sidebar chrome.
  - One nav-item carries the `active` class at a time.
  - Sidebar branding is intentionally stronger than navigation without
    overpowering it: the logo is 44 px, the brand name takes the 22 title
    step, and nav glyphs the 17 heading step against 13 body labels.
  - Do not add hotkey hints (`[1]`, `ctrl+k`) unless the binding actually
    exists in the application.
  - The nav button's own text is empty (icon and label live in its graphic),
    so every nav item sets `setAccessibleText` with its label.
  - The shell moves initial focus to the load action once the scene attaches.
    Left alone, JavaFX focuses the first traversable node — the search field —
    and the window opens with the search box lit while the step the user needs
    is loading a folder.

### 4.2 Top bar

- **Anatomy:** workspace chip → search field with `⌕` icon → spacer →
  status pill → load menu → secondary scan actions → single **primary
  action** button.
- **Classes:** `.top-bar`, `.workspace-chip`, `.workspace-chip-prefix`,
  `.workspace-chip-label`, `.episort-search-box`, `.episort-search-field`,
  `.episort-search-icon`, `.episort-search-clear`, `.top-search`,
  `.status-pill`.
- **Rules:**
  - The status pill is **derived from `AppShellViewModel.errorCode()`**:
    - empty → the pill is hidden. A permanent green `OK` is decoration; the
      absence of a warning is the signal.
    - present → variant `.warn`, text = error code (e.g. `TVDB_…`)
    - variant `.error` when the code carries `UNAVAILABLE` or `INVALID`.
  - The search prompt must be neutral (`Rechercher…` / `Search…`) until a
    real command palette ships. The Scan screen wires the search field to
    the preview-table filter (substring match on filename / extension).
  - Search fields use `.episort-search-box` + `.episort-search-field`
    with shared `.episort-search-icon` / `.episort-search-clear` controls:
    dark surface, fixed height, a low-intensity orange inactive border, a
    high-contrast orange focus border, and orange glow without an inner white
    text-field flash. TVDB, Scan, and History searches reuse this
    active/focus treatment while keeping separate text state.
  - The workspace chip mirrors the configured workspace (mono, accent
    color). When no workspace is configured it shows the localized empty
    placeholder. Always shows a tooltip with the absolute path.
  - The workspace tooltip is tracked and uninstalled explicitly.
    `Tooltip.uninstall(node, null)` is a no-op, so clearing the workspace
    without it would leave the old path hovering over an empty chip.
  - Nothing else on the Scan screen may be solid orange. The detail panel's
    Apply is a default `.button`, which still outranks the `.ghost` controls
    beside it without reading as a second next step.
  - **Exactly one** primary action exists per screen, owned by the top bar
    (`.button.validate-action`). On Scan it reads `Voir le plan` from the
    first click to the last: the plan pane is the only destination, and it is
    where validating and running happen. On History: `Rafraîchir`. On
    Settings: hidden.
  - Scan folder actions are context gated: before an input folder is loaded,
    only `Charger` is active; `Réinitialiser` and `Réanalyser` stay disabled.
    Once a folder is loaded, those secondary actions become available.

### 4.3 Card (metric)

- **Anatomy:** title (mono, faint) → value.
- **Classes:** `.card`, `.card-title`, `.card-value-mono`. Sizing for the
  metric row comes from `.metric-grid .card`.
- **Rules:**
  - Show the real value, or `—`. Never invent placeholder data
    ("Pending check", "Awaiting selection").
  - Cards carry counts and identifiers, so the value is always mono.
  - Metric cards are a row of equal small tiles, never a hero number with
    supporting stats.

### 4.4 Preview table (see §4.11)

The dashboard `.widget` and the single-column `.activity-table` are gone.
Every multi-column surface — scan preview, history, exact plan — is a
`.preview-table` (§4.11). Status dots survive on their own as `.dot` plus
`.dot-good` / `.dot-warn` / `.dot-error` / `.dot-idle`, used today by the TVDB
section of Settings.

### 4.6 Settings section

- **Anatomy:** title (mono accent) → description (muted) → action row.
- **Classes:** `.settings-section`, `.settings-section-title`,
  `.settings-section-description`, `.workspace-value`.
- **Rules:**
  - Reuse `SettingsPane` rather than re-implementing the layout per story.
  - The workspace path always uses `.workspace-value` (mono, accent-hover color).
  - Settings is a sidebar surface, not a dialog: it replaces the content
    area like Scan and History do.
  - The language combo sits alone on its row, so it carries `setAccessibleText`
    with the localized language label.

### 4.7 Buttons

- **Scan topbar:** use `.menu-button.load-primary` for the `Charger` / `Load`
  dropdown: dark surface, orange border/accent, and visible chevron. It opens
  `Load folder`, `Load files`, `Add folder`, and `Add files`. Use
  `.button.validate-action` for the screen's primary action.
- **Pressed state:** all `.button` and `.menu-button` variants keep a subtle
  `:pressed` state with 1 px downward translation, tighter shadow, and slightly
  stronger orange border/fill.
- **Variants:**
  - default `.button` — orange-on-translucent, glow on hover, the workhorse.
  - `.button.primary` — solid orange, dark text. Use for the single primary
    action of a screen ("Continue", "Test connection").
  - `.button.ghost` — transparent with subtle border, for tertiary actions
    (cancel, dismiss).
- **Rules:**
  - At most **one** `.primary` button per screen.
  - Disabled state is one opacity, `0.45`, for every button and icon button.
    `.button.validate-action` additionally swaps to a neutral grey fill because
    a dimmed solid orange still reads as available.
  - Button typography is centralized in `.button`: Inter/system sans at the
    13 body step, 700 weight, 34 px minimum height, and ellipsis overrun for
    long FR/EN labels. Icon-only buttons override to the 17 step, since a
    single glyph at body size disappears in a 32 px target. Screen-specific buttons should add only semantic variants
    (`.primary`, `.ghost`) rather than inline font rules.

### 4.7c Keyboard focus

- **Classes:** no new class. `:focus-visible` rules live in §10b of `app.css`
  and cover `.button`, `.menu-button`, `.nav-item`, `.filter-chip`,
  `.icon-button`, `.row-checkbox`, and `.preview-table`.
- **Rules:**
  - Every focusable control shows the same orange ring plus glow. A control
    that overrides `-fx-background-color` loses Modena's focus ring, so any
    new control that styles its own background must add its own
    `:focus-visible` rule in the same change.
  - Use `:focus-visible`, never `:focused`: a mouse click must not light up
    the control the pointer just pressed.
  - Solid-orange actions (`.primary`, `.validate-action`) ring in `#fff7ed`,
    since an orange ring on an orange fill is invisible.
  - Search boxes are wrappers: the inner text field is deliberately flat and
    the wrapper carries the state via `:focus-within`. JavaFX spells it
    `:focus-within`, not `:focused-within` — the latter silently matches
    nothing.

### 4.7b Custom confirmation dialogs

- **Anatomy:** undecorated modal `Stage` with a `.custom-dialog` root, title,
  message, and right-aligned action row.
- **Classes:** `.custom-dialog`, `.custom-dialog-title`,
  `.custom-dialog-message`, `.custom-dialog-actions`, plus existing
  `.button.primary` and `.button.ghost`.
- **Rules:**
  - Confirmation flows launched from the app shell must not use native Windows
    title bars or default system dialog chrome.
  - The destructive/confirming action is the only `.primary` button; cancel is
    `.ghost`.
  - Icon-only buttons (`×` close, search clear) always carry both a `Tooltip`
    and `setAccessibleText`. The close glyph is `×` everywhere, never `X`.

### 4.8 Inputs (`text-field`, `password-field`, `combo-box`)

- Translucent surface, subtle orange border at rest, full orange + glow on focus.
- Combo popup uses `.combo-box-popup` styling; no overrides needed per call site.

### 4.9 Section heading (`// LABEL`)

- **Class:** `.section-heading`, optionally with `.section-heading-accent`.
- **Rules:** mono, uppercase, prefixed by `//`. Used to introduce major
  dashboard regions (e.g. `// SCAN`, `// HISTORIQUE`, `// SOURCE`).

### 4.10 Banner (in-screen safety / info)

- **Anatomy:** `HBox` with a mono accent prefix (`//`) → wrapping prose label.
- **Classes:** `.banner` plus exactly one of `.banner-info` (default for
  the Scan safety notice and History audit notice), `.banner-warn`, or
  `.banner-error`. Inner labels: `.banner-icon`, `.banner-text`.
- **Rules:**
  - The Scan safety banner is **mandatory** while a preview is on screen:
    "Aperçu seul — aucun fichier ne sera modifié avant validation." Pull
    the copy from `UiText.scanSafetyBanner(language)`. Never duplicate it
    inside the table or detail panel.
  - Banners do not own primary actions; they describe state. Use
    `.button.primary` in the top bar for the corresponding CTA.
  - Do not stack more than one banner per screen. If the situation
    deserves two messages, escalate the worse status (`.banner-warn`
    overrides `.banner-info`).

### 4.10b Workflow Progress

- **Anatomy:** horizontal `HBox.workflow-steps` inside
  `.workflow-progress`; each step is a numbered pill
  (`.workflow-step-number` + `.workflow-step-label`) separated by
  `.workflow-step-connector`. Optional helper copy uses `.workflow-help`.
- **Classes:** `.workflow-progress`, `.workflow-steps`, `.workflow-step`,
  `.workflow-step.active`, `.workflow-step.complete`,
  `.workflow-step.pending`, `.workflow-step-number`,
  `.workflow-step-label`, `.workflow-step-connector`, `.workflow-help`.
- **Rules:**
  - Use on the Scan page above the metrics to show the current organization
    workflow: Workspace & Scan, Scan des fichiers, Correspondances TVDB,
    Revue du plan, Appliquer.
  - Exactly one step carries `.active`; prior steps may be `.complete`; future
    steps are `.pending`.
  - Keep any safety text secondary. The stepper is the primary element at this
    location.

### 4.11 Preview table (multi-column)

- **Anatomy:** `TableView<ScanRow>` (or `TableView<RunEvent>`) whose first
  column is a `CheckBox` cell, followed by mono columns for paths /
  extensions / metrics and a final status-pill column.
- **Classes:** `.preview-table` on the table; cell variants
  `.cell-mono`, `.cell-muted`, `.cell-good`, `.cell-warn`, `.cell-error`;
  `.selection-cell` on the clickable selection cell; `.row-checkbox` on the
  header and row checkboxes; `.row-status` plus one of `.quiet` /
  `.quiet-muted` / `.ready` / `.preview` / `.warning` / `.ignored` /
  `.replace` / `.conflict` / `.danger` on the status cell.
- **Rules:**
  - **A status is a word, not a badge.** `.row-status` is mono text:
    transparent background, no border, no radius, no exception. The level is
    carried by the ink alone.
  - **Why:** a pill only works while it is the exception in its column, and a
    status column has no exception to promote. Twenty rows all needing the same
    fix is the normal state of a fresh scan, so a pill per row turns the column
    into wallpaper. A conflict already stops the run through the blocking
    banner, the row decision and the header counters; in a column of muted
    greys, a red word is found instantly. Nothing in a data column earns a
    capsule.
  - Ink scale: `#767e8d` (nothing to do) → `#b8c0cc` (will happen) → `#7fae8c`
    (done) → `#d0904f` (needs a fix) → `#fb923c` (approved overwrite) →
    `#f87171` (conflict) → `#ff4d4d` (deletion, worn by nothing else).
  - The frequent hues are desaturated from their Tailwind values (`#d0904f`
    from `#fb923c`, `#7fae8c` from `#4ade80`): a full column of them has to
    stay legible instead of vibrating.
  - Mapping owned by `ScanTableCells.statusStyle` and
    `PlanReviewText.pillVariant`, both covered by tests that assert the split.
  - The full first-column cell toggles the row checkbox, not only the checkbox
    glyph. This must preserve desktop multi-selection behavior.
  - Paths, filenames, extensions, and metrics use `.cell-mono`. Prose
    columns (media type, status pill text) use the default cell style.
  - Long values display the truncated text in the cell **plus a tooltip
    with the full value** so the user always has access to the original.
  - Empty cells render the design-system placeholder `—` with the
    `.cell-muted` style. Never fabricate proposed names, TVDB matches,
    destinations, or confidences before the matching service produces
    them.
  - Empty table state goes through `setPlaceholder(...)`. A screen that
    supplies its own `.table-empty-state` (glyph + title + hint) owns its
    typography; `.preview-table > .placeholder > .label` is a direct-child
    selector on purpose, so it only dresses the bare-`Label` case and cannot
    silently outrank the empty-state classes.
  - Empty-state glyphs must exist in the mono stack. `◫` and `◴` did not and
    rendered as tofu boxes; the scan table uses `≡`, history uses `↺`, and the
    detail panels use `◌`.
  - Each row supplies a `ContextMenu` via `setRowFactory` with the
    actions documented in `UiText.scanContext*` / equivalent. Keep the
    menu short (≤ 5 items) and disable items whose data isn't ready.

### 4.12 Detail panel

- **Anatomy:** `VBox` of mono section headings (`§4.9`) and labelled
  fields. Empty state when no row is selected uses a single muted line.
- **Classes:** `.detail-panel`, `.detail-panel-section`,
  `.detail-panel-label`, `.detail-panel-value`, `.detail-panel-value-mono`,
  `.detail-panel-empty`, `.detail-panel-stub`.
- **Rules:**
  - Mirror the row's data exactly — no derived calculations or invented
    fields. Use `—` everywhere data is absent.
  - The TVDB-correction sub-panel exposes the *shape* of the future
    interaction (search field + result list + Apply / Reset) but its
    inputs and buttons must stay disabled until the matching service is
    real. Mark the panel's stub copy with `.detail-panel-stub`.
  - At narrow widths the panel collapses **below** the table (§3.1).
    Never replace the table with the detail panel — the user must always
    see context.

### 4.13 Filter chip

- **Scan filters:** the Scan screen keeps type filters left-aligned and status
  filters right-aligned on the same row. The two predicates combine. Ignored
  rows remain discoverable through the Ignored status filter but are excluded
  from active selection and batch operations.
- **Anatomy:** `ToggleButton` arranged in an `HBox`. Exactly one button
  in the toggle group is selected at a time.
- **Class:** `.filter-chip` (and `.active` is auto-applied via the
  `:selected` pseudo-class — both selectors are kept in CSS for
  symmetry).
- **Rules:** used today by the History screen for the All / Successful /
  Warnings / Conflicts / Failed / Ignored filters. The selected chip
  drives a `Predicate<RunEvent>` from `HistoryFilter`. Never use chips
  for primary navigation — that is the sidebar's job.

### 4.14 Workspace chip

- **Anatomy:** pill-shaped `HBox` containing the mono `>_` prefix and
  the workspace path label.
- **Classes:** `.workspace-chip`, `.workspace-chip-prefix`,
  `.workspace-chip-label`.
- **Rules:**
  - Always pulls from the `currentWorkspace` supplier; never hardcoded.
  - When no workspace is configured, shows
    `UiText.workspaceChipEmpty(language)` ("Aucun workspace" / "No
    workspace"); the chip itself is still rendered so the user sees the
    safety boundary even when empty.
  - Long paths truncate at `maxWidth = 280` and expose the full path via
    a tooltip.

### 4.14b Group name

- **Anatomy:** a plain `Label` in mono 13px/700, plus a 1px rule across the row
  that opens the block. No pill, no marker, no per-group colour.
- **Classes:** `.group-name` + `.group-name-head` or `.group-name-ignored`;
  `.group-block-start` on the `TableRow`; `.identity-group-name` caps the width
  at 200 in `SeriesIdentityDialog`.
- **Rules:**
  - **The break is the signal.** In the scan table the name is written on the
    first row of a block and every following row of the same block leaves the
    cell blank. Twenty-five files of one series would otherwise mean
    twenty-five repetitions of one piece of information.
  - **Mono, at row size.** The seed comes from the filenames, so it is a system
    value and takes the system face (§2.2). Mono against the sans of the row
    text is what makes it read as a header: a different voice, not a louder one.
    Never set it below the 13px of the rows it heads.
  - **One rule per block**, `rgba(255,255,255,0.14)` on the top border of the
    row, drawn by `ScanScreen.updateGroupBlockStyle`.
  - `ScanTableColumns.Host.startsGroupBlock(int)` is the single definition of
    the break, so the name and the rule can never land on two different rows.
  - **No identity colour.** Colour in Episort means status. Hashing a group
    name to a hue (pill, dot or square) adds a legend the user has to decode
    and puts a second accent on screen — both are banned by §1.5 and §6.
  - The block key is `(group name, ignored)`: an ignored block and an active
    block of the same name each announce themselves.
  - The comparison runs against the sorted, filtered view, so it holds under
    any sort order. `ScanScreen` calls `table.refresh()` on comparator and
    list changes, since a cell's rendering depends on its neighbour.
  - Every cell keeps its tooltip, blank ones included: a block header scrolled
    out of view is never lost.
  - `MixedGroupDialog` and `SeriesIdentityDialog` list one group per line, so
    they always write the name.

### 4.15 Exact plan review pane (Epic 6)

- **Anatomy:** in-window pane replacing the content area (no second `Stage`) →
  header → subtitle → metric chip row → conflict batch toolbar (only while
  conflicts remain) → banner → `TableView` (selection, source, destination,
  status, dates, decision) → notice → right-aligned action row (`Fermer` ghost,
  `Appliquer` / `Valider et exécuter` primary).
- **Entry point:** the top-bar primary action (`Voir le plan`) is the single
  route in. It closes the pattern gate on the way, so reaching the plan costs
  one click from a loaded folder.
- **Conflicts are resolved inline:** a blocking row carries its own decision
  `ComboBox` and its source/destination dates in the very list being validated.
  The selection, dates, and decision columns — and the batch toolbar — are
  hidden as soon as no conflict remains. There is no separate conflict window.
- **Classes:** reuses `.screen-root`, `.tvdb-dialog-header`,
  `.tvdb-dialog-title`, `.tvdb-dialog-message`, `.selection-column` /
  `.selection-header` / `.selection-cell` / `.row-checkbox`,
  `.tvdb-match-meta` (metric chips), `.banner` + `.banner-info` /
  `.banner-warn`, `.preview-table` with `.cell-mono` / `.cell-muted`, and
  `.row-status` with `.quiet` / `.quiet-muted` / `.replace` / `.conflict` /
  `.danger`.
- **Rules:**
  - Status mapping is fixed: `EXECUTABLE` → `.quiet`,
    `ALREADY_IN_PLACE` → `.quiet-muted`, `EXCLUDED` → `.quiet-muted`,
    `CONFLICT` → `.conflict`, `DELETE` → `.danger`, and an approved overwrite
    → `.replace` whatever its status. All of them are text — see §4.11.
  - A planned deletion keeps a class no other state uses (`#ff4d4d`), so the
    row that destroys a file never reads like the rows around it. Enforced by
    `PlanReviewTextTest.aPlannedDeletionGetsAPillOfItsOwn`.
  - Every row shows a real source path; a missing destination renders `—`
    with `.cell-muted`. Never invent a destination for an excluded item.
  - The primary action never disappears: while
    `OperationPlan.hasBlockingConflicts()` is true it reads `Appliquer` and only
    rebuilds the plan from the row decisions; once no conflict remains it
    becomes `Valider et exécuter`, disabled when no executable operation
    exists. Disabling is the only refusal channel — never hide the button.
  - Leaving the pane is refused outright while the run is in flight: the sidebar
    and top bar are disabled and no navigation abandons a run mid-move.
  - The notice line states that nothing has been touched yet and that
    validating moves the listed files right away — the click is the point of
    no return, so it must never read as another preview step.
  - Conflict and exclusion reasons are localized through
    `UiText.planConflict` / `UiText.planExclusion`; raw enum names must
    never reach the screen.

### 4.16 Execution pane (Epic 7)

- **Anatomy:** a body, not a window: `ExecutionPane` replaces the review
  content inside the plan pane once the user validates, so approving and
  running never cost a window close and reopen. Two phases.
  - **Progress** — one panel (`.exec-readout`): section heading, the count
    (`n` + `/ total` + percent), the bar, then the operation in flight written
    as its two paths under a rule (`.exec-flight`: source, destination).
  - **Recap** — the progress panel is hidden, and the recap carries the outcome
    sentence, the counters under two headings, then next actions and the
    diagnostic location.
  - A per-file failure reveals the inline warn banner with Retry / Continue /
    Stop, and any answer other than Retry writes the file into
    `.exec-failure-log` under the progress panel.
- **Classes:** `.exec-readout`, `.exec-count`, `.exec-count-total`,
  `.exec-percent`, `.exec-flight`, `.exec-label`, `.exec-path`,
  `.exec-path-empty`, `.exec-failure-log`, `.exec-failed-line`,
  `.exec-outcome` + `.good` / `.warn` / `.error`, `.exec-stat`,
  `.exec-stat-value` + `.zero` / `.alarming`, `.exec-stat-label`,
  `.exec-hint`, plus the shared `.episort-progress`, `.section-heading`,
  `.banner` + `.banner-warn`, `.banner-icon`, `.banner-text`,
  `.content-scroll`, `.button.primary` / `.ghost` / `.danger`.
- **Rules:**
  - **A move is a pair.** The in-flight operation always shows both paths. A
    bar over a lone filename says work is happening but not what it is doing to
    the library. A deletion has no destination and renders `—` with
    `.exec-path-empty`; never invent one.
  - **A waved-through failure stays on screen.** Continue and Stop write the
    file into `.exec-failure-log`, which outlives the banner that announced it.
    Retry writes nothing: the file may still land.
  - **The recap splits the counters, it does not hide them.** Four under
    `exec.recap.section.disk` (moved, renamed, deleted, source folders removed),
    seven under `exec.recap.section.untouched`. All eleven are always shown; a
    zero is a real answer and takes `.zero`. Only failures may take
    `.alarming`, and only when non-zero. Eleven counters of equal weight is a
    table of numbers, not an answer to "what happened to my files".
  - The outcome sentence is coloured by what happened: `.good` for a complete
    success only, `.error` when the run aborted or any file failed, `.warn`
    otherwise.
  - The pane cannot be left while the run is in flight: the back and close
    actions stay disabled and the sidebar and top bar are locked until the
    recap exists.
  - `Retry` is enabled only for `ApplicationError.recoverable()` failures —
    never offer a retry that cannot succeed.
  - Failure text goes through `ApplicationError.safeMessage()`; the recap
    never carries credentials, tokens, or PINs.
  - The outcome sentence distinguishes complete success, partial success,
    nothing processed, and aborted. Partial success is never worded as
    success.

### 4.17 About screen

- **Anatomy:** a full-frame surface in the content area, reached from
  **Aide › À propos** and left by the same leading back button the plan review
  carries. Three regions, 26 apart:
  - **header** — back + title.
  - **body** — two columns, 30 gutter, capped at 640 + 360. Left, the
    narrative: identity row (logo 56, product name, tagline, version) → one
    paragraph → `// CHAÎNE DE TRAITEMENT` and its five numbered steps, at 26
    between the three and 8 between the steps. Right, one `.detail-panel`
    holding every value read off the machine: `// ENVIRONNEMENT` as a
    name/value table (name in a 74 gutter, baseline-aligned) → divider →
    `// FICHIERS ÉCRITS` as three stacked label-over-path fields.
  - **footer** — pinned to the bottom of the content area by a growing spacer,
    opened by a `.settings-section-divider` rule: `// SOURCES ET CRÉDITS` over
    the TVDB attribution and the font credit, side by side on the same 640 /
    360 grid as the body.
- **Classes:** `.about-pane` on the `.screen-root` (spacing 26),
  `.about-fact-panel` on the right panel (`.detail-panel` plus spacing 18, for
  the divider between its two groups), plus `.about-brand-name`,
  `.about-tagline`, `.about-version`, `.about-body`, `.about-step-index`,
  `.about-step-label`, `.about-label`, `.about-value`, `.about-value-path`,
  `.about-note`. Header reuses `.tvdb-dialog-header` / `.tvdb-dialog-title` /
  `.header-action`; the rules reuse `.settings-section-divider`.
- **Rules:**
  - **Section headings follow §4.9.** `// LABEL`, uppercase, mono. About was
    the one screen whose headings were sentence case, which made them read as
    captions on the fields below rather than as region markers.
  - **Prose left, machine values right.** A paragraph is read and a cache path
    is looked up; they do not belong in the same column. The fact column is the
    shell's own 360 panel width, not a second measure.
  - **Responsive:** below 1090 px of content width the two columns stack, each
    keeping its own measure (prose 640, panel full width). `AboutPane` watches
    its own width; it is not wired to `AppShell.STACK_BREAKPOINT`, because what
    it needs to know is how much room the content area gives it.
  - **Never a native `Alert`.** About was the last surface in the app rendering
    Windows dialog chrome in a window that has none of its own.
  - **Nothing here is written twice.** The pipeline is
    `UiText.scanWorkflowSteps` — the workflow strip's own five steps — so the
    description of the product and the trail the user walks cannot disagree.
    The version appears once, under the product name, not again in the
    environment column.
  - **Every value is read, never declared.** The version comes from the build
    stamp (`BuildInfo`, generated by the `generateBuildInfo` Gradle task), the
    runtime from `Runtime.version()` and the JavaFX / OS system properties, and
    the three paths from the stores that own them. Anything unreadable renders
    `—`.
  - The TVDB attribution line is required and stays on this screen.
  - About holds the content area, so it disables its own menu entry and the
    plan review disables it in turn (§4.7 refusal by disabling). Opening it
    hides the top bar's primary action, secondary actions, and search: nothing
    in the bar acts on what is on screen.
  - The missing-workspace gate does not cover About: it guards the work, not
    the documentation.

---

## 5. State, signals & i18n

- **No fabricated state.** Every metric, pill, widget value must trace back
  to a `AppShellViewModel` field, an `Optional<Path>` supplier, an
  `ApplicationError`, or a documented spec value (e.g. Story 1.5 ACs).
- **Status pill mapping** (Section 4.2): single source of truth.
- **Language:** all user-visible strings exist in both FR and EN. Route
  through `UiText` rather than hardcoding. The dashboard reads
  `currentViewModel.language()` after every refresh.
- **Theme:** dark is the only theme. `app.css` has no `.theme-light` rule
  set: a partial light skin renders dark glass panels on a white page, which
  is worse than not offering one. `Theme.LIGHT` still exists in the model and
  is exercised by `AppShellViewModelTest`, but no UI path produces it. Adding
  a light theme means styling every component, not eight overrides.

---

## 6. Do / Don't

**Do**
- Reuse `.card`, `.banner`, `.button`, `.section-heading`, etc. for new screens.
- Add a new component class only when the existing ones genuinely don't fit;
  document it in this file in the same PR.
- Keep panel borders 1px and use `border.subtle` unless the element is
  focused or active.
- Use `—` as the only "no data" placeholder.

**Don't**
- Invent metrics, timestamps, counts, or activity rows for unbuilt epics.
- Add a second accent color, gradient buttons, or RGB animations.
- Use heavy drop-shadows on text or icon-only buttons without labels.
- Hardcode hex colors in new code; reference an existing class.
- Introduce nav items for surfaces that don't yet exist.

---

## 7. Files

| File                                                              | Role                                                |
| ----------------------------------------------------------------- | --------------------------------------------------- |
| `src/main/resources/styles/app.css`                               | All visual tokens & component classes               |
| `src/main/java/com/episort/ui/AppShell.java`                      | Layout shell: sidebar + top bar + view host         |
| `src/main/java/com/episort/ui/Sidebar.java`                       | Three-entry sidebar (Scan / History / Settings)     |
| `src/main/java/com/episort/ui/TopBar.java`                        | Workspace chip, search, status, language, primary   |
| `src/main/java/com/episort/ui/scan/ScanScreen.java`               | Scan dashboard (table + detail panel)               |
| `src/main/java/com/episort/ui/scan/RowDetailPanel.java`           | TVDB-correction detail panel                        |
| `src/main/java/com/episort/ui/execution/PlanReviewPane.java`      | Plan review + inline conflict resolution + execution, in the main window (§4.15) |
| `src/main/java/com/episort/ui/execution/ExecutionPane.java`       | Execution progress, failures, and recap (§4.16)     |
| `src/main/java/com/episort/ui/AboutPane.java`                     | About screen in the content area (§4.17)            |
| `src/main/java/com/episort/ui/LoadingOverlay.java`                | Blocking-work overlay (addendum)                    |
| `src/main/java/com/episort/BuildInfo.java`                        | Version read from the Gradle build stamp            |
| `src/main/java/com/episort/ui/history/HistoryScreen.java`         | History dashboard backed by `RunEventStore`         |
| `src/main/java/com/episort/ui/settings/SettingsPane.java`         | Settings-section component                          |
| `src/main/java/com/episort/ui/UiText.java`                        | FR/EN strings — extend, don't bypass                |
| `src/main/java/com/episort/persistence/FileRunEventStore.java`    | Append-only JSONL run-history log                   |
| `docs/design-system.md`                                           | This document — keep in sync with `app.css`         |

When changing visuals, edit `app.css` first, then update §2 (Tokens) and the
relevant component in §4. New components belong in §4 with anatomy + class +
rules. PRs that add inline styles in JavaFX without a corresponding class are
rejected on review.
## UI Pass Addendum

- File properties uses `.file-properties-dialog`, `.file-properties-header`,
  `.file-properties-section`, `.file-properties-grid`,
  `.file-properties-label`, `.file-properties-value`, and
  `.file-properties-path`. It is a compact dark modal with orange section
  headings, label/value grids, wrapped long paths, and copy-path feedback.
  Values must come from the clicked `ScanRow` or lightweight filesystem
  metadata; unavailable media metadata displays the localized unavailable text.
- Sidebar navigation uses `.nav-item-icon` and `.nav-item-label` inside the existing `.nav-item` button. Icons are restrained text glyphs because the app has no icon dependency.
- Scan extensions render as `.extension-badge` plus `.mkv`, `.mp4`, `.avi`, `.srt`, or `.fallback`; new extensions must use tokenized CSS colors, not inline hex literals in Java.
- `.preview-table` owns complete dark scrollbar styling for bars, tracks, buttons, thumbs, and corners.
- Ignored scan rows use `.ignored-row`: reduced opacity, muted background, and
  disabled action checkbox. They remain readable and context-menu reactivable.
- Manual TVDB search uses `.tvdb-dialog`, `.tvdb-search-results`,
  `.tvdb-result-card`, `.tvdb-selected-card`, `.tvdb-poster-frame`,
  `.tvdb-poster-placeholder`, `.tvdb-match-title`, `.tvdb-match-meta`,
  `.tvdb-match-overview`, and the top-search-compatible `.tvdb-search-box`.
  Results are visual rows with a poster/placeholder, metadata, short overview,
  and a select action; no-result and initial states use one centered
  `.tvdb-empty-state`; loading and errors stay inside the dialog instead of
  blocking the scan table.
- Blocking work uses the in-shell overlays, not a window: `.app-loader-overlay`
  + `.app-loader-card` for analysis, and `.prereq-overlay` + `.prereq-card` for
  the missing-workspace gate. Never use a native `Alert` / `Dialog`.
- The loader card is a status panel, not a splash: everything in it is
  left-aligned — `.app-loader-eyebrow` (mono, accent), `.app-loader-text` (the
  sentence naming the work), the bar with `.app-loader-percent` beside it, then
  the `.app-loader-cancel` ghost button and its `.app-loader-hint` on one row.
  There is no spinner: the bar carries both states itself, indeterminate while
  the work cannot count itself and filling with a percentage as soon as it can.
  JavaFX's default `ProgressIndicator` arc is the one control on screen that
  looks like it came from another application; the only place it survives is
  the workflow strip, where it replaces a step number in place.
- `.episort-progress` is the single progress-bar look (6 px, orange fill,
  `rgba(255,255,255,0.08)` track, fully rounded), shared by the loader overlay
  and the execution readout. A new bar reuses it rather than styling its own.
- The only multi-line input is the detail panel's input-pattern editor,
  `.pattern-editor`: a mono dark `TextArea` matching `.text-field`. A
  `TextArea` with no class keeps its default light control background and
  punches a hole in the panel.
- Every user-visible string goes through `UiText`, including file-chooser
  titles, extension-filter labels, and failure copy. French copy is
  vouvoiement throughout, and `…` is the only ellipsis.
