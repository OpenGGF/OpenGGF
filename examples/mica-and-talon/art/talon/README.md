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

| `powered/` | `palettes.txt` | Solar fade and cycle colour words | `python3 tools/modding/playable_art/solar_java.py` (or `--check`). |

Production records are in [`production/talon/`](../../production/talon/).
The final special-stage manifests point from that tree to these approved PNGs.
The remaining scripts record individual production steps and may require external
raw generations or ROM references; the ordinary build never runs them. Changes
to approved drawings need the native-style and render checks described above.

S2 title BODY0–4 use the camera-facing face selections in
[`s2-gaze-generation.json`](../../production/talon/titles/s2-gaze-generation.json).
The approved goggles, crest, connected wings and outfit remain outside the face
windows; native hotspots, indexed palette and MICA overlap pixels are retained.

### Lives-name correction (2026-11-11)

TALON uses his full five-letter name on S1/S2/S3K lives displays. S3K packs
its native glyphs into 32 pixels by removing only empty edge columns when the
ordinary word is too wide. No glyph ink, shadow, portrait pixels or palettes
change; MICA's name and the S1/S2 words keep their original composition. The
S2 special-stage HUD also retains full TALON. No new art is generated or stored.
