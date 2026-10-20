# Shared development line — 2026-10-20

## Decision and integration

The user requested develop and next at the same commit and delegated the merge
strategy. Preserve both histories with a merge of next `f514a5f13` into pinned
develop `ff5088abf`; then fast-forward both development refs to the verified tip.
Worktree: `.worktrees/ai-unify-develop-next`, branch
`feature/ai-unify-develop-next`. Do not move master or rewrite published history.

The active shared engine line remains `0.7.prerelease`; bundle metadata and runnable
artifact examples retain develop's version. Descriptor `targetBranch=develop`
with optional `candidateBranchAliases=next` explicitly admits both CI destinations.
The reserved `nextLine=0.8` remains ordered after develop; the 0.8 roadmap is future
scope. Existing candidate API 0.7.0, normalized pin name and empty published set
are unchanged. Before publication, aliases must be cleared. A later independent
next line clears aliases in both descriptors and advances its metadata together.

Rejected: keeping branch-specific metadata cannot produce identical commits;
squashing or cherry-picking would obscure reconciled history; changing the shared
version to 0.8 would prematurely promote the current 0.7 development line.

Runtime Java merged textually, but the first focused build exposed a semantic
conflict: next's new HUD setting was absent from develop's exhaustive
`SettingTiming` switch. Its reader installs the anchor at level assembly, so the
merged setting is `NEXT_GAME`; regression coverage checks both its timing and
that paused-game resume does not claim to apply it. The initial compile failed
before any tests ran. Four conflicts were reconciled by intent:

- Root changelog: use the shared 0.7 pointer; fold HUD positioning and develop's
  recent voice/menu refinements into the current 0.7 entries. Previously promoted
  Time Attack/mod APIs are already described in the 0.7 themes.
- Measurement hazards: retain both branches' independent observations.
- Settings tests: retain develop's modern screen coverage and next's HUD labels.
- Candidate signature pin: retain develop's inventory and add exactly the
  existing HUD-anchor configuration constant from next.

Next's HUD anchor, LAN-address paste/keypad, moved-worktree link repair and
file-backed terminology-guard diagnostics survive alongside newer develop work.
Alias tests cover both destinations, missing/empty defaults, malformed aliases,
publication/master rejection, immutability and earlier engine-line bounds.

## Validation checkpoint

Pinned pre-task destination: `ff5088abf5cce5878f143817f03c941805f11880`.
The change-based plan selects all 3,510 ordinary smoke candidates plus guards;
release-only exhaustive tests and separate trace/native profiles are excluded.
Initial default-tool preflight rejected Lua; the actual launch environment uses
`LUA_BIN=/usr/bin/lua5.4`, `JAVA_HOME=/usr/lib/jvm/java-25-openjdk` and that JDK
first on PATH. Tool preflight passed (Java 25, Lua 5.4, PowerShell).

Static checks passed: staged whitespace, hook shell syntax, root agent-doc and
skill mirrors. Changed Markdown link checks found two inherited unresolved links
in the workflow catalogue (Starpost resource generator and optional TraceChaser
checkout); no new missing local targets were added.

Focused queued validation passed on the staged merged source: **95 tests,
zero failures/errors/skips** across nine classes (policy, pins/signatures,
configuration/defaults, settings UI, text input, HUD and timing). Environment:
Java 25/Lua 5.4 with CLI `-Dopenggf.headless.gl=surfaceless-egl`.

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl -DmodApi.destinationBranch=develop \
  '-Dtest=TestModApiReleasePolicy,TestModApiPinPolicy,TestModApiSignatureSurface,TestEngineSettingsScreen,TestMenuTextEditor,TestHudRenderManagerWidescreen,TestConfigCatalog,TestSparseUserConfig,TestSettingTiming' test -B
```

Merge commit: `fe88bfb19` (parents `ff5088abf`, `f514a5f13`). The explicit queued
`-DmodApi.destinationBranch=next -Dtest=TestModApiReleasePolicy` check also passed:
15 tests, no failures/errors/skips. Broad smoke/guards follow the policy fix below.

### Imported history audit

`ci-push f514a5f13 fe88bfb19 next` passed the next-side incoming-history audit.
`ci-push ff5088abf fe88bfb19 develop` rejected historical next commit `92826f4c6`
for detectable API declarations without a candidate-pin operation. Those changes
are already represented in develop's current networking and the focused merged
signature inventory passed. Rewriting old public next commits was rejected.

The shell and PowerShell policies now freeze imported trailer/API-coupling debt
at exact pre-task next tip `f514a5f13d353550f8421462c773f17f90a04100`, using the
existing ancestry-bound trailer-baseline mechanism. This is not a resource-policy
exception: tree and incoming resource history still undergo their independent
checks. Descendants and unrelated merged commits retain trailer and API-coupling
checks. No moving remote ref or validation pass cache is used.

The snapshot-policy safety suite covers imported debt admission, descendant
trailer/API failures, unrelated merge history and imported-but-removed resource
violations in shell and PowerShell. The Java guard pins the new baseline in both
implementations. The candidate alias and its publication restrictions are
independent of this fixed historical boundary. Complete them once on the
stable merged source; attribute disputed failures by exact matched baseline/current
test identity rather than repeating broad suites. Do not advance or push either
ref until required verification and failure attribution are accounted for.

`python3 tools/testing/test_release_snapshot_policy.py` passed all 36 Python
safety cases, exercising both installed shell and PowerShell implementations.
