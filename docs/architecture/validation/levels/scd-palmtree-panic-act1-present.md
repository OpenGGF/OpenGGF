# Sonic CD Palmtree Panic Act 1 Present — partial coverage

Game/stage: `scd`, Palmtree Panic Act 1 Present, native `R11A`.
The development stock registry admits zone 0 / act 0 / level index 0 only.
The native catalogue retains all seventy stage payloads per release; registration
of this static entry does not certify another stage or a native entry/exit edge.

Implementation is **partial static terrain presentation**. No character route is
implemented yet. Native Sonic movement/collision, objects, time travel, checkpoint,
completion, incoming/outgoing transitions, music and animation remain missing.
This matrix applies the [level standard](../../../guide/contributing/level-test-standard.md)
as an obligation inventory, not an act certification.

Source reference is `scddisasm` at
`1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff`. Supplied complete content records are
USA `redump-29188`, Japan `redump-1632`, Europe `redump-8302`; the variant and full
disc identity remain distinct even where JP/EU stage program bytes match.
Runtime data comes exclusively from those user-supplied discs.

Support authorities are `SonicCdGameModule`, `SonicCdLevelInitProfile`,
`CdStockLaunch` and `CdR11aAssetLoader`. The static profile installs no actors and
explicitly rejects native title/art/restart/checkpoint assembly requests. Those
development restrictions do not establish the final game's supported character,
donor or roster policy. Required native and cross-game breadth remains unassessed.

| Configuration dimension | Observed coverage | Remaining obligation |
| --- | --- | --- |
| Viewport | Production 320×224 capture on all three supplied releases | 352, 400, 528 and 800 presets; gameplay, edge/culling and restore products |
| Movement donation | Off for static presentation; no movement executes | Actual supported donor policy and every supported donor through gameplay/lifecycle |
| Character/roster | Empty actor registry in the static entry | Native Sonic, additional admitted characters, solo/pair/mixed/duplicate followers and actual participant contract |
| Lifecycle | Explicit fresh static assembly, same-source reload, same-world restore | Cold title/new game, native restart/death/checkpoint, completion, transitions and time travel |

| Obligation and spot | Contract / setup authority | Test and observed execution | Status / gap |
| --- | --- | --- | --- |
| ENTRY: authenticated bytes and static assembly | Actual CUE → complete content match → R11A loader → normal world/profile | `TestSonicCdStockPreparation` (9), `TestSonicCdStaticStageLoad` (3); configured actual media, no skips | Partial: no native player/control/grounding entry |
| LOAD: geometry and ownership | Fresh mutable engine objects from immutable authenticated assets; no invented resident tails | `TestSonicCdLevelGeometry` (9), `TestSonicCdLevelRetail` (3), module/load contracts | Partial: unused unavailable cells remain unavailable; native resource/PLC and outgoing handoffs missing |
| CAMERA: initial static origin | Decoded centre/bounds; existing camera and renderer | `TestSonicCdDirectStageBoot` (3): unfocused static camera, visible change after movement, exact restored image | Partial: native follow, locks, wrap, parallax and all other widths missing |
| PRESENT: actual terrain/palette | Existing `GameplayCaptureSession.render`, controlled tiles-only pass, owned EGL/no-device backend | Three 320×224 regional PNGs inspected; visible terrain/palettes and repeatable held view | Partial: no native pixel-parity comparison, animated tiles/palettes, actor art, music or SFX |
| REWIND: same-world camera/level | Existing registry/adapters, original source retained by the world | Logic load test plus three real GL camera-change/restore checks; identical restored images | Partial: cross-registry source identity guarding remains open; native gameplay/service/interaction spots missing |
| OBJECT / EVENT / LIFE / ROUTE | Native source owners must be implemented and exercised | No executed native route | Missing, including placements, interactions, events, death/checkpoint and complete outgoing route |
| BOSS | `R11A` is a traversal payload; boss programs are native act-3 payloads in `CdStageKey` | No act-1 boss product claimed | Not applicable to this payload; act-3 coverage remains separate and missing |

Execution checkpoint: `56beba899` plus the pending admission changes, 24 October
2026. Ten frozen module/test files had aggregate source hash
`232d03415237dbac9591307c46e55a758e485f0cc121eca4ca546cdf4e04cf02`.
The detached source/geometry review scope retained hash
`1d46b97b4aa5ae28a0e128e515e1b4881c52544ca2808a830032201718b9ab61`.
These identify source; they are not execution certificates.

The combined focused run completed at 00:05:12 BST: 98 tests, zero failures,
errors or skips, Maven 34.613 seconds, queue wait zero. Two earlier invocations
stopped during test compilation on missing exhaustive CD cases in cartridge
object harnesses; explicit rejection cases repaired them before any tests ran.
The separate API/real-GL run completed at 00:07:21 BST: 34 tests, zero failures,
errors or skips (31 API/policy + 3 regional GL), Maven 34.685 seconds, queue wait
35 seconds. Commands, limits and subsequent broad validation state are in the
[implementation plan](../../plans/2026-10-23-sonic-cd-implementation.md#combined-admission-validation-queued-24-october-2026).
Both commands explicitly supplied the media property; GL additionally selected
surfaceless EGL with `DISPLAY`/`WAYLAND_DISPLAY` unset. Durable images remain in
the external Downloads task bundle, not the repository.
