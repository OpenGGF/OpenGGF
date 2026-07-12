# S3K Zone Bring-Up Skill AnPal Gameplay-State Forward Test

## Exact evaluation prompt

```text
Read only C:\Users\farre\IdeaProjects\sonic-engine\.worktrees\fbz-complete-execution\.agents\skills\s3k-zone-bring-up\SKILL.md. Do not read other repository files. Evaluation scenario: an S3K AnPal routine does not write palette colors; instead, on selected counter edges it mutates one gameplay bit consumed by several objects. A proposed implementation invents a three-state enum and updates it after object updates. From the skill alone, state mandatory analysis, architecture, phase-order, lifecycle, and validation requirements. Score explicit PASS/FAIL for: (A) audit the complete consumer graph before treating AnPal as palette-only; (B) preserve the exact disassembly state domain and reject a richer three-state enum when the ROM stores one bit; (C) preserve exact ordering relative to counter increment and object updates; (D) test at least two consecutive qualifying edges plus rewind and death/restart where applicable. Mark PASS only if explicit, not inferred.
```

## Baseline raw output

```text
MANDATORY ANALYSIS
- Run zone analysis before implementation and explicitly audit both acts’ AnPal entry plus every zone-specific routine reached from the audited entry points.
- Record cross-cutting dependencies and the placed/dynamic object inventory, including object behavior/state dependencies, before dispatch.
- Determine AnPal applicability from the disassembly; do not classify it solely from a presumed palette role.

MANDATORY ARCHITECTURE
- Preserve locked-on disassembly behavior first.
- Shared event/object/scroll state belongs in a typed ZoneRuntimeRegistry adapter.
- Timer-driven palette work routes through PaletteOwnershipRegistry, but a gameplay bit is not explicitly directed there by this skill; its cross-object state should use typed runtime state.
- New runtime/object state requires deterministic reset, checkpoint/death restore, session reload, and rewind capture/relink behavior.

MANDATORY PHASE ORDER
- The skill contains no explicit requirement specifying AnPal counter-edge ordering relative to counter increment or object updates.
- General test-first/disassembly-parity requirements apply, but the exact phase relationship must be obtained from another skill/disassembly analysis.

MANDATORY LIFECYCLE
- Cover deterministic reset, checkpoint/death restore, session reload, rewind capture/relink.
- Run both rewind coverage guards and focused capture/restore round-trip tests.

MANDATORY VALIDATION
- RED-GREEN-REFACTOR for every behavior task, with a focused failing test before implementation.
- Run focused/regression tests and obtain spec-compliance/code-quality review.
- Preserve known-red baselines; defer complete-run trace until broad route implementation and keep it comparison-only.
- Perform late visual/native route validation and rerun strict locked-on native parity after compatibility work.

SCORES
(A) FAIL — complete AnPal gameplay-bit consumer-graph auditing before palette classification is not stated explicitly.
(B) FAIL — exact ROM behavior is required generally, but exact state-domain preservation and explicit rejection of a richer enum are not stated.
(C) FAIL — exact ordering relative to counter increment and object updates is not stated.
(D) FAIL — rewind and death/restart coverage are explicit, but testing at least two consecutive qualifying edges is not stated; therefore the full criterion fails.
```

Baseline verdict: **RED, 0/4** for R20-R23. In particular, the skill did not reject the proposed three-state model for a one-bit ROM field.

## Forward-test raw output

```text
MANDATORY ANALYSIS
- Audit both acts’ AnPal entry and every reachable zone-specific routine.
- Trace every AnPal write and the complete consumer graph before classification.
- Classify non-palette mutation under the real owning feature; do not skip it or force it into palette cycling.

MANDATORY ARCHITECTURE
- Put gameplay state with its actual runtime owner, using typed zone runtime state where shared.
- Do not route gameplay-only state through PaletteOwnershipRegistry or hide it in the palette cycler.
- Preserve the exact native state domain: a ROM bit remains a bit unless the disassembly proves more values.
- Reject the proposed three-state enum and any invented intermediate/convenience state.

MANDATORY PHASE ORDER
- Determine whether AnPal reads before or after counter increment.
- Determine whether consumers observe the value before or after object updates.
- Preserve that exact frame phase.
- Test the real counter-increment -> AnPal-dispatch -> object-update pipeline, not an isolated helper.

MANDATORY LIFECYCLE
- Capture and restore the exact gameplay bit and counter phase through rewind.
- Verify death/restart, checkpoint, and session reload behavior where those lifecycles can cross the transition.
- Run both rewind coverage guards and focused capture/restore round-trip tests.

MANDATORY VALIDATION
- Follow RED-GREEN-REFACTOR with the focused failure recorded before implementation.
- Test at least two consecutive qualifying edges.
- On each edge, assert the value observed by every consumer.
- Run focused/regression tests and spec/code-quality review; preserve known-red baselines.
- Defer complete-run trace until broad implementation and keep it comparison-only.

SCORES
(A) PASS — the updated skill explicitly requires tracing every write and the complete consumer graph before classifying AnPal.
(B) PASS — it explicitly says a ROM bit remains a bit and forbids a richer enum/intermediate state without disassembly proof.
(C) PASS — it explicitly requires preserving read-before/after-counter and consumer-before/after-object-update ordering and testing the real pipeline.
(D) PASS — it explicitly requires at least two consecutive qualifying edges plus rewind and death/restart/checkpoint/session lifecycle validation where applicable.
```

## Forward verdict

**GREEN, 4/4** for R20-R23 after the **RED, 0/4** baseline. The revised gate explicitly catches the prior three-state mistake and requires the corrected one-bit model, exact frame phase, consecutive-edge assertions, and lifecycle restoration.

Structural validation:

- `.agents` and `.claude` mirrors are byte-identical (SHA-256 `6A1DCB43C150F6034D8C997BC5D9E40F6CD349FC7A8CA81C4B14395DE5B24274`).
- Both mirrors pass `skill-creator/scripts/quick_validate.py` with UTF-8 mode.
- `git diff --check` passes.
