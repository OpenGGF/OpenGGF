# Mod Support Phase 1 (Loader + Music Packs) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:subagent-driven-development` (recommended) or
> `superpowers:executing-plans`. Track every checkbox.

**Goal:** Ship a bounded data-only mod catalog, pending-state manager, namespaced track
registry, and music-pack playback through the real presentation backend.

**Architecture:** Startup-known deterministic processes receive no catalog and never
touch the mod root. Normal boot creates one immutable `EffectiveModCatalog`; the UI
edits a separate `PendingModState`. Audio manifests are validated and tracks prepared
after the presentation backend establishes its output rate but before gameplay opens.
`AbstractSmpsAudioBackend` owns a SMPS-or-streamed foreground source and LWJGL pumps
either. Logical streamed state joins rewind keyframes; PCM remains presentation-only.

**Specs:** `docs/superpowers/specs/2026-07-09-mod-support-phase1-design.md`, parent
design, and `docs/superpowers/specs/2026-07-10-mod-support-format-security-contracts.md`.

**Prerequisite:** Phase 0 Task B1's shared `com.openggf.io.ModAssetRoot` and
`ModInputLimits` commit. The rest of Phase 0 is not required.

**Tech stack:** Java 21, existing Jackson YAML and LWJGL stb_vorbis, JUnit 5. No new
dependency.

## Global constraints and gates

- Implement and commit directly on the existing `next` worktree (user directive
  2026-07-10); do not create a phase branch. Never `git add -A`.
- New mods default disabled. State changes require process restart and never mutate
  the effective snapshot.
- TDD order is mandatory: failing focused test, implementation, focused pass, then
  the named regression gate.
- Intermediate engine commits use the required seven trailers and justified
  `Changelog: n/a: covered by final phase-1 changelog entry in this branch`.
- Workstream gate:
  `mvn "-Ds3k.rom.path=s3k.gen" "-Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils" test`.
- Final gate: `mvn test`, plus manual speaker smoke for S1/S2/S3K. Update README and
  stage any `docs/TRACE_FRONTIER_LOG.md` change in the commit that caused it.

---

### Task 1: semantic versions + strict manifest model

**Files:** create `com.openggf.mods.SemanticVersion`, `VersionOperator`,
`VersionConstraint`, `VersionRange`, `ModApiVersion`, `ModManifest`, `ModDependency`,
`ModType`, `ModManifestParser`, `ModManifestException`; test
`TestSemanticVersionAndRange`, `TestModManifestParser`.

**Pinned API:**

```java
public record SemanticVersion(int major, int minor, int patch)
        implements Comparable<SemanticVersion> {
    public static SemanticVersion parse(String text);
}
public enum VersionOperator { LT, LTE, EQ, GTE, GT }
public record VersionConstraint(VersionOperator operator, SemanticVersion version) {}
public record VersionRange(List<VersionConstraint> constraints) {
    public static VersionRange parse(String text);
    public boolean contains(SemanticVersion version);
}
public final class ModApiVersion {
    public static final SemanticVersion CURRENT = new SemanticVersion(1, 0, 0);
    private ModApiVersion() {}
}
public record ModManifest(int formatVersion, String id, String name,
        SemanticVersion version, List<String> authors, String description,
        VersionRange engineApiRange, ModType type,
        String baseGame, String entrypoint, List<ModDependency> dependencies,
        Map<Integer,String> audioOverrides, Map<String,String> artOverrides,
        String insertAfter, OptionalInt patternWindows) {}
public record ModDependency(String id, VersionRange versionRange) {}
```

- [ ] Pin parsing to the canonical manifest-v1 YAML in the shared contract: singular
  conditional `baseGame`, object-list dependencies only, explicit audio/art maps,
  absent-or-class-name `entrypoint`, optional stock-key `insertAfter`, and optional
  bounded `patternWindows`. Phase 1 eligibility refuses any explicitly declared
  Phase-2 field. Write the golden range tests from shared
  spec §8 plus authors/description
  retention and bounds, invalid leading zero,
  prerelease/build, overflow, contradictory range, unknown fields, duplicate YAML key,
  invalid id/base/type, and `formatVersion != 1` tests.
- [ ] Run focused tests; expect compile failure.
- [ ] Implement strict DTO-to-domain parsing. Configure duplicate detection, nesting,
  collection, alias, code-point, string, and numeric limits before parsing. No plain
  unconstrained `new YAMLMapper()` call is permitted.
- [ ] Run focused tests; PASS. Commit exact files.

---

### Task 2: bounded repository scan + structured findings

**Files:** create `ModFinding`, `ModFindingSeverity`, `ModDescriptor`,
`ModRepositoryScanner`; extend shared `ModAssetRoot` only if the exact central-directory
validation seam is not already public; test `TestModRepositoryScanner`.

```java
public record ModFinding(ModFindingSeverity severity, String code,
        String message, String assetPath) {}
public sealed interface ModCatalogEntry permits ModDescriptor, InvalidModEntry {
    Path jarPath();
    List<ModFinding> findings();
}
public record ModDescriptor(Path jarPath, ModManifest manifest, String sha256,
        boolean containsCode, List<ModFinding> findings) implements ModCatalogEntry {
    public boolean hasErrors();
}
public record InvalidModEntry(Path jarPath, List<ModFinding> findings)
        implements ModCatalogEntry {}
public interface ModRepositoryScanner {
    List<ModCatalogEntry> scan(Path normalizedModRoot);
}
```

- [ ] Write failing tests for missing root, stable filename order, malformed jar,
  symlinked jar escape, central-directory entry/name/size/case collisions, dishonest
  inflation, manifest limits, code detection, scanner continuation, >1,024 jars, and
  repository aggregate-read overflow before hashing. Use iterative graph traversal (or
  keep graph size below the pinned jar cap) to avoid recursion exhaustion.
- [ ] A missing/unparseable manifest produces `InvalidModEntry`, never a nullable or
  fabricated `ModManifest`; retain it in filename order. The manager lists it by jar
  filename with an error badge/detail, but eligibility excludes it. Drive this through
  scanner → catalog → real manager-screen acceptance.
- [ ] Add a duplicate-id test asserting **all** colliding descriptors receive
  `DUPLICATE_MOD_ID`; no first-wins behavior.
- [ ] Implement scan through `ModAssetRoot.jar(modRoot, jar, limits)` using the
  scanner's injected immutable `ModInputLimits`; production composition passes
  `production()`. Validate the entire
  directory before reading the manifest. Catch expected per-jar I/O/parser/security
  failures and convert them to findings; do not claim VM-error recovery.
- [ ] Assert lowering entry-count, jar-size, entry-name, inflation, and aggregate caps
  changes factory/scanner outcomes without large fixtures; upward overrides fail.
- [ ] Run focused tests; PASS. Commit.

---

### Task 3: versioned pending state and immutable effective snapshot

**Files:** create `ModState`, `ModStateStore`, `ModStateSaveResult`,
`PendingModStateEditor`, `EffectiveModCatalog`; tests `TestModStateStore`,
`TestPendingModStateEditor`.

```java
public record ModState(int formatVersion, List<Entry> entries) {
    public record Entry(String id, boolean enabled, int order) {}
}
public sealed interface ModStateSaveResult {
    record Saved() implements ModStateSaveResult {}
    record Failed(String message) implements ModStateSaveResult {}
}
public final class EffectiveModCatalog {
    public static final EffectiveModCatalog EMPTY = ...;
    public List<ModDescriptor> orderedEnabled();
}
public record ModCatalog(List<ModCatalogEntry> scanned,
        EffectiveModCatalog effective) {}
```

- [ ] Pin every production/test/dev path to
  `<injected-normalized-mod-root>/modstate.json` (normally `mods/modstate.json`), never
  beside the root and never `config.yaml`. Test missing state, schema mismatch/quarantine, duplicate/null entries, defensive
  copies, atomic replace, injected write failure, and deterministic normalization.
- [ ] Test that pending enable/reorder changes and cascades do not change an existing
  effective snapshot; save returns `Saved`/`Failed`; `restartRequired()` flips only
  when pending differs from startup state.
- [ ] Implement and run focused tests; PASS. Commit.

---

### Task 4: audio manifest + namespaced track registry

**Files:** create `ModAudioManifest`, `ModAudioTrack`, `ModAudioSfx`,
`ModAudioManifestParser`, `TrackKey`, `SfxKey`, `ModTrackRegistry`,
`ModCatalogValidator`; tests
`TestModAudioManifestParser`, `TestModTrackRegistry`, `TestModCatalogValidator`.

```java
public record TrackKey(String modId, String localName) {
    public TrackKey { ModKeySyntax.requireOwnedKey(modId, localName); }
}
public record ModAudioTrack(TrackKey key, String assetPath, boolean loop,
        long loopStartFrame, OptionalLong loopEndFrame, float gain,
        boolean tempoEffects) {}
public record SfxKey(String modId, String localName) {
    public SfxKey { ModKeySyntax.requireOwnedKey(modId, localName); }
}
public record ModAudioSfx(SfxKey key, String assetPath, float gain) {}
public record ModAudioManifest(int formatVersion, List<ModAudioTrack> tracks,
        List<ModAudioSfx> sfx) {}
public final class ModTrackRegistry {
    public Optional<ModAudioTrack> find(TrackKey key);
}
```

- [ ] Implement `audio/audio-manifest.yaml` v1 exactly as pinned in the shared
  contract, including canonical golden bytes, track collection shape, loop omission
  semantics, and the parsed-but-ineligible Phase 1 `sfx` collection. Reject missing,
  noninteger, unsupported, and duplicate fields. Test strict normalized `audio/...`
  paths, bools, finite bounded gain, integer
  loop frames/order, duplicate keys, two mods with the same local name, missing assets,
  key grammar/owner mismatch, and override conflicts naming both owners. Validate each
  numeric override against the declared base game's music-id domain (never SFX or
  another game's id); scope conflicts by base game.
- [ ] Run static validation before eligibility freezes. Invalid manifest shape,
  missing/unsupported asset references, and metadata bounds are descriptor errors;
  override collisions are structured warnings and later effective order wins.
  Corrupt codec payloads discovered only during Task 6 preparation are runtime
  findings and pending-disable outcomes, not retroactive descriptor mutations.
- [ ] Implement, run focused tests; PASS. Commit.

---

### Task 5: eligibility graph and stable catalog freeze

**Files:** create `ModEligibility`, `ModDependencyGraph`, `EffectiveCatalogBuilder`;
test `TestEffectiveCatalogBuilder`.

- [ ] Write failing tests for API/dependency ranges, wrong base game, Phase-1 code and
  standalone refusal, disabled dependency, transitive blocked reason, self-cycle,
  multi-node SCC, independent stable order, and conflict findings from Task 4.
- [ ] Assert `engineApiRange.contains(ModApiVersion.CURRENT)` for lower, exact, upper,
  and incompatible ranges.
- [ ] Implement Tarjan SCC detection, mark every cycle member blocked, then stable Kahn
  topological ordering over the DAG with pending order as the ready-queue tie-breaker.
  Return `ModCatalog(scanned, effective)`: invalid scan entries remain in `scanned`
  for manager display and never enter the graph/effective snapshot.
  No move-until-stable loop.
- [ ] Use only validated descriptors + startup `ModState` to build immutable
  `EffectiveModCatalog`; newly discovered ids remain disabled.
- [ ] Add a timeout-backed cycle test; run focused tests; PASS. Commit.

---

### Task 6: bounded WAV/OGG decode, resample, and prepared tracks

**Files:** create `PcmData`, `PcmDecoder`, `OggPcmDecoder`, `PcmResampler`,
`ModRuntimeFindingStore`,
`PreparedTrack`, `PreparedAudioSession`, `ModAudioPreparer`; evolve existing `WavDecoder`; tests
`TestBoundedAudioDecode`, `TestPcmResampler`, `TestModAudioPreparer`; add a tiny
licensable/generated OGG fixture.

```java
public final class PcmData {
    private final int sampleRate;
    private final int channels;
    private final short[] ownedSamples;
    public static PcmData takeOwnership(int rate, int channels, short[] samples);
    public int sampleRate();
    public int channels();
    public int sampleCount();
    public short[] copySamples();
    short sampleAt(int index); // package-private, allocation-free playback seam
}
public record PreparedTrack(TrackKey key, PcmData pcm,
        long loopStartFrame, long loopEndFrame, float gain,
        boolean tempoEffects, String sourceSha256) {}
```

- [ ] Test WAV/OGG happy paths, corrupt data, unsupported rate/channels, duration,
  inflated/decoded/cache budgets, rate conversion, loop conversion, and deterministic
  cache eviction.
- [ ] Use stb handle/open-info APIs to learn channels/rate/frame count and reserve the
  decoded budget **before** native PCM allocation; do not use one-shot
  `stb_vorbis_decode_memory`.
- [ ] Budget peak simultaneous native decode + Java transfer + resample output before
  allocation; transfer ownership without a second retained-array clone.
- [ ] `ModAudioPreparer.prepare(effective, registry, outputRate)` performs all jar I/O,
  decode, and resample on the launch path. It returns immutable
  `PreparedAudioSession(tracks, findings, failedOwners)`; failed owners/dependents are
  excluded from the session view and atomically disabled/persisted in pending state for
  next restart (save failure adds a visible finding), while the process effective
  snapshot remains unchanged. Publish findings to `ModRuntimeFindingStore` for the
  manager detail view. No parser/I/O reaches callbacks.
- [ ] Run focused tests; PASS. Commit.

---

### Task 7: loop/rate-aware streamed player

**Files:** create `StreamedTrackData`, `StreamedTrack`, `StreamedMusicPlayer`;
test `TestStreamedTrack`, `TestStreamedMusicPlayer`.

- [ ] Test intro/loop-end wrap, stereo mixing/saturation, gain, pause reasons, app
  pause, fade, `multiplier > 1` speed behavior (1.25 approximation), stop/reset, and
  snapshot position restore. Arrays have exclusive ownership.
- [ ] Implement `mixInto(short[] output, int frames)` without I/O/allocation. Player
  state records logical id and `TrackKey`; idempotence requires both.
- [ ] Run focused tests; PASS. Commit.

---

### Task 8: prepared override resolver

**Files:** create `PreparedModMusic`, `ResolvedMusic`, `ModMusicResolver`; test
`TestModMusicResolver`.

```java
public record ResolvedMusic(int logicalMusicId, PreparedTrack track) {}
public final class ModMusicResolver {
    public static final ModMusicResolver EMPTY = ...;
    public Optional<ResolvedMusic> resolveStockOverride(String gameCode, int musicId);
    public Optional<PreparedTrack> resolve(TrackKey key);
}
```

- [ ] Test S1/S2/S3K numeric-domain isolation, later-wins, two keys sharing a local
  name, same file/two ids, empty parity, and fixed output-rate ownership.
- [ ] Resolver consumes only Task 6 prepared values. Implement; focused PASS. Commit.

---

### Task 9: real presentation pump + rewind snapshot integration

**Files:** modify `AudioBackend`, `AbstractSmpsAudioBackend`, `LWJGLAudioBackend`,
`AudioManager`, `audio/rewind/AudioBackendLogicalSnapshot`; create
`StreamedPlaybackSnapshot`; test `TestStreamedBackendIntegration` and extend audio
rewind tests.

```java
public record StreamedFadeSnapshot(float gain, int remainingSteps,
        int stepDelay, int delayCounter, float stepAmount) {}
public record StreamedPlaybackSnapshot(TrackKey key, int logicalMusicId,
        double sourceFramePosition, int pauseMask, StreamedFadeSnapshot fade,
        double rate) {}
```

- [ ] First write an instrumented subclass test that invokes real `playSmps`, drives
  the update/pump boundary, and records uploaded buffers. Prove non-zero streamed-only
  PCM at device and internal rates, base fallback, and empty-resolver parity.
- [ ] Add protected backend seams `hasPresentationWork()`,
  `presentationOutputRate()`, and pending foreground transitions.
  `LWJGLAudioBackend.hookUpdateStream()` consumes `hasPresentationWork()` even when
  `currentStream == null`; selecting streamed starts/queues the device source.
- [ ] Jingle pause/restore is queued and consumed at the existing safe update boundary.
  Fade targets foreground; streamed PCM mixes only into final presentation upload,
  never deterministic `drainPcm`.
- [ ] Capture/restore the new snapshot. Restore clears queued buffers first, then
  track/id/position/pause/fade/rate, and restarts the pump. Test stacked jingles,
  same-id idempotence, two-id restart, reverse begin/end, backend replacement, reset,
  and shutdown.
- [ ] During `AudioKeyframeStore.replayTo`, SMPS commands replay normally but
  streamed-override resolution is bypassed; streamed state comes only from the restored
  keyframe. Test two overridden music commands around a keyframe and mid-fade restore
  at rate 1.25 with identical subsequent state/PCM.
- [ ] Keep test access package-private/protected; no public test-only routing helpers.
- [ ] Map and test the exact live `GameAudioProfile` categories: invincibility and
  Super are non-SFX-blocking overrides that stack/resume; 1-up stacks, stops/blocks
  SFX, and fades the saved source back (S1/S2 remain blocked through fade; S3K releases
  at fade start); drowning is replacement music that stops the source, clears the
  stack, and does not resume it. Test cross-category nesting and saved streamed
  frame/rate/fade state only where the live category restores.
- [ ] Run focused and all audio/rewind tests; PASS. Commit.

---

### Task 10: pending-state mod manager + master-title action

**Files:** create `mods.ui.ModManagerScreen`, `game.MasterTitleSecondaryActions`;
modify `MasterTitleScreen`; test `TestModManagerScreen`,
`TestMasterTitleSecondaryActions`.

- [ ] Define an explicit secondary-action focus row entered with logical `menuDown`
  from ACTIVE game selection and returned with `menuUp`; it contains `MODS`. Consume
  the transition before normal selection handling. Optional raw `M` supplements it.
  Do not intercept `menuRight`, which remains stock game navigation.
- [ ] Manager takes `ModCatalog` (all scanned entries + effective snapshot) +
  `PendingModStateEditor` plus the
  engine-owned boot-lifetime `ModRuntimeFindingStore`, never mutates the effective
  snapshot. Audio preparation and later code-mod fault boundaries publish structured
  owner findings there; a successful later preparation may clear that owner's stale
  finding. Test >18-row scrolling, keyboard/gamepad action parity,
  edge-trigger/double-trigger prevention, cascade arm/cancel/commit, error/warning
  detail text, restart-required banner, and save-failure banner. Screen-level tests
  assert id/name/version/authors/description/dependencies, invalid-jar filename,
  dependency-constrained reorder refusal, valid within-constraint reorder, and
  runtime/decode failure badges.
- [ ] Render through real `PixelFont`; keep logic in testable methods. Back persists
  pending state and remains open/shows error on `Failed`.
- [ ] Run focused tests + `mvn package`; PASS. Commit.

---

### Task 11: external-content policy and launch ordering

**Files:** create `ExternalContentPolicy`, `SessionExternalContentView`, `ModSubsystem`; modify `Engine`,
`AudioManager`, `AttemptReplayHarness`, `UserRecordingSessionLauncher`,
`TraceCaptureTool`, `TraceCaptureSession`, `HeadlessGameBoot`, and reset/return-title;
test `TestModEngineWiringSeams`, `TestExternalContentPolicy`.

```java
public enum ExternalContentMode { NORMAL, STARTUP_DETERMINISTIC, SESSION_DETERMINISTIC }
public record ExternalContentPolicy(ExternalContentMode mode) {
    public boolean mayScanAtBoot();
    public boolean mayUseInSession();
}
```

- [ ] Startup test/headless/trace policy is chosen before resolving/listing the mod
  root. Test with a malformed enabled jar and a spy scanner asserting zero calls.
- [ ] `ModSubsystem` is the sole owner that atomically switches the installed
  `SessionExternalContentView`. Later attempt replay, recording replay, and capture
  switch to session-disabled:
  install `SessionExternalContentView.EMPTY`/resolver EMPTY while retaining the
  process effective catalog for manager visibility; release prepared PCM, rebuild the
  session where required, invoke no mod callback, and perform no further scan. Add one
  assertion at each distinct entry seam—`AttemptReplayHarness`,
  `UserRecordingSessionLauncher`, both `TraceCaptureTool` paths,
  `TraceCaptureSession`, `HeadlessGameBoot`, and direct `AudioManager` startup—that
  the installed view/resolver are EMPTY and prepared PCM has been released before
  deterministic stepping begins.
- [ ] Ending a later deterministic mode returns to title with the process catalog still
  visible. Mod content is not hot-restored into the old session; the next normal launch
  rebuilds `PreparedAudioSession` from the immutable effective snapshot and negotiated
  rate.
- [ ] Reorder normal gameplay launch: detect/select module → prepare presentation
  backend and query negotiated rate → validate/decode/resample effective tracks →
  install resolver → open gameplay session. Ordinary per-owner decode findings continue
  with prepared survivors/base fallback. Only fatal backend initialization or atomic
  resolver-installation failure returns to title with no partial session.
- [ ] Reset, backend replacement, and return-title clear resolver/player/cache. Run
  focused tests, workstream gate, and `mvn package`; PASS. Commit.

---

### Task 12: documentation and final integration

**Files:** create `docs/modding/music-packs.md`; update `CHANGELOG.md`, `README.md`,
`KNOWN_DISCREPANCIES.md`, configuration docs only if new user-facing configuration is
introduced.

- [ ] Document exact manifest/audio schemas, semver grammar, limits, stock override
  ids versus `TrackKey`, loop region, restart-required manager flow, tempo approximation,
  rewind keyframes, and deterministic-session behavior.
- [ ] Add/retain one checked-in music-pack sample source for Phase 4 gallery CI.
- [ ] Run all focused mod/audio tests, workstream gate, then `mvn test`. Record exact
  commands/results. Perform speaker smoke for one override in S1/S2/S3K.
- [ ] Review `git diff --check`, policy trailers, README release note, and trace-log
  staging. Commit final docs. Stop for human review; do not merge automatically.

## Execution order

Tasks 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11 → 12. Task 10 consumes Task 6's
`ModRuntimeFindingStore`; the order is load-bearing.
