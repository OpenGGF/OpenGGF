# MICA art surfaces

The playable sheet has 274 frames, 53 animation keys. Runtime art is indexed with transparency
at index zero. [Shared art rules and build commands](../../README.md) apply.
Frame bindings are in adjacent `frames.txt` or host-specific `*-frames.txt` files;
see the [table format](../../README.md#editing-frame-tables).
Paths below are relative to this directory; run repository tool commands from
the repository root. `assemble.py` is `tools/modding/playable_art/assemble.py`.

| Surface | Files | Method | How to regenerate |
| --- | --- | --- | --- |
| `competition/` | `frames.txt` (per host for MICA endings); 44 PNGs | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |
| `competition-menu/` | 2 PNGs | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |
| `continue/` | `*-frames.txt`; 53 PNGs | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |
| `endings/` | `frames.txt` (per host for MICA endings); 64 PNGs | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |
| `hud-and-screens/` | 28 PNGs | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |
| `portrait/` | `*-frames.txt`; 1 PNGs | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |
| `powered/` | `palettes.txt`; 111 PNGs | Host-specific native-pose powered sheets | Run `assemble.py` for each `powered/s2` and `powered/s3k` handoff into a target directory. |
| `reels/` | 2 PNGs | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |
| `sheet/` | 274 PNGs | Approved native-pose drawings and deterministic slopes | `assemble.py art/mica/sheet <target-output>` packages the handoff; it does not redraw approved frames. `production/mica/carried_reshade.py --check` covers the three carried frames. |
| `snowboard/` | `*-frames.txt`; 13 PNGs | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |
| `special-stage/` | `frames.txt` (per host for MICA endings); 72 PNGs | Canonical indexed parts, native body motion and joint placements | `python3 tools/modding/playable_art/locked_stage.py examples/mica-and-talon/production/mica/consistent-stage --check` (omit `--check` to rebuild). |
| `stage-poses/` | `frames.txt` (per host for MICA endings); 35 PNGs | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |
| `titles/` | `*-frames.txt`; 51 PNGs; masks and clear mask | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |

Production records are in [`production/mica/`](../../production/mica/).
The final special-stage manifests point from that tree to these approved PNGs.
The remaining scripts record individual production steps and may require external
raw generations or ROM references; the ordinary build never runs them. Changes
to approved drawings need the native-style and render checks described above.

## Slope sets on the mode reduction, 2026-11-12

Every frame of hers that is turned from a flat drawing rather than drawn now uses the reduction
TALON's walk slopes introduced (`derive_slopes.derive(method='mode')`): the flat's outline is lifted
before the turn, each pixel is the commonest index of its 8×8 block of the rotated Scale2x master,
speckle folds into the surrounding shade of its own material (scales, sand, white; coral, pink,
brown and ink untouched), and the silhouette is inked last. That covers the 24 up45/inverted WALK
and RUN slopes, the 12 homing-dive frames turned ±45° and 135° (FORWARD, UP, DOWN), and the 40
up45/inverted Open Cone slopes of the S2 and S3K banks, which Gilded and Hyper share and which keep
their silhouette on contour slot 2. Steep frames and HOMING_DIVE_FORWARD_UP are exact quarter turns
and did not change; no flat drawing changed. Each frame keeps its recorded support distance; sizes or
hotspots move by a pixel on some frames. On the noise audit the normal slopes go from 0.23–0.24 to
0.13–0.14, the homing dives from 0.23 to 0.11 and Open Cone from 0.15–0.18 to 0.12–0.14.
`production/slope_sets.py --check` rebuilds all of them and keeps `recipe.json`,
`native-pose-core.json`, the homing-directions mirror and `powered-concepts-generation.json` in step.

## Carried frames tonal re-shade, 2026-11-12

`hang-00`..`02` (HANG, HANG2 and the `TAILS_CARRIED` script) kept the selective material
contours of the 2026-11-10 joint carry redraw: ink on a third of the silhouette, jade-deep scale
lines and no jade-shadow, at mean luminance 107–109 against a sheet median of 88. Once TALON's
carry frames were re-shaded (see [his README](../talon/README.md)) they were the only un-inked
sprites in the carry shot. They now have the pipeline's 1 px ink silhouette (`recipe.json` and
`native-pose-core.json` no longer mark them `outline_mode: material`), ink scale separators and
jade-shadow scale shade, and average 98–100; all 273 drawn frames of the sheet have a full ink
silhouette. No pixel was added, removed or moved, so sizes, hotspots and the 36 carry pairings are
unchanged. `production/mica/carried_reshade.py --check` rebuilds them from the original rows in
[`carried-reshade.json`](../../production/mica/carried-reshade.json). An audit of the whole
sheet found no other un-inked frame; the remaining statistical outliers are intended drawings and
are listed in that record.

**Line-art pass (2026-11-12).** On the owner's line-art feedback her sheet was scored against her
own references (WAIT, WALK and RUN; `tools/modding/playable_art/style_audit.py`, profile
`production/style-audit.json`). Unlike TALON she does not have a different face per source sheet:
her poses were already brought to one skull and face (`consistency-landmarks.json`). What set her
apart from his cleaned sheet was speckle, a noise median of 0.33 against his 0.19.
`production/mica/sheet_despeckle.py` folds lone pixels and two-pixel flecks into their own material
on 203 flat drawings with `despeckle.py`'s `lines=True`, which keeps one-pixel diagonal lines: her
scale edges. The plain sprite cleanup reads those as isolated pixels and flattens her scales into
one jade mass (0.17, and no pangolin left), so it was rejected; with the edges kept the sheet median
is 0.23. Alpha, ink, sizes and hotspots are identical, and `production/slope_sets.py` re-derives her
WALK/RUN slopes and homing-dive directions from the cleaned flats. Left alone, with reasons in
[`sheet-despeckle.json`](../../production/mica/sheet-despeckle.json): her VICTORY frames (another
lane's), the three carried frames (`carried_reshade.py`) and the ball drawings. Her idle, `wait-00`
to `wait-08`, is kept exactly as drawn, hand-on-chin beat included, and cleaned like any other pose.
Against those cleaned references the audit still flags object, variant and dive poses whose scales
use a different mix of the jade ramp (two-tone shading, or a curled or back view that is nearly all
scales); a cleanup cannot re-band them and she has no source landmarks to redraw from, so they are
listed as deferred in the profile. Gilded MICA was scored and not changed: one Open Cone
design throughout, with the frames whose gloves, shoes and plate tips sit on the silhouette without
the contour slot listed as deferred.

**Sonic 1 title (2026-11-12).** `production/mica/titles/s1_despeckle.py` applies the same
line-preserving cleanup to the eight BODY frames of her Sonic 1 title portrait (`titles/s1-0..7.png`):
noise 0.15-0.20 before, 0.09-0.14 after, scale edges kept. `s1-frames.txt`, its bank, selectors and
hotspots are untouched, and no pixel changes its alpha or ink.


## Powered-form feedback, 2026-11-11

Fresh native-pose generations and selected indexed rows live in
[`feedback-generation.json`](../../production/mica/powered/feedback-generation.json).
The S2/S3K run drawings use one hood/torso scale and retain native phase timing.
The five already-approved transformation poses now play through the shared form
controller, including S1’s creator form. Fixed silhouette ink is converted
to the cycling contour slot; interior eye/glove/shoe separators may remain dark.

**Open Cone pilot (2026-11-11).** Wait, run (with derived slopes), roll and the
transformation are fresh owner-concept generations, recorded with selected rows in
[`powered-concepts-generation.json`](../../production/mica/powered/powered-concepts-generation.json).
Slots 2–6 are lifted plates from a deep amber underside to a pale-gold edge; face,
belly, gloves and coral boots keep the sheet colours. Hyper reuses the bank with the
Prism Gilt cycle. Walk (with derived slopes), push, duck and balance followed in the
rollout from her own earlier native-pose drawings; balance stays upright, as native
`SupSonAni_Balance` (s2.asm:38815) is. Every powered frame is now an Open Cone drawing.

`production/talon/feedback_art.py` reproduces every selected/converted image from
original indexed rows; raw generations and decoded native references stay outside
Git. `production/talon/PoweredFeedbackCapture.java` is the maintained native-clock
presentation probe: build the example, compile it against `target/classes` and
`target/mica-classpath.txt`, then pass game, character, `true|false|hyper`, jar
(or `-` for stock), and an external output directory. Run with DISPLAY and
WAYLAND_DISPLAY unset, surfaceless EGL and an owned save root. The probe declares
emeralds/rings, an airborne transformation start, camera registration and held
pose speed 0x900; it is art/palette evidence, not route certification.

`endings/pilot/` supplies four native-pose cockpit head crops (Sonic pilot
selectors 45–48, hotspot 50,8), plus a selector-12 S3K alias (12,8). TALON's solo
companion presentation selects them; MICA's solo companion bank selects existing
TALON pilot art. Fresh GPT Image candidates and native references stay in the
feedback-round scratch directory; reduction and area checks are recorded in
`production/mica/endings/pilot/provenance.json` (scale ratios 0.988–1.017).

The S3K cockpit rows cover both seats: selector 12 (the Tails seat) and selector 11 (the
Sonic seat, where MICA sits when TALON is the lead). `MicaEndings` also binds TALON's banks
as her solo companion: his Sonic 2 search photos (`S2_SOLO_PHOTOS`), his cockpits, and,
once drawn, his mid-size cockpit head and his figure on the final S3 graphics card. To keep
the host's own photos for a solo MICA, drop the `S2_SOLO_PHOTOS` line in `MicaEndings`.

`endings/s3k/arrival_rider-0e.png` (the rider on the mid-size Tornado, `S3K_PASSENGER`
selector 14, 16×24) and `endings/pilot/arrival_pilot-0d.png` (that plane's cockpit head,
`S3K_MINI_TORNADO_PILOT` selector 13, 16×8) were placed by hand from her own drawings; the
record is in `production/talon/endings/celebration-generation.json`.
`endings/s3k/tiny_passenger-16.png` and `-17.png` are no longer bound: they were drawn for
native selectors 22 and 23, which are that plane's exhaust flame.

`endings/final-pilot/` contains the front-view cockpit overlay and binary native
clear mask (184×120, selector 0, hotspot 0,0). It follows the independently
scrolling ending-plane backdrop. The native-pose reference, two candidate rows,
selected reduction, palette and size check are recorded under
`production/mica/endings/final-pilot/provenance.json`. Vehicle pixels come from
the ROM, not the authored overlay.
