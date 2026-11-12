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

`endings/final-pilot/` contains the front-view cockpit overlay and binary native
clear mask (184×120, selector 0, hotspot 0,0). It follows the independently
scrolling ending-plane backdrop. The native-pose reference, two candidate rows,
selected reduction, palette and size check are recorded under
`production/mica/endings/final-pilot/provenance.json`. Vehicle pixels come from
the ROM, not the authored overlay.
