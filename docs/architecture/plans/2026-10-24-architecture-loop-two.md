# Architecture cleanup, second October 24 loop

## Scope and base

The previous [follow-through](2026-10-24-architecture-followthrough.md) is
integrated. This loop starts from `85d32cda4fe333884f30e204749a3f5ddeef5923`
on `develop`, after fetching and fast-forward checking the destination.
The user authorized another bounded loop. All delegates use standard speed.

Two independent questions are in scope:

- Close standalone audio composition's reverse dependency on the desktop
  sound-test application while preserving playback and resource ownership.
- Reproduce and repair false-floor rewind after some blocks have retired,
  then close its field disposition only if destructive restoration and forward
  replay establish the contract.

Background-renderer lifetime remains with its verified graphics/context owner.
The five associated allowances are not targets for this loop. Pattern value
placement, broader RenderContext lifetime and mechanical lifecycle substitution
remain outside the selected scope.

## Grounding and design checkpoint

`SoundTestApp` combines ROM/profile/catalog selection with console, Swing and
WAV-export behavior. Its reusable presentation host calls back into that
application to construct a concrete profile. `HubMusicController` also uses the
host, despite already obtaining its own detached profile. The host owns the
producer executor, handler state and final PCM sink; its established timeout,
cross-thread dispatch and close behavior are preservation constraints.

The design comparison considers moving the composition owner as a unit versus
keeping the audio host below runtime composition and passing its selected
profile from each caller. Compatibility, all consumers and exact allowance
identities determine the cut, not an aggregate-count target.

The first design preserves the host's explicit game ID alongside the supplied
profile. S1 and S2 profiles inherit `presentationGameId()`'s `"base"` default;
deriving the bound ID from that method or enforcing equality would change their
behavior. Only the existing supported-ID validation belongs in this migration.
The two production callers already possess the profile they need. Existing
package-private WAV tests move with the application; host fixture tests remain
beside the host. The shell launcher, live example reference and exact AWT
exception must follow the application. Historical dated plans remain historical.

Both independent designs converged on intact application relocation plus profile
injection. Their alternative moved the host with the application. That leaves
the hub's reusable runtime presentation owner under tooling and preserves its
hidden dependency on desktop composition; it was rejected for ownership, not
because `game -> tools` is forbidden by the current package-pair guard.
Moving both classes into `game.audio` would instead require a new concrete-game
construction exemption and put Swing composition in the runtime domain.

The grounding pass also rejected expanding `GameModuleRegistry` for standalone
profile/catalog selection. Its static initialization constructs a default S2
module. Keeping standalone selection in the relocated application avoids a new
cold-start dependency and requires no additional factory or service contract.

The cross-judge selected the first candidate's behavioral characterization and
the second candidate's narrow dependency guard. The final contract is:

```java
public static StandaloneAudioPresentationHost open(
        String gameId, GameAudioProfile profile,
        SonicConfigurationService config, PerformanceProfiler profiler,
        boolean noDevice);

public static StandaloneAudioPresentationHost open(
        String gameId, GameAudioProfile profile,
        SonicConfigurationService config, PerformanceProfiler profiler,
        boolean noDevice, UnaryOperator<AudioPresentationSink> decorateSink);
```

Both callers pass the same profile they already used to load their ROM audio.
The host validates the existing ID first, then rejects a null profile before
allocating a sink. Each host still creates its own coordination-handler owner.
The old overloads and application callback are deleted together. No pinned
creator interface changes. The direct-dependency guard names the desktop app
and its nested classes; it does not claim complete transitive native-image
closure. The expected frozen reduction is eleven exact records, 167 to 156,
subject to strict guard verification.

The false-floor obligation is still open at the base. Source review found that
surviving children can reconstruct densely while the saved collapse cursor
retains original positions. The previous all-eight-child check does not prove
partial-collapse restoration. This loop requires an observed failing control
before claiming that defect is reproduced or repaired.

The worker observed four failing partial-collapse cases against unchanged base
production code. With block 0 retired, the next dispatch signaled block 2 and
left block 1 alive. Each restored master also remained active after 200 ticks;
the existing eight-child case passed. These failures discriminate the reported
cursor/list mismatch. The fix must select the original block index after child
scalars have been restored, without changing the native countdown or cursor.

## Validation and ownership

Workers own focused checks in their separate worktrees. The coordinator owns
combined validation, documentation, integration, push and cleanup. Future Maven
commands use the current main-checkout queue wrapper by absolute path while
retaining each worktree's working directory and build output. Existing queued
requests remain alive. ROM properties refer to the existing absolute paths.

Before delivery, inspect the change-based category plan against the pinned base
and select proportionate behavior, API and fresh-JVM structural checks. Ordinary
development does not use the release-only full suite. Record exact commands,
executed identities, failures and skips here after inspection; a queued request
is pending work, not a result.

Current checkpoint: false-floor commit
`b2aca1b3da1f250a6a6411195ae59acabbd68934` and audio commit
`d6429937241c58defd2a4efad1d6246276814a34` are composed at
`e7fca7a967154288b851e99be31979784f2bcc96`. Their reviewed diffs are unchanged.
The audio store contains 156 exact records. Combined delivery validation and
the worker's real relocated CLI help and shell checks passed. There are no
unresolved implementation or test gates in this bounded candidate.

The coordinator's actual display-free Java 25/Lua 5.4 environment passed
`python3 tools/testing/run_categories.py --base 85d32cda4fe333884f30e204749a3f5ddeef5923 --preflight`.
This checked tool prerequisites and executed no tests.

The audio worker's unchanged-production baseline passed 51 tests with zero
failures, errors or skips: 26 parser/catalog/probe characterization cases,
14 host cases, two WAV cases, five hub-controller cases, three ROM-theme cases
and one hub-sink case. It verified the nested ROM WAV identity and all three
theme identities. Admission took 182 seconds; Maven execution took 77 seconds.
This is baseline evidence, not verification of the moved implementation.

Independent static review accepted the false-floor four-file code/test/baseline
diff. Its SHA-256 was
`0057908eda094ef75c21ab89d47dda0d6d3f19fc553b10cd51d86f74de3f360a`,
confirmed unchanged by the coordinator. The review checked native pointer-table
semantics, attachment before scalar restoration and the transient disposition.
It did not execute tests. Engine-to-engine replay does not certify independent
ROM timing, player contact, missing future targets or duplicate attachment.

The final false-floor selection passed 39 tests across eight classes, with zero
failures, errors or skips. Its exact commands and baseline failures are in the
[field audit](../audits/2026-10-24-rewind-disposition-review.md#false-floor-partial-collapse-follow-up).
The reviewed four-file diff is unchanged in `b2aca1b3d`. This closes the exact
`Sonic1FalseFloorInstance#childBlocks` obligation, reducing the current field
baseline from 18 to 17 keys. The canonical bug entry is removed and the level
coverage backlog retains the local proof and unverified route/contact breadth.

The destination advanced to `aefa872ea1c534008ef9056760726874b45ed1f8`
during validation. Merge `94046667f` reconciles its disjoint queue-tooling
revision-four registry, diagnostic profiler and dashboard changes without
conflicts. It changes no Java, POM or fixture. Existing worker requests retain
their loaded tooling and selections; future Maven commands continue to use the
main-checkout wrapper. The original integration base remains the comparison
reference. Upstream's reported cold 50-class qualification is separate evidence,
not a loop-two engine result.

The moved audio implementation passed 55 focused behavior tests with zero
failures, errors or skips. It retains the 51 baseline cases and adds four host
cases for the supplied profile, separate coordination state, null-profile
rejection before sink decoration and invalid-ID precedence. Admission took 415
seconds. The complete structural guards and API checks are assigned to the
coordinator, replacing the worker's unsubmitted overlapping guard/API selection.
No existing request was restarted or narrowed for admission.

Independent static audio review found no blocking issue and confirmed the
staged/current diff hash
`2c3409de0bffe311d93eb7f29ca9fcd29a377407916d9faabf4c8beb4d5b6f8e`.
The coordinator independently matched that hash. Profile reuse adds no identified
shared mutable playback state; host state is still allocated separately. The
review did not run tests or inspect build output.

The final change-based plan selects all 3,662 ordinary smoke classes, plus
guards. Shared/unclassified triggers include the launch script, example prose,
hub caller, central policy table, allowance resources and upstream test tooling.
That automatic scope is disproportionate to the established change. The audio
host's behavior bodies are unchanged, both production callers are covered, and
the relocated application has an observed before/after contract. The false-floor
change affects only original-index selection and one exact policy entry; its
shared capture algorithm is unchanged and destructive replay has failed before
and passed after. The unrelated upstream tooling has its own qualification.
Focused behavior evidence plus complete fresh-JVM guards, API pins and the four
required S3K stability classes therefore replace all ordinary smoke categories.
No release-only full suite or broad ordinary smoke run is requested.

The composed API/S3K invocation completed in session `82633` at `e7fca7a96`
plus the coordinator's documentation-only changes. All 71 cases passed with
zero failures, errors or skips across six reports. XML inspection confirmed
eleven API checks, eight AIZ1 skip cases, six bootstrap cases, three decoding
cases, and both level-loading classes (36 and seven cases). Admission took 18
seconds; Maven execution took 90 seconds including compilation. The 12 inspected
XML/text report files were removed before the separate guard invocation.

The complete fresh-JVM `-Pguards` invocation completed in session `53227` with
748 tests across 90 reports, zero failures, errors or skips. It includes all
33 ArchUnit checks, the four new app-boundary controls, nine accounting checks,
77 architectural source checks and 123 build-tooling controls. The exact AWT
exception and both rewind disposition/static-state guards passed. Admission
took 32 seconds; Maven execution took 319 seconds. The XML inspection used the
suite identity for ArchUnit because its individual testcase `classname` fields
use the simple class name. The 748 suite/method identities were distinct.

The generated field report exactly matches the 17 baseline keys. Lifecycle
accounting remains 513 callers with 582 destruction calls and ten dynamic-add
calls; this loop introduced no lifecycle allowance. All 180 consumed guard
XML/text reports and both generated inventories were inspected and removed.
No test request remains active. These per-command waiting/execution durations
are not summed as elapsed delivery time; worker intervals overlap other work.

## Reproducing the validation

All commands ran from their owning worktrees with the current main-checkout
queue wrapper by absolute path. The variables below replace machine-local
paths; the existing ROM files were checked against AGENTS.md's SHA-1/CRC32
identities. No ROM links were created for validation.

```bash
export MAIN_CHECKOUT=/absolute/path/to/existing/OpenGGF-checkout
export ROM_ROOT="$MAIN_CHECKOUT"
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk
export LUA_BIN=/usr/bin/lua5.4
unset DISPLAY WAYLAND_DISPLAY JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS
rom_args=(
  "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen"
  "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen"
  "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen"
)

# Audio baseline and migrated behavior, in the audio worktree.
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off \
  '-Dtest=TestSoundTestApp,TestSoundTestPresentationHost,TestSoundTestWavExport,TestHubMusicController,TestHubMusicRomThemes,TestHubMusicPresentationSink' \
  "${rom_args[@]}" -B test

# Relocated CLI help, in the audio worktree.
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off \
  -DskipTests compile exec:java \
  '-Dexec.mainClass=com.openggf.tools.audio.SoundTestApp' '-Dexec.args=--help' -B

# Composed API and required S3K stability, in the coordinator worktree.
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestModApiSignatureSurface,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
  "${rom_args[@]}" test -B

# Separate fresh JVM, complete structural profile.
python3 "$MAIN_CHECKOUT/tools/testing/maven_queue.py" -Dmse=off -Pguards \
  -Dopenggf.headless.gl=surfaceless-egl "${rom_args[@]}" test -B
```

The false-floor baseline and final eight-class command are preserved in the
linked field audit. The audio test package changes preserve the same baseline
cases rather than identical old fully qualified test-class names. Shell syntax
and active-reference checks accompanied the actual CLI invocation. API pins are
unchanged. No speaker-device, interactive Swing, whole-SBZ2-route or complete
native-image validation is claimed.

Laziness Protocol kept the reusable host and its lifetime code intact. Migrate
Callers Then Delete Legacy APIs removed both old overloads with their callers.
Prove It Works required an observed false-floor failure and actual before/after
audio execution before pruning their exact allowances.
