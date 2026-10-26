# Native-accuracy repairs for tested TAS mechanisms — 2026-10-26

## Scope and ownership

The user requested the low/medium repair groups from the [supportability assessment](2026-10-26-tas-supportability.md), only when they improve shipped-ROM accuracy. The integration base is `58e1abb44c716415bf308cc591776d2429c85a41`. High/very-high work, arbitrary RAM emulation, and new competition/standalone modes are excluded. Existing movie evidence remains comparison-only. This record is in progress; it is not a delivery claim.

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

## Remaining delivery validation

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
