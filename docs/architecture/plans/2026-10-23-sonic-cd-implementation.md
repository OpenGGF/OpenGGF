# Implement Sonic CD with complete regional parity

Date: 2026-10-23; source pause 26 October; delivery 27 October; resumed 28 October 2026. Status: partial implementation, not complete game support.
Design: [fourth-game architecture](../designs/2026-10-23-sonic-cd-fourth-game.md).
Engine baseline: `b979916808b7f9379ede2b5dcdde1bcdc8bf8357`.
Reference pin: `1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff`.

Deliver Sonic CD as a stock game alongside S1, S2, and S3K. Complete the USA
release first, then Japan and Europe. USA MVP means the complete native USA
game and its required features. A playable Palmtree Panic demonstration is
an intermediate proof, not the MVP or a full-parity claim.

The initial 23 October task established the reference setup, architecture and
execution plan. Subsequent bounded implementation is recorded below and in the
[partial level matrix](../validation/levels/scd-palmtree-panic-act1-present.md).
The full USA game, then Japan and Europe, remains the objective. No release
number, publication date or completed stock-game claim is assigned here.

The user resumed gameplay and integration tooling on 28 October. The historical
[pause handover](2026-10-26-sonic-cd-handover.md) remains the 27 October delivery
record. The ordinary four-jump route now returns through call7000, with real
pre-save time-warp stars, cancellation and complete full/course replay. An extra
controller jump reaches a Spinning Disc attachment on7178. Controlled Main now
returns through the native common tail, and the permanent five-jump route
now returns7728 after the native timer210 save, timer230 restart HIGH-byte
write,22 old-LevelStart white-fade waits and the actual detached WARP scene.
First7729 stops at `SonicCdGameModule.RestartBoundary.R11B_LOAD` before
loading the Past destination or sampling its input. The old actor/World remain
retained: levelFrames7350 and old Frame V-int7372 do not advance during WARP.
Raw period80 still marks the destination; Past loading, act completion and
regional parity remain open.

## Camera-clear player projection — 29 October 2026

One fixed `clearLevelStartCameraPlayerValues()/0` method now projects the
player-owned fields of native FFF700..FFF800 clear onto the existing Frame.
It clears physics, current bounds/boss flag, the represented camera/global
scratch, control lock, history index and art last-frame/dirty flags. It preserves
complete history/art byte buffers, all actor motion/header/timers/animation/
links/rotation/fractions, controller latches, other globals, display, bindings,
pause, source, capture owner and actual clock. Every new reference is prepared
before adoption. No input, Body, DPLC, upload, placement, clock tick or new Frame
executes. Actual private load phase and the other clear owners remain unbound.

The native WORD timeWarpTimerFFF786 is inside this clear; external BYTE
timeWarpFF1521 is not. The later globals clear correctly preserves that WORD.
Native lock1 is conditional title-card creation; carried PLC bit1 skips it.
No unconditional title-lock setter or late ready/default value was added.

Tests-first84280 reached missing-method compilation errors with no JUnit, tool2/
incomplete and Maven1. Complete source before/after matched
`f1fb7bb3c5c4a8a641150166d3dd039fa8caf706047d391f3fc8c7a2bac082b8`.
The 801-case affected run33375 then completed801/0/1/0: all779 unchanged
consumers passed, while one new guard fixture selected dirty mapFrame205 before
its guard check and correctly hit DPLC bound198. RootR2 stages an explicitly
absent primary only for that component guard vector. Production did not change.
Independent static review's no-P1/P2 scope missed that cursor literal; observed
execution supplies the correction. All14 inherited tests/helpers remain exact.

Narrow follow-up57073 passes **22/0/0/0**, terminal tool/Maven0, at
00:52:57.855252Z. Before/after full source matches
`2db5bcb105a944ba021a260ad8f9384eefcd14e277ce1151388755e3f584852b`
at HEADd96 plus the two candidate files,16211 paths/981727038 bytes/five gitlinks.
Admission00:52:20.149710–00:52:57.855252Z;37.706s execution,43s queue. Earlier
33375 source382936732ac3a14115d619b63a4f80b779ad06b6c36b879d6a03c2be0b2d8536
was unchanged;103.028s execution and325s queue. All fresh exact case counts/
identities, Java25/cwd/media and skips were inspected;10 earlier plus2 follow-up
XML/TXT files removed. These are separate selections, not a fresh801-case pass.
The unchanged779 consumers need no ceremonial rerun after this test-only fix.

Full production removal inverse recovers112072 bytes; removing the appended
checks and small RootR2 fixture setup recovers20268 test bytes. No shared engine
algorithm or existing method body changed. No broader smoke/full-suite, native
or EGL run was needed for the bounded port. Natural7728/first7729 R11B_LOAD,
actual InitVDP/controller/module/current-Past/HUD/dynamics and regional gameplay
remain unfinished.

## Programme-load and Start values — 29 October 2026

Two package-private components now represent prerequisite values without admitting
an actual programme transition. `SonicCdProgramLoadServices` preserves the ordered
CDDA settings0380/8380 before current-driver comparison, selector-before-file
adoption, source-derived typed variables, exact-instance matching reuse and the
Past queue-slot0 request81. Unknown installation stops with its completed scalar
prefix. Actual original PCM bytes come from the authenticated detached bundle;
there is no generic RAM bank, fake different driver, audio processing, synthesis,
InitDriver execution or ready flag. With only one admitted USA/R1PCM identity,
a genuine known-different-driver replacement and malformed-after-selector path
remain unavailable through public ingress.

`CdProgramInitialization` keeps controller-bit knowledge, marker-equality
knowledge and partial version-cache knowledge separately. The native conditional
read order distinguishes unknown facts from known zero. Four explicit execution
region inputs are independent of disc identity. Separate cache, marker and
controller writers preserve every other value; they do not run InitVDP or
establish actual setup. The module must execute and capture the real ordered
operations earlier in the visit, without injecting postconditions at7729.

Root tests-first55183 failed for the missing programme helper, and59256 for the
missing initializer; neither ran JUnit. Source fingerprints remained unchanged
across those respective checks. A source review confirmed R1 lacked complete
value equality for capture replay. The initial24608 attempt was cancelled130
during compilation with no test result and an unattributed cause. The necessary
repeat88640 reached the exact equality assertion and failed1/1/0/0. RootR2 adds
only equals/hashCode for the eight immutable observation types, leaving native
transitions unchanged. Identity checks still preserve the exact reused driver;
value equality grants no World, Frame, phase or invocation authority.

Combined source-bound4528 passes **40/0/0/0**, terminal tool/Maven0, at
2026-10-29T00:21:57.173325Z. Its five exact suites are initialization14,
programme-load outer4+nested7, actual PCM source4 and existing programmes11.
Full source before/after matches
`3686b41f96d945606d4465aff3db97cab860299089290b49b69b6bf19c06e087`
at HEADb25e62cb9 plus these four files:16211 paths/981709668 bytes/five gitlinks.
Admission00:20:15.889950–00:21:57.173325Z; execution101.283s, queue0s;
total delivery wall was not measured. Every fresh identity/count, Java25.0.5.0.2,
worktree/media property and zero-skip result was checked. Ten consumed XML/TXT
files were removed. Independent initialization review found no P1/P2; the
programme review's equality P2 has the observed regression and RootR2 above.

These inactive private-CD value classes use proportionate direct source and
component checks. They change no live module, shared algorithm, Frame, renderer,
clock or capture owner; broad smoke and release-only full suite were not run.
Actual programme admission, initialization/presentation/controller setup, PCM
lifetime ownership, current Past scene, HUD and dynamic objects remain unbound.
Natural7728 returned / first7729 R11B_LOAD and whole-USA→JP/EU are unchanged.

## Detached PCM source prerequisite — 28 October 2026

The same authenticated USA disc owner now retains the original R1PCM file with
WARP/R11B before stock preparation closes it. The public four-component Source,
old image getters and complete old admission/failure prefix remain unchanged.
One private immutable byte view and `r1Pcm()/0` getter add the exact217866-byte
SNCBNK1B.BIN, SHA256
`fd7ab10c908bd26545afc5bf5da0765e7178fe8fe2929b15957acf9bd7d6e558`.
The factory checks entry extent, returned length and complete file hash. It does
not parse PCM as MMD, copy sector tail, install a driver, initialize a chip, set
readiness or perform audio work. JP/EU bundle admission remains unavailable.

Independent native/media research distinguishes the actual file-load completion
from lazy InitDriver in later PlaySoundID; an accepted queued request may select
a different branch. The native two CDDA settings0380/8380 precede current-driver
comparison, while its selector write precedes file completion. A future semantic
loader must preserve those prefixes and actual installed/unknown identity. Silent
A playback and matching file names do not establish historical installation.

Four actual-USA cases froze before production. Root tests-first85947 failed
only for missing r1Pcm()/0 at23:38:16Z,34.438s/noJUnit; full source
`2b813e610de3884dd0f8578e292460a1bff7ecd15a2f67c10e0d2ad4a04f1452`
matched before/after. One exclusive source creation followed23:39:09Z; inherited
bytes recover exactly outside the declared changes. Independent static review
found no concrete P1/P2. Frozen author sources/tests were not repaired.

Source-bound82707 passes **50/0/0/0**, terminal tool/Maven0: new source4,
existing programmes11, stock preparation21, Past assets14. Complete source
`fb06e333a436b06c574d805a62f82563c0e7bf84eb7f967ae675a87533812fbc`
matched before/after at HEADbaabd8ba3 plus candidate,16207 paths/981646233 bytes/
five gitlinks. Admission23:40:38.459016–23:42:29.963631Z; execution111.505s and
queue19s separately observed, totalwallunmeasured. All literal identities/counts,
Java25/cwd/media and zero skips were inspected;8 consumed XML/TXT removed.

Actual checks cover original file/hash/header/entries/zero template, detachment,
shared immutable view, independent cursors and exact bounds. Malformed PCM extent/
content defensive paths cannot be exercised through authenticated public ingress
without earlier source rejection; no fake usable owner or new seam supplies that
coverage. Old constructed rejection controls remain unchanged. This inactive
source-retention change uses proportionate direct media/stock/asset consumers,
with no repeated controller/EGL, broad smoke or release-only full suite. Native
module/load/init/PCM programme update/synthesis/current Past presentation remain
next, with natural7728/first7729 R11B_LOAD unchanged.

## Static ordinary-test inventory — 28 October 2026

The existing read-only declaration census now has a separate
`--test-inventory FULLY.QUALIFIED.CLASS` mode and optional `--expected-count`.
For a bounded ordinary top-level class, trusted literal JUnit annotations and
ordinary method/lifecycle shapes, it reports method names and a proposed source
count. Parameterized/dynamic/nested/inherited/conditional/unknown scopes remain
incomplete with null counts. It neither discovers JUnit invocations nor evaluates
assumptions/extensions/configuration. Strict source-bound executed-XML checks in
the integration runner are unchanged; a count is never adjusted automatically.

At pinned source669f7bc46 the actual Past retail class has five ordinary tests,
excluding three helpers. Expected4 exits1; expected5 exits0. This catches the
previous stale inventory before Maven without claiming a new engine run.
Author and independent40-test runs plus nine independent controls found one P2:
a visible imported type named `org` could shadow a qualified annotation spelling.
Root observed six failing subcases in three regressions, then removed that false
trust for direct/static root imports, declared root types and unknown static
on-demand imports. The known Assertions wildcard and direct Test import controls
remain supported. Final **44 Python tests pass**, zero failures/errors/skips;
syntax, help, both skill validators, mirrors and links pass. All existing26 census
cases remain unchanged. Original R1/review/frozen source remain available outside
the repository; owned scratch was removed. No Java/Maven/native/media execution
was needed for this Python/skill-only change, and no pass cache/receipt is written.

## Same-Frame addressed warp loading — 28 October 2026

The existing Frame now exposes raw preimages and addressed adoption for exactly
nine `LoadTimeWarpData` destinations: X/Y integer WORDs, flags BYTE, GV/XV/YV
WORDs, current bottom, conditional mini and left bound. Position fractions,
signed velocity representation and every other complete State/Environment field
are preserved. Unknown/stale preimages and targets owned by ring/time/water or
camera partitions reject before adoption. Public values supply no invocation,
source-order, conditional-read or exact-once authority; the actual private loader
must retain those obligations. Equal values/restored cuts intentionally remain
observational value checks. No checkpoint port or arbitrary restore is reused.

A separate fixed `ClearFlowers` counter writer establishes native LONG zero at
FF190A after the early selector-C wait, retaining the same Frame/CaptureOwner.
Later waits increment that counter normally. It is neither an extra clock nor a
new visit and does not clear ordinary levelFrames or the other flower-area owners.
All71 inherited method bodies, capture partitions, fields and old services are
byte-exact; the production delta is one import and three additive methods.

Tests-first build1010 failed for the three absent APIs, without running JUnit;
full source `f43e48e5b415206df0924e1e4a97bb1cd8610fa0ba96d48d0410ca285faeab2b`
matched before/after. The new14-case fixture was frozen before production.
Independent source review verified native widths/order, full inverse, guard
ordering and oracles, with no concrete P1/P2. The optional zone6 mini vector is a
constructed helper-port branch, not admission of zone6 on the actual PP source.

Source-bound44049 completed **793/0/0/0**, terminal Maven/tool0, over the exact
five declared classes: new stores14, legacy Frame733, fixed entry12, Past Frame16
and saved-load18. The complete source remained
`03e1cca33ca58f3a994cff3e6c60744a311e4d8a5a4d5a3695d63e320db964c4`
at HEAD598892925 plus candidate,16205 paths/981603831 bytes/five gitlinks.
Admission23:26:47.461693–23:28:25.665671Z; execution98.204 seconds, queue374
seconds separately observed. Every fresh case/count/Java25/cwd/media property and
zero skips were checked;10 consumed XML/TXT were removed. A supplemental source
name check first appended parentheses absent from these actual Surefire names;
reading the literal names corrected only that inspection, without changing or
rerunning tests. No stale count reconciliation was needed for this run.

These additive private-CD operations leave ordinary services and shared engine
algorithms unchanged. The five affected owner/consumer classes give bounded
coverage; no repeated broad smoke or release full suite was warranted. Actual
module/PCM/init/current-visit/presentation/HUD/placement wiring remains next.
Natural7728/first7729 R11B_LOAD and complete-USA→JP/EU remain unfinished.

## Fixed entry writers and actual Past touch inputs — 28 October 2026

Four fixed same-Frame value ports now represent the selected nonzero-spawn
LoadPlayer ID/hurt writes, controller/bored-timer clear, represented player-global
clear and raw time-zone marker clear. They retain the existing clock, history,
physics, art, source and native-slot entitlement. The native timeWarp BYTE belongs
to the external contact owner; the Frame's timeWarpTimer WORD is preserved. Marker
clearing returns the prior marker without switching programs or normalizing any
other bit. All67 existing methods/bodies remain; the private module must still
authenticate actual phase/order/spawn and perform P2/external/global/display writes.
These ports do not run loading or establish that the native entry happened.

The actual original-USA B source appends ObjColSizes126 bytes at206ECA and
EnemyPoints8 bytes at206CE0 as non-PLC raw views. Complete old70 metadata rows and
ordinals remain, with the full manifest now72. The36 PLC sources/37 compressed
profiles,19 lists/four sections, caches and decode extents are unchanged. Existing
typed scanner/response constructors consume these views; no empty dynamic context
or new table reader is substituted. Seven source checks cover all63 unsigned size
pairs, real word/operand literals and the zero-selector/negative-offset/odd-index
boundaries, which remain separate from actual B object-pass authority.

Queued86975 completed Maven0 at22:56:24Z, observing **926/0/0/0 over12 classes**:
assets14, new touch7, load plan13, static Past retail5, native readers42, player
source15, section adapter27, Body/Movement34, Past Frame16, actor8, new writers12
and legacy Frame733. Java25/cwd/original-media/S2/EGL properties and every actual
case were inspected. Before/after source was unchanged
`bd15ad8482da81608dee5584928bae6fbe73ae88b2865839f925691dc8b5f85e`
at HEADc251 plus candidate,16204 paths/981573116 bytes/five gitlinks.
The admitted interval was22:54:36–22:56:24Z; queue wait is separately observed.

The strict runner rejected Root's stale retail count4 versus actual5, so that
invocation is not relabelled as an exit0 handoff. Five ordinary declarations in
the unchanged source independently resolve the inventory. A read-only fresh-JUnit
qualification checks all926 identities/counts/properties, terminal Maven0 and
current complete source before/after against the same admission/finish hash.
It is a reconciliation of that completed execution, not another engine run or
validation receipt. All24 consumed XML/TXT were removed. No source change or
missing/failed/skipped case was hidden to obtain this result.

The preceding tests-first19380 build failed only for absent new enum values/four
methods, running noJUnit; before/after
`8b0ab723839d2bea3807c187fa160517dba3e236590ec22509018561f6f55a58`
matched. Review caught one R1 test missing an import; Root's separate R2 adds only
that import with a complete inverse. No production repair was needed. Independent
reviews verified the literal native products, complete metadata inverse and all
12 writer/7 touch oracles, finding no other P1/P2. Frozen R1 inputs are preserved.

This private-CD metadata/two-row append and four additive fixed operations leave
all old method bodies, drawing, source guards, clocks and shared engine interfaces
unchanged. Direct metadata/native-reader/adapter/new and legacy Frame consumers
provide proportionate coverage; no repeat of the unchanged controller/EGL run or
release-only full suite was needed. Actual module load lifetime, startup palette/
scroll/PLCs, HUD/dynamics, source-ready actor and first ordinary Past frame remain
next. Natural7728/first7729 R11B_LOAD and whole-USA→JP/EU remain unfinished.

## Bounded integration progress — 28 October 2026

The optional run-only `--maven-progress concise` now filters the existing child
stdout sink separately from `--compact-output` structured results. Defaults,
Maven argv, selections, queue notices, direct stderr, source/dependency checks,
fresh XML and verdicts remain unchanged. The pipe retains only a512-character
pending prefix and reads4096-byte chunks; display is bounded to64 content records
plus one summary,34241 Unicode characters. Queue/direct-stderr/JSON are outside
that bound. Exact suppression/truncation counters remain visible; no full log,
receipt or result cache is written. Skill mirrors describe the separate modes.

Author and independent144-method Python checks plus independent adversarial
streams found one startup cancellation P2: constructor failure leaked a pipe,
and interruption after launch allowed the reader to close a reused descriptor.
Root added two meaningful failing controls, then serialized read/close ownership,
retired failed sinks and joined known launched readers. All146 Python tests pass
with zero skips; syntax/help/mirrors/links and both skill validators pass. Tests
use temporary synthetic Git and Python-only fake-Maven fixtures, not the engine.
All accounted check-tree dependencies and disposable fixtures were removed.

The actual source-bound engine request78608 also passes2106/0/0/0 with this mode:
it processed810 Maven stdout lines and displayed29 content records plus its
summary. Full fresh14-class evidence/source checks remained intact. A preceding
intentional RED likewise retained its actual failures and errors. These are
presentation observations, not wall-time improvement or whole-game certification.
The source result/fingerprint is recorded in the following player section; no
repeated broad/engine run was used solely to qualify Python output presentation.

## Source-bound Past player, Frame and native slot — 28 October 2026

Named Body/Movement factories now consume the immutable stage-player binding.
Legacy constructors keep their validation and Present admission. Actual Past0
and the loaded program's marked values use the existing native algorithms with
bound source data. Context-free Past low-X calls stop before the native charge/
direction reads; the explicit borrowed path uses the existing full TimeWarp
helper. The ramp handler and both air-steering restrictions test the actual raw
Present/PPZ1 predicates. Display expiry fades only raw Past0, then chooses music
from the masked period. A missing future goodFuture read stops at its owning
boundary; no zero default or audio playback is supplied.

A separately named Past Frame factory takes complete explicit existing State,
Environment, Sonic-art and clock values and creates a fresh existing capture
owner. It does not run loading, reset an old Frame, prime a cue or infer a native
clock seed. Old factories remain unchanged. The old typed assets getter explicitly
rejects a B Frame; the readonly common relation retains the actual aggregate.
SelectorC services raw input, publication, camera/flag copying and level drawing
before fastPLC and the existing Finish/count. It neither grants Body entitlement
nor borrows the different selector12 fade order. A named Past actor path uses the
actual B visual product and exact Past Level/aggregate relation, retaining the
existing native slot,24-pattern restore/bind order and same-owner rewind contract.

Fresh source-bound78608 completes22:29:05Z with **2106/0/0/0** over14 exact classes:
new source/native regressions34, Past Frame16, Past actor8; legacy Body315,
Movement312, Frame733, Runtime516, World53, presentation input10, actor18,
ObjectPass5, TimeWarp64, source relation15 and seven literal EGL identities.
All fresh counts/cases and Java25.0.5.0.2/cwd/original-media/S2/EGL/opt-in properties,
terminal Maven0 and unchanged before/after source
`e5f98f7e3f56f7d9446bd9b478d6b6696c6fe4ae7161fd7fdd2026e7f0959e47`
at HEADf7e14 plus candidate were inspected. Admitted22:26:48–22:29:05Z after1s
queue wait; all28 consumed XML/TXT removed. This fingerprint also includes the
separately tested optional progress-tool candidate. Subsequent edits are prose.

The first candidate's2089 zero-outcome result was rejected for three stale
inventory counts, independently reconciled to10/18/5; it was never certified as
the declared2111 pass. One lean negative attempt produced no freshXML and remains
unattributed. A normal diagnostic test-compile passed, then six targeted music
assertions reproduced the native discrepancy. Three air fixtures first reached
unrelated TOUCH_SCAN; setting the native bit7 skip in that explicit component
fixture exposed all three expected24/actual0 assertions. Root corrected those
native gates and music selection after RED. Frozen author sources remain intact;
four disclosed author-fixture getter/distance errors were corrected separately.
Both source P2 findings are resolved by literal native checks and fresh regressions,
with no physics fitting, empty dynamic-world declaration or effect replay.

The read-only enum audit kept all old ordinals (Sound3→5, unsupported21→22),
observed316 CD Java sources and found only the existing KILL_PLAYER-absence stream,
which remains valid. Its initial byte cap was insufficient for the Runtime fixture;
expanded bounded reads retained the full scope and stable hashes. This is lexical
consumer evidence, not compiler/complete-coverage proof. Independent source reviews
also verified the legacy factory/inverse preservation and actor ownership limits.

This bounded private-CD source/algorithm join changes no shared engine contract or
selection policy. Direct old/new owners plus actual controller/rewind/EGL consumers
provide proportionate coverage; an unchanged broad/full suite adds no new affected
contract here. These are component-native slots and services, not an executed Past
World. The actual module must still perform native clears/writes, new-visit loading,
queued selectorC work, from-white waits, genuine dynamics and first gameplay.
Natural7728/first7729 R11B_LOAD and the full-USA→JP/EU objective remain unchanged.

## Past art adapter and common terrain queries — 28 October 2026

The actual USA `CdR11bSectionArt` now binds all36 finite queued sources,19 PLC
lists and four section entries to the existing shared kernel and distinct Past
residency value. Immediate core/Std operations drain separately from queued
budgets/POP state and carry the same512-byte code table into RAM block decoding.
The RAM product is equality-checked against the already authenticated cached
product; it does not become a current-pattern write or another persistent bank.
The raw life-icon operation reads the actual quarter and writes four tiles at
B420. Complete-tile failure prefixes retain typed source/destination units.
Factories prepare no headers, preload no pixels and create no Frame or clock.

Three named `fromStageTerrain` factories reuse the existing collision placement,
floor/wall and ground algorithms with common immutable format views. Legacy
constructors, raw-word/result types, native arithmetic, exceptions and synchronous
angle-write prefixes remain intact. Actual Past retail witnesses deliberately use
cleared component rows; they do not supply a player position or gameplay ingress.

Fresh source-bound70384 completes21:34:03Z with **1051/0/0/0** across ten exact
classes: Past section27, constructed terrain27, original-USA terrain3, legacy
placement10/floor42/ground54/Movement312, Runtime516, World53 and seven literal
EGL identities. Fresh identities/counts, Java25/cwd/original-media/S2/EGL/opt-in
properties, terminal Maven0 and unchanged before/after source
`a1c0fec8f9a713e7ec70d8867d427cf71666df6f958acdebb1312e544947606c`
at HEADc636 plus candidate were inspected; all20 consumed XML/TXT files were
removed. The admitted interval is21:32:04–21:34:03Z; queue wait is not retained.
Independent static reviews found no actionable P1/P2; all fixed pins remained
unchanged and declared terrain edits reversed to the three complete old sources.
The earlier tests-first build67639 failed for absent new production APIs and ran
no JUnit; its before/after source fingerprint matched. This bounded private-CD
change adds no shared-engine algorithm or selection policy. Existing native
algorithms, direct negative/prefix controls and actual World/EGL consumers provide
proportionate coverage without another unchanged broad/full-suite run.

These are component operations and source-aware queries. The actual destination
entry must still own native clears, source order, a new player/Frame lifetime,
selectorC drawing/fastPLC service, fresh input and from-white waits. No guard was
deleted, old Frame retargeted or current pixels manufactured to advance the route.
Natural7728/first7729 R11B_LOAD remains unchanged; whole-USA, then JP/EU, is open.

## Immutable stage/player data binding — 28 October 2026

`CdStagePlayerSource` now borrows the existing Present or Past immutable terrain,
player and Sonic products, actual variant/stage and native mapping address. Its
named factories retain exact aggregate-reference relations; equal metadata from
another opened aggregate does not own that aggregate. Present remains21F200 and
Past230F60, while the source's ordinary period is distinct from an actual marked
Frame gate. It performs no byte/hash reauthentication, optional-bank access,
decode, preload, mutable-Level retention, clock or lifecycle work. Existing typed
asset/Level/Frame getters and all Body/Movement/Frame guards remain unchanged.

Fresh source-bound95475 completes21:23:24Z with **15/0/0/0**, including four ROM-free
metadata/null/API checks and eleven original-USA/actual-derived data witnesses.
All15 fresh identities/counts, Java25/cwd/original-media properties, terminalMaven0
and unchanged source
`dd5beaa9f2a3eaaa55db5ec0cb323bbdabe8a680231aa9d464714d2e20fc47dc`
at HEADc420 plus candidate were checked. Admitted interval21:21:51–21:23:24Z,
queue31s separate; both XML/TXT diagnostics were consumed and removed. The prior
absent-class build ran no JUnit. Its before-fingerprint was printed but not
retained by the later bounded filter, so no exact before-hash claim is made for
that negative check. The code/test change comprises two new files only; old World/engine behavior
was not changed or certified by this focused data-binding result.

The binding is not an execution token or Past Frame admission. Actual source-aware
queries, player construction, context-sensitive TimeWarp, current art and load
lifetime still require their genuine callers. First7729 remains R11B_LOAD.

## Shared pattern values and section-art kernel — 28 October 2026

The strict Present residency facade now shares a package-private sparse immutable
pattern-value format. Its nine regions, both seed bodies,24 public declaration
heads, errors, equality/hash and unchanged-region snapshot identities remain
preserved. Facade snapshots and aggregate values reference the same immutable
byte/mask backing. There is no second producer bank. A separate Past facade owns
the source-derived11-span/1510-tile non-player inventory; `unknown()` certifies no
pixels, and its explicit pure clear requires the caller to establish the real
native InitVDP operation. Sonic F000 remains outside that profile and in its
existing player-art owner. Future inventory is not reached-entry execution.

The shared section kernel normalizes immutable lists/sections and sources scoped
to a bound program identity, original-file range and finite reader. Existing
queue and native decoder algorithms are reused. The strict Present facade keeps
its35 public declarations, complete State/exception blocks, owner checks, source17
guard and native failure prefixes. Its typed residency accessor still returns
Present residency. Declared fixture construction remains unauthenticated; real
fromAssets retains the full immutable source identity. Configuration does not
prepare a header or table merely to populate the new internal metadata.

Fresh source-bound64670 completes20:59:25Z with **749/0/0/0**: common pattern12,
Past residency22, legacy residency38, internal kernel3, unchanged section-art59,
presentation39, Runtime516, World53 and seven exact EGL identities. Actual counts,
identities, Java25/cwd/original-media/S2/EGL/opt-in properties, terminal Maven0 and
unchanged before/after source
`3aec8a940aa0f5b406e56591998a03dd0b7d44b9adfd305187790ebd80cc872c`
at HEAD90db plus candidate were verified. The admitted interval is20:56:54–
20:59:25Z after77s queue wait; all18 consumed XML/TXT diagnostics were removed.
Tests preceded production; the earlier missing-pattern-API build check ran no
JUnit. The three kernel tests likewise entered before their new production class.
No second redundant missing-class build was required. Two independent static
reviews found no actionable P1/P2 and verified the whole-file inverse proofs.

Change-based planning selects1430 content/rendering/rewind/CD/common candidates.
This extraction is confined to CD immutable data and existing native algorithms;
all old direct tests,37 new cases and complete Runtime/World/selected-pixel replay
consumers provide proportionate focused coverage. No shared engine algorithm,
selection policy, public Frame source guard, capture registration or clock changes
in this slice. There is no Past section adapter, immediate producer operation or
actual new visit yet. Natural7728/first7729 R11B_LOAD remains unchanged.

The next join needs a genuine Past adapter over these kernels, retaining title
aliases and shared code-table residue across immediate core/Std/RAM blocks and
queued section work. The player-source census identifies three independent family
guards and14 production Frame asset-getter sites: eight active player/presentation/
module relations need a genuine stage binding; six old Runtime guards stay strict.
Named factories avoid existing null-overload callers. Whole-disc launch assets
remain the existing R11A capability while the private current visit binds actual
R11B data. Native Past special chunks and TimeWarp's pre-charge X predicate must
be honored; deleting a guard or borrowing the context-free fallback is insufficient.
Actual entry/source lifetime, selectorC drawing/fastPLC ordering, fresh controls,
Sonic art and22 from-white waits remain unfinished.

## Original Past art readers and shared decode table — 28 October 2026

A new original-USA component regression sends all37 compressed R11B profiles
(36 PLC sources plus RAM blocks) through the existing native-algorithm engine
Nemesis decoder. Complete decoded hashes, exact fetched endpoints, one-short
SOURCE_EXHAUSTED controls and independent Java-format output comparisons pass.
The genuine Tiles10223 reader includes the actual adjacent RobotGenerator byte;
there is no padding or runtime native-source fallback.

Additional witnesses carry the same512-byte code table through core tiles,
Std15 and RAM blocks. Blocks preserve the final16 table bytes from Rings.
A separate explicitly cleared Cam4 component prefix decodes its seven preceding
sources before LogShadow, proving preservation of SonicHole's upper256 table
bytes. That independent seed does not claim the complete actual entry-to-section
lifetime or queued current-art service. Final-lookahead counterfactuals for blocks
and tiles establish fetched-but-pixel-unused bits without weakening real readers.

Fresh source-bound85247 completes20:09:07Z with **42/0/0/0**:37 actual discovered
parameterized profiles and five ordinary checks, exact Java25/cwd/original-media
properties, terminal Maven0 and unchanged source
`ea939dbcc54d43c5b97ab22352e9ad604d97103c95f46b9883bb44929c8f88ee`
at HEAD6951 plus test. Maven38.662s follows42s queue wait; the admitted interval
is20:08:28–20:09:07Z. Both consumed XML/TXT diagnostics were removed. This is a
test-only addition over unchanged decoder/asset production; a full suite or
repeated World run would not verify another changed behavior here. Native
execution/timing, current pixels, actual Past entry and whole-game/regional parity
remain unproved. The first natural7729 R11B_LOAD fence is unchanged.

The next art join must also preserve immediate title-art bytes in physical gaps,
use one common decoder/queue kernel with strict stage bindings, and distinguish
entry writes from the full future PLC inventory. Sonic remains in its existing
art owner; the asset cache's two decoded core products do not stand in for reached
native table/current-residency effects.

## Finite Past Plane B binding — 28 October 2026

`CdR11bBackgroundPlane` binds actual34-byte selector/four-pointer data and the
native initial1F0 mask through one shared name-table algorithm. Its pure calls
reuse the existing State, camera, flag and completed-observation types. The strict
Present130-byte/7F0 factories,35 public declaration lines and43 inherited cases
remain unchanged; private immutable terrain views feed the same kernel without
another decode, captured image, clock or GPU owner. Signed incremental row reads,
unsigned initial/column pointer offsets, blank-layout metadata, seven-bit ring
writes and reached typed failures remain distinct. The two native text-form
differences in incremental code are semantically equivalent at these reached
WORD/consumed-flag cuts; the routines are not claimed byte-identical.

Fresh source-bound21099 completes20:01:47Z: **130/0/0/0**, comprising legacy
Plane43, new constructed-format28, original-USA static-retail5, World53 and one
literal native-Plane-B EGL/replay identity. The extra retail witness binds actual
R11B assets with explicitly constructed component cameras and checks16 literal
bands/392 blocks; it is not a destination-entry or current-art picture. All fresh
counts/identities, Java25/cwd/original-media/S2/EGL properties, terminal Maven0 and
unchanged before/after source
`3b94915169c289e08b0821f531f212dd8b3c2386569cb279fed05032072bcf94`
at HEADde143 plus candidate were checked. The admitted interval is19:59:56–
20:01:47Z, following60s queue wait. All10 consumed XML/TXT diagnostics were removed.
The preceding absent-class build check executed no JUnit. Independent static
review found no actionable P1/P2 and verified the exact legacy whole-file inverse;
its review scope remains the author's constructed fixtures, not Root's execution.

The change-based plan selects1426 content/rendering/rewind/CD/common candidates.
These changes are confined to CD's immutable plane binding and existing kernel;
all43 old direct cases,28 new full4096-byte/invalid-source cases and59 actual
retail/World/EGL consumers provide proportionate focused coverage. Shared renderer
algorithms and source-family guards are unchanged. First7729 still stops before
actual Past loading. Current-art/table/PLC sequence, new lifetime/Frame and22
from-white waits remain the next live work, so this is no Past gameplay or parity
certificate.

## Period-aware foreground and Past background arithmetic — 28 October 2026

Both actual foreground callers now pass the complete raw Frame time-zone BYTE.
The shared event routine compares it to1; values80/81/82 do not select the Present
ramp/HUD branch merely because their low bits resemble a period. This does not
switch the bound background program: a retained R11A visit continues its Present
background until the actual destination lifetime is installed. The legacy pure
foreground entry methods retain their behavior and failure boundaries.

The new stateless `CdR11bBackgroundScroll` reuses the existing immutable State14.
Its initializer consumes integer words from completed SetupCamera, preserving
fractional nonwrites and clearing only the native first16 deformation bytes.
One unlocked step performs the actual fixed-point camera deltas, full-byte
crossing tests, four cloud accumulators,42 derived words and233–240 HScroll
longs, preserving supplied unwritten tails and separate cloud RAM. A locked
step retains the original value. There is no new clock, capture partition,
asset bank, source-family admission or partial-write owner.

The tests were imported before these APIs existed; build-only31194 stopped at
the expected missing symbols without executing JUnit. Fresh source-bound92658
completes19:52:21Z with **267/0/0/0**: foreground81, legacy background54, Past
background24, World53, Module4, ModAPI11, draw scope33 and the same seven exact
EGL methods. The runner checked all class/count/Java25/cwd/media/S2/EGL properties,
all seven literal selected identities, terminal Maven0 and unchanged source
`8545509f3a5cfbb0b2368d3bb87a6caca426c78c753d22d9897a8439803711a9`
at HEAD3013 plus candidate. The admitted interval is19:50:17–19:52:21Z; queue
wait50s is separate. All16 consumed XML/TXT diagnostics were removed. Two prior
bounded component reviews found no actionable P1/P2; the actual caller changes
were inspected against the raw native event selector and verified here.

Change-based planning conservatively selects all4390 candidates because the
package-level live presentation owner has broader consumers. These edits remain
inside the private CD algorithm/owner contract. The159 direct scroll cases and108
actual World/EGL/API/draw consumers cover their finite arithmetic, old wrappers,
source/phase/failure closure, selected pixels and full/course replay, so this
focused selection is proportionate; no shared engine algorithm or selection
policy changed in this slice. No full-suite, whole-game or native-parity claim
follows from it. The natural7728/first7729 R11B_LOAD fence remains unchanged.
Actual Past Plane B34/16 binding, current-art/PLC initialization, fresh entry
lifetime and22 from-white waits are still pending.

## Authenticated Past assets and static terrain — 28 October 2026

A distinct `CdR11bAssets` now binds the retained original USA R11B image by its
complete262144-byte SHA256 and original-file domain. A common header cannot
substitute R11A. Its factory decodes only the two core terrain sources, then
binds actual load metadata, placements and finite Sonic/player data. Other art
views are immutable inputs, not an eager bank or known current residency.

The common `CdStageTerrain` format retains both explicit shapes: Present and
Past. The strict public R11A facade preserves its types, errors, dimensions and
stable getter identities. The engine geometry adapter now shares that conversion;
`SonicCdPastLevel` is a separate mutable static projection with immutable Past
provenance. Past supplies660 native blocks,746 patterns,106 chunks,42x4 foreground
and10x2 background. Cleared native64x16 layout cells remain clear, and unavailable
unused drawing cells still fail individually. No stock stage selection, Frame
source-family guard, native collision solver or live World was widened.

R11B load metadata preserves19 PLC slots, native standard15 entries, all aliases,
four section thresholds and palette rows3/5. Native Sonic selects230F60 despite
an identical unused mapping copy. Reader/source lengths remain separate: blocks
retain4228 bytes but Java fetches4227; tiles retain10222 bytes while the native
view needs the genuine next RobotGenerator byte. The original Sonic EVEN byte
also remains a separately bounded read. The appended raw life-icon view is384
bytes at original362D6, independently joined by its absolute LEA and native128-byte
copy. It is the70th named view, not a37th PLC source or a Nemesis reader. Its
B420 publication still belongs to the future reached entry owner.

Fresh actual-media request88945 completes18:33:07Z: **30/0/0/0**, comprising
assets13, load-plan13 and static Level4. All exact fresh classes/cases, Java25,
feature cwd, original media and zero skips were inspected. Full source before/after
`3d29887c7e0f6ccc49ec1dbd49644f1b07db6d5c80133c8ca0cd95fd853848c6`
is unchanged. Maven1m36s followed1435s shared-admission wait; complete command wall
was not separately measured. This run covers the original69-view binding; the
subsequent raw-life-icon addition has separate affected verification. Every actual
pattern/block/collision sample and layout cell is compared, and two fixed512x256
source-space CPU planes match independent raw-word/palette pixel expectations.
The captures are static component projections on a test matte, not live Past,
selected current-art/GPU output, an emulator reference or native timing parity.
Earlier106 compatibility/common-format checks pass separately; no fabricated
combined fresh total is claimed. Three bounded static reviews found no actionable
P1/P2. Their minor native citation corrections were applied to integration copies.

The final raw-view/metadata and affected-consumer request90011 completes19:39:02Z:
**140/0/0/0** across assets14, warp-load18, World53, Module4, ModAPI11, shared draw
scope33 and seven exact EGL methods (Ring, Plane B, missing atlas, stars, old white
fade, WARP and stale returned atlas). All exact fresh identities/counts, Java25,
feature cwd, original media, authenticated S2 path, surfaceless EGL and opt-in were
inspected; source before/after
`35f29ce6913d76a2c25f7faa01625e9590250183ea0ec1d5ed16e1763b8bf2d8`
is unchanged at HEAD e34 plus candidate. Maven2m07s follows3091s queue wait;
full command wall was not separately measured. All14 diagnostic XML/TXT files
were consumed and removed. Later edits add attribution prose only. The raw API
was first absent at a build-only compilation check (five missing-symbol errors,
no JUnit); its earlier test-shaped request was cancelled as unnecessary for that
compile-only observation. No heap reduction or test-admission bypass was used.
One unrun Root byte-slice identity assertion was corrected to byte-content
comparison before execution; no runtime failure is claimed for that fixture.

The destination lifetime audit proves why same-level checkpoint carry is wrong:
RunMMD clears old objects, CPU art/history, controls and scroll scratch, while
Start also resets presentation and pattern residency. Surviving saved/main values
still require their own later writers. Existing module load override/profile and
shared session/load-generation contracts can own the distinct new level. Actual
initialization/PCM readiness, cleared-versus-surviving values, raw LifeIcon/current
art, immediate/queued PLCs, Past scroll, new input latches and22 from-white waits
remain the next live join. The helper's descriptive zone address is corrected from
levelFramesFF1504 to the native zone BYTEFF1506; its scalar read algorithm is
unchanged. First7729 remains R11B_LOAD, with no completed Past gameplay call.

Integration tooling now has a focused `soniccd` category plus explicit gameplay
closure. Matched policy schema2 uses ownership_rules so an old reader fails closed
rather than silently dropping CD. Root's62 Python checks and an independent62 plus
seven fresh controls pass. Current all/default4389 candidates and every prior
category selection are preserved; focused CD has465 candidate classes, including
common. These are source candidates, not discovered tests or measured speedups.
Current Java25/Lua5.4/PowerShell preflight passes with explicit LUA_BIN. The
change-based delivery plan conservatively selects all4389 because tooling changed;
focused validation is proportionate here: selection-set equality, reader/safety
controls, unchanged execution/admission/tag policy and actual bounded CD consumers
cover this change without repeating unrelated game campaigns. No release full
suite, new timeout, queue-budget change or manual candidate narrowing is added.

## Past-entry restoration and target fade prerequisites — 28 October 2026

`CdTimeWarpLoadData` now implements the distinct native saved42 consumer. It
publishes20 scalar stores in order, consuming37 distinct saved bytes for PP,
then reads the actual current zone and spawn at their late native cuts. Zone6
alone reads saved mini; signed spawn selects a fresh saved-X read and wrapping
left-bound subtraction. Unknown saved/live values and callback failures retain
only the earlier stores actually adopted by the borrowed owners. Same-value
observations do not certify private once-only authority. It supplies no Frame,
clock, RAM aggregate, capture adapter, checkpoint31 substitution or load admission.
The old checkpoint helper correctly still requires its separate warp-load caller.

`CdLevelPaletteFade.stepFromWhite` supplies the level's64-word current/target
operation. It follows whole unsigned WORD borrow/comparison branches, blue then
green then wrapping red fallback; it neither clamps RGB nor borrows WARP's
24-step fade. Its future caller owns initial white fill, native zone1-only water
selection and22 VSync/PLC iterations. Both explicit immutable buffers are required.
Existing to-white/black algorithms remain unchanged.

Fresh focused request63583 completes17:21:50Z: **118/0/0/0**. The exact selection
is checkpoint load35, warp load18, warp save21 and level palette fade44. Exact
fresh classes/cases, Java25.0.5.0.2, feature cwd and zero skips were inspected;
all checks are media-free component/owner contracts. Full source before/after
`375652ebeb73ce51face1820d5063823bb960fc4a7da0738ce07d5abd402161d`
at HEAD48db plus candidate is unchanged (16172paths/980931268bytes). Maven1m33s,
queue114s; complete wall was not separately measured. Earlier missing-method and
missing-class compilation checks failed before JUnit; they are not runtime RED
results. The first isolated palette44 pass is separate. Two static reviews found
no actionable P1/P2 and verified exact native store/colour order and all new test
oracles. Consumed diagnostic XML/TXT files were removed. These bounded primitives
change no live Frame/Runtime/controller path; no broad or release suite was needed.

Original-USA R11B research independently pins its actual image,39 native operand
joins, core geometry/player views,36 PLC art sources, standard15-entry/four-section
load metadata and destination aliases. Two identical Sonic mapping copies exist;
native code selects230F60, not the other21F200 copy. Reader views must preserve
real adjacent bytes independently of compressed source blobs. All decoder
measurements in that research are labelled Python source models, not Java/native
execution or current-art ownership. No ROM bytes or research data are runtime
fallbacks. R11A profile, layout dimensions and current art are not relabelled.

The live controller frontier remains7728 returned/first7729 R11B_LOAD. The next
slice must bind actual Past assets and its own geometry/load metadata, account for
entry resets/surviving state, integrate this saved consumer into the real load,
then perform Past scroll, art, palette publication and22 from-white waits. Neither
primitive alone starts Past, proves PCM playback or certifies native/regional parity.

## Selected WARP scene and programme rewind — 28 October 2026

The existing controller HOLD path now joins the returned R11A programme to the
actual retained USA `WARP__.MMD`, then to a typed Past-load fence. One immutable
asset binding, pure32-slot scene and selected-picture projection translate native
algorithms directly; no CPU, video timing, bus or general RAM simulation is added.
The existing shared rewind payload retains scene values and ordered sound metadata.
There is no second capture adapter, old-Frame clock, object walk or art service.

The counter0 entry is an explicit synchronous setup cut with no represented setup
pulses; it is not a measured console IRQ phase. Delay CRAM/held/tap knowledge stays
unavailable until its actual selection.90 delay pulses, preparation,24 fade-in,
215 ordinary Main pulses and24 fade-out pulses complete the scene. Input Start
handling remains an algorithmic component; this fixed controller route holds
Right. IPX entry/return each occupy a separate blank HOLD row. The existing source
marker is masked only in IPX's selection scratch, not cleared from live state.

Finite assets are101 patterns, the physical64×32 background, the actual mapping
roots and palette/cycle bytes.5302 declared relocated-copy bytes remain unavailable,
never padded. The scene draws selected scroll, CRAM and native SAT through existing
renderers, reusing101 IDs within the current664-entry client range. No new logical
bank, native decoder, GL owner or source17 art cache is added. The old picture is
reinstalled from frozen pattern generations when restoring the pre-WARP cut;
actual GPU texels verify those aliases even when its white palette hides differences.

The permanent five-jump route returns7373–7728 and compares seven full/course
edges, complete programme/scene/saved42/checkpoint and old-owner nonwrites before
and after every forward row. A separate real EGL proof renders267 initial rows,
compares independent source name-table/SAT/4bpp framebuffer expectations, actual
palette uploads, Sonic/background/Sparkle omission controls and eight full/course
state/pixel edges. It observes94 selected IDs, all five source Sonic frames and
four normally live Sparkle frames, with maximum64 selected SAT entries. These
counts are distinct from the101-pattern source extent and664 installation/retirement.
All664 entries retire; unrelated producer identities remain. Typed E0/D8/E0 requests
are ordered metadata, not synthesized or audible music.

Fresh request90606 completes16:49:53Z: **108/0/0/0**. Selection: all53 World cases,
seven exact EGL methods (Ring, PlaneB, missing resident, TimeStars, old fade,
WARP and stale-atlas), Module4, ModApi11 and shared scroll draw scope33.
Java25.0.5.0.2, exact fresh class/case identities, feature cwd, original media/S2,
surfaceless EGL and opt-in were checked. Source before/after is
`295ae485bf171710f277e5348560e75f6381eb661fada42bc133ffbd9c90c0be`
at HEAD0374 plus candidate,16170 paths/980879179 bytes. Maven execution1m01s,
queue102s; end-to-end wall was not separately measured. Four new component
classes separately passed68 cases in20964; that combined84-case observer request
had one intentional terminal error. No fresh all-green176 selection is asserted.
Bounded CD/controller/renderer coverage replaces the disproportionate unclassified
broad smoke selection; Frame/Runtime algorithms are unchanged by this slice.

Review found one early-entry atlas-admission defect. A real stale-atlas control
first reproduced publication before rejection, then passed after the existing
bound check moved before WARP entry. New fixtures also incorrectly compared
separately created optional asset owners and demanded a restored suspended R11A
cache be clean during WARP. They now retain one owner and check the old cache's
nonwrites; exact WARP pixels and GPU checks remain intact. No source algorithm
was tuned to those oracles. A temporary observer and two compile-only fixture
repairs remain historical, not additional green gameplay runs. Consumed diagnostics
are removed; actual captures and their limits stay external.

The integration tool now also verifies explicit original-CUE/named-MMD recipes
without extracting bytes. It checks every FILE→TRACK declaration, bound data-file
identity, parser/read budgets, the complete named programme pin and finite operands;
audio and the whole track/disc are explicitly unobserved. Root73 Python cases pass
(45 binary plus28 disc); independent29 supplied and seven fresh controls were
separate. A review-driven shadowed/dangling FILE regression was observed RED then
GREEN. Actual USA WARP confirms cycle offset238 and timer LEA1EE; the withdrawn1E8
forecast rejects as mismatch. Indexed LEAs remain literal observations, not an
instruction decoder. Existing binary CLI/schema are preserved.

Next is the real R11B/Past load and entry lifetime with its actual marked-period,
live-spawn, save and entry-global knowledge. The destination cannot be seeded from
R11A identity or saved42 alone. No Past frame, console timing parity, full-USA,
JP/EU or audible playback certification is claimed.

## Current palettes and the completed old-LevelStart fade — 28 October 2026

The opening DEV entry now explicitly establishes the source-backed fresh
`BeginLevel` postconditions. This is a semantic ingress contract, not a claim that
Title, IPX and the initial loading loops executed. In particular, IPX `RunMMD`
clears the complete water128-byte range on every programme load; the narrower
644-byte `ClearScreen` is not the source of that knowledge. The module retains
only previously unowned programme scalars in its existing shared rewind payload;
Frame pause/time-attack and Runtime level/PLC/save values keep their original owners.

Six actual R11A palette scripts/colour views use one cached immutable asset
binding. The normal palette, six BYTE timers/steps, known current water and the
sole displayed-normal selection are captured once by Runtime. Ordinary
`PalCycle_Do` follows its WORD time-stop gate. V-int publishes the previous
current palette after controller polling, before Sonic/level-art service. Held
cycles sample no source bytes; selected faults retain their completed write
prefix. GPU palettes are derived projections, applied after queued old pictures
drain and before the new terrain draw. Restore adopts native values without an
eager cache upload; the next safe draw projects the restored selection.

Marked-period old `LevelStart` uses the actual old palette-fade bit, clears only
its reached native globals and joins the existing Frame/Runtime owners. Native
`ClearPLCs` clears96 descriptor bytes plus32 saved decoder/count/budget/hint/pad
bytes, cancelling continuation while retaining NEMBUFFER512, section and resident
art. Selector12 performs controller read, prior-palette publication, fast PLC,
`VInt_Finish` timers and one existing V-int count; no Sonic art, Body, object walk,
scroll, SAT rebuild or ordinary palette-cycle tail runs. Each native colour pass
then processes all64 normal WORDs followed by all64 water WORDs. After22 waits,
the palette flag, direction and complete restart WORD clear in source order.

The unchanged five-jump controller recipe returns7351–7372 as HOLD rows.
levelFrames stays7350; the represented native clock advances7350→7372 and warp
timer230→252. Current normal/water are white by wait21; displayed normal selects
that white on wait22. At that earlier palette-only checkpoint,7373 stopped at `WARP_PROGRAM_START` before another
input/V-int and terminally closes the retained owners. Permanent full/course
edges7350→7351,7351→7352,7360→7372 and7371→7372 compare the complete programme,
actor/history, all96+31 objects, art, checkpoint/saved42, sounds and palettes
before forwarding and after every row. All earlier53 World cases remain.

Fresh affected request27889 completed15:26:04Z: **1430/0/0/0 across10 classes**:
Frame733, Runtime516, palette cycle13, authenticated source3, section art59,
World53, selected EGL5, Module4, ModApi11 and shared draw scope33. Maven execution
1m22, queue33s; total wall was not separately measured. HEADd706 plus candidate
has identical before/after fingerprint
`aa0a5a6c7b09367e122e6ab29419aa0007a89857f4b4819d35097167d3b1b082`,
16160 paths/980659893 bytes. Exact fresh classes/cases/counts, Java25.0.5.0.2,
feature cwd, original USA media/S2 paths, EGL backend/opt-in and zero skips were
checked. Later changes are attribution prose only. These bounded private-CD
owner/consumer checks replace the disproportionate unclassified broad selection;
this is not full smoke, release qualification or native parity.

The new actual EGL controller method reads all64 uploaded GPU palette colours on
every wait, checks the one-step publication lag, stable redraws, final whole-frame
white and four complete full/course state/pixel replay edges. Three actual ENGINE
captures at7350/7360/7372 are external and written after all cleanup; no emulator
reference or destination picture is inferred. Existing Ring, TimeStars, current
Tamabboh singleton and Log Shadow retirement EGL checks pass in the same run.

Earlier72462 aborted in a new spy fixture's real GL upload without a context;
its run was incomplete. The corrected fixture observes the existing upload
boundary. Earlier68908 had two new ClearPLCs oracle errors; the96+32 source
correction passed narrowly and now in the fresh combined run. No production
rule was fitted to those assertions. Observer48759 intentionally rethrew the
first7373 boundary (one error), and is absent from delivery. Consumed diagnostics
are removed rather than archived. Static live-join/route/EGL reviews report no
unresolved P1/P2; their scope is separate from Root's observed Java execution.

Integration tooling also gains optional `run --compact-output`. Projection occurs
only after full source/dependency/fresh-XML/literal-identity verification. Passing
case details and repeated declarations may be omitted; complete integrity issues,
counts/verdict and Maven stderr remain. Nine changed Python safety tests pass here;
separate author238 and review16 controls retain their own attribution. Actual
source-bound84961 passes one exact fade method at15:19:02Z,35.389s Maven/0s queue,
with identical before/after source18b10264…ea8f and the exact literal case. No pass
cache, retry, output receipt or alternate execution lane is added.

The next owner at that historical palette milestone was the IPX tail and WARP scene over the already
retained original programme, using existing controller HOLD and shared capture.
Preserve the inactive old-level owners until the real destination load. WARP's
own initial counter/latch provenance and separate fade schedule must be explicit;
do not borrow old Frame clocks, hydrate traces or pad unresolved programme bytes.
Then admit the actual R11B/Past source/load lifetime. Whole USA first, JP/EU next,
native audio playback and complete game parity remain unfinished.

## Resumed pre-save time warp — 28 October 2026

`CdPlayerTimeWarp` translates the native pre-save decision order directly. The
existing Frame V-int owns the timer tick; Movement pauses at its actual call,
Body borrows actual reserved/contact state, then resumes the original mode and
common tail once. There is no additional capture partition or clock. Missing native
save/restart inputs remain typed stops; a failed pure Body still publishes no
staged actor result. Reserved `CdTimeStars` uses only actual slots12..15, native
Init/6, the post-Body history and the shared animator. Its Draw admission is
explicit: the old queue constructor still rejects every nonprimary reserved
slot. The nine-script Powerup animation view is appended as Table ordinal67
(total68), with complete296-byte native/media authentication. Source17/C31/664,
current regions and the sole existing powerup art service remain unchanged.

Controller evidence uses ordinary Right and B holds1201–1232,1770–1801,
3002–3033,5201–5232. Call5415 creates the four real reserved stars;5416 requests
powerup art;5417 services the genuine1024-byte copy, retaining128 existing RAM
bytes. Landing5424 drops GV below$600;5425 cancels timer/flag, retains directionFF
and the later star visits delete all64 bytes. The7000 endpoint is healthy pushing,
not act completion. Permanent full/course edges5414→5415,5415→5416,5416→5417,
5424→5425,6999→7000 retain the complete checkpoint-aware state before forwarding
and after each row. All earlier route proofs remain.

Observed bounded validation at source HEAD `fccde248ac18dcfb21a8031ea2b1b5d88a6e19c8`
plus this gameplay delta: ten-class94093 ran2180/1failure/1error/0skips. Mapping52,
Body289, Frame711, assets126, retail60, queue20, Stars67, Movement309 and TimeWarp52
passed. Runtime's two stale fixtures were corrected from native source; fresh
21121 Runtime494/0/0/0 passed separately. The combined diagnostic495 result stays
red because its temporary observer deliberately rejected a healthy7000 bound.
Temporary fifth-jump5527 preserves the actual7179 controlled-player failure.
No fresh all-green2180 invocation or native parity is asserted.

Fresh two-method94420 completed10:15:06Z:2/0/0/0 (39.362s Maven,4s queue).
`controllerFourJumpRouteRunsTimeWarpStarsCancelsAndReplaysHealthyCuts` and
`actualEglControllerTimeWarpStarsUseCurrentArtAndRewindNativeServiceCuts` both pass.
Actual surfaceless-EGL World/PlaneB rendering has visible stars on5417–5424,
rejects a renderer-only star omission control, checks prior-SAT/NOW-art versions,
and reproduces full/course state and pixels on four service edges. All output
was written after session/graphics/boot cleanup. Captures are external ENGINE
products, not emulator comparison references. Exact fresh test identities,
Java25.0.5.0.2, cwd, original-media/S2 paths, EGL opt-in and zero skips were
inspected. One earlier22610 compile-only failure was a wrong fixture class
qualifier; it executed no tests and changed no gameplay code.

Reproduce the two methods with the shared Maven queue from this feature checkout,
absolute original `soniccd.media.root`/`sonic2.rom.path`,
`-Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true`, and both exact
method selectors above at the saved pre-save milestone. On current source use the
five-jump route selector below in place of its earlier four-jump name.
Optional `soniccd.timewarp.output` must be an absolute
Downloads output directory. No actor/pool/camera ingress is used. The broader
change planner selects4374 smoke classes because the CD paths are unclassified;
these private-CD owner/consumer checks are the proportionate selection for this
increment. Whole-game/regions/audio playback/time-travel destination remain open.

### Controlled Sonic and measured save boundary — 28 October 2026

Body captures controlbit0 after the unchanged raw/logical-input prelude. The
controlled path calls the same pure TimeWarp owner, skips MODE and PP special
collision, and reaches the one existing Display/history/water/angle-copy/animation/
touch/special-chunks tail. Its distinct `CONTROLLED_MAIN_RETURN` carries Display
and no MODE boundary; the actual `CdPlayableSonic` queue consumer admits it. A
context-free component call admits only source-proven no-write TimeWarp returns,
with precise first-unowned-read/store stops otherwise. Frame/Runtime captures,
State8/Environment4 and all ordinary-mode continuations are unchanged.

The fifth B hold is fixed at7001–7032. Disc64 attaches7178; controlled Body7179
returns, then the later dynamic Disc visit changes its live control again. The
route returns7329, with native Frozen animation, stand64, timer209 and current
stars;7330 ticks the timer210 then stops before the first scroll-lock store.
Complete full/course edges7000→7001,7032→7033,7177→7178,7178→7179,7328→7329 retain
all previous route edges, complete checkpoint-aware state and full sound lists.
The terminal assertion uses held public Frame values only, preserves the original
failure/suppressed closure and never captures or restores the failed composite.

Observed selection22875 at pre-correction source0127d6e9 ran1523/1/0/0: allFrame711,
Runtime494, caller5, module4 and route/EGL2 passed; one new Body307 oracle expected
zero angle buffers instead of the native common-tail scratch0x26/64. The corrected
case passed narrowly in3490; its separate temporary controller probe reached the
actual7330 boundary. Review found no production P1/P2 and one asset-free-CI issue:
the two new retail Body tests now explicitly skip missing/blank media, while
configured invalid media still fails. Asset-free20120 runs2/0/0/2; a prior lean
9580 compiler OOM ran no tests and was rerun normally. No fresh all-green1523 is
asserted.

Final configured95854 completed10:34:51Z:4/0/0/0 (37.842s Maven,13s queue). It runs
the corrected full-state case, both actual retail Frozen tests and
`controllerFiveJumpRouteRunsControlledDiscAndReplaysToTimeWarpSaveBoundary`.
Exact fresh identities/properties/Java25 and zero skips were checked; full source
before/after is b5ede797e738091c06c6d20a0b4650943e9a425208b2102a50fbd6cd1901d11b
at HEADfbd6eaaaa plus this controlled delta. Temporary observer source is absent
from delivery. These are bounded integration checks, not whole-USA or native
parity certification.

The next native210 branch writes the high byte of the existing scroll-lock WORD,
then changes the raw period, saves separate warp data and selects spawnMode2.
Existing31-byte checkpoint data and death-restart WORD1 do not represent that
sequence. Its exact state/lifecycle ownership must be implemented before claiming
7330 returned or travelling to another period; no current guard is bypassed.

### Warp-saved value component — 28 October 2026

`objects/CdTimeWarpSaveData` represents the distinct42 bytes in six source-native
spans, with23 raw sources and26 ordered stores. It retains the two saved-only
flag clears, both fresh water-routine reads, unsigned full-LONG time cap and
native first-unknown-byte/read/store failure positions. Its immutable knowledge
value is suitable for capture custody; its public metadata supplies no live
phase, producer, retry or exactly-once authority. It does not overlap or extend
the existing31-byte checkpoint payload and supplies no missing live defaults.

Fresh focused66173 passes21/0/0/0 at11:00:32Z (Maven1m30s,queue0s). All21 exact
ROM-free method identities, Java25.0.5.0.2, cwd and zero skips were inspected.
Source before/after d529437e28f6769223cbc74ee4c729bbd560a258533d3b52c6d20591f7f7f5a5
is HEADca7061fdd plus the two new files (16149paths/980229287bytes). Source was
created test-first, then frozen for Root intake; the final test freeze was after
both drafts existed, so it is not described as tests-frozen-before-production.
Root ran no earlier component RED and claims none. Packet draft/apply verified
explicit canonical mappings, frozen hashes and absent destinations under the
worktree lock. No media/GL/native run or whole-game proof belongs to this check.

At the component-only8f115050 checkpoint this value was unbound. The following
section records the later live join; that separate integration supplies the7330
execution proof, rather than retroactively enlarging this21-case component pass.


### Live timer210 save and measured timer230 fence — 28 October 2026

The actual Module borrows the complete foreground/background values through its
private current-Frame/assets/Runtime bridge at PLAYER_BODY. Body supplies its
local post-predispatch/post-ramp player fields before the common tail. Detached
save metadata prepares scrollLock's HIGH-byte write, raw period80, all26 saved
stores and final live spawn2. Runtime adopts the prepared values before later
dynamic objects. Saved spawn1 and Body-cut X6564 deliberately differ from live
spawn2 and the later Disc's completed X6559. Checkpoint31 remains separate.
Runtime's existing capture adds the immutable saved42 value (16 fields total),
and actual restart carry preserves it without widening death/checkpoint restart
admission. Old factory11/14 siblings retain their explicit unbound behavior;
new12/15 siblings require the save-world binding. No new phase or clock exists.

Custom world callbacks receive immediate pre/post checks of the complete closed
Frame cut, preparation, placement/status/saved bank and reserved images. Callback
exceptions remain primary, with failed nonwrite verification suppressed. Original
callback mutations are detected and retained, not rolled back. The inherited
atomicity limit remains: Frame adopts only a complete Body; Runtime folds its
metadata afterward. This is neither native all-owner partial-prefix publication
nor an all-owner atomic commit guarantee. At this earlier checkpoint native230
remained a typed stop; the later bounded restart slice below supplies its actual
WORD without treating the old death/checkpoint path as a destination loader.

Temporary ordinary observer35595 returned7349 calls and stopped7350 at
LEVEL_RESTART_WRITE. At7330 all42 known bytes, lock0100, raw80 and spawn2 were
observed; the current source bank and original World generation remain. Its
combined496 result has one intentional diagnostic error; Runtime494 and repaired
Body case1 pass separately in that invocation. Java25/cwd/media/S2/EGL and exact
fresh identities were checked, with unchanged sourcecc48a52d before/after. The
observer is removed from delivery source. The permanent route at this checkpoint added full/course
7329→7330,7330→7331,7348→7349 with complete state before and after every row and
an expected7350 failure after all healthy replay checks. Final affected execution
below passes this permanent case.

Focused callback regressions93376 ran15/6/0/0, reproducing unsafe validation,
preparation/owner mutation and exception-suppression paths plus one constructed
charge/timer fixture error. V-int sees chargeFF before Body wraps it, so that
fixture now explicitly seeds timer210. Root's first guard patch10507 then failed
15/10/3/0 by comparing immutable byte wrappers by identity; both checks now
compare bytes. Corrected27587 passes15/0/0/0 at11:53:02Z (Maven1m33,queue31s),
source2efbc528 before/after unchanged. These include all42/26 values/order,
missing live inputs, no-op/stale ports, throwing and swallowed reentry, full
Frame/placement nonwrites and separate Runtime/Actor/Shared/world209→210→211 replay.
Final95283 passes578/0/0/0 at11:58:26Z (55.034s Maven,0s queue): Runtime509,
WorldLoad53, actualTimeStarsEGL1, ModuleContracts4 and ModApiSignatureSurface11.
All five exact fresh class sets/case identities, Java25.0.5.0.2, cwd, original-media
and S2 paths, surfaceless EGL/explicit opt-in and zero skips were checked. Source
before/after is be99a9258ecd4dfaebbc6b5936d71e86fe16aaaca978c4921c5fa5626256643f
at HEAD993853da1 plus this gameplay delta (16151paths/980396136bytes). Only these
attribution/coverage prose edits follow validation; Java and test bytes remain
unchanged. This is bounded private-CD integration coverage, not full smoke,
release qualification, destination loading, native parity or completed USA/JP/EU.
The consumed diagnostics are temporary; no pass cache or raw-log archive is kept.

Reproduce from this feature checkout with the shared queue and actual absolute
media/S2 paths:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 \
  python3 -B tools/testing/maven_queue.py \
  --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  -Dsoniccd.opening.egl=true -Dsoniccd.media.root=/absolute/original/media \
  '-Dsonic2.rom.path=/absolute/original/Sonic 2.gen' \
  '-Dtest=TestCdOpeningObjectRuntime,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl#actualEglControllerTimeWarpStarsUseCurrentArtAndRewindNativeServiceCuts,TestSonicCdModuleContracts,TestModApiSignatureSurface' test -B
```

### Timer230 restart and the completed old-loop cut — 28 October 2026

`Context` explicitly borrows an optional actual restart WORD; old2/3-argument
constructors preserve the missing-input stop. `RestartResult` describes exactly
`(old & 00FF) | 0100`; it does not write WORD1. Body preserves the selected MODE
or controlled path and common closure, staging the existing restart `LevelWrite`,
existing `SCMD_FADECDA` sound request, then completed TimeWarp metadata. Runtime
checks that ordered native output against its current WORD and prepares its final
level value before adoption. No new sound family, phase, clock, capture partition
or loader is introduced. Compatibility contexts still stop before an unknown write.

All later physical object visits remain before the existing post-object restart
test. `endCycleAtLevelRestart` closes that old loop before LevelScroll, DrawObjects,
UpdateSectionArt/PLCs and the ordinary animation tail. The actual Module's next
beforeFrame reports `WARP_LEVEL_START` before the first unavailable LevelStart
reset/palette/program action. The old WORD1/rawPresent death/checkpoint carry
predicate is unchanged. Missing palFadeFlags cannot be inferred from the separate
fadeEnableDisplay byte or from displayed whiteness. The request is not synthesized
CDDA fading, palette fade, cutscene or destination execution.

Focused RED84920 has3 expected LEVEL_RESTART_WRITE errors,0 skips, against the
original production at HEAD3c plus new tests (36.024s Maven/0s queue). Source
`f56b8c0db64a86ec33e1aa282a87c0b05cb951d6f9c69da5002ce21f2e3bbdf6` matches before/after.
Source-bound exact-method positive89223 passes3/0/0/0 at12:36:22Z (1m33 Maven/0s
queue), HEADc70 plus this gameplay delta; source
`cc341cef8b3d6c6be6adda89d724a824feafaf275bdc2dbcad7c1a9c9509d0ce` matches before/after.
The new runner checks all three literal case identities, fresh XML, Java25 and
original-media property under the existing held queue/source leases.

Ordinary five-jump observer79991 measures ALL7350 calls returned. Call7350 has
restart0100, timer230, history212, the one FADECDA request, unchanged CPU SAT and
saved42; later star animations reach index9. First7351 reports WARP_LEVEL_START
before clock advancement (still7350). Its1/0/1/0 is an intentional diagnostic
stop, not a passing test (36.624s Maven/0s queue,12:37:54Z). Full source
`f74334422e553d1f7f7e9eba24e3025f2a536fdcf4cc37966dfd5b3548490a92` matches before/after.
The temporary observer is removed and preserved externally. Permanent route now
retains all earlier53 cases/proofs and adds complete full/course7349→7350 plus
restoration of the7350 old-loop cut before its one terminal7351 attempt.
Fresh affected20744 passes1673/0/0/0 at12:51:37Z: TimeWarp64, Body315,
Frame713, Runtime512, WorldLoad53, actualTimeStarsEGL1, ModuleContracts4 and
ModApiSignatureSurface11. Maven took56.029s after428s shared queue wait; total
wall time was not separately measured. All eight exact fresh class sets, actual
case identities, Java25.0.5.0.2, cwd, original-media/S2 paths, surfaceless EGL and
explicit opt-in were inspected. Source before/after is
`6682b18c6206309375b9b33350223794d9b21e7d501b057e16c3eba892459057`
at HEADc70 plus this gameplay/documentation delta (16152paths/980460143bytes).
Only attribution prose follows that run; no Java/test bytes change. Both the
permanent7350 full/course proof and expected7351 stop pass. Component and route
static reviews establish no actionable P1/P2; they claim no independent execution.
This is bounded private-CD owner/consumer verification, not full smoke, release
qualification, synthesized fading, cutscene/destination execution or whole-USA
parity. All consumed task reports are removed under normal diagnostic policy.

Reproduce with the shared queue and actual absolute original-media/S2 paths:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 \
  python3 -B tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true \
  -Dsoniccd.media.root=/absolute/original/media \
  '-Dsonic2.rom.path=/absolute/original/Sonic 2.gen' \
  '-Dtest=TestCdPlayerTimeWarp,TestCdPlayerBody,TestCdPlayerFrameRuntime,TestCdOpeningObjectRuntime,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl#actualEglControllerTimeWarpStarsUseCurrentArtAndRewindNativeServiceCuts,TestSonicCdModuleContracts,TestModApiSignatureSurface' test -B
```

Next: a real old-LevelStart exit owner, with the missing reset/palette inputs and
conditional22-step fade, then IPX/WARP__.MMD and the destination MMD with explicit
program/source/load/capture lifetimes. The separate native audit has authenticated
actual USA WARP and R11B files, but research does not implement those owners.
Preserve the earlier Body failure-prefix limit: a later pure Body throw exposes
no staged restart/sound output. This slice retains actual completed Body work on
later object failure, not native all-owner partial publication or whole-game parity.

### Detached WARP/Past programs and palette prerequisite — 28 October 2026

USA stock preparation now retains authenticated `WARP__.MMD` and `R11B__.MMD`
through `CdTimeWarpPrograms`, detached while its existing original-disc owner is
open. Full content/header/extent checks precede retention. The7242-byte WARP
image preserves its5302-byte unresolved relocation tail; the262144-byte R11B
image remains a distinct program despite its shared header with R11A. JP/EU
program consumers remain absent. The three existing logical assets and data
identity stay unchanged; equal identity alone does not certify retained program
presence. Requesting R11B as a stock stage remains unsupported. There is no new
executing program, source fallback, decoder, clock or world replacement here.

The actual USA WARP art at file748 has two distinct finite extents. The existing
Java Nemesis reader succeeds at1536 bytes and fails at1535. The existing bounded
native-style decoder kernel succeeds at1537 and stops at1536 during final code
refill after3228 output bytes. It genuinely consumes the first BG-map byte20 at
fileD48, leaving next byte01 unread. Both complete readers produce3232 bytes,
SHA256 `453513fafdfe7f1c9d6d28ae22c9ec36b3158315945508790c9ad2cc7d4d4d81`.
Actual-media regressions check those limits and changed-read/unread controls;
constructed code-table seeds0/A5 do not claim native incoming RAM. WARP native
source has28 sparkles, not the earlier provisional27; reachable frame coverage
remains separate from stored pointers. No console execution or cutscene is proved.

`CdLevelPaletteFade` implements one native full64-WORD normal-then-water colour
step, with raw WORD arithmetic and red/green/blue order, from Palette Fade.asm.
Both128-byte palettes must be explicitly known. It has no wait, loop progress,
PLC/render service or live admission. LevelStart's22 passes and WARP's separate24
pulses remain different callers. The current DEV boot did not establish native
palFadeFlags/current palette-cycle state or a complete water palette. Native
waterFadePal aliases the final128 SAT bytes; shipped ClearScreen clears644 bytes,
not just the640 SAT bytes, and reaches only the first4 water-palette bytes. These
facts prohibit treating unrelated display state or missing bytes as defaults.
An explicit cold-new-game provenance contract and current palette owner are next.

Observed RED: the palette stub's one real test throws its expected UOE. The
following run passes25 palette cases and rejects USA bundle absence through
stock preparation; JP/EU absence passes. After attaching actual programs, fresh
command96431 completes126/0/0/0 at13:41:37Z: programs11, Stock21, palette25,
World53, actualStarsEGL1, Module4 and ModApi11. Maven1m55, queue0s; total wall not
independently measured. HEAD462b plus candidate has unchanged before/after source
fingerprint `9b2eb07d2834c0580ae9aef2850507ef5a6d88dab3e22adee9cc7f7fc0f67460`,
16156 paths/980507446 bytes. Exact fresh class/case identities, Java25.0.5.0.2,
feature cwd, actual media/S2 paths and EGL opt-in are checked; zero skips. Only
attribution prose changes afterward. Static prerequisite review finds no P1/P2.
All14 consumed XML/TXT diagnostics are removed. This is affected verification,
not full smoke/release qualification. The permanent route remains7350/7351.

Reproduce from this feature checkout with original absolute media paths:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 \
  python3 -B tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true \
  -Dsoniccd.media.root=/absolute/original/media \
  '-Dsonic2.rom.path=/absolute/original/Sonic 2.gen' \
  '-Dtest=TestCdTimeWarpPrograms,TestSonicCdStockPreparation,TestCdLevelPaletteFade,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl#actualEglControllerTimeWarpStarsUseCurrentArtAndRewindNativeServiceCuts,TestSonicCdModuleContracts,TestModApiSignatureSurface' test -B
```

## Track work and evidence

- Keep the design's release identity and ownership constraints while filling
  in implementation. Revisit them when a probe disproves an assumption.
- Treat each phase as a bounded outcome, split into independently verifiable
  changes where needed. The phase IDs below are work packages, not created PRs.
- Keep per-zone matrices in the existing architecture/status structure and
  link them from the [coverage backlog](../../status/level-test-coverage.md)
  as implementation starts. Do not create a second completion ledger.
- Record source labels, program domains, user-media fingerprints, command,
  source SHA, input movie, sampling boundary, skips, and first divergence.
  Missing, implemented, executed, passed, failed, skipped, and not applicable
  are separate states. Zero tests or an unavailable disc never means passed.
- Preserve rejected approaches and their kill evidence in the dated design
  or this plan. Keep raw diagnostics temporary under the testing policy.
- Assign each worker an owned file set or worktree, focused checks, and a
  stopping condition. One integration lead owns combined validation.

## Sequence the program

`CD-01` establishes source/media facts and `CD-02` proves the native oracle.
They can progress together. `CD-03` binds release identity before stock
bootstrap work. `CD-04` and `CD-05` establish media and deterministic CD
services. Audio integration can continue alongside later gameplay work. Complete the
Palmtree Panic and special-stage proofs before distributing later zones.

The main dependency chain is:

```text
CD-01 + CD-02 -> CD-03 -> CD-04 -> CD-05
CD-05 -> CD-06 audio/video -> CD-07 bootstrap/save -> CD-08 player
CD-07 + CD-08 -> CD-09 time travel
CD-05 + CD-06 + CD-07 + CD-08 -> CD-11 special stages
CD-09 + CD-11 -> CD-10 Palmtree Panic closure -> CD-12 remaining zones
CD-06 + CD-07 -> CD-13 non-level work; CD-12 -> CD-13 demo/mode closure
CD-10 + CD-11 + CD-12 + CD-13 -> CD-14 USA MVP
CD-14 -> CD-15 Japan, CD-16 Europe -> CD-17 all-region closure
```

Early device/media phases use real-media component and request probes.
Their exits do not require the later complete gameplay routes. Native route,
menu, and whole-game integration checks join at the stated later phases.

Regional inventory and PAL research start in CD-01/CD-02. Region-specific
delivery follows USA. Infrastructure must represent Europe before USA work
hardens assumptions, even while Europe remains uncertified. CD-10 may start
zone work after CD-09, but closes only after the CD-11 round trip. CD-13 can
build menus early, while later-zone attract demos wait for CD-12.

## CD-00. Pin the requested reference

- [x] Register `docs/scddisasm` as an optional submodule from
  `https://github.com/OpenGGF/scddisasm.git`, with upstream `ai-development`.
- [x] Pin `1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff` and document initialization.
- [x] Run the reference structural audit. It passed with zero missing
  active includes and zero case mismatches.

Counsel synthesis is recorded in the design. Integration, push, checkout,
and cleanup outcomes are reported with this task's delivery.

The pre-existing local reference has modifications and a nested checkout.
Preserve that whole directory before replacing its main-workspace path.
Never reset it or copy its uncommitted content into the new pin.

## CD-01. Inventory the supported products and source contracts

Owner: media/reference workstream. Dependencies: CD-00 reference pin.

- [ ] Discover user-owned USA, Japan, and Europe images without copying,
  renaming, or downloading commercial media. Establish one exact USA target.
- [ ] Record TOC, track modes, indexes, pregaps, sector layouts, full data-track
  identity, audio-track identity, and disc header. Preserve both container and
  normalized content identity. Never identify a whole disc by ISO data alone.
- [ ] Create release entries with region, revision/pressing, product identifiers,
  execution profiles, and supported/unknown status. Use observed hashes only.
- [ ] Inventory the 70 stage payloads, all modes, demos, special-stage variants,
  object tables, event paths, native saves, and regional conditional branches.
  Resolve aliases, inactive sources, and runtime-selected overlays.
- [ ] Join the 127 file-ID and 215 command declarations with built outputs,
  actual media, and dispatch. Classify executable programs, data/media files,
  aliases, invalid commands, and inactive leftovers. Generate or guard provider
  and test inventories from one reviewed catalogue. Counts alone are not coverage.
- [ ] For each regional difference, identify its owning code, table, file,
  media track, or execution environment. Audit consumers before trusting names.
- [ ] Extend source citation and offset-research tooling for program-local CD
  domains when implementation needs it. Existing cartridge address tools do not
  establish a global CD address space.
- [ ] Inventory residual opaque source spans. Promote a semantic investigation
  only when its consumer blocks implementation. Rebuilding the reference is
  useful corroboration, not a prerequisite for every small engine change.

Exit evidence: a finite release/component inventory with every entry assigned
an owner and disposition. Missing user media remains an explicit prerequisite.

## CD-02. Prove native observation before building against it

Owner: TraceChaser/native-reference workstream. Dependencies: identified media
from CD-01. This work belongs in the external TraceChaser project where appropriate.

- [ ] Verify pinned BizHawk 2.11/core disc support with the exact media and a
  documented native execution environment. Identify any BIOS prerequisite.
  Do not assume cartridge recording infrastructure already supports CD.
- [ ] Capture cold boot to title, title to R11A, a complete warp, a special-stage
  entry/exit, PCM/CDDA playback, an opening-video segment, and save/reload.
- [ ] Establish native main/sub-CPU sampling phases, V-int and simulation clocks,
  disc command/response boundaries, Word RAM ownership, graphics completion,
  PCM cursor and bank state, CDDA position, input lag, and program identity.
- [ ] Repeat identical input captures and insert harmless idle input before
  relevant boundaries. Establish which fields are deterministic and which
  native device timing requires a named environment or bounded contract.
- [ ] Identify actual BIOS hashes and decide the game-visible firmware
  contract before freezing execution identity. Start with the design's HLE
  proposal, certify it against those native BIOSes, and reopen the architecture
  if it fails. Any future runtime BIOS bytes require their own verified source.
- [ ] Classify each timing family as source/device-derived, environment-measured
  under a firmware/drive profile, or unresolved. Specify model inputs and
  independent validation. Recorded readiness is diagnostic for unresolved
  families and cannot close full live parity. Blocking waits stay under the
  existing main-loop admission contract unless another contract is proven.
- [ ] Prove USA, Japan, and Europe capture cadence. Record physical refresh,
  gameplay update counts, native timer changes, audio samples, and movie frames
  independently. A PAL flag or a 50 Hz sleep is insufficient.
- [ ] Extend the sole live v5 trace contract and producer/consumer guards through
  an explicit design if CD metadata or timing kinds need representation.
  Keep physics/aux rows comparison-only. Never import gameplay values or make
  fixture/frame/route names select behavior.
- [ ] If GPGX/headless CD support or observation fails, report the exact missing
  capability. Prove an independent native reference path before continuing
  parity-dependent claims. A reference reimplementation is secondary evidence.

Exit evidence: attributable captures and a recorder/comparator conformance
test. No gameplay owner consumes comparison fields. Oracle limitations stay
visible in every affected acceptance row.

## CD-03. Separate game, release, region, revision, and environment

Owner: stock identity/bootstrap workstream. Dependencies: CD-01, CD-02 findings.

- [ ] Inventory all affected public API types and callers first, including
  both `@ModApi` and `@com.openggf.game.ModApi` spellings. Verify that signature
  generation and guards include them. Do not assume a hook found every type.
- [ ] Implement the design's immutable release descriptors and verified media
  identity, including firmware semantic contract, native environment, and
  drive profile. Represent multi-region cartridges without a World console.
- [ ] Keep logical cartridge composition separate from product/revision identity.
  Bind current S1 World REV01, S2 World REV01, and S3K targets to explicit
  profiles without changing behavior. Preserve unrelated accepted unknown-ROM
  development workflows while keeping them uncertified for parity.
- [ ] Select native code/data/offset tables, timing, language/presentation, and
  audio from the resolved release. Do not scatter region checks through managers.
- [ ] Pin release and environment for the lifetime of a WorldSession. A region
  change opens a new session. Key immutable caches and save/recording metadata
  by compatible release identity. Keep donor identity independent from host identity.
- [ ] Reject a forced known release when fingerprints disagree. Test incomplete,
  ambiguous, altered, mixed-region, and unknown images with useful diagnostics.
- [ ] Derive or guard stock game membership across detection, onboarding,
  `TraceCatalog`, `TraceExecutionModel`, save namespaces, tools, and launch.
  Keep `RomIdentity`/`LogicalRom` as cartridge address spaces, not disc regions.
- [ ] Audit `GameId`, `RomGame`, `LogicalRom`, stock switches, boot paths, tools,
  and serialization consumers, plus `data.Game.getRom()` and its relocation,
  export, audio, and checksum callers. Defer factory/abstract-obligation migration to CD-07's real disc consumer.
  Wrap existing cartridge resolution to attach descriptors without changing
  its factories. Public `GameId.SCD` joins the stock API in CD-07. Use the
  internal catalogue key for recognized but not yet admitted CD media.
  Change the unpublished candidate API only where
  necessary, with descriptor, `ModApiVersion`, and normalized pins together.

Exit evidence: current cartridge behavior and lock-on resolution stay covered.
Identical file names with different content cannot share a certified profile.
Region, revision, media format, and execution cadence remain distinguishable.

## CD-04. Read disc-backed assets through one bounded source

Owner: media/data workstream. Dependencies: CD-03.

- [ ] Implement a read-only disc source with complete CUE/BIN data and CDDA as
  the first canonical input path. Prove multi-file and single-file track layouts.
  Add CHD through an evaluated bounded dependency or reader with its own tests.
  Other containers are separately admitted formats, not a parity assumption.
- [ ] Parse sectors, mode/user-data boundaries, TOC/index/pregap conventions,
  ISO directory/file extents, cross-sector reads, and the game's file table.
  Reject malformed ranges and missing required tracks before launching.
- [ ] Add a bounded native-media alternative to `level.resources.LoadSource`
  without borrowing creator asset authority or fabricated cartridge offsets.
- [ ] Resolve program-aware asset slices. Load graphics, palettes, maps, objects,
  animations, collision, samples, video, and sound sequences from the supplied
  media. Never read the disassembly checkout at runtime.
- [ ] Authenticate required audio and video as well as executable data. A bare
  ISO can be an explicitly limited research input. It cannot pass full USA parity.
- [ ] Keep `Rom` cartridge semantics intact. Migrate stock source call sites
  deliberately. CD `rom()` remains absent, with explicit CD module paths rather
  than a fake cartridge image or a standalone-content exception.
- [ ] Bound decoder output, cached bytes, open files, and worker lifetime. Test
  close/reopen, truncation, cancellation, invalid extents, equivalent containers,
  and source changes after launch preparation.

Exit evidence: known asset bytes match native disc extractions and all runtime
asset provenance is attributable to the frozen user-media manifest.

## CD-05. Model deterministic CD service boundaries

Owner: timing/session workstream. Dependencies: CD-02, CD-04.

- [ ] Implement and verify the pure stamp-device core now, using the title's
  native image/trace requests as its first consumer. Special-stage and D.A.
  Garden game programs consume that core later. Keep their game logic separate.
- [ ] Implement the observed subset of disc command, overlay, transfer,
  Word RAM, graphics, audio-command, and BRAM protocol state machines.
  Decode raw commands inside the CD boundary. Gameplay calls semantic operations.
- [ ] Separate host-side preparation from native service/readiness. Host cache
  speed and background threads cannot advance gameplay or choose completion ticks.
- [ ] Classify each native command's acknowledgement and completion contract.
  A synchronous command wait alone does not justify a recorded readiness kind.
  Require the dedicated contract's production work and polling/service conditions.
- [ ] Extend typed hardware work descriptors and fingerprints for disc-backed
  operations. Include media, program/file/slice, operation, destination, and
  all relevant parameters. Never encode a file as a fabricated ROM address.
- [ ] Preserve the readiness-only replay contract. Add a recorded kind only
  after matching production submissions and service boundaries are proven.
  Trace data cannot create jobs or supply their payloads.
- [ ] Keep SP/IPX/SPX algorithms in `game.soniccd`. Pure device cores do not
  run the game's command dispatch. Reuse existing modes, with a bounded hosted
  native-program capability only where needed, instead of per-file Engine cases.
- [ ] Implement the explicit sub-CPU timer and per-MMD interrupt regime.
  `RunPCMDriver` is timer-interrupt work, not one tick per video frame.
- [ ] Implement only timing models classified in CD-02, with cited input/cost
  rules or independently validated firmware/drive manifests. Keep unresolved
  families visible and block live parity closure. Never tune per-operation
  constants to a single movie. Reopen the design before CD-08 if unresolved
  families dominate the intended semantic model.
- [ ] Implement explicit rational native clocks and per-release cadence.
  Preserve present cartridge timing. Cover pause, lag, skipped main loops,
  transitions, resets, and drift over long recordings.
- [ ] Register rewind state for pending jobs, command/ack state, ownership,
  clocks, ordinals, buffers, and consumption. Recreate transient workers and
  invalidate stale generations on restore, reset, and session replacement.

Exit evidence: isolated boot/file-transfer, graphics handoff, and streaming probes match
native observations. Record the live timing evidence class and validation
for each family separately from permitted recorded-readiness diagnostics.
Required unresolved live families block this exit. Warm/cold host caches and
delayed workers yield the same simulation. Full warp gameplay joins in CD-09/CD-10.

If two independent feature families require escaping these services to write
shared hardware state, stop and redesign this boundary before adding another
generic callback or a CPU interpreter. Preserve the failing evidence.

## CD-06. Add PCM, CD audio, and native video

Owner: audio/presentation workstream. Dependencies: CD-04, CD-05.

- [ ] Decode the actual regional FM driver and PCM banks. Match channel update
  order, sample addressing, envelope/loop/stop rules, bank switches, priorities,
  mixing, panning, clipping, and resampling against the native implementation.
- [ ] Implement CDDA track/index/sector transport, play/stop/pause/resume,
  seek, looping, fades, mode transitions, and regional track mappings.
  Soundtrack preference overrides are an extension, not a region implementation.
- [ ] Preserve native PCM/video synchronization and streaming behavior.
  Decode the user's original streams, including opening, good/bad endings,
  and pencil test. Match frame palettes, timing, skip controls, and audio phase.
- [ ] Validate command and chip/PCM state before comparing final audio.
  Use exact equality where the native contract permits it. Document any
  independently justified analog/resampling tolerance and its limits.
- [ ] Capture/restore transport positions, fractional sample phases, stream
  cursors, queued commands, decoder state, and native buffers. Keep the live
  sink outside simulation authority and retain no-device headless boot behavior.
- [ ] Measure bounded buffering and sustained playback without underruns.
  Set performance budgets from measured probes, not invented targets.

Exit evidence: independent effect/music tests plus synchronized native
gameplay and FMV captures, including pause, seek, loop, transition, and rewind.

## CD-07. Establish stock launch, native modes, and saves

Owner: bootstrap/mode/save workstream. Dependencies: CD-03 through CD-06.

- [ ] Add public `GameId.SCD` and the necessary stock/trace/save mappings.
  Make source-based stock launch canonical at this real consumer. Migrate any
  cartridge-only abstract obligations and callers together, with candidate
  version/descriptor/signature updates and focused cartridge checks.
- [ ] Add a genuine Sonic CD stock module and stock selection entry, initially
  labeled incomplete. Bind fresh source and services through WorldSession.
- [ ] Implement the design's host-mode map. `GameLoop` alone commits active
  `GameMode`; the CD coordinator requests transitions and owns only native
  program/load identity and carry state. Review proposed `NATIVE_PROGRAM`
  admission with the candidate API pins. Do not host stock CD via `MOD_SCENE`.
- [ ] Reproduce native startup, opening, title, new game, continue,
  return-to-title, reset, game over, and initial scene transitions.
- [ ] Model native backup RAM operations and records, including internal and
  external storage paths actually reachable in the shipped code. Cover
  unformatted/full/invalid/corrupt data, errors, slot operations, and cancellation.
- [ ] Keep device bytes/format/status in the CD device/HLE service, pure
  game-payload codec and requests in the CD save provider, and durable commits
  in host persistence. Campaign state remains the source for saved progress.
- [ ] Separate emulated BRAM state from host persistence. Rewind/replay cannot
  reissue host writes. Define deliberate commit points and atomic replacement.
  Preserve existing user saves before migration and test crash recovery.
- [ ] Namespace storage by device format and firmware semantic compatibility,
  plus game-payload/release compatibility. Expose cross-release compatibility
  only after verifying all three. Store Time Attack and unlock state correctly.
- [ ] Test repeated boot, region replacement, and cartridge/CD switching in
  one JVM. Assert source, audio, saves, and runtime-art ownership after every edge.

Exit evidence: cold boot through opening/title to the R11A load boundary and
BRAM save/reload through production owners, including failure and restore
paths. Player traversal is a CD-08/CD-10 obligation, not a CD-07 prerequisite.
Attract demos remain CD-13 obligations because their later-zone programs arrive in CD-12.

## CD-08. Implement the native player and shared stage loop

Owner: gameplay workstream. Dependencies: CD-03, CD-05, CD-07.

- [ ] Audit active player copies and native arithmetic/order across all zone
  families before deciding which code is shared. Preserve fixed-point widths,
  signedness, overflow, input latches, and collision order.
- [ ] Implement native movement, rolling, jumping, charge/release spin dash,
  Super Peel-Out, camera focus/look, hurt/death, inactivity behavior, water,
  drowning, power-ups, debug behavior, and animation/DPLC upload rules.
- [ ] Implement object slots, fixed objects, update order, spawn/despawn,
  saved flags by period, touch/solid collision, rings, monitors, checkpoints,
  score, timer, HUD, title cards, pause, results, and life/restart flow.
- [ ] Place broad movement/collision gates in narrow shared rules. Keep
  CD-only mechanics in their actual player/object/event owner. Do not copy S1
  physics and tune it until a movie passes.
- [ ] Integrate native art, tiles, palettes, layers, scroll, camera boundaries,
  sprite limits, and graphics queues. Match native output before wider views.
- [ ] Extend focused cross-game regressions for each changed shared owner.

Exit evidence: independently recorded movement/collision scenarios, slot and
animation checks, production source bindings, and deterministic rewind.

## CD-09. Implement time travel and world history

Owner: CD progression/time-travel workstream. Dependencies: CD-07, CD-08.

- [ ] Model stage location, current period, warp direction/charge/lifecycle,
  checkpoint state, generator/projector state, and global future/Time Stone
  progress as separate concepts. Do not overload zone or act integers.
- [ ] Port signs, speed/continuity gates, cancellation/rearming, trail and
  sound, fade/cutscene/load, destination selection, native coordinate/velocity
  transfer, camera/water/timer restoration, and object reset/persistence.
- [ ] Derive Good Future selection from the native conditions and their scope.
  Verify generator destruction, hologram/projector consequences, act/range
  flags, boss-act selection, and all-Time-Stones behavior independently.
- [ ] Cover warp during edge cases, death/restart after a warp, checkpoint
  interactions, repeated round trips, blocked destinations, and special-stage
  return. Establish each case from reachable source paths and native behavior.
- [ ] Exercise capture/restore immediately before, during, and after each
  handoff. Assert forward replay or explicit timeline isolation according to
  the owning load contract. A time warp is a game transition, not rewind UI.

Exit evidence: a complete R11A-to-Past-to-future production route with all
state owners verified, plus independent short rejection and restore cases.

## CD-10. Complete Palmtree Panic as the reference implementation

Owner: first-zone integration workstream. Start after CD-06 through CD-09.
Closure additionally requires CD-11 for native special-stage entry and return.

- [ ] Implement all R11/R12 A-D and R13 C-D bindings, objects, events,
  scenery, camera/scroll, palette/tile animation, boss children/defeat/escape,
  all entries, all exits, and outgoing progression.
- [ ] Supply act/period/route matrices under the existing
  [level test standard](../../guide/contributing/level-test-standard.md).
  Each applicable ENTRY, OBJECT, EVENT, CAMERA, BOSS, LIFE, LOAD, REWIND,
  PRESENT, ROUTE, and ORACLE obligation needs evidence.
- [ ] Validate native bad-future and good-future progression, special-stage
  entry eligibility and return, saves, title/reset, and host-media failures.
- [ ] Prove offscreen rendering and production PCM capture at native size.
  Add every actually supported width/donor/character/team configuration using
  the standard's required short cross-products and representative routes.
- [ ] Compare a second independent input movie. Keep short failure/rewind
  checks independently runnable instead of behind the full route.

Exit evidence: a complete bounded zone delivery with explicit inherited gaps.
Do not call the first playable act complete USA support.

## CD-11. Complete special stages and Time Stones

Owner: special-stage workstream. Start after CD-05 through CD-07.
Native level entry/return integration also needs CD-08 player support.

- [ ] Bind the stamp-device core proven in CD-05 to the special-stage program.
  Compare its requests and output independently of the title consumer.
- [ ] Implement native stamp graphics, fixed-point projection, clipping,
  object geometry, and Word RAM handoff with independent pixel probes.
  Do not use a visually similar S2 or S3K special-stage renderer.
- [ ] Keep Time Stones distinct from Chaos Emeralds in reward and save contracts.
- [ ] Port player controls, UFO behavior/collision, terrain/stamp effects,
  water/time penalties, timers, rings, rewards, results, pause, failure,
  stage sequence, all seven Time Stones, and return to the correct act state.
- [ ] Implement and verify the eighth/secret-stage path and special credits
  according to native entry/exit behavior established in CD-01/CD-02.
- [ ] Validate both direct development entry and real level entry. Check
  retry, restart, reset, all-stones progression, saves, and Time Attack access.
- [ ] Snapshot all simulation, renderer/graphics-work, audio, and transition
  state. Validate forward replay while a graphics operation is pending.

Exit evidence: every native stage and reachable secret path has an independent
matrix, native render/audio evidence, and correct progression/return behavior.

## CD-12. Complete the remaining six zones

Owner: one bounded zone workstream per zone after CD-10/CD-11 prove shared contracts.

- [ ] Collision Chaos, R3. Cover bumpers/flippers, moving platforms,
  vertical camera behavior, native object interactions, and boss mechanics.
- [ ] Tidal Tempest, R4. Cover water levels/palettes, currents, drowning,
  underwater movement and audio, transitions, and the underwater boss.
- [ ] Quartz Quadrant, R5. Cover conveyors, movement-sensitive mechanisms,
  period-dependent collision/geometry, and treadmill boss behavior.
- [ ] Wacky Workbench, R6. Cover charged floor behavior, electrical hazards,
  freeze/release state, transport paths, special player copies, and boss.
- [ ] Stardust Speedway, R7. Cover route/camera speed, moving transports,
  Metal Sonic race, Amy interactions, failure/retry, and race completion.
- [ ] Metallic Madness, R8. Cover mini-Sonic transitions, layer/scale changes,
  transport machinery, final encounters, escape, and ending selection.

These mechanic lists are initial research targets, not exhaustive zone specs.
For each zone, derive the complete object/event/asset list from all ten payloads.
Every phase includes all normal periods, both boss futures, title/results,
music, palette/tile/scroll changes, progression, save/respawn, and native bugs.

Require the same evidence as CD-10 for every act/period/route. Record
source sharing only after comparing actual consumers and regional variants.
No zone is complete because its data loader or registry entry exists.

## CD-13. Close every non-level program and unlock path

Owner: menus/secrets/presentation workstream. Start after CD-06 and CD-07.
Full attract-demo/mode closure also depends on CD-12's later-zone programs.

- [ ] Complete Time Attack for regular and special stages, record display,
  sorting/ties, persistence, resets, all unlock thresholds, and return paths.
- [ ] Complete D.A. Garden, Visual Mode, opening replay, both ending replays,
  pencil test, sound test, stage select, staff times, all regional secret
  screens, debug/native cheat codes, and special-stage credits.
- [ ] Complete attract demos, title inactivity, native controller behavior,
  credits, Thank You, and reachable Comin' Soon behavior. Explain any retained
  inactive program using actual dispatch and native evidence.
- [ ] Complete R11A, R43C, and R82A attract demos after their production
  player/zone bindings exist. Verify their native inputs, exits, and timing.
  Bind D.A. Garden to the stamp core from CD-05 and verify its own behavior.
- [ ] Exercise every unlock from a fresh save and from persisted data.
  Development shortcuts cannot satisfy native unlock gates.
- [ ] Include error/cancel/back/reset, pause and music transitions, repeated
  mode entry, native rendering, and supported rewind behavior per mode.

Exit evidence: every program/entry in CD-01 has an implemented/tested or
evidence-backed not-applicable disposition. Required unimplemented content
blocks complete parity even when the main route can reach the ending.

## CD-14. Certify the complete USA MVP

Owner: integration/parity lead. Dependencies: CD-10 through CD-13.

- [ ] Complete the USA manifest for every required stage variant, object/event
  family, normal/special mode, audio/media path, persistent record, secret,
  and incoming/outgoing transition. Reconcile expected versus executed cases.
- [ ] Run fresh native full-game routes to bad and good endings. Cover both
  future-restoration and all-Time-Stones progression, death/continue/restart,
  fresh saves, reloads, and separate Time Attack/unlock campaigns.
- [ ] Compare independent input recordings and perturb boundary inputs.
  Confirm the implementation generalizes beyond its development movies.
- [ ] Verify native pixels, palettes, sprite order/limits, scroll, text, HUD,
  video, sound commands, PCM/CDDA output, and their timing at named boundaries.
- [ ] Exercise supported OpenGGF extensions separately. Preserve the native
  acceptance lane when enabling wider views, donors, or creator features.
- [ ] Run the required focused/domain checks and change-based smoke coverage.
  Protect S1/S2/S3K stock launch, gameplay, rewind, saves, audio, and rendering.
  Keep the four named S3K regression obligations in AGENTS.md satisfied.
- [ ] Verify Windows, Linux, and macOS packaging/launch paths for the supported
  media formats and newly required decoders. Admit dependencies deliberately.
- [ ] Update stock selection, player/setup/configuration docs, known gaps,
  coverage backlog, active-line changelog, and affected mirrored agent skills.
  Remove incomplete labeling only when its acceptance conditions pass.

Exit evidence: zero unresolved required USA parity gaps, including live timing
families and firmware/HLE behavior. Recorded-readiness-only agreement cannot
satisfy this playable-game gate. Require zero missing or skipped required
USA acceptance cases, and no new/worsened shared regressions.
Known unrelated baseline failures require exact identity/attribution. They do
not erase missing CD evidence. Public release preparation uses the repository's
explicit full-suite, guards, domain checks, and release-publishing workflow.

## CD-15. Certify Japan

Owner: regional workstream. Dependencies: CD-14, early regional research.

- [ ] Bind the verified Japanese media/revision and native execution profile.
  Implement every code/data/presentation/media difference from CD-01.
- [ ] Compare shared components before reusing evidence. Execute every changed
  binding, every native mode, all period families, complete good/bad routes,
  special-stage and unlock paths, audio/video, and backup RAM behavior.
- [ ] Reject mixed USA/Japan data/audio sources and invalid forced profiles.
  Test new-session switching and any deliberately supported save compatibility.
- [ ] Complete the Japanese acceptance matrix and documentation with the same
  parity definition as USA. A soundtrack toggle cannot satisfy this phase.

## CD-16. Certify Europe and PAL execution

Owner: regional/timing workstream. Dependencies: CD-14, early PAL research.

- [ ] Bind verified European media/revision and native PAL execution profile.
  Implement the audited code, layout, localization, and media differences.
- [ ] Validate native simulation/timer cadence, input, V-int/main-loop phase,
  display geometry, FM/PCM clocks, CDDA sample cadence, video sync, and loading.
  Do not apply a global 50/60 speed multiplier to conceal subsystem differences.
- [ ] Repeat every required European mode/period and good/bad route, special
  stage, unlock, save, audio/video, transition, and rewind obligation.
- [ ] Validate long-duration clock drift and deterministic movies. Separate
  host display refresh from the selected native simulation environment.
- [ ] Keep Japan/USA behavior unchanged when selecting Europe, then restore
  a cartridge session and verify its existing profile.

## CD-17. Close the all-region contract

Owner: integration/parity lead. Dependencies: CD-15, CD-16.

- [ ] Reconcile all required release × native-environment × mode/stage/period
  rows. Share evidence only for proven identical behavior and real bindings.
- [ ] Require an explicit supported revision list. Unknown pressings remain
  uncertified until their content and behavior are verified. Three regions
  do not imply every pressing, prototype, rerelease, or remaster is supported.
- [ ] Retain complete component and transition coverage, negative media tests,
  independent native oracles, rewind/load isolation, and platform launch proof.
- [ ] Record the finite corpus used to support the parity claim and any limits
  of its native timing environment. A test corpus cannot prove every possible
  input sequence. Newly discovered shipped behavior creates a tracked obligation.
- [ ] Prepare a public release only under the actual release policy. Confirm
  its downloadable artifacts before saying the fourth game has shipped.

## Choose proportionate verification for each delivery

Follow [AGENTS.md](../../../AGENTS.md) and the
[testing guide](../../../tools/testing/README.md), not a second testing policy.
Focused checks prove bounded changes. Shared algorithms, physics/timing,
public contracts, or uncertain changes normally need change-based smoke.

Before broad validation, inspect
`python3 tools/testing/run_categories.py --base <pre-task-integration-sha>`.
State its candidate count, expected cost, and stopping rule. Run its preflight
in the launch environment, then the queued runner when appropriate. Reuse an
existing matching active job. Do not run duplicate baseline/development/merge
suites by ceremony. Attribute disputed failures with bounded matched tests.
Full suites are reserved for public release preparation.

Required native/domain checks remain separate from smoke. Every domain gate
names its actual command and selector when its implementation exists. The
plan does not invent runnable Sonic CD tests or recorder flags in advance.
Do not narrow a category selection by hand, relax an assertion, increase a
tolerance, or suppress a missing input just to close a phase.

Use headless gameplay and owned offscreen rendering. Never use the shared
desktop for automation. Measure performance after correctness probes exist,
interleave comparable baseline/current scenarios, and set budgets from evidence.
No fixed duration or staffing estimate is supported by this planning task.

## Preserve the finite coverage obligations

Each applicable row needs both short independent checks and meaningful route
evidence. Add cases when an observed branch makes the initial inventory incomplete.

| Coverage axis | Required contract |
| --- | --- |
| Product | USA, Japan, Europe with an exact revision/media manifest and environment |
| Stage payload | 70 declared retail payloads per region, with aliases/reachability explained |
| Progression | Every stage edge, all period warps, both boss futures, Time Stones, both endings |
| Object/event | Every dispatched family, subtype and meaningful state/interaction, including children |
| Lifecycle | Cold entry, checkpoint, death/restart, game over/continue, reset, outgoing load, reload |
| Rewind | Before/during/after each stateful boundary, pending service work, isolation on intentional resets |
| Non-level | Every native mode, secret, demo, unlock, record and save/error path |
| Presentation | Native pixels/palette/layer/scroll/sprite rules, audio and video synchronization |
| Extensions | Every supported width/donor/character/team obligation under the existing standard |
| Regression | Existing cartridge identities/profiles, public pins, sources, sessions, audio and saves |

## Checkpoint for this planning delivery

The lead owns documentation and submodule integration in
`.worktrees/ai-sonic-cd-parity-plan-20261023`. Astra, Opus, and Fable have
read-only design tasks. No engine code, POM, test selection, or queue policy
changes are part of this delivery. Its verification uses gitlink/configuration,
reference structure, documentation links/mirrors, policy, and checkout scenarios.
Engine parity and native media validation remain future phase work.

## Local implementation checkpoint, 23 October 2026

Implementation is authorized on `feature/ai-sonic-cd-local` only. The starting
commit is `63222aab395db19d3a3b12f797fb7803876898bd`; the retained worktree is
`.worktrees/ai-sonic-cd-local`. Do not integrate or push this work to `develop`.
The earlier planning commit was reverted on `develop`; its public history is
not erased. The current task does not change that delivery boundary.

The user supplied three regional CUE/BIN archives and a BIOS archive in
Downloads. Extraction preserved their contents and all four original archives.
All 121 extracted files matched archive sizes and CRC32 values. Each disc has
35 tracks, comprising one MODE1/2352 data track and 34 audio tracks. CUE file
references, indexes, sector-sized files, and U/E/J header fields passed checks.
`~/Downloads/Sonic-CD/media-inventory.json` records archive and per-file
SHA-1/SHA-256 identities and the supplied CUE layouts. These checks establish
available inputs, not pressing authenticity, firmware certification, physical
read offsets, native boot, or game parity.

The first implementation units have separate writers. The lead owns this
checkpoint, review, combined validation, and local commits.

| Unit | Owned implementation | Exit check | Current state |
| --- | --- | --- | --- |
| Disc source | `com.openggf.data.cd` and its tests | Synthetic malformed/layout/lifecycle cases and actual file reads from all three discs | In progress |
| Native catalogue | `com.openggf.game.soniccd` and its tests | All 70 valid stage payloads per region, invalid combinations rejected, source dispatch and component facts checked | In progress |
| Reference oracle | Task-owned `tools/soniccd/oracle_*` and Downloads captures | Verified BIOS selection, native USA CUE boot, observable memory domains, repeated bounded capture | In progress |

The first code shape uses one closeable CUE owner, immutable track facts, and
bounded ISO file extents. A generic machine or cartridge address adapter would
hide the disc's separate tracks and lifetimes. The catalogue uses a shared
finite source table with release-bound views. Duplicating 70 stage entries for
each region would obscure which facts differ by release. Traversal stages and
boss stages have distinct valid period combinations.

The native oracle and pure media/catalogue work proceed independently. Gameplay
parity remains behind CD-02's native evidence gate. Focused media/catalogue
tests can establish their own behavior without certifying the later game.
Workers do not run concurrent Maven jobs in this worktree. The lead uses the
current main-workspace queue wrapper with this worktree as its working directory
and owns the single command session for each validation job.

Next checkpoint records actual source state, exact commands, failures, skips,
and native capability limits before the next dependent unit starts. Decision
and evidence updates stay in this dated plan, following repository guidance.

### Recovery after the app restart

The worktree still has the partial disc reader and native catalogue. The earlier
workers and queued Java version probe did not survive; no completed Java or
native boot check is claimed. Fresh app-owned delegates continue the same owned
paths from those files. Their task IDs are recorded here so a later restart can
recover the actual tasks instead of starting duplicate work.

- Reader: `node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-resume-reader-20261023-r1`.
- Catalogue: `node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-resume-catalogue-20261023-r1`.
- Native oracle: `node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-resume-oracle-20261023-r1`.

The copied category runner selects the repository containing its own script.
Use this worktree's `tools/testing/run_categories.py` when inspecting changes.
The queue wrapper explicitly uses the caller's working directory and can be
invoked from the main workspace as documented. A plan from the main checkout
does not validate this development tree.

The replacement version probe completed through the shared queue with no wait.
`python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py -v`,
from this worktree, reported Maven 3.10.0 and Java 25.0.5.0.2. This is environment
verification only. Source compilation and focused behavior tests remain pending.

### First unit and native boot evidence

The catalogue worker completed its source unit. Its rerunnable source audit
passed at reference pin `1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff`:
`python3 -B tools/soniccd/catalogue.py --check`. The focused Python command
`python3 -B -m unittest discover -s tools/soniccd -p test_catalogue.py -v`
passed 17 tests with no skips. JUnit selectors `TestCdStageKey,TestCdProgramCatalogue`
and the actual-reader integration remain pending. The source inventory has 216
nonzero dispatch slots, including unnamed invalid slot 39, alongside the 215
named SCMD declarations. These are different counts of the same source table.

The catalogue covers a shared set of 70 stage payloads per release, all 127
file IDs, and 33 nonstage load bindings. Data streams, unused prototype copies,
payload aliases and files without an SPX load binding have explicit dispositions.
Source review found R33's R3PCM driver and R83's BOSSPCM load boundary, the dummy
commands' MCSONIC payload, and Time Attack's main-only SPX loader. Independent
review task: `node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-catalogue-review-20261023-r1`.

The native worker's initial 2,400-frame USA run reached the title screen. Its
`~/Downloads/Sonic-CD/native/usa-initial-2400/summary.json` records BizHawk
2.11.0.0, Genesis Plus GX, Mega CD mode, and a successful 120-frame native
save/restore forward-replay snapshot comparison. The supplied US Model 1 v1.10
BIOS matches an acceptable firmware-database entry at SHA-1
`f4f315adcef9b8feb0364c21ab7f0eaf5457f3ed`. The contact image was inspected.
The core exposes main RAM, CD program/Word/backup RAM, VRAM, and main/sub CPU
bus domains. Repeated boots, regional probes, and the exact observability
limits are still being checked. This is native oracle capability evidence;
it does not close the complete CD-02 timing/capture gate or any engine parity gate.

The catalogue review found two material omissions before Java validation.
Seventeen sound-test commands may load a PCM driver; their audio disposition
must not erase that conditional file dependency. `SPCmd_TestR3BMus` and
`LoadPCMDriver` (SPX.asm:2393-2397,2456-2464) show the path. The Python audit
also accepted a mutation changing the stage-disposition return to AUDIO,
because it substituted its expected classification. Both findings are accepted
after source inspection. Fix task:
`node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-catalogue-fixes-20261023-r1`.
The earlier source-audit pass is bounded by these findings, not a catalogue
acceptance. The fix adds native PCM dependency facts and mutation regressions;
the Java behavior checks remain authoritative for the executed catalogue API.

### Verified foundation unit

Both catalogue findings are fixed. The source audit passes and its focused
Python suite now passes 24 tests, including mutations of PCM targets and
disposition branches. The audit checks source correspondence; executed Java
behavior remains covered by JUnit. Independent reader review found no material
defect at fingerprint `a8f1453762f14c94baadc4dc5fabf0e34442c199881bbfe3cbcbd9e1d6833e0b`.
It proposed additional buffer-refill, reverse mixed-sector and depth-limit
fixtures for the next media unit.

From this worktree at `63222aab` plus the foundation source, the lead ran:

Here `OPENGGF_MAIN` identifies the main checkout and `SCD_MEDIA_ROOT` identifies
the supplied regional media directory in Downloads. The external checkpoint
retains the machine-specific invocation.

```bash
python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestCueDiscImage,TestIso9660FileSystem,TestSonicCdDiscMedia,TestCdStageKey,TestCdProgramCatalogue,TestRomImageClassifier,TestRomImageCatalogue,TestRomLocationResolver' -Dsoniccd.media.root=${SCD_MEDIA_ROOT} test -B
```

The run passed 143 tests, with zero failures, errors or skips. It compiled all
4,203 production and 4,312 test source files under Java 25. The selection includes
24 synthetic disc tests, three real regional disc invocations, 72 catalogue/key
invocations and 44 existing cartridge catalogue/classifier/location invocations.
Queue admission was immediate; Maven reported 1 minute 23 seconds. Compiler
deprecation warnings and test startup's missing FAST manifest warning did not
produce a failure. This is a focused pass, not a broad smoke or full-suite pass.

The local category plan selected 3,621 smoke candidates because the new package
and tools have no narrow change rule. Focused coverage replaces that broad run
for this unit: it adds isolated, currently unregistered libraries and diagnostic
tools without changing existing runtime callers, shared algorithms, physics,
timing, public API pins, build policy or configuration behavior. Stock admission
and later shared integration still require their affected smoke coverage.

The final native probe source and wrapper produced two matching cold boots in
each of USA, Japan and Europe, with 1,800 observed hardware frames per boot and
a 120-frame save/restore audio/video continuation check. Selected BIOS SHA-1s
are USA `f4f315adcef9b8feb0364c21ab7f0eaf5457f3ed`, Japan
`4846f448160059a7da0215a5df12ca160f26dd69`, and Europe
`f891e0ea651e2232af0c5c4cb46a0cae2ee8f356`. Five Python wrapper tests passed.
The final captures, source/runtime manifests and limits are under Downloads in
`Sonic-CD/native/{usa,japan,europe}-title-final-1800` and
`Sonic-CD/research/native-oracle.md`.

Managed bus peeks intentionally hide handled device I/O with FF; only the main
CPU bus advertises memory callbacks. Boot repeatability therefore does not prove
PCM/CDDA cursors, Word RAM ownership, graphics readiness or service timing.
A separate read-only observer investigation continues under task
`node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-observer-20261023-r1`.
It owns only `device_observer_*` tools and external evidence. No engine gameplay
uses these observations. No Sonic CD game module or playable route is admitted yet.

### Next local units

The verified foundation is committed locally as `9152f2ef2`. The commit passed
the installed policy hooks; machine-local paths in this plan were replaced by
environment variables after the first resource-policy rejection. No hook was
bypassed. Source changes were not repeated or retested for that prose-only fix.
The branch remains unpublished and the main checkout remains on `develop`.

Program-local MMD asset access and whole-disc observed identity are the next
consumer of the reader and catalogue. Task
`node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-program-assets-20261023-r1`
owns only the new `game.soniccd.media` package and a developer inspection tool.
It does not add stock admission, a CPU interpreter, or creator asset authority.

Task
`node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-disc-regressions-20261023-r1`
owns the three existing synthetic reader test/helper files to add buffer-refill,
reverse mixed-sector and depth-threshold cases. Production reader changes
require evidence from a failing case. These units and the device observer use
separate files; the lead remains the sole Maven owner. The 143-test pass belongs
to the foundation commit, not to later uncommitted source or tests.

The regression-fixture worker returned three test-only cases: a patterned
131,389-byte sequential stream spanning both 64 KiB refill boundaries; a shared
2352-to-2048 transition with stored gap and distinct boundary data; and depth
acceptance at eight levels including root versus rejection at nine. Production
is unchanged. Their focused JUnit validation remains pending while the program
asset worker owns source edits. No duplicate Maven job is queued.

Whole-disc provenance research runs separately under
`node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-media-provenance-20261023-r1`.
It may match public checksum metadata to the supplied full track set, but cannot
turn a header, mirror or repeatable emulator boot into pressing certification.
Its output belongs in Downloads research; no commercial media is acquired.

The provenance result matches all 35 tracks by size, CRC32 and SHA-1 for each
supplied region against the current primary Redump DAT: USA record
[29188](https://redump.info/disc/29188), Europe
[8302](https://redump.info/disc/8302), and Japan
[1632](https://redump.info/disc/1632). The lead repeated the inventory-to-DAT
comparison. US/European CUE bytes match the primary exports; Japan differs
only in filename capitalization. The DAT includes each CUE as well as its
35 BIN entries. No numeric revision is supplied by these records. The four
other catalogued USA retail variants share the data track but differ at audio
track 13. A data-track-only identity would therefore lose a real content variant.

The primary export's uncompressed SHA-256 is
`6910bd275ab99049147bb43367f9dfa149e164bf1dd0486acb96523e740f4fea`.
The Downloads report preserves metadata pins, track/index comparisons and
physical acquisition limits. Content matching does not establish a physical
pressing, firmware contract or game parity.

Task
`node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-retail-content-20261023-r1`
owns a new `game.soniccd.release` matcher. It binds source-verified whole-disc
layout/content identities to known content records, separately from the source
region enum. Its expected fingerprints are derived from the matched metadata,
not a trusted header or a fitted gameplay observation. Validation is pending.

### Asset and device observation checkpoint

The program-asset and retail-content workers returned stable source. The
retail reproducer matched all 105 public-metadata track triples and independently
derived the normalized fingerprints used by the Java asset tests and matcher.
Six reproducer Python tests passed. The reader's three new boundary fixtures
remain in the same combined validation unit.

The lead queued this focused Java command from the local worktree at `9152f2ef2`
plus those new files. Its result is pending; the preceding 143-test pass does
not cover them.

```bash
python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestCueDiscImage,TestIso9660FileSystem,TestCdMmdImage,TestCdObservedMediaIdentity,TestCdProgramAssets,TestSonicCdProgramMedia,TestSonicCdMediaInspectTool,TestCdRetailReleases,TestSonicCdRetailMedia' -Dsoniccd.media.root=${SCD_MEDIA_ROOT} test -B
```

Asset review task:
`node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-asset-review-20261023-r1`.
Observer review task:
`node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-observer-review-20261023-r1`.
Both are read-only, on stable scoped files.

The observer worker proved read-only device access through pinned ELF symbols
and the existing Waterbox monitor, with source-derived structure offsets and
exported-RAM anchors. It captured PCM, CDDA, CDD/CDC, ownership, graphics and
timer fields without invoking device read handlers. Two repeated USA boots,
observer-free and debugger-callback controls, and four restored continuations
preserved native AV and state. Six Python tests and two real identity/layout
rejection experiments passed. All producer hashes and limits are in Downloads
`Sonic-CD/research/device-observer.md` and its final manifest. Frame-boundary
sampling still misses transient service edges; CD-02 remains open.

The first combined Java run compiled successfully and executed 60 tests with
zero assertion failures, one error and zero skips. The error was
`TestSonicCdMediaInspectTool.realMainWritesExplicitExternalJsonAndRefusesToReplaceIt`:
the tool treated an unrelated Git marker on a user-home ancestor as an OpenGGF
source checkout. The test already used an external Downloads directory. Root
inspection confirmed that ancestor had neither the project POM nor source tree.
The fix recognizes an actual OpenGGF source root, retaining media, symlink and
new-file protections; its new regressions cover all three regional CLI options.
Fix task: `node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-cli-fix-20261023-r1`.

Asset review also found an identity-provenance mismatch not covered by that run:
the helper could combine one open disc's track hashes with another supplied
CUE's file records, or re-resolve a retargeted BIN symlink. The fix keeps frozen
CUE bytes and bounded source views on the open owner, hashes its retained file
handles, and rejects the wrong-CUE compatibility call. Normalized identity
semantics and the three retail fingerprints are unchanged. Wrong-owner,
symlink-retarget and source lifecycle tests are added. Fix task:
`node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-provenance-fix-20261023-r1`.
The same affected nine-class selector is running again after these source fixes;
the final result remains pending. This is an affected rerun, not a duplicate pass.

Observer review passed its five files and source/runtime/artifact bindings at
fingerprint `fe4ac9ad0777ce63a917fc6f667022a5199f65a676bcda38b82f67f87523a3ce`.
It confirmed read-only memory access and the recorded repetition/control/restore
evidence, retaining the one-disc x86-64, frame-boundary and wrap limits.

Two separate native investigations continue on new tool prefixes. Stamp input
and output fixtures are owned by
`node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-stamp-fixtures-20261023-r1`;
exact service-event observation is owned by
`node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-service-events-20261023-r1`.
They do not write Java source or the shared Maven target while validation runs.

The affected rerun executed 68 tests with zero assertion failures, three errors
and zero skips. Both provenance regressions passed. Only the three new
`cliAllowsExternalJsonUnderUnrelatedAncestorGit` parameterizations errored: their
synthetic fixtures lived inside the real worktree's generated target directory,
so the source-checkout guard correctly found that ancestor. The real external
CLI invocation passed. This is a test/output-contract mismatch, not another
home-Git misclassification.

The lead chose an explicit output contract for the undelivered diagnostic tool:
protect media, existing files and project source; permit temporary diagnostic
JSON under the generated `target/` tree, as required for Maven-owned test output.
Canonical symlink resolution must prevent a target alias from granting writes
into source. Nested real source roots stay protected. This avoids external
test scratch, test skips and extra output-policy flags. Fix task:
`node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-cli-target-policy-20261023-r1`.
Only the affected CLI class needs rerunning after that fix; the 65 other passing
invocations are retained on unchanged source.

The generated-output fix returned stable CLI source and boundary tests. The
lead is running only `TestSonicCdMediaInspectTool` through the shared lean queue
with the explicit regional media root. Its live command session is `73622`;
there is no other task-owned Maven job. The two native investigations remain
active and own only their tool prefixes and external build/capture directories.

Before committing the observer tooling, its task-specific output-root literal
must become a portable default or explicit parameter. The currently verified
observer producer stays frozen while both native consumers use it. No installed
binary, probe payload or layout is to be changed for that portability cleanup.
The observer files remain accounted-for local work, not an accepted Git unit.

### Verified media assets and retail content checkpoint

The CLI-only run above finished with 13 tests, six errors and zero skips.
All six errors were generated-output positives. A linked worktree sits below
the main source checkout, so checking every source ancestor assigned two owners
to the same output. The final contract uses the nearest canonical source root.
Its target directory is allowed; its source remains protected even when that
checkout itself sits in an outer target directory. This replaces the earlier
all-ancestor interpretation. Source/media symlinks and existing-file replacement
remain rejected.

The final affected command at `9152f2ef2` plus the completed asset/release unit
passed 14 tests, zero failures, errors or skips. Maven took 1 minute 18 seconds;
queue admission reported zero seconds of waiting. Session `37225` is finished.

```bash
python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py --lean -Dmse=off -Dtest=TestSonicCdMediaInspectTool -Dsoniccd.media.root=${SCD_MEDIA_ROOT} test -B
```

The preceding combined run's other eight classes passed all 60 invocations,
including real media for each region and both owner-bound provenance regressions.
Their source has not changed since that run. The completed unit therefore has
74 distinct passing invocations across these two runs, not a single 74-test
run or a broad smoke pass. Six retail-profile Python tests also passed.
The earlier 143-test foundation result remains separate. There is no live
task-owned Maven job. New device Java is excluded from this verified unit.

Known retail content keys are `redump-29188` (USA), `redump-8302` (Europe)
and `redump-1632` (Japan). All 105 metadata triples match the pinned primary
DAT, and actual-reader tests reproduce the complete layout/content fingerprints.
The API keeps region, content record and source provenance separate. A forced
known key rejects a different disc. This is not stock game admission, physical
pressing provenance, firmware certification or gameplay parity.

### Native continuation and service observation handoff

Both native investigations returned stable new tool prefixes and stopped all
owned processes. Service callbacks produced repeated/control/restore evidence
for the title and a normal-button route visibly reaching Palmtree Panic.
The source audit corrected the earlier scope assumption: S68K shares the
callbacks advertised as `M68K BUS`. CPU identity and access width are unknown
in that adapter. Sector forwarding is paired and preserves original results.
Graphics launch is bracketed before any lines complete, but busy clear, final
output completion and ownership return remain distinct boundaries. Eleven
Python checks passed. Exact owner events and clock epochs keep CD-02 open.

Six native title fixtures retain full 256 KiB ingress and egress Word RAM,
with partial progress explicit. Exact continuation comparisons match every RAM
byte and the complete 12,288-byte image. The observed subset is mode 7, normal
priority, 256 by 96 pixels; rotation, mirroring, wrapping and other modes are
not proven by these captures. Twelve Python checks, repeated boots, an
observer-free AV control and restored continuations passed. Egress is a
pre-consumption ownership poll, not an exact completion timestamp. Commercial
payloads stay outside Git in the Downloads task directory. These diagnostic
schemas are not V5 or engine-input authority.

Fresh scoped tasks now own the next units:

- `sonic-cd-stamp-device-20261023-r1` implements pure device semantics and
  synthetic plus opt-in exact continuation JUnit tests in the new device package.
- `sonic-cd-native-tools-review-20261023-r1` reviews only the new service and
  fixture tools and their evidence, read-only.
- `sonic-cd-native-owner-ledger-20261023-r1` prepares a separate pinned native
  core build and bounded owner instrumentation. Installed cores and frozen
  helpers remain unchanged.

Each task ID uses the existing delegated-task command prefix recorded above.
The parent retains Git, Java compilation, Maven target and delivery ownership.
All implementation remains on the local feature branch with no upstream,
integration or push. No playable Sonic CD module is implemented yet.

The media-loading unit is committed locally as `ae72cb487`; its patch and the
updated plan are exported to Downloads. The native-tool review verified both
fixture runs' full RAM comparisons and independently reproduced all six images.
It found one P2 acceptance gap: 1M ingress and changes to egress stamp mode or
priority could pass validation. Existing captures have the expected settings.
Fix task `sonic-cd-native-guards-portability-20261023-r1` owns the producer and
validator guards, regressions, a fresh guarded capture, and portable Python
output-root defaults. The review's nine-file fingerprint was
`0ed1cf71393cfeafc94f4857d437257926a9c5ebf4bbf8ebf8999bb12442d045`.
Captured original producer sources are preserved in the Downloads source bundle
before these changes, so old manifests remain attributable without rewriting
their pins. The pure Java and native-owner workers have the finding and changed
helper ownership. Their owned packages/prefixes remain separate. No Maven job
is active, and no probe unit has been committed yet.

### Guarded probe delivery checkpoint

The accepted fixture finding is fixed in both producer and validator. Ingress
requires 2M sub ownership and matching raw request/register state. Stamp mode
and priority stay bound while the ownership bits legitimately return to main.
The saved review mutations now reject. Eighteen stamp Python tests, including
27 compiled C# guard cases, six device tests and eleven service tests passed
without skips. Twenty-eight CLI scenarios checked portable roots and path guards.
Machine-specific Python root literals now derive from the user's home directory.

Two fresh 1,500-frame observed boots, an observer-free control and every
120-frame restored continuation passed. All six guarded ingress/egress RAM
payloads, images, inventories, AV streams and checkpoints equal the originals.
The guarded manifest SHA256 is
`10552d10b019ea1a689f62d85b32d4ae8c9f85468b66cb6230f19b095f09781a`.
The parent independently checked its current producer hashes and all bound run
streams/full-byte continuations with `stamp_fixture_validate.py`. Old manifests
remain unchanged and refer to their captured source versions; the historical
service checker correctly rejects a revised wrapper rather than silently
certifying it. The Downloads source archive retains the original versions.

The separate pure Java device unit returned seven stable files. Its pending
focused command is below. Session `14205` owns the only Maven job. Fresh review
task `sonic-cd-stamp-java-review-20261023-r1` is read-only on those seven files.
The native-owner build continues separately and does not write Java or target.

```bash
python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestCdStampGraphics,TestStampFixtureAdmission,TestCdStampNativeContinuations' -Dsoniccd.stamp.fixtures=${SCD_STAMP_FIXTURES} test -B
```

The device has no runtime registration, timing authority, file reads or hidden
mutable worker state. It transforms caller-owned 2M Word RAM in bounded line
steps. Exact native evidence remains partial mode-7, normal-priority title work;
other modes, rotations and wrapping have constructed tests only. Compilation
and JUnit are still pending at this checkpoint. CD-02 and CD-05 remain open.

The first device command finished in 1 minute 19 seconds with 112 tests,
one failure, zero errors and zero skips. All six native full-RAM continuations
and all 52 fixture-admission invocations passed. The sole failure was the
constructed overlap expectation in
`TestCdStampGraphics.takesEachSubsequentVectorFromTheCurrentCallerMemory`.
Its second output replaces only a byte's high nibble; the expected array
incorrectly discarded the original low nibble of the overlapping delta word.
Pinned `gfx_render` preserves that paired pixel. The lead removed the erroneous
expected-byte assignment; production source is unchanged. Only
`TestCdStampGraphics` is rerunning, in session `82450`. The other 58 passing
invocations retain unchanged source and need no ceremonial repeat.

### Verified pure stamp transform

The focused rerun passed all 54 `TestCdStampGraphics` invocations with zero
failures, errors or skips. Maven took 24.041 seconds, with zero queue wait.
Together with the unchanged six native continuations and 52 admission checks,
this establishes 112 distinct passing checks across the two affected runs.
It is not a single green combined run or a broad smoke result. Session `82450`
is finished; no task-owned Maven job remains.

Fresh review `sonic-cd-stamp-java-review-20261023-r1` returned bounded PASS on
the final seven files. Independent calculations matched all 10,240 local
orientation coordinates and 262,144 priority-byte combinations against the
native source semantics. It also reproduced all six complete egress RAM images,
hashes and cursor results, and verified the overlap correction. The reviewed
production file SHA256 is
`83683b04ee3f218fbf20d841dbb2dd978f6824c9c1c8d6f7cee76b7446cf1253`.

The transform supports both stamp sizes, both map sizes, all eight orientations,
priorities, clipping/repeat, unsigned cursor wrapping and overlapping reads and
writes. Immutable semantic progress plus caller-owned memory is sufficient to
restore and replay a bounded operation. These source/constructed checks do not
extend the native capture coverage beyond mode-7 title continuations. There is
no game module, live scheduling or 1M RAM integration in this unit.

The native probe tools were committed locally as `39dd03c50`. Next bounded
tasks are `sonic-cd-pcm-device-20261023-r1`, owning only the new PCM subpackage
and optional integer-oracle tool prefix, and `sonic-cd-stage-data-20261023-r1`,
owning only new stage-inventory tools and external evidence. The separate
`sonic-cd-native-owner-ledger-20261023-r1` continues its source-built core work.
The lead owns future compilation, validation and local commits. No branch is
being merged or pushed.

### Verified PCM sample device and owner-event handoff

The pure stamp transform is committed locally as `372af0e6f`. The next PCM
unit adds explicit register/RAM access boundaries, eight-channel integer
sampling, source-defined loop/sign/pan/envelope/clipping behavior and deep
immutable capture/restore. Work is bounded to 4,096 samples per request. Zero
work does not service time. CPU synchronization, PCM driver execution, host
volume, blip synthesis, resampling and live audio integration remain external.

At `372af0e6f` plus the eight PCM files, the following focused command passed
36 tests, zero failures, errors or skips. Maven took 1 minute 18 seconds and
queue admission reported zero wait. Session `8220` is finished.

```bash
python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestCdPcmDevice,TestCdPcmState' test -B
```

Four Python checks passed separately, covering twenty constructed integer cases
against fingerprint-verified pinned C with stub delta collection. That harness
does not include resampling and is not a Java-vs-C differential run, official
native capture or hardware proof. Fresh review
`sonic-cd-pcm-review-20261023-r1` returned bounded PASS on all eight files,
including the source fingerprints and meaningful literal expectations.
The reviewed production device SHA256 is
`cf5183e0e5243159592b9482c42e1fc8fcce6eced9ada7d9a81d77b81c4d2340`.
New task `sonic-cd-pcm-native-fixtures-20261023-r1` owns only a new tool prefix
and separate native builds/captures to observe real PCM blocks. Current Java
and the completed owner-ledger tools are frozen inputs to it.

The native-owner build unit returned fifteen stable tools and a separate pinned
R3 core. It captured 103,646 USA-title events with CPU identity, native access
width, owner commits, separate graphics start/busy-clear/final-output phases,
and explicit reset/rebase epochs. Repeated disabled/enabled captures and
120-frame restores matched AV and sampled public CPU/RAM views; all four
instrumented checkpoints are byte-identical without a mask. Twenty-four Python
tests and actual identity/layout/overflow controls passed. Fresh task
`sonic-cd-native-owner-review-20261023-r1` is reviewing that unit read-only.
Its final build and event artifacts remain external. No owner tool is committed
at this checkpoint. Source-model CDD seek approximation is not physical-drive
timing. CDC DMA completion and enabled level-3 IRQ remain unexercised.

One original official cold checkpoint hash differed despite matched AV/public
views/restore output. That earlier producer retained no raw state, so the
differing bytes cannot be attributed. The lead performed two additional
official-core boots using the immutable R3 compiled producer in control mode.
Both complete 2,026,393-byte states match byte-for-byte and hash to
`7bda4839945dfec7d9419d9a21692ada9603a10baa00d3c16845981279cc479a`.
Their AV/views/restored continuation streams also agree, and all eight installed
runtime pins remain unchanged. This bounded repeat did not reproduce or explain
the historical difference. It does not retrospectively turn that pair into a
raw-state pass or establish general cold-state determinism. The exact records
and raw follow-up states remain in Downloads with the owner-ledger research.

The stage-inventory task continues independently. No task-owned Maven or native
diagnostic process remains active at this checkpoint; delegated work may create
its own bounded external builds. The lead owns subsequent target writes, Git
and validation. All commits remain local, and no playable CD module exists yet.

The verified PCM unit is committed locally as `89fc2b9a0`. Native-owner review
found one P2 selector gap: main gate-array mirror `A12043` decodes to register
03 but was excluded from ownership commits; sub alias `FF8203` was accepted by
the owner hook but omitted from CPU write pairs. All 1,194 recorded R3 commits
used canonical ports, so their evidence remains valid. Fix task
`sonic-cd-native-owner-alias-fix-20261023-r1` owns only the selector/derived-patch
and affected tests, plus a separate R4 build and controls. The PCM-native worker
uses the immutable R3 source snapshot rather than concurrently changing files.
The review's fifteen-file fingerprint was
`be13540d8915813ef8ff63d10c71dd6d5a514412afd3966637b73ff9a2dfbfce`.

Stage-data analysis returned two stable new tools with 23 passing Python tests.
It inventories all 210 regional MMD headers and verifies 69 R11A blob views and
29 tables against actual media and pinned consumer encodings. Other stages
remain header-only. Native chunk IDs are one-based, pointer high bytes can
select PLCs, and Sonic's DPLC consumes one verified alignment byte beyond the
raw art blob. Gameplay, decompression output, carry suffixes and timing remain
unproven. Fresh task `sonic-cd-stage-inventory-review-20261023-r1` is reviewing
the tool and its metadata before they become production-loader inputs.

The alias fix requires one additional frozen-file edit: the analyzer duplicated
the canonical-only ownership-address check and would reject corrected mirror
events. The lead admitted the minimal `native_owner_analyze.py` validation change
with cases in the already-owned analyzer test. Original addresses and CPU access
widths stay in events; long selection unions both native word legs, including
decoder boundaries. The R4 inventory and producer pins will include that change.
Other frozen files and immutable R3 evidence remain unchanged.

Stage-inventory review found one P2 encoding error before loader use. Its
generic `.nem` filename inference mislabeled Game Over and Time Over art.
`LoadShieldArt` selects five raw pointers and copies 256 longs, or 1,024 bytes.
Some consumed views continue into the following source blob: Time Over by
64 bytes and Time Warp Stars by 32. The following DMA reads 1,152 bytes from
the animated-art RAM buffer, so its final 128 bytes are resident RAM, not extra
file bytes or invented zeroes. Task
`sonic-cd-stage-inventory-encoding-fix-20261023-r1` owns the two tool files,
source-consumer proof, regression cases and a separate R2 metadata output.
The original metadata remains immutable and superseded for encoding decisions.
No production stage loader consumes it yet. Other reviewed R11A offsets,
one-based chunk and tagged-pointer rules, and the verified Sonic alignment byte
remain valid within their stated bounds. No Java/native process is owned by
the lead; the three delegated units continue independently.

The mirror-fix worker reports 32 passing native-owner Python/C tests, zero
skips, a regenerated patch and a separate single-worker R4 build with passing
ABI inspection. Four fresh native controls are running. The lead inspected the
R3-to-R4 selector, analyzer and test changes while those controls execute.
Selection now follows actual mirrored decoder maps and unions both word legs
for native long writes while retaining the original access identity. These
pending native controls are not yet a completed validation result, and the
owner tools remain uncommitted.

### Verified owner ledger R4

The final alias fix and analyzer validation passed 32 native-owner Python/C
tests with zero skips. Its compiled controlled oracle uses the pinned native
CPU helpers, mapped handler bodies and decoder markers. This certifies alias
selection within that controlled source model, not real-route alias exercise.
The lead reviewed the selector/analyzer/test delta against the immutable R3
snapshot. No further material issue was found in that bounded review.

The separate R4 build passed ELF/ABI inspection and four fresh 1,800-call
disabled/enabled boots, each with a 120-call restored continuation. All four
complete states are 2,035,641 bytes with SHA256
`097368d1cb17c9dd313ff8ac460a2e48fa1d3d421666d0806fa2b0f12eec43c8`.
The lead checked their full hashes, lengths, saved restore flags and exact
official AV/public CPU/RAM/continuation streams. Enabled capture, per-call
counts and restored event bytes match R3 exactly, without forcing the counts:
103,646 capture events, 1,194 ownership commits and 4,936 continuation events.

Real identity/layout rejection controls passed. Overflow dropped 766 events,
was rejected as incomplete evidence, and preserved complete state equality
against its disabled control plus matching AV. Native core compressed SHA256 is
`82af02e7080426f11d5618221a7b05d62565504cbe4caa698cf687d76077b515`;
patch SHA256 is
`de60a4ea90b6b1f51b02d1914562b76646ab793c6b9fccca48ac7da9004af769`.
The final sixteen-file input fingerprint is
`7d9ef07172a299309db8b95a440af1ac03f97bed80fd19e67b2b5675fa26e5e4`.
R3 source/artifacts remain unchanged. The R4 inventory binds the actual sources,
compiled producer dependencies, toolchain, patch and native build.

This unit provides source-model owner observations only. Title captures use
canonical ports; mirrors have controlled decoder proof. CDC DMA completion,
enabled level-3 timer pending, general 1M paths, later gameplay routes, other
regions and complete firmware/drive behavior remain uncertified. The historical
official checkpoint discrepancy remains unattributed despite the two matching
lead follow-up boots. There is no physical timing, V5 or gameplay-input authority.
No installed core, shared TraceChaser or Java target was changed by this unit.
The parent owns staging/local delivery; no owner job is running.

The generated native patch retains exact upstream context, including CRLF and
whitespace-only context lines. Its single path joins the existing native-patch
whitespace attribute policy; the patch bytes and tested source fingerprint are
unchanged. Ordinary source whitespace checks still apply to all other new files.

The owner unit is committed locally as `c6607c15c`, with its patch exported to
Downloads. New task `sonic-cd-stamp-service-20261023-r1` owns only the new
`service.graphics` package and tests for bounded source-derived graphics
service arithmetic, readbacks and restore. It must keep contention/alignment
approximations explicit and preserve the existing transform and owner evidence
as frozen inputs. The stage-encoding fix and PCM-native fixture task continue
on separate tool prefixes. No Maven job is active; parent compilation waits
for stable Java handoff.

### Verified R11A source and consumer inventory

The encoding fix returned two stable tools and a separate schema-v2 R2 JSON.
The lead reviewed the source/consumer classification delta and checked its
current producer binding plus all five raw consumer records for each region.
All five ShieldArtIndex entries copy 1,024 actual media bytes, with exact
adjacent-blob attribution where needed. The complete pointer table and 80-byte
native copy/DMA routine are matched, including macro expansion. The following
1,152-byte RAM upload records its unknown 128-byte resident tail separately.
No padding, guessed encoding or extra file read is supplied.

Genuine Nemesis declarations now require checked PLC/block entry/header
consumers; unlisted uses remain unverified. Per region, the 69 source blobs
have 36 Nemesis, five raw and 28 unknown encoding summaries. Ten `.nem`-named
blobs lost filename-only certification. R2 has 30 tables including the new
ShieldArtIndex. Decoder execution/read cursors and final decompressed output
remain outside this inventory proof.

All 61 Python tests passed. All 45 actual-media mutations rejected, and two
three-region CLI runs produced identical R2 metadata. Original JSON and exact
R1 tool/test snapshots remain preserved as superseded evidence. Current R2
metadata SHA256 is
`f7eedea40b127a09b31441134ff26c361beb787c346d903834133db4e1460cbf`.
The original review's other bounds still hold: all 210 regional stage headers,
one-based chunk addressing, pointer tags, source-pinned R11A views and the
actual Sonic art alignment byte. Non-R11A layouts are still header-only.

New task `sonic-cd-r11a-assets-20261023-r1` owns only the new `level` package
and tests for actual media-backed R11A decoding, using the corrected R2 evidence.
It has no GameModule, GameId, shared engine or creator API changes. The existing
graphics-service task owns a separate Java package; the lead will compile only
after stable handoff from Java writers. Native PCM fixtures returned eight
startup/channel-stop blocks and are under fresh read-only review
`sonic-cd-pcm-native-review-20261023-r1`. Gameplay-active PCM and active-PCM
checkpoint restoration remain missing. The lead owns their test-only Java
consumer. No native fixture or comparison row becomes runtime game input.

### Native PCM startup fixture handoff

Stage inventory is committed locally as `5c0f2eae2`. The native PCM unit
returned eleven stable tool/test files and eight source-native startup/stop
blocks, with complete RAM, channel fields, selectors and integer samples/deltas.
They contain 3,160 samples, 3,107 nonzero samples and three loop events. Seven
active blocks use channel 3; the final block is channel stop with the chip
still enabled, not chip disable. No clipping limit was reached.

All nineteen Python tests passed with zero skips. Nine fresh native controls
cover two official, two unaltered-source, two disabled, two enabled and one
overflow run on the same 3,000-call authored-input route. Each has 120 forward
calls plus restoration/replay. AV, pictures and public CPU/RAM views agree;
unmasked raw states agree within each binary. Both enabled payload sets match.
Fresh review `sonic-cd-pcm-native-review-20261023-r1` returned bounded PASS on
the eleven files and checked additional corruptions, typed ABI, source/build
identity, `.invis` storage and before-volume integer hook phases. Native cycles
and unity host volume are separately validated metadata, not hidden chip fields.
The manifest SHA256 is
`0236df320dcb3322a21263b2b771f108269a779bb6712445e75ba36a309141b4`.

The post-3,000 restore interval has no active PCM; active-PCM native restoration
is not established. Nor are active multichannel mixing, clipping, resampling,
physical timing, sound-driver or in-level audio parity. Normal PP jumps did
not activate PCM in this bounded route. New task
`sonic-cd-pcm-soundtest-route-20261023-r1` owns a separate route tool prefix and
fresh captures, using native source-derived menu/controller inputs to seek
broader natural music coverage. The completed core/tools and captures are frozen.

The lead authored a test-only binary admission helper and opt-in Java
comparisons for all eight original blocks. These tests are pending compilation;
their source is separate from the verified tool unit. The `level` asset-loader
and `service.graphics` writers are still active, so no Maven command is started
against partially authored Java. The pending comparison checks every represented
chip scalar, all wave RAM, every sample/delta and Java capture/restore, without
claiming a native active-PCM restoration interval. Commercial payloads remain
outside Git and are never a runtime data source.

The reviewed PCM tools are committed locally as `144cc091d`; their core patch
joins the existing exact-context whitespace attribute policy. The parent owns
three uncommitted test-only additions in `device.pcm`: `PcmNativeBlock`,
`TestPcmNativeBlock` and `TestCdPcmNativeContinuations`. The opt-in property is
`soniccd.pcm.fixtures`, pointing to the original controls-startup-jumps-r1 root.
Admission checks the complete binary extent, channel fields, service-counter
metadata, unity host volume, all RAM, every sample/delta and unused storage.
The comparisons use the pinned manifest and both repeated payload sets, then
exercise Java state restoration separately. Compilation/JUnit remain pending.
No Maven job is queued or running; future combined focused validation waits
for stable `level` and `service.graphics` handoffs. No pass is claimed for
these new Java test files.

The graphics-service unit returned six stable Java/test files, calling the
production transform and preserving explicit clock domains, busy-clear/wake/IRQ
ordering and RAM/cursor snapshots. Offline scalar calculations matched all
1,052 R4 operations and 1,800 rebases with zero mismatches. These calculations
are not Java/JUnit execution or hardware timing proof. Fresh task
`sonic-cd-stamp-service-pcm-tests-review-20261023-r1` is read-only on these six
files plus the parent's three PCM comparison/admission tests. The R11A loader
writer was asked to announce a stable Java freeze before remaining external
checks if possible, so one combined focused compile/test run can overlap them.
No Maven command has started; the parent still owns target and validation.

### Native music-preview and pending Java checkpoint

The controller-only Sound Test route succeeded on its first discovery schedule.
Pinned source derives Down, Down, Down, Left, Right, A; native UI shows PCM NO.10.
Its source command loads the Palmtree Panic PCM bank and queues Past music.
All nine fresh controls match retail Sound Test entry/current instruction bytes
and PC `FF3C30`. The read-only state search requires the entire public 68K RAM
hash, not an assumed savestate offset or authoritative gameplay value.

Eight main music blocks contain 1,623 nonzero samples, seven channel masks,
varied envelope/pan values and six natural loops. The checkpoint is active;
all 120 following native calls contain active PCM. Exactly 21,568 integer
summaries match after restoration. Four complete forward/replay blocks contain
526 nonzero samples and match across both enabled cold boots. All nine fresh
AV/picture/public CPU/RAM controls and complete same-binary raw states match.
The existing nineteen PCM producer tests passed without skips on the new capture.
No clipping sample occurred. This is retail music-preview evidence, not an
in-level audio, physical timing, full sound-driver or resampling certificate.

Fresh review `sonic-cd-pcm-music-evidence-review-20261023-r1` returned bounded
PASS on the read-only checker and actual new evidence. The lead checked its
unchanged source identity and all repeated main/forward/replay payload bindings.
Checker SHA256 is
`08d24f1b241ede033dbba59c9e96f5aa6744c5d6170acb478c5eccbf5d5f3825`;
manifest SHA256 is
`a0b1355233f41659545f778f044ae39dfa0de55a5ff7a5a8293070036e27661b`.

The prior nine-file Java review also returned bounded PASS for the six graphics
service files and three startup PCM comparison/admission files. It independently
reproduced the 1,052 graphics-operation/1,800-rebase scalar boundaries and
validated all sixteen startup payloads plus mutation controls. This is static
review/calculation evidence; Java execution remains pending. The parent then
added `TestCdPcmNativeMusic`, using `soniccd.pcm.musicFixtures`, to compare all
eight music blocks and the four active native restore blocks through the same
test-only binary admission helper. The original nine reviewed files are unchanged.
Combined focused validation still awaits a stable R11A loader source handoff.

### Verified R11A loader, graphics service and PCM comparisons

At `92825f5b1` plus the frozen new units, the lead ran this combined focused
command. It compiled 4,232 production and 4,341 test sources, then passed all
87 tests across thirteen classes, with zero failures, errors or skips. Maven
took 1 minute 28 seconds; queue admission reported zero wait. Session `20024`
is finished and there is no live Maven job. This is bounded focused validation,
not a broad smoke/full-suite pass or stock game integration. The new packages
have no existing runtime callers; the shared Nemesis implementation is unchanged.

```bash
python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestCdBoundedAssets,TestCdObjectPlacements,TestCdR11aLoadPlan,TestCdR11aRetailAssets,TestCdR11aTerrain,TestCdShieldArt,TestCdSonicVisualAssets,TestCdStampOperationRequest,TestCdStampGraphicsService,TestCdStampOwnerLedgerSourceModel,TestPcmNativeBlock,TestCdPcmNativeContinuations,TestCdPcmNativeMusic' -Dsoniccd.media.root=${SCD_MEDIA_ROOT} -Dsoniccd.stamp.ownerLedger=${SCD_OWNER_LEDGER} -Dsoniccd.pcm.fixtures=${SCD_PCM_STARTUP} -Dsoniccd.pcm.musicFixtures=${SCD_PCM_MUSIC} test -B
```

The R11A unit's 26 invocations include actual supplied USA/Japan/Europe media.
Authentication precedes decode; known whole-disc content, variant, stage/file
join, original Word RAM domain and exact immutable MMD bytes are checked.
Decoded blocks/tiles, layouts, collision, palettes, placements, load metadata,
Sonic visual views and bounded raw shield copies are detached immutable data.
Actual Java decoder cursors and complete output hashes match the independent
reference. Unknown resident tails and other 207 regional stage paths remain
unsupported. Eight Python reference tests and all three regional checks passed
separately. Fresh review `sonic-cd-r11a-assets-review-20261023-r1` returned
bounded PASS on twenty files. The lead verified its unchanged aggregate SHA256
`0c1de79bc575b3a2467f0dd06024171a02848da6873eba1304d976c3f67418ca`.

The graphics service's 39 invocations include the explicit source-model ledger
check over 1,052 operations and 1,800 rebases. Request/clock/ownership validation,
ceil/ahead service, finish ordering, stale handles, RAM snapshots and replay
passed. The model retains native contention/alignment approximations; this is
not physical timing, full register mutation semantics or firmware certification.

The PCM consumer's 22 invocations cover two binary admission checks, eight
startup/stop blocks, eight music blocks and four active native restore blocks.
All 5,309 distinct captured integer samples/deltas, complete represented chip
state and wave RAM match Java exactly. Java checkpoint replay also passed.
This establishes the bounded startup and retail music-preview device comparisons,
not in-level audio, clipping, resampling or full-driver scheduling parity.

Task `sonic-cd-stock-admission-audit-20261023-r1` is read-only on actual launch,
module, media and API obligations for the next real disc consumer. It writes
only external research. The next integration still needs a deliberate engine
adapter and stock bootstrap; no GameId.SCD or playable game module exists yet.
All implementation remains local with no merge, push or main-workspace mutation.


### Static stock admission in progress

The verified units were saved locally as `08bc9aa91` (R11A assets),
`78f4fc226` (bounded graphics service), and `56beba899` (exact PCM comparisons).
Their three source patches and refreshed index/checkpoint are in the external
Downloads bundle. The worktree branch still has no upstream. No main checkout,
merge, push or PR action was taken.

Read-only task `sonic-cd-stock-admission-audit-20261023-r1` completed its external
engineering map. Current implementation ownership at base `56beba899` is:

- `sonic-cd-engine-level-adapter-20261023-r1`: new native-to-engine level geometry
  and tests, preserving original CD collision words separately from rendering.
- `sonic-cd-source-factory-migration-20261023-r1`: frozen 61-file canonical source
  factory migration, maintained consumers and tests. Its source-only handoff hash
  is `4d31740f6ab637e4a41d62dcb5855ca9be8f65c2de642aaf04d4720f647efb56`.
  Independent task `sonic-cd-source-factory-review-20261023-r1` is reviewing it.
- `sonic-cd-static-module-20261023-r1`: new normal CD module, static profile,
  controlled terrain presentation and tests. No native actors or title execution.
- Lead: detached `CdGameDataSource`/`CdStockLaunch`, public stock identity with
  cartridge capability separated, rejection at unsupported ROM/audio/editor/trace
  consumers, existing headless/capture boot seam, tests and eventual API pin.

These pending changes have not been compiled or executed. The earlier 87-test
result applies to their unchanged dependencies, not this admission unit. There
is no live or queued Maven job. Candidate signature pins remain deliberately
stale until all source handoffs are stable and compilation succeeds.

The change-based category plan at this checkpoint selects all smoke categories,
3,654 candidate classes and guards because public/shared factories changed.
This is routine smoke selection, not the public-release full suite. Combined
broad execution waits until focused fixes and API generation are complete.
Display-free preflight initially failed because the default Lua was not 5.4;
explicit `LUA_BIN=/usr/bin/lua5.4` fixed it. The repaired invocation passed Java,
Lua and PowerShell prerequisites with no tests executed:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 56beba899 --preflight
```

`TestSonicCdDirectStageBoot` is authored for actual supplied media and explicit
surfaceless EGL. It uses `GameplayCaptureSession`'s ordinary rendering passes,
checks visible terrain and same-world camera/level restore, and can save images
only when an external capture root is supplied. It has not run. Existing rewind
snapshots do not bind cross-registry source identity; this remains an open
acceptance issue, separate from module source-rebinding rejection. No CD-07 or
playable-game/parity completion is claimed.


The source-factory review returned bounded PASS with no P1/P2 finding. The lead
recomputed the frozen 61-file handoff fingerprint and it still matches exactly.
Expected migration-only API delta is eight obsolete method entries removed and
two canonical interface factories changed from default to abstract; the later
SCD identity/capability addition is a separate intended pin delta.

The seven-file detached preparation/engine geometry review also returned bounded
PASS. Its ordered path/NUL/bytes/NUL SHA256 is
`1d46b97b4aa5ae28a0e128e515e1b4881c52544ca2808a830032201718b9ab61`, independently
recomputed by the lead unchanged. Both reviews are source inspection only.
Unloaded geometry stays unavailable, whole-block serialization of those cells
is unsupported, and native collision/actor execution remains outside this slice.
The adapter task is complete; the static module task is the only active child.
No task-owned Maven job has started for these pending changes.


### Combined admission validation queued, 24 October 2026

The static module source handoff is frozen: ten files, aggregate sorted
path/NUL/bytes/NUL SHA256
`232d03415237dbac9591307c46e55a758e485f0cc121eca4ca546cdf4e04cf02`, verified
unchanged by the lead. The existing transform hook publishes decoded graphics;
no additional shared graphics hook was needed. Independent task
`sonic-cd-static-admission-review-20261023-r1` is reviewing the module and lead's
shared stock/boot changes without running Java or changing source.

One focused Maven job is alive in command session `12065`. It is waiting for
shared admission, not failing or executing tests yet. Keep this request alive;
do not submit a duplicate. The command is:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestSonicCdStockPreparation,TestSonicCdLevelGeometry,TestSonicCdLevelRetail,TestSonicCdModuleContracts,TestSonicCdStaticStageLoad,TestStockGameCapabilityMembership,TestTraceToolRomLocations,TestRomManagerGameResolution,TestRomManagerLocationResolution,TestGameDataSourceSharedFetches,TestGameDataSourceSessionOwnership,TestDelegatingGameModuleCoversInterface,TestGameModuleProfiles,TestLazyMappingHolder,TestLoadedLevelSceneSourceCapability,TestKis2GamePatchResolution,TestHeadlessAudioOwnership,TestNoProvisionalModApiShims' -Dsoniccd.media.root=${SCD_MEDIA_ROOT} test -B
```

All Java is frozen for this run. Compilation/runtime results and candidate pin
remain pending. Next: inspect this job's exact outcome, fix concrete failures,
then generate/inspect the compiled candidate pin and execute the explicit EGL
render check. Required broad smoke remains after focused fixes. The lead also
read-only verified existing root `s1.gen`, `s2.gen`, `s3k.gen` against the
repository's CRC32/SHA-1 pairs for later affected cartridge/S3K validation; no
ROM was copied, renamed or linked.


### Verified normal static entry and API migration, 24 October 2026

The 25-file module/shared-admission review returned bounded PASS with no new
P1/P2 findings, aggregate sorted path/NUL/bytes/NUL SHA256
`cf3f1f1c042d39616be963a57881fbbcbb331a3405f319c914779be348c49782`.
Its proof was source inspection, separate from the execution below.

All 4,244 production sources compiled. The first two focused invocations then
stopped in test compilation: `RewindRoundTripHarness.registryFor` and
`TestScalarOnlyCodecDeletion.invokeGenericRecreateWithState` had exhaustive
cartridge object-registry switches. Each now explicitly rejects SCD; neither
harness supplies native CD object execution. These were new enum-admission
compile failures, not baseline test failures. No JUnit ran in those attempts.
Session `12065` waited 223 seconds and ran Maven 77 seconds; repaired session
`79802` waited zero and stopped after 22.600 seconds.

The repaired focused selection in session `87412` compiled all 4,349 test sources
and passed 98 tests with zero failures, errors or skips. Maven took 34.613 seconds,
queue wait zero; completion was 00:05:12 BST. The eighteen-class selector is the
same command recorded above. Expected rejection of unsupported native title
entry is exercised in all three load tests; its logged exceptions are asserted
negative cases, not unreported load failures. Actual supplied regional media ran.

The lead generated the mutable candidate pin from the just-compiled classpath,
using `ModApiSignatureSurface --snapshot` under the shared build-activity lease.
Its exact delta was ten removed entries (eight obsolete methods and two previous
default factory signatures) and four additions (two abstract canonical factories,
SCD identity and optional cartridge mapping). The descriptor and `CURRENT=0.7.0`
remain unchanged; standalone/compatibility guidance and the runtime description
now describe source factories. Historical design assumptions remain labelled.

Session `41463` then passed 34 tests with zero failures, errors or skips: 31 API
signature/pin/release/runtime policy checks and three regional real GL checks.
It waited 35 seconds; Maven took 34.685 seconds, completion 00:07:21 BST:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestSonicCdDirectStageBoot,TestModApiSignatureSurface,TestModApiPinPolicy,TestModApiReleasePolicy,TestModApiRuntimePolicy' -Dsoniccd.media.root=${SCD_MEDIA_ROOT} -Dsoniccd.static.capture.root=${SCD_STATIC_CAPTURES} -Dopenggf.headless.gl=surfaceless-egl test -B
```

The lead inspected all three actual 320x224 PNGs. Each shows Palmtree Panic
terrain/palette through the ordinary world, loop, load and capture renderer;
all camera-change/restore images and subsequent held presentations match their
assertions. Captures are external under `native/engine-static-r1` in the Downloads
bundle. No native Sonic/object execution, parallax/pixel parity, audio service,
firmware, complete entry route or USA MVP result is claimed. The
[partial act matrix](../validation/levels/scd-palmtree-panic-act1-present.md)
records every outstanding coverage dimension and cross-registry snapshot gap.

All newly introduced documentation link targets resolve. Two targets already
absent at base `56beba899` remain absent in the workflow index:
`examples/starpost-valley/generate_resources.py` and optional
`tools/tracechaser/bizhawk-headless/README.md#diagnostic-rendered-frames`.
They were attributed against base, not repaired as unrelated work.

The next required check is the change-based 3,654-class smoke selection plus
guards at base `56beba899`, with verified original root cartridge images and
surfaceless EGL. Expected cost is roughly an hour of execution, with queue delay
unknown. It will use a 90-minute execution budget excluding waits, the runner's
10-minute no-output stop, and fail visibly on launcher/compilation/source changes.
Ordinary batches retain their full selection and release admission between at
most fifty classes; this is not the public-release full suite. No smoke result
has been claimed yet. Do not mutate the tested tree while that runner is alive.

Task `sonic-cd-r11a-player-bringup-20261024-r1` is read-only on concrete native
player/collision prerequisites and writes external research only, independently
of parent validation. No further design council or second game runner is planned.

### Broad run limits and focused corrections, 24 October 2026

Run `20261023T231338Z-666ba6f3` tested clean commit `5998f6290`, tree
fingerprint `29413264d95825f801f675c0cd780be3d639e64852da1b0869423a3bbb8908c4`,
against base `56beba899`. The unchanged 3,654-class smoke selection hit its
90-minute execution budget during ordinary batch 67/74; queue waits were
excluded. Preparation passed in 26.45s and recorded ordinary execution was
5,373.74s. The retained result is **incomplete**, with 36,152 tests, five
failures, zero errors and 110 skips. Guards were not reached. No full-suite or
completed-smoke pass is claimed. The tail was executing
`TestMhzKnucklesColdRouteCapture` when the invocation budget stopped the process.

Four failures are the new optional-media tests in
`TestSonicCdStockPreparation`: three `requestedVariantRejectsAnotherRegion`
invocations and `forcedContentRecordRejectsAnotherDiscEvenWhenStageProgramIsIdentical`.
Their media assumption ran inside `assertThrows(IOException.class, ...)`,
which incorrectly caught an absent-fixture abort as a failed assertion. The
fixture lookup now occurs before the exception assertion. Focused validation
must exercise this class both without the property (explicit media skips)
and with actual supplied media (all cases execute).

The fifth failure is
`TestHczMinibossNativeRendering#allOrbitPhasesMatchRomSpriteCompositionInNativePixels`,
the line-53 `GLFW must initialize for pixel verification` assertion in the
display-free environment. The exact method on detached baseline `56beba899`
also fails at that assertion, one test with zero skips/errors. Current session
`67038` reproduced the exact test identity, exception, message and source line;
Java `25.0.5.0.2`, the absolute S3K ROM property and surfaceless-EGL property
matched baseline. It waited zero; Maven took 27.293s. This is an existing
display-free GLFW limitation. No graphics helper or desktop environment was
changed. The clean detached baseline was removed after inspecting its generated
resource links; the original ROM/config/reference targets were untouched.

All 110 skip reasons were inspected: optional diagnostics/benchmarks/captures,
GLFW/OpenAL availability, opt-in CD media/native fixtures and source-dependent
route omissions. None reports a missing required cartridge ROM. Regional CD
and native-device checks have their separately recorded focused runs; their
omission from this broad command is explicit. Consumed runner diagnostics are
acknowledged after inspection. Remaining ordinary/guard coverage and affected
checks for subsequent source changes remain open, not a cached passing result.

The corrected optional-media class passed in session `89754` without the
property: nine tests, zero failures/errors, seven explicit media skips (the
two ROM-free cases passed), 12s waiting and 29.309s Maven execution. Session
`81396` then passed all nine with supplied regional media and zero skips,
165s waiting and 34.310s Maven execution:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestSonicCdStockPreparation' test -B
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestSonicCdStockPreparation' -Dsoniccd.media.root=${SCD_MEDIA_ROOT} test -B
```

All individual skip identities were inspected. These checks resolve the four
new fixture-test failures; they do not fill the timed-out ordinary/guard coverage.

## R11A player components and world-owned snapshots — 24 October 2026

The private tree imports source-reviewed finite components from the external
`world-snapshot-guard-r1`, `collision-placement-r2`, `player-terrain-r1`,
`player-assets-r1`, `player-art-runtime-r1` and `player-animation-r1` candidates.
Runtime data continues through authenticated supplied discs. Native placement
keeps original material/solidity words independent of drawing and delays shape
ID reads until the native gates. Floor/wall probes retain signed distances and
optional angle writes. Bounded animation and V-int art transfer own their state
without executing a player or issuing draw-time cues.

Command examples below use `OPENGGF_MAIN` for the original checkout and
`SCD_MEDIA_ROOT` for the supplied regional media directory. Exact machine-specific
invocations remain in the external task plan.

On parent `dbe9b40be` plus these 30 imported files, the first combined focused
check passed 130 tests, zero failures/errors/skips, with Java 25 and all three
supplied discs. Command from the private cwd, with DISPLAY/WAYLAND_DISPLAY unset
and LUA_BIN=/usr/bin/lua5.4:

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdTerrainCollision,TestCdPlayerTerrain,TestCdR11aPlayerAssets,TestCdR11aRetailAssets,TestCdSonicVisualAssets,TestCdShieldArt,TestCdR11aLoadPlan,TestCdR11aTerrain,TestCdBoundedAssets,TestCdObjectPlacements,TestCdPlayerArtRuntime,TestCdPlayerAnimation,TestSonicCdStockPreparation,TestSonicCdLevelGeometry,TestSonicCdLevelRetail,TestSonicCdModuleContracts,TestSonicCdStaticStageLoad' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" test -B
```

Queue wait was 21 seconds; Maven execution was 1 minute 35 seconds. All 17
completed XML reports and their media/Java properties were inspected. This
includes real-media foreign-world snapshot rejection; shared rewind/API/native
publication and real-GL regressions remain pending at this checkpoint. Scope is
an opaque WorldSession identity carried by engine-created layouts. Full/course
restore checks it before adapters, callbacks, RNG or character selection.
Unscoped public private registries retain Map roundtrips; reconstructing a Map
cannot confer engine-world authority. No public signature change is intended.

Corrected native reference tools are promoted under `tools/soniccd`: R2 output
admission discovers the bound producer repository's registered worktrees; R2
charge comparison checks release-row latches and displacement rather than stored
positive velocity. The recipes are `player_reference_retail_route.json`,
`player_charge_peelout.json` and `player_charge_spindash.json`, supplied explicitly
to the producer's scenario argument. Original external sources/manifests remain
unchanged. Promoted-path Python checks passed all 33 player-reference and 20 charge-checker
cases, with zero skips; no new native boot was needed for this path integration. Earlier movement captures retain ten unexplained
private-state replay bytes; the later charge captures match complete states.
Exact instruction phase and immediate threshold-release coverage remain unknown.

Cold entry has a source owner: RunMMD (CD Initial Program/IPX.asm:1307-1341)
clears [FF2000,FFFB00), including the complete Sonic CPU art window and comparison
bytes. Both R11A GameInit branches call InitVDP, whose full VRAM DMA clears the
resident window. The unique clear/call/setup bytes were checked against all
three supplied program images. These are cold transition facts; the finite art
owner still requires explicit complete ingress. Intervening PLC/DMA writers,
restart/carry boundaries and actual engine initialization remain separate proof
and implementation obligations. No initial cue, zero tail or timing is fitted
from a native capture. Collision/animation/art component passes do not enable
Sonic execution, actor touch, camera scrolling, time travel or complete act parity.

### Shared, charge and rendered verification at the player-component checkpoint

The unchanged shared/world component state passed 296 tests across 31 actual
classes (30 selectors), zero failures/errors/skips. API surface/pin policy passed
without changing any signature pin. Cartridge SHA-1/CRC32 identities and all
selected XML properties were checked. Command from the private tree, with
DISPLAY/WAYLAND_DISPLAY unset and LUA_BIN=/usr/bin/lua5.4:

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestRewindRegistry,TestCompositeSnapshot,TestCompositeSnapshotOwnership,TestRewindBenchmarkSizeEstimator,TestGameplayModeContextRewindRegistry,TestGameDataSourceSessionOwnership,TestEditorModeContextLifecycle,TestOwnerBoundGamePatch,TestS1S2PlcSessionOwnership,TestModApiSignatureSurface,TestModApiPinPolicy,TestModApiReleasePolicy,TestModApiRuntimePolicy,TestRewindController,TestRewindProfilerAttribution,TestSegmentCache,TestInMemoryKeyframeStore,TestPlaybackController,TestRewindDeterminismAuditor,TestGameplayInputBranchTool,TestModZoneEventLifecycle,TestSonic1SwitchStateRewindAdapter,TestFbzBadnikGraphRewind,TestS3kIczIceObjectRewind,TestS3kCnzLocalMechanicsRewind,TestNativeRewindAdapterPublication,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' "-Dsonic1.rom.path=$OPENGGF_MAIN/s1.gen" "-Dsonic2.rom.path=$OPENGGF_MAIN/s2.gen" "-Ds3k.rom.path=$OPENGGF_MAIN/s3k.gen" test -B
```

Session 77545 waited 244 seconds; Maven execution was 37.138 seconds. The
separate real-GL/audio/charge invocation (29219) executed 86 cases, with two
charge assertion failures and zero errors/skips. All three regional real-EGL
terrain/restore cases and both headless audio ownership cases passed. Command:

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdPlayerCharge,TestSonicCdDirectStageBoot,TestHeadlessAudioOwnership' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" "-Dsonic1.rom.path=$OPENGGF_MAIN/s1.gen" "-Dsonic2.rom.path=$OPENGGF_MAIN/s2.gen" "-Ds3k.rom.path=$OPENGGF_MAIN/s3k.gen" -Dopenggf.headless.gl=surfaceless-egl test -B
```

That invocation waited zero seconds and executed Maven for 1m25s. Source
attribution found two wrong authored expectations: starting at +/−4500 adds
native +/−100 to +/−4600, below the shoes cap +/−4608. Main.asm:984-1014 and
production agree; no production edit was needed. Corrected the expected
literals and added exact-cap +/−4508 and just-crossing +/−4509 inputs. The
entire charge class then passed 85 tests, zero failures/errors/skips (51184,
zero queue wait, Maven 26.716s):

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdPlayerCharge' test -B
```

These are separate bounded selections. The failed 86-case invocation is not
relabeled green or added to the successful charge total. Its five unchanged
render/audio positives remain evidence for those exact cases. The 130-component
and 296-shared checks retain their original tested inputs; later changes add
only the isolated charge owner, its corrected tests, standalone reference tools
and prose. No repeated unchanged broad coverage was launched.

The change-based planner against integration base `56beba899` selects all
3,660 smoke candidates in 74 ordinary batches plus guards for this working
state. That broad combined coverage remains pending during continuing player
implementation. Its prior 90-minute invocation was incomplete, and these
focused checks do not fill its unexecuted tail. A fresh complete smoke run needs
an execution budget informed by that measured cost; do not launch the public
release-only full suite. This is a private implementation checkpoint, not a
completed delivery or complete game/act/parity claim.

A follow-up cold-art source audit checked the intervening setup/PLC writers,
load/fade/lag interrupt selectors and conditional first V-int sequence. At the
completed cold/default-start cut before Level_MainLoop, both complete $300-byte
windows remain zero, last-frame is zero and dirty is clean; PLC work is empty.
The first two normal services see frame zero and issue no player DMA. An actual
later animation publication selects the first changed cue. Full details and
writer extents remain in the external `research/player-art-cold-ingress.md`.
That resolves the bounded source ingress question, not engine initialization:
no cold-art factory or lifecycle hook has been added. In-program restart,
checkpoint, carry and restore must retain their own state and never reuse that
cold clear. Native timing and live player/render integration remain open.

## Native player math and paired ground collision — 24 October 2026

At parent `d075b8633` plus four new files, the source-reviewed finite math and
ground-collision owners compiled and passed the complete selected checks:
110 tests across six classes, zero failures/errors/skips. Session3193 waited
157 seconds; Maven execution was1m37s. Its completed command returned exit0;
a later attempt to poll the already-closed session found no process. All six
current XML reports, counts and Java25 properties were inspected. This run
contains68 new math/ground cases and42 affected dependency regressions; do not
add repeated dependency counts to earlier coverage as new unique tests.

From the private cwd, with DISPLAY/WAYLAND_DISPLAY unset and
LUA_BIN=/usr/bin/lua5.4 (`OPENGGF_MAIN` denotes the original checkout):

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdPlayerMath,TestCdPlayerGroundCollision,TestCdPlayerTerrain,TestCdTerrainCollision,TestCdR11aTerrain,TestCdR11aPlayerAssets' test -B
```

`CdPlayerMath` consumes the actual supplied player's sine/arctangent views.
It retains low-byte/word input widths, unsigned magnitudes after NEG.W,
quotient256 and the returned angle word, including0100; byte projection is
explicit. Shared trig tables and byte-return behavior were rejected as different
asset/return contracts. No shared math implementation changed.

`CdPlayerGroundCollision` models one Player_GroundCol call over the actual native
probes: object bypass, signed quadrant rounding, four paired sensor orders,
secondary-on-tie selection, optional angle writes, signed radius bytes,
14-pixel alignment/stick/detach branches, and integer-word position writes that
retain all fraction bits. Unknown backing throws without a partially published
actor. The interleaved unused movement routines were excluded. Both owners are
immutable/pure and neither advances a player frame, clock or resource service.

Source labels are CalcSine/CalcAngle (Level/Functions (Misc).asm:69-219) and
Player_GroundCol/Player_PickSensor/walking routines (Level/Collision Floor.asm:
30-146,227-454), at `1ecc5f3a`. External handoffs and independent bounded reviews
retain exact source hashes in `research/player-math*.md` and
`research/player-ground-collision*.md` in the private task bundle. The tests use
constructed admitted asset/terrain bytes, with no runtime disassembly, media,
emulator or reference-state input. No exhaustive4billion-input or native timing
claim is made. Normal locomotion, actual actor/render binding and complete-game
parity remain open; these components leave the stock entry held and static.

## Disc-backed Sonic visual conversion and render acceptance — 24 October 2026

The immutable CD mapping/art views now convert to existing renderer data.
Five-byte piece order, signed X/Y bytes, dimensions and tile attributes remain
native. All 2171 source patterns include the actual alignment byte; all 24
resident tiles come from explicit resident state. Mutable engine patterns are
fresh per call/world. The data bundle uses a caller-provided virtual atlas base,
not hardware VRAM F000, and deliberately supplies no Draw-time DPLC metadata.
The existing CD art owner remains the sole cue/dirty/fixed-DMA producer.

At parent `15bea2cf8` plus four new files, session 15197 compiled and passed
37 tests, zero failures/errors/skips, with Java 25, actual media for all three
releases and display-free EGL. The completed XML reports and media/GL properties
were inspected. Queue wait was 11 seconds; Maven execution was 1m27s. From the
private cwd, with DISPLAY/WAYLAND_DISPLAY unset and LUA_BIN=/usr/bin/lua5.4:

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdPlayerVisualAdapter,TestCdPlayerArtRuntime,TestCdSonicVisualAssets,TestCdPlayerRetailComponents,TestCdPlayerVisualRendering' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.playerVisual.capture=true test -B
```

The 15 adapter tests cover format and mutable-storage ownership; 16 existing
art/asset regressions cover its dependencies. Three retail cases join the
actual data to contact, math and art consumers: start (64,1899) resolves to
(64,1898), angle FC and buffers FC/0; real trig preserves its signed asymmetry
and angle word 0100. These are explicit component calls, not a native frame.
Three GL cases boot the normal static world, then draw selected poses through
PlayerSpriteRenderer. Claim/restore all 24 tiles before drawing. A nonempty
frame on zero residency stays transparent; only explicit service makes pixels
appear. Changes stay inside the CPU opaque mapping footprint. Repeated draws
preserve art state, and restoring both logical state and the GPU bank restores
the exact image without issuing a cue. Renderer cache -1 stays distinct from
native last-frame 1.

Six 320x224 PNGs were exported byte-for-byte to the private external task bundle,
`native/engine-player-visuals-r1`, then their consumed target folder was removed.
Poses 1/2 have identical pixels/PNG bytes across the three supplied releases.
They remain queued for morning visual QA; no image was emitted overnight.

This is fixed-pose visual-component evidence over a static stage. No actor is
registered, camera focus stays null, and the stock profile still holds. There
is no claim of live player movement, source-native SAT/occlusion/culling,
physical DMA timing, complete opening-route or whole-game parity. The ongoing
mode/body/frame work must supply those consumers through the existing loop.

## Native player MODE and body calls — 24 October 2026

The immutable MODE owner now composes actual CD charge, trig, terrain probes
and paired ground collision for the four normal ground/air/roll/jump dispatches.
It preserves complete centre fractions, separate prior actor angle copies and
source global buffers. Jump return and unfinished KillPlayer continuations are
typed boundaries; the owner performs neither predispatch charge advancement nor
Main-tail animation/history/angle copying. Unsupported native predicates reject
rather than publishing an incomplete result. A caller-visible local register
effect is explicit: ObjGetFloorDist clobbers d5 to 13 on the path that reaches
it; later ground settling uses 13 without changing global acceleration 12.

Astra's source review found one production error and two authored expectation
errors before import. Downward landing read the low velocity byte instead of
the first byte of the native big-endian word; the correction changes only that
extraction. A side-air expectation had dropped C000 fractions, and a roll death
boundary expectation omitted the preceding five-pixel unroll. Their production
paths already behaved as the source requires. Frozen R1 and R2 external sources
and reports remain separate, preserving the rejected interpretation.

At parent `1fe96ac59` plus the imported MODE and body sources, parent62385 ran
corrected distinguishing tests against R1 production: three tests, exactly two
expected assertion failures, zero errors/skips, Java25. The 0138 landing row
passed; 01F8 wrongly remained airborne, and the two-distance -10 case wrongly
landed. After replacing only movement production with R2, parent6081 compiled
and passed all78 MODE cases plus58 Init/Main body cases:136 tests, zero
failures/errors/skips. Queue wait0s for both; Maven execution1m22s and1m26s.
Both completed XML identities and Java25 properties were inspected. Commands
from the private worktree, with display variables unset and Lua5.4 selected:

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=com.openggf.game.soniccd.player.TestCdPlayerMovement#postGravity0138And01F8ShareHighMemoryByteLandingThreshold+bothFloorSensorsBelowNegativeNineRejectLanding' test -B
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=com.openggf.game.soniccd.player.TestCdPlayerMovement,com.openggf.game.soniccd.level.TestCdPlayerBody' test -B
```

The body composes predispatch, routine0 Init-only or admitted routine2 Main,
source camera extension, control-lock retention, display eligibility, complete
history bytes, angle copies, animation and the explicitly empty development
touch scan. Its58 passing cases are constructed-data tests, not a retail route;
independent Body source review and normal actor/frame integration remain pending
at this checkpoint. All effects are staged until complete return; sounds stay
typed observations with no driver playback. No player is yet registered in the
normal stock world. Broad smoke remains incomplete, and these finite results
establish neither a playable opening route nor USA/full-regional parity.

## Complete player-slot and serviced-HOLD boundaries — 24 October 2026

Internal NativePlayableSlot dispatch runs once before all generic player setup,
input writes, physics and tails. Scripted, warm-up and movement-only helper
entries reject native participants; nonparticipants keep their existing path.
ControlledVBlankOnlyOwner allows NORMAL_PAUSE, LAG and SPECIAL_STAGE_PAUSE held
rows through the existing PLC/game-VBlank/hardware timing boundary, without a
player, timer or level tick. Non-opt-in and null-held owners retain unserviced
HOLD. Runtime-owned audio starts only after the opt-in decision/phase validates.

Source review exposed two inherited PLC token leaks made reachable by held
service: failed fade preclaim before the wrapper's try/finally, and failed
preparation/final publication before the token becomes finished. Acquisition
and owned wrappers now retire only their failed token, drop success callbacks,
consume its one-shot declarations and preserve primary/suppressed failures.
They never retry service/preparation/publication, close a retained fade, reset
queues/clocks, or detach a later replay token. Raw manually held tokens retain
their existing missing-preparation -> prepare -> finish correction contract.
This is coordinator cleanup, not rollback of partially serviced native work;
device owners still need complete rollback or closed admission/rebuild.

Parent63507 executed three regressions against the original coordinator:
2 assertion failures and1 stranded-token error, zero skips, Java25. Parent60554
initially repeated the combined selection with stale compiled coordinator bytes:
177 tests,9 failures/11 errors,0 skips; source/class identity exposed preserved
import mtime as the cause. That run does not validate the candidate. Updating
only the changed source timestamp triggered normal recompilation. Parent39232
then passed177 tests across16 actual classes, zero failures/errors/skips,
Java25 with actual S1/S2/S3K paths and surfaceless EGL: queue13s/Maven1m29s.
All16 XML identities/properties and the new compiled cleanup helper were checked.
The selection covers held/slot/PLC behavior, existing input/dispatch ordering,
API signatures and all four required S3K selectors (both level-loading classes).
Astra independently found no remaining P1/P2 in the bounded cleanup source.

Parent25620 added two missing runtime-owned audio ordering/invalid-phase cases
and the typed Body Display cut. It passed19 held cases and60 Body cases:79 tests,
zero failures/errors/skips, Java25, queue14s/Maven1m27s. DrawObject clears sprite
bit7 inside Display; DrawObjects sets it later after the camera pass. Both writes
belong to the queue owner. DisplayEligibility now retains the exact pre-tail
centre words, Y radius and sprite flags, while DisplayPublication retains the
completed pose. A constructed tunnel test distinguishes radius19/Y76 from
radius14/Y81ABCD after SpecialChunks. Fable's independent Body review found no
P1/code P2; its prose correction and suggested metadata cut are incorporated.

Commands use the current main queue wrapper from the private worktree with
DISPLAY/WAYLAND_DISPLAY unset and LUA_BIN selecting Lua5.4:

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" -Dmse=off '-Dtest=TestPlcOwnedIterationFailureCleanup#failedFadePreclaimDiscardsCompletionAndRetainsFadeWithoutReset+failedBeforeFadeVblankKeepsPrimaryAndMissingPreparationDiagnostic+finalPublicationFailureIsNotRetriedAndDoesNotRequireReset' test -B
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestNativePlayableSlotDispatch,TestControlledVBlankOnlyOwner,TestPlcOwnedIterationFailureCleanup,TestPlcFrameLifecycleCoordinator,TestPlcLifecycleDriverParity,TestControlledFrameRuntime,TestSpriteManagerGameplayInputFilter,TestSpriteManagerUpdateOrder,TestSpriteManagerCollisionOrder,TestSpriteManagerMainTailsTailsDispatch,TestModApiSignatureSurface,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' "-Dsonic1.rom.path=$OPENGGF_MAIN/s1.gen" "-Dsonic2.rom.path=$OPENGGF_MAIN/s2.gen" "-Ds3k.rom.path=$OPENGGF_MAIN/s3k.gen" -Dopenggf.headless.gl=surfaceless-egl test -B
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdPlayerBody,TestControlledVBlankOnlyOwner' test -B
```

These are bounded local checks, not a broad-smoke or native gameplay certificate.
The normal stock CD entry remains static while frame/actor/camera integration
continues. Remaining exceptional held-tail/comparison-window combinations are
source-reviewed but lack direct injected-failure fixtures. No public signature
pin, version, shared GameLoop switch or game-name carve-out was added.

## Explicit terrain-player world, 24 October 2026

The named `terrainOnlyDevelopmentPlayer()` module and
`bootSonicCdTerrainPlayer(source)` host now join the authenticated R11A source
to the normal WorldSession/load/GameLoop, one registered native Sonic slot,
source V-int input/art service, MODE/Main and exact foreground arithmetic.
Default construction remains STATIC/HOLD with no actors. No title entry,
objects, rings, HUD, native music driver or full-act completion is implied.
The supported route and remaining products are in the
[act matrix](../validation/levels/scd-palmtree-panic-act1-present.md).

Frame state has disjoint capture owners: actor Body/history/display/packet,
module Environment/art/pause/clock and the existing parallax handler's source
camera. Engine snapshots retain their world scope; a restored source owner
reprojects camera/actor and all 24 resident tiles without servicing a cue.
Only the existing controlled-frame/PLC/native-slot boundaries execute gameplay;
drawing never samples input, advances animation or services DPLCs. The internal
suppression bridge supplies actual neutral held input when the host suppresses
input; native taps still derive from the last controller read.

Native input after the omitted fade is not proved zero by LevelStart's earlier
clear: fade V-ints read it again. Raw-zero ingress is explicitly DEV policy
(no buttons held at the last represented fade read); logical input is cleared
by the source. Controls are unlocked because title is omitted. PauseMusic and
UnpauseMusic requests/latch are omitted DEV audio; retained Body sounds are
not a complete service/frame audio ledger. Absolute native V-int seed remains
unknown; the runtime exposes a delta from this admitted cut separately.

### Reviews, distinguishing failures and repairs

Astra's foreground review found no P1/P2. Opus's frame review found no P1 and
identified an invalid zero arctangent fixture plus the input/audio declarations
above. The corrected real-decoder fixture passed 74 frame cases; production
arithmetic was unchanged. Fable's actor review and Astra's admission review
each found one P2. Their frozen source mirrors and reports remain externally
preserved, with no repeated unchanged review round.

- A serviced Body failure left the engine owner open even though V-int/input/
  art/timers had advanced. The original actor failed all three regional death-
  boundary regressions (parent 31241: three expected assertion failures, no
  errors/skips). A package-private entitlement preflight now distinguishes an
  unserviced protocol call from a failed serviced slot. The latter closes
  admission and clears pending sprite publication; constructor/rebind bank
  failures also close rather than retry a potentially partial upload. No GPU
  rollback or complete fault-injection coverage is claimed.
- Retained-level reset unbound the presentation before background dimensions
  were read. The real reset consumer failed all three regional regressions
  with `CD background has no bound assets`. Presentation binding now precedes
  the terrain reset, while new actor construction stays after it.
- `CourseControl.restore()` sent undefined negative song IDs into audio;
  `-1` collided with the silent profile's speed-shoes command. Original code
  failed the `-1` and `-2` cases while five positive/publication controls passed.
  The existing restore owner now requests only nonnegative music IDs. This is
  a generic absence guard, without a game-name condition or audio fallback.

The reset/music negative selection completed ten cases: five expected failures,
zero errors/skips, Java 25. Its command-session handle was lost from the root's
printed tool result; completion was established from the ended owned wrapper
process and both complete XML reports. No command exit code is asserted for
that invocation, and no duplicate run was submitted to recover the handle.

Additional actor corrections preserve the native priority mask, reproject the
native mirror on a rejected foreign actor capture and use precise source
citations. Normal module admission supplies the CD rules provider; the older
direct actor fixture's static-module provider is not the production player
entry. Generic movement remains bypassed by the native slot.

### Observed normal-world and render checks

Parent 55641 passed 51 cases with no failures/errors/skips, all three actual
regional discs, actual Sonic 2 ROM, Java 25.0.5.0.2 and surfaceless EGL, with
DISPLAY/WAYLAND_DISPLAY absent. Queue wait was zero, Maven execution 1m46s.
It covers player admission, registry restore, default STATIC, API and audio
replacement, including actual visible native-player residency.

Parent 43898 passed the USA route with no skips: 132 ordinary inputs for
walk/reverse/B-jump/land/Down/pause/resume, every frame rendered, followed by
132 restored inputs rendered again. Complete actor/history, Environment,
CPU/resident art, pause/V-int clocks, sounds and camera match at every step;
final pixels match. Queue wait was 34s, Maven execution 26.317s. Positions in
the retained CSV span centre X64–169 and Y1843–1902; these are observations,
never runtime tuning targets. Six route screenshots and three regional
idle-player screenshots are queued externally, unviewed before 07:00.

After all three repairs, parent 4226 passed **192 cases, zero failures, errors
or skips**, on `a8cb4675a` plus the exact pending integration, completed
05:48:37 BST. Queue wait was zero; Maven execution 2m01s. All 13 XML class
identities and Java/media/EGL properties were inspected. Exact selection:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 \
  python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestCdPlayerFrameRuntime,TestCdPlayableSonic,TestSonicCdTerrainPlayerLoad,TestSonicCdTerrainPlayerBoot,TestCourseControlMusicRestore,TestModApiSignatureSurface,TestHeadlessAudioOwnership,TestSonicCdModuleContracts,TestSonicCdStaticStageLoad,TestSonicCdPlayerPresentation,TestNativePlayableSlotDispatch,TestControlledFrameRuntime,TestControlledVBlankOnlyOwner' \
  "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" "-Dsonic2.rom.path=$OPENGGF_MAIN/s2.gen" test -B
```

Counts are 74 frame, 12 actor, 24 regional load/reset, 8 boot/render/route,
7 music restore, 11 API, 2 audio owner, 4 module, 6 static, 10 held-byte,
9 native-slot/suppression, 6 controlled-frame and 19 serviced-HOLD. Earlier
overlapping selections are not additional unique coverage. Comment-only
cleanup afterward removes obsolete UNRUN labels; no executable changes follow
this focused result.

Shared changes still require the change-based smoke selection and guards.
The earlier timed-out broad invocation remains incomplete. These finite checks
do not establish whole-act, complete USA game, native audiovisual, instruction-
phase, physical timing or complete hardware-device parity. All source remains
on the local private branch; no develop merge, push or public announcement.

## Optional opening-object data and animator, 24 October 2026

This component unit is prepared on a separate local object worktree based on
`9598c663c`. The primary terrain-player checkout remains frozen for its running
smoke selection. No target directory was copied or shared. New components are
not yet admitted to the game loop and do not replace the default STATIC entry
or the explicit three-region terrain-only player entry.

`CdR11aAssets.openingObjects()` is optional and USA-only. It re-verifies the
actual retained immutable MMD before reading nine new native tables or decoding
Ring/Mosqui art. Ordinary regional loading and its constructor remain unchanged.
Raw five-byte mappings, signed pointers/aliases, script tables, touch sizes,
tile selectors and descriptive source/PLC art associations remain bounded and
immutable. Decoded art is not a live PLC transfer or residency certificate.
All runtime bytes still come from the authenticated user-supplied disc.

`CdObjectAnimation` ports the separate object animator, with byte timers,
selected signed-relative reads, map/flip publication and the exact shared
FD/FC/FB/FA comparison value. FF/FE selected bytes go directly to frame
publication without recursive command dispatch. Ring sparkle's routine change
does not delete or dispatch the object; those remain caller responsibilities.
Slot bytes must be carried into the animator, since Ring Init does not clear
its animation fields and native Null clears only the ID.

Astra's six-file asset review and Opus's two-file animator review found no
P1/P2 issue. Both frozen source mirrors remain unchanged externally. Their
source/data checks are distinct from the following parent Java executions:

| Parent command | Observed execution | Scope |
| --- | --- | --- |
| 82893 | 59 tests, zero failures/errors/skips; Java 25.0.5.0.2; queue 0s, Maven 1m36s | New visual/assets/retail consumers 18, animator 27, existing regional asset consumers 3, API 11; actual configured USA/Japan/Europe media |
| 45862 | 2 tests, zero failures/errors/skips; queue 18s, Maven 28.037s | Strengthened all-256 distinct directory targets and exact `id == scriptCount` rejection; production unchanged |

The second run adds one case and strengthens an existing case; these are not
61 distinct cases or a single suite. Other animator expectations passed in the
first run and were unchanged. The header/citation cleanup adds no executable
production change. All six initial XML identities and the two-case result were
inspected, including Java/media properties and zero skips.

Commands ran from the separate object worktree, with display variables unset,
Lua 5.4 and the current main queue wrapper. Each worktree owns its own target:

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdObjectAnimation,TestCdObjectVisualAssets,TestCdR11aObjectAssets,TestCdR11aObjectRetailAssets,TestCdR11aRetailAssets,TestModApiSignatureSurface' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" test -B
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdObjectAnimation#all256AnimationIdsCanSelectTheirOwnWordIncludingTheLastOne+scriptDirectoryEndIsNotAnotherEntryEvenWhenItsBytesWouldSelectAValidScript' test -B
```

Focused validation is proportionate here: the only existing runtime edit is an
explicit optional data-consumer method. No normal load, shared algorithm,
render/timing loop, mutable world owner or API signature pin is changed. New
slot/placement candidates are a separate uncommitted component under review,
not part of this data/animation checkpoint. Their successful compilation cannot
be used as evidence of object execution, touch, live Ring/Mosqui presentation,
shared sprite queues, PLC lifetime, whole-act or complete-game parity.


## Native placement and player touch prerequisite, 24 October 2026

This local component builds on `8c8a975fb` in the separate object worktree;
the primary `9598c663c` remains frozen under its own running smoke selection.
It does not admit objects to either stock/static or terrain-player gameplay.

`CdObjectPool` owns only physical dynamic slots 32..127. Immutable 64-byte
images preserve aliased fields, dirty ID-zero bytes and native write widths.
Null clears only ID; Delete clears the complete slot. Allocation does not
clear or reserve the result. The final FindNext boundary accepts whole-index
31..127: callers 0..30 could read reserved slots this owner cannot observe and
are rejected. The pinned native decoder confirms this restriction; the last
reserved slot31 still begins at32 and every dynamic caller remains exact.
Fable's A1 advisory motivated this explicit boundary rather than pretending
to observe unowned reserved state. No admitted R11A caller changes behavior.

`CdObjectPlacementRuntime` ports native cursor/header/saved-bank state,
initialization, forward/backward streaming, period checks, loaded-bit order,
allocation-failure rollback and unchanged-chunk behavior. Capture owns its
pool exactly once with its own saved flags/cursors. USA admission binds actual
loaded assets; pure constructed-format inputs retain their separate contract.
The actual USA camera0 Present pass produces parent slots32/33/34 with Ring,
Ring and Mosqui, matching the source list and dispatch pointers. Ring children,
body execution and complete RunObjects order are still separate work.

`CdPlayerObjectTouch` consumes all96 physical images, testing source sprite
sign/type gates before dimension lookup. It preserves word carry/borrow,
radius-byte underflow, exact duck shape, physical first-return order and the
unsigned hurt threshold. A generic item yields a detached routine-byte patch;
no pool mutation occurs during the scan. Enemy/special/hazard/monitor handlers
remain explicit unsupported call boundaries. The root must stage an item patch
at Body's post-animation/pre-SpecialChunks cut and commit only with the completed
body. This prerequisite adds no Body, frame, sprite-queue or reward writer.

Astra's touch review and Fable's slot/placement review found no P1/P2 defect.
Their frozen candidates remain unchanged externally. Root's only production
adjustment is the FindNext lower-bound restriction above; its caller-visible
regression was edited first and all six pool methods were then executed.
The actual-retail USA test now also constructs the touch consumer from the
loaded table and checks a detached item patch with the real owned slot pool.

| Parent command | Observed execution | Coverage and limits |
| --- | --- | --- |
| 83004 | 26 tests, zero failures/errors/skips; queue31s, Maven1m25s | Six pool,18 constructed streaming and two actual-media methods; original finite allocator contract |
| 13459 | 22 tests, zero failures/errors/skips; queue28s, Maven1m25s |19 touch methods and three regional object-asset cases, including the actual-USA factory/slot assertion |
| 11699 | Six tests, zero failures/errors/skips; queue41s, Maven1m19s | Final pool contract, including all31 earlier reserved-caller rejections,31/32/126/127 bounds, dirty reuse and capture ownership |

Java25.0.5.0.2 and each selected XML identity were inspected. The two media
selections used all three configured discs and had zero skips. Fresh source
compilation occurred for the final pool edit. Counts overlap; they are not one
combined suite or distinct aggregate. Source reviews did not execute Java.

Commands from the object worktree used display variables unset, Lua5.4 and the
current main queue wrapper; all targets remained worktree-local:

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdObjectPool,TestCdObjectPlacementRuntime,TestCdObjectPlacementRetailAssets' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" test -B
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdPlayerObjectTouch,TestCdR11aObjectRetailAssets' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" test -B
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdObjectPool' test -B
```

Focused validation is proportionate to new, unadmitted CD data/state owners.
No shared loop, timing/physics, renderer, public API pin or current world
execution is changed. The sole existing test edit consumes the new real-data
factory. The primary's broad smoke remains a separate unfinished result.
No live object, Ring pickup, Mosqui route, source sprite queue, native audio,
whole act, USA MVP or regional/full-game parity is certified by this unit.


## Ring execution and shared CPU sprite queue, 24 October 2026

These are finite, unadmitted components on the separate local object branch,
based on `7309600e6`. No native object world or launch entry is installed by
this checkpoint. The primary `9598c663c` smoke source remains untouched.

`CdRing` uses the existing physical pool and placement-owned saved bank for
Init group expansion, Main, Collect, Sparkle and next-dispatch Destroy. It
preserves dirty slot/animation bytes, loaded-bit and allocation-failure order,
the shipped warp D2/X clobber, source range/period arithmetic, reward thresholds
and selected sound ordering. DrawObject is a synchronous source-cut port; no
slot write follows it. The global Ring timer is a separate immutable value
advanced once by the future entitled loop tail, never per object or render.
The production factory binds the actual optional USA asset owner. Decoded art
still establishes no preload, residency or complete native PLC lifecycle.

`CdObjectDrawQueue` binds one live-slot port and one mapping port. It queues
physical addresses only in eight buckets of 63 pointers, shared by player0
and dynamic32..127 under this finite admission. It clears bit7 before culling,
uses the old foreground camera at enqueue, then rereads every current occupant
after camera movement, retaining append order and delete/reuse semantics.
CPU SAT construction preserves native full-word attributes, flips, per-piece
80-entry truncation, links, terminator and raw unused tail. Screen Y at0A is a
word. Closed-cut snapshots own only this CPU partition; the parent still owns
next-VInt displayed pixels, art, composite source/phase validation and closure.
There is no ID-selected mapping, second player raw image, private Ring queue,
generic RingManager, renderer or hardware scanline emulation in this unit.

Opus's four-file Ring review and Astra's CPU queue review found no P1/P2
source/expectation defect. Their mirrors remain frozen. The first parent queue
build revealed a missing private putWord helper: compilation failed before
any test ran. Root added only the big-endian two-byte writer; the subsequent
26-case run passed. Final queue source SHA-256 is `3d9b4fd470ed6a40e3a42133b2b89323407329793d42f2ad80e129e5363f9074`.
The reviews' UNRUN wording describes reviewer execution; it does not replace
these separately observed parent runs:

| Parent command | Observed execution | Scope |
| --- | --- | --- |
| 68837 |137 tests, zero failures/errors/skips; queue46s, Maven1m29s |28 new Ring cases, including two actual-USA retail methods;14 separate shared-phase checks and95 maintained frame/pause/S2/S3K/API cases |
| 25374 | Compilation failure, no tests executed; queue383s, Maven55.794s | Missing putWord helper in the first queue source; no behavior result |
| 84647 |26 tests, zero failures/errors/skips; queue120s, Maven1m23s |16 CPU arithmetic/order/SAT methods and10 phase/ownership/failure methods after the helper correction |

All16 selected reports from68837 and both queue reports from84647 were
inspected. Java25.0.5.0.2 was used;68837 had actual three-region media and all
S1/S2/S3K ROM properties, with zero skips. The137 count includes unrelated
maintained coverage and is not137 new component cases. These are separate
bounded selections, not a complete smoke or whole-game pass.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestNativeDynamicObjectFrameOrder,TestNativeDynamicObjectPass,TestCdRing,TestCdRingRetailAssets,TestNativeFixedLevelObjectPass,TestFbzFramePhaseOrdering,TestLevelFrameHardwareTimingBoundaries,TestPlcVBlankOrdering,TestInGamePause,TestS2FrameOrderingExecThenLoad,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestModApiSignatureSurface' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" "-Dsonic1.rom.path=$OPENGGF_MAIN/s1.gen" "-Dsonic2.rom.path=$OPENGGF_MAIN/s2.gen" "-Ds3k.rom.path=$OPENGGF_MAIN/s3k.gen" test -B
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdObjectDrawQueue,TestCdObjectDrawQueueLifecycle' test -B
```

The shared-phase source used by68837 remains a separate uncommitted change.
Its later source review identified an earlier controlled V-int increment that
can precede rejection of an invalid native binding. That P2 remains open until
its caller-level guard regression/fix is executed; it is not hidden by137 green
focused checks. No shared phase source is part of this component commit.
Ephemeral player-touch integration and actual Mosqui execution also remain
separate work. Next-VInt publication, art admission, real Ring pickup and
rewind through the complete world loop are still unverified. Full USA MVP
continues to mean the entire game, followed by regional certification.


### Native dynamic phases and pre-clock admission, 24 October 2026

Added the internal `NativeDynamicObjectPass` service without changing GameModule
or the pinned public Mod API. One resolved owner spans placement before reserved
slots and live dynamic execution after reserved slots, before the actual camera.
A bound native owner rejects legacy ordering, nonordinary phases and competing
generic object/ring managers. Unbound loops retain their existing execution.

The initial source review found a caller-level P2: ControlledFrameRuntime could
advance the represented V-int before LevelFrameStep rejected an invalid binding.
The actual two-case regression compiled and failed with V-int99 becoming100 and
the ordinary timer9 becoming8. The fix admits before beforeGameplay/clock work,
retains resolved absence, and revalidates callback-driven module/manager/load
replacement without a second lookup in an unchanged world. A valid earlier
service is not rolled back after a later fade replacement. Setup/hold/pause/lag
do not acquire an ordinary dynamic owner. No static or thread-local admission
cache, source-name branch or new timing-data authority was added.

Focused parent27844 ran159 tests: zero assertion failures, three fixture errors,
zero skips. All three positive controlled paths reached the real Camera without
a focused actor. Root attached the fixture's actual NativeProbe to that camera;
no production Camera change was made. Affected rerun20020 then passed all12
TestControlledNativeDynamicAdmission cases with zero failures/errors/skips.
The unchanged other147 checks from27844 passed, including direct phase order,
PLC, represented clocks, hardware boundaries, pause, Mod API and maintained
S3K loading/bootstrap/decoding consumers. XML verified Java25.0.5.0.2, actual
CD media and the surfaceless-EGL property; the actual unbound CD controller
movement check executed. These overlapping selections are not a broad pass.

Commands, worktree HEAD a5b01f073 plus the six uncommitted phase paths:

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestControlledNativeDynamicAdmission#legacyOrderRejectsBeforeRepresentedVIntAndGameplayEffects+competingRingManagerRejectsBeforeRepresentedVIntAndGameplayEffects' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" "-Dsonic1.rom.path=$OPENGGF_MAIN/s1.gen" "-Dsonic2.rom.path=$OPENGGF_MAIN/s2.gen" "-Ds3k.rom.path=$OPENGGF_MAIN/s3k.gen" -Dopenggf.headless.gl=surfaceless-egl test -B
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestControlledNativeDynamicAdmission,TestNativeDynamicObjectFrameOrder,TestNativeDynamicObjectPass,TestControlledFrameRuntime,TestControlledVBlankOnlyOwner,TestVIntRunCounter,TestGameLoopVIntRunCounter,TestPlcVBlankOrdering,TestLevelFrameHardwareTimingBoundaries,TestModApiSignatureSurface,TestNativeFixedLevelObjectPass,TestInGamePause,TestFbzFramePhaseOrdering,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" "-Dsonic1.rom.path=$OPENGGF_MAIN/s1.gen" "-Dsonic2.rom.path=$OPENGGF_MAIN/s2.gen" "-Ds3k.rom.path=$OPENGGF_MAIN/s3k.gen" -Dopenggf.headless.gl=surfaceless-egl test -B
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestControlledNativeDynamicAdmission' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl test -B
```

Separate primary9598 smoke45011 completed red (all74ordinary batches and guards):
ordinary38921 tests,2 failures,2 errors,176 skips; guards722 tests,8 failures.
Attribution and required corrections remain open; no clean delivery is claimed.
Actual Ring pickup, Mosqui execution, shared displayed SAT and composite rewind
remain the next private-world integration, separate from this internal phase.


### Ephemeral player touch through the frame owner, 24 October 2026

Body now accepts one optional immutable touch context at the actual post-Animate,
pre-SpecialChunks call. It stages the first physical selected item image without
writing the pool, retains no snapshot in player/environment/rewind state, and
rejects unimplemented enemy/monitor/hazard/special handlers with a typed boundary.
A later Body failure publishes no item patch, sound or player result. The caller
still owns committing an item only after the complete Body returns. Raw Display
width/priority travel with the call cut; the old terrain-only cull remains equal.

The named external-touch-required Frame factory does not claim empty dynamic
RAM and retains the two-stage art callback binding. Existing terrain factories
keep their prior behavior. Root added four Frame checks for binding, missing or
non-retained context, detached item/replay behavior, failed handlers and unserviced
/paused entitlement. Opus's four-file source review found no P1/P2 or wrong
expectation. Root's additional four Frame invocations executed separately from
that historical source review in the combined parent run below.

Parent83771 passed244 tests, zero failures/errors, one skip at componentac49f80cb
plus the reviewed touch/Mosqui files and parent Frame regressions. Per class:
Body78, Frame78, actual-regional actor12, regional lifecycle24, boot8 (one
skipped), Mosqui26, floor helper7 and Mod API11. Java25.0.5.0.2 and actual CD
media/surfacelessEGL properties were verified in all eight XML reports. The
skipped boot case is cartridge-to-CD switching: the command omitted sonic2.rom.path.
It is not counted as observed switching coverage and will be included with the
next affected world integration selection. The other boot cases included the
existing USA132-input rendered/rewind route and regional EGL loading. No new
Ring pickup or shared-object world route ran.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestCdPlayerBody,TestCdPlayerFrameRuntime,TestCdPlayableSonic,TestSonicCdTerrainPlayerLoad,TestSonicCdTerrainPlayerBoot,TestCdMosqui,TestCdObjectCollisionCheck,TestModApiSignatureSurface' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl test -B
```

This commit contains only the five touch/player test paths and this existing
record. Mosqui is a separately reviewed component. Pool commit, live dynamic
execution, shared CPU SAT and displayed-next-VInt pixels remain root-owned
world integration, with no touch suppression or generic Ring fallback.


### Native Mosqui body and floor call, 24 October 2026

The selected $3D body now uses the sole placement pool/saved bank and actual
Mosqui tables, including Init/Main fall-through, dirty-slot bytes, the second
movement on patrol reversal, pointer-selected animation, full-word debug gate,
dive/floor/landing/Wait and source draw/despawn order. Synchronous DrawObject
observes committed fields; the body reloads the live slot afterward, retaining
callback writes and zeroed-slot behavior. Unknown routine/script/good-future
conversion stops at its explicit ownership boundary. No enemy-touch response,
Explosion, audio driver, native art preload or complete-act support is implied.

Astra found no P1/P2 in the four frozen files. Root narrowed only the imported
javadocs: the shared DrawObject port must not reenter body dispatch, and the
returned PRIMARY_ANGLE byte is for the caller's existing shared scratch owner;
unlike pool/bank writes, that global has not already been committed. It must
not be ignored based on a universal overwrite claim. The immutable player
previous-angle bytes remain a separate field. The external mirror/review bytes
are preserved; no executable Mosqui expression changed after review/testing.

Parent83771 executed all33 Mosqui/floor invocations (26 body including eight
range rows and actual-USA assets, seven floor helper), zero failures/errors/skips,
within the244-case touch/player integration selection documented above. Actual
USA media bound both mapping/script pairs at their source addresses. Constructed
terrain verifies the dive continuation; this does not prove its actual opening
column, native capture parity, multibody World ordering or displayed pixels.
Root's next composition owns those globals, shared queue, displayed-next-VInt
latch, live Ring/Mosqui pass, unknown-ID rejection and capture/restore.


### Authenticated mappings and immutable shared picture, 24 October 2026

Added exact raw-pointer mapping resolution from the actual Sonic/Ring/normal-
and damaged-Mosqui source views, plus pure CPU SAT conversion to the existing
immutable SpritePresentation.Frame. Native full-word attributes, tile wrap,
column-major pieces/flips and reverse-SAT painter order are retained. Unknown
pointers/art and unsupported finite coordinate modes fail explicitly. Static
one-piece mappings remain a distinct declared format; no unowned production
static source is invented. Conversion neither clocks nor publishes a picture.

Opus's independent four-file review found no P1/P2. Parent83759 then passed all18
new methods, including actual-USA factory/bank checks, within58 selected cases
(geometry10, retail3, module4, actor12, API11, mappings7, picture11), zero failures/
errors/skips. Java25.0.5.0.2 and actual CD media were verified in all7XML reports.
Other root geometry/rule corrections in this tree remain a separate guard repair.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestSonicCdLevelGeometry,TestSonicCdLevelRetail,TestSonicCdModuleContracts,TestCdPlayableSonic,TestModApiSignatureSurface,TestCdObjectMappings,TestCdObjectPresentation' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" test -B
```

World composition must register one exclusive governed atlas range, subdivide
its three logical pixel spans, recreate the player pixel source at every completed
selector8 service and latch previous CPU SAT with now-serviced art. A one-time
preload would freeze Sonic and is not permitted. The existing renderer's mixed
sprite/plane-priority overlap approximation remains an explicit parity gap: native
sprite winner precedes plane comparison; do not reorder SAT to compensate. This
pure unit does not certify foreground compositing, native residency, actual
opening-object rendering, shared World restore or gameplay parity.


### Guard attribution and world composition repair, 24 October 2026

The completed smoke at clean9598c663c executed all74 ordinary batches plus
guards:38,921 ordinary cases, two failures, two errors and176 skips;722 guard
cases, eight failures. Matched19-case baseline/current runs reproduced the
exact same four ordinary failure identities, types and messages: bare GLFW
without a display, a same-JVM cleared EGL property, and an inherited Java launcher
notice preceding the audio CLI's stderr prefix. No unrelated donor or graphics
code was changed to hide those failures. Diagnostics were consumed and removed.
This remains a bounded red smoke with attributed environmental failures, not a
complete green delivery or a release/full-suite result.

Repair the introduced ownership violations at their owners. WorldSession keeps
only a private opaque scope; GameplayModeContext composes its registry through
the exact-caller EngineRewindRegistryFactory. Its single composition-root
allowlist entry does not widen creator authority. Public unscoped constructors
assign their own fields directly; frozen architecture stores and API pins stay
unchanged. Reconciled tests use actual gameplay contexts and empty registry
fixtures, retaining the original size-estimator constants. Independent Opus
review found no P1/P2; parent67225 passed141 rewind/core/API cases with zero
failures/errors/skips, Java25 and required cartridge properties inspected.

Initial CD geometry now uses the existing Sega-format decoders while retaining
independent blank descriptors. A root mistake passed a grid side instead of its
256 entries; the resulting failure and Mockito cascades were inspected, the
count corrected, then parent83759 passed58 geometry/retail/module/actor/API/SAT
cases with zero failures/errors/skips and actual regional media. Presentation
rules use an engine-owned semantic factory; unimplemented shared gameplay gates
remain absent. No cartridge profile or game-name exception was introduced.

The optional canonical scddisasm pin remains1ecc5f3a. Remove its floating branch
hint and include it with the other three canonical reference paths in structural
checks. Parent15071 then passed721 of722 guards; the remaining assertion rejected
the documented ordinary clone's optional --branch develop. Port the current
main-workspace assertion accepting only that optional branch, leaving developer
instructions unchanged. Parent27346 passed the exact corrected case, zero skips.
Following opening-world composition, parent55552 freshly passed111 affected
architecture/rewind/module/presentation guards, zero failures/errors/skips. All
selected XML Java25 identities were inspected. Unchanged guard coverage is reused
proportionately rather than repeated as a ceremony.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off -Pguards '-Dtest=TestArchUnitRules,TestArchitecturalSourceGuard,TestRewindArchitectureGuard,TestGameModuleRegistryUsageGuard,TestProductionAwtBlacklistGuard,TestNoWorldPresentation' test -B
```

All work stays on private local branches. Public changelog/guide publication is
intentionally deferred under the user's explicit no-disclosure instruction;
this existing dated engineering record preserves the corrections and limits.


### Opening native frame owner and shared player bridge, 24 October 2026

CdOpeningObjectRuntime now composes the authenticated USA placement pool/saved
bank, Ring and Mosqui bodies, ephemeral post-player touch, shared live-pointer
DrawObject queue, CPU SAT and immutable displayed-next-VInt picture. It executes
physical slots in live ascending order, including same-pass children; unknown
IDs and unsupported enemy/hazard/monitor/special contact fail at their real
dispatch or handler. No visibility/coordinate/route fence suppresses execution.
The separately declared development ingress has zero CPU SAT, empty displayed
picture, explicit Ring globals, absent reserved slots1..31 and preloaded actual
object art. It does not prove a retail title/new-game load or native residency.

Ordinary service latches the previous CPU SAT with now-serviced player art;
pause publishes without a body and lag retains the prior picture. A single
root-supplied primary-angle setter commits Mosqui's returned scratch byte before
later dispatch; actor previous-angle copies remain separate. Completed Body
returns stage the detached selected-item patch before pool commit. The last-loop
typed sound ledger resets on ordinary service only, remains visible across
holds and must not be replayed by draw/restore. FM_CHARGESTOP remains a stop
request. Runtime and queue failure close sibling admission without rollback.

CdPlayableSonic's optional ObjectPass delegates Body and the exact DrawObject
cut, then projects the shared on-screen bit after camera/SAT construction. It
does not build or draw a private one-player SAT in this mode. Pre-service misuse
still rejects without closing an unadvanced frame; body/enqueue/GPU failures
after admission close both owners. The existing terrain-only constructor and
draw path are retained. Snapshots partition actor Body/history/display from
Frame globals/art and runtime pool/CPU/display/sounds; each retained owner
preflights its capture before mutation.

Fable's independent runtime review found no production P1/P2. Its one test-only
finding confirmed native unconditional charge-stop precedes a Ring reward;
root corrected only that expectation. Parent18666 passed114 cases, no failures,
errors or skips:Frame80, regional actor12, shared bridge4 and runtime18. Actual
media and Java25 were inspected in all four reports. The earlier omitted S2
property was supplied in parent31860, whose cartridge-to-CD switching case
passed; it is not a retained skip. These component checks do not themselves
establish a composed World pickup or native gameplay parity.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestCdPlayerFrameRuntime,TestCdPlayableSonic,TestCdPlayableSonicObjectPass,TestCdOpeningObjectRuntime' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" test -B
```


### Actual opening World and first Ring, 24 October 2026

The separately named openingObjectsDevelopmentPlayer / bootSonicCdOpeningObjects
entry authenticates USA R11A before retiring an existing World. It assembles one
native Sonic and the shared Ring/Mosqui pass through normal input, PLC, level
frame, live dynamic slots, camera and ordinary tail owners. Default STATIC and
regional terrain-only entries retain their capabilities. Advanced camera,
afterTick and restore failures now close terrain-only admission more strictly
as well. The one exclusive OBJECTS atlas range supplies108 picture patterns;
the separate player resident bank retains24. No generic object/ring manager,
private player drawing or alternate sound playback substitutes for the pass.

Parent11800 compiled and ran69 cases, zero errors/skips:68 passed, including all13
new actual-World lifecycle invocations and maintained module4/static6/terrain24/
presentation10/API11. The sole failure was the original88-row input proposal,
which stopped at centreX214.75 before RingX240. Its observations were inspected;
only the ordinary input tail changed from2neutral to16Right, making102 available
rows. No movement, touch, object, camera or production expression changed to
reach the Ring.

Parent63645 then passed the single real-EGL route case, zero failures/errors/
skips. Actual USA media and surfaceless EGL/no ambient displays were required;
Java25 and selected properties were verified in XML. Ring pickup occurred at
call92 with FM_RING; call93 published the completed pickup SAT and now-serviced
player art. All91 rows after the two-call checkpoint reproduced complete actor,
shared state/art, pool/saved bank/cursors, Ring globals/sounds, CPU SAT/provenance,
displayed pixels, camera/scroll and resident bank through both full and course
restore. Restored and final framebuffer pixels matched exactly. Rejected boot
preserved the old World; fresh boot retired it and cleared CPU/display/Rings.
Mosqui dispatched throughout, including when outside the viewport. Audio stayed
NullAudioBackend. Actual PNGs, input log and observations live outside the repo.

Opus's independent World/bridge review found no P1 and one P2 in the stated
presentation limits, now recorded here and in the external R3 handoff:
**terrain is drawn at the current loop's camera, sprites come from the previous
loop; there is no synchronized native plane/scroll or pixel-parity claim.**
During camera motion this offsets sprites relative to terrain by one camera
delta. Native VInt_Level latches scroll/SAT/level drawing together
(Level/Main.asm:690,826-827,840-844); the development terrain path does not yet.
Engine-to-engine replay equality cannot establish native pixel parity. Mixed
plane/sprite priority remains a second explicit gap. Neither input nor actor
positions are adjusted to conceal these gaps.

Other reviewed limits remain explicit: BG/BG3 cameras are fixed0, external engine
stage failures close at the next admission gate, generic-manager preconditions
are checked before the first represented frame, and mid-cycle restore is not an
admitted cut. HUD, enemy/hazard response, native title ingress, driver audio,
live PLC residency, time travel, other stages and complete-game parity remain
open. The first Ring is an intermediate milestone toward the full USA game.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestSonicCdOpeningWorldEgl' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true "-Dsoniccd.opening.output=$SCD_NEW_EXTERNAL_CAPTURE" test -B
```

The optional capture hook requires a new directory under Downloads/Sonic-CD,
preserves earlier captures and records only post-step observations. It never
supplies native state to gameplay. A dedicated R3 external revision preserves
the tested six-file bytes, failed and successful input history, review response,
commands and remaining publication limits. No merge, push or public launch is
performed by this checkpoint.


### Optional frontier assets and counted formats, 24 October 2026

Added nine individually lazy USA views for Animal/3DPlant/ramp-booster data and
a separate lazy FrontierArtBank for their three actual art sources. Existing
opening load still eagerly reads exactly its original nine views and decodes
only Ring/Mosqui; initialArtBank remains two bindings. The owner authenticates
the retained immutable MMD, synchronizes private lazy caches and publishes only
complete immutable derivatives. Native act/time indices preserve actual zero
Animal base words and promise no art residency for other acts/periods.

Unscripted counted mapping tables now have an explicit decodeStatic entry;
the old animated entry still requires a script. Unscripted selection uses the
native full unsigned oMapFrame byte, independently of the script's five-bit
frame field (DrawObjects.DrawSprite, Object Functions.asm:333-335). This is not
the bit-5 header-free StaticMap format. A separate exact-tail overload retains
the ramp's11/21 unused source bytes after mapping frames8/9 without drawing or
compacting them; unlisted frames retain strict body/alignment validation.
The metadata is about native mapping indices, never gameplay frames/routes.

Finite Nemesis bounds distinguish authored blob from actual reader bytes:
Animals446/445, Plant392/393, Ramp678/677. Plant's required extra lookahead is
the actual adjacent MMD byte80, not invented padding. Exact decoded capacities
are640/896/2016 bytes. Bank association checks source/PLC row/VRAM/base word;
surrounding gaps remain absent. No object dispatch, live PLC or resident-VRAM
certificate is established by these bindings.

Independent Opus decoder and Sol asset reviews found no P1/P2 in their frozen
scopes. Root verified their advisories against code/native source, strengthened
tail-key/unlisted-frame regressions, documented StaticMap distinction and added
the full-byte unscripted frame boundary. A fresh asset owner first decodes its
frontier bank after the media source has closed, checking real Plant bytes and
repeat identity. Root44175 passed22 decoder/old-asset cases; then23823 passed13
new-asset cases; final71286 passed16 affected decoder/retail cases (13+3), all
zero failures/errors/skips. Java25.0.5.0.2 and actual media were inspected in all
selected XML. These selections overlap and are not one aggregate suite.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdObjectVisualAssets,TestCdR11aObjectRetailAssets' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" test -B
```

The full ordinary byte-pointer range does not invent any additional source
table or runtime admission. Roaming/hologram animals, solid FloatBlock,
object-to-player effects and shared-world dispatch remain separate owners.
Actual first Ring captures and their documented scroll/publication gap are
unchanged by this asset/format work. Original frozen reports/source mirrors
retain their reviewed hashes; root's small advisory followups are recorded here.


### Native frontier body owners, 24 October 2026

Added bounded Animal, 3DPlant and ramp-booster bodies using the existing injected
live pool and saved-placement bank. Animal's intact-projector Present path only
checks despawn; roaming/hologram variants retain explicit owner boundaries.
Plant commits parent and first-free children in native order, preserves dirty
unwritten slot fields and stops at the actual out-of-pool write without rollback.
Ramp Init replaces its sprite flag byte, preserves word arithmetic and returns
its player Y-velocity write as a staged typed effect, not a private player edit.
Draw callbacks are synchronous; completed child/despawn effects are never replayed.
Four raw slot aliases add names only, without changing state storage behavior.

The independent frozen-source review found no P1/P2. Its test-fixture advisory
was verified: placing a ramp overwrote the dirty sprite-byte seed. Root moved the
seed after placement so the assertion distinguishes byte replacement from OR.
No production change was needed. Original frozen source/report bytes remain intact.

Root session2865 passed113 focused cases: Animal25, Plant29, Ramp35, pool6 and
placement18, with zero failures/errors/skips and both actual-media methods run.
After the fixture correction, session48345 passed that exact Ramp case again;
its unrelated scroll-fixture errors and expected CD presentation regression
remain separate, not a green combined selection. Java25.0.5.0.2 and media inputs
were inspected in selected reports. Overlapping executions are not summed.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdAnimal,TestCd3dPlant,TestCd3dRamp,TestCdObjectPool,TestCdObjectPlacementRuntime' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" test -B
```

These pure owners are not yet dispatched by the opening world at this commit.
Solid FloatBlock, alternate ramp fall, animal roaming, enemy/hazard response,
live art residency, native ramp player mode and complete route parity remain
open. Existing first-Ring evidence and the terrain/SAT publication gap are unchanged.


### Secondary native slot aliases, 24 October 2026

Corrected oSolidType and oLayer aliases to physical bytes25/29, matching
Level/_Definitions.inc:59-66 at the pinned reference. Each zero-size declaration
follows the primary byte, so it shares the following secondary byte. The prior
names incorrectly shared primary routine/subtype24/28. The item-touch test had
encoded the same incorrect alias; it now checks that the primary routine wraps
while secondary solidity/layer retain their actual dirty values. A raw64-byte
regression proves alias writes change only25/29. Existing screen-Y byte view is
compatibility evidence, not a new word-wide screen-coordinate owner.

Both corrected regressions failed against the old aliases in session14216
(2tests/2failures/0errors/skips). After the minimal enum-value correction,
session89744 passed complete pool/touch classes:26tests/0failures/errors/skips,
Java25.0.5.0.2 verified in both reports. This enables later explosion/solid owners;
no existing production consumer used these two aliases, and enemy response is
still unadmitted. No player values or collision outcomes were adjusted.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdObjectPool,TestCdPlayerObjectTouch' test -B
```


### Paired completed-loop scroll publication, 24 October 2026

The opening development World now publishes terrain scroll and its immutable SAT
picture from the same completed native loop. The previous checkpoint's current
terrain/prior-sprite displacement is fixed for this admitted path. A real EGL
regression first reproduced it at pixel37,0 outside every displayed sprite tile.
No gameplay position, input, source clock or native movement was adjusted.

LevelSpritePresentation now exposes opaque immutable ScrollFrame capture, pure
owner/lifetime validation and a terrain draw scope. It retains the actual full
scroll buffers/registers and resolved render mode, with manager/World/source,
level/load generation, renderer, camera/parallax, graphics/atlas and viewport
ownership. Capture does not drain/draw/advance; scope never moves gameplay camera.
The scope owns normal pattern commands, not arbitrary SAT/effect/art mutations.
Same-instance graphics cleanup/reinit without a World/renderer/load reset remains
outside the available lifetime detection. No generic CD sprite publication was enabled.

The CD Presenter owns prepared/published render cuts separately from its native
foreground accumulator. Cold pairs remain null until first input after completed
production load. Its per-visit PLC composition delegates exactly one Runtime
service, then publishes the prior prepared cut on ordinary/pause; PP lag retains
both. After a completed ordinary tail it captures the next cut. Rendering and
restore never manufacture or advance it. Shared restore preflights all three
Frame/Runtime/scroll partitions before writing, and stale service handles reject
before they can close or change the current visit. Advanced capture/service/draw
failures close the sibling source owners without pretending to roll back GPU work.

Independent source review found two shared P2s, both repaired and re-reviewed:
queued terrain wrappers now own render-mode copies rather than controller reuse
buffers; cinematic admission covers contributor setup through unwind. The direct
graphics capture dependency now restores its prior target even when unwind,
discard or frame cleanup fails, retains the primary error and visits each release
once. Nested captures retain their documented immediate-inner order. Failed lists
are cleared and not pooled. No ordinary flush owner was refactored.

Observed checks, each attributed to its source selection rather than summed:

- 48345: original rendered pixel failure plus17 new-fixture cascade errors; the
 constructor-safe partial service fixture corrected the latter separately.
- 3453: tests-only R2 on old production,31tests/9 intended failures/0errors/skips.
- 38364: repaired shared scope/cleanup plus maintained capture regressions,
 57tests/0failures/errors/skips.
- 90130: actual CD pickup/full/course/pause/cold replay, regional entries and API,
 71tests/0failures/errors/skips.
- 79813: required S3K consumers, publication lifecycle and two real cinematic
 terrain/priority-mask probes,68tests/0failures/errors/skips.
- 78762: complete guards profile,722tests/0failures/errors/skips across89fresh XML.

All selected XML Java25.0.5.0.2 and required media/ROM/EGL properties were checked.
The actual capture contains93 observations; all22 earlier gameplay columns match
exactly. The91 consecutively sampled camera pairs publish prior prepared values;
the initial0-to2 sample gap deliberately omits call1. At call93 current cameraX72,
published cameraX69; the SAT and terrain use the latter. Actual call16/65/93 PNGs
were visually inspected. Repeated draw and full/course replay preserve the image.
The new local comparison uses only actual OpenGGF captures, not emulator references.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestLevelScrollCapture,TestLevelScrollDrawScope,TestCapturedCommandCleanup,TestLevelSpritePresentation,TestPooledRenderCommandDiscard,TestRenderContext' test -B
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestSonicCdTerrainPlayerLoad,TestSonicCdModuleContracts,TestSonicCdStaticStageLoad,TestSonicCdPlayerPresentation,TestModApiSignatureSurface' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true test -B
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" -Dmse=off -Pguards test -B
```

These checks are not a full ordinary smoke or release full-suite pass. Native
plane VRAM/palette publication, background deformation, mixed sprite/plane
priority, live PLC, driver audio, contact consequences and complete-game parity
remain open. Generic mode-copy allocation impact is unmeasured. Existing earlier
smoke attribution remains historical; no unrelated donor/platform code was fixed.


### Lazy enemy-contact asset bindings, 24 October 2026

Added six individually lazy USA tables for EnemyPoints and the explosion,
points and flower visuals. Points is an unscripted counted mapping. A separate
ContactArtBank binds the three cold PLC1 sources:70 explosion tiles,5 points
and39 flower tiles. The checkpoint tile gap remains unbound. Opening load still
reads its original nine views/two sources; neither existing bank is expanded.
No contact response, object lifetime or runtime residency is inferred from art.

Finite source/reader extents are explosions1256/1256, points100/101 and
flower646/645. Points uses the actual adjacent80 byte at the FlowerCapsule
header, not padding. Init base words8680/06C6/A6D7 record their actual consumers;
flower's later06D7/26D7 writes remain object execution decisions. All lazy
views/visuals/art first decode after media closure in the fresh-owner regression.
Authentication and separate JP/EU terrain loading are unchanged.

Independent source/data review found no P1/P2. Root combined session1757 passed
166 cases with zero failures/errors/skips, including all13 constructed asset
cases and three actual regional invocations. It also exercised the existing
frontier bank through the new runtime factory, World lifecycle, actual EGL
pickup/full-course replay and API. All nine XML confirm Java25.0.5.0.2 and the
actual media/EGL properties. This is one bounded selection, not a full suite.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestCdOpeningObjectRuntime,TestCdPlayerFrameRuntime,TestCdObjectMappings,TestCdObjectPresentation,TestCdR11aObjectAssets,TestCdR11aObjectRetailAssets,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestModApiSignatureSurface' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true test -B
```

These bindings do not implement hurt, defeat, death, score/global writes or
the missing aftermath bodies. The full USA and regional parity goal stays open.


### Live frontier object composition, 24 October 2026

The opening Runtime now dispatches the bounded Animal24, 3DPlant2C and ramp2A
owners through the actual live physical-slot walk. Later-slot plant children
run in the same pass; earlier slots wait. Each dispatch reads the completed
player fields, and ramp's staged Y-velocity word is applied exactly once before
the next slot, including after same-call despawn. No actor position, control or
collision flag is fabricated. The narrow Frame setter preserves every other
actor/shared/display/art/input field and enforces its open closed-frame cut.

Explicit development globals goodFuture/projDestroyed are zero and captured once
with the Runtime. Five logical picture spans now cover24/14/70/28/63 patterns;
the old three-argument bank constructor derives the two appended bases. The
Presenter reserves and retires199 patterns in its existing governed range.
Mapping joins use the actual authenticated plant/booster tables, retaining
unused counted tails. The old three-span pixel policy stays strict; the new
named policy admits only its five disjoint physical/logical spans. Animal art
is decoded by the frontier bank but deliberately absent from drawing admission.

Independent Opus review found no production P1/P2 and one test-only P2: the
plant fixture paused with Start, then released Start without unpausing before
its next spawn. Root session25051 compiled and reproduced the exact phase
error (1test/0failures/1error/0skips). The minimal fixture correction uses an
ordinary service to publish before starting the next pass; production stayed
unchanged. The restored ramp assertion now compares its actual pre-body capture.
The World/EGL restore checks include the new single-owned globals, and the
rendered expected-pixel check uses the five-span policy.

Combined root1757 passed166 tests with zero failures/errors/skips:23Runtime,
82Frame,7mappings,11picture,13constructed assets,3regional assets,15World,
1actualEGL pickup/replay and11API. All nine XML confirm Java25.0.5.0.2, actual
media and explicit surfaceless EGL. Queue wait115s, execution1m31. These checks
are affected coverage of the combined working tree, not an exhaustive suite.
The previous shared/S3K/guards checks are not repeated for unchanged owners.

The camera200/380 tests are constructed service-contract inputs, not natural
World-route proof. Camera200 runs the inert Animal without drawing; camera380
commits the plant family then stops at the actual unowned FloatBlock12. The
booster tests are explicit constructed ingress, because the natural route still
has the earlier player ramp/contact owners. No object IDs are skipped, no
contacts suppressed and no full-pool allocation silently omitted. Current World
render verification still covers the normal-input first Ring, not full act play.
Enemy hurt/defeat/death consequences, solid objects, native player ramp mode,
section PLC before camera680 and complete USA/regional parity remain open.


### Explicit player rotation byte before contact response, 24 October 2026

Body.State now requires native oPlayerRotDist ($39) explicitly, independent of
motion.stick ($38). There is no implicit five-argument zero default. Cold Frame
ingress supplies zero from ClearObjects, while every Body/Frame reconstruction,
projection and owned actor capture preserves dirty values. Native KillPlayer's
word write to oPlayerStick also writes this adjacent byte; the future response
must adopt it rather than dropping half the native store. Ordinary Main/MODE
do not write it. PP SpinningDisc and WWZ writers remain future owners.

Six new Frame invocations exercise 0/1/128/255 through Init, Main, V-int,
projection, narrow object writes and actor/shared restore replay, plus invalid
byte bounds. All existing constructors now supply an explicit ingress value.
No contact response or KillPlayer gameplay admission is added by this change.

The combined byte39/U3 command completed at13:07:28 BST with298 tests, zero
failures/errors/skips:78Body,88Frame,23Runtime,24Explosion,18Points,14Flower,
7pool,19touch-scan,15World,1realEGL pickup/replay and11API. All11selected XML
confirm Java25.0.5.0.2, actual supplied media and explicit surfaceless EGL.
Queue wait371s and Maven execution1m33 are separate. Source/test compilation
covered4289/4406 classes. The U3 results are for its exact concurrent source
inputs, not an implication of runtime dispatch or a full-suite certificate.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestCdPlayerBody,TestCdPlayerFrameRuntime,TestCdOpeningObjectRuntime,TestCdExplosion,TestCdPoints,TestCdFlower,TestCdObjectPool,TestCdPlayerObjectTouch,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestModApiSignatureSurface' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true test -B
```


### Pure contact aftermath bodies, 24 October 2026

Explosion18, Points1C and Flower1F execute over the existing live pool with
actual authenticated mapping/animation tables. Explosion preserves first-free
allocation and dirty bytes; a later Points child can execute this pass. The
low-byte points-frame shift, call21 animation finish, call22 Flower conversion,
Points23 rises/call24 delete-then-DrawObject, and Flower floor/growth/no-despawn
semantics match the pinned source. Deleted Points is still queued, then its zero
ID is skipped at DrawObjects. Points priority remains its allocated slot byte.
Three native aliases1C/3E/2A are added without changing secondary25/29.

The exact seven-file Opus unit passed all56actual cases (24/18/14), including
three opt-in retail methods, in the298-case byte39/U3 check above. Independent
Opus review found no P1/P2 and reconstructed the actual floor fixture through
the production helpers: distance31 above both blocks,15..1 while falling, then
-1 and angle22, landing on call9 at77F. The earlier fitted Python model is
secondary evidence, not the test oracle. All reviewed source hashes stayed exact.

Unsupported HUD/saved-flags routines remain boundaries. Saved-flags landing
commits the pool adjustment but loses its staged scratch-angle effect on throw;
that path is unreachable from admitted subtype1 badnik flowers. Dependency
exceptions can leave local per-dispatch writes unapplied. This unit therefore
does not promise complete native-prefix state on arbitrary failure. The runtime
must close instead of retrying it. World dispatch, art banking and the real
KillEnemy producer are separate integration units, not certified by these tests.
Fable's earlier handoff supplied reads/derivations only; Opus authored the code.


### Pure native enemy/hazard touch response, 24 October 2026

The pure response stages the selected enemy/hazard entry over one call-cut.
It returns final actor/player/world values, distinct changed slot images in
first-write order, and typed ordered calls, retaining neither snapshot nor pool.
It preserves native NEG/ASR, signed/unsigned compares, old-chain indexing and
odd-address boundary, unsigned score cap/life wrap, shield/combined-ring order,
hurt/kill/debug exits, bounce and the adjacent38/39 store. Result d0 is explicit
only at native MOVEQ -1 exits; Main does not consume it. No pool, audio or world
write occurs here. Body must pass the SAME snapshot object to scan and response;
selected-image equality alone cannot prove the other slots came from that cut.

Movement shares one floor-reset implementation, including the miniature branch,
while steep attachment remains separate. Ordinary MODE still rejects miniature
Sonic before work. This extraction preserves physics and the high-memory-byte
landing threshold; the shared helper's existence is not miniature admission.

Root54609 passed484cases with zero failures/errors/skips at13:31:39BST:
90response,89Movement,78Body,88Frame,12actor,4object-pass actor,85charge,
19scanner,3regionalcomponents,15World and1realEGL pickup/replay. The actual-USA
EnemyPoints method executed. All11XML confirmed Java25.0.5.0.2, actual media
and surfaceless EGL. Queue1180s is separate from1m40 Maven execution. This
bounded CD-internal extraction uses its complete maintained MODE/Body/Frame/
actor/World consumers; it does not repeat unchanged general-engine smoke.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestCdPlayerTouchResponse,TestCdPlayerMovement,TestCdPlayerBody,TestCdPlayerFrameRuntime,TestCdPlayableSonic,TestCdPlayableSonicObjectPass,TestCdPlayerCharge,TestCdPlayerObjectTouch,TestCdPlayerRetailComponents,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true test -B
```

Independent Opus review found no P1/P2 or wrong assertions in all179new-unit
invocations. Correction to authoring prose: SPECIAL selector0B can reach
Player_TouchHazard2; MechaBlu/bosses and spikes/time-over have related entries.
They remain unowned here, not unreachable. Expose existing hurt/kill routines
narrowly when those callers land rather than duplicate them. Native SubCPUCmd
blocks on hardware; this pure ordered request has no measured service cost.
Body/Frame staging, world score/contact globals, live aftermath dispatch,
routine4 recovery, lost rings11 and same-frame death-camera freeze remain
separate required units. Nothing in this commit admits contact into gameplay.


### Live contact-aftermath dispatch and picture banks, 24 October 2026

The existing physical object pass now dispatches Explosion18, Points1C and
Flower1F through the same pool/queue as the earlier objects. Points allocation
retains same-pass later-slot and next-pass earlier-slot behavior. The deleted
Points draw is queued without testing ID, then skipped by ID at SAT drain.
Flower's final scratch-angle request is applied once before the next slot;
no intervening animation/draw code reads that global. Its subtype2 low-priority
Init path still ends at the later06D7 overwrite. Immutable source tables join
at23F088/20AC64/235EF4, and the eight-span cold DEV picture covers313patterns.
The old three/five-span policies and the checkpoint-art gap remain strict.
Presenter reset/retirement and actual EGL expectations use the same313extent.

Root33585 passed315cases/0failures/errors/skips over12classes at13:39:06BST,
including28Runtime,7mappings,11picture,56pureU3,88Frame,15World,1actualEGL,
3regionalassets and11API (plus95Movement in the concurrent helper unit).
All12XML verify Java25.0.5.0.2, supplied media and explicit EGL. Queue247s,
execution1m34. Root43304 subsequently passed264affected Body/Frame/Runtime/
actor/World/EGL/API cases,0skips. These overlapping selections are not summed.
Independent Opus source review found noP1/P2 or wrong assertion in this unit.
The Flower terrain fixture was independently derived from the pinned layout,
not fitted: the probe finds two nonsolid blocks, distance31/angle0, and moves
720to722 inside the actual70B camera.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestCdPlayerMovement,TestCdOpeningObjectRuntime,TestCdObjectMappings,TestCdObjectPresentation,TestCdExplosion,TestCdPoints,TestCdFlower,TestCdPlayerFrameRuntime,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestCdR11aObjectRetailAssets,TestModApiSignatureSurface' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true test -B
```

The new slots are constructed transaction/device inputs, not a normal gameplay
route to a kill. The existing real EGL route still proves the first Ring only.
If a later allocation reuses a previously deleted/queued slot before its Init,
the bounded mapping resolver can stop at pointer0 where native would read
unowned memory. Preserve this shipped dirty-slot behavior as an explicit
coverage boundary; do not hide it with an ID/visibility or active-list filter.
Contact production, world score, hurt/death and complete USA remain separate.


### Body and Frame contact staging, 24 October 2026

Body now accepts an explicit ephemeral response context alongside the same
scanner/call-cut snapshot. The legacy two-argument context has no response owner;
items/no-overlap stay unchanged and handlers still reject without one. Enemy/
hazard response uses post-Display hurt and actual byte39, cursor invincibility,
scoreChain/debug/mini values, and declared Runtime-owned contact/world values.
Final actor/tile/priority/hurt/byte39/invincible/chain are adopted once before
SpecialChunks. One immutable response effect carries the final slot/world/contact
values; there is no pool write or duplicated global owner in Body/Frame captures.
Frame extracts typed FM/subCPU requests in call order before committing the whole
Body result. Display's queue cut remains before touch; final pose uses later state.

Root43304 passed264cases/0failures/errors/skips at13:51:10BST:100Body,93Frame,
28Runtime,12actor,4object-pass,15World,1actualEGL and11API. All8XML confirm
Java25.0.5.0.2, supplied media and explicit EGL. Queue467s/execution1m40.
All27newinvocations ran. Independent Opus review found noP1/P2 or wrong
assertion, and confirmed unchanged captured partitions and same-snapshot input.
It records weak assertions rather than treating them as proof of retention.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestCdPlayerBody,TestCdPlayerFrameRuntime,TestCdOpeningObjectRuntime,TestCdPlayableSonic,TestCdPlayableSonicObjectPass,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestModApiSignatureSurface' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true test -B
```

Runtime still must consume ObjectTouchResponse, prevalidate every changed slot,
commit world/contact globals once and capture them. Until U5, routine>=6 must
close before the death-camera/scroll branch; then the actor's final priority
projection cannot mislabel that death frame's earlier queue bucket. The native
three-slot FM drop policy and blocking subCPU timing remain unimplemented.
TerrainPlayerLoad/Boot are additional maintained consumers for the next combined
integration check. This commit alone admits no complete contact gameplay.


### Shared CD collision call cuts for Hurt, 24 October 2026

CdPlayerMovement exposes the existing native air-collision and LevelBound calls
without MODE admission, input, charge, motion integration or a new terrain/math
algorithm. The air call preserves routine4 and returns the actual scratch angle
and typed effects. LevelBound retains its signed word comparison, distinct from
HurtChkLand's earlier unsigned comparison, and stops before KillPlayer. Its
separate bored2B bottom branch throws only at that native predicate
(Sonic/Main.asm:1703-1707), because native sets routine6 directly there.

The direct landing regression stores0138 so its high-memory-byte threshold-9
rejects two-10 sensors; a wrong low-byte threshold-64 would land. The inside-bound
bored case remains admitted. All96 Movement invocations passed in Root81731's
406-case selection (one unrelated missing-S2-path skip was subsequently executed
alone); exact fresh Java25/media/EGL XML was inspected. Earlier315-case helper
coverage and the independent Opus source review found noP1/P2 or wrong assertion.
These are bounded CD call ports, not routine4 or death gameplay admission.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestCdOpeningObjectRuntime,TestCdObjectPool,TestCdPlayerMovement,TestCdPlayerBody,TestCdPlayerFrameRuntime,TestCdPlayableSonic,TestCdPlayableSonicObjectPass,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestSonicCdTerrainPlayerLoad,TestSonicCdTerrainPlayerBoot,TestModApiSignatureSurface' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true test
```


### World contact transaction and first actual enemy route, 24 October 2026

The opening Runtime constructs the actual asset-bound response once and passes
the SAME ephemeral pool capture through scanner, pure handler and unchanged-slot
compare-and-set. All new slot images and score/contact/ring values are validated
before writes; actor/invincible/chain values already belong to the final Body
and are not replayed from its earlier touch cut. The Runtime exclusively owns
contact(timeWarp/shield/combineRing), score/next-life native longs and HUD-score,
and captures/restores them once beside the existing ring/placement/SAT partition.
Module contact0/HUD-score0 are explicit DEV policy; score0/next5000/lives3 also
match first-load native reset writers. There is no retail title-entry claim.

Zero-ring contact death terminates at DEAD_CAMERA_FREEZE before effects/render,
because native's after-RunObjects death camera branch is still unowned. Ring-
bearing hurt reaches the actual unowned11 live dispatch rather than suppressing
it. Hurt4/death6/8, absolute native entry timing and complete USA remain open.

The first fixed controller-only real-World EGL proposal succeeded unchanged:
`2 -;80 R;repeat 14 {8 R+B;7 R}`. Call152 defeats first Mosqui(slot34), score0to10;
call160 visibly runs explosion/points; victim is Flower1F by178(off-screen in the
final picture). The checkpoint at82 and96-row continuation reproduce every
captured partition and final framebuffer through both full and course restore.
Terrain outside displayed sprites agrees with the completed-scroll control.
No native rows/clock/position/slot data feed this route. Durable pictures, input
and observations live externally in Downloads/Sonic-CD/native/engine-contact-
world-r1 and-r2; four PNGs are byte-identical between captures.

Root81731:406selected/0fail/0error/1skip at14:25:22BST;405executed passes,
Java25/media/EGL confirmed in12freshXML. Queue344s/execution2m09. The missing
S2-path cartridge-to-CD handoff case was then run ALONE with existing absolute
REV01 ROM verified CRC7B905383/SHA1 8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9:
Root11040 passed1/0/0/0 at14:27:33BST,21squeue/29.463sexecution. Thus all406
selected cases executed across the two invocations; do not sum the overlapping
historical selections as unique coverage. Independent Opus source review found
noP1/P2 or wrong literal in the seven-file world and two-file helper candidates.

Root strengthened W1 by restoring pre-contact score/life/shield values in the
constructed tests, W2 by checking empty terminal-death sound publication, and
W4 by checking the exact kill+26 continuation. Both rendered replay lanes now
draw/check each row; session/banks close before graphics/context; each capture
has its own correct CSV header and outcome. Root88628 passed35cases/0failures/
errors/skips at14:51:23BST (Runtime33/EGL2), Java25/media/EGL verified, queue458s/
execution32.288s. Production was unchanged; the concurrent pure KillPlayer call
extraction was separately validated by336affected cases, and remains a separate
source unit. No complete ordinary-smoke/full-suite/native-pixel parity claim.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestCdOpeningObjectRuntime,TestSonicCdOpeningWorldEgl' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true test
```


### Independent native contact controls and retained tooling, 24 October 2026

Promoted the bounded controller-only native Mosqui kill/hurt producer, probe,
checker, recipes, secondary corroborator and lossless picture converter. All
producer/probe/checker/recipe bytes equal the independently reviewed R2 freeze;
only the installed-layout test changes to assert sys.path preservation for both
an absent and already-present dependency path. Unittest discovery legitimately
adds the same tools directory before loading tests. The first promoted-path
run exposed that test assumption; after the bounded test-only correction all40
controls pass. Root also corrected a patch-helper naming error during that test
edit. No native process or tool algorithm changed during promotion.

The original R1 duplicate artifacts were hard-linked after validation and cannot
reprove independent repeats. R2 runs six fresh sequential fixed controls with
50evidencefiles/50inodes per route,64comparisonpairs,18786totalFrameAdvancecalls
(no discovery/newrecipe). Checkpoint equality and complete end-state comparison
are enforced; the imports are hash-pinned and media/research/explicit earlier
roots protected before scenario/preparation/output work. Independent source/
evidence review confirmed all four originalP2 fixes and byte-reproduced the
117-file inventory af804c0b…f60e, both event reports, and19secondary constant
comparisons. Its report-only unsafe recomputation command was corrected to a
read-only inventory exclusion check; no evidence file was edited. Root observed
40/40external and40/40promoted-path Python checks, not new native captures.

Native kill full raw difference is0; native hurt retains31unmasked differing
bytes in every mode, still unattributed. Eight offsets overlap the historical
player-referenceR1 ten-byte specimen; this does not prove a common cause. The
five V-int mod4 bounce matches are consistency only, not phase authority.
Control player/contact/dynamic placeholder categories are NOTOBSERVED, not
passes. The corroborator transcribes static expectations; it is not an independent
source reader. The tools produce comparison data only and never hydrate engine
state or grant timing/gameplay authority. Raw media/captures remain external.

```bash
python3 -I -B -m unittest discover -s tools/soniccd -p test_contact_reference.py
```


### Shared native KillPlayer call, 24 October 2026

The selected-touch cursor delegates to the same public pure KillPlayer call
that Hurt can invoke. It needs actual Actor/invincible/debug-word/scoreChain/mini
values, no fabricated selected object, world record, table or pool. Routine6
precedes ResetOnFloor; the post-reset Y writes both stick bytes; the ordered
reset/stop/Hurt calls and all unwritten fields match the former inline owner.
The full-word debug branch returns unchanged. There is no new death admission.
Root87184 passed336affected cases (99response,100Body,93Frame,33Runtime,11API),
0failures/errors/skips, including the actualUSAEnemyPoints view. AllfreshXML
Java25/media verified. Independent Opus review found noP1/P2 and confirmed
all9newliteral invocations. The report's LevelBound citation is1705, not1704;
the bored2B branch remains a distinct typed boundary in the collision port.


### Lost-ring body and spill globals, 24 October 2026

ObjLostRing11 owns all five routine entries and preserves native live allocation,
dirty routine addition, the zero-ring96-slot quirk, signed velocity word carry,
old-coordinate integration and the conditional floor/bounce/global-angle order.
It consumes explicit raw native V-int bits plus the physical RunObjectsD7,
never an engine counter or comparison row. Collect binds the unchanged existing
CdRing.collectRing through a package-only modifier seam; no duplicate reward
algorithm or saved-bank owner is introduced. Call/child/delete metadata is not
replayed by the caller. The separate immutable spill record owns only timer,
accumulator and frame; its native tail wraps as byte/word operations.

Root39416 executed79lost-ring cases (including bothactualUSAmedia methods and
2production reward bindings),21spill and26existingRing, all0failures/errors/
skips. Its one unrelated new clock-test wrapper-equality assertion failed and
was fixed separately; the233-case selection is not described as a233pass.
AllfreshXMLJava25/media confirmed. Independent Opus source review re-derived
all48velocity rows and actual floor probes, found noP1/P2 in these bodies/tests,
and confirmed d7 survives the native callees. MMZ and the dirty standing-object
branch remain explicit finite boundaries, and native phase is uncertified.
These device cases are not yet a normal-input hurt/landing gameplay claim.


### Explicit development V-int seed, 24 October 2026

The new external-touch factory overload accepts a declared unsigned native
longword seed at the admitted cut. The original factories keep an unknown seed.
No delta, engine frame or comparison observation substitutes for unknown time.
Only the existing service increments and shared snapshot restores the clock;
the getter exposes its immutable value and creates no second clock owner.
Cold actor, controls, art and cue state remain unchanged by a seed declaration.

Root39416 found one authored test error: record equality compared identity-based
CdAssetBytes wrappers between two separate runtimes. The test now compares both
complete buffers and scalar fields, with production untouched. The exactfailed
case passedalone in Root38507,1/0/0/0 at15:14:37BST (190squeue/26.846sexecution).
All96Frame cases subsequently passed in Root98350's149-case liveRuntime/World/
actualEGL selection at15:31:15BST,0skips; allfreshXMLJava25/media/EGL verified.
Independent Opus review found only that same testP2; source behavior is sound.
The eventual module's seed0 is an explicit DEV policy, not retail title/fade or
native phase proof. Hurt's additive Frame changes are a separate next unit.


### Live lost-ring frame composition, 24 October 2026

The physical ID11 dispatch now invokes the source-owned LostRing with the
Frame's declared raw absolute V-int counter, d7=127-whole, actual zone and bottom
bound. Unknown absolute time stops at the live binding before the body, never
substituting a delta or engine clock. The opening module explicitly selects DEV
seed0; terrain-only admission remains unknown. This is not retail entry-phase
or mid-pass lag timing certification. One runtime store owns spill bytes,
capture/restore and the once-per-unpaused-loop tail after Ring animation.
Shared rewards and ordered sound/angle effects are applied once; allocation,
call and delete observations are not replayed.

Root98350 passed149affected cases with actual media/EGL, no failures/errors/
skips. Root95177 then passed38Runtime cases including two review regressions:
a seed1/slot42 probe distinguishes absolute time from delta/engine frames and
requires exactly one angle write; an FE/FF/0 spill capture is restored and its
next tail replays to FD/1FD/0. Exact reward sound order and paused-clock advance
are now checked. World captures include the spill. Independent Opus source
review found noP1/P2. CdObjectState.signedWord is a real short-returning helper;
the earlier brief's contrary premise was wrong, not a production defect.

These checks remain bounded. Root95177 selected532cases at8314b20f6 plus eleven
pending source/test files:531passed, no skips, while the new walking input
proposal missed contact and reached the existing unowned FloatBlock12. This is
not described as a532pass. Hurt admission and its final controller route are
recorded in the next checkpoint, not inferred from device cases.


### Hurt recovery and real ring-spill route, 24 October 2026

Routine4 now follows ObjSonic_Hurt source order: old-velocity ObjMove, word
$30 gravity (underwater subtracts$20), PP special collision, unsigned HurtChkLand,
signed LevelBound, history, animation and unconditional DrawObject. Both bottom
calls use the same pure KillPlayer owner and return into Hurt. Main's Display,
timer service, input copy, MODE, touch and special chunks are absent. Landing
stops all three velocities, returns routine2 and writes hurt120; next Main
resumes at119. Standing-object movement remains a named finite dependency.
Actor queueing accepts the complete Hurt draw cut and the Frame preserves
ordered collision/KillPlayer sounds. Both Root clock ports and all Hurt changes
were composed; capture/restore retains both. Independent Opus source review of
original and composite6files found noP1/P2 or wrong assertion. Its prerequisite
KillPlayer port is committed in d40cb962c. Test comments now describe authorship
without the historical UNRUN label; frozen mirrors/reports remain unchanged.

Root95177,8314b20f6 plus eleven pending files, finished15:53:14BST with532tests,
0assertion failures,1error,0skips (43squeue,2m03execution). All12freshXMLs confirm
Java25.0.5.0.2, actual CD media/EGL and the existing S2REV01 property. Body120,
Frame99, Actor15, shared actor4, TouchResponse99, Movement96, Runtime38,
WorldLoad15, TerrainLoad24, TerrainBoot8, API11 and the two existing EGL routes
passed. Only the new walking proposal missed contact and reached known ID12.
No production state or gate was altered to force an encounter.

Rejected controller proposals are preserved externally: continuous Right
passed the Mosqui; coast/rest/Right restarted too quickly to meet its dive.
One observation-only rerun recorded the full patrol toX751, dive selection at
call439/X696, groundwait473 while Sonic had already reachedX797. It justified
waiting for the returning patrol and then a short walking approach. No fitted
physics, guest writes, player/object fixtures or production frame fences were
introduced. Final fixed input is `2 -;108 R;500 -;100 R`.

Root68427 ran ONLY the revised actual EGL method, passed1/0/0/0 at16:00:46BST
(0squeue,31.780sexecution), with freshXML Java25/media/EGL confirmed. Natural
hit644 had2rings: routine4, anim1A, hurt120, Xvelocity-$200/Yvelocity-$400,
rings0 and two real LostRing objects. Landing686 resets speeds/routine/timer;
Main687 resumes hurt119. From the call82 full/course checkpoints, each replay
matches605successive captured rows and finalpixels; each replay row is rendered.
The comparator covers actor, shared environment/art/clock, live slots/savedbank,
spill/ring/contact/score globals, CPU SAT/display, camera/scroll, logical artbank
and level clock. Terrain outside sprite pixels agrees with completed scroll.
Five actual PNGs plus fixed input/CSV/outcome remain external; selected pictures
were visually inspected. This is actual OpenGGF DEV evidence, not native timing,
FM playback, pixel/full-game or regional parity. The module remains silent and
uses explicit art preload/DEVseed0. Routines6/8, MODE kill continuations and the
same-frame death camera boundary are the next owners.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestCdPlayerBody,TestCdPlayerFrameRuntime,TestCdPlayableSonic,TestCdPlayableSonicObjectPass,TestCdPlayerTouchResponse,TestCdPlayerMovement,TestCdOpeningObjectRuntime,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestSonicCdTerrainPlayerLoad,TestSonicCdTerrainPlayerBoot,TestModApiSignatureSurface' "-Dsonic2.rom.path=$S2_REV01" "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true test
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestSonicCdOpeningWorldEgl#actualEglWalkingHitSpillHurtLandingAndFullCourseReplay' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true test
```


### Diving contact and retained-native observations, 24 October 2026

The actual EGL hurt test now shares one route owner for two real contacts:
grounded Mosqui8/two rings and diving Mosqui6/three rings. Native's firstRight
takes effect at level198; the earlier engine walk started at3 and met another
patrol phase. A neutral controller lead-in (`197 -;108 R;252 -;153 R`) aligns only
the input origin; no native gameplay value, clock seed or source gate changes.
The diving route hits600, lands643 and resumes119on644, matching the retained
native event numbers. These are comparison observations, never frame fences.

Root2436 at3f3d13a06 plus ONLY the changed testcase passed bothaffected methods,
2/0/0/0 at16:16:14BST (37squeue,35.040sexecution), freshXMLJava25/media/EGL
confirmed. New full/course checkpoints82 each replay562rendered rows and
finalpixels. Existing grounded605row route passes after helper extraction too.
No production delta. Original Ring/defeat methods do not use the changed helper
and were not repeated. New5actualPNGs/input/CSV/outcome remain external.

Read-only in-memory join to all overlapping accepted native forward polls uses
actual globals.levelFrames, no fitted ordinal shift or skipped row. At54unique
levels591..644, all23shared fields other than absoluteVInt match exactly:
fullXY, signedXY/Gvel, routine/flags/animation, hurt/rings/lostRingcount, three
spill values, cameraXYwords, heldinput/action and firstMosqui id/routine/XY/
spriteflags. That is1242exact scalar comparisons. The24th field, absoluteVInt,
differs onall54rows by61(native higher):54mismatches out of1296comparisons,
explicitly reported and not applied to gameplay. This verifies only these short
observed fields, not all actor state, device timing, native pixels/audio, later
bounces, whole-game or regional parity. DEVseed0 remains declared policy.


### Pure dead-camera bound write, 24 October 2026

CdR11aForegroundScroll.deadCameraFreeze models only Level_MainLoop's two word
writes after all object slots (Level/Main.asm:405-418). Both bottom bounds take
the current cameraY high word. All camera fractions, cross bytes, differences,
flags, shift/lock/left state, bounds left/right/bossFight and Y-centre remain
unchanged. It runs no LevelScroll/event path and does not select the branch;
levelRestart/debug/routine admission and SAT/tail completion remain caller-owned.
The helper is not yet wired into the World. The audited current early post-Body
death stop is still present until that integration unit lands.

Root56397 atf03955e65 plus ONLY the two camera files passed61/0/0/0 at16:29:52BST,
0queue/1m29execution, freshXMLJava25 confirmed. Five new invocations cover every
unchanged field, the fractional camera high word, signed-boundary raw words,
null owners and word-centre validation. Existing56foreground cases also pass.
This pure value unit does not prove a completed death frame or restart route.


### Powerup/Game Over raw art service, 24 October 2026

The new pure CdPowerupArtRuntime models LoadShieldArt (Powerup/Main.asm:157-186):
BCLR7 gates the lookup, byte SUBQ2/word double selects one of five registered
raw copies, DBF copies exactly1024bytes into complete caller-supplied CPU RAM,
and a1152byte DMA replaces the whole resident window atA880. The trailing128CPU
bytes are preserved, including nonzero carry; no source padding or defaultRAM
is invented. Inactive bit7 skips the lookup for anylowbyte, while unboundactive
pointer offsets stop before a Result. Powerup remains external, returned once
withbit7cleared; the component holds no mutableglobal/clock/capturebank. State
compares immutable bytes bycontent. Caller still owns actualVInt admission,
logical/global commit and DMA publication; draw/restore cannot resubmit it.

Root41425 at9694cefe1 plus these2newfiles passed20/0/0/0 at16:44:06BST
(100squeue,1m30execution), fresh2XMLJava25confirmed:18source-literal service
cases and2existingraw assetdecoder cases. Fivecopies, request-bit behavior,
bounded invalidselectors, retainedtail/fullupload, immutablebytes and replay
are covered with constructed format input. No media identity, native timing,
GPUupload or GameOver world support is inferred. Runtime/atlas/snapshot wiring
and the349-pattern C2picture remain next integration work.


### Explicit death/reset state ownership, 24 October 2026

The player Timers record now requires the actual reset word3A in addition to
hurt/invincibility/shoes. There is no default three-word overload. Every
constructor is migrated: cold0 is sourceClearObjects, existing state preserves
its word, and Body cursor/capture/projection retain it. KillPlayer's adjacent
word38 does not overwrite3A. RESET_TIMER is reserved for its native writers.
The new nine-field CdLevelGlobals has one Runtime store and private-owned
capture/validation/restore. DEVmodule supplies every field explicitly; HUD-time0
is the existing omission, not the native1. The flowerCount24-bit value stores
three independent per-time-zone wrapping bytes; no writer may carry across
zones. Clear-long also spans the adjacent fade byte. No state writer or runtime
6/8 context is admitted by this shape-only unit.

Root56302 at746c3e192 plus14exactpendingB0/MODE files passed576/0/0/0 at17:11:28BST
(760squeue,2m14execution). All13freshXMLJava25.0.5.0.2/actualmedia/EGL/existingS2
ROM confirmed:11LevelGlobals,128Body,103Frame,15Actor,4sharedActor,99TouchResponse,
115Movement,39Runtime,15WorldLoad,4EGL,24TerrainLoad,8TerrainBoot,11API. The24new
B0cases include nonzero timer retention through Init/Main/Hurt/contact/Kill,
Frame ports/replay, native widths and explicit Runtime ingress. Changed-value
global restore still awaits a real writer; no setter was invented. Independent
Opus review found noP1/P2 or wrong assertion in all12frozenfiles. Its packing
clarification was added as comment only after execution; frozeninputs unchanged.


### Native MODE return after KillPlayer — 24 October 2026

The four source-owned MODE tails now share one implementation between the
ordinary path and `resumeAfterLevelBound`. The resume consumes the stopped
LevelBound result and caller-returned state, validates fields the shared
KillPlayer/ResetOnFloor routines cannot write, and returns tail-only effects.
It neither re-dispatches from routine 6 nor repeats the MODE prefix. Body
composition is still pending; the pure helper does not itself admit pit deaths.

The same completed Root 56302 selection recorded above executed all 115
Movement invocations, including 19 new continuation cases, with no failure,
error or skip. The independent source review found no P1/P2. Constructed
terrain coverage is not a World/native timing or pixel-parity claim.
The frozen handoff's air-resume prose should read Y $07EA.8000; its tests
already preserve that fraction. Some selected global angle buffers remain
unchanged, as the assertions record.


### Pure Game/Time Over owner — 24 October 2026

`CdGameOver` now ports the source-owned $3B Init/Main stores, live allocation,
child ordering and typed sub-CPU requests. The separate authenticated lazy
56-byte mapping view retains its two relative table bases. No World dispatch,
resident powerup service, text renderer, audio or restart is claimed here.

Root44257 at382ef3e6e plus the six exact candidate files and a tests-only
World regression executed29 GameOver,14 constructed asset and3 regional
retail cases successfully, zero skips. Both new World rows independently
rejected at the old DEAD_CAMERA_FREEZE, the intended negative control.
The complete request was48/0 failures/2 errors/0 skips, not a green48 run.
Java25.0.5.0.2/media and all four fresh XML were inspected. Independent
Opus source review found no P1/P2. Only the view is observed while media is
open; the new fromObjectAssets calls run after close. JP's identical blob
is elsewhere; no regional GameOver owner or offset inference is admitted.


### Native player death fall, restart delay and Main return — 24 October 2026

The pure Body now runs routines6/8 from an ephemeral caller-supplied LevelContext.
It publishes a staged lives/HUD/GameOver-slot summary, full-pool alias metadata,
the restart saved-flags request and the typed relative Z80/FADECDA requests.
No pool/global/driver operation is replayed inside the Body. Main calls the
shared KillPlayer once and resumes the original movement tail, then completes
its remaining source calls. Frame contexts are not retained; Dead draws and
Restart does not. World commit and actual level reconstruction remain separate.

Root74991 atab8804a6a plus the six exact Body files and four camera files
executed542checks:541passed and one existing Runtime assertion was accidentally
changed by Root's broad test-text edit. No production defect caused that failure.
Restoring only the old ChargeSound expectation gave Root82194:40/0/0/0 for
the entire Runtime class. The other502cases passed in74991, including Body195,
Frame106, Actor18, Movement115 and actual EGL/restore consumers. All selected
XML, Java25/media/EGL and zero skips were inspected; this is not a single
combined542green run. Independent Body source review is pending at this commit.
The routine6/8 source and tests are byte-identical to the frozen Opus handoff.


### Selector-eight level-art source cut — 24 October 2026

The Frame accepts one ephemeral level-art call after Sonic DPLC/DMA and before
VInt_Finish, clock increment and Start toggle. Ordinary and normal-pause service
call it even when no Sonic DMA was pending; PP lag never calls it. The callback
is not stored. Failure keeps completed controller/player-art writes, closes
admission, and skips Finish, clock and Body entitlement. Existing terrain DEV
retains its explicitly omitted level-art owner through the original overload.

Root17600 at276cf1b6e plus the two exact port files and four camera files:
`maven_queue.py --lean -Dmse=off -Dtest=TestCdPlayerFrameRuntime test`, with
DISPLAY/WAYLAND_DISPLAY unset and Lua5.4.112/0/0/0, Java25.0.5.0.2 fresh XML
inspected, no queue wait,87s execution. Six new cases pin selector8/pause/lag
order, dirty Sonic DMA, no-DMA service, pre-Finish failure and null preflight.
This is the call port only; the World's powerup-state/byte/service binding
and real hardware timing remain pending.


### World death-camera cut — 24 October 2026

The World now checks levelRestart after every object slot and before scroll.
With debug off and the final player routine at least6, both bottom words take
the current cameraY while the camera/scroll state is kept. Sprite construction
and the ordinary loop tail still execute. The former post-player rejection
is removed. A pending actual restart remains a typed stop before scroll/SAT.
HUD-time input comes from the single level store; terrain DEV retains0.

Two constructed actor/slot cases through the actual World distinguish Main's
pre-contact draw bucket2 from Hurt's post-kill bucket0, run a later live Mosqui,
freeze bottom/camera, execute the ring tail and restore/replay the whole cut.
The original production rejected both (Root44257 negative control). They passed
inRoot74991 with the other502non-Runtime cases, including actual EGL routes.
Root's unrelated old Ring sound assertion edit was restored exactly in R2;
all40Runtime cases then passed in82194. Independent review's sole finding
was that same resolved test literal; no unaddressed P1/P2 remains. It was a
static review, not execution. No natural-input death or native parity is claimed.


### World-owned death and restart summaries — 24 October 2026

The Runtime passes the same pool snapshot to touch and level contexts. After
the complete Body succeeds, one validated summary commits lives/HUD and the
ID-only Game Over slot image, or resets the actual saved-object bank and
accepts the restart level record. All immutable values, allocation, owner,
image and alias checks precede the first pool write. Full-pool $FFF000/$3B
remains typed Body metadata, with no invented pool slot or host RAM buffer.
Final actor values and descriptive LevelWrite/call effects are not replayed.

Tests-first Root70992 hit the intended missing-context boundaries in all three
new cases (two DEAD_ROUTINE, one RESTART_ROUTINE). After the production change,
Root92167 passed all60affected checks:43Runtime+17actualWorldLoad, zero failures,
errors or skips. Java25.0.5.0.2 and supplied-media properties were inspected in
both fresh XML reports. Queue366s, execution86s; finished18:03:49BST. Independent
source review is pending. The later live $3B dispatch and actual LevelStart
reconstruction remain separate; this does not claim a completed death route.


### GAME/TIME OVER mapping and resident pixels — 24 October 2026

The immutable resolver now registers two two-frame roots over the unchanged
authenticated56-byte include. A separate nine-span DEV pixel policy consumes
the complete now-serviced36-tile powerup resident window, including explicit
RAM tail, alongside the prior eight sources. No service or default RAM is
hidden in the converter; the Runtime's shared byte/state binding is next.

Root23905 at1dca98b36 plus the four exact picture files passed83cases:
Mappings9, Presentation14, Runtime43 and WorldLoad17, zero failures/errors/skips.
Java25.0.5.0.2/media were verified in all four fresh XML, including execution of
actualUsaGameOverRootsAndNowServicedPowerupWindowDrawOnlyTheirRetailPieces.
Queue16s, execution88s; finished18:09:59BST. The independent source review
found no P1/P2. This proves bounded mappings/pixel-source behavior, not actual
Game Over world rendering, lifecycle restart, native DMA or hardware pixels.

Subsequent independent Body and world-summary/level-art reviews also found no
P1/P2. Their supplied execution results remain Root's observations; their
static source reasoning does not count as another run. The unreachable killed
ceiling/tunnel stop remains an advisory for the later roll-tunnel owner.


### Live Game Over and powerup art ownership — 24 October 2026

The Runtime now dispatches $3B in the same physical object walk. Lives are read
from the Ring store; powerup and timeOver are read and updated through the one
level-global store. Only typed sub-CPU requests enter the ordered sound ledger;
committed pool/global/call metadata is never replayed. One complete immutable
powerup CPU/resident state is captured, validated and restored with the World.
The selector8 callback services it after Sonic art and before Finish, including
pause; lag retains it. Picture conversion then uses the now-serviced nine spans.
Cold zeros are justified only for the existing fresh DEV admission, not restart.

Root5086 at978ec39d8 plus the exact Runtime2/client3 candidates has fresh reports
for255checks with zero failures, errors or skips: Runtime50, Presentation14,
Mappings9, Frame112, Powerup18, GameOver29, WorldLoad17 and EGL6. All eight XML
confirm Java25.0.5.0.2, supplied media and the requested EGL properties. Queue
telemetry ended18:44:17BST with1.8s waiting and99.8s execution. The command handle
was unavailable after context recovery; the completed JUnit/queue evidence was
used without repeating validation or inventing a recovered console exit status.
The independent Runtime source review found no P1/P2 and re-derived all seven
new cases. It also checked actual Nemesis extents: Springs ends exactly at
$A880 and HUD starts at$AD00; no R11A PLC overlaps the powerup window. On an
unreachable invalid-selector failure, the pure owner publishes no partial
BCLR result, so the closed Runtime keeps its pre-call diagnostic byte; this is
not the native post-BCLR byte and no closed owner consumer may rely on it.
Actual World route/capture results belong to the following client checkpoint.


### Controller death and constructed GAME OVER in the World — 24 October 2026

The presentation now retires all349picture patterns. WorldLoad and EGL rewind
comparators include both complete powerup windows, and the pickup oracle uses
all nine now-serviced art spans. The actual controller-only death route uses
fixed ordinary input, never a position, slot, clock or reference write. It
first takes a walking hit at644, then a zero-ring second contact at968. The
death camera remains(377,1803); lives fall3→2 at1052, reset60 then56 at1056.
The newly allocated lives-remaining GameOver object deletes itself in that
same physical pass. Full and course restore each reproduce974 rendered rows
and final pixels from the pre-event cut. Actual outputs and inputs are retained
under Downloads/Sonic-CD/native/engine-death-world-r1, outside the repository.

A separate constructed scene uses three same-owner gone-line actor cuts, each
preceded by clearing ALL dynamic slots, then21neutral forward inputs. It is
explicitly not a controller-only route to zero lives. The actual World runs
the GameOver bodies and selector8 art service; EGL renders32retail glyph tiles.
Both rewind modes replay the21rendered rows and final picture. Retirement then
removes the glyph atlas entries beyond the prior313-pattern bank. A terrain-only
absence control differs from the rendered GAME OVER image.

Root5086's255affected checks recorded in the preceding section include both
new EGL cases, the four maintained EGL routes and lifecycle consumers, without
skips. Independent client review found no logic defect but two evidence P2s:
PASS was written before cleanup assertions and labels omitted the dynamic-slot
clears. R2 keeps all R1 logic and moves PASS after successful retirement, with
complete construction labels. The R1 JUnit run did pass all cleanup assertions;
its original outcome ordering is historical rather than proof on its own.

Root4338 deliberately restored cleanup313:1test/1failure/0errors/0skips at
19:02:16BST, specifically first added glyph131385 still present; no outcome.txt
was written. Root6117 was an invalid positive attempt: copy2 preserved an old
source timestamp, Maven reused the negative bytecode and produced the same
failure with no outcome. After advancing the restored349source timestamp,
Root46132 actually recompiled and passed1/0/0/0, exit0 at19:09:51BST. Fresh XML
confirm Java25/media/EGL, and the new PASS follows all cleanup checks. Its GAME
image is byte-identical to the original. This does not add three passes to255.
The narrow repair did not change production or other test behavior; no broad
rerun was duplicated. Both review P2s are resolved.

These are silent DEV entries with explicit art preload and V-int seed0.
Native LevelStart fades/title/HUD/audio/PLC timing, actual reconstruction,
zero-lives program exit, pixel parity and the complete game remain open.

### Shared SolidObject probe ports — 24 October 2026

The CD movement owner now exposes CalcRoomInFront, CheckFloor and GetCeilDist
as thin calls into its existing private probes. Each returns the native signed
distance and optional primary/secondary angle-buffer stores, including stores
that preserve the old value or precede a zero-height fallback. Room seeds both
buffers before probing. A private probe-only cursor has no Environment/input;
unwritten scratch values never escape it. MODE continues to use the same probes
and final-value behavior. Source is Collision Check.asm:14-65,79-125,298-314 at
the existing CD pin; no collision algorithm or route-specific correction is added.

Root26637 tested the exact two-file port at7b758b364 plus those changes:
211tests/0failures/0errors/0skips, Java25, comprising Movement123, Ground54,
Terrain20 and Math14. All eight new constructed probe cases executed. The later
affected533case selection also ran the new88case pure SolidObject consumer;
that consumer remains a separate unit. Independent source review found no
P1/P2. Its optional suggestions concern direct ProbeResult validation coverage
and a room probe whose stored angle differs from its seed; no incorrect
expectation or production defect was found. No World standing/pushing or
Floating Block dispatch is admitted by these ports. Full-USA and regional
completion remain open.

### Authenticated Floating Block data — 24 October 2026

The USA object asset owner adds two lazy views: Ani_FloatBlock at$A3DC/30 and
MapSpr_FloatBlock at$A3FA/76. The existing strict decoder accepts six frames,
three scripts and15tiles without another format seam. A separate lazy art bank
binds ART_FLOAT_BLOCK to PLC_Std row2, VRAM$6680, tile$334..$342/baseword$4334.
The source blob is230bytes; the Java bit reader consumes231realbytes, including
the first$00byte of the adjacent SpringWheel blob at$366B6. No padding is
invented. Selector5/subtype2zero is newly admitted; selector0 is unchanged and
all other consumer combinations still reject. The original nine eager tables,
two initial art decodes and existing three art banks remain unchanged.

Root73022 passed34/0/0/0 with Java25 and supplied media: Visual13, constructed
Assets18 and all three regional retail invocations. The USA first lazy decode
after source closure executes; Japan/Europe remain explicitly unadmitted.
Independent source review found noP1/P2 and re-derived the three real mapping
alignment bytes, table/PLC operands and reader adjacency. This authenticates
data only. The Floating Block body, live mapping/art presentation, solid-player
state and world dispatch are separate work, not an act-completion claim.

### Carried restart and native restore admission — 24 October 2026

The opening World now ends the source LevelStart cut after every object slot,
without scrolling, rebuilding SAT or running the Ring/spill tail. The old PLC
token drains on its own owners, and the cut remains capturable. The next
beforeFrame builds an explicit native carry and invokes the existing level
load owner before admitting the new loop. Lives, score/next-life, saved object
bank, history, Sonic art, debug scratch and the declared raw-held latch carry;
the source-cleared fields use explicit ingress. Powerup CPU RAM is cleared and
its resident window carries. The load omits audio reset/configuration, retires
the old player/Frame/PLC/presentation owners, creates a new visit and emits one
LEVEL_LOAD rewind boundary. Lives0 and unowned restart paths stop typed.

Root69093 compiled and ran328cases:4failures,0errors,1skip. Two new assertions
incorrectly compared CdObjectState wrappers from different pools. R2 compares
the physical Identity and all64bytes through the existing public copyBytes();
the independent review's claim that no such accessor exists was incorrect.
The corrected77devicecases passed in Root53753, including their previously
unreached tails. Pool owner equality was not weakened. The one skip was the
reused-boot cartridge-to-CD case, because the first request omitted the S2ROM.

The other two failures exposed a scope assumption: raw RewindRegistry captures
share their WorldSession across loadLevel, so the old camera restored before
the late CD adapter rejected its retired Frame. The original CD-only review
recommended removing these raw-restore assertions; sanctioned live/course
paths already isolate epochs. This work deliberately adds a separate shared
native preflight rather than weakening the unchanged-state checks. It does
not promise arbitrary adapter rollback or global transactional restore.

NativeRewindRestoreGuard is an engine-internal opt-in. Only an actual native
root publication admitted into this registry may validate before full/course
restore. Foreign scope and course key checks still run first. Missing guarded
payloads reject before any earlier restore/reset. Ordinary registrations gain
no authority; generic adapter order, RNG-last and callbacks stay unchanged.
The existing CD opening validator is factored into this preflight. Its normal
rejection paths preserve gameplay state; immutable-source authentication may
memoize, and already-broken lifecycle owners retain their fail-closed rules.
The terrain-only adapter remains unguarded for raw old-visit tools; sanctioned
epoch/course paths are safe, and this pre-existing residual is not claimed fixed.

The tests-first interface-only control Root53753 produced the intended five
guard failures (6guardcases), while77correctedDevicecases passed:83/5/0/0.
Root57948 then passed533/0/0/0 across24affected classes, Java25, real supplied
media, surfaceless EGL and the existing S2ROM. Native-publication authority,
registry, session, owner-bound patch, API pin/signature and reused-boot checks
all executed. Independent restart device/World/probe and shared-guard reviews
were read in full; no productionP1/P2 remains. The shared guard review's
missing-payload and validator-purity documentation advisories are clarified
without changing code behavior. Broad smoke/guards remain pending.

Actual fixed controllers reach death968, life3→2/reset60 at1052, the old cut at
1112 and the new visit at1113. Generation1→2 and frame/V-int delta1 are observed;
Sonic returns to64,$76B with two lives. Full and course restore each reproduce
54rendered new-visit rows and final pixels. Live rewind also exercises the new
epoch's frame-zero saved bank before SpawnObjects and frame-one replay. Raw
old composites now reject without mutating the new visit. Captures, complete
input/CSV and provenance are external under native/engine-restart-world-r2.

This is a DEV restart: no native fade/loading/title/HUD/audio/PLC timing,
explicit clock seed0 and raw-held/tap policy, and no timing/pixel/full-game or
regional parity claim. The complete USA game remains the goal before regions.

### Pure native SolidObject owner — 24 October 2026

CdSolidObject ports every Object Solid.asm entry at the existing pin, including
GetOff/StandOn, top/bottom/plain solidity, pushes and all four shared KillPlayer
sites. It returns final player/object/global values, native write sets, d0 and
ordered calls. It reads an ephemeral call-cut capture only for actual a2
dereferences and commits no pool, player, angle buffer or sound. Existing
movement probes and KillPlayer are reused; no physics is copied. Reserved
and out-of-pool stand/push bytes stop at actual dereference, while byte0's
player-slot alias and compare-only paths preserve native behavior.

Root57948 executed all88cases, including the supplied-USA binding, without
failure/error/skip. The independent source review found noP1/P2, re-derived
all13probe distances, all22get-off reasons and the four kill sites. Before
commit, documentation clarifies three reviewed details without changing code:
bytes192..255 wrap on the24-bit bus; stick clearing depends on GetOff guards;
and SOLID_TYPE/ROUTINE_2 are one storage byte at$25, not separate writes. The
open-air prediction test does not distinguish the killed Y velocity; stronger
row-terrain coverage remains advisory. S4 must validate pool ownership before
committing the returned object/other-slot images. World integration, the
player's support continuation and Floating Block dispatch remain separate.

### Player push/stand storage — 24 October 2026

Body State now requires ObjectLinks(pushObject,standObject), the unsigned raw
bytes at$20/$3D (_Definitions.inc:92-93). They hold whole-slot indices rather
than object IDs. All0..255 values are retained; a solid-object dereference,
not storage construction, owns reserved/out-of-pool address admission. There
is no old State overload or zero fallback. LevelStart's ClearObjects supplies
explicit cold/restart zeros; Init and all Body calls preserve supplied values.
Existing actor capture/Extra rewind owns the pair automatically. State copies,
the narrow projection/Y-velocity ports and constructed test cuts preserve them.

The first21-constructor migration missed the fully-qualified EGL call: Root87868
stopped at testCompile, exit1, with no JUnit execution. R2 adds old.objectLinks()
to that ninth file; it does not weaken the new required ingress. Root86248 then
passed486/0/0/0 in11affected classes, actual CD media/EGL/S2ROM and Java25,
exit0 at20:33:31BST (0swait,81sMaven). Body199 and Frame121 include eight new
width/null, Init/Main/Hurt, cold/restart and actor restore/replay cases. World,
render, actor and signature-pin consumers also executed. Independent Codex
review accounts for all21calls/ninefiles and finds no remainingP1/P2 after R2.
This owns the storage; solid-result application and the next supported-object
MODE continuation remain separate. No new World support or parity is claimed.

### Native Floating Block body — 24 October 2026

CdFloatBlock now owns the seven source routines, using the authenticated lazy
USA mappings/scripts and selector5 art. Each TopSolid pair publishes its working
object, captures one cut, invokes the pure SolidObject owner and synchronously
requires the commit port to publish the returned pool images. Player/globals
thread into the next call; the Future Main-to-Fall continuation makes four calls.
Visible writes animation0 before solidity, Reset makes no Solid/Draw call, and
Fall preserves the native delete-and-return wrapper behavior. Source boundaries
reject before their first write; failures after published source writes do not
roll back. There is no new player, pool or snapshot retained in its result.

Root79585 at48562c827 plus this exact two-file owner and the separate S3 Frame
port passed311/0/0/0, exit0 at20:43:50BST (0s queue,86s Maven). The selected
Frame126, Solid88, Float42 and Runtime55 reports all show Java25, actual supplied
USA media and zero skips. All42Float invocations executed, including the media
binding. The independent Codex review read every method and caller contract,
found noP1/P2 or actionableP3, and retained matching frozen fingerprints. Its
source-only verdict is separate from Root's execution. The Draw test stand-in
fuses enqueue and later culling; the real queue/live-slot path remains S4 work.
No live World dispatch, mapping/art-bank admission, supported-object MODE,
route reachability, act completion or native timing/pixel parity is claimed.

### Guarded solid player/global commit — 24 October 2026

Frame now validates and applies the final SolidObject Player/Globals against
the live value cut. Every native PlayerField and GlobalField has a projection;
integer/fraction words are checked independently, and unrepresented motion,
slot identity and undeclared changes reject. Validation is pure. Apply builds
all immutable state before assignment and adopts once into the existing actor
and shared owners. Display, history, non-hurt timers, art, clocks, input and
publication remain untouched. There is no pool, effect replay or sound work.
Value equality preserves admission; it is not a source-call identity token.

The five new component cases execute within Root79585's311/0/0/0 result above,
including pure preflight, a real StandOn/GetOff pair, both rewind partitions,
replay, all four globals, stale/undeclared writes and lifecycle rejection.
Independent source review found noP1/P2. Its nonblocking coverage advisory
is retained: direct nonzero-fraction and Solid-triggered KillPlayer port cases
are not yet present, though the implementation and separate owner paths were
checked. S4 still owns same-cut pool coherence/CAS before publication, a
synchronous apply without intervening callbacks, ordered FM forwarding and
failure closure. Next supported-object MODE is not admitted by this port.

### Live Floating Block composition and actual World pixels — 24 October 2026

The physical dynamic walk now owns ID$12. Every native TopSolid call validates
its exact same-pool cut, the complete live pool, all returned image owners and
Frame's declared player/global write sets before the first result write. Pool
CAS then Frame adoption run synchronously with no callback between. Only FM
requests enter the existing contact sound ledger; source-call metadata is not
replayed. A later slot observes earlier stand/link/angle writes and can release
the previous slot. The object's pre-call source stores remain on failure; the
queue/Frame close, with no rollback. Existing native Solid/Float owners are reused.

The byte-equal mapping join adds20A3FA's6countedframes (empty0), and a separate
preloaded Float art span adds native334..342. Older pixel policies remain intact.
LogicalBanks derives a tenth span at powerup+36 in its older constructor forms;
all ten spans validate even with emptySAT. World reservation/retirement now spans
364patterns. This remains named DEV residency, not live native sectionPLCs.

Tests-only Root46828 hit exactly the missing$12dispatch in five cases (59tests,
1failure/4errors/0skips). Root52852 then passed483/0/0/0 across16actualclasses,
exit0 at21:03:46BST (0squeue/138sMaven), actualmedia/EGL/S2ROM/Java25. A mistaken
TestApiSignaturePin selector matched nothing; the corrected separate
TestModApiSignatureSurface request88687 passed11/0/0/0, pin unchanged. Independent
Codex review found noP1/P2 and re-derived actual terrain probes, fractions,
angleC0 writes, live two-block release, exact FM order, SAT and picture ranges.

All three P3 proof/wording advisories were addressed in tests/comments, with
production unchanged: actual playerSAT uses post-objectX65/Y721 plus native
mapping offsets; GPUdifferences are required within a Float-only footprint that
excludes Sonic/other tiles; full/course replay compares all6intermediate rows'
pixels. Root68523 passed the2affectedmethods,0failure/error/skip,exit0 at21:15:17BST
(58squeue/31.225sMaven). No unchanged broad selection was repeated for that edit.

Real HeadlessGameBoot neutral controls exercise a constructed slot40Fall at
(200,740) after explicitly clearing ALL dynamic slots. Seven Fallcalls endY74E;
full/course eachreplay6rows/finalpixels and remove the nine used Float patterns
on World retirement. No player/globalsetter, normalPresent-to-FutureFall route,
standingMODE, nativeplanes/PLCs/timing/pixel/act/fullgame claim. R1/R2images/input
match byteforbyte; captures/provenance remain external under
native/engine-float-block-world-r1 and-r2. Broad smoke remains pending. The next
source dependencies are supportedplayer movement, actual rampBG and earlier
Springdispatch; neither gatesuppression nor staticBG substitutes for those owners.

### Supported player, ramp MODE and source-ordered flipper commits — 24 October 2026

Movement now borrows an invocation-local object cut for the native stand-slot
reads at jump/flipper, idle balance and ObjMove. Whole slot0 aliases the working
player; dynamic32..127 reads use real immutable pool images; reserved/outside
addresses stop at the actual read. Shared ObjMove and ObjMoveGrv preserve native
local resistance, OLD Y-velocity integration, caps and arithmetic wrapping.
Hurt and Dead reuse these moves at their existing source cuts. Ramp control
bit1 runs the native MODE branches; charge keeps bit0/routine/size/mode guards
and admits that real ramp byte without normalizing it. Other control/warp
boundaries remain. Native owners: ObjSonic modes/CheckJump/ChkFlipper
(Level/_Objects/Sonic/Main.asm:675-776,1779-1861,3112-3157) and ObjMove/ObjMoveGrv
(Level/Object Functions.asm:42-128), at reference pin1ecc5f3a.

Body, Frame and Runtime forward one borrowed context from the same post-spawn
pool snapshot as touch and level context. A flipper animation store is an
immutable before/after patch; a later touch scans and responds against the same
locally overlaid snapshot. After the whole Body succeeds, Runtime folds final
images in native effect order, validates every real owner and the full original
cut, then CAS-writes final pool images and commits external globals once.
A same-slot enemy response retains the earlier animation store. Actor/effect
metadata is never replayed; exact sounds remain Frame-owned. Captures acquire
no snapshot/context owner, and real SolidObject later detaches a jumped player.

Tests-first Movement RED40558 stopped at RAMP_ENTRY as expected. First combined
Movement287 check46048 exposed the unchanged Charge ramp guard and invalid
noncardinal atan fixture; both independently reviewed P1/P2 findings were
repaired against source. A direct Charge RED28152 reached its original gate.
Root27341 then passed376/0/0/0 (Movement202, Charge86, Ground54, Terrain20, Math14),
Java25, with strict assets unchanged. Its media-independent command selected
TestCdPlayerMovement,TestCdPlayerCharge,TestCdPlayerGroundCollision,
TestCdPlayerTerrain,TestCdPlayerMath through maven_queue.py -Dmse=off.

Combined request13247 ran1050 cases across19 actual classes on ac4e5efa1 plus
these12 Java files: one failure, no errors/skips. Selection included Body226,
Frame132, Pool12, Runtime62, Movement/Charge/terrain/math/ground, Solid88, Float42,
Actor/ObjectPass, actual WorldLoad/EGL, terrain load/boot, restart carry and
ModApi signatures; USA media/EGL/S2ROM and surfaceless flags were supplied.
The only failure was a constructed flipper/enemy missing spriteFlags bit7,
which native Player_ObjCollide .Loop correctly skips (PP Player Object
Collision.asm:28-32). R2 changes only that fixture's explicit $80 byte.
Request25192 reran the entire Runtime62:62/0/0/0, exit0 at21:50:29BST,
156squeue/29.688sMaven. All selected XML counts/skips were inspected; production
is identical between those two runs. Do not describe the original1050 run as
green or add overlapping totals as independent coverage.

Independent Codex composition review confirmed that fixture P2 and its R2
response, with no further P1/P2. Prior nonblocking Movement oracle gaps and
the absence of a same-slot Runtime replay remain recorded externally. The
Runtime standing/jump cases do replay actual actor/shared/pool/SAT state.
No natural supported platform route, live ramp World traversal or native
planes/PLC/timing/pixel/act/game parity is claimed. ACTIVE_3D_RAMP_BACKGROUND,
Spring$0A, background tile streaming and section residency remain real owners.
The fixed-ac4 broad smoke runs independently; it does not cover this later
source revision and is not promoted to a pass before its result is inspected.

### R11A background arithmetic component — 24 October 2026

The pure background owner now binds the existing authenticated cloud-size and
water-section views. Its one immutable State holds all six camera longs, full
crossing/padding/flags,512-byte cloud and deformation RAM,1024-byte HScroll RAM
and VScroll. Init takes explicit complete RAM and incoming d0/d1 bits; step
takes completed foreground words/diff, actual signed player X velocity and
control. No actor, World, callback, renderer, clock or snapshot is retained.
Native cloud ticks, signed arithmetic, flag high bytes, fractional carries,
borrow/halving and FixBugs0 water-block accounting are preserved. A $281 BGY
case intentionally writes247 HScroll longs, not a generic240; unwritten bytes
remain supplied values. Invalid adjacent reads stop at their actual boundary.
Native owners: InitLevelScroll/LevelScroll/ApplyBGHScroll (PP Scroll:135-534)
and BG flag helpers (Level/Scroll Flag Set.asm:102-224), pin1ecc5f3a.

First actual queued component run45832 passed118/0/0/0: Background54, Foreground61,
RetailAssets3, with actual supplied media, Java25 and lean4GiB/4core admission;
exit0 at21:52:40BST,5squeue/86sMaven. Command selected those three named classes
through maven_queue.py --lean -Dmse=off with soniccd.media.root. Compilation
explicitly included the new owner; all three completed XML files/skips were
inspected. Independent Codex review found noP1/P2. Its two P3 coverage limits
remain explicit: the detachment case does not mutate caller table arrays, and
some water-exit suffixes cannot distinguish wrong cursors because their bands
coincide. Source assessment found neither a production escape nor cursor bug.

This commit is the arithmetic component only. Existing World background stays
static until one retained BG State is integrated with justified load/restart
RAM/register ingress, source-order stepping, capture/restore and publication.
BGCameraSectIDs/BGCameraSects runtime joins, native tile-plane streaming and
section PLC residency remain separate work. No live ramp, new plane picture,
render/native timing/parity, act completion or full-game claim follows.

### Spring raw views/art and native background section source joins — 24 October 2026

Spring now has lazy authenticated USA Ani_Spring14-byte and complete shared
MapSpr_Spring1/2/3 200-byte views, with a separate36-tile normal bank at native
$520..$543. Existing startup nine views/two decodes, four banks and selector0/5
admission are unchanged. Source546-byte Nemesis data needs547 real reader bytes;
the next $80 byte belongs to Art_MonitorTimePosts, not padding. Exact consumption,
XOR output/hash, all tiles/gaps/cache/post-close access and regional rejection
ran against supplied media. Native owners: Spring/Main.asm:186-244,492-496 and
PLC_Std (PP Data:70-71), pin1ecc5f3a. Shared independent indices are still raw;
a separate explicit decoder prerequisite owns their later visual family.

BGCameraSectIDs130-byte and BGCameraSects16-byte views bind independently
verified USA offsets3534/35B6 and JP/EU3530/35B2. The retained offscreen row0
and finalrow129 are source data. Selectors0/2/4/6 address four longs, including
the nonnull static/BG1 alias; MOVEA leaves the static branch's CCR unchanged.
These are native camera RAM addresses, not MMD offsets. Initial live pointers
and incremental copied-camera pointers are distinct source cuts. Only metadata
is added; no plane write, rendering fallback, source-table literal or extra
eager decode is installed. Native PP Scroll:892-995,1092-1198 supplies the
lookup contracts; later bounded PlaneB ownership remains separate.

Frozen Spring3 and BGsection2 do not overlap production owners. Root reconciled
one constructed test's enum-wide TABLE count35->37; source blobs stay69. The
new enum values shift later ordinals, but no CD ordinal consumer/published pin
uses them. An initial wrong Spring mirror src/ prefix stopped the import guard
before writes; unnecessary queued62838 was cancelled before admission130. The
correct paths main/ and test/ were then checked against exact hashes and imported
fresh-mtime. Original freezes remain untouched.

Root33885 passed172/0/0/0 across seven actual classes: ObjectVisual13,
ObjectAssets21, ObjectRetail3, RetailAssets9, Background54, Foreground61,
ModApiSignatureSurface11. Java25, actual supplied media, lean4GiB/4core;
exit0 at22:05:38BST,262squeue/95sMaven. Command used those exact named selectors
through maven_queue.py --lean -Dmse=off with soniccd.media.root. All completed
XML identities/skips were inspected. Independent Spring review found noP1/P2;
Root also inspected the full two-entry BG delta/new tests. Known Spring P3
limits: post-close access alone would not catch eager decoding, and malformed
authenticated-owner accessor retry paths have no direct test seam.

No Spring animation/object/collision/runtime admission, diagonal art, real PLC
residency, BG tile streaming, new World route or native-plane/pixel/game parity
is claimed. The full USA then regional task remains active.

### Bounded shared indices and normal Spring visual families — 24 October 2026

The explicit indexed decoder retains a complete mapping include, a signed-word
index root and a disjoint declared body window. Native index aliases, negative
relative words, nonmonotonic ordering and real EVEN padding remain bounded;
old strict decode/static/tail APIs are unchanged. Root added two direct oracle
checks for the reviewed strict-padding and adjacent-window gaps. Spring now
has lazy vertical/horizontal getters over the same200-byte include at roots0/6
and body windows12..46/46..80, each3frames/one real FC script/36tiles. The
separate four-byte speed view reads signed F000/F600 by subtype bit1. Generic
mapping joins retain each actual root, registering $209BA0/$209BA6; diagonal
$209BF0 and frames>=3 remain rejected. Native Spring Main:237-244,492-496 and
Mappings:1-44 at pin1ecc5f3a own these values. No Spring object is dispatched.

Root7648 established a meaningful pointer RED: the new actual-media mapping
case reached the old resolver and failed at unregistered $00209BA0 (1test,
1error, no skip). Root89987 passed88/0/0/0 over Visual24, ObjectAssets21,
ObjectRetail3, Animation28 and Mapping12. Root then added the two decoder
oracles and the family composition. Root44673 completed193/1failure/0errors/
0skips across Visual26, ObjectAssets22, ObjectRetail3, Animation28, Mapping13,
Runtime62, WorldLoad20, EGL8 and API11. The sole failure was a missing expected
SPRING_SPEEDS digest in the retail fixture's exhaustive table map; independent
source bytes F000F600 hash to5eda02f69ae1d12606f936787ccebdac8133522694513985847a115cc00f2621.
Root's one-line fixture-only correction changed no production. Root76885
reran all3 regional retail invocations:3/0/0/0, exit0 at22:26:08BST,285squeue/
30.165sMaven. XML identities and all skips were inspected; assertions below the
former failure now execute. The original193-case run is not described as green
and these overlapping counts are not summed as independent coverage.

Commands used exact named selectors through the queued Maven wrapper, Java25
and soniccd.media.root=<supplied-media-root>. The193-case run
also enabled the existing opening EGL tests with surfaceless rendering. The
independent decoder review found noP1/P2; the family review confirmed the
fixture P2 and its exact R2 correction, with no productionP1/P2. Lazy-cache
oracle limits remain disclosed. No Spring launch/collision/runtime bank,
diagonal art, natural route, live PLC residency or native pixel/game parity
is claimed. Fixed-ac4 broad smoke is independent and still pending.

### One source-ordered background World state — 24 October 2026

The CD presentation now owns one immutable background arithmetic State per
visit alongside foreground state. Explicit complete deformation/HScroll ingress
and native cleared camera/crossing/cloud/flag domains feed InitLevelScroll,
then exactly one FG+BG load step before ObjSonic_Init. The camera begins at
$70B; incoming register upper halves are zero. Completed ordinary object passes
feed actual signed player oXVel and authoritative high-byte scrollLock to FG
then BG. Sprite culling retains pre-scroll FG; SAT construction receives the
completed FG/BG1/BG3 words. Capture/restore, draw and projection never tick clouds.
Pause/lag/death/restart cuts hold BG. Published HScroll and sprite tables retain
the same completed-loop cut; all requested packed words/fractions survive.

Root corrected its original false HScroll-carry premise. LevelStart calls
ClearScreen (Main:225; Functions General:220-226), whose FixBugs0 DBF clears257
longs:1024HScroll bytes plus one player header long. Restart carries all512
deformation bytes, clears HScroll/clouds and runs one load tick. Fresh zero
deformation remains explicit DEV residue policy. Cleared flags/tile and later
LoadPlayer's ID1 are represented by the collapsed cold builder; neither load
scroll owner reads that ID. Native ClearFlowers covers every cloud byte.
Cold load writes229HScroll longs, leaving27 source-cleared tail longs.

Independent review found no productionP1/P2 and one test-integrationP2: two
existing replay helpers compared new immutable RAM wrappers by identity. RootR3
compares owner, foreground, all background scalar fields and every RAM byte in
both helpers; the published projection assertion now requires224 entries. It
changes no R2 runtime behavior. Original R1/R2 external freezes remain preserved.
BG-Y/opposing-cull SAT oracle and overwritten-deformation transport limits remain
recorded; pure carry independently checks all512 bytes.

Queued Root33510 passed290/0/0/0 on this eight-Java-file composition at base
b550e4ac8: WorldLoad29, RestartCarry21, Foreground63, Background54, TerrainPlayerLoad24,
TerrainPlayerBoot8, PlayerPresentation10, OpeningWorldEgl8, OpeningObjectRuntime62
and ModApiSignatureSurface11. Java25, actual CD media and S2 donor, explicit EGL
opt-in/surfaceless mode; normal7GiB/8core admission. Exit0 at22:39:52BST,65squeue/
128sMaven. All ten completed XML identities and zero skips were inspected.
The full named selection used maven_queue.py --normal -Dmse=off with media,
soniccd.opening.egl=true, openggf.headless.gl=surfaceless-egl and the donor path.
No image artifact was retained by this run. Native PlaneB name-table streaming,
copied-camera/VInt service, PLC residency, natural controller ramp traversal and
pixel/act/game parity remain separate owners. Fixed-ac4 broad smoke is still
pending and has a GLFW-startup failure awaiting matched baseline attribution.

### Finite native Plane B transformation component — 24 October 2026

The new pure PlaneB owner binds the existing authenticated section ID/live
pointer views and retained terrain. Its explicit immutable4096-byte seed
represents64x32 raw tile words at VRAM E000..EFFF. Init draws16rows with native
7F0 lookup wrap; incremental row/column work keeps signed finite lookup, live
BG1 selection and separately supplied copied cameras. Static CCR gating, top
priority, right-mask flag consumption, blank A0 metadata provenance, real
layout crossing, all raw pattern attributes/flips and VDP rings are preserved.
BG2/3 draw helpers are native no-ops. Actual finite reads throw typed boundaries
without a partial Result or seed mutation. There is no implicit clear/service,
World state, renderer upload, clock, pointer-memory emulation or PLC owner.
Native owners: PP Scroll:892-995,1092-1198 and Block Draw:20-68,111-234,376-395,
pin1ecc5f3a. Result metadata describes completed writes; callers never replay it.

Independent review found noP1/P2 and two oracle gaps. Root's test-onlyR2 adds
an initial selector8 pointer-read failure at byte16 and makes the second
chunk's block137 use asymmetric raw tiles A2AB/42AC/12AD/E2AE. The ring-wrap
case asserts these exact four destination words, defeating the old periodic
fixture alias. Production is byte-identical to the reviewed freeze.

Root84398 passed58/0/0/0: Plane43 (including actual USA post-close view binding),
Terrain5, RetailAssets9 and one fresh actual-EGL Ring/pickup/full-course-replay
case. Java25, supplied regional media/S2 donor and surfaceless EGL, normal
7GiB/8core admission; exit0 at22:43:31BST,1squeue/88sMaven. All four completed
XML counts/identities and zero skips were inspected. Exact selectors used the
queued wrapper with the same media/EGL flags and a fresh external output.
The route retained94PNGs, controller log, state CSV and a successful outcome
in its explicit external task directory. It ran at0e4e6d055 plus these two
new component files; the World does not yet call this PlaneB owner. Therefore
those images show source-ordered BG scroll through development planes, not
native PlaneB integration. The opt-in component case proves source binding,
not a native plane-image/PLC oracle. Complete native video, section residency,
full act/game and regional gameplay parity remain unclaimed.

### Normal native Spring execution component — 24 October 2026

CdSpring ports normal up/side/down routines0..18 and the routine2==5 player
mirror. Exactly five dynamic-slot aliases name speed30,parent34,anchor36 and
signed byte offsets38/39. Init returns before Main; reached Solid calls publish
a0 and borrow one real pool cut, then require synchronous a0/a2 publication.
A distinct immutable launch patch names exact native stores, preserving both
fractions and all undeclared actor/slot fields. Move lock is word3E, not timer32.
Down's returning sound call falls into Animate in the same dispatch; up/side
return directly to the wrapper. Real script FC occurs on animation call11; reset
runs on the next dispatch. DrawObject precedes live parent following and unsigned
anchor-window deletion, with post-draw flags reloaded. ID-zero dynamic parents
remain actual live images. Diagonal selector15/routines, MovingSpring execution
and other reserved/unaligned parent reads remain named unsupported boundaries.
No collision, probe, KillPlayer or animator implementation is duplicated.
Native owners: Spring Main:98-409, Object Solid and Object Animate, pin1ecc5f3a.

Root22580 passed231/0/0/0: Spring65, Solid88, Animation28, Mapping13, Pool12,
ObjectAssets22, ObjectRetail3. Java25 and actual supplied media for inherited
opt-in consumers; Spring's65 constructed invocations have no media skip. Lean
4GiB/4core admission,330squeue/94sMaven, exit0 at22:53:33BST. All seven completed
XML identities and skips were inspected. Command used the named selectors through
maven_queue.py --lean -Dmse=off with soniccd.media.root. Three Java files match
the external freeze exactly; tests were authored before production.
Independent bounded source review found noP1/P2 or wrong expected assertions.
One nonblocking oracle gap remains: the dedicated parent/draw case does not
set parent ID0 or assert complete post-draw flags/returned-pool equality; source
handles those correctly. These are component passes, not a World Spring claim.
Root still owns launch/solid phase-CAS, typed sound forwarding,400patternbank
and cleanup, ID0A live dispatch, actual contacts/SAT/replay, PLC residency and
later regional admission. Fixed-ac4 broad smoke remains independent/pending.

### Guarded Spring launch, level-plane cut and normal picture policy — 24 October 2026

Frame now validates current player/globals and every Spring launch store at an
open/idle/closed cut, then adopts checked motion once. Both fractions and all
other actor/slot fields, animation cursor, display cut, timers/history/links and
shared/art/clock state survive. MoveLock word3E remains separate from timer32.
Spring actions have no global-write authority. These are value checks, not
proof a call ran; Runtime still owns live identity/phase/source-prefix binding.
The Runtime's ephemeral two-argument V-int service calls the supplied level-plane
service after powerup art and before Finish/count on ordinary and every paused
selector8; lag skips it. Null leaves prepared input intact; source failure closes
the existing owners without rollback. The old one-argument entry delegates to
a no-op. No extra clock/callback retention or stricter swallowed-reentry promise
is introduced. Native Main:829-847 and Spring Main:257-396 own these cuts.

A separate explicit Spring picture factory validates all11spans/400patterns even
for empty SAT, using actual normal36tiles520..543 and adjacent powerup544..567.
All old factories/helpers/convert are byte-identical. New tests cover all400joins,
both source families/all6frames/rawattributes, gaps and all logical span overlap
pairs. Runtime is still364 and has no Spring dispatch in this commit; its
400-pattern reservation/logicalbanks/cleanup/client work remains next.

Tests were authored before production. Root79344 test-compile exited1 at absent
launch APIs: compileRED only, no JUnit execution. Root73735 completed298/0failures/
1error/0skips: Frame165, Runtime66, picture19, WorldLoad29, EGL8, API11. The only
error was Root's new failure test reading displayedFrame after correct terminal
closure. Existing requireReadable correctly rejects it. Test-onlyR2 removes the
unused old-picture capture, checks rendering rejection and names that behavior;
all source/art/count/closure assertions stay. Production unchanged. Root53533
reran fullRuntime66:66/0/0/0, exit0 at23:11:29BST,451squeue/30.365sMaven. The
original298 run remains red, and overlapping counts are not added as distinct
coverage. All completed XML identities/skips were inspected. Both runs used
queued exact selectors and actual supplied media/Java25; the298 run enabled
actual surfaceless EGL and S2 donor, normal7GiB/8core,147squeue/105sMaven.
The corrected narrow run used lean4GiB/4core.

Independent review confirmed R1's P2 and exactR2 correction, with no remaining
P1/P2 in these six files. Nonblocking oracle limits remain: positive launch
preservation starts after Init with empty display/mostlyzero history/cursor,
and picture raw bases/flips are paired rather than Cartesian. No live Spring
contact, native PlaneB World service, PLC residency, native timing/pixels or
complete-game/region result follows from these port/component checks.

### Native Plane B CPU service, copies and publication — 24 October 2026

The presentation now retains one immutable PlaneB image and a native draw-copy
image alongside its one FG/BG arithmetic state. Explicit ClearScreen seed,
one load scroll and forced liveFG0400 precede initial442block drawing;560
unwritten bytes retain the clear seed. Each genuine selector8 copies all32
camera bytes/eight flag bytes, draws once after art/beforeFinish, and consumes
only copied BG flags. Live flags/fractions stay authoritative; repeated paused
services recopy them, lag skips service, and death/lock can repeat draw without
BG arithmetic. No load/fade V-int or extra clock is fabricated.
Selected PlaneN is published with prior prepared ScrollFrame/SAT N while CPU
advances N+1. All4096bytes/copies restore without source work; restart clears
plane/copies/publication, retaining only existing justified deformation ingress.
Terrain PLC is a composed service with its own stable identity and stale-owner
guard; all interface methods forward. Existing native opening restore preflight
covers the extended publication before unrelated writes. Rendering, native FG
streaming/flag consumption and364pattern reservation remain unchanged.
Native owners: Main:225-249,821-847; PP Scroll:807-820,892-995,1092-1198, pin1ecc5f3a.

Independent review found one P2: WorldLoad still expected raw Frame service
identity. Root's pre-import test-onlyR2 checks nonnull/different/stable wrapper,
keeping all entry/input assertions. No productionP1/P2 was found. Root5314
completed508/6failures/1error/0skips: World32, TerrainLoad27, EGL8, TerrainBoot8,
RestartCarry21, Presentation10, Plane43, BG54, FG63, Frame165, Runtime66, API11.
All seven failures/errors were three old raw-Frame lookup/cast sites in the
Tools Boot class; all other selected checks passed. All12XML were inspected.
Root's first Boot migration66860 then failed compilation because the direct
module getter is package-private; noJUnit ran. Test-onlyR4 keeps Boot's package
and uses a same-CD-package SonicCdTestAccess bridge, with wrapper identity
checks. No production accessor, game service, reflection or owner is added.
All8 Boot cases passed in Root60964,0failures/errors/skips, exit23:26:32BST,
176squeue/34.246sMaven. The original508 run is not called green and overlapping
counts are not summed. Production is unchanged after5314. The508 run used
actual regional media/S2, Java25, surfaceless EGL, normal7GiB/8core,1squeue/
131sMaven, exit23:16:32BST; correctedBoot also used actual media/EGL.

Original fivefile freeze and reviewed reports are preserved. New Plane-callback
finite-read failure lacks a direct World test (existing CPU BG failure differs);
source closure is reviewed, with generic callback failure separately exercised.
The39/442 count assertions use pure component expected Results, not independently
measured native draw calls. The renderer still uses development planes: no native
PlaneB image, native priority/PLC/timing, route/act/game or region-parity claim.
The next client must consume selected immutable words with explicit residency
authority; it may not replay observations or advance source state during draw.

### Live normal Spring composition and rendered picture — 24 October 2026

The physical dynamic walk now dispatches normal Spring ID0A with current
post-Body player/global values and one shared Solid owner. Each genuine Solid
call checks actual snapshot/image ownership and the current pool cut before
pool writes, then applies exactly its declared player/global writes. Launch
has a separate guarded motion-only port after the native object prefix and
before FM_SPRING. Both integer coordinates preserve their fractions; move-lock
WORD3E stays distinct from invincibility WORD32. Completed Solid sounds precede
Spring sound; no call/effect is replayed. The final SAT lens still reads actual
post-object actor state with the earlier Body eligibility/bucket. Cold/restart
admission and each serviced picture validate all eleven actual art spans.
The existing governed picture range now owns400 patterns and removes the same
400 on close; normal Spring art contributes36 after the old364. No extra state
or mutable snapshot partition was added. Native sources are ObjSpring/Index,
Init/Action/Reset (_Objects/Spring/Main.asm:130-409), DrawObjects
(Object Functions.asm:263-420), and PLC_Std (PP Data Act1 Present:54-83), pin1ecc5f3a.

Root60408 ran the nine-class combined selection at9e6d6c973 plus four imported
Java files:477 tests,2failures,1error,0skips, exit23:42:10BST. Runtime76 had a
reset full-image expectation omitting FLAGS bit3 clear and a replay fixture
retaining unsupported playerControl1; WorldLoad32 still expected unowned0A,
but the actual400-Right-input route reached unowned34 at physical58. Frame165,
picture19, EGL8, API11, Spring65, Solid88 and mappings13 passed. Independent
source review identified both Runtime fixture defects and a masked art-wrapper
equality defect. Test-only R2 preserves dirty-control preservation cases,
seeds control0 only for next-Body replay, asserts MAIN_RETURN and a real animation
change, checks both art buffers/scalars by content, and clears the expected
reset flag. The same World recipe now asserts the actual SpinTunnel tag34
frontier, without admitting that body, changing inputs or adding a camera fence.
Root33975 reran complete Runtime76/WorldLoad32:108/0/0/0, exit23:45:16BST,
13squeue/32.987sMaven. Production stayed byte-identical to reviewed frozenRuntime.

A new constructed actual-EGL test clears all dynamic slots after real player
Init, places up/side/down Springs40/41/42, runs real native Init, then explicitly
selects animation routines4/10/16. Sonic remains live. Six actual source-animator
rows check independent native piece/SAT/painter literals, all36 normal tile
nibbles, family-local framebuffer differences against completed-scroll terrain,
full/course state and every-row pixel replay, real atlas textures, and absence
of all400 logical entries after World close before PASS is written. It proves
constructed rendering and retirement, not a natural contact/launch route.
Only57 entries were actually materialized in this fixture, including all36
Spring IDs; checking400 policy/reservation/removal is not rendering all400.
Root34863 ran complete EGL class9/0/0/0, exit23:49:35BST,
33squeue/43.403sMaven. Actual PNGs/input/outcome are retained externally under
native/engine-spring-picture-r1. The previous eight EGL routes remain intact.
Independent client review found no P1/P2. Its nonblocking GPU oracle limit
remains: per-family presence and complete phase-image changes do not separately
prove every family's GPU animation/color change against native RGB. No stronger
per-family or native-pixel claim is made.

Commands were queued exact selectors with -Dmse=off and supplied absolute
media/S2 properties: combined -Dtest=TestCdOpeningObjectRuntime,
TestCdPlayerFrameRuntime,TestCdObjectPresentation,TestSonicCdOpeningWorldLoad,
TestSonicCdOpeningWorldEgl,TestModApiSignatureSurface,TestCdSpring,
TestCdSolidObject,TestCdObjectMappings; corrected Runtime/WorldLoad selection;
then -Dtest=TestSonicCdOpeningWorldEgl with fresh soniccd.spring.output.
All used Java25.0.5.0.2, DISPLAY/WAYLAND_DISPLAY unset, actual surfaceless-EGL
opt-in and no-device audio. Every selected XML identity/skip/property was
inspected; stale XML from other runs was excluded. The original477 run stays
red, overlapping results are not summed, and no broad/full-suite pass follows.

The native PlaneB CPU image/service/rewind is present, but drawing still uses
development planes. Cold/terrain-only rendering needs a shared owner scope
before descriptor mutation; its feasibility audit stopped without importing
unchanged client snapshots. Native section PLC residency, diagonal Springs,
mixed plane/sprite priority, natural Spring launches, remaining USA gameplay
and Japan/Europe parity remain outside these bounded checks. Work stays private
and local with no destination integration, push, PR or complete-game claim.

### Retained background resource scope — 25 October 2026

An internal LevelSpritePresentation.withScrollAndBackground scope now accepts
one optional immutable ScrollFrame and an exact8192-byte physical64x32 RGBA
image. Null privately observes the current completed-load view without creating
or publishing a public frame. Actual attached World/source/module/Level,
renderer/load lifetime, camera/parallax, viewport, graphics/atlas and cache
identities are retained and revalidated. Renderer scopes, native preparation
and arbitrary enclosing graphics captures reject before cache mutation.
A fixed static512px/no-feature/no-full-width-or-persistent-owner contract is
enforced; it is not a native-owner override or a game-name branch.

The existing GraphicsManager owns the true ordinary FIFO barrier: close prior
normal patterns, transfer queued work to existing capture ownership, clear the
old list, and drain on the old view/image with failure-safe tail release.
Its new helpers are package-private; public forwarding stays on the existing
unmarked SpritePresentation. The marked ModApi type gains no public/protected
signature. After old work drains, cache bookkeeping settles before retained
installation/upload; captured callback patterns/commands drain while selected.
Scroll/mode/guard restore on all exits. Derived cache need not roll back after
upload failure. No field, manager, epoch or default-path behavior is added;
old capture/validate/withScroll/cinematic/S3K/generic code and capture executor
remain byte-identical. Source-only independent review found no productionP1/P2.

Tests were frozen before R2 production; Root99167 tests-only test-compile
failed on intentionally absent APIs, compilationRED only/no JUnit. Root85534
then ran96/0failures/3errors/0skips: a new persistent content42 upload stub
also rejected recovery's old-image settlement, and Root omitted the explicit
S3K property needed by two cinematic rows. Review independently confirmed the
sole testP2. Test-onlyRootR3 clears the mocked endpoint fault before image43;
all original failure/restoration/recovery assertions remain. Production is
unchanged. Root89645 reran complete96/0/0/0 with actual verified S3K property,
Java25.0.5.0.2/surfaceless and no ambient display, normal7GiB/8core,
43squeue/33.654sMaven, exit00:11:31BST. All nine selected XMLs/ROM/backend
properties and skips were inspected. The original96 run remains red.

Exact selectors: TestLevelScrollCapture7, TestLevelScrollDrawScope27,
TestCapturedCommandCleanup10, TestLevelSpritePresentation7,
TestPooledRenderCommandDiscard5, TestRenderContext21,
TestLevelSpritePresentationLifecycle6, TestStageplayCinematicCameraRendering2,
TestModApiSignatureSurface11. Command used queued -Dmse=off, explicit
-Dopenggf.headless.gl=surfaceless-egl and -Ds3k.rom.path to the supplied image.
Focused scope/lifecycle/cleanup/API and actual S3K cinematic coverage bounds
this optional internal component: no existing production caller uses it yet,
and existing paths are source-preserved. Actual CD cold/terrain native-plane
cache integration, residency,256px wrap and EGL pixels remain next; the
mocked/CPU scope tests do not prove those. No broad/full-suite pass follows.

Owning render-thread convention and current completed-load admission remain;
no closed-World or same-instance cleanup/reinitialization detector is invented.
Caller must not enter during a load or mutate source/art/palette/cache owners
inside the callback. Native pattern residency and CD terminal/per-visit gates
belong to its client. Oracle limits retained from review include early-clone
mutation coverage, resolver-emitted command coverage, and actual ordinary
LevelManager/GPU cache integration. No repeat review or unrelated graphics
change was used to turn the run green. Work is private/local, not delivered.


## Spin Tunnel tag assets, pure response and Frame field port — 25 October 2026

Private component checkpoint at b6d17e897 plus nine Java changes. The USA
POWERUP_MAPPINGS view binds exactly274 retained bytes at file3EE9C/native23EE9C;
its thirteen raw counted frames deliberately use the existing raw mapper,
including the valid empty frame inside another body. Strict visual decoding,
eager startup views, prior art banks and the400-pattern policy are unchanged.
The pure ID34 owner ports native Tag Init/Main and post-draw despawn metadata;
it retains no pool, snapshot, Frame or callbacks. Native unadjusted TST.B at
both debug word bases observes the HIGH byte. No new gameplay sound is added.
The Frame port validates the complete expected motion and preserves every
field outside the exact eight-field executed-write contract, then applies
once at the existing completed cut. Fractions, state partitions, art, input,
clock and service ownership remain unchanged. Live Runtime dispatch is next.

Tests were authored before production. Independent static review found one
malformed newly appended mapper assertion and one replay comparison using
CdAssetBytes identity through record equality. Root repaired those tests only:
restored the existing FloatBlock frame5 assertion, and compared the real next
service's art/DMA byte contents and scalars. Frozen originals remain external.
Actual-media mapper negative Root62691 failed exactly one expected missing
23EE9C pointer error on the old mapper (387s queue/1m30s Maven). Frame tests-only
Root70543 test-compile failed only on the two proposed absent Frame APIs;
that is compilationRED, not a Jupiter run.

After exact production imports, Root5579 ran queued --lean -Dmse=off test with
-Dtest=TestCdSpinTunnelTag,TestCdPlayerFrameRuntime,TestCdObjectMappings,TestCdR11aObjectAssets,TestCdR11aObjectRetailAssets,TestCdObjectVisualAssets,TestCdObjectPool,TestModApiSignatureSurface,
supplied soniccd.media.root and explicit surfaceless-egl, DISPLAY/WAYLAND unset.
Observed481 tests/0failures/0errors/0skips; immediate admission/1m39s Maven.
Exact completed XML identities, Java25 and media/backend properties inspected.
Counts: Tag156, Frame235, mapper15, assets23, retail3, visual26, pool12, API11.
The three regional invocations executed; USA-only object admission is unchanged.
This is bounded component/media/API evidence, not World contact, native timing,
section PLC residency, rendered debug pixels, an act/game or regional parity.
Independent source reviews found no remaining productionP1/P2 in these units.


## Retained native Plane B client and cold-mask correction — 25 October 2026

The CD client projects the selected immutable4096-byte native Plane B into
physical64x32 RGBA descriptors and pairs it with the same published scroll/SAT.
Native pattern/palette/H/V/priority bits survive, including opaque pattern0;
encoding all2048 IDs is separate from admission to the authenticated800 resident
base-level patterns. CPU draws require actual Level patterns/palettes but no GPU
entries. GPU draws additionally require the actual current atlas0 entries,
matching64-pixel upload samples and matching manager lookup, rechecked after
old FIFO drain. No fake entries, allocator fallback, source advance or new BG
owner is added. Native PLC/section residency remains explicitly unimplemented.
The real static512px window,256px vertical wrap and origin0 cache are selected
through the shared scope; the old draw-time parallax update is removed.

Independent client review found noP1/P2 and noted a missing discriminating BG
pixel oracle. Root28111 ran the exact four-file R2 client on4fd78f6bc:
130/0failures/3errors/0skips after727s queue/2m13s Maven. WorldLoad46,
OpeningEGL11,TerrainLoad27,ScrollScope27 andAPI11 passed. All three actual
regional TerrainBoot cold-render cases errored at GraphicsManager.drawArenaMask
because standalone scroll captured a null transition sample before first
presentation preparation. CPU headless short-circuit and opening tilesOnly
had concealed that actual GPU/player path. No fixture assertion was weakened.

Root's captureForDrawScope snapshots actual cold mask geometry at capture,
without advancing its transition; an existing sample is retained exactly.
Only standalone captureScroll and nullable-background capture select it.
The ordinary/native-table capture body, cinematic paths and S3K publication
semantics remain byte-identical. Two tests were authored before this repair;
the three existing actual terrain cases supply observed regressionRED.
A bounded independent review found noP1/P2; the new pure cold test uses native
320px width and does not independently certify wider asymmetric wing geometry.

Test-onlyRootR3 also compares real native-BG terrain pixels against the same
foreground/art/camera/scroll with transparent BG descriptors. Pixels must differ,
then an ordinary draw must restore the native image and the source capture.
This rejects an omitted BG draw that deterministic replay alone could miss.
Root60908 passed71/0failures/0errors/0skips,577s queue/1m34s Maven,
exit01:23:09BST. Exact eight XMLs/Java25/media/EGL/verifiedS2+S3K properties and
case identities inspected: TerrainBoot8 (all three regional actual EGL cases),
ScrollCapture9,ScrollScope27,SpritePresentation7,Lifecycle6,S3KCinematic2,
NativePlaneEGL1 (cold/full/course/93-row replay and BG counterfactual),API11.
It used queued --normal -Dmse=off, display variables unset, supplied media,
soniccd.opening.egl=true and explicit surfaceless-egl/ROM properties. The original
130-case invocation remains red. Changed checks were rerun proportionately;
unchanged WorldLoad46/TerrainLoad27/otherOpeningEGL10 retain their earlier result,
not a new post-repair pass. No image/output property or desktop audio was enabled.
These are actual engine pixel/resource/replay checks, not native pixel parity,
priority composition, section PLC, all regional GPU gameplay or act completion.

The two broad-smoke boot errors are now matched on retained9598c663/currentac4e5efa
by TestSurfacelessEglContext then TraceCaptureBootDimensionsTest in one alphabetical
reused JVM. Root67418 and83348 each observed5/0failures/2errors/0skips; both
Trace errors match line41/initGl332 and follow the predecessor's unconditional
backend-property clear. Earlier isolated Trace cases passed both trees. Together
with the matched HCZ53 and CLI182 failures, all four original broad failure
identities reproduce on both retained trees. This does not make broad smoke
green, cover later changes or establish a release/full-suite pass. Its diagnostics
were consumed and deleted; the clean validation tree/merged local branch were
removed after preserving11 unknown ignored regular files outside the repository.


## Live Spin Tunnel tag dispatch and next real Door frontier — 25 October 2026

ID34 now runs from the actual physical object walk with current post-Body motion,
full debug words/raw held, authenticated Powerup pointer, exact Init preflight,
one same-owner96-slot CAS and the eight-field Frame patch. Optional real debug
DrawObject is followed by a mandatory live reload before native despawn. The
real766-byte saved bank is preflighted, loaded bit7 alone clears before full
delete, and failed tails preserve completed prefixes. No Body, sound, clock,
input or publication is replayed. Existing Spring/Solid/Float/V-int/capture and
400-pattern policy remain unchanged except mutual nonreentry guards.

Tests were authored first; independent review confirmed the authored ramp-timer
expectation must be60. Root49039 new dirty-Init test against old production
failed exactly1 expected unowned34 error atphysical40,0skips (197squeue,
28.238sMaven). After exact production import, Root82832 ran fullRuntime89,
World46 and NativePlaneEGL1:136/4failures/1error/0skips after883squeue/1m30sMaven,
exit01:46:50BST. All76 inherited Runtime cases passed. Three new assertions
omitted the prior Body ChargeSound(FM_CHARGESTOP), whose unconditional stopped
charge branch is native Sonic/Main.asm:1033-1036. The composite replay called
closed-cut restore after an ordinary service had opened the next loop. The
remaining World assertion still expected34; its actual exception was unowned
vertical Door35 atphysical32. These are distinct from a new gameplay defect.

RootR3 changes tests only: compare the preserved prior ledger for no-sound
Tag/ramp/warp paths; pin ChargeStop then Spring sound order before an unknown
later dispatch; complete the additional real ordinary loop before composite
restore; migrate the unchanged400-Right World route to the actually observed
Door35 dispatcher. Timer60 remains. No production behavior or input route is
changed. Root10457 fullRuntime89 plus that World method passed90/0/0/0,
immediate admission/30.293sMaven,exit01:50:55BST. Exact XML identities/Java25,
media/backend andzero skips inspected. The first136 run remains red; other45
World cases and theEGL method retain their prior result, not a new rerun claim.
The observed frontier establishes live Tag dispatch, not natural Tag contact,
velocity launch/native parity, section PLC service, or a completed act.

Root82832's exact NativePlane EGL method passed after its own teardown and
saved three320x224 actual ENGINE PNGs at cold,2-row checkpoint and93-row end.
Each image's dimensions/hash/pixel diversity were checked; its full/course
replay, selected PlaneB and visible-background counterfactual passed. A
method-scoped outcome explicitly records the red combined run. Visual review
and display remain queued for daylight, with no contact-event or native pixel
claim from those images. All work and exports remain private/local.


## Vertical Tunnel Door authenticated assets and body — 25 October 2026

Upcoming ID35 now has retained-source asset access and a source-ordered body;
it is not yet dispatched by Runtime or registered in the picture policy.
Table ordinal31 appends MapSpr_FlapDoorV atCE9A/28/native20CE9A; two static
frames preserve all four native pieces/flip attributes and two real pads.
SelectorC is admitted only atsubtype2=0, retaining0/5 and all other rejections.
The standard row1 binds actual ART_TUNNEL_DOOR at36AFE/154, nativebase8328,
VRAM6500,12tiles328..333. Existing startup9views/2art, all previous table
ordinals/banks/defaults and USA-only object admission remain unchanged/lazy.
Actual Java reader153 succeeds and152 fails; no adjacent/invented padding is
added. Independent author/reviewer byte joins bind actual USA ID/function,
Init mapping/selector operands, PLC and XOR pixel hashes. Source authenticity
is separate from decoding/semantic-guard coverage and from native residency.

The body ports Init0 fallthroughMain2, unsigned borrow/range gates, word3A
clear, incoming bit7 FM_A4 before frame1 before unconditional shared Solid,
and Reset4 high-byte3A add8 with byte carry/low3B retention. Reset never calls
Solid, including carry. Actual DrawObject is followed by a live reload and
source saved-bank loaded-bit clear before full deletion. One ephemeral real
Solid cut and synchronous publication thread current actor/global results;
there is no second gameplay store, animation, bank or effect replay. Only two
storage aliases are added to the existing object image. Root's prose-only
citation correction narrows the spawner owner to385-446; gameplay is unchanged.

Tests were frozen first. Static reviews confirmed a new retail helper's missing
throwsIOException, a retained31-table expectation incompatible with append32,
and one horizontal-contact expected X F3.8000 instead of native F2.8000
(width4+9+1, overlap8). Root corrected tests only and preserved frozen originals.
The raw production bank/body have no identified additional P1/P2 within review.
Root37799 tests-only test-compile failed on expected absent Door APIs/type/aliases;
that is compilationRED/no JUnit, not a witnessed behavioral negative.

After exact production imports, Root34255 queued --lean -Dmse=off test with
TestCdR11aObjectAssets,TestCdR11aObjectRetailAssets,TestCdTunnelDoorVertical,
TestCdSolidObject,TestCdObjectState,TestCdObjectPool,TestCdSpring,TestCdFloatBlock,
TestModApiSignatureSurface and suppliedmedia/explicitEGL/displayunset.
Observed329/0failures/0errors/0skips, immediateadmission/1m36sMaven,
exit01:58:15BST. Exact eight XML identities/Java25/media/backend inspected:
assets29,retail3,Door79,Solid88,pool12,Spring65,Float42,API11. The requested
TestCdObjectState selector matches no standalone class and contributes no cases;
alias correctness is directly checked in Door and these actual consumers.
All regional retail and selected media-gated Solid/Float cases executed.

Coverage is bounded: constructed Door Solid and actual CPU DrawObject/cull,
not final DrawObjects/SAT/GPU or live Door contact; fake-auth mutations reject
at the full-MMD digest before semantic guards; direct decoder/slice tests
supply separate format evidence. CAS stand-ins do not prove Runtime phase/full
pool preflight. Native320 cold geometry and pending audio/PLC remain limited.
Actual400-Right World currently stops at unowned35, not a completed act. Next:
12-span412-picture join, real Runtime callbacks/dispatch, graphics cleanup range,
actual route observation and rendered/replay checks. No full-suite/game claim.


### Vertical Door counted mapping and twelve-span picture — 25 October 2026 GMT

On component base7c5c28ea738515600091fbb60775fb53af808204, Root44297 ran
only TestCdObjectMappings#actualUsaVerticalTunnelDoorJoinsItsExactSourceAndPreservesEveryPriorMappingRoot
with the supplied media, --lean -Dmse=off and explicit surfaceless-EGL.
Observed1/0failures/1error/0skips, immediateadmission/28.454sMaven,
exit01:10:05Z: exact native20CE9A has no registered counted-table pointer.
This behavioral negative used the new mapper test against unchanged production.

Exact frozen production then adds only the existing actual-byte addObject join
at20CE9A/count2 and a separate twelve-span412-pattern policy. The old eleven
Spring spans/policy/convert and every old test/helper remain byte-identical.
The new span is actual ART_TUNNEL_DOOR, native328..333/12tiles, appended after
Spring logically. NOW-serviced immutable player/powerup resident windows retain
the established tails, and all physical/logical spans validate before empty SAT.
No service/state/default-seed/renderer callback or fallback is introduced here.

Root45971 --lean -Dmse=off test selected exact qualified
com.openggf.game.soniccd.level.TestCdObjectMappings and
com.openggf.game.soniccd.resources.TestCdObjectPresentation, suppliedmedia,
explicitEGL and noDISPLAY/WAYLAND. Observed41/0failures/0errors/0skips,
immediateadmission/1m35sMaven, exit01:20:32Z. Both XML identities,17+24,
Java25.0.5.0.2/media/backend and absence of aborted tests were inspected.
Six new media-gated cases and the prior gated cases executed. Literal native
closed/open mapping pieces, real pads, all412 pixels, every span pair/bound,
raw flips, coordinates/palette/priority, actual CPU SAT and ADD.W attribute
carry are checked. Separate source review reports noP1/P2; optional oracle
qualifications do not claim same-world certificates or GPU/native residency.
Frozen author/reviewer reports remain external, unchanged, private/local.

This is CPU mapping/picture evidence only. No live Door Runtime/client412,
World route/contact, GPU rendering, audio playback, nativePLC or regional
parity is admitted by this checkpoint. Runtime tests-only build91483 separately
failed compilation on expected absent12-bank/accessor/typedFM APIs; no JUnit
ran and no behavioral Runtime negative is claimed. Its exact external tests
remain preserved for the following Runtime import. Full-game goal stays active.


### Vertical Door live Runtime, World cleanup and complete GPU oracle — 25 October 2026 GMT

Base547318cd9 plus exact frozen Runtime440e07b0/Test4d1e021a adds real ID35
physical dispatch, a twelfth LogicalBanks field with old arity compatibility,
one cached actual Door bank and one dedicated FM_A4 ledger wrapper. Real Door
prefix/call-cut/result go through the existing shared Solid. Every96-slot value,
all returned images and Frame expected player/globals preflight before S4 writes;
S3 applies once, shared FM follows, and later slots observe the completed image.
Dedicated A4 runs after routine4/word-timer0 prefix, before frame1/Solid. Effects
are not replayed. Snapshot is unchanged; phase/mutual guards cover callbacks.
Independent Runtime source review found noP1/P2. Root24309 actualmedia lean
TestCdOpeningObjectRuntime passed97/0/0/0 (89 retained+8 new), immediateadmission,
1m28sMaven, exit01:24:44Z; Java25/media/backend and all8new XML names inspected.

Presentation changes only comment/cleanup count400->412; all prior client/native
PlaneB scope logic stays unchanged. New EGL component proof explicitly clears
all96 dynamic slots and seeds ID35 physical40 at camera+(192,72). It stages the
same live Sonic24px left before Init and each row2..35, keeping the declared
actor/header/timer/history/animation owners; it repeats that ingress on full and
course replay. This is constructed component evidence, not a normal-input Door
contact. Source Init is silent/frame0, Reset carries after32 calls without Main,
next Main emits A4/frame1, following V-int selects the open prior SAT. Actual
DrawObjects/SAT, decoded12 art tiles, real textures, complete captures/replay,
terrain controls and all412 retirement checks use the engine owners.

Root87329 actualEGL tests-only with new Runtime/picture but old400cleanup gave
1/1failure/0errors/0skips at surviving real logicalID131472 (OBJECTSbase+400)
after close. Queue7s/Maven27.188s, exit01:29:05Z; exact media/Java25/optin/S2ROM
properties inspected. After exact412client import, Root77883 normal queued
-Dmse=off test selected TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,
TestCdPlayerFrameRuntime,TestModApiSignatureSurface with actualmedia/EGLoptin,
explicit surfaceless-EGL, suppliedS2ROM and noDISPLAY/WAYLAND. Observed304/
1failure/0errors/0skips, immediateadmission/1m45sMaven, exit01:31:54Z. EGL12,
Frame235,API11 and45of46 World tests passed. Sole failure: same400Right route's
stale expected35 now observes Unowned CD object ID19 at physical59. No input or
runtime fence changed. Root changed only expectedID/methodname;51832 one exact
World frontier method passed1/0/0/0, immediate/27.867sMaven, exit01:33:25Z.
All class identities, failures, properties and zero aborts/skips were inspected.

Independent World review found P2: one changed pixel across a pose could certify
partially missing geometry despite correct CPU versions/uploads. Root preserved
the frozen original and wrote a negative first: erase one complete visible Door
tile from framebuffer copy while preserving visible RGB from the other five.
Root68639 observed1/1failure/0errors/0skips because the weak oracle accepted the
partial image (25.343sMaven, exit01:38:23Z). Root's test-only revision now checks
every native nibble at literal flipped/positioned screen pixels against the
current engine palette/display-colour policy, or exact current terrain for zero,
including alpha. It pins full rows, layer, subject, ghost/opacity, priority shader
and occlusion metadata. No sprite converter/renderer constructs this geometry
oracle, and the omitted-tile negative remains for both poses. Production did not
change for this repair. This tests engine display colours, not native palette
or mixed VDP plane/sprite priority parity.

Root51554 reran only the affected constructedDoor EGL method:1/0/0/0, immediate
normaladmission/27.506sMaven, exit01:40:03Z, actualmedia/optin/EGL/S2ROM/Java25
inspected. Every real Door pixel and both omitted-tile negatives passed, followed
by full/course replay,412 retirement and cleanup. R2 closed/open ENGINE captures
and terrain controls under external native/engine-tunnel-door-r2 are byte-identical
to earlier R1 images; R1 outcome records the superseded weak-oracle limit. R2 PASS
is written only after all assertions and session/graphics/boot cleanup. Images
are320x224 and remain queued/unviewed/unpublished during00-07 Europe/London quiet.
No broad unchanged selection was repeated after these assertion-only repairs.
Earlier all12 EGL/235Frame/11API/45otherWorld outcomes retain their exact sources;
304 is a red batch, not a claimed304-green rerun. Whole-game/region/nativePLC/
audio/nativepalette/priority parity remain open. The actual next owner is19.


### Monitor data and detached USA FM source — 25 October 2026 GMT

On576656be9, native Monitor views append atA9C6/136 andAA4E/450, preserving all
32 old table ordinals, nine startup views/two art decodes and priorbanks/defaults.
Strict existing decoding admits20frames/10scripts/82tiles. The actual8052 XOR
art source is1540bytes, but Java's finite8bitpeek needs1541, including the real
next Explosion header byte00 at3513E. The standardPLC1/row9/B500/base05A8 join,
all decoded2624 bytes and eight odd five-byte static item pointers are separately
authenticated from USA media. New monitorTime()/MonitorTimeArtBank reads stay
lazy and detached afterdisc close. This unit adds no mapper/static/picture/body/
reward/residency admission; native item85A8 attributes remain execution-owned.

Root52866 tests-only test-compile RED showed absent Monitor APIs/tables; no
JUnit ran. Exact frozen production then passed Root13888 --lean -Dmse=off
-Dtest=TestCdR11aObjectAssets,TestCdR11aObjectRetailAssets,TestCdObjectVisualAssets,TestCdObjectMappings,TestCdFmSoundAssets,TestModApiSignatureSurface
-Dsoniccd.media.root=<supplied-media-root>
-Dopenggf.headless.gl=surfaceless-egl test, noDISPLAY/WAYLAND, Lua5.4.
Observed98/0failures/0errors/0skips, immediateadmission/1m41sMaven,
exit02:03:45Z. Exact6XML identities/Java25/media inspected:34/3/26/17/7/11.
Retail3 reachedactual new source/byte/decode/lazy/after-close assertions; no
aborted media gate was counted as a pass. Frozen originals remain external.

Root's new CdFmSoundAssets uses only the existing actual CdProgramAssets/MMD
pipeline. It requires observed knownUSA whole-content plusheaderUSA and exact
MDINIT file/hash/header/copy operands/bank. Native file1DA/length1AD6 detaches
6870 origin-zero Z80 bytes; no934 unresolved relocation bytes or1C00 RAM is
padded. LE pointer rows90..DF,79 declared priorities/80 native reads and AB's
backward AA voice addressE6A stay distinct. DF's native priority read is real
byte4 at7DA. Raw header addresses do not admit a universal80-ID parser (D5 is
nonordinary). Assets remain uninstalled: no stock preparation/source stream,
clock/driver/synthesis/CDDA/PCM/rewind change or sound-playback claim.

Root FM tests136lines/hash2e2647ce froze01:52:41.643680Z before production;
56213 test-compile RED missingnewclass/noJUnit;125line production9574f076 first
write01:54:58.064377Z. Root3191 leanactualmedia selected newFM assets/MMD image/
ProgramAssets/RetailReleases/ModAPI:39/0/0/0 (7/5/4/12/11), immediateadmission/
1m33sMaven, exit01:56:32Z. ExactXML/Java25/media/backend/zeroabortsskip inspected;
newregional cases admitUSA and explicitlyrejectJP/EU withoutclosingmediaowner.
Unit bounds test all coordinate domains, RAM/overflow/ID errors, immutability,
closed-owner readability, fullbank/index/priority/header/voice byte identities.
Whole-file hash gates are authentication precedence, not semanticmutationproof.

One independent bounded review of both units found noP1/P2; it separately read
actualUSA/JP/EU data tracks and corroborated all relevant source/operand/hash
joins and Monitor's1541 reader extent. Neither that review nor the data audits
ranJava/nativecode. Parentexecuted outcomes above are bounded component evidence.
Existing silent audio and actualMonitor19 frontier remain; no route/game/parity
advance follows from these assets. Rawmovement and pureMONITOR touch candidates
are separate next units. All5 source/test files here remain exactfrozen copies.

### Shared raw-object movement and Monitor touch response — 25 October 2026 GMT

Private/local continuation from `ecfa2c2a62448c8d2e731288c28bdbf300b35e4c`.
The raw-object ports delegate to the same instruction integrator used by MODE,
Hurt and Dead. They consume primitive raw fields and an optional actual standing
slot projection, preserve the native read cuts and typed unsupported addresses,
and retain no pool or snapshot. This extracts existing arithmetic without a
second movement implementation. All 120 inherited Movement methods are retained.

The pure PP contact response now admits MONITOR through its existing selected
boundary and same call-cut snapshot. It models signed upward velocity, the
unsigned wrapped Y comparison, zero-only falling-byte write and animation-2
break bounce. It publishes final detached images without sounds, allocation,
score or reward. Body adopts its final actor once before SpecialChunks. An absent
response owner and SPECIAL still stop at their existing typed boundaries. The
existing Frame/Runtime transaction and capture partitions are unchanged.

Tests were authored externally before production. Root12876 tests-only compile
failed at the absent raw APIs; Root4122 tests-only compile failed at the absent
Monitor exits. Neither invoked JUnit. Independent reviews found no production
P1/P2. The Monitor review did find one wrong Body expectation: stationary ground
MODE selects idle animation 5 before touch, rather than retaining incoming 0.
Root preserved the frozen R1 and changed only that expected literal in R2
(`5abc461aa9e48312b2e72eaa1aab7d0db43147891f9176b7aa457a431409ac80`).
Production was not changed to satisfy it. The optional Monitor-specific flipper
no-write overlay assertion remains weaker than the existing Enemy/Item checks;
the raw negative support-packet cases do not cover every non-flipper ID.

Observed Root77298 from this six-file working candidate, immediate admission,
Maven 1m45s, terminal `2026-10-25T02:30:04Z`:

```text
python3 <main-workspace>/tools/testing/maven_queue.py --normal -Dmse=off   -Dtest=TestCdPlayerMovement,TestCdPlayerTouchResponse,TestCdPlayerBody,TestCdPlayerFrameRuntime,TestCdOpeningObjectRuntime,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestModApiSignatureSurface   -Dsoniccd.media.root=<supplied-media-root> -Dsoniccd.opening.egl=true   -Dopenggf.headless.gl=surfaceless-egl -Dsonic2.rom.path=<verified-S2-REV01> test -B
```

All eight completed XML reports were inspected: Movement265, Touch130, Body237,
Frame235, Runtime97, WorldLoad46, EGL12, API11: **1033 tests, zero failures,
errors or skips**. Java25, actual media, EGL opt-in/backend and the verified S2
SHA-1 were checked. The retail EnemyPoints test and all twelve EGL cases ran.
The imported sources received fresh timestamps before compilation. This is
bounded CD-consumer validation, not a broad-smoke, native-parity, Monitor-body,
item-reward or completed-game certificate. The unchanged 400-Right observation
still reaches unowned Monitor ID19 at physical slot59.

External frozen handoffs/reviews remain under the private task directory:
`object-motion-port-codex.md`, `object-motion-port-review.md`,
`monitor-touch-response-codex.md`, `monitor-touch-response-review.md`.
The review's P2 is reconciled above; original mirrors remain unchanged.

### Monitor picture bindings and closed shoes reward port — 25 October 2026 GMT

Private/local continuation from `37ec5b96faa195cbad3387fc712b39da95866e7a`.
The mapping factory joins all twenty authenticated Monitor/time-post frames and
eight odd header-free item roots. A separate thirteen-span policy adds the82
native Monitor patterns to the prior412, for494 total; previous policies and
logical ranges are unchanged. This is declared development preload, not native
PLC service. The Runtime and World client still select the previous412 policy;
Monitor bodies, item dispatch and natural reward remain unadmitted here.

A shared pure shoes primitive returns the native five stores and ordered typed
SubCPU commands. The Frame validate/apply port compares the actual five input
values and adopts flag, timer and three physics words once at the existing
closed cut, preserving all other state. It changes no capture schema and plays
no audio. Region selects only the1320-USA/1200-non-USA source rule.

Two source-supported corrections preserve the original external freezes. The
R1 author counted only labelled command declarations and omitted the anonymous
`rs.b 1` at System.inc:183. Root R2 changes SHOESMUS6B→6C and FADEPCM81→82 in
production and numeric assertions, with the counter lesson recorded in the
pitfall catalogue. Root R2 tests froze before its corrected production copy.
Independent source review confirms the corrected values and exact five stores.

Root89550 tests-only `test-compile` failed at missing reward/picture APIs; no
JUnit ran. Root90505 selected exactly:

```text
TestCdPlayerSpeedShoesReward,TestCdPlayerFrameRuntime,TestCdObjectMappings,TestCdObjectPresentation,TestCdOpeningObjectRuntime,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestModApiSignatureSurface
```

It ran through the main-workspace Maven queue with `--normal -Dmse=off`, actual
`soniccd.media.root`, `soniccd.opening.egl=true`, surfaceless EGL and verified S2
REV01. Immediate admission, Maven1m52s, terminal `2026-10-25T02:41:47Z`:
**512 tests, five assertion failures, zero errors/skips**. All42 pure reward,
21 mapping,30 presentation,97 Runtime,46 WorldLoad,12 EGL and11 API tests passed;
Frame had248 passes and five new-test failures. All eight XML reports, actual
media, Java25 and EGL properties were inspected. All seven new media-gated
picture methods executed. The original235 Frame cases remain unchanged.

All five failures omitted standing MODE's independent FM_CHARGESTOP call
(Sonic/Main.asm:1029-1037), before Display services the shoes timer. Root R3
changes only three exact sound-list assertions inside the same two new methods:
ChargeStop alone, or ChargeStop then the optional expiry level music. This also
corrects the later nonexpiry assertion that the first failure had hidden.
No production or fixture change was needed. Root34057 reran precisely those two
methods/five invocations with `--lean -Dmse=off`: **5/0/0/0**, immediate admission,
Maven25.962s, terminal `2026-10-25T02:45:16Z`; exact XML identities/Java25 checked.
Restore/replay, actual next Display1320→1319, DMA byte content and expiry-order
checks all completed. The512 red batch and repaired-five result are separate
observations, not a fabricated green512 run.

Independent picture and shoes reviews have no remaining P1/P2 objection to
these final sources. Optional gaps remain: explicit static rejection vectors
for every post/HUD first piece; an inherited wrapper-identity inequality; and
requiring both expiry-effect indices to be present before comparing order.
Original R1, Root R2 and R3 mirrors remain preserved externally. Full hashes and
source citations are in the four matching private handoff/review reports.

A separate read-only route probe, Root44900, observed the unchanged Right input
route stop on attempted call324: ID19, whole59, X2128/Y728, subtype4, layer0,
savedFlagsID45, routine0. Its one actual-media case passed with no skips; terminal
`02:35:56Z`, Maven25.439s. The first probe failed to compile because my diagnostic
used a nonexistent unsignedWord accessor; I corrected it to centreX/centreY.
The temporary diagnostic was then removed byte-for-byte. This identifies a live
shoes Monitor, not a natural contact, break or reward. It adds no runtime route
predicate. Broad-smoke/full-game/native-playback/regional-parity claims remain
excluded; the prior broad baseline-attributed failures remain unchanged history.

### Source-ordered Monitor and item body components — 25 October 2026 GMT

Private/local continuation from `eddee5d17d0464a2eb058b7c3b1d9d7823d6ef41`.
New ID19 Monitor and ID1A item owners use the existing saved bank, shared raw
movement, actual plain Solid and object floor/animation/DrawObject functions.
Exactly three storage aliases expose raw control2C, stand link3D and deletion
word1E. No Frame, Runtime, global store, renderer or existing owner changes.
Native prefixes stay live when a later callback fails; effect lists describe
completed work and are never replay instructions. The Runtime binding remains
future work, so normal gameplay still stops at the ID19 dispatch.

The Monitor handles routines0/2/4/6/8 and monitor subtypes0..7. It preserves
sprite visibility/read order, timeStop's animation-only gate, actual saved-byte
addressing, live item-then-explosion allocation, dirty slot bytes and the direct
Break draw. Item preserves byte-wrapped/signed-relative static-pointer math,
old-negative-velocity motion, reward on clean call33 after32 moves, deletion
word29 and the returning DrawObject after deletion. Only source kind4 reaches
a mandatory synchronous award callback; other rewards and posts/HUD remain
typed boundaries. Real Frame rewards, sound ordering, physical child scheduling
and authenticated World pictures are not certified by these component tests.

The original external tests preceded production and remain frozen. Root R2
repairs only the author's five known test groups: invalid compound var syntax;
two expected final DrawObject bit7 mutations; explicit offscreen input for the
time-attack despawn test; and next-call SAT visibility input in the layer test.
The harness models DrawObject, not the later DrawObjects publication. Root68205
tests-only compile failed at absent body/alias APIs, with no JUnit execution.
The exact original three production files were then imported freshly.

Observed Root81938, queued `--lean -Dmse=off` exact selection
`TestCdMonitor,TestCdMonitorItem,TestCdObjectPool`, no optional media dependency:
**146 tests, one failure, zero errors/skips**, immediate admission, Maven1m20s,
terminal `2026-10-25T03:00:47Z`. All59 Item and12 pool cases passed; Monitor
74/75 passed. All three XML reports and Java25 were inspected. The independent
review found the same remaining test defect: a fixture with only push bit5
changed the player but no object byte, so skipping the unchanged object image
could not trigger the expected missing-publication rejection.

Root R3 changes only that method's first subcase to matching object/player
standing bits and link40. Native GetOffObject now clears the object's bit3
(Object Solid.asm:15-55); StopObjPush still only clears the player's push link.
This creates the intended unequal object image without altering production.
Root54605 reran that exact method: **1/0/0/0**, immediate admission, Maven24.589s,
terminal `2026-10-25T03:02:30Z`; exact XML/Java25 checked. Both the object and
other-slot rejection subcases completed. Keep the146 red run and repaired-one
result separate. No broad repeated run was needed for this test-only change.

The source review found no production P1/P2; its remaining test P2 is reconciled
above. Original R1, Root R2 and R3 files remain externally preserved. Final Root
Monitor test SHA-256 is `32fe83d7bf5a73a118f219109adb769f146726402a6ec589700effe44b84a45a`.
Optional gaps remain: negative relative mapping word, final saved-bank byte
vectors, and the real adapter/physical pass/World render obligations. Exact APIs,
all134 declared component shapes, full native citations and immutable hashes are
in private `monitor-bodies-codex.md` and `monitor-bodies-review.md`.
No native/region/whole-game, natural contact/break/reward or audio claim is made.


### Live Monitor reward, 494-pattern World and detached FM retention — 25 October 2026 GMT

Private/local continuation from `6f8f47e1333643e36b6abeed05bf432031e651a3`.
The real physical ID19/1A walk now composes the existing Monitor/item bodies,
shared Solid, raw gravity/floor, saved-object bank and single Frame shoes owner.
All96 call-cut slot images and the expected actor/globals are checked before
publication; pool images commit before the one Frame patch and ordered sound
requests. Falling writes the primary angle directly once; the legacy external
angle adapter is not also called. The MMZ global-layer read remains a typed
boundary before body entry. Deleted-item DrawObject can report live ID0; the
shared SAT builder owns skipping it. Completed metadata is never replayed.

One actual Monitor/time art bank is retained in the common factory. Both cold
admission and now-serviced conversion select the existing thirteen-span policy;
the World reserves and removes all494 patterns. Runtime Snapshot ownership is
unchanged. Other item rewards, posts/HUD/warp, reserved powerups and unsupported
regions remain explicit boundaries. Source-only Runtime/World review found no
production P1/P2; the original World preflight P2 was confined to a test fixture.

Tests-only Root76438 failed to compile at absent bank/sound APIs, no JUnit ran.
After exact production import, Root54190 ran all121 Runtime cases (97 old+24 new):
**121/0/0/0**, immediate admission, Maven1m23s, terminal03:18:28Z; actual media,
Java25/backend and exact XML were checked. The original Runtime/test mirrors are
unchanged (`827c28f2...`, `87201b63...`).

The new constructed actual-EGL component proof keeps fixed source-backed assets
and the actual GameLoop. It clears all96 dynamic slots once, seeds one subtype4
Monitor40, and stages only Sonic's contact and move-away states at closed cuts.
Those operations are disclosed in the test, capture construction metadata and
outcome. The actual Body touch precedes same-pass Break; earlier item32 and
explosion33 wait.32 old-velocity rise moves precede real award call33: flag1,
USA timer1320, physicsC00/18/80 and command6C. Next actual Display gives1319.
The alternate explosion creates no Points/Flower; item Delete30 and the genuinely
earned expiry both run. Pre-break and nonzero-award full/course restores compare
complete state and framebuffer on every replay row. Expiry is forward-only.

Independent counted/static SAT, decoded tile nibble/current-palette opaque ARGB,
transparent-terrain and omitted-visible-tile negative checks cover the rendered
geometry. At teardown all494 actual tile versions are installed as witnesses,
including unused suffix tiles, then all must be absent after module retirement.
This is retirement coverage, not evidence that every tile drew. PASS is written
only after all assertions and session/graphics/boot closure.

Root47242 and diagnostic75238 first failed the original coarse solidity-mask
preflight before any contact. An actual top-solid block at x168/y1907 had floor
distance11, so its selector flag did not imply occupied space. Root R3 narrows
the precondition to the actual glyph/motion envelope and actual shape distances;
coordinates, physics and downstream pixel/reward assertions are unchanged.
Root91135 then exposed a missing expected ChargeSound: the native short-floor
CheckCharge branch requests ChargeStop even at charge0. Root R4 changes only
that new sound-helper expectation. Production was not adjusted to a fixture.

With the old412 client, Root9419 reached the intended cleanup RED after the
contact/reward/pixel/replay/expiry checks: pattern131484, the first suffix tile,
remained in the atlas. With the exact494 client Root80562 passed **1/0/0/0**,
immediate admission, Maven1m24s, terminal03:43:49Z. Exact method/Java25/media/EGL
properties and zero skips were inspected. Five labelled ENGINE PNGs, input,
state CSV, construction and final outcome stay in the private task capture
root; quiet-hours visuals were queued, not emitted. This is constructed engine
coverage, not a natural contact, native palette/priority/PLC/audio/timing,
regional or whole-game parity claim.

The unchanged400-Right route now finishes. Root5317 failed only its stale
unowned19 expectation. The replacement asserts actual call324 Monitor admission
at whole59/subtype4/saved45/X850/Y2D8 and replays every row324..400 through full
and course restores. A bounded ordinary-input probe then observed the next
unowned dispatch on attempted call518: **ID0D, physical37**, player wordY65482.
The first probe's error handler accidentally called the module's live-owner
getter after its fail-closed exception; retaining the already-owned Frame
reference exposed the original exception in Root46997, **1/0/0/0**, terminal
03:56:06Z. The temporary diagnostic was removed. A permanent regression checks
ID0D/routine0/subtype1/X96F/Y318 and both closed owners. No natural Monitor hit
or shoes reward was observed on this Right-only route.

Stock preparation separately retains `Optional<CdFmSoundAssets>` from the SAME
still-open authenticated programs used for the stage, before try-with-resources
closes them. USA requires presence; Japan/Europe explicitly require absence.
Release, stage and FM full-disc identities must agree. The existing source
identity and three exact named streams remain unchanged. The bank's readable
6870-byte slice shares its privately copied7514-byte backing; no file handle,
source fallback, duplicate payload, driver installation or playback is added.
Tests-only Root78691 failed to compile at pending getter/three-argument API,
with no JUnit. Exact production mirrors were then imported; independent review
found no P1/P2. Negative-constructor guard reachability remains explicitly
limited by private authentic owners, not a reason to invent forged fixtures.

Combined affected validation on HEAD6f plus the eight integrated Java files:

```text
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 <main-workspace>/tools/testing/maven_queue.py --normal -Dmse=off '-Dtest=TestSonicCdStockPreparation,TestCdFmSoundAssets,TestCdProgramAssets,TestCdMmdImage,TestCdRetailReleases,TestGameDataSourceSessionOwnership,TestGameDataSourceSharedFetches,TestModApiSignatureSurface,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestCdPlayerFrameRuntime,TestHeadlessAudioOwnership,TestHeadlessGameBootAudioIdentity,TestSonicCdTerrainPlayerLoad,TestSonicCdTerrainPlayerBoot' -Dsoniccd.media.root=<supplied-media-root> -Dsoniccd.opening.egl=true -Dopenggf.headless.gl=surfaceless-egl -Dsonic2.rom.path=<verified-S2-REV01> test -B
```

Root20780: **424 tests, zero failures/errors/skips**,15 current XML identities,
immediate admission, Maven1m17s, terminal `2026-10-25T04:00:10Z`. Counts: stock17,
FM7, programs4, MMD5, releases12, source ownership5/shared fetches10, API11,
World47/EGL13, Frame253, audio ownership2/native boot3, terrain load27/boot8.
Every XML had Java25 and actual media/EGL settings; all regional retention,
all13 actual-EGL methods, both natural-route regressions and no-device cases
executed. This proportionate affected-consumer batch covers the concrete CD
source/World changes; it is not smoke/full-suite/native audio or full-game
certification. Earlier broad baseline-attributed failures remain history.


### Time Post and FM queue components, closed direction port — 25 October 2026 GMT

Private/local continuation from `5d2bca4027ee1b0e52cf1a1336660a89b9a03b32`.
New Time Post and main-CPU FM queue components remain separate from Runtime,
World, warp, driver installation and playback. One overlapping State byte alias
names post spin at2A. The direction port writes only the existing Movement gate;
there is no new global store, snapshot field, sound effect or actor mutation.

Time Post uses the existing authenticated full Monitor family, one saved bank,
shared animator and native wrapper. It preserves Init/Main/Spin/Done order,
nonzero-contact clear before an MMZ layer read,60-byte spin and dirty/unwritten
bytes. Missing reached layer is typed rather than supplied as zero. Past writes
FF; Future writes FF then01 through synchronous expected-value callbacks. Voice
77/76 is synchronously requested before wrapper Draw/despawn, preserving actual
prefixes if a later callback fails. Returned effects describe completed work and
are never replayed. HUD subtypeA remains a genuine reserved-slot boundary;
activation does not bypass the existing unowned warp continuation.

Malformed animation reads preserve only native reset/decrement/speed/index
prefixes before a finite-view stop. The existing animator owns successful
transitions once. The retail family does not reach every generic FE/FD/error
branch; no fallback script or enlarged address authority was added. The post's
saved helper uses the exact766-byte linear bank and the invocation's updated
direction for a later warp-despawn calculation.

The FM owner is pure three-byte main queue state plus UNCOMMITTED transition
plans. PlayFMSound uses low d0 byte and first zero slot, preserves zero-to-zero
stores and drops a fourth nonzero request. Flush always brackets Stop/Start,
then interleaves each nonzero fixed Z80 write and corresponding main-byte clear
in source order.1C09 is sndPlay;1C0A/1C0B are the first two arbitration cells;
1C0C is never written. Explicit empty values are not native cold-RAM authority.
No driver RAM, arbitration, bus/clock service, synthesis or playback is installed.

Both external test files were frozen before production. Tests-only26454 failed
to compile at absent owner APIs; no JUnit ran. After exact import and the sole
State alias, queued Root1753:

```text
python3 <main-workspace>/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestCdTimePost,TestCdFmRequestQueue,TestCdMonitor,TestCdMonitorItem,TestCdObjectPool' test -B
```

Observed **251/0/0/0**, immediate admission, Maven1m20s, terminal04:06:29Z.
All five XML identities/Java25 were checked: Time Post87, FM queue18, Monitor75,
item59, pool12. Independent static review found no P1/P2, with separate callback,
reserved-slot, animator and sparse-plan coverage limits. Its P3 off-by-one subtype
citation is corrected only in the integration copy; external originals remain
frozen. An initial author output-location error moved only the two known-created
Time Post Java files unchanged from checkout scratch to the authorized external
root before validation; unknown files were untouched.

The Frame direction port preflights existing open/idle/closed-frame phase,
unsigned expected/new bytes and exact current value, then builds replacement
Movement/Body environments and assigns once. Same-value stores are legal.
All actor/Display/timer/art/input/clock values and other gates survive. Its six
new methods declare11 invocations; the253 inherited invocations are unchanged.
Tests were frozen04:11:03.429688Z before production. Tests-only71291 failed at
absent methods, no JUnit. Production was authored once at04:12:38.139429Z.

Root9159 exact Frame class: **264 tests/one failure/zero errors/skips**, immediate,
Maven1m21s, terminal04:14:00Z. The one new test wrongly expected standalone Frame
closure after unsupported Body. Existing runBody's finally consumes the failed
turn and clears pending/reentry state; its World adapter owns fail-closed
admission. The typed TIME_WARP_ACTIVE result and all value checks already passed.
Only that test's lifecycle assertion/name changed; production stayed unchanged.
Root28938 reran the exact repaired method: **1/0/0/0**, immediate, Maven1m18s,
terminal04:17:15Z, exact XML/Java25 checked. It now proves nonzero shared restore,
replay, unchanged actor, typed next-Main warp stop and no repeated Body without
another service. The264 red run and repaired-one pass remain separate results.

These are component/value-port checks, not real Time Post touch/activation,
reserved HUD execution, World restore, native sound timing, demo/regional or
whole-game parity. The existing dynamic-only pool is not widened. Runtime
binding and the selected SPECIAL collision response remain separate work.

### 25 October, 05:11 GMT — SPECIAL flags, live Time Post and reserved storage

Local continuation from52de6329b; no remote/main-workspace integration or public
publication. The real selected SPECIAL response now owns exactly raw C0-group
selectors1F/17/21/23 and roll-only2F/3A/3B. Direct flags increment only selected
collision-status byte; roll flags require actual post-Animator animation2.
The same call-cut/actual ResponseContext is required. Other SPECIAL branches,
HUD and warp continuation remain typed missing owners. No record shape, scan
order, player/world write or new inner hurt/invincibility gate was added.

The live ID19 dispatcher reads current subtype: below8 goes to Monitor, otherwise
Time Post. One Time Post shares the actual Monitor assets and placement owner.
Its synchronous callbacks validate current physical identity, source activation
prefix, existing Frame phase and expected direction. The FF and optional Future
01 stores use the one existing gate-byte port; the voice ledger append happens
before wrapper draw/despawn. Returned completed effects are never forwarded
again. A later failure retains completed saved/object/direction/voice prefixes.
No second direction store, audio handshake/playback or next-Main warp owner is
introduced. The Snapshot partition is unchanged by this composition.

Separate reserved storage covers actual whole slots1..31, excluding Frame0 and
dynamic32..127. Images/captures are immutable and owner-bound; complete restore
preflight and full-bank value CAS precede any write. Its clear factory names the
native ClearObjects cut, not completed HUD/title initialization. This checkpoint
does not yet install that bank into Runtime or admit secondary-player gameplay.
The horizontal Door asset join adds lazy14-byte script/28-byte mappings at native
23F02A/23F038, one exact two-frame mapper registration, and reuses the existing
12-tile Door bank. All prior asset views, eager startup loads and494-pattern
policy remain. Equal JP/EU bytes at these ranges do not admit those owners.

Tests-first and observed validation, all on this local objects tree:

- Reserved bank/assets tests-only82031 failed compilation before absent APIs;
  after exact production import,13203 ran105/0/0/0: bank22, assets39, retail6,
  pool12, visual26. Actual media, both regional parameter sets, Java25 and all5
  XML identities were checked. Immediate admission, Maven1m29s,04:31:00Z.
- Mapper70643 with old production failed at unmapped23F038, the intended
  behavioral RED. Exact one-line registration followed. Mapper's25 cases
  passed in94188 with actual media; the aggregate run below was red elsewhere.
- SPECIAL61324 failed compilation before the new predicate/exits.94188 ran
  Response167/Body272/Mapper25:464 tests, one failure, one error, zero skips.
  Both defects were new Body fixtures: animation target255 is outside the
  actual60-script asset, and a wide hitbox atX64 wraps before overlap. RootR2
  uses last valid59 and moves actor/selected/ring together toX100hex, preserving
  selector coverage and assertions. Narrow63080 reran both methods:8/0/0/0,
  immediate, Maven28.400s,04:48:03Z. No production repair or464-green claim.
- Time Post Runtime90996 tests-only compile failed at absent TimePostSubCpu;
  no JUnit ran. Imported exact production and the two known DrawDone-bit7
  assertion corrections.23918 ran140/one failure/zero errors/skips, immediate,
  Maven1m23s,05:05:09Z. The new replay test omitted the earlier real MODE
  ChargeStop. RootR3 replaces its count with exact ordered ChargeStop+voice;
  other prefixes/replay assertions and production are unchanged.
-8738 executed that repaired method1/0/0/0, Frame264/0/0/0 and WorldLoad47/0/0/0.
  Its13 EGL cases failed before engine launch because Root's command inherited
  DISPLAY=:0. The mistaken extra TestSonicCdApi selector matched no class and
  is not API coverage. Total325/13failures/0errors/skips, Maven1m22s,05:08:32Z.
-39776 reran only the affected13 EGL cases with DISPLAY/WAYLAND_DISPLAY unset,
  plus the real TestModApiSignatureSurface11:24/0/0/0, immediate,
  Maven45.165s,05:10:19Z. All current XML identities, Java25.0.5.0.2, supplied
  media, explicit EGL opt-in/backend and S2REV01 path were verified. No images
  were viewed/emitted during the quiet window.

Representative commands (placeholders protect user-supplied paths):

```text
python3 <main-workspace>/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestCdReservedObjectBank,TestCdR11aObjectAssets,TestCdR11aObjectRetailAssets,TestCdObjectPool,TestCdObjectVisualAssets' -Dsoniccd.media.root=<supplied-media-root> test -B
python3 <main-workspace>/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestCdPlayerTouchResponse,TestCdPlayerBody,TestCdObjectMappings' -Dsoniccd.media.root=<supplied-media-root> test -B
python3 <main-workspace>/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestCdPlayerBody#specialRollReadsActualAnimatorAnimationWithoutUsingRollStatus+specialUnownedSelectorsRemainTypedWithActualOwner' -Dsoniccd.media.root=<supplied-media-root> test -B
python3 <main-workspace>/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestCdOpeningObjectRuntime' -Dsoniccd.media.root=<supplied-media-root> -Dopenggf.headless.gl=surfaceless-egl test -B
python3 <main-workspace>/tools/testing/maven_queue.py -Dmse=off '-Dtest=TestCdOpeningObjectRuntime#timePostNonzeroDirectionSpinSavedSoundAndAllExistingPartitionsRestoreAndReplay,TestCdPlayerFrameRuntime,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestSonicCdApi' -Dsoniccd.media.root=<supplied-media-root> -Dsoniccd.opening.egl=true -Dopenggf.headless.gl=surfaceless-egl -Dsonic2.rom.path=<verified-S2-REV01> test -B
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 <main-workspace>/tools/testing/maven_queue.py -Dmse=off '-Dtest=TestSonicCdOpeningWorldEgl,TestModApiSignatureSurface' -Dsoniccd.media.root=<supplied-media-root> -Dsoniccd.opening.egl=true -Dopenggf.headless.gl=surfaceless-egl -Dsonic2.rom.path=<verified-S2-REV01> test -B
```

Independent static reviews found no remaining P1/P2 in the reserved/assets,
SPECIAL/mapper and Time Post Runtime/Frame candidates. Their disclosed fixture
findings were source-attributed and fixed only in Root copies. The Frame comment
now cites163-168, including the first FF store. Frozen originals remain intact.
The140-red plus repaired-one pass remain separate results; no140-green claim.
The Frame264 pass supersedes its earlier standalone-lifecycle fixture failure.

These are bounded component/constructed Runtime and existing World/EGL checks.
Time Post spin continuation with absent primary is not a playable warp flow.
Horizontal Door body/real reserved-bank Runtime binding, splash, secondary
player, HUD, native clocks/PLC/audio and regional parity remain unfinished.
The ordinary Right-only frontier remains attempted518 at live ID0D whole37.
Historical baseline-attributed broad-smoke failures and later-coverage limits
remain as previously recorded; no new broad/full-suite or whole-game claim.

### 25 October, 05:17 GMT — horizontal Door body component

Local continuation from33dc1d9d4. ID0D now has a separate normal horizontal Door
body, borrowing the actual authenticated14-byte script/28-byte mappings and the
existing shared12-tile bank. Init falls into Main in the same dispatch; Main
checks the real primary then reads actual reserved whole1 even when its ID is0.
Only the actual all64-byte cleared secondary image is admitted today. The body
calls the real shared TopSolidObject for that image and rejects secondary
player/global/other-slot writes or alias-sensitive effects before commit. Active
secondary stand-link0 aliases primary natively; it is deliberately not modeled
by passing secondary values through the existing primary-only Frame patch.

Each helper uses one ephemeral actual dynamic capture, mandatory synchronous
preflight/commit and actual live readback. The body checks object/other-slot
publication and unchanged inactive secondary storage. Native opening uses signed
negative Y velocity and wrapped BMI/unsigned rectangle edges. Subtype0 successful
first-fit allocation publishes dirty-preserved splash0B before FM_A4; full pool
or nonzero subtype requests no sound. Splash dispatch itself remains unowned.
Shared animation consumes ten frame bytes, FC on call11, then Reset on the next
dispatch. Wrapper draw reloads actual bit7 before despawn/saved-bank work.
Completed effects never instruct another commit, allocation, draw or sound.

Both new files were frozen externally after tests-first authoring, unchanged on
import: body SHA256d68b591fb67cfc93e809f15dd43a1997a4f8014d73e1a6a72f4d336c17e7dbfb,
test718d160ab473d94ce2e42a3b2dfddf5d7be11c3e332cc3ee1ac3bf5eaa431d7d.
Tests-only98819 failed compilation at absent CdTunnelDoor; no JUnit ran. Exact
production import followed. Queued focused validation:

```text
env -u DISPLAY -u WAYLAND_DISPLAY python3 <main-workspace>/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestCdTunnelDoor,TestCdSolidObject,TestCdObjectPool' -Dsoniccd.media.root=<supplied-media-root> test -B
```

Root47941: **195/0/0/0**, immediate admission, Maven1m25s, terminal05:16:01Z.
Current XML identities and Java25.0.5.0.2/media property were checked: Door95,
Solid88, pool12, no skips. These are component/native-format cases; Solid's media
case binds the real asset owner but is not a route or rendered Door proof.

Independent static review found no concrete P1/P2. Its P3 correctly notes the
first-helper globals fixture has no actual global write on blank terrain;
that assertion proves unchanged-globals continuity, not nonempty angle-store
publication. Actual Runtime commit, reserved bank capture/restart, live route,
real Frame phase/CAS, pixels, splash, native audio/PLC and active secondary
remain separate work. No runtime admission,95-case native parity, whole-game,
full-suite or public delivery claim follows this focused component pass.

### 25 October, 05:47 GMT — live horizontal Door, reserved replay and FM arbitration

Local continuation from05c5c47c6. Runtime now constructs one actual reserved bank
at the explicitly declared DEV ClearObjects-equivalent cold/restart cut. Later
native HUD/title initialization is still omitted. The actual bank, including all
31 complete images, is captured once and fully validated before any composite
restore write. Immutable reservedState reads and guarded CLOSED_CUT
writeReservedImage ingress expose no second store or mutable bank getter.

Live ID0D uses the real horizontal Door body, placement, authenticated views,
shared Solid owner and existing494 policy. Every helper preflight compares the
same single96-image dynamic cut and actual primary/gates/globals. Commit
validates all images and PRIMARY's Frame patch before S4, applies S3 once only
for PRIMARY, forwards shared FM once and returns actual live values. SECONDARY
checks the real whole1/all64bytes and strict no-write shared return; it never
feeds its projection through the primary Frame port. All old callback guards
include the new Solid/FM cuts. A completed splash/FM prefix remains if a later
real0B or dirty-secondary visit stops. Splash behavior itself remains unowned.

Original Runtime tests are140 byte-preserved cases plus24. Tests-only63332
failed compilation before reservedState/reserved/TunnelDoorFm existed; no JUnit.
Exact production import followed. Root83402 ran164 tests/one failure/zero
errors/skips, immediate, Maven1m24s, terminal05:27:22Z. One new assertion compared
file offset3F038 with actual native pointer23F038. RootR2 fixes only that literal;
production has only the disclosed header-wording correction. Independent review
confirmed the source address and no remaining production P1/P2. The original
164-red result and repaired-one result remain separate.

Both World complete-state comparators now check every reserved identity and all
64 bytes. A new raw-storage-only test keeps actual secondary1 clear, stages
otherwise inactive reserved2..31, mutates bytes, proves the complete-state oracle
rejects the mismatch, then restores and replays seven rows through full/course
captures. It certifies storage/lifetime, not HUD or secondary execution.

Root29195 ran four narrow cases: repaired Runtime SAT/pixel1, World bank1,
ordinary Door518/full-course replay1 and a temporary next-frontier probe1:
**4/0/0/0**, immediate, Maven1m17s, terminal05:33:55Z. The real controller-only
route now completes518 with Door whole37/routine2/mapping23F038/subtype1 at
X96F/Y318. Probe observed first new stop at **attempted540, ID3E, whole52**;
actorX2090/Yword65456/routine2. Native ObjectIndex names ObjPataBata. The probe
was removed; permanent owned518 replay and540 unowned-dispatch boundary cases
replace the stale518 missing-owner expectation. No gameplay tuning or native
parity conclusion follows those engine rows.

The separate pure CdFmSoundArbitration planner models ProcSoundQueue/.ProcSlot
at pin1ecc5f3a. Its six explicit bytes distinguish direct sndPlay1C09,
arbitration cells1C0A/B/C, rValue1C17 and curPriority1C18. A caller supplies the
actual raw R byte; this owner supplies no R-register clock or phase. One R store
precedes three slot calls. Empty/invalid cells return without advancing the
cursor, valid losers still clear/advance, full unsigned priority compares before
the winning7F mask, equal candidates replace, and same-value stores stay ordered.
No FM_END clamp hides raw90..FF priority reads. From the actual authenticated
bank, GetList's LE operand derives DriverInfo and its priority pointer; the
112-byte window includes neighboring header data after79 declared priorities.
DF reads real header byte4. Generic format construction authenticates no source.
All results are UNCOMMITTED plans; no driver RAM, service, bus, track, YM/SMPS,
synthesis or playback is installed.

The26 external tests preceded one production pass. Root appended one actual-USA
fromAssets success case before importing production: programs close before the
new factory runs, base78B proves a nonzero-high-byte LE pointer, all112 bytes
match the actual bank and DF remains4. This closes both optional static-review
factory-coverage gaps without forging an owner. Tests-only45144 failed at the
absent planner API (no JUnit); exact frozen production was then imported.

Combined affected validation, with actual media and isolated surfaceless EGL:

```text
env -u DISPLAY -u WAYLAND_DISPLAY python3 <main-workspace>/tools/testing/maven_queue.py -Dmse=off '-Dtest=TestCdOpeningObjectRuntime#horizontalDoorRealClosedAndOpenSatShareTheExistingTwelveTilesAnd494PatternPresentation,TestSonicCdOpeningWorldLoad#reservedImagesArePartOfFullAndCourseWorldRestoreAndForwardReplay+ordinaryRightRouteRunsTheOwnedHorizontalDoorAt518AndReplaysItsCompletedPass+diagnosticRightAfterHorizontalDoorFrontier' -Dsoniccd.media.root=<supplied-media-root> -Dopenggf.headless.gl=surfaceless-egl -Dsonic2.rom.path=<verified-S2-REV01> test -B
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 <main-workspace>/tools/testing/maven_queue.py -Dmse=off '-Dtest=TestCdFmSoundArbitration,TestCdFmSoundAssets,TestCdFmRequestQueue,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestModApiSignatureSurface' -Dsoniccd.media.root=<supplied-media-root> -Dsoniccd.opening.egl=true -Dopenggf.headless.gl=surfaceless-egl -Dsonic2.rom.path=<verified-S2-REV01> test -B
```

Root15322: **125/0/0/0**, immediate admission, Maven1m46s,
terminal05:45:08Z. All six current XML identities and Java25.0.5.0.2/media/EGL
opt-in/backend/S2 path were checked: World49, EGL13, FM arbitration27, FM
assets7, main queue18 and API11. New bank/518/540 and actual FM-owner methods
were confirmed present with no skips. The temporary diagnostic is absent from
the final tree. Frozen author originals remain unchanged. Independent Runtime
and FM planner reviews found no remaining P1/P2 after the disclosed Root test
correction; no repeated review loop or broad-suite substitute was used.

Impact remains confined to CD owners and their existing load/render/rewind
consumers; no shared engine, public Mod API or build contract changed. Focused
component, full affected World/EGL and signature checks address those joins.
Historical broad-smoke attribution/coverage limits remain, and no new broad or
release-only full suite is claimed. This is a private checkpoint, not whole-game
or regional parity. Active/dirty secondary, splash, actual warp stars/HUD/save,
period loading, native PLC/timing and audio execution remain separate owners.
Pata-Bata data/art/body work follows the observed540 frontier.

### 25 October, 06:16 GMT — Pata-Bata components and rejected simultaneous art plan

Local continuation from2fc88c650. The observed ordinary540 blocker is native
ObjPataBata/ID3E. New authenticated lazy views bind shared18-byte animation at
B51C, normal34-byte mappings atB52E and damaged40-byte mappings atB550. Real
native pointers are20B52E/20B550; both have three frames and both complete shared
scripts. Normal's odd body offset17 and damaged's real final pad remain exact.
The private art bank is50 native tiles3B0..3E2, base23B0, VRAM7600, sourced from
camera PLC4/PLC_Cam2_Full row2. It is not a PLC_Std row or a resident-art proof.
Selector1/subtype2-zero admission is added; old table ordinals/eager startup,
other consumers/banks and USA-only authentication remain. Regional negative
checks do not supply JP/EU gameplay admission.

The body calls DestroyOnGoodFuture first, initializes only routine0 and enters
Main for every other raw routine. Init falls through. X uses long delta, signed
wrapped word range and one reversed add; phase reads the high byte of its word.
CalcSine replaces D0's low word without clearing its old upper word. SWAP then
uses that old delta lane in the Y fraction before register ASR count&63; counts
32..63 sign-fill. The actual player-math owner supplies sine bytes. Anchor Y is
the complete2C/2E long including native initial fraction8000. Native good-future
conversion deletes the whole image, restores only X/Y words, sets ID18 and
skips the body; its sound test observes already cleared flags and emits no FM.
Shared animation, actual draw reload and anchor-based despawn/saved-bank tail
remain ordered. Six new field aliases add no state/store/schema. There is no
Runtime dispatch or Pata-Bata residency in this checkpoint.

Tests-first26384 failed compilation at absent body/asset/alias APIs; no JUnit.
Exact frozen production was imported. Root98778 focused actual-media run:
**140 tests/three failures/zero errors/skips**, immediate, Maven1m30s,
terminal06:01:06Z. Class results: visual26, assets44, pool12 and math14 passed;
retail9 had one failure, body35 had two. The new Pata-Bata retail method's three
regions and body's optional actual-USA factory case executed without skips.

All failures were fixtures, with production unchanged:

- The inherited FloatBlock helper still rejected selector1, now deliberately
  owned by Pata-Bata. RootR2 retains the other unsupported selectors and asserts
  exact23B0 for1/0.
- Two new register/shift methods use large dirty anchors but camera0. Native
  post-draw CheckObjDespawn2 correctly clears those far objects. RootR2 supplies
  the corresponding actual anchor camera and asserts not-deleted; every motion,
  sine, shift and expected position literal remains. Narrow69868 passed both
  body methods; no despawn behavior was suppressed.
- Once the inherited USA path passed the selector assertion, its all-table
  digest manifest omitted the three new enum keys. RootR3 adds their independent
  full-include hashes and checks manifest cardinality, retaining every old hash
  and the complete per-table loop. The source-derived18/34/40-byte digests were
  independently re-derived by static review.

Root69868: narrow five cases, **5/one failure/zero errors/skips**, immediate,
Maven31.274s,06:04:37Z: body2 passed; retail3 reached the missing-manifest defect.
Root96888: exact repaired retail method/all3 regions, **3/0/0/0**, immediate,
Maven29.433s,06:09:21Z. Current XML/Java25.0.5.0.2/media were checked. The140-red,
body2 pass and final retail3 pass remain separate; no140-green claim. One bounded
independent review found no production or remaining P1/P2 after RootR2/R3.
Frozen originals are preserved.

```text
env -u DISPLAY -u WAYLAND_DISPLAY python3 <main-workspace>/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestCdPataBata,TestCdR11aObjectAssets,TestCdR11aObjectRetailAssets,TestCdObjectVisualAssets,TestCdObjectPool,TestCdPlayerMath' -Dsoniccd.media.root=<supplied-media-root> test -B
env -u DISPLAY -u WAYLAND_DISPLAY python3 <main-workspace>/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestCdPataBata#calcSineReplacesOnlyD0LowWordAndSwapExposesThePriorFullDeltaHighWord+positiveSineSignFillsWithZeroInsteadOfJavaModulo32Shifts,TestCdR11aObjectRetailAssets#actualOpenedMediaAuthenticatesBeforeDecodeAndKeepsThreeRegionalTerrainLoads' -Dsoniccd.media.root=<supplied-media-root> test -B
env -u DISPLAY -u WAYLAND_DISPLAY python3 <main-workspace>/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestCdR11aObjectRetailAssets#actualOpenedMediaAuthenticatesBeforeDecodeAndKeepsThreeRegionalTerrainLoads' -Dsoniccd.media.root=<supplied-media-root> test -B
```

**Rejected approach and its kill evidence.** Root initially proposed retaining
all thirteen opening art spans and adding Pata-Bata as a fourteenth source,
544 declarations. Source inspection killed that design before integration:
Mosqui's70 native tiles occupy3A0..3E6 and contain Pata-Bata's entire50-tile
3B0..3E2 span. Their physical union is still494. Different logical bases cannot
make a native-tile-only resolver distinguish the two sources. Correct strict
physical-overlap rejection must remain. A per-object art selector, renamed
physical IDs or draw-time upload would model different hardware behavior.

The external picture worker completed only two independent exact mapper joins;
its presentation mirror is unchanged, and no544 policy exists. The external
Runtime candidate was already authored against that withdrawn factory, frozen,
reported blocked and explicitly NOT imported. It must not be treated as a
usable implementation. No544 cleanup constant or extra50 logical bank is in this
tree. This records the abandoned design and its source proof to prevent reuse.

Next prerequisite is the actual camera-PLC request/service ordering and one
captured resident native window, preserving untouched bytes and old-SAT/new-art
visibility. Reusing the70-tile window with a50-tile write at offset16 is a
hypothesis to check against native producers, full PLC batch writes and timing,
not authority to insert an art-at-draw shortcut. That bounded source audit is
separate. Native camera PLC/residency, Pata-Bata draw/World contact, GPU proof,
ordinary route beyond540, native audio and JP/EU remain unowned. These validated
components do not certify a route, complete bank loading or whole-game parity.


### 2026-10-25 06:34 UTC — independent Pata-Bata mapping joins

On component base `3d7811703fd9fd956fd8ebf91f26cd9caeae9bcf`, register only
actual USA roots20B52E and20B550 through the existing strict byte join. The
normal/damaged three-frame directories, odd bodies, real padding and complete
shared scripts remain source-authenticated. Four added mapper methods preserve
all25 inherited methods and assert declared data, actual USA joins, complete
queue/SAT word arithmetic and JP/EU rejection. No presentation policy, resident
art, Runtime dispatch, cleanup count or logical Pata-Bata bank is added.

Root65519 ran the new actual-USA mapper method before production registration:
**one test/zero failures/one error/zero skips**,25.684s,06:19:14Z. Its expected
failure was unmapped native20B52E. Root93583 then ran the exact mapper plus
existing World/EGL/API consumers: **102/0/0/0**, Maven1m48s, terminal06:22:42Z.
The four reports are mapper29, WorldLoad49, EGL13 and API11. XML properties
confirm Java25.0.5.0.2, supplied CD media, EGL opt-in, surfaceless backend and the
supplied S2 donor; DISPLAY/WAYLAND were unset. The new actual-USA and JP/EU mapper methods executed without skips. This is bounded validation, not a suite or new Pata-Bata World
claim. The code is the mapper-only subset of the frozen partial picture packet;
its unchanged presentation and rejected544 tests/Runtime remain unimported.

```text
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 <main-workspace>/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestCdObjectMappings#actualUsaPataBataJoinsBothExactOwnerRootsAndRetainsEveryPreviousCountedAndStaticRoot' -Dsoniccd.media.root=<supplied-media-root> test -B
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 <main-workspace>/tools/testing/maven_queue.py -Dmse=off '-Dtest=TestCdObjectMappings,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestModApiSignatureSurface' -Dsoniccd.media.root=<supplied-media-root> -Dsoniccd.opening.egl=true -Dopenggf.headless.gl=surfaceless-egl -Dsonic2.rom.path=<supplied-S2-ROM> test -B
```

The completed camera-art audit strengthens the earlier blocker: the first
post-scroll section equality680 selects the complete incrementalPLC9, ordered
Pata50 at7600, Anton22 at8120, Tamabboh59 at8500. Pata aliases Mosqui's70 tiles;
Tamabboh additionally overwrites plant/gap/booster bytes. One70-tile replacement
alone cannot represent the batch. Native UpdateSectionArt/ProcessPLCs occur
after CPU SAT build; the following selected V-int services decompression after
DrawLevel. Queue cancellation preserves bytes already written, ordinary slow
service transfers3 tiles (18 when the addressed scroll-lock high byte is set),
and no leftover budget crosses a completed head. Pause selector8 services art;
lag selector0 does not. The shipped queue Pop bug must remain. These are source
contracts, not observed hardware timing or a new native capture.

Next work owns complete first-batch authenticated producers and one captured
native residency generation with partial service, request/preparation state,
old-SAT/new-art conversion and restart carry. No object-ID pixel override,
art-at-DrawObject upload, overlapping static spans or544 reservation is valid.
The ordinary route still honestly stops at attempted540/ID3E. Later camera
sources, native cold/title/load prehistory, full-USA gameplay, warp/audio and
JP/EU parity remain open. No public integration or disclosure is authorized.


### 2026-10-25 07:00 UTC — native camera-art sources and bounded storage

On mapping base `3da416ae4d3c4272bd69076ee739818317b8857d`, add a lazy six-source
producer bank, current camera-byte residency and the descriptor-buffer primitive.
The complete actual first PLC9 is Pata50,Anton22,Tamabboh59. Source lookup checks
producer plus authenticated source extent while preserving the caller's actual
ordered list and destination, including relocated Anton. Canonical row metadata
is descriptive. Existing Pata/Mosqui/Plant/Ramp immutable decode objects are reused;
Anton/Tamabboh are explicit opt-in decodes. Eager startup, old banks and regional
gates are preserved. No Runtime dispatch, section service or art upload is added.

Native prefill/refill needs separate finite inputs from existing Java Nemesis:
Pata715/714, Anton485/484, Tamabboh1143/1142, Mosqui847/846, Plant393/393,
Ramp678/677 (native/Java bytes). Extra bytes come from actual adjacent retained
MMD data, never padding. The bank exposes native readerInputRange/readerBytes;
existing NemesisRead remains the Java contract. Full supplied-media digests,
endpoint pixels and native full-versus-one-short read tests accompany it. Two
independent Python formulations were author research, not native execution;
Root's retail tests executed the retained production media pipeline afterward.

Residency owns only7400..7CC0,8120..83E0,8480..9000. Explicit DEV seeding supplies
Mosqui/Plant/Ramp and leaves Anton plus the8800 gap unknown. Known masks remain
part of value equality; unknown backing storage never becomes a certified pixel.
Each exact32-byte write changes one current native tile. Pata preserves16 old
prefix and4 suffix tiles; Tamabboh overwrites24 plant tiles, the gap and34 ramp
tiles, preserving4 plant and29 ramp tiles. No source/object identity chooses an
alternate bank. Player/powerup windows remain independently owned.

The PLC buffer owns only96 descriptor bytes: free-source-long scan before the
empty-list branch, source-long then destination-word stores, prefix-preserving
bounded overflow and the shipped Pop bug copying88 bytes while keeping8 tail
bytes. It does not clear the native decoder/count/hint portion; the enclosing
ClearPLCs owner must clear those additional32 bytes and cancel active decode.
Neither component is a complete native allocator or full VRAM owner.

Root12409 tests-only PLC-buffer compileRED found absent CdR11aPlcBuffer; no JUnit.
Root74727 exact positive: **15/0/0/0**, Maven1m18s, terminal06:48:14Z. Root62797
source/residency tests-only compileRED found absent APIs/classes; no JUnit.
Root89669 exact positive actual-media selection: **82/0/0/0**, Maven1m27s,
terminal06:53:37Z: assets47, retail12, residency23. All current reports confirm
Java25.0.5.0.2, supplied media and zero skips. The source-bank actual-USA/JP/EU
method ran all3 regions. Two bounded independent reviews found no P1/P2; the
buffer review notes only an optional stronger nonzero replay-tail fixture.
These are distinct focused runs, not a full-suite, native or World pass.

```text
env -u DISPLAY -u WAYLAND_DISPLAY python3 <main-workspace>/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestCdR11aPlcBuffer' test -B
env -u DISPLAY -u WAYLAND_DISPLAY python3 <main-workspace>/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestCdR11aObjectAssets,TestCdR11aObjectRetailAssets,TestCdR11aCameraArtResidency' -Dsoniccd.media.root=<supplied-media-root> test -B
```

The separately authored native tile decoder has26 passing Root tests at63417
but is not part of this checkpoint's code commit pending its own source review.
The enclosing section/request/preparation/service unit is still being authored
externally. Runtime494/current-generation conversion, previous-SAT visibility,
one captured section state, DEV restart carry and the ordinary540 Pata-Bata
frontier remain to integrate. No544 policy, full native cold/title history,
cycle timing, complete-USA gameplay or JP/EU parity is claimed.


### 2026-10-25 08:27 UTC — native camera PLC service and live Pata-Bata

From local base `beb519543922cfa5e59dcc4fa445ea04287110f6`, compose the previously
separate native tile decoder, section request/service state, current picture
policy and physical Pata-Bata dispatch. The game uses one current residency value,
not an additional Pata art span: all thirteen admitted spans/494 logical IDs stay
unchanged. Immutable source banks remain producers, never alternate live pixels.

`CdNemesisPlcDecoder` prepares the actual header, supplied512-byte code table and
16-bit prefetch without output. A successful call emits one complete tile and
retains pending DBF, run, full XOR history, native read-ahead and the sole remaining
count. Its relevant-low-field representation does not invent incoming CPU high
halves or claim device/midrow captures. Preparation and tile faults retain typed
read evidence; malformed row prefixes are not resumable native continuations.

`CdR11aSectionArt` owns raw section storage, descriptors, relevant saved decoder
fields, budget/count/hint state and the single current residency. Actual tables
select complete lists, preserving high-byte section stores and low-byte carry.
InitPLC cancels prepared work and clears its128 owned CPU bytes without clearing
NemBuffer or written art; LoadPLC appends. The selected slow service transfers3
or18 tiles according to the addressed scroll-lock high byte, fast transfers18.
Destination WORD preadvance happens before decoding, final POP retains the shipped
88-byte copy/tail bug and does not save final decoder RAM, and no leftover budget
crosses into a new head. Unknown producers/writes stop with the completed prefix.

Runtime stores this aggregate once and snapshots it once. Actual post-scroll FG
X is borrowed after successful CPU SAT build; section update and preparation run
before the existing ring/spill tail. The next selector8 services player, powerup,
DrawLevel and camera art before Finish/count, then converts the previous CPU SAT
with NOW-serviced immutable generations. Pause services; lag and omitted CPU tails
retain the corresponding state. Root's Module passes the one live scroll-lock WORD;
terrain-only service remains on its prior path. Old1/2-argument Runtime adapters
are explicitly DEV lock0 compatibility. No second clock/preparation owner exists.

The new picture policy first validates the existing494 bindings, then freezes161
current native versions (Mosqui70, Plant28, Ramp63) while preserving their logical
IDs;333 other versions remain from the frozen prior policy. Anton/gap storage does
not silently add drawable IDs. Pata uses real current physical bytes shared with
Mosqui. Earlier sources/frames remain immutable. All captured fields and pictures
restore by value/assignment without decode, requeue, upload or effect replay.

Restart requires explicit residency/table/section ingress. Represented current art
and code-table bytes carry; queue/decoder/count/budget/hint clear in the new owner.
For the admitted Present/checkpoint0 path, the collapsed title policy clears only
the section high byte and preserves its low byte. The table is the last represented
one: native immediate PLC3/Std1/title history and its Ring table remain omitted DEV
prehistory, not fabricated completed work. Native cold/title/fade/loading parity is
still open. There is no cold-seed fallback on restart.

Tests and source feedback remained separate evidence. Decoder RED58879 compiled
without its absent owner; positive63417 passed26, including actual retained USA
inputs for all six producers. Section RED6653 found the absent owner; positive70246
passed36 ROM-free cases. RootR2 added one actual-USA factory/full three-row PLC9
check after source close:20347 passed1, including the conditional45 selected slow
services, exact current bytes and queued/partial replay. These are service calls,
not a45-game-frame timing assertion. Independent reviews found no P1/P2.

Picture RED21007 found the absent23-argument policy. Positive99539 ran39 with one
new fixture failure: it assumed every write changed pixels. Pinned-source Python
output digests match actual decoded Mosqui/Pata; native3B0 is32 zero bytes in both,
while3B1/3B2 differ. RootR2 pins that equality and both differing witnesses, retaining
all pixel/source/SAT checks. Narrow79818 passed the repaired case; production was
unchanged. This write-versus-pixel distinction is preserved in the hazard catalogue.

Runtime/Carry RED13208 found absent APIs. An ambiguous Root replacement guard then
caught the separate opening/terrain callback matches before writing Root production.
An accidentally started incomplete join56389 was cancelled during compilation
(exit130, no JUnit); the correction scopes only the opening path. Complete62452 ran
213 cases: Carry23 passed; Runtime190 had3 failures/1 error, all in new fixtures,
with all164 inherited cases passing. The four test-only repairs were source-backed:
loaded dirtyD5 clears to55, good-future conversion keepsD5, routine8 reset1 reaches
the zero cut whereas reset0 returns, and raw period81 stops at the existing Frame
SOURCE_FAMILY boundary before either object runs. No period gate was bypassed.
Root32466 passed all four repaired cases. Independent review accepted these repairs.

**Actual ordinary route.** Old540-stop expectation was killed by16623: World49 had
only that stale assertion failure; EGL13/API11 passed. The owned temporary normal
Right probe plus updated EGL checks14472 passed14; the probe was removed afterward.
Pata physical52 initializes at call540, routine2, X2471/Y636, anchorX2472. Source
`Objects (Act 1).bin` row88/offset704 is `09A8027CBEFF4000`: subtypeFF selects the
damaged root20B550, not normal20B52E. Root's first permanent replay test incorrectly
expected normal;38430 passed the new Boulder boundary but failed that one literal.
The source-backed subtypeFF/damaged correction51480 passed both permanent checks.

At540 section0200 has Tamabboh remaining2 and compressed head237D9D;541 completes
all PLC9 work and Pata moves to Y635. Full and course replay from539 compare all
state, CPU SAT and immutable picture across540/541, including all59 current Tamabboh
tiles. Pata remains live at560, X2466/Y628. The next measured stop is **561, ID1B,
physical46, routine0, X2573/Y1184**; later Pata52 is unvisited after that failure.
Native ObjectIndex identifies1B as Boulder (Checkpoint is13). Layout row94 is
`0A0D04A01B004000`. This is engine/disassembly evidence, not a native capture or
full-game claim. Pata remains offscreen on this route segment; new natural Pata GPU
proof is not inferred from the CPU replay or the older constructed EGL scenes.

Final affected-consumer run28479: **582/0/0/0**, Maven1m09s, terminal08:16:47Z.
Classes: Frame264, object pass4, Runtime190, restart carry23, World50, EGL13,
terrain-only27 and API11. All eight XML reports confirm Java25.0.5.0.2, supplied
CD media, EGL opt-in/surfaceless backend, supplied S2 donor and no skips. This final
combined run supersedes the earlier red-and-narrow results for these eight classes;
it does not turn the separate39 picture run or37 section cases into an invented
combined run. Fresh-JVM guards76228: **728/0/0/0**, Maven4m50s,08:22:37Z. Ninety
fresh report identities/mtimes and Java25 were checked, excluding older reports.

The change planner selected3738 smoke classes solely because this CD namespace is
unclassified. The proportionate focused selection covers the bounded CD consumers,
actual media, new callback/rewind/restart and rendering paths plus structural guards;
no shared engine/renderer/physics algorithm, public API or build-selection policy
changed. No release-only full suite or repeated historical broad smoke was launched.

```text
env -u DISPLAY -u WAYLAND_DISPLAY python3 <main-workspace>/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestCdNemesisPlcDecoder' -Dsoniccd.media.root=<supplied-media-root> test -B
env -u DISPLAY -u WAYLAND_DISPLAY python3 <main-workspace>/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestCdR11aSectionArt' -Dsoniccd.media.root=<supplied-media-root> test -B
env -u DISPLAY -u WAYLAND_DISPLAY python3 <main-workspace>/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestCdR11aSectionArt#optionalActualUsaFactoryCompletesAllThreePlc9SourcesAfterMediaCloseAndReplays' -Dsoniccd.media.root=<supplied-media-root> test -B
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 <main-workspace>/tools/testing/maven_queue.py -Dmse=off '-Dtest=TestCdPlayerFrameRuntime,TestCdPlayableSonicObjectPass,TestCdOpeningObjectRuntime,TestSonicCdRestartCarry,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestSonicCdTerrainPlayerLoad,TestModApiSignatureSurface' -Dsoniccd.media.root=<supplied-media-root> -Dsoniccd.opening.egl=true -Dopenggf.headless.gl=surfaceless-egl -Dsonic2.rom.path=<supplied-S2-ROM> test -B
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 <main-workspace>/tools/testing/maven_queue.py -Dmse=off -Pguards test -B
```

Next prerequisite is authenticated Boulder data/body and compatible current-residency
extension. Its19 tiles6E80..70E0 later overlap18 relocated Anton tiles; independent
immutable actor art would conceal that overwrite. The next audit proposes disjoint
unknown complements toward6E00..A400 while retaining existing resident authority.
No Boulder implementation, checkpoint save, complete native load/title/audio/warp,
full-USA route or JP/EU parity is claimed here. All changes remain private/local.

## Source-ordered Boulder and one current art image — 25 October 2026

Local candidate over `0f13fc9c0232f303ad52bc20249c6c3eb745896c`; private
objects worktree/branch only. Main and the retained primary worktree remain
untouched. No merge, push, PR, publication or whole-USA completion is claimed.
The preceding Pata record's Boulder stop is superseded by the exact same-pass
frontier below. Native reference remains `1ecc5f3a` / FixBugs0.

**Authenticated input.** USA MOVE.L at MMD file9618 has operand209654;
BOULDER_MAPPINGS at9654 is14 bytes including the real EVEN zero. One static frame
has pieces(-20,-8),1×3,tile0 and(-12,-16),4×4,tile3:19 tiles total. SelectorB,
subtype2 zero gives0374. The append-only table becomes ordinal39 of40; JP/EU
retain their actual209662 operand and remain unadmitted by this USA object owner.
The retained ART_BOULDER source begins364C2, header8013,270 blob bytes. Java's
shortest finite view is270; native Nemesis needs271, including the real next
FloatBlock byte80 at365D0. Both produce608 bytes with digest
`abdbe20715a2c4784fae851622bb276dc1630abc339efb1e13b99a5ddc4d0c7d`.
Independent media/source-model evidence and Java actual-media tests are distinct;
there was no native decoder execution. The existing source bank appends Boulder
as seventh entry, canonical PLC2 row0 at6E80, preserving earlier cache identities.

**Physical ownership.** Keep the three original region ordinals/contents/masks.
Append disjoint complements6E00..7400,7CC0..8120,83E0..8480,9000..A400. The union
owns432 physical tiles6E00..A400; ownership is not known residency. Both historical
three-input seeds leave the complements unknown. Complete writeTile is the only
new-known operation. Boulder374..386 is a current physical view, not an immutable
actor bank: relocated Anton22@6E00 overwrites18 Boulder tiles and leaves386.
Partial transfer prefixes, unknown masks, old-picture immutability and carried
non-default bytes are asserted. Restart never reconstructs/reseeds the producer.

**Execution and commit.** New CdBoulder Init publishes its native prefix and falls
into Main. Main calls the existing shared SOLID_OBJECT only for incoming negative
sprite flags, once, against one real pool capture. The runtime checks current
physical dispatch/ID/identity, complete same-owner96-slot CAS, all returned images
and exact Frame projection before S4/S3. It writes each once and appends ordered
shared FM requests once. Effects are completed metadata, never replay commands.
Draw returns through the actual pool image before the private native despawn
helper. Placement has no public CheckObjDespawn helper; existing saved-bit/delete
primitives support the source tail without another owner. Init preserves dirty
fractions/velocities/collision type and fields it does not write. No new Frame
field, child, timer, animation or capture partition is introduced.

LogicalBanks appends the fourteenth base, retaining the old13 compatibility form
and shorter constructors. Cold DEV ingress explicitly writes the19 decoded tiles
into the existing current image; restart carries its actual image and masks
verbatim. Two conversion cuts use the separate24-argument513-pattern policy.
Old494 policies stay unchanged. Pixel conversion reads current physical bytes,
never reopens the compressed source or picks a producer by object ID. Static
mapping registration uses the authenticated native root. The renderer's OBJECTS
range was already allocated:494→513 changes its module cleanup count, not the
range reservation or allocator.

**Tests and rejected oracles.** Tests-only68517/16785/42721/45511 fail compilation
on the intentionally missing enum/data/body/bank APIs, not JUnit assertions.
Residency35784 passes31; assets68090 passes67 (52 ordinary plus15 regional), no
skips. Body/picture selection53823 exposes the new C24 checked-I/O mistake before
JUnit; RootR2 removes the unnecessary source-bank accessor from conversion.
Selection90633 runs175 with one old SectionArt boundary fixture failure; Body63,
new picture7, mappings29 and inherited picture39 pass. Expanding residency made
7CC0 owned, so only the obsolete outside fixture moves toA3E0→A400, preserving
native destination preadvance, completed tile and budget/count prefix. Narrow
22342 passes that case. Strengthened picture6633 passes7, including typed unknown
last386 and Animals7100 reached-source/terminal-state checks. Do not call the
175-red plus narrow repair an executed175-green selection.

Runtime64318 runs493 with one new test failure: knownMask() creates a fresh
CdAssetBytes wrapper, so the equality assertion compared identity. RootR2 changes
only that assertion to byte content;9321 passes it. The body author found the
same wrapper-oracle mistake in a saved-bank failure assertion; RootR2 preserves
its frozen original and compares copies. Independent static reviews find no
remaining production P1/P2. Frozen author files are provenance, not Java passes.

World R1 fails test compilation51528: PatternVersion has no version(), and three
additional invented test accessors also require real API projection. Root repairs
only the oracle. Executed70192 exposes a missing ordered ChargeStop after the
ordinary MovementStop; source caller continuation, not a new sound behavior,
explains it.84495/97699 then kill invented shared-entry count/range assumptions.
Native0..7FF entries are implicit governance and need not appear in registered
virtual ranges. Final RootR6 observes both inventories read-only; it registers no
producer to make the assertion pass. These observer/sound lessons are recorded
in the existing measurement-hazard catalogue.

After those fixture repairs, actual old494 cleanup selection17331 fails exactly
at owned ID131566 (OBJECTS131072+494), after geometry/replay checks. This is the
behavioral negative, distinct from earlier compile/oracle failures. Importing
only the two-line513 cleanup change gives99313 two passes: the constructed EGL
method plus an owned ordinary-input diagnostic. Twenty-four actual ENGINE PNGs
and construction/input/outcome remain external. RootR6 removes the test-time
range registration only;98783 passes the same EGL method without recapture.
The final599 run below validates all14 EGL cases on that final source.

**Constructed visual product.** Four independent320×224 USA scenes clear96
slots, seed only Boulder40 and stage Sonic far left. Initial dirty SOLID_TYPE1
survives actual Init, real DrawObjects marks bit7, and next Main/shared Solid
clears it. Literal two-piece SAT, all19 current versions, complete40×32 picture
opaque/transparent geometry, current palette/terrain/Plane B controls and visible
omission negatives are checked. Sixteen cuts each receive full and course restore
and forward state/pixel replay without repeating construction or reloading art.
At teardown only, all513 actual current-policy versions are uploaded/required
present, then absent after module close; existing shared entry identities survive.
That upload is cleanup coverage, not proof all513 tiles were drawn. Captures are
OpenGGF component output, not emulator references, native timing/palette/priority
parity or natural-route contact. PASS follows session/graphics/boot cleanup.

**Natural route.** Call560 leaves Pata52 live at2466/628. At call561 Boulder46
Init actually completes: routine2, X2573/Y1184, map209654,tile0374,sprite4,
priority4,width16,Yradius16,frame0. The later physical47 Spring stops at
DIAGONAL_TILE_SELECTOR/F after publishing its native Init prefix: ID0A,
routine20decimal,X2576/Y1038. Pata52 and later Ring54 are unvisited. The completed
player prefix is X145239552/Y-337408 in16.16, XV1536/YV1520,routine2. The permanent
World assertion retains this exact prefix and later-slot nonexecution. The owned
diagnostic is removed. There is no completed call561 or natural Boulder GPU claim.
This was normal controller input through GameLoop, not a trace/readiness stream;
no trace fixture or physics/aux value drove the engine.

Final affected consumers81471: **599/0/0/0**, Maven1m11s, terminal10:26:02Z.
Frame264, object pass4, Runtime206, carry23, World50, EGL14, terrain27, API11.
All eight XML identities confirm Java25, actual CD media, EGL opt-in/surfaceless
backend and the verified S2REV01 donor; no skips. Fresh guards: **728/0/0/0**,
Maven4m58s, terminal10:34:21Z;90 fresh report identities/mtimes and Java25 checked.
This final combined selection supersedes earlier Runtime/World/Frame/EGL/carry
red-and-narrow results, not separate component runs. No repeated broad smoke or
release-only full suite was launched for this bounded CD-only change. The prior
unclassified-namespace planner breadth remains disproportionate; shared engine,
renderer algorithm, physics, public API and build-selection contracts are unchanged.

```text
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 <main-workspace>/tools/testing/maven_queue.py -Dmse=off '-Dtest=TestCdPlayerFrameRuntime,TestCdPlayableSonicObjectPass,TestCdOpeningObjectRuntime,TestSonicCdRestartCarry,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestSonicCdTerrainPlayerLoad,TestModApiSignatureSurface' -Dsoniccd.media.root=<supplied-media-root> -Dsoniccd.opening.egl=true -Dopenggf.headless.gl=surfaceless-egl -Dsonic2.rom.path=<supplied-S2-ROM> test -B
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 <main-workspace>/tools/testing/maven_queue.py -Dmse=off -Pguards test -B
```

Next is ordinary primary diagonal Spring, the same ID0A family. Source layout
record96 is0A10040E0A0A4000; selectorF gives0490. Its46 tiles9200..97C0 already lie
inside the one current image; a later BigRing write aliases all46, so no separate
immutable diagonal live bank is acceptable. Existing Frame launch fields suffice;
Runtime needs source routine20/22 guards and the body needs native unsigned
multiply/orientation/animation ordering. Actual-USA operands and Java/native
finite reader lengths must be authenticated before admission;780 blob bytes
alone prove neither consumer extent. Full USA, JP/EU parity, checkpoint/warp,
complete native cold/title/PLC timing and audio playback remain future work.

## Primary diagonal Spring and the later Spikes boundary — 25 October 2026

Private objects branch, parent `a18ca432d4605ba8fb19ec10a8da2a17ae3350c3`;
reference remains `1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff`. This slice
supersedes the preceding same-call Spring stop. Main and the retained primary
worktree remain untouched; no fetch, integration, push, PR or public release.
USA completion precedes Japan/Europe gameplay parity. The static default and
explicit opening-object DEV entry remain separate.

**Authenticated data and current art.** Native layout record96 is
`0A10040E0A0A4000`: Spring0A/subtype0A at2576/1038, Present, no flips. USA's
actual Spring3 MOVE.L operand at file98A6 is209BF0; animation LEA at9B0C is209B92.
The existing200-byte mapping exposes diagonal directory80/body86..200, three
frames with7/7/8 pieces; the existing14-byte script remains the sole animation
input. SelectorF/subtype2zero reads0490. Std PLC1 row5 names diagonal art at9200,
header002E/46 tiles. Source extent780 is not used as a guessed consumer length:
independent supplied-media source models establish Java779/native780, both
one-short failures, decoded1472 bytes SHA256
`1ae40f48422bc8b884ae4c85288d3f363737cd3e6979c458b93aac15ed581c8b`.
Native consumes the real trailing00 at34917; neither reader consumes the normal
Spring's following8024 header. Java execution is separately covered below; these
source models are not native CPU/register execution.

`springDiagonal()` is lazy, uses the existing mapping/script, and admits only
selectorF/subtype2zero. The source bank appends eighth ART_DIAGONAL_SPRING,
canonical PLC1 row5, retaining all prior source identities/order. Its46 tiles
9200..97C0 already belong to current AT_9000; no region, actor-selected fallback
or immutable live art bank is added. Later BigRing106 at9100..9E40 aliases all46.
The explicit cold DEV factory writes those46 actual decoded tiles once; restart
takes the supplied current bytes/masks/table/section verbatim. This represents
neither full native Std/title/cold PLC history nor BigRing gameplay execution.

**Execution and publication.** Spring now requires the same source-backed
CdPlayerMath dependency used elsewhere; constructor8 places it before the shared
Solid owner, Assets6 includes diagonal/tile IDs, with no silent legacy default.
Init returns without same-dispatch Solid. Main20 calls one actual SOLID_OBJECT,
accepts its returned d0 or object flag5, then launches. CalcSine(E0) gives
FF4B/00B5; unsigned MULU.W, LSR.L8 and low-word stores produce strong2896/-2896
or weak1810/-1810 before native H/V negations. The exact launch writes only
integerX/Y, XV/YV and flags, preserving fractions and the already-completed Solid
result, including stand links. It adds no angle, animation, move-lock or ground
speed store. Returning FM98 is followed by AnimateObject in that same dispatch.
Ten eligible delay0 tokens precede FC; FC leaves timer0/frame1 and selects24,
then a separate Reset dispatch selects20/frame0/previous1.

Runtime appends bank15 with the old14-argument and all shorter constructors
retained. Guard20 joins the existing SOLID routines,22 joins launch;14 remains
BTM. It reuses the same pure Solid, whole-pool cut/CAS, Frame preflight/apply and
ordered FM ports. No new Frame fields, sound type, phase, capture partition,
clock, physical pass or restore operation is added. Both conversion cuts use a
separate25-argument559 policy. Old513 and earlier policies remain unchanged;
new conversion eagerly rejects unknown current490..4BD and invalid/overlapping
logical spans. The World change is only comment/count513→559 for cleanup of an
already-allocated OBJECTS range, not a larger global allocator reservation.

**Tests, rejected fixtures and review.** Tests preceded their corresponding
production passes; frozen external originals remain unchanged. Author and Root
repairs are separate revisions. Assets50782, picture79857, body5193 and
Runtime89008 were missing-API compilation negatives, not behavioral passes.
Assets40511 ran74/1/0/0: an old fifth F-negative selector assertion was missed;
RootR2 fixed only that contract and narrow65745 passed all3 regional cases.
Picture12153 ran119/1/0/0: the prepared head already includes77 consumed bytes,
so RootR2 pins234659 rather than the original23460C; narrow92597 passed1/0/0/0.
Spring+Solid37765 then passed221/0/0/0 with no production repair.

Runtime R1's bank literal, PlcEntry versus buffer Entry type and five old native
span-start literals were corrected in RootR2. Run92450 reached515/1failure/1error/
0skips: a new observer called a guarded displayed getter during V-int reentry,
and another expected foreign-pool authority from a native Identity that contains
only wholeIndex. RootR3 observes the already-private publication field in the
test and rejects an actual dispatch-index mismatch; production guards are not
relaxed and real Solid foreign-cut rejection remains. Exact two tests16933
passed2/0/0/0. These separate repairs did not make the earlier515 run green.

World89877 did not compile because the new fixture called nonexistent `jump()`;
RootR3 uses `playerJump()`. Run88740 found legitimate earlier player/Spring
overlap and a temporary route assertion that wrongly required a later call.
RootR4 preserves actual painter order and adds renderer-only Spring isolation.
Run53910's permanent route passed, while one initial H scene landed on real
terrain by-3 integer pixels; RootR5 uses one initial air cut at cameraY+56 for
all orientations, asserts both real feet clear, and adds no repeated staging.
Run12468 then found a source-opaque tile with the same RGB as native terrain.
RootR6 keeps actual ordered World and Spring-only native-plane comparisons, and
separately uses a TEST-only clear colour outside the current palette for
observable missing-tile/piece controls. This changes no source glyph, palette,
authoritative Frame, actor, art or production renderer. All revisions/limits are
recorded; neither compilation nor oracle failures are called cleanup negatives.

Final R6 test SHA256 is
`231f911e8fb92852fba1f821e29b9d0d4ad03c8da8f1a341653fd90bce66706a`.
Against old513 cleanup,4807 completed every earlier assertion and failed only at
retained owned ID131585 = OBJECTS base131072 +513, terminal12:03:09Z,
1test/1failure/0errors/0skips. Importing only the two-line559 cleanup gives the
positive result below. Independent static reviews cover assets, body, policy,
Runtime and final World R6; no remaining concrete P1/P2 was identified. Reviewer
execution claims are explicitly attributed to Root rather than duplicated runs.

**Rendered component and ordinary route.** Actual USA320x224 EGL scenes cover
two strengths × four H/V combinations. Each begins from one disclosed closed
cut, clears96 dynamic slots, inserts Spring40 and stages/rebinds Sonic once.
Real Body/DrawObjects/next Main Solid and launch run through GameLoop. The prior
CPU SAT is displayed with NOW-serviced art; source-literal7/7/8 geometry, all46
versions/current-palette pixels, actual native Plane B transparency and the
separate contrast controls are checked. Six cuts per scene yield96 full/course
restores plus state/pixel forward replay through row13, with no reconstruction
or effect replay. At teardown only, all559 actual current-policy versions must
be installed then absent, while existing shared entries retain identity. That
upload is cleanup coverage, not a claim that all559 tiles rendered. Forty-eight
unchanged ENGINE-labelled PNGs, construction/input and PASS-after-cleanup remain
outside the repository. They are not emulator or natural-route captures.

Ordinary, unstaged Right now completes diagonal Spring47 Init in the **same
call561**: routine20, map209BF0,tile2490,speedF600,24x12/frame0. Pata52 Main
retains integerY628 but changes Xfraction8000/Yfraction5FFF; Ring54 initializes.
The next failure is exactly `Unowned CD object ID $26 at live physical slot 61`.
Spikes61 remains routine0 at2643/1038/map0; Runtime and Frame close after that
partial pass. Boulder/player/section prefix assertions remain. Call561 is NOT
complete; no natural diagonal launch or GPU result is claimed. The temporary
probe is removed. This uses ordinary controller GameLoop input, not a trace or
readiness stream, so no trace frontier/sweep was changed.

**Final observed verification.** Selection54167 passed **1036/0/0/0**,
Maven2m54s, terminal12:07:04Z. All17 fresh XML identities confirm Java25,
supplied CD media, EGL opt-in/surfaceless backend and S2REV01 donor, no skips:
assets56+retail18, mappings29, old presentation39, new picture7, Section37,
Boulder picture7, Spring133, Solid88, Runtime228, Frame264, object pass4,
carry23, World50, EGL15, terrain27 and public-surface11. This combined run
supersedes the earlier red-and-narrow component attribution for this final
candidate. Fresh guards64613 passed **728/0/0/0**, Maven5m11s,
terminal12:12:53Z; all90 fresh XML identities and Java25 checked.

The change-based planner selected3741 all-smoke classes because the private CD
namespace is unclassified. The17 known affected consumers and fresh structural
guards are proportionate here: no shared renderer/physics algorithm, public
Mod API, build or selection-policy change. No complete smoke/full-suite or
native-parity certification is claimed; routine full-suite remains release-only.

```text
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 <main-workspace>/tools/testing/maven_queue.py --normal -Dmse=off '-Dtest=TestCdR11aObjectAssets,TestCdR11aObjectRetailAssets,TestCdObjectMappings,TestCdObjectPresentation,TestCdDiagonalSpringPicture,TestCdR11aSectionArt,TestCdBoulderPicture,TestCdSpring,TestCdSolidObject,TestCdOpeningObjectRuntime,TestCdPlayerFrameRuntime,TestCdPlayableSonicObjectPass,TestSonicCdRestartCarry,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestSonicCdTerrainPlayerLoad,TestModApiSignatureSurface' -Dsoniccd.media.root=<supplied-media-root> -Dsoniccd.opening.egl=true -Dopenggf.headless.gl=surfaceless-egl -Dsonic2.rom.path=<supplied-S2-ROM> -Dsoniccd.diagonal.spring.output=<new-external-capture-dir> test -B
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 <main-workspace>/tools/testing/maven_queue.py --normal -Dmse=off -Pguards test -B
```

Next is Spikes26: the bounded source audit identifies actual shared Solid,
player-Y LONG adjustment and direct HurtPlayer, not a fabricated touch scanner
selection. Actual USA assets/readers and narrow returning Hurt publication
still require implementation/authentication. The independent FM PlaySoundID
planner remains frozen externally, unimported/unrun in this checkpoint; there
is no audio-playback milestone. Full USA, JP/EU gameplay parity, native cold/
title/PLC timing, warp/checkpoint/completion and broader route products remain open.

## Pure FM PlaySoundID and shipped StopSound plans — 25 October 2026

Private parent `3626da4f7573b6a5fbb1a8fc207f4219d0a54440`, same reference pin.
This is an independent audio-format foundation, not an audio playback milestone
or a new World service. Existing main request queues and arbitration remain
separate pure owners; gameplay sound ledgers are not replayed or treated as a
consumption cursor.

New CdFmPlaySound models the fixed PlaySoundID/PlaySFX initialization and
StopSound closure, including reached key-off/YM register leaves. It takes an
immutable supplied RAM value and returns an **uncommitted** projected value and
ordered byte-store/YM intents, or a bounded fault with only the reached planned
prefix. A new call from a fault value is a fresh entry, not mid-instruction
resume. There is no Z80 CPU/register/stack execution, timing, bus operation,
track bytecode/UpdateTrack, synthesis, CDDA/PCM, driver lifecycle or executor.

The actual NativeView accepts only the existing private usable CdFmSoundAssets
owner. Full bank length1AD6/SHA and retained instruction/table/leaf signatures
are checked. Source[0000,1AD6) is defined; omitted bank tail, gap, variables and
stack are not invented zeros. Variables[1C00,1D60) are owned but undefined until
explicit resident ingress. A declared-format view instead takes an8192-byte
backing plus exact defined/write/instruction masks and three Layout addresses,
with no authenticity claim. Arrays, masks and result lists are copied;
unknown backing is canonicalized but never exported as certified RAM. Value
equality retains current bytes/masks/ownership and actual-versus-declared source
identity rather than relying on CdAssetBytes wrapper identity.

Every reached read uses the current projection. Words read LE low first, then
the16-bit-wrapped high address; addresses outside8KB do not mirror into RAM.
Writes are permitted only in reviewed data and explicit resident ownership.
Known reviewed instruction writes produce SELF_MODIFICATION; other unreviewed
installed code/data writes produce UNOWNED_WRITE. These are planner support
boundaries, not invented native memory protection. Ingress cannot alter bound
instructions; identical already-defined instruction content is allowed. Reviewed
TrackInitData322..32D, SFXTrackPtrs3BB..3C6, DriverInfo/SFX data6D7..1AD6 and
variables remain separate from protected instruction spans.

FixBugs=0 quirks stay literal: raw dispatch80/81..8F returns,00..7F/E0..FF stops,
90..DF initializes SFX; unchecked channel/table reads are not clamped. Channel07
can select the0921 data address via the shipped pointer arithmetic. Count0
executes256 DJNZ rows, countFF executes255. Aliased writes affect later reads.
StopSound keeps its wrong IX=TrackInitData and stride2, not a sensible six-track
reset:343 clearing stores, six same-value table RMWs,352 total byte stores and79
ordered YM intents,431 steps total. Supplied table overlays can suppress later
track intents without mutating the source. Native source is
`Sound Drivers/FM/_Driver.asm:545-556,670-722,774-995,1050-1088`, with variable
and table layouts pinned in the independent research/review.

The external R1 tests froze before one production pass. Independent static
review found no concrete P1/P2/P3, with an accurately reported gap: its optional
actual-asset helper was not annotated or called by JUnit, and the successful
LE-wrap case omitted the full untouched-byte/mask comparison. RootR2 preserves
all17 original tests, adds that comparison and one real optional JUnit media
case. It constructs the existing retained USA owner, closes its disc source,
then invokes every anchor, real95 initialization and StopSound checks. Missing
media is an explicit assumption skip; the executed command supplies actual media.
No authenticated owner is forged.

Root60128 was a tests-only compilation negative with the production owner
absent, exit1/no JUnit execution; elapsed Maven time was not retained. Importing
the unchanged R1 production SHA256
`9eb0153a09ae41185c282e86fc9bad94957fb07d073dfe08f5cdf7cddf9869e0`
then gives36356 **70/0/0/0**, Maven1m36s, terminal12:30:05Z: PlaySound18,
request queue18, arbitration27, retained assets7. All four fresh XML identities,
Java25/media properties and skip counts were checked. The new actual-USA case
is a completed testcase, not merely a present helper. Public surface89436 passed
**11/0/0/0**, Maven32.152s, terminal12:34:06Z, after26s queue wait.

This bounded new owner has no runtime callers and changes no shared audio
algorithm, interface, build, graphics or gameplay owner. The four actual/declared
format classes and public-surface checks address its consumers; repeating the
whole engine or prior728-guard run would not establish missing lifecycle or
playback integration. Those earlier guards apply to the diagonal checkpoint,
not this later source. No fresh full-smoke/full-suite or native parity claim.

```text
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 <main-workspace>/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestCdFmPlaySound,TestCdFmSoundAssets,TestCdFmRequestQueue,TestCdFmSoundArbitration' -Dsoniccd.media.root=<supplied-media-root> test -B
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 <main-workspace>/tools/testing/maven_queue.py --normal -Dmse=off '-Dtest=TestModApiSignatureSurface' test -B
```

Next audio work must join actual queued requests, arbitration and initialization
at their native ownership/service boundaries, then implement reached track
updates before synthesis/playback. It must retain undefined RAM and support
faults rather than silently booting a fabricated driver. Spikes remains the
separate ordinary route frontier; no audio plan is used to claim that pass or
USA/regional gameplay complete.


## Spikes assets, current pixels and direct Hurt publication — 25 October 2026

Parent is local `8f283a9597999c991a9233c19cf8543fd21b892b`; reference remains
`1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff`. The component results below are followed by
observed live Runtime and constructed World evidence. Final combined consumers
and fresh structural guards passed as recorded below; this remains bounded
validation, not a complete game or regional-parity result.

Actual retained USA bytes authenticate Spikes ID26, MapSpr_Spikes root20C1F0,
full24-byte mapping including its alignment bytes, one static frame with four
size05 pieces, sixteen cells and eight distinct patterns. The actual MOVE.L at
C130 is217C0020C1F00004. SelectorA/subtype2 zero reads0320; PLC_Std1/row0 is
ART_SPIKES at238018 to6400, header8008. The inventory and Java reader need86
bytes; the native decoder requires87, including the real following00 at3806E.
The independent source models agree on256 decoded bytes, SHA256
`75e3391d4045ec96fa925a79bba808a88425444e43d4694f105e7085057c2f72`.
Those finite-reader models are source research, not native execution. JP/EU have
root20C1FE and remain unadmitted. No production asset is taken from disassembly.

The existing source bank appends Spikes as source9, preserving earlier identities
and extents. The one physical store appends AT_6400 at ordinal7, extent6400..6500;
its440 owned tiles are not440 known tiles. Both historical three-input seeds
leave these eight new tiles UNKNOWN. The gap6500..6E00 remains outside ownership.
Only explicit complete32-byte writes establish knowledge; there is no Spikes
pixel bank, automatic restore seed or producer fallback. New current-picture C26
calls unchanged C25 first, then eagerly snapshots every current320..327 tile,
validates logical/native overlap with all fifteen old spans even for an empty
SAT, and appends eight bindings:567 patterns in sixteen spans. All old559 logical
IDs and NOW player/powerup/current-camera versions remain governed by C25.

The shared direct HurtPlayer entry takes the actual source a2 and a fresh complete
same-owner pool cut, then calls the same existing pure Hurt owner below scanner
and Hazard gates. It does not fabricate a HandlerBoundary or replay a touch result.
Frame adds only validate/apply pairs for the full32-bit pre-Hurt Y rewind and the
returning Hurt result. The Y pair compares the complete current Actor, verifies
Y minus signedYV shifted8 and preserves every other field. The Hurt pair checks
the five Frame-owned/read globals and the native exit-specific fields; contact,
world globals, actual96-slot CAS, phase/source identity and sound publication
remain Runtime obligations. All aggregate values are constructed before adoption.
Old Frame and response algorithms/capture structures are unchanged.

CdSpikes preserves Init fallthrough and source order: routine/map/sprite/priority
prefix before selectorA, width/radius afterward; one actual shared Solid call
when unsigned word DY is below32, including incoming bit7 clear. Upright standing
contact uses actual post-Solid values, commits the LONG Y rewind before capturing
a fresh direct-Hurt cut, then reloads live source and Draw output before the
native current-X/saved-bit/despawn tail. Vertical-flip collision83 is a distinct
scanner path. Shared Solid can release a primary-slot stand0 alias; Hurt keeps
post-Solid links and scratch angles. LostRing allocation writes only its native
ID and integer XY over the actual first free image. Ordinary Hurt does not emit
FM93; reached Reset/Kill requests retain their native order. Completed effects
are observations, never a replay program.

Observed component checks, all with Java25 and actual supplied media:

- Assets/store tests-only93124 rejected missing APIs before production import.
  Positive78333 passed117/0/0/0: unit61, regional21 and physical-store35;
  Maven1m32s, terminal12:50:32Z, after20s admission wait.
- Direct-Hurt/body tests-only30593 rejected missing APIs, with no JUnit execution.
  First positive73816 ran628/2/0/0, Maven1m22s, terminal13:18:30Z. Response191
  and Frame314 passed. Two Spikes fixture expectations were wrong: a bored2B
  actor with object bit3 clear reaches Solid.GetOff/d0=0 before StandOn, and the
  reached saved-bank index769/766 throws IndexOutOfBoundsException. RootR3 pins
  that actual GetOff reason/zero return and corrects the exception; production
  is unchanged. Rerun29477 passed all123 Spikes cases, Maven27.061s,
  terminal13:23:24Z. This is505 passed plus a separate repaired123 rerun,
  not a claim that one fresh628 run passed.
- Picture tests-only82106 rejected the absent C26 overload, Maven22.486s,
  terminal13:26:39Z, no JUnit execution. Positive97139 passed77/0/0/0:
  mappings32, section service37 and new Spikes picture8; Maven1m36s,
  terminal13:29:37Z. It exercises the actual Std Spikes source transfers in slow3/3/2
  and fast18 service, immediate final POP without next-head work, current-byte
  replacement and partial snapshot replay. The Root mapper test repair only
  corrects a new prior-Boulder geometry assertion to the actual retained map.

Fresh XML identities, media/Java properties, errors and skips were inspected;
every stated positive has zero skips. Frozen author originals remain external,
with separate Root repair revisions. Independent static reviews accept the final
asset/store, direct-Hurt/body and picture candidates with the documented fixture
repairs; they are not independent execution evidence. A dry change-based plan
selects3744 smoke classes because this private CD namespace is unclassified.
No such broad job was launched; the focused consumers and remaining World/guard
obligations must be assessed against the actual final change. There is no
full-suite, native-parity, natural Spikes contact, GPU or full-USA claim here.


### Live Spikes, actual World pixels and the next route boundary

The Runtime candidate adds only a sixteenth LogicalBanks component, preserving
all shorter constructors and deriving the fifteen-argument compatibility base
as diagonalSpring+46. It caches one actual-pool body with the shared Solid owner.
Nine-source validation and cold-only eight-tile DEV writes precede both C26
conversion cuts. Restart carries the supplied bytes, knowledge masks, table and
section verbatim; no new seed, source selection, service or capture partition is
introduced. All three private publications use the existing mutual guard. Each
complete96-image CAS and Actor/global/image/contact/FM preflight finishes before
pool writes; images, Frame, contact and the already-constructed FM ledger publish
once. A failure in the later Hurt cut retains the preceding LONG Y store.

Tests-only52300 rejected the absent banks16/spikes accessor, Maven25.210s,
terminal13:43:15Z, no JUnit execution. Importing unchanged frozen Runtime
`d74b54de8454a60b47d21d103e0a1ce3d6f32f29c09062eb0d3b9264e3bcb319`
and its257 tests gave9204 **258/0/0/0**, Maven1m25s, terminal13:49:08Z:
Runtime257 plus one temporary actual controller-route observation. All228 old
Runtime test identities remain; only demonstrated bank/source/known-byte and
explicit DEV helper-seed expectations migrate. Independent static review found
no P1/P2 in the Runtime and constructed World packet.

Ordinary controller-only Right now **completes call561**, including physical61
Spikes Init/Main/Draw/tail: ID26/routine2/X2643/Y1038/map20C1F0/tile0320,
priority3/width16/Yradius8/collision0/sprite4. It completes through call581;
**call582 remains incomplete**, rejecting ID13/physical34 before Checkpoint writes.
N ObjectIndex (Act1 Present):26 identifies Checkpoint, distinct from Boulder1B
at34. Physical32 vertical Door and33 diagonal Spring have already initialized
before that stop. Player prefix is X153497088/Y10844672 in16.16, XV1536/YV2696,
routine2/flags2; section0200/count0/queue-head0. Checkpoint remains routine0,
X2760/Y744/map0. Its raw offset28 is subtype2, not saved ID; saved ID23 is0.
The temporary observer is removed. The permanent test restores full and course
cuts around560->561, advances without construction to581, then checks the exact
582 failure and completed earlier prefixes. It never resumes a failed pass.
There is no natural Spikes hit or natural diagonal launch claim.

One constructed USA320x224 Sonic-only EGL product first earns a Ring through
actual controller input:92 rows of the retained102-row proposal. From that
pristine closed cut, each of four independent native flips clears96 dynamic
slots, seeds only physical40 ID/XY/subtypes/placement flips and stages/rebinds the
actual actor once. No object routine/map/tile/collision/bit7 or helper result is
injected. Upright collision0 reaches direct Hurt after real landing and after
physical32's visit; its new ID11 LostRing stays routine0 and Ring count1 until
next pass. Vertical collision83 instead reaches Body scanner Hurt before the
object walk, so32 initializes and clears rings in the same pass. These are
separate native entries with distinct ordered sound ledgers. Landing zeros YV,
so this World case certifies a same-value full32-bit rewind; nonzero carry/borrow
is separately exercised by actual body/Frame/Runtime component tests.

Literal four-piece/16-cell SAT geometry, every current320..327 tile, native
priority/flip/painter order, actual Plane B transparency and complete glyph
omission sensitivity are checked for all four flips. Legitimate player overlap
remains in the World; isolated glyph and outside-palette contrast controls are
explicitly renderer-only. Five selected cuts per scene give40 full/course
restores, with forward state/pixel replay and no rebind, reseed or source reload.
All567 current-policy entries are explicitly installed for retirement, then
removed while800 actual other-producer entries retain identity. This upload is
not a claim that every owned tile appeared in the scene.

Behavioral negative39372 ran2/2/0/0, Maven29.573s, terminal14:09:40Z after755s
admission wait. The new EGL product reached the intended FIRST residual owned
entry131631=base+559 after every preceding gameplay/pixel/replay assertion. The
other failure was Root's new Door word oracle0328 instead of the retained full
8328 (Object Tile IDs, word_20CF86:40), corrected in a separate test copy.
Only the production reservation comment/count changes559->567/+8. Positive14365
then passed **2/0/0/0**, Maven1m26s, terminal14:20:22Z after298s admission wait:
the same EGL product and the permanent controller/replay/boundary test.
Fresh XML Java25, actual media, surfaceless EGL, exact existing S2 donor and all
skip/error identities were checked. No images existed from the negative run.

Twelve unchanged actual ENGINE PNGs (World, native-plane-only engine comparison,
render-only isolated glyph; one row2 image for each flip) plus input, explicit
construction and PASS outcome are retained under Downloads
`native/engine-spikes-r1`. Success artifacts are written only after retirement
and session/graphics/boot teardown. These are engine component images, not
emulator references. Later-frame pixels are asserted, not additional saved PNGs.
No new native capture was launched. Full-USA gameplay, other regions, viewport/
roster/entry breadth, native startup/timing/pixel parity, Checkpoint save/restart,
warp/completion and sound playback remain open.

Final combined consumers44611 passed **1288/0/0/0** across eleven classes,
Maven1m16s, terminal14:36:44Z after339s admission wait. All eleven fresh XML
identities, Java25, actual media, explicit EGL opt-in and existing S2 donor
properties were checked, including errors/skips. The selection covers touch191,
Frame314, Body272, Spikes123, playable-pass4, Runtime257, restart23, World-load50,
EGL16, terrain-player27 and public-surface11. Fresh structural guards99234 passed **728/0/0/0**, Maven4m53s,
terminal14:49:02Z; all90 fresh XML identities, Java25, errors and skips were
inspected. This is a fresh Spikes result, separate from the earlier diagonal
guards. The command uses a new JVM and LUA_BIN=lua5.4. This is a proportionate focused selection
for the private CD owners and their consumers; no common engine algorithm, Mod
API or build policy changed. The unclassified3744-class smoke fallback is not
launched, and no full-suite result is claimed.


```text
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 <main-workspace>/tools/testing/maven_queue.py --normal -Dmse=off '-Dtest=TestCdPlayerTouchResponse,TestCdPlayerFrameRuntime,TestCdPlayerBody,TestCdSpikes,TestCdPlayableSonicObjectPass,TestCdOpeningObjectRuntime,TestSonicCdRestartCarry,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestSonicCdTerrainPlayerLoad,TestModApiSignatureSurface' -Dsoniccd.media.root=<supplied-media-root> -Dsoniccd.opening.egl=true -Dopenggf.headless.gl=surfaceless-egl -Dsonic2.rom.path=<verified-S2-REV01> test -B
```


```text
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 <main-workspace>/tools/testing/maven_queue.py --normal -Dmse=off -Pguards test -B
```

The private checkpoint retains only the26 owned files (22 Java, four existing
engineering/coverage documents). Main and the retained primary worktree remain
unchanged; no fetch, pull, merge, push, PR or public disclosure is performed.
The next object is Checkpoint13, with a distinct D960 art span and a real saved
payload/live-water/camera/time prerequisite. Its source-only reports and frozen
asset candidate are separate future work; they are not imported or counted in
this Spikes result. FM TrackUpdate likewise remains a source-only proposal with
a required internal projection bridge and unresolved suffix-address accounting.


## Checkpoint save, fresh-visit load and Anton frontier — 25 October 2026

Private/local candidate over `ea8dabcf67df7d02f7a2a39496ff7cea93b7e8bd`;
main and retained primary are untouched. This extends the explicit USA opening
object entry, not the default static entry or complete-USA certification.

Checkpoint is native ID13, not Boulder1B. Authenticated USA mapping/script
operands bind four frames and one script, with a tenth finite camera-art source
(Java211/native212). The single current physical owner adds twelve initially
unknown tiles at D960..DAE0; explicit DEV cold writes authenticate those bytes,
restart carries current bytes/masks verbatim, and the picture policy eagerly
snapshots twelve current versions alongside the previous567 (579 total).
No actor-selected immutable art fallback or claimed native cold-PLC history.

The actual pool body preserves Init's return (no Main fallthrough), dirty free
child ADD4, full32-bit parent pointer and raw24-bit bus reads, physical child
ordering, angle+8 before shared math, word stores retaining fractions, and real
delay3 animation. Contact uses the existing E3 SPECIAL response; Main clears
collision/activates, commits checkpoint then spawn as two stores, executes the
source-ordered18-store SaveData helper, then requests AE once before Draw.
Saved bytes are31 addressed bytes with knowledge, including water-height/routine
aliasing. One Runtime aggregate captures saved/live values; synchronous private
phase/source/cursor/CAS ports own publication. Metadata is never replay authority.

Fresh restart uses actual LoadCheckpointData destinations and unknown-aware CAS,
then SetupCamera, shared BG init, load scroll and the actual postscroll cameraX
for one initial section-list append. New-visit ClearFlowers clears the carried
Ring timeStop/timer/frame before LoadData; it preserves their actual handoff
values until that writer. Saved time0 restores raw0000FF3B (frames59, seconds
byte underflow), not normalized time or elapsed HUD. Native HUD ticking remains
explicitly omitted: raw paused WORD, in-service shared Kill, ledger rotation
and max-time+Start disposition still need ownership before enabling it.

Observed focused checks used the main-workspace Maven queue wrapper from the
private objects tree, Java25.0.5.0.2, supplied media, and explicit skips. Assets90
and store/save70 passed. Body/Aux initially had three dirty-ID fixture errors;
corrected Body3 + Load35 + Frame6 + Setup4 passed48. Runtime/carry/API325 had
eight new fixture failures: seven empty-ledger assumptions ignored completed
Body sounds; priorityFF is valid after native mask7, so the late failure case
now reaches malformed frameFF in DrawObjects. The corrected eight passed.
No production sound/priority behavior changed to fit those oracles.

Actual EGL retirement negative23407 selected those8 plus the new Checkpoint
World test:9/1/0/0, failing only after contact/save/spin/animation/ordered pixels
and full/course replay on the first residual atlas entry131639 (base+567).
Presentation then changed exactly the count/comment567->579/+12 atop the new
Module methods. Positive27422 selected the two Checkpoint World tests plus a
temporary controller frontier observation:3/0/0/0, no skips, Maven1m30 finished
17:49:10Z after30s queue. Its command was `maven_queue.py --normal -Dmse=off
-Dtest=TestSonicCdOpeningWorldEgl#actualEglConstructedCheckpointWorldSavesSpinsAnimatesReplaysAndRetires579Patterns+actualEglConstructedCheckpointContactDiesNativelyRestartsLoadsAndReplaysOnlyTheFreshEpoch,TestSonicCdOpeningWorldLoad#checkpointNaturalRightFrontierObservation
-Dsoniccd.opening.egl=true -Dopenggf.headless.gl=surfaceless-egl
-Dsoniccd.media.root=<Downloads/Sonic-CD/media> -Dsonic2.rom.path=<existing S2 ROM> test`
with DISPLAY/WAYLAND absent and LUA_BIN=lua5.4. This is a bounded selection,
not a whole act, broad smoke or full-suite pass. Combined consumers25852 passed1149/0/0/0 across18 exact selected fresh XML,
including Runtime283, all18 EGL and50 World/load cases, Java25/media/EGL/S2
checked, Maven1m36/18:02:13Z with no queue wait. Fresh guards68483 passed
728/0/0/0 across90 fresh XML, Java25/Lua5.4, Maven5m09/18:09:27Z, no queue wait.

The constructed contact scene has one initial pole/actor ingress, then44 real
neutral rows: final31 saved bytes/knowledge and one AE;32 real ball spin visits;
frames2/3/FF animation; current12 patterns, literal SAT, actual ordered World,
PlaneB and isolated renderer omission controls. Six full/course cut pairs replay
state and pixels without ingress. All579 entries are installed for retirement
(old missing entries use test-only transparent witnesses), then removed while
actual shared entries retain identity. This is not all579 rendered in gameplay.
Twelve unchanged320x224 ENGINE PNGs, input/construction and post-cleanup outcome
are under Downloads `native/engine-checkpoint-r1`, not emulator references.

A separate once-constructed pole plus upright Spikes scene completes save at
row2, actual shared Kill at row6, native dead/reset and NEXT-beforeFrame fresh
Runtime/Frame/actor/scroll/load generation. It verifies saved31 preservation,
fresh cleared XY fractions, real cameras/clouds/raw-time load, Ring7/frame1,
one section0 append and actual Boulder service through new row7/remaining1.
Old-epoch full/course snapshots reject; fresh cuts0/1/3 produce28 forward rows
with state/pixel replay. It intentionally stops before Animals on new row8.
This is an actual engine restart proof, not a controller-only checkpoint hit.

Controller-only Right now completes Checkpoint Init at582 and through603;
full/course replay across581/582 passed. Inside604, earlier physical bodies
complete before unowned Anton3F at55. The observed untouched slot is X2832/Y1061,
subtypeFF, saved membership3A, routine0. Later Boulder56/Spring57/Spikes58 remain
unexecuted. The temporary observation is replaced by permanent assertions for
582 and603 replay plus this exact partial604 boundary. No failed pass is resumed,
and no natural Checkpoint contact or completed604 is claimed. Native source
maps3F to ObjAnton; its body/current-art/terrain publication is the next frontier.


The final affected-consumer command used the same media/EGL/donor flags and18
exact classes: TestCdR11aObjectAssets, TestCdR11aObjectRetailAssets,
TestCdR11aCameraArtResidency, TestCdCheckpointSaveData, TestCdCheckpointLiveAux,
TestCdCheckpoint, TestCdCheckpointLoadData, TestCdCheckpointPicture,
TestCdObjectMappings, TestCdR11aSectionArt, TestCdSpikesPicture,
TestCdPlayerFrameRuntime, TestCdR11aForegroundScroll, TestCdOpeningObjectRuntime,
TestSonicCdOpeningWorldLoad, TestSonicCdRestartCarry, TestSonicCdOpeningWorldEgl,
TestModApiSignatureSurface. Fresh guards used `maven_queue.py --normal -Dmse=off
-Pguards test -B` in a fresh JVM. This bounded affected-consumer selection
replaces broad smoke: new Frame/camera ports preserve old algorithms, their full
consumer classes and all old/new World products are exercised, and no unrelated
donor/build/timing behavior changes. Remaining whole-act/native clock/reference
obligations stay open. The permanent route test now also passes603 replay. The
one-off diagnostic observer is removed. Fixed-input review96d5b374 found no
P1/P2 in the new restart proof/Root corrections; all execution is Root-attributed.


## 2026-10-25 — Anton current art and natural Checkpoint649

This private local milestone is the candidate over `b9816d16a656358840d547d47ec34887dc791d12`,
on `feature/ai-sonic-cd-objects-local`; it changes neither main nor the retained
primary tree and has no merge, push, PR or public release. USA DEV Palmtree
Panic is still partial, not USA MVP or JP/EU gameplay parity.

Actual USA retained operands authenticate Anton mappings (112 bytes) at `$20B3AA` and
scripts (12 bytes) at `$20B39E`; selector 2 admits secondary 0/1 with words2409/2370.
The existing TEN-source bank and Anton Java 484 / native 485 extents/cache identity
remain unchanged. No actor-specific art bank or cold Anton preload was added.
C28 preserves the old 579 bindings, validates the new 22-pattern metadata/overlap
without reading those pixels, and resolves selected final409..41E through the
one immutable current physical State. Empty SAT/unselected UNKNOWN remain
valid. Relocated374..385 reuse Boulder current pixels/logical aliases;
lower 370..373 remain unadmitted and 386 survives. Potential 601 is a logical
extent, not proof every tile is known or rendered.

The actual-pool body preserves Init's missing routine increment, full LONG
Y+1 Place/no snap, negative-only0->2->4, full fractional Main delta, repeated
same-call reversal, accepted[-7,7] floor and shared animation. Additive terrain
entries with arities 7/3 publish every reached primary-angle clear/raw/flip/forced store
synchronously, including prefixes before a later read failure; old pure arities 6/2
contracts remain. Runtime's private adapter preflights actual same-owner 96-slot
images, current source/dispatch/phase and expected Frame byte before its one
existing Frame write, including same-value stores. It calls no legacy angle
port and replays no completed metadata. Exact-cycle detection is an explicit
host non-return support boundary, not a native budget/return. Snapshot/phase,
factory arities 11/14, clocks, sounds and the cold ten-source construction stay intact.

Controller-only Right completes Anton Init 604 (r0/Y1062), Place 609 (r2),
610 (r4), and through 656. It naturally contacts/saves Checkpoint at call 649:
checkpoint 2 / spawn 1, final 31 saved bytes known, raw DEV time 0, actual playerXY
and one AE. Full/course restores compare the complete checkpoint owner BEFORE
replayed contact as well as afterward. Inside 657, physical 40 rejects ID20
(collapsing platform), X3248/Y784/subtypeC0/saved44, before later Anton 55;
Runtime/Frame fail closed. Partial actor XY are183030784/48438784, XV1534,
YV-300. This is not a completed 657 or native trace/frame-parity observation.
The permanent test is
`ordinaryRightRouteCompletesAntonAndNaturalCheckpointSaveReplaysThenStopsAtCollapsePlatform657`.

The separate actual EGL product earns canonical Anton art through ordinary
Right 649/real PLC9, captures that natural save image before any ingress, then
runs eight initial-only normal/damaged × four-flip scenes. Real terrain is
sloped, not an assumed flat strip: fixed scene X2875/probe763 has D-2/raw08.
A detached pure-terrain support vector supplies expected Y/raw angles; all
other addressed bytes stay literal. Sonic retains warmed X2743 with separately
queried two-foot support; no production floor, spawner gate, post-ingress clear
or source reload is invented. Forty ordinary rows per scene reach four mapping
frames/all 22 current tiles, both prior-CPU/NOW and current-CPU/NOW conversion,
ordered World/current-palette/native PlaneB, actual-renderer isolation/contrast
omission controls,112 selected full/course restores and4000 forward replay
rows / pixels. All 601 entries are installed for retirement (522 missing old 579
are explicitly TEST transparent witnesses), removed at close, and 800 actual
other-producer entry identities survive. This is not all601 scene-rendered.
All module/session/graphics/boot cleanup returns before optional PNG/PASS writes.
`Downloads/Sonic-CD/native/engine-anton-r1` contains 113 unchanged 320×224 ENGINE
PNGs plus input/construction/outcome: one natural649 capture and112 constructed
scene/render-only controls, with no emulator/native image comparison.

Observed tests-first and corrections remain distinct:

- Assets70499 missing-API compile RED; positive36226:100/0/0/0.
- Floor2431 missing-overload compile RED.94337:44/0/2/0: two fixtures put an
  unavailable material in a constructor-validated chunk. Root R2 uses the actual
  alternate-path resident chunk without relaxing loading;85367 corrected2 pass.
  Final affected terrain/legacy consumers47868 pass263/0/0/0 across 7 classes.
- Body91853 missing-owner compile RED.73218:77/1/0/0: wrong expected index
  exception only. Root R2 preserves production; focused96257 passes1.
- Picture40563 missing-C28 compile RED; exact R1positive57304:44/0/0/0.
- Runtime39663 missing18-component API compile RED.1972:380/2/0/0 includes
  Body77 and a temporary ordinary observer; Runtime300/302 pass. Root assertions
  fix byte-wrapper identity, first painter-selected UNKNOWN41A, unchanged prior
  Body sound prefix and terminal getter observation; production stays frozen.
  Focused6200 passes2. Temporary observers are removed.
- World64511 fails unproved terrain603 prerequisite, not cleanup. Read-only
 649 diagnostics establish actual slope.83391 reaches row1 but reveals swapped
  Y/X radius literals; Root R3 pins13/18.60813's actor-forward fixture crosses
  into real unowned platform 20; Root R4 retains the warmed camera window.
 73125 then completes the whole proof prefix and fails EXACT old 579 cleanup at
 131651=base+579. Exact two-line601 cleanup passes18640:1/0/0/0 with all teardown.
  No production movement/collision/terrain change was made to fit a fixture.

Final combined37663: **634/0/0/0 across 10 exact fresh XML**, Java25.0.5.0.2,
actual CD media, explicit surfaceless EGL/S2 donor, no skips; Maven1m40,
finished20:19:24Z, no queue wait. It includes all 302 Runtime,77 Anton, 19 EGL,
50 World/load, 31 carry,100 assets, 34 mapping, 10 picture and 11 public API cases.
The change planner proposed 3751 smoke classes because private CD source paths
are unclassified. Bounded direct affected-owner integration plus the7 terrain
consumers is proportionate here: all code changes remain in private CD owners,
with no shared renderer/build/selection/donor algorithm change. No full-suite
or full-smoke claim. **Fresh structural guards 45840:728/0/0/0**, 90 exact fresh XML, Java25/private cwd/Lua5.4, fresh JVM; Maven 5m04, finished 20:27:44Z, no queue wait.

Exact final command shape (from the private cwd):
`env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py --normal -Dmse=off '-Dtest=TestCdR11aObjectAssets,TestCdR11aObjectRetailAssets,TestCdObjectMappings,TestCdAntonPicture,TestCdAnton,TestCdOpeningObjectRuntime,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestSonicCdRestartCarry,TestModApiSignatureSurface' "-Dsoniccd.media.root=${SCD_MEDIA_ROOT}" -Dsoniccd.opening.egl=true -Dopenggf.headless.gl=surfaceless-egl "-Dsonic2.rom.path=${SONIC2_ROM}" test`.
Independent fixed-input reviews found no unresolved production P1/P2;
Root corrected the two later test findings and preserves all frozen R1 packets.
Next finite native owner is platform 20; its source-only authentication/TopSolid
prefix research and pure FM track-update packet remain external/unimported.
Native cold/title/immediate PLC history, live HUD timer, audio service/playback,
complete act/game and regional breadth remain open.

Final repaired World/route fixed-input review `535adfcaafc3aed3fb1e605948dc85b7fc65a7706d3a06713bce08c46a2760ba` found no new P1/P2; it records the two prior test findings and Root corrections. All reviewed inputs remained unchanged; executable outcomes are Root observations.

Command paths use the portable placeholders `OPENGGF_MAIN` (project root), `SCD_MEDIA_ROOT` (supplied disc media) and `SONIC2_ROM` (verified World REV01 donor). The observed absolute paths remain in the private external task record.


## 25 October 2026 — bounded native FM track projection

Over private Anton base `2e9c4932848a50a9c12aeba610265fa7e9bf282b`,
`CdFmPlaySound.planTrack/4` projects one explicit UpdateTrack(IX) entry over
its existing immutable current RAM/source/defined/owned state. It adds no
installer, scan, timer, queue arbitration, executor or audio service. The old
394-line/22221-byte source prefix and existing PlaySoundID/StopSound test remain
byte-identical. The retained-address discrepancy is reconciled: TrkCmd_Call is
1A bytes, so DriverInfo remains06D7; actual retained-USA anchors now execute in
the optional-media test rather than being source-only claims.

The finite subset covers ordinary note/duration, panning, instrument, legato,
jump, repeat and stop, retaining native store/read and untimed YM-intent order.
Active vibrato, portamento note parsing, raw-frequency and special FM3 bodies,
unsupported command targets, command limits and missing/unsupported RAM reads
return typed uncommitted prefixes. A fault projection starts only a fresh whole
entry. Current overlays/masks are retained; no source-baseline reconstruction,
new RAM export, instruction ownership or fabricated IY is introduced. The six
handlers and finite code/data classification are source-bound; declared-format
views remain unauthenticated. This is neither track lifecycle nor synthesis,
playback, device timing, all-sounds support or game completion.

Frozen production SHA256 `f5849902d540ed1b9b636ee9c6f4f991e67ff74c8396a2226978cbe4cd251916`
and new test `8208c90a6b189d1ad96bfeb5ac89d949815b18a51e1e0f82db9503f8de2cbceb`
were imported with ordinary0644 modes/fresh mtimes after tests-only session87854
failed compilation on the absent Track APIs (no JUnit execution). Exact R1
positive55298 passes **87/0/0/0** across five fresh XML: TrackUpdate17,
PlaySound18, RequestQueue18, Arbitration27 and Assets7. Java25.0.5.0.2,
actual supplied-media property and private cwd are verified; the retained-USA
composition test executed with no skip. Maven1m34, finished20:46:32Z, no queue
wait. Independent fixed-input review
`d965c6d02015f29000b7bb8d230183401f5c5d0b2ca73d84e0ab1e1acac348c1`
found no P1/P2, with finite read-order/fault coverage limits explicit.

Exact focused command from the private cwd:
`env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py --normal -Dmse=off '-Dtest=TestCdFmTrackUpdate,TestCdFmPlaySound,TestCdFmRequestQueue,TestCdFmSoundArbitration,TestCdFmSoundAssets' "-Dsoniccd.media.root=${SCD_MEDIA_ROOT}" test`.
The planner proposes3752 smoke classes because this private CD owner is
unclassified. These five directly affected pure-owner classes plus fresh
structural guards are proportionate: no runtime/renderer/timing/shared-game
algorithm or build/selection policy changed. Fresh guards20606 pass **728/0/0/0**,90 exact fresh XML, Java25/private cwd/Lua5.4,
Maven5m04, finished20:55:38Z, no queue wait. Command:
`env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py --normal -Dmse=off -Pguards test -B`.
No broad smoke/full-suite or audio-output claim follows.


## 25 October 2026 — collapsing platform and native controller frontier742

Private local candidate over `74b4aa0ce54a36bd3125f3411a2f45063fced0d2` on
`feature/ai-sonic-cd-objects-local`. Main and retained primary remain unchanged;
no merge, push, PR or public release. USA DEV opening is partial. Source is N
`1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff`; actual runtime bytes still come only
from the authenticated USA disc, never disassembly fallback.

Actual USA retained operands authenticate six complete native-order views:
map1 C520/118, sizes1 C596/24, map3 C5AE/62, map2 C5EC/408, sizes2 C784/40 and
map4 C7AC/158. Tables45..50 append without changing earlier ordinals. Four static
visual getters use frames1/6/7/6 and57 patterns. ART_PLATFORM is the eleventh
cached source, canonical PLC1/row6 at97C0, header8039. Java452 and native453 are
distinct finite consumers; native's extra80 is the real next Boulder header,
not invented padding. Both decode1824 bytes with SHA256
`b33a50d46c89a5115a18548e6a3b083428c8a5bd2bcafc3c4c45e2718c5c768a`.
The destination97C0..9EE0 aliases existing AT_9000; no Region/art-bank growth.
Platform is Std-only: exactly57 cold complete-tile DEV writes are explicit,
not an assertion that title/Std PLC executed. Restart preserves bytes/masks,
code table and raw section verbatim, including UNKNOWN.

Opt-in Terrain7 and Movement prefix entries preserve actual reached angle stores
and precise PRIMARY/SECONDARY read sites. Only the two source-owner unavailable
branches create private markers. A caller can retain a genuine marker and throw
it during a later callback; private construction alone does not identify the
current invocation. Root's narrow private callback transport preserves that
original host exception instead of misclassifying it as a current native read.
Old pure entries/bodies remain. Solid.runWithPrefix6 returns genuine Completed
Result or NativeStopped(Prefix,stop), without invented d0/Path/resume tokens.
Frame validates/adopts that prefix with its existing field owners, before any
assignment. The same original exception escapes after native-prefix publication.

CdCollapsePlatform preserves Init/Main fallthrough, unconditional Top, actual
GetOff then A3 then breakup, dirty ID0 allocation and negative-cell reuse,
physical earlier/later child visits, WORD Delay wrap, transition-without-Fall,
full LONG gravity and signed0200 deletion. MultiRow cell14 is native5, not6
(ObjCollapsePlatform_Sizes1 / byte_20C598, Collapsing Platform/Data (Present).asm:11-25).
All four aliases share existing64-byte storage. Native wrapper Draw and reached
saved/despawn tails retain source order. Runtime19 caches one Body sharing the
actual placement/Solid. A private invocation-local record authenticates the
cached Body/whole dispatch; existing inSolidCommit excludes callback reentry.
Full96/current-source/site/routine/masks/nonwrites, Frame expected values and
the entire typed sound ledger preflight before one pool publication, matching
Frame apply and ledger assignment. NativeStopped commits only its prefix then
Body rethrows the identical stop. Snapshot/phase/factory11/14/tail/clocks retain
existing ownership. Completed descriptive effects are never executed again.

C29 preserves the exact C28 parameter prefix and old601 bindings, adding an
explicit57-pattern logical interval. Metadata validates all18 old bases without
resolving Anton pixels. Only selected final4BE..4F6 reads immutable current
physical art. Four normal mapper registrations cover sparse40 patterns. Empty
SAT/UNKNOWN remains admitted. Both Runtime conversions use C29; two presentation
lines reserve/retire658. Installed658 is not658 scene-rendered.

Controller-only heldRight now completes657 (physical40 and45 Init), real C0
breakup723/A3once, Delay->Fall732 and through741. Full/course restore and forward
checks cover657/723/732/741 while preserving all earlier561/582/Anton/save649
proofs and checking checkpoint owner BEFORE replay. First failure742 is unowned
ID21 at physical75, X3824/Y348/subtype40, stillroutine0/zero Init fields.
SpawnObjects in742 creates75 from the preceding741 free slot BEFORE Body and
dynamic walk: comparing that source with the earlier closed free image was an
invalid oracle. Earlier child35 Fall is already committed at partial742:
Y032F4000/delta00048000; actor XY216737536/48973568, XV1536/YV0. Runtime/Frame
fail closed;742 is not complete and is never resumed. Permanent method:
`ordinaryRightRouteCompletesAntonCheckpointAndCollapseThenStopsAtPlatform21At742WithReplay`.

Separate actual EGL evidence warms controller649, then eight initial-only scenes
use native placement fields and one actor restore/rebind, followed by96 ordinary
neutral rows each. Fixed real two-foot floor766/parentY718/48-pixel elevation
keeps the warmed camera window; no spawner mute, terrain repair or later ingress.
Literal full64 images/physical allocation, delays/fall/delete, complete Body+FM,
prior CPU/NOW and current CPU/NOW art, native palette/PlaneB/ordered GPU pixels,
isolated/contrast omission controls and selected full/course state+pixel replay
all execute. Observed60 Delay transitions,2970 Fall visits,28 deletions. Sparse40
patterns are scene-rendered; all57 current patterns get installation witnesses,
including17 unmapped. All658 are installed for retirement (540 missing old601
use explicit TEST transparent witnesses), then absent;800 actual unrelated entry
identities survive. All cleanup returns before output. External
`Downloads/Sonic-CD/native/engine-collapse-platform-r1` holds41 original320x224
ENGINE PNGs, input, construction and PASS. The17-image private progress page
keeps bytes unchanged. No emulator/native pixel/timing parity is claimed.

Execution and source corrections are distinct. Tests-first missing-API REDs
preceded each imported owner. Assets109, combined probe/Solid/Frame893, Body38
and picture84 passed after narrow manifest/callback/fixture corrections. Runtime
R1 test failed Java int/Short inference; later test revisions corrected actual
Top/GetOff/primary alias, Body sound prefixes, alternate terrain fixture,
value-Frame API and reverse-SAT first UNKNOWN4D3. Runtime327 passes unchanged
production. World R1 repeated wrong Sizes1 cell14 twice; Root R2 fixes only those
literals. Old601 cleanup then fails EXACT new owned131673 after full proof;
658 two-line production passes1/0/0/0 at22:23:22Z. No gameplay was tuned to fit.

Final affected-consumer27102 selected25 exact classes and ran2147/1/0/0 at
22:31:01Z, Maven1m57/no queue: only old Anton picture count10 vs new11 failed;
all other2146 passed, including20 EGL/50 World-load/327 Runtime/413 Frame.
Root changes that one inherited count only; focused56421 then passes all10
Anton picture cases at22:32:21Z,33.576s/no queue. Exact fresh XML class sets,
Java25.0.5.0.2, configured actual media, EGL/S2 and zero skips were checked.
This is a repaired bounded selection with narrow verification, not a fresh
all-green2147 invocation or full-suite/smoke claim. The change planner selected
3754 unclassified smoke classes; direct private CD owners and consumers are a
proportionate bounded selection. Fresh structural guards68138 pass728/0/0/0 across90 exact fresh XML,
Java25/private cwd/Lua5.4,5m16/22:39:19Z/no queue. A follow-up inventory sweep
found the same source-count10->11 assertion in older Spikes/Checkpoint pictures;
only those two counts migrate. Four old picture consumers53068 ran29/1/0/0:
Boulder7,Checkpoint7,diagonal7 passed; Spikes' old arity inventory still stopped
atC27. Immutable J already has C28 (source-proven pre-existing stale test, no
baseline execution claim); current C29 is additive. Append28/29 to that inventory,
retain all lower contracts, and15100 passes all8 Spikes cases narrowly at22:44:08Z
(34.763s/no queue/J25/media/zero skips). No unchanged broad/guard ceremony repeat
is needed for these literal test-only migrations. This does not turn either
red invocation into a claimed fresh combined green run.

Command shape from the private cwd:
`env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py --normal -Dmse=off '-Dtest=TestCdR11aObjectAssets,TestCdR11aObjectRetailAssets,TestCdPlayerTerrain,TestCdPlayerMovement,TestCdObjectCollisionCheck,TestCdSolidObject,TestCdPlayerFrameRuntime,TestCdCollapsePlatform,TestCdObjectMappings,TestCdR11aSectionArt,TestCdCollapsePlatformPicture,TestCdOpeningObjectRuntime,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestSonicCdRestartCarry,TestModApiSignatureSurface,TestCdBoulder,TestCdFloatBlock,TestCdSpring,TestCdSpikes,TestCdAnton,TestCdMonitor,TestCdMosqui,TestCdPataBata,TestCdAntonPicture' "-Dsoniccd.media.root=${SCD_MEDIA_ROOT}" -Dsoniccd.opening.egl=true -Dopenggf.headless.gl=surfaceless-egl "-Dsonic2.rom.path=${SONIC2_ROM}" test`.
Narrow correction uses `-Dtest=TestCdAntonPicture` with actual media. Prior
frozen packets/reports retain original hashes and failures; Root revisions are
separate. Independent Runtime/World fixed-input review
`484f0103f0050161ba6d934dd8b26622c1088ebfabf6b9c58439049c32a52dda`
finds no additional unresolved P1/P2 after World RootR2; prefix and Body/picture
reviews retain their original findings and exact resolutions. Controller-route
review is separate. All reviewer execution is explicitly unclaimed.

Native startup/title/instant PLC history, live HUD time, FM lifecycle/executor/
synthesis/playback, next ID21, whole act/game and regional gameplay parity remain
open. Installed/rendered/source knowledge and actual controller/constructed
coverage stay separate.

Final independent controller-route source review
`716a5c8c73b3ff7cc1c46f61009b409bf8cdbb9a6c98fbc86367db279f38cd4c`
finds no concrete P1/P2 in Root routeR4; all fixed inputs preserved. A separate
source-only ID21 audit corrects the quick subtype transcription to40 and pins
native row167, map/width/range authentication targets, same source11/57/current
span and the separate ObjGetCeilDist7X prerequisite. It establishes no completed742.


## Native ID21 Platform and controller continuation — 26 October 2026

Private candidate over `d975f191b3e181e3bd75ba577318970cac7dfef9`;
main/retained trees remain untouched. No merge, push, PR or public release.
The full USA-then-Japan/Europe objective remains active.

Actual USA operands authenticate one132-byte Map1 at20CCC8, width/range
views at20C940/20C944 and existing57-tile ART_PLATFORM ownership. Three views
append to the asset table; the existing source11, C29, bank19 and658-pattern
reservation remain unchanged. One ordinary mapper registration admits the
three native frames. No Map2 fallback or additional art/preload/region exists.

`CdPlatform` executes native Init (returns before Main), full-WORD time-stop,
all ten subtype families, sag, native sine/word/long movement, optional dirty
Spring allocation, ceiling prefixes and exact anchor/saved-byte destruction.
Eight State aliases address existing bytes. Runtime admits actual physical ID21
through a cached private invocation;26 site checks and full96 source/output,
Frame and ordered-FM preflight precede publication. Genuine stopped Solid
prefixes publish synchronously and rethrow the original exception. Object
ceiling probes never pre-clear the primary angle; raw reached stores publish
before a later unowned terrain read. All inherited owner/capture/clock/phase
and shared algorithm bodies remain unchanged.

Focused observations before final delivery checks: ceiling32 passed; actual
asset/mapper156 passed. Body R1 ran51/1/1/0: camera80 also triggered the anchor
tail, and a primary invalid terrain word failed construction before the probe.
Root R2 isolated the camera and alternate chunk, yielding50 passing methods;
its adjacent row was still wrong. Native full-height subtracts negative a3,
so R3 uses307->317 and passes the remaining method narrowly. Runtime R1
ran362/0/1/0: time-stop bypass assertions passed, then the fixture's FF-derived
frame3 failed the actual three-frame draw reader. Root R2 sets only MAP_FRAME0;
the narrow check passes. Production was not changed to fit these fixtures.
Retail Root R2 corrects an empty-cache assertion to the existing legitimate
ANIMAL_BASE_TILES dependency; old views/source identities remain exact.
Original red runs remain red; repaired bounded selections are not fresh
all-green class invocations.

Actual EGL World21 passes16 initial-only scenes: three sizes x four flips,
vertical/horizontal oscillation and two timed falls/deletes. There are6273
ordinary calls including649 warm and5100 full/course replay calls; literal
native SAT/current12/palette/ordered terrain/PlaneB/omission controls pass.
All81 original320x224 ENGINE PNGs and post-cleanup input/construction/outcome
remain outside Git in `Downloads/Sonic-CD/native/engine-platform21-r1`.
Selected12 entries retire while800 actual unrelated entry identities survive.
This is not57/658 rendered or whole-act/native parity. Other subtype World
products remain explicit matrix gaps despite component/Runtime coverage.

The permanent Right-only route now completes1200, with full/course cuts
741->742 Init,742->743 Main and1199->1200, retaining all earlier Ring/Spikes,
Checkpoint649 save, Anton and collapsing-platform assertions. Physical75 is
native subtype40 at3824/348: stationary without contact, not an unimplemented
oscillator. The1200 endpoint is routine2/pushing raw physical link53; no
object class is inferred from that link. Additional Right-only observation
through3600 remains pushing, not an unsupported implementation frontier.
The permanent route selector passes1/0/0/0 at00:04:58Z,30.062s/no queue.

A separate controller-only probe round-trips `1200 R;32 R+B;1200 R` through
InputScriptCompiler/Bk2MovieLoader. No actor/object/terrain/PLC ingress follows
boot. Actual1802 reaches routine6/FM93;1886 reaches routine8/reset60;1947
replaces Runtime/Frame/actor/level and advances production generation1->2.
Fresh checkpoint XY fractions are0, saved31 remains known, rings0/lives2 and
Ring animation7/1 restore; HUD remains explicit DEV0. Fresh ordinary calls1..6
complete. Fresh7/global1954 completes the last Boulder tile and pops its head,
then stops in prepare on UNKNOWN_SOURCE2381C0/VRAM7100 (Animals). Failed-state
count0/budget3 and the advanced queue preserve that prefix; no failed-call
resume or completed1954 is claimed. This is diagnostic source-fingerprinted
observation, not yet the permanent restart-route assertion or native parity.
The next finite source must reuse FrontierArtBank's cached Animals decode;
no per-actor bank, cold preload, current owner or picture growth is justified.

Validation commands use the private worktree, Java25 and actual media:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 "${OPENGGF_MAIN}/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestCdR11aObjectAssets,TestCdR11aObjectRetailAssets,TestCdObjectMappings,TestCdObjectCollisionCheck,TestCdPlatform,TestCdOpeningObjectRuntime,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl' "-Dsoniccd.media.root=${SCD_MEDIA_ROOT}" "-Dsonic2.rom.path=${SONIC2_ROM}" -Dsoniccd.opening.egl=true -Dopenggf.headless.gl=surfaceless-egl test
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 "${OPENGGF_MAIN}/tools/testing/maven_queue.py" -Dmse=off -Pguards test -B
```

Final eight-class check31987 passes672/0/0/0 at00:11:10Z, Maven1m48/queue21s. Exact8fresh XML, Java25/privatecwd/actualmedia/EGL/S2 and zero skips were verified. Fresh guards74260 pass728/0/0/0 across90 exact fresh XML, Java25/privatecwd/Lua5.4, Maven5m15/queue272s, ending00:22:45Z. Tool preflight passed without executing tests.
The change-based planner selects3755 smoke classes because these private CD
paths are unclassified. Source additions are confined to CD assets/object
owners; shared algorithms, selectors/build policy and other games are unchanged.
The focused CD consumer/World/replay closure plus guards is proportionate here;
no smoke/full-suite pass is claimed. Independent static component/integration
reviews found no remaining P1/P2; exact fixture reds/corrections and UNRUN
worker limits remain in the external reports. Temporary observer code is not
part of the final candidate; useful controller recipes/failure cuts are retained
here and in the existing measurement catalogue.


## Cached Animals PLC source and natural restart replay — 26 October 2026

Private candidate over `0afaea40f8b01a5e58cdb2b5d8b3a1123c5db35c`.
The existing FrontierArtBank already owns Animals and its cached Java445-byte
NemesisRead. The section source list now appends that same read as source12,
canonical PLC2/row1/VRAM7100,20 tiles. Only the two existing source-count guards
and demonstrated inherited source-list contracts migrate. There is no new
bank, decode, public API, cold Animals preload, physical region, logical span,
C30 or658 reservation change. Restart keeps its actual art/table/queue carry.

Original USA/JP/EU media authenticate the identical446-byte Art_Animals source,
header0014. Independent finite Python source models require Java445/native446;
one-short444/445 fail independently, both full reads produce640 bytes with
SHA256 `ac1edab54e2b767578c6b3c2b828dc9821ef02c7a3bed9079dc7dded4a26e8d1`.
The native reader consumes the real final00 at3837D, excluding successor80.
These are model observations, not native execution or regional gameplay parity.

The permanent new selector is
`TestSonicCdOpeningWorldLoad#controllerJumpRouteRestartsFromNaturalCheckpointAndServicesAnimalsWithFreshEpochReplay`.
It drives only normal input:1200R,32R+B,thenR. Actual Checkpoint649 saves31
bytes; Kill1802, gone1886/reset60 and load1947 retire the old epoch. The saved
camera2583/section0200 selects actual full PLC4, although source metadata stays
canonical PLC2. Fresh7 prepares Animals and fresh8..14 services all20 current
tiles, retaining partial masks/nonwrites and preparing Pata next. Full/course
cuts at loaded0,1,7,10 compare complete checkpoint/live/knowledge BEFORE forward
replay to14. Old captures reject after actual generation1→2. No actor, object,
terrain, PLC or physics values are injected, and failed calls are never resumed.

Matched old-source11 RED83312 reaches the genuine fresh7/global1954 unavailable
Animals stop:1/0/1/0,00:42:40Z,Maven1m24/no queue. Restoring exact source12 and
Root guard joins gives2447:4/0/0/0 at00:47:43Z,Maven1m27/no queue (natural proof1
and three repaired Boulder retail cases). Initial asset RED35724 ran123/8/1/0;
first source12 check60150 ran123/1/0/0, with only the old Boulder assertion that
Animals was unregistered. Root R2 replaces exactly that assertion with identity
equality to the existing frontier cached decode; other unknown-source guards stay.

Nine affected classes96460 ran718/1/0/0 at00:57:44Z,Maven1m52/no queue:
WorldEGL21,WorldLoad51,Mappings38,unit87,retail36,collision32,Platform51,
SectionArt40 all pass; Runtime361/362 pass. The stale reversal test expected
Animals to stop. Root R2/R3 then incorrectly inferred the full initial PLC2's
GoalPost from canonical metadata; both narrow runs failed, exposing actual head0.
Native SectionData distinguishes initial2/4/5/6/7 from incremental8/9/A/B/C.
Reversal actually selects PLC8 with six rows: Boulder,Animals,Mosqui,Anton,Plant,
Ramp, and no GoalPost. Root R4 renames only that case to
`cameraArtActiveReversalCompletesActualIncrementalSourcesWithoutDroppingCurrentPrefix`,
checks actual selection, all six genuine VInt/Body/Draw/tail service prefixes and
open CLOSED_CUT, and separately retains the full initial GoalPost unknown guard.
It passes1/0/0/0 in34195 at01:10:59Z,29.167s/no queue. Thus717 combined passes
plus the repaired narrow case are bounded coverage, not a fresh all-green718 run.
All stated completed results have exact fresh XML, Java25/private cwd/actual
media and zero skips; the combined run also supplies EGL and the actual S2 ROM.

Command shape is the preceding ID21 affected command plus
`TestCdR11aSectionArt`; all nine classes are explicit. Guard command remains
`env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 "${OPENGGF_MAIN}/tools/testing/maven_queue.py" -Dmse=off -Pguards test -B`.
Fresh source12 structural guards11227 pass728/0/0/0 at01:23:55Z,
Maven4m58/queue31s. Exact90 fresh executable-class XML, Java25.0.5.0.2/private
cwd and zero skips are verified. A source census initially counted one historical
package compatibility facade with no Jupiter declarations; excluding that
non-executable facade makes the exact selected set90. No test rerun was needed. The3755-class unclassified smoke
selection remains disproportionate to one cached-list append and two count
joins; direct consumer/replay coverage plus structural guards is the chosen
bounded validation, with no full-smoke/full-suite claim.

A separate diagnostic cold-start continuation on the new source12 completes
2005 controller calls (fresh58). Attempted2006/fresh59 fails during ordinary-tail
prepare: SectionArtException UNKNOWN_SOURCE2320DA/VRAM9EE0, count0/budget2,
advanced queue after the prior producer is exhausted. Observer22838 ran1/0/1/0,
30.011s/queue14s,ending01:12:59Z. Its145612-byte temporary source is frozen
externally; the155082-byte permanent natural test is restored. This next source
needs its own native/media proof and is not admitted by weakening the guard.
No completed2006, failed-call replay, full act/USA or regional parity is claimed.
The full private objective remains active; no main-tree integration or publication.

Independent one-method R4 review `f2af1642d40318f771b7e09cb4408d275d6769722e6afd6426b9027a7efc5486`
finds no actionable P1/P2 and verifies all six frozen inputs unchanged. The prior
registration/Natural review `a957eb2423ccef7dffff03e158cf1d3f5aa017ce2a61ff975dce2cc018f94bb0`
also finds no new P1/P2 within its fixed scope. Neither reviewer executed tests.
All nine current Java files match their final frozen author/Root revisions;
permanent WorldLoad is restored and the diagnostic observer is outside Git.


## Finite GoalPost art and natural queue completion — 26 October 2026

Private candidate over `527d9986afaefa4860f431112aca1eb075287350`.
Actual PLC4/row5 source2320DA/VRAM9EE0 is authenticated as GoalPost, canonical
PLC2/row6. Header8018 means XOR24/768 bytes. No previous GoalPost cached read
exists: one new NemesisRead is computed through the existing private helper
inside the same synchronized sectionArtSources cache. It appends source13;
the two existing size guards and demonstrated source-list contracts migrate.
First12 source identities/order/cache/extents remain exact. No public API,
new decoder/bank/current owner/physical region, C30/logical span, cold preload,
body or658 reservation change is made. GoalPost physical4F7..50E lies in
existingAT_9000; its art registration does not execute the goal object.

Original USA/JP/EU source448 is identical to the pinned native include.
Independent Python models first establish Java448/native449, with447/448
one-short failures. Native449 consumes the real adjacent Signpost header byte80
at3229A; next4C is excluded. Inventory stays448. The successful768-byte output
SHA256 is68359c6e3dc65e9cf72db3d20ce887acc3728fdd32de20abc1fd5af185ff4c9a.
Production checks now corroborate both finite readers, six retained incoming
code-table entries, XOR output, slow3/fast18 WORD-high-byte gates, full descriptor
preadvance, no final saved-decoder RAM write, and the shipped88-byte POP. A
native448 support-negative fails on tile24 with23 complete tiles736 resident
bytes;28 diagnostic bytes never become resident, and the old final tile/masks
survive. Fast failure preserves budget13/A360; slow preserves budget1/A1E0.
No Signpost producer is admitted by consuming its first byte.

The new permanent selector is
`TestSonicCdOpeningWorldLoad#controllerJumpRouteRestartsNaturallyAndServicesGoalPostWithFreshEpochReplay`.
It uses opening/load and ordinary input only:1200R,32R+B,thenR. No actor,
object, terrain, PLC or gameplay values are injected. Actual old death/reset/
load1947 and retired-epoch rejection precede the same saved camera2583/651,
section0200/fullPLC4. Fresh59 now prepares GoalPost with budget2 and zero pixel
writes; fresh60..67 services all24 tiles and empties the actual queue. Full/course
cuts58/59/63/66 compare saved/live/knowledge and complete State BEFORE forward
replay, then all intermediate and final states. C29 and all physical nonwrites
remain intact. This does not claim goal contact, native timer/title/audio,
act completion, full USA or regional gameplay parity.

Matched Source12 RED11797:4/0/1/0 at01:46:58Z,33.101s/queue57s; the new natural
case reaches exactly UNKNOWN_SOURCE2320DA/9EE0 at direct step2308/fresh59,
while three source-count picture fixes pass. Assets RED47471:129/10/0/0 at
01:54:42Z,51.686s; all90 unit cases pass, ten retail failures are only missing13/
complete GoalPost lists. Source13 first positive72042 passes179/0/0/0 at02:02:25Z,
1m53/queue140s: unit90,retail39,Section48,Natural2. Exact4 fresh XML,
Java25.0.5.0.2/private cwd/actual media and zero skips were verified.

A wider direct-consumer census attributes four stale picture oracles on saved
Source12:42506 runs46/4/0/0 at01:41:17Z,57.082s/queue339s. Three counts still
expected11; Boulder expected Animals to remain unavailable. The other42 pass.
Only those literal counts and Boulder's one method/name migrate. Boulder now
checks genuine Animals preparation with no current writes, input89/read6CD5/
shift16/count20/budget3, immutable replay and an unknown Signpost guard. It passes
alone on Source12 in32688 at01:43:21Z,31.413s/queue16s. These are test-contract
repairs, with no gameplay tuning. The original red invocations remain red.

Remaining consumers2319 pass481/0/0/0 at02:27:54Z,1m42/queue221s: Runtime362,
World-load52,EGL21 and six older picture classes46. Exact9 fresh XML,
Java25/private cwd/media/EGL/actual S2 ROM/zero skips were checked. The full
World-load class rechecks the permanent restored file after a temporary observer
compile, and retains all older50 cases; only its two natural cases overlap179.
No unchanged unit/retail/Section batch is repeated. Fresh source13 structural
guards31748 pass728/0/0/0 at02:40:19Z,5m02 execution; queue wait was not
retained and is unknown. Exact90 fresh executable-class XML,Java25.0.5.0.2/
private cwd/no skips checked, excluding only the historical no-Jupiter facade. The unclassified
private CD planner scope selects3755 smoke classes. One cached registration,
count joins and direct consumers have bounded impact; this owner/replay/render
selection plus structural guards is proportionate, not a full-smoke/full-suite
or all-game certification.

Command shape, from the private worktree with DISPLAY/WAYLAND_DISPLAY unset:
`python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py --normal -Dmse=off '-Dtest=TestCdR11aObjectAssets,TestCdR11aObjectRetailAssets,TestCdR11aSectionArt,TestSonicCdOpeningWorldLoad#controllerJumpRouteRestartsNaturallyAndServicesGoalPostWithFreshEpochReplay+controllerJumpRouteRestartsFromNaturalCheckpointAndServicesAnimalsWithFreshEpochReplay' "-Dsoniccd.media.root=${SCD_MEDIA_ROOT}" test`.
Remaining consumers select
`TestCdOpeningObjectRuntime,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestCdSpikesPicture,TestCdCheckpointPicture,TestCdAntonPicture,TestCdBoulderPicture,TestCdDiagonalSpringPicture,TestCdCollapsePlatformPicture`
with actual media/S2,soniccd.opening.egl=true and surfaceless EGL. Guards use the
existing -Pguards command and LUA_BIN=lua5.4. No public release suite is launched.

Separate controller-only observer61311 passes1/0/0/0 at02:18:53Z,
30.921s/queue626s. All2347 calls return, including400 fresh calls after load1947.
Final actor X16=222745856,Y16=48966912,routine2,flags32,XV/YV/GV0; section0200,
remaining0/head0. It reaches a controller bound while pushing, not an unsupported
implementation or act-end boundary. Its inherited summary label still names the
first epoch replacement; the400 FRESH_RETURNED rows establish that no second
replacement caused this stop. The permanent196490-byte World-load source was
restored with fresh mtime before481. Do not infer a fresh raw object link from
an older epoch's diagnostics or resume a previously failed2006 call.

Frozen independent reviews find no actionable P1/P2: natural proof
ad3e317cb968ed654b2a70a1e0266512ef1dde22976e73b6bc878be7b49ed6b2;
source/service/contracts b2d28da793b93d3295c91250c64c33d04fbf6edba1e7edc3292e48138781f2dd.
Reviewers did not execute. The original first asset-author attempt stopped before
writing because Root's E alias was ambiguous; the corrected absolute-path brief
produced the final files. All R1 mirrors remain immutable; no Java repair was
needed after the179/481 positives. No remote/main-tree operation or new quiet-
hours visual publication occurs. Preparatory end-of-act source research is
separate from a reached route frontier and does not authorize implementation.


### Controller navigation after source13 — 26 October 2026

Private saved cc74a36ed, production unchanged. A later B press at1770 genuinely
jumps and avoids the earlier recipe's Kill1802; all3001 calls return before a
pushing bound. Appending a third32-row B press at3002 reaches the actual next
implementation boundary inside3211 after3210 complete ordinary calls.
UNKNOWN_SOURCE23AC08/7400 occurs only after the final queued Boulder tile and
native POP; section0400/count0/budget3/prior table/RAM are retained, and both
owners close. ART_SONIC_HOLE/114 is an inventory hint until actual source/PLC/
finite-reader authentication. Physical36 is real ID21 subtype15 under Sonic;
this is natural upper-path platform contact, unlike the earlier offscreen75.
The permanent test now preserves every old52 case byte-for-byte and appends
controllerThreeJumpRouteRetainsNaturalCheckpointReplaysClosedCutsAndStopsAtUnknownSource14.
The fresh focused positive at03:27:53Z passes1/0/0/0 on Java25.0.5.0.2/actual
media/privatecwd (Maven30.097s, queue60s). It discovers partialBoulder3205
(count16) and first real ID21 standing3206, then verifies full/course closed
3001→3002,3205→3210,3209→3210 with checkpoint/live/knowledge comparisons before
forwarding. The final attempted3211 is an asserted terminal negative after
the last Boulder tile/POP; it is never restored or resumed. Production remains
exactly cc74/source13. Old52 coverage from the source13 consumer run remains
attributed there; no unchanged179/481/728 coverage is repeated for this append.

Independent source-model research now authenticates Hole Java113/native114,
header800E/14tiles, plus the three producers already queued behind it:
Waterfall327/328/29tiles, Disc167/167/12tiles (real next-header80), and
Shadow23/24/16normal tiles. The four full transfers fit existing disjoint
physical regions; no Region/C30/span/preload change follows. Root has accepted
one existing-helper cached read each and append sources14..17, with old13
identities preserved. Source-only assets and native-service proof authors are
working externally; no new source is imported or claimed executed yet.


### Reviewed route-proof correction and positive — 26 October 2026 03:50 UTC

The first positive1/0/0/0 was narrow evidence with four subsequently identified
P2 assertion gaps. Separate RootR2 adds native third-jump acceptance/FM_JUMP3002,
complete comparisons after every replayed row, finite Boulder271 completion,
and terminal clocks/load-boundary/unreached Ring/spill-tail checks. Its fresh
review found no additional P2 but caught one P1 compile-access error in a hash
call. RootR3 replaces only that inaccessible helper with public JDK MessageDigest
and HexFormat; production is unchanged. R2 job17990 waited624s, began testCompile,
and was cancelled exit130 as known-uncompilable before tests; validation is
incomplete, not a behavior pass or observed compiler-failure result.

RootR3 frozen228570B/SHA
 db8c1a44b9973e751140aadaab26b44d52eecb5fa42f41042a82892ab8afcf39
passes the same focused selector1/0/0/0 at03:50:46Z in session67192:
Maven30.883s/queue75s, fresh exact XML/J25.0.5.0.2/privatecwd/actual media/no skips.
The native reader oracle is separate from resident pixels and retained savedRAM:
Boulder payload49/1761 consumed bits yields271/source2365D1/read8080/shift15,
repeat0/pixel0/previousRow0. Terminal VInt/native/engine counters advance once;
Ring/spill tail does not, no load epoch/boundary is invented. Old52 source bytes
remain exact. Native image/timing parity and completed3211 remain unclaimed.
The four accepted queued producer registrations are still external/UNRUN.


### Actual queued Hole/Waterfall/Disc/Shadow sources — 26 October 2026

Source17 candidate appends four exact producers through the existing synchronized
cache/helper, after the old thirteen: Hole PLC5/row2 at7400,14tiles/Java113/native114;
Waterfall5/4 at7C80,29/327/328; Disc5/5 at8120,12/167/167 despite inventory166;
Shadow5/6 at8300,16/23/24, normal0010. Disc's actual extra80 is Waterfall's first
header byte;1D remains outside both views. No new decoder, bank, Region, preload,
logical/picture span, body or public API follows. Both cardinality guards become17.
The actual queue is incremental10, not the canonical full5 association.

Matched source13 RED53855 runs6/1/0/0 at04:01:28Z (33.248s/queue121s): the three
format/finite unit cases and two regional negative cases pass; USA expects17
and sees13. Earlier35627 stopped at test compilation (23.799s/queue116s) because
a new fixture omitted checked IOException; it ran no tests. Root's separate
unitR2 adds only that declaration to the helper and first test caller.

First imported candidate15639 runs193/5/0/0 at04:09:51Z (2m10s/queue323s): all
93 unit and42 retail cases pass; Section53/58 pass. Its five failures assert
cross-owner identity after calling openingObjects() again. That factory loads
a new authenticated ObjectAssets each time; the cache is per ObjectAssets, not
per level-assets factory. RootSectionR3 retains one owner for the cache test,
compares full finite inputs across distinct owners, and retains genuine input
identity across the SAME captured decoder. It also includes the independently
reviewed correction for Waterfall's unknown failed final tile8000: preserve
all values/masks and expect strict UNKNOWN_PATTERN, never inject seed bytes.
The five affected cases pass5/0/0/0 in59496 at04:12:31Z (33.992s/queue0).
This is188 earlier passes+narrow5, not a fresh193all-green invocation.

Affected current consumers83689 pass429/0/0/0 at04:26:49Z (1m40s/queue97s):
Runtime362, real EGL21 and six picture classes46. Exact8freshXML/J25.0.5.0.2/
actual media/privatecwd/EGL and knownS2 donor/no skips checked. Nine EGL source
cardinalities and seven Runtime/three picture counts now17; logical19/658 and
all current physical extents remain unchanged. No new quiet-hours visual output.
Independent source/guard and Root test-repair reviews find no open P1/P2 after
those recorded corrections. The broader permanent WorldLoad53 consumer remains
pending promotion; no fully green delivery is inferred from429.

Fresh controller observer53617 (same4233 planned recipe) completes3221 then
stops inside3222 at unowned object22/live40. N's index names it Tamabboh.
SourceHole prepares3211, services14 across3212..3216, and Pata prepares3216;
closed3221 has15 Pata tiles and remaining35. The failed3222 VInt commits3 more
Pata tiles before Body/spawner/earlier objects reach the unowned Tamabboh.
Slot40 was zero at3221; its staged ID22/r0/XY4624,529/subtypes0/saved101 survive.
Both owners close, no3222completed/resume. Waterfall/Disc/Shadow have component
service evidence but are still unprepared on this natural route. Source-only
permanent service/replay promotion and Tamabboh native audit are underway.


### Permanent queued service/replay promotion — 26 October 2026

The same53 WorldLoad cases now pass at04:43:06Z (71775,35.708s/queue10s),
including the promoted controllerThreeJumpRouteServicesHoleReplaysClosedCutsAndStopsAtTamabbohSlot40.
Original52 migrate only two complete source-bank counts/lists to17; selected
PLC4 descriptor lists stay unchanged. The former53rd test's actual upper route,
natural checkpoint/third jump and pre3210 replay remain. Nine selected valid
cuts include3210/3211/3213/3215/3216/3220; full/course comparisons run BEFORE
forwarding and on EVERY intermediate closed row through3221. Hole14/Pata15
current bytes/masks/nonwrites are distinct from availability of all17 sources.
Only after every valid replay does3222 assert the real Tamabboh40 spawn/body
boundary, including the extra3 VInt Pata tiles, clocks and unreached tail.
No failed3222 capture or continuation is admitted.

AuthorR1 initially fails compilation at3181/3182 (29599,04:40:40Z,27.142s/queue22s):
LiveAux.knownMask is an integer, not a byte-array wrapper. Separate RootR2
changes only that explicit knowledge comparison to integer equality. Frozen
255736B/SHA317e3c853cb7d2dd344c6fcecb3ae3596d2b6082d17ea2df7b04c35779bd3ab5
is the tested53-case source; exact fresh XML/J25/privatecwd/media/no skips checked.
Independent final route review is complete: no open P1/P2 in the exact R2
route and three production overrides. Review is static, with the53 positive
attributed to Root. Source17 is ready for a private local save.

Change-based inspection at base626077597 selects3755smoke classes plus guards
because these private CD paths are unclassified, not because shared engine
algorithms changed. Focused actual-media owners/services, all affected runtime/
EGL/picture consumers and complete WorldLoad cover the bounded registration
impact. No constructor, field, public API, physics, common renderer, build or
selection-policy implementation changed; existing structural code is byte-
identical outside the source-cache appends/two numeric guards. The last fresh
728 structural checks belong to source13 and are not relabeled new passes.
No broad3755/full-suite rerun is claimed; release-only full-suite stays unused.

A parallel source-only FM audit57659B/SHA25cc1ed1ea9250cabaac0046741b57717f7e568b7aba4a1784cc7a14de89fde4
pins native queue/R/YM-status/TimerB/track order and missing lifecycle authority.
Its direct UpdateTracks composition proposal is unaccepted/unimplemented, not
playback. Tamabboh source audit62991B/SHA7379f684e1b09ecab95d0d18ccd5336a504965f29450c01090a9dfee3d987231
pins body/missile closure and the existing picture gap440. Assets are frozen
externally (five tables/three getters/selector4,0) but UNRUN/unimported. Body
source-only authoring is active with nine slot aliases and synchronous read/
angle/FM ports; no Tamabboh production is in the current tree. Preserve the
picture gap as the current scoped boundary; any later admission must use exact
selected current8800 bytes with no duplicate art or fabricated logical alias.


### Tamabboh component intake, 26 October 2026

Source17 was saved privately as baf29bd50787ce33c5c737be6eaf5f298752d70d.
Frozen Tamabboh views append five tables (59 total), three lazy cached visual
getters and selector4/subtype0 only, preserving the complete17-source suffix.
No source, decoder, physical region or current-art preload was added. Actual
USA normal/damaged roots20B968/20B97E share the complete62-byte include;
missile20BA70 keeps42 bytes and its odd frame offset31. Three ordinary mapper
registrations use the unchanged exact-byte join. Child Y subtraction is decimal
10, matching native immediate #10 without a hexadecimal prefix.

Tests-first Source17 failed testCompile on absent tables/getters (70196;
25s queue, duration unavailable from bounded output; no tests). First complete
asset selection47999 executed146/1/0/0 at05:06:15Z (2m01s/queue0). All45 retail
cases passed. The sole inherited FloatBlock short negative loop still rejected
selector4; separate RootR2 adds only that exclusion. Narrow38502 passes1/0/0/0
at05:07:49Z (31.762s/queue3). Do not label this a new146-case green invocation.
Independent asset review17900B/SHA9bddce26e5d54782ef4ac824b72d6a2d80c9b9f03a5b2bcaee34d5ca89c7a2be
records the R1 P2 and exact R2 source resolution; R1 remains unchanged.

Body tests-first31917 fails testCompile on absent CdTamabboh/aliases at05:09:36Z
(23.016s/queue0), no tests. BodyR2 changes only two source-derived CSV mistakes:
641 masks to640, so the deletion vector is768; raw254 selects warp and clamps
to period2/index7 for savedID1. Exact R2 test52887B/SHA301074c399aec739d01f20a11b6763f107883023c80cb1a216849a7c8fca3764
passes COMPLETE TestCdTamabboh110/0/0/0 in30106 at05:18:55Z (1m28s/queue112).
The same command's two new mapping tests against the old mapper yield2/0/1/0,
with the actual-owner case stopping at unregistered20B968. That is a matched
mapper RED, not a112-case positive. The frozen three registrations are then
imported; COMPLETE TestCdObjectMappings passes40/0/0/0 in39749 at05:22:18Z
(1m39s/queue13). Each terminal XML is fresh, Java25.0.5.0.2/private cwd/supplied
media/zero skips checked. These are component and format/actual-media proofs,
not native gameplay/image parity or a completed3222.

The new body has nine enum aliases only, synchronous fresh96-slot angle/player/
FM ports and scalar completed metadata. Root must still bind actual phase,
cached invocation, stable player Frame, primary-angle CAS and FM ledger. Independent body/mapper static review is complete with no new P1/P2; the exact
two R1 CSV defects are resolved by RootR2. Source-only Runtime composition is
still active; Runtime
and its362-case test file remain exact saved Source17 at this checkpoint.
C29/19 logical banks/658 patterns and the unadmitted native440 gap remain.
No Tamabboh natural route or constructed World/GPU proof has run. Current
component changes remain uncommitted; the full USA/JP/EU goal stays active.


This component-only save uses bounded owner/actual-media/mapping checks because
all shared algorithms are unchanged and Runtime is still byte-identical to
Source17. It is not a new full smoke/guards/full-suite result. Fresh structural
and combined-consumer checks belong to the later live integration. The final
body/mapper review37945B/SHAcb2efb87bf2d8136397b9f53db1711900e0735b7ccf08e4672f3ff8c263e1542
covers all frozen body/aliases/root additions and RootR2 test substitutions;
actual Root results remain separately attributed in that static report.


### Live Tamabboh intake and actual Spinning Disc boundary, 26 October 2026

The frozen Runtime235934B/SHAa9deb71e328315efad7e59a57a09c56f418ab94b70cdfc21a903084aa7676c6a
adds cached actual-body invocation, stable Frame player, fresh96-slot angle/
player-read/FM cuts and typedA0; Source17/C29/19banks/658 stay unchanged.
AuthorR1's compound-var declaration and decimal22 expectation are repaired
only in RootR2. Tests-first52364 stops testCompile on missing TamabbohFm in
saved Runtime at05:33:33Z(28.598s/queue0), no tests. Complete389 Runtime62957
at05:36:28Z yields389/9/0/0(1m35s/queue0): all362 inherited and18 new pass;
nine new test oracles fail. RootR3 preserves full prior Body sound metadata
instead of empty/A0-only ledgers and compares three complete766-byte saved
banks by byte value rather than CdAssetBytes identity. It changes nine new
methods only, no fixture/input/production adjustment. Narrow56269 passes9/0/0/0
at05:41:12Z(31.185s/queue0); not a new389-case green invocation. Exact XML,
Java25.0.5.0.2/private cwd/media/no skips verified. Final Runtime static review
reports no new openP1/P2 and records the known R2/R3 test resolutions.

The unchanged genuine observer259346B/SHA157f1a7ef2abcf181e6e6d30cd236d075eef8fdf2c42f266eceef3adf84ee3f9
is reused once in57473:1/0/1/0at05:44:09Z(32.200s/queue0), fullXML/real media/
private cwd/J25 checked. Same4233-controller recipe, no ingress, original
production generation1: every ordinary call through3258 returns. Call3259
commits its legitimate VInt/Body/spawn/earlier-dynamic prefix, then stops on
unowned ID09/live46, nativeFFDB80, before Init. Pinned ObjectIndex line16 names
ObjSpinningDisc. Before3258 slot46 is all zero; after3259 exact placement image:
09000000000000001350000003880000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000
(X4944,Y904,subtypes0,savedID0). Runtime/Frame FAILED/closed; no failed-call
capture/restore/resume, later CPU SAT build/scroll/tail or epoch transition.
Permanent WorldLoad255736/317e3c85 is restored644/freshmtime after terminal.

Native queue milestones now naturally observed:3233 prepares Waterfall29;
3243 completes Waterfall and prepares Disc12;3247 completes Disc/prepares
Shadow16;3253 completes Shadow/prepares Tamabboh59;3258 has15 Tam tiles current,
44 remaining. Failed3259 VInt commits three more,18 current/41 remaining.
Waterfall/Disc/Shadow are now naturally completed producers, superseding the
older Source17-only-not-yet-prepared limit. This observes service/current bytes,
not native timing or completion of the whole59 Tam source. Tile440 is offset24
and has no new Tam commit yet; no actual440 picture read was reached here.

Closed3258 actor X16=291823872/Y16=22832128/XV1536/YV2520/routine2/flags2,
links0; attempted3259 X16=292217088/Y16=23477248/YV2576, same routine/flags/links.
Before/after placement chunk4224/right213/left186/saved106,93 ->
4352/right225/left191/saved111,93. Rings3/lives3, score10/nextlife5000,
checkpoint2/spawn1/HUDtimeDEV0/PLCflags3, generation1. Tam40 executes Main:
closed3258 raw64
220424280020b96811f98000020900000000000000000f1004100102000018002c0000650400000000001210ffff60000020b942000000000000000000000000
partial3259 raw64
220424280020b96811f8e000020900000000000000000f1004100102000017002c0000650400000000001210ffff60000020b942000000000000000000000000
The previous integer/fraction4601.8000 becomes4600.E000 with native delta-A000;
Y521 stays, animation timer24->23. No Fire/nativeA0 or440 closure follows from
this Main observation. Root still owes permanent3258/replay/3259 promotion,
combined consumers/fresh guards, current440 and constructed World pixels.


The permanent53-case route candidate is now frozen. RootR2 changes only the
old GoalPost surface assertion from C30 absence to presence while preserving
C29. Full52671at06:25:50Z executes53/1/0/0(35.776s/queue0): all old52 pass,
new case's invalid physical95 Ring proxy fails. RootR3 replaces it with the
actual one Ring2-to6 identity transition, collision0/priority1, preserving
reward3 and full sound checks. Narrow38305at06:32:29Z reaches the final prefix
but fails on slot58: earlier Ring Init parents create later children before
unsupported46. RootR4 adds a comparison-only unwarped native Ring Init store
projection over the placement images (N Ring/Main.asm:38-210), preserving
FindNext order, dirty bytes, collected bits, anchors and LOADED clears. It
publishes nothing and invokes no body. The entire later tail remains checked.
Narrow4970 passes1/0/0/0at06:42:58Z(34.548s/queue0), exactfreshXML/Java25/
privatecwd/media/no skips checked. This is not a fresh53-case green run.
RootR4 source286694B/SHAcd1649d48fa685feceb60f8acd796e485ed6200e06ab419f2849b0ae3802834a
compares all valid full/course cuts before forwarding and every intermediate
row through3258, then attempts3259 once. Independent final route review remains
in progress; Runtime/current659 client integration and combined checks remain.

C30's tests-first74657 stopped testCompile on absent arity30 at06:01:20Z
(26.356s/queue0), no tests. First positive72889 executed13/1/0/0at06:04:53Z
(1m32s/queue27). Twelve pass; the remaining immutable-mask test compared fresh
CdAssetBytes wrappers at161 and168. RootR2 changes only those assertions to
all-byte array equality. Narrow25358 passes1/0/0/0at06:06:56Z(29.918s/queue0),
fresh exact XML/J25/cwd/media checked. Static review27066B/SHA80a3b31386c79ed47377b812e9e5cd7a7563aabd6cdb2345f51b060aa47bb7ed
finds no productionP1/P2 and records the R1 mask defect/R2 resolution. This
proves a component selected-current singleton, not a wired659-pattern World.


### 26 October 2026 — live Tamabboh singleton consumer join

On private4a78c8b6e plus the owned live-Tamabboh changes, Root appended
LogicalBanks.tamabboh as component20, preserving all nineteen old bases and
constructors. Compatibility19 uses Math.addExact(collapsePlatform,57); both
Runtime current-camera conversions now select C30. The client reserves659
patterns. No new art producer, preload, physical Region, capture partition,
phase, factory argument or gameplay setter was introduced. The extra binding
is only current native440 at8800; the other58 retain Plant/Ramp aliases.

Tests-first12220 stopped in test compilation against the old19-component API
(missing20-argument construction/accessor), with no test execution. Combined
38429, command `env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3
$OPENGGF_ROOT/tools/testing/maven_queue.py --lean -Dmse=off
-Dsoniccd.media.root=$SCD_MEDIA_ROOT
'-Dtest=TestCdOpeningObjectRuntime,TestCdTamabbohPicture,TestSonicCdOpeningWorldLoad'
test`, completed at06:58:56Z in1m44s, queue0:458/0/1/0. WorldLoad53 and
picture13 passed; Runtime392 had one error in an inherited invalid-Platform
fixture. Its deliberately overflowing compatibility19 construction now fails
before the intended factory assertion. RootR2 supplies explicit20 arguments
and a disjoint singleton758 in that loop, retaining the old57-tile bounds
assertion; production is unchanged. Exact corrected-method run30993 passed
1/0/0/0 at07:08:30Z,32.802s execution after421s waiting. Fresh XML confirms
Java25.0.5.0.2, private cwd, actual media property and no skips. These are457
passing combined cases plus one corrected focused pass, not a fresh458 pass.

Independent source reviews resolve the permanent route's exact Ring identity
and earlier-parent allocation oracles and find no additional P1/P2 in the final
route or659 consumer delta. Their execution evidence is attributed to Root.
The newly wired659 client still needs its actual Tamabboh World/GPU proof,
combined adjacent consumers and fresh structural guards before local save.
Spinning Disc assets are separately authenticated and statically reviewed,
remain unimported, and do not complete its player-rotation/Solid/control owner.


Adjacent-owner run83314 completed at07:15:01Z,45.575s/queue7s,
629/0/0/0 with exact actual-media selectors TestCdPlayerFrameRuntime413,
TestCdPlayerObjectTouch19,TestCdObjectDrawQueue16,TestSonicCdRestartCarry31,
TestCdTamabboh110 andTestCdObjectMappings40. The queued command uses the same
unset display variables, LUA_BIN=lua5.4, --lean, -Dmse=off and CD-media property
as above. Six fresh XMLs prove Java25/private cwd/media/no skips. Fresh
separate-JVM `maven_queue.py -Dmse=off -Pguards test -B` run51732 completes
728/0/0/0 at07:22:01Z,5m12s/queue0; ninety fresh executable XML classes total728.
The dry change-based plan selects3757 smoke classes solely because private CD
owners are unclassified. Focused CD consumers plus all structural guards are
the proportionate bounded selection; no generic S1/S2/S3K algorithm changed.
This is neither a full-smoke nor a release-only full-suite pass.

A temporary read-only println in the SAME permanent controller case measures
closed3257 in run74288:1/0/0/0 at07:23:37Z,32.802s/queue0. Native FG camera is
4350/210; placement previousChunk4224,right213,left186,rightSaved106,leftSaved93;
Frame cameraXCentre96; actor X16=291430656,Y16=22201344,XV/GV1536,YV2464;
bounds0..10391,bottom784. Fixed constructed actorX=(4446-96)=4350 gives clamped
camera target4254; old4350 and target4254 lie in chunk4224. This supports ONLY
the horizontal no-new-spawner prerequisite. It does not prove flat terrain,
new pixels or a complete constructed scene. No inputs/state writes changed;
permanent286694-byte RootR4 was restored exactly after the diagnostic.

The independent actual-EGL Ring pickup/full-course case94046 passes1/0/0/0
at07:35:28Z (30.521s,queue26s), with the final C30/twenty-base consumer and
actual USA media/S2 donor. The external native Tamabboh World candidate
599058B/SHAeee3685ec11dcb58ccb52c4039c051adbdcb0beeec22ac8ba6d2de57a409c0d6
compiles but run96648 fails1/1/0/0 at07:41:05Z (30.065s,queue0), before any
constructed ingress, at its fixed-foot terrain prerequisite. Read-only
probe39773 repeats that failure at07:45:58Z (30.803s,queue0): fixedactorX4350,
probeY357, both distances26 and no angle write. This is the native blank
31-(357&15) result, not a floor. No search, fitted pose, terrain/physics/
camera/spawner repair or failed-call resume was used. The long candidate
stays external and unpromoted. Its native Wait/Fire/children, actual10 views/
59 scene offsets, full/course pixels and cleanup obligation remain open.

A separate short test now independently checks current440 and resource
ownership without that route prerequisite. It boots the actual USA World,
then uses the existing pure SectionArt owner to service actualPLC9 to END,
including cached Pata50/Anton22/Tam59. It does not service or change the live
Runtime. All59 current component tiles match the cached authenticated source;
C30 renders genuine440 in all four flips with actual palette/version and
complete GPU pixels. The live capture and its unknown440 remain unchanged.
All659 client entries are installed; transparent TEST witnesses may fill
only missing old658 entries. The singleton must be a genuine current upload.
Module close must remove659 and retain unrelated actual producer identities,
followed by nested session/graphics/boot cleanup. No PNG or PASS artifact is
produced. This is component-current renderer/resource evidence, not the open
native Tamabboh World scene or natural queue's completion.

The matched control18997 changes only the client back to saved658 while
retaining this same short test/Runtime/C30. It executes1/1/0/0 at08:09:19Z
(1m22s,queue295s): all prior pixel/version/nonwrite/installation checks pass;
only post-close offset658 fails with residual actual patternID131730.
Restoring final659 gives positive47662:1/0/0/0 at08:32:48Z (1m22s,queue870s).
Both use queued Maven with -Dmse=off,
-Dopenggf.headless.gl=surfaceless-egl,-Dsoniccd.opening.egl=true, actual media
and the authenticated absolute S2 donor path, exact selector
TestSonicCdOpeningWorldEgl#actualEglCurrentTamabbohSingletonRendersAndRetiresThe659thClientPattern.
Fresh XML proves Java25.0.5.0.2, private cwd, all flags and zero skips.
Final source-only guards73500, `maven_queue.py -Dmse=off -Pguards
'-Dtest=TestJunit5MigrationGuard,TestNoAssertionFreeDiagnostics' test -B`, pass
5/0/0/0 at08:43:22Z (28.905s,queue381s); both fresh XMLs/privatecwd/Java25
checked. The earlier complete728 guards cover unchanged production; these
five additionally cover the new short test. No unfiltered smoke/full-suite,
whole-act, full-USA, JP/EU or native image/audio parity result is claimed.

Final production pins before local save are Runtime236887/SHA
b059bbd2e8cde70a4ff2f055386698e34ccb4a9de13e2e09848b126cffa344e5,
ObjectPresentation105618/SHA
6ca4154bfb2bee104190a02bd1c69f90e65da5e306ea2ed0876ccf27e54e2b21,
and PlayerPresentation56282/SHA
a58a03cf792e5bd90fc1f2097726fa28372bcf55e7302248ec725b9c1640f38e.
Final EGL543723/SHA82827d4d189023bc3346d539243db000dac577e8b7eae89122a7bd16bef49a02
contains22 tests: all21 prior tests plus the short independent resource case,
not the failed external long scene. Independent bounded static reviews find
no additional open production/resource P1/P2; the long-scene fixture P2
remains explicit. Main/remote remain untouched. Next work is the reached
Spinning Disc09, starting with unimported authenticated views, genuine plain
Solid prefixes and raw rotation-byte ownership; none completes3259 yet.

### Spinning Disc components and Frame publication, 26 October 2026

The private tree now has authenticated Disc PlayerFrames16, animation8 and
mappings36 at appended table ordinals59..61, total62. The sole cached visual
getter and selector6/subtype2=0 reuse existing owners; all255 other subtype2
values remain unadmitted. Existing Source17 bytes/cache/read extents are exact.
Tests-first46693 stops testCompile on the absent views/getter; after importing
the frozen asset owner,71893 passes156/0/0/0 at08:58:06Z (1m56s,queue0):
108 unit and48 regional retail cases. Regional operand/data checks do not
constitute JP/EU gameplay support.

The mapper is exactly one new ordinary root2095C6; no new current pixels,
bank, preload, Region or logical span. The author hit provider capacity after
freezing both files, before its report; Root preserved those files, inspected
the full delta and documented that interrupted custody. Removing one109-byte
production line recovers the old mapper; removing the appended test block
recovers all40 old tests. Old mapper82118 fails exactly on unregistered2095C6.
Full positive33634 runs42/1/0/0 at09:17:02Z: all40 old cases and the new raw
map/SAT/four-flip geometry case pass; the other new test incorrectly expects
37 generic assets instead of the unchanged106. RootR2 changes only that
literal, production unchanged; narrow91496 passes1 at09:28:47Z. This is41
passing cases plus the corrected focused pass, not a fresh42-case green run.

Plain SOLID_OBJECT enters the existing opt-in prefix cursor with no forced
Top type1. Legacy run/6 and the actual algorithm remain exact. Tests-first
57036 has34 identical old-admission errors; final51676-byte owner plus the
one reviewed odd-row fixture correction passes232/0/0/0 in60341 at09:04:45Z
(1m27s,queue wait unknown). That correction makes the intended raw odd lookup
use sprite4, rather than sprite44 selecting another resident chunk.

Body.State appends required raw rotationAngle2B as component8 with no default
overload. Cold ClearObjects owns zero; ordinary reconstruction, capture,
Solid/Hurt/Kill/Spring/tunnel/checkpoint carry the actual byte, and genuine
new-LevelStart ingress clears it. Charge2A and distance39 remain distinct.
All affected production and fixture constructors migrate explicitly, including
38 Root-only callers. Tests-first62626 stops testCompile against State7.
After the two production and seven fixture imports,56094 passes1203/0/0/0
at09:35:34Z (1m53s,queue0): Body282, Frame427, Playable18, Carry31,
Runtime392 and WorldLoad53. Exact fresh six-class XML/J25/private cwd/actual
media/no skips checked. Actual Ring EGL/full-course25515 separately passes1
at09:38:02Z. These precede the following Frame-port refactor.

The new bounded Disc body uses genuine plain Solid once, native acquisition,
orbit, cursor1B, controls and custom A0 jump, shared animation once, Draw and
despawn. It has no Frame/pool authority beyond its existing component ports.
Initial62983 runs75/6/1/0 at10:00:29Z: disclosed CSV literals and an invalid
terrain-loader fixture fail. RootR2 preserves production and corrects six
test spans in four methods: left orbit240; cardinal-before-directions18/16/18;
jump from actual YV0 to-1664/-896 and unchanged GV567; and a constructible
flaggedF9/player-sprite44 unavailable floor at native21F2E0. The latter now
checks exact native stop details. The withdrawn angle80/X247 concern is not
changed. Narrow77824 passes14 at10:04:56Z. Bounded independent review finds
no new production/value-contract P1/P2 and substantiates the exact repairs.

Frame's new ObjectSolidPublication holds four immutable values, with no owner
or execution token. The two old apply methods reuse one aggregate builder;
their validators stay byte exact. New prepare methods build the complete
State/Environment before a Runtime can publish its pool overlay. Prepared
apply does full-pair CAS then only assigns the two ready references. Direct
Disc validate/apply uses the same pure33-site value validator and already
prebuilt result. No fields, captures, clocks, services or phases are added.
Private invocation/source/sequence/exact-once/FM and cross-owner preparation
remain the future Runtime's responsibility; the public values grant none.

Tests-first13589 stops testCompile on the six absent Frame ports. Final90167
Frame and313502 test fixture pass combined49018:1058/0/0/0 at10:17:02Z
(1m38s,queue0), comprising Frame591, old Runtime392 and the entire repaired
Disc75. Thus all75 now have a fresh positive. Actual WorldLoad53 plus the
exact Ring EGL/full-course case35430 pass54/0/0/0 at10:27:41Z
(36.984s,queue0) after this refactor. Fresh exact XML, Java25, private cwd,
media, EGL opt-in/surfaceless and donor flags/no skips checked. Frame-port
review finds no actionable issue in this finite value-adoption scope.

Focused commands use queued Maven, unset DISPLAY/WAYLAND_DISPLAY, Lua5.4,
-Dmse=off and the actual CD media root. Exact selections are the classes and
methods above; the existing actual-EGL method is
TestSonicCdOpeningWorldEgl#actualEglNormalInputPickupAndFullCourseReplay.
The component/Frame positive selects TestCdPlayerFrameRuntime,
TestCdSpinningDisc,TestCdOpeningObjectRuntime. Complete structural guards are
the remaining check before this component save. The dry3757-class smoke
selection is disproportionate to this CD-only slice; affected CD owners,
real World/replay/GPU consumers and guards are the bounded selection, not a
release full-suite or full-game claim.

Private tooling commitf381b4235 separately adds a frozen-packet checker,
explicit pinned import and exact fresh JUnit summary, plus mirrored skill.
Thirty-nine Python safety tests pass, independent review finds no actionable
issue, and Root exercised real mapper/rotation/body/Frame imports and result
attribution. It neither runs Maven nor bypasses its queue or preserves pass
caches. It ignores stale unrelated XML while rejecting stale requested and
unexpected fresh classes. Tool worktree cleanup preserves an unrelated
pre-existing TraceChaser checkout difference.

Runtime still has no ID09 dispatch at this checkpoint. Its source-only join
is being authored separately. The natural recipe still returns3258 and
stops3259 before Disc Init; no Disc contact/World, controlled Body, native
timing/image/audio parity, whole-USA or JP/EU certification follows here.

Final component guards11366, queued `maven_queue.py -Dmse=off -Pguards
test -B` with unset displays/Lua5.4, pass728/0/0/0 at10:44:00Z
(5m06s execution; queue wait not retained). All90 fresh executable XML
reports, individual identities/counts, Java25/private cwd and zero skips are
checked; stale unrelated reports are excluded. Current six production and
twelve test files match their exact frozen packets/Root-only repairs.
No Java changed during the run. The local component save follows these
bounded checks; it does not import the pending Runtime join or move3259.

## Spinning Disc Runtime integration — 26 October 2026, private/local

Based on private `558dd7d00`, the cached ID09 body now runs at its physical
dynamic slot with the actual time-stop word, pre-scroll camera and raw gates.
Its private invocation cursor authenticates synchronous Solid/direct-player/FM
calls. Pool overlay, same-Frame publication and immutable sound ledger are
prepared before any owner assignment; stopped Solid prefixes publish before the
identical native exception closes the visit. Public records/equal values do not
grant replay or once-only authority. LogicalBanks20/C30/659, source17, capture
partitions, clocks and ordinary-tail ordering remain unchanged.

Test-first26758 failed compilation on the absent SpinningDiscFm record. After
production import,35484 completed410/3/0/0 at11:15:28Z (queue315s, execution1m38s).
The three new fixture failures were independently traced to animator flip restore,
already-own-stand preserving incoming YV0400, and Solid moving Y before an
already-rolling jump. RootR2 changes only four spans in those three tests;
production is unchanged. Narrow83319 passes3/0/0/0 at11:22:27Z (32.475s, queue0).
This records407 earlier passes plus3 corrected cases, not a fresh410 pass.

Actual EGL29031 passes2/0/0/0 at11:26:00Z (31.641s, queue0), selecting
`TestSonicCdOpeningWorldEgl#actualEglNormalInputPickupAndFullCourseReplay+actualEglCurrentTamabbohSingletonRendersAndRetiresThe659thClientPattern`.
Commands use the queued wrapper, -Dmse=off, actual media/S2, surfaceless EGL and
explicit EGL opt-in. Exact fresh XML, Java25/private cwd and zero skips were
checked with the integration helper. This covers existing World consumers and
resource retirement; it does not establish native Disc contact or Tamabboh Fire.

Independent Runtime review18501/SHA51c874f310c32a518c5a794f67d1f091a4888b25c1aa198dfd741ea2724cdbea
finds no actionable P1/P2. Fresh guards88371 pass728/0/0/0 across90 exact
source-selected executable classes at11:33:40Z (5m05s,queue15s), Java25/private
cwd/no skips checked. No additional review loop was requested.

Temporary controller observer98700 then completes3420 ordinary calls; first3421
stops unowned ID07/live63 (native ObjTunnelPath). It is1/0/1/0 at11:35:36Z
(33.507s,queue25s), not a passing route test. Genuine Disc Init3259 and subsequent
pre-routine2 call3260 return; rotation remains unchanged and the physical46 slot
is later reused as a Spring. No Disc attachment or controlled Body success is
inferred. The source-only observer was removed after inspection, restoring the
permanent WorldLoad286774/c2899ca8... exactly with fresh0644 mtime. Its full53
case route promotion and the new Tunnel Path source audit are pending.

At closed3420 cameraX4481/Y521 and placement previousChunk4352 precede a new
chunk visit. Failed3421 has native07 at FFDFC0/X13F0/Y00F0 with routine0 and
retains earlier VInt/Body publications before terminal closure. External
camera/saved-bank diagnostics belong only to the last closed cut, not the
partial failed call. No capture/retry/continuation of that failed visit occurs.
Controlled Body API design is accepted but implementation is deferred: its
bit0 boundary was not the observed next frontier. Full USA then JP/EU, native
timing/pixels and full-act coverage remain open; no main/remote integration.

### Permanent Disc continuation and SAT identity correction

Frozen route R1 preserves53 cases by changing only the one method name and its
post3258 tail; Root independently reconstructs the exact286774-byte J baseline.
First24230 completes the healthy forward/replay work but fails at12:04:02Z
(35.446s,queue0),1/1/0/0: it compares historical3420 CpuSat reference identity
after replay has legitimately built a fresh immutable SAT. RootR2 changes only
that comparison span: all640 bytes, sprite count and provenance must match;
the actual current reference remains the before/after failed-call witness. No
production or replay rule changes. Narrow88410 passes1/0/0/0at12:07:48Z
(34.005s,queue61s), exact fresh XML/Java25/privatecwd/media/no skips checked.

The promoted method is
`controllerThreeJumpRouteRunsDiscAndStopsAtTunnelPathSlot63`: genuine returned
Disc Init3259/Main3260; ordinary input through3420; full/course3258→3259,
3259→3260 and3419→3420 compare complete checkpoint-aware state before
forwarding and after each row. All earlier proof blocks remain byte-exact.
It then attempts3421 once, verifies unknown07/native FFDFC0 placement plus
earlier Body/VInt/terminal nonpublication, and never resumes that failed visit.
It deliberately does not claim a complete96-slot partial dynamic-prefix oracle
or any natural rotation/controlled Body. Independent route review and combined
affected consumer/test-source-guard validation are pending.

### Final affected Runtime/route consumers

Combined76706 passes470/0/0/0 at2026-10-26T12:21:00Z: Runtime410, allWorldLoad53,
Ring/full-course and current440/659-retirement EGL2, JUnit-migration guard3 and
assertion-free-diagnostics guard2. Queue353s; execution42.122s. Exact five fresh
XML class sets/individual identities/counts/Java25/privatecwd/media/S2/EGL
opt-in/surfaceless and zero skips verified with the integration helper. Command (repository/media/ROM
paths supplied as OPENGGF_ROOT, SCD_MEDIA_ROOT and S2_ROM):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 "$OPENGGF_ROOT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" "-Dsonic2.rom.path=$S2_ROM" '-Dtest=TestCdOpeningObjectRuntime,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl#actualEglNormalInputPickupAndFullCourseReplay+actualEglCurrentTamabbohSingletonRendersAndRetiresThe659thClientPattern,TestJunit5MigrationGuard,TestNoAssertionFreeDiagnostics' test -B
```

This is a fresh combined410 Runtime pass after the three documented fixture
corrections, and a fresh all53 pass with the permanent3421 frontier and replay
proofs. The prior full728 guard run is separate; the two affected source guards
were repeated here after the test-only route change. No repeated full-smoke or
release-only full suite was launched. The exact all-smoke catalogue remains
disproportionate to this bounded CD-only dispatch/owner slice; actual CD
consumers, real EGL and structural guards cover its understood dependencies.
Independent route review21643/SHA69bc1dd390d05a2f66da6fc77e45e62aa24a6c08ae509a9eb0c3aa6d9b16caa1 finds no actionable P1/P2 in the exact R1/R2 scope; no execution was performed by the reviewer. The local milestone is ready to save.

Next native owner is ID07 Tunnel Path. Its complete source/media audit pins
the unique common row236 and two raw views (GVels8/paths284), no drawing or
allocation and no new art/source/Region. Its own future controlled81/C1 branch
is distinct from actual observed contact; controlled Body remains deferred.
No new3421 successful continuation or whole-game/regional certification.

### Authenticated Tunnel Path raw views — 26 October 2026

This metadata-only increment is based on private saved61945ae41, after the
saved live-Disc milestone42fc3f354. Table ordinals62/63 append the complete
8-byte GVels and284-byte Paths views at USA offsets8D90/8D98. Native owners
are ObjTunnelPath_SetPlayerGVel and ObjTunnelPath_Paths (Tunnel Path/Main.asm:
375–478 at pinned N1ecc5f3). Actual USA/JP/EU operands and complete raw bytes
were authenticated independently in the source packet; the runtime owner stays
USA-only. There is no getter, decode, selector, art/source/Region, current-pixel
or queue change. Existing62 table rows and the full Source17 suffix are exact.
Complete manifests migrate to64; the Spinning Disc three-table subset remains
bounded with limit(3). All selector-negative vectors are preserved.

Tests freeze before production; Root first imports only those tests and
run45986 stops at expected testCompile missing both TUNNEL_PATH enum values.
No JUnit was reached. Root then imports the single frozen production file as
0644 with a fresh mtime. Positive73344 passes163/0/0/0 at12:49:31Z (112 unit,
51 regional retail;2m03s execution,19s queue). Exact fresh classes/identities,
Java25, media and skips were inspected with the integration helper.

Affected consumers34306 complete512/1/0/0 at13:19:24Z (58.575s,queue0):
Runtime410, all WorldLoad53, real Ring/current659 EGL2, mapping42 and source
guards5. The sole failure is the inherited mapper contract's total-table
literal62 versus actual64. Root changes only that literal; narrow53206 passes
1/0/0/0 at13:29:36Z (31.645s,queue0). This is not a fresh all512 green claim;
the other511 identities were observed passing in the prior run. Actual
media/S2/surfaceless/EGL opt-in/private cwd/Java25/zero skips were checked.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 "$OPENGGF_ROOT/tools/testing/maven_queue.py" --lean -Dmse=off "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" '-Dtest=TestCdR11aObjectAssets,TestCdR11aObjectRetailAssets' test -B
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 "$OPENGGF_ROOT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" "-Dsonic2.rom.path=$S2_ROM" '-Dtest=TestCdObjectMappings,TestCdOpeningObjectRuntime,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl#actualEglNormalInputPickupAndFullCourseReplay+actualEglCurrentTamabbohSingletonRendersAndRetiresThe659thClientPattern,TestJunit5MigrationGuard,TestNoAssertionFreeDiagnostics' test -B
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 "$OPENGGF_ROOT/tools/testing/maven_queue.py" --lean -Dmse=off "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" '-Dtest=TestCdObjectMappings#spinningDiscMapperKeepsArtColdReusesOneVisualAndPreservesTheExistingPublicContract' test -B
```

Independent static review24737B/SHA19bbcbac80685b3e19507ad1a5824a412bbb71ddbad545389a194e8fe5b9da6e finds no actionable P1/P2; no execution by the reviewer. Validation is bounded to actual metadata consumers; a broad smoke repeat is disproportionate to these two raw enum rows. The component Body/State and prepared Frame packets are separate, unimported work. The permanent route still returns3420 and fails3421 at unknown07/live63. No new object dispatch, controlled Body, native image/timing or regional gameplay parity is claimed.

### Tunnel Path component and prepared Frame values — 26 October 2026

Based on saved metadataaa5097649, the source-only body implements native
ObjTunnelPath (Tunnel Path/Main.asm:119–388 at N1ecc5f3): prelude saved-bank
clear before player gates; raw routines0/2/4/6; Init/Main acquisition81/C1;
branch-dependent signed SUB/BGE/DIVS, high-byte countdown, WORD snaps/LONG
integration and release bytes24/25=00/02. Raw32 pointers retain their bits,
selected WORD reads resolve the owned24-bit bus views/alignment, and actual
unowned reads/divide-zero stop after reached prefixes. There is no Draw,
animation, Solid, allocation, movement helper, art/queue or controlled Body
implementation. Ten State aliases are additive; inverse removal recovers the
complete old10949-byte source.

The component carries eight finite cross-owner publications as prebuilt own
source64 plus complete State8/unchanged Environment4 and ordered finite scalar
stores. Its synchronous ports check actual source/full96/nonwrites; returned
metadata triggers no work. Private producer/site/order/once-only authority,
Frame ownership, all-owner preflight and typed FM91 ledger acceptance still
belong to the future actual Runtime binding. Public equal values and a no-op
cannot prove that authority. Current live Runtime still does not dispatch07.

The Frame delta is exactly one import and one public prepareObjectTunnelPathPlayerResult/3.
Existing open/closed guards and full State AND Environment CAS precede the
Body's pure finite predicate; only then it constructs the existing private
four-reference ObjectSolidPublication. Old validate/applyPrepared/1 methods
adopt ready references unchanged. No apply/type/field/helper/owner/capture or
old-method refactor is added; both complete old Frame/fixture byte inverses
pass. All705 expected Frame cases are executed below, including114 new
24-field/full-State/full-Environment/nonwrite/guard/adoption vectors.

Root tests-first43026 stops at the expected missing Body/alias compile RED;
source import then85026 stops at testCompile13:35:27Z (1m23s,queue0): a test
lambda captures the changing path loop index. RootR2 adds a stable local and
fixes the independently confirmed COMMIT-return fixture whose old overlay
erased its own attack. The harness now overlays its post-attack actual pool,
retaining the foreign-slot delta; production and expected immutable cuts do
not change. R2 run90413 executes64/0/1/0 at13:37:36Z (32.674s,queue0). Its
one alignment vector completes both valid WORD reads but legitimately reaches
later DIVIDE_X_SPEED/ZERO_DIVISOR. RootR3 retains the pointer/targets and
requires that exact later stop/published prefix. Narrow17028 passes1/0/0/0 at
13:46:47Z (36.208s,queue0); no native speed/pose or production tuning occurs.
Frozen R1 remains unchanged; final RootR3 test is46267B/SHA0c94b1c62cbf55e5b13cfba15f57e24e1cc6738c788805b07595d4348d6af6d0.

Frame tests-first8469 fails only missing prepare3 at13:54:32Z (28.646s,queue0).
After frozen91692B/SHAc0c8622196adfc3e65297d0f23b4a9b88ebeaefa546241efe0374c65a68a880c production import, combined88818 passes1179/0/0/0 at13:57:06Z:
Frame705, allBody64 and existingRuntime410 (1m32s,queue1s). This is a fresh
all64 pass after the three bounded test repairs. Root uses actual media,
Java25/private cwd and exact fresh XML/individual identities/zero skips.
World/real EGL/source-guard run12112 then passes60/0/0/0 at13:59:48Z
(38.277s,queue36s): allWorldLoad53, Ring/full-course/current659 EGL2 and
JUnit/assertion-free guards5; actual media/S2/EGL opt-in/surfaceless checked.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 "$OPENGGF_ROOT/tools/testing/maven_queue.py" --lean -Dmse=off "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" '-Dtest=TestCdPlayerFrameRuntime,TestCdTunnelPath,TestCdOpeningObjectRuntime' test -B
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 "$OPENGGF_ROOT/tools/testing/maven_queue.py" --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" "-Dsonic2.rom.path=$S2_ROM" '-Dtest=TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl#actualEglNormalInputPickupAndFullCourseReplay+actualEglCurrentTamabbohSingletonRendersAndRetiresThe659thClientPattern,TestJunit5MigrationGuard,TestNoAssertionFreeDiagnostics' test -B
```

Original Body static review33565B/SHAa94ed9be98228fc6d0d68f5284d4cfa6d36282e3e0c86d8a1ab3595424fb23af confirms the disclosed fixture P2/no additional production P1/P2. Separate Frame/RootR3 review24121B/SHAce78ca370f632f10b736011741b2a0dc48354afaa698901c45ef0812039fe0f1 confirms the exact three repairs resolve it and finds no new actionable P1/P2. Both are static, not execution results. Focused actual-owner/World coverage is proportionate to this CD-only addition; no broad smoke or release-only full suite was repeated. The actual07 Runtime/publication binding and fresh ordinary3421 continuation remain separate pending work; no whole-act/native timing/pixel/regional certification.

### Live Tunnel Path and permanent Log Shadow frontier — 26 October 2026

Based on saved componentcb423035d, cached ID07 now enters the actual dynamic
pass through constructor4/run2. A private invocation owns source progression,
eight finite cross-owner store cuts, site/order/once-only admission and FM91.
Each cut checks the full96 pool, source64 and complete actual Frame State8 /
Environment4. Pool overlay, Frame prepared references and the immutable sound
ledger are ready before the first owner assignment. Effects do not replay work;
all failures retire the private scope and preserve earlier native publications.
Source17, LogicalBanks20, C30/659, snapshots, phases and clocks are unchanged.
The only public Runtime addition is typed SoundRequest.TunnelPathFm/1.

The author froze tests before production; Root's test-first53710 stops at
expected missing TunnelPathFm compilation, without JUnit. First5794 executes
433/3/0/0 at14:18:09Z (1m37s,queue0). All three failures are new expectations
that discarded an earlier real Body FM_CHARGESTOP. RootR2 copies the whole
pre-Dynamic ledger, appends FM91 and compares the complete ordered result in
exactly those three methods. No production or inherited410 case changes.
Narrow69297 passes3 at14:34:42Z (41.432s,queue0); combined25655 then passes
440 at14:59:05Z (42.568s,queue0): Runtime433, real EGL2 and source guards5.

Controller observer31273 returns all5200 three-jump calls without attachment
(1/0/0/0,14:37:25Z,33.754s execution,27s queue). A fourth ordinary B jump at
5201..5232 reaches5255; observer57554 first fails5256 at unowned ID33/live47
(1/0/1/0,14:44:26Z,35.590s execution,101s queue). These are input-only calls,
not actor/slot/camera/art staging. Both temporary observers were removed and
the permanent source restored exactly before route promotion.

The final fixed recipe is Right throughout, B only1201..1232,1770..1801,
3002..3033 and5201..5232; Start is false. It is equivalent to
1200R;32RB;537R;32RB;1200R;32RB;2167R;32RB;24R through the attempted5256.
Genuine Tunnel Init3421 and next pre-routine2 visit3422 return; live63 retains
its initialized image through5255. No acquisition, rotation or controlled Body
is inferred from those routine visits.

Closed5255 remains in epoch/load generation1: primary XY13A28E00/024A0400,
XV=GV982/YV0/routine2/angleFC/flags0, camera4866/490, section0400 with empty
queue and sound ledger. Failed5256 has the exact newly placed64-byte live47
image ID33/routine0/X1548/Y0088. Earlier Body reaches XY13A66400/024AA300,
XV982/YV-97/GV990 and native/VInt5256. Physical63 is after47 and receives no
failed-call visit. Last-closed camera/placement/saved diagnostics are not
partial-failure observations; no complete96 failed dynamic-prefix claim,
closed capture, retry or resume is made. N ObjectIndex names ObjLogShadow;
unique Present layout row261 matches, distinct from Future row260.

The permanent53-case method is now
controllerFourJumpRouteRunsDiscAndTunnelPathAndStopsAtLogShadowSlot47.
Only its name and post3420 tail change; old proofs/helpers/imports are exact.
Full/course cuts3420→3421,3421→3422,5200→5201 and5254→5255 compare complete
checkpoint-aware state and natural save BEFORE forwarding and after every
row. Only then5256 is attempted once, checking the physical47 prefix and
terminal nonpublication against the actual current pre-call SAT reference.

First route75564 runs1/1/0/0 at15:17:15Z (32.602s,queue0): its script assertion
mistakes the BK2 combined engine input mask for directions alone. RootR2
changes only that assertion to Right8 OR generic INPUT_JUMP on action rows;
separate exact action2/Start/old-row equality and the actual input recipe stay
unchanged. Narrow1208 passes1 at15:24:38Z (34.323s,queue14s). The reusable
representation lesson is in the existing measurement-hazard catalogue.

Fresh combined88405 passes493/0/0/0 at15:27:54Z (42.851s,queue1s): all433
Runtime, all53 WorldLoad, Ring/full-course and current440/659-retirement EGL2,
JUnit-migration3/assertion-source2. Fresh guards68532 pass728/0/0/0 across90
classes at15:36:53Z (5m03s,queue23s). Exact fresh XML classes/individual
identities, Java25.0.5.0.2, private cwd and zero skips were inspected with the
packet helper. The combined run additionally verifies actual CD media,
authenticated S2 donor, surfaceless EGL and explicit EGL opt-in.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 "$OPENGGF_ROOT/tools/testing/maven_queue.py" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" "-Dsonic2.rom.path=$S2_ROM" '-Dtest=TestCdOpeningObjectRuntime,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl#actualEglNormalInputPickupAndFullCourseReplay+actualEglCurrentTamabbohSingletonRendersAndRetiresThe659thClientPattern,TestJunit5MigrationGuard,TestNoAssertionFreeDiagnostics' test -B
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 "$OPENGGF_ROOT/tools/testing/maven_queue.py" --normal -Dmse=off -Pguards test -B
```

The exact tested production is288277B/SHA
b4049db264f1955930e6f1a25e6c87e30e3d0e84fc65fd73dd915b255f0a3688;
Runtime RootR2 test900260B/SHA
08ebdde2133c9578b651bf2baef6d041011f5b0c29614a708cba60f5232b6883;
permanent route RootR2 test312430B/SHA
d3989357f39b342499869a5cb4e28ca31cccea817f40cd5f9751d15e3413323d.
They remained unchanged through combined and guards. Independent Runtime
review30579B/SHA6e5960c2ae978c65c8113da3277e015d3a96e04eab3b1618381f2dd0906ecaae
and route/FM-repair review28881B/SHA508ade4a1e916cbfa645952a30db00400bb9861f348a6ca8364329085b535c8a
find no actionable P1/P2 within their static scope. The latter reviewed route
R1; Root's one representation-only R2 correction is separately attributed above.

Focused actual owners/World/EGL plus fresh structural guards address this
CD-only slice. No broad3757-class smoke catalogue or release-only full suite
was repeated. Next owner is Log Shadow33; its audit finds a small routine0/2
body and existing art producer, with five missing logical current bindings.
No implementation is included here. Full USA then JP/EU remains active;
native Tamabboh scene/controlled Body/full-act/image/timing and regional
certification remain open. No main-workspace or remote integration occurred.

### Log Shadow metadata and first use of the migration tools — 26 October 2026

Based on private277d5ecf4, append Table64 LOG_SHADOW_BASE_TILES at USA89DE/16
and65 LOG_SHADOW_MAPPINGS at89EE/16. The only new public API is synchronized
cached logShadow()/0, using existing decodeStatic/3 with two counted frames
and sixteen tiles. It reads no art/base selector. The two source tables and
actual ID33 Init operands were independently authenticated in USA, Japan and
Europe by the author; owner admission remains USA-only. Old64 table rows,
all selector negatives and the complete Source17 suffix are byte-exact.
There is no new art producer, bank, Region, preload, C30/659 or live dispatch.

The first author prompt ambiguously abbreviated the audit directory. The
author froze both tests, stopped before production and retained a partial
report. Root supplied the exact external audit path/hash; the author verified
it, kept tests/partial report unchanged, then created production once and wrote
a separate final report. No frozen Java repair occurred. Root used the new
read-only draft command plus ordinary packet validation/apply for both imports.

Tests-first68229 stops at expected missing enum values/getter in testCompile;
no JUnit ran (queue0; exact completion time/execution duration not retained).
After production import39084 passes171/0/0/0 at16:32:37Z: unit117 and54
regional retail invocations (2m11s execution,queue0). The new enum auditor
compares actual full CD production/test scope against277d5ecf4, confirms the
64→66 append and finds exactly one stale direct total in mapper test2439.
Root changes only that literal64→66; the adjoining asset total106 stays exact.
Re-audit has no direct stale total but still returns incomplete3 for18 indirect
uses. This is lexical evidence, not complete symbol or consumer proof.

Affected22355 then passes535/0/0/0 at16:35:35Z (55.562s execution,62s queue):
mapper42, Runtime433, allWorldLoad53, actual Ring/current440 EGL2 and source
guards5. Exact fresh class sets/individual identities/counts/Java25.0.5.0.2/
private cwd/media/zero skips were checked with the packet helper; consumer
checks additionally verify actual S2 donor, surfaceless EGL and EGL opt-in.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 "$OPENGGF_ROOT/tools/testing/maven_queue.py" --lean -Dmse=off "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" '-Dtest=TestCdR11aObjectAssets,TestCdR11aObjectRetailAssets' test -B
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 "$OPENGGF_ROOT/tools/testing/maven_queue.py" --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" "-Dsonic2.rom.path=$S2_ROM" '-Dtest=TestCdObjectMappings,TestCdOpeningObjectRuntime,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl#actualEglNormalInputPickupAndFullCourseReplay+actualEglCurrentTamabbohSingletonRendersAndRetiresThe659thClientPattern,TestJunit5MigrationGuard,TestNoAssertionFreeDiagnostics' test -B
```

Tested production79487B/SHA17218466af3e34549249fda1f72a73a10548de8fd82faafcd5915f5fecc9cc45,
unit297149B/SHA6a9e8a13c19482e9ec5e29f4dc8503caf495952e85e82238f963b84e3a8fe68e,
retail439197B/SHAf9ce41646c4ba706537a29e8ea9863a6992ba2a6223efac48a5820106ab1be87,
mapper180271B/SHAc4b1a492a5b1b5b21bea0b982e0891918e6c6977c79fdba2969cbf75827f47d2
remain unchanged through both completed selections. Independent static review
25663B/SHA7be7d90d7a17ff1a94a94123a735e30ad90a1040005732b02524e37a87e82ec3
finds no actionable P1/P2 in the exact packet. All three byte inverses and
essential complete-manifest/subset migrations were checked. That reviewer
executed no Java/tests/media and supplied no runtime success.

Two raw views and a lazy visual getter have bounded understood consumers;
focused actual-owner/World/EGL/source checks are proportionate. No fresh broad
smoke, structural728 repeat or release-only full suite was launched. The
actual route still returns5255 then stops unowned33 at5256. Body, mapper,
five missing current bindings/C31 and eventual live dispatch remain separate;
no current Log Shadow pixel, full-act/native timing or regional gameplay
certification is claimed. Full USA then JP/EU continues privately.

### Log Shadow native component — 26 October 2026

On saved metadata5ed206c94, new CdLogShadow models ObjLogShadow
(Log Shadow/Main.asm:7–70 at N1ecc5f3): routine0 Init/routine2 RTS, then one
unconditional shared Draw and live-slot reload before the shared despawn tail.
The nine Init stores retain dirty bytes; seven precede the selected base WORD,
and MOVE.W/ORI.W precede Draw. Native raw byte/word ALU, saved-bank linear
addressing, warp-only clamping and full-slot deletion retain their source order.
Short constructed base views fail only at the reached read, with earlier
stores visible. Callback exceptions preserve their identity/prefix; reentry
is rejected and its transient guard retires in finally. Returned immutable
effects describe completed work and supply no replay authority.

The core is constructor3 / Assets3 / Globals5 / run2 / Result2, with one
existing-shape DrawObjectCall/2 port. The actual assets binder reuses the
authenticated two views and static visual. There is no new State alias,
player/Frame/clock/Solid/FM/allocation/art/Region/source/capture owner.
The native object has no FixBugs alternative in this closure.

Root imports only the frozen test first;15453 stops during testCompile on the
expected absent CdLogShadow (no JUnit; exact completion time/duration not
retained). After the frozen production import38456 passes35/0/0/0 at17:07:51Z
(1m32s execution,5s queue). The affected JUnit/source guards23786 separately
pass5/0/0/0 at17:11:05Z (34.351s,queue0). Exact fresh XML/individual identities,
counts/Java25.0.5.0.2/private cwd/zero skips were checked. No source repair or
RootR2 was needed. These are constructed public-owner component vectors,
not another fresh535 consumer run or successful controller5256.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 "$OPENGGF_ROOT/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdLogShadow' test -B
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 "$OPENGGF_ROOT/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestJunit5MigrationGuard,TestNoAssertionFreeDiagnostics' test -B
```

Tested production13059B/SHA1ec402144ba7261a7644fe66bf575704d598e0887010bcb2495f242eb8f6cbea
and test36816B/SHAfd8c10e4937a36ab5926e32653032e5477038e304f46a13811247dc3aa7b778a
remain identical to the external0444 inputs through both completed selections.
Source-only review29741B/SHA59cc6927f1bc4a8357c114855324c4e89bc0e1e816fb5147b07ed464930d03a0
inspects the complete production/test/native closure and finds no P1/P2;
it executes no Java. The actual mapper/current five-pattern policy, client and
Runtime binding remain separate. No broad smoke/structural728 repeat is
needed for this unbound component. The current route remains5255 returned /
5256 unknown33; actual Init, native pixels/timing, full USA and JP/EU gameplay
certification remain open.


### Log Shadow mapping and selected current policy — 26 October 2026

On component base f4a968965, one mapper registration joins the authenticated
static two-frame root. One additive C31 overload keeps the complete old C30
parameter prefix and appends the Log Shadow logical base. It calls C30 first,
checks metadata and all twenty explicit prior extents, then strictly reads
captured current residency only when native41F..423 is selected. Existing
Anton418..41E and Plant424..427 remain aliases of the old policy. No source,
decode, physical Region, preload, actor-specific pixel selection or lifecycle
owner is added. Source17 and all earlier overload bodies remain unchanged.
The Runtime/client join is separate; C31 alone does not reserve664 patterns.

Tests-first15918 stops at the expected absent C31 overload during testCompile
(no JUnit; exact execution/end time not retained). Before production import,
RootR2 corrects the mapper registration census34→35 and compares known-mask
bytes rather than distinct CdAssetBytes wrapper identities. First positive41198
finishes17:36:42Z with67/1/0/0 (2m00s execution,63s queue): the sole failure is
an expected SAT link byte. Native DrawSprite399–402 increments/stores the link;
DrawObjects357–365 clears the next entry, except at the full80-entry limit.
RootR3 changes exactly eight expected link bytes00→01 across two frames/four
flips; all640-byte and next-terminator checks remain. The narrow79423 passes1
at17:42:05Z (32.474s,queue0), followed by fresh full picture coverage below.
RootR4 appends30/31 to an older Spikes complete-overload inventory ending29;
no other inherited assertion changes. No production repair was made.

Fresh picture selection25851 passes152/0/0/0 at17:52:58Z (1m40s execution,
20s queue): mapper45, Anton10, Boulder7, Checkpoint7, CollapsePlatform7,
DiagonalSpring7, LogShadow9, ObjectPresentation39, Spikes8 and Tamabboh13.
Separate affected-consumer selection77381 passes493/0/0/0 at17:56:57Z
(43.707s execution,41s queue): Runtime433, World53, selected actual EGL2 and
JUnit/source guards5. Exact fresh class sets, individual identities/counts,
Java25.0.5.0.2, private cwd, media, zero skips and the second run's authenticated
S2 path/surfaceless EGL/explicit opening opt-in were checked. These are two
separate completed selections, not an invented combined run or full-suite pass.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 "$OPENGGF_ROOT/tools/testing/maven_queue.py" --lean -Dmse=off "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" '-Dtest=TestCdObjectMappings,TestCdAntonPicture,TestCdBoulderPicture,TestCdCheckpointPicture,TestCdCollapsePlatformPicture,TestCdDiagonalSpringPicture,TestCdLogShadowPicture,TestCdObjectPresentation,TestCdSpikesPicture,TestCdTamabbohPicture' test -B
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 "$OPENGGF_ROOT/tools/testing/maven_queue.py" --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" "-Dsonic2.rom.path=$S2_ROM" '-Dtest=TestCdOpeningObjectRuntime,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl#actualEglNormalInputPickupAndFullCourseReplay+actualEglCurrentTamabbohSingletonRendersAndRetiresThe659thClientPattern,TestJunit5MigrationGuard,TestNoAssertionFreeDiagnostics' test -B
```

Tested Presentation115275B/SHA3067064c419940a26df86a624b280b38c782c046ce74f2aeecb96ed4c7eab3ba,
Mappings19288B/SHA258416467f0fe26aee27c27c664f9dc02d06aa1eec34c467a0e01bc5f3a886d4,
MappingTest186935B/SHAfc5d96040470499f207df4468621fa96c99ed240d596473ec0a1db19c71fddd2,
ShadowPictureTest25667B/SHA61efe923b3b0acbea1ba109902153d076646300e3e9f12bc218eb99023aa7339
and SpikesPictureTest21788B/SHA59d0b88582423085e5c066ccd4c923d43ae63ee939d37f6c1e54c51239ee6703
remain unchanged through both completed selections. Source-only review21353B/
SHAa70e6e846f50b307a912b05254ff8fb29dee89c74077322fd4253b8d0b6e3f9c
finds the same eight SAT literal errors and no additional production P1/P2;
it reviewed R1/RootR2 and executed no Java. The exact source-backed R3 repair
resolves that finding. All frozen worker originals and bounded inverses remain.

The impact is one additive policy and registration with understood consumers;
focused actual-owner/World/EGL and complete CPU picture-family checks are
proportionate. No unchanged structural728 repeat, broad smoke or release-only
full suite was launched. Native Log Shadow scene pixels, real664 installation
and retirement, live Init/Main and natural5256 return remain unproved. The
actual route remains5255 returned / first5256 unowned33. Full USA then JP/EU
continues privately.


### Live Log Shadow, current five patterns and Sonic Hole frontier — 26 October 2026

Over private saved base `be61f9b089fe93e4fc4b716c7e3212192f8dcea5`, the live
Runtime caches the existing CdLogShadow body and visits physical ID33 once in
the unchanged increasing object walk. Its Globals use actual timeZone,
goodFuture, low act BYTE, raw timeWarpDirection and pre-scroll camera X.
There is no new player/Frame/Solid/FM/capture/phase owner. LogicalBanks appends
component21 and checked compatibility20; all older constructor bodies remain
exact. Both pixel-policy sites call C31. The client changes exactly its
reservation comment and count,659→664; load/restart/camera methods are exact.
Source17, physical regions, cold writes, readers, caches and restart carry do
not change. Twenty indispensable inherited test migrations retain the full
old contract;23 additions give456 Runtime cases.

Tests-first Root26987 reached the expected absent21/getter compile boundary.
After importing the two production mirrors,39361 passed456/0/0/0 at18:13:10Z
(1m38s execution, queue0). No production/test repair was required. Independent
bounded Runtime/client, resource and route reviews found no actionable
P1/P2; authors' source-only counts are not execution results.

Temporary observer88056 returned5322 ordinary calls and failed once at5323:
`Unowned CD object ID $31 at live physical slot 46` (18:38:16Z,
34.166s execution,318s queue,1 test/0 failures/1 expected diagnostic error/0
skips). It was restored out of the checkout before permanent promotion.
The four-jump recipe stays Right throughout, with B only1201..1232,
1770..1801,3002..3033 and5201..5232; Start is false. Actual Shadow47 enters
Init5256 and routine2 Main5257 with complete64 image preservation; another
Shadow44 is routine2 at5286. Y0088/sprite04 is culled above camera490, so
these visits establish no natural Shadow scene pixels or player acquisition.

Permanent `controllerFourJumpRouteRunsLogShadowReplaysClosedCutsAndStopsAtObject31Slot46`
retains all53 cases, imports/helpers, previous assertions and prior replay
edges. Only its name and post5255 tail change. New full/course edges
5255→5256,5256→5257,5321→5322 compare complete checkpoint-aware state before
forwarding and every returned intermediate cut. The first5323 attempt occurs
once after healthy replay. Supplied full64 slot46 placement, earlier
Body/VInt values, terminal closure and CPU/scroll/tail nonpublication are
checked without a failed composite capture, retry or resume. Eight changed
dynamic images are an observed aggregate, not a native full96 partial oracle.
The original suppressed terminal hasPreparationBoundary read is preserved.
Focused37307 passes1/0/0/0 at19:35:53Z,34.498s execution after628s queue.

The short independent EGL resource test runs ALL seven actual PLC10 programs
through the existing SectionArt component:69 service calls and199 tiles,
including genuine current Shadow16. Five selected new patterns upload and
render against exact CPU pixels in four flip settings. Native pixels are
uniform33: flips/equal native IDs cannot be visually distinguished, so the
separate ID/version/entry/texture checks carry those resource obligations.
Only missing old659 entries use transparent TEST installation witnesses.
The live World stays unchanged; all664 installed client entries retire and
actual other-producer entry identities remain. Old659 client12593 fails
at cleanup offset659 after all preceding service/GPU/nonmutation assertions
(18:54:11Z,1m24s execution,392s queue,1/1/0/0). Restored664 client90812 passes
1/0/0/0 at19:01:01Z,1m35s execution,queue0. No fixture fitting or producer
repair was needed. No PNG/native World geometry/all664 scene-rendered claim.

Fresh combined90516 passes517/0/0/0 at19:40:44Z,52.839s execution after76s
queue. Exact five fresh XML classes, all unique case identities, Java
25.0.5.0.2/private cwd, actual media/authenticated S2, surfaceless EGL and
explicit soniccd.opening.egl=true were checked. Its selection is Runtime456,
WorldLoad53, real Ring/current440/currentShadow EGL3, JUnit migration3 and
assertion-source2. This is proportionate affected coverage of the two small
production joins and their direct consumers, following the unchanged recent
728 structural guards; it is not full smoke/full suite or whole-game parity.
No unchanged broad run was repeated merely as a save ceremony.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 "$OPENGGF_ROOT/tools/testing/maven_queue.py" --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" "-Dsonic2.rom.path=$S2_ROM" '-Dtest=TestCdOpeningObjectRuntime,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl#actualEglNormalInputPickupAndFullCourseReplay+actualEglCurrentTamabbohSingletonRendersAndRetiresThe659thClientPattern+actualEglCurrentLogShadowFivePatternsRenderAndRetireThe664EntryClientRange,TestJunit5MigrationGuard,TestNoAssertionFreeDiagnostics' test -B
```

Source pins: Runtime290019/SHA59d7cfd2498cd2005b08f1c5fcd9c7fe1027e0d4d0bd6c93603f4d463016a229;
client56286/SHA6246915598be82f075ef747d4e56bd0e3079dd3eccd302221ecd5a25499336a4;
RuntimeTest937503/SHAb59a634081069e60a2fa304e4611d30784080b810f15f31d9797ab4badacff0d;
WorldLoad326238/SHAa342f26c5b05c71aca03e32d8e43abbaa947fb9a5b698d5a9b2d752efffb53a5;
EGL558642/SHA16209f6237e7854529c61212868a7820ce1f29584ae7cf981a70032eeee71fb0.
Frozen author mirrors remain0444, imported sources0644/fresh-mtime. The new
packet preservation tool checks declared byte migrations in both directions;
these checks establish bytes, not runtime authority or native parity.

Native ObjLogShadow (Log Shadow/Main.asm:7–70), DrawObject (Object
Functions.asm:137–180) and the shared spawner/despawn routines explain these
cuts at N1ecc5f3. Independently authenticated ID31 is ObjSonicHole, row297,
saved86, raw16f002e8b100e000 (Tunnel Path/Main.asm:11–103; Object Index Act1
Present:56). Failed slot46 has routine0 and no ID31 Init has executed.
Existing Source17 and Mosqui70 current aliases cover its fourteen tiles;
metadata/body/read-only primary/FM/mapping/Runtime are the next bounded join.
No new art source/span is indicated. Controlled Body remains deferred.
Full USA then JP/EU, native timing/image parity and whole-act closure remain
open. This is private local progress, with no main-workspace/remote change.


### Sonic Hole authenticated component and mapping — 26 October 2026

Over saved private basef9068367f3c98892d824476009683d37c1702c64, append
Table66 SONIC_HOLE_MAPPINGS at USAfile8AD4/18 and one synchronized lazy
sonicHole()/0 through the existing static1-frame/14-tile decoder. All old66
rows and the complete Source17/cache/reader/selector suffix remain exact.
Independent original regional operands and full18 bytes are authenticated;
JP/EU view bytes do not authorize USA gameplay ownership. Root tests-only
24049 reaches absent enum/getter compile RED; after production,35566 passes
123 unit+57 regional=180/0/0/0 at19:59:51Z (2m06 execution,483s queue).
Fresh exact two-class identities, Java25/private cwd/media/skips and pins
were checked. No new art/preload/physical region or tile selector is added.

CdSonicHole uses constructor5 and existing placement, a read-only primary
port, FM_A3 metadata port and Draw. Native ObjSonicHole (Tunnel Path/Main.asm:
11–103 at N1ecc5f3) dispatches raw0/2/4. Init retains width-specific native
stores and raw-timeZone tile03A0/03BB. SetDisplay always uses raw linear
2+3*savedID+timeZone, including saved0; old-clear BSET6→BCLR6 falls through
Main, old-set goes to routine4/Draw. Main samples actual CONTROL2C first,
then X WORD and Y WORD only behind the native rectangle gates. Successful
overlap commits saved6, requests FM_A3, advances routine4 and Draws. Waiting
Main has no Draw. Draw remains mutating; the shared tail reloads its actual
source before aligned distance/warp/loaded-bit/delete behavior.

Every read/FM callback receives fresh actual full96/source64. Private
finally checks all placement Status fields,766 saved bytes and96 images;
the original callback Throwable retains identity with distinct verifier
faults suppressed. There is no rollback, Frame/player writer, capture owner,
angle/Solid/terrain/animation behavior or command replay. Equal-value stores
and mutate/undo remain unobservable value-check limits. Root Runtime must
supply actual private invocation/field sequence/full Frame-pair/FM authority.

One ordinary mapper registration resolves the one complete counted root and
its three pieces/fourteen tiles on existing Mosqui70/current aliases. Root's
enum auditor finds both outside66 totals at mapping tests2439/2530 before
integration. It visibly returns incomplete for indirect uses; zero stale
counts is never a complete consumer certificate. The mapper packet performs
those two total migrations, root census35→36 and static census7→8; seven
new cases retain all prior source bytes outside those four tokens.

Both component tests import first. Build-only59567 uses test-compile and
reaches absent CdSonicHole without requesting a test-queue slot or running
JUnit. That compile RED does not claim a separate old-mapper JUnit failure.
After both production imports, combined91006 passes591/0/0/0 at20:06:36Z
(2m07 execution, queue0): Body22, mapper52, old Runtime456, allWorldLoad53,
actual Ring/current440/currentShadow EGL3 and source guards5. The22 Body
methods stream65536 SetDisplay,65536 tail and32768 warp vectors plus callback
and raw-byte cases; those loops are not additional JUnit case counts. Exact
seven fresh classes/identities/Java25/media/S2/EGL opt-in/skips and all seven
source pins were checked. Earlier180 is a separate run, not a fresh771 total.
No source/test repair was needed. Independent metadata and Body/mapper
static reviews found no actionable P1/P2; no broader unchanged run repeated.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 "$OPENGGF_ROOT/tools/testing/maven_queue.py" --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" "-Dsonic2.rom.path=$S2_ROM" '-Dtest=TestCdSonicHole,TestCdObjectMappings,TestCdOpeningObjectRuntime,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl#actualEglNormalInputPickupAndFullCourseReplay+actualEglCurrentTamabbohSingletonRendersAndRetiresThe659thClientPattern+actualEglCurrentLogShadowFivePatternsRenderAndRetireThe664EntryClientRange,TestJunit5MigrationGuard,TestNoAssertionFreeDiagnostics' test -B
```

The component is still unbound from the Runtime at this checkpoint. Natural
route5322/first5323unowned31 remains the measurement; no returned5323,
Sonic Hole activation/current scene image, FM playback or native parity is
claimed. C31,21 logical banks,664 client entries, Source17 and the Frame are
unchanged. User requests a break after the live Sonic Hole join, a handover,
and GitHub feature/ai-nothing-to-see-here based on next. This supersedes the
prior local-only publication restriction for that requested feature branch;
main remains untouched and no release/PR/other-branch push is requested.


## 26 October 2026 — Sonic Hole live Runtime, before permanent route promotion

The source-only body, assets and mapper are saved in `2b31c6c95` with the
separate 180-case metadata and 591-case component/old-consumer positives. The
live ID31 join now caches the real body and validates actual dispatch scope,
control → conditional X/Y → FM order, complete96/source64 and the actual Frame
pair before ordered sound publication. It adds only typed `SonicHoleFm`, no new
Frame writer, art source, region, bank or logical span. Returned effects remain
completed metadata, never commands.

Tests-only build21956 stops on the expected missing `SonicHoleFm` declaration,
with no JUnit run. First positive56391 runs494 with0 failures,1 error,0 skips at
20:50:11Z: one factory test omitted the existing host resident-art sink. RootR2
adds a recording sink to that fixture only. Narrow17880 passes1; fresh full52519
passes494/0/0/0 at21:14:30Z (39.516s execution,46s queue): Runtime486, selected
Ring/current440/currentShadow EGL3, source guards5. Complete fresh class/case
identities, Java25, media, S2 REV01, surfaceless EGL and explicit opt-in are
checked; production and RootR2 test hashes remain unchanged. The independent R1
Runtime review reports no concrete P1/P2; it is source evidence, not execution.

Actual command, from the private source worktree with existing absolute inputs:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 "$OPENGGF_ROOT/tools/testing/maven_queue.py" --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" "-Dsonic2.rom.path=$S2_ROM" '-Dtest=TestCdOpeningObjectRuntime,TestSonicCdOpeningWorldEgl#actualEglNormalInputPickupAndFullCourseReplay+actualEglCurrentTamabbohSingletonRendersAndRetiresThe659thClientPattern+actualEglCurrentLogShadowFivePatternsRenderAndRetireThe664EntryClientRange,TestJunit5MigrationGuard,TestNoAssertionFreeDiagnostics' test -B
```

Temporary observer36594 returns5414 ordinary four-jump calls, including Hole
Init5323/Main5324 at physical46, then rethrows `TIME_WARP_ACTIVE` on5415.
TimePost requests `SCMD_PASTSFX` and sets raw direction255 on5414; timer and charge
are0. Runtime/Frame close, Body and all96 images stay at the last completed
values, and VInt advances5414→5415. The failed composite is never captured or
resumed. Observer source is removed immediately and the permanent53-case file
restored byte-exact. Promotion/replay is pending at this checkpoint. Natural
control remains0, so Hole activation, A3 and new Hole World pixels are not proved.

User delivery changed: finish Sonic Hole, publish only
`feature/ai-nothing-to-see-here` based on next, write a handover, then pause.
Time warp and the next owner must not be implemented during this delivery.
Full USA, then Japan/Europe, remains unfinished.


## 26 October 2026 — permanent Sonic Hole route to ordinary time-warp boundary

Source checkpoint is `cffe153a0a49a1363e69b38e7663720e339acab0` plus the
World-load fixture SHA256
`e0f4217af24b7526d50a5c53e9344b028e04282520ea85c113528cf430bb4fbb`.
The temporary observer is absent. Exactly the permanent case's name and
post5322 tail change; explicit two-edit whole-file forward/inverse verification
recovers all326238 baseline bytes. All53 cases, imports, helpers, earlier
controller recipe and replay edges remain intact.

Fresh78472 passes53/0/0/0 at21:27:25Z (36.526s execution,16s queue), including
`controllerFourJumpRouteRunsSonicHoleReplaysClosedCutsAndStopsAtTimeWarpActive`.
Complete fresh class/case identities, Java25/private cwd, actual media, S2 REV01,
surfaceless EGL properties and unchanged source pin are checked. Runtime494 and
World53 are separate green invocations, not an invented combined547 run. No
route fixture or production repair was needed. Independent static route review
is pending at this save checkpoint; it conveys no native parity result.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 python3 "$OPENGGF_ROOT/tools/testing/maven_queue.py" --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" "-Dsonic2.rom.path=$S2_ROM" '-Dtest=TestSonicCdOpeningWorldLoad' test -B
```

Natural Hole46 Init5323 and Main5324 return with the complete native header and
unwritten bytes preserved. Player control stays0: no natural Hole activation,
FM_A3 or Hole World-pixel claim. Full/course5322→5323,5323→5324,5413→5414 compare
complete checkpoint-aware state before and after every forward row, alongside
all older edges. The same input-only Right/four-jump recipe returns5414. The real
TimePost requests `SCMD_PASTSFX` and sets raw direction255 on5414; timer/charge0
reach the existing `TIME_WARP_ACTIVE` stop on5415. The original exception and
terminal closure remain, no failed-composite capture or retry. The observed
failed-call Body/environment/full96/saved766 equality and5414→5415 VInt are
bounded observations, not a general native partial-prefix oracle.

User's stopping point is now reached: no time-warp or controlled-Body
implementation. The next-based feature worktree owns shared merge verification,
final CD-only checks, handover, authorized feature-only push and cleanup.
The full-USA-then-Japan/Europe objective remains unfinished.
