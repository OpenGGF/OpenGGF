# Dead and unfinished code sweep validation

**Worktree:** `bugfix/ai-dead-unfinished-sweep` at `345fa27c2`
**Purpose:** reproducible pre-mutation baseline evidence.

## Environment and exploratory output

`mvn -v` reported Maven 3.9.16 on Java 21.0.11 (Arch Linux). The selected ROMs
match `AGENTS.md` after `cksum -a crc32b`, decimal-to-hex normalization, and
`sha1sum`:

```bash
# Run from the discovered-ROM repository root.
WORKTREE=$(pwd)
```

| Game | Selected path | CRC32 | SHA-1 |
|---|---|---|---|
| S1 World REV01 | `${WORKTREE}/Sonic The Hedgehog (W) (REV01) [!].gen` | `AFE05EEE` | `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B` |
| S2 World REV01 | `${WORKTREE}/Sonic The Hedgehog 2 (W) (REV01) [!].gen` | `7B905383` | `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9` |
| S3&K locked-on | `${WORKTREE}/Sonic and Knuckles & Sonic 3 (W) [!].gen` | `63522553` | `CFBF98C36C776677290A872547AC47C53D2761D6` |

The initial exploratory `mvn test` regenerated the tracked rewind-gap report.
Its diff contained generated gap-inventory additions/reordering only, with no
source change. It was restored using the exact command below before every later
Maven run and is unstaged:

```bash
git restore --source=HEAD --worktree -- docs/status/rewind-round-trip-gaps.md
```

The design's recorded exploratory baseline is 14,258 tests, 33 failures, 15
errors, and 35 skips.

## Surefire outcome manifest

`tools/test-reports/surefire-outcome-manifest.xsl` is an XSLT 1.0 normalizer.
Each sorted row has this exact structure:

```text
classname<TAB>method-or-display-name<TAB>PASS|FAILURE|ERROR|SKIPPED<TAB>exception-type<TAB>single-line-message
```

Passed and skipped rows are retained, so an execution disappearing or changing
class is observable. Use:

```bash
for report in target/surefire-reports/TEST-*.xml; do
  xsltproc tools/test-reports/surefire-outcome-manifest.xsl "$report"
done | LC_ALL=C sort | gzip -n > docs/architecture/validation/evidence/dead-code-sweep/RUN_NAME.tsv.gz
diff -u <(gzip -dc BASELINE.tsv.gz) <(gzip -dc CANDIDATE.tsv.gz)
```

The normalized row count must equal the Surefire test total. Every diff needs
review; only stated upstream or baseline changes are acceptable.

## Focused pre-mutation baselines

| Exact command | Result and manifest | Baseline outcome |
|---|---|---|
| `mvn clean -Dmse=off "-Dtest=com.openggf.tests.TestBuildToolingGuard,com.openggf.tests.TestArchUnitRules,com.openggf.tests.TestArchitecturalSourceGuard" test` | 98 rows = 98 tests; 95 PASS, 3 FAILURE, 0 ERROR, 0 SKIPPED. `focused-orphans-baseline.tsv.gz`. | ArchUnit rejects `trace -> graphics`; source ratchets reject `ObjectManager` (2972 vs 2914) and `AbstractPlayableSprite` (3175 vs 3161). |
| `mvn clean -Dmse=off "-Ds3k.rom.path=${WORKTREE}/Sonic and Knuckles & Sonic 3 (W) [!].gen" "-Dtest=com.openggf.tests.TestBuildToolingGuard,com.openggf.tools.disasm.TestRomOffsetFinderIncludedAsmLabels,com.openggf.trace.TestTraceV5LoadingContract,com.openggf.game.sonic3k.objects.TestCnzMinibossDefeatPhase,com.openggf.level.TestLevelManagerInitialPresentationPlcLifecycle,com.openggf.tests.TestS3kSpecialStageHeadlessBoot" test` | 91 rows = 91 tests; 89 PASS, 1 FAILURE, 1 ERROR, 0 SKIPPED. `focused-compat-baseline.tsv.gz`. | Build tooling guard cannot lock shared `.git/config`; S3K headless boot cannot load `liblwjgl.so`. |
| `mvn clean -Dmse=off "-Dtest=com.openggf.game.sonic2.objects.TestTodo4_MCZBossCollision,com.openggf.tests.TestS3kSpringObjectInstance,com.openggf.game.sonic3k.objects.TestSonic3kSpringObjectInstance,com.openggf.sprites.playable.TestSidekickCpuControllerCatchUpFlight,com.openggf.sprites.playable.TestSidekickCpuControllerFlightAutoRecovery" test` | 137 rows = 137 tests; 96 PASS, 1 FAILURE, 40 ERROR, 0 SKIPPED. `focused-comments-baseline.tsv.gz`. | Build tooling guard cannot create a temporary Git pack; 40 errors start with `liblwjgl.so` and cascade through `GlfwKeyNameResolver$Holder` across S3K spring and sidekick tests. |

The commands were serial and each used `mvn clean`. The generated rewind report
was clean after every command. These known red outcomes are the pre-mutation
comparison baseline, not sweep regressions.

## Reserved delivery comparisons

### Updated integration baseline

The main workspace remained on `develop`; fetch and `git merge --ff-only
origin/develop` left it current at `3f0fd4a70`. Existing untracked user files
were not touched. Maven again reported Java 21.0.11, and all three ROM hashes
matched the table above.

The planned default-four-fork command was run first:

```bash
WORKTREE=$(pwd)
mvn -Dmse=off \
  "-Dsonic1.rom.path=${WORKTREE}/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=${WORKTREE}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=${WORKTREE}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  clean test
```

Surefire reported 14,364 tests, 33 failures, 14 errors, and 31 skips. Its XML
contained 14,355 normalized testcase rows: 14,277 PASS, 33 FAILURE, 14 ERROR,
and 31 SKIPPED. This contextual run is preserved as
`updated-baseline-default-fork.tsv.gz`.

Task 4 had already exposed a shared LWJGL extraction race between default
forks. When the development default-fork run reproduced it at full-suite scale,
the design and plan were amended to use the repository's documented CI fork
mode for both sides of the comparison. The accepted baseline command is the
same command with `-Dsurefire.forkCount=1`; it reported and normalized exactly
14,341 tests: 14,263 PASS, 33 FAILURE, 14 ERROR, and 31 SKIPPED. That complete
manifest is `updated-baseline.tsv.gz`. After each main run, the only tracked
change was the generated rewind-gap report, which was inspected and restored.

### Development worktree result

Task 2 ran serially in `bugfix/ai-dead-unfinished-sweep` after deleting the
seven proven-obsolete types and relocating the unused Kosinski reference:

| Exact command | Result |
|---|---|
| `mvn clean -Dmse=off -DskipTests package` | Passed. Production and test sources compiled; the removed classes and `kosinski.txt` were absent from both `target/classes` and `target/OpenGGF-0.6.prerelease-jar-with-dependencies.jar`. |
| `mvn clean -Dmse=off "-Dtest=com.openggf.tests.TestBuildToolingGuard,com.openggf.tests.TestArchUnitRules,com.openggf.tests.TestArchitecturalSourceGuard" test` | 98 rows = 98 tests; 95 PASS, 3 FAILURE, 0 ERROR, 0 SKIPPED. `focused-orphans-development.tsv.gz` is byte-equivalent by normalized decompressed rows to `focused-orphans-baseline.tsv.gz`. The same `trace -> graphics`, `ObjectManager`, and `AbstractPlayableSprite` ratchets remain. |

The focused suite did not modify `docs/status/rewind-round-trip-gaps.md`, so no
rewind report was restored. Native resource JSON validated with `jq empty`; the
Kosinski include is absent and the broad `shaders/.*\\.(glsl|vert|frag)` include
remains.

### Task 3 compatibility cleanup result

After removing only the caller-free compatibility aliases, the protected
no-argument `LevelManager` constructor, and the six unverified duplicate S3K
results constants, Task 3 ran the following commands serially from this
worktree:

```bash
mvn clean -Dmse=off -DskipTests test-compile
WORKTREE=$(pwd)
mvn clean -Dmse=off \
  "-Ds3k.rom.path=${WORKTREE}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  "-Dtest=com.openggf.tests.TestBuildToolingGuard,com.openggf.tools.disasm.TestRomOffsetFinderIncludedAsmLabels,com.openggf.trace.TestTraceV5LoadingContract,com.openggf.game.sonic3k.objects.TestCnzMinibossDefeatPhase,com.openggf.level.TestLevelManagerInitialPresentationPlcLifecycle,com.openggf.tests.TestS3kSpecialStageHeadlessBoot" \
  test
```

Both commands passed. The focused run produced 91 normalized rows: 91 PASS, 0
FAILURE, 0 ERROR, 0 SKIPPED. Its normalized manifest is
`focused-compat-development.tsv.gz`. Compared with
`focused-compat-baseline.tsv.gz`, the only two changes are environmental
improvements: `TestBuildToolingGuard` can now write its temporary Git config,
and `TestS3kSpecialStageHeadlessBoot` can load `liblwjgl.so`; both changed from
the recorded baseline failure/error to PASS. The suite did not modify
`docs/status/rewind-round-trip-gaps.md`, so no generated rewind report was
restored. Exact-symbol scans found the removed trace aliases only in the
intentional guard literals; remaining non-code hits are historical sweep
evidence.

### Task 5 pre-merge focused reruns

After merging `develop` at `3f0fd4a70` into the worktree without conflict, the
Task 2–4 focused groups were rerun cleanly:

| Group | Result | Evidence |
|---|---|---|
| Orphan/source guards | 176 rows: 173 PASS, 3 FAILURE, 0 ERROR, 0 SKIPPED | `focused-orphans-premerge.tsv.gz`; the same pre-existing ArchUnit, `ObjectManager`, and `AbstractPlayableSprite` ratchets. |
| Compatibility owners | 91 rows: 90 PASS, 0 FAILURE, 1 ERROR, 0 SKIPPED | `focused-compat-premerge.tsv.gz`; only `TestS3kSpecialStageHeadlessBoot` failed to locate `liblwjgl.so`, the already documented host-native condition. |
| Comment owners, `-Dsurefire.forkCount=1` | 60 rows: 60 PASS | `focused-comments-premerge.tsv.gz`; includes the reverse-gravity characterization. |

### Task 5 development full-suite result

The rejected default-four-fork attempt failed native initialization early.
Once one reused fork could not load `liblwjgl.so`,
`GlfwKeyNameResolver$Holder` poisoned later configuration setup. Surefire
reported 12,271 tests, 31 failures, 2,860 errors, and 28 skips; the incomplete
XML held only 12,256 rows (9,344 PASS, 31 FAILURE, 2,853 ERROR, 28 SKIPPED).
`development-default-fork-incomplete.tsv.gz` is diagnostic evidence only; no
missing row is waived.

The accepted development command added `-Dsurefire.forkCount=1` to the exact
clean ROM-backed baseline command. It completed 14,342 normalized rows:
14,261 PASS, 34 FAILURE, 16 ERROR, and 31 SKIPPED. The baseline/development
manifest diff was reviewed row by row:

- the expected new
  `reverseGravitySwapsVerticalSpringDirectionDuringNativeInit` row is PASS;
- `TestTraceSessionLauncherProductionFailureCleanup` retained the same failure
  type/message apart from a volatile object identity hash;
- six pre-existing Tornado errors retained the same test identity and
  `NullPointerException` type, with only helpful-NPE message rendering absent;
- `productionObjectLifecycleRawCallCountsDoNotGrow` retained the exact same 13
  violation paths and budget; only filesystem traversal order changed after
  dead source files were removed; and
- three `TestGameLoopSpecialStageRewindGate` rows changed from PASS to two null
  focused-sprite errors and one failed rewind-engagement assertion. Full stacks
  enter unchanged `GameLoop`/camera/rewind code, none of which this branch
  modifies. The exact class was immediately rerun alone in the same worktree,
  JDK, and single-fork mode and passed 4/4. This isolates the difference to the
  class's documented suite-order/global-state fixture limitation rather than a
  sweep behavior change; no production or test fix was made in this cleanup.

The accepted manifest is `development.tsv.gz`. The generated rewind report was
the only tracked test output and was restored after the run. No baseline test
disappeared; development has exactly the one intentional added PASS row.

After the living documentation was refreshed, the focused documentation/build
guard command
`mvn -Dmse=off -Dsurefire.forkCount=1
"-Dtest=com.openggf.tests.TestBuildToolingGuard,com.openggf.configuration.TestModifierSupportDocumentation"
test` passed all 85 tests.

### Task 4 stale-marker characterization

The new
`reverseGravitySwapsVerticalSpringDirectionDuringNativeInit` characterization
passed before any comment edit, proving native UP becomes DOWN and native DOWN
becomes UP while reverse gravity is active. Two clean executions of the planned
default-fork focused command did not complete: the first emitted 51 passing
rows before `SIGBUS` terminated the `TestS3kSpringObjectInstance` and
`TestSidekickCpuControllerCatchUpFlight` forks; the second emitted 53 passing
rows before the catch-up fork terminated with the same `SIGBUS` in
`ld-linux-x86-64.so.2`. Neither partial run had a Java failure or error row,
but neither manifest is accepted because testcase rows are missing.

The before/after comparison therefore used the identical class set with the
repository's documented CI fork setting:

```bash
mvn clean -Dmse=off -Dsurefire.forkCount=1 \
  "-Dtest=com.openggf.game.sonic2.objects.TestTodo4_MCZBossCollision,com.openggf.tests.TestS3kSpringObjectInstance,com.openggf.game.sonic3k.objects.TestSonic3kSpringObjectInstance,com.openggf.sprites.playable.TestSidekickCpuControllerCatchUpFlight,com.openggf.sprites.playable.TestSidekickCpuControllerFlightAutoRecovery" \
  test
```

Both serial runs passed 60 tests with 60 PASS, 0 FAILURE, 0 ERROR, and 0
SKIPPED. `focused-comments-characterization.tsv.gz` and
`focused-comments-development.tsv.gz` each contain 60 normalized rows and
their decompressed diff is empty. The earlier 137-row baseline is not treated
as equivalent: it contains unrelated `TestBuildToolingGuard` rows and native
initialization errors, so its removed/reclassified rows remain unaccepted
environmental differences pending the required Task 5 full-suite comparison.
The stale-string scan returned no matches, and
`docs/status/rewind-round-trip-gaps.md` remained clean.

### Merged result

Not yet run. Task 5 records `merged.tsv.gz`, comparison against the updated
baseline, and review of every outcome difference.
