# GitHub issue sweep — 2026-10-13

Destination: `develop`; pinned pre-task integration base
`5e2f51f07917d5eff8ac120007dac55327f93f32`. All changes were developed in the
single `.worktrees/ai-github-issues` checkout, branch `bugfix/ai-github-issues`.
The main checkout's local files and dirty disassemblies were preserved.

## Dispositions and owning evidence

These are implementation/source findings. Executed validation is recorded below;
source inspection alone does not certify a complete act or route.

| Issue | Disposition | Evidence and change |
| --- | --- | --- |
| [205](https://github.com/OpenGGF/OpenGGF/issues/205) | Fixed | ROM `AddPoints` / `HUD_AddToScore` words count tens of displayed points. Results retain native counters and decrement timing, convert awards to engine points, preserve S1/S2 `Hud_Base` fixed zero, and append the S3K BCD zero nibble. Six writable S1 score digits use engine points divided by ten. |
| [203](https://github.com/OpenGGF/OpenGGF/issues/203) | Fixed | ICZ `sub_8B114` / `loc_8AF9E` detaches the eight chain pieces through `CreateChild6`. Detached pieces have native motion, slot lifetime, culling and captured rewind state. |
| [202](https://github.com/OpenGGF/OpenGGF/issues/202) | Fixed | CNZ escape flame follows the parent's facing through `Refresh_ChildPositionAdjusted`, keeping the flame at the rear of the ship. |
| [201](https://github.com/OpenGGF/OpenGGF/issues/201) | Existing fix | `93ae8c010` captures and restores live player hardware priority and display bucket independently. The existing two-direction restoration regression covers the omitted-state failure. |
| [200](https://github.com/OpenGGF/OpenGGF/issues/200) | Existing fix | `d52f6c573` transcribes MGZ capsule animal orbit bucket 5 and released bucket 1; results elements use native bucket 0. The unified renderer paints buckets 7 through 0 independently of hardware tile priority. |
| [199](https://github.com/OpenGGF/OpenGGF/issues/199) | Existing fix | `c03dc4170` publishes the carried leader's orientation in `TailsCarryController`; added MGZ player-steered carry coverage exercises that path. |
| [198](https://github.com/OpenGGF/OpenGGF/issues/198) | Fixed | MGZ state 8 uses ordinary plane assignment. Its high-priority Plane B terrain was absent from the sprite mask. The mask now replays the completed background compositor with its original window, scroll and sampling metadata. Spiker's native low hardware priority is unchanged. |
| [197](https://github.com/OpenGGF/OpenGGF/issues/197) | Fixed | `Draw_Sprite`'s multisprite path suppresses main mapping frame zero while retaining the column child. Ordinary projectile frame zero is an arrow and must not be emitted for a defeated head trigger. |
| [196](https://github.com/OpenGGF/OpenGGF/issues/196) | Fixed | MGZ `sub_3555C` / `sub_355E4` requests `sfx_Skid` when platform running reverses into the skid animation in shipped `FixBugs=0` code. |
| [195](https://github.com/OpenGGF/OpenGGF/issues/195) | ROM behavior retained | `CutsceneKnux_HCZ2` / `loc_65C72` triggers button 8; tension-bridge `loc_38918` / `loc_3892C` clears OnObj and sets InAir without writing animation. In comparison-only `s3k-sonic-tails-complete-emeralds/hcz_4`, physics CSV rows `$381`→`$382` clear OnObj/set InAir but retain LOOKUP animation 7 and mapping `$C3`; `$383`–`$391` continue falling with that animation. These are segment-relative rows; V5 metadata records BK2 offset 97208, and row 898 corresponds to native recording frame 98107 under its offset convention. No forced FALL write was added. |
| [194](https://github.com/OpenGGF/OpenGGF/issues/194) | Existing art isolation; current HCZ results/tally pixels pass | The older screenshot names a 0.6 prerelease. Current results jobs claim standalone pattern payloads, assemble a private bank, and cache it in `RESULTS_SCREENS` at `$60000`; they do not overwrite the level's tile bank. |
| [193](https://github.com/OpenGGF/OpenGGF/issues/193) | Existing palette behavior | The WFZ flame uses native palette line 2. Added actual placement-trigger/fire-cycle/decoded-flame color coverage instead of changing a correct palette selector. |
| [191](https://github.com/OpenGGF/OpenGGF/issues/191) | Existing fix | `4cb33be27` restores eleven-update sparkle sequences and six/eight-update blank gaps; `a2b89a071` preserves the final twinkle's natural SFX tail. |
| [190](https://github.com/OpenGGF/OpenGGF/issues/190) | Existing cue | `c3370a23a` requests OOZ laser SFX `$EA`. Added shooter Fire-event/mailbox admission and real ROM program PCM coverage. |
| [188](https://github.com/OpenGGF/OpenGGF/issues/188) | Fixed engine recovery; native hurt-pipe bug retained | Shipped Obj1E preserves Hurt; the optional `fixBugs` branch changes its routine to normal. The engine now honors hurt gravity/bounds despite object control and clears the control byte at S2/S3K HurtStop, matching owning player routines. S1's different recovery remains explicit in landing rules. |
| [187](https://github.com/OpenGGF/OpenGGF/issues/187) | Fixed | S1 music restoration tests `f_lockscreen`, including the interval after defeat before lock release. S1 events now expose that native lock to the existing shared music guard. |
| [186](https://github.com/OpenGGF/OpenGGF/issues/186) | Fixed | A `SETUP_ONLY` title/load iteration now consumes host input edges before returning. Holding the unpause key no longer produces another pause edge on the next title frame. |
| [185](https://github.com/OpenGGF/OpenGGF/issues/185) | Fixed | S1 SS results load the ROM mini-Sonic art through the existing results PLC and use Map_SSR's text-only Continue frame during the initial slide, then show and animate the mini-Sonic icon at the native award boundary. |
| [184](https://github.com/OpenGGF/OpenGGF/issues/184) | Existing fix | `a56e126c8` installs the shipped S1 InitMusicPlayback FM/PSG silence program; `TestSmpsPhysicalPolicy` covers its writes. |
| [183](https://github.com/OpenGGF/OpenGGF/issues/183) | Fixed | REV01 SYZ layout is two 256-pixel rows. `LevelLayoutLoad` clears eight-row RAM, and `GetBlockData` indexes that RAM rather than repeating supplied rows. Decoder zero rows and `BgScroll_SYZ`'s initial +1 expose the correct lower strip instead of top clouds. |
| [182](https://github.com/OpenGGF/OpenGGF/issues/182) | Shipped ROM bug retained | Caterkiller's `Cat_BodySeg1.chkBroken` selects delayed body deletion after the head breaks, with an explicit `FixBugs` comment about rolling hurt from a surviving body. Changing it would violate the project's shipped `FixBugs=0` contract. |
| [181](https://github.com/OpenGGF/OpenGGF/issues/181) | Shipped ROM bug retained | S1 `React_ChkHurt.damage` branches on nonzero flashing time to `.noDamage`; shipped `FixBugs=0` returns from the scan rather than continuing to the next contact. The source explicitly identifies lava blocking later ring contacts; no touch-order rewrite was made. |

## Decisions and rejected changes

- Preserve raw results counters for native timing/state; convert at score-award
  and presentation boundaries. Converting the stored counters would change tally
  duration and existing replay fields.
- Keep fixed trailing-zero art separate from variable digits. Scaling S1/S2
  variable bonus digits would append a second zero; retaining a variable zero
  when all counters are exhausted would display `00`.
- Reject clearing Hurt at CPZ tube entry: that is the optional fixed-ROM branch,
  not the shipped behavior. Repair the independently missing native HurtStop,
  gravity and boundary writes instead.
- Reject an unconditional HCZ FALL write and S1 lava/Caterkiller bug fixes on the
  owning ROM evidence above.
- Reject the first SYZ test bootstrap: raw decoder indexes `$86`–`$88` select
  ROM MZ, not the registry's third gameplay zone. The failing BG Y of 343 is
  exactly MZ3's `$720 * 48 / 256 + 1`; supplied-ROM SYZ3 uses `$620` and yields
  295. The corrected test derives `$8C`–`$8E` from `ZONE_SYZ` and asserts the
  loaded native zone before inspecting its layout.
- ICZ peer review initially questioned gravity and piece ordinal 4. Full reads
  overturned both: `MoveSprite` adds `$38` gravity (`sonic3k.asm:36117`), and
  `Go_Delete_Sprite_2` returns to the caller's final `sub_8AFA2` chain refresh.
  The valid finding was the narrower engine offscreen-delete window.
- Preserve the unpublished Mod API `0.7.0` candidate. The new landing-rule
  component, accessor and five-argument constructor replace the mutable `0.7`
  signature pin; the previous four-argument constructor keeps its behavior.

## Affected act/mechanic matrix

| Contract | Local coverage | Inherited obligations |
| --- | --- | --- |
| S1 SYZ1/2/3 lower background | Native ROM layout, cleared row and raster/window sampling at low camera positions | Full-act viewport/donor/load/respawn/rewind routes |
| S1 boss music and SS Continue/results | Locked and defeated-but-locked music expiry; real ROM mappings/patterns, award timing and glyph output | Full boss-to-special-stage campaigns and viewport/team breadth |
| S2 CPZ hurt tube | Actual tube capture, hurt gravity/bounds, S1/S2/S3K landing-control rules, real S2/S3K terrain callbacks and normal-held controls | Long route and composed ring-contact replay across loads |
| S2 WFZ flame / OOZ cue | Actual trigger/palette and native shooter/mailbox/ROM PCM | Full encounter replay and supported viewport/team breadth |
| S3K ICZ1 chain | Breakup, native child motion/lifetime, captured graph restore and forward replay | Supported donor/team/viewport routes and respawn boundaries |
| S3K MGZ trigger/platform/carry | Head composition, skid cue, player-steered carry | Whole act/boss routes, all supported donor/team combinations |
| S3K MGZ2 rising background | Production ROM Spiker overlap at 320/400; compositor priority/transparency/scroll oracle | Remaining widths, donors, teams and complete rising-floor rewind route |
| S3K CNZ2 escape | Both ship/flame facing directions | Complete boss route/escape rewind across all supported teams |
| S3K SOZ2 wide Knuckles controller | Refreshed 800px frozen inputs pass 48 whole-world replay windows and 18 semantic events through playable LRZ | Fresh nine-route class validation pending; other widths/donors and native-emulator presentation remain open |
| Shared scores, host pause and player priority | Award/display conservation; locked title input edge consumption; two-direction priority restoration | Full per-act campaign and transition matrices |

The [coverage backlog](../../status/level-test-coverage.md) retains the incomplete
stock-route obligations. These local checks do not certify an entire level.

## Integration context and validation

Implementation commit: `78da80b2c`. Upstream `10db9f005` and `b3464b31b`
changed documentation only; `db52f62a5` merged them into the development
checkout without conflicts. The main checkout remained on `develop`.

Verified supplied ROM aliases in the main checkout, without copying/linking:
S1 `s1.gen` CRC32 `AFE05EEE`, S2 `s2.gen` `7B905383`, S3K `s3k.gen` `63522553`.
Maven reports Java 25. Tool preflight passes with `LUA_BIN=/usr/bin/lua5.4`;
the default `lua` is not the required 5.4 executable.

Preliminary compile attempts stopped on incorrect new-test API/import
references; no tests ran in those attempts. The references were corrected.
`python3 tools/testing/maven_queue.py -Dmse=off test-compile -B` then passed
on the stable development tree (Java 25; 3,847 main and 3,835 test sources;
69 seconds). This is compilation evidence, not a test pass.
On `db52f62a5`, the primary focused invocation ran 304 tests in 87 seconds:
three failures, no errors or skips. Two new SYZ assertions had bootstrapped MZ
instead; the new ring-pickup assertion stopped at timer 95, above the native
inclusive `$5A` cutoff. Both corrections change test setup/expectations only.
The supplemental invocation passed four tests in 26 seconds, with no skips:
HCZ terrain pixels across results-art publication, detached ICZ movement/culling,
and the S3K art-registry mapping check. The corrected eight-test rerun passed
in 26 seconds, with no failures, errors or skips. Production code was unchanged
between these runs. The pickup regression checks S2 `Touch_Rings`
(`s2.asm:31942-31943`) at the blocked timer 90 and permitted timer 89.
On `8a1660050`, the change-based plan selected 3,185 candidate smoke classes
and separate guards. The actual launch environment passed preflight. Ordinary
smoke completed 26,715 tests in 3,458.69 seconds: 33 failures, no errors,
63 skips. The fresh guard JVM passed all 678 tests in 257.65 seconds, with no
failures, errors or skips. The run ID was `20261013T143418Z-d19fe86b`;
the 105-minute execution limit and ten-minute no-output limit were not reached.
Queue admission took approximately 48 minutes and did not count toward that limit.

Three smoke failures exposed test obligations of this change: the new ICZ
chain-link class adds one real isolated rewind pass, so inventory totals must
be 1,317/1,074/243; the background sampling recorder must mark its overridden
compositor initialized before the metadata bridge dispatches it. Its two
sampling expectations remain unchanged and an exactly-once assertion checks
the recording seam. Of the other 30 failures, 26 match the completed baseline
comparisons recorded in the concurrent
[mod-compatibility delivery](../designs/2026-10-13-mod-runtime-compatibility.md#completed-recovery-and-attribution):
the two MHZ authored-route pulley assertions and all 24 DEZ/LRZ/MHZ/SSZ recovery
identities, frames, slots and state-byte differences. Only nonsemantic Java
identity suffixes in the named SSZ Tails dump are normalized. `git diff --name-only`
between pinned `5e2f51f07` and their baseline `b3464b31b` is empty for `src`,
`examples`, `racing`, `pom.xml`, `tools`, `.githooks` and `.mvn`; those baseline
updates were documentation only. The recorded baseline uses Java 25 and the
same supplied ROM identities. This is reuse of observed matching negative cases,
not a new baseline run or a claim of complete route coverage. At that checkpoint,
Infinite Sonic, SOZ, CLI and networking still required focused attribution.
Their later results and remaining limits are recorded below.
Integration, push and cleanup had not yet run.

The first corrected focused run was not a pass: the inventory test passed its
one case (1.365 seconds, no skips), then the JVM aborted at the newly added
`GL11.glIsEnabled` in `LevelRenderer`'s background tile command. A no-GL recorder
cannot own that native state. Blend suppression/restoration now belongs to
`BackgroundRenderer.beginTilePass/endTilePass`, behind that owner's initialized
boundary and override seam. The pixel oracle exercises both incoming blend
states, verifies exact restoration and retains its sampled color/mask checks.
This replaces the insufficient test-only initialization repair; it is not an
OOM or a successful lean run. The failed fork dump was inspected and discarded.
The owner correction passed bounded source review and
`python3 tools/testing/maven_queue.py -Dmse=off test-compile -B` (Java 25,
73 seconds); no tests run in that build. The subsequent clean 13-case run below
verified both CPU recordings and the native mask/Spiker pixels.

The remaining matched baseline command cleaned `target/` and ran on pinned
`5e2f51f07` in the same task worktree: five cases in 103 seconds, one CLI failure,
no errors/skips. Infinite Sonic, the SOZ wide Knuckles route and both networking
backends passed. This confirms the Infinite Sonic regression. The later current
SOZ run below confirms its recording regression. The earlier network failure
remains unreproduced on unchanged networking dependencies.

The baseline CLI subprocess was observed directly with the report's classpath:
both launches returned status 4 and empty stdout. With the graphics option in
`JDK_JAVA_OPTIONS`, stderr begins `NOTE: Picked up JDK_JAVA_OPTIONS:` before
`PRODUCER_UNAVAILABLE: fixed producer pair is not installed`; without that
environment variable it starts with the expected producer diagnostic. This is
an inherited launcher-prefix assumption, not an audio dispatcher regression.
Subsequent focused checks retain the graphics backend through Maven's explicit
`-Dopenggf.headless.gl=surfaceless-egl` property.

The S1 correction preserves `max(declaredFG, declaredBG)` as logical foreground
height while retaining eight cleared BG rows. All 19 supplied S1 routes have
BG height no greater than FG height; GHZ1 is 5/1, so padding had incorrectly
changed Infinite Sonic's foreground source height from 5 to 8. New assertions
exercise real GHZ1 `LevelGeometry` (1,280 FG / 2,048 BG pixels) and all three SYZ
foreground heights. No procedural-mod budget or assertion threshold was changed.
The clean current focused run on `013d57d74` plus that S1 correction completed
13 tests in 96 seconds: 12 passes, one SOZ failure, no errors or skips. Inventory,
both CPU compositor recordings, native mask/Spiker pixels, three S1 geometry/
background tests, Infinite Sonic, CLI and both racing backends passed. The mask
oracle covered both incoming blend states. SOZ still failed at `cold route reaches
its real boss`; its baseline case passed, so that checkpoint required frame-state
attribution before delivery. The later witness and controller repair are recorded
below. The network failure was not reproduced in
either focused comparison; its unchanged synthetic transport dependencies and
packet-discarding test wait helper limit attribution of the original broad failure.

The temporary read-only SOZ witness then ran the same input on `ff64248bc` and
the pinned base, cleaning build outputs when changing HEAD. The first physics
difference is input 24045: hurt Knuckles is released from the light at
`($45F2,$4B6)` with base Y velocity `-$400`, candidate `-$3D0`. Native
`loc_17BD0` integrates motion and adds `$30` independently of `object_control`,
so the candidate owns the correct acceleration. The positions reconverge by
24100; later candidate/base Y 1203/1202 at 26990 is consistent with retained
fractional motion; that original comparison observed integer centres, not raw
fractions. At 26992 the candidate catches the wall at X 18837 while the original
recording passes at X 18840. This is controller-recording invalidation after a
ROM correction, not evidence for restoring the omitted gravity. Raw CSVs remain
temporary; the comparison does not hydrate either run.

That investigation also found the terrain callback's inherited `resetOnFloor`
gate still skipped hurt landing while an object held movement. Native
`Player_TouchFloor`/`Knux_TouchFloor` clears InAir before HurtStop tests it and
clears control. The correction keeps normal held suppression and allows that
hurt landing tail. Added tests enter the public movement dispatch and real
terrain collision, covering both floor callbacks under S2/S3K and normal-held
controls; they do not call the landing setter directly.
The clean focused run on `ff64248bc` plus this correction passed 214 permanent
cases (nine hurt-movement, six landing-control, 13 air-landing, 186 movement),
with no errors/skips. Two temporary controller candidates added one jump at
26985 or 26987 while preserving the remaining 35,440-input stream. Both cleared
the wall and reached the lower light, but died later at 27773/27774 during the
rock-pushing passage. They are rejected inputs, not weakened route assertions or
passing cold completions. The complete 216-case invocation took 90 seconds.

### SOZ recording repair and merged-develop checks

Follow-ups `013d57d74` (GL state owner), `ff64248bc` (logical S1 foreground
bounds) and `a1aa708b1` (real hurt terrain callback) remain part of this delivery.
Merge `73f23d89e` incorporated develop through `5d9dd8138`, including the updated
Maven queue, runtime compatibility fixes and Second Strike. The only conflict was
release prose; both entries were retained. A bounded post-merge invocation passed
230 permanent cases across selected-world rules, native AIZ object/child/rider
rewind, bootstrap/loading/decoding, API signatures, default difficulty policies and
hurt recovery. Its temporary jump-only candidate still died at 27773; the complete
231-case invocation took 91 seconds with no errors/skips. The union retained all
25 additive candidate API signatures, including this task's three landing-rule
entries. No candidate version or published pin changed.

The lower passage requires a Down-only roll: shipped `SonicKnux_Roll` rejects
Down while Left/Right is held. R+Down at 27437 never rolled and died at 27773.
Down-only at that grounded speed defeats the lower Skorp and retires its tail,
but the unchanged upper approach hits sideways spikes at `($4BF4,$517)` and dies
later at 30010. Moving the glide one input earlier did not clear the corner.
A26987 with the same lower roll also failed there. A short hop with the old later
Right inputs hit the spike during that hop; an A2/A1 hop sequence and two extra
full jumps instead met the upper Skorp tail during descent. These are rejected
controller recordings, not engine adjustments or passing routes.

The selected approach rolls through the upper Skorp at 28852 instead of jumping
over its body; a later roll at 28879 had already passed its actual X19202 body.
Two short stationary hops use the existing wait on the upper switch. The native
integer-only landing writes in `loc_1E154` retain the fraction: the measured
starting fraction 50688 becomes 30208 then 9728. Near-apex Right input leaves
Knuckles at `(19416,1275)` before the original glide edge; the next two inputs
reach `(19420,1277)` and `(19424,1279)` without hurt, then the narrow native top
landing succeeds at X19428. The full `SolidObject_cont` / `loc_1E154` path matters:
the four-pixel forced-top branch alone is insufficient without its narrower
`width_pixels` check. `SolidObjectParams` orders air height before ground height.
No player, spike, enemy, physics, ring, position or timer state was seeded or tuned.

The original boss suffix no longer fits that arrival. Reimplementing its steering
with a four-pixel deadband or velocity feedback failed; the existing
`SozEndBossVictoryRoute` uses an eight-pixel deadband and its own capsule policy.
Its documented 16-input / four-held-input / -48 approach was authored from a cold
30450-input prefix using engine-owned snapshots and restored pad history. Phase0
lost after three hits; phase1 naturally landed all eight at
31299/31763/32115/32467/32819/33171/33523/33875. Authoring stops restoring once the
selected continuation crosses the real LRZ load. Only controller buttons were
exported through `InputLogAuthorTool`; no live adaptive controller remains.

The new 35,412-input script and BK2 have identical controller logs. The independent
frozen-input acceptance passed one case in 35.441 Maven seconds (13.30 test seconds),
no failures/errors/skips, on `73f23d89e` runtime: real LRZ load/control readiness
35191, 48 selected 45-input whole-world restore/replay windows, eleven puzzle spots,
18 semantic events, actual capsule/results, outgoing-history isolation and 180
playable destination inputs. Temporary witnesses and authoring code were removed
before promotion. This is engine route evidence, not native-emulator parity or
certification of other viewport/donor products. Fresh permanent route and final
merged-candidate checks are recorded below.

Commit `8837dc657` promotes that controller-only repair to the canonical
`soz2-cold-knuckles-800.script` / `.bk2`; the historical 35,440-input recording
retains its original dated evidence in the
[SOZ2 matrix](levels/s3k-soz-act2.md#knuckles-800px-controller-refresh-2026-10-13).
The promoted route has 35,412 fixed pads; all nine permanent cold-route cases
passed fresh final validation after the latest merge.

Merge `a2280d37b` of develop `e6ebe555` reconciled six conflicts,
retaining the Chaos and issue entries, the 20,838-entry candidate API pin and
rewind inventory totals 1,320/1,077/243. Those totals supersede the earlier
1,317/1,074/243 expectation only for this later merged tree. The executable
candidate verified below is `a2280d37b`; subsequent closeout edits are documentation only.

The background tile-pass owner also now saves/restores the actual native draw
and read framebuffer bindings and four-component viewport, instead of assuming
the manager's cached display state. Uninitialized begin/end calls remain free
of GL queries. `TestBackgroundRendererStateRestoration` supplies the focused
regression; both cases passed in the final native graphics group. Earlier
blend/mask pixel passes retain their original recorded scope.

The updated local change-based plan selects 3,222 candidate classes across all
ten smoke categories plus guards. Preflight passes Java 25, Lua 5.4 and
PowerShell. The final focused selection is based on the reviewed new upstream
scope and affected consumers; the earlier 3,185-class smoke run is historical
coverage, not a pass on this merged candidate. Final focused results follow.

The 63 ordinary skips comprise 32 Infinite Sonic cases whose selected acts lack
the prerequisite platform/flyer, 24 opt-in probes/captures/soaks or missing local
audio-reference captures, six graphics capability assumptions, and one existing
CPZ spin-tube fixture that did not capture/release Sonic. ROM identity checks
passed; no missing-ROM skip was reported. The affected MGZ priority-pixel checks
ran successfully in their explicit surfaceless EGL fixture; these skips still
limit broader graphics coverage.

Commands below substitute `MAIN` for the absolute main-checkout path used by the
invocations; the supplied aliases were passed directly, without copying ROMs.

```bash
MAIN=$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")
LUA_BIN=/usr/bin/lua5.4 EGL_PLATFORM=surfaceless \
python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic1.rom.path=$MAIN/s1.gen" "-Dsonic2.rom.path=$MAIN/s2.gen" \
  "-Ds3k.rom.path=$MAIN/s3k.gen" \
  '-Dtest=TestResultsScoreUnits,TestGameLoopTitleCardPause,TestS3kSpecialStageResultsTally,TestSonic2SpecialStageResultsTallyCadence,TestSonic1PowerUpMusicRestore,Sonic1SpecialStageResultsScreenTest,TestSonic1SpecialStageContinueArt,TestSonic1SpecialStageResultsPlcReadiness,TestSonic1ResultsRomMappings,TestSonic1ResultsNumberPatterns,TestNumericPatternWriter,TestSonic1SyzBackgroundSampling,Sonic1SpecialStageWidescreenPresentationTest,TestPowerUpMusicRestore,TestSmpsPhysicalPolicy,TestPlayableHurtLandingObjectControl,TestPlayableHurtObjectControlMovement,TestCPZSpinTubeObjectInstance,TestSonic2OOZBossLaserSound,TestTitleScreenStarTiming,TestWfzTornadoThrusterRendering,TestSonic2PaletteOwnershipIntegration,TestSonic2ResultsFixedZeroArt,TestSonic2SpecialStageResultsWidescreenCommands,TestCnzEndBossShip,TestMGZHeadTriggerObjectInstance,TestMgzTopPlatformSkid,TestS3kIczSwingingPlatformObject,TestTailsCarryController,TestSidekickCpuControllerCarry,TestS3kBadnikChildGraphRewind#iczDetachedChainRestoresVisibilityAndForwardDebrisMotion,TestBackgroundPlaneSpriteOcclusion,TestS3kMgzBackgroundPriorityPixels,TestAbstractPlayableSpriteRewindCapture,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestModApiSignatureSurface,TestModApiReleasePolicy,TestHybridGameRules' test -B

LUA_BIN=/usr/bin/lua5.4 EGL_PLATFORM=surfaceless \
python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl "-Ds3k.rom.path=$MAIN/s3k.gen" \
  '-Dtest=TestHczResultsTerrainPixels,TestIczSwingingChainLinkMovement,TestSonic3kPlcArtRegistry#s3kArtRegistryMappingsStayWithinSaneSpriteSheetLimits' test -B

LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off \
  "-Dsonic1.rom.path=$MAIN/s1.gen" \
  '-Dtest=TestSonic1SyzBackgroundSampling,TestPlayableHurtLandingObjectControl' test -B

LUA_BIN=/usr/bin/lua5.4 EGL_PLATFORM=surfaceless \
JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
python3 tools/testing/run_categories.py \
  --base 5e2f51f07917d5eff8ac120007dac55327f93f32 \
  --rom-directory "$MAIN" --max-minutes 105 --run
```

The exact matched five-case baseline invocation below ran on pinned `5e2f51f07`;
the inherited `JDK_JAVA_OPTIONS` prefix explains its one CLI assertion failure.
It used the updated main-checkout queue wrapper while retaining this worktree's
cwd and `target/`.

```bash
LUA_BIN=/usr/bin/lua5.4 EGL_PLATFORM=surfaceless \
JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
python3 "${MAIN}/tools/testing/maven_queue.py" \
  -Dmse=off -Psmoke \
  '-Dtest=TestInfiniteSonic#openSectionsMixRomHalvesAndClimbBetweenTiers,TestSozColdRouteCapture#knucklesWideColdActTwoCompletesWithPuzzleReplayAndPlayableLavaReef,TestCompleteRunAudioCli#freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput,TestDirectConnectEndToEnd#fullRoundWithLatencyProxiedGuest' \
  "-Dsonic1.rom.path=${MAIN}/s1.gen" \
  "-Dsonic2.rom.path=${MAIN}/s2.gen" \
  "-Ds3k.rom.path=${MAIN}/s3k.gen" \
  "-Dkis2.rom.path=${MAIN}/kis2.gen" clean test -B
```

Exact historical launch strings for the 13-case current run and the 216-case
invocation were not retained; their observed outcomes above are not replaced
with reconstructed commands. The latter's 214 permanent cases were selected by
`TestPlayableHurtObjectControlMovement,TestPlayableHurtLandingObjectControl,TestCollisionSystemAirLanding,TestPlayableSpriteMovement`;
the two additional cases were rejected temporary controller probes, since removed.
That permanent selector is a reproduction scope, not a separately observed
214-case invocation or a claim that the latest merged tree has passed it.

Coverage limits for retained/prior behavior: the S1 drowning check observes the
native FM/PSG silence write program, not an audible drowning transition. No
dedicated S1 lava-plus-flashing-plus-later-ring reproduction test was found;
that disposition rests on the owning `React_ChkHurt` branch. Caterkiller's
delayed-body behavior has the existing
`TestSonic1CaterkillerBodyChaining#normalHeadDestructionKeepsBodySegmentsCollidableThroughNextVblank`
regression. HCZ's animation evidence above is read-only trace comparison.

### Final merged-candidate verification

On `a2280d37b`, the clean movement/API/rewind/ROM-loading group passed 383
cases in 80 Maven seconds; native graphics passed ten in 23.132 seconds;
all nine permanent SOZ cold-route cases passed in 67 seconds; and fresh guards
passed 680 cases across 88 classes in 258 seconds. Every invocation completed
with zero failures, errors and skips. Queue waits were respectively 207, 52, 23
and 50 seconds, excluded from these execution times. The required four S3K
loading/bootstrap/decoding/AIZ checks were included in the first group.

The reviewed integration preserves Chaos's nested presentation ownership and
this task's rear-plane priority metadata, native Hurt gravity and real terrain
landing. Candidate API signatures are the exact sorted parent union; both
landing-rule constructors remain. Separate draw/read FBOs, offset viewport,
caller pixels and both incoming blend-enable states passed actual native EGL
checks. The permanent SOZ class uses the committed fixed recording with all
existing cold-start, puzzle, replay, results and destination assertions unchanged.

The 3,222-class planner fallback includes upstream work already separately
validated and changes covered by the earlier complete smoke selection. Repeating
that hour-long selection would duplicate unaffected coverage; the final groups
cover the reviewed interaction points and repaired failures, with fresh guards.
This is proportionate focused validation, not a green rerun of all smoke
categories or a release-only full-suite pass. The historical broad run remains
red with the recorded inherited failures and one unreproduced network failure;
its matched baseline/current networking checks passed. No new engine trace
frontier or native-emulator presentation certification is claimed.

The exact final launch arguments are below, with `$MAIN` substituting the same
absolute main-checkout path as above. All tests used the updated local wrapper
from this single task worktree. Graphics flags remain explicit; no ambient
desktop display was used. The final destination is the main workspace's existing
`develop`; its executable tree can fast-forward to this validated candidate.

```bash
env -u JDK_JAVA_OPTIONS -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 EGL_PLATFORM=surfaceless python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dsonic1.rom.path=$MAIN/s1.gen" "-Dsonic2.rom.path=$MAIN/s2.gen" "-Ds3k.rom.path=$MAIN/s3k.gen" '-Dtest=TestPlayableSessionRules,TestHybridGameRules,TestPlayableHurtLandingObjectControl,TestPlayableHurtObjectControlMovement,TestPlayableSpriteMovement,TestCPZSpinTubeObjectInstance,TestMotionMutatorConsumers,TestCollisionSystemAirLanding,TestWorldPolicyHistory,TestGameLoopTitleCardPause,TestGameLoopMutatorPacing,TestRemainingRewindTailInventory,TestNativeMutatorRuntime,TestSonic3kChaosPackAssembly,TestModApiSignatureSurface,TestModApiPinPolicy,TestLevelRendererBackgroundSamplingPerformance#stationaryPositiveScrollAndNegativeYRetainExactSamplingOutput+deferredCommandsRetainAnchorsAndRingGenerationAfterSourceMutation,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' clean test -B

env -u JDK_JAVA_OPTIONS -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 EGL_PLATFORM=surfaceless python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dsonic1.rom.path=$MAIN/s1.gen" "-Dsonic2.rom.path=$MAIN/s2.gen" "-Ds3k.rom.path=$MAIN/s3k.gen" '-Dtest=TestBackgroundRendererStateRestoration,TestBackgroundPlaneSpriteOcclusion,TestScenePresentationPass,TestPresentationSpritePixels,TestS3kMgzBackgroundPriorityPixels' test -B

env -u JDK_JAVA_OPTIONS -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 EGL_PLATFORM=surfaceless python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Dsonic1.rom.path=$MAIN/s1.gen" "-Dsonic2.rom.path=$MAIN/s2.gen" "-Ds3k.rom.path=$MAIN/s3k.gen" '-Dtest=TestSozColdRouteCapture' test -B

env -u JDK_JAVA_OPTIONS -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 EGL_PLATFORM=surfaceless python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Pguards test -B
```

Packaging the same executable candidate with
`env -u JDK_JAVA_OPTIONS -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 EGL_PLATFORM=surfaceless python3 tools/testing/maven_queue.py -Dmse=off -DskipTests package -B`
passed in 31.734 seconds. Maven explicitly skipped tests and produced the main,
fat, mod-testkit, SDK and SDK-javadoc jars. No executable changes followed the
completed checks; the remaining commit records these outcomes and matrix updates.
