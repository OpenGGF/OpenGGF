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


## Read-only late sprite owner projection

`NativeSpriteSubmissionAccess.visitResolvedAdmissions` walks an immutable copy of
accepted calls and resolves each physical SST at that visit. Ordinals, selected
buckets, duplicate pointers and orphan capacity remain intact. A current Java
owner with ID zero blocks external fallback. The caller may supply a typed,
slot-checked external source; ordinary draw closures are never consulted or run.
Metadata transcription remains with each native producer. The bridge adds no
ObjectManager fields or methods and does not alter queue admission, dispatch,
rendering, or rewind formats.

At `591e0145e` plus the frozen bridge, the queued command
`env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestNativeSpriteResolvedAdmissions' test -B`
passed six tests, zero failures, errors or skips. Queue wait was 440 seconds;
Maven execution was 1:27. XML confirmed all six identities, including ID-zero
precedence, typed external mismatch rejection, unchanged captured state/capacity,
and destructive graph/queue restore with fresh late owners. This is a ROM-free
input bridge, not an actual S2 sprite metadata or rendered-route qualification.

The affected S3K EGL follow-up on `866491114` plus the frozen Views sources ran
one test with one assertion failure and no errors or skips: the uploaded page
had no miniature human entry after normal world steps. Read-only diagnosis found
that the actor pass submits Draw calls before ObjectManager's next queue reset;
the reset discards those calls. The domain correction must preserve the actual
calls across that boundary. Reconstructing bodies inside Views was rejected
because it would bypass native admission, blink, and call order. The pixel check
and activation gates remain unresolved until that correction is composed.


## S2 native human world and ROM terrain child

This child starts at `893494f91` in the isolated `s2-native-competition-host`
worktree. Approved rendering prerequisites were picked in order as
`85ea932b0` (source `c49b0360b`) and `9c4127d05` (source `323239a13`);
the sole conflict retained the incoming native-sprite changelog bullet and
unrelated existing prose. The pre-reset ring composition bridge is separately
committed as `9288e3fea`. It validates native roster/stream authority before
allocation, shares the ordinary constructor/reset/status/cache path, enables
streaming before its first reset, registers ordinary rewind before native
cursors, and retains native ring-slot binding.
The authorized ring dispatch/latched-animation repair `287c79e7` was later
picked cleanly as `b3f7d5eb0`. Native adapter replacement across act loads is
separate commit `ee58d8d5b`; the injected primary identity and private art
resolver delegation, with its affected dust fixture, are `2c5782c16`.

`Sonic2NativeCompetitionHost` supplies fresh distinct human Sonic P1 and Tails
P2, binds their banks/cameras before opening `NativeCompetitionWorld`, and
retains the same world across act loads so retiring a policy cannot erase the
competition ledger. Its load profile replaces campaign progress reset, player
spawn, co-op and title assembly. The real ROM geometry/collision/placements,
production object/ring managers and admitted canonical world step remain the
execution owners. Native CNZ placements use `$1802A` / `$18492`. Both actors'
fresh-act velocity words and angle are cleared explicitly; `resetState()` alone
preserves those values. ROM player scripts and dust/tail owners use the existing
playable-art initializer; its ordinary renderer/cache does **not** establish
native interlace VRAM publication or pixel correctness.

The native terrain decoder intentionally avoids `Sonic2Level`'s ordinary
fixed-length animated-map patch. `LoadLevelBlocks_2P` (`s2.asm:86878-86898`)
overwrites its DBF counter on every source word when `fixBugs=0`: copying stops
after writing the first word whose halved tile index is zero. The verified
REV01 EHZ source `$40372` writes 119 words despite its declared 60, from
`Block_Table+$1788` through `$1874`; CNZ `$406BE` writes 127 despite declared
80, from `+$1760` through `$185C`. Both spill into deformation scratch beyond
`Block_Table+$1800`; MCZ is a zero-header no-op. The bounded 64 KiB terrain RAM
image preserves these writes and fails on out-of-ROM/out-of-RAM execution. It
is a terrain-load image, not a fabricated snapshot of unrelated live RAM.
An attempted `Sonic2Level.inMemoryBuilder` decode was rejected by the actual
six-act test when CNZ's native tile 936 exceeded the initial ordinary art
cache. The native decoder now extends `AbstractLevel` directly and preserves
the raw descriptors, ROM collision profiles and geometry. PLC/animated VRAM
references are valid independently of the initial zone-art cache; padding
that cache or deleting the native references would hide the ownership error.

`Sonic2CompetitionLevelEvents` retains the native Act 1 no-event branch and
Act 2's repeated right-boundary writes for both cameras: EHZ `$2920`, MCZ
`$2100`, CNZ `$26A0`. It never enters ordinary boss event routines. P2 scroll
runs before these writes; both camera render copies remain independent. The
loop-tail signpost threshold keeps the shipped P1-success early return and P2
fallthrough, signed word comparison, timer gates and debug suppression. CNZ's
existing slot-machine owner is not dispatched twice.

The first bounded selection on `9c4127d05` plus the bridge/new domain sources
passed 62 tests, zero failures/errors/skips. Exact selectors were
`TestNativeCompetitionRingInitialization,TestNativeCompetitionRingStreaming,TestRingNativeSlotDispatch,TestSonic2CompetitionNativeArt,TestSonic2CompetitionLevelEvents,TestModApiSignatureSurface`.
The command used `env -u DISPLAY -u WAYLAND_DISPLAY`, Java 25, Lua 5.4,
`maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl`, the absolute
verified S2 ROM property, and `test -B`. Queue wait was 4 seconds and Maven
execution 31.469 seconds. This is focused validation, not a broad suite.

The first actual-world EGL check on `9288e3fea` plus uncommitted host sources
failed at the first saved-Tails EHZ load: the independent playable-art resolver
still selected persistent configuration, leaving native P1's ROM animation/aux
resources absent. It ran one test with one error and no skips (16 seconds queue,
79 seconds Maven). The requested narrow correction delegates that private
resolver to LevelManager's injected native-roster-first authority. No campaign
configuration write is used to hide the ownership problem.

The corrected actual-world check, queued as `TestSonic2NativeCompetitionHost`
on `b3f7d5eb0` plus the authorized resolver/adapter repairs and owned new
sources, passed one test with zero failures/errors/skips. Queue wait was 14
seconds; Maven execution was 79 seconds. It booted real `HeadlessGameBoot`
with surfaceless EGL, unset display variables and no-device audio, loaded
all six profiles, exercised P2 control and once-only timers, actual ROM ring
collection, positioned real signposts in both finish orders across zones,
same-world act transitions/ledger retention, EHZ registry restore/forward
replay and actual CNZ2 timeout kills without fabricated Obj39 completion.
Saved Tails/Knuckles remained untouched; a final ordinary saved-Tails boot
still selected Tails. Five affected dust-fixture checks passed separately
in the preceding six-test invocation (its host check still failed the
subsequently corrected terrain validation). No native field was drawn.

Strengthening that regression to restore both physical finish orders in
every act exposed an actual Act 2 failure: the generic primary camera
restore consulted the saved campaign character before object and solid
identity reconstruction. With saved Tails, Tails became ID 1 and was then
skipped as the duplicate sidekick, leaving required player ID 2 unresolved.
Repairing references only in a final post-restore callback was too late.
The approved native-only adapter replacement restores P1 explicitly after
sprite restoration and before object/solid restoration, preserving the
ordinary camera path. The original regression remains intact; independent
saved-Tails/Knuckles checks force actor replacement and object recreation.

The shipped entry owner is now composed after `world.load` returns, where
the inherited VInt clock will no longer be overwritten by load completion.
Its first RunObjects has no VBlank; the existing title object's five left,
eleven bottom and nine background passes own the next 25 waits. Each pass
runs the normal two-human movement/history/animation and the existing SST
0..127 object/solid/queue owner while level and sprite cadence stay zero.
Placement, oscillators and progress timers do not run in this loop. A strict
same-thread scope excludes only these exact humans' fixed dust controllers;
ordinary and extension dust behavior remains unchanged. The existing title
state predicate owns the single BackgroundOutInit deformation callback.
The shared hooks have independent passing controls: the neutral playable pass
and strict dust scope (`66efa39c5`, five), title leave state (`73fa7ee78`, four),
object/solid/queue pass (`d53a5968c`, five), primary camera replacement
(`f504dd1e2`, three saved-Tails/Knuckles/ordinary cases), and non-idle Flipper
cold recreation (`e8fdfb61c`, four). The central Flipper change captures only
its existing animation state; no reinitialization or object timing was changed.
The whole actual host remains a separate, stronger regression.

The native touch seam (`59153e47c34b1f38f2dc87c102d0c76eb1410d0a`)
keeps the ordinary object-then-ring order and admits only the injected native
humans to ring-then-bumper-then-object touch. Its nine controls include mixed
real ring/bumper/monitor/enemy interactions. Native attacker score authority
(`80549b8e3411142164b26c96d09dadffebbdb919`) changes only the bank selected
by DestructionEffects; three real kill/independent chain/ordinary fallback
controls pass. The native feature bridge
(`52f90e2d2eda68fd871c18b9cd5b4488d90996f3`) retains the one world reel
machine and all ordinary initialization, but suppresses construction of the
ordinary bumper model; two independent feature controls pass.

The terrain/RAM producer is committed as
`8e29f2a824d9323cde72cc05132d81b6cf4bbb02`. Host/load/runtime composition is
`1d9087477fade7f21d93bea74ebde4880bcf3c42`. The latter includes five passing
art-binding checks and 21 level-event controls. These checkpoints expose the
real load data to the root render worker without claiming a completed product.

Qualified VRAM originals `95f480cbacf9fada79802e140caeee0184d0b4fa` then
`b23bce6740e03fb04af622351f9d3df809d87f55` are composed locally as
`20ffa863a` / `0120d194e`. Every createGame selects its actual fresh PLC owner;
one stable RuntimeArtCoordinator/DPLC sink forwards to that act's physical
VRAM service. Initial drain and runtime service use its lifecycle view, and
LoadZoneTiles patches only its own span after initial drain. The raw PLC adapter
is omitted; the binding adapter selects the retained act owner before canonical
actor reconstruction, then Policy.bindRoster binds that owner to the repaired
human identities. Explicit CtrlDMA publication services queued DMA only,
including LAG; it does not repeat normal PLC or loop-tail work. NativeArt.vram
still means initial zone art. Live consumers obtain the current image from the
injected Sonic2InterlaceVramService. The five art-binding controls cover the
pre-entry null owner, per-act replacement, retained-owner restore/rebinding,
delegate diagnostics/registration, and actual ROM DMA at the explicit boundary.

Actual Host job 7800 used ArtRuntime/Host/LevelEvents: 27 tests, one failure,
zero errors/skips (271 seconds admission, 28.243 seconds Maven). Art five and
events 21 passed. The real EHZ1 badnik kill, earned 2:2 overall tie, both goal
orders, destructive restore/forward and actual screen/controller afterFrame
Act2 entry passed before the EHZ2 ring test found no ring in its initial P2
window. REV01 EHZ2's first ring X is 416, versus EHZ1's 292; this was not ring
status carryover. The corrected setup walks canonical P2 camera columns to the
nearest real uncollected placement and verifies both banks remain unchanged
until its actual pickup.

The affected Host-only job 41027 then reached CNZ1 after EHZ1/2 and MCZ1/2,
but failed its strict triangle-bumper +$A00 assertion with $9CE (one failure,
zero errors/skips; 729 seconds admission, 29.690 seconds Maven). $9CE matches
the native oblique cosine branch at angle $88. The probe had positioned the
body exactly at the pre-physics eight-pixel branch threshold. Its correction
uses the outer valid contact pixel derived from native half-widths, retains
exact +$A00 and the axis-side/flag assertions, and adds actual body/cursor
state diagnostics. This is not a production bumper adjustment. Review also
corrected goal setup to advance each camera independently by at most one
native column; copying P1's camera to P2 could silently skip P2 loader columns.
The affected six-act check remains pending after these changes.

The exact parent post-frame transition source `d916bc11d7728cbc5b2626ad85e60e512465a927`
was consumed only for NativeMenuScreen/Controller and S2Screen, plus their two
focused test classes (`b0237085f`, 15 passing). The old-base GameLoop was not
selectively guessed. The actual host regression uses the real controller and
outgoing claimed result token: input advances once, setup remains staged until
that token finishes, and afterFrame then performs 26 sequential entry walks on
the retained context. Full outer GameLoop integration remains the parent's
complete d916 composition.

Native pause is a separate bounded continuation owner. The approved hook-only
4d9 original is `b784a23c6` locally (51 Python safety cases pass in 40.778 seconds),
and qualified audio original `06f7a13838bc0010e2833c54b12463507d90ce0a` is
`5e5d1cc97`; only the audio evidence conflict was reconciled, retaining existing
prose plus the incoming pause checkpoint. The exact b0c world pauseVBlank/test
subset is `a028b0f63`. No audio alias, ancestral S1 prose or parent plan was
imported. The NEW S2 pause planner captures both native controller words,
Game_paused and its wait/body continuation independently of finish timer flags.
It requires an explicit final native entry-wait sample, never a zero/$8000 seed.
NativeWaitInput reads a logical sample from the injected actual wait owner;
it neither advances InputHandler nor admits a physical field. Slow_motion_flag
also requires an explicit injected owner. Missing owners reject before load.

The Host binds AudioNativePauseAccess to its injected AudioManager. Entry writes
FE once, repeated polls write nothing, and every accepted ORDINARY_WAIT pause
continuation writes FF, including B/C frame advance while paused remains true.
The resume row still returns PAUSED; the following token owns one ordinary wait
and body. Restore/reset emit no audio operation. The pending title flag becomes
consumable only after that body, is captured with the planner, and the narrow
Screen hook consumes it once before staging another load. Simultaneous actual
world-transition/title priority is explicitly unqualified and rejects instead
of inventing precedence. The separate Screen leaf is
`3c25a05379f6bc2eee5cad3be44dd4740d77777a`; pause/planner caller checkpoint is
`5d8031a0c2b83d988b8946144d128d8ff1546d7f`. Job 45284 completed 53 tests with
one Host assertion failure, zero errors/skips (279 seconds admission,
34.978 seconds Maven): Pause 24, Product 9, Iteration 8 and API 11 passed. The
actual Host also passed pause polling, P2 resume, B/C single-body stepping,
following-body title exit, view-prepare failure continuation, restore/forward,
EHZ1/2 and MCZ1/2 before reaching the first CNZ1 bumper assertion. These are
bounded checkpoints, not six-act or combined audio qualification. Parent
retains combined audio source/receipt qualification.

The failed CNZ expectation was traced through actual terrain and ROM angle
selection. A zero-X airborne Tails still runs ObjectMoveAndFall followed by
Tails_DoLevelCollision; the native left-wall probe moved its centre ten pixels
before TouchResponse. At the real id-$A bumper, this selects the oblique
surface angle `$88`; ROM Sine_Data gives sine -49 and cosine -251, producing
exact velocity words `(2510,490)`. The earlier proposed outer-pixel axis
expectation was rejected after this evidence. The corrected test retains
actual terrain collision, requires the oblique contact geometry and verifies
both words against the ROM table, rather than fitting or altering production
bumper behavior. The final affected actual-world run below passed those strict
checks in both CNZ acts.

Qualified lifecycle originals `cb6bbece6e3b89f7cb9cc06cacc7ad72be2cdcd7`,
`9fed541ac058d2f3536cc274fb8bf870f3b47f96` and
`11ba32e397f8ac1c78f1e388b321d5f6ee6d4f81` are composed as `7f872459c`,
`01e37f3a8` and `d6ca0c2e2`. Prefix backend original
`25afa09cdece7ee9e901223cfcbbcdaad0a8f0dc` is `f95698339`. These peer sources
retain their original qualified coverage (134 tests across 17 classes for the
lifecycle stack; 52 focused for the prefix backend); this child has not repeated
those unchanged selections. Host now creates participants with the Session's
canonical factory, publishes the same lifecycle for NativePlayerDeathPass and
its concrete service, registers its adapter, and initializes it after actual
players/cameras/art exist. Real death dispatch remains in the canonical actor
world. CardSink delegates to the injected S2 provider and real prefix slots
2/3; no fixture card or artificial finish is supplied.

Camera displacement samples bracket only actual camera work through the
existing native LevelEvents pre-camera/fixed-object callbacks for P1 and the
owned P2 update directly. Signed integer deltas become native WORDs with
`(short)((new-old)<<8)`; a completed stationary pass publishes zero, while a
proven frozen call preserves its previous words. Samples are consumed before
callbacks and cleared at load, failure, reset and roster rebind. Active teleport
rejects before camera sampling/motion: the ordinary integer camera does not
supply ScrollHoriz's Teleport_flag skip semantics. Fractions, fullswap and
other raw-camera writers remain root qualification gates. The leading
RunObjects now also publishes its own Views.prepare boundary before the 25
waits; this is CPU/build-source visibility, not physical-field admission.

Obj79 P1 environment is explicitly injected and remains unavailable until the
root supplies the live native owner for dynamic resize, all foreground/background
camera banks and water words. Host never derives that bank from initial terrain
RAM, a fresh scroll provider, dry-zone constants or ordinary parallax. An unbound
P1 checkpoint throws, and supportsZone additionally requires that binding. P2's
initial checkpoint has no primary environment by the original branch.

### Remaining native qualification obligations

All `supportsZone` results remain false unless the root supplies operational
native state and explicitly qualifies the complete view/special/results route.
The narrow `Views.prepare(context, session, profile, art)` / `Views.draw` port
supplies native terrain RAM and initial zone-art VRAM to that owner; its default
qualification is false. No title/provider activation belongs to this child.

- S2's initial leading RunObjects and 25 title-leave object passes use the
  native entry owner above; the current six-act regression additionally includes the
  corrected native loader setup and pause integration. The common `LOAD_THEN_PROCESS_ONCE` coordinator is
  specifically S3K's absolute slot 3 / managed 4..93 / fixed 94+ walk; an actual
  S2 attempt failed its slot-3 invariant because S2 starts managed SST at 16.
  Reusing that coordinator was rejected and the invalid request removed.
  The load profile submits the actual primary/Std2/2P-life PLCs after
  InitGameModule creates its fresh service, drains their omitted title PLC
  boundaries, then appends secondary after geometry. The real entry owner
  services the leave iterations. An injected overseas flag selects cue 8 versus domestic 6;
  CNZ's initial 15 descriptors must retire before its secondary six are appended.
- The committed native VRAM service and stable art binding now own full-size
  playable/DPLC, object PLC and animated-art uploads into actual raw VRAM at
  production boundaries. This is CPU/art publication evidence, not native field
  drawing. The root still owns live RAM composition, shared metadata, native
  per-player ring snapshots and exact Build/VInt/HInt event authority. Ordinary
  8x8 cache population cannot qualify native 8x16 pixels or page composition.
- The ROM `$12C52` `teleport_swap_table` (`s2.asm:26169-26190`) lists 18 scoped
  exchanges, including actor bytes `$08..$3F`, speed banks, complete position
  history, cameras/background/copies/flags/differences, object cursors/block
  ownership, ring cursor pairs and bumper cursor pairs. Human identities and
  progress banks remain in place. The surrounding swap code additionally fixes
  character art/radii/standing owners and certain live-object per-player fields.
  An arbitrary whole-actor snapshot or a positions-only swap is rejected.
- Teleport already arms `$40`; its current session decrement still needs the
  native VInt owner. `Vint_Level` (`s2.asm:702-713`) decrements before the body
  and holds on pause. `RunObjects` (`s2.asm:29805-29807`) suppresses both actors,
  fixed and dynamic objects while the flag is set; horizontal scroll separately
  stops. Rings, bumpers, vertical scroll, art/PLC, oscillation and placement keep
  executing. Skipping the entire world for 64 ticks would be incorrect.
- Obj39's actual dismissed-card boundary now publishes the saved owning slot
  and time-over status through the consumed peer lifecycle/card implementation.
  The actual CNZ2 timeout check reaches real prefix slots 2/3, the P1 `$B000`
  parent, native routine 8 and the 720-frame card timer; real P2 B input then
  dismisses the surviving left card into ACT_RESULTS. Capture/restore and a
  second real dismissal reproduce the result. Consuming a time-over request
  alone never fabricates a finish. Full traversed timeout/death routes remain
  distinct from this bounded interaction. See Obj39_Check2PMode
  (`s2.asm:27753-27772`).
- The committed native CNZ three-block service (`44305b28fc7a54ddd34321a8512322ea6a2da38b`,
  local `2a60f6db0`) is consumed directly. It owns raw ROM table order, both
  cursor banks, initial P1 seeding, later P1/P2 walks, per-human collision after
  rings, rewind and cursor-pair-only swap. Source lists are 324 bytes each at
  `$1781A` / `$1795E`, including terminators. Actual Host endpoint/restore checks
  passed in both CNZ acts in the final affected run; no duplicate bumper model exists.
- CNZ's reel machine is world-global in the ROM; its two-human branch changes
  DMA placement. That does not establish native cage behavior: current
  PointPokey retains one captured-player state, whereas ObjD6 dispatches P1
  `$30` then P2 `$34` independently. Its simple P2 score reward also currently
  targets the primary bank. These are reported qualification gaps outside this
  host child's object ownership; no ordinary CPU equivalence is assumed. The
  streaming worker also owns the missing all-fields slot-machine rewind codec;
  this Host must consume its committed accessor once available, not invent one.
- The outer native-world module decorator currently hides nominal
  `SpriteTableBuildProfile` checks in existing ring/lost-ring/render consumers.
  Restoring only the inner module's interface cannot qualify those paths.
- Full physical routes, native split-field drawing, P1 checkpoint globals and
  actual post activation, teleport, exhaustive CNZ dual-bumper lifetime,
  competitive special stages and connected act/zone/game result pages remain
  separate acceptance obligations. No native nonzero frozen-camera WORD or
  fractional/raw-camera qualification is inferred from the integer motion port.

### Final bounded Host matrix

On `f9569833965810ca1fb6f2bd2c8e42e7a6521bfb` plus the owned Host,
RuntimeModule, LevelEvents and two test files, queued job 60404 passed **32 tests,
zero failures/errors/skips**: the single actual six-act Host regression and 31
camera-event cases. Admission took 457 seconds; Maven (including Java 25
production/test compilation) took 1 minute 24 seconds. The invocation used
the following flags/selector; `$S2_ROM` denotes the supplied absolute original
ROM path, passed literally in the actual run:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u JAVA_TOOL_OPTIONS \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic2.rom.path=$S2_ROM" \
  '-Dtest=TestSonic2NativeCompetitionHost,TestSonic2CompetitionLevelEvents' test -B
```

HeadlessGameBoot supplied actual EGL and no-device audio. ROM identity was
reverified as CRC32 `7B905383`, SHA1
`8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`. No native field was drawn. Every
row below loads the original ROM, uses the originally fresh two-human roster
with independent banks/cameras, executes the leading object pass plus 25 title
waits, collects an actual placed ring, and restores/replays both physical signpost finish orders.
Act 2 loads retain the exact context, art forwarder and competition ledger.
These are positioned interaction checks, not complete traversed routes.

| Native act | Bounded interaction and replay evidence | Remaining route/view qualification |
| --- | --- | --- |
| EHZ1 | Human movement and once-only timers; saved-Tails authority; real badnik score and earned overall 2:2 tie; registry object restore/forward; pause/frame-step/title continuation; actual P2 death 6→A→2 with initial checkpoint, independent life/ring banks and camera settle/replay | Full traversal; actual star-post activation/P1 globals; native fields; fullswap |
| EHZ2 | Actual native load, movement, ring pickup and both goal orders with destructive restore/forward; Act 1 ledger retained | Full traversal and independent route events; native fields; fullswap |
| MCZ1 | Saved-Knuckles authority, native terrain/object/ring banks and both goal orders with destructive restore/forward | Full traversal and zone interactions; native fields; fullswap |
| MCZ2 | Same retained world and ledger, actual act-specific load and both goal orders with destructive restore/forward | Full traversal and zone interactions; native fields; fullswap |
| CNZ1 | Saved-Tails authority; raw-order three-block cursors; actual P2 triangle-bumper contact with exact ROM velocity words, independent P1 bank and rewind; both goal orders | Full traversal; both-human cage/prize and slot-machine rewind integration; native fields; fullswap |
| CNZ2 | Act-specific bumper table and strict contact/replay; both goal orders; real timer expiry, dead fall, Obj39 prefix ownership/PLC/wait/P2-input dismissal and results restore/forward | Same CNZ interaction gaps; full traversal; native fields; fullswap |

All six support gates remain false. The root owns actual A/B/C metadata and
field admission, connected native views/results/special stages and atomic
teleport. The caller still needs a qualified production NativeWaitInput and
Slow_motion_flag owner; this regression injects a logical wait source and does
not claim hardware input or field authority. The P1 environment supplier is
unbound and throws; P2's real star-post activation is not covered by the initial
respawn-bank check. No broad suite, source-cap ratchet, provider/title activation,
main-workspace mutation, push or merge was performed by this child.


The two affected rewind guards ran in a separate JVM as job 61908 on the same
unchanged production/test source: **5 tests, zero failures/errors/skips** (4
architecture and 1 field-disposition), 165 seconds admission and 28.327 seconds
Maven. Exact selector was `TestRewindArchitectureGuard,TestRewindFieldDispositionGuard`
with `-Dmse=off -Pguards test -B` through the same queued Java 25/Lua 5.4
launcher and unset display variables/JAVA_TOOL_OPTIONS. The field guard reported
already-closed baseline gaps; this child changed no baseline or source cap.
This is affected guard coverage, not the complete guards profile or smoke suite.


Scoped disassembly citation checking against pinned S2 `34fc7f130a96` reports
17 exact/7 near-routine anchors and no unresolved or unverifiable entries.
After runtime readback, only citation-comment placement and this evidence prose
changed; executable production/test statements remained unchanged. Whitespace
and staged-path review passed. The native child workers finished with no owned
Maven process left; registry expiry remains 2026-10-23 08:46 UTC and its shared
file was read without modification.


## S2 physical video admission prerequisite

The live interlace producer is still unbound. Its CPU page construction, VInt
VRAM publication, HInt publication and drawing need distinct source boundaries.
The existing software `VIntRunCounter` delegates to ObjectManager during a level;
it is not a physical field identity or parity seed. A generic `LAG` phase also
cannot distinguish Vint_Lag from Vint_CtrlDMA or a represented mid-handler
closure. Neither ordinary frame parity nor comparison metadata may fill that gap.

Current lifecycle order is material: `latchBeforeFadeUpdate` constructs a token
and can immediately claim an outgoing blocking fade; only afterward does the
before-fade callback run. `claim` services PLC before `dispatchGameVBlank`.
Consequently a constructor identity synthesized from the logical iteration, or
an extra callback at game-VBlank dispatch, would neither prove physical admission
nor place native S2 SAT uploads before its actual DMA work. A physical permit
must exist before that first claim, supplied by the existing live/headless
admission owner. That contract is currently absent. No EngineContext, timing,
frame-token, GameLoop admission or generic carrier scaffolding was authored here.
These shared surfaces remain parent-owned for a separate architecture unit.

The native ordering to preserve is VSRAM/Hint_flag and palette, Plane A C000 and
H-scroll, P1 SAT to F800, existing queued DMA/audio, then camera/scroll/P2 HInt
copies (Vint_Level, s2.asm:698-808). HInt clears Hint_flag at handler entry,
then selects Plane A A000 and publishes the retained P2 VSRAM and old P2 RAM
SAT to that same F800 hardware table (H_Int, s2.asm:1181-1220). The clear is not
mainline resumption: the interrupt retains the CPU through H_Int_Done/RTE
(s2.asm:1234-1235). A parked builder continuation may resume only after an
explicit completed-handler/CPU-resume event, after the old P2 upload; observing
the cleared memory flag alone is insufficient (BuildSprites_2P,
s2.asm:30987-31246). It resumes only P2 and must not rerun actors, deformation
or P1. There is no shipped S2 SAT
page flip. The global H-scroll table already contains the lower segment.

Any future field seed must come from an explicit video-reset contract or a full
captured carrier restore. Unknown physical arrival, partially written SAT RAM,
and HInt display-off duration remain unsupported rather than becoming two fixed
112-line views. The independent read-only admission bridge and CPU VRAM word
transaction can be tested without supplying those missing physical facts. This
disposition does not complete video parity or authorize provider activation.


## Selective S2 transfer speed-word prerequisite

The selective actor primitive cannot safely swap Java base speed fields and a
water latch. Direct source review shows absolute writes by super_shoes
(s2.asm:25924-25949) and both shoes-expiry owners (s2.asm:36307-36329,
39042-39056), independent of an underwater status that may remain set. The
proposed native three-effective-WORD bank therefore needs explicit writer,
getter, neutral-friction and rewind ownership before full swap qualification.
Base profiles, Java friction, init-physics and Super remain actor-attached;
the word bank is not reconstructed by inverting those flags. The appended actor
private-field/tilt primitive remains held for that semantic owner, exact raw S2
routine access and the parent's qualified class-size extraction. No APS or
movement source was added for this proposal. The reusable pitfall is mirrored
as S2 P90; P89 is already used by the parent's native palette-timer work.


## Retained S3K view consumer and destructive framebuffer replay

The internal `S3kCompetitionViews` consumer uses the domain's authoritative plane
geometry and uploaded sprite-page pair. It does not rebuild the native queue or
transcribe art. Each mapping freezes only its used ROM pattern versions and uses
the default SAT source ownership/replay path. The two raster segments retain the
full 224-line H-scroll table, separately latched P1/P2 vertical words and race
backdrop CRAM line 2, color 0. Preparation binds owners; drawing reads retained
publication without ticking actors, cameras, pages, animation or clocks.
`S3kCompetitionViewRegisters` captures the distinct normal, lag and fade latch
rules. Resource teardown retires the arena and graphics resources while their
owned GL context is still current.

Original domain leaf `ed363156fc0c813e3867c385fd1b6c1aa53b2830` applied cleanly
on the full original `cb6bbece6` reconstruction stack. Its 66 focused tests passed
without failures, errors or skips (397 seconds waiting; 1:23 Maven execution).
The unchanged full-registry comparisons exposed and then verified three genuine
domain corrections: native shoe state no longer activates a campaign timer;
generic HUD recreation retains the complete captured `ObjectSpawn`; and LF0
load initialization selects the existing native parallax handler without
advancing its state. Missing-P2/both-human cases cover matching and differing
characters, retained IDs/slots/counters, current fixed-dust wiring and forward
replay. No filters, timer cleanup or snapshot rewriting were used.

On composed root `0b1204fb8` plus the same four frozen GPU source/test files, the
retained stronger check completed with command exit 0:
`env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Ds3k.rom.path=<absolute existing locked S3K ROM>' '-Dtest=TestS3kCompetitionArenaPixels#actualAlzRetainsPublishedPixelsThroughDestructiveHumanRestore' test -B`.
Surefire XML confirms that exact one test, zero failures/errors/skips, Java
25.0.5 and the requested ROM/EGL properties. Queue wait was 5 seconds, Maven
execution 1:28. After removing both humans, canonical full-registry restoration
recreated them; four forward steps reproduced player state, retained view
registers and every 320x224 framebuffer pixel. Prior six register cases, the
five-arena draw check and actual ALZ countdown/independent-controller check are
reused only for their unchanged covered behavior.

This commits a bounded internal consumer, not provider activation. Native audio,
shield/star presentation, independent native color/scanline corroboration and
all-five-arena race/route qualification remain open. S2 physical admission,
three-effective-WORD speed ownership and full synchronous swap are separate
prerequisites. No aggregate smoke or whole-session parity is claimed here.
