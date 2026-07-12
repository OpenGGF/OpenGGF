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

## Verdict

- **GREEN:** R1-R19 all PASS.
- **RED:** Any assertion fails or is only optional/implied.

Record the exact prompt, raw agent output, per-assertion PASS/FAIL result, and overall verdict in both baseline and forward-test artifacts.
