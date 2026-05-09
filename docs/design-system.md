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
| `font.sans`       | `"Inter", "Segoe UI", "Helvetica Neue", sans-serif`               | Default `*` font, body, titles                       |
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
| Sidebar brand name | `.sidebar-brand-name`       | 17   | 800    | sans   |
| Sidebar section    | `.sidebar-section-label`    | 10.5 | 700    | mono   |
| Nav item           | `.nav-item`                 | 13   | 600    | sans   |
| Status pill        | `.status-pill`              | 11.5 | 700    | mono   |
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
┌─────────────────────────────────────────────────────────────────────┐
│ TOP BAR (12 × 22 padding)                                           │
│  >_ [search…………………]      ⟶ spacer ⟶  [pill]  Lang [combo]         │
├──────────┬──────────────────────────────────────────────────────────┤
│ SIDEBAR  │ CONTENT (ScrollPane)                                     │
│ 230px    │   .dashboard (26 × 28 padding, 22 vertical gap)          │
│ fixed    │     • heading                                            │
│          │     • primary status block                               │
│ brand    │     • metric grid (4 cols, 16 gap)                       │
│ section  │     • // PRÉREQUIS  → SettingsPane card                  │
│ nav-item │     • // EXÉCUTIONS → activity table                     │
│ nav-item │     • // SYSTÈME    → 3 status widgets                   │
│ …        │                                                          │
└──────────┴──────────────────────────────────────────────────────────┘
```

- Root: `BorderPane` with `.app-shell.theme-dark`.
- Sidebar: `min/pref/max width = 220 / 230 / 230`. Always fixed width.
- Top bar: `HBox` filling width; height driven by content.
- Content: `ScrollPane` with `.content-scroll` so the dashboard scrolls but
  sidebar and top bar stay pinned.

---

## 4. Components

Each component is documented as **Anatomy → Class → Rules**.

### 4.1 Sidebar

- **Anatomy:** brand row → section labels (`.sidebar-section-label`) → nav
  items (`.nav-item`) → flexible spacer (no footer with invented metadata).
- **Classes:** `.sidebar`, `.sidebar-brand`, `.sidebar-brand-name`,
  `.sidebar-section-label`, `.nav-item`, `.nav-item.active`.
- **Rules:**
  - Only list surfaces that actually exist in the current epic. Do not
    pre-list future features. Inactive but real surfaces are allowed.
  - One nav-item carries the `active` class at a time.
  - Do not add hotkey hints (`[1]`, `ctrl+k`) unless the binding actually
    exists in the application.

### 4.2 Top bar

- **Anatomy:** search field with `>_` prefix → spacer → status pill → language
  label + combo.
- **Classes:** `.top-bar`, `.top-search`, `.search-prefix`, `.status-pill`,
  `.language-label`.
- **Rules:**
  - The status pill is **derived from `AppShellViewModel.errorCode()`**:
    - empty → `OK`
    - present → variant `.warn`, text = error code (e.g. `TVDB_…`)
    - reserved variant `.error` for unrecoverable errors (Story 1.5).
  - The search prompt must be neutral (`Rechercher…` / `Search…`) until a
    real command palette ships.

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

### 4.5 Activity table

- **Anatomy:** `TableView` with mono uppercase column headers and prose cells.
- **Classes:** `.activity-table`, cells may opt into `.mono`.
- **Rules:**
  - Empty state must use `setPlaceholder(...)` with neutral text. Never
    populate with fake rows.
  - Column resize policy: `CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS`.

### 4.6 Settings section

- **Anatomy:** title (mono accent) → description (muted) → action row.
- **Classes:** `.settings-section`, `.settings-section-title`,
  `.settings-section-description`, `.workspace-value`.
- **Rules:**
  - Reuse `SettingsPane` rather than re-implementing the layout per story.
  - The workspace path always uses `.workspace-value` (mono, accent-hover color).

### 4.7 Buttons

- **Variants:**
  - default `.button` — orange-on-translucent, glow on hover, the workhorse.
  - `.button.primary` — solid orange, dark text. Use for the single primary
    action of a screen ("Continue", "Test connection").
  - `.button.ghost` — transparent with subtle border, for tertiary actions
    (cancel, dismiss).
- **Rules:**
  - At most **one** `.primary` button per screen.
  - Disabled state is provided by the framework; never manually grey-out.

### 4.8 Inputs (`text-field`, `password-field`, `combo-box`)

- Translucent surface, subtle orange border at rest, full orange + glow on focus.
- Combo popup uses `.combo-box-popup` styling; no overrides needed per call site.

### 4.9 Section heading (`// LABEL`)

- **Class:** `.section-heading`, optionally with `.section-heading-accent`.
- **Rules:** mono, uppercase, prefixed by `//`. Used to introduce major
  dashboard regions (e.g. `// PRÉREQUIS`, `// EXÉCUTIONS`, `// SYSTÈME`).

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

| File                                                     | Role                                       |
| -------------------------------------------------------- | ------------------------------------------ |
| `src/main/resources/styles/app.css`                      | All visual tokens & component classes      |
| `src/main/java/com/episort/ui/AppShell.java`             | Reference implementation of the layout     |
| `src/main/java/com/episort/ui/settings/SettingsPane.java`| Settings-section component                 |
| `src/main/java/com/episort/ui/UiText.java`               | FR/EN strings — extend, don't bypass       |
| `docs/design-system.md`                                  | This document — keep in sync with `app.css`|

When changing visuals, edit `app.css` first, then update §2 (Tokens) and the
relevant component in §4. New components belong in §4 with anatomy + class +
rules. PRs that add inline styles in JavaFX without a corresponding class are
rejected on review.
