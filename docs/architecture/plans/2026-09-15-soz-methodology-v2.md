# Sandopolis Zone: methodology v2 application plan

Date: 2026-09-15. Status: placed inventory, native pilot, quicksand, spring-vine and sand-rock slices integrated;
pushable-rock implementation and local/native corroboration complete; full routes and native certification remain open.

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
