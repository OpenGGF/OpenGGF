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
| [195](https://github.com/OpenGGF/OpenGGF/issues/195) | ROM behavior retained | HCZ cutscene/button/tension-bridge routines release standing status without forcing a falling animation. In comparison-only `s3k-sonic-tails-complete-emeralds/hcz_4`, physics CSV rows `$381`→`$382` clear OnObj/set InAir but retain LOOKUP animation 7; `$383`–`$391` continue falling with that animation. These are segment-relative rows; V5 metadata records BK2 offset 97208, and row 898 corresponds to native recording frame 98107 under its offset convention. No forced FALL write was added. |
| [194](https://github.com/OpenGGF/OpenGGF/issues/194) | Existing art isolation; current reproduction checked separately | The older screenshot names a 0.6 prerelease. Current results jobs claim standalone pattern payloads, assemble a private bank, and cache it in `RESULTS_SCREENS` at `$60000`; they do not overwrite the level's tile bank. |
| [193](https://github.com/OpenGGF/OpenGGF/issues/193) | Existing palette behavior | The WFZ flame uses native palette line 2. Added actual placement-trigger/fire-cycle/decoded-flame color coverage instead of changing a correct palette selector. |
| [191](https://github.com/OpenGGF/OpenGGF/issues/191) | Existing fix | `4cb33be27` restores eleven-update sparkle sequences and six/eight-update blank gaps; `a2b89a071` preserves the final twinkle's natural SFX tail. |
| [190](https://github.com/OpenGGF/OpenGGF/issues/190) | Existing cue | `c3370a23a` requests OOZ laser SFX `$EA`. Added shooter Fire-event/mailbox admission and real ROM program PCM coverage. |
| [188](https://github.com/OpenGGF/OpenGGF/issues/188) | Fixed engine recovery; native hurt-pipe bug retained | Shipped Obj1E preserves Hurt; the optional `fixBugs` branch changes its routine to normal. The engine now honors hurt gravity/bounds despite object control and clears the control byte at S2/S3K HurtStop, matching owning player routines. S1's different recovery remains explicit in landing rules. |
| [187](https://github.com/OpenGGF/OpenGGF/issues/187) | Fixed | S1 music restoration tests `f_lockscreen`, including the interval after defeat before lock release. S1 events now expose that native lock to the existing shared music guard. |
| [186](https://github.com/OpenGGF/OpenGGF/issues/186) | Fixed | A `SETUP_ONLY` title/load iteration now consumes host input edges before returning. Holding the unpause key no longer produces another pause edge on the next title frame. |
| [185](https://github.com/OpenGGF/OpenGGF/issues/185) | Fixed | S1 SS results load the ROM mini-Sonic art through the existing results PLC and use Map_SSR's Continue/mini-Sonic frames after the native award boundary. |
| [184](https://github.com/OpenGGF/OpenGGF/issues/184) | Existing fix | `a56e126c8` installs the shipped S1 InitMusicPlayback FM/PSG silence program; `TestSmpsPhysicalPolicy` covers its writes. |
| [183](https://github.com/OpenGGF/OpenGGF/issues/183) | Fixed | REV01 SYZ layout is two 256-pixel rows. `LevelLayoutLoad` clears eight-row RAM, and `GetBlockData` indexes that RAM rather than repeating supplied rows. Decoder zero rows and `BgScroll_SYZ`'s initial +1 expose the correct lower strip instead of top clouds. |
| [182](https://github.com/OpenGGF/OpenGGF/issues/182) | Shipped ROM bug retained | Caterkiller's delayed body deletion has an explicit `FixBugs` comment about rolling hurt from a surviving body. Changing it would violate the project's shipped `FixBugs=0` contract. |
| [181](https://github.com/OpenGGF/OpenGGF/issues/181) | Shipped ROM bug retained | S1 `React_ChkHurt` exits the scan during flashing. The source explicitly identifies lava blocking later ring contacts; no touch-order rewrite was made. |

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
  overturned both: `MoveSprite` adds `$38` gravity (`sonic3k.asm:36077`), and
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
| S2 CPZ hurt tube | Actual tube capture, hurt gravity/bounds, S1/S2/S3K landing-control rules | Long route and composed ring-contact replay across loads |
| S2 WFZ flame / OOZ cue | Actual trigger/palette and native shooter/mailbox/ROM PCM | Full encounter replay and supported viewport/team breadth |
| S3K ICZ1 chain | Breakup, native child motion/lifetime, captured graph restore and forward replay | Supported donor/team/viewport routes and respawn boundaries |
| S3K MGZ trigger/platform/carry | Head composition, skid cue, player-steered carry | Whole act/boss routes, all supported donor/team combinations |
| S3K MGZ2 rising background | Production ROM Spiker overlap at 320/400; compositor priority/transparency/scroll oracle | Remaining widths, donors, teams and complete rising-floor rewind route |
| S3K CNZ2 escape | Both ship/flame facing directions | Complete boss route/escape rewind across all supported teams |
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
Change-based smoke, separate fresh guards, integration, push and cleanup remain
pending.

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
```

Coverage limits for retained/prior behavior: the S1 drowning check observes the
native FM/PSG silence write program, not an audible drowning transition. No
dedicated S1 lava-plus-flashing-plus-later-ring reproduction test was found;
that disposition rests on the owning `React_ChkHurt` branch. Caterkiller's
delayed-body behavior has the existing
`TestSonic1CaterkillerBodyChaining#normalHeadDestructionKeepsBodySegmentsCollidableThroughNextVblank`
regression. HCZ's animation evidence above is read-only trace comparison.
