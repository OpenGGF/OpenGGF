# Seamless reload ownership

## Problem and existing flow

A same-level reload enters `LevelSeamlessTransitionExecutor`, which resolves the
current zone/act through `SeamlessLevelTransitionRequest.retargetedForReload`.
`LevelActTransitionExecutor` then consumes the resolved request to preserve world
state, admit runtime art, replace managers and transfer resources. Retargeting is
an identity change, not a policy decision.

Previously the live retargeting method reconstructed policy through builder
setters. It omitted runtime-art admission policy and preloaded-act camera-release
timing, and combined two independently configured end-of-level flags. An unused
executor normalizer maintained a different copy list; tests exercised that list
instead of the live path. A test could therefore pass while production lost state.

## Caller usage and chosen shape

The executor resolves identity before selecting the hosting frame:

```java
executeReload(
        request.retargetedForReload(levelManager.currentZone, levelManager.currentAct), host);
```

The immutable request owns retargeting with one private constructor:

```java
private SeamlessLevelTransitionRequest(
        SeamlessLevelTransitionRequest source, int zone, int act)
```

It changes only transition type and target identity and directly assigns every
other final field from the source. It does not reapply defaults or setters that
combine flags. A newly added final field must be assigned in both constructors
before Java compilation succeeds. The competing executor normalizer is removed.
This retargeting correction changes no public signatures, dispatch order, survival
policies or rewind boundaries. The separate frame-ownership correction below
removes effects that previously leaked into the following gameplay frame.

## Alternatives and limits

A private immutable policy payload shared by the source and retargeted request
would eliminate the copy list entirely. That is structurally stronger, but would
move every existing field/getter and the builder conversion for this bounded fix.
The direct-copy constructor is the smaller intervention and adds compile-time
pressure absent from a builder chain. Consider a payload only if more request
transformations demonstrate a need; do not create a general transition framework.

The constructor still requires correct assignments. The request regression
therefore enumerates actual instance fields and requires a non-default witness
for every policy field, then compares source and retargeted values. Independent
end-flag combinations and the native title wait sentinel have separate controls.
The executor test checks actual dispatch; ROM-backed checks exercise retained
end ownership and camera-release timing received by a carried object.

This closes request-policy loss. It does not certify complete routes, prove native
accuracy of every policy, or change the existing terminal resource-transfer fence.

## Frame ownership: completion values instead of executor flags

A frame-top reload runs a transition-only driver row. An event-owned reload runs
inside a normal level frame, whose remaining loop tail still needs to know whether
the transition already advanced oscillation and whose recorder owns the rewind
boundary. The old executor published identical markers for both entry points;
frame-top markers therefore survived into the following gameplay frame.

The bounded design keeps those entry points distinct:

```java
// The package-private executor has no persistent per-frame markers.
record Completed(boolean loopTailOscillationAdvanced) {}
Completed execute(SeamlessLevelTransitionRequest request) throws IOException;

// LevelManager: existing public event entry, publishes only successful effects.
public void executeActTransition(SeamlessLevelTransitionRequest request) throws IOException;
// Package-private driver entry, publishes no level-frame markers.
void reloadActAtFrameBoundary(SeamlessLevelTransitionRequest request) throws IOException;
```

`LevelSeamlessTransitionExecutor.Host` distinguishes `FRAME_BOUNDARY` from
`LEVEL_FRAME`, replacing a boolean clock-tail argument. Synchronous screen events
use the latter. Only the in-frame manager entry publishes the existing one-shot
markers after successful completion. The existing seamless-transition host stores
and consumes them, keeping the manager facade thin; frame-boundary drivers keep their existing
clock bridge and rewind boundary. Failed execution publishes no new effects.
The executor's oscillator-marking callback into its manager is removed.

The alternative considered with Claude Opus 5.5 was a rewind-captured coordinator
state machine unifying pending requests, execution context and frame effects.
That would change snapshot shape and more consumers; its additional benefit
depends on a separately unverified pending-request restoration concern. Choose
the smaller completion-value design first. Pending-request ownership remains a
separate investigation, not a claimed correction here.

A real S2 frame-boundary reload reproduced the leaked marker before the change.
S3K witnesses cover AIZ/HCZ/CNZ/LBZ in-frame publication and CNZ/LBZ/MHZ next-frame
continuations with two restore/replay cycles. These are positioned short lifecycle
checks, not whole-act, native-trace or full configuration certification. Existing
FBZ synchronous and admission/handoff failure controls remain required.
