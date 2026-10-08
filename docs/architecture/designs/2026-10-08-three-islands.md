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
