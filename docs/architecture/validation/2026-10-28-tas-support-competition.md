# TAS Competition support checkpoint — 2026-10-28

## Outcome and ownership

Assessments `s3k-competition:1` and `s3k-standalone:2` concern one feature:
original Sonic 3 Competition. **Neither ID is promoted by this worker.**
Original and locked-on resources are now admitted separately; all five arenas
pass real two-human entry, controls, clocks, destructive rewind and pixel replay.
Original S3 also passes a positioned Time Attack goal/results/save/restart/back
consumer check. Complete controller routes remain unqualified. Failed speculative
drivers and an unanchored publication-input candidate are not unsupported verdicts.

Base `8945c0a789b27be57b2361c70f561f31872a76ca`; isolated branch
`feature/ai-tas-support-competition-20261028` at
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-c9c8b8e5`. Hooks are installed. Production
source checkpoint `895b76b67`; test checkpoint `dd7fa6fdc`. The worker owns the
competition package, precise tests, contextual provider/service arm and approved
explicit collision-profile seam. Parent owns shared loop/title-call-site composition,
classification/changelog/discrepancy/coverage reconciliation, broad smoke and the
four mandatory S3K gates. Inherited dirty S1/S2 disassemblies were preserved.
No main-workspace mutation, push, integration or release suite was performed here.

## Historical evidence and cartridge identity

The 2026-10-26 report at `964c29d25fdab09fe8edd4acbe541a27fc64904c`
observed TWO_PLAYER returning to title because the stock native provider was
absent. That negative is preserved in the historical audit. At this task's base,
substantial locked-on Competition code already existed, but the module still did
not register the title provider. Rebuilding that engine or restoring the old
missing-host defect would have been incorrect.

The supplied `${S3K_ROM}` is the absolute original locked-on `s3k.gen` path used
in every ROM request: 4 MiB, CRC32 `63522553`, SHA-1
`CFBF98C36C776677290A872547AC47C53D2761D6`. Its immutable upper cartridge window
at `$200000`, length 2 MiB, is original `RomIdentity.S3`: CRC32 `9BC192CE`, SHA-1
`75E9C4705259D84112B3E697A6C00A0813D47D71`. Existing catalogue/classifier APIs
supply this window; no ROM file was copied, linked, patched or downloaded.
Product admission verifies the whole selected view against `RomIdentityTable`.

Research uses immutable `skdisasm` pin
`8b2cf3c4178f2382f844da67798de4befc50c5e0`. Original `Competition_Menu`
(s3.asm:11589-11670) independently loads its program-owned menu bindings.
ROM operands at `$95AE/$95B0` and `$9616/$9618` pin the original menu resources
`$19D2A2` and `$19F46E`. Original `LoadSolids` (s3.asm:7347-7361) supplies two
separate `$600`-byte index banks. `sub_1074C` (s3.asm:17490-17514) reads **words**,
where locked-on decoding reads a byte at stride two. Original AngleArray/height/
width tables are `$704A0/$706A0/$726A0`, with 512 angles and `$2000` bytes per
profile plane. No scanner or global address constant was relaxed.

## Implemented bindings and contracts

- `S3kCompetitionResources` selects verified original-S3 versus locked-on resources.
  References inside the S3 asset chip relocate only within this competition owner;
  low program/table references have explicit native bindings. Table contents are
  never relocated twice. No runtime asset comes from disassembly or another ROM.
- Original `S3CompetitionGame` decodes only the five act-zero competition arenas
  through ordinary `LevelResourcePlan`/`LoadOp` and `Sonic3kLevel`. Preview act-one
  rows are not extra races. Existing `$C0` engine level namespace is normalized to
  native arena indices 28..37. Locked-on continues using the source-aware base module.
- Explicit `CollisionProfile` owns addresses, profile count and index encoding.
  Its existing three-argument form and every old level constructor retain locked-on
  addresses, 256 profiles and `BYTE_AT_STRIDE_2`. Original S3 selects 512 profiles
  and `UNSIGNED_WORD`; complete ROM ranges, word boundaries and both banks' index
  bounds are checked before chunk decode. Actual original records beyond entry 255
  distinguish word decoding; all loaded arena chunks compare to independent ROM words.
- Original wide DPZ sand-bar mapping `$35668` is pinned by opcode `$217C` at
  `$354F8`, operand at `$354FA`; alternate spring entries are explicit as well.
  TimeAttack_Records (s3.asm:13338-13347; sonic3k.asm:15186-15195) uses different
  icon slices: original `$EE0-$20=$EC0`, locked-on `$F60-$20=$F40`. Original
  `$41F9` at `$B0B2`, operand `$FFFF0EE0`, independently pins that difference.
- `NativeTitleModeProvider.competitionScreen(NativeMenuHostContext)` defaults to
  the old no-argument method. The immutable internal context validates engine,
  root module, input and publication capabilities. The S3K provider creates fresh
  host/views per visit, owns native records/save policy and composes an independent
  single-player Exit supplier defaulting to `NONE`. Existing three-argument Exit
  compatibility remains. Parent owns the two actual loop call sites and title policy.
- Record persistence uses existing SaveManager ordering with separate cartridge keys.
  Visit retirement closes world/views, stops its music and prevents reinitialization.
  Menu/selection and result music requests use native `$2D/$28`; original level
  music reads `$4680+index` and maps through `SONIC_3.musicId`. Parent separately
  composes the completed stock SMPS profile/loader and `getSoundMap` binding.

These changes translate game/resource semantics. No CPU/cycle simulation, virtual
VDP/beam/status protocol, arbitrary RAM corruption compatibility or trace-row state
hydration was introduced.

## Focused execution

All test commands use Java 25 (`JAVA_HOME=/usr/lib/jvm/java-25-openjdk`),
`LUA_BIN=/usr/bin/lua5.4` and `python3 tools/testing/maven_queue.py`. Rendered
requests additionally use `env -u DISPLAY -u WAYLAND_DISPLAY`,
`-Dopenggf.headless.gl=surfaceless-egl` and `-Ds3k.rom.path=${S3K_ROM}`.
`${S3K_ROM}` denotes the supplied absolute path; it is not a skipped prerequisite.
All requests used `-Dmse=off ... test -B`; no full suite was run.

| Selection / tested source | Result | Queue / Maven wall |
| --- | --- | --- |
| Existing eight classes: NativeHost, Session, Screen, Assets, ArenaPixels, MenuPixels, RosterRestore, Pause; base plus initial resource port before later cue/namespace refinements | 97 passed, 0 failed/errors/skips; receiving evidence, **not** an untouched-base or final-candidate pass | 1292 s / 1m41s |
| `--lean '-Dtest=TestS3kNativeTitleModeProvider'`; production `6d8ec1147`, route-only test follow-up `3e14f7afd` | 3 passed, 0 failures/errors/skips | 3171 s / 59.059 s |
| `'-Dtest=TestS3kCompetitionScreen,TestS3kCompetitionNativeHost'`; same production source | 18 + 24 = 42 passed, 0 failures/errors/skips | 2485 s / 34.790 s |
| `'-Dtest=TestS3CompetitionProduct,TestS3CompetitionCollisionProfile'`; `895b76b67` | Decoder 3 passed; product 2 passed/5 failed, 0 errors/skips. One product failure was an overstrict per-arena beyond-255 witness: BPZ/CGZ do not contain such nonzero later records. Aggregate witness corrected, every actual chunk remains compared. | immediate / 1m43s |
| `'-Dtest=TestS3CompetitionProduct'`; production `895b76b67` plus test correction retained in `1591b2a8a` | 3 passed: original operands and both cartridges' five-arena entry/control/clock/destructive replay. 4 full-route driver failures, 0 errors/skips. | 25 s / 42.446 s |
| `'-Dtest=TestS3CompetitionProduct#grandPrixRealControllerRouteReachesEveryTrackAndResults+singleEventRoutesFinishRestartAndPersistOneHumanRecord'`; `1591b2a8a` | 4 failed bounded driver candidates, 0 errors/skips; charged-start/run/blocked-jump sequences did not complete Azure. No physics conclusion follows. | 37 s / 38.599 s |
| `'-Dtest=TestS3CompetitionProduct#publishedSoloMatchAzureInputCandidateReachesNativeGoal'`; `1591b2a8a` plus controller-only candidate retained in `dd7fa6fdc` | 2 failed under inferred GO alignment, 0 errors/skips. Original/locked-on identical at world frame 1374, lap 0, no result. This is unsynchronized input evidence, not native parity or an unsupported verdict. | immediate / 35.665 s |
| `'-Dtest=TestS3CompetitionProduct#originalPositionedTimeAttackGoalRewindResultsSaveRestartAndBack'`; settled test source `dd7fa6fdc` | 1 passed, 0 failures/errors/skips. Actual countdown/clock, positioned six crossings, final-cross restore/forward, native results pixels/save, new world/clock and pause/back/title teardown. | 15 s / 33.641 s |

Exact receiving eight-class selector:
`TestS3kCompetitionNativeHost,TestS3kCompetitionSession,TestS3kCompetitionScreen,TestS3kCompetitionAssets,TestS3kCompetitionArenaPixels,TestS3kCompetitionMenuPixels,TestS3kCompetitionRosterRestore,TestS3kCompetitionPause`.
These results are separate selections and must not be summed into a full-suite claim.

Earlier failed probes remain historical evidence: the initial product request at
`6d8ec1147` was stopped (exit 130) after a driver assumed mode-row reset and spun
on an unconfirmed cursor; loops were bounded and native retained selection preserved.
`3e14f7afd` had 2 passes/2 route failures/3 original resource errors in 7 cases;
`9bd6d9dd4` exposed the separate original Time Attack icon slice; `f1c376c69`
had 2 passes/4 route failures/1 missing selection-table profile lookup. The approved
word-index decoder then removed original actors' fall-through and made reached
original/locked-on route states identical. An ambiguous constructor compile was
fixed by prefixing CollisionProfile; a later goal-fixture compile used the sprite
view instead of the native body and was corrected before execution. One redundant
preparation request was cancelled before execution. No request was restarted to
escape queue waiting; one observer was used per job.

`git diff --check`, installed commit hooks and immutable citation checks passed.
The latter reports existing unverifiable prose references and nearby labels, with
no hard failures; warnings about dirty S1/S2 reference revisions are preserved.
Temporary Maven diagnostics are consumed under `target/`, not archived externally.

## Route and output ledger

| Native act-zero arena | Reached short behavior, both cartridges | Full route |
| --- | --- | --- |
| Azure Lake `$0E00` | real menu/entry/GO, Sonic P1 / Tails P2, neutral-P2 clock, destructive roster restore, forward state/cameras/pages/pixels | Unqualified; ordinary driver probes stalled; publication candidate awaiting observed native entry/GO |
| Balloon Park `$0F00` | same two-human entry/control/rewind/output; original word indices compared | Unqualified |
| Desert Palace `$1000` | Knuckles P1 / Tails P2, same control/rewind/output; original word indices compared | Unqualified |
| Chrome Gadget `$1100` | Sonic P1 / Tails P2, same control/rewind/output; original word indices compared | Unqualified |
| Endless Mine `$1200` | Sonic P1 / Tails P2, same control/rewind/output; original word indices compared | Unqualified |

Original Time Attack goal/results/save/restart/back is separately qualified at a
**positioned boundary**. No lap count, finish flag or timer was assigned; actor
centres were positioned around the actual native goal, with ordinary frames between
crossings. This is not a traversed race. Match Race, Time Attack and GP component
rules have inherited receiving tests, but complete events/routes are not certified.

Rendered evidence uses real ROM data and NullAudioBackend. Every short replay
compares all 320×224 pixels before/after destructive restore. Inspected original
Time Attack results contain actual lap times/record and native glyph/portrait art.
These are engine output/replay checks, not independent native-emulator pixel parity.
External `competition/engine-pixels/` holds the pictures, including named failed
route probes; it contains no ROMs or Maven logs.

## Publication input and remaining prerequisites

The parent independently verified TASVideos **1713M → 2953S**, nitsuja & mmarks:
**solo Match Race**, P2 neutral, not a two-human GP strategy. GMV SHA-256
`cadc9a799a9eac37f43b3ee602ea296f87b72eeb39356e0f8c933c4fe4c12f14`,
7263 input records. The checked-in gzip is controller-only source [394,1431),
1037 records, preserving A/B/C independently and simultaneous L+R. The candidate
aligns source 538 to the engine's real GO; source GO is inferred and must not be
presented as observed. Input extraction/roundtrip is not runtime qualification.

A parent-owned bounded isolated native pass will establish actual entry/GO and
logical-input ordinals using the original S3 memory window. No native job was run
by this worker. Its observations may compare state and establish test input
alignment; they must never supply engine gameplay values. Until that anchor and
an actual finished representative route exist, neither original scenario ID can
be promoted from locked-on success, eager asset loading, title routing or parser
acceptance. Published-input mismatch currently establishes no physics defect.

Other inherited/unqualified obligations: original title → Competition traversal
and stock audio after composition, complete GP/Match/Time Attack breadth, native
pixel comparison, all viewport presets (only native 320×224 here), every materially
different character route and repeated load/restore combinations. Miniature mode
has one/two human slots (Time Attack exactly one), not campaign CPU followers;
no donor/team breadth is inferred from unrelated campaign settings. This record
is not certification against the complete level-test standard or a publication movie.

Worker checkpoint: no live Maven/test/native process; all owned tests have ended.
The parent-owned native anchor pass is a separate pending prerequisite. The next
action is to apply its independently observed input boundary to the controller
qualification, retaining all finish/goal assertions and comparison-only authority.
