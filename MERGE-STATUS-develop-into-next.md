# In-progress merge: origin/develop -> next

Merge is **live and resumable** (`MERGE_HEAD` present) in `/home/farrell/code/projects/OpenGGF-next`.
Do not `git merge --abort` unless you intend to discard the resolutions below.

- Merge base: `6a4c3948`
- Ours: `next` @ `126e78ca8` — Mod API 0.7 candidate, multiplayer time attack, v2/v3 editor, FBZ
- Theirs: `origin/develop` @ `ab71e5569` — 543 commits
- 146 conflicts total; **132 resolved and staged** (all of `src/main`), 14 unmerged (all `src/test`).
- `mvn compile` currently reports ~80 errors: the post-merge fix-up loop, not conflicts.

## Resolution policy applied

`next` is `develop` plus additive next-only features. So:

- **Shared runtime / physics / trace code → take `develop`.** It is the accuracy line and
  carries newer trace-verified fixes (spring foot probes, ICZ clamp asymmetry, CNZ magnet
  drop motion, LBZ tube elevator ordering, ring phase continuation, solid-contact body,
  trace metadata, replay bootstrap).
- **Next-only features → preserved.** Mod API / `game.patch` / `ModAssetRoot`,
  `ObjectCallbackRouter` fault boundary, multiplayer `net.*`, time attack, editor v2/v3,
  FBZ objects and events, `KosinskiModuleQueue`, `@ModApi` annotations.
- **Both sides additive → union**, with combined record components and compatibility
  constructors (`PlcProgressSnapshot`, `LevelSnapshot`, `SessionManager` overloads,
  `HeadlessGameBoot.boot` overloads).
- **Generated artifacts → took `develop`'s newer output**, must be regenerated after the
  build compiles: `docs/status/rewind-round-trip-gaps.md`,
  `src/test/resources/rewind/round-trip-tail-inventory.txt`.
- **Docs:** `CLAUDE.md` / `AGENTS.md` rebuilt on develop's concise rewrite with a new
  "`next`-line subsystems" section for Mod API + multiplayer, plus two hard rules (Mod API
  descriptor, mod boundaries). `docs/architecture/engine-map.md` editor and
  `WorldSession` sections updated for the next-only editor and `GameDataSource`.
  `CHANGELOG.md` union-merged.
- **Trace fixtures:** `s3k/fbz_completerun/*` took develop's re-capture (schema 7,
  2026-07-27) over next's schema-5 copy.

## Audio: re-implemented (decision taken — re-implement, not revert)

`develop` `bad6ac420` removed the split presentation audio runtime and cut 943 lines from
`AbstractSmpsAudioBackend`; device writing now belongs to `OpenAlPcmSink` and final PCM,
history, reverse and capture belong to `AudioPresentationProducer`. Rather than port next's
old backend PCM mixing forward, mod music is now a **native music route** in develop's
command pipeline. The entire `StreamedMusicPort` / `mods/Streamed*` layer merged cleanly and
is unchanged; only the integration seams were rewritten.

Investigation that fixed the design: prepared mod tracks are **fully buffered PCM** with an
allocation-free `mixInto` (`PreparedTrack`, `StreamedTrackData`, `StreamedMusicPlayer`) —
"streamed" means streamed *presentation*, not incremental decode. So a track maps directly
onto a presentation voice.

New/changed:
- `presentation/StreamedMusicVoice` — hosts a `StreamedMusicPort` as a `PresentationVoice`.
  It adapts rather than reimplements: loop points, fade ramps, pause mask and speed-shoes
  tempo stay owned by the port, which a plain Q32.32 cursor (`SampleBackedVoice`) would drop.
- `PresentationVoiceSnapshot.Streamed` (sealed permits extended) carrying
  `StreamedMusicPort.State` verbatim, plus `AudioPresentationCommand.StreamedVoiceDescriptor`.
- `AudioSourceDescriptor.Route.STREAMED_TRACK` / `STREAMED_SFX` + factories.
- `AudioPresentationSourceFactory`: holds the launch-scoped port (session still owns
  teardown), `streamedStockOverride`, `streamedTrack`, `recreateStreamed`.
- `AudioPresentationCommandResolver`: `AudioCommand.PlayNamespacedMusic` → `ReplaceMusic`.
- `AudioPresentationDependencyResolver.recreateStreamed` — default **rejects** a restore
  without a live port instead of silently resuming silence.
- `WavDecoder`: develop's stricter RIFF parser **plus** next's bounded reader, so creator
  audio is still size-capped (`decode(in, maxBytes)`, `decodePcm(id, in, maxBytes)`).
- `LWJGLAudioBackend`: develop's empty hooks kept — the sink owns the device.

**Payoff:** mod music now inherits presentation, rewind snapshot/restore, history, reverse
and capture for free. That removes the old limitation recorded in
`docs/status/known-discrepancies.md` ("streamed PCM is excluded from deterministic audio
capture and command replay") — **that entry must be updated or deleted before commit.**

## Conflicts: all 146 resolved

Nothing is unmerged. `mvn test-compile` is clean for both main and test sources.

## SFX pool: re-implemented (done)

Creator one-shots are now ordinary sample voices instead of a private backend pool:
- `StreamedMusicPort.sfxPcm(SfxRef)` + `SfxPcm` expose prepared PCM (additive on the
  unpublished 0.7 candidate). `ModStreamedMusicPort` implements it from `PreparedSfx`.
- `AudioPresentationSourceFactory.streamedSfx(...)` registers that PCM in
  `DecodedPcmCache` under `mod-sfx:<owner>:<name>` and builds `SampleBackedVoice.oneShot`.
- New `AudioCommand.PlayNamespacedSfx` + resolver case, so creator SFX now goes
  **through the deterministic timeline** (it previously bypassed it — the old
  `playNamespacedSfx` javadoc said so explicitly).
- `AbstractSmpsAudioBackend.tryPlayStreamedSfx` is now preflight-only; the private
  one-shot deque, `MAX_STREAMED_ONE_SHOTS`, `mixStreamedOneShots`,
  `clearStreamedOneShots` and the `PlayNamespacedSfx` transition are gone.
- `DecodedPcmCache.register(DecodedPcm)` added, rejecting id reuse with different PCM.

## Post-merge integration fixes applied

`TraceMetadata` lost `@JsonIgnoreProperties(ignoreUnknown = true)` in my resolution
(restored — it was causing every `capture_mode`/`gameplay_segment` fixture to fail);
`ObjectExecutionController` now supplies a participant-scoped `resolvePlayer` resolver so
next's multi-sidekick `resolveSolidNowOnly` path works against develop's extracted executor
(new `ObjectManager.processParticipantSolidCheckpoint`); next's `ObjectCallbackRouter` fault
boundary was **restored** in both the batched solid path and the collision-response
eligibility check (original follow-up #1 — done); FBZ visual asset paths in tests updated
for develop's docs reorg; `LevelFrameResult` annotated `@ModApi` (develop's `EngineStepper`
returns it and that interface is `@ModApi`).

## Direction (settled)

develop's changes win; next's code, tooling and tests adapt. The two questions I raised
earlier were not real choices and are closed.

## Mod API boundary: done

`ModApiSignatureSurface` gained a curated **engine-internal terminal set** (39 entries,
pinned by `mods/mod-api-engine-internal-types.txt`, with a review test). develop-owned
runtime types — the hardware-timing service and its ROM work ledger, the
initial-ProcessSprites assembly, solid-contact internals, the audio presentation
producer/sink, trace playback profiles, the hardware-timing trace port — appear in engine
signatures without joining the creator contract. Creators may still receive such a
reference; nothing about its shape is promised. This mirrors the existing
`ALLOWED_PLATFORM_TYPES` mechanism, so every entry is explicit and reviewable.

Also: 19 SMPS/synth snapshot types that develop's refactor stopped exposing lost their
`@ModApi` (candidate-surface reduction, allowed with no published baselines); Jackson
binding annotations were added to the platform allowlist for `TraceMetadata`;
`processRuntimeArtQueue` moved off the `@ModApi` `ObjectArtProvider` to the existing
engine-only `RuntimeObjectArtQueue` bridge; `AudioBackendLogicalSnapshot`,
`DeterministicAudioRuntime` and `FrameAudioMode` were finally deleted (develop's own guard
asserts those names are gone). `mod-api-signatures-0.7.txt` regenerates cleanly and all
Mod API gates pass.

## Suite status

The baseline matters more than the raw count. A red is only **merge-introduced** if the same
test is green on *both* parent branches. Measured, not inferred: `origin/develop`'s own tip
has **77 red methods** (its test tree does not even compile — see below), so classifying
against `next` alone badly overstated the damage.

| | tests | failures | errors | reds |
|---|---:|---:|---:|---:|
| pre-merge `next` (`126e78ca8`) | 15,174 | 54 | 16 | 58 |
| `origin/develop` tip | 13,386 | 62 | 15 | 77 |
| merged, first run | 16,015 | 128 | 53 | 181 |
| merged, after triage | 16,062 | 56 | 10 | 54 |

Against the **union** of both parents, merge-introduced reds are **18**, not the 38 an earlier
draft of this doc claimed. (A run scored 19; `TestModAssetRoot`'s concurrent-read-budget test
passes 3/3 in isolation and is a load-dependent flake, not a regression.) Whole clusters previously listed as merge damage — the AIZ
fire-curtain tests, the MHZ cluster — fail identically on `origin/develop` and are recorded as
its defects instead.

### `origin/develop`'s test tree does not compile

`src/test/java/com/openggf/game/sonic3k/objects/TestS3kSignpostInstance.java` calls three
methods that do not exist on `S3kSignpostInstance` (`resultsChildTimingAdjustment`,
`romVelocityAfterGravity`, `romBumpCheckAvailableAfterCooldownEntry`) — 24 compile errors.
`develop`'s **main** sources compile cleanly. The develop-side numbers above were obtained
with that one file set aside; nothing else was modified.

## AWT removed

AWT must not be used so native images stay buildable.

- `WindowIconLoader`'s macOS Dock path (`java.awt.Taskbar` + `ImageIO`) was deleted rather
  than baselined. The app bundle still supplies that icon; a plain JAR loses it.
- The mod SDK's `BufferedImage`/`ImageIO` PNG path was replaced by
  `com.openggf.io.PixelImage` + `PngCodec` — **pure Java**, on `Inflater`/`Deflater`.
  STB was tried first and rejected: the SDK is a CLI creators run on a plain JDK, and the
  scaffolded project's `convert-sample-art` step runs it in a child JVM without the LWJGL
  natives on its classpath, so a native decoder just moves the problem onto creators.
  Decode covers non-interlaced 8-bit greyscale/greyscale+alpha/RGB/RGBA/palette with all
  five row filters; anything else is rejected by name. Geometry is still validated from the
  header before any pixel allocation, preserving the hostile-input defence.
- `TestProductionAwtBlacklistGuard`'s blanket `tools/modsdk` exemption is **removed**, so
  the guard now enforces zero AWT everywhere except `audio/debug/SoundTestApp`, which stays
  by decision.

## Main-code bugs the merge introduced, now fixed

- `ObjectManager.execOrder` sized by `dynamicSlotCount()` — next's fixed SST slots sit above
  the allocatable window, so execution indices overflowed. Restored `processSlotCount()`.
- Fixed SST slots stopped executing — `isManagedDynamicSlot` covered only the dynamic window.
  Fixing that by *widening* `isManagedDynamicSlot` was itself a bug: 19 call sites share it,
  including the `createDynamicObjectInSlot` allocation guard, so the widened form let objects
  be seated below `firstDynamicSlot`. Split into `isManagedDynamicSlot` (the true dynamic
  window, restored) and `isExecutableSlot` (the wider process-slot range, used only by the
  four `execOrder` population sites).
- **S3K dynamic slot count: 90, not 89.** An earlier pass mechanically took `develop`'s 89
  under develop-wins. That was wrong, and the disassembly is unambiguous:
  `Dynamic_object_RAM ds.b object_size*90` (sonic3k.constants.asm:307). With
  `firstDynamicSlot = 4` the window is absolute slots 4-93, so `lastDynamicSlotExclusive` is
  94 — exactly the range `Offset_ObjectsDuringTransition` scans (sonic3k.asm:104166-104180,
  90 `dbf` iterations from `Dynamic_object_RAM + object_size`). Restored to 90.
- **`S3kResultsScreenObjectInstance.traceDebugDetails` crashed** — the format string gained a
  `children=%d` specifier when `childrenRemaining` was introduced, but the argument list did
  not, so every argument after it shifted and `%d` was handed a boolean
  (`IllegalFormatConversionException`).
- Mod fault boundary lost in four paths (object `update`, batched solid, collision-response
  eligibility, rewind capture/restore). All restored.
- Participant-scoped solid checkpoints had no resolver after develop's executor extraction.
- `TraceMetadata` lost `@JsonIgnoreProperties`.
- **Creator SFX could never play** — `playNamespacedSfx` was gated on
  `sendLiveBackendCommands()`, which develop hardwires to `false`.
- `DelegatingGameModule` did not forward develop's `createInitialFixedSstDispatcher` or
  `getTracePlaybackProfile`, so a mod-patched module silently dropped both.
- `AutomaticTunnelObjectInstance` wrote playable positions directly instead of through
  `NativePositionOps`.

### Audio and mod-SDK adaptations (next re-engineered onto develop's architecture)

- **PCM assertions rewritten.** `develop` moved mixing out of the audio backend into the
  presentation layer, so `hookUploadStreamBuffer` and `hasPresentationWork` no longer exist and
  the sample-mod tests' `PcmPoolBackend` could never record anything (`uploaded` stayed null).
  A track is now driven as a `StreamedMusicVoice` through `AudioPresentationMixer` — the same
  path the running engine mixes it with — and a one-shot is checked at
  `StreamedMusicPort.sfxPcm`, the point the creator asset actually becomes samples. The dead
  `PcmPoolBackend` was deleted.
- **Namespaced-track spy moved.** The engine preflights a track with `hasStreamedMusic` and
  lets presentation play it from the recorded command, so `tryPlayStreamedMusic` is never
  called and the recording backends observed nothing. They now record at `hasStreamedMusic`.
- **`playNamespacedSfx` preflight.** Kept on `tryPlayStreamedSfx`: despite the name, the
  concrete backend's implementation queues nothing. Adding a parallel `hasStreamedSfx` was
  tried and reverted as pure duplication.
- **Sample PNG fixture regenerated.** The checked-in `sample.png` is a byte-exact record of
  scaffolder output; the pure-Java `PngCodec` encodes valid but different bytes than ImageIO
  did, so the fixture was regenerated from the new encoder rather than loosening the
  comparison.
- **Flappy pipe positions.** Pipes now take one `PIPE_SPEED` step (2px) in their spawn frame.
  This is correct: the controller spawns each into a free slot *above* its own, and ROM
  ExecuteObjects reaches a higher slot in the same pass — behaviour the `execOrder` fix above
  restored. The expected value was updated, not the engine.

### AIZ fire phases need the hardware timing service pumped

`develop` paces AIZ's fire phases on `HardwareTimingService`, so a bare `events.update(...)`
never advances art readiness. `TestAizFireCurtainRendererRom` now services
`VINT_SERVICE` / `PRE_MAIN_LOOP` / `POST_OBJECTS` per frame. That takes 1 of its 4 methods
green; the other 3 fail identically on `origin/develop` and are recorded as its defect.

## Behavioural conflicts resolved in develop's favour

- ICZ frost capture walks only the native Player_1/Player_2 pair, matching the ROM's two
  player slots; next's third-sidekick expectation was inverted into a positive assertion of
  the cap.
- S3K prior-list touch response reads frame-start x/y, with only the lost-ring projection
  reading live — the trace-verified ICZ fix that moved that frontier.

## Guard/tooling adaptations

Line budgets ratcheted with dated rationale; title-card oscillator suppression moved to
`OscillationManager`; raw-`getInstance` scanner given the four JDK crypto owners next's
multiplayer code uses; `TestModifierSupportDocumentation`'s read-site parser narrowed to the
enclosing call (a single `return a || b || c` was marking every binding chord-dead); audio
architecture guard scoped so next's unrelated FBZ `captureRuntime()` is not a false positive;
rewind annotation/AWT/tail-inventory baselines ratcheted; two temp-dir leaks routed through
`TestTempFiles`.

## Remaining merge-introduced reds

Of the 23 measured against the union baseline, these were fixed after that run:

- **Mod SDK / sample mods (4 classes).** `TestSamplePlatformerIntegration`,
  `TestPhase3StandaloneSampleIntegration`, `TestPhase2SampleModIntegration`,
  `TestSampleFlappyIntegration` — all green. Details in "Main-code bugs" below.
- **`TestS3kAizMutationPipeline`** — pinned `next`'s old log wording; `develop` deliberately
  reworded it (`"overlays (128x128/16x16/8x8)"` → `"palette, PLC, and transition floor"`).
- **`TestFbzRomWorldOffsetPolicy.s3kDynamicWindowRepresentsSlot93`** — see the slot-count fix
  below; this one was my regression, not a test-expectation drift.

Still open, with what is known about each:

- **`TestFbzActTransitionHeadless`** (2) — **traced; needs an FBZ design decision.** The
  engine is not at fault and neither is the allocator: the *test* seats
  `FbzOutdoorBgMotionObjectInstance` at absolute slot 3 itself, via
  `addDynamicObjectAtSlot(fixedSlot3, 3)` (line 200), as its "fixed slot below the dynamic
  window" boundary probe. That trips `develop`'s `InitialObjectDispatchController` invariant
  that a fresh initial Process_Sprites leaves slot 3 unowned.

  **`develop` is right.** Engine slot 3 sits immediately below the dynamic window, exactly
  where the ROM's `Reserved_object_3` sits below `Dynamic_object_RAM`
  (sonic3k.constants.asm:303-308), and `SpawnLevelMainSprites` writes
  `Obj_ResetCollisionResponseList` there at level load (sonic3k.asm:8112). Slot 3 is spoken
  for; an arbitrary FBZ object may not squat on it. The engine does not model
  `Obj_ResetCollisionResponseList` at all, which is why the slot looks free.

  **Not changed here.** The obvious move — drop the slot-3 probe — removes this test's only
  below-window fixed-slot coverage, and there is no other valid below-window slot (0/1/2 are
  Player_1, Player_2, and the ROM's own reserved entry). The real fix is probably to model
  `Obj_ResetCollisionResponseList` as the slot-3 occupant, which is a small FBZ/S3K design
  task rather than a merge resolution. Left red deliberately, with the cause pinned.
- **`TestFbzAct1RouteHeadless`** (3). One was a straight crash (format-args bug, fixed below);
  it now fails further along on a ring `placementId` off by one (229 vs 228).
- **CNZ / ICZ / LBZ / MGZ / HCZ headless routes** (7 single methods) and
  **`TestS3kCnzVisualCapture`** (2) — untriaged.
- **`TestPlaybackAdvanceOnlyInputBridge`**, **`TestUserRecordingDeterminismSmoke`**,
  **`TestSonic1GiantRingTimeAttackGate`**, **`TestS3kAizIntroEventsHeadless`** — untriaged.

## KosM queue ownership: `develop` holds the single-counter model, `next` has the stray one

An earlier draft of this doc had this backwards. `develop`'s
`S3kKosModuleQueue` **is** the ROM's `Kos_modules_left`: a single four-deep FIFO enforced as
`timing.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE) >= 4`, over one work kind, in one
service. Every S3K KosM consumer routes through it — title card, AIZ and HCZ events, plane
intro, end boss, large fan, water wall, and results. Results art sharing that counter with
everything else is exactly ROM behaviour, not a divergence from it.

The second queue is `next`'s own `com.openggf.level.resources.KosinskiModuleQueue`, and it
predates the merge. Only three call sites use it, all `next`-only: `Sonic3kPlcLoader`,
`Sonic3kFBZEvents`, and `Fbz2SubbossInstance`/`FbzEndBossInstance`. Their KosM loads
therefore do **not** decrement the same counter the rest of S3K waits on, so FBZ work can
run concurrently with a title-card or results load that the ROM would have serialised.

**Direction:** migrate those three onto `S3kKosModuleQueue` and retire the generic queue, or
back it by the timing service so both views share one counter. This is `next`-side cleanup;
no change to `develop` is called for. Not attempted in this merge — `KosinskiModuleQueue`
carries its own `DmaTarget` and rewind-journal contract that `S3kKosModuleQueue` has no
equivalent for, so it is a design task, not a rewire. An FBZ act-2 subboss trace is the
oracle for whether the current overlap is observable.

Two ordering bugs found while reconciling this and now fixed: `childrenRemaining` was never
initialised to 12 (so `Obj_LevelResultsWait`'s $30 gate passed immediately), and the create
dispatch published *and* consumed a wait frame in the same pass instead of returning.

## Backport candidates for `develop`

Fixes made here that are not `next`-specific are tracked in
[docs/architecture/audits/testing/develop-backport-candidates.md](docs/architecture/audits/testing/develop-backport-candidates.md),
with the defect stated as it exists on `develop` today: the
`AutomaticTunnelObjectInstance` direct playable-position writes, the AWT in
`WindowIconLoader`, the unidentifiable `RewindRegistry` capture failure, and the
`TestModifierSupportDocumentation` parser marking sibling bindings chord-dead. The file also
records `develop`'s own pre-existing failures (the fire-curtain cluster, the broken
`TestS3kSignpostInstance`), and what is deliberately *not* backportable so it is not
re-proposed later.

**Shipped:** the AWT removal is on `develop` as PR #175
(`bugfix/ai-awt-native-image-removal`). Worth backporting next, in rough order of value:

1. **S3K dynamic slot count 89 → 90.** `develop` has the ROM-wrong value; see the
   disassembly citation above. This shifts object slot identity, so it wants a trace sweep on
   `develop` rather than a blind cherry-pick.
2. The `TestAizFireCurtainRendererRom` hardware-timing pump (4 red → 3 on `develop`).
3. `AutomaticTunnelObjectInstance` → `NativePositionOps`.

## Verification not yet done

The S3K dynamic slot count 89 → 90 fix is the highest-risk change here: it moves object slot
identity, so it moves trace output. It is verified against the disassembly two independent
ways (the `ds.b` allocation and the `dbf` iteration count) but **has not been run through a
`*TraceReplay` sweep**. If `develop`'s 89 was tuned to make a specific trace pass, restoring
90 will surface that trace's real defect rather than cause one — either way the sweep is the
oracle and should run before this merge is relied on.
