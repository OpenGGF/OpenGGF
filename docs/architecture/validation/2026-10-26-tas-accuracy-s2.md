# S2 TAS accuracy repairs — 2026-10-26

Scope: `arz-endpoint-routine-add` (`s2-boss-optimization:2`) and
`hurt-launcher` (`s2-oil-speed-corruption:1`, `s2-ring-attack:3`). Base:
`58e1abb44c716415bf308cc591776d2429c85a41`. Implementation worktree:
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-e1b6e58b`, local branch
`bugfix/ai-tas-accuracy-s2-20261026`. Parent owns combined validation,
central classification/documentation and integration. This record supplements
[the supportability assessment](2026-10-26-tas-supportability.md) and
[the original S2 verdicts](2026-10-26-tas-verdicts-s2.md).

## Native contracts

Stock source is the clean pinned S2 disassembly
`34fc7f130a96db171e1a1c43e28e0bad81cd6ca7`, shipped `fixBugs=0`.
The immutable KiS2 source inspected is `c336fede00092017f48c9d7851e0d3728c4fae2b`,
preserved externally with the original verdict evidence. Source establishes the
expected instruction order; executed tests below establish the reached engine
mechanisms. No source-only citation certifies a published TAS route.

### ARZ endpoint

`Obj89_Main_Sub2` moves, calls `HandleFace` (which can observe the last hit and
select defeat routine 8), then aligns parts. Both travel endpoints enter
`Obj89_Main_Sub2_AtTarget` (s2.asm:65070-65072), which adds 2 to the live routine.
Thus ordinary endpoints select 4, but a last hit at the endpoint selects A.
`Obj89_Main_KillBoss` (s2.asm:65237-65250) awards 1000 points, writes countdown
$B3 and publishes capsule PLC before that add.

The skipped explosion routine also skips its escape setup and its native
`boss_defeated` write; the hammer remains attached. The abstract health/score
defeat flag is therefore distinct from whether the explosion phase executed. `Obj89_Main_SubA`
(s2.asm:65334-65383) increments the still-positive $B3 to $B4 and immediately
selects C. Animal/explosion art and level music belong exclusively to equality
with $18; an eventual-greater-than retry must not invent a publication on this
skip path. Ordinary defeat instead runs 180 explosion updates and 50 A updates.
`Obj89_Main_SubC` (s2.asm:65388-65416) moves at (+$400,-$40), widens camera max X
by two to $2C00, then deletes after leaving view.

### Hurt launcher

`loc_252F0` (s2.asm:51309-51376) rejects player routines >=6 and CPU flight
routine 4, accepts alive hurt routine 4, writes interact/obj_control=$81,
roll animation, inertia=$1000 and zero x/y velocity. The optional `fixBugs`
routine-2 write is deliberately absent in the shipped branch. Neither
`loc_253C6` (s2.asm:51382-51405) nor the chained movement routine tests hurt.
`Obj48_MoveCharacter` (s2.asm:51476-51487) reads live player velocities.

`Obj01_Hurt_Normal` (s2.asm:38173-38192) ignores obj_control: move, add $30
gravity, collide, bound. Therefore a still-hurt chained player can move once in
the player slot and again in the ball slot. This is ordinary native owner
composition, requiring neither a multiplier nor RAM corruption emulation.
`loc_25408` (s2.asm:51409-51435) explicitly writes player routine 2 only for
negative-subtype exit balls. Offscreen release in `loc_25474`
(s2.asm:51443-51450) clears control/on_object without clearing hurt.

KiS2 has the same admission, live movement and exit owners in its pinned
`loc_252F0` (KiS2 source lines 54587-54650), `loc_25408` (KiS2 source lines 54681-54707),
`Obj48_MoveCharacter` (KiS2 source lines 54748-54761), and `Obj01_Hurt_Normal`
(KiS2 source lines 41036-41054). These KiS2 line references refer to the external immutable
KiS2 source, not the stock S2 submodule.

The previous ball is selected only through the player's actual native interact
owner while on_object is set. Non-launcher stale-slot cross-writes and the named
S2 Sideways exception remain outside this repair. The existing player contact
identity and ObjectManager/SpriteManager restore relinking provide that owner;
a separate static capture registry cannot survive reconstruction faithfully.

## Verification scope and results

Focused checks use this exact common command, with `SELECTOR` replaced by
the literal selection recorded per run below. To satisfy the repository's
resource policy, `$HOME` denotes the executing user's home directory in recorded
ROM paths; the original invocations passed those same expanded absolute paths.
The first record commit was rejected for literal machine-local home paths, then
corrected to this lossless substitution without changing any executable input:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=SELECTOR' -Dsonic2.rom.path=$HOME/code/projects/OpenGGF/s2.gen -Dkis2.rom.path=$HOME/code/projects/OpenGGF/kis2.gen test
```

R0 and R1 selection:
`TestTasS2ArzBossEndpoint,TestTasS2HurtLauncher,TestTasS2CurrentLimitations#hurtLauncherAdmitsNativeRoutineFourEntry`.
Runtime source remains the base commit for both.

- R0: no tests executed. Fixture compilation rejected the private
  `SpriteManager.removeSprite(Sprite)` overload. Corrected to the public
  `removeSprite(String)` API. Queue wait 349 seconds, Maven execution 1:24.
- R1: **24 tests / 12 failures / 0 errors / 0 skips**. Queue wait 706 seconds,
  Maven execution 31.533 seconds. Native endpoint A/current 4 failed in both
  left/right transition cases and both endpoint continuation cases. Hurt
  admission failed in both existing rule variants and four new hurt sequence
  cases. The two new normal controls passed restoration and forward replay,
  then failed native transfer from the restored ball: the static registry
  cleared the discarded instance instead. ARZ ordinary/approach controls,
  ordinary defeat/escape/replay, and dead/debug admission controls passed.
- G0/G1: focused repaired selection adds `--lean` (bounded component and graph
  tests with no route replay) and uses
  `TestTasS2ArzBossEndpoint,TestTasS2HurtLauncher,TestTasS2CurrentLimitations#hurtLauncherAdmitsNativeRoutineFourEntry,TestLauncherBallObjectInstance,TestS2LauncherGraphRewind,TestRewindSchemaRegistry#exactDefaultObjectPolicyCapturesPlayerReferenceLinks,TestScalarOnlyCodecDeletion#s2LauncherGraphRecreateClassesImplementRewindRecreatable+s2LauncherGraphRecreateClassesRoundTripPassedWithoutCodec,TestSonic2ArzBossPaletteFlash,TestSonic2ArzBossPlcReadiness,TestS2ArzArrowGraphRewind`.
  ARZ continuation now also checks its native hammer flag and executes an actual
  capsule landing, animals and results from a separately declared post-defeat
  approach using the acquired camera limits. No full approach/fight route is
  claimed.
  G0 stopped at a missing `EggPrisonAnimalInstance` import in that added capsule
  fixture: no tests executed. Queue wait 58 seconds, Maven execution 1:49.
  G1 fixes the import and adds a control for native offscreen release after a
  declared on_object-clear inlet transfers interact to another real ball. The
  old ball must still write this player's control byte; engine unload cleanup
  separately protects a later owner. That declared inlet does not certify a
  stacking route. G1: **51 tests / 0 failures / 0 errors / 0 skips**, queue wait
  82 seconds, Maven execution 2:02. Completed on base plus the ARZ/launcher runtime
  and test changes described above; only an existing stale Obj48 header citation
  was corrected afterward (no executable change). The runtime/source commit is
  recorded at final handoff below.
- G2: final focused rewind strengthening used the same command with `--lean`
  and selection `TestTasS2ArzBossEndpoint,TestTasS2HurtLauncher`. Runtime unchanged
  from G1; the two test classes additionally restore release before replaying
  launcher cooldown and restore ARZ at escape entry with both reached values of
  the hammer flag. Camera is included in the ARZ snapshot comparison. Unchanged
  graph/palette/PLC tests were not repeated. **24 tests / 2 failures / 0 errors /
  0 skips**, queue wait 9 seconds, Maven execution 1:49. Launcher twelve cases
  and both endpoint tails passed. Both ordinary tails failed the strict restored
  ObjectManager snapshot: active `BossExplosionObjectInstance` spawn.objectId
  changed from $58 to 0. The old coordinate-plus-zero-scalar marker selected its
  longest constructor with zero object ID; no later scalar field owns that spawn
  value. This was an existing generic recreation defect exposed by the added
  checkpoint, not a native gameplay mismatch. The direct boss dispatch also
  left its spawned explosions frozen; the final fixture uses ObjectManager
  dispatch for their actual animation, retirement and slot lifetime.
- G3: focused validation of the authorized explosion recreation repair passed:
  **25 tests / 0 failures / 0 errors / 0 skips**, queue wait 412 seconds, Maven
  execution 2:11, completed 2026-10-26 20:01:31 UTC.
  Common command adds `--lean` and
  `-Dsonic1.rom.path=$HOME/code/projects/OpenGGF/s1.gen`; selection:
  `TestTasS2ArzBossEndpoint,TestBossExplosionRewind,TestSpawnRewindRecreatableCleanup#spawnCoordinateZeroScalarArgsRecreatorsUseMarkerInterface+spawnCoordinateZeroScalarArgsRecreatorsDoNotDeclareRecreateForRewind+spawnTrailingZeroIntsRecreatorsUseMarkerInterface+spawnTrailingZeroIntsRecreatorsDoNotDeclareRecreateForRewind,TestScalarOnlyCodecDeletion#batch26ClassesAllImplementRewindRecreatable+batch26ClassesGenericRecreateProducesInstance+batch26ClassesRoundTripPassedWithoutCodec+batch39ClassesAllImplementRewindRecreatable+batch39ClassesGenericRecreateProducesInstance+batch39ClassesRoundTripPassedThroughObjectManagerSessionPath`.
  The exact marker-list guards and S1/S2 generic recreation/codec-deletion
  methods remain intact. The new narrow regression uses real fresh recreation
  for native IDs $58, $3F and the existing zero-ID constructor, compares every
  registered snapshot channel, and advances the restored animation to retirement
  without repeating its sound. No marker implementation or other object changed. The ordinary ARZ graph
  creates 22 effects and naturally retains one at escape entry; its $58 identity
  survives the immediate full snapshot comparison and continued forward replay.
  G3 executes 12 ARZ cases, 3 explosion regressions, 4 exact marker guards and
  6 exact S1/S2 recreation/codec-deletion methods.

No broad suite is delegated. Completed test summaries were inspected for skips.
The authored component world reports expected absent FAST-load manifests,
dynamic capsule placement not found in the layout list, and absent stage-clear
audio service. These do not abort the gameplay assertions; hardware load timing,
layout respawn bookkeeping and audible stage-clear output are outside this
fixture's claims. Existing compilation deprecation warnings were not changed.
Original ROM identity checked locally: S2 SHA-1
`8bca5dcef1af3e00098666fd892dc1c2a76333f9`, CRC32 `7B905383`; KiS2 SHA-1
`6cd0537a3aee0e012bb86d5837ddff9342595004`, CRC32 `2AC1E7C6`.
The launcher component tests select both actual Sonic and KiS2-profile Knuckles
movement owners; they do not certify cartridge/profile loading or a ring route.

The queued version probe reported Java `25.0.5.0.2` after a 327-second admission
wait; Lua reported `5.4.9`. The containing shell later returned 2 for a mistaken
read of `game/rewind/RewindCaptureContext.java` (the actual path is under
`game/rewind/schema/`); that was a diagnostic-path error, not a test result.

Renamed capture-bearing test IDs for parent reel reconciliation:

- `TestTasS2ArzBossEndpoint#currentLimitationEndpointFinalHitRestartsAttackInsteadOfSkippingExplosions`
  → `#endpointAddsToLiveRoutineAfterFinalTouch` (eight left/right, approach/endpoint,
  last/nonlethal-hit cases).
- `TestTasS2CurrentLimitations#currentLimitationHurtLauncherRejectsNativeRoutineFourEntry`
  → `#hurtLauncherAdmitsNativeRoutineFourEntry` (S2/KiS2).

Existing reel hooks remain in those methods; their outcome labels now describe
the corrected native behavior. A repaired inlet and double movement do not
prove repeated unbounded stacking, terrain recovery routes, buried-ring approach
or ring collection. Those family-level conclusions remain explicitly limited.


## Delivered behavior and coverage limits

ARZ tests execute actual final/nonlethal touch response at both travel endpoints
and on the approach. Endpoint death selects A with countdown $B3, then C on the
next update ($B4); approach death retains 180 explosion updates and 50 settle
updates. Both paths award 1000 once, publish capsule PLC, release camera max X
to $2C00 and retire the actual boss. Ordinary effects now receive the same manager
dispatch as their boss: 22 are created, and earlier effects retire before escape.
Only the ordinary path submits animal and
explosion PLCs and drops the hammer. The pre-final-touch snapshot is restored
into fresh boss/child instances and replayed through escape, with all registered
snapshot channels compared. A second restore at escape entry covers both
values of the native hammer flag and the camera through forward replay.
Each of the four continuation cases then executes
real capsule button contact, animal production and results from an explicitly
declared post-defeat approach over authored terrain. This does not measure a
whole-fight saving, play the route between boss and capsule, or certify ROM art
publication/pixels; the PLC service is observed at its command boundary.

The launcher tests reach damage through `applyHurtOrDeath`, keep hurt through all
49 opening updates, launch with native velocities, then observe 32 pixels per
hurt frame versus 16 for normal control: two native owner additions, with the
second consuming the new $30 gravity velocity. Both actual Sonic and
KiS2-profile Knuckles execute this path. A second real ball transfers native
interact and releases through a negative-subtype exit, explicitly ending hurt;
its eight cooldown updates complete and replay identically from the release
snapshot. Pre-hurt, captured, transferred and release
checkpoints use real sprite/object snapshot adapters and production interact
relinking, followed by full registered-channel forward comparison. Offscreen
release preserves hurt; real manager deletion and player retirement clear the
owned launcher state. No static ball capture registry remains. Dead/debug and
CPU-flying rejection, airborne normal CPU admission, unique participant dispatch,
legacy graph recreation, and ARZ palette/PLC readiness controls also pass.

An extra declared-state control proves that on_object-clear admission does not
clear an old ball and that its later offscreen routine still writes the player
unconditionally. That is a bounded native branch check, not executed proof of
the terrain route producing simultaneous drivers. Full repeated stacking,
buried-ring approach/collection, OOZ entry-launcher bridge reconstruction,
multiplayer shared animation/cooldown edge cases, and expanded viewport/donor
breadth remain inherited gaps. No shared movement, rule, collision, Sideways or
trace owner was changed. Parent owns the act-matrix/backlog and family verdict
updates, combined smoke/domain validation and integration.

Final review removed the proposed ownership guard from native offscreen release:
`loc_25474` writes the player even when interact has changed. Ownership protection
belongs only to engine unload cleanup. Removing the obsolete cached-velocity map
also removed its schema entry and its old field-specific graph assertions;
actual player-velocity and reconstruction checks replace that obsolete owner.
The existing codec-deletion tests required no edits. The newly reached
explosion restore defect is corrected by the existing spawn-preserving
`SpawnTrailingZeroIntsRewindRecreatable` marker and a private captured-spawn
constructor. Public creation paths and animation behavior remain unchanged;
the captured sound/priority scalars are restored by the existing schema. Its
exact marker guard entry moves with the owner rather than dropping enforcement.

Static checks: `git diff --check` passed. The focused citation command was
`python3 tools/disasm/disasm_citations.py check src/main/java/com/openggf/game/sonic2/objects/LauncherBallObjectInstance.java src/main/java/com/openggf/game/sonic2/objects/bosses/Sonic2ARZBossInstance.java src/main/java/com/openggf/level/objects/boss/BossExplosionObjectInstance.java docs/architecture/validation/2026-10-26-tas-accuracy-s2.md --limit 8`.
It exited 0 with `near=4, ok=3, unverifiable=13`, no stale references. Bare
assembly names and external KiS2 references are not all resolvable by that tool;
its successful exit is not independent native execution evidence. Root
AGENTS/CLAUDE and the underflow method in `TestTasS2CurrentLimitations` remain
unchanged. Central changelog/discrepancy/matrix updates belong to the parent.

Final tested source SHA: `1748703ba5440ec0af8fb642da81cb61bb62f43a`. G3 ran the exact executable
source/test contents committed there; this subsequent record-only commit changes
no executable inputs. G1/G2 selections above cover unchanged owners separately.
Both commits use installed hooks, all seven justified policy trailers, and the
final model-version coauthor block verified with `git interpret-trailers --parse`.
No live validation jobs or retained task diagnostic logs remain. The worktree
and local branch are retained for parent review/integration; no push is authorized
in this workstream.
