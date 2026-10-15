# Second Strike — two-team campaign delivery

Status: implementation authorized on 2026-10-15; both Sol team leads running. No new
gameplay is claimed by this checkpoint. The binding content scope is the
[complete AIZ prototype and S3&K campaign proposal](../designs/2026-10-15-second-strike-campaign-scope.md).

Pinned pre-task destination: `develop`
`76022570e8233823e1e36419666a0f29c768f4b6`, fetched and fast-forward checked.
Both implementation teams start there. Main's existing three dirty disassemblies
and five untracked user files remain outside this task.

## Teams and ownership

| Owner | Models and remit | Private branch / worktree |
| --- | --- | --- |
| Integration lead | Root Sol: shared-contract sequencing, composition, delivery-wide validation, normal develop integration/push and accounted cleanup. | `feature/ai-second-strike-campaign-delivery`; `.worktrees/ai-second-strike-campaign-delivery` |
| AIZ team | Sol lead and Sol implementers; Astra reviews coherent change batches. Complete M0–M3, both apparent acts, native branches and HCZ handoff. | `feature/ai-second-strike-aiz-completion`; `.worktrees/ai-second-strike-aiz-completion` |
| Remaining campaign team | Sol lead with Sol and Sonnet implementers; Opus reviews coherent campaign waves. Implement M4–M6 across every remaining zone, stage-family variants and ending/resource closure. | `feature/ai-second-strike-campaign-expansion`; `.worktrees/ai-second-strike-campaign-expansion` |

Live catalog selections: Sol `codex/gpt-6.1-sol`, Astra
`codex/gpt-6-astra`, Sonnet `claudeAgent/claude-sonnet-5-5`, Opus
`claudeAgent/claude-opus-5-5`. Discover availability before child dispatch; never
silently substitute another model for a requested reviewer. Use saved app-owned
delegated tasks, with bounded child outcomes and results retained in task history.

A team lead may own two concurrent implementation workers, each in an isolated
worktree with disjoint files. Add reviewers at coherent batch boundaries rather
than dispatching one review per small edit or idle watchers. T3 child-task IDs
and batch state are recorded below as they become available.

## File boundaries and shared contracts

- AIZ owns its authored route, badnik refinements, AIZ native owners and tests,
  and the example's existing shared registration/resource/combat files. It owns
  shared Mod API/candidate-pin edits only in explicitly sequenced contract
  batches coordinated by root. Campaign consumers must not fork these contracts.
- Campaign owns new `secondstrike/campaign/` zone recipes, their focused tests,
  and the corresponding non-AIZ native zone-owner extensions. Its public entry
  point is handed to AIZ/root for registration; it does not concurrently edit
  `SecondStrikeMod`, existing `SecondStrikeRoutes`, common enemy files, API pins,
  API version descriptors, shared placement/compiler code or the root README.
- Campaign requests a missing semantic extension with exact owner, bounded
  recipe, stock-default behaviour, callers and focused checks. Root sequences
  the contract before its consumers, using accepted commits in private trees
  only after live builds/tests drain. No speculative generic difficulty engine.
- Each team owns a separate dated implementation plan under
  `docs/architecture/plans/`; root owns this coordination plan. Shared README,
  changelog, coverage index and final API export are reconciled by root from
  stable team patches. Do not rewrite the delivered evidence as future claims.
- Never mutate another team's tree, main, unrelated worktrees, queues or locks.
  Create/remove worktrees under the normal exclusive main-worktree lease.

## AIZ implementation batches

1. **A0 — combat/resource/native contracts:** reproduce or dismiss the P2
   hit-separation risk with actual attacks; implement the bounded recovery if
   needed. Lock scarce ring/monitor rules, bonus-invitation lifecycle and narrow
   brittle-log support. Preserve native physics and absent-policy defaults.
2. **A1 — complete apparent Act 1:** author A1–A7, not just the first four
   encounters; preserve forest/fire identity and semantic release boundaries.
   Finish Fire Breath warning/recovery and real cold/checkpoint routes.
3. **A2 — true Act 2 and completion:** A8–A11, early Bouncer feasibility spike
   and admit/reject decision for A12, Knuckles' distinct finale, waterfall-boss
   branches, reward/bonus/rewind/save lifecycle and playable HCZ handoff.

Astra reviews these as meaningful source batches, with precise findings and
evidence. Fix blockers in the owning team; subsequent review covers fixes and
unresolved objections, not repeated unchanged code. Final handoff must distinguish
implemented, runtime-observed and rejected/deferred stretch content.

## Remaining campaign implementation waves

1. **C1 — Sonic 3 half:** HCZ → MGZ → CNZ → ICZ → LBZ. A zone receives its own
   level, enemy, event and boss treatment, with both acts and character branches.
2. **C2 — S&K body:** MHZ → FBZ → SOZ → LRZ. Wind, footing, darkness and industrial
   pressure retain native movement, control, lifecycle and readable recovery.
3. **C3 — finale and product closure:** HPZ → SSZ → DEZ → eligible Doomsday;
   fourteen Chaos/Super boards, all three bonus families and ending/save/resource
   integration. Knuckles ends through his own SSZ branch. Doomsday is exempt
   from the ordinary five-world-ring rule because rings are flight fuel.

Use the proposal as an implemented-content blueprint, not merely another roadmap
or one global difficulty multiplier. Start source-grounded zone workers in
parallel with AIZ; contract dependencies are coordinated, not guessed. Existing
native route blockers are explicit prerequisites, not license to advertise an
unexecuted hard route. Do not silently absorb unrelated parity repair.

Opus reviews stable wave-sized changes, including route intent, counterplay,
native/mod boundary, character differences, lifecycle and evidence limits. The
team includes actual Sol and Sonnet implementation work; reviewers do not own
the implementation or repeat full-suite validation.

## Verification and delivery

Workers run focused checks appropriate to owned behaviour. Use original absolute
ROM paths/hashes; new game code keeps required S3K startup checks applicable.
Native state and ROM owners remain authoritative; trace rows are comparison only.
Headless tests and offscreen capture are the default. Never automate the user's
desktop focus/keyboard. Native window diagnostics require an owned isolated
display and fail closed before launch when isolation is unavailable.

All test-running Maven commands use the intended-tree queue; build-only commands
hold that tree's build/test lease. Keep current queued requests, source freezes,
required graphics flags and admission reservations intact. No worker submits a
broad run. Root inspects the final change-selected batched **smoke** plan against
the pinned integration base, adds semantic dependencies where necessary, states
count/cost/stopping rule, preflights the actual environment and owns combined
coverage plus fresh guards. Release-only full-suite is not routine validation.
Disputed failures get bounded matched baseline/current checks by full identity,
assertion and skip reason. Consume/acknowledge category diagnostics normally.

Before a batch handoff, provide stable commits, exact source/tested state, changed
files/contracts, reviewer task IDs/findings/resolutions, commands/results/skips,
real route/rewind/capture observations, remaining gaps and cleanup ownership.
Keep strict creator packaging at zero unsupported references, matching compiled
candidate pins and actual engine/SDK/test-kit consumers. Do not weaken guards or
copy native implementation into creator content to bypass missing APIs.

Only root integrates into and pushes the current main-workspace `develop`
branch after applicable checks. Teams keep feature branches local and do not
create PRs or mutate main. Remove only fully accounted merged task worktrees and
branches, preserving user config/ROM/save targets, useful captures and histories.

## Compact execution checkpoint

- Pinned base: `76022570e8233823e1e36419666a0f29c768f4b6`.
- Root, AIZ and campaign lead worktrees created under the normal main lease;
  independent reflink checkouts, hooks installed, initial trees clean.
- AIZ team task accepted/running: client request
  `second-strike-aiz-completion-lead-20261015-r1`.
- Campaign team task accepted/running: client request
  `second-strike-campaign-expansion-lead-20261015-r1`.
- Exact T3-owned task handles:

  ```text
  node:delegated-task:command%3Amcp%3A9d9a959c-518d-49f2-b8a6-385bfac2c064%3Adelegate-task%3Asecond-strike-aiz-completion-lead-20261015-r1
  node:delegated-task:command%3Amcp%3A9d9a959c-518d-49f2-b8a6-385bfac2c064%3Adelegate-task%3Asecond-strike-campaign-expansion-lead-20261015-r1
  ```

- Both leads received the sibling's live coordination identity and shared-file
  boundaries. Reviewers are dispatched only when concrete batches are ready.
- Reviews and implementation validation: not yet started.
- No main publication hold or timing pause is in force. Preserve foreign jobs and
  immutable performance sources; normal admission applies.
