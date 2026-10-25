# Stageplay ground placement

Task base: engine `0ffa7f3c7`; desktop editor `5bdf435`. The architect synthesis
selects one batch query over either decoded entry terrain or an explicitly
prepared scene frame. Discovery runs no player physics ticks. Objects use the
route planner's shared three-column visual support rule; player proposals use
the extracted native vertical sensor kernel and paired selection rule.

A successful object anchor is a fixed point of support and has clear body space.
A player proposal has a selected geometric foot at distance zero and standing
LRB ceiling clearance; playback remains the landing oracle. Object alternate
shape selection retains primary top solidity, while player solidity belongs to
the stock donor profile. Unsupported gravity, plane and orientation states are
typed refusals. No pinned API signatures or document formats grow.

Search evaluates every integer seed in the padded band, deduplicates exact
anchors, and returns bounded top-first choices. Time, native scans, preparation
ticks and cancellation belong to one batch budget; an interrupted query is
INCOMPLETE, and cannot claim a nearest placement or NONE_IN_BAND.

The CLI is query-only and accepts many columns in one JVM. The editor uses its
existing preview worker. Applying a proposal is one undoable edit with document,
selection and playhead freshness checks; a physical shot-entry move also moves
held visibility keys. Interior player teleports, Free object relocation and
ambiguous bindings are rejected.

## Decisions and evidence

The architect rejected per-candidate native drop simulation, a separate editor
job/process, CLI document writers and persisted declarative grounding intent.
The motivating route-prose sweep conflated route qualification with support,
accepted a one-pixel tolerance and launched two JVMs per candidate. Ground
queries instead expose native surface rows, authored anchors and typed evidence.

Implementation and focused validation evidence is recorded below. The lead owns
change-based smoke and integration; these task branches remain local.

### Native scan baseline

Before moving any player code, `TestPlayerTerrainScanEquivalence` captured
332,800 outputs per game at `0ffa7f3c7` plus the unrelated visual-support
extraction. The deliberate `BASELINE` assertions emitted these SHA-256 values:

| Set | Digest | Empty defaults |
| --- | --- | ---: |
| S1 GHZ1 | `ca6fbf21a004fb8bec30ca7add70b3cf3fee87928ba86b2cdb7257d579a66f4d` | 329258 |
| S2 EHZ1 | `91ad08392a292345d0c38ba5831271074a5c2ab6e61cc91a9c9ce8ae473ba82c` | 325752 |
| S3K AIZ1 | `5ade97e5f1d420067331c1cf7abfffcc34d0100c8a9d5b527a8e0dced0ca4e97` | 316768 |

Inputs cover 80 irregular columns, 130 rows (including negative ceiling edges),
solidity bits 12–15, both vertical directions, both layers, both loop planes and
mirrored empty ceiling defaults. The digest includes signed distance/angle,
tile identity, direction and all six native angle-write trace fields. It pins
native empty/angle-only results as well as actual surfaces. Golden digest
assertions now serve as the before/after gate without retaining a second floor
implementation in test code.

### Integration details and deviations

- **Missed context requirement:** retain optional cast identity in the pure query
  and preview transport. Resolving it permanently to only donor/character/path
  loses the selected live actor's loop, rolling, transformed or switched-path
  state. Profiles are still captured once per distinct donor tuple; explicit
  stock descriptors do not inherit another cast member's state.
- **Existing ownership detail:** authored terrain was shared with the live world.
  Capture its entry snapshot when decoded/registered so ENTRY_LAYOUT cannot
  silently include subsequent mutations. ROM entry terrain remains library-owned.
- **Internal implementation:** a package-private controller generation accessor,
  exposed through pure StageplayGround, detects edit/undo and selection/playhead
  round trips without growing the pinned controller's public API. A composition
  overload on the unpinned session factory keeps camera construction in its owner.
- **Transport observation limit:** cancelling an in-flight preview kills only the
  owned existing worker. Its counters are unobserved (`-1`) if it cannot reply;
  the report is INCOMPLETE/CANCELLED. No worker kind or job lifecycle was added.
- **Result safety:** nearest declines truncated lists as well as incomplete scans;
  refusals are bounded to 64 with the same truncation signal. This prevents an
  incomplete list from implying a globally nearest placement.

The extraction gate passed 112 focused physics tests with zero skips, including
all three golden hashes. The core query gate passed 12 tests with zero skips,
covering pixel-phase enumeration, pair ties, native solidity conventions,
empty/default rejection, bounded lists, strict JSON, profile caching and inner
clearance cancellation. Later source/transport and ROM validation is recorded below.

The combined authoring run exposed three invalid new test fixtures, rather than
application failures: object commands require a visible Puppet key, and the
existing document rejects duplicate placed identities even when their visibility
intervals do not overlap. The implementation now relies on that stronger core
binding invariant rather than adding an unreachable second overlap validator;
fixtures test the actual document boundary and valid Puppet metadata preservation.
A null-sensor call-order regression check also preserves CollisionSystem's old
single-sensor path without a new ground-mode read.

The existing preview worker's ROM set includes explicit requested player donors
as well as document references. A newly requested donor can restart that same
worker kind; ordinary cast queries reuse the already-prepared worker and PID.

The first ROM run passed 75/78 tests (zero skips), including all object batches,
live mutation, persistent-preview PID/state checks, four required S3K loading
families and the golden scan oracle. Three native landing tests stopped before
playback because they filtered only raw angle zero. ROM floor tiles here use odd
angle flags (`1`/`255`), which native angle response cardinal-snaps; those flags
are not slope measurements. The test now selects a native cardinal candidate.
The placement orientation gate reuses existing `CollisionSystem.anglePosQuadrant`
for even bytes (including the asymmetric exact `0x20` boundary), and respects
native odd-angle flags. No sensor outputs or player angle writes were changed.

The next oracle exposed the native preserved Y fraction (`+0.21875`) while the
ROM centre word remained at the proposal. Existing implementation pitfalls already
specify comparing foot pixels for landing support. The kill test now reads actual
`getCentreX/Y()` words, requires `!air` within six ticks, and additionally requires
unchanged full 16.16 presentation coordinates after landing. It does not zero,
subtract or fit a native fraction. The rejected exact-presentation-coordinate
assertion would have required changing shipped-ROM behavior.

Application refuses the active Free interval and any later return to Free in the
shot. A completed native Free initialization prefix may precede the directed key;
its original Free anchor remains untouched, matching existing object-route graph
preparation. The focused regression covers that distinction.

## Delivered shape against the synthesis

| Section | Implementation |
| --- | --- |
| 1 — objects | StageplayVisualSupport owns support/clear/rest; route statuses and messages remain unchanged. Final support is a fixed point and supplies the reported row/angle. |
| 2 — players | Package-private NativePlayerTerrainScan and NativeGroundSelection are used by the original player owners and the read-only placement facade. Stock profiles are sprites-owned; discovery never ticks a puppet. |
| 3 — bases | Resource overloads expose decoded EntryLayout and the prepared SceneFrame's own LevelManager, wrapping and mutation state, with derived caveats. |
| 4 — search | Every integer seed in the 16-pixel padded band is visited; exact anchors are deduplicated, sorted and bounded. nearest declines incomplete/truncated results. |
| 5 — work | One GroundWork covers preparation, native floor/body/ceiling scans, cancellation and time. Interrupted and unvisited queries are INCOMPLETE. |
| 6 — conventions | ObjectPath selects an object shape; player top/LRB bits come from the stock donor. No player-capable object clear helper was added. |
| 7 — application | One undoable controller edit; dependent held player keys move together. Interior physical moves, active/future Free intervals and stale generations are refused. Existing document identity validation rejects duplicate bindings globally. |
| 8 — transport | CLI ground is query-only and rewrites --queries paths. The existing persistent preview worker accepts ground batches; both editors share the workspace action. Desktop Direct lists it explicitly. |
| 9 — boundaries | Exact Stageplay consumer/owner pairs and kernel-user guards pass. No pinned public signatures grew. Both stale floor-snap help strings now describe the 14-pixel local attachment. |

## Validation commands and outcomes

Commands below preserve the executed selections and arguments, with local path
prefixes written as portable variables: `ENGINE_MAIN` is the primary engine
checkout, `ENGINE_WORKTREE` and `EDITOR_WORKTREE` are the two authorized task
worktrees, and `TASK_ARTIFACTS` is the explicit external task directory containing
`floors40.json` and its output. At execution these expanded to absolute paths;
ROM identity was verified before any ROM-backed run. All engine commands run in
`ENGINE_WORKTREE`, except the forty-column launcher demonstration, which runs in
`TASK_ARTIFACTS`. Desktop commands run in `EDITOR_WORKTREE`.

All commands ran in the task engine worktree at base `0ffa7f3c7` plus the task
changes, using Java 25. The final commit is reported at handoff; executable
sources are unchanged between the final successful checks and that commit.
No broad smoke or release-only full suite was run; the lead owns combined smoke.
ROM paths below matched the documented CRC32 and SHA-1 identities exactly.

```bash
python3 ${ENGINE_MAIN}/tools/testing/maven_queue.py -Dmse=off '-Dtest=TestStageplayVisualSupport,TestStageplayObjectRoutePlanner' test -B
```

13 passed, no failures/errors/skips.

```bash
python3 ${ENGINE_MAIN}/tools/testing/maven_queue.py -Dmse=off '-Dtest=TestPlayerTerrainScanEquivalence' -Dsonic1.rom.path=${ENGINE_MAIN}/s1.gen -Dsonic2.rom.path=${ENGINE_MAIN}/s2.gen -Ds3k.rom.path=${ENGINE_MAIN}/s3k.gen test -B
python3 ${ENGINE_MAIN}/tools/testing/maven_queue.py -Dmse=off '-Dtest=TestPlayerTerrainScanEquivalence,TestGroundSensor,TestGroundSensorServiceResolution,CollisionSystemTest,TestCollisionSystemAirLanding,TestTerrainCollisionManager' -Dsonic1.rom.path=${ENGINE_MAIN}/s1.gen -Dsonic2.rom.path=${ENGINE_MAIN}/s2.gen -Ds3k.rom.path=${ENGINE_MAIN}/s3k.gen test -B
```

First command captured the pre-extraction hashes through three intentional
placeholder assertion failures, with no errors/skips. After extraction the second
command passed 112 tests, with all 998,400 sampled outputs identical and no skips.

```bash
python3 ${ENGINE_MAIN}/tools/testing/maven_queue.py -Dmse=off '-Dtest=TestStageplayGroundSurvey,TestNativePlayerTerrainPlacement,TestNativeRouteTerrainProbe' test -B
```

12 passed, no failures/errors/skips.

```bash
python3 ${ENGINE_MAIN}/tools/testing/maven_queue.py -Dmse=off '-Dtest=TestStageplayTool,TestStageplayGroundWorkspace,TestStageplayWorkspace,TestStageplayController,TestStageplayRouteWorkspace,TestStageplayGroundSurvey' test -B
python3 ${ENGINE_MAIN}/tools/testing/maven_queue.py -Dmse=off '-Dtest=TestStageplayGroundWorkspace,TestNativePlayerTerrainPlacement,CollisionSystemTest,TestCollisionSystemAirLanding' test -B
```

The combined command first exposed compile errors (missing imports and invalid
Optional access), then ran 65 tests with three invalid new fixtures and no skips.
After the fixture/core-invariant correction, the affected second command passed
94 tests with no failures/errors/skips. The final workspace run below includes the
additional completed-Free-prefix regression.

The following environment and ROM arguments were used for each ROM command:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 ${ENGINE_MAIN}/tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestStageplayGroundRom,TestStageplayObjectRouteRom,TestStageplayLivePreviewRom,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestPlayerTerrainScanEquivalence,TestStageplayGroundSurvey' -Dsonic1.rom.path=${ENGINE_MAIN}/s1.gen -Dsonic2.rom.path=${ENGINE_MAIN}/s2.gen -Ds3k.rom.path=${ENGINE_MAIN}/s3k.gen test -B
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 ${ENGINE_MAIN}/tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestStageplayGroundRom,TestNativePlayerTerrainPlacement,CollisionSystemTest' -Dsonic1.rom.path=${ENGINE_MAIN}/s1.gen -Dsonic2.rom.path=${ENGINE_MAIN}/s2.gen -Ds3k.rom.path=${ENGINE_MAIN}/s3k.gen test -B
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 ${ENGINE_MAIN}/tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestStageplayGroundRom,TestStageplayGroundWorkspace' -Dsonic1.rom.path=${ENGINE_MAIN}/s1.gen -Dsonic2.rom.path=${ENGINE_MAIN}/s2.gen -Ds3k.rom.path=${ENGINE_MAIN}/s3k.gen test -B
```

The first ran 78 tests, with three raw-angle-zero oracle failures; all other
75 passed, including both classes named TestSonic3kLevelLoading, the other required
S3K families, object-route ROM checks, exact scan hashes, live mutation and preview
worker PID/state reuse (including composite ROM transport). No skips.
The second ran 67 tests, with three exact-presentation-coordinate oracle failures;
its five native placement and 58 collision tests passed, including angle flags,
asymmetric quadrants and angle-only default rejection. No skips.
The final command passed all 20 tests, with no failures/errors/skips. Six stock
donors (S1 Sonic; S2 Sonic/Tails; S3K Sonic/Tails/Knuckles) land within six ticks at
the proposed ROM centre words and retain exact full-position stability afterward.
Object placements pass the shared route support check in all three games; each
40-column entry batch leaves the current session and child-process set unchanged.

```bash
env LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 0ffa7f3c7 --preflight
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 ${ENGINE_MAIN}/tools/testing/maven_queue.py -Dmse=off -Pguards -Dopenggf.headless.gl=surfaceless-egl test -B
python3 ${ENGINE_MAIN}/tools/testing/maven_queue.py -Dmse=off '-Dtest=TestModApiSignatureSurface' test -B
```

Preflight passed (after selecting installed Lua 5.4 instead of the default Lua);
no engine tests ran through the category runner. Guards: 753 passed, no skips,
including 34 ArchUnit checks. Signature surface: 11 passed, no skips.
Python launcher syntax, changed-file whitespace and mirrored skill equality passed.

## Forty-column CLI demonstration

The launcher ran from `${TASK_ARTIFACTS}`,
which verifies that the relative query path is rewritten before Java starts:

```bash
python3 ${ENGINE_WORKTREE}/tools/stageplay/stageplay.py ground --set s1/0/0 --queries floors40.json --s1-rom ${ENGINE_MAIN}/s1.gen > floors40-result.json
```

Input: forty object queries, x = 80 + 16*i, inclusive surface rows 800–1200,
half-width 8, height 28, foot offset 14, primary object path, maxMillis 20000.
One JVM, no scene, no compilation, no native object donor and no preparation ticks.
The complete raw JSON and input remain in that explicit external task directory.
An initial 400–900 band correctly returned 30 NONE_IN_BAND and ten FOUND; extending
the authoring band to include the observed lower floors produced the following
complete batch. This changes the query, never engine collision behavior.

The CLI reported ENTRY_LAYOUT with TERRAIN_ONLY, NO_EVENTS_OR_MUTATIONS and
DEFAULT_WRAPPING caveats: 40 FOUND answers, 50 placements, no truncation,
56613 native scan charges, zero preparation ticks, 151 ms and stopped=NONE.
That is one observed invocation, not a timing benchmark or performance guarantee.
Compact projection of every answer (authored Y/native surface Y):

```text
column-00 x=80: 949/963
column-01 x=96: 947/961
column-02 x=112: 947/961
column-03 x=128: 945/959
column-04 x=144: 945/959
column-05 x=160: 945/959
column-06 x=176: 945/959
column-07 x=192: 945/959
column-08 x=208: 945/959
column-09 x=224: 945/959
column-10 x=240: 945/959
column-11 x=256: 945/959
column-12 x=272: 945/959
column-13 x=288: 948/962
column-14 x=304: 949/963
column-15 x=320: 951/965
column-16 x=336: 949/963
column-17 x=352: 947/961
column-18 x=368: 947/961
column-19 x=384: 945/959
column-20 x=400: 945/959
column-21 x=416: 945/959
column-22 x=432: 945/959
column-23 x=448: 945/959
column-24 x=464: 945/959
column-25 x=480: 945/959
column-26 x=496: 945/959
column-27 x=512: 945/959
column-28 x=528: 945/959
column-29 x=544: 945/959
column-30 x=560: 849/863, 945/959
column-31 x=576: 849/863, 948/962
column-32 x=592: 849/863, 950/964
column-33 x=608: 849/863, 951/965
column-34 x=624: 849/863, 952/966
column-35 x=640: 849/863, 953/967
column-36 x=656: 849/863, 952/966
column-37 x=672: 849/863, 951/965
column-38 x=688: 849/863, 949/963
column-39 x=704: 849/863, 947/961
```

### Native roster guard

A final availability audit reproduced an actual missing profile refusal: the
packaged CLI answered FOUND for an S1/Tails request at x=128, band 800–1200.
The global stock character registry contains all three body factories; it is not
native donor availability. The sprites-owned factory now intersects that registry
with the donor's semantic DonorCapabilities roster and refuses non-stock keys,
without game-name branches or art loading. ROM checks cover all six supported
donors plus S1 Tails/Knuckles, S2 Knuckles, and S1 alternate player path refusals.
This closes a missed native-profile requirement; it does not change player scans,
radii, angle writes or simulation. The earlier sampled output oracle remains valid.

The native roster matrix passed all four ROM tests after the capability guard,
with no skips. Offscreen form inspection also prompted a short visible F1 hint:
the existing menu elides long subtitles, so evidence/caveat access now appears on
the first line rather than only at the end of a clipped footer.

### Prepared native size guard

The synthesis explicitly refuses non-stock prepared size states. Checking rolling
and super flags alone missed custom native radii (for example glide/climb sizes).
The live-state callback now receives the already-cached stock profile and compares
both actual radii and top/LRB solidity bits, without hard-coded game geometry.
The same six-donor landing oracle queries the prepared cast at frame 5, applies
custom native radii while not rolling, requires NON_STOCK_STATE, restores standing
radii and requires FOUND again. Discovery does not replay or mutate that frame.
This closes a missed requirement within the agreed stock-profile design.

## Desktop editor validation

The provided `scripts/install-engine.sh` builds the engine without tests and
installs it into the editor's private `.m2/repository`. It ran under both
worktrees' build locks and the engine's shared build-activity lease. The engine
artifact validation reported no findings. The desktop source change is only its
hand-listed Direct menu entry; its existing Stageplay picture test also captures
the player and object footprint forms.

From the editor task worktree:

```bash
scripts/install-engine.sh ${ENGINE_WORKTREE}
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 ${ENGINE_MAIN}/tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dopenggf.stageplay.desktopPictures=true -Dsonic1.rom.path=${ENGINE_MAIN}/s1.gen -Dsonic2.rom.path=${ENGINE_MAIN}/s2.gen -Ds3k.rom.path=${ENGINE_MAIN}/s3k.gen package -B
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 ${ENGINE_MAIN}/tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dopenggf.stageplay.desktopPictures=true -Dopenggf.stageplay.liveScene=${ENGINE_WORKTREE}/examples/stageplay-demo/src/main/resources/scenes/live-air-traffic.stageplay '-Dtest=TestStageplayChromePictures#liveAirTrafficUsesNativePreviewAndDesktopAbilityInspector' -Dsonic1.rom.path=${ENGINE_MAIN}/s1.gen -Dsonic2.rom.path=${ENGINE_MAIN}/s2.gen -Ds3k.rom.path=${ENGINE_MAIN}/s3k.gen test -B
env -u DISPLAY -u WAYLAND_DISPLAY python3 ${ENGINE_MAIN}/tools/testing/maven_queue.py -Dmse=off -DskipTests package -B
```

The first editor package ran 36 tests: 31 passed, one errored before test work
because its required `openggf.stageplay.liveScene` property was missing, and four
unrelated picture families skipped behind their explicit refresh opt-ins:
TestEditorPolishS1Pictures, TestEditorPolishS2Pictures, TestEditorPolishS3KPictures
and TestAuthoringChromePictures. No assertion failures. Supplying the existing
live-air-traffic scene made the matched one-test retry pass with no skips; the
subsequent build-only package succeeded. This is 32 successful tests across the
matched attempts and four documented opt-in skips, not a full-suite pass.

New offscreen images are `target/stageplay-desktop/ground-player-form.png` and
`ground-object-footprint-form.png` in the editor task worktree. Initial inspection
confirmed legible fields and prompted the first-line F1 help hint described above.

### Final affected validation

After the roster and prepared-size guards, this focused command passed all nine
tests with no failures, errors or skips (four ROM tests and five survey tests):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 ${ENGINE_MAIN}/tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestStageplayGroundRom,TestStageplayGroundSurvey' -Dsonic1.rom.path=${ENGINE_MAIN}/s1.gen -Dsonic2.rom.path=${ENGINE_MAIN}/s2.gen -Ds3k.rom.path=${ENGINE_MAIN}/s3k.gen test -B
```

The complete guard selection also passed twice before the final size comparison
(753 tests, no skips each); the second pass includes the native roster refusal
and visible F1 text. Final affected structural and desktop checks follow below.

The final engine was installed into the editor again after the native profile
changes. Its matched picture-and-package check passed one test with no skips:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 ${ENGINE_MAIN}/tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dopenggf.stageplay.desktopPictures=true '-Dtest=TestStageplayChromePictures#captureCollisionRouteAndVisualFootprintForms' -Dsonic1.rom.path=${ENGINE_MAIN}/s1.gen -Dsonic2.rom.path=${ENGINE_MAIN}/s2.gen -Ds3k.rom.path=${ENGINE_MAIN}/s3k.gen package -B
```

That capture made a small presentation defect visible: this menu's collapsed
subtitle renders a newline as a glyph. Its initial F1 hint now uses ordinary
sentence spacing; full evidence retains its line breaks in the F1 details screen.
The caption-only adjustment is validated by a final reinstall and matched capture
rather than another complete guard run. The recorded forty-column projection
matches every placement in the final raw CLI JSON exactly; the work time is the
final observed invocation.

The complete guard selection after the prepared-size callback change passed all
753 tests, with zero failures, errors or skips, including all 34 ArchUnit checks.
Only the two single-line subtitle separators changed after that guard build;
the final editor capture below covers those literals. No native scan, selection,
profile, survey, preparation or application logic changed after its relevant
successful checks. No broad smoke or full suite was run.

The final matched editor picture-and-package command passed again after the
caption spacing correction: one test, no failures/errors/skips; package succeeded.
Both resulting forms were inspected at 1600×900: all player and footprint fields
and the initial F1 hint are legible, without the newline glyph. Final copies are
`ground-player-form.png` and `ground-object-footprint-form.png` beside the input
and raw output in `${TASK_ARTIFACTS}`.
Consumed Maven logs and generated configuration templates were removed.

Desktop editor commit: `6812543f3a508a942be47c2c5d9c435c26f50ae8`. Engine commit is the commit containing this
report. Both branches remain local in the authorized worktrees for the lead;
there was no merge, push, main-workspace branch switch or broad smoke run.
The remaining delivery step belongs to the lead: change-based smoke on the
shared player physics change before integration. These focused checks do not
claim an exhaustive trace sweep or a full-suite pass.
