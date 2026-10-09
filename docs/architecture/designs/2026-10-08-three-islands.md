# Three Islands

Task: design and deliver a story-based JRPG mod reusing as much of the original games as
possible, with turn-based combat. The user chose the "Three Islands" concept (Sonic 1, Sonic 2
and S3K islands linked by Warp Rings) and asked for **full-wait** turn-based combat instead of
active time battle, on a feature branch in the main checkout (no worktree), installed into the
local instance, committed and pushed. Integration base: `d4993a7307` (`develop`). Branch:
`feature/ai-three-islands`.

## Shape

One S3K startup scene (`examples/three-islands`), registered through `ModContext` with the
400-pixel display. There are no engine, API, stock zone, physics, timing or audio-driver
changes. Additional ROMs come from `SceneArt.rom("s1"/"s2")`; Sonic 1 and Sonic 2 zone music
comes from `SceneMusic`. Because a scene world never mutates an engine `Level`, the stock
zone/act test matrix does not apply; the mod carries its own rules and scene tests.

- `core/`: engine-free rules. `Battle` queues every living fighter by speed each round and
  stops on a hero's turn (`Phase.CHOOSE`). `advance()` is a no-op until `submit()` resolves,
  which is the full-wait contract. Techs require every member alive and still queued this
  round, then remove the partners from the queue and spend EP from each. Enemy AI patterns
  are basic, sweep, flurry, charger (telegraphed party-wide blow) and rival (Knuckles).
- `field/`: `FieldPath` walks an act's level-kit collision; `Field` places badniks, monitors,
  Starposts and bosses deterministically per zone; `Stage` draws the kit and backdrop.
- `art/`, `audio/`, `screen/`, `view/`: ROM sprite requests and palettes, hero poses from
  each ROM's animation scripts, multi-part S3K bosses, a case-sensitive original font,
  and the audio router.

Story, emeralds and joins: Tails joins after `west-arrive`, Knuckles after the Angel Island
rivalry. Seven zone bosses return the seven emeralds; with all seven and 50 rings Sonic can
turn Super (10 rings a turn, untouchable, double attack and defence). A chapter whose ROM is
missing is skipped consistently: zones cleared, emeralds handed over, levels raised.

## Decisions and rejected approaches

- **ATB rejected** by the user in favour of full wait. The battle never advances while a
  command is pending; `BattleTest.theBattleWaitsCompletelyForEachHeroCommand` repeats
  `advance()` 50 times during a pending turn and checks nothing moved.
- **Scene-only rather than a level-to-battle handoff.** Stock levels cannot hand over to a
  scene and back without new API work, so fields are scene renderings of each act's level
  kit and battles are fought on the same stage where the party stood.
- **Route finder iterations** (each judged from real GL captures of all ten acts and the
  `routestats` debug jump):
  1. A step/short-drop search window alone left the walker floating at its old height across
     deep drops and through tall walls. Air columns reached 3,741 of 6,131 in Marble Zone.
  2. Searching the whole column when nothing is in reach, plus quick 16-pixel-per-column hops
     for changes over 64 pixels, cut airborne columns to about 10% in most acts (Death Egg
     17%). Long eased ramps through scenery were rejected.
  3. Level kits' `playableArea()` is the starting camera boundary. Sonic 1's Marble Zone
     lowers it with level events, so routes were capped at the surface (path y never passed
     672). The route and camera now use the layout's full height.
- **Marble Zone replaced by Star Light Zone.** Even with the full height, Marble's kit
  rendered floors with no block art (the party stood in empty sky) and its background
  plane decoded as plain sky. Star Light had the cleanest Sonic 1 route statistics (about 10%
  airborne). The Bomb King boss and the Sonic 1 Orbinaut replaced Marble's Caterkiller boss.
- **Sonic 1/2 background planes** are single-band pictures up to 1024x512. Mapping camera
  height to the plane's rows showed only sky. They are now anchored by their trimmed
  content bottom, as Eggman's Sky does.
- **Sonic 1 Robotnik.** The address used by Sitar Hero (`Nem` $5E4CE with mappings $1A1E4)
  draws Robotnik on foot, not the Egg Mobile. Overlaying "face" frames was wrong; the Spring
  Yard boss is now a grounded single frame.
- **Music.** Scene-music synthesis measured about 4.5 s for a 60 s Sonic 1 or 2 song in
  isolation, and `start` needs a second background part render. Captures starve the worker
  (1% after 1,520 capture ticks). The audio router therefore plays an S3K driver "stand-in"
  at once (the island's map theme) and switches to the stock song when ready; the loading
  card waits up to four seconds. Songs were cut from two minutes to one to halve the wait.
  A bug was found and fixed: a READY part preparation must be published with
  `prepared()` before `start()`, which otherwise throws "music preparation must finish
  before starting". `TestThreeIslandsScene.aSonic1ZonePlaysItsOwnRomSongAfterTheStandIn`
  covers it.
- **Balance** was first tuned on a `1 + 1.6 * tier` level estimate. An XP model showed real
  levels run higher (about 3 at the Green Hill boss and 20 by the end), so each zone now
  declares the party level it is balanced for. Ordinary foe HP scales 100/165/260% and boss
  HP 50/52/130% for one, two and three heroes, because Knuckles and the Trinity Rush lift a
  trio far above a duo. A scripted player then wins every zone's fights in 2–4.5 rounds and
  every boss in 5–10 rounds at the zone's level (`BalanceTest`). Before tuning, Sonic alone
  won 13 of 60 Star Light fights, and bosses with a duo took 11–15 rounds.
- **Validator.** The creator validator accepts enums only when constructors copy literal
  scalar or String arguments, and no static arrays. The content tables store elements as
  ordinals, and the title's sky list is an instance field. The jar validates with zero findings.

## Validation and its limits

Tool setup: Java 21; Maven is the IDE's bundled 3.9.16, put on PATH for the queue. The
change touches only an isolated creator scene, its two engine test bridges and docs, so
proportionate focused validation applies; this is not a full-suite pass.

```sh
python3 examples/build_example.py three-islands --skip-engine
python3 tools/testing/maven_queue.py -B -q -Dmse=off -DskipTests test-compile dependency:build-classpath \
  -Dmdep.outputFile=target/test-classpath.txt -Dmdep.includeScope=test
python3 tools/testing/maven_queue.py -B -Dmse=off '-Dtest=TestThreeIslandsExample,TestThreeIslandsScene' \
  '-Ds3k.rom.path=<root S3K ROM>' '-Dsonic1.rom.path=<root S1 ROM>' '-Dsonic2.rom.path=<root S2 ROM>' test
```

`TestThreeIslandsExample` packages and validates the mod (zero findings, `WIDE_16_9`) and runs
the 35-test creator suite. `TestThreeIslandsScene` runs four ROM-backed scene tests:

- a new game through the story, map and village save, then a zone's loading card into a battle;
- every zone's terrain and boss (10 of 10 zones with all three ROMs configured);
- a keyboard-fought battle;
- the Sonic 1 song taking over from the stand-in.

Because two engine test classes were added, the structural guard profile also ran
(`maven_queue.py -Dmse=off -Pguards test -B`): 674 tests, 673 passed. The one error,
`TestTraceChaserBoundaryGuard.powershellForwarderRejectsSymlinkAncestorWithoutExecutingOutside`,
cannot launch `pwsh`, which is not installed in this environment; it is unrelated to this change.
The final focused run above passed 6/6 engine tests on the delivered commit's code.

The installed jar is the same validator-checked build (`build.py --skip-engine --install`);
installation enables and trusts that exact hash while keeping other mods' entries. Local
runtime state (`mods/`) is not committed.

GL captures with `ExampleModCapture` (S3K ROM SHA-1 `b711a909cce238ca4af3e517a2edca306228efa5`, plus the
REV01 Sonic 1 and Sonic 2 ROMs) were inspected for the title, every zone's route at six
points, every boss, the map, village, shop, menu, story, battle menus, results, game over and
ending. Raw captures were working material and are not kept.

Not covered: a full unassisted playthrough to the ending at real speed, real-device audio
latency, the native build (code mods do not load there) and the full engine suite.

## 2026-10-09: exploration and narrative revision

User feedback: the single collision-following route played like a linear side-scroller,
not the intended story-led JRPG, and battles felt too hard. Revision base:
`38def484c14fb9094e54d55e758ed0e2c84d5bfc`, current checkout
`feature/ai-three-islands`. The original delivery notes above describe the earlier design.

### What changed

- Replaced runtime field traversal with a 960x640 two-dimensional area. North, south and
  central routes reconnect; a lake/shaft, bridge, groves/ruins and perimeter share explicit
  collision with navigation. Diagonal speed is normalised, followers remember both axes,
  and formation placement avoids piling everyone onto the leader at checkpoints.
- Encounters use two-dimensional distance. Ordinary patrols can be avoided; conversations,
  caches, Starposts and bosses require an explicit interaction. Bosses require two story
  discoveries, in either order, plus any zone midboss. Camera, depth sorting, minimap,
  location labels, interaction prompts and region palettes replace the old progress strip.
- Added forty story scenes: two discoveries, a traveller conversation and a camp moment per
  zone. The arc now follows the displaced islanders, Tails' unanswered signal, Eggman's
  forged warning to Knuckles, and the party's plan to separate the islands safely. Traveller
  responses acknowledge discoveries. The menu journal replays discovered evidence and gives
  directions to the remaining landmarks.
- Discovery milestones catch underlevelled heroes up to the chapter level (the second clue
  reaches one level above it), restore the party and save. Optional battles therefore aren't
  required for levelling. Starposts offer repeatable free rest. Completed field content uses
  namespaced existing save flags, preserving save format 1. Checkpoint values 1/2 identify
  the new camps; old horizontal coordinates resume at the entrance camp without discarding
  party/chapter progress or guessing which new discoveries have been completed.
- Ordinary escape is reliable. Enemy attack is 80% of its previous formula and random enemy
  critical hits are removed; telegraphed attacks, guarding and all full-wait rules remain.
  Victory grants XP to fallen heroes too, restores everyone to at least 60% HP and returns
  20% maximum EP (minimum 2). Starting medicine is increased. Encounter level is capped at
  the lesser of the chapter level and party level + 1.
- ROM level kits and the old collision route remain **battle scenery only**. Field terrain
  is original geometry rendered by the scene; sprites and music still come from the ROMs.
  The creator mod's engine/API scope is unchanged.

### Decisions and evidence

The side-on route was rejected for exploration rather than patched with more horizontal
stops: its contact test had only an X coordinate, making fights effectively unavoidable.
The new topology has a flood-fill regression that reaches every landmark in every zone
while excluding patrol contact radii, including their horizontal movement allowance.

The earlier battle simulation restored the party and supplied extra items before every
fight. That could not measure exploration attrition. A new test carries the same party,
HP/EP and bag through six ordinary encounters and the zone's boss chain. Its first failure
was Death Egg / Convergence Engine at seed 7: the setup omitted the seven emeralds that
normal chapter progression guarantees. Corrected that setup rather than weakening the
final boss to accommodate an impossible story state. The established 60-seed per-encounter
simulation reports 60/60 victories in each zone and against every boss, with ordinary fights
averaging 2.0–4.4 rounds and bosses 4.4–8.6 rounds. This is evidence for that scripted policy,
not a measured human win rate.

Native GL captures exposed companions sharing a position after a debug jump/checkpoint;
formation reset now lays a valid initial trail. Captures also checked the bridge, Green Hill,
Chemical Plant, Hydrocity, party menu and the new dialogue. The scene test exercises actual
vertical keyboard movement, explicit interaction, locked bosses, reverse discovery order,
save/Continue and the surviving Flame Craft gate. Separate rules tests cover legacy
checkpoints, both discovery orders, collision, escape, party recovery and beginner attacks.

### Validation scope

The change-based plan at the base above selected 3,068 ordinary engine classes plus guards
because `examples/three-islands` is unclassified; it also included a pre-existing untracked
Sonic 2 movie outside this task. Proportionate validation applies to this isolated creator
scene and its existing engine test bridge. No engine, API, physics, trace, build policy or
stock S3K implementation changed. The full engine suite and guards were not run for this
revision. The unrelated movie directory was left alone.

Commands (Java 21; absolute existing root ROM paths supplied):

```sh
python3 tools/testing/run_categories.py --base 38def484c14fb9094e54d55e758ed0e2c84d5bfc
python3 tools/testing/maven_queue.py -B -Dmse=off \
  '-Dtest=TestThreeIslandsExample,TestThreeIslandsScene' \
  '-Ds3k.rom.path=<root S3K ROM>' '-Dsonic1.rom.path=<root S1 ROM>' \
  '-Dsonic2.rom.path=<root S2 ROM>' test
python3 examples/three-islands/build.py --skip-engine --install
```

Final focused result on the working changes over the stated base: **40/40 creator rules
tests passed; 7/7 engine bridge tests passed (including five ROM scene tests), zero skips**,
all ten zones exercised. Maven completed successfully in 47.077 seconds. A final test-only correction made the
beginner simulation use the live screen's level cap; rerunning `maven_queue.py --lean -B
-Dmse=off -Dtest=TestThreeIslandsExample test` passed all 40 creator tests and both bridge
checks, zero skips, in 34.826 seconds. Production code was unchanged after the ROM scene run. The installed jar
matches the packaged jar and its enabled/trusted local state pins that exact hash. Packaging runs
through the production creator validator. Native rendering uses `ExampleModCapture` with
macOS's `-XstartOnFirstThread` and the established native graphics permissions. Captures
and raw Maven logs are temporary review material and are removed after inspection.

Remaining limitations: fields share a compact clearing topology with different materials,
landmark names and stories; a full set of individually designed towns/dungeons is future
work. ROM heroes still have side-facing poses. This revision does not certify a complete
unassisted human playthrough, real-device audio latency, or the full engine suite.


## 2026-10-09: continuous field correction

This supersedes the exploration revision above. User review rejected the remaining
level-selection/village loop and the generic Green Hill rendering. Adding two-dimensional
movement alone had not delivered the intended continuous, story-driven adventure.

The scene now starts or resumes directly on Sonic in the field. Walking triggers the
opening conversation; dialogue overlays the current scene. Physical west/east trails
connect regions and allow backtracking, including travel within an island before its
bosses are defeated. Specific story barriers control island crossings and the Angel
Island rival encounter. MapScreen and VillageScreen are removed. A merchant is a field
interaction whose shop returns to the same position. Tails joins during the West Island
arrival in the field. Legacy saves resume at a valid field checkpoint.

Battles retain the actual field instance and camera, hide the contacted patrol, and move
the combatants into nearby walkable formation positions. Victory returns to that same
field and position. Boss victories play their story there; they do not select another
level. Optional discoveries no longer gate ordinary bosses. The last boss on each island
still requires the preceding chapter threats to be resolved.

Green Hill is composed from decoded ROM grass, checkerboard cliff, palm, plant and water
fragments. The first composition stretched too much grass texture across the ground and
was visually noisy; native captures led to sparse grass accents over a ROM-palette ground
colour. Other regions use their own decoded ROM textures and backdrops. Terrain geometry
remains authored for overhead movement rather than copying the original side-scrolling
collision layout. No extracted art is bundled in the mod.

A story-only simulation, without optional fights or discoveries, exposed a Spring Yard
level deficit and attrition between Angel Island bosses. First chapter completions now
provide catch-up levels and modest medicine, and boss victories restore the party. Plain
Progress.clear remains a pure flag setter: putting rewards there changed HP while decoding
saves. The separate idempotent completeChapter operation owns the gameplay rewards.

### Final correction validation

Working changes over `38def484c14fb9094e54d55e758ed0e2c84d5bfc`, current checkout.
The queued focused Maven command documented above completed in 47.370 seconds:
**43/43 creator rules tests and 9/9 engine bridge tests passed, zero skips**. The bridge
includes seven ROM scene tests and exercised all ten zones. The production creator
validator reported zero findings. Coverage includes direct field startup, walking-triggered
dialogue, physical travel/backtracking, narrative barriers, encounter field/camera identity,
save/resume, optional discoveries, field recruitment, and merchant return position.
The story-only balance simulation covers twenty seeds through the complete chapter route.
These are scripted checks, not a measured human difficulty rating.

Native ExampleModCapture walkthroughs were inspected for the opening field, inline dialogue
and Motobug battle. The change-based plan still selects 3,068 ordinary classes plus guards;
the isolated creator scene and bridge justify focused validation as described above.
The full engine suite and guards were not run. Fields still share a compact clearing
topology; individually authored town and dungeon layouts remain a limitation. Original
side-facing character art also remains. Temporary probes and raw logs are removed after
inspection; no stock engine or public Mod API behavior changed.

The corrected package was installed with `build.py --skip-engine --install`; its SHA-256
matches the installed jar and the enabled/trusted local state. Final native field and
battle captures were inspected after installation; two curated preview images are kept
outside the repository, while capture build directories, probes and raw logs were removed.

## Area music continuity (2026-10-09)

At base `30398f3e679873a4620fdce1e9d2329b54d60eab`, every return from a driver
battle/jingle discarded the mod's prepared-song handle and requested an S3K island
stand-in. Even when the host could reuse the rendered ROM song, that introduced an
unrelated cue before the next poll restored the area music. Interior entries also
requested different music; loading independently requested the outdoor track.

The field now owns one zone theme outdoors and indoors. Loading keeps the source
cue until the destination field opens. One foreign-ROM preparation survives driver
interludes, including jobs still rendering when a battle starts; completion may
publish the song but cannot start it over the battle. Returning starts the prepared
song immediately. Changing foreign areas retires the previous preparation, keeping
memory bounded to the host's single song. Repeated field updates do not restart it.

The temporary stand-in approach is retired: its observable extra cue was the reported
inconsistency. An S3K fallback is now reserved for preparation/playback failure and
is stable until leaving that music context. Initial foreign-song preparation can be
silent; this does not implement prefetching, crossfades or seamless sample-position
resume. Combat returns and the finite one-minute render restart the same theme.
The existing host limitation that foreign playback masks driver SFX remains.

Validation uses the proportionate exception: the change-based plan selected 3,075
ordinary classes plus guards due to unclassified example paths (and pre-existing
expedition/fixture edits). No engine/API/driver code changes; creator audio transition
regressions, SDK packaging and actual ROM scene checks directly cover the consumers.
In the current checkout over the base above, the Java 21 queued command
`-Dmse=off -Dtest=TestThreeIslandsExample,TestThreeIslandsScene test`, with absolute
root S1/S2/S3K ROM properties, passed 63 nested creator tests and 13 outer tests
(packaging, creator launcher and 11 scene cases), no failures or skips, at 13:41 BST.
All ten zones and interiors were exercised, including the existing expedition edits.
A follow-up adds cached-start failure handling and a real shrine-entry music assertion;
its focused results are recorded below. These checks establish routing and host
playback lifecycle, not a physical-speaker listening test or full engine-suite pass.
The initial sandboxed Maven attempt could not acquire `.git/maven-admission.lock`;
the authorized elevated queue invocation completed normally.

Unrelated expedition work remains unstaged. The local installed jar includes that
existing working-tree content; the music commit contains only this task's edits.

The follow-up queued command selected `TestThreeIslandsExample` and
`TestThreeIslandsScene#aSonic1ZonePlaysItsOwnRomSongWithoutPlaceholderSwitches`,
with `-Dmse=off` and the same absolute S1/S3K paths. At 13:43 BST it passed all
64 nested creator tests (including ten audio regressions), packaging, and the
real-ROM music/doorway case: three outer tests, zero failures/errors/skips.
The final assertion enters the shrine through its actual door and confirms both
the requested and playing song remain `s1:81`. The failure regression verifies an
unavailable cached playback selects the fallback without faulting the scene.
