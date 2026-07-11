# Live Rewind Object Regression Repair Design

## Problem

Live gameplay rewind has regressed for Sonic 2 objects. Broken monitors can remain broken after rewinding to a frame where they were intact. EHZ Masher fish can resume from the wrong position, and other badniks can similarly desynchronize after restore.

The affected behavior crosses two layers of the object rewind system:

- Layout objects such as monitors combine per-instance state with `ObjectManager` placement and remembered-spawn state. Recreated objects can lazily initialize after restore and must observe the remembered state from the restored frame, not the later live frame.
- Badniks combine base position/velocity fields with subclass-owned authoritative movement state. A rewind restore must leave both representations synchronized before gameplay resumes.

## Scope

This repair targets normal live gameplay rewind. Trace Test Mode is not the reproduction target, but its rewind tests remain regression coverage because it consumes the same object snapshot machinery.

The fix will preserve the current snapshot format and generic recreate architecture. It will not migrate every legacy object override or redesign the rewind subsystem.

## Design

### Restore ordering

`ObjectManager` will restore placement-owned state, including remembered-spawn data, before restored or recreated objects can perform lazy initialization against that state. Object recreation and per-object field restoration will continue to use the existing two-phase identity-aware path.

The ordering contract is: restore manager state that constructors or first updates may consult, recreate objects, restore their captured fields and links, settle derived state, then restore collision/touch controller state.

### Badnik state synchronization

Badniks with hand-written rewind state will restore the authoritative movement container and the base/render position and velocity fields to the same captured values. The repair will address the shared legacy restore seam where possible. Object-local handling is acceptable only where a subclass owns a genuinely unique authoritative state representation.

No zone, route, or frame-specific condition will be introduced.

### Compatibility

Existing snapshots, compact field policies, object identities, slot allocation, and recreate markers remain unchanged. The repair must not hydrate engine state from trace data.

## Testing

Tests will exercise the production `ObjectManager.rewindSnapshottable()` path:

1. Capture an intact Sonic 2 monitor, break it and set remembered state, restore the earlier snapshot, then run its first resumed update. The monitor must remain intact and solid.
2. Capture a moving Masher with non-zero subpixel state, advance/mutate it, restore, and run one resumed update. Position, velocity, subpixel phase, and subsequent movement must match a control object advanced from the captured state.
3. Exercise a representative badnik using the shared rewind path to prove base and subclass movement state do not diverge.

Each regression test must be observed failing before production changes. Verification will include focused tests, rewind round-trip and coverage guards, relevant Sonic 2 object tests, and the broader rewind/trace-replay suite where runtime permits.

## Success Criteria

- Live rewind restores Sonic 2 monitors to intact state before their destruction frame.
- Masher resumes at the captured position and follows the same subsequent trajectory.
- Representative badnik state remains synchronized after rewind.
- Rewind coverage and architecture guards remain green.
- No unrelated working-tree changes are modified or committed.
