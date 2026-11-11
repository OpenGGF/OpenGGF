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
| `sheet/` | 282 PNGs | Approved native-pose drawings and deterministic slopes | `assemble.py art/talon/sheet <target-output>` packages the handoff; it does not redraw approved frames. |
| `special-stage/` | `frames.txt` (per host for MICA endings); 72 PNGs | Canonical indexed parts, native body motion and joint placements | `python3 tools/modding/playable_art/locked_stage.py examples/mica-and-talon/production/talon/consistent-stage --check` (omit `--check` to rebuild). |
| `stage-poses/` | `frames.txt` (per host for MICA endings); 36 PNGs | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |
| `titles/` | `*-frames.txt`; 50 PNGs; masks and clear mask | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |

| `powered/` | `s2/`, `s3k/`; 64 PNGs; `palettes.txt`; effects | Authored Solar idle/run/flight/transform, original rolling reuse, cycle-owned contours | `assemble.py` each host handoff; `production/talon/feedback_art.py --check`; `solar_java.py --check`. |

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

**Corona Crest pilot (2026-11-11).** Wait (two-frame crest flicker), run (with
derived slopes), roll and the transformation (wings wrapped, goggles drop, wings
thrown open as the crest ignites) are fresh owner-concept generations, recorded in
[`powered-concepts-generation.json`](../../production/talon/powered-concepts-generation.json).
Slot 2 stays dark bronze for the chest bars, moustache and feather breaks; 3–5 are
white-gold plumage, 6 the cycling orange tip, and fixed amber 14/15 and red 10
finish the flames. Free flight is the earlier drawing in the new palette.

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
