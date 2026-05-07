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

**Torture-test progress.** Sniffing `tortureFixedAdjacent` after each landing:

| State | Failure at iter 1600 |
| --- | --- |
| Pre-step 4 (synthesized usedSlots) | `dynamicObjects[0].slotIndex: A=18 B=19`, `usedSlotsBits differs`, full slot cascade |
| Post step 4 + 5a-5c (live usedSlots + Animal/Points/Explosion codecs) | Placement-managed slot realignment (Masher slot 25→27, Bridge cascade) |
| Post step 5d-5e + 6 (Shield/Stars codecs + spawner re-pin) | Single-field diff: `dynamicObjects[0].state.genericState.values[2]: A=9 B=0` |

The remaining single-field divergence is a per-object scalar that the default
generic capture doesn't track yet (likely a counter, a phase index, or similar
state on whichever class lands at `dynamicObjects[0]` at iter 1600). It is no
longer an architectural issue — the slot drift cascade that motivated the
test's @Disabled commentary is closed.

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
