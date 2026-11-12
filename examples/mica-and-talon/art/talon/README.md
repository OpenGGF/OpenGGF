# TALON art surfaces

The playable sheet has 282 frames, 47 animation keys. Runtime art is indexed with transparency
at index zero. [Shared art rules and build commands](../../README.md) apply.
Frame bindings are in adjacent `frames.txt` or host-specific `*-frames.txt` files;
see the [table format](../../README.md#editing-frame-tables).
Paths below are relative to this directory; run repository tool commands from
the repository root. `assemble.py` is `tools/modding/playable_art/assemble.py`.

| Surface | Files | Method | How to regenerate |
| --- | --- | --- | --- |
| `competition/` | `frames.txt` (per host for MICA endings); 46 PNGs | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |
| `competition-menu/` | 2 PNGs | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |
| `continue/` | `*-frames.txt`; 47 PNGs | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |
| `endings/` | `frames.txt` (per host for MICA endings); 18 PNGs | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |
| `hud-and-screens/` | 29 PNGs | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |
| `portrait/` | `*-frames.txt`; 1 PNGs | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |
| `reels/` | 2 PNGs | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |
| `sheet/` | 282 PNGs | Approved native-pose drawings and deterministic slopes | `assemble.py art/talon/sheet <target-output>` packages the handoff; it does not redraw approved frames. `production/talon/tonal_reshade.py --check` covers the 24 flight and carry frames `production/talon/walk_cleanup.py --check` the 48 WALK/RUN frames and `production/talon/pose_cleanup.py --check` the other 203 flat poses. |
| `special-stage/` | `frames.txt` (per host for MICA endings); 72 PNGs | Canonical indexed parts, native body motion and joint placements | `python3 tools/modding/playable_art/locked_stage.py examples/mica-and-talon/production/talon/consistent-stage --check` (omit `--check` to rebuild). |
| `stage-poses/` | `frames.txt` (per host for MICA endings); 36 PNGs | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |
| `titles/` | `*-frames.txt`; 50 PNGs; masks and clear mask | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |

| `powered/` | `s2/`, `s3k/`; 162 PNGs; `palettes.txt`; effects | Corona Crest Solar for every common pose at the 37 px rung, one-limb canon, cycle-owned contours | `assemble.py` each host handoff; `production/talon/feedback_art.py --check`; `solar_java.py --check`. |

Production records are in [`production/talon/`](../../production/talon/).
The final special-stage manifests point from that tree to these approved PNGs.
The remaining scripts record individual production steps and may require external
raw generations or ROM references; the ordinary build never runs them. Changes
to approved drawings need the native-style and render checks described above.

S2 title BODY0–4 use the camera-facing face selections in
[`s2-gaze-generation.json`](../../production/talon/titles/s2-gaze-generation.json).
The approved goggles, crest, connected wings and outfit remain outside the face
windows; native hotspots, indexed palette and MICA overlap pixels are retained.

## Powered-form feedback, 2026-11-11

Fresh native-pose generations and selected indexed rows live in
[`feedback-generation.json`](../../production/talon/feedback-generation.json).
Six free-flight phases now match the approved carry profile and material contours;
normal/ascending variants share the native identical torso pose. Goggle/skull
registration removes source-atlas row drift without changing scale or pixels.
Solar has dedicated S2/S3K banks, shared by the S1 creator form, and an animated
five-pose wind-up. Powered silhouette ink belongs to the contour slot. Normal carry
contact geometry is unchanged. Native S2 has no carry state; its picture comparison
is pose-only.

**Corona Crest Solar (2026-11-11).** Fresh owner-concept generations cover wait (crest
flicker), walk and run (with derived slopes), roll, spindash, spring, duck, look up, hurt,
skid, balance, push, free flight and the transformation (wings wrapped, goggles drop,
white flash, wings thrown open as the crest ignites). They are recorded in
[`powered-concepts-generation.json`](../../production/talon/powered-concepts-generation.json).
Every frame keeps the one-limb canon: a gloved hand at the end of each feathered
wing-arm, with feathers folding along a forearm that reaches out. Slot 2 stays dark bronze
for the chest bars, moustache and feather breaks; 3–5 are white-gold plumage, 6 the cycling
orange tip, and fixed amber 14/15 and red 10 finish the flames. Each frame is locked to his
skull and registered to his normal frame for the same pose, so the crest rises above his
normal body (42–44 px standing). Carry, swim, hang and other rare poses stay normal-bank
aliases, as native Super Tails reuses Tails' art.

**Owner height rung, 37 px (2026-11-11).** Normal TALON stands 37 px (Tails 31, MICA 39).
The in-level sheet, its slopes and the full-body ending frames were rescaled with feet and
hotspots fixed (hanging and carried poses keep their grip at the top; push keeps the hand on
the wall); collision stays the Tails archetype. 37 frames were re-reduced from their raw
generations; the rest reduce a Scale2x master and restore the original 1 px material
contours. [`rescale-37.json`](../../production/talon/rescale-37.json) lists every frame's route
and reason. Titles, the settled S2 title poses, lives icons, special-stage and slot poses,
ending close-ups, far shots and cockpit heads keep their native scene scale. The carry grip
still touches in all 36 pairings.

**Flight and carry tonal re-shade (2026-11-12).** The owner found the carry frames washed out
beside the 37 px rung set. Measured over opaque pixels the sheet averaged luminance 88 with ink
on every silhouette pixel; the twelve carry frames averaged 126 with no ink at all, and the twelve
free-flight frames drawn to match them 112 with a slate-deep silhouette. Both sets now follow the
rung set's index usage: the pipeline's 1 px ink silhouette (`recipe.json` no longer opts them out
with `outline_mode: material`), ink separator lines, plumage one step darker (slate-shadow base,
slate-deep shade), grey-white feather tips instead of chest cream, and a brown satchel instead of
stray shoe red. They now average 91–100 and every one of the 282 sheet frames has a full ink
silhouette. No pixel was added, removed or moved, so sizes, hotspots and the 36 carry pairings are
unchanged. `production/talon/tonal_reshade.py --check` rebuilds the 24 frames from the original rows
and per-drawing wing-tip boxes in
[`tonal-reshade.json`](../../production/talon/tonal-reshade.json), and keeps `recipe.json`,
`feedback-generation.json` and `rescale-37.json` in step. MICA's three carried frames received the
same treatment (`production/mica/carried_reshade.py`). Titles, special-stage, ending and other
native-scene surfaces keep their host's selective contours and were not changed. Under Solar the
aliased carry frames pick up the bank's flame-tip slot on their feather tips, as his other aliased
wing poses already do.

**Walk and run cleanup (2026-11-12).** The owner found his walk blurry. Measured with
`tools/modding/playable_art/noise_audit.py`, the flat WALK frames had 35% of their body pixels
isolated (RUN 31%), the same as WAIT (38%) and the sheet median (30%), against 11% for the flight
set; the 31 px sheet measured the same, so the rung rescale did not add speckle, though its route-2
frames (`walk-00`..`02`, `run-02`) have softer edges. A loop makes that speckle boil, so
`production/talon/walk_cleanup.py` cleans the twelve flat WALK/RUN drawings: isolated pixels,
one-pixel anti-alias steps and two-pixel flecks are folded into the surrounding shade of their own
material, for plumage and white/cream only, and plumage settles on three bands. Ink, goggles, beak,
strap, satchel and shoes are untouched, white is never darkened, and the silhouette cannot change.
Eleven recorded hand pixels restore the white cheek on the three route-2 walk frames. The 36 slope
frames are re-derived from the cleaned flats with a mode-reduced rotation
(`derive_slopes.derive(method='mode')`) at each frame's existing support distance; ten of them
change size or hotspot by one pixel. Flat WALK now scores 0.22 on the audit's noise measure (was
0.35), RUN 0.20 (0.31) and the 45° sets 0.13–0.14 (0.22–0.24); the flight set is 0.15 and WAIT
0.37. `walk_cleanup.py --check` rebuilds all 48 frames from
[`walk-cleanup.json`](../../production/talon/walk-cleanup.json). The rest of the sheet was measured
and left as it is: WAIT and most other poses score what WALK did before.

**Pose cleanup (2026-11-12).** The owner then asked for the rest of the sheet to match the cleaned
walk and run. `production/talon/pose_cleanup.py` applies the same rules (`walk_cleanup.clean`) to
the other 203 flat drawings: WAIT, BLINK, LOOK_UP, DUCK, SKID, PUSH, the BALANCE sets, SPRING, HURT,
HURT_FALL, DEATH, DROWN, GET_UP, VICTORY, HANG, HANG2, BUBBLE, FLOAT2, SPINDASH 00–02, SUPER_TRANSFORM
00/01/03/04, TAILS_CARRIED, the tired and swim sets and the 113 object poses. Speckle folds into
the surrounding shade inside plumage and inside white/cream only; white is never darkened; alpha,
ink, sizes and hotspots are identical, and there are no hand pixels. These drawings averaged 0.34 on
the audit's noise measure (0.25–0.41 by animation) and now average 0.19 (0.13–0.26); the sheet
median falls from 0.32 to 0.19, with the cleaned WALK/RUN flats at 0.20–0.22 and the flight set at
0.15. Left as drawn, and listed with reasons in
[`pose-cleanup.json`](../../production/talon/pose-cleanup.json): the four ROLL frames and the
SPINDASH blur ball, whose one-pixel steps are curve shading and motion streaks and which already
score 0.10–0.18, and the white transformation flash. `pose_cleanup.py --check` rebuilds the 203
drawings from their original rows.
The pass also corrected the shared rule: a white or cream patch with no plumage beside it that
touches amber, brown or red is a goggle glint, beak shine or shoe white, and is now put back exactly
as drawn. The walk cleanup had rewritten seven such pixels on `walk-04`, `walk-05`, `walk-07`,
`run-01` and `run-03`; those five flats and their fifteen slope frames are rebuilt.

**Solar slopes on the mode reduction (2026-11-12).** The 48 up45/inverted Solar WALK and RUN
frames (S2 and S3K banks) are re-derived from their unchanged flats with the same reduction, by
`production/slope_sets.py`. Bronze slot 2 (contour, bars, moustache) is lifted from the silhouette
before the turn and the new outline goes back on it; white-gold plumage and cream/white are
despeckled within their own material, and the bars, flame tips, goggles and shoes pass through
untouched. They previously carried the fixed ink of the pre-remap flat inside the body. Each frame
keeps the support distance of the frame it replaces, now recorded as `support_kept` in
`powered-concepts-generation.json`. The audit's noise measure goes from 0.19 to 0.17–0.18.

`production/talon/feedback_art.py` reproduces every selected/converted image from
original indexed rows; raw generations and decoded native references stay outside
Git. `production/talon/PoweredFeedbackCapture.java` is the maintained native-clock
presentation probe: build the example, compile it against `target/classes` and
`target/mica-classpath.txt`, then pass game, character, `true|false|hyper`, jar
(or `-` for stock), and an external output directory. Run with DISPLAY and
WAYLAND_DISPLAY unset, surfaceless EGL and an owned save root. The probe declares
emeralds/rings, an airborne transformation start, camera registration and held
pose speed 0x900; it is art/palette evidence, not route certification.

`endings/final-pilot/` contains the front-view cockpit overlay and binary native
clear mask (184×120, selector 0, hotspot 0,0). It follows the independently
scrolling ending-plane backdrop. The native-pose reference, two candidate rows,
selected reduction, palette and size check are recorded under
`production/talon/endings/final-pilot/provenance.json`. Vehicle pixels come from
the ROM, not the authored overlay.

TALON uses his full five-letter name on S1/S2/S3K lives displays. S3K packs
its native glyphs into 32 pixels by removing only empty edge columns when the
ordinary word is too wide. No glyph ink, shadow, portrait pixels or palettes
change; MICA's name and the S1/S2 words keep their original composition. The
S2 special-stage HUD also retains full TALON. No new art is generated or stored. All three hosts preserve the native lower
counter row, including ×; S3K now replaces only the upper name tiles.
