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

## Incremental entry-gate evaluation

### Exact evaluation prompt

```text
Read only the s3k-zone-bring-up skill. An S3K AnPal gameplay-state mutation is reached only when Palette_fade_timer is zero. A qualifying counter edge occurs while fading; later Blaster-family objects will consume the same state. State the mandatory analysis, ownership, ordering, and integrated tests. Score explicit PASS/FAIL: R24 audits every entry gate and distinguishes a permanently skipped edge from deferred/catch-up work; R25 requires a real counter-increment -> gated AnPal -> object-consumer pipeline test with two qualifying edges, suppression, and the post-gate catch-up/no-catch-up result; R26 includes planned consumer families such as Blaster and keeps the API shared at the runtime owner rather than family-local. Mark PASS only if explicit, not inferred.
```

### Baseline raw output (prior gate)

```text
R24: FAIL — the gate required exact counter phase and qualifying edges, but did not require auditing Palette_fade_timer or other entry guards and did not distinguish a skipped edge from deferred work.

R25: FAIL — it required the real counter-increment -> AnPal -> object-update pipeline at two qualifying edges, but not a suppressed edge or the post-gate no-catch-up/catch-up result. A fade-suppressed edge could therefore be replayed later without failing the documented gate.

R26: FAIL — it required the complete current consumer graph and the real runtime owner, but did not explicitly include planned families such as Blaster or reject a contract embedded in the first consuming family.
```

Baseline verdict: **RED, 0/3** for R24-R26. The prior 4/4 AnPal gate could still miss a fade-suppressed qualifying edge incorrectly deferred until `Palette_fade_timer` cleared.

### Independent updated raw output

```text
- Audit every entry gate before mutation, especially `Palette_fade_timer`, plus act/mode/event guards. Trace the disassembly to determine whether a qualifying edge suppressed during fading is permanently skipped or deferred/caught up. The skill mandates this analysis; it does not prescribe which semantic is correct.
- Put the exact native state domain at its real runtime owner, exposed through a shared typed runtime state/API—normally a `ZoneRuntimeRegistry` adapter. It must not live in `PaletteOwnershipRegistry`, the palette cycler, or the first Blaster-family consumer.
- Reproduce exact frame ordering: establish whether the routine reads before/after counter increment, then test the real pipeline of counter increment → gated AnPal dispatch → object updates, including whether consumers observe the old or new value.
- Integrated coverage must include an ungated qualifying edge, the next qualifying edge while fade is nonzero and therefore suppressed, gate clearing, and the following pipeline/qualifying edge proving the disassembly’s catch-up or no-catch-up result. Assert every consumer’s observed value at each edge; cover at least two consecutive qualifying edges.
- Also capture/restore the exact state and counter phase through rewind, with round-trip coverage, and verify death/restart, checkpoint, and session reload where those lifecycles can cross the transition.
```

### Updated scoring

```text
R24: PASS — the skill explicitly requires auditing Palette_fade_timer plus every act/mode/event entry guard, and determining from the disassembly whether suppression skips the edge permanently or defers it for catch-up.

R25: PASS — the skill explicitly requires the integrated counter-increment -> gated AnPal -> object-update pipeline to prove two qualifying edges, a gated suppression, and the native catch-up or no-catch-up result after the gate clears.

R26: PASS — the skill explicitly inventories present and planned consumers, names later Blaster-family work as an example, and requires shared typed state/API at the real runtime owner rather than a family-local contract.
```

Updated verdict: **GREEN, 3/3** for R24-R26. The gate now rejects both fade-suppressed-edge catch-up invented by the engine and a first-family-local API that would block later consumers.

Incremental structural validation:

- `.agents` and `.claude` mirrors are byte-identical (SHA-256 `062069711FA1A30EDC9CBE0E887E1BEF97B95106A2968393BD4C35FE997C57F5`).
- Both mirrors pass `skill-creator/scripts/quick_validate.py` with UTF-8 mode.
- `git diff --check` passes.
