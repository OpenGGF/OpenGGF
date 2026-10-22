# Native title modes and competition delivery

Date: 2026-10-22. Base: `94c40570a15a11b70caf7456e9b0d0a8eec8a9b4`.
Owner: native_modes swarm lane, isolated branch
`feature/ai-gap-native-modes-20261022`. Integration belongs to the parent swarm.

## Source and activation boundary

The S2 REV01 title selects one player, two player, or options through
`Obj0F_Main` and `TitleScreen_CheckIfChose2P` / `TitleScreen_ChoseOptions`.
Its attract sequence is EHZ1 (competition), CPZ1, ARZ1, CNZ1, selected by
`TitleScreen_Demo` / `DemoLevels`. `OptionScreen_Controls` uses the shipped
`fixBugs=0` A-button behavior on **all** rows; Start on sound test returns
to SEGA. Options launch deliberately does not clear emerald progress.

S3K competition selects Grand Prix, Match Race, or Time Attack through
`Competition_Menu`; `Comp_ZoneList` orders ALZ, BPZ, CGZ, DPZ, EMZ. Its
`Obj_Sonic2P` / `Obj_Tails2P`, miniature art, wrapping and specialized touch
response are separate native paths. Arena IDs $0E-$12 use S3KL object
dispatch; the same table-selection correction applies through zone $15.

Current source reproduces the audit: `GameLoop` rejects `TWO_PLAYER`, sends
`OPTIONS` to ordinary gameplay, and has no native competition session;
`GameplayTeamBootstrap` constructs CPU secondaries; `SpriteManager` supplies
P1 input to ordinary non-CPU playables. One camera and global player counters
cannot represent the shipped competition product. The existing August S2
[design](2026-08-09-s2-native-competition-human-p2-design.md) remains useful
source evidence, not an implemented capability.

Neither an action token, an object-table fix, nor an ordinary two-sprite boot
authorizes competition activation. A provider exposes its competition screen
only after the connected session, supported arena and result flow work.

## Owned interfaces and file boundaries

- The lead owns `GameLoop` title-action routing and native-mode dispatch,
  native menu/session contracts, `GameMode`, shared participant/view plumbing,
  and API signature-pin reconciliation. Other lanes retain their ending and
  special-stage lifecycle regions; overlapping hooks are exchanged explicitly.
- The S2 title/options consumer owns `sonic2/titlescreen/TitleScreenManager`,
  new `sonic2/options` code and ROM-backed menu assets. It uses an internal
  `NativeMenuScreen` contract, obtained via `NativeTitleModeProvider` from
  `GameModule.getGameService`, rather than a game-name branch in the shell.
  A menu exit carries its exact launch team and progress-reset semantics.
- S2 competition owns new `sonic2/competition` state, native results, select,
  items and two-player assets; shared input/camera/counter hooks remain lead
  owned. S3K competition owns new `sonic3k/competition` state, five arena
  consumers, specialized object identities, miniature art and native results.
- General S3K campaign/audio/loading, movement/glide semantic definitions,
  `PlayableSpriteMovement` glide and `GlideWallGrabTerrain` stay with the
  peer coordinator. No competition consumer edits those surfaces implicitly.

All runtime assets come from the supplied ROM. New persistent session state
must have a registered rewind adapter; fresh launch/load establishes history
isolation, and restore must replay both participants and both views.

## Acceptance matrix

This is a delivery ledger, not a claim that rows are implemented.

| Product boundary | Required independent acceptance | Initial state |
| --- | --- | --- |
| S2 retail title menu | Both controllers, three selections, wrap, native art and exit actions | Options/one-player connected; competition row remains gated |
| S2 options/sound test | Three teams, two item policies, $00-$7F sound IDs, shipped A bug, Start destinations, ROM boxes/text/background | Implemented; consumer ROM checks and production launch verified |
| S2 idle attract | Native 640-frame ownership, four-demo rotation, ROM input playback, abort/end return and EHZ competition demo | Missing |
| S2 first arena | EHZ both human controllers, independent state/cameras/HUD, compressed art, dual object/ring lifetime, finish and original act results | Missing |
| S2 remaining competition | EHZ2, MCZ1/2, CNZ1/2 including native layout, item/teleport, death/checkpoint, act/zone/game results and ties | Missing |
| S2 competitive special | Native two-player halfpipe, independent rings, three-stage series, tiebreaker and original results | Missing |
| S3K entry/session | Grand Prix/Match Race/Time Attack, player select, five arena selection and session isolation | Missing |
| S3K first arena | ALZ two views and human slots, native miniature movement/art, wrapping, items, laps, goal and results | Missing |
| S3K remaining arenas | BPZ/CGZ/DPZ/EMZ specialized objects, native deformation, item/goal/result paths | Missing |
| Rewind and presentation | Capture/restore plus forward replay for both players, load-history isolation and offscreen split-render evidence | Missing |

Workers run focused tests only through the queue, with absolute ROM paths.
The parent owns combined smoke against the pinned pre-task integration base.
No broad/full suite belongs to a consumer. Each milestone records exact tested
source, failure/skip identities, active jobs and remaining matrix rows.

## Native host milestone

The host now routes options through a typed native screen, keeps unavailable
competition at the title, and accepts menu/arena/results phase changes from the
native owner. Launches pin an exact no-save team without writing persistent
configuration; the S2 options path copies only emerald inventory and special
stage cursor into fresh managers. Input edges advance after the screen consumes
its command. Mode exit/module replacement retires the active screen.

A bounded review rejected loop-only publication of a replacement runtime: Engine
would retain its retired level/sprite/camera managers for rendering. The native
launcher now publishes through Engine's existing binding owner; a focused
manager-identity regression covers the handoff. This is separate from the
required full rendered options-to-level evidence after the consumer is wired.

The initial focused selection passed 39 tests with zero failures, errors or
skips on `3c711d86c` plus the host worktree changes. The exact queued command was
`maven_queue.py --lean -Dmse=off -Dtest=TestNativeMenuController,TestNativeMenuProgress,TestGameLoopNativeMenus,TestNativeMenuRenderDispatch,TestMenuScreenModeController,TestEngineRenderDispatcher,TestGameplayTeamBootstrapContext,TestModApiSignatureSurface test`.
Subsequent publication/PLC-phase edits receive affected focused checks before
commit. This is host validation, not competition gameplay or ROM parity proof.

The affected follow-up command
`maven_queue.py --lean -Dmse=off -Dtest=TestNativeMenuController,TestGameLoopNativeMenus test`
passed 9 tests, zero failures/errors/skips, after 342 seconds of queue waiting
and 88 seconds of Maven execution. It includes Engine/loop manager identity
publication and separate ordinary-level/results PLC phase authority. Source was
`b8807b95a` plus this host change; no broad suite or rendering proof is implied.

The options consumer `1774ae03d` (lane pick `a1ee57fea`) passed 62 focused checks,
including four complete ROM-RGB screen comparisons and native music-mailbox
service integration. Module wiring now retains the options owner across fresh
worlds. A production `GameplayCaptureSession` regression drives title → options
→ Tails-only gameplay, renders ROM menu pixels and visible Tails, checks fresh
manager/participant identity, and verifies direct-launch emerald retention versus
the SEGA/title new-game reset. It also checks that persistent team configuration
is untouched and the actual audio backend is no-device `NullAudioBackend`.

On `d0290b7f3` plus module wiring, the host/controller/API checks passed 20 tests.
The rendered launch initially exposed a premature test expectation at the native
title-card boundary, not a runtime transition failure. After waiting for the
actual LEVEL mode, `TestSonic2NativeMenuLaunch` passed with no skips (91 seconds
queue waiting, 28.729 seconds Maven execution). These checks do not certify native
competition, attract demos, or startup timing.
