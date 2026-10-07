# MHZ1 Post Two Ambush implementation

Accepted scope is the [Hardened blueprint](../designs/2026-10-07-hardened-s3k-blueprint.md)
prototype: one local native MHZ1 encounter, solo Sonic, donor off, 320px,
no save. Original implementation base is `6d817a9d74f135714f3da59ab9aab156cc09473e`.
The later full-act, chapter, campaign and Stage Trials gates remain future work.

1. Survey the physical second post's floor, headroom, placements, rings and
   camera using production ROM decoding. Freeze an explicit bounded encounter.
2. Add transaction-owned immutable placement plans without replacing the native
   level, objects, terrain, events or PLC owners. Add explicit fresh-entry,
   no-save and native-input controller opt-ins with stock defaults.
3. Author the resident-art sentry and recreatable projectiles. Capture admitted
   simulation phase, committed aim and finite projectile state through rewind.
4. Integrate the title, lesson, entry, play, checkpoint retry, results and clean
   exit using existing engine presentation/input/audio owners.
5. Build/package the maintained JVM example; document human and agent authoring,
   valid/invalid plans, supported cells and observed evidence. Capture actual
   graphics and PCM outside the repository from committed input programs.
6. Verify focused behavior, two-cycle replay, fault containment, native checkpoint
   reload, the four mandatory S3K regressions and AIZ→HCZ safety. Review the actual
   combined selection, preflight and run required category/guard validation.
7. Independently review high-risk boundaries and the final base-to-head diff;
   fetch/assess develop, push only the feature branch and open/link its PR.
   Account for children and retain the PR tree for review.

Parallel ownership: placement child owns frozen plan registration/application;
encounter child owns geometry, gameplay objects and focused mechanics; session
child owns the three launch/input opt-ins. The lead owns presentation, integration,
authoring documentation and delivery. Each child uses a separate copy-on-write
worktree and queued focused Maven commands. Their commits and exact verification
are reconciled in the outside-repository task ledger before integration.

Current checkpoint: destination `5b3a63641033506fc0d89ad5188a0c97fae29089`
was merged by intent as `296ad3090` after the frozen trace invocation finished.
Its seven upstream changes are documentation only. The prior merged focus passed
122 cases with zero skips. The three AIZ/HCZ trace assertions matched the clean
5b3 baseline by exact test identity and message; they remain inherited failures,
not route passes. The new entry focus completed 144 cases: 143 passed and one
native checkpoint retry stayed in ENTRY because its mandatory native title
request was not consumed. The example now uses the existing omitted-presentation
owner before neutral rows. The corrected seven prototype cases and actual runtime
policy case pass, with zero skips. Queue/provider, mandatory S3K and API/SDK cases
passed in the preceding focus. The actual compiled 20,127-line candidate export
matches the pin. Broad validation and final polished recapture remain required.

The observed entry-art gap is addressed through ordinary neutral native rows and
a semantic readiness query, preserving each queue/fade owner. The animated title
footer is shown after the panel settles. Maintained safe/failure controller
programs are under `examples/hardened-s3k/walkthroughs`; final capture evidence
must confirm their milestones after the entry correction. No future full-act or
campaign gate is complete.

Destination follow-up: exact `2fc65c8479570f16ebd9830115485ee369c2b1e6` adds
Survivors, stock-default invincibility-expiry music ownership and memory tools.
It is merged by intent as `259a9a48f`, preserving both version-comment paragraphs
and the upstream title callback/forwarding/unavailable two-player branch. The
candidate pin auto-merged; its combined compiled export remains a required check.
The pre-2fc coherent entry-source trace run at `84e1a9a6f` reproduced all three
literal 5b3 baseline assertions. One shared clean 2fc baseline and final merged
candidate check establish new-destination attribution without duplicating a full
baseline suite. Focused packaging, final merged captures and combined validation
remain pending.
