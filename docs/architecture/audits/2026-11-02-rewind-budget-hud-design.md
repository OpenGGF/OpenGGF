# Rewind budget HUD design review (2026-11-02)

Read-only visual review of the rewind budget plate states in `TransportHud`. The
seven-criterion rubric and the gameplay-plate family target are from
[the menu design review](2026-10-27-menu-design-review.md#rubric). The finish pass
there leaves the F6-F10 gameplay plates at
Type 8.5, Hierarchy 9, Space 9, Colour 9, Icons 8.5, Consistency 9, Clarity 9 and
**overall 9**. Rules come from the `ui-design-language` skill's in-game overlay
section. The Opus 5.5 implementation is the subject. The reviewer also ran on Opus 5.5.

**Decision: pass.** The budget states score overall **9** and do not fall below the
family's score on any criterion. No changes are required. The scope is the HUD only.
The Settings rubric for the budget setting rows is still the lead's separate
responsibility; Settings captures were not ready for this review.

## Capture provenance

- Tool: `com.openggf.tools.TransportHudDesignCaptureTool` (the production
  `TransportHud` renderer) from the `rewind-budget` worktree's `target/classes`, run by
  the parent through `capture.sh` under
  `<task-output>/`. Settings: surfaceless EGL
  (`-Dopenggf.headless.gl=surfaceless-egl`, with `DISPLAY`/`WAYLAND_DISPLAY` unset),
  `NullAudioBackend`, S2 World REV01 (`s2.gen`), EHZ1 at frame 89 (`state.csv`).
- The output is 3× nearest-neighbour PNGs: 960×672 for the native 320 frame and
  1200×672 for 400. Native 1× geometry was checked by downsampling by 3 and diffing
  against `00-gameplay.png`.
- Images reviewed (first 12 hex characters of SHA-256):

| Picture | 320 | 400 |
| --- | --- | --- |
| `04-live-rewind` (baseline) | `2300e3607845` | `21bbf929f023` |
| `05-rewind-budget-full` | `c2fe5aa28b23` | `1838490ea1f3` |
| `06-rewind-budget-mid` | `c47691d7931d` | `2468a786ed3f` |
| `07-rewind-budget-low` | `fdc2c89b089a` | `ce9ca0c05707` |
| `08-rewind-budget-empty` | `0ea4e33234bc` | `d68ebdcf4ef9` |
| `26-simultaneous-budget-low` | `2df5d125596b` | `18c8ff9f1397` |
| `27-simultaneous-budget-empty` | `6f2ecf5a992c` | `7887b25544fa` |

### Limits

- These are synthetic status snapshots. The capture tool hands each plate state to the
  renderer directly. They do not show that live rewind consumes the budget, refills it,
  enforces the one-second denial or stops automatically at empty. Runtime behaviour
  needs its own tests.
- Only the default 60 fps seconds rendering is shown. PAL 50 support goes through the
  `framesPerSecond` field, which keeps the five-argument record constructor. Its
  seconds text was not captured. No visual change is expected at the 60 fps default.
- One game and zone (S2 EHZ1) at 320 and 400. S1 and S3K lane placement for these states
  was not recaptured. The plate uses the same lane and layout as the accepted live-rewind
  plate, which was checked on all three games in the earlier review.
- Motion between the states (the meter draining and the change from cyan to amber) is
  not shown in still captures.
- Validation reported by the parent: the first focused set of 71 tests passed, including
  the pixel test `TestTransportHudPresentation`. The expanded Settings, Stageplay and
  audio tests are still pending and are not claimed here. This reviewer ran no builds,
  tests or captures.

## Measured geometry (native 320, 1×)

- The budget plate spans x 8–82 and y 177–195 (75×19). The baseline live-rewind plate
  spans x 8–88 and y 180–195 (81×16). The plate grows upward by the 3-row meter
  allowance. Its bottom edge is unchanged, so 4 clear rows remain above lives at y = 200.
- All four states have the same width, and the value is right-aligned in the fixed
  6×8 face. `10.0s` and `6.4s` therefore do not shift the plate or the label.
- Inside the plate, rows run from the top: border, 2 rows of padding, a 7-row text
  line, 5 rows of space, the 2 px meter, 1 row of padding, then the border. The hold
  toast (`13-recording-hold`) has the same spacing from text to meter. The budget meter
  therefore reuses the family's meter layout rather than adding a new one.
- In the empty state the meter becomes 17 dashes, each 2×2 px with 2 px gaps.
  At 1× they read as a dotted line, clearly different from both a solid track and a
  short amber fill.
- The empty glyph is a 9 px skip-to-start sign: a bar followed by two left arrows.
  The bar is separated from the arrows by one column on most rows and joins the first
  arrow's tip on the middle row. It reads as `|◀◀` at 1× and clearly at 3×.

## Scores

| State | Type | Hier. | Space | Color | Icons | Consist. | Clarity | **Overall** |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Full (`05`) | 8.5 | 9 | 9 | 9 | 8.5 | 9 | 9 | **9** |
| Mid (`06`) | 8.5 | 9 | 9 | 9 | 8.5 | 9 | 9 | **9** |
| Low (`07`) | 8.5 | 9 | 9 | 9 | 8.5 | 9 | 9 | **9** |
| Empty (`08`) | 8.5 | 9 | 9 | 9 | 8.5 | 9 | 9 | **9** |
| With playback/recording (`26`, `27`) | 8.5 | 9 | 9 | 9 | 8.5 | 9 | 9 | **9** |
| F6-F10 family target | 8.5 | 9 | 9 | 9 | 8.5 | 9 | 9 | **9** |

Pass or fail against the family score:

- **Typography, pass at 8.5.** The plate uses the fixed native 6×8 transport face that
  the contract requires. Digits are tabular and right-aligned. The lowercase `s` and the
  full-cell decimal point make `10.0s` read evenly spaced rather than tight. That fixed
  6×8 face is the same limit behind the family's 8.5.
- **Hierarchy, pass at 9.** The glyph and `REW` take the status colour, the value is
  white and the meter is visually below both. The eye reaches the state first, then the
  amount.
- **Space, pass at 9.** The plate still uses one row plus the 2 px meter, which the
  skill allows. It sits in the above-lives lane with its bottom edge unchanged and has
  4 clear rows above lives. It stays clear of score, time and rings at both widths.
  The extra space above the meter matches the accepted hold toast. Tightening it in only
  one plate would split the family.
- **Colour, pass at 9.** The plate uses one status colour at a time: cyan when full or
  mid, then amber glyph, label and fill when low. Low keeps the navy edge. Empty adds an
  amber edge, so the warning escalates one step instead of turning a different colour.
  The amber 1.8 s fill reads clearly against the blue track.
- **Icons, pass at 8.5.** The skip-to-start glyph is new but fits the authored
  transport set. At 1× its bar merges with the first arrow tip on the middle row, so it
  matches the family score rather than raising it.
- **Consistency, pass at 9.** The plate reuses the existing `TransportHud` plate,
  `MenuChrome` colours, fixed face and lane. A remaining budget in seconds replaces
  `F:1248` because that amount is the state the player needs.
- **Clarity, pass at 9.** Empty is shown three ways: amber edge, stop glyph and dashed
  meter. It does not depend on colour alone and stays readable at 1×. A full meter
  makes `10.0s` read as the time available rather than the time already rewound.

Simultaneous overlays: in `26` and `27`, at both widths, the budget plate sits in the
above-lives lane. The recording plate and the two-row playback plate sit in the
bottom-right lane. They do not overlap, and the budget plate does not cover the native
HUD. The playback plate's second row (`NO SIDECAR Tab: Details`) is an earlier state
and was not rescored.

## Notes (non-blocking)

- If a later round improves the transport icon set, separate the empty glyph's stop bar
  from the arrow tip on the middle row. This affects only the Icons score.
- Capture a 50 fps seconds state and a long budget value such as `120.0s` the next time
  `TransportHudDesignCaptureTool` gains states. This would confirm that the fixed-width
  value still sizes the plate to content.
- Capture the budget states on S1 and S3K if lane geometry ever changes.

## Lead follow-up: settings and cross-game clearance

The lead additionally generated `settings-320/` and `settings-400/` under the same task directory through `MasterTitleCaptureTool` and real Settings input, selecting only `rewind-settings-` scenes with `-Dopenggf.capture.scenePrefix=rewind-settings-`. Scope, budget, Off/dependent help, ordered choices and stage toggles are captured. The new picker uses the same light row face as the Settings form; the title remains bold. English labels and help fit without truncation at both widths. The four dependents indent 8 pixels and dim when scope is Off; the help explains that condition. The chooser exposes positive values and Unlimited, with no zero option. The lead rates this bounded Settings addition Type 9, Hierarchy 9, Space 8.5, Colour 9, Icons 8.5, Consistency 9, Clarity 9, overall 9, matching the B1 family target. This is lead assessment, separate from Opus's HUD review; translated layouts rely on normal English fallback and existing measured paging, not a fresh all-language capture campaign.

The lead also ran the HUD capture owner on original SHA-1-verified S1 World REV01 and S3&K locked-on ROMs at 320 and 400 pixels (`caps-s1-320`, `caps-s1-400`, `caps-s3k-320`, `caps-s3k-400`). The empty state and simultaneous recording/playback states retain the native HUD clearance and separate lanes. These extend the S2 evidence; they are still synthetic presentation states, not gameplay/trace parity certification.

Expanded settings/stage/audio/localization checks subsequently passed 126 tests with zero failures/errors/skips. The final targeted pacing check found a mock gameplay context with no level manager under the newly enabled rewind default; its pacing-only fixture now explicitly disables rewind until a rewind case enables it. Regression verification and combined smoke are recorded in the implementation plan.

## Bounded integrated live-loop witness

A temporary ROM-backed S2 EHZ1 probe in `<live-probe-output>/` subsequently drove real `GameLoop.step()` calls and owned `InputHandler` key events, with no recorded-input ownership, surfaceless EGL and the asserted NullAudioBackend. It recorded 700 neutral forward ticks, then rewound 120 ticks and observed 480/600 remaining. Another 480 successful ticks exhausted the allowance and released rewind automatically. After release and a fresh denied press, the empty HUD remained visible for 60 NTSC ticks and then expired while the key remained held. `mid.png` and `denied.png` use the actual manager HUD model. The probe passed; it is a short original-loop behavior witness, not an exhaustive route, audio-synthesis or trace-parity result.

The same live-loop witness also passed with the PAL region cadence: a 10-second pool contained 500 frames, 120 successful backward steps left 380, and denied-press feedback expired after 50 held ticks. Both NTSC and PAL runs additionally started a fresh native title run in the same WorldSession with life-loss refill Off and observed a full new pool. These checks extend the earlier NTSC witness without changing the scope of the visual rubric.
