# Sandopolis Zone: methodology v2 application plan

Date: 2026-09-15; status reconciled 2026-09-16. Implementation and two cold
native-movement Sonic + Tails routes at width 320 are delivered on `develop`
(`358679affa`, delivery record `1e3626aca`). Both bosses and outgoing transitions
are verified in those routes. Full methodology acceptance remains incomplete:
the lower Act 2 subtype-$87 puzzle, strict native behavior/pixel parity, broader
character/donor/viewport routes and remaining lifecycle/rewind products are open.
See [remaining acceptance](#2026-09-16-retrospective-and-remaining-acceptance).
Dated execution sections retain the evidence and status at the time of each run.

## Objective and authority

Apply [methodology v2](../designs/2026-09-15-zone-methodology-v2.md) to locked-on
S3K Sandopolis Acts 1 and 2: ordinary entry and traversal, all reachable mechanics
and character branches, bosses, Act 1-to-2 lifecycle and the outgoing LRZ route.
Verify the exact outgoing destination/dispatch against ROM before implementation.
Use existing runtime support where correct; this is not a direction to rewrite it.

The [SOZ catalogue](../research/s3k-zones/soz-analysis.md) is the starting research,
not verified current coverage. The planning base is develop `44f8503b3886d99dc5b64b29b59fc7b67f47d793`.
No implementation or trace baseline was measured for this documentation change.
Execution base: `2b2bf8e2818f424106a9494523bdf8fae53f082b`; isolated
`.worktrees/soz-v2` on `feature/ai-soz-v2`. The intervening upstream commit only
adds score/ring coverage to the roadmap and is preserved.

## First execution slice: inventory and native pilot

1. Inspect production zone, event, object, scroll, palette, animation, PLC and
   transition registrations, player spawn/CPU and terrain-driven player hooks,
   existing tests and current discrepancy/frontier
   records. Decode both acts' actual placements, including used subtypes and
   reachable children. Separate absent factories from implemented route gaps.
2. Reverify source labels and branches in the existing analysis with the S3K
   disassembly skill. In particular, reconcile its Act 1 transition description
   of “no title card” with its Act 2 entry description of spawning `Obj_TitleCard`
   mid-fade. Determine the actual title/control lifecycle before writing an oracle.
3. Establish the native capture path with one small Act 2 light-switch/darkness
   sequence. Verify ordinary initialization, the owning clocks, fade direction,
   palette, torch presentation and relevant ghost state. Demonstrate that the
   setup can be reproduced before committing to a large capture tool.
4. Create both SOZ act matrices under `docs/architecture/validation/levels/` and
   link them from the coverage backlog. Enumerate supported character routes,
   checkpoint sets, width/donor/team obligations and rewind boundaries. Mark
   inherited gaps and unmeasured results explicitly; do not seed passing statuses.
5. Record ROM/disassembly identity, current implementation findings, exact pilot
   recipes and baseline evidence here. No trace sweep is needed merely to justify
   the user-selected target.

## Dependency-ordered route slices

The labels below come from the catalogue and must be checked against the current
locked-on source. They identify research owners, not fitted runtime constants.
Each row includes ordinary local traversal, a short independent native comparison,
representative compatibility, rewind/replay and review of the coupled boundary.

| Slice | Scope and owning reference leads | Early independent check and principal risk |
| --- | --- | --- |
| 1. Act 1 entry and desert traversal | `loc_695A`, `sub_730C`, `SOZ1_ScreenEvent`, `SOZ1_BackgroundEvent`, `AnPal_SOZ1`, `AnimateTiles_SOZ1`; placed quicksand variants, rocks, vines, platforms and badniks | Camera-driven background/animation at entry; quicksand approach, sink and escape in adjacent phases. Verify movement donor feasibility and actual production object bindings |
| 2. Act 1 arena and miniboss | `sub_55E96`, `sub_55D94`, `Obj_SOZMiniboss`; sand rise, delayed redraw/art handoff, door children | Trigger before/at/after lock, art readiness and spawn order; ordinary boss interaction through defeat and door opening; failed child allocation and rewind |
| 3. Seamless Act 2 entry | `SOZ1_BackgroundEvent`, `sub_55EFC`, `SOZ2_ScreenInit`, `SOZ2_BackgroundEvent` | Paired-player door admission versus solo/extra followers; fade, queue readiness, reload, wrap setup, title lifecycle and control release. Compare the entire short transition, not just its destination |
| 4. Act 2 darkness and traversal | `AnPal_SOZ2`, `AnimateTiles_SOZ2`, `Obj_SOZLightSwitch`, Hyudoro/capsule routines; doors, switches, wires, sand corks | Light switch immediately before/at/after a darkness step and during brightening; ghost release/character/checkpoint conditions, torch/palette agreement and held-player ownership |
| 5. Rising sand and pyramid changes | `SOZ2_ScreenEvent`, `SOZ2_BackgroundEvent`, reached `SpecialEvents` sand routine, `sub_5699A` | Moving sand, camera/background collision and terrain edits in the same frame sequence; cross the Act 2 vertical wrap with players, camera, objects and rendering; reverse approach and rewind |
| 6. Act 2 boss and exit | `sub_56706`, `sub_56A12`, `Obj_SOZEndBoss`, reached defeat/capsule/transition routines | Wall collapse/reconstruction, solids, boss hit/defeat and darkness/animation shutdown; ordinary completion into the next route, cleanup and reset isolation |

Shared state and phase contracts precede dependent objects. The Act 2 native pilot
is early research; it does not replace the dependency order or claim traversal.
Catalogue quicksand as object behavior, not water physics, unless source review
disproves that distinction. `No_Resize` is not evidence that SOZ has no events.

## Explicit presentation work and revised next batch

Historical amendment during implementation; the current status and next actions
are in the header and final retrospective. This table records the amendment’s
acceptance boundaries, not today’s outstanding task list.

The original slice table named scroll/animation routines but the execution summary
and next-task selection drifted toward placed objects. Presentation remains part
of route completion, not a final cosmetic pass. Audit at `e9e7267dc` found SOZ
still selected the generic quarter-speed scroll fallback. Act 1 has a custom
animated-art channel, but its phase may read cached presentation state and its
secondary transfer copies `$60` bytes where native DMA requests `$60` words.
Its declared range ends at `$33E`, rather than the required `$341`. These are
implementation findings, not fixes delivered by this documentation correction.

| Route slice | Required presentation delivery | Acceptance boundaries / current gap |
| --- | --- | --- |
| 1: normal Act 1 desert | Dedicated `sub_55D56` parallax, seven fractional X tiers, Y/16, `word_560DC` bands; `loc_55DF2` FG/BG heat shimmer; `AnimateTiles_SOZ1` and `AnPal_SOZ1` | ROM-backed tables; all 224 lines; independent FG/BG wave phases; all 32 art phases and split transfers; same-camera/update-order behavior; GPU-visible tiles `$330..$341`, reverse movement and rewind. Normal-desert owners and focused checks now implemented (record below); full native pixels and wider phase coverage remain open |
| 2: Act 1 arena | `sub_55DB6` / `sub_55E4C`, sand offset and shake, custom background blocks/art | Before/after arena switch, delayed redraw, shimmer and animation phase-zero handoff; event owner and final arena rendering implemented; partial VDP row presentation open |
| 3: Act 2 entry | Initial pyramid background, wrap setup, secondary art, palette fade and torch initialization | Queue readiness, fresh versus seamless entry, control release and reset/restore implemented; full cold victory route open |
| 4: normal Act 2 traversal | Event-selected background framing, `sub_566D2` half-speed outdoor parallax where selected; `AnimateTiles_SOZ2` torches tied to the live palette fade accumulator | Three timer frames per animated intensity, pinned dark state, eight-pass cadence; correct six-tile DMA extent; brightening/darkening and rewind. Do not apply outdoor scrolling to every Act 2 state; coupled owners implemented; full route/pixel comparison open |
| 5: sand and pyramid changes | `sub_566E8`, moving sand, background/collision changes, vertical wrap | Both sides of trigger/wrap, players/camera/objects/rendering together, art retention and restored state; open |
| 6: boss and exit | `sub_56706`, `SOZ2_BGDrawArray`, wall reconstruction, animation inhibition and restored secondary art | Arena mode bands, darkness reset, repeated inhibition writes, defeat/exit restoration and rewind; open |

The coupled presentation batch recorded below implements **normal Act 1 parallax,
heat shimmer and animated-background correction**, with source-derived arithmetic,
ROM art-range checks and native/engine moving-camera observations (not matched
pixels), plus Act 1 palette cycling. Next resume the remaining Act 1 traversal
blockers (swinging platforms, rappel wires, sand/path mechanisms and enemies) and
join a continuous route to the miniboss. Act 2's palette/torch/event state is a
separate coupled batch; implementing a bright-only torch loop is not completion.
Boss, sand-rise and transition presentation stays attached to its owning slice.

## Acceptance and validation

- Pin short comparison checkpoints before candidate rendering, including act
  starts, sand rise, door/transition, darkening/brightening, ghost activation,
  wrap crossing, boss wall and final exit. Declare input/setup and compared fields.
- Run native standard-width plus a wider viewport, S1 donor and extra-follower
  case at each mandatory mechanic. Expand phase, width, donor and participant
  combinations where the mechanic is sensitive; final matrices retain the full
  level-standard obligations, including S2 donation where supported.
- Exercise every checkpoint's production death/reload, repeated entry/reset,
  object/event reconstruction and forward replay. Establish whether each load
  preserves or resets the rewind timeline through its real production owner.
- Add short independently runnable boss/event tests. Cold full-act routes must
  prove access to those encounters with ordinary inputs for materially different
  character routes; setup teleports, state writes and forced hits are not traversal.
- Once both routes are broadly implemented, run applicable full-route V5 replay
  and complete native/visual coverage. Inspect skips and attribute inherited trace
  failures by identity and first differing field; a green local route does not
  override a red strict comparison.
- Execute mandatory S3K loading/bootstrap/AIZ checks, affected shared regressions,
  required structural guards and repository delivery validation. Select exact
  commands/test identities from the implemented changes, not speculative names.

## Execution record and next action

Implementation and the two bounded cold routes are delivered. Next establish
independent passage evidence for the lower Act 2 rock/switch puzzle, then resolve
the strict trace failure and expand the remaining matrix obligations.
The [retrospective](#2026-09-16-retrospective-and-remaining-acceptance) is the current
action list; subsequent dated sections preserve the execution history. Reuse the
SOZ analysis for ROM findings and act matrices for acceptance evidence.

Documentation validation for this initial plan checks local links, whitespace,
policy trailers and behavioral scenarios: blocked native capture must remain
unaccepted; seeded boss evidence must not become a cold-route pass; compatibility
failure must expand the affected checks; a title-lifecycle catalogue contradiction
must be resolved from ROM before implementation. Engine behavior is unchanged.

## 2026-09-15 initial execution

### Inventory and source corrections

- [Placed inventory](../research/s3k-zones/soz-object-inventory.md): 599 Act 1 and
  490 Act 2 live placements, excluding one terminator per act. Exact source bytes
  match the verified locked-on ROM at `$1F4866` and `$1F5676`. SKL object IDs are
  hexadecimal in the inventory; the older analysis's object table is decimal.
- Shared art/loading and Act 1 custom animation exist. Dedicated SOZ event,
  scroll, darkness and most object owners are absent; concrete shared factories
  do not establish production subtype or traversal coverage.
- `loc_56324` allocates `Obj_TitleCard` at fade count 5 and sets its `$3E` byte.
  Thus “no title card” in the catalogue was incorrect. `loc_55CEA` preserves the
  relative P2 offset when moving P1 to `($140,$3AC)`; it does not place both at
  the same coordinate. Both catalogue descriptions are corrected.
- [Act 1](../validation/levels/s3k-soz-act1.md) and
  [Act 2](../validation/levels/s3k-soz-act2.md) matrices now enumerate obligations
  and inherited gaps. Dynamic-spawn/art/audio inventories remain incomplete.

### Native pilot: ordinary inputs, comparison only

The disassembly supplies the darkness timers, palette-step conditions and switch
reset rules. The emulator pilot checks their combined execution under ordinary
inputs and establishes a reusable visual reference; it is not needed to discover
constants already explicit in source. Its useful additional evidence includes
which participant activates the switch and the timing of the visible response.
Keep future probes tied to a specific integration question or comparison need.

Command variables: `SOZ_REPO` is the absolute main-checkout path;
`SOZ_CAPTURE_ROOT` is the external task directory named `soz-v2-20260915`.
Native artifacts remain outside the repository.
ROM SHA-1: `CFBF98C36C776677290A872547AC47C53D2761D6`.
BK2 SHA-256: `82EABFBC65E33C160CE209BAA1CA3F967CB677FE22350BC100625D8C41A8E1BF`.
Source reference: `docs/skdisasm` at `1a454a0e335137a1a016d1090a9f4528d36944cf`;
unrelated working-reference edits were preserved.

Reused the existing native visual host with an explicit diagnostic Lua override;
its historical FBZ output names and unused checkpoint plan are not SOZ acceptance
metadata. The probe uses the reset BK2 opening, ordinary AIZ vine cheat, pause/A,
title menu and level-select index 17 (SOZ2). No RAM writes or gameplay hydration.
From LFC35, idle until step 2801, steer toward X `$2B0` through step 3099 with
20-of-50-frame jump holds, then release inputs through step 3800. The saved
`native-pilot.lua`, `host.json`, input/switch rows and PNGs identify the exact run.

Command (absolute source and output paths as above):

```bash
python3 tools/bizhawk/capture_fbz_visual_references.py \
  --bizhawk-home ${SOZ_REPO}/docs/BizHawk-2.11-linux-x64 \
  --rom "${SOZ_REPO}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  --movie ${SOZ_REPO}/src/test/resources/traces/s3k/_movies/s3k-complete-sonic-tails.bk2 \
  --exporter ${SOZ_CAPTURE_ROOT}/native-pilot.lua \
  --fresh-entry-act 2 \
  --output ${SOZ_CAPTURE_ROOT}/pilot-3 --timeout 120
```

`pilot-1` failed before emulation because the sandbox could not open X. Running
with display permission produced `pilot-2` in 7.927s and the expanded observer
`pilot-3` in 9.029s, both exit 0. Their 3800 darkness rows are byte-identical.
This is native evidence only, not an engine parity result or published fixture.

- Darkness changes at LFC923/1823/2723: 900-frame spacing. Shade steps 1→2
  at LFC923/927 and 3→4 at LFC2723/2727: four-frame spacing.
- Tails acquires the first switch at step 3106, native slot16, centre `($2B0,$300)`.
  Its P2 held byte is 1; P1 remains uncontrolled. Travel advances by two from
  step 3107 through step 3122, reaching `$20`. This is independent follower
  activation, not a P1-only switch contract.
- At step 3122 the owner resets darkness to zero, timer to 899 and fade direction
  to byte `$FC`. The first palette step follows at 3123, then 3127/3131/3135.
  Native dark and restored-light PNGs were visually inspected; engine palette,
  torch and ghost comparisons remain open.

The SOZ1 probe uses level-select index 16, then Right with 20-of-70-frame jump
holds for 1000 frames. Read-only hooks at `$3FD4E/$3FDCE/$3FE18/$3FE42`
observe the normal-strip owner. `quicksand-1` exits 0 in 7.627s. At native frame 4587,
P1 centre `(562,1620)` is captured by the first `($230,$640)` strip: velocities
`(-3632,168)` become `(-1816,84)` and status2 becomes14. Next frame the held pass
changes `(-1792,140)` to `(-896,168)`. Source explains the arithmetic and the
acquisition return; these measurements are corroboration, not runtime constants.

### Focused implementation and verification

`TestSozQuicksand` first ran against the baseline registry: five tests, five
expected failures, zero errors/skips (47.006s Maven). `$38` still resolved to a
placeholder. Implemented its four invisible variants through the SKL binding,
retaining S3KL's HCZ fan at the same ID. The owner uses native word comparisons,
per-participant held/cooldown state, the actual level clock for waterfall damping,
and native position operations for fractional slide movement. No runtime assets
are read from the disassembly.

The next focused run compiled the implementation but exposed missing service
injection in all five direct-object fixtures; corrected the fixtures rather than
weakening production service requirements. Further results are recorded below.

Tool preflight initially rejected ambient Lua. With `LUA_BIN=/usr/bin/lua5.4`,
`run_categories.py --base 9ee0efd93 --preflight` passed Java 21/Lua 5.4/PowerShell;
no tests ran in preflight. Final selection uses the actual execution base above.

The final pilot (`pilot-4`, exit 0 in 8.880s) adds ghost prerequisites. All 3800
observations have `Hyudoro_count=0` and `Last_star_post_hit=0`; darkness rows
remain byte-identical to pilot 3. `Hyudoro_ctr` gates Sonic/Tails spawning on the
checkpoint flag, so this pilot does not certify released ghost behavior. Its
Lua SHA-256 is `7F575C20CCF6BFD4253E1ED20726354F1324707A73FCB76BC5C03E7A31A61A6D`.

Independent source review identified a real candidate defect: `setRolling`
changes sprite dimensions and therefore ROM centre coordinates. Replaced it with
existing raw rolling-flag operations and explicit radii; the waterfall retains
its old radii. Strengthened the deep-sand test so setup cannot cancel the defect.
Added flipped slide/sand/waterfall, Knuckles jump and distinct-participant rewind
cases. The discovery guard also correctly required `$38` to move to the shared
implemented-ID set: every zone now has a concrete owner, with different owners
in S3KL and SKL. These are test/implementation corrections, not oracle relaxation.

The first cold-route test pressed jump immediately at load and missed the native
approach. A 35-frame neutral opening reaches the placed strip without a teleport.
That exposed a production integration defect: inline solid cleanup cleared
`Status_OnObj` despite the sand's retained participant bit. The object now uses
existing `markObjectSupportThisFrame`, as other non-solid owners do, after each
acquisition/held pass; releases and slide cooldown do not claim support. A second
independent source review found no substantive issue with that boundary fix.

Focused command in `.worktrees/soz-v2` (execution base plus candidate changes):

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestSozQuicksand,TestSozObjectInventory,TestSonic3kObjectProfileRegistryGuard,TestSozAct1QuicksandRoute' \
  "-Ds3k.rom.path=${SOZ_REPO}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  "-Dsonic1.rom.path=${SOZ_REPO}/Sonic The Hedgehog (W) (REV01) [!].gen" test
```

Result: 22 tests, zero failures/errors/skips, Maven 42.925s. This establishes
local object behavior, ROM placement/factory inventory and representative cold
entry/release, not a complete SOZ route or a matched native engine sequence.
The expanded `TestSozAct1QuicksandRoute` then passed all four configurations
(zero failures/errors/skips, Maven 18.942s). It restores the registered gameplay
snapshot and repeats one forward input twice at acquisition, held and release,
comparing each registered subsystem with `RewindSnapshotDiff`. This is bounded
rewind evidence for the reached strip; other variants retain local reconstruction
checks and the remaining act-level rewind obligations stay open. Final delivery
validation follows below.

### Updated integration base and validation scope

Candidate implementation: `86d800e53`; upstream reconciliation: `990203ad3`.
Updated integration base is develop `59d5b888181b14f29a48cbb75312b2801d56d7dc`.
Upstream FBZ rendering/fresh-load and results-driven load classification changes
were merged by intent without conflicts; both versions' release prose remains.

`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 59d5b8881 --preflight`
passes the required tool checks. The unchanged runner's plan selects all 2,583
ordinary classes because `Sonic3kObjectProfile` is classified as shared/unknown.
This particular profile edit moves one implemented ID between sets; the registry
guard exercises that contract. Runtime changes are confined to a single native
object family and its factory, using existing clocks, physics setters, support and
rewind APIs. No shared algorithm, build policy or timing contract changes.

Under repository proportionate-validation policy, delivery uses the focused SOZ
suite, the S3KL fan regression, required S3K entry/loading/bootstrap/decoding
checks, the existing FBZ-to-SOZ load-timeline regression, and structural guards.
This is focused validation, not a full ordinary-suite pass; whole-act native,
trace, rendered and compatibility matrices remain open.

### Next Act 1 dependency

The opening spring vine is the next source-backed interaction to bring up before
claiming a matched native approach. `Obj_SOZSpringVine` allocates one later-slot
visual child with eight pieces, deforms a sloped support surface, and processes
P2 before P1 in `sub_40878`; it is not a reskinned ordinary spring. Its allocation
failure path, shared tension state, ROM-backed `Map_SOZSpringVine` / `ArtTile_SOZMisc`
and slope publication must be included together. This is a source inventory lead,
not a new passing behavior or a completed route claim.

### Structural results and candidate correction

The dedicated `-Pguards test -B` run on `990203ad3` completed 667 tests in
3m16s: three failures, zero errors/skips. One was introduced by this candidate:
`TestRewindCoverageGuard.noNewCoverageGapsBeyondBaseline` reported `halfExtent`
and `variant` as undeclared final scalars. Both are recreated solely from the
immutable placement subtype, so they now carry the existing `RewindTransient`
annotation with that derivation reason. Mutable participant state still uses
its captured helper. No coverage-baseline entry was added.

`python3 tools/testing/maven_queue.py -Dmse=off -Pguards '-Dtest=TestRewindCoverageGuard' test -B`
then passed one test, zero failures/errors/skips (43.053s including recompilation).
The remaining two guard failures were checked against the unchanged base;
see the matched results below.

The shared-slot regression command selecting `TestHCZCGZFanObjectInstance` and
`TestS3kHczCgzFanGraphRewind` passed seven tests with zero failures/errors/skips
(18.219s), using the absolute S3K ROM property above.

### Matched baseline and focused delivery result

On unchanged main-workspace develop `59d5b8881` (HEAD checked before and after),
`python3 tools/testing/maven_queue.py -Dmse=off -Pguards '-Dtest=TestBuildToolingGuard,TestNoAssertionFreeDiagnostics' test -B`
ran 119 tests in 51.255s: two failures, zero errors/skips. XML comparison confirmed
identical failing method identities and messages on base and candidate:

- `TestBuildToolingGuard.supportedDocumentationMustUseDirectMavenAndExplicitHookBootstrap`:
  the guard still requires direct-Maven/concurrent-worktree guidance and the old
  printed-pinned-base command; current repository guidance requires the Maven queue.
- `TestNoAssertionFreeDiagnostics.noAssertionFreeTestMethodsUnderTestsTree`:
  existing `FbzRouteEvidenceProbe#printEvidence` and
  `LevelSolidityMapProbe#writeSolidityMap` have no recognized assertion oracle.

Those pre-existing failures remain unresolved and do not certify a green guard
suite. No unrelated test or instruction was changed to conceal them. Consumed
baseline XML/text reports were removed; no category-run diagnostics were created.

Final candidate context: `990203ad3` plus the explicit constructor-derived field
annotations, with no further gameplay changes. This focused command passed **81
tests, zero failures/errors/skips**, in 22.910s:

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestSozQuicksand,TestSozObjectInventory,TestSozAct1QuicksandRoute,TestSonic3kObjectProfileRegistryGuard,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestFbzSandopolisTimelineHeadless' \
  "-Ds3k.rom.path=${SOZ_REPO}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  "-Dsonic1.rom.path=${SOZ_REPO}/Sonic The Hedgehog (W) (REV01) [!].gen" test
```

The seeded FBZ EXIT_READY test proves the production fresh-load timeline boundary
and SOZ restore/replay; it does not prove the outgoing boss route. Changed Markdown
links, whitespace and the AGENTS/CLAUDE mirror were checked. The commit resource
policy initially rejected machine-local documentation paths; portable command
variables fixed that issue without weakening the policy.

### Final upstream reconciliation

Develop advanced to `1cfe9ef82` with the shared-support/configuration refactor
before SOZ integration. It merged without conflicts. The relevant S3K solid-profile
change extracts the same `Arrays.copyOfRange` loop into `SolidProfileDecoder`;
quicksand's existing support API is unchanged. The final focused integration check
also includes `TestSolidProfileDecoder`. The two inherited failing guard owners
and the guidance/probe text they inspect are unchanged by that upstream work.
Selection against this actual destination still chooses the full ordinary suite
solely because the one-ID discovery-profile edit is unclassified; the bounded
validation rationale above continues to apply to the complete SOZ diff.

### Integrated result

Merged into develop as `4bd85d687`. The final main-workspace command below passed
**90 tests, zero failures/errors/skips**, in 50.120s. Concurrent commits `6fbe2c276`
and `2860359ba` changed only the simplification task's documentation; the tested
engine tree is the SOZ integration tree. This result does not adopt that other
task's broad-suite result as evidence for SOZ.

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestSozQuicksand,TestSozObjectInventory,TestSozAct1QuicksandRoute,TestSonic3kObjectProfileRegistryGuard,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestFbzSandopolisTimelineHeadless,TestHCZCGZFanObjectInstance,TestS3kHczCgzFanGraphRewind,TestSolidProfileDecoder' \
  "-Ds3k.rom.path=${SOZ_REPO}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  "-Dsonic1.rom.path=${SOZ_REPO}/Sonic The Hedgehog (W) (REV01) [!].gen" test
```

For the next vine contract, `sub_4093E` launches an already-supported player when
its directional side marker crosses local X `$3C` inside `[0,$60)`. Its flipped
correction uses `NOT.W` on `$18` (`$FFE7`, -25), not negation (-24). Launch velocities
are `-$EF0` on X/Y, with X negated for horizontal flip. `sub_40A08` deforms the
collision profile and eight child Y coordinates from the same values in that
object pass. Preserve that coupling when implementing the next family.

### CI inventory follow-up

Push `6cd8ec188` smoke run
[34956007086](https://github.com/OpenGGF/OpenGGF/actions/runs/34956007086) completed
19,096 tests with one failure, zero errors and 2,827 skips. The failure is ours:
`TestRemainingRewindTailInventory.remainingRoundTripTailMatchesInventory` still
expected 1,010 total / 790 passing object classes. Its actual sweep found 1,011 /
791, unchanged 220 graph-covered and zero no-codec entries; no remaining-tail
bucket grew. The new quicksand class passed its sweep and all 15 unit checks.

The follow-up updates the expected totals and the accompanying resource summary
(which was already one class older), retaining all zero-tail assertions. It adds
this ordinary-suite inventory check to the existing implementation pitfalls so
future proportionate object validation includes it. No engine behavior or failure
allowance changes. The bounded follow-up is based on `6cd8ec188` in
`.worktrees/soz-rewind-inventory`, branch `bugfix/ai-soz-rewind-inventory`.

The follow-up runner plan selects all 2,588 classes because the inventory text
resource is unclassified. The actual executable change is two expected counts,
with no runtime changes or altered failure categories. Focused verification uses
`python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestRemainingRewindTailInventory,TestSozQuicksand' test`:
16 tests passed, zero failures/errors/skips, in 49.241s including a fresh compile.
The inventory test runs the real sweep across all 1,011 concrete object classes.

Follow-up commits `c2812dc11` and `1050440ca` merged into develop as
`7fae85a69`. The integrated command
`python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestRemainingRewindTailInventory test`
passed one test, zero failures/errors/skips, in 19.353s. This is the focused
post-integration inventory check; the engine checks above remain applicable
because the follow-up changes no production code.

### Spring-vine continuation

Base `316788395c81dd447e5a87d9f5b1d90b8efa528a`, isolated worktree
`.worktrees/soz-spring-vine`, branch `feature/ai-soz-spring-vine`. The previous
inventory correction's replacement CI [34959073568](https://github.com/OpenGGF/OpenGGF/actions/runs/34959073568)
completed successfully. This continuation targets SKL `$3F`: 12 Act 1 and five
Act 2 placements, starting with the Act 1 vine at `($298,$698)` immediately after
the first quicksand strip. HCZ's S3KL conveyor-spike binding remains separate.

Source contract: `Obj_SOZSpringVine` at `$40786` points to mapping `$40B0C`
(one 2×2-tile piece, tile zero, offsets -8/-8) in `ArtTile_SOZMisc`, palette 2.
The controller is invisible: `Delete_Sprite_If_Not_In_Range` does not draw.
One later-slot `loc_40872` display object owns eight pieces and independent
coarse-X culling. Shared tension processes P2 before P1; extra followers receive
independent P2 state, with P1 retaining final pivot ownership. `sub_40A08` returns
without changing slope or child heights at pivot zero. Rebound entries +/-1
are live oscillation values; only zero ends the sequence.

The existing sloped2 resolver assumes two pixels per byte. Native
`SolidObjectTopSloped` / `SolidObjSloped` instead sample every pixel. The candidate
adds explicit table resolution with the old default retained, plus a top-contact
direct top-helper window for `loc_1E45A`'s inclusive 16-pixel edge. This
window bypasses the full-solid bottom classifier: overlap 16 is valid even for
Tails-sized or rolling collision radii. The nullable default retains existing
providers' behavior, and an explicit helper selection applies across donors.
No game/zone-name condition
is added to shared collision code. Source review rejected drawing a ninth parent
piece and identified the inclusive landing edge for regression coverage.

Native source corroboration uses `${SOZ_CAPTURE_ROOT}/native-vine.lua` and
`${SOZ_CAPTURE_ROOT}/vine-1`, with the same verified ROM/BK2 and visual host above,
`--fresh-entry-act 1`. From native LFC35 it holds Right and C for the first 20 of
each 70 frames, for 600 frames. The exporter observes `$40852`, `$40AA8` and
`$409BA` without RAM writes. It exits zero in 6.024s; exporter SHA-256
`497AC95EE6C52DB050A9A608A78EB6623B48C940E13C8635C0CE6DA1EE003706`.
All 473 after-deformation observations match the candidate's source-derived
96-byte slope arithmetic and eight child heights, including loading and rebound.
P1 reaches the launch routine at native frame 4580 and P2 at 4587. A native frame
was visually inspected. This is arithmetic/native evidence, not a matched engine
trajectory or engine pixel certification.

Focused unit, collision, ROM art/inventory and cold-route rewind checks are in
progress. The shared collision contract requires normal broad validation;
preflight passed Java 21, Lua 5.4 and PowerShell, with no tests run by preflight.

Focused run (52.801s) executed 242 checks with zero errors/skips: 240 passed,
including all four new cold-vine routes with full registered-state restore/replay,
the four existing quicksand routes, ten vine unit checks, ROM mapping/inventory,
art crawler/renderer corruption checks, registry discovery and required S3K
bootstrap/loading/AIZ checks. The two failures were an expected inventory delta
(actual 1,013 total / 793 passing / 220 graph-covered / zero no-codec; neither
new class grows a failure bucket) and the new landing-boundary harness. The
inventory correction subsequently passed its actual sweep. The boundary harness
had omitted `snapshotPreUpdatePosition`, leaving `solidContactFirstFrame` true;
its pure geometry query passed while both global and inline drivers correctly
skipped the uninitialized object. Explicit S3K player rules alone did not address
that lifecycle omission. The corrected harness is being checked narrowly.

Allocation limitation: if the display slot cannot be allocated, the candidate
keeps the controller without a display. It does not reproduce native writes
through a failed allocation pointer; slot-exhaustion RAM corruption is outside
this slice's parity claim.

The 245-frame engine capture (`engine-vine-1`, `vine-input.txt`: 35 neutral,
then three 20-frame Right+C / 50-frame Right blocks) completed in 17.737s.
Its CSV stays in LEVEL mode, reaches X571 and contains no vine launch. Frame140
shows the eight-piece vine but the player has not reached it. This capture used
the build before the radius-independent landing-window adjustment; it is render
inspection only. The new boundary test's subsequent snap assertion incorrectly
read the radius after ResetOnFloor restored standing radii; its expected snap
now uses the incoming radius, as `loc_1E45A` does. These harness corrections do
not substitute for the queued corrected run.

Corrected focused command:

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestSolidObjectManager,TestSozSpringVine,TestSozAct1SpringVineRoute' \
  "-Ds3k.rom.path=${SOZ_REPO}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  "-Dsonic1.rom.path=${SOZ_REPO}/Sonic The Hedgehog (W) (REV01) [!].gen" test
```

All 97 tests passed, zero failures/errors/skips, in 22.449s on the candidate
based on `316788395`. This includes all incoming-radius boundary cases and
all four cold-route rewind configurations after the shared resolver adjustment.
Read-only source review found no further issue. Broad validation remains pending.

### Combined vine validation

Implementation commit `bbc983d57` merged updated develop `b8d0ae91b` cleanly
as `8e09d509d`; the release-note merge preserved both changes. The upstream
support-helper cleanup did not modify the shared collision files.

`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 316788395c81dd447e5a87d9f5b1d90b8efa528a --run`
selected all 2,594 ordinary classes plus guards. On `8e09d509d`, ordinary
completed 20,518 tests, zero failures/errors, 19 skips in 770.67s. Skips were
opt-in diagnostics/routes/native checks, unavailable EGL/OpenGL checks, local
reference captures, and `TestCPZObjectBugs.testSpinTubeForcesRolling`'s unmet
capture assumption. No SOZ checks skipped; separate trace/native profiles are
not included in this ordinary-suite result.

Guards completed 668 tests with three failures, zero errors/skips in 173.73s:
`TestRewindArchitectureGuard.objectRewindAnnotationsDoNotGrowWithoutExplicitBaselineTriage`
reported quicksand's two constructor-derived annotations;
`TestBuildToolingGuard.supportedDocumentationMustUseDirectMavenAndExplicitHookBootstrap`
expected obsolete direct-Maven prose; and
`TestNoAssertionFreeDiagnostics.noAssertionFreeTestMethodsUnderTestsTree`
reported `FbzRouteEvidenceProbe#printEvidence` and
`LevelSolidityMapProbe#writeSolidityMap`. A bounded matched-base run is pending.

The quicksand annotations belong to the earlier SOZ slice: `halfExtent` and
`variant` are immutable spawn-subtype decodes rebuilt by spawn recreation.
Explicit triage now records those two fields in the architecture guard;
mutable participant ownership/cooldown remains captured. This changes neither
runtime behavior nor the guard's general prohibition. Its focused check is pending.

Matched baseline command on main develop `2f3797ceb`:
`python3 tools/testing/maven_queue.py -Dmse=off -Pguards '-Dtest=TestRewindArchitectureGuard,TestBuildToolingGuard,TestNoAssertionFreeDiagnostics' test -B`
completed 123 tests, the same three failure identities/messages, zero errors/skips
in 84s. The relevant guards and root guidance are unchanged from `b8d0ae91b`.
The corrected candidate command
`python3 tools/testing/maven_queue.py -Dmse=off -Pguards '-Dtest=TestRewindArchitectureGuard' test -B`
passed all four tests, zero failures/errors/skips, in 19.266s. The two unrelated
build-guidance/probe failures remain baseline failures; they are not a green
structural-guard claim. Broad diagnostics were inspected and scheduled for deletion.

### Spring-vine integration result

The latest presentation changes from `2f3797ceb` merged cleanly; release notes,
coverage rows and pitfall entries retain both tasks' content. Main develop
integrated the vine slice as `b247c5fad143e24ae84949b27e5b9159e2f13c45` without
switching branches or changing unrelated local files.

The same combined change-based command and original base above ran after
integration, selecting 2,595 ordinary classes plus 84 guard classes. Ordinary:
20,521 tests, zero failures/errors, the same 19 skip identities, 772.52s.
Guards: 668 tests, two failures, zero errors/skips, 176.05s. Both remaining
failures match the baseline identities and messages exactly: obsolete direct-Maven
prose and the two pre-existing assertion-free probes listed above. The quicksand
rewind annotation guard now passes. No new or worsened failure remains in this
validation; the structural suite as a whole is not green.

All 17 vine placements now bind to the implementation. The first Act 1 vine has
cold-route launch and registered-state replay coverage across four representative
configurations. Act 2 reachability, full-act progression, remaining objects/events,
bosses and complete native trajectory/pixel certification remain open. The short
engine capture is rendering evidence only. No SOZ BK2 full-level completion claim
is made by this slice.

### Breakable sand-rock continuation

Base `77f6ee81dcc62637785e3f65136dcc9d0efdd466`; isolated
`.worktrees/soz-sand-rock`, branch `feature/ai-soz-sand-rock`. SKL `$44` has
17 Act 1 and 13 Act 2 placements, all subtype zero. First spots are
`($260,$5B0)` and `($1C0,$3B0)`. S3KL `$44` remains the CNZ trap door.

`Obj_SOZBreakableSandRock` at `$41702` points to mapping `$4182E`, five
frames of two pieces, with art base `ArtTile_SOZMisc+$10`, palette 2. Both
pointer bytes and mapping shape are checked against ROM. `loc_4172E` saves
player animation before SolidObjectFull can reset a rolling landing. A rolling
standing rider breaks the rock; all standing riders are released, but only
those with saved animation 2 receive `-$300` Y velocity, rolling radii 7/14 and
animation 2. These radius writes preserve the player's centre. The breakup
falls through on the triggering pass, advances every six passes and moves X to
`$7F00` on pass 25, where ordinary coarse-X culling removes it.

Source review found a distinction between a fresh contact and the native standing
latch: offscreen P2 may retain its standing bit while SolidObjectFull returns no
contact. The candidate reads the owner-specific `hasObjectStandingBit` after its
manual checkpoint, including for the trigger. A retained-latch/no-contact unit
regression covers this; no fallback to a player's unrelated OnObj flag is used.

The existing cold Right plus 20-of-70 jump sequence did not break the first rock
in any of four configurations over 1,800 passes. The vine launched at local
frame264 (`x676,y1690,vx=vy=-3824`); five passes later the player hit the ceiling
at `x603,y1619` and Y velocity became zero. The existing native pilot does the
same at native frames4580–4585 (`x679,y1690` to `x605,y1619,vy0`). This rules out
changing the vine launch to make that route pass. The failed input is not route
coverage. Short explicitly positioned production spots now exercise the placed
rock independently; cold reachability remains open.

The previous push's [CI 34970270333](https://github.com/OpenGGF/OpenGGF/actions/runs/34970270333)
completed 19,127 smoke tests with zero failures, one error and 2,831 skips.
`TestLevelManagerEndProgression.advanceToNextLevelUsesConfiguredSuccessorRedirect`
reached a real S2 load with null ROM because its obsolete no-argument load stub
was bypassed by the native title-card path introduced in `36479a2fc` (already in
the pre-vine base). The test now stubs the actual public load boundary and
asserts the title-card flags as well as successor selection. No production
progression behavior changed. A first compile exposed missing test imports;
after correction, seven rock unit tests and all eight progression tests passed.

The first ROM-focused run executed 92 tests: 88 passed, including all existing
vine routes, ROM mapping/art crawler, renderer guard, registry profile and actual
rewind inventory (1,014 total / 794 passing / 220 graph-covered). Its four
failures were the unsuccessful cold rock approach above; no skips/errors.
The replacement `TestSozSandRockProduction` passed all five positioned production
spots (zero failures/errors/skips, 19.280s build): both acts; Act 1 additionally
640px, S1 donor, and two followers. Each restores all registered state and replays
twice at breakup, phase 6 and phase 24/removal. The failed cold-route experiment
was removed; its evidence above remains an explicit reachability gap.

The positioned `GameplayCaptureTool` capture under external task directory
`$TASK_DIR/engine-rock-1`,
where `$TASK_DIR` is the external `soz-v2-20260915` capture directory. It uses
Act 1, Sonic/Tails, centre `($260,$58C)` and input `45 -; 1 C; 80 -`.
CSV inspected before images: frame 82 rebounds at Y velocity `-768`; frame 40
shows the intact rock and frame 100 the flattened breakup mapping. This is
engine rendering evidence, not a matched native pixel comparison.

Required S3K regressions passed 58 tests, zero failures/errors/skips, 20.340s:
`python3 tools/testing/maven_queue.py -Dmse=off
'-Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils'
"-Ds3k.rom.path=$REPO_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen"
test -B` from this worktree.

The change-based plan against the pinned base selects all 2,597 ordinary classes
plus guards because of shared registration/constants/profile paths. Proportionate
validation applies: the production behavior is confined to this object, its ROM
art/zone bindings, and unchanged shared contact/rewind APIs; the progression repair
changes only a test stub. Focused production, ROM art, inventory, rewind, bootstrap
and structural checks exercise these consumers directly. No shared collision,
physics or load algorithm changed. This is focused validation, not a full-suite
pass. `LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base
77f6ee81dcc62637785e3f65136dcc9d0efdd466 --preflight` passed Java 21, Lua 5.4 and
PowerShell checks. CI smoke remains mandatory.


Focused structural checks passed 123 tests, zero failures/errors/skips, 58.493s
in a fresh JVM:
`python3 tools/testing/maven_queue.py -Dmse=off -Pguards
'-Dtest=TestRewindArchitectureGuard,TestArchitecturalSourceGuard,TestObjectServicesMigrationGuard,TestObjectPhysicsStandardizationGuard,TestObjectUpdateClockTerminologyGuard'
test -B`. Mirrored pitfall files are identical; `git diff --check` passes.
The final source review found no remaining material issues after the standing-latch
correction. Develop remained at the pinned base when refreshed for integration.


### Sand-rock integration

Implementation `9c7d33b55` merged without conflicts into develop as
`8cb81eb29843108a86165ce87d1c3ab73d4d8b4a`. The integrated tracked tree matches
the verified development tree exactly. Unrelated main-workspace files and dirty
disassembly references were preserved. All 30 sand-rock placements now bind to
the implementation, leaving 185/183 unimplemented placements in Acts 1/2.
Full routes and native certification remain open.

Post-integration focused verification uses:

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestSozBreakableSandRock,TestSozSandRockProduction,TestLevelManagerEndProgression' \
  "-Ds3k.rom.path=$REPO_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  "-Dsonic1.rom.path=$REPO_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" test -B
```

Result on integrated `8cb81eb29`: 20 tests passed, zero failures/errors/skips,
19.420s. All five ROM-backed spots executed. Focused validation remains bounded;
the inherited full-guard failures recorded in the spring-vine section were not
rerun or claimed resolved. Documentation checks found 95 valid local link targets,
identical skill mirrors and no whitespace errors.


### CI profile-expectation follow-up

[CI 34974147324](https://github.com/OpenGGF/OpenGGF/actions/runs/34974147324)
on pushed `d3cd963a4` ran 19,137 smoke tests: one failure, zero errors, 2,833
skips (6m11s). The obsolete progression stub failure is resolved. The new failure
is `TestSonic3kObjectProfile.cnzPlacedActorsAreMarkedImplementedForS3klLevelsOnly`:
its old array required `$44` to remain absent from SKL. The registry/profile guard
passed locally, but that separate expectation test was missed by focused selection.
This is a missed test update caused by the new shared `$44` classification.

Follow-up base `d3cd963a4`, isolated `.worktrees/soz-rock-profile`, branch
`bugfix/ai-soz-rock-profile`: remove `$44` from the CNZ-only assertions and add
explicit coverage of both pointer-table owner names and both acts' implemented
profiles for CNZ and SOZ. No production behavior changes. Focused validation covers
`TestSonic3kObjectProfile`, `TestSonic3kObjectProfileRegistryGuard`,
`TestSozBreakableSandRock` and `TestLevelManagerEndProgression`. The existing CI
failure supplies the failing case; no broad rerun is needed to verify a test-only
expectation update. CI smoke still runs on the replacement push.

The follow-up change-based plan selects 300 common/tooling classes plus guards.
This test-only correction uses the proportionate-validation exception and the four
focused classes above; the implementation's completed ROM/rewind/structural checks
remain unchanged. CI individually confirmed seven rock unit tests and eight
progression tests passing, while `TestSozSandRockProduction` was skipped because
CI has no ROM; its five positioned configurations executed locally.

Follow-up focused command (worktree based on `d3cd963a4`):
`python3 tools/testing/maven_queue.py -Dmse=off
'-Dtest=TestSonic3kObjectProfile,TestSonic3kObjectProfileRegistryGuard,TestSozBreakableSandRock,TestLevelManagerEndProgression'
test -B` passed 23 tests, zero failures/errors/skips, 49.714s. The replacement
profile test positively checks each implemented owner; it does not weaken the
remaining CNZ-only assertions.

Follow-up commit `67366458d` integrated without conflicts as `79e20e6bb`.
On that develop commit, `python3 tools/testing/maven_queue.py -Dmse=off
'-Dtest=TestSonic3kObjectProfile,TestSonic3kObjectProfileRegistryGuard' test -B`
passed all eight tests, zero failures/errors/skips, 19.173s. The integrated tracked
tree matches the tested worktree; unrelated local files are preserved.


## Shared native capture host (2026-09-15 continuation)

User-requested extraction of the FBZ host into
`tools/bizhawk/capture_native_references.py`, based on `a27e86f68617f9f96caf801ce646674dbae8c9b6`
in `.worktrees/native-zone-capture`. The common host requires an explicit
exporter, plan and expected ROM identity. FBZ manifest/offset/boundary policy
stays in the compatibility command; repository exporters consume `OGGF_NATIVE_*`.
Legacy external scripts retain aliases through the FBZ command only.

The triggering SOZ probe failed `assert(savestate.load(...))` while movie playback
was active. Removing the assertion did not load SOZ: the next zone assertion
failed. Moving `movie.stop()` before the load produced the correct zone and
observations. The separate intro controller lock remains a positioned-probe
setup issue, not evidence that the rock implementation has native parity.
Do not reuse the failed probe runs as gameplay evidence.

The host now returns failure for logged Lua exceptions even when BizHawk exits 0,
for timeout/process failure, and for missing or empty declared outputs. The
`host.json` acceptance field remains pending independent evidence review.
The rejected approach was treating the emulator process exit as exporter success.

Validation is proportionate: the selector proposed 2,599 ordinary classes plus
guards solely from unclassified scripts/test placement. This change affects only
the diagnostic host/exporter environment and an ordinary test launcher, so Python
host contracts, existing FBZ Python/Lua regressions, their focused JUnit consumers,
and a real BizHawk launch cover the changed paths; no engine-suite pass is claimed.

Observed native smoke capture: `$TASK_DIR/shared-host-1`, common command with
`--require-output observations.csv --require-output native.png --timeout 30`,
explicit `shared-host-smoke.lua`, `shared-host-plan.lua`, verified S3K ROM/complete
BK2 and the prior `vine-1/fbz1-lfc35.State`. Exit 0, no host failures, 1.517 seconds.
CSV contains exactly three SOZ1 (`$0800`) rows at LFC36–38, controller lock 1;
PNG inspected as SOZ entry. This validates capture plumbing, not playable-route
or pushable-rock behavior. Artifacts remain in the external SOZ task directory.

Focused verification before integration:

- `python3 tools/bizhawk/test_native_capture.py`: 12 passed.
- `python3 src/test/resources/bizhawk/fbz_framebuffer_probe_test.py`: 7 passed.
- `lua5.4 tools/bizhawk/test_fbz_boundary_fixture.lua tools/bizhawk/capture_fbz_boundary_fixture.lua`: 8 scenarios passed.
- `LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestNativeReferenceCaptureTool,TestFbzVisualExporterGuard,TestFbzVisualEvidenceToolingContract' test -B`: 7 JUnit tests, zero failures/errors/skips, 50.763 seconds, completed 16:40:06 BST. This includes the host Python suite and existing Lua exporter contract.
- Java 21/Lua 5.4/PowerShell preflight, Python compile, diff whitespace and changed guide-link checks passed.
- Independent read-only review found no actionable correctness or compatibility issues.

Implementation `2b6e4629b` integrated cleanly into `develop` as `da6d5810489574a93eb311f8681e4aa65dfd25ff`,
preserving intervening FBZ chain-art and Tails validation changes. The same focused
JUnit command on that integrated commit passed all 7 tests without failures,
errors or skips (18.529 seconds, 16:42:15 BST). The eight Lua boundary scenarios
and push-policy audit also passed. A native compatibility-command smoke capture
at `$TASK_DIR/legacy-host-1` used the old `OGGF_FBZ_*` variables and fresh-entry
flag: exit 0, no failures, 1.266 seconds; its CSV and PNG match the common-command
capture byte for byte. No engine gameplay or full-suite result is inferred.

### Pushable-rock continuation

Base `342f01cb4dadb5e1ed771270d90f759923825807`; isolated
`.worktrees/soz-pushable-rock`, branch `feature/ai-soz-pushable-rock`.
The next missing placed Act 1 actor after the sand-rock spot is SKL `$3E` at
`($3E0,$5F4)`, subtype 9. Seven Act 1 and three Act 2 placements use this family.
S3KL `$3E` remains the HCZ conveyor belt. Cold traversal to these spots is still
open; the first acceptance target is an independent positioned production push
through the complete authored track, with normal input after setup.

`Obj_SOZPushableRock` is at ROM `$40546`, mapping pointer `$40776`, art base
`ArtTile_SOZMisc+$8C`, palette 2. The single mapping has two 2x3 pieces, tile `$25`,
with a flipped second piece. `SOZRockRideInfo` at `$1E3FD8` contains ROM pointers;
subtype low five bits index them. Subtype 9 reads `$1F6CCA`:
Y `$630`, X `$460`, Y `$652`, X `$4F0`, then `$FFFF`. Runtime track data is read
through the ROM service, never copied from the disassembly as fallback assets.

Source contract: save each player's Status_Push before SolidObjectFull, then read
the object's per-player pushing bits; process P1 first and P2 only if P1 was not
eligible. A shared signed word timer decrements on an eligible pass and moves one
pixel at underflow, reloading four. Shift the pushing player's centre by the same
pixel, preserve its subpixel, play PushBlock and probe the trailing edge at
Y+11 with primary solid bit `$C`, independently of the focused player's path.
FindFloor still honors the global background-collision owner. Floor distance up to 14 snaps; greater distance enters falling. MoveSprite
uses the old velocity before `$38` gravity. Y equality does not finish a fall;
X equality does finish a horizontal ride. Target Y snaps preserve the fraction,
X velocity survives later falls, and a negative next track word stops motion.
The terminal read leaves the native track cursor unchanged. Horizontal sound gates
on `Level_frame_counter & $1F`, not V-int. Falling at/below signed camera-max-Y
plus `$120` writes X `$7F00` and disables carry before ordinary culling.

The existing `SolidObjectProvider.setPlayerPushing` callback models native object
pushing bits, including an offscreen P2 skip which produces no fresh contact.
Using it avoids a new shared collision API. Review caught the PUSH-to-FALL carry
boundary: the checkpoint remembered X before the prior push, but native FALL
saves its d4 after that push. Initial X velocity is zero, so the implementation
suppresses horizontal carry until there is actual track velocity; a live solid
controller rider/pusher regression covers this. No shared collision algorithm
changed. Retained offscreen-P2 rider-baseline behavior remains a shared controller
coverage concern; this slice does not claim it certified.

Subtype `$87` also registers native `_unkF7C4` for SOZPushSwitch's `sub_41AA8` rock
interaction. Door implementation is still missing; that coupled behavior remains
an explicit inherited gap, not a completed door route. The rock's ordinary push
and track semantics apply to the high-bit subtype, with its low-five-bit path.


Validation selection against the pinned base chooses all 2,599 ordinary classes
plus guards through registration/constants and inventory paths. Proportionate
validation applies to this bounded object family: there are no shared collision,
physics, timing-service, public API or build-policy changes. Focused checks cover
its source branch edges, real controller rider/pusher transition, ROM tracks and
art, both acts' positioned production spots, registered-state rewind, inventory,
registry/profile and required S3K bootstrap/loading/AIZ regressions. Structural
checks run in a fresh JVM; CI smoke remains unchanged. This is not a local full-suite
claim. Java 21/Lua 5.4/PowerShell preflight passed with `LUA_BIN=/usr/bin/lua5.4`.


First focused run:
`python3 tools/testing/maven_queue.py -Dmse=off
'-Dtest=TestSozPushableRock,TestSonic3kObjectProfile,TestSonic3kObjectProfileRegistryGuard'
test -B` passed 18 tests, zero failures/errors/skips, 50.040s after queue wait.
This includes ten pushable-rock unit cases and both profile checks, avoiding the
separate profile-expectation omission found in the preceding sand-rock slice.


#### Updated-base verification and probe corrections

The task was preserved and advanced to `98f5d4fc1b9265a8a4857b026e56548a6738fd29`
before final checks. Append-only conflicts in this plan and both pitfall mirrors
retained the shared capture-host record, upstream FBZ art-word lesson and SOZ
lessons. Registry/art changes merged without conflict. The refreshed selector
lists 2,602 ordinary classes plus guards; the bounded-family proportionate scope
above still applies. There are no shared production physics or timing changes.

Rejected test/probe setups:

- Releasing Right after a two-pixel Y change mistook slope following for free fall.
  The first combined run had 230 tests, five production-spot failures and no skips;
  the other 225 passed. The production observer now requires downward motion with
  unchanged X for the first free-fall step. Four Act 1 configurations then passed.
- Holding position in Act 2 lets the longer track leave the ordinary camera cull
  window. Holding Right throughout outruns the rock and resets the level after
  falling into the pit. The final test boards with normal input, brakes, then rides.
  A monotonic level-frame check rejects unnoticed death/reload; complete registered
  rewind is exercised twice at actual boarding as well as the four track boundaries.
- Waiting 1,800 native frames did not clear the entry lock. Source inspection found
  `Obj_LevelIntro_PlayerFallIntoGround` (`$41FF4`), whose `loc_420A6` waits for fresh
  A/B/C and whose `loc_4213C` clears both locks and deletes the intro actor. A one-time
  lock clear was also rejected: the active intro rewrites it. The final probe makes
  the ordinary jump at observation step 46 and sees unlock at step 51, before any
  positioned player setup. This supersedes the earlier unresolved entry-lock note.

Native evidence is now reproducible through
`tools/bizhawk/capture_soz_pushable_rock.lua`. It accepts the verified SOZ1 LFC35
state, completes that intro handshake, sets only P1 position/motion/control once,
then holds Right after 30 neutral frames until the rock enters free fall. It
releases input afterward. These are declared positioned observations, not cold
traversal or full engine/native trajectory parity.

```bash
python3 tools/bizhawk/capture_native_references.py \
  --bizhawk-home "$BIZHAWK_HOME" --rom "$S3K_ROM" \
  --rom-sha1 CFBF98C36C776677290A872547AC47C53D2761D6 --movie "$BK2" \
  --exporter tools/bizhawk/capture_soz_pushable_rock.lua \
  --plan "$TASK_DIR/pushable-plan.lua" \
  --fixture-state "$TASK_DIR/vine-1/fbz1-lfc35.State" \
  --output "$TASK_DIR/native-pushable-10" \
  --require-output pushable.csv --require-output terminal.json --timeout 30
```

The explicit plan is `return {}`; the exporter owns this fixed diagnostic recipe.
The source ROM/BK2/state hashes are recorded in `host.json`; exporter SHA-256 is
`7D064977F55F04D2D5DAAFE08287069FC61ACC9E40AB31366D2F0598926DD3A4`.
The promoted probe reproduced the preceding diagnostic's two CSV files byte for
byte: 785 contiguous object rows, 48 pushes spaced five frames apart, first fall
at frame 295, first ride 318, second fall 398, second ride 417, terminal 542.
The rock stays at `($4F0,$652)` through frame 799 with unchanged terminal cursor
`$1F6CD2`. Exit 0, no host failures, 2.067 seconds. No engine state was hydrated
from these rows. Missing terminal observations produce no `terminal.json`.

Engine visual checks used `InputLogAuthorTool --inline '300 R; 500 -'` and
`GameplayCaptureTool --game s3k --zone soz --act 1 --x 0x3B0 --y 0x5EC
--main sonic --sidekick tails`, the resulting `pushable-input.txt`, and widths
320 and 528. Outputs are `$TASK_DIR/engine-pushable-1` and
`$TASK_DIR/engine-pushable-wide`: each has 800 state rows/PNGs and an MP4, with no
death rows. CSVs were read first; intact frame 100, falling frame 300 and wide
terminal frame 550 were inspected. The existing background/palette differences
remain outside this object-art check. The 320 view does not show the terminal;
the 528 view does. Capture command completed successfully; its inherited Discord
worker shutdown warning did not prevent output or process completion.

Combined focused command (ROM variables are existing absolute paths):

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestSozPushableRock,TestSozPushableRockProduction,TestSolidObjectManager,TestSozObjectInventory,TestRemainingRewindTailInventory,TestSonic3kPlcArtRegistry,TestPatternSpriteRendererCorruptionGuard,TestSonic3kObjectProfile,TestSonic3kObjectProfileRegistryGuard,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
  "-Ds3k.rom.path=$S3K_ROM" "-Dsonic1.rom.path=$S1_ROM" test -B
```

248 tests passed, zero failures/errors/skips, 23.887 seconds at 16:58:10 BST.
After adding the actual-boarding rewind checkpoint, the production class alone
passed all five configurations again (20.119 seconds, 17:02:19 BST). The remaining
unchanged checks were not repeated for that test-only addition.

Separate structural command:
`python3 tools/testing/maven_queue.py -Dmse=off -Pguards
'-Dtest=TestRewindArchitectureGuard,TestArchitecturalSourceGuard,TestObjectServicesMigrationGuard,TestObjectPhysicsStandardizationGuard,TestObjectUpdateClockTerminologyGuard'
test -B`: 123 passed, zero failures/errors/skips, 59.284 seconds at 17:00:01 BST.
The rewind inventory is 1,015 total / 795 passed / 220 graph-covered / no failure
buckets. This is focused validation, not a full ordinary-suite or full-guard pass.


Final review found no material movement, terminal, culling, native-setup or
rewind defect. Its validation suggestion was applied: the Act 2 terminal now
asserts the current riding relation, not the sticky “ever boarded” flag. All
five production configurations passed again with that assertion (48.772 seconds,
17:05:31 BST, zero failures/errors/skips). Updated-base preflight, mirror equality,
Lua syntax, changed-link and whitespace checks passed.

Implementation `58555c17a` integrated cleanly as
`c646e19fdd392ba83702e9449ea7ffd4eed783e0`. The combined focused command above
passed on that actual `develop` commit: 248 tests, zero failures/errors/skips,
53.555 seconds, completed 17:09:21 BST. The updated-base candidate had already
passed the 123 separate structural guards; no structural code changed during
integration. Push-policy validation also passed. Full SOZ routes, all-character
breadth, native trajectory/pixel certification and the subtype `$87` door link
remain open; this delivery covers the bounded rock family and recorded spots.

## Two-family continuation: loop fall-through and static solids

Requested batch: two further SOZ tasks before the next delivery report. Task
worktree `feature/ai-soz-route-controllers`; actual initial base
`d92fea6f15ba0f90f9df15aab2d8c19763ded87f`. The sprite-publication audit merged
between the preceding SOZ delivery and worktree creation; it is documentation-only
and retained unchanged. Previous SOZ CI run `34993361968` completed successfully.

Source owners: `Obj_SOZLoopFallthrough` (`$4044A`, `sub_40474`, `loc_40508`) and
`Obj_SOZSolidSprites` (`$41F44`, `loc_41F90`, `loc_41FAC`). See the placed inventory
for exact widths, signedness, ownership and release contracts. The two SKL factory
slots preserve HCZWaterWall/CNZGiantWheel in S3KL. The profile now reports both
implemented meanings. Runtime mappings remain ROM-backed: `$41FC8`, two frames,
three/two 4×2 pieces, terrain base 1 and palette 2. No shared physics, scheduler,
public API or loading algorithm changed.

The change selector on the actual base proposes 2,605 ordinary classes plus
structural guards because central constants/art/profile files trigger its full
fallback. The scope is two bounded actors and their registrations, with direct
production and branch-edge coverage and no unresolved cross-cutting code change.
Use proportionate focused validation plus separate relevant structural guards;
this is not a full-suite pass. Preflight passed with `LUA_BIN=/usr/bin/lua5.4`
(Java 21 and PowerShell also present); default Lua was rejected before tests.

Implementation/test setup corrections preserved as evidence:

- First compilation used a nonexistent `NativePositionOps.addYPos16_16`. The
  actor now computes native Y locally and writes through the existing preserving
  helper plus fraction setter; no new shared API was needed.
- First behavioral run: 22 tests, five failures, zero errors/skips. One ROM art
  assertion read the instruction rather than its immediate word (correct `$41F4E`).
  Two Sonic capture snapshots preceded native shield registration; three neutral
  initialization passes before positioning remove that unrelated restore hazard.
  Two Act 2 solid approaches from 85 pixels above encountered neighboring terrain.
- Final positioned landing recipe starts two pixels above the intended surface,
  after initialization. Act 2 pillar moved from the covered `($A90,$628)` to the
  upper pillar `($18F0,$218)`; the ledge remains `($A82,$608)`. Act 1 uses
  `($2190,$118)` and `($2198,$138)`, approaching the latter's exposed right side.
  These checks deliberately do not claim ordinary route reachability.
- The next run passed all 22 actor/production/inventory checks. Only the expected
  rewind inventory count failed: observed 1,017 total / 797 passed / 220 graph
  covered, no failure buckets. Updated the baseline after that observation.
- First combined run: 253 tests, one failure, zero errors/skips. An older profile
  test assumed CNZ `$49` could never be implemented in SKL. Replace that exclusion
  with explicit checks of both owner names and shared implementation membership.

Independent source review found no confirmed correctness defect. Its suggestions
added actual camera-width assertion to the loop spots and real-manager side and
underside tests for both solid shapes. Rendering readiness is checked separately.
Local loop tests cover acquisition-return, unsigned region edges, signed speed,
existing ownership/routine gates, literal radii, fixed-point fractions, equality
release, bit-7-masked extent and independent follower state through recreation.
Production spots restore and replay the whole registered world twice at landing,
loop acquisition, held movement and release. Cold routes, maximum/duplicate team
breadth, all donor/width combinations and matched native trajectories remain open.

Native corroboration used the common host with the existing SOZ2 LFC35 state
`$TASK_DIR/pilot-4/fbz2-lfc35.State` (SHA-256
`A7CF91BB8EE8EE93D3B51E4E3C0271F549FCDE12D7BB93916E9331364F963074`), same verified
ROM/BK2 and empty plan as above, and a one-off `$TASK_DIR/native-loop.lua` exporter.
This is a positioned diagnostic, not a controller-only acquisition oracle: nearby
native owners also execute. The accepted run is `$TASK_DIR/native-loop-3`:
exit 0, no host failures, 1.817 seconds, exporter SHA-256
`FC502F55B3259178FEE63CAAE4046170A2757364BEE24364EF12824CAFE5F0DE`.
It contains 34 contiguous LFC335–368 observations at X2896, owner SST45870.
`object_control=$81` and Status_OnObj remain through Y839; the next sample Y859
clears both, crossing the source threshold Y848. Held velocity rises by `$38`
per pass. The initial observed velocity is `$D00`, so this is not a matched
engine/native entry-velocity or full-trajectory comparison. Release PNG inspected.

Two rejected probe setups are explicit: `native-loop-1` gave the camera/loader
only 30 passes and never observed the target owner; `native-loop-2` repeatedly
positioned inside the capture region and rewrote player control while the owner
retained its held bit. Both failed the required `released.json` output. Final
setup holds P1 above the capture region for 300 passes to allow streaming, then
writes the target position/motion once and observes neutral input. The writes
are to P1 position, speed, ground speed, status, control and animation only.
No native observations enter engine state.

Engine rendering: queued `GameplayCaptureTool --game s3k --zone soz --act 1
--x 0x2190 --y 0xEB --width 320 --main sonic --sidekick tails --frames 120
--stills 10,60 --out-dir "$TASK_DIR/engine-solid-sprites-1"`, via `exec:java`.
120 PNGs, state CSV and MP4, exit 0, 16.950 seconds. All rows show stable support
at centre `(8592,236)`, no death/hurt. CSV read before inspecting frame 60:
Sonic and the mapped pillar are visible at native width. Existing background/
palette differences remain outside this local art check. Inherited Discord
worker shutdown warning did not prevent output or process completion.

The 123 selected structural guards passed with zero failures/errors/skips,
61 seconds, completed 17:32:54 BST. Subsequent source edit only corrected the
loop owner's address comment to verified `$4044A`; the held routine is `$4045E`.
The combined 255-test run passed all but the new side/underside spot: the
hand-built actor had not survived its first display pass and retained the native
first-frame solid gate. The fixture now supplies camera bounds and the preceding
position snapshot, matching production's already-passing landing lifecycle.

The isolated side/underside test now passes (17.639 seconds, 17:40:56 BST).
Its first underside point was outside the shared native box because it omitted
SolidObjectFull's four-pixel vertical bias; moving the probe inside that box
exercised the intended bottom branch. This changed test geometry only.

Before final checks the worktree advanced cleanly to updated destination
`0c38edf5d433e3a5670be2b594f7362fe7d7d4b1`, preserving upstream S1 SMPS physical
compatibility and S2 title-star timing fixes. No conflicts. Updated-base preflight
passed; the unchanged scope decision applies to its 2,606-class selector.

Combined focused delivery command:

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestSozLoopFallthrough,TestSozSolidSprites,TestSozRouteControllersProduction,TestSozObjectInventory,TestSolidObjectManager,TestRemainingRewindTailInventory,TestSonic3kPlcArtRegistry,TestPatternSpriteRendererCorruptionGuard,TestSonic3kObjectProfile,TestSonic3kObjectProfileRegistryGuard,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
  "-Ds3k.rom.path=$S3K_ROM" "-Dsonic1.rom.path=$S1_ROM" test -B
```

Separate structural command (completed above):

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Pguards \
  '-Dtest=TestRewindArchitectureGuard,TestArchitecturalSourceGuard,TestObjectServicesMigrationGuard,TestObjectPhysicsStandardizationGuard,TestObjectUpdateClockTerminologyGuard' test -B
```

Updated-base candidate result: **255 tests passed, zero failures/errors/skips**,
54.556 seconds, completed 17:42:48 BST. This includes all 11 production cases,
both-shape side/underside checks, explicit donor capability and camera-width
assertions, object/art/profile guards, unchanged S3K loading gates and the verified
1,017/797/220 rewind inventory. Changed documentation links, AGENTS mirror and
whitespace checks passed. No category-run diagnostics were created. These are
focused checks, not full-suite or full-zone certification.

Implementation `8aa71f8f6` integrated without conflict as
`0973cba4ec4258017df2aedcd232e3b422c9f95c`. The same combined focused command
passed on that actual `develop` commit: **255 tests, zero failures/errors/skips**,
51.979 seconds, completed 17:45:22 BST. No implementation changes occurred during
integration; the separate 123 structural checks remain applicable. The native
probe and engine image are bounded corroboration/visual checks as described above.

## Connected mechanisms batch

User requested a larger chunk. Worktree `soz-mechanisms`, branch
`feature/ai-soz-mechanisms`, starts at `d9de72b7a314bdd6e4157eb4db2ef638e2669d96`.
Fetch/fast-forward found the base current; preceding push CI `34997088728` passed.
Scope: floating pillars `$42`, push switches `$45`, doors `$46`, and the existing
special-rock `$87` link. This adds 135 concrete placements and targets a connected
puzzle, with independent short pillar/hazard checks. Whole-act completion remains
outside this batch's claim.

The new SOZ runtime state stores only `_unkF7C4`'s object-slot identity. Signals
remain in the existing, rewind-registered `Level_trigger_array`; no shadow array,
trace hydration or new shared collision algorithm is introduced. A switch checks
the current occupant/routine of the published slot, not the nearest rock. Pillar
shape bytes and all three mapping tables are loaded from the locked-on ROM.

Independent source review caught the pillar's damage classification (native
`HurtCharacter` selects normal sound for routine `$411D8`) and the need for
owner-local airborne stale-standing consumption. Both corrections use existing
engine contracts. An initially proposed unflipped-switch decay quirk was rejected:
ROM `$41A5A` contains `66 D8`, branching to `loc_41A34` and setting d5. It does
**not** fall through into decay on non-displacement pushes. The offscreen transition
at `loc_4197E` really does fall through into retained decay in the same pass.

Native corroboration uses the common capture host, BizHawk 2.11, locked-on ROM
SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6`, complete Sonic/Tails BK2
SHA-256 `82EABFBC65E33C160CE209BAA1CA3F967CB677FE22350BC100625D8C41A8E1BF`,
and the previously recorded SOZ2 LFC35 save. External output:
`$TASK_ROOT/native-mechanisms-2`.
The diagnostic holds a declared positioned entry `($2600,$1A4)` while camera
streaming catches up, confirms door `$268C` is loaded, then uses ordinary right
and jump input. No reference state is supplied to the engine.

The host completed with no reported failures. CSV has 600 contiguous frames,
LFC420–1019. At frame0 the switch is X9776, signal0 and doorY448. At frame150
signal114, switchX9804 and doorY562; after charging to120 the jump crosses the
switch and door, with player X10081/Y428 at frame300. The inactive second switch
on channel8 also decays the byte; net charge growth is below one per pass.
Frame150 PNG inspected as the expected switch scene. These are source
corroboration and route observations, not matched engine trajectories or pixels.
The earlier `native-mechanisms-1` probe did not wait for target placement loading,
so its initial entry cannot anchor a matched comparison; the corrected probe
checks the owning door before starting observations.

The initial compile/old rock and
inventory regression completed 20 tests with no failures/errors/skips. Initial
isolated switch/door tests completed six with no failures/errors/skips; later
edge-case and production additions supersede that limited scope. An initial
production entry at `($2500,$170)` stalled before reaching a switch; it is not
passing route evidence. The connected test now uses the explicitly observed
second switch and its downstream door.

Scope decision: the unchanged change-based selector chooses 2,609 ordinary
classes plus guards because zone registration, art keys and discovery profiles
fall back to the full suite. At the documented normalization cost that is roughly
24 minutes ordinary plus 10 minutes guards, excluding queueing. Proportionate
validation applies here: changes are confined to the new SOZ owners and their
registrations, with no shared physics, collision algorithm, timing-port or public
Mod API change. The existing trigger and runtime snapshot contracts are reused.
Combined focused validation will cover SOZ interactions/production routes,
shared solid consumers, runtime adapters, the ROM art crawler/corruption guard,
profile bindings, rewind inventory and required AIZ/bootstrap/load/decoder gates;
a separate JVM covers the applicable source/rewind/services/physics/clock guards.
This is focused validation, not a full ordinary-suite pass. Java21/Lua5.4/PowerShell
preflight passed on the task tree; no tests are implied by preflight.

Additional rewind review found that `ensureZoneRuntimeStateInstalled()` needs to
recognize SOZ's self-owned state, just as LBZ does. Without that case, restore
reconciliation could replace a nonempty rock link with a new default state.
The candidate now preserves it on reconciliation and clears it on fresh act
installation; both paths have a focused regression. Deleted rocks are rejected
explicitly even if the manager's active-object cache still contains their instance.

The first Act 1 pillar spot `($5A0,$660)` died at the first observed engine pass:
player `(1440,1580)`, pillar `(1440,1664)`, no acquired support. Native
`native-pillar-2` normalizes a live routine 2 immediately before observation and
also enters routine 6 on the first pass at this constrained ceiling/hazard site.
Its different oscillator phase means this is corroboration of an unsafe landing
spot, not matched trajectory evidence. `native-pillar-1` did not normalize the
actor routine after warmup and cannot establish the cause. The production ride
spot moves to the separate placed pillar `($1380,$560)`; neither collision nor
oscillator behavior is changed to make a positioned fixture survive.

Placement inspection resolves the pillar fixture deaths: both `($5A0,$660)` and
`($1380,$560)` have `$6B` invisible hurt blocks directly above them, at Y`$5F0`
and `$4F0`, respectively. The ordinary ride spot is now `($8E0,$670)`, with no
such nearby placement. Setup also starts airborne above the maximum pillar
excursion and checks survival before the controlled landing, avoiding an already
dead actor being mistaken for a failed landing.

The special-rock positive-route attempt was rejected rather than used to change
physics: from the fresh positioned entry, the rock falls onto ROM track 7
`$5EC,$47F0,$FFFF` before reaching switch `$4830`. Positive local contact remains
covered, while the production spot verifies slot publication and invalidation on
fall with rewind. Rewinding the very first bootstrap pass exposed an unattributed
`instaShieldRegistered` false→true restore boundary; the interaction spot settles
three production frames first. The new SOZ runtime reconciliation regression
independently verifies preservation of a nonempty slot and clearing on fresh act
installation. Full positive special-rock puzzle reachability stays in known
discrepancies. The native attempt `native-rock-switch-2` publishes slot46092 and
routine `$405D6`, but the starting badnik kills P1 before contact and reloads the
level, so it supplies no positive-coupling or route evidence. The first attempt
waited for a downstream door outside the initial loading window and correctly
failed its required-output check.

`native-mechanisms-2` was additionally checked across all 600 rows: switch X is
exactly `9776 + (signal >> 2)`, door Y is exactly `448 + signal`, and the largest
single-pass door displacement is one pixel. First recorded passage past X`$26B0`
is frame251/LFC671, player `(9905,428)`, signal102.

Engine capture `engine-mechanisms-1` uses native width320 and the round-trip-checked
input `200 R; 20 R+A; 180 R; 20 R+A; 80 R`. `InputLogAuthorTool` wrote 500 frames;
`GameplayCaptureTool` completed 500 PNGs, state CSV and MP4 in 20.054 seconds at
18:49:13 BST. CSV was read before inspecting still240: the jump clears the switch,
with the downstream passage open. Frame300 is `(9932,428)` beyond the door;
frame499 is `(10357,492)` with 23 rings, no hurt or death. This is a functioning
rendered local route, not matched native pixels or whole-act completion. The
inherited Discord worker shutdown warning did not prevent process completion.

The final short run completed at 19:04:57 BST: 30 tests, zero failures/errors/skips
(53.220 seconds including compilation), covering all nine production cases, ten
switch/door cases and eleven runtime-adapter cases. The final Act 1 pillar at
`($8E0,$670)` lands and carries correctly; the initial sites' invisible hurt blocks
remain intact. Participant slots are now bound in query order before collision
callbacks can bind a follower first, with a follower-only retained-push rewind
regression.

Before combined delivery validation, the worktree fast-forwarded to actual
`develop` base `3808306ad98b5b5b5774b35086b8759b538667b0`. Git's task-local
autostash reapplied cleanly, preserving all new files. Incoming changes include
sprite/shield publication banks and KiS2 capability/boss-rebound parity; no
conflicts or upstream edits were discarded. Updated-base preflight passed.
The selector now contains 2,612 ordinary classes; the same bounded-impact
proportionate-validation decision applies. The final check covers these 20
relevant classes, with explicit existing ROM paths:

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestSozMechanisms,TestSozPillarAndRockSwitch,TestSozMechanismsProduction,TestSozObjectInventory,TestSozPushableRock,TestSozPushableRockProduction,TestSozLoopFallthrough,TestSozSolidSprites,TestSozRouteControllersProduction,TestSolidObjectManager,TestRemainingRewindTailInventory,TestSonic3kPlcArtRegistry,TestPatternSpriteRendererCorruptionGuard,TestSonic3kObjectProfile,TestSonic3kObjectProfileRegistryGuard,TestS3kZoneRuntimeStateAdapters,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
  "-Ds3k.rom.path=$S3K_ROM" "-Dsonic1.rom.path=$S1_ROM" test -B
python3 tools/testing/maven_queue.py -Dmse=off -Pguards \
  '-Dtest=TestRewindArchitectureGuard,TestArchitecturalSourceGuard,TestObjectServicesMigrationGuard,TestObjectPhysicsStandardizationGuard,TestObjectUpdateClockTerminologyGuard' test -B
```

The combined candidate run on base `3808306ad` completed at 19:08:09 BST in
1:01: 305 tests, one failure, no errors or skips. The sole failure was the
rewind inventory count pin: all three new object classes passed the isolated
round-trip sweep, increasing total/passed from 1017/797 to 1020/800 while the
220 graph-covered cases and every failure bucket stayed unchanged. Both the
Java pin and readable inventory summary were updated; the latter also lagged
the incoming base by two classes. All other 304 checks, including the nine
production cases and required AIZ/load gates, passed. A narrow inventory
recheck follows this bookkeeping-only correction.

The structural run completed at 19:20:24 BST: 123 checks, one failure, no
errors/skips. `productionObjectLifecycleRawCallCountsDoNotGrow` found 583 raw
destruction calls against a 582 budget, including three new retained-switch
expiration sites. Those three now use `ObjectLifetimeOps.expireDynamic(this)`;
the already-released placement stays released, and replacement/zero-charge
expiration retains the same lifetime behavior. The other 122 guard checks passed.
The affected switch/production/inventory tests and lifecycle guard are rerun
narrowly before integration; no unrelated lifecycle calls or budgets are changed.

The inventory-only recheck produced a fresh passing report at 19:23:53 BST
(one test, no failures/errors/skips). The lifecycle guard recheck completed
at 19:24:14 BST in 19.705 seconds: all 33 checks passed, no errors/skips.
Together with the unchanged guard results, all 123 selected structural checks
now pass; this is the selected guard scope, not the complete guard profile.

Final affected-behavior recheck completed at 19:24:38 BST in 23.525 seconds:
`TestSozMechanisms,TestSozMechanismsProduction,TestRemainingRewindTailInventory`,
20 tests, zero failures/errors/skips, with both explicit ROM paths above.
This verifies the final lifecycle helper edit, connected route/rewind cases and
corrected inventory pin before integration.

Delivery: source commit `6c8dad550`, integrated cleanly into `develop` as
`23a51f28e` on base `3808306ad`. The identical 20-class combined command above
completed on the integrated main workspace at 19:27:26 BST in 56.344 seconds:
**305 tests, zero failures, errors or skips**. This includes the corrected
inventory pin and final lifetime-helper calls. The selected structural checks
passed as recorded above; no full-suite or complete-act certification is implied.
CI push policy and release-tree audit passed before delivery. No trace frontier
changed. The full special-rock puzzle approach and inherited act-matrix gaps
remain open.

### Push-switch overlap correction

User visual review found the moving body behind its fixed track. Native
`Obj_SOZPushSwitch` uses main mapping frame 1 and child frame 0; the multi-sprite
builder submits the main before `loc_1B46A` processes children, giving the main
earlier SAT precedence. The engine uses painter order, so the two frame calls
must be reversed: track first, moving body last. This corrects the original
`6c8dad550` rendering without changing geometry, movement or rewind state.

The branch starts at `5002e9e8c`. The change selector proposes 2,151 ordinary
classes plus guards. Proportionate validation uses the six classes
`TestSozMechanisms,TestSozMechanismsProduction,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`
with the explicit S3K and S1 ROM properties from the preceding batch, and a fresh
`GameplayCaptureTool` run with the same 500-frame input and positioned entry.
Only object-local drawing order changed; shared rendering and gameplay algorithms
are untouched. No new test mirrors the two-call implementation; the real rendered
overlap is the visual acceptance check.

Worktree validation completed at 19:37:20 BST: 77 tests, zero failures/errors/skips
(49.225 seconds). Capture `engine-mechanisms-layer-fix` completed 500 frames at
19:37:40 BST. All 500 CSV state rows equal `engine-mechanisms-1`; inspected
still150 shows the body covering the track instead of being covered by it.
The capture's inherited Discord shutdown warning did not prevent completion.

Correction source `d5c8e463e` merged cleanly as `a2cac9c1e`. The same focused
command passed on integrated `develop` at 19:39:08 BST: 77 tests, zero failures,
errors or skips (53.426 seconds). Push policy and release-tree audit passed.

### Presentation planning correction

User review required explicit parallax and animated-tile work in the designs and
plans. Updated the methodology design, slice-level work above, research arithmetic
and both act matrices. Source audit checked `sub_55D56`, `loc_55DF2`,
`MakeFGDeformArray`, `ApplyFGandBGDeformation` and `AnimateTiles_SOZ1/SOZ2`.
Rejected the catalogue's 112-line and three-tile DMA claims: `$DF` loop extent
produces 224 scanlines, and DMA d3 is a word count. The torch routine consumes
the old frame byte before increment/reset, yielding frames 0,1,2, not two frames.
No runtime code or effect was delivered in this documentation correction.

## Normal Act 1 desert presentation implementation

Task base `2843b657430dedfba57484aa1130dcaed79d2597`, worktree
`.worktrees/soz-desert`, branch `feature/ai-soz-desert`. Implements the revised
next batch's normal desert presentation; arena/redraw/sand modes and all Act 2
event-selected presentation remain open. No new placed-object family is claimed.

`SwScrlSoz` now handles Act 1: ROM wave `$5077E`, bands `$560DC`, signed camera
Y/16 and seven 16.16 X tiers (base X/16, constant X/64 increment). Normal
`loc_55DF2` has independent FG/BG wave phases and writes all 224 lines. The
handler has no logical accumulator: camera and `Level_frame_counter` reconstruct
its output. Act 2 retains the existing fallback until its event owner is delivered.

`AnimateTiles_SOZ1` now writes all six secondary tiles (`$60` DMA words = `$C0`
bytes), extending graph ownership through `$341`. Its phase uses the current
camera's `sub_55D56` calculation instead of a potentially stale presentation-pass
BG copy. The existing late-arena camera-lock compatibility bridge is unchanged
and remains to be replaced by the actual arena owner. The newly registered
`AnPal_SOZ1` consumes four ROM colors before incrementing its offset, every six
passes; timer and offset use the existing palette-cycle rewind codec and palette
ownership path. The zero-based destination is palette 2, colors 12–15.

### Regressions, consumer check and review

- Initial `TestS3kSozPatternAnimation`: five tests, two failures, no errors/skips,
  19:57:30 BST (46.796 seconds). Missing dedicated scroll returned the sentinel
  BG X; secondary phase0 pixel192 read7 instead of ROM11.
- After scroll/DMA implementation, six tests had only the new palette failure:
  pass0 expected `$2CE`, observed `$06C`, 19:59:52 BST (48.418 seconds).
- With palette registration, the eight-name selection below passed 81 tests,
  no failures/errors/skips, 20:01:25 BST (52.719 seconds). It includes actual
  quicksand/vine and mechanism routes with world rewind and compatibility cases.
- Consumer selection completed 234 tests with eight errors, no failures/skips,
  20:03:14 BST (21.741 seconds). All eight were provider-routing tests for other
  zones using an unopened ROM. Eager SOZ table reads caused the errors. SOZ now
  reads its tables on first Act 1 init/update and fails explicitly there if data
  is unavailable; no fallback asset table was added. All other 226 checks passed.
- The narrow scroll/SOZ recheck passed 99 tests, no failures/errors/skips, at
  20:05:48 BST (50.358 seconds). Existing rewind inventory needed no pin change.
- Read-only source review found no actionable issue in arithmetic, DMA, palette
  cadence/rewind or the final lazy-loading change.

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestS3kSozPatternAnimation,TestSozAct1QuicksandRoute,TestSozAct1SpringVineRoute,TestSozMechanismsProduction,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
  "-Ds3k.rom.path=$S3K_ROM" "-Dsonic1.rom.path=$S1_ROM" test -B
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestS3k*PatternAnimation,TestS3k*PaletteCycling,com.openggf.game.sonic3k.scroll.*Test,TestPaletteCycleStateCodec,TestRemainingRewindTailInventory,TestAnimatedTileChannelGraph*,TestPatternSpriteRendererCorruptionGuard' \
  "-Ds3k.rom.path=$S3K_ROM" test -B
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=com.openggf.game.sonic3k.scroll.*Test,TestS3kSozPatternAnimation' \
  "-Ds3k.rom.path=$S3K_ROM" test -B
```

Proportionate validation: the unmodified selector chooses 2,612 ordinary classes
plus guards because shared owner/registration files trigger its fallback, around
24 minutes ordinary +10 minutes guards at prior normalization cost. This slice
adds SOZ-local arithmetic/palette behavior and adjusts only SOZ DMA ownership;
shared algorithms, timing ports and public contracts are unchanged. The explicit
production tests, all S3K scroll/animation/palette consumer tests, graph/rewind
checks, rendered captures and relevant structural guards address those bounded
risks. This is focused validation, not a full-suite pass. Java21/Lua5.4/PowerShell
preflight passed. No category run directory was produced.

### Native and rendered observations

External task root remains `$TASK_ROOT` (`soz-v2-20260915`). Native host command
uses `capture_native_references.py`, official BizHawk2.11/GPGX, locked-on ROM
SHA1 `CFBF98C36C776677290A872547AC47C53D2761D6`, the preceding complete Sonic/Tails
BK2, and `quicksand-1/fbz1-lfc35.State` (SHA256
`42FBA067A592A5B7E3B45D001F30C553E0FFEC5083DF0967F5929C574CA0BEBA`).
Exporter `native-desert.lua`, empty plan, output `native-desert-1`, required
`desert.csv`, timeout45. It stops playback before loading the save, verifies
SOZ1, then holds right with jump during the first20 of each80 passes; no RAM
setup writes. Exporter SHA256
`C6FB9A5EB28FBD8DA45B46AC6BDBF02325472E1E7198200771F131F83AABAF74`.

Host completed with no failures in 2.168 seconds. Independently inspected600
contiguous rows, LFC36–635: **134,400 packed scanline words match** the ROM-derived
rational band calculation with actual LFC, no offset. Both BG camera coordinates
and all600 animated phase bytes match. Camera advances X32→418, Y1033→1517;
100 palette transitions occur. A trial LFC−1 calculation immediately mismatched
row0/line0, so no fitted phase correction was introduced. Native image300 inspected.
This corroborates normal-desert fields; it supplies no engine gameplay values.

`InputLogAuthorTool` authored600 frames, repeating20 right+jump then60 right
(the final cycle is truncated). `GameplayCaptureTool --game s3k --zone soz
--act 1 --main sonic --sidekick tails --input $TASK_ROOT/desert-input.txt`
produced `engine-desert-1` at width320 (600frames,20.152 seconds) and
`engine-desert-wide-528` at width528 (240frames,18.261 seconds). Both completed
with the inherited Discord shutdown warning only. CSV preceded image inspection:
standard frame300 player `(692,1660)`, camera `(533,1564)`, no hurt/death;
wide frame150 camera `(324,1551)`, player `(567,1639)`, no hurt/death. Desert
bands and art are visible. The initial inspection missed purple flame tiles and
an edge seam subsequently reported by the user; the correction is recorded below.
The initial wide640 capture request was rejected before rendering because the
tool supports preset widths; 528 replaced it without changing engine behavior.

The engine and native entries do not have matching camera/phase trajectories, so
these captures are visual inspection and numeric source corroboration, not matched
pixel certification. The standard scripted route stalls at X692 later in the clip;
it is not full traversal evidence. Arena/transition, Act 2 presentation and broader
phase/viewport/native pixel obligations remain open in the act matrices.

Selected structural validation completed at 20:06:52 BST in58.388 seconds:
`-Pguards -Dtest=TestRewindArchitectureGuard,TestArchitecturalSourceGuard,TestObjectServicesMigrationGuard,TestObjectPhysicsStandardizationGuard,TestObjectUpdateClockTerminologyGuard`,
123 tests, zero failures/errors/skips. Post-integration verification uses the union
of the two ordinary selections above, so the final tree is checked once together.

Delivery: source `ce016a0fc`, clean merge into `develop` at `58d4c4965` on the
unchanged base `2843b6574`. Combined post-integration selection (union above)
completed at 20:10:19 BST in55.728 seconds: **309 tests, zero failures, errors or
skips**. All eight prior provider-routing errors are resolved; selected structural
checks passed as recorded above. Documentation link/whitespace checks and CI
push policy passed. No full-suite, complete-act or matched-pixel claim is made.


## Desert visual artifact correction

Follow-up to `ce016a0fc` / `392dd73ed`, in `.worktrees/soz-background-fix` on
`bugfix/ai-soz-background`, based on `392dd73edf439e976005fe142d4c4c9a2ffe9bf9`.
The user identified purple flames and a right-edge seam in the desert capture.

- **Static art ownership:** both SOZ custom animation routines return directly,
  despite the `Offs_AniPLC` pointer naming `AniPLC_LRZ1`. Remove that unexecuted
  list from SOZ loading/registration; preserve real LRZ registration. The wrong
  list writes eight static SOZ tiles at `$350..$357`. A regression that only
  called `animator.update()` initially missed the mutation because writes publish
  at VBlank; adding the publication boundary reproduces the overwrite on pass6
  (first pixel expected4, actual5). Both acts must preserve this range through
  two full script cycles. The custom SOZ1 `$330..$341` transfers remain active.
- **Repeat period:** `LevelRenderer` expanded the background FBO width from its
  512-pixel plane period to `max(viewport, period)`. That same width reaches the
  compositor's modulo, so width528 repeated a duplicate16-pixel strip before
  wrapping. Render the existing plane period. Paths that apply horizontal scroll
  in the tile pass already select viewport width and retain that behavior.
  The production GL capture regression reproduced expected512 / actual528.
- The earlier scroll-word/native-state arithmetic checks were valid but did not
  check GPU art ownership or the compositor's repeat width. Corrected the
  catalogue's false shared-AniPLC dependency; numerical scroll parity is not
  native pixel certification.

Validation uses the repository's proportionate scope exception. The selector
falls back to all2613 ordinary classes because these files are shared/unclassified.
The actual changes remove one zone's unused registrations and pass the existing
background period through unchanged; no shader math, physics, timing, public
contract or cache lifetime changes. Direct GPU checks cover every supported
viewport, and existing graphics/per-line tests cover the shared consumers.
The full fallback would include unrelated audio/network/mod suites; focused
verification below is not a full-suite pass. Tool preflight passed with Java21,
`LUA_BIN=/usr/bin/lua5.4` and PowerShell (the default `lua` was the wrong version).

Commands use `python3 tools/testing/maven_queue.py -Dmse=off`, explicit
`-Ds3k.rom.path="$S3K_ROM"`, and S1 ROM where applicable:

- Animation/render/route selection:
  `-Dopenggf.test.gl.native=true
  -Dtest=TestS3k*PatternAnimation,TestS3k*PaletteCycling,com.openggf.game.sonic3k.scroll.*Test,TestAnimatedTileChannelGraph*,com.openggf.graphics.*Test,TestLevelRenderer*,TestBackgroundScroll,TestParallaxRewindSnapshot,TestSozBackgroundCapture,TestGameplayCaptureSmoke,TestSozAct1QuicksandRoute,TestSozAct1SpringVineRoute,TestSozMechanismsProduction,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils test -B`:
  355 tests, zero failures/errors,10 skips,28.900 seconds at20:26:59BST.
  The skips are eight opt-in visual baselines and two opt-in background diagnostics.
- Graphics prefix classes require `com.openggf.graphics.Test*` as well as the
  suffix pattern above. With `TestShaderPixelCentreSampling` explicitly selected
  and native GL enabled:205 tests, zero failures/errors,2 skips,19.065 seconds
  at20:28:27BST. Skips are property-gated scroll-upload and slot-window native
  diagnostics. The shader pixel-centre test executed successfully; its earlier
  isolated default-context attempt had skipped.
- Structural selection:
  `-Pguards -Dtest=TestRewindArchitectureGuard,TestArchitecturalSourceGuard,TestObjectServicesMigrationGuard,TestObjectPhysicsStandardizationGuard,TestObjectUpdateClockTerminologyGuard test -B`:
  123 tests, zero failures/errors/skips,58.610 seconds at20:29:26BST.
- Added a direct pixel regression after review: ordinary20 right+jump then41
  right frames exposes the sky at camera `(96,1270)`. The right-edge rectangle
  must contain sky pixels, not the previous black strip. Separate period checks
  cover320/352/400/528/800. Initial art priming and all event-selected backgrounds
  are not independently native-pixel-certified by this test.

Captures repeat the earlier `desert-input.txt` command, with output
`engine-desert-fixed-528` (240 frames) and `engine-desert-fixed-320` (600 frames).
Both complete successfully; inherited Discord shutdown warning remains.
The complete before/after state CSVs are byte-identical for each width.
At wide frame60 the previous black strip at the far right and the purple shapes
below the horizon are gone; frame150 and native-width frame300 also show intact
sand art. Compared against the existing native desert reference for appearance,
not an aligned native pixel trajectory. The route still stalls later atX692.

Independent read-only review found no production defect. It noted missing pixel
assertions (added above), post-boot-only static-art sampling, and no new tests for
all event-specific per-line overrides. Source inspection confirms those overrides
still select viewport width; their broader native certification remains open.
The final pixel/viewport selection passed6 tests with no failures/errors/skips
at20:30:48BST (19.304 seconds). Post-integration result is recorded below.


Delivery: source `98cecce2f`, clean merge into the unchanged `develop` base at
`096b6249c6b04e3fd3e4b0320d4cffaa2b5b4c42`. Combined post-integration ordinary
selection is the union of the two selections above (including the final pixel
regression):561 tests,549 passed, zero failures/errors,12 opt-in skips,
58.312 seconds at20:34:06BST. Skip identities match the candidate selections;
no new failures. The123 selected structural checks passed on the same source.
No full-suite or whole-act pixel certification is claimed. Diff whitespace
and CI push policy checks passed. Direct comparison of wide frame60's exposed
sky rectangle `(500..527,50..159)` counts1760 black pixels before the fix and
zero after it; the corrected capture is outside the repository.


## Foreground heat-shimmer rendering connection

Follow-up based on `f93e4cee50573f30b7c7ec4ac1d8bd67d4fd8a91`, in
`.worktrees/soz-foreground-haze` on `bugfix/ai-soz-foreground-haze`.
The user's AIZ2 comparison exposed another presentation boundary: `SwScrlSoz`
computed both halves of `H_scroll_buffer`, but only the background half reached
per-line rendering. SOZ1 had no advanced render mode enabling the foreground
path. Earlier claims of complete foreground/background shimmer were too broad.

`loc_55DF2` calls `MakeFGDeformArray` using the shared
`AIZ2_SOZ1_LRZ3_FGDeformDelta` table, then applies independently phased background
shimmer. Register SOZ1's foreground heat-haze mode through the existing zone
provider/controller, as AIZ2 does. Its zone/act gate excludes SOZ2; it carries no
mutable state and is restored through the existing controller snapshot. The
shared renderer, wave arithmetic, palette, sprites and physics are unchanged.
Arena/background-event coupled owners implemented; full route/pixel comparison open under the existing obligations.

The registration regression failed before the change (SOZ1 expected enabled,
actual disabled). It also checks restored registration and rejection of SOZ2/HCZ
contexts. The GPU regression follows ordinary jump/right entry, freezes the
observation boundary, and compares an opaque wall's rows with the same scene
rendered with the mode disabled. Expected displacement comes from ROM bytes,
not fitted screenshots. It then restores the mode and compares the whole frame.
Both320 and528 viewports are covered; existing all-width seam tests remain.

The change selector falls back to2613 ordinary classes for the provider file.
Proportionate validation is appropriate for this zone/act registration: no
shared algorithm or contract changes; focused mode, GPU, restoration and S3K
consumer checks exercise the affected behavior directly. Java21/Lua5.4/PowerShell
preflight passed with `LUA_BIN=/usr/bin/lua5.4`. This is not a full-suite claim.
Focused command (explicit S3K and S1 ROM properties):
`python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.test.gl.native=true
-Dtest=TestS3kAdvancedRenderModeRegistration,TestSozBackgroundCapture,TestS3kSozPatternAnimation,TestAdvancedRenderMode*,TestSpecialRenderEffectRewindSnapshot,TestLevelRendererBackgroundViewport,TestShaderPixelCentreSampling,TestSozAct1QuicksandRoute,TestSozAct1SpringVineRoute,TestSozMechanismsProduction,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils
test -B`:119 tests, no failures/errors/skips,24.296 seconds at20:41:43BST.
The first GPU rectangle extended across a transparent sloped edge at native-width
pixel `(250,78)`, where a whole-composite shift incorrectly moves the background
as well. Restricting the assertion to the opaque wall above that edge fixes the
oracle; no engine behavior or wave constants were changed to accommodate it.

Capture repeats the existing `desert-input.txt` at width528 for240 frames, output
`engine-desert-foreground-haze-528` under the external task root. It completed in
18.374 seconds at20:42:06BST, with only the inherited Discord shutdown warning.
The complete state CSV is byte-identical to `engine-desert-fixed-528`. Inspected
frame150 at camera `(324,1551)`, player `(567,1639)`: foreground stonework and
sandfall rows now have the subtle native one-pixel distortion. The previous
purple-art and right-edge fixes remain visible. The GPU comparison checks actual
row displacement, beyond what can be inferred from a single still.

Independent read-only review found no correctness issue. Coverage is one ordinary
scene, all32 wave entries within its sampled rows, at two widths; mode restoration
is not a replacement for the production route rewind checks also run above.
Transparent-edge/native trajectory and event-selected arena certification remain
inherited gaps. Selected guards (`python3 tools/testing/maven_queue.py -Dmse=off -Pguards
-Dtest=TestRewindArchitectureGuard,TestArchitecturalSourceGuard test -B`) passed76
tests with no failures/errors/skips in20.476 seconds at20:42:31BST.
Integration result follows below.


Delivery: source `ef3f6777b`, clean merge into the unchanged `develop` base at
`8b24f087c75868335b660a8a80f9b47c7ca78e02`. The same focused selection passed119
tests after integration, zero failures/errors/skips,55.590 seconds at20:45:35BST.
The76 selected structural checks passed on the same source. Diff whitespace and
CI push policy passed. This closes the normal SOZ1 foreground-render connection;
it does not certify the inherited arena/event presentation gaps.


## Full SOZ completion campaign

User-authorized goal begins at `1a63f57015a8012e6a34b2fe4f07cc0d52f91056`.
Complete both acts' remaining ROM-backed objects, events, bosses, transitions and
presentation, then the per-act/character route and rewind obligations. Loading
or short positioned captures do not close the goal. Preserve inherited gaps until
implemented and verified. Integrate verified batches into `develop`; keep task
branches local and follow the existing delivery policy.

Work allocation: parent owns the shared SOZ runtime/event/light/palette/scroll
contracts and integration; independent worktrees implement swinging platforms and
rappel wires, the three badnik families, and local sand/path mechanisms. Boss and
Hyudoro work follows the relevant shared contracts. Maintain source-derived
thresholds and production ownership; no trace-state hydration or fixture-specific
progression shortcuts.

All existing and new media stays under the existing external SOZ task root
(`soz-v2-20260915`), preserving previously shared links. An external capture index
tracks sources. Preserve a complete chronological archive and a separate curated
highlights reel. The latter follows Act 1 then Act 2 progression using moving
successful gameplay; repeated tests and sampled stills belong in the archive.
Failed attempts, native references and positioned setups remain labelled and
distinguishable from ordinary engine routes.

Campaign delivery status: integrated and pushed, with both cold Sonic + Tails
boss exits and handoffs verified and media delivered. Original full-matrix
acceptance remains incomplete: route breadth, native/pixel parity and remaining
mechanic/lifecycle obligations continue below. Registered object counts, merged
commits and two successful routes do not close those gaps.

### Shared Act 2 lighting prerequisite

Local implementation `5056db010` owns the native palette/torch counters in
`SozLightingState`, captured through `SozZoneRuntimeState`. It preserves signed
word/byte expiry, pre-increment sand/torch reads, forced sand writes on fade steps,
mid-fade switch reversal, and the boss timer hold without repeatedly restarting
brightening. Both palette and animation upload ROM bytes; no reference rows supply
runtime state. Normal Act 2 scroll now uses `sub_566D2`'s signed half-camera words.

Source audit correction: `sub_55EFC` is called by the seamless transition, not cold
Act 2 initialization. The unused reset code before `AnPal_SOZ2` is not its prologue.
Cold counters start at zero and the first palette tick advances darkness to one;
seamless entry seeds darkness5, step4 and timer1799. The earlier pilot agrees with
the cold path. The catalogue also incorrectly described palette selection as
`step & 6`; that mask belongs to torch selection, while palette selection uses the
52-byte slice offset. Both prose errors are corrected.

Focused validation at `5056db010`: queued Maven `-Dmse=off
-Dtest=TestSozLightingState,TestSozRuntimeState,TestS3kSozPatternAnimation
-Ds3k.rom.path="$S3K_ROM" test -B`: 15 tests, zero failures/errors/skips (50.146s).
Coverage includes all seven torch frames, exact ROM palette destination bytes,
cold/seamless initial state, timer/fade edges and capture/restore forward replay.
This is prerequisite validation; switch/ghost/event wiring and complete routes
remain pending. Initial production test stopped before the final dark bank;
extending it through 3700 ticks supplied that missing test coverage.

### Act 2 boss background and terrain replay prerequisite

The event owner now queues the ROM boss/secondary terrain and art, binds queued
job identities across rewind, runs the thirteen-row wall deformation and its eight
solid rows, and restores the secondary background after collapse. Native delayed
redraw helpers consume two columns/rows per dispatch, including same-call
fallthrough on sand exit. Sand special events stop on player death and the moving
sand pass performs the native standing-object release check.

Focused queued Maven validation on the completion branch:
`-Dtest=TestSozBossWallState,TestSozScreenEvents,TestRewindSnapshotDiffTerrain,TestRewindSnapshotDiffDynamicIdentity`
with explicit `-Ds3k.rom.path="$S3K_ROM"`: 12 tests passed, no failures/errors/skips
(49.807s). This covers wall stage timing, queued ROM bytes, solid graph recreation,
and full snapshot forward replay; it does not certify a complete boss route.
The first arena replay exposed descriptor-identity comparison in the rewind diff:
new equal terrain objects were reported as changed. Comparison now checks saved
terrain contents and still rejects changed collision indices and frame state.

### Kept object-state table

SOZ cork state exposed a missing part of `Respawn_table_keep`: lower object-owned
bits were captured for rewind but dropped on bonus/special-stage reload. The
persistent snapshot now carries those bytes by layout index, preserves them when
ring state is attached, and restores them before the first return placement scan.
Normal loads still clear them. The regression includes entry280 to distinguish
layout indexing from an eight-bit rolling counter. The mutable Mod API `0.7` pin
includes this record extension and the previously added placement-ownership API;
policy remains candidate `0.7.0` with no published baseline.

Focused queued Maven: `TestPersistentRespawnDestroyLatchRoundTrip`,
`TestLevelTransitionCoordinatorPeeks`, `TestBonusStageTransitionCoordinator`,
`TestObjectPlacementControllerS1Counter`: 17 passed, no failures/errors/skips
(47.387s). SDK/Javadoc/sample packaging and API hook-policy checks: 29 passed,
no failures/errors/skips (26.672s). Full route and combined delivery checks remain
pending.

### Retirement and source-entry audit

Ordinary replay exposed Skorp tails retaining a retired root. Retirement hooks
now detach references while children retain their own next-dispatch deletion or
debris behavior; the same boundary is covered for Sandworm/Rockn. Three direct
retirement/replay cases pass;26 other focused badnik, viewport/donor, inventory
and art checks passed. The wall contact audit also corrected d6 bits16/17:
`SolidObject_cont` publishes side contact there, not standing. Three wall checks
pass. See the frontier log for the still-red ordinary replay and its next owner.

The SOZ1 falling-into-sand intro was missing from the original catalogue. Native
`SpawnLevelMainSprites` loc695A initializes animation2/airborne and creates
`Obj_LevelIntro_PlayerFallIntoGround` at41FEE. This must join the entry acceptance
slice, including locked fall, sand splash, jump-pressed release, companion state
and rewind; plain cold loading is not sufficient.

### Cold sand entry implementation

The native controller occupies absolute SST5 (`Dynamic_object_RAM` is SST3,
plus two). Its first dispatch initializes control only; later dispatches apply
old velocity before gravity, wait for physical jump press, and release the team
on emergence. Logical controls are cleared while physical press remains readable.
Local positioned fixtures explicitly skip this intro instead of inheriting a
cold-entry controller after teleporting. The fixture option uses ordinary config
state, since session overrides survive the per-test reset and contaminated the
next cold-entry test. Two cold/skip and full-state emergence replay tests pass
with no skips (queued `TestSozFallingIntro`, 48.432s). Ordinary trace still exposes
an extra initial integration and companion cadence; this is not trace parity.

### Boss, title and transition integration

- `382eee7fc`: final boss and child graph, real eight-hit combat, wall handshake,
  capsule/results, escape and native LRZ request. Corrected production breadth
  asserts actual320/352/400/512/528/640/800 widths and follower counts:68tests,
  zero skips. An earlier width/team setup was invalid and is not breadth evidence.
  Shared capsule results support now captures/relinks its participant owners.
- `1919562e6`: Act1 arena admission, controller/door, rising sand and seamless
  Act2 load/title/fades.22focused tests passed, zero skips (21.593s). Natural
  admission from a positioned approach appears in the26.667s arena film; this
  is not a cold full-act victory. Temple rendering needed source-window selection,
  raw layout columns and background-high replay above foreground-low. Correct
  CPU art alone did not prove the displayed image. Merge64b7e4be9 retained
  both acts' independent art registrations and palette/window runtime methods.
- `0176523e6`: capture startup now retires the omitted title through its native
  owner, permitting Hyudoro initialization. Raw title-request consumption had
  hidden ghosts from otherwise plausible positioned captures. Four capture
  regressions and four ghost lifecycle checks passed, zero skips.
- `a203872dc`: explicit captured graph-reference policies and forced recreation
  in production rewind helpers.58guard/production checks plus24strengthened
  out-of-place checks passed, zero skips.
- `04cbcd574`: source `loc_13AB4→loc_13B18` preserves assembled sidekick state
  instead of clearing object control in CPU INIT. A game-owned semantic service
  selects the branch; shared CPU code does not name a zone.169focused consumers
  and58required S3K loading/bootstrap regressions passed, zero skips.

Remaining visual limit: event redraw counters follow native two-row cadence,
while the renderer presents the new selected source window as a whole. Exact
partial Plane-A/Plane-B contents across seamless load need retained-plane
ownership and cross-load reconciliation, not a fitted delay. No such renderer
migration is included in this slice. Ordinary-route and final combined delivery
checks remain distinct from the above focused evidence.

The merged art/event check completed30tests:29passed and one assumed an empty
20-frame art queue. The final boss adds legitimate ROM work; the test now awaits
the real completion token with a bounded240-frame limit and replays the observed
number of steps. Six screen checks plus five arena checks pass after that fix.
The explicit mapping corruption guard, intro and sidekick state checks pass
(6tests,0skips,53.335s). Ordinary-input cold capture reaches death at3988; it
is retained as an unsuccessful route attempt. The missing terrain sand-slide
owner found at trace1419 is the next concrete traversal fix.

### Reload and team follow-up

`37260aa17` verifies actual boss-to-LRZ load consumption, destination title/fade
retirement, released controls and outgoing rewind timeline reset. Checkpoint
coverage now expands its six native cases to90 actual configurations: both acts,
Sonic/Tails/Knuckles,320/400/512/640/800 widths, and off/S1/S2 donors. Each walks
into a placed starpost, recreates/replays activation and performs production
death/reload, asserting actual width, donor and player capability. Queued
`TestSozCheckpointReloadProduction` with all three absolute ROM properties:
90passed,0skips (21.253s). This covers one post per act, not all placements.

The capture session now uses the native omitted-title boundary after later
loads as well as boot. Its repeated SOZ2 load creates a new ghost controller;
S1/S2 startup consumers and sprite visibility also pass (4tests,0skips,52.697s).
`28aabd127` preserves the actual third/fourth ghost contact identities through
recreation, retaining native P1/P2 priority. Six contact cases and60existing
regressions passed,0skips.

All ten placed checkpoints now have physical activation and production
death/reload evidence for Sonic, Tails and Knuckles:24additional native cases
passed without skips (19.432s), complementing the90configuration matrix.
The methodology now explicitly inventories player spawn, CPU and layout-driven
hooks outside object/event tables after discovery of `sub_730C`. Its27focused
ROM/shared-seam/production cases passed,0skips, in `1bd8dfcb5`.

Pillar side contact now follows `SolidObject_cont` / `loc_1E042`: zero horizontal
velocity still clears ground velocity on left penetration, while the right
branch preserves it. The real solid-contact regression failed before the
override (expected0, actual1421), then all5pillar tests passed without skips
(17.635s). The isolated spike test now supplies its camera dependency explicitly;
its earlier failure was missing test session state, not spike behavior. The
connected special-rock fixture remains under separate route investigation.

The ordinary replay next exposed a shared collapsing-bridge admission gap:
`SolidObjectTop` / `loc_1E45A` rejects negative `object_control`, but permits
positive wire capture. The real contact test reproduced the missing positive
landing while the bit7 negative case passed. The semantic provider override
then passed all14bridge regressions (0skips,52.811s); no zone-name physics
branch or recorded state was introduced.

Wire flip `loc_4B0DE` also clears status bit1 via AND FC; the focused
regression reproduced preserved-air before the fix and all23unit cases pass
afterward. Combined production validation currently fails forced-recreation
standing history: placed bridge index40 TOP entry disappears on restore.
That real identity gap is under investigation;27selected tests are not reported
as a passing set (one production failure,0skips,53.525s).

Standing-history restoration was a real identity defect, not list equality:
`DefaultSolidExecutionRegistry` stored spawn indices and character codes, so
promoted collapsing bridge index40 disappeared when resolving placed objects
after recreation. It now uses existing `ObjectRefId`/`PlayerRefId` mappings
for placed and dynamic owners. The reproducing wire production4cases and7
identity/registry checks pass (11total,0skips,54.241s); additional shared-spawn
and duplicate-character identity coverage is pending.

Wrapped rising-sand rendering is corrected in `a4d3f40e6`. The main background
already had a2048-pixel wrapping texture; an attempted extra tilemap period
override was rejected by measured texture dimensions. The actual missing floor
was the BG-high overlay replay using wrapY=false. SOZ selects wrapped replay
through its native `backgroundLayoutYMask`, retaining HCZ defaults. Root inspected
`completion-connected-mechanisms/wrapped-overlay/lower/frames/01100.png`: Sonic
is visibly grounded on the rising sand. Worker overlay/capture7tests passed,
0skips; connected mechanisms13 and provider/consumers17 passed separately.

The independent shared-spawn/duplicate-character standing identity regression
also passes, together with connected mechanisms and HCZ overlay consumers:
16tests,0skips,56.693s. No comparison rule was relaxed.

### Rappel wire final-swing alias correction

The next ordinary SOZ1 frontier at frame2648 showed expected handle X `$D00`
versus `$CF4`, with expected player frame `$91` versus `$90`. Source audit found
that the original implementation (`c746aa1af`) had misidentified `$46` as an
untouched pointer. `sonic3k.constants.asm:46` defines `parent3 = $46`; the wire
initializer writes the seventeenth endpoint there, and `loc_4AC98` tests its P1
capture byte. The claimed shipped-ROM bug was an implementation error. Final
swing now continues while P1 is held, selecting retract after release and still
performing the current pass's angle update. All eight placed subtype checks now
hold through a complete 128-pass angle cycle before releasing, rather than
asserting the erroneous immediate retraction. The irrelevant ROM-byte `$38`
assertion was removed. Research/inventory/discrepancy prose contained no other
claim of an unused `$46` wire pointer; the source comment and test were the
stale claims. The alias-verification lesson is in implementation pitfalls.

Validation on parent base `306d7fe0f` plus this correction: queued
`TestSozSwingAndWire,TestSozSwingAndWireProduction` with the absolute locked-on
ROM property ran27tests,0skips in52.179s. All23unit cases passed, including eight
complete final-swing cycles. Three production cases passed; the existing wire
ratchet activation recreation case failed because solid-execution standing
history lost the promoted bridge entry (ListN versus List12). This matches the
parent's already-reproduced failure above, before final-swing entry; no assertion
was weakened. The parent owns the identity correction and combined trace replay.

Quicksand routine admission now recognizes boundary-dead CPU dispatch as
native routine6 even though generic dead=false. `sub_400F0` rejects it before
ascending+$68 damping; the same death admission applies to all four variants,
and waterfall held-routine release. The unit regression failed before the fix;
all16quicksand and4production route cases pass after it (0skips,53.384s).

### Connected victory and independent recording follow-up

`e4478100e`, integrated by `d849452c1`, connects the positioned Act1 arena
approach through ordinary pursuit, sink victory, results, door and visible Act2.
Eight graph recreation/replay edges and destination replay pass. The continuous
capture revealed a real black-world handoff: target zone initialization cleared
palette staging after resource transfer. The shared executor now initializes
zone features before `transferAfterTargetInit`; the other production consumer,
ICZ, is included in focused checks.29consumer tests and1capture test passed,
0skips. Root inspected final frame05348 in the unified external
`completion-act1-victory/native-320` folder: both players and temple are visible.
The89.2-second movie is an arena-to-destination scenario, not a cold full act.

`84b2d2b7d` adds an independent59507-row SOZ recording harness and repairs the
S2/S3K recovery handoff's missing collision-plane/art-priority copy. The new
cross-game regression failed before the fix; all18recovery tests then passed,
0skips. The independent trace did not improve (9763errors; first queue34,
Tails2312), rejecting the copy omission as this recording's immediate cause.
The earlier emerald recording reaches transformation4868 without supplied
prior-campaign progression. Exact commands, compared counts and limits are in
[the frontier log](../../status/trace-frontier-log.md). Neither recording's
comparison rows supply gameplay state; queue checks and later rows remain intact.

Connected lower-room escape validation lands in`aa90938e9`: ordinary input now
connects cork, switch8, placed swing, switch9 and native exit/redraw20. Six graph
milestones force recreation and forward replay. Production and capture boot
boundaries are aligned before the first input; eight combined checks pass with
zero skips. Earlier attempts were defeated by route timing, and an initial
fixture/capture mismatch came from the pending initial Process_Sprites pass and
follower setup. No gameplay behavior was changed to make the route succeed.
The Act2 matrix records exact configuration and remaining breadth limits.

`b52f3f52d` resolves the measured recovery2312 mismatch. The CPU handoff itself
matched position and native control, but `handleMovementDispatch` promoted stale
engine platform support to grounded state before gravity. Recovery status resets
now clear the engine riding cache and common roll/push/on-object flags while
preserving radii, native interaction slot and object-owned standing bits. A real
manager regression failed before the fix;107recovery/solid checks and59final
S3K checks pass without skips, including forced graph recreation/replay. The
independent trace remains red but all compared player fields now match through5669;
first main-player divergence moves6241→7291. Raw errors increase9763→13159 as
the subsequent trajectory changes; the longer exact prefix is the measured gain.
The next Tails frontier5670 is premature despawn. Queue34 remains separate.

`496f44d99` corrects the lower escape's requested solo roster: `none` was an
unknown character alias and produced fallback Sonic. Blank is the real solo
setting. Both harnesses now assert actual leader/follower identities; all8
production/capture checks pass again with zero skips. Definitive captures use
`completion-connected-mechanisms/verified-rosters`; prior attempts are retained
and labelled. Five switchB passage trials remain unproved, with no established
runtime defect. The source-driven rock track and switch decay were not altered.

`9fc8974f8` adds an ordinary-controller final-boss route from the positioned
`$51C0/$620` approach through eight hits, capsule, results, forced walk and Lava
Reef. After initial setup it does not write player position, velocity, boss HP
or phase. Continuous snapshots and forced graph recreation exposed an exit
helper retaining a deleted boss. Native `loc_77A6E` has no parent pointer; the
helper now reads the captured `_unkFAB8` fall signal and follows independently.
The worker reports72focused checks,63final checks and the58-test required S3K
quartet passing without skips. The actual-solo native-width capture contains
3050gameplay frames and reaches Lava Reef at2869. This remains a positioned
boss-to-destination route; connected cold Act2 validation is separate.

The proposed retained-plane Act1 arena redraw implementation was rejected after
42 image pairs at cameraY`$960`, widths320/528/800, static and moving camera,
showed zero changed pixels despite8,170–10,775changed tilemap bytes. The
foreground occludes those partial writes. Seamless loading fades palettes before
its redraw. All prototype source and tests were removed; diagnostic images remain
in `completion-arena-redraw/native-bounds-pixel-comparison` in the unified capture
folder. This corrects the earlier visible-defect assumption and does not certify
native pixel identity. Act2 redraw visibility is being measured independently.

`60526be71` resolves the measured post-boss redraw defect. BG2C retains a captured
64×32 descriptor plane through art admission, then applies the native two-row
writes and entering-row maintenance. An optional internal renderer interface
supplies those descriptors without changing normal background caching or the
existing 512-pixel period. The first entry frame previously flashed new
high-priority descriptors before the art arrived; subsequent partial writes are
visible in wide margins. The initial native-width A/B experiment excluded that
entry frame because its mutation did not survive resource admission, so its
later-frame occlusion result did not rule out this flash.

Source/runtime/controller graph checks pass 11 tests, source/graphics checks
pass 6, and cache plus required S3K bootstrap/loading checks pass 88, all without
skips. Same-revision registry restoration recovers identical image and tilemap
bytes; clear-to-normal checks the cache against the ROM source. Root inspected
800-pixel frames 00000, 00008 and 00029: retained margin, partial rising redraw,
and completed temple. The three `capture-4x-slow.mp4` clips in
`completion-act2-redraw/verified-retained-production` are explicitly slow motion.
These are source-backed engine checks, not native pixel certification. No Act1
retained-plane prototype was restored.

## Combined delivery validation

At `240071d5e`, queued category validation against pre-task base
`1a63f57015a8012e6a34b2fe4f07cc0d52f91056` selected all 2,657 ordinary classes
plus guards (`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py
--base 1a63f57015a8012e6a34b2fe4f07cc0d52f91056 --run`). The ordinary lane
completed 21,158 tests with four failures, no errors and 25 skips in 818.64s.
Failures were the rewind-tail class inventory, SOZ standalone-art count, stock
zone-dependent factory inventory and the old CNZ-only numeric-slot assumption.
The guard lane completed 668 tests with nine failures, no errors/skips in 180.38s.
SOZ-owned findings covered event access, lifetime/touch/control conventions,
update-clock naming, the object-manager facade budget and test lifecycle/oracles.
These are being corrected through their existing owners, not by raising budgets.

A bounded matched run on unchanged updated `develop` at
`ea5973d9aafa12dc85de014fe413cf0501a2c770` used queued Maven with `-Dmse=off
-Pguards -Dtest=TestBuildToolingGuard,TestTraceChaserBoundaryGuard,TestNoAssertionFreeDiagnostics
test -B`: 131 tests, three failures, no errors/skips, 52.799s. The baseline
failures are stale direct-Maven prose expectations, tracked `tools/bizhawk/README.md`,
and assertion-free `FbzRouteEvidenceProbe#printEvidence` /
`LevelSolidityMapProbe#writeSolidityMap`. The SOZ checkpoint delegate adds a new
oracle finding only in the candidate and must be fixed; the baseline failures
are not attributed to this task.

The 25 ordinary skips include opt-in diagnostics/benchmarks, native graphics
capability gates, the CPZ spin-tube assumption and separate capture scenarios.
SOZ captures were exercised explicitly in the focused runs recorded above;
this ordinary run does not certify those skipped capture invocations or trace
parity. Final correction and integrated-tree results remain pending.

The root correction batch moves placement ownership and remembered-spawn lookup
into the existing placement controller, injects zone-event access into the
seamless handoff, and updates the explicit art/factory/rewind inventories. The
three badnik children are classified against their production graph test, which
now asserts that the actual shell, legs and segment types exist at recreation
milestones. The focused 343-test run passed 342 tests; its sole stale hardcoded
inventory pin was corrected and the one-test rerun passed without skips.
The final inventory is 1,063 classes: 839 isolated passes and 224 graph-covered.
Focused guards then passed 81 of 82 tests with no skips; the sole failure contains
exactly the two unchanged baseline diagnostic probes above. SOZ event access,
facade size, lifecycle setup and checkpoint oracle findings are cleared.

The object-policy correction `6e0ae9e75` passed 123 focused checks without skips
and is merged into the completion branch. It uses existing deletion, touch and
control contracts; the exit helper retains all engine followers under the
explicit native-P2 extension policy and tests duplicate identity. No guard
allowlist or numeric budget changed. Read-only review of `7191f0664` found no
concrete issue: handoff accessors resolve the target services dynamically after
initialization, and placement delegation preserves ownership and cache flags.

The final lower-rock audit did not establish a passage. Correcting an attempt
that never entered spin dash and then trying charge/brake/jump variants reached
`$49FD` but did not pass the door. The independent recording has zero player or
nearby-object samples inside X `$46C0..$4A80`, Y `$480..$700`; it instead uses
the upper `$4A30/$330` switch and `$4AF8` swing. These observations justify no
geometry or collision change and do not establish that the lower puzzle is
optional. Failed captures remain labelled in the unified media folder.

### Verified cold Act 1 completion

`9f779282f` preserves the 26,716-frame controller route and explicit capture
consumer. A repeat on merged runtime `f43f63425` passes one test without skips:
queued Maven `-Dmse=off -Dtest=TestSozColdAct1Capture -Ds3k.rom.path="$SOZ_ROM"
-Dsoz.cold.act1.capture="$SOZ_CAPTURE_ROOT/completion-cold-route/native-320-final"
-Dsoz.cold.act1.stride=4 test -B`. Here `$SOZ_ROM` is the discovered absolute
locked-on ROM path and `$SOZ_CAPTURE_ROOT` is the unified external media folder.
JUnit took 29.098s; Maven including recompilation took 1:21.

The naturally spawned boss appears at frame22,823, sinks at25,122, and the
normal handoff loads Act2 at26,436 and releases control at26,535. The capture
asserts no player death, the actual Sonic+Tails roster, boss/sink transitions,
nonblack target palette and visible destination world. Root inspected the
final temple image with both players visible; the route author inspected the
sinking frame. Inputs supply no gameplay-state or hardware-timing values.
The cold route is native width320 with intros enabled, not every configuration.
Short production graph tests remain the separate rewind evidence.

### Verified cold Act 2 completion

`de765d83d` preserves 32,432 controller frames and `TestSozColdAct2Capture`.
Queued Maven `-Dmse=off -Dtest=TestSozColdAct2Capture
-Ds3k.rom.path="$SOZ_ROM"
-Dsoz.cold.act2.capture="$SOZ_CAPTURE_ROOT/completion-cold-route/act2-native-320-final"
-Dsoz.cold.act2.stride=1 test` passed one test without skips:137.1s test,
2:35 Maven. A subsequent sparse repeat with explicit final control-lock and
object-control assertions passed one test without skips:4.747s test,23.416s
Maven. Production runtime is unchanged from `819d99cc1`.

The route reaches the real boss after all three corks and the timed final
shaft, delivers eight natural hits, completes capsule/results and loads LRZ
at32,252, retaining180 destination frames. The matrix records exact combat
milestones. Both players are visible in the inspected destination; control is
released. The native recording's upper switch/swing route is followed, leaving
the separate lower rock puzzle explicitly unverified. No collision, geometry
or physics tuning was needed for the final Act2 route authoring.

### Integrated delivery verification

The completion branch merged into `develop` without conflicts at
`358679affa0b4d3ff44c18e49ca77ff55297849d`; unrelated main-workspace changes
were preserved. Final queued validation used
`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base
1a63f57015a8012e6a34b2fe4f07cc0d52f91056 --run`, after actual-tree preflight.
It selected all 2,659 ordinary classes plus guards. Ordinary completed 21,161
tests with zero failures/errors and 27 skips in 835.57s. Guards completed 668
tests with three failures, zero errors/skips in 184.39s.

Each guard failure's test identity and every nonblank failure-message line
matches the bounded unchanged-`develop` baseline recorded above: stale direct
Maven prose expectations, the tracked BizHawk README, and the two assertion-free
legacy probes. No SOZ-owned failure remains. This is an ordinary-suite pass
with three pre-existing guard failures, not a fully green combined run.

The 27 skips are the earlier 25 plus the two opt-in cold capture classes; both
cold captures passed explicitly as recorded above. Other skips remain opt-in
measurements, separate graphics/audio captures and the existing CPZ spin-tube
assumption. No missing-ROM skip was reported. Independent strict trace parity
and the lower Act2 rock puzzle remain the documented limits; no test tolerance,
queue capacity or guard budget was weakened to close delivery.

The unified external `soz-v2-20260915` folder retains the original media,
controller inputs and state records. Its 311 movie/probe sources are assembled
chronologically by `assemble_highlights.py` into `SOZ-work-compilation.mp4`, with
a chapter index and source SHA-256 inventory. After user feedback about stills
and duplicate tests, `curate_highlights.py` replaces the initial all-source reel
with 22 moving excerpts (4:20.700) from the two final cold runs, ordered through
Act 1 then Act 2, in `SOZ-work-highlights.mp4`. `highlights-chapters.csv` records
exact source intervals. The archive and originals are unchanged; final cold
movies remain available independently. The external README records final
media verification and durations. Generated validation diagnostics are consumed
and acknowledged rather than archived.

## 2026-09-16 retrospective and remaining acceptance

The campaign delivered playable cold completions, not every acceptance item in
the original objective. Earlier aggregate “complete” wording was too broad.
Act matrices remain authoritative for individual obligations. This reconciliation
claims no new runtime result, route coverage or native parity.

### Lessons and amendments

- **Inventory:** placed factories were insufficient. User review prompted explicit
  parallax/animated-tile planning; foreground shimmer, cold player entry and
  terrain-driven sand sliding also needed their own production owners. The design
  includes presentation and player/terrain dispatch from the start.
- **Sequencing:** positioned family tests preceded cold success for too long.
  Cold traversal exposed shared collision, initialization and transition issues.
  Future slices extend a persistent cold input route while keeping short local
  tests independent; this does not require constant strict full-route replay.
- **Native evidence:** source already explained darkness constants. The pilot
  added live participant ownership and visible timing evidence. Future probes
  start with a named question left after source review.
- **Visual acceptance:** initial inspection missed purple flame tiles, a wide
  background seam and switch/track layering. Moving native/wide inspection must
  examine animation phases, exposed edges and overlaps, not just artifact creation.
- **Configuration:** a purported solo capture resolved an unknown alias to a
  fallback character. Assert actual roster/donor/viewport before counting evidence;
  corrected captures do not retroactively certify the earlier attempts.
- **Rejected work:** 42 Act 1 redraw image pairs showed no visible change at the
  tested boundaries, so the prototype was removed. Act 2’s measured entry flash
  and wide-margin redraw justified `60526be71`. Source-level differences alone
  do not establish a visible defect or justify unnecessary renderer changes.
- **Media:** representing all 311 sources made poor highlights. The revised
  22-scene reel is editorial selection; the archive preserves the complete record.

### Remaining acceptance, in recommended order

1. Establish native and engine passage evidence for the lower Act 2 subtype-$87
   rock/switch puzzle. The successful upper route and failed lower attempts prove
   neither lower passage nor its optionality. Do not alter geometry without evidence.
2. Resolve the independent strict trace failure and compare outstanding native
   state/presentation intervals. Cold engine completion is not native parity;
   retain the recorded frontier and comparison tolerances.
3. Expand complete routes across supported characters, donors, viewports and teams,
   prioritizing distinct branches and sensitive mechanics in the act matrices.
4. Close remaining checkpoint, repeated-transition, rewind/forward-replay and
   per-mechanic obligations. Whole-route success does not certify every placement,
   branch, lifecycle boundary or pixel sequence.

Final ordinary-suite results and baseline guard attribution remain as recorded
above. This follow-up changes prose only: check links, status/scope consistency,
media references and commit policy; do not rerun unchanged engine tests.

## 2026-09-16 non-trace acceptance follow-up

The later [supported-roster correction](#2026-09-16-supported-roster-correction)
supersedes this section's character/donor product claims; original run counts
remain historical evidence, not current certification.

User direction: finish remaining work while skipping traces for now. Base is
`e25269d0e70d0db9ddbb5b914a30795d859ab755`, worktree `.worktrees/soz-acceptance`,
local branch `feature/ai-soz-acceptance`. No strict replay, trace production or
hardware-timing tolerance change is part of this follow-up.

### Verified lifecycle and mechanism breadth

Queued focused Maven (`-Dmse=off`, explicit absolute S3K/S1/S2 ROM properties,
`test -B`) completed these expanded production selections:

| Selection (`-Dtest=`) | Cases | Failures/errors/skips | JUnit / Maven time |
| --- | ---: | --- | --- |
| `TestSozCheckpointReloadProduction` | 450 | 0 / 0 / 0 | 21.990s / 40.752s |
| `TestSozTeamCheckpointResetProduction` | 90 | 0 / 0 / 0 | 7.807s / 26.594s |
| `TestSozConnectedMechanismsProduction` | 60 | 0 / 0 / 0 | 22.250s / 39.079s |

Every checkpoint now covers all three leaders, five required logical widths and
three donors with activation replay and death/reload. Repeated team reloads cover
native/mixed/duplicate teams over the full width/donor product. Four connected
mechanism scenarios cover that same product, including forced graph recreation.
The act matrices record the exact obligations closed; these checks do not certify
full character routes, every team-sensitive interaction or native pixels.

### Cold input portability

`TestSozColdAct1Capture,TestSozColdAct2Capture` now accept `soz.cold.width` and
`soz.cold.followers`, retaining native-320 Sonic + Tails defaults and asserting
resolved width/roster. The same explicit capture properties as the final campaign
were used under external `acceptance-followup/cold-*`, with PNG strides 30,000
and 40,000 respectively. Only controller inputs are replayed.

Both matched native-width controls pass with zero skips (23.304s Maven). At
width 400, Act 1 dies at frame 10,536; at 800 it dies at 1,804. Act 2 fails the
real-boss-reached assertion at both widths after all 32,432 inputs. First observed
position separation beyond 30 pixels at width 400 is Act 1 frame 5,926 and Act 2
frame 3,979; these are ordinary engine route observations, not trace frontiers.
No production cause is attributed from fixed-input failure alone. Wider routes
need independent controller authoring; do not alter spawn/collision timing to
make a native-width input recording pass unchanged.

### Native lower-passage investigation

New positioned probes are under the unified media root's `acceptance-followup/`.
They load the prior SOZ2 LFC35 save after stopping movie playback and verify zone
and unlocked controls. Declared setup positions/motion and optional rings/P2
position are written before observation only. The original badnik-overlap entry
again dies without useful pushing; it remains rejected setup evidence.

The revised `$4754/$5AC` approach with ordinary right/jump input does push the
native rock, which then leaves its push routine before the switch, corroborating
the engine's fall/link invalidation. Direct switch, spin-jump and paired-controller
trials have not established passage. In `native-lower-diagnose-1`, the player
remains routine 2 with 99 rings: the jump's upward speed becomes zero near
`$493E/$593`, then the player reaches the lower wall at `$49F5`. This is a failed
positioned route, not proof of an impossible or optional puzzle and not native
pixel/trajectory certification. No collision, geometry or door timer changed.

User observation identified deprecated `bit.band` console spam during the slow
probe. Current probes use `&`; the matching completion-marker run has
1,200 observations and finishes in 3.069s rather than timing out at 45s.
Screenshot frequency was also reduced, so the speedup is not attributed solely
to removal of the warnings. The reusable warning is recorded in the existing measurement-hazard catalogue.


A bounded engine-only input-authoring search also failed to produce a passage:
8-frame action blocks, up to 70 rounds, 128 retained candidate states, and
16 neutral/directional/jump/spindash timing choices per candidate. It used the
engine's own snapshots for search, then required a clean forward run before any
result could count. The height-favoring search stopped at its round bound with
best X `$48E5`; an earlier forward-distance search reached `$49F5` before losing
all candidates. Neither is an exhaustive impossibility proof. The temporary
failing authoring test was removed; no fitted gameplay rule or acceptance bypass
was retained. The next useful evidence is a successful native controller route,
not another geometry change inferred from these failed attempts.


### Widescreen arena admission finding

Expanding controller-only boss routes found a real Act 1 admission blocker at
widths 640/800. With ordinary right input, the player stops at the arena wall
`$4438`, while the outer camera left edge is still below `$4310` (at width 800,
`$42A8`). The camera gate cannot be reached by further walking. The ROM owner
is `sub_55E96`, which compares its native 320-pixel `Camera_X_pos` at `$4310`
after the Y camera reaches `$960`. The engine centers wider views on the same
player, so this local admission check now adds half the extra viewport width
before comparing the native gate. Width 320 is unchanged; camera bounds, terrain
and boss routines are unchanged. This is an intentional widescreen extension,
not an inferred native physics correction.

The new threshold regression initially passed native 320 and failed all four
wider cases at the exact centered-native boundary (5 cases, 4 failures, no errors
or skips). It checks the pixel immediately before admission and equality at the
gate, with the player still before the arena wall. Full victory/transition tests
are required in addition to this local threshold check.

The first expanded boss rewind checks also exposed test-driver input history:
restoring the gameplay registry leaves external runner button history untouched.
The tests now prime the preceding input before re-executing the captured edge;
Knuckles shell-contact sprite differences disappear without dropping compared
fields or changing production rewind. Controller routes for other characters are
authored independently; a Sonic jump rhythm can invoke flight/glide midair.


The final end-boss selection passes 40 cases, zero failures/errors/skips
(54.72s JUnit, within a combined Maven run whose Act 1 controller candidates
still failed). This class result is not a claim that the combined invocation
passed. Cases are Sonic off/S1/S2, Tails off/S2, Knuckles off/S1/S2 at each required
width, all solo. Controller authoring found native Tails period/hold/approach
`32/20/-32`, native Knuckles `40/4/+32`, and donor Knuckles `16/4/-48`; those
numbers select only test inputs, not boss/physics behavior. Other verified cases
retain the original `48/12/-12` input strategy. All eight hits and graph rewind
assertions pass in clean forward runs, including capsule/results and LRZ load.
A ground-jump-only candidate was rejected because it failed natural combat;
repeated broad rhythm searches did not author S1-donor Tails. Its bounded direct
attempt kept pilot HP at eight while opening the shell, then died at frame 851.
No runtime capability, collision box or damage rule was changed to force it through.


### Final focused validation and media

The final ordinary selection `TestSoz*,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`
completed 1,139 tests, zero failures/errors, eight skips in 3:42 Maven. It includes
all 56 SOZ classes and the four required S3K regressions. The skips are explicitly
opt-in captures: Act1Victory, ColdAct1, ColdAct2, EndBossVictory, Miniboss,
Act1Arena, ConnectedMechanism and PostBossRedraw. They are not missing-ROM skips.
`TestHeadlessStateTeardownGuard,TestSingletonLifecycleGuard` in a separate
`-Pguards` JVM passed all 13 checks without skips (18.285s Maven).

Both cold controller captures were then run explicitly after the runtime fix:
2 tests, no failures/errors/skips, 21.194s Maven, outputs in
`acceptance-followup/cold-final-control/{act1,act2}`. The captures use sparse PNG
strides 30,000/40,000 but execute every controller frame and retain the complete
state CSV. This is controller playback, not strict trace replay.

Commands, from `.worktrees/soz-acceptance` based on `e25269d0e`; set
`ROM_ROOT` to the existing ROM directory (the actual run used absolute paths):

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base e25269d0e70d0db9ddbb5b914a30795d859ab755 --preflight
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestSoz*,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' "-Ds3k.rom.path=${ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" "-Dsonic1.rom.path=${ROM_ROOT}/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=${ROM_ROOT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" test -B
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Pguards -Dtest=TestHeadlessStateTeardownGuard,TestSingletonLifecycleGuard test -B
```

The change-based plan selects the full 2,660-class ordinary suite plus guards,
with a historical cost around 24 + 10 minutes. Proportionate focused validation
was chosen: the sole runtime edit is one local SOZ1 widescreen admission condition,
with no shared algorithm, public contract or native-width condition change.
The focused selection exercises its threshold, complete boss/transition paths,
rewind and neighboring SOZ event/object consumers; the explicit GPU capture
checks the visible result. This is not a full-suite pass. Preflight initially
found the default Lua version unsuitable; explicitly selecting `/usr/bin/lua5.4`
passed Java 21, Lua and PowerShell checks before validation.

All 45 final Act1 victory cases pass (74.18s JUnit in the final selection).
Knuckles and S2-donor Tails use an authored left-side golem approach; Sonic and
other Tails variants retain the established approach. Early escape/braking and
ground-jump-only candidates failed and were discarded. The surviving control
choices do not alter boss AI, geometry, movement or native character capabilities.

The explicit `TestSozAct1VictoryCapture` at `soz.act1.victory.width=800` passed
without skips (9.906s JUnit / 26.379s Maven). It produced 5,325 state rows and
1,332 PNGs, encoded at the normal 15fps cadence to an 88.800-second movie under
`acceptance-followup/act1-victory-wide800/`. Battle, sinking and visible unlocked
Act2 frames were inspected. The curated reel replaces its earlier golem battle
excerpt with 13 seconds from this moving wide capture; it remains 22 chronological
Act1-then-Act2 scenes and 260.700 seconds. Full decode and chapter/source checks
pass. The original 311-source archive is retained unchanged; the capture README
now distinguishes it from the acceptance follow-up. Failed native probes and
sparse still controls are not added to the highlights.

### Still open after this follow-up

Strict traces remain deferred. Full cold character/donor/viewport routes, the
S1-donor Tails final-boss victory, positive lower subtype-$87 puzzle passage,
repeated seamless/exit cycles and remaining per-participant/native-pixel obligations
are not certified by these bounded checks. Their existing matrix entries remain
open; no production rule was changed solely to make a failed recording pass.


The end-boss route check was strengthened after the combined selection to let
`GameLoop` own the pending LRZ load, title/fade and playable control release.
Constructing a new loop only after the headless driver had already consumed the
load stalled all 40 title checks; that rejected harness handoff was corrected by
switching before the load, after the final tested boss/redraw milestone. A single
Sonic control passed, then the complete 40-case selection passed with zero
failures/errors/skips (54.88s JUnit / 1:12 Maven). Destination checks retain the
requested character, actual width and donor movement capability. Only this
changed test class was repeated; the other focused selection results above
remain applicable. No production title/load owner changed.


### Integration verification

Source/test/docs commit `31686bac5` fast-forwarded into the main workspace's
unchanged `develop` branch from `e25269d0e`; fetch/pull found no upstream delta
and no conflict resolution was needed. Post-integration queued Maven selected
`TestSozAct1ArenaAdmission,TestSozAct1VictoryProduction,TestSozEndBossInputRoute`
with `-Dmse=off`, the same explicit S3K/S1/S2 ROM paths and `test -B`.
All 90 cases passed with zero failures/errors/skips in 2:54 Maven
(0.517s admission, 71.46s golem, 52.75s end-boss JUnit). This confirms the integrated
runtime threshold and both natural-victory transition paths; it is focused
post-integration validation, not a full-suite run. The follow-up integration
record changes documentation only and does not require repeating engine tests.

The changed Markdown files passed 150 local link/anchor checks and whitespace
validation. The commit hook caught absolute machine-local paths in the command
record; commands now use `ROM_ROOT` while retaining the exact ROM filenames,
options and selections. Existing dirty disassemblies and unrelated user files
were preserved. Captures remain in the external unified SOZ task directory.


## 2026-09-16 supported-roster correction

Based on integrated `d2b84abb5`, isolated branch `feature/ai-soz-tails-route`.
The outstanding S1-donor Tails end-boss search exposed an acceptance error,
not evidence for a boss/physics change. `LaunchProfile.mainCharacterValues`,
`sidekickValues` and `sanitizedFor`, the donor capability declarations and
`CONFIGURATION.md` already restrict S1 to Sonic, S2 to Sonic/Tails, and native
S3K to Sonic/Tails/Knuckles. Raw test configuration had bypassed that gate.

A landing-triggered, 20-frame held-jump controller opened the shell at tick182
but died at285 without a hit. It was rejected. A paired 601-frame-bounded probe
then ran identical default controller decisions with S1 and S2 donors. Rolling
status and movement initially matched, but animation differed at tick0: S1
remained animation0, S2 used roll2. The first non-animation state difference was
at tick257: S2 removed one boss hit point while S1 took damage. The S1 fixture
died at468; the S2 observation reached600. `Sonic1PlayerArt.loadForCharacter`
returns null for Tails, leaving no profile; `ObjectTouchResponseController`
requires the roll animation and rolling status for a spin attack. The production
launch clamps this unsupported request. Neither boss geometry nor touch logic
was changed. Temporary observation/controller code and CSVs are disposable;
these findings supersede the earlier search's interpretation.

The corrected acceptance helper consults `LaunchProfile.sanitizedFor` and asserts
ROM-backed renderer, animation profile/scripts and mappings for every participant,
including at the tested destination/reload. Character keys, not internal follower
instance codes such as `tails_p2`, identify characters. The launch regression
checks S1 Tails/Knuckles and S2 Knuckles fallback explicitly. The matrix becomes:

- All ten checkpoints × five widths × six supported character/donor combinations:
  300 physical activation/replay/death-reload cases.
- Each boss: 30 natural victories covering the same supported combinations and
  widths, with the existing graph recreation/replay and playable transition.
  Act1 Sonic uses the supported stock pair with off/S2 and solo with S1. A
  Sonic-duplicate S1 companion changed combat enough to kill the width320
  controller at3677; that mixed-combat route remains unauthored, not a runtime
  defect. The default solo S1 controller also died at3197; the existing left-side
  approach passed all five widths. Repeated duplicate-team lifecycle coverage
  remains in the90-case test.
- Repeated team reloads retain 90 cases and one/two/six follower counts. S1 uses
  Sonic duplicates; S2 substitutes Sonic for Knuckles; native rosters retain
  mixed characters and duplicates. No finite maximum is asserted.
- Connected mechanisms retain all60 width/donor cases. Other donor-sensitive
  object/light/badnik tests replace unsupported S1 Tails followers with Sonic.

This corrects the support premise; it does not erase remaining supported cold
route breadth, mixed combat, repeated seamless/exits, lower subtype-$87 passage,
or native presentation obligations. Trace recording/replay remains deferred.

### Validation and delivery

The change-based plan selected2660 ordinary classes plus guards because test
helpers are unclassified. Proportionate validation applies: production source,
physics, timing, asset loading and launch policy are unchanged; only bounded SOZ
fixtures, assertions and one launch-policy regression change. Run the affected
SOZ classes, launch-profile tests and the mandatory S3K quartet. This is focused
validation, not a full-suite claim. The completed selections below account for711 unique passing cases, zero errors
and zero skips on the corrected acceptance tree. Initial failures were the new
helper reading follower instance codes (173 cases), then the width320 S1 golem
controller (one case); each affected class was rerun after its correction. No
unattributed runtime failure remains in these selections.

All commands used `python3 tools/testing/maven_queue.py -Dmse=off`, `test -B`,
and these explicit ROM properties (`ROM_ROOT` is the main workspace):

```bash
"-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen"
"-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen"
"-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen"
```

Selections and completed outcomes:

- `-Dtest=TestSozCheckpointReloadProduction,TestSozTeamCheckpointResetProduction,TestSozConnectedMechanismsProduction,TestSozLightGhostCompatibility,TestSozSandRockProduction,TestSozAct1VictoryProduction,TestSozEndBossInputRoute,TestLaunchProfile,TestLaunchProfileApplier,TestLaunchProfileStore,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`:
  initial663 cases,173 assertion failures, no errors/skips,1:54 Maven. The
  415 cases outside the five affected classes passed and were not repeated.
- `-Dtest=TestSozPushableRockProduction,TestSozMechanismsProduction,TestSozRouteControllersProduction,TestSozAct1SpringVineRoute,TestSozBadnikProduction,TestSozAct1QuicksandRoute`:
  48 passed, zero failures/errors/skips,25.711s Maven.
- `-Dtest=TestSozAct1VictoryProduction,TestSozConnectedMechanismsProduction,TestSozLightGhostCompatibility,TestSozSandRockProduction,TestSozTeamCheckpointResetProduction`:
  248 cases, one S1 golem-controller failure, zero errors/skips,1:38 Maven;
  the218 non-golem cases passed with corrected participant keys.
- `-Dtest=TestSozAct1VictoryProduction`: the final left-approach controller
  completed30 cases, zero failures/errors/skips,49.93s JUnit /1:07 Maven.

The temporary comparison probe's two passing observation tests are not part of
these711 acceptance cases. No trace fixtures or native parity checks were run.
