# S3K actual-fixture reel and repair assessment — 2026-10-26

This worker continues delivered `94b2a5bf1daeaaad550ff7b8091a8361d6a9094e`,
using shared recorder API `4ffa30ed51aa0c3ba93d33073682bebf57989399` and
headless cached-art bridge `7e1e53607fcda7849143c030a0c15987dee0e8ea`.
The parent owns inventory/scope, combined execution/encoding, result cards,
integration and push. This branch changes test capture hooks only; assertions,
parameter providers, scenario inlets, input sequences and update counts remain
unchanged. No production physics or gameplay repair is included.

## Reviewed scope

Every one of the 67 owned invocations in 20 `TestTasS3k*` classes has an explicit
class/method/case disposition. There are 31 drawable invocations and 36 cases
whose fixture has no visible drawable presentation at its tested ownership/view boundary. The latter receive one-frame labelled
result cards in the parent assembly; they are never represented by substitute
level footage. Exact identities, parameters, reasons and source evidence are in
`$TASK_REEL_ROOT/full/s3k/dispositions.json` (the external dated reel directory).

| Fixture owner | Actual presentation |
| --- | --- |
| Bonus bounds, tally/death, pause, special-stage entry | The existing `GameplayCaptureSession` step and production render, once per original step |
| NewGamePlus | Existing presentation-input step, then the same session render; title/data-select/load rows retained |
| Flame slope | Existing SOZ2 `HeadlessTestFixture`, after its original frame; native terrain/player/shield/object presentation |
| Blue Sphere motion | Existing component manager's exact player/grid/HUD/banner, through its production renderer |
| DDZ missile | Actual selected launched missile, native mapping/direction/palette, fixed component view |
| Signpost RNG | Actual signpost at both original update boundaries; no fabricated sparkle |
| MHZ rapid hit | Existing parent/children through their production object draws; hit proxy remains collision-only |
| Slot-six cutscene button | Actual button sprite and pressed state at the two existing updates |
| Water-skim lifetime | Actual splash state/coordinates with production ROM art; no replacement player or water plane |
| Terrain entry, selected methods | Real LBZ lift and monitor/contents components only; authored sensor terrain and unowned player art are absent |

The component helper is opt-in and owns its EGL context with try-with-resources.
It loads native art/mapping/color bytes into detached presentation owners; their
player, grid or terrain is never installed or drawn. Object drawing temporarily
overlays only the render-manager service and then reinstates the identical
service reference. It does not restore gameplay state: a changed rewind snapshot
fails capture. Blue Sphere uses the complete manager rewind snapshot; object
components use the actual manager's identity context, including child references;
the splash guards participant and handler state. Shared headless drawing has a
listed-player-field guard, not a proof of all world-state purity.

Non-drawable reasons are fixture-specific: mocked title presentation and header
routing, parser-only machine actions, mock terrain/player-only checks with sensor
geometry but no art/layout (with separate real-object component overrides), no-op
respawn sprites, dummy ORT/full-solid draws, player-only Blue Sphere delay without character/view/grid, grid-only conversion without viewpoint,
and queue-only admission/service without a display/DMA owner. ROM availability
alone does not make those data fixtures scenes.

## Qualification and rejected adapters

All real test capture uses `tools/test_reel/test_reel.py`, which invokes the
Maven queue, pins source hashes and actual JUnit IDs, unsets DISPLAY and
WAYLAND_DISPLAY, and requests surfaceless EGL. Java 25 and Lua 5.4 are explicit.
Original absolute `s1.gen`, `s2.gen`, `s3k.gen` and `kis2.gen` under
`$ORIGINAL_ROM_ROOT/` are supplied through the tool's matching
ROM options. Captures and retained metadata stay under the external task directory.
No video is assembled by this worker.

The initial build/capture attempt ran no tests because of owned import and
short-width signature mistakes. `capture-002` then ran 28 focused invocations:
26 passed, two MHZ capture errors, zero skipped. The errors came from the guard
calling a child's rewind capture without its manager identity table. They did
not identify a gameplay mutation. The corrected isolated `capture-003` ran both
MHZ controls successfully with nine component frames each, zero failures/errors/skips.

Image inspection rejected two assertion-passing captures. The DDZ viewport
excluded the actual launcher coordinates; only the component view origin was
corrected. The generic encounter art library's first BUTTON match referred to an
unresident level tile range, yielding no visible button. The helper now prefers
the exact standalone native PLC entry (`ArtNem_GrayButton` and `Map_Button`) through
the existing registry/loader. No live art queue, tile memory or fixture state is
changed. The signpost's allocated sparkle has no RingManager in this fixture and
would use a primitive placeholder, so that child is deliberately omitted from
the component picture while its allocation/RNG assertions remain intact.

`capture-004` requalified all six initial component-helper classes: 15 tests
passed, zero failures/errors/skips. Pixel inspection confirmed the corrected
missile and button. A final narrower pass added the terrain lift/monitor
boundaries, corrected the MHZ component crop, retained logical jump metadata for
the water fixture, and requalified all four flame cases after the shared cached-art
bridge. `capture-005` stopped at compile (zero tests) on the new participant
snapshot overload; using the playable owner's no-argument snapshot corrected it.
`capture-006` then passed all 13 invocations, zero failures/errors/skips.
No gameplay mutation was observed by the stated guards.

The final reusable slices are:

| Run directory | Selected final footage | Verified outcome |
| --- | --- | --- |
| capture-002 | Nine unchanged session/menu/pause invocations | Each passed; the overall run retained the two separately corrected MHZ guard errors |
| capture-004 | Blue Sphere six, DDZ missile two, signpost two, cutscene button one | Eleven passed drawable invocations; whole run 15/0F/E/S |
| capture-006 | Flame four, MHZ two, splash one, visible terrain objects four | Eleven passed drawable invocations; whole run 13/0F/E/S |

All 31 drawable cases have actual capture records. The far-monitor control in
`capture-006` has 29 honestly blank frames with the monitor outside the fixed
fixture view; its explicit disposition selects a card. The wheel/no-monitor
control has zero drawable frames. The airborne-wheel monitor branch records only
its one actual monitor update; later wheel updates are explicitly invisible
controller/sensor physics, not fabricated travel footage. LBZ lift footage shows
only the actual moving platform, with no invented wall or player. Every original
control/assertion still executes.

Captured native frame counts include flame 46/46/102/102, MHZ 9/9, splash 10,
LBZ lift 112/112, visible glide-monitor 29, and wheel-contact monitor 1. Image QA
inspected native sprites, meaningful nonblank pixels, mode transitions and endpoint
frames; the splash jump row records logical input mask 16 without inventing a
physical A/B/C button. Component modes are explicitly labelled as component views.
Session title/load/fade holds, including their real blank rows, are retained.

Exact queued commands, selected IDs, original ROM CRC/SHA-1, source diffs/hashes,
per-case JUnit outcomes and state/frame links remain in each external run's
`run.json`, `manifest.json`, `case.json` and `state.jsonl`. The capture selectors
were the named session/component classes for `capture-002`, the six component
classes for `capture-004`, and
`TasS3k(FlameSlope|MhzRapidHit|WaterSkimLifetime)#` plus
`TasS3kTerrainEntry#(lbzLiftWallEntryProducesRepeatedReverseCorrectionsAndExit|freshGlideIntoMonitorReboundsUpwardAndRetainsGlide|airborneWheelDepartureRetainsAdhesionAndCrossesAnUnsupportedGap)`
for `capture-006`. Original ROM options and environment above apply to all runs.
Final-run queue waiting exceeded 20 minutes due to scheduler-reported memory
capacity behind an unrelated category batch; it was not execution time or a
failed run. No lock/resource override or duplicate request was used.

The source-preservation check compares every changed test's parameter expansions
and exact assertion/verify calls against delivered `94b2a5bf1`; all agree.
Focused capture verifies the actual executed outcomes and native-duration frames;
this is not full-suite, full-publication, ROM-parity or complete-world purity
certification. Parent execution owns the remaining result-card cases.

## Unsupported children and shared remediation

The source-based assessment covers all 17 unsupported child IDs from the final
per-game verdicts and published report, retaining exact family/name/publication
identities. It does not implement or guarantee the proposed repairs. Complexity
means code/system scope, not elapsed time. The full owner evidence, proposed
repair, dependencies, rewind/other-game impact, acceptance checks, confidence and
unresolved questions are in the external `supportability.json` beside dispositions.

| Remediation group | Child IDs | Classification / complexity |
| --- | --- | --- |
| unsigned-x-underflow | s3k-hwrap:1 | Bounded parity repair / MEDIUM |
| cutscene-slot-six-alias | s3k-bridge:1 | Deliberate compatibility model / MEDIUM |
| water-skim-slot-lifetime | s3k-hczslot:1 | Bounded parity repair / MEDIUM |
| reset-power | s3k-nolr:3, s3k-reset:1 | Feature implementation / HIGH |
| soz-ground-response-axis | s3k-shields:3 | Bounded parity repair / MEDIUM |
| special-stage-entry-input-latch | s3k-sphere-timing:2 | Bounded parity repair / MEDIUM |
| pause-vint | s3k-rng:1, s3k-ddz:2 | Bounded parity repair / MEDIUM |
| mhz-proxy-repeated-damage | s3k-boss:2 | Bounded parity repair / MEDIUM |
| s3-competition-host | s3k-competition:1, s3k-standalone:2 | Feature implementation / VERY_HIGH |
| standalone-sk-boot | s3k-standalone:1 | Feature implementation / HIGH |
| kosm-mode-corruption | s3k-standalone:3, s3k-legacy-mode:1, s3k-mode:1 | Deliberate compatibility model / VERY_HIGH |
| ort-backward-cursor-desync | s3k-ort:1 | Deliberate compatibility model / HIGH |

Shipped corruption is distinct from missing ordinary logic. The slot alias,
unequal layout/ORT cursor recovery, and KosM overflow can have safe, explicitly
bounded semantic compatibility models; Java memory safety does not make their
published consequences impossible. Arbitrary CPU/RAM emulation is outside the
current semantic engine, so exact bytes/consumers and scope must be established
before claiming those broader routes. Reset signal parsing alone likewise does
not implement a crash/reset-dependent publication.

The flame assessment uses the final completed-player counterparts, not the
rejected early-landing inference. Native and engine match at tick 34, centre
`$856.7100/$409.ED00`, vx/vy 529, g749 and angle `$20`. At tick 35,
`loc_1137E / loc_113A0 (sonic3k.asm:22764-22794)` decrements the negative SOZ
response angle before rounding, directing the same penetration to X and clearing
ground speed. Engine currently applies it to Y. Acceptance must compare that
axis and the subsequent reverse/ejection chain, retaining the no-dash and special
slide controls. The earlier after-frame sample inside collision at PC `$12072`
is not completed-player evidence and supports no early-landing claim.
