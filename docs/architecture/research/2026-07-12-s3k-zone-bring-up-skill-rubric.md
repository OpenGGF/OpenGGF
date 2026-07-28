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
| R37 | Boss damage-source classification | Every phase identifies the actual damage publisher and collision-bearing object; scripted child/self-impact damage requires negative player-hit and no-invented-shield-branch tests. |
| R38 | Repeated-table cardinality | Nested/repeated child tables are expanded with exact helper semantics and multiplied by every owner, with direct, steady, transient, and peak live counts reported separately. |
| R39 | Complete boss graph topology | Inventory includes ordered forward/reverse links, terminal cycles, and cross-links, with stable role metadata and recreation-order-independent relink. |
| R40 | Independent partial-prefix failures | Every child table is tested at each failure ordinal; successful prefixes persist exactly and missing suffixes do not heal, retry, roll back, duplicate, or suppress independent sibling tables unless the ROM does so. |
| R41 | Allocation primitive and first tick | Every allocation site records primitive/search direction and whether the allocated child first executes later in the same object sweep or next frame; focused tests prove the boundary. |
| R42 | Completion publication chain | Workflow follows boss defeat through sign/controller and results to the actual global/event writer and transition consumer; it forbids assuming the boss writes the event flag. |
| R43 | Per-phase participant policy | Activation, targeting, contact/hazard, damage authority, forced movement, and completion each receive an explicit policy; activation status/control bits are traced to their exact native slot rather than inheriting a shared solid routine's policy, preserving P1-only, nearest native P1/P2, and all-player distinctions under extension sidekicks. |
| R44 | Real cyclic graph restoration | A real `ObjectManager` capture -> remove/diverge -> restore test proves exact peak count/topology, cyclic/cross-link reconstruction, partial-prefix preservation without healing, and no duplicate reconstruction. |
| R45 | Callback execution and movement oracle | Each installed routine pointer is a distinct update state whose installer control transfer is recorded; signed byte/word and asymmetric timer boundaries, unsigned byte-angle endpoints/reflection, and the called helper's actual register-to-axis contract receive focused tests. |
| R46 | Routine-owned render/collision state | Priority, collision flags, solidity, and draw eligibility are tested at every owning transition, including defeat and converted-slot states; render suppression cannot silently suppress collision, and flicker retains native movement/gravity/delete behavior. |
| R47 | Two-phase rewind graph recreation | Parent-required graph members use a parent-free recreate shell when needed, restore role/family scalars without object references in phase 1, and clear/rebuild only the captured prefix in settled phase 2, closing cycles only when the captured terminal exists. |
| R48 | Native end-control lifecycle | End-of-level-in-effect, delayed sign allocation, and later results/event publication remain distinct; exact allocation primitive/order/success, converted-slot non-solid lifetime, and the real sign/results route handoff are tested without a substitute controller or boss-local flag. |
| R49 | Executable compatibility boundary | Multi-sidekick authority, widescreen world-coordinate locks, donation-neutral traversal, and object-manager/session reset leakage are executable tests at the owning feature boundary, followed by the exact native route with extensions disabled. |
| R50 | Synchronous event continuation | Seamless reload analysis records the exact publisher/consumer, completes reload inside the current screen/background-event invocation when native, resumes its remaining tail, and preserves later loader phases; a merely asynchronous request is rejected. |
| R51 | Survival versus offset domains | Workflow independently models surviving state, original-slot survival, coordinate-offset eligibility, and deliberately fresh ring/placement respawn state; the offset scan cannot act as a carry filter. |
| R52 | Original-slot graph and once-only offsets | Every live fixed/SST occupant is restored at its original logical slot, complete links settle before a separate start-inclusive/end-exclusive range plus bit-predicate offset, and centre/subpixel plus anchor/origin/target shifts occur exactly once. |
| R53 | Identity, title, music, and PLC ownership | Current identity changes at reload while apparent/presentation identity waits for its native publisher; results/title routines retain title and music ownership, and explicit transition art is not duplicated by loader aliases. |
| R54 | Live camera-copy and bound preservation | Workflow captures live camera current/copy coordinates and current/target bounds before target defaults, shifts only native-selected axes/live bounds, preserves the rest, and rejects replacement by full destination defaults. |
| R55 | Ring and placement loader cadence | Real-pipeline tests prove same-transition-frame ring initialization after screen events versus next-frame object placement after sprite processing, both windowed from the post-offset camera with their exact fresh-state semantics. |
| R56 | Early controls versus late bounds | Results/title/controller ownership is phase-separated: controls restore while title completion remains blocked; only the later timer/child-completion publisher releases gradual bound workers, with intermediate-state, first-growth, and rate tests. |
| R57 | Rewind hard boundary | A completed synchronous reload is a hard boundary with deterministic snapshots immediately before publication and after full in-call completion; seeking across or restoring a half-reload is forbidden, and restart is verified independently on both sides. |
| R58 | Transition multi-sidekick execution | More-than-two and duplicate-character tests prove every sidekick survives, shifts, and regains control exactly once with daisy-chain state intact while native transition authority remains native-only. |
| R59 | Transition widescreen execution | Every supported viewport proves shifted world bounds/camera copies, ring and placement windowing, and event thresholds use actual viewport dimensions without premature activation or unsafe gaps. |
| R60 | Transition donation execution | Donation off and every supported donor traverse the identical reload/title/control path without raw game-name branches or ability workarounds unless a mandatory route is proven blocked, followed by the native transition rerun. |

## Verdict

- **GREEN:** R1-R60 all PASS.
- **RED:** Any assertion fails or is only optional/implied.

Record the exact prompt, raw agent output, per-assertion PASS/FAIL result, and overall verdict in both baseline and forward-test artifacts.
