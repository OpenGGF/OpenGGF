# S2 object roll ownership

Base: `c1e641cad1644ee0b63667ec0e52f94b801b774f` (develop).
Task branch: `bugfix/ai-s2-pinball-ownership`.

## ROM evidence and decision

S2 `s2.constants.asm` aliases `pinball_mode` to `spindash_flag` at `$39`.
`Obj84` writes that byte on entry/exit; `ForcedSpinObjectInstance` retains
its paired pinball/spindash writes. Native consumers include rolling jump
admission, the zero-inertia `$400` boost, landing, airborne jump-height
handling, follower panic/spindash logic, dust and scripted animation.
The shared solid controller and motion mutators also inspect pinball state.
They must see the native state, rather than an object capture implementation detail.

`Obj85` capture (`loc_2AD26`, diagonal `loc_2AF2E`) sets rolling/radii and
`obj_control=$81`; it does not write `$39`. Its slope launch sets inertia,
angle `$E0` and clears in-air, then `loc_2B068` clears object control.
`Obj86` first standing contact (`loc_2B20A`) sets rolling and writes
`obj_control=1`; `loc_2B238` advances its rider state. Leaving/launching clears
control (`loc_2B23C`/`loc_2B2E2`). `ObjD6` Point Pokey capture/release likewise
holds through `$81` without borrowing `$39` (`loc_2BABE`/`loc_2BB12`).
References are the existing S2 disassembly, shipped `fixBugs=0`; no fixed
branch or fitted trace condition is introduced. The S2 REV01 ROM used for
headless checks has SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`.

Use those existing movement/control owners to preserve capture rolling for
its full duration. Remove launcher, flipper and cage synthetic pinball writes
and their saved native values. A later Obj84 change survives release because
there is no stale native value to restore. Keep launch velocity, actual roll
status, radii, cooldown and all existing control ownership semantics unchanged.
Released slope rolls use ordinary stopping, jumping and landing rules; they
must not retain a synthetic indefinite boost/landing/jump-blocking mode.
The existing vertical Tails one-shot roll-stop handoff remains separate.

The rejected replacement was another persistent synthetic flag or ownership
registry: control suppression already supplies the required capture duration,
and the ROM release routines supply no lifetime for an additional latch.
The old rolling-jump helper inferred synthetic ownership from mismatched
pinball/spindash booleans and mutated state during a read. Remove that repair:
rolling jump now reads native pinball directly, as the other native consumers do.
No public API fields, methods, records or signature pins change. Cross-game
native pinball behavior and the S3K spring sources are unchanged.

## Lifecycle and coverage limits

There is no new state to clear on death/reset or capture in player rewind.
Object graph rewind keeps the existing control and per-player capture maps;
the removed private saved-native fields no longer need schema entries.
Focused checks cover all launcher modes, sustained flipper riding, release,
overlapping native writes, hurt release, roll stop/landing/jump behavior,
Obj84 animation/CPU alias behavior, recreated object graphs and cage rewind.
Native headless checks cover flipper ride/launch and CNZ object regressions.
Whole CNZ act routes, viewport/donor/team breadth, exhaustive death/respawn
combinations and trace-frontier movement remain inherited gaps. No trace
frontier or whole-act certification is claimed.

## Validation

The first focused iteration passed 203 checks and errored in the converted
native-pinball movement test because its sensorless fixture reached AnglePos.
Supply real test sensors; this is test setup, not a gameplay failure.
Final queued checks on the task sources (Java 25.0.4.1), with no skips:

```sh
python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestLauncherSpringObjectInstance,TestFlipperRollOwnership,TestPointPokeyObjectInstance,TestS2PointPokeyRewind,TestForcedSpinObjectInstance,TestForcedSpinMultiSidekick,TestPlayableSpriteMovement,TestS2LauncherGraphRewind,TestS2FlipperGraphRewind,TestS2TraversalRiderRewind' test -B
# 226 tests, 0 failures/errors/skips
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestSolidOrderingSentinelsHeadless,TestCNZObjectBugs' '-Dsonic2.rom.path=<absolute S2 REV01 ROM path>' test -B
# 14 tests, 0 failures/errors/skips; identity verified above
python3 tools/testing/maven_queue.py -Dmse=off -Pguards '-Dtest=TestRewindFieldDispositionGuard,TestRewindPolicyRegistry,TestModApiSignatureSurface' test -B
# 33 tests, 0 failures/errors/skips
```

The initially queued lean guard request was cancelled before execution and
replaced with the normal guards lane, which supplies the graph audit heap.
No broad smoke/full suite, trace sweep, integration or push was performed;
the coordinating lead owns combined delivery validation and release prose.
