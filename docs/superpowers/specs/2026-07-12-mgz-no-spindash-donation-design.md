# MGZ No-Spindash Donation Compatibility Design

## Evidence

Sonic 1 donation composes the S3K host with `spindashEnabled=false`. The locked-on S&K disassembly arms `Obj_MGZDashTrigger` only when a contacting player's animation byte is 9. MGZ contains paired dash triggers and trigger platforms keyed by the same low subtype nibble. The reproduced MGZ1 mechanism has trigger index 7 at `(0x12D0,0x5C4)` and four following vertical trigger platforms using index 7 at X `$1320` through `$13E0`.

## Decision

First add a ROM-backed headless characterization using S1 player capability rules and sustained grounded rightward intent at the reproduced trigger/platform route. The characterization must prove that the missing capability prevents mechanism activation and blocks forward progress before any fallback is considered.

If the route blockage is not proven, stop with characterization evidence and no production change. If proven, preserve the native animation-9 arm path exactly and add a fallback only for players whose effective `PlayerCapabilityRules.spindashEnabled()` is false. The fallback requires sustained grounded intentional movement/pushing toward the adjacent trigger for a test-derived threshold. It must not branch on zone, donor game name, route, frame number, or character class.

Any new per-player counter is keyed by stable player identity through a captured map and is cleared when intent, grounded state, adjacency, or capability eligibility ends. Native S3K traces must remain unchanged.

## Verification

- Paired native-spindash and no-spindash route tests.
- Focused dash-trigger/platform and rewind tests.
- Rewind coverage and static-state coverage guards.
- Isolated MGZ complete-run trace.
- The pre-existing short MGZ trace input-alignment mismatch at frame 33271 remains out of scope and trace data is read-only.

## Verification Results

The ROM-backed route test proved the S1-capability player stopped at centre X `$12BD` before the `$12D0` trigger with index 7 and all paired platforms inactive. The 12-frame grounded-intent fallback opens the same real mechanism after collision-established landing. Focused tests and rewind guards pass. The complete-run trace requires a 3 GB test heap and matches the exact `a68084f79` baseline: 10,308 existing errors, first at frame 866 (`tails_status_byte` expected `$02`, actual `$03`). The shorter MGZ trace retains its pre-existing frame-33271 input-alignment error.
