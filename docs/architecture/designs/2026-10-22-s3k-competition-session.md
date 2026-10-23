# Native S3K competition session and item consumers

Date: 2026-10-22. Contract base: `3c711d86c`. The native-modes lead owns
integration and activation. These consumers do not enable the title entry.

`S3kCompetitionSession` is an internal world-policy service. Goal and item
objects obtain it through `WorldSessionPolicyAccess.getService` using injected
object services. The host supplies miniature participants, real ROM terrain,
native SST allocation, the reserved projectile slot, views, sound and scene
requests. No ordinary co-op bootstrap or public Mod API extension is involved.

The host calls `beforeFrame(Level_frame_counter)` before participant slots,
calls each item's `touchNativePlayer` in the participant touch prepass, executes
objects in native SST order, then calls `afterFrame`. The neon display calls
`signalGo` when its mapping frame reaches 3. Goal objects report crossings;
only the session requests a scene change.

## Native decisions

- `sub_36DFC (sonic3k.asm:74089-74207)` changes each side latch before checking
  Y, distance or collision layer. A valid crossing requires layer `$C`, signed
  Y in `[goalY - (subtype*8 - $14), goalY + $14)`, and `abs(dx) < $20`.
  Backward crossings and death-respawn rollback saturate at lap zero. A new
  highest lap records the previous HUD lap time and replenishes wheels.
- `loc_371D8 (sonic3k.asm:74447-74455)` starts both timers on neon mapping
  frame 3. A first crossing before that causes the 120-tick false-start
  penalty. It is not permission to start ordinary gameplay timers.
- `sub_36FAA (sonic3k.asm:74302-74324)` scans live wheel routines in SST
  order and consumes `byte_36FDA`; no RNG selects wheel contents. Each new
  highest lap advances the sequence start by two, masked to the low nibble.
- `Obj_2PItem (sonic3k.asm:73281-73346)` orbits 56 pixels at byte-angle steps
  of +1 or -1, with native wrapping. Miniature touch has an eight-pixel-wide
  player box and half-height `y_radius - 3`, independently of campaign touch.
- `loc_36486` through `loc_36698 (sonic3k.asm:73390-73583)` implement speed,
  slow, full secondary-status/shoe-timer swap, one ring, projectile, slide,
  spring and empty effects. Immediate effects consume even when a cosmetic
  child allocation fails. Dropped slide/spring consume without creating a
  child when dynamic RAM is full. Projectile 4 unconditionally overwrites
  `Breathing_bubbles`, a different native allocation contract.
- `sub_36926 (sonic3k.asm:73784-73907)` requires actual ground-following,
  floor and left-wall probes. The item terrain port deliberately has no
  production flat-floor fallback.
- `loc_37308 (sonic3k.asm:74514-74582)` contains the shipped repeated-P1
  timer test. Grand Prix grace pauses only when unfinished P1 is on its
  final lap. The session retains this `FixBugs = 0` behavior.
- `sub_BA04 (sonic3k.asm:14894-14973)` chooses the GP winner by arena wins;
  an equal arena time awards neither player a point. Totals are separate.

ROM reads for item animation `$36A58`, sequence `$36FDA`, and goal animation
`$36FF2` were compared byte-for-byte with the supplied locked-on ROM. Runtime
reads use `Rom`, never the disassembly tree. Raw animation termination `$FC`
must delete transient item routines; the general animation-set abstraction
does not represent that opcode, so these consumers execute their ROM bytes.

## Coverage and host obligations

Focused classes are `TestS3kCompetitionSession` and
`TestS3kCompetitionGoalAndItems`. They cover both slots, side-latch geometry,
five laps, false-start edges, independent timers, deterministic sequence/slot
ordering, full dynamic-RAM failure, the reserved projectile overwrite, shoe
swap/expiry, dropped spring/slide behavior, scalar recreation and session
capture/restore followed by forward replay. The lead records actual commands,
results and skips after the combined focused run; source presence is not a pass.

Remaining end-to-end obligations belong to the native host: actual miniature
participants and terrain, arena/load-history isolation, native countdown,
independent cameras/HUD, sound, allocation/lifetime integration, ROM art,
offscreen rendering, TA top-three persistence, Match Race's return to arena
select, and all five complete supported routes. A results request is not proof
that those scene consumers or supported routes are ready for activation.

## Implemented domain and native host boundary

The isolated checkout is `s3k-native-competition`, based on contract `3c711d86c`.
Shared roster/menu contract `b8807b95a` was applied once as `76afbe67f`. The
parent owns shared runtime integration, the final release prose and activation.

`S3kCompetitionArenaDefinition.load` reads act-zero start centres, camera bounds
and placement pointers from the ROM. `loc_1B720` loads placements below `$800`
up front in ROM order: ALZ 8, BPZ 15, DPZ 17, CGZ 15 and EMZ 15. All use one
native SST rather than independent camera placement cursors. `S3kZoneSet`
implements the complete unsigned-byte dispatch ranges: S3KL `$00-$06` and
`$0E-$15`; SKL `$07-$0D` and `$16-$FF`.

`CompetitionArenaObjectInstance` implements the eight specialized arena hazards
and the placed path-swap, rock, spring, invisible block, button, fan, bumper and
mud variants. Off-layout EMZ rock subtypes other than `$31` and diagonal springs
are explicit unsupported branches. Their absence does not describe a shipped
placement. ROM sheets include mappings, animations, cross-bank tile references,
miniature DPLCs, menu planes and palettes; thirty source spans have independent
SHA-256 assertions. Runtime loading never reads the disassembly checkout.

The miniature model is constructed as
`S3kCompetitionPlayer(slot, character, centreX, centreY, readSpeeds(rom, character))`.
Install `S3kMiniatureAnimation.fromRom(rom)` with `setAnimator`. `sprite()` returns
the stable `S3kMiniatureSprite` roster identity; `nativePlayer()` returns its
backing model. `tick(Input, Terrain, Bounds, Level_frame_counter)` owns the native
kinematics. Its mandatory terrain port supplies real jump, wall, ground, air and
death probes. Normal campaign movement must not also tick these actors. The
shared native sprite fields can be imported with `synchronizeToModel()` when a
terrain adapter writes them; publication preserves both fractional words.

The goal allocates exactly eight children, stopping at the first failed
`AllocateObjectAfterCurrent`: two item icons, two lap numbers, neon display, two
time displays and the native no-op record. The actual ROM neon script publishes
GO on mapping frame 3. Native goal, neon and timer records own their respective
phases: false-start hold precedes children; finish handling precedes timer
increments; timer mappings show the incremented value. Missing time records do
not silently advance a session clock. False-start message and error sound are
conditional on native child allocation.

The host performs one first-hit touch scan after each miniature actor, against
the previous object pass's collision list. `touchNativePlayer` returns whether
the scan must stop. It then executes the shared SST in order, including new
children in higher slots. After both views have built their sprite lists, publish
carried visibility with `refreshNativeVisibility`. The field is native input to
full solids, rock debris lifetime and shard reformation.

Session rewind restores before sprite recreation. Immediately after all sprites
restore, `bindRoster` relinks the session to the new miniature backing models,
validates exact slot/character identities and calls `Host.rebindPlayers` for the
host's terrain/view adapters. Only then may objects restore. Object graphs need
the real identity table. The original arena field named `parent` was excluded
by a generic transient policy; fresh blade children retained no owner. The
native reference is now named `nativeParent`, and focused graph restoration uses
the real `ObjectRewindDynamicCodecs.genericRecreate` entry point. Supported
`ObjectSpawn` probe constructors are required as well as a recreate method.

`S3kCompetitionScreen` owns native mode/character/arena selection and result
destinations through a local `Host`. Grand Prix follows ALZ, BPZ, CGZ, DPZ, EMZ;
the host must retain the session across arena loads and call `enterArena` after
setting the new saved starts. Match Race returns to arena selection. Time Attack
uses one human and records its result on the finish boundary, before the results
wait. `S3kCompetitionRecords` preserves the supplied 80-byte general-save block,
performs strict unsigned top-three insertion and captures its state for rewind.
Its physical arena order follows zone IDs, independently of menu order. The host
persists only successful insertions and restores the records with the world.

No `NativeTitleModeProvider.competitionScreen()` is installed by this milestone.
The concrete host still needs native terrain/SST wiring, both view projections,
Tails' separate tail attachment, menu/result drawing, general-save persistence,
history isolation, and actual ALZ followed by the other four complete routes.
The domain's direct controller and state-machine tests do not establish those
route or rendered acceptance rows.

## Arena load and presentation consumers

`S3kCompetitionLevelInitProfile` retains the existing ROM geometry/resource
steps and replaces `InitArt` and `InitPlayerAndCheckpoint` with explicit native
host callbacks. It resolves `Supplier<List<S3kCompetitionPlayer>>` once at the
first executable step of each load. A profile created before rewind or a GP
transition therefore cannot retain retired model identities. It does not request
the common initial `Process_Sprites` token; the host executes the native initial
player/object pass once and prepares both views. `S3kCompetitionRuntimeModule`
supplies that profile, competition art and the injected scroll provider while
suppressing campaign title and sidekick admission.

The corrected `loc_1B720` contract is stricter than an all-arena lifetime mode:
it changes `Object_load_routine` to 12 before its first allocation, including
allocation failure and an initial placement at or beyond `$800`. Every placement
uses the fixed `_unkF712` pointer without ordinary respawn-bit tests or counter
advancement. `loc_1B74C` leaves the coarse back origin at zero. Standard range
helpers consequently test the fixed origin, rather than the live P1 camera;
they do not make competition objects immortal. The lead owns this shared loader
and helper implementation. The domain profile passes the complete ROM list to
its allocation callback and supplies no camera rescan or replacement lifetime
policy.

`S3kCompetitionScroll` owns both native cameras, their separate 64-entry position
histories and camera-lag words, the arena events, and the native 108/116-line
deformation regions. Both cameras initially derive from P1's centre, as in
`loc_1A454`/`loc_1A46E`. The locked-on event table selects the actual S3-half
routines and ALZ delta table. The profile's `vramDuplicateStride` is the native
refresh write stride (`$100`, or `$80` in BPZ), rather than a level-layout row
stride. Shared view code must consume the supplied profile and packed raster
words rather than install an ordinary campaign scroll handler.

The art provider uploads each shared `Pattern[]` bank once and binds its sheet
aliases to that same virtual atlas base. CPU sheet and animation queries do not
resolve ambient graphics. Arena palettes use their ROM level palette pointer,
Sonic/Tails' first line and the native `Pal_Level_2P` replacement of line two.
The owning instruction operand at `$6BFC` selects `$6C0C`; a guessed `$6C0E`
would shift every secondary colour.

Native page publication is explicit at the profile host boundary. Ordinary,
pause and title V-int branches may flip a completed page before uploading its
front. Lag republishes the retained front without a flip or DMA drain. Fade's
`Do_ControllerPal` uses the fixed `Sprite_table`, without flipping, and drains
DMA. The shared single-SAT and campaign numeric-HUD owners are disabled for
this profile. These contracts do not certify a rendered arena or a full route.

The first focused attempt for these consumers stopped in compilation because
the campaign animation publication method is package-private. Calling through
the campaign profile was rejected as well: its animation resolver omits the
competition zones, while the native table selects ALZ/BPZ scripts and DPZ's
custom camera-driven producer. `S3kCompetitionPatternAnimator` reads that ROM
dispatch, submits script and DPZ uploads in native order, then publishes only
at the profile's real DMA boundary. Its rewind payload retains both DPZ phase
bytes, script counters, queued immutable bytes and the presented destination
tiles. Existing BPZ/CGZ/EMZ palette animation remains in its original owner;
installing this pattern manager must preserve that owner's rewind adapter.

The object visual review also corrected the goal's `$08/$20` and item's
`$08/$08` native visibility extents. Their captured draw-publication flags
prevent an allocated reserved projectile from rendering before its earlier
SST slot first executes. Arena descriptors preserve the elephant tip offset;
HUD descriptors preserve the VDP-biased origins, view ownership and timer's
three fixed mapping pieces plus five independent digits. Generic views must
consume those submissions instead of inferring them from `artKey/getX`.

`S3kCompetitionTerrain` implements the miniature and item terrain ports using
the injected decoded collision geometry, native layer bits and 16-bit probe
coordinates. It does not invoke ordinary playable movement. Death uses the
session and P1 camera Y for both slots, including the shipped signed threshold
and same-pass post-respawn gravity. The actual ALZ regression compares the
decoded start neighbourhood with the existing floor oracle and derives both
feet's response from the actual ROM angle, including the odd-angle cardinal fallback.

`S3kCompetitionScrollProvider` uses the existing `CameraDrivenScrollHandler`
boundary after object execution and before animated-tile submission. It advances
both native cameras once, mirrors both outputs and suppresses ordinary camera
following. Rendering and raster queries are read-only. The host records each
actor's position immediately after that actor, before touch and object execution;
the provider must not record it again. The active handler's existing parallax
rewind path captures the supplied scroll owner, so a second independent scroll
registration is unnecessary.

The menu composer decodes native copy lists and object tables into 64-by-64
tile planes and ordered mapping pieces. Current depth determines wheel art
priority while the previously queued word determines its sprite bucket.
`MenuView` carries those six words explicitly. Phase clocks reset at native
entries; an explicit `mainLoopStarted` flag distinguishes initial result palette
presentation from a later 16-bit frame wrap. Time Attack results use plane-A
scroll `-$88`. Mapping-piece flags include full native art-word carry: the
Knuckles record icon resolves `$85AF + $BFD5` to `$4584`, changing both palette
and high-priority interpretation.

Arena and item classes now implement the existing `TouchResponseProvider`
admission contract. Their captured publication flag controls the canonical
ordered collision list; their own miniature touch methods still dispatch the
effects. A consumed wheel remains listed with collision byte zero, matching the
native pointer list. The host must query the previous list separately after
each participant so fixed-slot replacement can resolve a current SST owner.

The concrete host remains a separate next milestone. Shared prerequisites are
owned by the lead: the native world, supplied participant banks, fixed slot 94
replacement, previous collision-list access, upfront placement and view/art
injection. `Breathing_bubbles` is absolute SST slot 94; a wheel normally replaces
it before the canonical loop reaches that slot, so the projectile can execute
in the same pass. An earlier-slot fixture only proves deferred cursor behaviour,
not this actual placement. The host will use the canonical live object loop,
never execute an active-object snapshot itself.

## Focused verification checkpoint

The first queued run used Java 25.0.5, absolute supplied S3K ROM and
`-Dopenggf.headless.gl=surfaceless-egl`, with both desktop display variables
unset. It ran 84 tests with no skips: the player (19), assets (8), session (10),
goal/items (10) and screen (7) classes passed. Dispatch had one synthetic rock
subtype error; arena tests exposed the native parent-reference restore omission
above. These are new-work failures, not attributed to a baseline.

The second run completed 101 tests, with no skips: the dispatch fixture and real
generic arena reconstruction passed. Its one remaining HUD failure exposed a
second native state distinction: finished `Update_HUD_timer=$80` is nonzero,
although it no longer increments. P2's first crossing after P1 finishes must not
trigger a false start. The local fix also retains the separate initial-finisher
and allocated late-finisher SST phases.

The final affected run passed 44 tests, with no failures, errors or skips.
Together with unchanged selections from the preceding runs, the current domain
has 113 passing focused cases across ten classes. The added checks include
same-pass two-player false starts and finishes, timeout display, native child
allocation prefixes, true generic object recreation, sprite/session roster
rebinding, immediate TA record notification and restore followed by forward
replay. The desktop display remained unset; these logic/asset checks did not
launch a rendered arena. No broad smoke or release-only full suite was launched.

All Maven executions were queued from this checkout, with
`JAVA_HOME=/usr/lib/jvm/java-25-openjdk`, `LUA_BIN=/usr/bin/lua5.4`,
`env -u DISPLAY -u WAYLAND_DISPLAY`, and the common Maven arguments
`--lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl`,
`-Ds3k.rom.path=$S3K_ROM` (the supplied absolute locked-on ROM path),
and `test -B`. Exact focused selectors:

1. `TestS3kCompetitionDispatch,TestS3kCompetitionPlayer,TestS3kCompetitionArenaObjects,TestS3kCompetitionSession,TestS3kCompetitionGoalAndItems,TestS3kCompetitionAssets,TestS3kCompetitionScreen`
2. `TestS3kCompetitionDispatch,TestS3kCompetitionPlayer,TestS3kCompetitionArenaObjects,TestS3kCompetitionSession,TestS3kCompetitionGoalAndItems,TestS3kCompetitionScreen,TestS3kCompetitionHud,TestS3kCompetitionBinding,TestS3kCompetitionRecords`
3. `TestS3kCompetitionSession,TestS3kCompetitionGoalAndItems,TestS3kCompetitionScreen,TestS3kCompetitionHud,TestS3kCompetitionBinding`

The third command is the final affected check for the first milestone. Asset, miniature, arena,
dispatch and record implementations had not changed since their respective
passing executions. Whitespace and disassembly-citation checks also passed.

The fourth command selected `TestS3kCompetitionScroll,TestS3kCompetitionArtProvider,TestS3kCompetitionAssets,TestS3kCompetitionLevelInitProfile`.
It stopped in compilation before executing tests because of the inaccessible
campaign animation method described above; execution took 52.917 seconds.

The fifth command selected all sixteen domain classes explicitly: `TestS3kCompetitionDispatch,TestS3kCompetitionPlayer,TestS3kCompetitionSession,TestS3kCompetitionGoalAndItems,TestS3kCompetitionAssets,TestS3kCompetitionScreen,TestS3kCompetitionHud,TestS3kCompetitionBinding,TestS3kCompetitionRecords,TestS3kCompetitionArenaObjects,TestS3kCompetitionArtProvider,TestS3kCompetitionLevelInitProfile,TestS3kCompetitionScroll,TestS3kCompetitionPatternAnimator,TestS3kCompetitionMenuScene,TestS3kCompetitionTerrain`.
It completed 200 tests with two failures, no errors and no skips. All fifteen
non-terrain classes passed. The failures were a terrain expectation at the
native `$F0` angle edge and the fixture's flat-floor assumption. Source
`loc_11F7A` wraps `$F0 + $10` to zero, selecting horizontal momentum; `$E8`
exercises the negative half-vertical branch. The ROM check now derives the
actual paired slope rather than demanding a flat surface. These were new-work
failures and were corrected without fitting production physics.

The sixth affected command selects `TestS3kCompetitionTerrain,TestS3kCompetitionScrollProvider,TestS3kCompetitionScreen,TestS3kCompetitionMenuScene,TestS3kCompetitionArenaObjects,TestS3kCompetitionGoalAndItems`.
It completed 103 tests with one failure, no errors and no skips. Canonical
touch-list admission/recreation, native camera-boundary ownership and initial
menu presentation passed. The remaining actual-ALZ fixture still excluded odd
floor angles. The seventh command selected
`TestS3kCompetitionTerrain,TestS3kCompetitionScrollProvider` and completed 31
tests with that same one failure and no errors or skips. Its bounded observation
showed 91 matching ROM floor samples, 52 negative paired samples, all with angle
`$FF` in collision tiles `$54/$55`. Native floor selection converts this odd
angle to cardinal zero; excluding it was an invalid fixture assumption.

The eighth command selected only `TestS3kCompetitionTerrain`. It passed all 21
tests with no failures, errors or skips after the assertion adopted the actual
native cardinal conversion. Production terrain code was unchanged by the
fixture repairs. Together with unchanged affected selections from commands five
through seven, the current domain has 213 passing cases across seventeen
focused classes. The camera provider's offset convention was independently
checked against the existing `ScrollEffectComposer`; both use negated VDP words
and the existing positive BG-minus-FG scroll offset. No sign change was needed.
No renderer, complete arena route or broad suite is certified by these checks.


## Concrete native world checkpoint after shared boundary composition

The implementation checkout remains `s3k-native-competition`, isolated from the
main workspace. It composed the authorized shared roster/world/upfront/SST and
retained-plane dependencies, including the documented compatibility subsets at
`6715c9d75` and `c97b67d9e`. The latter deliberately omits the root-owned EGL
consumer test; this checkout does not contain the root renderer dependency range.
The palette lifecycle bridge is separately committed as `d08a9776f`: independent
native palette registration preserves an original combined owner while it still
owns patterns, retires palette-only predecessors, and removes the native binding
on normal reload/teardown. The committed pause primitive was imported with exact
source equivalence to root `b0c2c6ae` in source-only dependency `1cd8a10fb`.

`S3kCompetitionNativeHost` now opens the canonical shared world with the exact
human miniature pair, native LevelState/game-state banks, actual ROM terrain,
object art and source-order upfront placements. It bypasses ordinary player/CPU
bootstrap and uses one canonical world step. P1/P2 movement, position recording
and native touch consume the previous published physical SST pointer list; the
list resolves each current occupant again before P2. General allocation stays
inside the native window, and projectile SST94 replacement remains unconditional
at full RAM. The Host owns transitions after the canonical pass, never in objects.

Fixed Tails tails96/97 and dust98/99 execute in the real SST pass. Miniature dust
writes resolve the current fixed address after object recreation rather than
holding a retired Java actor. Skid children retain the shipped unscaled +$C Y
write and deferred earlier-slot execution. Native damage, life thresholds,
projectile knockback and bouncing-ring owners retain FixBugs=0 P2 spill/collection
bank quirks. GP level reload clears both total-ring words and P1 life flags while
preserving the complete P2 extra-life flag byte.

The domain owns retained 8KiB foreground/background descriptor buffers and both
physical native sprite pages. Both planes publish their exact native geometry
through the active ZoneFeatureProvider. BPZ uses64x64 tiles; the other arenas
use128x32, independent of ordinary level-layout dimensions. Page production
consumes actual admitted queue pointers, including duplicate/late-reused and
stale input words. It preserves the two physical P1 prefix entries, P1/P2 range
flag asymmetry, whole mapping footprints and uploaded-versus-prepared identity.
The root renderer consumes those DTOs; it does not infer art or advance owners.

Ordinary native phase order is now cameras/ScreenEvents, Animate_Tiles,
Render_Sprites, Animate_Palette. A domain callback at the end of the pattern pass
prepares pages before the palette update; the previous campaign pre-actor AnPal
call and duplicate post-world page build were rejected. Initial load instead
renders sprites, runs the initial tile pass, then arms the all64 current-RAM black
fade at loc_665A. The native palette owns raw16-bit current and target words,
22 fade passes and first AnPal on call23, including BPZ/CGZ/EMZ cycles. Only words
actually written by that later native phase override an object palette claim.
A direct world switch also clears an inherited graphics fade presentation;
otherwise an outgoing campaign black fade can multiply the new native RAM fade.
Existing latchable palette uploads retain VBlank publication authority.

The ROM loop dispatch span at loc_66EA omits shared ring animation and global
oscillation. The internal default-true spill-clock capability leaves ordinary
updates unchanged; competition retains its native three-word bank while still
retiring stale object mirrors. Native fresh state is zero, then ring loss writes
counterFF without a later ChangeRingFrame decrement. The feature provider also
retains initialized oscillator RAM instead of advancing it each competition body.

The captured pause planner seeds Ctrl1=$8000 from GetDemoPtr, checks the previous
P1 Start sample, and accepts current input only at its selected native wait. A
pause wait always skips miniature/object/session-tail/page preparation, including
the resume wait. Its continuation reaches a distinct ordinary wait/body before a
pending TA-B selection or slow-motion title request is consumed. P2 does not pause.
The dedicated adapter restores the planner and native Game_paused flag together.
The parent-owned native audio pause port remains explicitly outstanding; the
Host does not substitute the application/window PCM pause API.

### Current bounded evidence and acceptance limits

R9 and R10 stopped in compilation before tests (52.788s and55.505s): the first
used an invalid typed SFX call, the second omitted checked-ROM failure handling
in the tail bank. Both were corrected. R11 ran66 tests (7failures,1error,0skips):
58 passed, while Host fixtures incorrectly expected first dynamic SST3 rather
than4, treated structural human P2 registration as a CPU controller, and used a
mock missing its solid-execution registry. These fixture repairs preserved real
no-CPU assertions and native allocation semantics. R12 ran all23 exact domain
classes:268 tests,3failures,0errors,0skips. Its remaining fixtures manually
published invisible EMZ visibility and used the same invalid allocation boundary;
R14 covers all three repaired cases. R13 stopped in compilation (55.979s) because
the new feature provider omitted its required no-extra-art cache method.

R14 completed205 tests across17 exact classes with0 failures/errors/skips,
after263s queue waiting and87s execution. It covered all changed failed R12
fixtures, the real Host15 cases, damage12, all5 palette owners11, dust10,
pause17, pattern9, planes12, sprite pages7, player21, session11, binding3,
load profile8, arena29, HUD14, palette/pattern lifecycle14 and retained-cache12.
It used Java25, Lua5.4, the absolute supplied S3K ROM and display-free
surfaceless-EGL flags through the shared Maven queue. The checks are logic/asset
and ownership tests: no child renderer/whole-arena visual qualification is claimed.

Exact R14 selector:
`TestNativePaletteOwnerReplacement,TestNativePatternOwnerReplacement,TestS3kCompetitionPaletteAnimator,TestS3kMiniatureDust,TestS3kCompetitionPatternAnimator,TestS3kCompetitionNativeHost,TestS3kCompetitionPause,TestS3kCompetitionArenaObjects,TestS3kCompetitionHud,TestS3kCompetitionDamage,TestS3kCompetitionSession,TestS3kCompetitionPlanes,TestS3kCompetitionLevelInitProfile,TestRetainedPlaneDescriptors,TestS3kCompetitionPlayer,TestS3kCompetitionBinding,TestS3kCompetitionSpritePages`.

The next affected selection is deliberately seven classes, reusing unchanged R14
coverage: `TestSharedSpillAnimationPolicy,TestRingManagerRewindSnapshot,TestNativeParticipantLostRingOwnership,TestNativeCompetitionIteration,TestS3kCompetitionNativeHost,TestS3kCompetitionScreen,TestS3kCompetitionPatternAnimator`.
R15 stopped in test compilation before any execution after76s: its new inert
ring-policy fixture omitted the required render-command override. The only repair
was that empty fixture method. R16 reran the same seven selectors and passed79
cases with0 failures/errors/skips after9s queue waiting and31.606s execution:
native iteration8, actual Host18, pattern10, Screen15, shared spill policy7,
participant ownership8 and ring snapshots13. Host coverage includes17 frozen
pause waits with restore/forward, previous-P1 versus P2 input, the separate resume
boundary, inherited-fade retirement and actual TA-B ordinary-body-before-selection.
The semantic ring seam is separately committed as `e3ccf2040`. Unchanged classes
reuse their exact R14 or earlier affected passing executions; no all23 repeat
or broad engine suite was launched.

| Arena | Native placement count | Objects, palette, retained-plane source checks | Remaining acceptance |
| --- | ---: | --- | --- |
| Azure Lake |8| Implemented; actual Host/ROM terrain/timer/touch/restore checks | Root concrete view and complete supported route/finish qualification |
| Balloon Park |15| Implemented; actual GP reload, all descriptor words and palette cycles checked | Full player/object traversal, root view and finish qualification |
| Desert Palace |17| Implemented; both-camera tile DMA phases, descriptor words and native objects checked | Full player/object traversal, root view and finish qualification |
| Chrome Gadget |15| Implemented; descriptor words, palette cycles and native objects checked | Full player/object traversal, root view and finish qualification |
| Endless Mine |15| Implemented; descriptor words, palette/button ordering and native objects checked | Full player/object traversal, root view and finish qualification |

All rows remain unactivated. Native audio pause integration, parent view/route
qualification and the parent-coordinated required S3K boot/loading/resolver/decoder
guards remain outside this child's completed evidence. Direct goal-crossing,
full-RAM and rewind checks are meaningful behavior controls, not complete arena
traversal proof. No broad smoke/full suite, main mutation, push, PR or cleanup of
the owned worktree has been performed.


## Counted actor Draw calls at the canonical queue reset

The parent's actual EGL arena consumer exposed a missing normal-frame actor after
90 world steps. In `98bead812`, `nativePlayerPass` submitted P1/P2 Draw calls
before `ObjectManager.update` entered its canonical `beginNativeSpriteFrame`.
That reset discarded the actor calls. Initial assembly had passed because
`beginInitialSolidExecution` resets the queue before the miniature pass instead.
The existing initial-only admission assertions did not cover this counted-frame
boundary; the earlier focused passes must not be read as proof of it.

The minimal correction preserves actual calls at the existing post-player-tick,
pre-touch point as ordered raw slot/requested-bucket intents. The normal pass
emits those intents immediately after the queue resets and before external/dynamic
SST execution. Initial assembly explicitly emits them after its actor pass and
before dynamic slots. Neither path derives calls from later visibility, end-of-
frame blink timers or an earlier completed queue. The intent list contains no
actor references, clears before emission, and clears on load, roster rebind and
both initial/counted failure exits. Completed queue admissions and physical/uploaded
sprite pages retain their existing separate rewind ownership.

A direct replacement of `ExternalSlotDispatch` was rejected after reading the
parent's incoming `RingSlotDispatch`: rings own that single callback and rebind
it at construction/reset/restore. Instead, the approved independent prefix lives
inside `NativeSpriteSubmissionQueue`. Its `beginFrame` resets admission then
runs the prefix; ObjectManager's unchanged caller next invokes its original
external begin hook. `clear` unbinds the prefix; queue restore neither invokes nor
restores callback identity. State shape, ObjectManager, walkers, allocator,
capacity and spill limits are unchanged. The Host binds after its explicit object
reset; an existing fresh-load RingManager reset reinstalls the ring owner's
independent resolver/dispatcher that the object reset cleared, without advancing
or resetting the native three-word spill-animation bank.

The affected focused selection is
`TestNativeSpritePassPrefix,TestObjectManagerNativeSpriteSubmissions,TestS3kCompetitionNativeHost,TestS3kCompetitionSpritePages,TestS3kCompetitionSpriteSubmissions`.
The new controls cover queue-reset/prefix/external/dynamic order in both walkers,
duplicates and native pressure, reset/resync/rebind, queue snapshot restore with
current callback identity and callback failure recovery. The real-ROM Host checks
assert exact actor calls on every one of90 counted passes, uploaded P1 presence,
blink read-before-decrement, equal-character view flags, initial/full-RAM/GP reload,
actor recreation plus queue/page restore-forward, and failures before and at the
queue boundary. No view/test fallback, GPU source copy, API change to Host Views
or SpritePages, renderer qualification or native-audio claim accompanies this fix.
R18 passed79 tests with0 failures/errors/skips after643s queue waiting and81s
Maven execution: actual Host23, SpritePages10, domain submissions3, new prefix10
and existing queue33. The90-frame check reached the actual uploaded native P1
piece; initial-only assertions are no longer its proxy. The tested state was
`98bead812` plus these two corrective leaves. Independent bounded source review
found no stale-intent/order/restore/failure defect. Incoming RingSlotDispatch
rebinding was inspected read-only in the parent's source; that implementation is
not imported into this child, so its composed integration and the actual EGL
rerun remain parent-owned. ObjectManager has zero changes in both leaves.
