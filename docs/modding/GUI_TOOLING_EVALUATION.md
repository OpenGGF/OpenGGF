# GUI tooling evaluation

Phase 4 evaluates GUI direction; it does not commit to building one. No adopter
feedback was checked into the repository at evaluation time, so the evidence is the
five maintained sample sources and their CI build paths.

## Observed creator friction

The evidence corpus is the [five-source gallery](samples/index.md) and its
[real package/validation test](../../src/test/java/com/openggf/tools/modsdk/TestSampleModsPackage.java).
That test materializes each committed source, invokes the real converter/package
commands, requires zero validator findings, then scans all five jars as one repository.

- The [music pack](samples/phase4-gallery-music-pack/README.md) needs strict audio
  YAML, generated WAV data, loop metadata, packaging, and validation. Its operations
  are non-spatial and already script cleanly.
- The [data-only reskin](../../src/test/resources/mods/sample-reskin-src/reskin-sheet.yaml)
  supplies a PNG plus sheet YAML with palette, frame-piece, origin, and mapping fields;
  gallery CI runs `convert art`, `package`, and `validate`. The checked-in base64 is
  fixture storage, not a creator-facing image-decoding requirement. Previewing mapping
  offsets and palette failures is the visual friction.
- The [badnik+zone project](../../src/test/resources/mods/sample-mod-src/project/README.md)
  combines Java, object-art conversion, a baked level export, and validated packaging.
  Gallery CI compiles Java directly and invokes `convert level --from-export`; the
  maintained sample does not exercise TMX. Its separate creator build scripts also
  provide a Maven lifecycle, while code build/debug remains ordinary IDE/compiler work.
- The [character sample](../../src/test/resources/mods/sample-character-src/README.md)
  adds Java plus `convert art --playable`. Its sheet describes palette, frame pieces,
  and origin offsets; conversion emits playable-v2/DPLC data and packaging validates
  the API/art budget. Visual frame/origin inspection could reduce iteration, but the
  trusted-code path still requires a compiler and validator.
- The [standalone sample](../../src/test/resources/mods/sample-standalone-src/README.md)
  has the broadest real orchestration: Java compilation, playable-art and baked-level
  conversion, WAV music/SFX staging, namespaced objects, save topology, packaging,
  and validation. A GUI cannot remove those ownership and validation boundaries.

The common denominator is not a missing project-shell GUI; it is the need for exact,
scriptable converters with clearer previews and diagnostics.

## Options

| Option | Strengths | Costs and risks | Rough effort |
|---|---|---|---|
| Extend in-engine panels | Reuses the live renderer, collision, editor history, playtest loop, mod manager, and runtime-owned registries. Best place to preview levels, collision profiles, sprite origins, palette lines, and pattern-window cost. | Engine UI/input code is specialized; project creation, Java builds, YAML editing, and jar packaging fit poorly. Loading hostile/incomplete projects into a gameplay process increases lifecycle and fault-boundary work. | Medium for one focused inspector (roughly 4–8 engineer-weeks); large for a general studio (multiple releases). |
| Build an external studio | Can provide conventional project navigation, forms, source control integration, process isolation, and cross-game asset previews without entering gameplay. | Highest cost: duplicate renderer/asset semantics or embed the engine, ship another cross-platform application, keep format/API versions synchronized, and design extension/security/update models. The five samples do not yet justify this maintenance surface. | Large, plausibly 4–6 engineer-months for a trustworthy first release, then ongoing parallel maintenance. |
| Stay CLI-only | Existing commands are deterministic, headless, CI-friendly, composable with IDEs/Tiled, and already enforce the real runtime contracts. Lowest drift risk because the same code packages and validates samples. | YAML/binary error discovery can be slow; visual metadata and collision/profile authoring are awkward; multi-asset projects repeat output staging and converter/package wiring across build scripts. | Small, roughly 2–4 engineer-weeks of diagnostic, template, and documentation improvements spread over normal releases. |

Effort ranges are unvalidated order-of-magnitude planning estimates derived only from
the surfaces named in the table. They are not schedules, staffing commitments, or
estimates from completed comparable work; any approved design must estimate again.

## Recommendation

Keep `ggfmod` as the authoritative build, conversion, validation, and packaging
surface. Do not build an external studio now. If adopter feedback identifies a
specific visual bottleneck, add a narrow in-engine inspector/editor panel that emits
the same source formats and invokes the same converter contracts; likely first
candidates are collision-profile visualization or playable-sheet frame/origin/bank
preview. These are examples, not queued work. Any panel must remain optional: every
project stays reproducible headlessly through the CLI and CI sample gallery.

This is a **CLI-first, targeted-panel** recommendation, not a GUI build commitment.
Before approving any panel, record a reproducible creator task against one maintained
sample or an equally bounded fixture, capture a baseline duration and failure/redo
count for the CLI/Tiled/IDE workflow, and write a separate design with ownership,
headless parity, malformed-project isolation, and maintenance cost. The proposal must
name the measured improvement it accepts; this evaluation authorizes no implementation.

## Revisit trigger

Re-evaluate an external studio only after either:

1. at least two independent adopters provide linked, reproducible reports of the same
   cross-domain workflow, including the affected source/build steps and a measured
   baseline, and a written options check shows Tiled, an IDE, improved diagnostics,
   or one focused panel cannot address it; or
2. at least three separately approved panels duplicate the same project discovery,
   navigation, or build/validation orchestration, and a written cost comparison shows
   a shared external shell has lower implementation and maintenance cost than one
   engine-owned shared panel service.

Until then, the sample gallery is the regression and friction probe: proposals should
show which sample step becomes materially simpler without weakening deterministic
CLI reproduction. Meeting a trigger starts a new evaluation/design review; it does not
authorize or schedule an external studio.
