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

The third command is the final affected check. Asset, miniature, arena,
dispatch and record implementations had not changed since their respective
passing executions. Whitespace and disassembly-citation checks also passed.
