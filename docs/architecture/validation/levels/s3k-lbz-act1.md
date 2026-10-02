# S3K LBZ act 1 — focused cup elevator coverage

Canonical game/zone/act: Sonic 3 & Knuckles, Launch Base, act 1 (zone 6, act 0).
This matrix records the 2026-10-03 contact correction, not whole-act certification.

| Obligation | Evidence | Remaining scope |
| --- | --- | --- |
| Cup side contact, P1 and CPU participant | `TestLbzCupElevatorSolidDispatch`: real object-manager dispatch stops airborne rolling player at cup edge without capture; repeats after object, solid registry and player restore. | Local native movement case; other character geometry and donors remain open. |
| Cup control and ownership | `TestLbzCupElevatorInstance`: existing movement, capture/release and participant-state checks. | Full-route rewind, load/death boundaries remain open. |
| Recorded Sonic + Tails route | `TestS3kLbzZoneSliceTraceReplay`, 46,075 rows, native 320px, donor off. First mismatch advances from 3714 to 9867. | Still fails; presentation, other widths, solo/Tails/Knuckles routes and donors are not certified. |

## Source and matched trace evidence

Base `ce26682b63`, worktree `trace-special-return`. Native `loc_26EEA`
performs `SolidObjectFull2_1P` after the cooldown/angle gates and before capture,
within each player's control call. `MANUAL_CHECKPOINT` installs a resolver but
does not execute it. The cup had no call to that resolver; seeded standing flags
in older unit tests hid the omitted contact. The fix adds the per-player call
at its native position, preserving P1 contact/capture before P2 processing.

At row 3714 native Sonic is `(11CB,0884)`, X/G speed zero; baseline engine is
`(11CA,0884)`, X speed `-048F`, G speed `-0294`. Y speed `03B0` matches.
Cup centre is `(11A0,0888)`; right side is centre + width `20` + padding `0B`.
Engine diagnostics confirm angle `80`, cooldown zero, outside, solid admitted.
Native aux does not expose angle/cooldown; those are not claimed as native
measurements. The immutable fixture supplies comparison evidence only.

Matched baseline and candidate commands (Java 21, verified ROM CRC `63522553`;
`S3K_REFERENCE_ROM` is the absolute path to the verified user-supplied ROM):

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Ptrace-segments -Dtest=TestS3kLbzZoneSliceTraceReplay -Dtrace.context.diagnosticChars=full "-Ds3k.rom.path=$S3K_REFERENCE_ROM" test
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestLbzCupElevatorInstance,TestLbzCupElevatorSolidDispatch' "-Ds3k.rom.path=$S3K_REFERENCE_ROM" test
```

Baseline: 6,557 comparison errors, first 3714 `x_speed`. Candidate: 6,316,
first 9867 `tails_air` expected 0 / actual 1. Each completed trace invocation
runs one test, one assertion failure, zero errors/skips. The next mismatch
coincides with rolling-drum deletion and standing ownership; no further fix
is included here. The initial two-case dispatch regression passed without
skips; the final focused object/restore invocation passes all 23 tests, zero failures, errors or skips. An earlier invocation passed both restore cases but exposed one older test without injected services; adding `TestObjectServices` repaired its setup without changing production code.

The change-based plan against `ce26682b63` selects 2,615 classes plus guards.
The parent campaign owns combined validation against its actual integration
base; these local runs are focused proof, not a broad-suite pass.

## Rolling-drum deletion follow-up (2026-10-03)

Base `d8851f0a41`. At row9867 native deletes rolling drum slot4 (`2C3CA`)
but Tails remains status09/onObj04; the engine instead clears the ride and
sets air/status03. Positions and velocities still match at that boundary.
Native CPU despawn follows at9868, while the synthetic release delays it.

`loc_2C3CA` runs both `sub_2C3E8` participant calls before
`Delete_Sprite_If_Not_In_Range`. Its `Delete_Current_Sprite` tail clears the
object SST only; it does not execute `loc_2C48A` rider-release writes.
The drum now selects the existing post-routine range check and leaves live
native P1/P2 state intact on deletion. Dead-native cleanup and extension
unload/omission cleanup remain separate. No shared collision cleanup changes.

`TestLbzRollingDrumDeletion` uses actual object-manager capture and range
unload, verifies the final native flip update and standing state, checks dead
P1/P2 and extension cleanup, and repeats from a pre-capture rewind snapshot.
The older compatibility assertion that unloading releases both native riders
was rejected because it contradicts the native delete tail. Existing compact
participant-relink tests and omitted-extension checks remain in scope.

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestLbzRollingDrumDeletion,TestLbzRollingDrumInstance,TestLbzResidualCompatibility' "-Ds3k.rom.path=$S3K_REFERENCE_ROM" test
```

The trace command is the same matched LBZ invocation above. The focused run
passes 31 tests, zero failures/errors/skips. Candidate replay has 4,031 errors,
first row18939 `x_speed` expected `0018` / actual `000C`, versus base 6,316
errors first9867 `tails_air`. One replay test fails with zero errors/skips;
there is no earlier comparison failure. No claim of whole-route parity.
The plan against `d8851f0a41` selects the same common/content/gameplay/physics/
rendering/rewind/tooling categories plus guards; combined campaign validation
remains the parent integration's responsibility.
This extends local ownership/rewind coverage; inherited route/configuration
and presentation gaps remain open.
