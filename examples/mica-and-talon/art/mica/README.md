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
| `endings/` | `frames.txt` (per host for MICA endings); 60 PNGs | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |
| `hud-and-screens/` | 28 PNGs | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |
| `portrait/` | `*-frames.txt`; 1 PNGs | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |
| `powered/` | `palettes.txt`; 111 PNGs | Host-specific native-pose powered sheets | Run `assemble.py` for each `powered/s2` and `powered/s3k` handoff into a target directory. |
| `reels/` | 2 PNGs | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |
| `sheet/` | 274 PNGs | Approved native-pose drawings and deterministic slopes | `assemble.py art/mica/sheet <target-output>` packages the handoff; it does not redraw approved frames. |
| `snowboard/` | `*-frames.txt`; 13 PNGs | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |
| `special-stage/` | `frames.txt` (per host for MICA endings); 72 PNGs | Canonical indexed parts, native body motion and joint placements | `python3 tools/modding/playable_art/locked_stage.py examples/mica-and-talon/production/mica/consistent-stage --check` (omit `--check` to rebuild). |
| `stage-poses/` | `frames.txt` (per host for MICA endings); 35 PNGs | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |
| `titles/` | `*-frames.txt`; 51 PNGs; masks and clear mask | Selected native-pose generation, locked palette and native hotspots | Use the matching production recipe/manifest and selected indexed rows; raw input paths in provenance require the original external scratch inputs. |

Production records are in [`production/mica/`](../../production/mica/).
The final special-stage manifests point from that tree to these approved PNGs.
The remaining scripts record individual production steps and may require external
raw generations or ROM references; the ordinary build never runs them. Changes
to approved drawings need the native-style and render checks described above.

## Powered-form feedback, 2026-11-11

Fresh native-pose generations and selected indexed rows live in
[`feedback-generation.json`](../../production/mica/powered/feedback-generation.json).
The S2/S3K run drawings use one hood/torso scale and retain native phase timing.
The five already-approved transformation poses now play through the shared form
controller, including S1’s creator form. Gold shadows stay saturated yellow at
maximum shimmer so scale plates remain legible. Fixed silhouette ink is converted
to cycling gold; interior eye/glove/shoe separators may remain dark.

`production/talon/feedback_art.py` reproduces every selected/converted image from
original indexed rows; raw generations and decoded native references stay outside
Git. `production/talon/PoweredFeedbackCapture.java` is the maintained native-clock
presentation probe: build the example, compile it against `target/classes` and
`target/mica-classpath.txt`, then pass game, character, `true|false|hyper`, jar
(or `-` for stock), and an external output directory. Run with DISPLAY and
WAYLAND_DISPLAY unset, surfaceless EGL and an owned save root. The probe declares
emeralds/rings, an airborne transformation start, camera registration and held
pose speed 0x900; it is art/palette evidence, not route certification.
