# Rewind Encounter Validation Catalog

This pass adds a small test-side encounter shape for validating engine
forward-only state against engine rewind+replay state. Trace recordings are
used only as deterministic input streams; the encounter assertions do not read
ROM trace state as an oracle.

Initial non-disabled catalog entries:

| ID | Game | Zone | Family | Mechanic | Window | Compared snapshot keys |
| --- | --- | --- | --- | --- | --- | --- |
| `s2-ehz1-early-traversal` | Sonic 2 | EHZ1 | `baseline-objects` | Trace-input traversal before torture-scale dynamic spawns | rewind frame 180, compare frame 300 | `camera`, `object-manager`, `rings`, `sprites` |
| `s2-ehz1-mid-run-traversal` | Sonic 2 | EHZ1 | `transient-dynamics` | Mid-run traversal crossing badnik kills, animals, points popups (single rewind) | rewind frame 180, compare frame 1500 | `camera`, `object-manager`, `rings`, `sprites` |

The `s2-ehz1-mid-run-traversal` scenario passes today: a single rewind+replay
matches the forward run across a 22-second window covering early-trace badnik
encounters. Slot drift surfaces in `TestRewindTorture` only after many rewinds
in succession, not from any one rewind window.

## Slot-drift mitigation progress

The `TestRewindTorture` checklist itemized three architectural fixes; all three
have landed:

1. **Capture live `usedSlots` BitSet directly.** Done. `ObjectManagerSnapshot`
   stores the live `usedSlots.toLongArray()` instead of synthesizing a
   restorable subset.
2. **Add rewind codecs for transient dynamic objects.** Done.
   `AnimalObjectInstance`, the `AbstractPointsObjectInstance` family
   (Sonic1/Sonic2/Sonic3k), `ExplosionObjectInstance`, the `ShieldObjectInstance`
   family (base + S3K Fire/Lightning/Bubble), and the
   `InvincibilityStarsObjectInstance` family (base + S3K) are covered. The
   non-player-bound classes use construct-and-restore codecs; the player-bound
   classes use deferred codecs that stash the captured slot for post-restore
   re-spawn.
3. **Coordinate shield re-pin to honour the captured shield slot.** Done.
   `DefaultPowerUpSpawner.addPowerUpObject` now consumes the captured slot via
   `ObjectManager.consumePendingPlayerBoundSlot(...)` before falling back to a
   fresh free slot.

`RewindObjectStateBlob` also needed content-aware `equals`/`hashCode` so the
diff helper doesn't report false divergence on byte-identical compact sidecar
blobs.

**Torture-test progress.** Sniffing `tortureFixedAdjacent` after each landing
(checkpoint interval shown alongside):

| State | Earliest failure |
| --- | --- |
| Pre-step 4 (synthesized usedSlots), CHECKPOINT_INTERVAL=100 | iter 1600: `dynamicObjects[0].slotIndex: A=18 B=19`, full slot cascade |
| Post step 4 + 5a-5c, CHECKPOINT_INTERVAL=100 | iter 1600: placement-managed slot realignment (Masher 25→27, Bridge cascade) |
| Post step 5d-5e + 6, CHECKPOINT_INTERVAL=100 | iter 1600: single-field `ShieldObjectInstance#sequenceIndex: A=9 B=0` |
| Post deferred-codec entry restore, CHECKPOINT_INTERVAL=100 | iter 1700: player physics drift (downstream symptom) |
| Post bucketed-dynamics diff, CHECKPOINT_INTERVAL=10 | iter 1640: player physics drift |
| Same fixes, CHECKPOINT_INTERVAL=5 | iter 1635: player physics drift |
| Same fixes, CHECKPOINT_INTERVAL=1 (exhaustive) | iter 1631: hitbox dimensions transposed (20×38 → 38×20), runningMode GROUND→RIGHTWALL, angle 0→-40 |

The iter-1631 signature is real engine-state drift during torture replay, not a
slot/codec framework gap. Likely sources:

- Subtle drift baked into the keyframe at frame 1620 during torture replay
  (the keyframe captures cycle 1620's intermediate state mid-cycle, while
  Phase A's reference snapshot at frame 1620 is from the post-cycle state of
  the forward-only run — the controller's keyframe and Phase A's reference
  may not be at exactly the same logical step within the frame).
- Some captured state on the player or a level object that affects
  ground/wall transition logic but isn't fully covered by current capture.

This is per-frame instrumentation territory: the next investigation needs
to diff state at frames 1620-1631 step by step rather than rely on cycle-end
checkpoints.

**Concrete next-step diagnostic.** Write a one-shot test that:

1. Drives `RewindController` forward through frames 0..1631 in a fresh fixture, calling `registry.capture()` after every step into a `Map<Integer, CompositeSnapshot>` keyed by frame.
2. After step 1, calls `controller.seekTo(1620)` and then steps forward one frame at a time to 1631, capturing into a parallel `Map<Integer, CompositeSnapshot>`.
3. Diffs the two maps frame-by-frame via `RewindSnapshotDiff.diffKey` for keys `camera`, `sprites`, `object-manager`, `rings`, `level`.

The first frame whose diff is non-empty pinpoints exactly where the 11-step replay from keyframe 1620 diverges from the original forward stepping. Comparing that diff against the frame-0..1620 reference identifies which specific captured-state field is missing or wrong on the live engine after restore. From there it's a per-field framework fix (extend codec coverage, wire identity context through default subclass capture for `PlayableEntity`/`ObjectInstance` references, or add a missing capture in some subsystem).

A known gap that may or may not be the cause: default object-subclass capture currently excludes `PlayableEntity` and `ObjectInstance` reference fields on object instances (`isDefaultObjectFieldValueType` doesn't include them, even though `RewindCodecs.codecFor` returns `PlayerReferenceCodec`/`ObjectReferenceCodec` for those types). Fields like `FlipperObjectInstance#lockedPlayer`, `GrabberBadnikInstance#grabbedPlayer`, `SpringboardObjectInstance#launchPlayer` are therefore not captured. Fixing this requires plumbing a `RewindCaptureContext` with an identity table through default subclass capture so the reference codecs can encode/resolve player refs by `PlayerRefId`. This may not be the iter-1631 cause (EHZ1 doesn't have flippers/springboards/grabbers), but is a real coverage gap visible in the per-frame diagnostic.

Incremental enabling path:

1. Add focused encounter entries by game, zone, object family, and mechanic
   before enabling broad torture coverage.
2. Prefer short windows around one mechanic: monitor break, badnik collision,
   platform ride, spring launch, bumper, boss phase, act boundary.
3. Keep `TestRewindTorture` disabled until the matching focused encounters for
   transient dynamic objects are stable. Its current comments remain the
   architectural checklist for enabling the stress patterns.
4. Keep lightweight pattern/bounds tests non-disabled; they validate schedule
   generation without depending on object snapshot coverage.
