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

Moving placement restore earlier must be state-only: it must not spawn, unload, or update objects during rewind restoration. After restore, placement active/remembered/dormant bits and the manager's active slot membership must agree with the captured snapshot. A capture immediately after restore must be equivalent for those fields.

### Badnik state synchronization

Badniks with hand-written rewind state will restore the authoritative movement container and the base/render position and velocity fields to the same captured values. The repair will address the shared legacy restore seam where possible. Object-local handling is acceptable only where a subclass owns a genuinely unique authoritative state representation.

The implementation will first inventory concrete badniks that override the legacy context-free `captureRewindState()` / `restoreRewindState()` pair and identify which hold a second authoritative movement container. The existing legacy dispatch contract will remain compatible, but the common restore path will post-normalize or context-route state where a safe shared invariant exists. Masher-specific code is permitted only for its unique `SubpixelMotion.State`; the shared invariant must also be demonstrated with at least one non-Masher legacy badnik such as Buzzer or Coconuts.

No zone, route, or frame-specific condition will be introduced.

### Compatibility

Existing snapshots, compact field policies, object identities, slot allocation, and recreate markers remain unchanged. The repair must not hydrate engine state from trace data.

## Testing

Tests will exercise the production `ObjectManager.rewindSnapshottable()` path:

1. Capture an intact Sonic 2 monitor, break it and set remembered state, force the original live instance out of the reusable restore path, restore the earlier snapshot, assert that a distinct monitor instance was recreated, then run its first resumed update. The recreated monitor must observe the captured remembered bit before lazy initialization and remain intact and solid.
2. Capture a moving Masher with non-zero subpixel state, advance/mutate it, restore, and run one resumed update. Position, velocity, subpixel phase, and subsequent movement must match a control object advanced from the captured state.
3. Inventory legacy badnik rewind overrides and exercise at least one non-Masher representative through capture, mutation, restore, immediate recapture, and multiple resumed frames. Base/render fields, subclass state, active membership, and trajectory must remain synchronized.

Each regression test must be observed failing before production changes. Verification will include focused tests, rewind round-trip and coverage guards, relevant Sonic 2 object tests, and the broader rewind/trace-replay suite where runtime permits.

Mandatory verification comprises the new focused tests, relevant Sonic 2 monitor/Masher/badnik unit tests, `TestRewindCoverageGuard`, `TestStaticStateRewindCoverageGuard`, `TestRewindArchitectureGuard`, and the applicable rewind round-trip tests. The full `*TraceReplay` sweep is best-effort because it may depend on local ROMs and runtime; if it cannot run or fails for unrelated existing reasons, the exact command and outcome must be reported.

## Success Criteria

- Live rewind restores Sonic 2 monitors to intact state before their destruction frame.
- Masher resumes at the captured position and follows the same subsequent trajectory.
- At least one non-Masher legacy badnik remains synchronized across immediate recapture and multiple resumed frames.
- Placement bits and active object membership match the captured frame without restore-time spawning or unloading.
- Rewind coverage and architecture guards remain green.
- No unrelated working-tree changes are modified or committed.
