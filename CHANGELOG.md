# Changelog

All notable changes to Episort are documented in this file.

## [Unreleased]

No changes yet.

## [0.2.0] - 2026-08-13

### Changed

- Analysing a full media library sends a fraction of the metadata requests it
  used to, and sends them several at a time. A show now costs two requests
  whatever its length, because its seasons are asked for twenty at a time in a
  single call instead of one call per season; a film costs none beyond the
  search that found it, which already carries its title and year; a folder the
  scan reads as a series never queries the film index, nor the reverse; and
  folders that resolve to the same title or the same show share one lookup even
  when they are analysed at the same moment. On a library of 300 shows and
  1,000 films, that is roughly 3,000 requests where the previous version sent
  more than 20,000.
- Requests leave at a rate the gateway reports it can take, rather than as fast
  as the application can produce them. The pace is read from the remaining quota
  on every answer and spread over the time left before it resets, so a small
  folder runs at full speed and only a library-sized run slows down. A refused
  request is waited out for exactly as long as the gateway asks, then sent
  again. Cancelling an analysis now stops the pending requests instead of
  letting them finish.
- The interface now runs at the display's refresh rate instead of JavaFX's
  default 60 Hz ceiling. On a 480 Hz display the measured rate went from
  62.6 fps to 500 fps, and the frame dropped every second by the beat between
  the 60 Hz pulse and the display's refresh — a 31 ms stutter — is gone
  (worst frame now 3 ms).
- The window is placed by Windows rather than by Episort. It carries the system
  frame, hidden behind the title bar Episort draws itself, which brings back
  everything the system does with a window and Episort was imitating: dragging,
  Aero Snap and the animation into a snapped half, Snap Assist offering the
  other half, the Windows 11 snap layouts, maximizing, and minimizing into the
  taskbar. Episort's own edge detection, snap preview and maximize are gone with
  it. Other platforms keep the previous behaviour, and
  `-Depisort.window.systemFrame=false` or `-Depisort.window.nativeMove=false`
  fall back to it on Windows.
- Wheel scrolling in tables and panels glides instead of jumping by a fixed
  step, at a rate independent of the frame rate.
- Screens, the blocking-work overlay and the dimming behind it fade and settle
  instead of switching between two frames; hover, press, focus and
  enabled/disabled states animate from the stylesheet. The motion scale is
  documented in `docs/design-system.md` §6b.
- Menus, dropdowns and tooltips settle into place instead of appearing between
  two frames. Every popup the application opens is covered, including the ones
  built by JavaFX itself, because the entrance is installed once on the window
  list rather than per control. Closing stays instant: a menu on its way out
  would still be occupying the space its own dismissal is meant to give back.
- Clicking a filter chip on Scan or History settles the rows that answer it,
  and the chip takes the same 1 px press dip as a button. Typing in the search
  box deliberately does not: a settle per keystroke would make the table
  strobe. Ticking a row's checkbox now animates too — the tick is drawn at rest
  and revealed by opacity, which is the only way a shape's colour change can be
  animated at all.

- The sidebar's storage readout reads as three groups instead of five evenly
  spaced lines: the percentage sits with the word that qualifies it beside it,
  the bar stays attached to the figure it draws, and the two measurements form a
  tight pair below. It also starts on the same left edge as the navigation
  labels, and its divider spans the same width as they do. Narrowed to the icon
  rail it now reports `54 %` centred under the glyphs; before, the full phrase
  was cut to `54 ...` and the rail reported nothing.
- The storage readout is quieter. It is ambient information, not something the
  user opened the application to read, so nothing in it takes a heading size or
  a light ink any more: the whole block is one muted grey, the figure sits at the
  body size, and the caption, the measurement and the availability line sit at
  the caption size the rest of the shell uses for a label attached to a value. A
  large bright percentage over a bright used / total line drew the eye away from
  the work in the content area every time it landed on the sidebar.
- The title-bar menu titles round their highlight instead of filling a square.
  The orange behind an open `Fichier` menu was the only square corner in the
  interface, and it stays on screen for as long as the menu is being read.
- Everything that acts on a click now says so under the pointer. The menus, the
  dropdowns and their choices, the row checkboxes, the sortable column headers,
  the TMDB result cards and the TMDB attribution link kept the plain arrow while
  the buttons beside them already showed a hand. Text fields keep the caret and
  table rows keep the arrow, on purpose.

- Java 21 and JavaFX 21 are now Java 25 and JavaFX 25, both long-term support,
  built with Gradle 9. JavaFX 23 is the first release with CSS transitions,
  which is what the animated states above are built on. The portable bundle
  embeds the Java 25 runtime.

### Removed

- Every coloured glow. Hover, focus, pressed and selected states were answered
  by an orange `dropshadow` at zero offset: a halo. It read as a light-emitting
  object on a matte surface, it spread the answer over a dozen soft pixels
  where a control needs an edge, and — invisibly — any node under an effect
  loses subpixel text rendering, so the halos were deciding how a third of the
  application's text was rasterised. States now speak through fill, edge and
  text colour, all of which went one step stronger to carry the difference.
  Keyboard focus, which has to be unmissable, became a two-stroke ring: the
  control's own border at full accent plus a quieter one drawn 3 px outside the
  layout bounds, so nothing moves when focus arrives. Shadows that remain are
  black, offset downward, and mean elevation only; `CssHaloTest` fails the
  build if a coloured one comes back.
- The cyan ambient light in the bottom-right of the dark shell. A second accent
  is against the product's own rule, and the shell now carries one hue.

### Fixed

- Clicking away from the search field now leaves it. It kept the caret, the
  focus ring and the keyboard after a click on empty space, because JavaFX only
  moves focus when the click lands on something that takes it, and most of a
  screen takes nothing. Clicking a button or another field is unchanged.
- The window can no longer be made smaller than the interface it holds. Its
  minimum was declared as 1180×760 while the shell needs 1338×907, and below
  that the interface was not made smaller, it was cut off — the close button
  first. The minimum is now read from the content itself, which is also why
  Windows snaps this window to that width rather than to an exact half.
- The window no longer takes a pale edge when it holds focus. Windows outlines
  any window carrying a frame and picks that colour itself — in dark mode a grey
  light enough to read as white against Episort's shell — so the shell now
  states its own edge, the same one its panels use.
- The window buttons hold their place when the top bar runs out of room. The
  workspace path gives way first, ending in an ellipsis and keeping its tooltip.
- Windows-specific window styling (dark title bar, rounded corners) is applied
  to Episort's own window rather than to whichever window on the desktop
  answered to the title "Episort" first — an Explorer window showing a folder
  by that name could be picked instead.

### Added

- `./gradlew run -Depisort.debug.window=true` reports whether the system frame
  was adopted, whether any of the window is lost to a border, and how much room
  the interface insists on — the three things that cannot be judged by eye.
- `./gradlew run -Depisort.debug.fps=true` reports the frame rate, the worst
  frame of each second, and whether the window holds focus. Windows presents a
  background window at the desktop's base rate whatever the application asks
  for, and the readout says so rather than looking like a regression.

## [0.1.2] - 2026-08-12

### Fixed

- The Windows single-file portable launcher now embeds the Episort icon,
  application metadata, version information, and a GUI manifest.

### Known limitations

- Release executables are not yet code-signed and may trigger a Microsoft
  Defender SmartScreen warning on first launch.

## [0.1.1] - 2026-08-12

### Fixed

- A manually confirmed TMDB movie identity no longer remains blocked by the
  automatic filename-similarity warning after its metadata is loaded.
- An active video recognized as a Series or Movie now overrides an obsolete
  non-media scan group and can be matched against TMDB.
- Double-clicking an editable scan-table cell once again opens its inline
  editor instead of being intercepted by row selection refreshes.
- Files restored from the ignored state immediately regain their TMDB matching
  controls and consistent row metadata.
- `Ctrl+A` selects all scan rows while preserving the native shortcut inside
  text editors.

## [0.1.0] - 2026-08-11

First public release.

### Highlights

- Scan mixed working directories containing `.avi`, `.mp4`, and `.mkv` media.
- Parse and group series and movie releases before any filesystem operation.
- Resolve metadata through TMDB using aired, DVD, and absolute episode orders.
- Access TMDB through the Janus gateway without requiring users to create a
  TMDB account, API key, Janus account, or `.env` file.
- Review detected series groups and ambiguous files before generating a plan.
- Review every source and destination path before confirming file operations.
- Preserve original media extensions and target Plex/Jellyfin-friendly season
  folders and filenames.
- Journal completed operations and provide a reviewed rollback workflow.
- Use the complete French or English desktop interface.
- Distribute one self-contained executable per operating system. It embeds Java
  21 and extracts its verified runtime only into the user application-data
  directory.

### Safety

- Filesystem operations remain constrained to the selected workspace.
- No rename or move occurs before the two explicit validation gates.
- Upstream TMDB credentials remain exclusively in the Janus vault.
- Janus caller access is restricted, monitored, rotated, and revocable by the
  Episort operator.

### Validation

- Windows x64 single-file executable built, extraction-smoke-tested, and
  launched successfully without creating files beside itself.
- Janus/TMDB connectivity validated from the embedded release configuration.
- 472 automated tests completed with no failures or errors (10 skipped).
