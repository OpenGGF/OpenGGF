# Project simplification work list

## Goal and delivery

Reduce repeated implementation and distributed ownership without changing ROM
behavior, rendering, save compatibility, creator fault boundaries, or replay
authority. This implements the September 15 user-requested project-wide review.
Initial integration base: `94a41febdde969bb862e2b3c1e142d60fcdb7429` (`develop`).
Worktree: `.worktrees/ai-simplification`, branch `feature/ai-simplification`.

Use existing owners and internal helpers. Preserve observable ordering, equality,
mutability, errors, and cleanup behavior. Changes to public Mod API contracts are
outside a mechanical extraction: prefer private representation changes or record
why a candidate needs separate work. No gameplay algorithms or timing constants
are being redesigned. Java 21; use the shared Maven queue.

## Work list

Every implementation item includes review, relevant focused verification, and the
combined delivery selection. Investigation gates close with evidence for landing
a bounded extraction or retaining the existing design; they do not authorize a
speculative framework. Update the results below as the work proceeds.

- [x] **1. Save decoding.** Add an internal save-payload reader for the three
  identical team/numeric readers in DataSelectSessionController,
  S3kDataSelectPresentation, and S3kSaveProgressions. Preserve permissive
  stringification and Number.intValue; leave Engine team fallback semantics local.
  Verify malformed/absent values and existing data-select consumers.
- [x] **2. Rendering support.** Extract exact SOLID fallback line/rectangle/diamond
  append operations into a graphics helper. Preserve command ordering and caller
  lists; DebugRenderContext's alpha-blended overlay path remains distinct.
  Share white-texture construction across three screens, keeping separate texture
  lifetime. Verify command equivalence and existing screen/native checks.
- [x] **3. Tool WAV output.** Share the identical stereo PCM writer used by the FM
  and PSG SFX tools. Preserve byte order, frame truncation and sample-rate behavior.
  Compare emitted WAV data and retain entry-point tests.
- [x] **4. S2 participants.** Extract prepend-if-absent list plumbing into an internal
  helper. Keep queries and participation policy at each call site. Preserve equals
  semantics, original-list identity, fallback order, and mutable new lists.
  Exclude append-based OOZLauncher and preserve call-site null/type guards.
  Verify trigger/traversal participation and exact list edge cases.
- [x] **5. Palette validation.** Share reachable-art traversal behind the two
  existing validator entry points. Preserve wrapper-specific domain bounds,
  exception types, owner attribution and first error. Use internal adapters rather
  than widening ModLevelDefinition/ModZoneLevelData's Mod API. Verify both wrappers
  including unreachable art, transparency/backdrop and invalid references.
- [x] **6. Solid profiles.** Share the identical ModLevel/Sonic3kLevel array builder.
  Preserve signed angles, indices, copying and malformed-array behavior. Verify
  in-memory levels, ROM-backed loading and mutation isolation.
- [x] **7. Audio configuration gate.** Remove external field re-enumeration when
  binding a session coordination handler if a bounded private representation can
  preserve the API and defensive-copy contracts. Merely moving the setter chain
  is insufficient. Retain copy-coverage and runtime AudioManager-path evidence;
  direct driver fixtures bypass the historically faulty copier.
- [x] **8. Scalar rewind pilot.** Check Buzzer's enum/five scalar override against
  existing compact schema eligibility. Remove bespoke capture only if recreation,
  restore and forward replay (including projectile timing) remain equivalent.
  Keep Cog/Conveyor restore side effects outside this pilot.
- [x] **9. Trace attachment gate.** Assess a bounded owner for active-segment
  installation/detachment in TraceSessionLauncher. Preserve verify-before-close,
  partial-install cleanup, suppression order and comparison-only authority.
  Require existing payload/failure-cleanup tests and affected run coverage before
  landing; reject an extraction that just adds forwarding callbacks.
- [x] **10. Guard scanning gate.** Share raw file IO only where missing-root,
  traversal and ordering behavior can be preserved. Keep scopes, predicates and
  lexical handling explicit. Compare file sets and run affected structural guards.
- [x] **11. S1 art table gate.** Pilot one ordinary ordered registration group.
  Keep custom/composite loaders explicit and preserve in-place replacement order,
  null suppression, mappings, patterns, palette and priority. Retain only if it
  actually improves readability without another loading framework.
- [x] **12. State mirrors gate.** Audit LevelManager/WorldSession writes and
  geometry/editor/seamless-load consumers. Remove no mirror without proving its
  lifetime and performance contract. Document retention if evidence is inadequate.
- [x] **13. Existing infrastructure closure.** Confirm test setup is already owned
  by HeadlessTestFixture/TestEnvironment/ROM conditions. Retain general manager,
  audio snapshot, physics/boss/zone/stage, network and transaction boundaries unless
  a specific redundant obligation is demonstrated. No universal new harness.
- [x] **14. Delivery.** Review combined changes; inspect category plan and preflight;
  run selected ordinary/guard tests plus domain checks. Attribute new failures with
  matched baseline tests. Integrate into develop without switching main's branch,
  verify integration as required, push only develop, inspect/remove accounted-for
  task worktree and delete the merged local branch.

## Validation strategy

Finish focused implementation before the combined broad run. Shared algorithm and
runtime changes require normal change-based validation; record exact selection,
commands, skips and failures. Use the pinned initial base for the combined diff,
and reconcile new develop commits before integration. Broad failures get bounded
matched baseline checks rather than speculative unrelated fixes. Consume and
acknowledge category diagnostics before delivery. Do not describe partial checks
as a full-suite pass.

## Decisions and results

- Planning: current source supersedes older guidance about CollisionRules aliases;
  its compatibility constructor is retained. Public API cleanup is not a goal.
- Planning: palette traversal and solid-array construction remain separate helpers;
  they have different inputs and validation ownership.
- Planning: the task uses one isolated worktree with disjoint worker file ownership;
  the coordinator owns documentation, combined validation and Git integration.

### Implementation and gate outcomes

- Items 1–6 implemented: SavePayloadReader, SolidWireCommands, SolidColorTexture,
  StereoPcmWavWriter, package-private Sonic2PlayerParticipants (18 consumers),
  IndexedPaletteUsage behind the two validators, and SolidProfileDecoder.
  Palette maps/claims remain lazy to preserve first-error order. Failed solid
  construction now assigns only on success; both callers are private constructor
  paths, so a partially decoded level is not externally published.
- Item 7 implemented: private SmpsSequencerConfig.Settings holds 55 builder-frozen
  fields. SmpsConfigBinding shares these while snapshotting end flags before lazy
  session-handler resolution. The public Builder's historical read-only live set
  view stays intact. Rejected eager handler arguments: a factory could mutate the
  set before its snapshot, or be cached before malformed input fails. Tests cover
  both counterexamples. The public Mod API signatures remain unchanged.
- Item 8 prototype (subsequently rejected below): Buzzer used compact capture; public
  BuzzerRewindExtra remains for API compatibility. New checks exercise six fields,
  recreation, reusable snapshots, flame initialization and projectile timer replay.
  This does not certify full projectile graph/route/team/viewport rewind breadth.
- Item 9 closed, retained: active trace attachment state spans source-tail
  verification, anticipated dynamic-art admission and partial-install suppression
  (TraceSessionLauncher.openRunPayload, closeRunSegment, detachAndCloseRunPayload).
  Moving cleanup alone leaves these ownership obligations distributed; a helper
  would largely call back into the launcher. No net ownership simplification was
  established, so no trace runtime change lands.
- Item 10 implemented narrowly: the identical optional-root sorted Java traversal
  in TestSingletonLifecycleGuard and TestTraceReplayInvariantGuard now uses
  ObjectGuardSourceScanner. Missing/file roots, immutable results, suffix-only
  matching (including directories), and portable ordering are preserved.
- Item 11 closed, retained: only 15 buildArtSheetFromRom calls exist in the large S1
  provider. Ordinary calls interleave custom loaders: lamppost/signpost/bridge,
  custom cannon, edge wall/rock, custom breakable wall. A table would introduce
  interrupted groups while retaining the ROM mapping explanations. The existing
  calls are clearer and preserve registerSheet's replacement order directly.
- Item 12 closed, retained: LevelManager's mirrors are populated from WorldSession
  in construction and inherited-level restore. resetGameplayState intentionally
  clears disposable local zone/act state without clearing durable WorldSession;
  resetState subsequently clears both through writeCurrent* helpers. Converting
  every read to WorldSession would conflate these lifetimes. LevelLayoutLookup and
  renderer/water collaborators also use the local level in hot paths. No removal
  without a dedicated lifetime/performance design.
- Item 13 closed, retained: HeadlessTestFixture/TestEnvironment/RequiresRom already
  own common setup. Existing level and game-loop collaborators handle the proposed
  general decomposition. Physics, ROM event machines, special stages, network
  isolation and creator transactions keep their present boundaries.

### Verification in progress

- Tool preflight passed with Java 21 and LUA_BIN=/usr/bin/lua5.4 (default Lua is not
  5.4). Existing main s1.gen/s2.gen/s3k.gen identities match documented SHA-1/CRC32.
- First combined focused run: 303 tests, two failures and one error, zero skips.
  All three were new-test assumptions: GLCommand flips Y around configured screen
  height; Sonic1's config has a null coordination handler. Corrected tests use
  encoded coordinates and S3K's real handler; production extraction unchanged.
- Native temporary WhiteTextureProbe passed RGBA bytes, dimensions, nearest filters,
  independent allocation/deletion and recreation across two hidden native contexts.
  It uses the compiled production SolidColorTexture; remove probe after review.
- Combined category plan selects 2,586 ordinary classes and all structural guards.
  Shared runtime changes require this full selection; no proportionate narrowing.

### Commands

Run from the task worktree with Java 21 and `LUA_BIN=/usr/bin/lua5.4`.
Set `OPEN_GGF_REPO` to the absolute main-checkout path. Command records below
normalize that machine-local prefix to this variable. The verified ROM properties are:
`-Dsonic1.rom.path=${OPEN_GGF_REPO}/s1.gen`,
`-Dsonic2.rom.path=${OPEN_GGF_REPO}/s2.gen`,
`-Ds3k.rom.path=${OPEN_GGF_REPO}/s3k.gen`.

- Initial focused: `python3 tools/testing/maven_queue.py -Dmse=off
  -Dtest=TestSavePayloadReader,TestSolidWireCommands,TestStereoPcmWavWriter,TestDataSelectSessionController,TestS3kDataSelectProfile,TestS3kDataSelectPresentation,TestSfxRenderToolEntryPoints,TestSonic2PlayerParticipants,TestObjectPlayerQuery,TestSonic2TriggerParticipation,TestS2MandatoryTraversalParticipation,TestModPaletteUsageValidator,TestSolidProfileDecoder,TestModLevel,TestSonic3kLevelInMemoryConstruction,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestS3kAiz1SkipHeadless,TestBuzzerRewind,TestGenericRewindEligibility,TestObjectGuardSourceScanner,TestSmpsAssetCatalog,TestS3kSfxRuntimePathWithMusic
  <ROM properties above> test -B`.
- Corrected tests: `python3 tools/testing/maven_queue.py -Dmse=off
  -Dtest=TestSolidWireCommands,TestSmpsAssetCatalog test -B`: 16 tests,
  zero failures/errors/skips. These replace the three initial new-test failures.
- Combined: `python3 tools/testing/run_categories.py
  --base 94a41febdde969bb862e2b3c1e142d60fcdb7429 --run`, with ROM properties
  in MAVEN_OPTS. Candidate commit `c41291ae7` (documentation follow-ups do not
  change tested source).
- S2 domain: `python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-replay
  -Dtest=TestS2Ehz1TraceReplay,TestS2Ehz1BuzzerSpawnRegression,TestS2OozLevelSelectTraceReplay,TestS2MtzLevelSelectTraceReplay
  <ROM properties above> test -B`.

### Broad-run findings and revised rewind decision

The ordinary lane on c41291ae7 completed in 739.65 seconds: 2,586 reports,
20,468 tests, 3 failures, 0 errors, 18 skips. Guards did not start: the coordinator
edited this work list during the ordinary lane, changing the runner's working-tree
fingerprint. The corrected final run must keep documentation unchanged as well as
source until both lanes finish.

- `TestSonic2LiveObjectRewindRegressions.legacyOwnersAndSnapperMappingRemainConcrete`
  and `.buzzerForcedReconstructionMatchesIndependentlyAdvancedControl` failed because
  the pilot removed the concrete Buzzer payload. The latter is the established
  production ObjectManager negative control for non-Masher legacy dispatch,
  including forced recreation, immediate recapture and subsequent trajectory.
  Coconuts has no equivalent live reconstruction control in that class. Replacing
  this coverage and reviewing public payload behavior is disproportionate to the
  25-line extraction. **Item 8 is closed as retained:** restore the original Buzzer
  override and remove the migration-specific test, preserving existing assertions.
  The c41291ae7 prototype is rejected and will not appear in merged task history.
- `TestS1GameplayAudioTimelineCli.shellUsesAbsoluteBootstrapToolsAndRejectsInjectedEnvironmentBeforePathLookup`
  failed `expected 0, actual 4`. The runner already adds absolute ROM properties;
  coordinator-supplied MAVEN_OPTS contaminated the child shell environment, which
  this test deliberately rejects. Final runs omit MAVEN_OPTS. No tooling change.
- Skips: opt-in benchmarks/soaks/routes/native captures and local audio references;
  two unavailable EGL/OpenGL probes; existing CPZ spin-tube assumption. No missing
  stock ROM skips. The separate native white-texture probe passed. Broader native
  rendering/opt-in route certification remains outside this maintenance delivery.

The earlier item-8 implementation notes describe the rejected prototype, not the
final scope. Level coverage/backlog and Buzzer source remain unchanged in the final
diff. The final production reduction is measured after this reversion.

### Pre-integration result

- Corrected final-source focus (`TestSonic2LiveObjectRewindRegressions` and
  `TestS1GameplayAudioTimelineCli`): 15 tests, zero failures/errors/skips.
- Full separate guard run (`python3 tools/testing/maven_queue.py -Dmse=off
  -Pguards test -B`): 668 tests, two failures, zero errors/skips. Both failures
  reproduce on baseline `59d5b8881` (Java/tools/POM identical to pinned `94a41febd`):
  `TestBuildToolingGuard.supportedDocumentationMustUseDirectMavenAndExplicitHookBootstrap`
  still expects old direct-Maven guidance; and
  `TestNoAssertionFreeDiagnostics.noAssertionFreeTestMethodsUnderTestsTree`
  flags existing `FbzRouteEvidenceProbe#printEvidence` and
  `LevelSolidityMapProbe#writeSolidityMap`. Baseline command:
  `python3 tools/testing/maven_queue.py -Dmse=off -Pguards
  -Dtest=TestBuildToolingGuard,TestNoAssertionFreeDiagnostics test -B`.
  Compared complete assertion messages by class/test identity: exact matches.
- The S2 domain command ran eight tests, with three trace failures and no skips;
  all five Buzzer spawn regressions passed. Baseline ran the same three full trace
  classes on `59d5b8881`, with identical ROM properties and trace profile. Every
  complete parsed JSON report matched, including all error/warning spans, values,
  bootstrap fields and verification groups:

  | Fixture | Compared frames | Errors | Warnings | First error |
  |---|---:|---:|---:|---|
  | S2 EHZ1 | 5,852 | 16,388 | 0 | frame 6, dynamic_art.outstanding_transfer_ids; expected [2], actual [] |
  | S2 MTZ1 | 10,134 | 30,667 | 0 | frame 6, dynamic_art.outstanding_transfer_ids; expected [2], actual [] |
  | S2 OOZ1 | 11,019 | 33,225 | 0 | frame 6, dynamic_art.outstanding_transfer_ids; expected [0], actual [] |

  These are pre-existing divergences; the task does not move a trace frontier or
  claim these fixtures pass. Their owning timing/art behavior is unchanged.
- Independent final source review found no actionable issue. Buzzer was restored
  after the broad-run evidence; existing tests and its public payload remain intact.
  `TestModApiSignatureSurface` passed all nine checks in the ordinary lane.
- Native probe and all local Markdown link targets passed. Consumed category
  diagnostics were acknowledged and deleted; remaining temporary reports are
  removed after comparison. The final task commit squashes the rejected prototype
  out of the integrated code history while preserving its decision evidence here.
- Incoming develop commits `072ddec8a` and `59d5b8881` are documentation-only,
  disjoint from this task; preserve both during integration.

### Integrated verification

Task commit `6b95c6305` was merged cleanly into develop as `1cfe9ef82`,
preserving both incoming documentation commits. The final production Java diff
removes 346 lines overall. No public Mod API signature or gameplay timing rule
was changed.

At `1cfe9ef82`, with Java 21 and Lua 5.4, the completed command was:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 94a41febdde969bb862e2b3c1e142d60fcdb7429 --run
```

Run `20260915T094329Z-da339c14` selected the full ordinary suite and fresh-JVM
guards. No MAVEN_OPTS was injected and no tracked files changed during the run.

- Ordinary: 2,585 reports, 20,466 tests, zero failures/errors, 18 skips;
  765.49 seconds. All skip identities/reasons were inspected: the opt-in,
  graphics, local-reference and CPZ limitations described above remain; no stock
  ROM was missing.
- Guards: 84 reports, 668 tests, two failures, zero errors/skips; 175.57 seconds.
  Both class/test identities and complete assertion messages exactly match the
  baseline failures documented above. The combined command exits 1 for those
  inherited failures; this is not a claim that every verification gate is green.
- Integrated Java/tools/POM match the reviewed task tree exactly. Domain trace
  comparisons and native texture checks remain applicable to unchanged code.

Delivery completed: develop was pushed through `e895a062c` after policy and
release-tree checks passed. The clean task worktree was removed, its fully merged
local branch deleted, and stale worktree metadata pruned. Consumed category and
matched-baseline diagnostics were deleted. Original dirty disassemblies and
untracked user files remain untouched. This documentation-only closure is pushed
as a follow-up; unchanged engine tests are not repeated.

Concurrent delivery note: SOZ merge `4bd85d687` arrived after this task's completed
broad run, between the verification-record and closure commits. It was preserved
and included in the develop push through `6fbe2c276`. Its six Java paths are
disjoint from this simplification diff. The full ordinary/guard measurements above
apply to `1cfe9ef82`, not to the later SOZ code; SOZ's bounded validation and
upstream reconciliation are recorded in
[its work plan](2026-09-15-soz-methodology-v2.md). No simplification source changed
after the measured run.
