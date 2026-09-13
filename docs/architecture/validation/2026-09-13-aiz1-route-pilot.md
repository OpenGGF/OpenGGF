# AIZ act 1 route-controller pilot: measured result

Second-zone pilot of the live-state route-controller technique
([design](../research/2026-09-13-live-state-route-controllers.md)), on the
shared `com.openggf.tests.route` primitives. Branch
`feature/ai-aiz1-route-pilot`; test `TestS3kAiz1RoutePilot`
(opt-in, `-Dopenggf.aiz1.pilot=true`). Native Sonic+Tails, 320px, engine at
develop `923f14188`.

## What the pilot proves

- **The primitives generalise off FBZ.** `InputProgram`, `RouteSteering`,
  `ObjectLifetimeFrames` and `RecentFrameLog` drove an AIZ1 controller with no
  change; only the authored program string and the stage logic are
  zone-specific. This was the pilot's primary question and the answer is yes.
- **A live gate can own an S3K event boundary.** The intro handover is gated
  on live state, not a frame count: hold neutral until object control has been
  seen and then released, and the Knuckles-cutscene pad lock
  (`isControlLocked`, `getMoveLockTimer`) has cleared. `isObjectControlled`
  alone is insufficient because the cutscene keeps the pad locked after object
  control ends.
- **The intro-length discrepancy is real and measurable.** The engine session
  starts with P1 at `$0040/$0420` already spawned; the `aiz1_to_hcz_fullrun`
  fixture's rows 0-288 are the plane fly-in before the player object exists.
  The engine intro is ~289 frames shorter, so the authored program is
  289 frames ahead of every global-timer object for the rest of the act.

## The cost finding

AIZ1's opening after the intro is a dense, essentially unbroken chain of
global-oscillator and spring hazards, unlike FBZ2 act 2 where long stretches
were plain running that the authored program covered for free. Between the
first ledge and the giant ride vine (fixture rows ~2090-2662, ~570 frames)
the player rides, in order:

1. `$1870` `Obj_FloatingPlatform` (global oscillator phase),
2. an `obj $13` spring, back left to
3. an `obj $10` floating platform, onto
4. an `obj $08` spring bounced twice to build rightward speed,
5. an `obj $0F` floating platform ridden right, then
6. the `$1DE0` `Obj_AIZGiantRideVine`, grabbed by falling onto its
   swing-phased handle.

Each is phase-dependent, so each needs its own gate; a missed phase on any one
shifts arrival at the next, and the divergence compounds. The pilot gated (1)
with a climb/hop/run-off stage that regulates the drop speed to ~`$460` so the
arc clears the `$1930` wall onto the following spring — that stage works — but
(2)-(5) then arrive off-phase and the vine grab (6) is missed.

Two consequences for the technique's cost model:

- On a hazard-dense act the "authored program + sparse live gates" hybrid
  degenerates to "one gate per hazard". The per-hazard authoring cost, which
  the [design](../research/2026-09-13-live-state-route-controllers.md) §4 named
  as the real cost, dominates here with no cheap stretches to amortise it.
  Roughly two and a half hours of authoring bought the intro gate plus one
  hazard gate; AIZ1 to the act-2 reload has on the order of a dozen more.
- **The vine needs engine state the pilot cannot see.** Grabbing
  `Obj_AIZGiantRideVine` requires the live world position of its swinging
  handle (driven by the global `AIZ_vine_angle`), which no public accessor
  exposes; `AizVineHandleLogic` and the handle are package-private. Gating it
  would mean adding a production accessor to satisfy a test — an
  architecture/scope decision that belongs to the owner, not a pilot branch.

## Recommendation

- Keep the pilot as a checkpoint; do not chase AIZ1 to completion on this
  branch. Completing it is multi-day route authoring (a dozen-plus dense gates
  plus the miniboss and fire transition) and at least one production accessor.
- Revisit the plan's pilot ranking with this datum: HCZ act 1, the other
  phase-2 S3K pilot, has water rather than a spring/vine chain, and would price
  a different hazard family. GHZ act 3 (no water, no sidekick, one boss) would
  cheaply confirm the primitives hold under S1 physics, which the design flags
  as unverified.
- If AIZ1 completion is funded, expose the vine handle position and grab state
  through `AizZoneRuntimeState` as a first, separately-reviewed step.
