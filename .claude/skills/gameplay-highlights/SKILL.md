---
name: gameplay-highlights
description: Use for OpenGGF work compilations, promotional reels, mod showcases, or refreshing highlights after fixes; not individual capture or parity testing.
---

# Gameplay work highlights

Turn verified captures into a concise account of the delivered work. Keep the
complete source archive separate from the edited reel. Do not concatenate every
probe, screenshot and test variant into a “highlights” movie. This skill works
across games; it does not require an S3K bring-up or authorize engine changes.

For a promo, trailer or showcase meant to sell the work, also follow
[promo-direction](../promo-direction/SKILL.md) for the brief, beat-grid storyboard,
typography, layouts, soundtrack and the flatness review. This skill still governs
provenance, honest labelling, verification and the OpenGGF-Media archive.

## Collect evidence as work proceeds

Use one explicit task directory outside the engine repository for durable media. Reuse
an existing task root when resuming. Give each scenario/version its own directory;
never overwrite an earlier source capture when recording a correction.

Use [gameplay-capture](../gameplay-capture/SKILL.md) for ordinary engine footage,
[bk2-input-authoring](../bk2-input-authoring/SKILL.md) for inputs, or
[trace-capture](../trace-capture/SKILL.md) for an existing replay. Native reference
footage must remain identifiable as native. Do not record new traces just to
make a reel when existing ordinary gameplay supplies the requested footage.

Keep enough provenance beside each retained scenario or in the task index:

- Source commit (or base plus uncommitted changes), game/zone/act, actual width
  and resolved character/donor/team.
- Capture driver/command, input file or controller recipe, and entry method:
  cold route, checkpoint reload, positioned setup, or seeded diagnostic.
- Relevant setup writes, frame stride/rate and whether audio was captured.
- State CSV and named event frames; outcome, known defects and whether the
  footage is final, superseded, or a failed experiment.

Read the state CSV before choosing frames. Verify actual identity and milestone
progression: configuration strings alone are not proof. Positioned entry can
skip priority switches, lighting, camera history or terrain events. A beautiful
clip from invalid setup is not a good final demonstration. Record real input
masks if a live controller leaves the textual input column blank.

## Select the story

Unless the user requests another structure, order by game/zone, Act 1 then Act 2,
and progression through each route, with bosses, results and exits at their
natural positions. Loops and alternate paths need route milestones; raw X alone
is not a reliable ordering key.

Choose one useful moving example per feature. Show enough approach, interaction
and consequence to understand it. Prefer footage from the delivered revision.
Exclude idle waits, long test setup, debug overlays, failed attempts, still-image
montages, duplicate width/roster tests and repeated fights unless comparison is
explicitly the point. Do not cut away the consequence that demonstrates success.

For a follow-up fix, replace the affected excerpts and recheck nearby cuts.
Preserve unaffected scenes and original captures. Re-evaluate source time ranges:
a corrected controller route can shift every later milestone. Do not assume
old frame numbers remain valid or append another copy of the same feature.

Keep a small ordered CSV/JSON edit list with, at minimum:

`act, route_order, title, source, source_start_seconds, source_end_seconds`

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

Choose one audio policy for the reel. Preserve genuine audio consistently or
make the reel silent when sources lack it; do not invent soundtrack licensing or
mix unrelated audio into evidence. Use text files for labels and structured
subprocess arguments/concat manifests for paths rather than shell interpolation.

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
  preference recipes, original editing project files, assembly scripts, edit
  lists, chapters, source-commit provenance and retained verification/decisions.
  Document required dependencies and path relocation so the edit can be rebuilt.
- Update the project index and the repository's project/root/source catalogs,
  including source sizes and SHA-256 hashes. Catalogue footage, finished reels,
  audio, artwork, frame sequences and bulk state data that remain external in
  `catalog/assets.csv`, with their durable locations and root mappings. Never
  imply that a catalog entry uploads or makes an external asset downloadable.
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
  aspect ratios, boss outcomes, act handoffs and replaced footage. Sampling stills
  alone cannot catch frozen sections or incorrect playback cadence.
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
