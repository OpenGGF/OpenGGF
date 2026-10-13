# Runtime mod compatibility evidence

## Intent and decisions

The player needs to identify combinations that replace each other's contributions,
may interact badly, or cannot be prepared for play. Warnings must explain their
evidence. Only a failed mandatory engine contract warrants blocking an affected
launch; an author declaration or ordinary override does not prove a crash.

Implementation starts from develop `bd06ece22` in the copy-on-write worktree
`feature/ai-mod-runtime-compatibility`. The user explicitly authorized execution
and the existing integration/push workflow; additional skill approval handoffs
are not required.

- **Observed overlap:** published singleton contributions identify the target,
  actual application order, winner and replaced owners. Prepared audio uses its
  own publication outcome. Additive registrations and composable mutators are
  not presumed incompatible.
- **Suspected incompatibility:** declared conflicts/exclusivity and callback
  failures are advisory compatibility evidence. A callback fault identifies the
  executing owner; other enabled mods supply context, not proof of causation.
- **Verified blocker:** hard dependency/ordering failures, rejected runtime
  requirements, mandatory registration and failed patch metadata/application
  prevent the affected content from being prepared. Preserve the
  existing owner/dependent failure recovery. Do not blacklist arbitrary pairs
  because one owner threw or the player stopped moving.

Existing `conflictsWith` and competing exclusive claims become warnings. Invalid
claims, trust/API validation, hard dependencies, allocation limits and failed
registration retain their established gates. An empty report does not certify
arbitrary mod code as compatible. No automatic combinatorial execution or desktop
automation is introduced.

## Boundaries and shared interfaces

`ModCompatibilityDiagnostic` is an engine-owned immutable record with level,
code, target, participants, summary, evidence and blocked owners. Participants
identify the combination/context; blocked owners separately identify content
that cannot be prepared, so a healthy dependency never inherits its dependent's
play-blocked badge. Both owner sets are canonical;
evidence preserves application order. `ModRuntimeFindingStore` owns independent
producer-scoped diagnostics with identity based on level/code/target/participants,
and blocked-owner subset, not just the message code. Existing preparation findings remain independent.
Replacing the active context clears stale compatibility evidence; repeated events
upsert the same identity and storage is bounded.

`ModCompatibilityReporter` projects catalog requirements and published runtime
contributions without executing creator code. `ModSubsystem` owns context and
installs the runtime preparation observer, covering ordinary and standalone
registration paths without adding logic to Engine. The callback boundary adds attributed fault evidence before its
existing quarantine/abort. Mod Manager presents all three levels, participants,
evidence and the active configuration, including pending-settings status.

The context includes mod build identities and effective order. Records describe
catalog eligibility, registration/preparation, or a callback failure explicitly;
preparation is not described as proof of gameplay activation. New diagnostics
are outside the creator API and do not grant gameplay authority.

## Implementation and validation plan

1. Implement the immutable model and independent bounded store; verify multiple
   conflicts using the same code, all-owner visibility, producer isolation,
   immutable results and context invalidation.
2. Change declared/exclusive conflict policy to warning-first; verify effective
   participants/dependents, deterministic diagnostics, unchanged ordinary winners,
   invalid-claim rejection and retained hard dependency/ordering blockers.
3. Wire catalog, published contribution and callback producers into ModSubsystem;
   verify all levels through real host paths and no inferred pair causality.
4. Present diagnostics in Mod Manager Details/Notices and row badges; verify
   multiowner visibility, distinction from existing runtime findings and pending
   changes. Update installation/manifest/content guidance and 0.7 changelog.
5. Run focused queued regressions, inspect the change-based smoke plan against
   `bd06ece22`, perform actual Java 25/Lua 5.4/PowerShell preflight, and execute
   required smoke/guard coverage. Attribute disputed failures with matched focused
   baseline/current checks. Inspect skips and acknowledge consumed diagnostics.
6. Review the complete change, reconcile concurrent develop work by intent,
   integrate without switching the main branch, verify any new integration risk,
   push only develop, then account for and remove this task worktree/branch.

## Evidence and rejected approaches

`ModContributionReport` already distinguishes singular last-wins contributions
from owner-scoped additive registrations. `ModFaultBoundary` already attributes
an exception to its executing owner and quarantines required dependents. These
support diagnostics without replaying registration or changing gameplay.

Rejected: treating an override, author declaration, common mutator capability or
movement stall as proof of a nonplayable combination. Each can be intentional.
Rejected: permanently blocking a whole active set after a single callback fault.
The event alone cannot distinguish a mod defect, engine defect or interaction.

Validation results and integrated commits will be recorded after execution.

Static review found and corrected healthy-dependency badge attribution, audio
evidence retirement through backend invalidation, and an explicit empty startup
selection being mistaken for an unavailable selection. It also corrected a
callback fixture to retain real data registration plans rather than available-only
audio metadata. Focused regressions cover each case. Test execution was deliberately
paused under the user's S1 performance measurement interval through 11:05 UTC.
