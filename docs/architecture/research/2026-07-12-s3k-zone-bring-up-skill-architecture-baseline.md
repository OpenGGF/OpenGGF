# S3K Zone Bring-Up Skill Retained-Plane Baseline

## Evaluation scenario

A fresh read-only agent received only the pre-update `s3k-zone-bring-up` skill and was asked for mandatory checks when a zone uses staged Plane-B redraws, target-vs-normal palette writes during a fade, Plane-A runtime mutations, and mid-transition rewind.

## Exact evaluation prompt

```text
Read only <repo>\.worktrees\fbz-complete-execution\.agents\skills\s3k-zone-bring-up\SKILL.md. Do not read other repo docs or code. Answer this evaluation scenario only: A new S3K zone uses staged Plane-B row/column redraws, target-vs-normal palette writes during an overlay fade, Plane-A runtime mutations, and rewind mid-transition. From the skill alone, state the mandatory architecture and validation checks an implementer must perform for retained VDP tilemap state, palette visibility, dirty invalidation, widescreen, act scoping, and real runtime rewind. Be concrete. Then score whether the skill explicitly requires each: (A) distinguish full CPU layout cache from fixed 64x32 VDP ring and preserve native plane size under widescreen; (B) layer-aware dirty invalidation so Plane-A writes cannot rebuild retained Plane B; (C) target-palette writes must have a real visible fade consumer/materialization, not dead metadata; (D) retained state must be act/mode scoped and survive the next ensure/render; (E) real ROM-backed tall-cache capture→restore→reconcile→next-render test rather than synthetic helper-only tests. Mark PASS only if explicit, not inferred.
```

## Raw agent output

```text
From the skill alone, the implementer is explicitly required to:

- Put shared event/object/scroll state in a typed ZoneRuntimeRegistry adapter.
- Route timer-driven palette work and event-driven palette mutations through PaletteOwnershipRegistry.
- Route Plane-A tile/block/chunk edits through ZoneLayoutMutationPipeline, directly or via S3kSeamlessMutationExecutor.
- Route staged overlays and frame render flags through SpecialRenderEffectRegistry / AdvancedRenderModeController.
- Give all new runtime state deterministic reset, checkpoint/death restore, session reload, and rewind capture/relink behavior.
- Run both rewind coverage guards and focused capture/restore round-trip tests.
- Audit both acts' event entry points and nonstandard handlers during analysis.
- Use RED-GREEN-REFACTOR focused tests, then build/regression tests and stable-retro visual validation.
- Test every supported widescreen viewport width, explicitly recording world-coordinate thresholds, camera lock/release, spawn/culling, hazards, boss containment, premature activation, and unsafe falls/deaths.
- Validate both acts and rerun native locked-on tests after compatibility work.

However, the skill does not specify the detailed retained-tilemap architecture posed in the scenario. Strict explicitness scores:

A FAIL: It never distinguishes a full CPU layout cache from a fixed 64x32 VDP ring, nor says native plane dimensions must remain fixed under widescreen.
B FAIL: It requires Plane-A mutations to use the mutation pipeline, but never requires layer-aware dirty tracking or guarantees that Plane-A invalidation cannot rebuild retained Plane B.
C FAIL: It requires palette writes to use PaletteOwnershipRegistry, but never requires target-palette writes to be consumed/materialized by a visible fade path or rejects dead target metadata.
D FAIL: It requires deterministic reset, checkpoint/death restore, session reload, and rewind behavior, but does not explicitly require retained tilemap state to be act/mode scoped or to survive the next ensure/render operation.
E FAIL: It explicitly asks for focused capture/restore round-trip tests, but not a real ROM-backed tall-cache capture -> restore -> reconcile -> next-render test, and it does not reject synthetic helper-only coverage.

Bottom line: the skill mandates the high-level registries, mutation routing, lifecycle/rewind coverage, and compatibility validation, but all five retained-VDP-state requirements are absent at the requested level of specificity.
```

## RED result

The agent correctly recovered the existing high-level registry, mutation, rewind, and widescreen requirements, but scored all new explicit assertions as failures:

| Assertion | Result | Baseline finding |
|---|---|---|
| R13 | FAIL | No full CPU cache vs fixed 64x32 VDP ring distinction or native-size widescreen rule. |
| R14 | FAIL | No layer-aware dirty invalidation rule protecting retained Plane B from Plane-A writes. |
| R15 | FAIL | No requirement that target-palette metadata reach a visible fade/materialization consumer. |
| R16 | FAIL | No act/mode scoping or next ensure/render survival gate for retained state. |
| R17 | FAIL | No ROM-backed tall-cache lifecycle test; synthetic helper-only tests were not rejected. |

Overall: **RED, 0/5** for R13-R17.

This baseline is corroborated by the Task 5 review loop, which independently found each missing gate in production code.
