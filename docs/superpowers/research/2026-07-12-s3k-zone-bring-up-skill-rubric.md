# S3K Zone Bring-Up Skill Evaluation Rubric

## Exact Evaluation Prompt

```text
Use the repository's s3k-zone-bring-up skill for this request:

"Implement Flying Battery Zone completely and accurately from the locked-on S3K disassembly. Both acts, every event, object, badnik, boss, transition, and visual system are in scope. Explain the complete workflow you would follow from analysis through final acceptance. Include task ordering, artifacts, implementation gates, and validation. Do not edit files or run implementation; return the proposed workflow only."

Use only the skill and resources it explicitly requires. Do not infer additional requirements from unrelated project documents. Return a concrete ordered workflow and definition of done.
```

## Binary Assertions

| ID | Requirement | PASS condition |
|---|---|---|
| R1 | Nonstandard events | Workflow explicitly audits `Dynamic_Resize`, `ScreenInit`, `ScreenEvent`, `BackgroundInit`, `BackgroundEvent`, and other zone-specific handlers rather than assuming `_Resize` owns all events. |
| R2 | Complete content inventory | Workflow requires placed ID/subtype counts, shared placed objects, dynamic-spawn graph, badniks, bosses, children, art/PLC/VRAM handoffs, audio cues, transitions, checkpoints, and route-impact classification. |
| R3 | Route-driven delivery | Workflow implements playable Act 1/Act 2 slices in dependency order and identifies traversal blockers before polish. |
| R4 | Runtime-owned architecture | Workflow routes state and behavior through `ZoneRuntimeRegistry`, palette, animation, mutation, scroll, render, service, profile, lifetime, and rewind frameworks as applicable. |
| R5 | Test-first work | Every behavior task requires a failing focused test, observed RED result, minimal implementation, GREEN result, and recorded verification. |
| R6 | Trace-last ordering | Complete-run trace capture/replay is deferred until the zone and reachable content are broadly implemented; focused tests drive normal development. |
| R7 | Trace integrity | Trace validation is comparison-only and forbids engine hydration, tolerance masking, and route/frame carve-outs. |
| R8 | Multi-sidekick audit | A mandatory final audit covers more than two characters, duplicate-character banks, participant policy, shared state, forced movement, carriers, bosses, and transitions. |
| R9 | Widescreen audit | A mandatory final audit covers supported viewport widths, world-coordinate events, camera locks, spawn/culling, hazards, boss arenas, and unsafe falls/premature activation. |
| R10 | Cross-game donation audit | A mandatory final audit checks all donor capability profiles for route blockers and permits only explicit capability-driven workarounds that preserve native behavior. |
| R11 | Native regression after compatibility | Strict locked-on behavior is rerun after compatibility adaptations with donation/extension modes disabled. |
| R12 | Complete acceptance | Definition of done forbids reachable placeholders and requires events, objects, bosses, visual systems, audio, rewind/reset, focused tests, late trace/visual gates, compatibility audits, and regression preservation. |
| R13 | Native retained plane | Incremental plane redraw work explicitly distinguishes the full CPU cache from the fixed 64x32 VDP ring and preserves native dimensions/cadence under widescreen. |
| R14 | Layer-aware invalidation | Plane-A runtime mutations cannot invalidate/rebuild a retained Plane B; genuine Plane-B/layout invalidations explicitly clear retained authority. |
| R15 | Palette visibility lifecycle | Target-palette state must have a production fade consumer or safe opaque-screen materialization, with first-visible-frame and rewind tests; dead metadata is rejected. |
| R16 | Retained lifecycle/scoping | Retained state is act/mode scoped and survives real ensure/window/upload/render preparation after live writes and rewind restore. |
| R17 | Real runtime validation | A ROM-backed tall-cache capture -> mutate -> restore -> reconcile -> next-render test is required; synthetic helper-only coverage is insufficient. |
| R18 | Scalable carrier state | Carrier/grab state is keyed by playable identity, not fixed arrays or mutable player-list order, and hot-path participation remains allocation-free. |
| R19 | Dynamic-family rewind graph | Child role/config has stable recreation metadata and a real ObjectManager graph test proves exact relink, no duplication, and lifetime restoration. |
| R20 | AnPal consumer graph | Workflow traces every AnPal write through all consumers before classifying it as palette-only and assigns non-palette state to its real runtime owner. |
| R21 | Exact AnPal state domain | Workflow preserves the disassembly's exact state domain and explicitly rejects richer enums/intermediate states when the ROM stores one bit. |
| R22 | Exact AnPal frame phase | Workflow determines ordering relative to counter increment and object updates and tests the value consumers observe in the real frame pipeline. |
| R23 | AnPal edge/lifecycle validation | Workflow tests at least two consecutive qualifying edges and exact counter/state restoration through rewind plus death/restart/checkpoint/session lifecycles as applicable. |
| R24 | AnPal entry-point gates | Workflow audits `Palette_fade_timer` and every other entry guard, then proves whether a suppressed qualifying edge is skipped or deferred rather than assuming catch-up. |
| R25 | Gated pipeline validation | A real integrated counter-increment -> gated AnPal -> object-consumer test covers two qualifying edges, one suppressed edge, and the native catch-up/no-catch-up result after the gate clears. |
| R26 | Shared future-consumer API | The consumer graph includes present and planned object families (for example Blaster), and shared state/API lives at the runtime owner rather than inside the first consuming family. |
| R27 | Signed mapping offsets | S3K mapping-table relative `dc.w` frame pointers are decoded as signed 16-bit displacements from the table base; zero-extension is explicitly forbidden. |
| R28 | Backward/shared frame audit | Mapping intake explicitly audits negative/backward pointers and shared-frame references. |
| R29 | Explicit mapping frame count | When the first frame pointer cannot delimit the pointer table, a disassembly-verified explicit frame count is required instead of inferred length. |
| R30 | Real-ROM mapping-shape regression | A real-ROM test asserts exact table shape and catches runaway/oversized/OOM behavior; fixes must target the generic decoder or verified metadata, not heap workarounds or object-specific address cases. |
| R31 | Complete art corruption suite | New or changed mappings require both the complete ROM-conditional S3K art crawler and `TestPatternSpriteRendererCorruptionGuard`, in addition to focused table-shape coverage. |
| R32 | Shared callback graph | Object analysis traces every tail-jumped shared routine, callback/function pointer field, field width, and competing consumer before assigning timer or animation semantics. |
| R33 | Signed predecrement edge | Workflow derives `subq.w #1` plus `bmi` as firing on update `N+1`, after zero becomes `$FFFF`, and rejects unsigned/animation-derived substitutions. |
| R34 | Earliest reachable consumer | When raw animation `$F4` and `Obj_Wait` share a callback, the workflow compares both reachable paths and treats the earliest one as behavior-owning. |
| R35 | Boundary-first RED test | A focused observed-RED test covers the last non-firing update, exact firing update, and the competing consumer's later boundary. |
| R36 | Independent oracle adjudication | A code/comment/disassembly conflict pauses implementation for independent disassembly adjudication instead of weakening the expectation or choosing local behavior. |

## Verdict

- **GREEN:** R1-R36 all PASS.
- **RED:** Any assertion fails or is only optional/implied.

Record the exact prompt, raw agent output, per-assertion PASS/FAIL result, and overall verdict in both baseline and forward-test artifacts.
