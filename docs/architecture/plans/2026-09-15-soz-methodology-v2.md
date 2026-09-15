# Sandopolis Zone: methodology v2 application plan

Date: 2026-09-15. Status: placed inventory, native pilot, quicksand, spring-vine and sand-rock slices integrated;
full routes and native certification remain open.

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
   transition registrations, existing tests and current discrepancy/frontier
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
| 1. Act 1 entry and desert traversal | `SOZ1_ScreenEvent`, `SOZ1_BackgroundEvent`, `AnPal_SOZ1`, `AnimateTiles_SOZ1`; placed quicksand variants, rocks, vines, platforms and badniks | Camera-driven background/animation at entry; quicksand approach, sink and escape in adjacent phases. Verify movement donor feasibility and actual production object bindings |
| 2. Act 1 arena and miniboss | `sub_55E96`, `sub_55D94`, `Obj_SOZMiniboss`; sand rise, delayed redraw/art handoff, door children | Trigger before/at/after lock, art readiness and spawn order; ordinary boss interaction through defeat and door opening; failed child allocation and rewind |
| 3. Seamless Act 2 entry | `SOZ1_BackgroundEvent`, `sub_55EFC`, `SOZ2_ScreenInit`, `SOZ2_BackgroundEvent` | Paired-player door admission versus solo/extra followers; fade, queue readiness, reload, wrap setup, title lifecycle and control release. Compare the entire short transition, not just its destination |
| 4. Act 2 darkness and traversal | `AnPal_SOZ2`, `AnimateTiles_SOZ2`, `Obj_SOZLightSwitch`, Hyudoro/capsule routines; doors, switches, wires, sand corks | Light switch immediately before/at/after a darkness step and during brightening; ghost release/character/checkpoint conditions, torch/palette agreement and held-player ownership |
| 5. Rising sand and pyramid changes | `SOZ2_ScreenEvent`, `SOZ2_BackgroundEvent`, reached `SpecialEvents` sand routine, `sub_5699A` | Moving sand, camera/background collision and terrain edits in the same frame sequence; cross the Act 2 vertical wrap with players, camera, objects and rendering; reverse approach and rewind |
| 6. Act 2 boss and exit | `sub_56706`, `sub_56A12`, `Obj_SOZEndBoss`, reached defeat/capsule/transition routines | Wall collapse/reconstruction, solids, boss hit/defeat and darkness/animation shutdown; ordinary completion into the next route, cleanup and reset isolation |

Shared state and phase contracts precede dependent objects. The Act 2 native pilot
is early research; it does not replace the dependency order or claim traversal.
Catalogue quicksand as object behavior, not water physics, unless source review
disproves that distinction. `No_Resize` is not evidence that SOZ has no events.

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

The placed inventory, native pilot, quicksand, spring-vine and sand-rock families are delivered
at the bounded scope recorded below. Cold rock reachability, full dynamic/art/audio
inventory and later route slices remain open. Keep commands, RED/GREEN results,
review findings, resolved
catalogue contradictions, amendments and rejected approaches in this plan as work
proceeds. Reuse the existing SOZ analysis for verified ROM findings and the act
matrices for acceptance evidence. No new receipt or log format is required.

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
