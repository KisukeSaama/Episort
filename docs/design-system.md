# Episort — Design System

Hyprland-rice inspired desktop UI. Dark translucent panels over near-black, a
single orange accent, tiling-window-manager layout. The rice reference stops at
the layout and the palette: nothing here emits light. JavaFX implementation
lives in
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
4. **One family, explicit roles.** Instrument Sans carries the complete UI.
   Paths, identifiers, codes, measurements and numeric readouts retain their
   semantic classes, but differ through weight, colour and tabular alignment.
5. **No invented data.** Every label and value must come from a real
   view-model signal or display `-`. Placeholder text is allowed only when the
   feature is genuinely empty (e.g. table placeholder).
6. **No halos.** A state is stated by a fill, an edge and a text colour, never
   by a coloured `dropshadow`. A glow is a light-emitting object on a matte
   surface: it reads as decoration, it spreads the answer over a dozen soft
   pixels instead of drawing an edge, and it silently costs the whole subtree
   its subpixel text rendering (§2.2). Shadows are for elevation only, and they
   are black. Keyboard focus, which needs to be unmissable, uses a second
   border layer on a negative inset — a ring, not a haze.

---

## 2. Tokens

### 2.1 Color

| Token                  | Value                                | Usage                                       |
| ---------------------- | ------------------------------------ | ------------------------------------------- |
| `bg.base.start`        | `#07080b`                            | Dark theme: top of the root gradient        |
| `bg.base.end`          | `#0a0c11`                            | Bottom of the root gradient                 |
| `bg.ambient.near`      | `rgba(249, 115, 22, 0.10)`           | Top-left ambient light (radial)             |
| `bg.ambient.far`       | `rgba(249, 115, 22, 0.04)`           | Bottom-right ambient light (radial)         |
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
| `accent.ring`          | `rgba(249, 115, 22, 0.45)`           | Outer stroke of the keyboard focus ring     |
| `text.primary`         | `#f5f7fb`                            | Headings, card values, body emphasis        |
| `text.secondary`       | `#b8c0cc`                            | Body text, default nav-item                 |
| `text.muted`           | `#8a93a3`                            | Subtitles, descriptions                     |
| `text.faint`           | `#767e8d`                            | Section labels, hints, footers              |
| `status.good`          | `#4ade80`                            | Verified / OK signals                       |
| `status.warn`          | `#fb923c`                            | Setup-required, blocking-but-recoverable    |
| `status.error`         | `#f87171`                            | Failed / unrecoverable                      |

Light theme equivalents are defined in `styles/light.css`, loaded after the
base component stylesheet only while light mode is active: shell `#eeeae4`,
panel `#faf8f5`, chrome `#f8f5f1`, primary text `#211d1a`, secondary text
`#554d47`, muted text `#6b625b`, positive text `#2f6f44`, error text
`#a23b3b`, and the same orange accent. Light status colors are darker inks,
not the luminous dark-theme signals, so text keeps AA contrast on warm panels.
These values form one complete alternate token set; component anatomy is shared.

> **Rule.** Never reach for hex values in new CSS. Reuse the class names
> (`.card`, `.banner`, `.status-pill`, `.button.primary`, etc.) which already
> bind these tokens. Hex literals only belong inside `app.css`.

**The ink set is closed**, and `TextPaletteTest` holds it closed. The four
text tokens had grown to twelve: an `#a8b0bd` here, a `#d2d8e0` there, a
`#d7dce5`, an `#f1f5f9`, plus a parallel ramp of white at 0.40 / 0.42 / 0.45 /
0.58 / 0.62 / 0.72 / 0.86 opacity doing the same work with different numbers.
No single one of them looks wrong on its own, which is exactly how they
accumulate, but five near-identical greys on one screen read as approximate
rather than decided. `.button` had also drifted onto `accent.hover` at rest
and warmed to an undeclared third orange, `#fdba74`, leaving the hover state
nowhere to go; the accent is now at rest and `accent.hover` on hover, which is
what the two tokens are named for.

Off-token ink needs a reason, and there are five: the row-status scale
(§4.11), dark ink on a solid orange fill, text tinted toward a coloured
surface it sits on (never grey on colour), faded accent glyphs in empty
states, and disabled controls. They are enumerated in the test, not left to
judgement. Colour is also the status channel (§1.2), so a hue that is neither
the accent nor one of the three status inks is a legend the user has to
decode: a yellow `#facc15` had appeared twice and is gone.

### 2.2 Typography

Instrument Sans carries the whole interface. Which **file** carries a given
weight is not a detail JavaFX will work out on its own, so the family stack is
per weight, not per application:

| Weight | Family stack                                                                | Face drawn              |
| ------ | --------------------------------------------------------------------------- | ----------------------- |
| 400    | `"Instrument Sans", "Segoe UI", sans-serif`                                 | Instrument Sans Regular  |
| 500    | `"Instrument Sans Medium", "Instrument Sans", "Segoe UI", sans-serif`       | Instrument Sans Medium   |
| 600    | `"Instrument Sans SemiBold", "Instrument Sans", "Segoe UI", sans-serif`     | Instrument Sans SemiBold |
| 700    | `"Instrument Sans", "Segoe UI", sans-serif`                                 | Instrument Sans Bold     |

**Why, and the bug it cost.** Instrument Sans ships Medium and SemiBold as
their own RIBBI families — `InstrumentSans-Medium.ttf` reports family name
`Instrument Sans Medium`, not family `Instrument Sans` at weight 500. JavaFX's
font model holds one regular and one bold face per family, so under
`"Instrument Sans"` it knows exactly two faces:

```
Font.getFontNames("Instrument Sans") -> [Instrument Sans Bold, Instrument Sans Regular]
weight 400 -> Regular    weight 500 -> Regular
weight 600 -> Regular    weight 700 -> Bold
```

For as long as the rules named only `"Instrument Sans"`, every `600` in the
stylesheet — captions, column headers, buttons, section headings, status
words, filters — rendered at **400**, and the documented 400/500/600/700
ladder was really 400/400/400/700. Two of the four bundled fonts were loaded
at startup and never drawn. Nothing warned: an unmatched weight is not an
error, it is a fallback.

Three rules follow from this, all enforced by `TypographyScaleTest`:

1. **A rule that sets `-fx-font-weight` sets `-fx-font-family` in the same
   rule.** Even at 400 and 700, where the stack is the plain one. Inheritance
   is what makes this necessary rather than verbose: `.button` names SemiBold
   for its 600, so `.button.validate-action` asking for 700 without a family
   of its own would inherit SemiBold, find one face there, and render
   SemiBold instead of Bold.
2. **The numeric weight stays.** It no longer picks the Instrument Sans face,
   but it is what steers the `"Segoe UI"` fallback if the bundled font ever
   fails to load.
3. **Never add a fifth weight** by adding a file. It would need its own family
   name in every rule that uses it, and the ladder is already four steps
   against six sizes.

Contrast: `text.faint` is the lightest a label may go. Every colour in the
table above clears WCAG AA (4.5:1) against the real translucent panel, not
against the nominal background. Disabled controls are the only exemption.

#### Every scene root, not just the shell

`-fx-font-family` inherits down a scene graph and stops at its edge. A dialog
`Stage`, a `ContextMenu`, a `ComboBox` popup and a `Tooltip` each own a scene
root that is not a descendant of `.app-shell`, so none of them inherited the
interface font: their unclassed labels fell back to the platform face at the
platform size, and the four dialogs rendered in Segoe UI beside a shell in
Instrument Sans. §1 of `app.css` lists every root the application can open;
a new root belongs in that list on the day it is written.

#### Antialiasing

One mode for the whole application: **grayscale**, set on `.text` in §1.

Modena asks for LCD subpixel text, but Prism silently drops to grayscale for
anything drawn through an intermediate image — which is every node under an
`-fx-effect`. `elevation.panel` sits on the cards, the tables, the settings
sections and the detail panel, so most of the interface was already grayscale
while the top bar and sidebar, carrying no shadow, were LCD: two
rasterisations of the same 13 px face, side by side. Grayscale wins the tie
because it is what the majority of the surface already used, because no
shadow can revoke it, and because subpixel rendering fringes light strokes on
a near-black ground.

#### The scale

Six steps, each at least 1.27× the one below it. There is nothing between
them: two roles that need to feel different at the same step are separated by
weight and colour, never by half a pixel.

| Step | Ratio | Role                                                              |
| ---- | ----- | ----------------------------------------------------------------- |
| 10   | —     | **Caption.** Instrument Sans, 600, `text.faint`. Compact labels: sidebar |
|      |       | headings, column headers, field labels, status pills,              |
|      |       | row statuses, badges, accelerator hints, meta lines.               |
| 13   | 1.30  | **Body.** Instrument Sans, 400–500. Everything meant to be read: prose, section and card |
|      |       | titles, table cells, paths, inputs, buttons, list items, hints.     |
| 17   | 1.31  | **Heading.** 600–700. Dialog and settings-section titles, metric   |
|      |       | values outside the grid, empty-state titles, icon glyphs.          |
| 22   | 1.29  | **Title.** 700. Metric-grid values, overlay and dialog titles.     |
|      |       | The sidebar brand uses 700 as the strongest interface weight.      |
| 28   | 1.27  | **Display glyph.** Detail-panel empty-state mark.                  |
| 36   | 1.29  | **Display glyph.** Table empty-state mark.                         |

Anything smaller than 10 or between two steps is a bug. The previous scale ran
10 · 10.5 · 11 · 11.5 · 12 · 12.5 · 13 · 13.5 · 14 · 15 · 16 · 18 · 20 · 22 ·
23 · 28 · 36: seventeen sizes, six of them inside a 3 px band, which is not a
hierarchy but noise. The window-control glyphs were the last survivor at 12.

**A step's weight is fixed, not a range.** Step 10 is 600 and step 22 is 700,
everywhere, without exception. Left as ranges they drifted: the metric-row
card titles were the one caption in the app at 400, the file-properties
section title was lighter at 500 than the labels underneath it at 600, and the
prerequisite and About titles sat at 600 beside page headings at 700. Step 17
keeps its 600/700 split because the two weights carry a real distinction there
— an orange 600 settings-section title against a white 700 dialog title. There
is no third reading: the sidebar's storage figure used to be one and is now at
the body step, because a permanently visible ambient readout may not take a
heading step at all (§4.1).

**One role, one treatment.** The three empty states had three: 17/700 for the
table, 17/700 in a different near-white for TMDB, and 13/600 for the detail
panel. They are now one. Same for their hints, which had split between
`text.faint` and `text.muted`. And `.exec-count` was 500 against its own
`/ total` suffix at 600, so the figure the user came to read was lighter than
its punctuation.

#### Weight and colour carry the rest

| Weight | Meaning                                                              |
| ------ | -------------------------------------------------------------------- |
| 400    | Long prose and quiet supporting copy.                                  |
| 500    | Body data, navigation and input text.                                 |
| 600    | Captions, filters, buttons, section headings and compact metadata.     |
| 700    | Primary actions, overlay titles and emphasised values.                 |

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
| `elevation.popup`    | `dropshadow(gaussian, rgba(0,0,0,0.55), 8, 0.08, 0, 3)`                            | Context menus, dropdown panels       |
| `elevation.dialog`   | `dropshadow(gaussian, rgba(0,0,0,0.72), 30, 0.12, 0, 10)`                          | Modal dialogs                        |
| `focus.ring.inset`   | `-3px` (`-2px` on the 16 px row checkbox)                                          | Outer stroke of the focus ring       |

Every shadow in the application is black and offset downward: it says how far a
surface sits above the one behind it. There is no coloured shadow anywhere, and
adding one is the breach §1.6 describes — `CssHaloTest` fails the build on it.

Keyboard focus is two border layers rather than a fourth shadow:

```css
-fx-border-color: #f97316, rgba(249,115,22,0.45);   /* accent.ring outside */
-fx-border-width: 1, 1;
-fx-border-insets: 0, -3;                            /* drawn beyond the bounds */
-fx-border-radius: 8, 11;                            /* radius.sm, then + inset */
```

The negative inset is what keeps `border.width` at 1px without moving anything:
the outer stroke is painted outside the layout bounds, so the control does not
resize and its content does not shift when focus arrives. The light theme
restates these rules in `light.css` with its own ink, because its `:hover` rules
share their specificity and come from the later stylesheet.

---

## 3. Layout

```
┌─────────────────────────────────────────────────────────────────────────┐
│ TOP BAR (12 × 22 padding)                                               │
│  [workspace-chip]  ⟶  [pill] [rescan] [reset] [Load ▾] [PRIMARY]             │
├──────────┬──────────────────────────────────────────────────────────────┤
│ SIDEBAR  │ CONTENT (ScrollPane)                                         │
│ 230px    │   .screen-root (22 × 26 padding, 14 vertical gap)            │
│ fixed    │     • .screen-heading-row (34 high) SCAN | HISTORIQUE | …     │
│          │     • workflow progress / banner (screen state)               │
│ brand    │     • metric-grid (FlowPane, 16 gap, wraps)                  │
│ NAVI     │     • body:                                                  │
│ Scan     │         wide:    HBox(table, detail-panel 360px)             │
│ History  │         medium:  VBox(table, detail-panel)                   │
│ Settings │                                                              │
└──────────┴──────────────────────────────────────────────────────────────┘
```

- Root: `BorderPane` with `.app-shell.theme-dark`.
- Sidebar: 230 px with labels down to the compact breakpoint, 64 px and
  icon-only below it (§3.1). Three entries do not justify hiding their labels
  while there is width to spare; at 820 px a 230 px rail is more than a
  quarter of the window, and the table pays for it.
- Top bar: `HBox` filling width; height driven by content. Hosts the
  workspace chip, status pill, the load menu, the secondary scan
  actions, and the **single primary action** for the current screen. Language
  selection lives in Settings, not here.
- Content: each screen's root is wrapped in a `ScrollPane.content-scroll`
  inside the `viewHost` `StackPane`. Sidebar + top bar stay pinned.
- Every screen opens on a `.screen-heading-row`: an `HBox` 34 px tall — the
  height of one control — holding the section heading, and whatever the screen
  puts on that line beside it (Scan's search box). Screens that put nothing
  there still reserve the height, so the heading and everything under it land
  on the same line on all three. Never drop the row because a screen carries
  the heading alone.
- History keeps at least 240 px of usable table height. When the window is too
  short for the stacked table and detail panel, the screen scrolls vertically
  instead of compressing the table to its column header.

### 3.1 Breakpoints

`ShellLayout` owns the decision; `AppShell` applies it and puts
`.layout-wide` / `.layout-medium` / `.layout-compact` on the root so the
stylesheet can tighten with it. The window opens at 1280 × 800 and floors at
820 × 600.

| Width               | Sidebar        | Detail panel                | Top bar                    | Screen padding |
| ------------------- | -------------- | --------------------------- | -------------------------- | -------------- |
| ≥ 1200 px (wide)    | 230 px, labels | Right of the table, 380 px  | Every action               | 26 / 30        |
| 1000–1200 (medium)  | 230 px, labels | Below the table, full width | Every action               | 26 / 30        |
| < 1000 px (compact) | 64 px, glyphs  | Below the table, full width | Mirror actions folded away | 18 / 18        |

**Order of concessions.** The detail panel moves under the table before the
navigation loses its labels, because a panel that has moved still says
everything it said and a rail of bare glyphs does not. Only after that does
the chrome give anything up.

**What compact gives up, and what it never does.** The rail keeps its glyphs,
each with the tooltip and accessible text it carries at every width, and the
storage readout keeps the percentage and the bar with its full reading in a
tooltip. The top bar folds `Réanalyser` and `Réinitialiser`, the only two
controls in it that exist twice: they are the Analyse menu's own entries and
share its enabled state (§4.7), so folding them costs a click rather than an
action. `Charger`, the status pill, the workspace chip and the screen's single
primary action exist nowhere else and are never folded, at any width.

**The floor is the compact layout's, not the wide one's.** It used to be
1180 × 760 against a single threshold at 1200: the narrow layout existed
inside a twenty-pixel band, was effectively never rendered, and the window
could not be tiled to half of a 1920 screen. `ShellLayoutTest` asserts that
every layout is reachable between `MIN_WIDTH` and 2560, so a floor raised back
above a breakpoint fails the build rather than quietly deleting a layout.

---

## 4. Components

Each component is documented as **Anatomy → Class → Rules**.

### 4.1 Sidebar

- **Anatomy:** brand row → navigation label → nav items (`.nav-item`) →
  flexible spacer → logical-volume storage readout pinned to the bottom.
- **Classes:** `.sidebar`, `.sidebar-brand`, `.sidebar-brand-name`,
  `.sidebar-section-heading`, `.sidebar-section-marker`,
  `.sidebar-section-label`, `.nav-item`, `.nav-item.active`,
  `.nav-item-icon`, `.nav-item-label`, `.storage-usage`, `.storage-heading`,
  `.storage-reading`, `.storage-percentage-row`, `.storage-percentage`,
  `.storage-percentage-suffix`, `.storage-detail`, `.storage-capacity`,
  `.storage-available`, `.storage-progress`.
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
  - The navigation heading occupies the same 36 px row as a nav item. Its
    orange marker is optical punctuation only: 1 × 14 px, aligned to the text,
    never a full-height border.
  - The sidebar does not expose the workspace tree. The configured path remains
    visible in the top bar and editable in Settings.
  - The bottom readout reports the logical filesystem volume containing the
    workspace: percentage used, used / total capacity, then usable space. A
    RAID exposed by the operating system as one volume therefore reports the
    complete logical RAID capacity, not the workspace directory contents.
  - Volume reads run off the JavaFX thread so a NAS cannot freeze the shell.
    Before configuration or when the volume is unavailable, every value is
    `-` and the progress bar is hidden.
  - Storage sizes use binary measurements with localized labels (`To`, `Go`,
    `Mo`, `Ko` in French; `TB`, `GB`, `MB`, `KB` in English).
  - The readout is **three groups, not five stacked lines.** The caption sits a
    10 above the reading; the percentage row and the bar are one reading at the
    6 step, because the bar is the figure drawn; the two measurements are a pair
    at the 4 step, a 10 below the bar. A flat 6 between all five children put
    the caption as close to the figure as the figure was to the bar, and the
    block had no primary.
  - The whole block is a single ink, `text.faint`, and everything in it except
    the figure sits at the caption step: the `STOCKAGE` caption, the word
    qualifying the figure (`utilisé` / `used`), the used / total measurement and
    the availability phrase are all Instrument Sans 10 / 600. The figure alone
    takes the 13 body step at 600, and that one step is the entire hierarchy
    inside the block.
  - This is an **ambient readout**, not a metric the user came to the screen to
    read, so it takes neither a heading step nor an ink above `text.faint`. It
    was 17 / 700 in `text.secondary` over a 13 / 500 `text.secondary`
    measurement, and in a rail where near-white already means the brand at
    22 / 700 and a nav item under the pointer, that made the quietest
    information in the shell the loudest column after the brand. Dropping the
    ink was not enough on its own — `text.faint` is the floor for a label, so
    the remaining volume knob was the amount of ink, which is why the three
    supporting lines went to the caption step. The figure keeps the lead within
    the block; the block keeps no lead in the column.
  - The figure and its qualifier are two labels and two i18n keys
    (`sidebar.storage.used.value`, `sidebar.storage.used.suffix`) so the
    collapsed rail can drop the word and keep the figure.
    `sidebar.storage.used` stays the whole phrase for the tooltip and the
    accessible reading.
  - The block takes the rail's own horizontal 14, so the divider spans the width
    of a nav item's hover pill and the labels start on the same left edge as the
    nav labels. Collapsed, the horizontal padding is 0 and the figure centres
    over the glyphs above it; it needs no size override there, because the body
    step already fits `100 %` in the 48 px the rail has left.
  - The shell moves initial focus to the load action once the scene attaches.

### 4.2 Top bar

- **Anatomy:** workspace chip → spacer →
  status pill → `Réanalyser` → `Réinitialiser` → load menu → single **primary
  action** button.
- **Classes:** `.top-bar`, `.workspace-chip`, `.workspace-chip-prefix`,
  `.workspace-chip-label`,
  `.status-pill`, `.window-controls`, `.window-button`, `.window-snap-preview`.
- **Rules:**
  - The status pill is **derived from `AppShellViewModel.errorCode()`**:
    - empty → the pill is hidden. A permanent green `OK` is decoration; the
      absence of a warning is the signal.
    - present → variant `.warn`, text = error code (e.g. `TMDB_…`)
    - variant `.error` when the code carries `UNAVAILABLE` or `INVALID`.
  - Search is local to the table it filters. Scan and History place it at the
    start of their filter row, with explicit prompts (`Rechercher dans les
    fichiers…` / `Rechercher dans l’historique…`) so its scope is unambiguous.
  - Search fields use `.episort-search-box` + `.episort-search-field`
    with shared `.episort-search-icon` / `.episort-search-clear` controls:
    dark surface, fixed height, a low-intensity orange inactive border, and a
    high-contrast orange focus border carrying the §2.4 ring, without an inner
    white text-field flash. TMDB, Scan, and History searches reuse this
    active/focus treatment while keeping separate text state.
  - The workspace chip mirrors the configured workspace (Instrument Sans, accent
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

  - The title-bar menu titles (`Fichier`, `Analyse`, `Affichage`, `Aide`) carry
    no fill at rest and take `radius.sm` on the fill they wear when hovered or
    while their menu is open. At radius 0 the open title was a square of orange
    in an interface with no other square corner, and it is the one fill on screen
    the eye cannot avoid, since it stays for as long as the menu is being read.
  - The empty portions of the bar are the window drag surface. Controls,
    menus, and fields retain their normal pointer behavior. Window placement
    previews use the restrained `.window-snap-preview` outline and disappear
    when the pointer leaves a snap edge or the drag finishes.
  - The main stage is opaque and undecorated. On Windows 11, native DWM corner
    preference supplies the rounded restored-window outline; maximized and
    full-screen states remain square. The shell, top bar, and sidebar must not
    add a window-level background radius, because it produces a fringe over the
    native DWM cutout. Unsupported platforms keep an opaque square outline.

### 4.3 Card (metric)

- **Anatomy:** title (Instrument Sans, muted) → numeric value (Instrument Sans, emphasized).
- **Classes:** `.card`, `.card-title`, `.card-value-mono`. Sizing for the
  metric row comes from `.metric-grid .card`.
- **Rules:**
  - Show the real value, or `-`. Never invent placeholder data
    ("Pending check", "Awaiting selection").
  - Cards carry counts and identifiers, so the value uses the system-value role.
  - Metric cards are a row of equal small tiles, never a hero number with
    supporting stats.

### 4.4 Preview table (see §4.11)

The dashboard `.widget` and the single-column `.activity-table` are gone.
Every multi-column surface — scan preview, history, exact plan — is a
`.preview-table` (§4.11). Status dots survive on their own as `.dot` plus
`.dot-good` / `.dot-warn` / `.dot-error` / `.dot-idle`, used today by the TMDB
section of Settings.

### 4.6 Settings section

- **Anatomy:** title (Instrument Sans accent) → description (muted) → status/action row.
- **Classes:** `.settings-section`, `.settings-section-title`,
  `.settings-section-description`, `.workspace-value`, `.tmdb-attribution`,
  `.tmdb-attribution-copy`, `.tmdb-attribution-link`.
- **Rules:**
  - Reuse `SettingsPane` rather than re-implementing the layout per story.
  - The workspace path always uses `.workspace-value` (Instrument Sans, accent-hover color).
  - The TMDB row displays the real Janus-backed connection result established
    automatically during application startup: `.dot-good` + `Actif` on success,
    `.dot-error` + `Inactif` otherwise. It never exposes the caller key, upstream
    TMDB credential, or raw connection error and offers no manual test button.
    There is no local cache control because Janus owns response caching.
  - The TMDB section ends with the official dark-background logo, the provider's
    recommended attribution copy, and a keyboard-focusable direct link to the
    The Movie Database website. The logo is bundled so attribution remains visible
    offline; the link opens through JavaFX host services.
  - Settings is a sidebar surface, not a dialog: it replaces the content
    area like Scan and History do, and is laid out like them. Its root carries
    `.screen-root` for padding and vertical rhythm, spans the full content
    width, and opens on a `.section-heading.section-heading-accent`
    (`PARAMÈTRES`) followed by one `.page-subtitle` line — the same two rows as
    the Scan and History headers. No page-specific padding, no centred column,
    and no larger title above the first section: those three made the content
    move sideways and downwards every time the user changed screen.
  - The language combo sits alone on its row, so it carries `setAccessibleText`
    with the localized language label.

### 4.7 Buttons

- **Scan topbar:** use `.menu-button.load-primary` for the `Charger` / `Load`
  dropdown: transparent interior, orange border, white text, and visible orange chevron.
  Its hover, pressed, and open states remain unfilled. It opens
  `Load folder`, `Load files`, `Add folder`, and `Add files`. Use
  `.button.validate-action` for the screen's primary action.
  `Réanalyser` / `Reanalyze` and `Réinitialiser` / `Reset` are shared
  `.button.ghost.header-action` controls immediately to its left. They mirror
  the Analyse menu actions and always share their enabled/disabled state.
- **Pressed state:** all `.button` and `.menu-button` variants keep a subtle
  `:pressed` state with 1 px downward translation and a stronger orange
  border/fill. `.filter-chip` takes the same dip, because a chip is a control
  the user aims at repeatedly and its only other answer happens elsewhere on
  screen.
- **Variants:**
  - default `.button` — orange-on-translucent, deeper fill and edge on hover,
    the workhorse.
  - `.button.primary` — solid orange, dark text. Use for the single primary
    action of a screen ("Continue", "Test connection").
  - `.button.ghost` — transparent with subtle border, for tertiary actions
    (cancel, dismiss).
- **Rules:**
  - At most **one** `.primary` button per screen.
  - Disabled state is one opacity, `0.45`, for every button and icon button.
    `.button.validate-action` additionally swaps to a neutral grey fill because
    a dimmed solid orange still reads as available.
  - Button typography is centralized in `.button`: Instrument Sans at the
    13 body step, 600 weight, 34 px minimum height, and ellipsis overrun for
    long FR/EN labels. Icon-only buttons override to the 17 step, since a
    single glyph at body size disappears in a 32 px target. Screen-specific buttons should add only semantic variants
    (`.primary`, `.ghost`) rather than inline font rules.

### 4.7d Pointer

- **Rule:** anything that acts on click carries `-fx-cursor: hand`, and states it
  on its **base** selector, not inside `:hover` — the pointer has to change on the
  way in, not after the fill does. Its `:disabled` rule returns
  `-fx-cursor: default`, because a control that cannot be pressed must not invite
  the press.
- **Covered:** `.button` / `.menu-button` (so every variant and the title-bar
  menus inherit it), `.nav-item`, `.filter-chip`, `.icon-button`,
  `.workspace-explorer-header`, `.row-checkbox`, `.combo-box` and the cells of
  its popup, `.context-menu .menu-item` (every menu the application opens),
  `.tmdb-attribution-link`, the filled cells of `.tmdb-search-results`, the
  table's selection cell, and the **label** of `.preview-table .column-header`,
  which sorts. The label and not the header: the skin drives the header's own
  cursor to a resize arrow near each edge through `setCursor`, and a
  `setCursor` call outranks a stylesheet, so a rule on the header would either
  lose or take the resize cursor with it.
- **Deliberately not:** text fields and the pattern editor keep the caret; table
  rows keep the arrow, since selecting a row is what a table does and a grid where
  every cell begs to be clicked reads like a page of links; the window buttons
  keep the arrow, because the system's own do; a dialog header being dragged is a
  window move, not a click.

### 4.7c Keyboard focus

- **Classes:** no new class. `:focus-visible` rules live in §10b of `app.css`
  and cover `.button`, `.menu-button`, `.nav-item`, `.filter-chip`,
  `.icon-button`, `.row-checkbox`, and `.preview-table`.
- **Rules:**
  - Every focusable control shows the same two-stroke ring (§2.4). A control
    that overrides `-fx-background-color` loses Modena's focus ring, so any
    new control that styles its own background must add its own
    `:focus-visible` rule in the same change — in both stylesheets, since the
    light theme's `:hover` rules would otherwise take the outer stroke with
    them.
  - Use `:focus-visible`, never `:focused`: a mouse click must not light up
    the control the pointer just pressed.
  - Solid-orange actions (`.primary`, `.validate-action`) ring in `#fff7ed`,
    since an orange ring on an orange fill is invisible.
  - A control that keeps the `button` style class inherits `.button:hover`,
    `.button:pressed` and `.button:focus-visible` for free; it must not restate
    them. What it does owe the shared rules is a corner radius on its **base**
    selector, since those rules draw on it. `.episort-search-clear` declared its
    radius inside `:hover` only, so its hover chip rounded to 6 and its focus
    ring to the `.button` default of 8 on the same 24 px box.
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

- Translucent surface, subtle orange border at rest, full orange plus the §2.4
  ring on focus.
- Combo popup uses `.combo-box-popup` styling; no overrides needed per call site.
- The hierarchical `.tmdb-episode-picker` follows the same input surface in
  both themes; it must never keep the dark fill when the light stylesheet is
  active.
- Light combo boxes explicitly own the displayed value plus popup `filled`,
  `selected`, `focused` and `hover` cells. Popup scenes do not inherit the
  shell's `.theme-light` class, so those popup selectors remain unscoped inside
  the light-only stylesheet and must always set text and background together.

### 4.9 Section heading (`LABEL`)

- **Class:** `.section-heading`, optionally with `.section-heading-accent`.
- **Rules:** Instrument Sans 13 / 600, uppercase, preceded by a 1 px orange keyline supplied by
  CSS rather than decorative text. The base treatment is muted; the optional
  accent treatment raises the label to primary text and uses the full accent
  on the keyline. Used to introduce major dashboard regions (e.g. `SCAN`,
  `HISTORIQUE`, `SOURCE`). Translation values contain the label only. All
  instances stay on the 13 px body step.

### 4.10 Banner (in-screen safety / info)

- **Anatomy:** `HBox` with a compact semantic marker (`•` for information) →
  wrapping prose label.
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
    workflow: Workspace & Scan, Scan des fichiers, Correspondances TMDB,
    Revue du plan, Appliquer.
  - Exactly one step carries `.active`; prior steps may be `.complete`; future
    steps are `.pending`.
  - Keep any safety text secondary. The stepper is the primary element at this
    location.

### 4.11 Preview table (multi-column)

- **Anatomy:** `TableView<ScanRow>` (or `TableView<RunEvent>`) whose first
  column is a `CheckBox` cell, followed by system-value columns for paths /
  extensions / metrics and a final status-pill column.
- **Classes:** `.preview-table` on the table; cell variants
  `.cell-mono`, `.cell-muted`, `.cell-good`, `.cell-warn`, `.cell-error`;
  `.selection-cell` on the clickable selection cell; `.row-checkbox` on the
  header and row checkboxes; `.row-status` plus one of `.quiet` /
  `.quiet-muted` / `.ready` / `.warning` / `.ignored` /
  `.replace` / `.conflict` / `.danger` on the status cell. That list is closed:
  every variant in it is returned by `ScanTableCells.statusStyle`,
  `PlanReviewText.pillVariant` or `HistoryScreen.styleClassFor`. A variant no
  mapping can produce is an ink waiting to be used inconsistently, which is why
  `.preview` was removed rather than left available.
- **Rules:**
  - **A status is a word, not a badge.** `.row-status` is Instrument Sans 10 / 600 text:
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
  - The selection column has no header text, so its header checkbox carries a
    `Tooltip` and `setAccessibleText` of its own, and each row checkbox is named
    with the file it acts on. A column of boxes announced only as "check box"
    tells a screen reader which control it is and never which row.
  - Paths, filenames, extensions, and metrics use `.cell-mono`. Prose
    columns (media type, status pill text) use the default cell style.
  - In light mode, selected rows keep dark ink on the pale-orange selection
    fill. Positive proposed names use the darker light-theme green; neither
    selection nor status may fall back to luminous dark-theme text colors.
  - Long values display the truncated text in the cell **plus a tooltip
    with the full value** so the user always has access to the original.
  - Empty cells render the design-system placeholder `-` with the
    `.cell-muted` style. Never fabricate proposed names, TMDB matches,
    destinations, or confidences before the matching service produces
    them.
  - Empty table state goes through `setPlaceholder(...)`, on **every** table
    without exception. JavaFX's fallback is the untranslated string
    "No content in table", so a table that skips this ships an English system
    message into a French screen the day its list comes back empty. A screen that
    supplies its own `.table-empty-state` (glyph + title + hint) owns its
    typography; `.preview-table > .placeholder > .label` is a direct-child
    selector on purpose, so it only dresses the bare-`Label` case and cannot
    silently outrank the empty-state classes.
  - Empty-state glyphs must exist in Instrument Sans. `◫` and `◴` did not and
    rendered as tofu boxes; the scan table uses `≡`, history uses `↺`, and the
    detail panels use `◌`.
  - Each row supplies a `ContextMenu` via `setRowFactory` with the
    actions documented in `UiText.scanContext*` / equivalent. Keep the
    menu short (≤ 5 items) and disable items whose data isn't ready.

### 4.12 Detail panel

- **Anatomy:** `VBox` of Instrument Sans section headings (`§4.9`) and labelled
  fields. Empty state when no row is selected uses a single muted line.
- **Classes:** `.detail-panel`, `.detail-panel-section`,
  `.detail-panel-label`, `.detail-panel-value`, `.detail-panel-value-mono`,
  `.detail-panel-empty`, `.detail-panel-stub`.
- **Rules:**
  - Mirror the row's data exactly — no derived calculations or invented
    fields. Use `-` everywhere data is absent.
  - The TMDB-correction sub-panel uses the shared combo-box and button
    vocabulary for identity search, episode order and Apply / Reset.
  - A resolved match exposes `Voir sur TMDB ↗` / `View on TMDB ↗` through the
    existing `.tmdb-attribution-link`. The URL is built only from a numeric
    TMDB identity and opens through the shell's external-link handler.
  - While a TMDB lookup runs, the panel names the work and reports it with the
    shared `.episort-progress` bar, indeterminate, under the sentence — the
    loader card's own order (§ addendum). It carried a bare Modena
    `ProgressIndicator` arc, the one control in the shell drawn by another
    toolkit outside the workflow strip.
  - Once real series metadata is loaded, the order selector lists every
    **concrete named episode group** advertised by TMDB, not one option per
    category. Two Digital groups such as `Crunchyroll Season Split` and
    `Netflix` remain separate choices, labelled with their category, group count,
    and episode count. Long labels keep a tooltip. `Premier épisode` / `First episode` then opens a hierarchical
    `.tmdb-episode-picker` for the selected group: season-like groups become
    retractable seasons, while Absolute uses ranges of 50 and displays the
    absolute number.
    Its adjacent default button
    applies that episode to one row or a consecutive sequence to the checked
    rows in current table order. Disable both controls when no series metadata
    or starting episode is available; do not hide them.
  - At narrow widths the panel collapses **below** the table (§3.1).
    Never replace the table with the detail panel — the user must always
    see context.

### 4.13 Filter chip

- **Scan filters:** the search box belongs to the heading row. Below the metric
  cards, `ResponsiveFilterPane` keeps the four media-kind chips as a left block
  and the seven status chips as a right block whenever their preferred widths
  plus a 20px separation fit. Its maximum width is bound to the scan table, so
  the right block stops at the table edge and never extends above the adjacent
  correspondence panel. When that panel stacks below, the table and filter bar
  expand together. At narrower widths it stacks both intact groups, left-aligned
  with an 8px row gap. Never split the chips inside either group.
- Ignored rows remain discoverable through the Ignored status filter but are
  excluded from active selection and batch operations.
- **Anatomy:** `ToggleButton` arranged in an `HBox`. Exactly one button
  in the toggle group is selected at a time.
- **Class:** `.filter-chip` (and `.active` is auto-applied via the
  `:selected` pseudo-class — both selectors are kept in CSS for
  symmetry).
- **Rules:** used today by the History screen for the All / Successful /
  Warnings / Conflicts / Failed / Ignored filters. The selected chip
  drives a `Predicate<RunEvent>` from `HistoryFilter`. Never use chips
  for primary navigation — that is the sidebar's job.
  The History filter row is a `FlowPane`: search, filters and the grouped
  rollback/clear actions wrap onto additional rows at narrow widths rather
  than truncating labels or forcing the table outside the content area.

### 4.14 Workspace chip

- **Anatomy:** pill-shaped `HBox` containing the Instrument Sans `>_` prefix and
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

- **Anatomy:** a plain `Label` in Instrument Sans 13px/700, plus a 1px rule across the row
  that opens the block. No pill, no marker, no per-group colour.
- **Classes:** `.group-name` + `.group-name-head` or `.group-name-ignored`;
  `.group-block-start` on the `TableRow`; `.identity-group-name` caps the width
  at 200 in `SeriesIdentityDialog`.
- **Rules:**
  - **The break is the signal.** In the scan table the name is written on the
    first row of a block and every following row of the same block leaves the
    cell blank. Twenty-five files of one series would otherwise mean
    twenty-five repetitions of one piece of information.
  - **System value, at row size.** The seed comes from the filenames. Weight
    and colour distinguish it from row copy (§2.2): a different voice, not a
    louder one. Never set it below the 13px of the rows it heads.
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
  - `SeriesIdentityDialog` sizes to its real row count: 300px for one resolved
    identity, 38px more while the unresolved warning is present, 72px per
    additional row, capped at 600px where its scroll pane takes over. A single
    identity must never open into a mostly empty 520px-high window.

### 4.15 Exact plan review pane (Epic 6)

- **Anatomy:** in-window pane replacing the content area (no second `Stage`) →
  header → subtitle → metric chip row → conflict batch toolbar (only while
  conflicts remain) → banner → `TableView` (selection, source, source size,
  destination, status, compared copy, dates, decision) → notice → right-aligned action row (`Fermer` ghost,
  `Appliquer` / `Valider et exécuter` primary).
- **Entry point:** the top-bar primary action (`Voir le plan`) is the single
  route in. It closes the pattern gate on the way, so reaching the plan costs
  one click from a loaded folder.
- **Conflicts are resolved inline:** a blocking row carries its own decision
  `ComboBox` and its source/destination dates in the very list being validated.
  The selection, compared-copy, dates, and decision columns — and the batch
  toolbar — are hidden as soon as no conflict remains. There is no separate
  conflict window.
- **Classes:** reuses `.screen-root`, `.tmdb-dialog-header`,
  `.tmdb-dialog-title`, `.tmdb-dialog-message`, `.selection-column` /
  `.selection-header` / `.selection-cell` / `.row-checkbox`,
  `.tmdb-match-meta` (metric chips), `.banner` + `.banner-info` /
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
  - Source size is computed once when the plan view is refreshed. While a
    conflict is blocking, `Copie comparée` / `Compared copy` names the existing
    destination or duplicate and shows its size; the dates column compares the
    same two files. Missing/unreadable sizes render `—`.
  - Conflicts between two real files offer three explicit outcomes: replace the
    existing destination with the source, keep both files unchanged, or delete
    the source and keep the existing file. The labels name both files and the
    resulting state; never use a bare `Ignorer` / `Ignore`. Structural path and
    folder conflicts never offer deletion. A deletion remains only a row
    decision until the exact plan is applied and then explicitly validated.
    The decision column reserves 340px minimum and every popup option repeats its
    full label in a tooltip; destructive meaning must never depend on truncated
    text.
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
  - A per-file failure reveals the inline warn banner with Retry / Stop. Retry
    is available only for a recoverable error; Stop preserves completed work,
    starts no remaining operation, and writes the file into
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
    the library. A deletion has no destination and renders `-` with
    `.exec-path-empty`; never invent one.
  - **A failure stops the remaining plan.** Stop writes the file into
    `.exec-failure-log`, which outlives the banner that announced it. Retry
    writes nothing: the file may still land. There is no continue-after-error
    action.
  - **The recap splits the counters, it does not hide them.** Four under
    `exec.recap.section.disk` (moved, renamed, deleted, source folders removed,
    parent containers of non-empty source folders renamed with `[TRI]`),
    seven under `exec.recap.section.untouched`. All twelve are always shown; a
    zero is a real answer and takes `.zero`. Only failures may take
    `.alarming`, and only when non-zero. Twelve counters of equal weight is a
    table of numbers, not an answer to "what happened to my files".
  - The exact-plan notice states the post-run folder rule before validation:
    empty source folders are removed, while the parent container of a non-empty
    source folder is renamed in place with the `[TRI]` prefix. The recap count
    comes from completed filesystem renames, never from a prediction.
  - The outcome sentence is coloured by what happened: `.good` for a complete
    success only, `.error` when the run aborted or any file failed, `.warn`
    otherwise.
  - The pane cannot be left while the run is in flight: the back and close
    actions stay disabled and the sidebar and top bar are locked until the
    recap exists.
  - `Retry` is enabled only for `ApplicationError.recoverable()` failures —
    never offer a retry that cannot succeed.
  - Failure text goes through `ApplicationError.safeMessage()`; the recap
    never carries credentials, API keys, or read access tokens.
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
    paragraph → `CHAÎNE DE TRAITEMENT` and its five numbered steps, at 26
    between the three and 8 between the steps. Right, one `.detail-panel`
    holding every value read off the machine: `ENVIRONNEMENT` as a
    name/value table (name in a 74 gutter, baseline-aligned) → divider →
    `FICHIERS ÉCRITS` as three stacked label-over-path fields.
  - **footer** — pinned to the bottom of the content area by a growing spacer,
    opened by a `.settings-section-divider` rule: `SOURCES ET CRÉDITS` over
    the TMDB attribution and the font credit, side by side on the same 640 /
    360 grid as the body.
- **Classes:** `.about-pane` on the `.screen-root` (spacing 26),
  `.about-fact-panel` on the right panel (`.detail-panel` plus spacing 18, for
  the divider between its two groups), plus `.about-brand-name`,
  `.about-tagline`, `.about-version`, `.about-body`, `.about-step-index`,
  `.about-step-label`, `.about-label`, `.about-value`, `.about-value-path`,
  `.about-note`. Header reuses `.tmdb-dialog-header` / `.tmdb-dialog-title` /
  `.header-action`; the rules reuse `.settings-section-divider`.
- **Rules:**
  - **Section headings follow §4.9.** `LABEL`, uppercase, Instrument Sans, with the
    shared orange keyline. About was
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
    `-`.
  - The TMDB attribution row is required and stays on this screen. It includes
    the approved TMDB logo and the provider's endorsement disclaimer.
  - About holds the content area, so it disables its own menu entry and the
    plan review disables it in turn (§4.7 refusal by disabling). Opening it
    hides the top bar's primary and secondary actions: nothing
    in the bar acts on what is on screen.
  - The missing-workspace gate does not cover About: it guards the work, not
    the documentation.

---

## 5. State, signals & i18n

### History rollback action

- The history filter toolbar reuses the shared `.button.primary` vocabulary for
  `Rétablir le plan sélectionné`. The action stays visible and disabled unless
  the selected execution has a retained reversible manifest. Selecting another
  history row immediately recomputes availability; no parallel component is
  introduced.
- Rollback is a full-frame flow in the content area, never a modal. Its review
  reuses the exact-plan anatomy: header and shared workflow stepper, subtitle,
  metric chip, `.banner`, `.preview-table.plan-table`, notice, and right-aligned
  actions. Current path, original path, and status mirror the source,
  destination, and status columns of the rename-plan review. Each row is one
  exact inverse move (`current → original`); paths use `.cell-mono`, leading
  ellipsis, and a full-path tooltip. A manifest that retains no move renders
  `UiText.rollbackEmpty` as the table placeholder (§4.11).
- Confirming swaps the review body in place for the execution anatomy: shared
  workflow step 5, `.exec-readout`, exact current/original path pair, and
  `.episort-progress`. Completion swaps that body for an `.exec-recap` and keeps
  it visible until the user closes it. Navigation is locked only while disk
  operations are running.
- Rollback completion and refusal are real audit events. The button disables
  immediately after success because a manifest can be consumed only once. The
  history banner then persists the real success (`.banner-good`) or refusal
  (`.banner-error`) result instead of leaving the disabled action as the only
  feedback. Both are semantic variants of the shared `.banner` component.

- **No fabricated state.** Every metric, pill, widget value must trace back
  to a `AppShellViewModel` field, an `Optional<Path>` supplier, an
  `ApplicationError`, or a documented spec value (e.g. Story 1.5 ACs).
- **Status pill mapping** (Section 4.2): single source of truth.
- **Language:** all user-visible strings exist in both FR and EN. Route
  through `UiText` rather than hardcoding. The dashboard reads
  `currentViewModel.language()` after every refresh.
- **Theme:** the first launch is dark. Preferences offer System, Dark and Light.
  System follows the Windows application-theme setting while Episort is open;
  on unsupported platforms or when detection fails it safely resolves to dark.
  Both themes share component anatomy, typography, status colours and the orange
  accent; `.theme-light` supplies the alternate neutral surfaces and text tokens.
  Light mode preserves the dark theme's hierarchy: section headings, active
  navigation and interactive states remain orange; inactive navigation icons
  remain neutral. The top-bar workspace chip uses a light neutral surface with
  dark Instrument Sans text, and `.workspace-value` remains unboxed machine data rather
  than adopting text-field chrome.
  It also mirrors the dark theme's material cues rather than flattening them:
  two restrained ambient lights, orange-tinted shell separators, subtle orange
  panel borders, softly tinted controls, and the same hover/focus escalation.
  Popup menus are full theme surfaces: 10 px radius, 8 px maximum shadow blur,
  warm/cool panel background, readable enabled and disabled labels, tinted hover,
  and theme-matched separators. Disabled items retain full container opacity;
  their muted label colour alone communicates unavailability.

---

## 6. Do / Don't

**Do**
- Reuse `.card`, `.banner`, `.button`, `.section-heading`, etc. for new screens.
- Add a new component class only when the existing ones genuinely don't fit;
  document it in this file in the same PR.
- Keep panel borders 1px and use `border.subtle` unless the element is
  focused or active.
- Use `-` as the only "no data" placeholder.

**Don't**
- Invent metrics, timestamps, counts, or activity rows for unbuilt epics.
- Add a second accent color, gradient buttons, or RGB animations.
- Use heavy drop-shadows on text or icon-only buttons without labels.
- Hardcode hex colors in new code; reference an existing class.
- Introduce nav items for surfaces that don't yet exist.

---

## 6b. Motion

Motion here confirms that a state changed. It never announces, never loops, and
never draws attention to itself: the brand is precise and mechanical, so a
movement decelerates into its final state and stops.

### The scale

| Token     | Duration | Used for                                                        |
| --------- | -------- | --------------------------------------------------------------- |
| `pointer` | 90 ms    | Hover and press feedback: text warming, a button's 1px press dip, a row's tick |
| `surface` | 160 ms   | A surface changing condition: enabled/disabled, selected, focus, a dropdown opening, a table answering a filter chip |
| `view`    | 240 ms   | A whole screen or full-window overlay arriving or leaving        |

One easing curve: `ease-out`. No `ease-in-out`, no spring, no overshoot, no
`linear` except in tests. Durations are not tokenisable in JavaFX CSS — there
are no custom properties for non-colour values — so this table is the contract.
`MotionScaleTest` enforces it on both sides: `app.css` may declare no other
duration, and the Java-side constants (`ViewTransition`, `PopupMotion`) must
land on one of these three.

### What can actually be animated

JavaFX interpolates a CSS property only when it is a real styleable property of
an interpolatable type. Measured on JavaFX 25 (see `CssTransitionSupportTest`
and the §28 comment in `app.css`):

| Property                                  | Interpolated |
| ----------------------------------------- | ------------ |
| `-fx-text-fill`                           | yes          |
| `-fx-opacity`                             | yes          |
| `-fx-scale-x` / `-fx-scale-y`             | yes          |
| `-fx-translate-x` / `-fx-translate-y`     | yes          |
| `-fx-background-color`, `-fx-border-color`| **no**       |

A Region's background and border are the composite properties
`-fx-region-background` and `-fx-region-border`; JavaFX holds the start value
and flips it rather than interpolating. Writing
`transition: -fx-background-color …` is a silent no-op — the declaration parses,
nothing moves, and nothing warns. So a colour wash lands in one frame while the
transition carries the text colour, the lift, or the fade.

### Where each part lives

A rule of thumb for choosing between CSS and Java: **CSS can only carry a
transition when a state changes.** Anything that has no "before" — a screen
mounted for the first time, a popup window opening, a list replaced wholesale —
has to set its start state in Java and play forward.

- **Pointer and surface motion**: §28 of `app.css`, declared on base selectors so
  the transition applies both entering and leaving a state.
- **View and overlay motion**: `ViewTransition` in Java. A screen root outlives
  its time on screen and is remounted later still holding its old opacity, so a
  CSS entrance class would animate it *away* from that value first. Java sets
  the start state and plays forward, which cannot flash. Only the incoming side
  is animated — fading a screen out first would add its duration to every
  navigation.
- **Filtered content**: `ViewTransition.playContentSwap`, on the table's
  `.virtual-flow` so the column headers hold still. Played when a filter chip
  replaces the list in one gesture, and deliberately *not* when the search box
  narrows it — a settle per keystroke would make the table strobe. Opacity only:
  the flow is clipped to the table, so offsetting it would open a strip of empty
  table along one edge for the length of the animation.
- **Dropdowns**: `PopupMotion`, installed once on `Window.getWindows()` from
  `EpisortApplication.start`. Every `PopupControl` the application opens — the
  context menus, the menu bar's menus, `ComboBox` and `MenuButton` popups,
  tooltips, including the ones built by skins Episort never touches — fades in
  and slides 8px out of its anchor: down when the popup opens below the control,
  up when JavaFX flipped it above. The slide moves the popup's *window*, not its
  root — a popup's scene is sized exactly to its content, so offsetting anything
  inside it is cut off by the window edge and opens a strip of nothing along the
  opposite one. Not claimed: the bare `Popup` behind the window-snap preview,
  which must answer a drag in the frame it is asked for. Closing is instant, for
  the reason the incoming-side-only rule gives above.
- **Scrolling**: `SmoothScroll` + `ScrollEasing`. Wheel notches move a target and
  the content approaches it exponentially, so repeated notches read as one
  continuous movement. Frame-rate independent by construction.
- **Frame rate**: `RenderTuning` matches the JavaFX pulse to the display at
  startup instead of leaving it at 60 Hz. Verify with
  `./gradlew run -Depisort.debug.fps=true`, which also reports window focus —
  Windows presents a background window at the desktop's base rate whatever the
  pulse asks for, and that stretch is not a regression.

### Do / Don't

**Do**
- Reuse the three durations and `ease-out`.
- Put a transition on the base selector, not on the `:hover` rule.
- Declare the resting value of every animated property on that same base
  selector. A property only one state names leans on the runtime to put it back
  on the way out, which is a state change nothing in the stylesheet describes.
  `.button` names `-fx-opacity: 1` for that reason: the dimmed value belongs to
  `:disabled`, and an action returning to available has a rule to return to.
- Animate a disabled state: an unavailable action stays on screen by design
  (§1), and fading into it is the difference between dimming and flickering.
- Reach for opacity when a colour has to change and cannot be interpolated. The
  row checkbox's tick is drawn at rest in the accent and hidden at `-fx-opacity:
  0`; that is the whole reason ticking a row can animate.

**Don't**
- Animate a background or border colour — it silently does nothing.
- Add a pulsing, looping, or attention-seeking animation.
- Animate a discrete gesture that repeats fast, such as each keystroke in a
  search field. Motion marks a state change, and typing is one long change.
- Animate anything on the path of a bulk operation's progress readout: a
  progress figure must be able to jump, because the work does.

---

## 7. Files

| File                                                              | Role                                                |
| ----------------------------------------------------------------- | --------------------------------------------------- |
| `src/main/resources/styles/app.css`                               | Dark/base tokens and all component anatomy           |
| `src/main/resources/styles/light.css`                             | Complete light-theme token and state overrides       |
| `src/main/java/com/episort/ui/AppShell.java`                      | Layout shell: sidebar + top bar + view host         |
| `src/main/java/com/episort/ui/Sidebar.java`                       | Three-entry sidebar (Scan / History / Settings)     |
| `src/main/java/com/episort/ui/TopBar.java`                        | Workspace chip, status, language, primary           |
| `src/main/java/com/episort/ui/scan/ScanScreen.java`               | Scan dashboard (table + detail panel)               |
| `src/main/java/com/episort/ui/scan/RowDetailPanel.java`           | TMDB-correction detail panel                        |
| `src/main/java/com/episort/ui/execution/PlanReviewPane.java`      | Plan review + inline conflict resolution + execution, in the main window (§4.15) |
| `src/main/java/com/episort/ui/execution/ExecutionPane.java`       | Execution progress, failures, and recap (§4.16)     |
| `src/main/java/com/episort/ui/AboutPane.java`                     | About screen in the content area (§4.17)            |
| `src/main/java/com/episort/ui/LoadingOverlay.java`                | Blocking-work overlay (addendum)                    |
| `src/main/java/com/episort/BuildInfo.java`                        | Version read from the Gradle build stamp            |
| `src/main/java/com/episort/ui/history/HistoryScreen.java`         | History dashboard backed by `RunEventStore`         |
| `src/main/java/com/episort/ui/settings/SettingsPane.java`         | Settings-section component                          |
| `src/main/java/com/episort/ui/UiText.java`                        | FR/EN strings — extend, don't bypass                |
| `src/main/java/com/episort/ui/ShellLayout.java`                   | Wide / medium / compact and what each gives up (§3.1) |
| `src/test/java/com/episort/ui/ShellLayoutTest.java`               | Asserts every layout is reachable by a real window   |
| `src/main/java/com/episort/ui/Fonts.java`                         | Loads the four Instrument Sans faces (§2.2)         |
| `src/test/java/com/episort/ui/TypographyScaleTest.java`           | Enforces the size scale and the weight↔family bind  |
| `src/test/java/com/episort/ui/TextPaletteTest.java`               | Enforces the closed text-ink set (§2.1)             |
| `src/main/java/com/episort/ui/ViewTransition.java`                | Screen, overlay and filtered-content entrances (§6b) |
| `src/main/java/com/episort/ui/PopupMotion.java`                   | Dropdown and tooltip entrances (§6b)                |
| `src/main/java/com/episort/ui/SmoothScroll.java`                  | Continuous wheel scrolling on tables and panes (§6b) |
| `src/main/java/com/episort/ui/ScrollEasing.java`                  | The curve that scrolling follows (§6b)              |
| `src/main/java/com/episort/ui/platform/RenderTuning.java`         | Matches the JavaFX pulse to the display (§6b)       |
| `src/main/java/com/episort/ui/platform/NativeWindowMove.java`     | Hands window dragging to Windows                    |
| `src/main/java/com/episort/ui/platform/NativeWindowFrame.java`    | Gives the window the system frame, drawn by nobody   |
| `src/main/java/com/episort/ui/diagnostics/FrameRateProbe.java`    | `-Depisort.debug.fps=true` frame-rate readout       |
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
- Manual TMDB search uses `.tmdb-dialog`, `.tmdb-search-results`,
  `.tmdb-result-card`, `.tmdb-selected-card`, `.tmdb-poster-frame`,
  `.tmdb-poster-placeholder`, `.tmdb-match-title`, `.tmdb-match-meta`,
  `.tmdb-match-overview`, and the top-search-compatible `.tmdb-search-box`.
  Results are visual rows with a poster/placeholder, metadata, short overview,
  and a select action; no-result and initial states use one centered
  `.tmdb-empty-state`; loading and errors stay inside the dialog instead of
  blocking the scan table.
  In the narrow scan detail panel, the selected poster frame is 96Ã—142 so the
  title and overview retain a readable text column; truncated titles, proposed
  paths, destinations, and notes expose their complete values in tooltips.
  The elastic title input may be paired with compact `.tmdb-search-year` and
  `.tmdb-search-id` standard text fields. Both are optional, digits-only
  refinements: year maps to TMDB's four-digit `year` filter, while an ID takes
  priority and performs an exact TMDB record lookup. These fields reuse the
  existing text-field tokens and states; they add no color or spacing token.
- Blocking work uses the in-shell overlays, not a window: `.app-loader-overlay`
  + `.app-loader-card` for analysis, and `.prereq-overlay` + `.prereq-card` for
  the missing-workspace gate. Never use a native `Alert` / `Dialog`.
- The loader card is a status panel, not a splash: everything in it is
  left-aligned — `.app-loader-eyebrow` (Instrument Sans, accent), `.app-loader-text` (the
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
  `.pattern-editor`: an Instrument Sans dark `TextArea` matching `.text-field`. A
  `TextArea` with no class keeps its default light control background and
  punches a hole in the panel.
- Every user-visible string goes through `UiText`, including file-chooser
  titles, extension-filter labels, and failure copy. French copy is
  vouvoiement throughout, and `…` is the only ellipsis.
