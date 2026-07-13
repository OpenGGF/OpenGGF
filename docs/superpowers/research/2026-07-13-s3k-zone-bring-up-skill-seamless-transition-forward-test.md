# S3K Seamless-Transition Skill Forward Test

## Exact Evaluation Scenario

```text
Read ONLY {SKILL_PATH}. Do not inspect code, disassembly, research, plans, tests, or other skills. Do not edit files or run traces. Scenario: an S3K seamless same-zone act transition is driven by an event routine that requests an in-place reload, resumes synchronously in the same event call, and is followed by later loader phases. Some state survives the reload while only a narrower subset of live entities is eligible for coordinate offsets. The transition mutates Current/Apparent zone-act identity, title/music ownership, an all-live object graph with native slot semantics and separate bit/range offsets plus anchors, live camera bounds and camera-copy coordinates, rings and placed objects on different frame boundaries, and an end-control sequence whose control restoration and boundary release occur in different phases. State the mandatory oracle analysis, architecture, implementation tests, rewind rules, and executable multi-sidekick/widescreen/cross-game-donation acceptance that the skill explicitly requires. Then list anything the skill does not explicitly require. Return read-only prose only.
```

The baseline evaluator used the untouched `.agents` mirror. The forward evaluator was a fresh agent with no baseline context and used the updated `.claude` mirror.

## Baseline — Untouched Skill

- `.agents/skills/s3k-zone-bring-up/SKILL.md` SHA-256: `0A0F82F1C53B2CFE5C8AA1085A8740707227ECD5ADFDC2DE114B38DCC76B8ACA`
- `.claude/skills/s3k-zone-bring-up/SKILL.md` SHA-256: `0A0F82F1C53B2CFE5C8AA1085A8740707227ECD5ADFDC2DE114B38DCC76B8ACA`
- Mirror status: byte-identical.

### Baseline Raw Result

```text
The skill’s mandatory requirements are broad bring-up gates, not a complete transition-specific contract.

Mandatory oracle analysis:

- Use locked-on S3K disassembly behavior as the oracle, defaulting to S&K-half sonic3k.asm addresses and verifying any rare S3-half fallback.
- Analyze both acts’ Dynamic_Resize, ScreenInit, ScreenEvent, BackgroundInit, BackgroundEvent, deform/background handlers, custom animated-tile wrappers, AnPal, and all zone-specific routines reachable from them.
- Catalogue event flow including bounds, camera locks, cutscenes, seamless act transitions, music/SFX timing, checkpoints, exits, reset/rewind behavior, and next-zone transition.
- Produce counted placement inventories and a complete dynamic-spawn graph, including transition, controller, cleanup, child, boss, capsule, and exit objects.
- Trace shared routines, callback/function-pointer behavior, field widths, same-call fallthrough/tail-jump semantics, native signed timer boundaries, competing consumers, and exact frame phase before assigning semantics.
- Classify player participation separately for activation, targeting, contact/hazard, damage authority, forced movement, and completion; preserve distinctions between P1-only, native P1/P2 selection, and all-player operations.
- Determine exact native coordinate semantics and lifecycle/allocation behavior instead of deriving behavior from zone, route, frame, or convenience assumptions.

Mandatory architecture:

- Shared event/transition state belongs in a typed ZoneRuntimeRegistry adapter.
- Tile/block/chunk changes route through ZoneLayoutMutationPipeline, directly or through S3kSeamlessMutationExecutor.
- Objects use ObjectServices.
- Forced/control state uses ObjectControlState; participant selection uses allocation-free ObjectPlayerQuery with ObjectPlayerParticipationPolicy; playable native-position writes use NativePositionOps; destruction, despawn, remembered placement, and slot transfer use ObjectLifetimeOps.
- Participant state must be keyed by playable identity, never current list order or a fixed native-slot-sized array.
- Parent/child or otherwise graph-linked families must capture every role, phase, radius, delay, special flag, and recreation detail; relinking occurs only after scalar restoration settles.
- New state must have deterministic reset, checkpoint/death restore, session reload, and rewind behavior.
- Native behavior is implemented first; compatibility support must not alter the native path.

Mandatory implementation tests:

- Every behavior task is RED–GREEN–REFACTOR: focused failing test first, recorded expected failure, minimum disassembly-backed implementation, focused and regression GREEN runs, then specification and code-quality review.
- Freeze known-red trace baselines before shared changes and reject first-error-frame/error/warning regressions.
- Focused tests must exercise exact last-non-firing and firing boundaries for signed timers/callbacks, including competing consumers where relevant.
- Object graphs require a real ObjectManager capture → remove/diverge → restore test proving exact members/configuration/topology, settled relinks regardless of recreation order, no duplicates, and correct restored lifetime. Constructor-only probes are insufficient.
- Transition work must have focused coverage for events, objects, coordinate semantics, participation, collision, control/lifecycle, reset, checkpoint, session, and rewind behavior.
- Both acts and all native routes must complete without bypasses; the exact native route must be rerun after extension cases.
- Late complete-run trace replay is comparison-only—controller input only, with no trace hydration, tolerances, carve-outs, or known-trace exceptions—and mandatory stable-retro checkpoints must pass.

Mandatory rewind rules:

- Run both rewind coverage guards plus focused capture/restore round trips.
- Capture exact state and counter/frame phase, with relinking after scalar restoration.
- Graph-linked recreation must use parent-free probe/recreate shells where required; clear stale links, rebuild only the captured contiguous prefix, and close cycles only when their true terminal role exists.
- Verify death/restart, checkpoint, and session-reload semantics wherever those lifecycles cross the transition.
- Preserve native state domains and exact ownership rather than inventing richer convenience state.

Executable compatibility acceptance:

- Multi-sidekicks: test 0–3 sidekicks and duplicate-character banks; explicitly cover three-or-more-character shared mutable event state, native participation authority, solids/hazards, carriers, forced movement, bosses, transitions, and full-route completion. Reset must recreate a pristine graph with no static control-bit or link leakage.
- Widescreen: test every supported width and explicitly record world-coordinate event thresholds, camera lock/release, boss-arena containment, spawn/cull behavior, screen-edge transitions, premature event/object activation, and unsafe falls/deaths. A generic “route passed” result is invalid.
- Cross-game donation: test donation off and every external donor supported by the host, complete mandatory routes, identify ability-dependent blockers, and add only documented semantic capability/profile workarounds. Donation-off native behavior must remain unchanged.
- After all extension cases, rerun the strict locked-on native suite with donation and extension modes disabled.

The skill does not explicitly require:

- A transition-specific oracle of the in-place reload’s exact call stack, the fact that execution resumes synchronously in the same event invocation, or the ordering of later loader phases.
- Named semantics or update order for Current versus Apparent zone/act identity.
- Exact rules for title-card and music ownership across this reload.
- A requirement that the transition traverse every live entity, nor the precise eligible subset for coordinate offsets.
- Exact native-slot enumeration, separate bit-offset versus range-offset algorithms, anchor handling, or their ordering.
- Named camera-bound fields, camera-copy coordinates, or precise offset timing for those fields.
- The differing frame boundaries for ring offsets versus placed-object offsets.
- The exact phase split between end-control restoration and boundary release.
- Concrete numerical expected values, configuration strings, fixture names, or commands for the multi-sidekick/widescreen/donation acceptance matrix.

Those scenario-specific invariants would therefore need to be added to the transition specification and RED tests; they cannot be claimed as explicit requirements of this SKILL.md alone.
```

| ID | Baseline | Evidence |
|---|---|---|
| R50 | FAIL | The evaluator explicitly found no exact call stack, synchronous same-event continuation, event tail, or later loader ordering. |
| R51 | FAIL | Survival and coordinate-offset eligibility were not separate required domains. |
| R52 | FAIL | No all-live original-slot rebuild, bit/range predicate, graph-before-offset, or anchor ordering was explicit. |
| R53 | FAIL | Current/Apparent, title, music, and transition-art ownership were not explicit. |
| R54 | FAIL | Camera-copy fields, live bounds, preserved targets, and timing were not explicit. |
| R55 | FAIL | Same-frame ring versus next-frame placement cadence was not explicit. |
| R56 | FAIL | Early control restore versus late bound release was not explicit. |
| R57 | FAIL | General rewind coverage existed, but no synchronous-reload hard boundary or prohibition on half-reload/cross-boundary seek. |
| R58 | FAIL | Broad multi-sidekick audit existed, but not once-only transition survival/offset/control restoration at this owning boundary. |
| R59 | FAIL | Broad widescreen audit existed, but not shifted copies/bounds plus ring/placement windowing at this transition boundary. |
| R60 | FAIL | Broad donation auditing existed, but not the identical reload/title/control path with raw-name prohibition at this boundary. |

Baseline verdict: **RED, 0/11**.

## Forward — Updated Skill

- `.agents/skills/s3k-zone-bring-up/SKILL.md` SHA-256: `0721B4BA56AC0F6AFEEA4C7451E01AF25DA801AB1717A3D4BC1B01FCEB2E91F9`
- `.claude/skills/s3k-zone-bring-up/SKILL.md` SHA-256: `0721B4BA56AC0F6AFEEA4C7451E01AF25DA801AB1717A3D4BC1B01FCEB2E91F9`
- Mirror status: byte-identical.

### Forward Raw Result

```text
The skill’s mandatory transition contract is:

Oracle analysis:

- Use the locked-on S3K disassembly as the oracle, with S&K-side addresses by default.
- Trace the entire native frame and loader continuation, not merely the reload request: exact publisher and consumer; whether reload completes synchronously inside the active ScreenEvent or background-event call; which tail of that event continues afterward; which loader phases run later in the same frame versus the next frame.
- An asynchronous design is acceptable only if it reproduces the same in-call continuation and every subsequent phase boundary.
- Determine four independent native sets: state surviving reload; entities retaining their original logical slots; entities eligible for coordinate offsets; ring/placement state deliberately reinitialized.
- Derive from the disassembly: offset predicate bits; start-inclusive/end-exclusive slot range; selected current/copy camera axes; live bounds to shift; axes and target bounds to preserve; anchor/origin/target adjustments; identity, title, music, PLC/art, control, completion, and bound-release ownership; exact ring and placed-object frame boundaries.
- Audit explicit transition PLC/art operations against later loader aliases and identify the sole native owner of every upload.

Required architecture:

- Do not use offset eligibility as the survival/carry filter.
- If native object RAM remains live but the engine rebuilds objects, capture every live fixed/SST occupant in its original logical slot, including occupants outside the offset range and occupants failing the bit predicate.
- Restore the complete owner/child/link graph before applying offsets.
- Apply each eligible entity’s native centre-coordinate, subpixel-preserving offset exactly once.
- Apply separate anchor/origin/target hooks exactly once.
- Preserve the requested offset range as start-inclusive/end-exclusive and separate from survival policy.
- Publish state in native ownership order: Current identity changes at the reload point; Apparent/presentation identity remains unchanged until its later owner publishes it; results/title routines retain title-card creation/conversion and music stop/restore; a broad transition callback cannot substitute for these owners.
- Before target defaults load, capture live camera position, camera-copy coordinates, current bounds, and target bounds.
- Shift only disassembly-selected current/copy axes and live bounds. Preserve other axes and target bounds; do not replace live bounds with full destination entry defaults.
- Use the actual viewport wherever later windowing or locks depend on screen width.
- Keep end-control ownership phased: results/presentation exit may open the gate allowing the existing controller to restore all participating players while the title remains active; title wait owns only oracle-proven resets, music restoration, and routine advancement; a later timer/child-completion phase publishes completion; only the native completion consumer starts gradual live-bound workers.
- More generally, transition state must use the runtime-owned frameworks where applicable, have deterministic reset/checkpoint/death/session behavior, and preserve object participation, native coordinates, control, lifecycle, and graph semantics through the shared object/runtime vocabulary.

Mandatory implementation tests:

Every behavior task is test-first: focused RED, minimum disassembly-backed GREEN, refactor, focused/regression GREEN, then spec-compliance and code-quality review.

For this transition, tests must exercise the real frame pipeline and prove:

- synchronous same-call reload and continued event tail;
- correct later loader phase boundaries;
- every live occupant survives at its original slot;
- complete graph relinking precedes offsetting;
- eligible entities receive range-and-bit offsets exactly once;
- ineligible/out-of-range survivors remain carried but unshifted;
- anchor/origin/target hooks run exactly once;
- Current and Apparent identities publish in their distinct phases;
- title/music ownership is not moved early;
- PLC/art uploads occur once at their native owners;
- camera position, copy coordinates, current/live bounds, and preserved axes/targets match the oracle;
- ring loading in the transition frame uses the post-offset camera;
- object placement waits until the next frame when sprite processing has already occurred;
- fresh ring/placement respawn state;
- no restart, fade, reload title, duplicate art upload, or premature music work;
- controls are already restored while title completion remains blocked and bounds have not begun changing;
- the exact first live-bound growth and per-update growth rate after the true completion consumer.

Focused coverage must also include reset, checkpoint, session, and rewind behavior, including the two rewind coverage guards and capture/restore round trips for new runtime/object state.

Rewind rules:

- A completed synchronous reload is a hard rewind boundary.
- Take deterministic snapshots immediately before reload publication and immediately after the full in-call reload completes.
- Never seek through a half-executed reload.
- Never seek across the transition boundary.
- Verify checkpoint/death restart independently on both sides, covering Current/Apparent identity, original slots and graph links, camera-copy coordinates and bounds, loader phase, and title/control ownership.
- New state must have deterministic rewind capture and graph relinking as well as reset, checkpoint/death, and session-reload behavior.

Executable compatibility acceptance:

- Multi-sidekick: offset and later restore controls for every configured sidekick exactly once; preserve native signal authority and daisy-chain state; test more than two characters and duplicate characters; the final audit covers 0–3 sidekicks plus duplicate-character banks and explicitly records shared mutable transition state.
- Widescreen: test every supported viewport width; prove post-shift world bounds, camera copies, ring and placement windowing, and event thresholds use the actual viewport; prove no early activation or unsafe gaps; record per-width threshold behavior, premature activation, unsafe falls/deaths, and camera lock/release behavior rather than a generic route pass.
- Cross-game donation: run donor disabled and every supported donor through the same reload/title/control path; complete mandatory routes; add an ability workaround only if a mandatory route is genuinely blocked; any workaround must be semantic capability/profile work with the blocked route and rationale documented; never branch on raw game name; preserve native behavior with donation disabled.
- After extension cases, rerun the exact native transition with donation and extension modes disabled. The broader final audit also requires rerunning the strict locked-on native suite.

Not explicitly required by the skill:

- Concrete class names, method signatures, serialization formats, or a particular test-class name for the transition.
- Exact numeric offset bits, slot bounds, camera deltas, event thresholds, viewport widths, timer values, or live-bound growth rates—those must be derived from the disassembly/native oracle.
- One specific internal mechanism for synchronous reload, provided exact in-call continuation and phase boundaries are preserved.
- Offsetting every surviving entity or carrying only offset-eligible entities.
- Changing Current and Apparent identity together.
- Loading full destination camera defaults over live bounds.
- Seeking through or across the reload boundary.
- Showing a reload title, fading, restarting, duplicating art uploads, or changing music early—in fact, the required tests exclude these.
- Restoring controls and releasing bounds in the same phase.
- A donation ability workaround when the native mandatory route is not blocked.
- Raw game-name branches.
- A performance benchmark or allocation target specifically for this transition.
- A new zone-local transition framework when existing runtime-owned facilities suffice.
```

| ID | Forward | Evidence |
|---|---|---|
| R50 | PASS | Exact same-call reload, continued event tail, asynchronous-equivalence standard, and later loader phases are mandatory. |
| R51 | PASS | Four independent survival/slot/offset/fresh-state sets are mandatory. |
| R52 | PASS | All-live original slots, settled graph, range/bit predicate, centre/subpixel offset, and once-only anchors are mandatory. |
| R53 | PASS | Current/Apparent, title/music, and unique PLC/art ownership are explicit. |
| R54 | PASS | Live camera/current-copy and current/target bounds are captured before defaults and selectively shifted/preserved. |
| R55 | PASS | Ring initialization is transition-frame and placement is next-frame through the real pipeline. |
| R56 | PASS | The evaluator requires the controls-restored/bounds-unchanged window and later exact first-growth/rate boundary. |
| R57 | PASS | Completed reload is a hard boundary; half-reload and cross-boundary seeks are forbidden with two-sided restart tests. |
| R58 | PASS | More-than-two/duplicate sidekicks shift and regain control exactly once while native authority and daisy-chain state survive. |
| R59 | PASS | Every viewport proves shifted copies/bounds and ring/placement windowing with actual dimensions and safety checks. |
| R60 | PASS | All donors use one path, raw game-name branches are forbidden, workarounds require proven blockers, and native is rerun. |

Forward verdict: **GREEN, 11/11**, after baseline **RED, 0/11**.

No evaluator edited files or ran implementation or trace validation.
