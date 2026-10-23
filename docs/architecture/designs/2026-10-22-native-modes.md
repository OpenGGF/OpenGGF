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

The initial base reproduced the audit: `GameLoop` rejects `TWO_PLAYER`, sends
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

## Human world and rewind prerequisite milestone

On `1d5ad54e6` plus this milestone, `NativeCompetitionWorld` composes a pinned
ROM world with an explicit supplied human roster and native world policy. It
validates the participants before retiring the outgoing context and publishes
the resulting managers through the existing Engine owner. This remains an
internal composition seam; neither competition provider is activated.

The factory now installs supplied humans before policy roster binding. Slot
identity determines both controller inputs, including independent ABC edges,
and P1/P2 execution order; ordinary CPU bootstrap and scripted input remain
separate owners. The canonical level update advances each present bank once.
Actor restore relinks the policy roster before object reconstruction, and a
second camera adapter plus final reference repair keeps both captured views
while focusing reconstructed participants. This proves registry behavior, not
the still-pending live competition rewind controls or load-history isolation.

S2 plain AllocateObject now consumes the streaming provider's semantic general
slot limit; native placement banks, explicit reservations and AllocateAfterCurrent
keep their own ranges. The collector-aware lost-ring call carries the actual
actor. The native bumper skips its ordinary moving-endpoint unload, while
ordinary play retains it. Replacing a ring spawn list also clears its active
indices, preventing an empty editor resync from retaining invalid indices.

`LevelAnimationInternalAccess.installNativeAnimatedPatternManager(context,
manager)` installs a rewindable native pattern owner with its own key. It retains
the existing palette manager and its original adapter: S3K's combined manager
still owns palette cycles/fades after competition replaces AniPLC. Domain state
is removed at ordinary animation initialization/reset and refreshed on native
replacement. The independent regression restores and replays both owners and
rejects a replacement that aliases the palette key or has no rewind state.

Focused evidence, all queued with Java 25 and Lua 5.4:

- Required S3K keep-green loading/bootstrap/decoding and participant/input/API
  selection: 86 passed, zero failures/errors/skips, 96 seconds execution with
  zero queue wait, absolute locked-on ROM and surfaceless EGL selected.
- Allocation/touch/loss/API selection: 123 tests, one error in an old bumper
  fixture missing injected services; the remaining 122 passed. Correcting that
  fixture yielded four bumper and 36 ring checks passing in the next selection.
- That 46-test selection had one new camera-registry fixture error because its
  mocked collision system returned a null snapshot; the fixture now uses the real
  collision owner. No runtime workaround was added for either fixture error.
- Final affected command `maven_queue.py --lean -Dmse=off
  -Dtest=TestNativeCompetitionParticipantDispatch,TestNativePatternOwnerReplacement,TestModApiSignatureSurface
  test -B`: 20 passed, zero failures/errors/skips, 88 seconds execution, zero
  queue wait. Includes both-camera full-registry restore and native pattern/palette
  ownership. Source was frozen for that run. An independent read-only review
  found no material issues in this bounded prerequisite layer.

The complete product acceptance matrix above remains open for live world,
S2 interlace/halfpipe/teleport, both native session/render/result products and
actual route capture/restore coverage. Domain-only tests do not close those rows.

## Immutable native view projection milestone

`NativeCompetitionRenderer.View` pairs a captured camera, native VSRAM/BG
registers, a complete packed horizontal-scroll page and an immutable admitted
sprite frame. Each view renders its full native coordinate space to an owned
scratch target before the compositor copies only its declared raster rows.
This keeps background/priority scratch passes independent from the split and
preserves the lower view's native screen-origin subtraction in VSRAM. No camera
tracker, physics, world timer, animation or palette cycler runs during drawing.
Separate `prepareSprites` collections retain independent H40 table/line budgets.

`LevelArtInternalAccess.initializeObjectArt(level)` exposes the existing object/HUD
art step internally; it does not run the ordinary playable-art admission bundled
into campaign `initArt`. The generic renderer preserves native mode ownership of
ROM plane addressing, sprite projection, page publication and visibility flags.
In particular, ordinary decoded-world sampling is not proof of S3K competition's
native refresh-row duplication, or S2's interlace VRAM/field/H-int state. Those
producers remain explicit integration work.

On `c98adcaec` plus this renderer/bridge, the exact focused selection
`TestNativeCompetitionViews,TestNativeSpriteSubmissionQueue,TestObjectManagerNativeSpriteSubmissions,TestNativeSpriteSubmissionRegistration,TestModApiSignatureSurface`
ran 57 tests with zero errors/skips. Its only failure was a test comparing
array-backed `GameStateSnapshot` values by reference equality, after every
composed framebuffer pixel and camera/frame assertion had passed. The test now
uses the existing content-aware rewind diff. The affected two-view checks passed
2/2, zero failures/errors/skips, after 22 seconds of queue waiting and 29.257
seconds of Maven execution. Both commands used Java 25, Lua 5.4, the absolute
verified S2 ROM and surfaceless EGL with display environment variables removed.

The view test compares every pixel of a 108/116 split with the corresponding
rows from independently rendered views of real loaded ROM terrain and admitted
art. It checks independent 20-piece scanline admission, immutable scroll input,
restored scissor state and unchanged camera/frame/game-state values. It is a
render composition regression, not a native arena/field timing certification.

The split renderer now drains earlier commands under their owning projection and
uses `GraphicsProjectionAccess.openProjection` for each complete native draw.
It clears an incoming local matrix override while drawing and restores the exact
source/matrix tuple afterward. The real split-view pixel check seeds an identity
matrix override that would otherwise replace the native orthographic matrix,
verifies both retained terrain views and SAT images, then checks reference-exact
restoration. On `e9236582a` plus this change, the queued five-selector check
`TestNativePaletteOwnerReplacement,TestNativePatternOwnerReplacement,TestNativeCompetitionViews,TestRetainedPlaneRendering,TestGraphicsProjectionScope`
passed 26 tests with zero failures, errors or skips (1:29 Maven execution, queue
wait reported through 90s before admission). The command used Java 25, Lua 5.4,
unset displays, explicit surfaceless EGL and both absolute S2/S3K ROM properties.
This checks the composed palette-owner and projection seams; the synthetic split
producer still does not certify the native arena's actual mappings or geometry.

## Admitted native loop handoff

The competition screen dispatch must not wrap a canonical world step in the
ordinary menu PLC claim/prepare pair. `LevelFrameStep` already owns both edges;
the old composition would prepare the same token again when the screen returned.
`NativeCompetitionIterationAccess` now scopes the admitted token to the screen
update. `NativeCompetitionWorld.step(input[, nativePlayerPass])` consumes that
token through the same canonical executor, without adding a token argument to
the screen interface. Explicit-token overloads remain available to production
drivers that already own their iteration.

One native loop may run per scope, including an alternate halfpipe scan with its
own semantic phase. Foreign tokens, nested scopes and a second world step fail
before another body runs. An idle or setup-only screen closes an ordinary wait;
an existing blocking fade retains its prepared token. Options, selection and
results continue through their existing menu phase wrapper. Scope state is
removed on callback failure and never survives into drawing or a later update.

On `60ebe9309` plus this handoff, the exact queued Java 25/Lua 5.4 selection
`TestNativeCompetitionIteration,TestGameLoopNativeMenus,TestPlcVBlankOrdering,TestNativeCompetitionParticipantDispatch,TestNativeCompetitionFixedAuxiliaryDispatch,TestNativeCompetitionFixedSstAccess,TestObjectCollisionResponseList,TestModApiSignatureSurface`
passed 63 tests with zero failures/errors/skips (92 seconds Maven execution).
The seven new checks include the actual canonical level executor and alternate
native scan, assert one VBlank/prepare and the ordered hardware service boundaries,
and cover setup, fade, menu, failure and invalid-token controls. This validates
the loop composition seam; it does not close any arena, halfpipe or title
activation row in the acceptance matrix.

## Authoritative retained plane geometry

The internal `RetainedPlaneDescriptorSource` publishes nullable foreground and
background `Plane` owners with `widthTiles()`, `heightTiles()`, `revision()` and
`descriptorAt(pixelX, pixelY)`. A nonzero revision makes the complete plane
authoritative. Each positive power-of-two dimension is at most 128 tiles; no
additional cell-count restriction is imposed by this render-only contract.
The source owns wrapping on both axes and anchors the image at zero. The tilemap
manager validates geometry before reading descriptors, builds native pattern
words directly, and keys the derived image by source identity, plane identity,
revision and dimensions. Layout dimensions, camera windows and actual-art height
scans do not participate. Rewind restores the domain owner and invalidates these
derived keys; it does not snapshot another authoritative copy of the plane.

The renderer uses the selected front plane's vertical wrap and preserves retained
horizontal wrapping in widescreen. The background tile pass uses the exact plane
period and height, including 128x32 and 64x64 images, while the existing compositor
keeps native HScroll/VScroll ordering. A direct per-line tile-pass alternative was
rejected because it would apply HScroll before compositor vertical displacement,
potentially selecting another scanline's scroll word. Existing one-off descriptor
overrides remain compatible, including mixed foreground/background ownership.
This prerequisite does not certify a domain plane producer or activate competition.

The isolated renderer child starts at `60ebe9309`; no uncommitted parent PLC
iteration is included. Read-only review caught mixed ownership bypassing legacy
foreground rewind invalidation, and art-lookup invalidation failing to publish
without a descriptor revision. Both are covered independently of plane geometry.
The pixel oracle's first art repeated every 64 columns and could therefore hide
an incorrect 512px cap on a 128-column plane. Its corrected palette includes the
high column bit, while the row oracle distinguishes rows 0/32 and 63/0. Expected
colors are decoded from synthetic native words and asymmetric tile pixels, not
from the renderer's packed descriptors or a second rendering of the same view.

Combined affected validation on that base plus this child change used
`maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl
-Dtest=TestRetainedPlaneDescriptors,TestRetainedPlaneRendering,TestLevelTilemapManagerRewindReset,TestLevelRendererBackgroundViewport,TestLevelRendererBucketInvalidation
test`, Java 25, Lua 5.4, unset `DISPLAY`/`WAYLAND_DISPLAY`, and the absolute
locked-on ROM via `-Ds3k.rom.path` (CRC32 `63522553`, SHA-1
`CFBF98C36C776677290A872547AC47C53D2761D6`). It passed 51 tests with zero
failures, errors or skips after 115 seconds queued and 1:24 of Maven execution.
The EGL check uses production `GameplayCaptureSession`/`HeadlessGameBoot`, verifies
the installed `NullAudioBackend`, and draws through `NativeCompetitionRenderer`
and `LevelRenderer`, including both-plane 128x32/64x64 boundaries, palette/flips,
revision uploads and unchanged-view buffer/publication identity. The earlier
unchanged incremental-window selection passed 30 checks; ordinary rewind and
renderer regressions are included in the final command. No broad suite or native
arena/ROM producer parity is claimed; combined S3K coverage belongs to the parent.

The first commit attempt was rejected by the conservative Mod API policy detector:
its only declaration delta was a private nested cache record inside the annotated
tilemap manager. That representation was replaced with private source/plane/revision
key arrays, comparing dimensions against the existing derived tilemap dimensions.
The detector's declaration output then matched the base exactly and the unchanged
pre-commit hook passed. Neither signature pins nor hook policy were changed.
The exact queued assertion `maven_queue.py --lean -Dmse=off
-Dtest=TestModApiSignatureSurface#candidateZeroSevenSurfaceIsPinnedToTheCurrentSurface
test` then passed 1/1 with zero failures, errors or skips on the private-field
representation (Java 25/Lua 5.4, 1:29 Maven execution). The candidate signature
therefore remains equal to its unchanged pin.

After this representation change, the final affected selector
`-Dtest=TestRetainedPlaneDescriptors,TestRetainedPlaneRendering,TestLevelTilemapManagerRewindReset`
passed 24 tests with zero failures, errors or skips under the same explicit EGL,
no-display, ROM and Java/Lua settings above (28.729 seconds Maven execution).
The other renderer and incremental-window code was unchanged; its completed
focused coverage was retained rather than repeated.

## Competition menu intensity and pause waits

`NativeMenuRaster` composes detached ROM nametables and resolved mapping pieces
at native 320x224. Whole sprite footprints pass through the existing shared H40
SAT evaluator. The first opaque admitted sprite wins before terrain priority;
a later high-priority sprite cannot replace an earlier hidden low-priority one.
Plane A/B priorities select intensity even when their pixel is transparent.
Low-priority sprite colors $0E/$1E/$2E force normal intensity. Plane palette-three
$E/$F pixels remain ordinary CRAM colors. The domain's complete competition-menu
ROM inventory found no active sprite $3E/$3F operator pixels; this bounded port
rejects those operators rather than approximating them. The native merge is
corroborated by Genesis Plus GX `make_lut_bg_ste` and `make_lut_bgobj_ste`, revision
`49c584764893b0505ac7f768a754f97330fa4392`.

An all-shadow backdrop was rejected: the competition menu initializes transparent
high-priority Plane A words ($8000), then clears its card windows. Those words
raise the underlying low-priority background to normal intensity despite having
no opaque pixels. Halving final RGB was also rejected. The indexed result first
applies native CRAM fade, then normal/shadow DAC conversion and the configured
display profile/output expansion. The S3K adapter consumes domain scene data;
it does not transcribe menu object tables or supply fallback art.

`S3kCompetitionMenuRenderer` queues one detached image after earlier pattern
work. It owns its texture/FBO and restores incoming framebuffer, unpack, texture
and scissor state at its command boundary. Unpack state is normalized before
first texture allocation as well as uploads. Closing invalidates previously
queued commands, preventing them from recreating retired GL resources.

`NativeCompetitionWorld.pauseVBlank` services a NORMAL_PAUSE token without
publishing controller input or running actors, cameras or level state. The mode's
captured native planner owns the previous-sample entry test, current-sample pause
loop, and resume/Time-Attack-exit continuation. A resume observed after the pause
wait still returns PAUSED; a distinct admitted iteration runs the ordinary wait
and body. A boolean wrapper that resumed immediately in the pause row was
rejected because native competition returns to `loc_6678` and waits again before
its final body and game-mode check. No generic campaign pause behavior changes.

These consumers remain behind native-mode activation gates. Menu transport and
pause-boundary checks do not certify the actual split arena producer, hardware
page publication, complete title/session/result route, or native emulator parity.

On `a2e7ce453` plus this slice, the focused six-selector command
`-Dtest=TestNativeMenuRaster,TestS3kCompetitionMenuPixels,TestNativeCompetitionIteration,TestRetainedPlaneRendering,TestNativeCompetitionRingInitialization,TestModApiSignatureSurface`
compiled after an inaccessible palette-helper call was corrected. Seven raster
cases, unchanged iteration cases, retained-plane EGL, ring-init and eleven API
checks passed; the two new fixtures then exposed nested Mockito construction and
a missing PNG directory. Subsequent affected checks isolated a record-array
identity assertion and an unfocused test camera. Those fixture corrections do not
change native physics. The final exact `TestNativeCompetitionIteration` selection
passed 8/8 without failures, errors or skips (29.798s Maven), exercising the public
world pause method, preserved actor/camera/input/game-state contents, and the next
ordinary token's actual world body. The unchanged menu EGL case passed 1/1
(33.127s Maven for that two-class invocation, whose then-unfixed pause fixture
still errored). Thirty distinct final-state cases passed across these affected
runs, without claiming a single combined green invocation.

All checks used the queue, Java 25.0.5 and Lua 5.4. Rendering used unset display
environment variables, explicit surfaceless EGL and the original absolute S3K ROM
property. Its five ROM menu phases were inspected as temporary PNGs and every
uploaded framebuffer pixel matched the indexed compositor's display conversion.
The independent raster tests establish priority/intensity and budget controls;
the upload comparison alone is not an independent emulator oracle. The hostile
unpack fixture seeds/checks state at the renderer command boundary and retires
its own PBO before unrelated outer cleanup. A prior fixture left that foreign
PBO active during outer cleanup and produced GL_INVALID_OPERATION despite correct
menu pixels; narrowing its lifetime retained the allocation/restore control.
No GraphicsManager, shader, shared SAT or public API source was changed.


## Native screen loads after the outgoing iteration

The native results screen used to call the next act loader from inside its
`TWO_PLAYER_RESULTS` PLC iteration. The S2 loader's authentic leading object pass
and 25 title-leave waits then attempted to open another lifecycle iteration
before the outgoing token had finished. The existing rejection was correct;
nesting or replacing that token would hide a product-flow defect.

`NativeMenuScreen.afterFrame` now owns setup after a successful outgoing frame.
The controller records only the screen whose update and exit consumption both
succeeded, clears stale candidates at iteration entry, and clears a candidate
before invoking it. Close/replacement/failure cannot execute an old load. GameLoop
drains this completion after `runLogicalIteration` returns, inside the production
trace failure boundary, then publishes the resulting screen mode. Both S2 act/VS
loads and S3K arena/GP loads stage their existing load operations there; input and
world execution are not repeated. This handoff finishes before presentation and
is not another saved gameplay timeline or hardware-admission path.

At `3087ec79e` plus the frozen implementation, the exact queued selection
`TestS3kCompetitionArenaPixels,TestS3kCompetitionViewRegisters,TestNativeMenuController,TestNativeCompetitionIteration,TestGameLoopNativeMenus,TestSonic2CompetitionProduct,TestS3kCompetitionScreen,TestS3kCompetitionNativeHost,TestModApiSignatureSurface`
ran 82 tests: 81 passed, no assertion failures/skips, one new GPU-view teardown
error. Queue wait was 512 seconds, Maven execution 1:25. Java 25 and Lua 5.4,
absolute verified S2/S3K ROM properties, surfaceless EGL and unset desktop display
variables were explicit. XML was inspected by selected class. All 64 transition,
controller, product and actual native-host cases passed, including outgoing result
preparation before 26 sequential entry iterations, input once, failure/close
discard, mode publication and preserved native actor/history ownership. The six
new register cases and 11 candidate API cases passed separately in that selection.
The GPU error belongs to the new Views cleanup after the Host has already torn
down the session registry; it is not a transition pass or a rendered-route claim.
Only affected GPU work is being rerun after its correction. Competition activation
remains gated, with native audio, S2 selective teleport/interlace and actual route
qualification still required.
