# Author a bounded native encounter

[Post Two Ambush](../../../examples/hardened-s3k/README.md) is a source-first JVM
patch example beside MHZ1's second physical starpost. Follow its maintained code
and tests rather than treating the blueprint's future campaign YAML as callable API.

## Start with evidence

Pin the engine commit and exact ROM identity. Load the concrete native level and
survey floor, headroom, camera edges, nearby native objects and ring placements
through production decoding. Cite the physical checkpoint's native centre,
subtype and local approach separately from combat-safe geometry. S3K SKL object
`0x8D` is Mushmeanie; S3KL `0x8D` is Rhinobot. The stock detached Mushmeanie shell
is harmless, so a harmful borrowed shell needs an unambiguous pointed warning.

Copy `examples/hardened-s3k` to your own namespaced creator project. Change the
manifest ID, entry point, registration keys and rewind keys together. Keep assets
ROM-backed: resident renderers may supply verified mappings; a disassembly label
is research evidence, never a runtime fallback.

## Freeze a plan transactionally

Register local factories through `ModContext.registerObject`. Register an immutable
`LevelPlacementPlan` for the native level index through
`registerLevelPlacementPlan`. Bounds are inclusive and at most 1,024 pixels on
each axis; retain at most 64 exact native `RingSpawn` identities, add at most 32
same-owner registered objects and eight coordinate-only `RingAddition` values,
and register at most eight level plans.

The example's `HardenedS3kMod.register` is a complete valid declaration. Its bounds
and ring coordinates come from `EncounterPlan`. The surveyed shelf has no native
rings; this plan retains none and adds exactly one safe recovery ring at
`(0x1D70,0x01A8)`. The engine assigns its identity beyond the original ROM ring
inventory and uses native rendering, award, collection and rewind owners.
Authors never provide a new ring ID. The engine derives the
owner from the committed registration. The plan removes only unretained rings
inside its rectangle, preserves outside rings and every native object, and appends
owned additions. The concrete native `Sonic3kLevel` and its prepared/deferred
resource fences remain authoritative. Source geometry, events and PLCs are not
wrapped or removed.

These declarations are invalid:

```java
// A plan cannot name another owner's factory.
new LevelPlacementPlan.ObjectAddition("other-owner:shot", 100, 100, 0, 0);

// Bounds wider than the bounded authoring contract are rejected.
new LevelPlacementPlan.Bounds(0, 0, 1025, 200);

// A coordinate match does not authorize inventing a native placement identity.
// An absent RingSpawn placementId causes owner-attributed assembly failure.
new RingSpawn(100, 100, 999999);

// Added rings must be inside their plan and must not duplicate any native ring.
new LevelPlacementPlan.RingAddition(-1, 100);

// The example validates its authored pattern before registering anything.
EncounterPlan.validatePattern(0, 4, 180);   // tell too short
EncounterPlan.validatePattern(36, 5, 180);  // too many live spores
EncounterPlan.validatePattern(36, 4, 181);  // lifetime above the pilot budget
```

A factory missing at transaction freeze, an unmatched native retention identity,
an overlapping plan or a budget violation publishes no partial transformed world.
Do not modify stock badnik timers or allocate numeric mod object IDs.

## Keep simulation and presentation separate

`EncounterState` belongs to the session module and registers a rewind adapter.
`Sentry`/`Spore` receive injected `services()` and implement recreation plus their
captured state. Snapshot non-default tell/aim/volley timers and live children,
then restore and replay the same native inputs twice. Test allocation failure,
offscreen re-entry, art readiness, expiry and owner abort independently of a long
route. Use admitted gameplay ticks for authored pattern timing; native objects
continue reading their own ROM clocks.

The example uses `GameplayFrameController.nativePlayerInput()` to opt into normal
native movement on advanced rows. Its entry/menu rows HOLD the world. Existing
controlled modes retain their default neutral input. `LevelInputOverlay` owns
modal Start/Enter before host pause without replacing gameplay bindings.
`freshLevelStartPosition` applies only to a fresh start. Death, checkpoint and
stage-return owners keep their native positions. `requiresNoSaveSession` rejects
slot attachment before replacing the old session and supplies an explicit no-save
context for an initial challenge launch.

The title/lesson and transitions use the engine font, render queue and native
ROM music/SFX. Do not count a queued cue as observed sound: inspect final PCM
variance/DC/flat windows and the actual captured scene. Repeated close/launch
must release drawing, capture and session resources.

## Package, verify and report

Build with the example command, inspect the jar inventory for classes/manifest
only, and run the real SDK validator. Document trust/installation/recovery and
the JVM-only code-mod limitation. Maintain exact controls and supported cells.

Change one authored rule at a time. First run the corresponding regression and
native contact/retry/replay checks, then the combined change-based selection and
all domain-required checks. Use absolute existing ROM paths and inspect skips.
Shared API/session/input changes require normal broad validation. Keep stock AIZ→HCZ
and the four mandatory S3K regression classes green.

Drive independent safe and failure input programs through production gameplay,
capture synchronized state/frames and actual PCM outside the repository, and
inspect frames selected from state rather than assuming a successful load is a
route proof. Record inputs, ROM hash, package/content identity, commit and exact
commands. Update the owning MHZ matrix/backlog with the changed local obligations;
retain inherited gaps. The local ambush does not certify the full act or campaign.
