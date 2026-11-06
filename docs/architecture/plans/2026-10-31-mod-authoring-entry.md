# Lowering the mod-authoring entry barrier

The user authorized completion of the three first-priority investments from the
mod framework assessment: task recipes and an agent skill, one creator workflow
with preflight, and actionable machine-readable diagnostics. Preserve expert
Java/provider overrides and existing ownership, trust, state and validation rules.

## Delivery and ownership

Integration base: `599d46762bac0387d2ede55dab3b48741db9896a` on `develop`, fetched
and confirmed current before delegation. Main-workspace dirty reference submodules
and unrelated files remain untouched. The lead integrates in
`feature/ai-mod-authoring-first`; three T3-owned isolated workers implement:

1. Recipes/skill: six task routes, maintained source references, explicit lifecycle
   and verification limits, and identical agent/Claude skill mirrors.
2. Creator workflow: extend the portable project builder with local configuration,
   CLI precedence and a read-only text/JSON doctor; retain existing invocations
   and export the supported workflow in creator kits.
3. SDK diagnostics: additive guidance on existing validation findings and JSON
   operational errors for validate/package, preserving original fields, strict
   warning semantics, exit behavior and atomic publication.

No live gameplay reload, scripting language, runtime semantic hooks, publication
baseline or compatibility promise is added. Improving the existing builder avoids
a competing launcher. Diagnostic enrichment stays in SDK presentation rather than
expanding the runtime API. Recipes link maintained examples rather than copying
large implementations or making every mod read the entire handbook.

## Acceptance and validation

- Each worker owns focused regressions and a committed, reviewable handoff; no
  worker pushes or runs broad delivery suites.
- Review actual API references and skill routing for all six tasks plus original
  scene work; verify links, syntax and byte-identical mirrors.
- Exercise configuration precedence, relative paths/spaces, malformed configuration,
  artifact mismatch, optional prerequisites and doctor's lack of writes/launches.
- Exercise actual SDK JSON success and failure paths, escaping, warning policy,
  unknown diagnostic codes and unchanged publication behavior.
- Inspect the change-based category plan against the integration base. Choose
  combined coverage from affected consumers and plausible risks, adding real
  portable creator/artifact witnesses. Use the shared Maven queue; no release
  full-suite. Compare disputed failures with bounded matched baseline checks.
- Lead reconciles documentation/changelog, performs final integration review,
  merges into the main workspace's existing branch, pushes only that branch and
  removes task worktrees only after all work is accounted for.

## Checkpoint

All three worker slices are integrated locally: recipes `e62f044a8`, workflow
`5f72fa891`, diagnostics `916f6d4f0`. The lead additionally aligns generated/kit
instructions with the canonical portable workflow and ignores personal config in
new projects. Worker verification: recipe syntax/139 references and routing;
37 Python tests; 34 SDK checks with zero skips. Historical dirty artifacts used by
the workflow worker do not qualify current-source binaries. The diagnostics
worker's initial lean compilation exhausted heap before any tests; its normal
rerun passed. Lead combined Python checks pass 37/37 and real tool preflight passes.

The change-based plan selects 4,366 smoke classes because SDK/tool paths and
`.gitignore` are conservatively unclassified. This is disproportionate here:
changes are confined to creator CLI presentation, scaffolding, portable tooling
and prose, with no runtime/physics, selection policy, POM or workflow change.
Combined validation instead covers SDK diagnostics/packaging, all starter purposes,
SDK artifact/Javadoc boundaries and converters, Python tooling, actual matching
current-source artifacts and portable starter/test witnesses. An independent
review checks the combined source and uncommitted reconciliation. This is bounded
creator coverage, not engine gameplay qualification. No broad suite is launched.
Main integration, push and cleanup remain pending.

Combined focused Java invocation (normal queue lane) passed 85 tests across 12
SDK/scaffolder/converter classes, zero failures/errors/skips; Maven execution
reported 1m27s, queue wait 0s. Python checks now pass 38/38 after the independent
review identified an unreadable-compressed-identity error path. Three archive
shapes reproduced escaping exceptions against the worker version and now produce
structured artifact.identity failures. The lead repaired it without another review
round. Skill syntax, 86 focused local links/anchors, code fences and both mirror
pairs pass. The lean worker compilation failure remains reported above; it did
not execute tests. Current-source artifact build and consumer qualification follow.

The clean `93e6704e1` universal/SDK/testkit/Javadoc build and creator-kit export
passed (build-only verify, 43.436s). Fresh music/reskin/object portable starter
registration tests passed. Character packaging passed but its real registration
failed because portable conversion omitted the maintained runner.png -> runner.ggfp
step present in the starter POM (also needed by standalone). This exposed a gap
not covered by the worker's three-purpose witness. A focused regression failed
on stale output, then passed after adding the exact playable converter path;
Python checks pass 39/39. The same real character registration now passes with
zero skips. No validator or runtime contract was weakened. Fresh matching artifacts
and all seven purpose witnesses will qualify the repaired candidate; Java source
is unchanged from the completed 85-test run.

## Completed qualification

Clean repaired producer `de16e54e0917fd7d7185cbf74dde0bb1ab549da0` rebuilt and
verified universal engine, SDK, testkit and exact API Javadoc artifacts in 52.001s
(build-only; tests explicitly skipped). Creator-kit assembly accepted matching
identities and `sourceDirty=false`, with no dirty-source override. A fresh unpacked
kit outside the checkout generated music, reskin, object, character, zone, scene
and standalone projects under paths containing spaces. Every purpose passed:

- read-only `--doctor --test --format json`, with unchanged project bytes;
- portable `--test`, one real Jupiter registration test and zero skips;
- strict SDK JSON validation with zero findings;
- exclusion of local config, test classes and JUnit from its distributable jar.

The scene project's actual Git ignore check excludes local config and generated
saves. A real missing-JAR CLI call returns parseable failure JSON with actionable
guidance. These are seven successful creator registration checks, not gameplay,
ROM, pixel or complete campaign qualification. Temporary consumers and artifacts
are owned task output and are discarded after recording these bounded results.

Combined source checks (from the integration worktree, Java 25):

```sh
python3 -m unittest discover -s tools/modding -p 'test_*.py' -q
python3 tools/testing/maven_queue.py --normal -Dmse=off -Dtest=TestDiagnosticReports,TestCreatorFeedback,TestModJarValidator,TestGgfModCliCommands,TestJarPackager,TestProjectScaffolder,TestPurposeStarters,TestModApiSdkPackager,TestModSdkArtifactVerifier,TestModApiJavadocTool,TestArtConverter,TestLevelConverter test
python3 tools/testing/run_categories.py --base 599d46762bac0387d2ede55dab3b48741db9896a --preflight
python3 tools/testing/maven_queue.py -Dmse=off -DskipTests -Puniversal-jar verify
```

Results: 39 Python tests; 85 Java tests across 12 classes, zero failures/errors/skips;
tool preflight passed; artifact verify passed. The Java source/resources are
identical between the 85-test candidate and the final repaired producer; only
portable Python and its regression/guidance changed afterward and received their
own checks. No broad smoke, release full-suite or gameplay qualification is
claimed. Queue wait for the combined focused invocation was 0s; end-to-end task
wall time was not measured. Documentation-only evidence updates do not repeat
unchanged executable checks. Delivery follows merge to the existing `develop`
branch, push only that branch, and removal of fully accounted task trees/branches.

## CI fixture correction

CI run 41196711655 at `e7a64336a` exposed two failures in
`TestPhase2SampleModIntegration`: the checked reference lacked the newly generated
`.gitignore`. Its README also predated the portable workflow text. The earlier
12-class creator selection missed this exact generated/reference oracle even
though fresh starter witnesses passed. Synchronize both fixture files; do not
ignore dotfiles or weaken inventory/byte equality. The creator testing guide now
names this consumer and the maintained-gallery packaging check for starter edits.

The change-based selector conservatively selects all 4,366 smoke classes for
these fixture inputs. No runtime source, selection policy or generated behavior
changes in the correction. Use focused coverage of the exact failing class,
`TestSampleModsPackage`, `TestProjectScaffolder` and `TestPurposeStarters`, with
an absolute verified S2 REV01 ROM path so the load/rewind/save obligation executes.
This supplements the failed hosted smoke evidence without repeating unaffected
engine breadth locally. New hosted CI status is reported separately from local
regression results.

Correction validation: the queued normal-lane command below passed 22 tests across
all four classes with zero failures/errors/skips, including both failed hosted
cases and the S2 load/rewind/keyed-save check. The original S2 path was verified
as CRC32 `7B905383`, SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`.
Maven execution reported 1m51s and queue admission reported 0s wait. Exact command
from the correction worktree (Java 25 and Lua 5.4 environment), with the local
absolute original-ROM directory represented by `OPENGGF_ROM_ROOT`:

```sh
python3 tools/testing/maven_queue.py --normal -Dmse=off -Dtest=TestPhase2SampleModIntegration,TestSampleModsPackage,TestProjectScaffolder,TestPurposeStarters "-Dsonic2.rom.path=$OPENGGF_ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" test
```

Fixture README rendering, whitespace and guide code fences were checked. No
runtime implementation changes were needed. The hosted rerun remains a separate
pending result after the correction push; the local result is bounded validation.
