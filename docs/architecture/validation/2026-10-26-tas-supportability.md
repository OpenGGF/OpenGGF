# Unsupported TAS tricks: repair scope

**Subsequent implementation:** [native-accuracy repairs](2026-10-26-tas-accuracy-repairs.md)
record the accepted low/medium changes, new evidence and current classifications.
The counts, negative results and estimates below describe the original audit
source; they are preserved as history rather than silently relabeled.

At the original audit source, all 29 executed unsupported assessments were accounted for in 18 shared remediation groups. The estimates below were prospective. The linked accuracy-repair record supersedes implementation status for the nine accepted low/medium groups, while preserving these original estimates.

**Low:** one bounded owner. **Medium:** several states/consumers with bounded parity work. **High:** timing, lifecycle, asset aliasing or new session features. **Very high:** substantial mode support or corruption compatibility.

| Repair group | Games | Complexity | Approach |
| --- | --- | --- | --- |
| S2 ARZ final-hit endpoint transition | s2 | Low | bounded parity repair |
| Blue Sphere pre-load input pulse | s3k | Medium | bounded parity repair |
| MHZ early proxy hit / rapid damage | s3k | Medium | bounded parity repair |
| S1 offscreen platform slope lock | s1 | Medium | bounded parity repair |
| S2 hurt-state OOZ launcher corruption | s2 | Medium | bounded parity repair |
| S3K HCZ water-skim slot reuse | s3k | Medium | bounded parity repair |
| S3K flame-dash reverse slope zip | s3k | Medium | bounded parity repair |
| S3K slot-six cutscene button alias | s3k | Medium | deliberate compatibility model |
| Unsigned horizontal level wrap | s1, s2, s3k | Medium | bounded parity repair |
| Global V-int phase during Start pause | s1, s2, s3k | High | bounded parity repair |
| ORT allocation-failure duplicate spawn | s3k | High | deliberate compatibility model |
| Reset/Power movie actions and lifecycle | s3k | High | feature implementation |
| S2 DEZ explosion/animal tile alias | s2 | High | deliberate compatibility model |
| S2 P2 Start intro transition | s2 | High | feature implementation |
| S2 special-stage jump-dependent lag | s2 | High | deliberate compatibility model |
| Standalone S&K Knuckles title launch | s3k | High | feature implementation |
| KosM overflow and corrupted mode switch | s3k | Very High | deliberate compatibility model |
| Standalone Sonic 3 Competition mode | s3k | Very High | feature implementation |

Cross-game groups use the highest scoped estimate. Unsigned wrap is locally low in S1 but medium across shared movement. Pause advancement is locally medium, but high when including S2 hardware/PLC admission, fades and duplicate-tick prevention.

## S2 ARZ final-hit endpoint transition

At both travel endpoints preserve native read-modify-write ordering: add2 to the live routine after movement/handleFace instead of assigning4. Keep the later-slot defeat and face/parts animation order intact.

Scenario IDs: `s2-boss-optimization:2`.

- `s2-boss-optimization:2`: ARZ final-hit travel-endpoint explosion skip. Confidence: HIGH. Publications: [1779M](https://tasvideos.org/1779M), [3325M](https://tasvideos.org/3325M), [3339M](https://tasvideos.org/3339M).
  Acceptance: Actual final hit at endpoint: hitcount1->0, score1000, routine8->A; approach final hit remains8. Normal non-defeat endpoint2->4 at left and right boundaries. Rewind pre-final-touch and replay endpoint; explosion-skip/escape/capsule schedule follows native routineA without restarting attack.
  Engine owners: [Sonic2ARZBossInstance.java:370](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic2/objects/bosses/Sonic2ARZBossInstance.java#L370), [Sonic2ARZBossInstance.java:509](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic2/objects/bosses/Sonic2ARZBossInstance.java#L509), [Sonic2ARZBossInstance.java:217](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic2/objects/bosses/Sonic2ARZBossInstance.java#L217).

## Blue Sphere pre-load input pulse

Retain the final pre-load Ctrl_1 sample through the boot Process_Sprites-equivalent boundary and resume normal input sampling at the first native VBlank-owned opportunity. Model the latch as lifecycle/input state, preserving exact existing fade counts.

Scenario IDs: `s3k-sphere-timing:2`.

- `s3k-sphere-timing:2`: Pre-load Up is retained into the first special-stage player update. Confidence: HIGH. Publications: [3521M](https://tasvideos.org/3521M).
  Acceptance: Upgrade inputTiming1 to started=true/velocity$200 at first player update. Retain neutral and first-boot-Up controls, all22 fade holds and next-mainloop velocity checks; test held Up versus one-tick pulse and rewind.
  Engine owners: [Sonic3kSpecialStageManager.java:683](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic3k/specialstage/Sonic3kSpecialStageManager.java#L683), [Sonic3kSpecialStageManager.java:917](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic3k/specialstage/Sonic3kSpecialStageManager.java#L917), [TestTasS3kSpecialStageEntryInput.java:42](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/test/java/com/openggf/tools/TestTasS3kSpecialStageEntryInput.java#L42).

## MHZ early proxy hit / rapid damage

Separate proxy collision-byte state from parent flash/invulnerability; reproduce routine0 reinitialization and the native per-update proxy consequence after weather destruction. Keep the single initial accepted attack; do not synthesize six new touch callbacks or force HP2.

Scenario IDs: `s3k-boss:2`.

- `s3k-boss:2`: Current limitation: early MHZ proxy hit does not repeat damage across weather destruction. Confidence: HIGH. Publications: [3422M](https://tasvideos.org/3422M).
  Acceptance: Upgrade early-hit tail to HP2 after six updates while retaining no-early-hit HP9, routine4 and timer$39 controls. Keep single-contact cardinality, ordinary flash-lockout tests, defeat/clamp boundary, slot removal and rewind/forward replay.
  Engine owners: [MhzEndBossHitProxyChild.java:109](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic3k/objects/bosses/MhzEndBossHitProxyChild.java#L109), [MhzEndBossInstance.java:145](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic3k/objects/bosses/MhzEndBossInstance.java#L145), [MhzEndBossInstance.java:955](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic3k/objects/bosses/MhzEndBossInstance.java#L955), [MhzEndBossInstance.java:986](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic3k/objects/bosses/MhzEndBossInstance.java#L986).

## S1 offscreen platform slope lock

Express the object-routine decision to skip the entire native solid checkpoint (including continued-rider cleanup) as a semantic provider/checkpoint contract for FBlock. Movement and deletion still execute. Preserve object/player standing bits and existing rider bookkeeping while skipped; resume native release/carry at the next admitted checkpoint. Do not merely return false from isSolidFor or widen bounds: those paths can trigger cleanup or create contacts the ROM did not run. Inspect end-of-frame support reconciliation so it does not erase a deliberately skipped latch.

Scenario IDs: `S1_SLOPE_GLITCH:1`.

- `S1_SLOPE_GLITCH:1`: Offscreen moving-platform departure retains standing for the Tee-N-Tee slope-lock trigger. Confidence: HIGH for missing outer FBlock solid-dispatch gate; MEDIUM for downstream slope travel until a second route is executed. Publications: [3570M](https://tasvideos.org/3570M), [5121M](https://tasvideos.org/5121M).
  Acceptance: After an authorized repair, offscreen=true must retain standing/object latch after actual edge departure; offscreen=false must release. Keep the object alive control. Visible reentry must run native continued-rider release; destroyed-object and jump/hurt exits must not retain unrelated support. Restore-forward across offscreen skip and visible reentry; compare player flags, rider identity, object standing bit and positions. Exercise a second FBlock subtype or camera entry and the downstream Tee-N-Tee slope travel before claiming the complete route supported.
  Engine owners: [Sonic1FloatingBlockObjectInstance.java:262](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic1/objects/Sonic1FloatingBlockObjectInstance.java#L262), [ObjectExecutionController.java:85](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/level/objects/ObjectExecutionController.java#L85), [ObjectSolidContactController.java:2346](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/level/objects/ObjectSolidContactController.java#L2346), [DefaultSolidExecutionRegistry.java:105](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/solid/DefaultSolidExecutionRegistry.java#L105), [TestTasS1SlopeVerdict.java:17](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/test/java/com/openggf/game/sonic1/objects/TestTasS1SlopeVerdict.java#L17).

## S2 hurt-state OOZ launcher corruption

Model shipped routine4 admission and its continued hurt-versus-object-control interaction explicitly, including native launcher capture/lifetime fields. Do not clear hurt to make capture work; the missing clear in fixBugs=0 enables the reported speed effect. Audit every later launcher state, not just the first isHurt guard.

Scenario IDs: `s2-oil-speed-corruption:1`, `s2-ring-attack:3`.

- `s2-oil-speed-corruption:1`: Hurt-state entry into OOZ launcher. Confidence: HIGH for rejected inlet; MEDIUM for the complete later stacking/ring route. Publications: [335M](https://tasvideos.org/335M), [3339M](https://tasvideos.org/3339M).
  Acceptance: Both S2/KiS2 real hurt entries capture with inertia$1000, normal control captures, dead/debug/CPU-flying controls remain rejected. Observe native routine4 passage and speed effect over the full capture/release sequence; test repeat stacking with independent native evidence. Rewind at pre-hurt, capture and release; ensure no registry leak after player/object retirement. For s2-ring-attack, separately prove the buried-ring approach and ring collection; inlet repair alone is not a whole-route acceptance.
  Engine owners: [LauncherBallObjectInstance.java:187](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic2/objects/LauncherBallObjectInstance.java#L187), [LauncherBallObjectInstance.java:278](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic2/objects/LauncherBallObjectInstance.java#L278), [LauncherBallObjectInstance.java:366](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic2/objects/LauncherBallObjectInstance.java#L366), [PlayableSpriteMovement.java:3718](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/sprites/managers/PlayableSpriteMovement.java#L3718).

- `s2-ring-attack:3`: OOZ buried-ring launcher corruption constituent. Confidence: HIGH for rejected inlet; MEDIUM for the complete later stacking/ring route. Publications: [3339M](https://tasvideos.org/3339M).
  Acceptance: Both S2/KiS2 real hurt entries capture with inertia$1000, normal control captures, dead/debug/CPU-flying controls remain rejected. Observe native routine4 passage and speed effect over the full capture/release sequence; test repeat stacking with independent native evidence. Rewind at pre-hurt, capture and release; ensure no registry leak after player/object retirement. For s2-ring-attack, separately prove the buried-ring approach and ring collection; inlet repair alone is not a whole-route acceptance.
  Engine owners: [LauncherBallObjectInstance.java:187](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic2/objects/LauncherBallObjectInstance.java#L187), [LauncherBallObjectInstance.java:278](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic2/objects/LauncherBallObjectInstance.java#L278), [LauncherBallObjectInstance.java:366](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic2/objects/LauncherBallObjectInstance.java#L366), [PlayableSpriteMovement.java:3718](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/sprites/managers/PlayableSpriteMovement.java#L3718).

## S3K HCZ water-skim slot reuse

Move range/lifetime admission to the native water-slot owner after participant work, making retirement and later allocator reuse explicit. Coordinate the handler lifecycle so the slot can be freed without losing the native participant consequence on the retirement tick.

Scenario IDs: `s3k-hczslot:1`.

- `s3k-hczslot:1`: Water-skim slot becomes reusable after offscreen departure. Confidence: HIGH. Publications: [1617M](https://tasvideos.org/1617M), [1656M](https://tasvideos.org/1656M), [4266M](https://tasvideos.org/4266M), [6615M](https://tasvideos.org/6615M).
  Acceptance: Upgrade WaterSkimLifetime to require retirement and replacement at slot5, preserving the nonpersistent range control. Add active/inactive participant departure, two-player mixed departure, near-range equality and return-to-water controls plus rewind across retirement/reallocation.
  Engine owners: [HCZWaterSkimSlotObjectInstance.java:53](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic3k/objects/HCZWaterSkimSlotObjectInstance.java#L53), [HCZWaterSkimHandler.java:137](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic3k/features/HCZWaterSkimHandler.java#L137), [HCZWaterSkimHandler.java:559](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic3k/features/HCZWaterSkimHandler.java#L559).

## S3K flame-dash reverse slope zip

Expose the native zone collision-response profile through the existing semantic rules/provider boundary and apply the negative-angle adjustment only at its owning response phase. Preserve the probe geometry and pre-correction movement. Requalify the local matched state and downstream exit; do not add an early-landing workaround.

Scenario IDs: `s3k-shields:3`.

- `s3k-shields:3`: Current limitation: fire-dash normal-slope entry applies a vertical correction and misses the native reverse zip. Confidence: HIGH. Publications: [1656M](https://tasvideos.org/1656M).
  Acceptance: Retain no-dash tick44 control and separate ordinary special-slide controls. At tick34 assert centre $856.7100/$409.ED00, vx/vy529, g749, angle$20 and grounded; at tick35 require vx33/vy545/g0 rather than vx545/vy33/g771. Require native corrections -2/-3/-5/-9/-17 and tick39 exit $834.AE00/$40C.2A00, vx=-4345, airborne; sweep nearby angles/fractions and rewind.
  Engine owners: [CollisionSystem.java:400](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/physics/CollisionSystem.java#L400), [CollisionSystem.java:544](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/physics/CollisionSystem.java#L544), [TestTasS3kFlameSlope.java:78](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/test/java/com/openggf/tests/TestTasS3kFlameSlope.java#L78).

## S3K slot-six cutscene button alias

Model this named raw-slot alias as an explicit S3K cutscene reference to a slot identity and its x/y words, including self-occupancy. Keep Java object safety; expose only the native fields this routine reads, rather than emulating arbitrary writable SST memory.

Scenario IDs: `s3k-bridge:1`.

- `s3k-bridge:1`: Self-occupied slot 6 presses its own cutscene button. Confidence: HIGH. Publications: [1656M](https://tasvideos.org/1656M), [4266M](https://tasvideos.org/4266M), [6081M](https://tasvideos.org/6081M), [6890M](https://tasvideos.org/6890M).
  Acceptance: Retain slot6=self and real-Knuckles controls; add empty slot, other occupying actor, out-of-range, stale slot reuse and rewind-before-press cases.
  Engine owners: [S3kCutsceneButtonObjectInstance.java:94](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic3k/objects/S3kCutsceneButtonObjectInstance.java#L94), [Aiz2BossEndSequenceState.java:82](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic3k/objects/Aiz2BossEndSequenceState.java#L82), [ObjectQuery.java:9](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/level/objects/ObjectQuery.java#L9).

## Unsigned horizontal level wrap

Normalize the predicted pixel word and side limits to native unsigned word comparisons at the owning boundary step. Preserve native fixed-point prediction, subpixel write semantics, right-side inclusivity, CPU bounds and boss lock rules. Gate any demonstrated game differences through movement rules, never a fixture/zone check.

Scenario IDs: `S1_LEVEL_WRAP:1`, `s2-horizontal-underflow:1`, `s3k-hwrap:1`.

- `S1_LEVEL_WRAP:1`: Horizontal unsigned underflow transports to the right boundary. Confidence: HIGH for the named local unsigned-underflow mechanism; route acquisition remains separately bounded. Publications: [2410M](https://tasvideos.org/2410M), [2629M](https://tasvideos.org/2629M), [3570M](https://tasvideos.org/3570M).
  Acceptance: Convert the named limitation to a native-parity assertion at right boundary9767 with vx/gSpeed0 after an authorized repair; retain x40 nonwrapping clamp16. Add fractional carry near0, exact left/right equality, positive overflow/underflow and screen-lock controls at the comparison owner. Exercise a second native input/entry, then restore-forward over the wrap; run affected cross-game boundary regressions.
  Engine owners: [PlayableSpriteMovement.java:3794](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/sprites/managers/PlayableSpriteMovement.java#L3794), [PlayerLevelBoundaryRules.java:57](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/rules/PlayerLevelBoundaryRules.java#L57), [TestTasS1TerrainVerdicts.java:13](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/test/java/com/openggf/tests/TestTasS1TerrainVerdicts.java#L13).

- `s2-horizontal-underflow:1`: Unsigned left-boundary underflow wrap (S2 and KiS2 rules). Confidence: HIGH. Publications: [2037M](https://tasvideos.org/2037M), [3325M](https://tasvideos.org/3325M), [3584M](https://tasvideos.org/3584M).
  Acceptance: Convert/add separate native-expectation regression for both S2 and KiS2: predicted$FFE8 -> right bound$2168, ordinary slow x24/vx-$100 unchanged. Retain current limitation evidence until superseded explicitly. Test x fractions, word rollover, exact left/right edges, boss lock, CPU followers and signed/high-word controls. Rewind before crossing, restore/replay, and validate a real wrap destination through camera/object streaming.
  Engine owners: [PlayableSpriteMovement.java:3796](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/sprites/managers/PlayableSpriteMovement.java#L3796), [PlayableSpriteMovement.java:3837](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/sprites/managers/PlayableSpriteMovement.java#L3837), [PlayerMovementRules.java:86](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/rules/PlayerMovementRules.java#L86).

- `s3k-hwrap:1`: Left-edge underflow reaches the unsigned right boundary. Confidence: HIGH. Publications: [779M](https://tasvideos.org/779M), [1112M](https://tasvideos.org/1112M), [1400M](https://tasvideos.org/1400M), [1595M](https://tasvideos.org/1595M), [1617M](https://tasvideos.org/1617M), [1656M](https://tasvideos.org/1656M), [2468M](https://tasvideos.org/2468M), [2494M](https://tasvideos.org/2494M), [2741M](https://tasvideos.org/2741M), [2744M](https://tasvideos.org/2744M), [3425M](https://tasvideos.org/3425M), [4266M](https://tasvideos.org/4266M), [5310M](https://tasvideos.org/5310M), [5453M](https://tasvideos.org/5453M), [5504M](https://tasvideos.org/5504M), [5654M](https://tasvideos.org/5654M), [6081M](https://tasvideos.org/6081M), [6106M](https://tasvideos.org/6106M), [6615M](https://tasvideos.org/6615M), [6890M](https://tasvideos.org/6890M).
  Acceptance: Convert the named limitation into the independent $1128 right-boundary assertion, retaining the in-bounds control. Add exact $0000/$FFFF prediction and boundary-equality cases, subpixel carry/borrow, both directions, main/sidekick, all three games.
  Engine owners: [PlayableSpriteMovement.java:3797](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/sprites/managers/PlayableSpriteMovement.java#L3797), [PlayerMovementRules.java:86](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/rules/PlayerMovementRules.java#L86).

## Global V-int phase during Start pause

Give each represented paused VBlank exactly one authoritative power-on tick while keeping object/player/level gameplay frozen. Preserve existing hardware/PLC phase admission and trace timing contract. Prefer one clock owner rather than offsetting capsule masks or injecting trace values.

Scenario IDs: `S1_CAPSULE_RNG:2`, `s2-pause-cycles:1`, `s2-capsule-rng:2`, `s3k-rng:1`, `s3k-ddz:2`.

- `S1_CAPSULE_RNG:2`: Start pause shifts the VInt phase used by capsule animals. Confidence: HIGH for the named pause clock discrepancy; MEDIUM for any plan that centralizes every VBlank counter path. Publications: [1401M](https://tasvideos.org/1401M), [1948M](https://tasvideos.org/1948M).
  Acceptance: After authorized repair, three paused iterations must produce before+3 with level time unchanged; fresh Start must produce before+4 exactly once. Retain prepared-FZ PLC pause test: prepared entry drains, next descriptor stays queued, gameplay callback remains0 until resume. Actual capsule scene with matched RNG: pause windows across modulo-eight residues must shift spawn timing/animals after resume without RNG draws during pause. Capture/restore before pause, within pause and before resume; compare counter, flags, PLC state and resumed animal/RNG sequence. Focused cross-game pause and explicit transition/fade/trace counter regressions reject double increments and no-op service ticks.
  Engine owners: [LevelFrameStep.java:101](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/LevelFrameStep.java#L101), [ObjectManager.java:771](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/level/objects/ObjectManager.java#L771), [Sonic1EggPrisonObjectInstance.java:312](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic1/objects/Sonic1EggPrisonObjectInstance.java#L312), [LevelIterationAdmissionController.java:245](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/LevelIterationAdmissionController.java#L245), [ObjectManager.java:4232](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/level/objects/ObjectManager.java#L4232), [TestTasS1PausePlcVerdicts.java:43](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/test/java/com/openggf/tests/TestTasS1PausePlcVerdicts.java#L43).

- `s2-pause-cycles:1`: Pause gameplay while V-int-dependent object phase advances. Confidence: HIGH for clock ownership mismatch; MEDIUM for full cross-game timing consequences. Publications: [1779M](https://tasvideos.org/1779M), [2037M](https://tasvideos.org/2037M), [3038M](https://tasvideos.org/3038M), [3325M](https://tasvideos.org/3325M), [3339M](https://tasvideos.org/3339M), [3584M](https://tasvideos.org/3584M), [5640M](https://tasvideos.org/5640M), [5675M](https://tasvideos.org/5675M).
  Acceptance: Real Start/held/release/fresh-Start sequence: paused V-int advances each represented interrupt; player/object positions, velocities, timers and RNG remain frozen. Pause shifts final capsule checker off-mask, no animal allocation or RNG consumption; on-mask running control still spawns and blocks results. Rewind across pause/resume with exact input and counter replay; loading, hardware-timing and reused-session tests show no missed/double ticks.
  Engine owners: [GameLoop.java:1466](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/GameLoop.java#L1466), [LevelFrameStep.java:117](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/LevelFrameStep.java#L117), [VIntRunCounter.java:89](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/timing/VIntRunCounter.java#L89), [ObjectManager.java:2857](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/level/objects/ObjectManager.java#L2857), [EggPrisonObjectInstance.java:424](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic2/objects/EggPrisonObjectInstance.java#L424).

- `s2-capsule-rng:2`: Pause deletes the final V-int-masked animal spawn. Confidence: HIGH for clock ownership mismatch; MEDIUM for full cross-game timing consequences. Publications: [3325M](https://tasvideos.org/3325M), [3584M](https://tasvideos.org/3584M), [5675M](https://tasvideos.org/5675M).
  Acceptance: Real Start/held/release/fresh-Start sequence: paused V-int advances each represented interrupt; player/object positions, velocities, timers and RNG remain frozen. Pause shifts final capsule checker off-mask, no animal allocation or RNG consumption; on-mask running control still spawns and blocks results. Rewind across pause/resume with exact input and counter replay; loading, hardware-timing and reused-session tests show no missed/double ticks.
  Engine owners: [GameLoop.java:1466](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/GameLoop.java#L1466), [LevelFrameStep.java:117](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/LevelFrameStep.java#L117), [VIntRunCounter.java:89](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/timing/VIntRunCounter.java#L89), [ObjectManager.java:2857](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/level/objects/ObjectManager.java#L2857), [EggPrisonObjectInstance.java:424](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic2/objects/EggPrisonObjectInstance.java#L424).

- `s3k-rng:1`: Start-pause advances universal clock while gameplay waits. Confidence: HIGH. Publications: [712M](https://tasvideos.org/712M), [1112M](https://tasvideos.org/1112M), [1400M](https://tasvideos.org/1400M), [1595M](https://tasvideos.org/1595M), [1656M](https://tasvideos.org/1656M), [2741M](https://tasvideos.org/2741M), [2744M](https://tasvideos.org/2744M), [3425M](https://tasvideos.org/3425M), [5504M](https://tasvideos.org/5504M).
  Acceptance: Upgrade StartPauseClock to native +3 universal ticks with unchanged IGT/player/level-frame; resume +1. Combine actual pause with DDZ homing and signpost RNG phase controls, preserving ordinary natural homing tests. Rewind during pause and across resume; cover frame-step, host focus pause and fades separately.
  Engine owners: [GameLoop.java:1278](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/GameLoop.java#L1278), [GameLoop.java:1465](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/GameLoop.java#L1465), [GameLoop.java:1150](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/GameLoop.java#L1150), [LevelFrameStep.java:117](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/LevelFrameStep.java#L117), [DdzMissileObjectInstance.java:283](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic3k/objects/DdzMissileObjectInstance.java#L283).

- `s3k-ddz:2`: Pause-assisted missile alignment. Confidence: HIGH. Publications: [2744M](https://tasvideos.org/2744M).
  Acceptance: Upgrade StartPauseClock to native +3 universal ticks with unchanged IGT/player/level-frame; resume +1. Combine actual pause with DDZ homing and signpost RNG phase controls, preserving ordinary natural homing tests. Rewind during pause and across resume; cover frame-step, host focus pause and fades separately.
  Engine owners: [GameLoop.java:1278](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/GameLoop.java#L1278), [GameLoop.java:1465](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/GameLoop.java#L1465), [GameLoop.java:1150](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/GameLoop.java#L1150), [LevelFrameStep.java:117](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/LevelFrameStep.java#L117), [DdzMissileObjectInstance.java:283](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic3k/objects/DdzMissileObjectInstance.java#L283).

## ORT allocation-failure duplicate spawn

Represent the S3K backward layout cursor and respawn-byte cursor independently, including failed-allocation recovery. Allow the native mispaired latch to admit a second live instance of the same placement without conflating per-instance identity, unload bookkeeping or rewind links. This is address/cursor semantics in placement, not an arbitrary request to duplicate a boss.

Scenario IDs: `s3k-ort:1`.

- `s3k-ort:1`: Leftward mid-pass SST exhaustion desynchronizes ORT. Confidence: MEDIUM. Publications: [6890M](https://tasvideos.org/6890M).
  Acceptance: Upgrade one-free-slot recovery to native two live B instances while fully-full upfront control still produces one; retain exhaustion/recovery reach checks. Add zero/two-free controls, both scroll directions, multiple failed entries, unload/respawn and exact rewind replay. Only then drive duplicate AIZ2 bosses/results and test the downstream KosM scenario separately.
  Engine owners: [ObjectPlacementController.java:1409](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/level/objects/ObjectPlacementController.java#L1409), [ObjectPlacementController.java:181](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/level/objects/ObjectPlacementController.java#L181), [ObjectPlacementController.java:1822](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/level/objects/ObjectPlacementController.java#L1822).

## Reset/Power movie actions and lifecycle

Add typed Reset and Power actions to input-log parsing, recording and playback, then implement explicit warm-reset versus cold-power session boundaries with a documented native preserved/cleared-state inventory. The parser change is small; session, audio/art/timing, save and retained native-state behavior make this a cross-system feature. Crash-dependent exploits need their own compatibility model after this action path exists.

Scenario IDs: `s3k-nolr:3`, `s3k-reset:1`.

- `s3k-nolr:3`: Published reset-assisted no-Left/Right completion. Confidence: MEDIUM. Publications: [6106M](https://tasvideos.org/6106M).
  Acceptance: Retain neutral/Reset/Power/Start discriminating rows and assert distinct typed machine actions. Run a real session warm-reset and power-cycle test with known save/RNG/mode/emerald state; verify no-device audio and resource teardown. Reproduce the claimed MHZ-to-FBZ reset boundary independently, then demonstrate any crash/reset-dependent publication sequence.
  Engine owners: [Bk2MovieLoader.java:216](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/debug/playback/Bk2MovieLoader.java#L216), [Bk2FrameInput.java:26](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/debug/playback/Bk2FrameInput.java#L26), [GameLoop.java:1448](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/GameLoop.java#L1448).

- `s3k-reset:1`: Native Reset/Power action through playback boundary. Confidence: MEDIUM. Publications: [6081M](https://tasvideos.org/6081M), [6106M](https://tasvideos.org/6106M), [6615M](https://tasvideos.org/6615M).
  Acceptance: Retain neutral/Reset/Power/Start discriminating rows and assert distinct typed machine actions. Run a real session warm-reset and power-cycle test with known save/RNG/mode/emerald state; verify no-device audio and resource teardown. Reproduce the claimed MHZ-to-FBZ reset boundary independently, then demonstrate any crash/reset-dependent publication sequence.
  Engine owners: [Bk2MovieLoader.java:216](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/debug/playback/Bk2MovieLoader.java#L216), [Bk2FrameInput.java:26](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/debug/playback/Bk2FrameInput.java#L26), [GameLoop.java:1448](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/GameLoop.java#L1448).

## S2 DEZ explosion/animal tile alias

Represent ROM VRAM destination ownership and ordered PLC writes for participating S2 object sheets, with a shared native tile store or alias-aware resolver. Upload last-written bytes to every dependent renderer while retaining mapping/tile-base semantics. Do not special-case DEZ animals or overwrite a dedicated sheet from test-observed pixels.

Scenario IDs: `s2-dez-art-corruption:1`.

- `s2-dez-art-corruption:1`: DEZ animal PLC overwrites explosion art at shared tile580. Confidence: HIGH for alias; MEDIUM for scope of safe shared implementation. Publications: [335M](https://tasvideos.org/335M).
  Acceptance: Both existing PLC load orders: animals-last explosion mapping reads Pig pixels; explosion-last remains fiery. Assert real rendered pixels, not only source arrays. Independent overlap/range cases and repeated writes prove general last-writer semantics; non-overlap controls remain stable. Rewind before/after each PLC publication restores bytes and pixels; level/act transitions refresh all affected renderers.
  Engine owners: [Sonic2ObjectArtProvider.java:212](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic2/Sonic2ObjectArtProvider.java#L212), [Sonic2ObjectArtProvider.java:355](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic2/Sonic2ObjectArtProvider.java#L355), [Sonic2ObjectArtProvider.java:470](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic2/Sonic2ObjectArtProvider.java#L470), [PatternSpriteRenderer.java:54](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/level/render/PatternSpriteRenderer.java#L54).

## S2 P2 Start intro transition

First synchronize the5735S P2 Start inlet against native input-copy, interrupts, load/fade boundaries and title presentation. Then implement the demonstrated intro/load/input ownership transition, including any bounded shared-memory/VDP alias effect proven to cause corrupted title art. Do not add a one-frame P2 shortcut to the fixed timer.

Scenario IDs: `s2-intro-p2-pause:1`.

- `s2-intro-p2-pause:1`: P2-dependent introductory-text/title transition capability. Confidence: HIGH for absent engine capability; LOW/MEDIUM for native causal mechanism until synchronized probe. Publications: [3584M](https://tasvideos.org/3584M).
  Acceptance: Native synchronized P2 Start/neutral plus held/early/late controls identify exact causal owner and one-frame effect. Engine real title session follows that native phase/input behavior and pixel corruption if independently established. Repeated boot and rewind/restore of supported title phases preserve input edges, audio isolation and art ownership.
  Engine owners: [TitleScreenManager.java:73](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic2/titlescreen/TitleScreenManager.java#L73), [TitleScreenManager.java:552](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic2/titlescreen/TitleScreenManager.java#L552), [TitleScreenManager.java:573](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic2/titlescreen/TitleScreenManager.java#L573), [TitleScreenManager.java:1364](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic2/titlescreen/TitleScreenManager.java#L1364).

## S2 special-stage jump-dependent lag

Define and validate a live native-work model using actual object/player/sprite construction cost and VDP transfer boundaries, with bounded deterministic counters. Jump is an input to native work, not a fitted skip predicate. Keep external trace lag admission separate and comparison data unable to hydrate gameplay.

Scenario IDs: `s2-special-stage-lag:1`.

- `s2-special-stage-lag:1`: Jump-dependent live halfpipe pacing capability. Confidence: HIGH for missing input dependence; MEDIUM for a bounded replacement; LOW for exact native schedule without cycle evidence. Publications: [5640M](https://tasvideos.org/5640M).
  Acceptance: Use native completed-loop/VInt records for running and jumping at matched entry; characterize differing schedule/cost before implementing. Current240-update running/jumping child plus negative controls show causal work dependence; independent stages/routes reject a route/frame-fitted model. Rewind restores pacing state and resimulates exact schedules; externally paced traces retain current strict admission guards.
  Engine owners: [Sonic2SpecialStageLagModel.java:22](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic2/specialstage/Sonic2SpecialStageLagModel.java#L22), [Sonic2SpecialStageLagModel.java:34](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic2/specialstage/Sonic2SpecialStageLagModel.java#L34), [Sonic2SpecialStageManager.java:1304](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic2/specialstage/Sonic2SpecialStageManager.java#L1304), [2026-07-27-cross-game-hardware-timing-trace-contract.md:1](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/docs/architecture/designs/2026-07-27-cross-game-hardware-timing-trace-contract.md#L1).

## Standalone S&K Knuckles title launch

Carry explicit cartridge/lock-on identity from ROM detection into S3K startup/resource policies. Provide standalone SK title one-player exit to MHZ with native selected character, lives/emerald/save initialization, bypassing locked-on save-grid dispatch. Prove standalone asset addressing and campaign behavior, not just the route enum.

Scenario IDs: `s3k-standalone:1`.

- `s3k-standalone:1`: Standalone S&K title starts Knuckles at MHZ. Confidence: MEDIUM. Publications: [2486M](https://tasvideos.org/2486M), [2494M](https://tasvideos.org/2494M), [5310M](https://tasvideos.org/5310M).
  Acceptance: Use a verified original standalone SK ROM for title -> Knuckles -> MHZ LEVEL, no save grid; compare native initialized state. Retain locked-on AIZ save-grid/NewGamePlus controls and distinct TWO_PLAYER routing; test standalone Sonic/Tails where supported and reload/rewind boundaries.
  Engine owners: [Sonic3kRomDetector.java:48](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic3k/Sonic3kRomDetector.java#L48), [StartupRouteResolver.java:34](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/startup/StartupRouteResolver.java#L34), [GameLoopNativeMenus.java:227](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/GameLoopNativeMenus.java#L227), [Sonic3kGameModule.java:545](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic3k/Sonic3kGameModule.java#L545).

## KosM overflow and corrupted mode switch

Design an explicit, bounded S3K native memory-adjacency compatibility owner: represent only the overflow records and neighboring mode/state words the shipped queue scan can read/write, then let downstream mode consumers react through ordinary semantic policies. Merely raising Java queue capacity or toggling mode on the fifth request cannot model the native address/content-dependent effect. Keep safe Java memory and compare each native write/consumer boundary.

Scenario IDs: `s3k-standalone:3`, `s3k-legacy-mode:1`, `s3k-mode:1`.

- `s3k-standalone:3`: Locked-on corruption switches to SK-alone mode. Confidence: MEDIUM. Publications: [6081M](https://tasvideos.org/6081M), [6890M](https://tasvideos.org/6890M).
  Acceptance: Retain full four-entry and freed-slot controls; independently assert exact native overflow address/value writes for fifth and later archives. Drive real duplicated result/tally consumers into the queue, observe the mode change and native game-over/restart consequences. Rewind before overflow, during partial service and after mode transition; compare a second input sequence, not only one publication.
  Engine owners: [KosinskiModuleQueue.java:55](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/level/resources/KosinskiModuleQueue.java#L55), [KosinskiModuleQueue.java:169](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/level/resources/KosinskiModuleQueue.java#L169), [Sonic3kGameModule.java:545](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic3k/Sonic3kGameModule.java#L545).

- `s3k-legacy-mode:1`: KosM queue overflow into adjacent native mode state. Confidence: MEDIUM. Publications: [6081M](https://tasvideos.org/6081M).
  Acceptance: Retain full four-entry and freed-slot controls; independently assert exact native overflow address/value writes for fifth and later archives. Drive real duplicated result/tally consumers into the queue, observe the mode change and native game-over/restart consequences. Rewind before overflow, during partial service and after mode transition; compare a second input sequence, not only one publication.
  Engine owners: [KosinskiModuleQueue.java:55](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/level/resources/KosinskiModuleQueue.java#L55), [KosinskiModuleQueue.java:169](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/level/resources/KosinskiModuleQueue.java#L169), [Sonic3kGameModule.java:545](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic3k/Sonic3kGameModule.java#L545).

- `s3k-mode:1`: KosM queue overflow into adjacent native mode state. Confidence: MEDIUM. Publications: [6890M](https://tasvideos.org/6890M).
  Acceptance: Retain full four-entry and freed-slot controls; independently assert exact native overflow address/value writes for fifth and later archives. Drive real duplicated result/tally consumers into the queue, observe the mode change and native game-over/restart consequences. Rewind before overflow, during partial service and after mode transition; compare a second input sequence, not only one publication.
  Engine owners: [KosinskiModuleQueue.java:55](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/level/resources/KosinskiModuleQueue.java#L55), [KosinskiModuleQueue.java:169](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/level/resources/KosinskiModuleQueue.java#L169), [Sonic3kGameModule.java:545](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic3k/Sonic3kGameModule.java#L545).

## Standalone Sonic 3 Competition mode

Implement standalone S3 competition menu/player/level/results modes plus timed race/match lifecycle, native track resources, independent participant state/cameras and split-display presentation. Reuse suitable native-menu/session abstractions and existing other-game competition infrastructure, but port S3-specific rules rather than relabel S2 mode.

Scenario IDs: `s3k-competition:1`, `s3k-standalone:2`.

- `s3k-competition:1`: Launch standalone Sonic 3 Competition. Confidence: MEDIUM. Publications: [1713M](https://tasvideos.org/1713M).
  Acceptance: Replace missing-host title limitation with real TWO_PLAYER -> Competition menu -> chosen event/track -> race -> native results, retaining one-player control. Verify native per-track/two-player timers, wins/ties/restarts and input lanes; real ROM rendering and rewind/forward replay for each competition route.
  Engine owners: [GameLoopNativeMenus.java:222](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/GameLoopNativeMenus.java#L222), [GameLoopNativeMenus.java:238](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/GameLoopNativeMenus.java#L238), [Sonic3kGameModule.java:545](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic3k/Sonic3kGameModule.java#L545), [NativeTitleModeProvider.java:11](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/nativemenu/NativeTitleModeProvider.java#L11).

- `s3k-standalone:2`: Standalone Sonic 3 Competition host. Confidence: MEDIUM. Publications: [1713M](https://tasvideos.org/1713M).
  Acceptance: Replace missing-host title limitation with real TWO_PLAYER -> Competition menu -> chosen event/track -> race -> native results, retaining one-player control. Verify native per-track/two-player timers, wins/ties/restarts and input lanes; real ROM rendering and rewind/forward replay for each competition route.
  Engine owners: [GameLoopNativeMenus.java:222](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/GameLoopNativeMenus.java#L222), [GameLoopNativeMenus.java:238](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/GameLoopNativeMenus.java#L238), [Sonic3kGameModule.java:545](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/sonic3k/Sonic3kGameModule.java#L545), [NativeTitleModeProvider.java:11](https://github.com/OpenGGF/OpenGGF/blob/94b2a5bf1daeaaad550ff7b8091a8361d6a9094e/src/main/java/com/openggf/game/nativemenu/NativeTitleModeProvider.java#L11).


## Limits

- No gameplay repairs were implemented in this assessment.
- Complexity describes scope and interaction risk, not elapsed-time promises.
- Group complexity includes the highest affected-owner estimate; individual estimates are retained.
- None is demonstrated fundamentally impossible; this is feasibility assessment, not proof of a repair.
- Exact CPU/RAM/VDP emulation is a larger undertaking than bounded native-consequence models.
- The S2 P2 intro causal inlet remains a research prerequisite; do not invent a timer shortcut.
- Existing unsupported verdicts and all full-movie synchronization limits remain unchanged.
