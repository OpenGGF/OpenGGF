# GPT-6.1-Sol bug competition — judging rules

Established 2026-10-18 by the parent scorer before receiving either team's
submission or starting opposing review. These rules apply equally to Alpha
and Beta. The user sets the scoring schedule; this document makes evidence,
counting, and review procedures explicit without adding scoring categories.

## Scope and frozen submissions

- Both teams start from develop commit
  `50348f299a2cc152a3c7e641b96974df504e2990`.
- Each team aims for 10 real bugs, with 5–20 as the requested range and a
  maximum of 20 submitted discovery claims. Scope preferences prevent
  overlap; they do not change the evidence standard.
- A submission is the team's explicitly finalized claim ledger and named
  commit(s), not private hypotheses, progress notes, or discarded experiments.
  The judge records its commit and receipt time before the opponent reviews it.
  Material amendments remain visible; withdrawing an already submitted claim
  does not erase a subsequently proven refutation.
- Only defects present on the common base earn discovery credit. Regressions
  introduced by this competition must be repaired but cannot earn discovery
  points. Known, unfixed defects may count if independently demonstrated;
  already-fixed behavior cannot.
- User instructions and repository invariants govern all work. ROM quirks
  intentionally matching shipped behavior are not defects.

## What earns discovery credit

A claim must establish all of the following:

1. A concrete, reachable trigger through supported behavior or an actual input
   boundary, with its relevant prerequisites.
2. Expected behavior grounded in a documented contract, a real caller or
   consumer requirement, an established invariant, or the owning ROM routine.
   Preferences, cleanup, feature requests, and invented null/invalid-input
   contracts do not establish bugs. Malformed-input handling can qualify when
   the boundary promises rejection, isolation, or bounded processing.
3. An observed failure on the pinned base, with a reproducible command or
   minimal reproducer, exact assertion/symptom, and relevant environment.
   A plausible code reading alone is not proof.
4. A causal explanation connecting the failure to the claimed source location,
   rather than an unrelated environment, fixture, or test defect.

A failing test is evidence only if its expectation is itself justified.
Severity does not alter the user's count-based points.

## Distinctness and duplicates

- Count independent root causes and violated contracts, not assertions,
  parameter variants, affected games, or repeated symptoms. One missing check
  causing several manifestations normally counts once.
- Separate defects in one file may count separately when each has its own
  supported trigger and causal correction. One patch may fix several defects.
- A duplicate is not a refutation. Across teams, discovery credit goes to the
  earliest complete, evidenced submission for that root cause; use durable
  task-message receipt order, not a claimed discovery time. If order cannot
  be established, credit both teams' independent discovery while counting the
  integrated defect once. Do not invent a tie-breaking timestamp.
- Fix credit follows the accepted verified correction's contributor team,
  independently of discovery ownership. Equivalent duplicate fixes are not
  integrated twice.

## What earns fix credit

- The defect must first qualify as a real bug.
- Evidence must show the justified reproducer fails before the correction and
  passes after it, with source state, commands, outcomes, and skips identified.
  A regression test is preferred when useful; an equivalent bounded reproducer
  is acceptable. Assertions must describe behavior rather than mirror code.
- The correction must address the cause and preserve supported consumers,
  rewind/state ownership, ROM fidelity, and repository contracts as applicable.
  Disabling checks, weakening an oracle, fixture-specific tuning, or hiding
  errors cannot earn credit.
- Focused checks and delivery-wide checks are proportionate to the actual
  combined diff. New or worsened failures block acceptance/integration.
  Proven baseline failures do not invalidate an otherwise verified correction;
  unmeasured or skipped coverage must remain explicit.
- Discovery and fix counts are independent: a real bug with a rejected or
  incomplete patch may earn discovery credit but no fix credit. A rejected
  patch is not by itself a refuted discovery.
- Fix points remain provisional until accepted changes are integrated and the
  required verification succeeds. Record any unresolved delivery state.

## Opposing review and refutation

- After submissions freeze, assign an Alpha-affiliated GPT-6.1-Sol reviewer to
  Beta and a Beta-affiliated GPT-6.1-Sol reviewer to Alpha. Each receives the
  original brief, these rules, the submitted evidence, source states, and any
  prior response or unresolved objection. Review source is read-only; any
  necessary probe belongs in the reviewer's own isolated checkout.
- For every claim, the opponent returns: supported, demonstrably mistaken,
  duplicate/overlapping, or insufficient evidence. Give source/contract
  references, reproduction results, and limits. Assess patch safety separately.
- The refuter bears the burden of proving the material bug claim false:
  for example, correct baseline behavior under the stated prerequisites,
  proof the claimed supported path is unreachable, or an authoritative contract
  showing the observed behavior is intended. Merely failing to reproduce,
  missing a dependency, insufficient submitted proof, a duplicate, or a bad
  proposed fix does not earn refutation points.
- A proven material error in one submitted bug claim awards the opposing
  reviewing team 2 points, once for that claim. Multiple reviewers or arguments
  do not multiply the award. Reject or merge artificial claim splitting before
  awarding points. No negative points are added.
- Give the submitting team one bounded opportunity to answer each material
  objection with evidence or a correction. The judge can request a targeted
  follow-up on an unresolved question; do not repeat unchanged review rounds.
- The parent scorer is the final judge, checks contested evidence independently,
  and records a reason for acceptance, rejection, consolidation, or deferral.
  An unresolved claim earns neither discovery/fix credit nor refutation credit.
  Self-reported counts, confidence, and opponent agreement are not verdicts.

## Score, attribution, and advancement

Discovery and fix each receive the highest attained milestone, not a sum of
milestones:

| Accepted count in that category | Points |
| --- | ---: |
| 0–4 | 0 |
| 5–9 | 1 |
| 10–19 | 5 |
| 20 | 20 |

Team score = discovery milestone + fix milestone + 2 × proven opposing
refutations. No severity bonuses, speed points, penalties, or unannounced
tie-breakers are added.

Credit each team's awarded points to its parent and every participating
(sub)agent whose concrete contribution is evidenced by task history, commits,
tests, reproduction, or review. Merely existing, observing, or being named
does not establish participation. Agent credit is attribution of the team
award; it does not multiply the team score. The neutral root orchestrator's
participation and the two team totals are recorded separately; the root is
not a third competitor.

The higher-scoring team advances. Equal scores remain a declared tie, with
both teams advancing jointly; no extra round or altered metric is silently
introduced. Advancement does not authorize unrelated future repository work.

The final ledger records every submitted claim and ruling, accepted
bug/fix counts, refutation awards, contributor/task IDs, tested source state,
verification limits, integrated commits, and delivery/cleanup status.

