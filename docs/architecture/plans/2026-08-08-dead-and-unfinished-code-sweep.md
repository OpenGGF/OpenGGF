# Dead and Unfinished Code Sweep Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove reachability-proven dead production code and stale unfinished markers, while publishing a reproducible ranked audit of genuine unfinished runtime paths.

**Architecture:** This is an evidence-tiered cleanup with no runtime behavior, schema, ROM-address, or ownership change. Each deletion requires exact-name reachability proof; live or ambiguous omissions remain in production and receive a concrete audit disposition.

**Tech Stack:** Java 21, Maven/Surefire/JUnit 5, `rg`, Git worktrees, Markdown architecture artifacts.

## Global Constraints

- Runtime assets remain ROM-only.
- Do not delete or “complete” any behavior that lacks disassembly/source-of-truth evidence.
- Preserve reflection, registry, serialization, service-loader, CLI, Graal, and data-driven entry points.
- Do not stage the test-generated `docs/status/rewind-round-trip-gaps.md` change.
- Use the exact discovered ROM paths and verify their hashes before ROM-backed full-suite runs.
- Treat a new failure, new error, failure-to-error change, changed assertion/exception, or disappeared executed test as a regression.
- Execute implementation and Maven tasks serially in this shared worktree.
  Parallel work is limited to read-only searches and reviews; Tasks 1→2→3→4→5
  are strict dependencies because they share Git state, `target/`, Surefire
  reports, and generated tracked output.
- Never bypass commit hooks.

## File structure

### Create

- `docs/architecture/audits/2026-08-08-dead-and-unfinished-code.md` — scan method, deletion proof, exclusions, and ranked unfinished inventory.
- `docs/architecture/validation/2026-08-08-dead-and-unfinished-code-sweep.md` — JDK/ROM evidence, exact commands, baseline/development/post-merge comparisons.
- `tools/test-reports/surefire-outcome-manifest.xsl` — deterministic per-test Surefire outcome normalizer.
- `docs/architecture/validation/evidence/dead-code-sweep/*.tsv.gz` — compressed baseline/development/merged outcome manifests.

### Delete

- `src/main/java/com/openggf/debug/DebugArtViewer.java`
- `src/main/java/com/openggf/game/sonic2/debug/Sonic2SpecialStageDebugProvider.java`
- `src/main/java/com/openggf/game/sonic3k/dataselect/S3kSavePayload.java`
- `src/main/java/com/openggf/integration/presence/NoOpPresenceClient.java`
- `src/main/java/com/openggf/sprites/animation/VelocityAnimationProfile.java`
- `src/main/java/com/openggf/sprites/managers/DebugSpriteMovementManager.java`
- `src/main/java/com/openggf/timer/timers/SpindashCameraTimer.java`
- `src/main/resources/kosinski.txt` (content relocates to architecture research)

### Modify

- `src/main/resources/META-INF/native-image/com.openggf/OpenGGF/resource-config.json` — remove only the `kosinski\\.txt` include.
- `src/main/java/com/openggf/tools/disasm/DisassemblySearchResult.java` — remove deprecated `hasBinclude()`.
- `src/main/java/com/openggf/trace/TraceReplayBootstrap.java` — remove deprecated `usesS2TornadoRideStartForTraceReplay(...)`.
- `src/main/java/com/openggf/trace/TraceMetadata.java` — remove deprecated `hasPerFrameCnzSlotMachineState()`.
- `src/main/java/com/openggf/level/InitialProcessSpritesLevelManagerBase.java` — remove deprecated initial-object alias.
- `src/main/java/com/openggf/game/sonic3k/objects/CnzMinibossInstance.java` — remove deprecated one-argument test shim and stale caller claim.
- `src/main/java/com/openggf/level/LevelManager.java` — remove the throwing no-argument constructor.
- `src/main/java/com/openggf/game/sonic3k/specialstage/Sonic3kSpecialStageRomOffsets.java` — remove only the six unused results constants.
- `src/main/java/com/openggf/game/sonic2/objects/bosses/Sonic2MCZBossInstance.java` — correct stale collision TODO.
- `src/main/java/com/openggf/game/sonic3k/objects/Sonic3kSpringObjectInstance.java` — correct stale reverse-gravity description.
- `src/test/java/com/openggf/game/sonic3k/objects/TestSonic3kSpringObjectInstance.java` — prove reverse-gravity vertical subtype swapping.
- `src/main/java/com/openggf/sprites/playable/SidekickCpuController.java` — remove two stale task-history sentences.
- `CHANGELOG.md` — summarize the cleanup and audit.
- `README.md` — add the merge-policy release/change-log summary.
- `docs/guide/playing/game-status.md` — qualify current AIZ/S3K and Mecha Sonic parity.
- `docs/architecture/research/s3k-zones/aiz-analysis.md` — add dated current napalm/splash status.
- `docs/architecture/research/s3k-zones/lbz-analysis.md` — identify inert Big Arm as a blocker.
- `docs/architecture/research/s3k-zones/lrz-analysis.md` — record missing LRZ1 falling intro.
- `docs/architecture/research/s3k-zones/ssz-analysis.md` — record missing SSZ falling intro.
- `CONFIGURATION.md` — qualify special-stage debug controls by supported game.
- `docs/guide/playing/controls.md` — qualify special-stage debug controls by supported game.
- `docs/guide/cross-referencing/architecture-overview.md` — qualify SMPS parity.
- `docs/guide/contributing/audio-system.md` — document S3K coord-flag ownership/gaps.
- `docs/guide/contributing/architecture.md` — document S3K coord-flag ownership/gaps.
- `S3K_OBJECT_CHECKLIST.md` — distinguish registry coverage from behavior parity.
- `docs/status/s3k-known-bugs.md` — add current unfinished S3K entries.

### Retain and document

- `src/main/java/com/openggf/game/sonic2/objects/bosses/CNZBossAnimations.java`
- `src/main/java/com/openggf/game/sonic3k/specialstage/Sonic3kSpecialStageScalars.java`
- `src/main/java/com/openggf/graphics/DebugColorShaderProgram.java`
- `src/main/java/com/openggf/graphics/DebugPrimitiveRenderer.java`
- `src/main/resources/shaders/shader_debug_text.frag`
- `src/main/resources/shaders/shader_debug_text.vert`

These are coherent unfinished features or ROM-derived work, not deletion
targets. Create `docs/architecture/research/compression/kosinski-format.md` by
relocating the useful `kosinski.txt` content and adding current-owner/provenance
context.

---

### Task 1: Freeze the reachability and unfinished-code audit

**Files:**

- Create: `docs/architecture/audits/2026-08-08-dead-and-unfinished-code.md`
- Create: `docs/architecture/validation/2026-08-08-dead-and-unfinished-code-sweep.md`
- Create: `tools/test-reports/surefire-outcome-manifest.xsl`
- Create: focused baseline manifests under `docs/architecture/validation/evidence/dead-code-sweep/`

**Interfaces:**

- Consumes: the design's exact deletion list and marker-disposition taxonomy.
- Produces: durable proof, comparison tooling, focused baselines, and follow-up
  contracts used by Tasks 2–5 and reviewers.

- [ ] **Step 1: Restore and classify the exploratory generated report**

Confirm `docs/status/rewind-round-trip-gaps.md` differs only because `mvn test`
regenerated it in this isolated worktree, then restore it to branch `HEAD`.
Require that file to be clean before every later Maven run that can regenerate
it. Do not stage it.

- [ ] **Step 2: Verify JDK 21 and discover the three ROMs**

Run `mvn -v`, `find` at the project root for `.gen` files, then verify the three
selected files with:

```bash
cksum -a crc32b "/actual/discovered/path/game.gen"
sha1sum "/actual/discovered/path/game.gen"
```

Normalize GNU `cksum`'s decimal CRC before comparing it with `AGENTS.md`:

```bash
rom_crc_dec=$(cksum -a crc32b "/actual/discovered/path/game.gen" | awk '{print $1}')
printf '%08X\n' "$rom_crc_dec"
```

Record actual paths and match all uppercase eight-digit CRC32/SHA-1 values from
`AGENTS.md` before any ROM-backed focused test.

- [ ] **Step 3: Re-run the exact top-level reachability scan**

For each of the seven Java deletion-candidate names and four retained
declaration-only feature names, run a repo-wide exact-word search over
tracked files, then classify each match as declaration, current evidence,
historical artifact, or live reachability. Explicitly cover `src/main`,
`src/test`, `tools`, `scripts`, root `run*` / `dev*` launchers, `.github`, `.mvn`,
`pom.xml`, `config.yaml`, `config.yaml.example`, `README.md`,
`CONTRIBUTING.md`, `CONFIGURATION.md`, current support docs, `META-INF/services`,
Graal configs, and resources. For `DebugArtViewer`, also run:

```bash
git log --all --follow --oneline -- src/main/java/com/openggf/debug/DebugArtViewer.java
```

Record why its history shows only incidental maintenance rather than a
supported tool contract.

- [ ] **Step 4: Re-run the explicit unfinished-marker scan**

Run:

```bash
rg -n -i --glob 'src/main/**' --glob 'tools/**' \
  '(TODO|FIXME|\bstub(bed)?\b|\bscaffold\b|not (yet )?implemented|\bno-op\b|Phase [0-9]+)'
```

Classify every genuine match as dead, stale, unfinished, intentional contract,
generated output, historical/tooling note, unsupported broader game mode, or
ambiguous ownership. Ordinary phase labels and descriptions of intentionally
no-op state transitions are intentional contracts.

- [ ] **Step 5: Create the Surefire manifest normalizer**

Create `tools/test-reports/surefire-outcome-manifest.xsl` with this complete
XSLT 1.0 transform:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="text" encoding="UTF-8"/>
  <xsl:template match="/">
    <xsl:for-each select="//testcase">
      <xsl:value-of select="@classname"/><xsl:text>&#9;</xsl:text>
      <xsl:value-of select="@name"/><xsl:text>&#9;</xsl:text>
      <xsl:choose>
        <xsl:when test="failure"><xsl:text>FAILURE</xsl:text><xsl:text>&#9;</xsl:text><xsl:value-of select="failure/@type"/><xsl:text>&#9;</xsl:text><xsl:value-of select="translate(failure/@message, '&#10;&#13;&#9;', '   ')"/></xsl:when>
        <xsl:when test="error"><xsl:text>ERROR</xsl:text><xsl:text>&#9;</xsl:text><xsl:value-of select="error/@type"/><xsl:text>&#9;</xsl:text><xsl:value-of select="translate(error/@message, '&#10;&#13;&#9;', '   ')"/></xsl:when>
        <xsl:when test="skipped"><xsl:text>SKIPPED</xsl:text><xsl:text>&#9;</xsl:text><xsl:value-of select="skipped/@type"/><xsl:text>&#9;</xsl:text><xsl:value-of select="translate(skipped/@message, '&#10;&#13;&#9;', '   ')"/></xsl:when>
        <xsl:otherwise><xsl:text>PASS</xsl:text><xsl:text>&#9;&#9;</xsl:text></xsl:otherwise>
      </xsl:choose>
      <xsl:text>&#10;</xsl:text>
    </xsl:for-each>
  </xsl:template>
</xsl:stylesheet>
```

After each Maven run, normalize and retain the report with:

```bash
mkdir -p docs/architecture/validation/evidence/dead-code-sweep
for report in target/surefire-reports/TEST-*.xml; do
  xsltproc tools/test-reports/surefire-outcome-manifest.xsl "$report"
done | LC_ALL=C sort | gzip -n > docs/architecture/validation/evidence/dead-code-sweep/RUN_NAME.tsv.gz
```

Validate the row count against Surefire totals. Comparison is executable:

```bash
diff -u <(gzip -dc BASELINE.tsv.gz) <(gzip -dc CANDIDATE.tsv.gz)
```

Any diff is reviewed; only explicitly attributable baseline/upstream changes
may be accepted.

- [ ] **Step 6: Write the audit and validation skeleton**

Include:

- repository size and scan commands;
- the seven deleted Java types, four retained unfinished types, retained debug
  shaders, and relocated compression document with exact dispositions;
- the six compatibility methods/constructor and six S3K constants;
- the three stale-comment sites;
- the complete P0–P3 unfinished table from the design;
- `AbstractLevel.markAllDirty()`, CPZ debug placement, and human-P2 monitors;
- a documentation freshness map naming the authoritative current status,
  roadmap, research, or living-reference file for each unfinished finding and
  the exact claim that needs correction;
- intentional exclusions and heuristic limitations;
- removed file/line totals; and
- exact recommended source/test evidence for every deferred item.

The validation skeleton records JDK/ROM proof, the original exploratory totals,
the manifest format, and empty named sections for focused baseline,
updated-integration baseline, development, and merged results.

- [ ] **Step 7: Record exact focused pre-mutation baselines**

Run the exact focused commands later used by Tasks 2–4, each with `mvn clean`
and the discovered S3K ROM property where required. After each run, restore only
the test-generated rewind report diff, create `focused-orphans-baseline.tsv.gz`,
`focused-compat-baseline.tsv.gz`, and `focused-comments-baseline.tsv.gz`, and
record exact outcomes in the validation skeleton.

- [ ] **Step 8: Self-review the audit and tooling**

Run:

```bash
rg -n 'TBD|implement later|and other|similar to' \
  docs/architecture/audits/2026-08-08-dead-and-unfinished-code.md
xmllint --noout tools/test-reports/surefire-outcome-manifest.xsl
```

Expected: no placeholder/open-ended inventory language and valid XSLT. Check
every design row has a matching audit disposition.

- [ ] **Step 9: Commit the reviewed execution contract and baselines**

Stage the design, implementation plan, audit, validation skeleton, XSLT, and
focused baseline manifests, then commit with the repository trailer block. Do
not stage `docs/status/rewind-round-trip-gaps.md`.

### Task 2: Remove obsolete types and relocate the compression document

**Files:**

- Delete: the seven Java files and `src/main/resources/kosinski.txt` listed in
  File structure.
- Modify: `src/main/resources/META-INF/native-image/com.openggf/OpenGGF/resource-config.json`.
- Create: `docs/architecture/research/compression/kosinski-format.md`.

**Interfaces:**

- Consumes: Task 1 exact-name proof.
- Produces: a compiling source/resource tree with no supported or unfinished
  feature removed, and compression reference material in the correct docs owner.

- [ ] **Step 1: Verify the pre-deletion proof still holds**

Run the Task 1 exact-name searches and verify only declarations, historical
artifacts, or the documented direct shader-color consumers remain. If any new
live reference appears, stop deletion of that candidate and amend the audit.

- [ ] **Step 2: Delete obsolete files and relocate the compression reference**

Remove exactly the seven obsolete Java files. Move the useful `kosinski.txt`
content to `docs/architecture/research/compression/kosinski-format.md`, add its
original attribution/provenance and links to current Kosinski decompressor
owners, then remove the runtime resource. In the Graal resource config remove
only:

```json
{ "pattern": "kosinski\\.txt" },
```

Keep the broad shader resource pattern, all debug shaders, the three retained
unfinished feature types, and `CNZBossAnimations`.

- [ ] **Step 3: Compile production and tests**

Run:

```bash
mvn clean -Dmse=off -DskipTests package
```

Expected: the command passes. A missing-symbol error means the candidate had
static reachability and must be restored/reclassified. Search `target/classes`
and `jar tf target/*jar-with-dependencies.jar`; every deleted FQCN and
`kosinski.txt` must be absent.

- [ ] **Step 4: Run focused source/build guards**

Run:

```bash
mvn clean -Dmse=off \
  "-Dtest=com.openggf.tests.TestBuildToolingGuard,com.openggf.tests.TestArchUnitRules,com.openggf.tests.TestArchitecturalSourceGuard" \
  test
```

Expected: no new test failure or error compared with the recorded baseline;
known architecture ratchets may remain red with identical messages.
Create `focused-orphans-development.tsv.gz`, diff it against
`focused-orphans-baseline.tsv.gz`, then inspect and restore only a
suite-generated rewind-report diff.

- [ ] **Step 5: Verify deletion and diff quality**

Run exact-name searches for the deleted types/resources, task-scoped
`git diff --check`, and `git diff --stat`. Historical artifacts and the current
design/plan/audit/validation evidence may retain deleted names.

Validate native resource configuration explicitly:

```bash
jq empty src/main/resources/META-INF/native-image/com.openggf/OpenGGF/resource-config.json
rg -n 'kosinski\\\\\.txt|shaders/\.\*' \
  src/main/resources/META-INF/native-image/com.openggf/OpenGGF/resource-config.json
```

Expected: valid JSON, no Kosinski resource entry, and the broad shader pattern
still present.

- [ ] **Step 6: Commit the orphan cleanup**

Commit only the type/resource deletion batch with required trailers.

### Task 3: Remove caller-free compatibility APIs and duplicate constants

**Files:**

- Modify: the seven compatibility/constant owners listed in File structure.
- Test: existing owner and build-guard tests; no new behavior test is needed for
  removal of caller-free APIs.

**Interfaces:**

- Consumes: generic replacement APIs already present in each owner.
- Produces: no compatibility alias; all live callers continue using the generic/current API.

- [ ] **Step 1: Prove each old symbol remains caller-free**

Use exact-symbol `rg` across production/tests/resources. The two trace aliases
may appear in guard strings that reject their use; retain those guards. The CNZ
one-argument shim may appear only in its stale Javadoc.

- [ ] **Step 2: Remove the exact deprecated members**

Delete only:

```text
DisassemblySearchResult.hasBinclude()
TraceReplayBootstrap.usesS2TornadoRideStartForTraceReplay(TraceData)
TraceMetadata.hasPerFrameCnzSlotMachineState()
InitialProcessSpritesLevelManagerBase.consumePendingInitialObjectSetupPass()
CnzMinibossInstance.setLower2CounterForTest(int)
LevelManager.LevelManager()
```

Do not rename or change their retained replacements.

- [ ] **Step 3: Remove the exact S3K duplicate results constants**

Delete the three `ART_KOS_RESULTS_*` / `PAL_RESULTS` address constants and their
three size constants from `Sonic3kSpecialStageRomOffsets`. Do not alter verified
special-stage offsets or `areOffsetsVerified()`.

- [ ] **Step 4: Compile and run focused tests**

Run:

```bash
WORKTREE=$(pwd)
mvn clean -Dmse=off -DskipTests test-compile
mvn clean -Dmse=off \
  "-Ds3k.rom.path=${WORKTREE}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  "-Dtest=com.openggf.tests.TestBuildToolingGuard,com.openggf.tools.disasm.TestRomOffsetFinderIncludedAsmLabels,com.openggf.trace.TestTraceV5LoadingContract,com.openggf.game.sonic3k.objects.TestCnzMinibossDefeatPhase,com.openggf.level.TestLevelManagerInitialPresentationPlcLifecycle,com.openggf.tests.TestS3kSpecialStageHeadlessBoot" \
  test
```

Expected: all selected owner tests pass; pre-existing build-guard failures
remain identical.
Create `focused-compat-development.tsv.gz`, diff it against
`focused-compat-baseline.tsv.gz`, then inspect and restore only a
suite-generated rewind-report diff.

- [ ] **Step 5: Verify no dead symbol remains**

Search every deleted symbol. Expected: only intentional guard strings,
historical artifacts, and the current design/plan/audit/validation evidence
remain.

- [ ] **Step 6: Commit the compatibility cleanup**

Commit this batch separately with required trailers.

### Task 4: Correct stale unfinished markers

**Files:**

- Modify: `Sonic2MCZBossInstance.java`.
- Modify: `Sonic3kSpringObjectInstance.java`.
- Modify: `SidekickCpuController.java`.
- Modify: `src/test/java/com/openggf/game/sonic3k/objects/TestSonic3kSpringObjectInstance.java`.

**Interfaces:**

- Consumes: existing MCZ harmful-touch callback, S3K spring reverse-gravity
  direction swap, and completed sidekick routines.
- Produces: source comments that describe current ownership without changing code.

- [ ] **Step 1: Add the missing reverse-gravity spring proof**

In `TestSonic3kSpringObjectInstance`, import `java.lang.reflect.Field` and add:

```java
@Test
void reverseGravitySwapsVerticalSpringDirectionDuringNativeInit() throws Exception {
    GameStateManager gameState = new GameStateManager();
    gameState.setReverseGravityActive(true);

    Sonic3kSpringObjectInstance up = new Sonic3kSpringObjectInstance(
            new ObjectSpawn(0x100, 0x100, Sonic3kObjectIds.SPRING, 0x00, 0, false, 0));
    up.setServices(new TestObjectServices().withGameState(gameState));
    invoke(up, "ensureInitialized");

    Sonic3kSpringObjectInstance down = new Sonic3kSpringObjectInstance(
            new ObjectSpawn(0x100, 0x100, Sonic3kObjectIds.SPRING, 0x20, 0, false, 0));
    down.setServices(new TestObjectServices().withGameState(gameState));
    invoke(down, "ensureInitialized");

    assertEquals(4, intField(up, "springType"),
            "Reverse_gravity_flag swaps native UP to DOWN during Obj_Spring init");
    assertEquals(0, intField(down, "springType"),
            "Reverse_gravity_flag swaps native DOWN to UP during Obj_Spring init");
}

private static int intField(Object target, String fieldName) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.getInt(target);
}
```

This is a characterization test for already-present behavior, so it should pass
before the comment changes. A failure means retain/reclassify the comment.

- [ ] **Step 2: Pin current behavior with focused tests**

Run:

```bash
mvn clean -Dmse=off \
  "-Dtest=com.openggf.game.sonic2.objects.TestTodo4_MCZBossCollision,com.openggf.tests.TestS3kSpringObjectInstance,com.openggf.game.sonic3k.objects.TestSonic3kSpringObjectInstance,com.openggf.sprites.playable.TestSidekickCpuControllerCatchUpFlight,com.openggf.sprites.playable.TestSidekickCpuControllerFlightAutoRecovery" \
  test
```

These pin MCZ harmful drill contact, both S3K spring owner surfaces, and the
sidekick catch-up/automatic-flight routines. Record the exact test outcomes in
the validation report. Create `focused-comments-characterization.tsv.gz` and
diff it against `focused-comments-baseline.tsv.gz`. The only permitted added
row is:

```text
com.openggf.game.sonic3k.objects.TestSonic3kSpringObjectInstance	reverseGravitySwapsVerticalSpringDirectionDuringNativeInit	PASS		
```

Every pre-existing row must remain identical.

Observed execution amendment (2026-08-08): two clean executions of the command
above ran the new assertion successfully but default-fork native initialization
terminated with `SIGBUS` after producing only 51 and 53 passing rows. Do not
accept either partial manifest or waive its missing rows. Re-run the identical
class set with `-Dsurefire.forkCount=1` before the comment edits and use that
complete serial manifest as the characterization side of the Task 4 behavioral
comparison. Record both default-fork attempts and the serial fallback in the
validation report. The updated full-suite baseline comparison in Task 5 is
still mandatory.

- [ ] **Step 3: Correct the MCZ comment**

Replace the stale TODO in `onHitTaken(...)` with:

```java
// Harmful drill contact owns this flag in onTouchResponse(...); a successful
// attack on the boss deliberately does not set it.
```

Keep the nearby ROM citations and do not alter collision code.

- [ ] **Step 4: Correct the spring and sidekick comments**

Change “Reverse gravity support (DEZ gravity-flip, currently stubbed)” to
“Reverse gravity support (DEZ gravity-flip)”. Remove only the two “Stubbed in
Task 2; body lands in Task 4/5” paragraphs from `SidekickCpuController`.

- [ ] **Step 5: Assert stale text is gone and behavior is unchanged**

Run:

```bash
rg -n 'currently stubbed|Stubbed in Task 2|TODO: Wire boss_hurt_sonic' \
  src/main/java/com/openggf
```

Expected: no matches. Re-run the focused owner tests from Step 2; outcomes must
be identical, using `-Dsurefire.forkCount=1` to match the amended complete
characterization run. Create `focused-comments-development.tsv.gz`, diff it
against `focused-comments-characterization.tsv.gz`, then inspect and restore
only a suite-generated rewind-report diff. No missing testcase row is an
acceptable comparison result.

- [ ] **Step 6: Commit the stale-marker cleanup**

Commit the reverse-gravity characterization test and the three comment
corrections with required trailers.

### Task 5: Verification and delivery documentation

**Files:**

- Modify: `docs/architecture/validation/2026-08-08-dead-and-unfinished-code-sweep.md`.
- Modify: `CHANGELOG.md`.
- Modify: `README.md`.
- Modify: `docs/guide/playing/game-status.md`.
- Modify: `docs/architecture/research/s3k-zones/aiz-analysis.md`.
- Modify: `docs/architecture/research/s3k-zones/lbz-analysis.md`.
- Modify: `docs/architecture/research/s3k-zones/lrz-analysis.md`.
- Modify: `docs/architecture/research/s3k-zones/ssz-analysis.md`.
- Modify: `CONFIGURATION.md`.
- Modify: `docs/guide/playing/controls.md`.
- Modify: `docs/guide/cross-referencing/architecture-overview.md`.
- Modify: `docs/guide/contributing/audio-system.md`.
- Modify: `docs/guide/contributing/architecture.md`.
- Modify: `S3K_OBJECT_CHECKLIST.md`.
- Modify: `docs/status/s3k-known-bugs.md`.

**Interfaces:**

- Consumes: Tasks 1–4 and the updated `develop` baseline.
- Produces: reproducible no-regression evidence and merge-policy documentation.

- [ ] **Step 1: Fetch and update the main integration baseline**

In the main workspace, preserve all user changes, fetch the remote, and
fast-forward `develop`. Do not switch branches. If pulling would overwrite user
changes, stop integration and report the exact conflict.

- [ ] **Step 2: Record the updated main-workspace baseline**

Require `docs/status/rewind-round-trip-gaps.md` to be clean in the main
workspace, then run the exact clean ROM-backed full-suite command from the
design on updated `develop`. Use the worktree's reviewed XSLT to create
`updated-baseline.tsv.gz` in the worktree evidence directory. Restore only the
suite-generated rewind-report diff in main. Copy JDK/ROM evidence, command,
commit, totals, and failure/error summaries into the validation report.

- [ ] **Step 3: Update and verify the development worktree**

Merge/rebase the worktree branch onto updated `develop` without switching the
main workspace. Resolve conflicts carefully. Run focused tests from Tasks 2–4,
then require the rewind report clean and run the exact clean ROM-backed suite.
Create `development.tsv.gz`, restore only the suite-generated rewind-report
diff, and compare with:

```bash
diff -u \
  <(gzip -dc docs/architecture/validation/evidence/dead-code-sweep/updated-baseline.tsv.gz) \
  <(gzip -dc docs/architecture/validation/evidence/dead-code-sweep/development.tsv.gz)
```

Review every diff and record the result. Any unexplained outcome change blocks
integration.

Observed execution amendment (2026-08-08): the default four-fork development
full suite reproduced Task 4's shared LWJGL native-extraction race, emitted only
12,256 normalized testcase rows, and cascaded to 2,853 errors after
`liblwjgl.so` initialization failed. Preserve that run only as
`development-default-fork-incomplete.tsv.gz`. Add
`-Dsurefire.forkCount=1` to the clean ROM-backed command and rerun both the
updated main-workspace baseline and development suite; these complete serial
manifests replace the default-fork pair as the comparison gate. Use the same
serial command for post-merge verification.

- [ ] **Step 4: Refresh current progress documentation**

Apply the Task 1 documentation freshness map to the exact current
files listed in File structure:

- qualify README/game-status AIZ claims and add the Mecha Sonic ordering debt;
- add dated AIZ, concrete LBZ blocker, and LRZ/SSZ falling-intro zone notes;
- mark F12/F3 special-stage debug controls S2-only/capability-dependent;
- qualify SMPS parity and name `Sonic3kCoordFlagHandler` plus its three discarded
  meta-command semantics;
- clarify the S3K checklist's registry-coverage meaning; and
- add current S3K-known-bug entries for napalm, Big Arm, falling intros, and
  AIZ end-boss splash children.

Preserve historical designs, plans, validations, changelog history, and the
historical trace investigations inside `s3k-known-bugs.md`. Ensure retained
unfinished features are described as retained/unwired rather than dead or
complete.

- [ ] **Step 5: Complete release documentation**

Add a concise cleanup/audit entry to `CHANGELOG.md` and the required
release/change-log summary to `README.md`. Record removed file/line totals,
notable retained unfinished work, and no-runtime-behavior scope.

- [ ] **Step 6: Independent end-to-end review**

Have a reviewer check requirements traceability, every deletion proof, audit
completeness, historical-doc preservation, test evidence, policy trailers, and
no-regression comparison. Fix every valid finding and repeat until green.

- [ ] **Step 7: Commit final docs and merge**

Commit all remaining task artifacts and release docs with required trailers.
Merge the worktree branch directly into main-workspace `develop` without
switching the main workspace branch.

- [ ] **Step 8: Post-merge verification**

The main-workspace copy owns all post-merge evidence edits. Require the rewind
report clean, run the exact clean ROM-backed suite on merged `develop`, create
`merged.tsv.gz`, restore only the suite-generated rewind-report diff, and diff
it against `updated-baseline.tsv.gz`. No baseline-passing test may fail, no red
baseline outcome may worsen/change due to this work, and no executed test may
disappear. Record exact results in the main-workspace validation report.

- [ ] **Step 9: Commit post-merge evidence**

On main-workspace `develop`, make an ordinary policy-compliant documentation
commit containing the updated validation report and `merged.tsv.gz`. Do not
amend the merge commit or an earlier worktree commit.

- [ ] **Step 10: Push and clean up**

Push only main-workspace `develop`. Verify the worktree has no uncommitted or
unmerged work, remove generated outputs, remove the worktree, delete the fully
merged local `bugfix/ai-dead-unfinished-sweep` branch, and prune worktree
metadata.

## Plan self-review

- Spec coverage: every design requirement maps to Tasks 1–5.
- Placeholder scan: no implementation placeholder or open-ended cleanup target remains.
- Type consistency: every retained replacement API is named in Task 3.
- Ownership: tasks execute serially; Task 5 alone owns integration and release docs.
- Test strategy: deletion proof precedes deletion; no behavior change is claimed without owner tests.
