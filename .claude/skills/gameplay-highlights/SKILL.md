---
name: gameplay-highlights
description: Use for OpenGGF work compilations, promotional reels, Stageplay or mod showcases, or refreshing highlights after fixes; not individual capture or parity testing.
---

# Gameplay work highlights

Turn verified gameplay captures or reviewed Stageplay renders into a concise
account of the delivered work. Identify staged material as authored presentation.
Keep the complete source archive separate from the edited reel. Do not concatenate
every probe, screenshot and test variant into a “highlights” movie. This skill works
across games; it does not require an S3K bring-up or authorize engine changes.

For a promo, trailer or showcase meant to sell the work, also follow
[promo-direction](../promo-direction/SKILL.md) for the brief, beat-grid storyboard,
typography, layouts, soundtrack and the flatness review. This skill still governs
provenance, honest labelling, verification and the OpenGGF-Media archive.

## Collect evidence as work proceeds

Use one explicit task directory outside the engine repository for durable media. Reuse
an existing task root when resuming. Give each scenario/version its own directory;
never overwrite an earlier source capture when recording a correction.

Choose the capture owner from what the excerpt needs to show:

- For staged stories, choreographed promos, dialogue and directed performances, use
  [stageplay-authoring](../stageplay-authoring/SKILL.md). Its screenplay/compiler and
  offline renderer provide actor, camera, caption, audio and scene-transition tracks.
  `LIVE` shots can demonstrate native interactions when supported by world telemetry.
- For ordinary gameplay, continuous routes and gameplay feature captures, use
  [gameplay-capture](../gameplay-capture/SKILL.md), with
  [bk2-input-authoring](../bk2-input-authoring/SKILL.md) when scripted inputs help.
- For an existing replay, use [trace-capture](../trace-capture/SKILL.md).
  Native reference footage must remain identifiable as native. Do not record new
  traces just to make a reel when ordinary gameplay supplies the requested footage.

A reel may combine these sources. Label their source kinds in its provenance and
make the distinction clear wherever an edit could imply that authored action was
observed gameplay. Authored samples and `TERRAIN_ONLY` motion do not demonstrate
placed-object AI or zone events. `LIVE` telemetry can substantiate the native
interactions actually observed in its authored setup. Staged spawns and cuts do
not prove continuous cold route completion; native parity needs separate reference
evidence, including identifiable emulator footage when used.

Keep enough provenance beside each retained scenario or in the task index:

- Source commit (or base plus uncommitted changes), game/zone/act, actual width
  and resolved character/donor/team.
- Capture driver/command and source kind. For gameplay, retain the input/controller
  recipe and entry method: cold route, checkpoint reload, positioned setup or seeded
  diagnostic. For Stageplay, retain the screenplay, compiled scene, shot IDs and
  per-shot `TERRAIN_ONLY`/`LIVE` world policy, including spawn setup and cuts.
- Relevant setup writes, frame stride/rate and whether audio was captured.
- Measured gameplay state CSV or labelled Stageplay JSONL: authored timeline,
  `TERRAIN_ONLY` simulation or `LIVE` native world telemetry, with named event
  frames. Keep their meanings distinct. Record the outcome, known defects and whether
  the footage is final, superseded, or a failed experiment.

For gameplay, read the state CSV before choosing frames. Verify actual identity
and milestone progression: configuration strings alone are not proof. Positioned
entry can skip priority switches, lighting, camera history or terrain events. A beautiful
clip from invalid setup is not a good final demonstration. Record real input
masks if a live controller leaves the textual input column blank.

For Stageplay, read the screenplay and appropriate JSONL before choosing excerpts.
`timeline` records authored samples and cues; physical actors remain `UNRESOLVED`.
`simulate` with `TERRAIN_ONLY` records actual native player motion against
detached static terrain, including arrival/blocked status, without placed-object
AI, ring collection or zone-event simulation. With `LIVE`, `sample.world` records
the shared native clocks, actors, placed objects, rings and events. Use those rows
to verify actual support/riding, pickups, hurt/recovery and ability transitions.
LIVE is a native world under authored shot setup and inputs; it does not by itself
prove continuous cold route completion or ROM parity. Normal gameplay CSV records
observed gameplay with its own entry/input semantics. Keep every-frame output
when cue timing matters; a sampled
export omits cues between sampled frames. Verify the visible performance in motion;
correct authored coordinates alone do not establish a good-looking shot.

## Select the story

For gameplay feature reels, order excerpts by game/zone and route progression when
that tells the work clearly, with bosses, results and exits at their natural positions.
Loops and alternate paths need route milestones; raw X alone is not a reliable ordering
key. For staged stories and promos, order by the intended story beats and apply the
performance, speaker and camera guidance in `stageplay-authoring`. Follow the user's
brief; there is no mandatory shot formula.

Choose useful moving excerpts for each feature or story beat. Show enough approach,
interaction and consequence to understand it. Prefer footage from the delivered
revision.
Exclude idle waits, long test setup, debug overlays, failed attempts, still-image
montages, duplicate width/roster tests and repeated fights unless comparison is
explicitly the point. Do not cut away the consequence that demonstrates success.

For a follow-up fix, replace the affected excerpts and recheck nearby cuts.
Preserve unaffected scenes and original captures. Re-evaluate source time ranges:
a corrected controller route or revised screenplay can shift every later milestone.
Do not assume old frame numbers remain valid or append another copy of the same feature.

Keep a small ordered CSV/JSON edit list with the source kind, sequence order, title,
source path and source start/end seconds. Include act/route milestones for gameplay
and shot IDs for Stageplay. Preserve useful existing edit-list fields when revising
a production.

After assembly, publish a chapter map with output start/end times and those
source intervals. Paths should be relative to the unified root when possible.
Retain the exact assembly command/script with the media so the edit is repeatable.
A named source interval is evidence of provenance, not proof of native parity.

## Assemble without distorting the footage

Use `ffprobe` to inspect source dimensions, duration, frame rate and audio before
encoding. Keep gameplay at its recorded speed. If PNGs sample every N simulation
frames, their input rate is simulation FPS / N; encoding sparse samples at full
simulation FPS speeds up the route. Outputting 60fps does not recover absent
frames. Recapture at sufficient cadence if the animation is the subject.

Normalize clips to a common canvas, pixel format, frame rate, time base and audio
policy before concatenation. Use nearest-neighbour integer scaling and padding
for pixel art; keep the original aspect ratio. Native and widescreen clips may
need different padding. Labels can identify act/feature and setup when material;
do not imply a positioned demonstration is a cold completion run.

Choose an audio policy for the reel. Preserve genuine gameplay audio consistently
or make gameplay evidence silent when its sources lack audio. Stageplay excerpts may
use authored ROM music/effects and creator-supplied voices, plus native SFX in
LIVE shots; distinguish authored cues from observed native events. Catalogue their
sources and preserve the intended timing. Do not invent soundtrack licensing or let
editorial audio imply a native sound/event that was never observed. Use text files
for labels and structured subprocess arguments/concat manifests for paths rather
than shell interpolation.

Prefer one final encode after normalized segments so chapter seeking does not
cross incompatible H.264 configurations. Add chapters and fast-start metadata.
Write a new output file; validate it before replacing an existing published reel.
Keep the old deliverable if assembly or verification fails.

## Archive production sources in OpenGGF-Media (required)

Every delivered OpenGGF highlights reel, promo or showcase must publish its
production sources and external asset catalog to the public
[OpenGGF-Media repository](https://github.com/OpenGGF/OpenGGF-Media).
This is part of reel delivery, including follow-up edits; a local task folder
alone does not satisfy it. Use a separate media-repository checkout and follow
its [archive policy](https://github.com/OpenGGF/OpenGGF-Media/blob/main/docs/archive-policy.md)
and [rebuilding guidance](https://github.com/OpenGGF/OpenGGF-Media/blob/main/docs/rebuilding.md).

- Reuse the production's stable `projects/<id>/` directory. Preserve earlier
  revisions and identify final, superseded, rejected and unfinished material.
- Store the blueprints/plans, capture drivers and commands, controller inputs,
  preference recipes, Stageplay screenplays and compiled scenes, original editing
  project files, assembly scripts, edit lists, chapters, source-commit provenance
  and retained verification/decisions.
  Document required dependencies and path relocation so the edit can be rebuilt.
- Update the project index and the repository's project/root/source catalogs,
  including source sizes and SHA-256 hashes. Catalogue footage, finished reels,
  audio, artwork, frame sequences and bulk state data that remain external in
  `catalog/assets.csv`. Distinguish authored Stageplay timeline JSONL,
  `TERRAIN_ONLY` actor simulation, `LIVE` native world telemetry and measured
  gameplay CSV in those entries. Include durable locations and root mappings; a
  catalog entry does not upload or make an external asset downloadable.
- Keep ROMs, extracted ROM art/audio, recordings, frame dumps, packaged builds,
  installed environments, temporary logs and credentials out of the commit.
  Preserve original notices and review production sources for public exposure.
- Run `python3 tools/catalog.py verify` from the media checkout, relevant source
  syntax checks and index/link checks. Commit and push the integrated media
  branch under the normal delivery rules; verify the remote commit and paths.

Include the public production-directory link and archive commit in the delivery.
If archiving or pushing fails, preserve the sources and report the exact unfinished
state; do not claim the archive or reel delivery is complete.

## Verify and deliver

- Inspect the manifest against actual source durations: no missing inputs,
  negative/reversed intervals, accidental overlaps or clips beyond EOF.
- Inspect representative moving segments and every cut, particularly changing
  aspect ratios, boss outcomes, act handoffs and replaced footage. For staged shots,
  check purposeful action and reactions, speaker identity, and pixel stability during
  camera motion. Sampling stills alone cannot catch frozen sections, fractional-zoom
  shimmer or incorrect playback cadence.
- Use `ffprobe` to confirm final dimensions, duration, frame rate and chapter
  order/count. Decode the entire file with
  `ffmpeg -v error -xerror -i <reel> -f null -` and require a successful exit.
  Codec success does not replace visual inspection.
- Reconcile output duration with the edit list and confirm the chapter map points
  to the actual final sources. Keep full cold runs for uninterrupted reachability;
  editorial cuts cannot prove a continuous completion.
- Complete the required OpenGGF-Media publication, then deliver the reel link,
  duration, public production-directory link, archive commit, external source-folder
  location and any material limits.
  Remove only known temporary encoding files after successful publication.
  Preserve source captures, inputs, state/provenance and the reproducible edit.

No fixed reel length, resolution, chapter count or SOZ path is universal. Match
the user's scope and existing deliverable; avoid adding more process or footage
than helps explain the work.
