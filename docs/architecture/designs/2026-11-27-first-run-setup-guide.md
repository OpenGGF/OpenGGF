# First-run setup guide (0.8)

Owner-approved design (revision 2, 2026-11-27): Balanced is Wide 16:9 with Native
widescreen, and Multiplayer is a shortcut, not a preset. Implemented in
`MenuSetupGuide` with `SetupGuideSession`, `SetupGuidePreset` and `SetupGuideLibrary`.
The rendered mock-ups and the proposal page are kept outside the repository for review at
`$HOME/scratch/agent-tmp/keep/openggf-setup-guide-design-20261127/`
(`out/setup-guide-preview.html`, `out/00-contact-sheet.png`).

## Flow

Legal notice (Language is offered there) › **1 Your games** › **2 How do you want to
play?** › **3 Ready to play**, or **3 Play together** on the multiplayer path. A player
whose games are already in the ROM folder reaches gameplay with Continue, Enter
(Balanced) and Enter (Play). Back returns a step; Back on step 1 asks **Leave setup?**
(Keep setting up is the default; Skip for now records `skipped`). A guide opened from
Extras after it was settled closes without that prompt and keeps its progress for the
rest of the run.

## When it opens, and what is saved

| Event | Behaviour |
| --- | --- |
| Eligibility | The engine-built hub, `hub.setupGuide` blank, not test mode. Hubs built by tools and tests have no session and keep the earlier startup rule. |
| Same run | `SetupGuideSession` is owned by `Engine` for the process; `claimAutomaticOpen` succeeds once, however often the hub is rebuilt. A run whose guide opened never also opens Add your games. |
| Fresh vs upgrade | An upgrade is a `config.yaml` that existed when the configuration was loaded (`ConfigurationPersistence.loadedExistingFile`). Upgrades and Extras visits add Keep my settings, first and recommended. |
| Games | Saved as they are added (`SetupGuideLibrary.plan`): only games that do not already work; a different file for a working game is a conflict whose default keeps the current file; `roms.directory` is set only while no game works. |
| Finish | The chosen style's differing values and `hub.setupGuide: done` in one `ConfigurationPersistence.apply`. A failed save stays on the page with the existing save-failure message. |
| Skip | `hub.setupGuide: skipped` only. |
| Window closed | Nothing but added games is saved; the guide opens next launch. |

Deviations from the proposal: the state key is `hub.setupGuide`, not `startup.setupGuide`,
so it sits with the other hub-owned state (`hub.lastGame`, `hub.dismissedNotices`) in
Advanced rather than as a Gameplay row. Strings use the `menu.setup.*` namespace. When the
hub has a session, Extras is always shown, with Setup guide as its last row; its subtitle
reads Extra modes.

## Play styles

| YAML key (default) | Stock | Balanced | Enhanced | Multiplayer | Keep |
| --- | --- | --- | --- | --- | --- |
| `display.aspect` (`NATIVE_4_3`) | `NATIVE_4_3` | `WIDE_16_9` | `WIDE_16_9` | kept | kept |
| `display.widescreenDrawMode` (`EDGE`) | kept | `NATIVE` | `NATIVE` | kept | kept |
| `display.interpolation` (`false`) | `false` | kept | `true` | kept | kept |
| rewind (`rewind.budgetScope`, `act`) | `off` | kept | on | kept | kept |
| `display.spriteScanlineLimit` (`false`) | `true` | kept | kept | kept | kept |
| `audio.fmCore` (`fast`) | `accurate` | kept | kept | kept | kept |
| `gameplay.loadTimeSimulation` (`FAST`) | `PROFILED` | kept | kept | kept | kept |

Rewind is written through `rewind.budgetScope`, which gameplay reads and which mirrors
`rewind.liveEnabled` (`SonicConfigurationService.applySettings`). Enhanced turns it on by
restoring the default `act` scope only when it was `off`, so a chosen scope survives.
Per-game Game Setup profiles (`launch.*`) are never written.

Native widescreen is frame-for-frame equivalence-tested against 4:3 for S1 GHZ1, S2 EHZ1
and S3K AIZ1 (`2026-11-13-widescreen-native-draw-mode.md`). Sonic CD reads the
simulation width (`CdObjectWindowing`) but its wide tests pin Edge, so the guide says
"Sonic CD: wide picture only; same play as 4:3 not yet verified". Stock is described as
closest to the originals, never as exact: OpenGGF reimplements rather than emulates.

## Sonic CD

`HostedGameMedia` gained `candidate(Path)` and `inspect(Path)` returning a typed
`MediaKind` (READY, OTHER_RELEASE, INCOMPLETE, UNREADABLE, NOT_THIS_GAME) and the exact
English diagnostic; `CdDiscLocator.inspect` reuses the folder search's probe (layout and
512 header bytes, no hashing). Headlines and fixes come from the kind, never from
parsing messages. READY is identified, not verified: the guide shows **Found**, and
`CdHostedMedia.openVerified` still checks the whole disc against Redump 29188 at start.
Time Attack rooms race only Sonic 1, Sonic 2 and Sonic 3 & Knuckles (`GhostFileCodec`),
so Play together never offers Sonic CD.

## Layout and the menu rubric

Rubric: `docs/architecture/audits/2026-10-27-menu-design-review.md` (type, hierarchy,
spacing, colour, icons, consistency, clarity; 5 functional, 7 good indie, 9 production
polish). Scores below are the author's judgement from the production pictures, not a
usability study.

| Screen | Key decisions | Self-assessed |
| --- | --- | --- |
| Your games | Shared badge column; Ready green, Found cyan, problems amber with WARNING, missing dim with INFO; side-by-side Scan/Locate that stack when a translation needs it | 9, spacing 8.5 |
| Empty library | Cyan INFO and a title naming what is missing; two action rows; a visible Set up later | 9, spacing 8.5 |
| Sonic CD problem | Content-sized alert, `Error` label column, path broken after folders; lines that do not fit move to Details (F1) | 9, spacing 8.5 |
| How do you want to play? | One icon per style (proposed CARTRIDGE, BALANCE, WIDE, PLAYERS; CHECK for Keep), RECOMMENDED tag, THIS SETS from existing Settings labels, caveats never dropped | 9, icons 8.5 |
| Prompts | CHECK leads the safe default, WARNING and amber the consequential choice; diff rows before ▸ after; buttons go side by side when stacking does not fit | 9 |
| Play together / Ready | GLOBE and PLAYERS action rows; gold primary Play with PLAY, as the hub's START | 9 |

Fitting never ellipsizes: blocks drop in a fixed order (notes, reassurance, ready rows into
the count, diagnostic and fix into Details), style rows shrink before caveats would leave
the page, and Details always holds the complete text. Paragraph lines that shape right to
left align right, as the legal page's do; chrome stays left to right.

## Translations

67 of the 69 approved messages (`menu.setup.*`; the unused step counter and intro line were left out), `menu.setup.games.unreadable`, the
`hub.setupGuide` Settings label and help, and drafts for six existing keys the guide shows
that were missing from every locale (`crash.field.error`, interpolation, the draw-mode
label and its Native/Edge values, `settings.value.scd`). All are `claude-opus-5-5`
machine drafts with `reviewed: false` in all 19 shipped catalogs (es-419 overrides eight
and inherits the rest from es). No human or native-speaker review has happened. Arabic,
Urdu, Bengali, Hindi and Thai especially need native assessment, and Japanese, Korean
and Chinese game-name forms follow each catalog's existing entries.

## Verification

- `TestSetupGuidePreset`, `TestSetupGuideLibrary`, `TestSetupGuideSession`,
  `TestCdDiscInspection`: preset writes through the real file, upgrade diffs, plan rules
  (no clobber, ROM folder), Sonic CD kinds on synthetic discs (`SyntheticProgramDisc`), a
  ROM-backed Sonic 2 identification, and the once-per-run guard.
- `TestMenuSetupGuide`, `TestMasterTitleSetupGuide`: keyboard and controller through the
  real input path; finish, skip, cancel and re-entry; Keep, multiplayer shortcut; the hub's
  startup rule and Extras entry.
- `TestSetupGuideLocaleCompleteness`: every message the guide can show is a current
  translation in every shipped locale (no English fallback); drafts are marked unreviewed.
  A deliberately stale source string failed it for every locale before it was restored.
- `TestSetupGuideRendering`: production font and GL renderer under surfaceless EGL, 15
  states × 20 languages × 320 and 400 pixels: complete glyphs, ink inside the frame, no
  ellipsis, and games-page blocks that neither overlap nor reach the footer.

## Known limits

- Race online opens Extras › Time Attack itself, not a specific room mode.
- Generic controllers show the engine's generic button names (for example SOUTH) in the
  footer, as other menus do.
- The guide's own text is machine-drafted; see Translations.
