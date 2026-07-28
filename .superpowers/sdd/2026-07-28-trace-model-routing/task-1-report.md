# Task 1 report — trace model routing

## Status

DONE

## Delivered

- Added the identical Terra/Sol routing table, routing object, allowed enums, telemetry/nullability contract, schema-repair boundary, deterministic escalation rules, decision table, sequential worktree ownership, retained-diff review, and final routing aggregate to both fleet skills.
- Kept the existing harness differences: Codex names explicit `spawn_agent` model/effort overrides; Claude retains `Agent`-tool wording and states tier intent without claiming unsupported overrides.
- Added the stable worker-prompt prefix and compact-dynamic-handoff rule.
- Created `docs/architecture/validation/trace/trace-model-routing-pressure-tests.json` with the prescribed case, verbatim RED and GREEN worker responses, exact expected routes, required fields, edge-case assertions, and mirror-semantics result.

## Pressure test evidence

The baseline RED response left all requested model/effort routes, escalation routing, discovery contract, and routing/usage fields unspecified. The GREEN rerun, using `gpt-5.6-terra` at medium effort and no forked conversation context, returned:

```json
{
  "discovery": ["gpt-5.6-terra/low"],
  "triage": ["gpt-5.6-terra/medium"],
  "narrowFix": ["gpt-5.6-terra/medium", "gpt-5.6-sol/high"],
  "sharedFix": ["gpt-5.6-sol/high"],
  "ordinaryVerify": ["gpt-5.6-terra/medium"],
  "sharedVerify": ["gpt-5.6-sol/high"],
  "narrowEscalatesAfterAttempt": 2
}
```

An initial GREEN run exposed an ambiguity around repeated branches and pre-execution verification. The skills now specify that a route plan reports each category's default route and only appends a replacement route for an actual model escalation. The recorded final GREEN rerun passes all exact route and required-field assertions.

## Verification

- `jq empty docs/architecture/validation/trace/trace-model-routing-pressure-tests.json` — pass.
- Focused `jq -e` route/field assertion against the saved GREEN raw response — pass (`true`).
- `git diff --check` — pass.
- `git diff --no-index .agents/skills/trace-green-fleet/SKILL.md .claude/skills/trace-green-fleet/SKILL.md` — expected nonzero diff; inspected and confirmed only known harness-specific wording differs.
- Focused `rg` for routing fields and models in both skills — pass.
- Negative `rg` for `gpt-5.6-luna` in both skills — pass.

## Concerns

- No runtime fleet executor exists in this task; the skill contract and pressure artifact are the enforceable documentation-level interface.
- The scoped routing commit also includes the Task 1 design and implementation-plan artifacts produced for this workstream; local disassembly trees remain unrelated and untracked.

## Fix round 1/5

Addressed reviewer findings:

- Replaced prose-only stage embedding with concrete Discovery, Triage, Fix, and Verify JSON examples that include every common routing field plus observed `beforeFrame`, `afterFrame`, `status`, and `regressionCount`.
- Changed all displayed worker prompts so their first line is the same byte-stable invariant prefix and routing/input JSON follows it.
- Replaced the Fix prompt's generic three-attempt wording with Terra-at-most-two-unsuccessful attempts, one final Sol attempt, and a cumulative cap of three.

Commands and output:

```text
jq empty docs/architecture/validation/trace/trace-model-routing-pressure-tests.json
true
git diff --check
# exit 0
git diff --no-index .agents/skills/trace-green-fleet/SKILL.md .claude/skills/trace-green-fleet/SKILL.md
# expected exit 1; only known harness-specific wording differs

# Extracted each Discovery/Triage/Fix/Verify JSON fence from both skills and piped it to jq empty
# all eight examples parsed successfully
```
