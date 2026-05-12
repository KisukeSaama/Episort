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
| `text.faint`           | `#5a6271`                            | Section labels, hints, footers              |
| `status.good`          | `#4ade80`                            | Verified / OK signals                       |
| `status.warn`          | `#fb923c`                            | Setup-required, blocking-but-recoverable    |
| `status.error`         | `#f87171`                            | Failed / unrecoverable                      |

> **Rule.** Never reach for hex values in new CSS. Reuse the class names
> (`.card`, `.widget`, `.status-pill`, `.button.primary`, etc.) which already
> bind these tokens. Hex literals only belong inside `app.css`.

### 2.2 Typography

| Token             | Family                                                            | Used by                                              |
| ----------------- | ----------------------------------------------------------------- | ---------------------------------------------------- |
| `font.sans`       | `"Inter", "Segoe UI", system-ui, "Helvetica Neue", sans-serif`   | Default `*` font, body, titles, controls             |
| `font.mono`       | `"JetBrains Mono", "Cascadia Mono", "Consolas", monospace`        | `.mono`, all system labels, paths, codes, headings   |

| Role               | Class                       | Size | Weight | Family |
| ------------------ | --------------------------- | ---- | ------ | ------ |
| Page title         | `.app-title`                | 26   | 800    | sans   |
| Page subtitle      | `.app-subtitle`             | 13   | —      | sans   |
| Primary status     | `.app-status`               | 14   | 600    | sans   |
| Description / hint | `.app-description`          | 13   | —      | sans   |
| Section heading    | `.section-heading`          | 11   | 700    | mono   |
| Card title         | `.card-title`               | 11   | 700    | mono   |
| Card value         | `.card-value`               | 22   | 800    | sans   |
| Card value (mono)  | `.card-value-mono`          | 18   | 700    | mono   |
| Card hint          | `.card-hint`                | 11.5 | —      | sans   |
| Widget title       | `.widget-title`             | 11   | 700    | mono   |
| Widget line        | `.widget-line`              | 11.5 | —      | mono   |
| Sidebar brand name | `.sidebar-brand-name`       | 23   | 800    | sans   |
| Sidebar section    | `.sidebar-section-label`    | 10.5 | 700    | mono   |
| Nav item           | `.nav-item`                 | 13   | 600    | sans   |
| Nav item icon      | `.nav-item-icon`            | 18   | 800    | mono   |
| Status pill        | `.status-pill`              | 11.5 | 700    | mono   |
| Label              | `.ui-label`                 | 12.5 | —      | sans   |
| Technical text     | `.technical-text`           | 12   | —      | mono   |
| Button             | `.button`                   | 12.5 | 700    | sans   |
| Settings section   | `.settings-section-title`   | 14   | 700    | mono   |
| Body / control     | (default)                   | 13   | —      | sans   |
| Language label     | `.language-label`           | 11.5 | —      | mono   |

### 2.3 Spacing

Padding and gaps are limited to a small scale. Reuse these values rather than
inventing new ones.

| Token   | Value | Typical use                                       |
| ------- | ----- | ------------------------------------------------- |
| `s-2`   | 2     | Sidebar item rhythm                               |
| `s-4`   | 4     | Title / subtitle rhythm                           |
| `s-6`   | 6     | Widget line rhythm                                |
| `s-8`   | 8     | Card content rhythm                               |
| `s-10`  | 10    | Section heading → content                         |
| `s-12`  | 12    | Top bar internal padding (vertical)               |
| `s-14`  | 14    | Sidebar horizontal padding                        |
| `s-16`  | 16    | Metric grid gap, widget row gap                   |
| `s-18`  | 18    | Sidebar vertical padding, widget padding (h)      |
| `s-22`  | 22    | Top bar horizontal padding, dashboard section gap |
| `s-26`  | 26    | Dashboard outer padding (vertical)                |
| `s-28`  | 28    | Dashboard outer padding (horizontal)              |

### 2.4 Radius, border, elevation

| Token                | Value                                                                              | Usage                                |
| -------------------- | ---------------------------------------------------------------------------------- | ------------------------------------ |
| `radius.sm`          | `8px`                                                                              | Buttons, inputs, nav items           |
| `radius.md`          | `10px`                                                                             | Top search field                     |
| `radius.lg`          | `14px`                                                                             | Cards, widgets, table, settings      |
| `radius.pill`        | `999px`                                                                            | Status pill, status dots             |
| `border.width`       | `1px`                                                                              | All borders. Never thicker.          |
| `elevation.panel`    | `dropshadow(gaussian, rgba(0,0,0,0.55), 22, 0.05, 0, 6)`                           | Cards, widgets, table, settings      |
| `elevation.glow.acc` | `dropshadow(gaussian, rgba(249,115,22,0.45), 14, 0, 0, 0)`                         | Focused inputs                       |
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
│  [workspace-chip]  >_ [search……………]   ⟶  [pill]  Lang [combo]  [PRIMARY]│
├──────────┬──────────────────────────────────────────────────────────────┤
│ SIDEBAR  │ CONTENT (ScrollPane)                                         │
│ 230px    │   .screen-root (22 × 26 padding, 14 vertical gap)            │
│ fixed    │     • heading // SCAN | // HISTORIQUE | …                    │
│          │     • workflow progress / banner (screen state)               │
│ brand    │     • metric-grid (FlowPane, 16 gap, wraps)                  │
│ NAVI     │     • body:                                                  │
│ Scan     │         wide:    HBox(table, detail-panel 360px)             │
│ History  │         medium:  VBox(table, detail-panel)                   │
│ Settings │         narrow:  same VBox + .compact-sidebar (icons only)   │
└──────────┴──────────────────────────────────────────────────────────────┘
```

- Root: `BorderPane` with `.app-shell.theme-dark`.
- Sidebar: `min/pref/max width = 220 / 230 / 230` at wide/medium widths,
  `64 / 64 / 64` when `.compact-sidebar` is applied (< 900 px).
- Top bar: `HBox` filling width; height driven by content. Hosts the
  workspace chip, search, status pill, language combo, and the **single
  primary action** for the current screen.
- Content: each screen's root is wrapped in a `ScrollPane.content-scroll`
  inside the `viewHost` `StackPane`. Sidebar + top bar stay pinned.

### 3.1 Breakpoints

| Width                  | Sidebar          | Detail panel                  | Metric cards     |
| ---------------------- | ---------------- | ----------------------------- | ---------------- |
| ≥ 1200 px (wide)       | 230 px, labels   | Right of the table, 360 px    | One row, no wrap |
| 900 – 1199 px (medium) | 230 px, labels   | Below the table, full-width   | Wrap as needed   |
| < 900 px (narrow)      | 64 px, icons-only| Below the table, full-width   | Wrap as needed   |

---

## 4. Components

Each component is documented as **Anatomy → Class → Rules**.

### 4.1 Sidebar

- **Anatomy:** brand row → section labels (`.sidebar-section-label`) → nav
  items (`.nav-item`) → flexible spacer (no footer with invented metadata).
- **Classes:** `.sidebar`, `.sidebar-brand`, `.sidebar-brand-name`,
  `.sidebar-section-label`, `.nav-item`, `.nav-item.active`,
  `.compact-sidebar`, `.nav-item-icon`, `.nav-item-label`.
- **Rules:**
  - Sidebar lists exactly **three** surfaces: **Scan**, **History**,
    **Settings**. Do not add other entries without removing one — keep the
    surface count to three until the navigation model evolves.
  - Do not put a "Safe Mode" card or any other persistent status card in
    the sidebar. The safety model is communicated by in-screen workflow
    progress, banners (§4.10), and pill statuses (§4.2), not by sidebar chrome.
  - One nav-item carries the `active` class at a time.
  - Sidebar branding is intentionally stronger than navigation without
    overpowering it: the logo is 44 px, the brand name uses
    `.sidebar-brand-name` at 23 px, and nav glyphs use `.nav-item-icon` at
    18 px.
  - Do not add hotkey hints (`[1]`, `ctrl+k`) unless the binding actually
    exists in the application.
  - At narrow widths (< 900 px) the sidebar receives the
    `.compact-sidebar` class; nav-item labels become hidden and only the
    `.nav-item-icon` glyph is shown. Width drops from 230 px to 64 px.

### 4.2 Top bar

- **Anatomy:** workspace chip → search field with `>_` prefix → spacer →
  status pill → language label + combo → single **primary action** button.
- **Classes:** `.top-bar`, `.workspace-chip`, `.workspace-chip-prefix`,
  `.workspace-chip-label`, `.top-search`, `.search-prefix`, `.status-pill`,
  `.language-label`.
- **Rules:**
  - The status pill is **derived from `AppShellViewModel.errorCode()`**:
    - empty → `OK`
    - present → variant `.warn`, text = error code (e.g. `TVDB_…`)
    - reserved variant `.error` for unrecoverable errors.
  - The search prompt must be neutral (`Rechercher…` / `Search…`) until a
    real command palette ships. The Scan screen wires the search field to
    the preview-table filter (substring match on filename / extension).
  - The workspace chip mirrors the configured workspace (mono, accent
    color). When no workspace is configured it shows the localized empty
    placeholder. Always shows a tooltip with the absolute path.
  - **Exactly one** primary action exists per screen, owned by the top
    bar (`.button.primary`). On Scan: `Charger un dossier` → `Valider
    l'aperçu`. On History: `Rafraîchir`. On Settings: hidden.
  - Scan folder actions are context gated: before an input folder is loaded,
    only `Charger un dossier` is active; `Changer de dossier`,
    `Réinitialiser le dossier`, and `Réanalyser` stay disabled. Once a folder
    is loaded, those secondary actions become available and the primary action
    switches to `Valider l'aperçu`.

### 4.3 Card (metric)

- **Anatomy:** title (mono, faint) → value → optional hint.
- **Classes:** `.card` + value class `.card-value` or `.card-value-mono`.
  Accent variants: `.card-accent`, `.card-good`, `.card-warn`, `.card-error`
  (apply to the card root).
- **Rules:**
  - Show the real value, or `—`. Never invent placeholder data
    ("Pending check", "Awaiting selection").
  - Use `.card-value-mono` for paths, codes, identifiers. Use `.card-value`
    for prose values ("Connexion vérifiée").
  - Apply an accent variant only when a real status signal justifies it.

### 4.4 Status widget

- **Anatomy:** `.widget-title` → one or more `.widget-line` rows.
- **Classes:** `.widget`, `.widget-title`, `.widget-line`,
  `.widget-line-muted`, `.widget-line-good`, `.widget-line-warn`,
  `.widget-line-error`. Optional status `.dot` + `.dot-good/.dot-warn/.dot-error/.dot-idle`.
- **Rules:**
  - Each line maps to a single observable fact. No counters, durations, or
    timestamps unless backed by a real signal.
  - Stay at ≤ 4 lines per widget; if you need more, split into two widgets.

### 4.5 Activity table / list

- **Anatomy:** `TableView` with mono uppercase column headers and prose cells, or `ListView` for virtualized one-dimensional file lists.
- **Classes:** `.activity-table`, cells may opt into `.mono`.
- **Rules:**
  - Empty state must use `setPlaceholder(...)` with neutral text. Never
    populate with fake rows.
  - Column resize policy: `CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS`.
  - File rows may use `.extension-badge` at the row end to show the extension without leaving the virtualized `ListView` model.
  - **Superseded for Scan.** The dual-`ListView` before/after pattern and
    the `.rename-action-column` between them are replaced by the preview
    table in §4.11. Reuse `.activity-table` for one-dimensional lists
    (e.g. the TVDB-search results inside the detail panel) but route new
    multi-column scan/history surfaces through `.preview-table`.

### 4.6 Settings section

- **Anatomy:** title (mono accent) → description (muted) → action row.
- **Classes:** `.settings-section`, `.settings-section-title`,
  `.settings-section-description`, `.workspace-value`.
- **Rules:**
  - Reuse `SettingsPane` rather than re-implementing the layout per story.
  - The workspace path always uses `.workspace-value` (mono, accent-hover color).
  - When displayed from the main screen, settings use the `.settings-dialog` modal shell instead of replacing the main page.

### 4.7 Buttons

- **Scan topbar:** use `.menu-button.primary` for the `Charger` / `Load`
  dropdown. It opens `Load folder`, `Load files`, and `Add files`; the existing
  validate action remains a disabled/enabled `.button.ghost` until active rows
  have valid proposed names.
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
  - Disabled state is provided by the framework; never manually grey-out.
  - Button typography is centralized in `.button`: Inter/system sans, 12.5 px,
    700 weight, 34 px minimum height, and ellipsis overrun for long FR/EN
    labels. Screen-specific buttons should add only semantic variants
    (`.primary`, `.ghost`, `.destructive`) rather than inline font rules.

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
  header and row checkboxes; `.row-status` plus one of `.preview` / `.ready` /
  `.warning` / `.conflict` / `.ignored` on the status pill.
- **Rules:**
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
  - Empty table state goes through `setPlaceholder(...)` with localized,
    neutral text (`scanTablePlaceholder` / `historyEmptyPlaceholder`).
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

### 4.12a AI chat panel

- **Anatomy:** section heading -> target breadcrumb -> scrollable message
  stack -> multiline input + primary send button. Assistant messages may
  contain a minimal Markdown subset rendered as JavaFX nodes.
- **Classes:** `.ai-chat-panel`, `.ai-chat-scroll`, `.ai-chat-messages`,
  `.ai-chat-message`, `.ai-chat-message-user`, `.ai-chat-message-assistant`,
  `.ai-chat-markdown`, `.ai-chat-md-heading`, `.ai-chat-md-paragraph`,
  `.ai-chat-md-bullet`, `.ai-chat-md-code`, `.ai-chat-tool-card`.
- **Rules:**
  - Do not use `WebView` or HTML for AI content; render the supported
    Markdown subset directly in JavaFX controls.
  - Empty or whitespace-only messages are never displayed.
  - Tool-call cards remain explicit confirmation points before any row data
    changes.

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

---

## 5. State, signals & i18n

- **No fabricated state.** Every metric, pill, widget value must trace back
  to a `AppShellViewModel` field, an `Optional<Path>` supplier, an
  `ApplicationError`, or a documented spec value (e.g. Story 1.5 ACs).
- **Status pill mapping** (Section 4.2): single source of truth.
- **Language:** all user-visible strings exist in both FR and EN. Route
  through `UiText` rather than hardcoding. The dashboard reads
  `currentViewModel.language()` after every refresh.
- **Theme:** dark is the canonical theme. Light overrides exist in
  `app.css` under `.theme-light` and must be kept in sync when adding new
  dark rules. Do not add a third theme.

---

## 6. Do / Don't

**Do**
- Reuse `.card`, `.widget`, `.button`, `.section-heading`, etc. for new screens.
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
- Batch-level TVDB controls live in `.batch-tvdb-section` below the scan table. Candidate/order controls may be disabled until real TVDB candidate data exists, and labels must come from `UiText`.
- `.preview-table` owns complete dark scrollbar styling for bars, tracks, buttons, thumbs, and corners.
- Ignored scan rows use `.ignored-row`: reduced opacity, muted background, and
  disabled action checkbox. They remain readable and context-menu reactivable.
- Manual TVDB search uses `.tvdb-dialog`, `.tvdb-search-results`,
  `.tvdb-result-card`, `.tvdb-selected-card`, `.tvdb-poster-frame`,
  `.tvdb-poster-placeholder`, `.tvdb-match-title`, `.tvdb-match-meta`,
  and `.tvdb-match-overview`. Results are visual rows with a poster/placeholder,
  metadata, short overview, and a select action; loading and errors stay inside
  the dialog instead of blocking the scan table.
- App-level modals (e.g. local-AI bootstrap) use `.modal-overlay` (dimmed backplate), `.modal-card` (glass card with orange border), `.modal-header` + `.modal-header-icon` + `.modal-title-block` (`.modal-title` Inter / `.modal-subtitle` Inter muted), `.modal-body` containing `.modal-info-row`s (`.modal-info-label` JetBrains Mono caption + `.modal-info-value` Inter, or `.modal-info-value-mono` for technical values), `.modal-progress` (orange-filled `.progress-bar` + `.modal-progress-status` mono), and `.modal-footer` aligned right with `.button.ghost` then `.button.primary`. Hosted in a `StageStyle.TRANSPARENT` window sized to the owner so the overlay actually dims the app — never use a native `Alert`/`Dialog`.
