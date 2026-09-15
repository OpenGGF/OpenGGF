# Sandopolis Zone: methodology v2 application plan

Date: 2026-09-15. Status: placed inventory, native pilot and quicksand slice integrated;
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

The placed inventory, native pilot and quicksand family are delivered at the
bounded scope recorded below. The next Act 1 dependency is the spring vine;
full dynamic/art/audio inventory and later route slices remain open. Keep commands, RED/GREEN results, review findings, resolved
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
