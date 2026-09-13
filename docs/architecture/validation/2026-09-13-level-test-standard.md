# Level test standard documentation validation

Base: develop `5a3cb848a8d45b7bf236fc5ee8ca30cc8af1a33e`.
Task: `20260913-level-test-standard`; isolated branch `feature/ai-level-test-standard`.

This delivery publishes the [standard](../../guide/contributing/level-test-standard.md),
[backlog](../../status/level-test-coverage.md) and
[plan](../plans/2026-09-13-level-test-standardisation.md), and connects existing agent,
object/boss and zone guidance. It does not implement the coverage backlog, change
JUnit/Maven/CI selection, or install the planned coverage-report/prerequisite gates.

## Checks

- `git diff --check`: whitespace/diff validation.
- Inline Python filesystem checks: every local Markdown file link in changed files
  resolves; AGENTS.md/CLAUDE.md are byte-identical; all eight changed skill pairs are
  byte-identical; all eight implementation entrypoints link to the standard.
- Inline Python registry comparison: extract LevelData references from all three
  registry constructors and compare the exact set with the backlog. There are 89
  unique keys: S1 21, S2 20, S3K 48. No missing, duplicate or invented registry slot.
  This validates inventory transcription, not gameplay/coverage completeness. Resolving
  aliases and discovering non-registry paths remain explicit LTS-02/LTS-08 work.
- Inspected the change-based plan using `python3 tools/testing/run_categories.py
  --base 5a3cb848a8d45b7bf236fc5ee8ca30cc8af1a33e --max-minutes 10` without `--run`.
  Scope is Markdown-only with no executable selection change. Documentation checks
  replace engine execution for this delivery under the repository's documentation
  validation policy; no new engine-suite or ROM/trace result is claimed.

## Behavioral scenario review

Manual review of the written policy, not execution of an automated enforcement tool:

| Scenario | Required outcome in the standard |
| --- | --- |
| New act loads successfully but has no mechanic/rewind matrix | Partial implementation; cannot claim standard compliance |
| Local boss fix has unrelated inherited route failures | Verify changed boss/rewind obligations and retain named gaps; baseline delivery policy still applies |
| Test sets each width/donor string but never steps gameplay | Does not satisfy breadth; requires behavior and effective configuration assertions |
| Width and donor independently pass but interact at arena release | Add the explicit combined scenario; pairwise sampling cannot waive a known interaction |
| Full route fails before its checkpoint assertions | Checkpoint coverage remains unestablished; extract independent fixtures |
| Boss fields restore but recreated children point to the old root | Fails graph/forward-replay obligation |
| Object interaction restores only default state | Fails meaningful-state setup and replay obligation |
| Bonus/load boundary deliberately resets rewind history | Verify reset/isolation and new-timeline replay; do not require prohibited cross-boundary rewind |
| Maven exits zero with missing-ROM skips in an exhaustive lane | Coverage assessment fails; automatic rejection is explicitly planned rather than claimed implemented |
| Two raw slots resolve to the same canonical scene | Record alias disposition; do not count two conformant playable acts |
| Shared test exercises generic logic but never binds an act's data | Cannot discharge that act's production integration obligation |
| Expensive test is retired after a faster test is added | Require assertion/configuration/path/defect mapping and preserve unique evidence/lane frequency |
| Existing example test is linked in the guide | Treat as a pattern and state its limits; link alone confers no current pass certification |
| New main character or donor becomes supported | Expand required cases and matrices; planned drift detection must expose missing coverage |

All scenarios have explicit treatment in the standard/plan. Remaining work is the
backlog audit and implementation itself. LTS-01 is documented; LTS-02 through LTS-09
remain pending. Documentation-only follow-up/integration does not justify repeating
the previously completed engine benchmark.
