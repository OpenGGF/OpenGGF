# S3K Zone Bring-Up Skill Forward Test

## Exact Evaluation Prompt

```text
Use the repository's s3k-zone-bring-up skill for this request:

"Implement Flying Battery Zone completely and accurately from the locked-on S3K disassembly. Both acts, every event, object, badnik, boss, transition, and visual system are in scope. Explain the complete workflow you would follow from analysis through final acceptance. Include task ordering, artifacts, implementation gates, and validation. Do not edit files or run implementation; return the proposed workflow only."

Use only the skill and resources it explicitly requires. Do not infer additional requirements from unrelated project documents. Return a concrete ordered workflow and definition of done.
```

## Iteration History

- Iteration 1: RED, 9/12. Missing explicit numeric placement counts, multi-sidekick shared-state verification, and widescreen safety assertions.
- Iteration 2: RED, 11/12. Missing an explicit world-coordinate event-threshold assertion in the widescreen report.
- Iteration 3: GREEN, 12/12. Run through a fresh read-only Claude sidecar because the native four-thread agent tree was exhausted by completed baseline/review children.
- Iteration 4: GREEN, 12/12. Run through a fresh read-only delegated forward-test agent against the current final skill bytes.

## Iteration 4 Raw GREEN Workflow

```text
1. Establish locked-on ground truth.

   - Treat FBZ as an S3KL locked-on zone.
   - Use `sonic3k.asm` and S&K-side ROM addresses below `0x200000` by default.
   - Resolve constants with `RomOffsetFinder --game s3k`.
   - Use an S3-half address only after verifying that no S&K equivalent exists.
   - Check live event, scroll, animation, palette, and object registrations rather than trusting the skill’s dated FBZ status snapshot.
   - Record existing focused-test, guard, and trace results. Freeze known-red traces by first-error frame, error count, and warning count.

2. Produce the analysis contract with `s3k-zone-analysis FBZ`.

   Audit both acts’ complete reachable routine graph:

   - `Dynamic_Resize`
   - `ScreenInit`
   - `ScreenEvent`
   - `BackgroundInit`
   - `BackgroundEvent`
   - Deform/background handlers
   - Custom animated-tile wrappers
   - AniPLC and AnPal handlers
   - Every other FBZ-specific routine reached from those entry points

   Do not assume `_Resize` owns all event behavior.

3. Create and normalize the analysis artifacts.

   Produce:

   - `docs/s3k-zones/fbz-analysis.md`
   - `docs/s3k-zones/fbz-object-inventory.md`

   Normalize the analysis with `ZoneSpecNormalizerTool` into the stable 13-section layout before review. Keep palette cycling separate from event-driven palette mutation and mark genuine research gaps `(not analyzed)`.

4. Build the complete content inventory.

   Parse every locked-on placement file and record:

   - Numeric total placements for each act
   - Numeric count for every ID/subtype row
   - S3KL pointer resolution
   - Shared versus FBZ-specific status
   - Route impact: blocker, high-impact, or polish
   - Factory or placeholder status
   - Mappings, animation, art, PLC/KosinskiM and VRAM dependencies
   - Audio cues
   - Checkpoint interaction
   - Allocation primitive
   - Test owner

   Add a complete dynamic-spawn graph for event objects, badnik children, projectiles, bosses and boss children, transitions, capsules/exits, debris, and cleanup objects.

5. Trace cross-category dependencies.

   Catalogue the RAM flags, palette entries, VRAM destinations, art loads, event counters, render modes, and layout state written or consumed by events, objects, bosses, deform logic, animation, palette code, and transitions. Record each producer, consumer, gating condition, ownership handoff, and reset/rewind lifetime.

6. Pass the human analysis-review gate.

   Present:

   - Event-stage counts for both acts
   - Parallax band/mode count
   - Animated-tile script count
   - Palette-cycle channel count
   - Confidence for each category
   - Numeric placement totals and per-ID/subtype counts
   - Nonstandard handlers
   - Traversal blockers
   - Shared and FBZ-specific objects
   - Badniks, bosses, children, and projectiles
   - PLC/art/VRAM handoffs
   - Music and SFX timing
   - Checkpoints, exits, capsule, and next-zone transition
   - Reset, session, checkpoint, and rewind concerns

   The gate fails if any act, placement file, reachable handler, dynamic spawn, boss child, transition, or dependency remains unaccounted for.

7. Decide feature scope from the accepted analysis.

   Mark each category `IMPLEMENT`, `VALIDATE`, or `SKIP`:

   - Events always receive a correct handler, even if the ROM routine is minimal.
   - Implement parallax only if FBZ has a unique deform path; otherwise use the proven shared path.
   - Implement AniPLC/custom DMA only when the disassembly shows it exists.
   - Validate a substantive existing AnPal implementation; implement it if missing; skip only if the ROM proves it is `rts`.
   - Keep timer-driven palette cycling separate from camera/event-driven palette mutation.

8. Assign runtime ownership before coding.

   Route applicable behavior through:

   - `ZoneRuntimeRegistry` for typed shared event/object/scroll state
   - `PaletteOwnershipRegistry` for palette cycling and mutations
   - `AnimatedTileChannelGraph` and `S3kAnimatedTileChannels` for animated art
   - `ZoneLayoutMutationPipeline` or `S3kSeamlessMutationExecutor` for layout edits
   - `SpecialRenderEffectRegistry` and `AdvancedRenderModeController` for overlays/render modes
   - `ScrollEffectComposer`, `DeformationPlan`, `ScatterFillPlan`, and `WaterlineBlendComposer` for deform math
   - `ObjectServices` for object dependencies
   - `ObjectControlState` for forced/control bits
   - `ObjectPlayerQuery` and `ObjectPlayerParticipationPolicy` for player selection and extension-sidekick policy
   - `NativePositionOps` for playable native coordinates
   - `ObjectLifetimeOps` for deletion, despawn, remembered placement, and slot transfer
   - Canonical solid, touch-response, and lifecycle profiles through compatibility wrappers

   Every new runtime and object field must define deterministic initialization, reset, checkpoint/death restore, session reload, and rewind capture/relink behavior.

9. Build route-driven implementation waves.

   Wave 1: foundations

   - Typed FBZ runtime state
   - Event ownership
   - Collision/render modes
   - ROM constants
   - Art, PLC, animation, palette, and parallax foundations

   Wave 2: Act 1 route

   - Traversal blockers, doors, launchers, carriers, forced movement, hazards, and terrain mutations
   - Camera and boundary events
   - Act 1 boss/miniboss gate and support objects
   - PLC/music handoff
   - Defeat and seamless Act 1-to-Act 2 transition

   Wave 3: Act 2 route

   - Act 2 blockers, carriers, hazards, badnik dependencies, event gates, visual-mode changes, and boss prerequisites
   - Checkpoint and death restoration

   Wave 4: final boss and exit

   - Boss parent, children, projectiles, arena, hit behavior, defeat/explosion sequence, and cleanup
   - Capsule/exit sequence
   - Music/results handling
   - Next-zone transition

   Wave 5: reachable-content completion

   - Every remaining reachable object/subtype
   - Every badnik and dynamic spawn
   - Visual/audio polish
   - No reachable placeholder may remain after this wave

   Wave 6: late trace and stable-retro validation

   Wave 7: multi-sidekick, widescreen, and donation audits

   Wave 8: strict native regression with compatibility features disabled

   Each wave names disjoint file ownership, dependencies, tests, verification commands, and reviewer checks. Only independent tasks within a wave run in parallel.

10. Apply the test-first gate to every behavior task.

    Every task must:

    1. Add one focused failing test.
    2. Run it and record the expected RED result.
    3. Implement the minimum disassembly-backed behavior.
    4. Run the focused test to GREEN.
    5. Run relevant regression and guard tests.
    6. Refactor while retaining GREEN.
    7. Pass spec-compliance and code-quality review.

    Tests added only after implementation do not pass. Object/badnik blockers also receive the `AgentWorkflowTool` preflight.

11. Dispatch work by specialist ownership.

    - Events: `s3k-zone-events`
    - Parallax: `s3k-parallax`
    - Animated tiles: `s3k-animated-tiles`
    - Palette cycling: `s3k-palette-cycling`
    - Every object/badnik family: `s3k-implement-object`
    - Each boss sequence: `s3k-implement-boss`
    - Level, boss, and transition art handoffs: `s3k-plc-system`

    Each worker receives the analysis path, inventory rows, ROM labels, file ownership, failing test, verification command, dependencies, and S&K-side address rule. Dependent work waits for implementation plus two-stage review to become green.

12. Integrate in dependency order.

    Merge events first, then parallax, animated tiles, palette cycling, PLC/art handoffs, objects/badniks, bosses, and transitions. Combine additive constants and registrations. Prefer the events implementation for event-state conflicts and the feature owner for feature-specific conflicts.

13. Verify each integrated wave.

    Run:

    ```powershell
    mvn package
    mvn "-Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils" test
    mvn "-Dtest=Test<FBZFocusedTests>" test "-Ds3k.rom.path=s3k.gen"
    ```

    Keep applicable state/transition guards green. Run both rewind coverage guards and focused capture/restore round-trip tests. Inventory-derived baselines may document historical exceptions, but guards must not be weakened for new shortcuts.

14. Validate focused behavior and visuals.

    Use `s3k-zone-validate FBZ` after major reachable content exists. Compare stable-retro and engine output at:

    - Act starts
    - Event and camera transitions
    - Parallax/render modes
    - Animated-tile states and gating
    - Palette cycles and one-shot mutations
    - PLC/VRAM handoffs
    - Boss entrances, arenas, and defeat states
    - Act transition
    - Capsule/exit and next-zone transition

    Validation must demonstrate route continuity and required mechanics, not merely one matching screenshot. Every required checkpoint receives PASS or an investigated FAIL.

15. Run complete-run trace polish last.

    Only after both acts and all reachable content are broadly implemented:

    - Record/install the complete-run BK2 segment and replay fixture.
    - Replay controller input only.
    - Use trace state solely as comparison context; never hydrate engine state from trace data.
    - Move the first divergence through disassembly-backed fixes.
    - Forbid tolerance masking, zone/route/frame carve-outs, and known-trace exceptions.
    - Update `docs/TRACE_FRONTIER_LOG.md` whenever the frontier moves or a green trace regresses.

16. Perform mandatory compatibility audits after native parity.

    Multi-sidekick:

    - Test 0–3 sidekicks, including more than two characters.
    - Test duplicate-character pattern banks.
    - Verify participant policy and shared mutable event/boss state.
    - Cover solids, hazards, carriers, forced movement, bosses, transitions, and full-route completion.

    Widescreen:

    - Test every supported viewport width.
    - Record world-coordinate event-threshold behavior.
    - Verify camera locks/releases, spawning/culling, hazards, boss-arena containment, screen-edge transitions, premature activation, and unsafe falls/deaths.

    Cross-game donation:

    - Test donor off and every supported external donor/capability profile.
    - Complete mandatory routes and identify unavailable-ability blockers.
    - Permit only explicit capability-driven workarounds with documented route and rationale.
    - Preserve native behavior when donation is off.

17. Rerun strict locked-on acceptance.

    Disable donation and extension modes, then rerun the native locked-on suite. Compatibility adaptations must not change the native path, and existing regression baselines must remain preserved.

### Definition of done

FBZ is complete only when:

- Every applicable event entry point and nonstandard handler for both acts matches the locked-on disassembly.
- Every reachable placed ID/subtype and dynamic spawn has a concrete correct factory.
- No reachable placeholder remains.
- Traversal objects, hazards, badniks, bosses, children, projectiles, checkpoints, transitions, capsule/exit behavior, art/PLC/VRAM handoffs, audio, palette, animation, parallax, collision, and render behavior are implemented.
- Reset, checkpoint/death restore, session reload, and rewind behavior have focused test-first coverage.
- Both acts and all native character routes complete without debug bypasses.
- Focused tests, required regressions, guards, rewind round trips, and builds are green.
- Mandatory stable-retro checkpoints pass.
- Complete-run replay is comparison-only and green.
- Multi-sidekick, widescreen, and cross-game donation audits pass.
- The final compatibility-disabled locked-on suite passes and prior regression baselines are preserved.
```

## Iteration 4 Raw GREEN Rubric Result

| ID | Result | Evidence |
|---|---|---|
| R1 | PASS | Step 2 explicitly audits `Dynamic_Resize`, `ScreenInit`, `ScreenEvent`, `BackgroundInit`, `BackgroundEvent`, deform/background handlers, custom animation wrappers, and reached FBZ-specific routines. |
| R2 | PASS | Steps 3–5 require numeric placement and subtype counts, shared objects, route classification, dynamic spawns, badniks, bosses/children, PLC/VRAM, audio, checkpoints, and transitions. |
| R3 | PASS | Step 9 orders foundations, Act 1 route, Act 2 route, final boss/exit, then remaining content and polish. |
| R4 | PASS | Step 8 explicitly assigns state and behavior to runtime, palette, animation, mutation, scroll, render, service, object-control, participation, profile, lifetime, coordinate, and rewind frameworks. |
| R5 | PASS | Step 10 requires focused test, observed RED, minimum implementation, GREEN, recorded regression/guard verification, refactor, and review for every behavior task. |
| R6 | PASS | Steps 14–15 defer complete-run tracing until both acts and reachable content are broadly implemented; focused tests are the normal development loop. |
| R7 | PASS | Step 15 makes replay comparison-only, prohibits hydration, tolerance masking, and zone/route/frame or known-trace exceptions. |
| R8 | PASS | Step 16 mandates 0–3 sidekicks, more than two characters, duplicate banks, participant policy, shared state, forced movement, carriers, bosses, and transitions. |
| R9 | PASS | Step 16 mandates every viewport width plus world-coordinate thresholds, camera locks, spawn/culling, hazards, boss containment, unsafe falls, and premature activation. |
| R10 | PASS | Step 16 checks donor off and every donor/capability profile, identifies route blockers, and permits only explicit capability-driven adaptations preserving donor-off behavior. |
| R11 | PASS | Steps 16–17 rerun strict locked-on behavior after compatibility work with donation and extension modes disabled. |
| R12 | PASS | The definition of done forbids reachable placeholders and covers events, objects, badniks, bosses, transitions, visuals, audio, reset/rewind, tests, trace, stable-retro, compatibility, and regression preservation. |

Overall verdict: **GREEN**

## Iteration 4 Reproducibility Provenance

- Execution: fresh read-only delegated forward-test agent; it did not edit files or run implementation.
- Primary skill: `.agents/skills/s3k-zone-bring-up/SKILL.md`
- Primary SHA-256: `631A7BE6F0A24B4261D83FC7C1A17F53960C0AA46E5F3FD9A1F9473D062DBD68`
- Mirror skill: `.claude/skills/s3k-zone-bring-up/SKILL.md`
- Mirror SHA-256: `631A7BE6F0A24B4261D83FC7C1A17F53960C0AA46E5F3FD9A1F9473D062DBD68`
- Mirror status: byte-identical.

Reproduce structural validation from the repository root with UTF-8 mode explicitly enabled:

```powershell
$env:PYTHONUTF8='1'
python <user-config>\.codex\skills\.system\skill-creator\scripts\quick_validate.py .agents\skills\s3k-zone-bring-up
python <user-config>\.codex\skills\.system\skill-creator\scripts\quick_validate.py .claude\skills\s3k-zone-bring-up
```

## Supersession Note

Iteration 4 supersedes the prior `Raw GREEN Workflow` and `Raw GREEN Rubric Result` sections while preserving their historical Iteration 3 verdict above.
