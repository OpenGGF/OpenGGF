# Architecture, goals and progress audit remediation

## Scope and starting evidence

The October 25 Astra review inspected `159655c8460dac15912dc4326491386a75368666`.
The user authorized changes with Sol implementation workers. Delivery starts at
`f7620e579c1a8cf7b0371f9c5a438f6a4eff5ea7` on `develop`; the intervening private-lobby
design is unrelated and remains intact. This is routine development, not public
release preparation or a claim of complete campaign qualification.

The reviewed findings are bounded:

- Native PCM tests can classify non-graphics `IllegalStateException` failures as
  missing graphics prerequisites.
- An isolated placement helper retires its audio backend rather than its entire
  owning manager. No resource leak was demonstrated by the review.
- S2 Sideways platform allocation failure retains an unregistered Java child;
  native failure instead writes through fixed Tails-tail storage. The existing
  known-gap assertions characterize strict capture failure, not a repair.
- Active roadmaps lag native ending and racing-extraction implementation, omit
  Stageplay's release disposition, and combine simulation correctness with
  optional presentation qualification. Rule-placement prose also describes a
  superseded collision-rule shape.
- Continuous AIZ-to-HCZ evidence must be established on current source. The
  October 17 recorded chain frontier is historical and does not certify this base.
- Hosted publication deliberately has no private-ROM dependency. Existing
  release guards require manual ROM validation; candidate-specific qualification
  must be strengthened with that policy made explicit.

## Work ownership

The parent owns composition, delivery-wide validation, integration, push and
cleanup. Workers use separate T3-managed checkouts, commit locally and return
focused evidence. They do not push or modify the main workspace.

| Slice | Owned outcome | Initial state |
| --- | --- | --- |
| Capture assurance and disposal | Narrow prerequisite admission, non-graphics failure control, complete isolated audio disposal | `dfc7d9784`: 32 focused tests passed, zero failures/errors/skips |
| Roadmap reconciliation | Source-backed implementation/qualification status, Stageplay scope, finite configuration tiers, timing dispositions and corrected architecture prose | `e4fb59d17`: 99 links/anchors and factual/structural checks passed |
| Native fixed-slot qualification | Preserve the native animation prerequisite; qualify storage/removal boundaries without inventing parent bytes | Fixed-tail-only candidate rejected at supported raw deletion/reuse; original storage gap remains open |
| Continuous campaign boundary | Current named AIZ-to-HCZ frontier and smallest ROM-owned correction, retaining strict comparisons | `aa5fb2d34`: load correction passes 149 focused and 43 authority checks; strict prefix reaches HCZ's first row |
| Release preparation | Explicit exact-candidate qualification and required case accounting, consistent with publication policy | `a2cf1b322`: Python safety and actual tool/strict-ROM prerequisite preflight passed; real route run remains parent validation |

The user confirmed that ROMs must not be placed on GitHub and accepted stronger
local preparation. Preserve hosted publication and manual ROM opt-in. The local
tool must bind its named subset to the expected candidate and reject missing or
skipped evidence; it must not describe the existing 26-case pilot as complete
release qualification or replace the other public-release gates.

The strict-mode worker reports 42 route-tool safety checks and 98 release-policy
safety checks passing without skips. Skip-classifier checks pass 19 cases and
skip `CheckedInPolicyTests.test_preserved_f56d4fae1_reports` because its historical
archive is absent. Actual Java 25/Lua 5.4/PowerShell preflight and committed-SHA
strict prerequisites with the original local S3&K ROM pass. No native route,
smoke or full suite was run for that worker; parent will exercise the strict
26-case pilot against the composed committed candidate as affected domain/tool
integration evidence, separately from broad smoke.

Session/global ownership and restore-order concerns are source-level maintenance
risks. They do not authorize an indiscriminate service-locator migration or new
restore framework. A change needs a concrete violated invariant and focused
before/after evidence. The previous composition-root cleanup tranche stays closed
unless an affected behavior supplies that evidence.

## Verification and integration

1. Workers run focused checks through the shared Maven queue, with original ROM
   identities and explicit absolute paths. They inspect exact failures and skips;
   graphics work uses the configured offscreen backend.
2. The parent reviews composed changes and inspects the change-based category plan
   against the pinned pre-task base above. Broad validation, if required by the
   actual combined changes, uses smoke, preflight, bounded batches and a stated
   class count/cost/stopping rule. No routine full suite is authorized.
3. Required affected trace/domain checks remain separate. Recorded-timing replay,
   live routing and native audiovisual evidence retain their different claims.
   Do not weaken comparisons or promote bounded route checks to campaign parity.
4. New or worsened failures block integration. Disputed failures use bounded,
   matched baseline/current checks rather than repeated broad runs.
5. Integrate into the existing main-workspace `develop` branch without switching
   it; preserve unrelated changes and reconcile upstream intent. Push only that
   branch after required verification. Remove task checkouts/branches only after
   all changes and generated files are accounted for.

## Checkpoint

The reviewed documentation and assurance slices are composed locally; main
integration, combined validation and push remain pending. Assurance command:
`maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl` with original three
absolute ROM properties, followed by
`-Dtest=TestNativePcmCapturePrerequisites,TestNativePcmCaptureLive,TestPlayablePlacementAudioOwnership,TestNativePlayerTerrainPlacement,TestStageplayGroundSurvey,TestAudioManagerStreamedPortOwnership,TestRawGlfwTestSetupGuard test -B`.
At `dfc7d9784`, all 32 cases passed without failures/errors/skips on Java
25.0.5.0.2/EGL, including all five PCM cases. The worker's pre-fix controls observed
the false skip and both missed private-port releases. Reports were consumed.
This is bounded Linux verification, not cross-platform graphics qualification.

The campaign worker's clean `f7620e579` canonical session 51690 finished at
11:12:16 UTC with one assertion failure and no errors/skips: segment 8 retains
four physics errors at row 7126, and the walk stops at input 68801/segment 11,
HCZ1/load generation 9. Alternate session 12519 retains its sole row 26179
`rings` difference, with no errors/skips. The parent reviewed the exact historical
`760b2005d` runtime/test change and native corroboration and authorized a narrow
adaptation to the present instance lifecycle owner. Independent pre-fix regression:
four cases, three failures; corrected four cases pass inside focused session 3120
(149 total cases). At `aa5fb2d34`, canonical/prefix session 52854 completes with
six cases, one inherited failure, zero errors/skips. All three strict prefixes and
the known-green AIZ/HCZ prerequisites pass. Segment 8's four row-7126 errors are
removed; completed segment 9 also has no mismatches. Segment 11 still stops at
input 68801, with 81,981 incomplete-comparison errors and first row 1768 `y`
`$0870/$0871`. Alternate session 4338 retains its sole trailing rings difference;
authority session 60839 passes all 43 cases. These are bounded frontier advances,
not complete campaign qualification. Exact commands and fingerprints are in the
October 25 [frontier entry](../../status/trace-frontier-log.md#2026-10-25--fresh-load-plc-order-qualifies-the-continuous-aiz-to-hcz-load-boundary).

The fixed-tail worker's 12-case native animation prerequisite passes without skips,
but the conservative hook treats a nested engine-internal snapshot component as
a public API change. The established normalized terminal remains unchanged.
An isolated Sol policy worker now owns the narrow correction and both-shell
negative controls; hooks and signature pins must not be bypassed or artificially
changed. The normal candidate signature check remains separate.

The independently executed normalized candidate check passed one physical case
before storage edits. The later fixed-tail-only candidate exposes the already
documented dynamic-parent storage boundary: supported raw deletion and Stageplay
retirement do not execute Obj7A's linked-target cleanup. Obj05 then reads 13 bytes
across eight fields at a cleared or reused parent slot, without an occupant guard.
A live-object resolver, class-default bytes or automatic cascade cannot reproduce
that contract. The parent rejected shipping the candidate's unknown-parent throw
and stopped expansion into generic SST storage. The worker retains the finite
disconfirming evidence in the existing rewind audit and restores the candidate
edits; the independent animation prerequisite can land after the hook correction.
The original Sideways allocation-pressure defect remains open.

The parent integration checkout is
`.worktrees/ai-audit-remediation-20261025` on
`feature/ai-audit-remediation-20261025`. The main workspace's four dirty reference
submodules and five untracked user files are preserved. Another task advanced main
to `824a16f21` with an unrelated lobby-document revision; preserve it at integration.
There are no parent-owned Maven jobs yet. Workers own their live jobs. Update this
checkpoint with accepted commits, actual validation, remaining boundaries and
cleanup state as the slices complete.
