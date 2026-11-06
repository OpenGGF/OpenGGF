# Native-accuracy repairs for tested TAS mechanisms — 2026-10-26

## Scope and ownership

The user requested the low/medium repair groups from the [supportability assessment](2026-10-26-tas-supportability.md), only when they improve shipped-ROM accuracy. The integration base is `58e1abb44c716415bf308cc591776d2429c85a41`. High/very-high work, arbitrary RAM emulation, and new competition/standalone modes are excluded. Existing movie evidence remains comparison-only. All nine groups are implemented and qualified by the bounded checks below. Integration and cleanup status is recorded at delivery.

| Group | Owner | Native behavior and acceptance boundary |
| --- | --- | --- |
| unsigned-x-underflow | parent | Predicted X and bounds are unsigned 16-bit words; retain each game's right-edge equality rule and boss-lock extent. |
| soz-ground-response-axis | parent | SOZ decrements a negative response angle after the terrain probe and before quadrant rounding; no change to probe geometry. |
| offscreen-solid-dispatch | parent | S1 Obj56 omits its entire SolidObject call offscreen, preserving an existing ride until an admitted checkpoint releases it. |
| arz-endpoint-routine-add | S2 worker | Preserve live routine-byte addition, endpoint skip, and exact escape/PLC gates. |
| hurt-launcher | S2 worker | Admit routine 4 and use live player velocity/control ownership; preserve ordinary capture and rewind identity. |
| special-stage-entry-input-latch | S3K lifecycle worker | Boot consumes the last entry-fade sample without inventing a controller poll. |
| mhz-proxy-repeated-damage | S3K lifecycle worker | Separate parent collision admission and child collision-byte lifetime across real child destruction. |
| water-skim-slot-lifetime | S3K slots worker | Retire after participant exit and native range departure; preserve P2 and rewind. |
| cutscene-slot-six-alias | S3K slots worker | Model the explicitly armed native slot pointer, not a hardcoded/unarmed slot-six fallback; preserve sequence versus level reset. |

The parent owns shared physics, S1 Obj56, final classification, shared documentation, combined validation, integration, and cleanup. Workers own their game-local changes and focused evidence; their dated records carry exact selectors and outcomes.

## Rule placement

Unsigned word comparisons apply to all three games in the shared boundary owner. Existing semantic rules continue to select right-edge equality/extent. SOZ response-angle adjustment is supplied by an internal zone policy implemented by the S3K feature provider; shared collision code contains no game/zone-name branch, public mod API change, or trace lookup. S1 Obj56 uses the existing skipped-solid-routine contracts, including the corresponding batched continued-rider behavior. The FBlock prior-display and display-queued latches are captured by the existing scalar object rewind owner; the boundary and response policy are stateless.

## Parent native-expectation red

On unchanged production at the integration base, upgraded existing tests executed **10 cases: 6 failures, 0 errors, 0 skips**. All four ordinary controls passed. The six failures were S1/S2/KiS2/S3K unsigned underflow, S1 offscreen rider preservation, and the SOZ correction axis. Last reported queue wait was 331 seconds; Maven reported 1:24 execution. The initial shell redirect failed because target did not exist and ran no Maven; it was corrected before the single submitted red job.

Environment: Java 25 via `JAVA_HOME=/usr/lib/jvm/java-25-openjdk`, `LUA_BIN=/usr/bin/lua5.4`, DISPLAY and WAYLAND_DISPLAY unset, `-Dmse=off -Dopenggf.headless.gl=surfaceless-egl`, original absolute S1/S2/S3K ROM properties. Command selector:

```text
com.openggf.tests.TestTasS1TerrainVerdicts#horizontalUnderflowUsesUnsignedNativeRightBoundary,com.openggf.game.sonic2.TestTasS2CurrentLimitations#underflowClampsToUnsignedNativeFarRight,com.openggf.physics.TestTasS3kTerrainEntry#leftUnderflowUsesUnsignedRightBoundary,com.openggf.game.sonic1.objects.TestTasS1SlopeVerdict,com.openggf.tests.TestTasS3kFlameSlope
```

Invoked through `python3 tools/testing/maven_queue.py` with the selector as `-Dtest` and `test`. The flame trajectory oracle uses completed-player boundaries from the earlier native investigation, not raw emulator-frame snapshots. Negative X velocity is observed on logical ticks 36–41 (six updates), followed by a native wall stop; the exact tick-39 exit and tick-44 endpoint remain independent assertions. The prior tick-10 early-landing inference remains rejected.

## Delivery validation strategy

Focused owner checks and relevant rewind/normal-path controls precede combined validation. Inspect the change-based smoke plan against the pinned integration base, add semantic categories as needed, and run the four mandatory S3K gates. No release-only full suite. Historical capture/test identities and source hashes must remain historical when repaired tests are renamed; update current classification with new evidence rather than relabeling old recordings.

## Parent focused repair results

The first repaired selection expanded to **13 cases: 11 passed, 2 new control failures, 0 errors/skips** (Maven 1:24; last reported queue wait 693 seconds). Every original native parity assertion passed, including the exact SOZ correction/exit and two full registered-world restore/forward trajectories. Both failures were new S1 controls assuming jump/deletion immediately cleared OnObj. Immutable Sonic_Jump (01 Sonic.asm:1205–1264) sets InAir/clears Push without clearing OnObj; DeleteObject clears only the object's SST. The controls were corrected to those owners, not by changing production to satisfy an invented cleanup rule.

The next four-class control selection ran **18 cases: 16 passed, 2 fixture/expectation failures, 0 errors/skips**, Maven46.154s. All eight SOZ angle/zone/distance/Sonic/Tails controls and all three cross-game boundary vector/restore cases passed. The S1 width control initially selected subtype$30 (width32), not$70 (width64). The deletion control initially required the historical riding identity to vanish, contrary to the existing explicit ObjectManager removal contract; the valid assertions are absence from the live object walk and cleared object standing bit, with the player status/interaction identity retained. No production changes followed either correction.

Final affected S1 selection **7 passed, 0 failures/errors/skips**, Maven46.599s:

```text
com.openggf.game.sonic1.objects.TestTasS1SlopeVerdict,com.openggf.game.sonic1.objects.TestS1FloatingBlockRenderGate
```

The preceding control selection additionally included `com.openggf.sprites.managers.TestNativeLevelBoundaryWords` and `com.openggf.physics.TestGroundWallResponseAxis`. Both commands use the native-red environment above, `--lean`, and only the original S1 ROM property (the other component controls are ROM-free). S1 coverage includes visible release, actual jump retaining the native OnObj bit, object retirement, full registered-world replay twice, two native widths, fixed32 render height, initial/queued/completed display phases, and retained render-bit restoration. Downstream full slope routes, render-queue saturation and coordinate-wrap rendering are outside these checks.

The four modified citation-heavy production files have exactly the baseline59 stale and9 unresolved-file citations. A pinned-source parse/classification comparison found **zero new strict findings**; recognized exact matches increased57→60. Existing unrelated stale references were not rewritten.

The reel inventory resolver now accepts only explicit reviewed method renames, emits the current JUnit method identity and retains historical method names as provenance. Historical-source inventories keep their original identities; old recordings cannot match a renamed invocation implicitly. **21 Python recorder safety/timing tests pass**. The original full video remains a record of its pre-repair tested source.

## Validation selection and source ownership

The change-based plan against `58e1abb44` selects **4,226 smoke candidate
classes** plus guards because shared source paths are unclassified. That file-path
fallback is disproportionate to these bounded native branches. The chosen
replacement is the affected mechanism/ordinary-path/restore checks, fifteen
existing movement and skipped-solid consumers, affected structural/schema/API
checks in a fresh JVM, and the four mandatory S3K ROM gates. This is bounded
validation, not a full smoke or full-suite pass. Two independent reviews agreed
with this scope; one identified the MHZ body-only counterexample below. No
release-only full suite or broad baseline/development reruns were launched.

The actual launch environment passes `run_categories.py --base 58e1abb44 --preflight`
with Java25 and Lua5.4. An initial preflight without `LUA_BIN` selected the wrong
Lua version and ran no tests; the explicit environment corrected it.

Parent source and recorder resolver: `d2c3a8dd36aa456a533477202767a10191258e4c`.
S3K slot source: `7e0f20ef29b69a4eee499f89871ea66d1acab86e`; receiving merge
`2ea97b2849e235dfc3ee7e10afd4a8d2f2dfb4c0`. The worker's
[slot record](2026-10-26-tas-accuracy-s3k-slots.md) preserves the native red and
106 distinct final passing cases, including the corrected injected-services
fixture. Full bridge allocation history and HCZ boss-overwrite routes remain
outside those checks.

## Review counterexamples and corrections

The first lifecycle candidate `1926b380` passed its focused checks but a source
review found that direct routine-0 body contact also cleared the separate proxy.
Native touch changes only the struck slot; routine0 then restores parent HP and
collision while the untouched proxy suppresses the damage tail. A new early
body-only test reproduced HP2 instead of9 on `80f3359e8`. The follow-up keeps
pending collision/property/attacker state with the struck owner and defers flash
and attacker stopping to the native post-dispatch tail. This counterexample is
separate from the valid early-proxy positive case. The native body-only negative ran **4/1 failure/0 errors/0 skips** on
`80f3359e8`; the repair at `d65c78da54047515acb8f41ca8d40855dd1c17da`
passed **153/0/0/0** across the five affected MHZ classes (queue475s, Maven1:33).
The [lifecycle record](2026-10-26-tas-accuracy-s3k-lifecycle.md) preserves exact
commands. Its unchanged Blue Sphere source `1926b38052161dd0c4361a59fece36360689f75c`
retains the earlier 169-case combined focused pass, overlapping those MHZ checks.

The strengthened S2 ordinary escape-entry rewind check found a pre-existing
shared explosion reconstruction defect: `BossExplosionObjectInstance` used a
coordinate-only recreation constructor, losing native spawn ID$58 to0. The
bounded correction selects the existing captured-ObjectSpawn recreation
contract; ordinary public construction, animation and sound behavior are
unchanged. Strict object snapshots remain intact, and the ARZ continuation now
updates its actual object graph so explosion lifetime is not artificially held.
Final source is `1748703ba5440ec0af8fb642da81cb61bb62f43a`.
The [S2 record](2026-10-26-tas-accuracy-s2.md) records baseline24/12 failures,
repaired51/0/0/0, stronger24/2 failures exposing the old explosion identity,
and final25/0/0/0 (queue412s, Maven2:11). The actual ordinary graph creates22
effects; one naturally survives at escape entry and retains its ID through
immediate strict restore and forward replay. Launcher12 from the stronger
selection pass unchanged. Mock audio/load readiness and separately declared
capsule approach remain explicit limits.

The prospective supportability wording is corrected by implementation evidence:
S1 jump/delete do not promise OnObj cleanup; Blue Sphere boot-only input is too
late when a final-fade sample exists; the AIZ raw slot is armed by actual Knuckles
initialization, not hardcoded to6. These refinements improve native accuracy
rather than broadening compatibility by assumption.

## Classification mapping

The owner checks promote these twelve named assessments, preserving
all original publication IDs and historical negative results. No complete movie
or broader route acquires a verdict from a local repair.

| Scenario IDs | Native behavior now implemented | Scope retained |
| --- | --- | --- |
| `S1_LEVEL_WRAP:1`, `s2-horizontal-underflow:1`, `s3k-hwrap:1` | Unsigned predicted-X right clamp | Representative entries, fractional/equality vectors; not every wrap route. |
| `S1_SLOPE_GLITCH:1` | Offscreen Obj56 omits SolidObject and retains standing | Actual SBZ3 trigger; full shortcut and render-queue saturation not executed. |
| `s2-boss-optimization:2` | Endpoint increments live routine and skips explosion phase | Both endpoints and ordinary escape/PLC/capsule continuation; separate declared capsule approach. |
| `s2-oil-speed-corruption:1`, `s2-ring-attack:3` | Hurt-state launcher entry, native live-velocity continuation and recovery | Sonic/KiS2 opening, movement, transfer and exit; repeated stacking and buried-ring route not executed. |
| `s3k-sphere-timing:2` | Boot uses final-fade controller sample once | Neutral/pulse/held/deferred boot/first-mainloop and rewind controls; no optimal route claim. |
| `s3k-boss:2` | One early proxy contact repeats native damage after weather destruction | Body-only negative, ordinary flash/defeat and graph replay; exact publication approach not executed. |
| `s3k-hczslot:1` | Native water owner retirement permits slot reuse | Participant/range/P2 and rewind controls; full boss overwrite not executed. |
| `s3k-bridge:1` | Armed raw cutscene slot reads the current occupant, including itself | Real Knuckles initialization/retirement/reuse; full bridge allocation history not executed. |
| `s3k-shields:3` | SOZ negative-angle adjustment chooses horizontal correction | Native completed-player dash/no-dash pair and world replay for publication1656 only. |

The two S2 launcher rows share one repair and evidence. The three boundary rows
share one algorithm. This yields nine repair groups rather than twelve unrelated
fixes. The cutscene mechanism is a bounded read through the existing slot owner;
it does not introduce general RAM corruption or override the separate authorized
S2 sideways-platform safety exception.

## Existing consumer qualification

At receiving source `2ea97b2849e235dfc3ee7e10afd4a8d2f2dfb4c0`, the fifteen
existing movement/solid/rewind consumer classes passed **345 tests, 0 failures,
0 errors, 0 skips**. Last queue status was392s; Maven execution was1:42. Command:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dsonic1.rom.path="$HOME/code/projects/OpenGGF/s1.gen" -Dsonic2.rom.path="$HOME/code/projects/OpenGGF/s2.gen" -Ds3k.rom.path="$HOME/code/projects/OpenGGF/s3k.gen" '-Dtest=com.openggf.sprites.managers.TestPlayableSpriteMovement,com.openggf.sprites.managers.TestRightBoundary,com.openggf.physics.TestCollisionSystemAirLanding,com.openggf.physics.TestS3kCpuFollowGroundWallPipeline,com.openggf.physics.TestFbzCalcRoomInFrontBackgroundCollision,com.openggf.level.objects.TestObjectSolidContactController,com.openggf.level.objects.TestSolidObjectManager,com.openggf.game.solid.TestSolidExecutionRegistry,com.openggf.game.solid.TestSolidExecutionRewindSnapshot,com.openggf.game.sonic1.objects.TestSonic1FloatingBlockObjectInstance,com.openggf.game.sonic1.objects.TestSonic1CollapsingFloorWalkOff,com.openggf.game.sonic3k.objects.TestSonic3kCollapsingPlatformTransitionSolid,com.openggf.game.sonic3k.objects.TestSonic3kCollapsingPlatformFragment,com.openggf.game.sonic3k.objects.TestAizCollapsingLogBridgeObjectInstance,com.openggf.game.sonic3k.objects.TestAizCollapsingLogBridgeGraphRewind' test
```

These consumers cover the changed shared boundary/response and complete skipped
solid checkpoints, including existing S1/S3K collapsing objects. Later local
MHZ/launcher/explosion work does not alter these tested owners; its own affected
checks and the final composition are recorded separately. Queue intervals from
parallel worktrees overlap and are not additive delivery time.

The independent follow-up review found no material issues in the struck-slot
MHZ correction or captured-spawn explosion reconstruction. It checked immutable
native control flow and the new source blobs, without claiming to have rerun the
workers' queued checks. The first review's body-only finding is closed by its
executed red/green counterexample, not merely by the later review opinion.

## Current representative verdicts

| Game | Supported | Unsupported |
| --- | ---: | ---: |
| Sonic 1 | 27 | 1 |
| Sonic 2 / KiS2 | 33 | 5 |
| Sonic 3 / S&K / S3&K | 44 | 11 |
| Total | 104 | 17 |

These are the same121 named assessments as the original92/29 report. Twelve
promotions come from nine implemented low/medium groups. The remaining17
assessments occupy nine high/very-high groups: pause V-int, ORT recovery,
Reset/Power, DEZ art aliasing, P2 intro, special-stage live work cost, standalone
S&K entry, KosM overflow/mode alias and competition support. Their historical
unsupported evidence is unchanged; none is classified impossible.

All82 original family/source records and55 publication associations are retained.
The undefined S1 shortcut is a provenance note rather than an engine verdict.
Mixed families are aggregates of their binary children. Every full movie remains
unverified. The current machine-readable overlay preserves each promoted row's
complete previous assessment and source commit; the old report and video remain
unchanged. Final receiving validation passed as recorded below.

Current-source inventory on `e21f3b4bb22d73ca2cd2dccdda70fabc31d311dd`
resolves257 invocations in the original62 classes after explicit method renames
and changed parameter controls. The same scope against historical `58e1abb44`
still resolves249. This is static inventory only: no new recording was made and
new repair-only methods are not implicitly added to the old scope. Invocation
art dispositions must be reviewed before a new complete capture.

## Final receiving and structural checks

Combined source `e21f3b4bb22d73ca2cd2dccdda70fabc31d311dd` includes all three
worker branches and the parent repairs. Merges required no manual conflict
resolution; the shared S2 test retains both unsigned-boundary and hurt-launcher
changes. The reviewed MHZ and explosion production blob hashes match the
integrated files. No executable source changed after these runs.

- **150 passed, 0 failures/errors/skips**,22 receiving classes, Maven1:35,
  queue1s. This includes all four required S3K gates: AIZ1 skip8, level loading7,
  bootstrap resolver6 and decoding3. Original absolute S1/S2/S3K/KiS2 ROM
  properties were supplied; no missing-ROM skip was accepted.
- **637 passed, 0 failures/errors/skips**,11 structural/schema/API classes in
  the separate guards JVM, Maven39.272s, queue0s. Includes the changed recreation
  marker and exact policy registry, field/reference ownership, per-game rule
  placement and unchanged public Mod API signature surface.
- **21 Python recorder checks passed**; current257/historical249 static
  inventories resolve, and changed Markdown link targets and whitespace checks
  pass. These counts are not recordings or additional gameplay executions.

Exact receiving command (portable home path spelling; original ROMs remained
in the main workspace):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dsonic1.rom.path="$HOME/code/projects/OpenGGF/s1.gen" -Dsonic2.rom.path="$HOME/code/projects/OpenGGF/s2.gen" -Ds3k.rom.path="$HOME/code/projects/OpenGGF/s3k.gen" -Dkis2.rom.path="$HOME/code/projects/OpenGGF/kis2.gen" '-Dtest=com.openggf.tests.TestTasS1TerrainVerdicts,com.openggf.game.sonic1.objects.TestTasS1SlopeVerdict,com.openggf.sprites.managers.TestNativeLevelBoundaryWords,com.openggf.physics.TestGroundWallResponseAxis,com.openggf.game.sonic1.objects.TestS1FloatingBlockRenderGate,com.openggf.game.sonic2.TestTasS2CurrentLimitations,com.openggf.game.sonic2.objects.TestTasS2ArzBossEndpoint,com.openggf.game.sonic2.objects.TestTasS2HurtLauncher,com.openggf.game.rewind.TestBossExplosionRewind,com.openggf.physics.TestTasS3kTerrainEntry,com.openggf.tests.TestTasS3kFlameSlope,com.openggf.game.sonic3k.features.TestTasS3kWaterSkimLifetime,com.openggf.game.sonic3k.objects.TestTasS3kMemoryLimitations,com.openggf.tools.TestTasS3kSpecialStageEntryInput,com.openggf.game.sonic3k.objects.bosses.TestTasS3kMhzRapidHit,com.openggf.game.sonic3k.objects.bosses.TestMhzEndBossHitProxyRewind,com.openggf.game.sonic3k.specialstage.TestSonic3kSpecialStageManagerSnapshot,com.openggf.game.rewind.TestS2LauncherGraphRewind,com.openggf.tests.TestS3kAiz1SkipHeadless,com.openggf.tests.TestSonic3kLevelLoading,com.openggf.game.sonic3k.TestSonic3kBootstrapResolver,com.openggf.game.sonic3k.TestSonic3kDecodingUtils' test
```

Exact separate structural command:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Pguards '-Dtest=com.openggf.tests.game.TestPerGameRuleArchitectureGuard,com.openggf.game.rewind.TestRewindArchitectureGuard,com.openggf.game.rewind.TestRewindTransientGuard,com.openggf.game.rewind.schema.TestRewindFieldDispositionGuard,com.openggf.game.rewind.schema.TestExactRewindPolicyGuard,com.openggf.game.rewind.TestRewindRecreateLinkToleranceGuard,com.openggf.game.sonic3k.TestZoneEventRewindSchemaGuard,com.openggf.mods.TestModApiSignatureSurface,com.openggf.game.rewind.schema.TestRewindSchemaRegistry,com.openggf.game.rewind.TestSpawnRewindRecreatableCleanup,com.openggf.game.rewind.TestScalarOnlyCodecDeletion' test
```

These results are focused receiving/consumer/structural evidence, not a full
smoke suite, exhaustive trace sweep, rendered post-repair reel, or campaign
certification. Earlier worker runs overlap these selections; their counts must
not be summed as unique coverage. Consumed Maven logs and temporary selectors
are removed after this durable record, without creating pass caches or receipts.
