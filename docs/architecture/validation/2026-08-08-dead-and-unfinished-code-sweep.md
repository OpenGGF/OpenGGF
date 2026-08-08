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

Not yet run. Task 5 records the clean ROM-backed full-suite command and
`updated-baseline.tsv.gz` after main-workspace fast-forward.

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

### Merged result

Not yet run. Task 5 records `merged.tsv.gz`, comparison against the updated
baseline, and review of every outcome difference.
