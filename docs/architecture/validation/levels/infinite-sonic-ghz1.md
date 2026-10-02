# Infinite Sonic — GHZ1 solo Sonic prototype coverage

This mod replaces stock GHZ1 (registry zone 0, act 0, level ID `0x80`) only when
its explicit patch activates for solo Sonic. It is an endless terrain course, not a
new stock act. [Design and decisions](../../designs/2026-10-01-infinite-sonic.md).

| Contract | Evidence | Scope / gaps |
| --- | --- | --- |
| Real code package | `TestInfiniteSonic.compileAndValidate` compiles project sources and calls SDK `package` validation | No ROM payloads or baked assets shipped |
| Entry and traversal | `protectedTraversalPreservesEncountersAcrossRebaseAndReplay` uses explicit test-only invulnerability and holds Right for 6,000 frames, asserts no death and at least four world rebases | All five current `WidescreenAspect` presets: 320, 352, 400, 528, 800; resolved camera width asserted |
| Rewind at world recycling | Same test captures the full registry, runs 800 frames across recycling, restores and replays | Compares X/Y, fractional X, ground speed entire map, enemy state and occupied object slots; native donor/off; does not test GameLoop's interactive history recorder |
| Backtracking | Same test holds Left for 1,800 frames and proves reverse rebasing without death | Same five widths |
| Fresh reload / act isolation | `freshReloadResetsTheCourseAndOtherActsRemainStock` loads GHZ2, checks stock placements, reloads GHZ1 and checks original generated layout | 320px; physical death/respawn and live-history reset not yet covered |
| Character/team scope | `patchOnlyActivatesForSoloSonic` checks solo Sonic, solo Tails, Sonic+Tails and wrong-game selection | Other teams/characters use stock GHZ, by explicit activation policy |
| Install/registration | Local production scanner, state/trust handling and restricted classloader registration check | IntelliJ default project-root working directory; enabled local jar |
| Art and presentation | Reuses ROM pipeline; mirrored chunks and stock GHZ background | GPU visual review and background seam continuity remain open |
| Encounter habitats | `encountersAreSeededSpacedAndFitTheirEntirePatrolCorridor` checks 997 sections, both kinds, rest gaps and floor profiles against decoded collision | Fixed seed; ground relief and flying clearance checked across full patrol |
| Zero-ring challenge | `holdingRightWithNoRingsIsNowLethal` dies from enemy contact after the safe opening | All five widths; normal damage, bounded live slots; automatic respawn remains open |
| Attacks and explosions | `aPhysicalStompDestroysTheBadnikAwardsScoreAndDoesNotRespawnIt` exercises ground and air enemies | Real touch response from a positioned descending spin; score, no immediate respawn and explosion rewind/replay |
| Gameplay breadth | Terrain-aware bounded patrols, zero rings, paused timer, no finish | No missiles, rings, checkpoints, bosses or difficulty progression; donors unverified |

## Execution

Main checkout, `feature/ai-infinite-sonic`, base
`67c850fc5132156acede8687ca169ada074f795f`, uncommitted prototype, 2026-10-01:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
PATH=/usr/share/idea/plugins/maven-plugin/lib/maven3/bin:$PATH \
python3 tools/testing/maven_queue.py -q -Dmse=off -Dtest=TestInfiniteSonic \
  "-Dsonic1.rom.path=$PWD/Sonic The Hedgehog (W) (REV01) [!].gen" test
```

Completed: **7 tests, 0 failures, 0 errors, 0 skipped**, 13.59 seconds test time.
`build.py` also completed engine compilation and SDK package validation. Focused
validation only; no full ordinary/guard-suite or trace-parity claim.

The pre-existing ROM-load warning for S1 mappings at `0xE8DF` appeared during the
stock bootstrap as well as the mod load; this task does not alter object art.
The implementation and the passing assertions establish a playable terrain
prototype, not full compliance with the stock zone/act certification standard.

### Enemy follow-up

Base `ff18f7ffd45d192802441f3effa2d4d9fc2a279a`, uncommitted enemy changes in the
same checkout, 2026-10-01: **15 tests passed, 0 failures, 0 skipped**, 12.67 seconds.
Java 21 `javac --release 21` compiled the mod and `TestInfiniteSonic`; JUnit Platform
1.10.3 `LauncherFactory` selected that class using `target/classes`, the cached Maven
dependency classpath and the absolute World REV01 ROM path above. The final test run
includes all five traversal and damage widths plus both enemy stomp variants.

This was a direct focused JUnit run against cached unchanged engine classes, not a
completed Maven/category run: the queue could not write its admission lock because
`.git` became read-only. SDK package validation also passed for the installed 0.2.0 jar. The production scanner,
trust store and restricted classloader loaded it with two patch registrations and no
rejections or registration failures.
No full suite, structural guards, GPU review or automatic death/respawn claim.

### Queued Maven verification before commit

On 2026-10-02, filesystem permissions were restored. On the same base and enemy
changes, the queued Maven command from the original execution section was rerun
with Java 21 (also first on `PATH`). Surefire reports **15 tests, 0 failures,
0 errors, 0 skipped**, 11.47 seconds; Maven exited successfully. This supersedes
the cached-engine limitation for delivery. SDK packaging runs inside this test.
Validation remains focused; the full ordinary suite, structural guards and GPU
review were not run.
