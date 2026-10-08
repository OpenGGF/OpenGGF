# Three games, one controller: challenge and engine architecture blueprint

[Shared roadmap and reconciled decisions](2026-10-07-mod-challenges-blueprint.md).
Design/research at develop `09282b17305cb5794e43a26855cd2b9543b4ff5f`, 2026-10-07. This delivers a blueprint, not implementation. **Observed** means inspected current source or documented earlier evidence; behavioral/performance claims remain untested in this research. All new types, protocols and commands below are **proposals**. In source references, `M/` expands to `src/main/java/com/openggf/` and `T/` to `src/test/java/com/openggf/`.

## Intent and architectural decision

A player controls three real OpenGGF games side by side with one controller sequence. Each retains its own movement, enemies, timing, lives, progression and ROM assets. Success means every declared route finishes under that common sequence; three animations or separately steered route controllers do not qualify. The polished ambition is three complete campaigns. Opening-act success establishes only an opening-act challenge.

**Recommend an engine-owned process host for the prototype and MVP, retaining it as the reference backend.** One worker JVM owns one console/game; the application host owns input capture, scheduling, composition, device output and challenge records. Current global state makes this the credible first delivery. Three JVMs and framebuffer transport cost memory and latency, so performance is a gate, not an assumed advantage.

A later in-process backend should compose a proposed `GameInstance` from console-lifetime services, a session controller, existing durable `WorldSession`, and disposable gameplay/editor contexts. Do not replace these lifetimes with one undifferentiated Game Container or resurrect the historical `GameRuntime`. Admit that backend only after ownership migration and hostile independence tests pass. A process prototype proves product feasibility and process isolation; it proves nothing about concurrent in-process ownership. A polished product may retain processes if its measured budgets pass.

## Current source/test inventory and ownership matrix

| Surface and concrete evidence | Observed ownership; required boundary |
| --- | --- |
| `M/game/session/{SessionManager,WorldSession,GameplayModeContext,GameplaySessionFactory}.java`; `T/game/session/{TestSessionManager,TestGameDataSourceSessionOwnership}.java` | Durable level/module/source and disposable managers exist. `SessionManager.openGameplaySession` destroys the previous static mode. Factory construction is a useful seam, not a multiworld factory. A worker/session controller owns creation, switching and teardown. |
| `M/game/session/{EngineContext,EngineServices}.java`; `M/game/GameServices.java`; `M/GameLoop.java#refreshRuntimeBindings` | One current service root; loop construction configures it. Explicit mode references coexist with global fallback and graphics rebinding. `GameplayModeContext` binds/registers/unbinds the root V-int carrier. Console clocks and media services must belong to the game instance. |
| `M/level/LevelManager.java#initGameModule/#buildObjectServices`; `M/level/objects/DefaultObjectServices.java`; `M/sprites/managers/SpriteManager.java`; `M/physics/GroundSensor.java` | Object services capture real managers, but loading requires the globally active context; sprites/sensors still consult globals, and GroundSensor has a static override. Inject world collaborators through the entire reachable call graph, including recreation. |
| `M/game/OscillationManager.java#update`; `M/level/objects/AbstractObjectInstance.java`; `M/game/rewind/snapshot/OscillationStaticAdapter.java`; `M/game/sonic3k/objects/AizIntroArtLoader.java` | Mutable static oscillator arrays/`lastFrame`, camera bounds, art/renderers/active services remain. Same-frame oscillator updates can suppress a sibling. Static rewind adapters restore shared state; they do not isolate it. Console/world owners replace these stores. |
| `M/graphics/{GraphicsManager,PatternAtlas,RenderContext,PatternRenderCommand,GLCommand}.java`; `M/graphics/shaderlib/DisplayShaderPipeline.java`; `T/graphics/{TestPaletteUploadLatch,TestGraphicsManagerReinit,TestPatternAtlasDirtyUploads,TestRenderContext}.java` | Mutable atlas, palette publication, SAT, scroll, render queues, viewport and fade bindings; static GL caches/pools and donor palette registry. Postprocessing captures framebuffer 0. Each worker owns complete render state; in-process rendering needs explicit targets and context-aware caches. |
| `M/audio/{AudioManager,LWJGLAudioBackend}.java`; `M/audio/presentation/{AudioPresentationProducer,OuterFramePresentation}.java`; `M/audio/output/OpenAlPcmSink.java`; `T/audio/{TestAudioManagerRuntimeInstallation,TestUnifiedAudioPresentationIntegration}.java` | AudioManager holds synthesis, requests, overrides and rewind history. Producer has its own sample clock/owner thread; sink owns the native device. Separate per-game producers from one host sink. Current standalone presentation instances still have configuration/restore fallbacks. |
| `M/data/{Rom,RomByteReader,RomManager}.java`; `M/game/StockGameDataSources.java`; `M/game/sonic1/resources/Sonic1PlcService.java`, `M/game/sonic2/resources/Sonic2PlcService.java`; `T/game/rewind/TestS1S2PlcSessionOwnership.java` | Readers provide immutable copied/windowed bytes. ROM handles remain closable; pinned identity is not a lifetime lease. PLC/timing progress is already partly session-owned, but publication targets and convenience global access remain. Share frozen data, never live queues/handles. |
| `M/control/{InputHandler,PlayerInputState,LogicalInputSnapshot}.java`; `M/tools/RecordingFrameDriver.java#skipFrameFromRecording/#lastPolledBk2Input`; `T/tools/TestRecordingFrameDriverHardwareTiming.java` | Logical overrides exist, but refresh still polls devices and `update` consumes edges. Native lag and pause have different polling baselines. One sampler, separate per-game input adapters/poll histories. |
| `M/game/rewind/{RewindRegistry,LiveRewindManager}.java`; `T/game/rewind/{TestRewindRegistry,TestRewindDeathRespawnBoundary}.java`; `T/TestGameLoopSpecialStageRewindBoundary.java` | Restore reconstructs graphs, restores RNG after constructor effects and reconciles derived state. Fresh loads/mode changes can sever histories. No existing three-world rollback transaction. |
| `M/game/save/{SavePaths,SessionSaveRequests,SaveManager}.java`; `M/configuration/SonicConfigurationService.java`; `M/TraceSessionLauncher.java`; `M/Engine.java` | Current-session save routing, mutable config overrides, static active trace/Engine and process cleanup. Progression JSON is not a complete savestate. Editor swaps rebuild actors; tool endpoints need explicit member identity. |

The March plan, `docs/architecture/plans/2026-03-24-game-runtime-container.md`, its review notes and associated architecture design are **historical**: they retained graphics/audio/ROM globals and corrected construction cycles, chiefly for editor swapping. April's `docs/architecture/designs/2026-04-07-runtime-ownership-migration-design.md` explains the later world/mode split. Current source has neither GameRuntime nor RuntimeManager; `T/game/TestProductionSingletonClosureGuard.java#gameRuntimeReferencesDoNotReturnToProductionCode` actively guards against reintroducing the old abstraction. Some engine-map prose still describes older module-singleton ownership; current registry code and tests take precedence.

## Recommended host, resources and lifetimes

A proposed `ChallengeHost` supervises stable member handles `(runId, memberId, generation)`; `ProcessGameEndpoint` owns worker boot/step/checkpoint/media/close requests. Generation changes reject stale commands, checkpoints and GPU/PCM packets. Construction failures release every already-acquired lease before publishing a playable run. Never let a scene or creator callback start processes or swap EngineServices.

Within each endpoint, instantiate fresh modules and resolved provider graphs, pin the validated ROM source and immutable launch settings, then construct core managers, collision/level peers, registries and injected object services in existing factory order. Game modules themselves contain mutable event, title, special-stage and art providers; they are not shareable configuration objects. Load through the normal production pipeline, establish input baselines and render/audio owners, and join the start barrier only after the declared normal entry is ready. Record each neutral preparation recipe; never manufacture initial physics from trace rows.

The host owns window/controller lifecycle, user preferences, catalog discovery, UI and device sink. Each game owns its power-on V-int carrier, mode/poll state, RNG, timers, actors, level mutations, PLC/DMA journals, palettes, pattern versions, synthesis/override stacks, rewind and pending saves. Immutable ROM readers, identity-qualified pure decompression results and frozen definitions may eventually be shared; mutable levels/pattern objects, live ROM handles, modules and caches containing renderers may not. Cache keys include ROM digest, logical patch/content digest, decoder version and resource kind; eviction uses leases. For processes, accept duplicated caches initially rather than inventing a shared mutable cache.

Workers render complete native-size frames, transported with bounded sequence/epoch metadata to the compositor. Preserve full clears, water splits, palette latches and special-stage framebuffer behavior. In-process migration should first render each game into its own FBO, then compose; direct sub-viewports require auditing every clear/projection/fullscreen effect. Existing framebuffer-0 shader assumptions and static GL caches must be adapted first.

Each game produces deterministic final PCM using its production driver/presentation clock. A proposed PCM sink lease exports blocks without opening/closing the host device. Host focus/mix gains apply after synthesis: muted worlds keep progressing. Default audio is one focused game with all worlds' clocks running; optional normalized mixing has independent volume/mute and clipping tests. Device consumption measures audible presentation latency, never gameplay time. Host pause drains/freezes output coherently; restore discards old queued media under a new epoch. Close worker loops/producers/render leases before process/window cleanup; only the host closes its device/window.

Process isolation contains worker JVM/native crashes, not challenge-rule mistakes. Reuse owner-derived `ModContext` transactions and `ModFaultBoundary` for creator callbacks. Any worker fault or rule abort stops admission, marks the run incomplete, preserves survivor evidence and offers whole-run restart. Do not continue partially mutated worlds. VM-fatal failures cannot be reliably contained by an in-process callback boundary.

Rejected alternatives have concrete kill evidence: active-root swapping leaves shared oscillators, camera bounds, media and asynchronous cleanup outside the swap; three SessionManager opens destroy predecessors. Separate GL contexts/classloaders do not repair static/native caches and device-current-context ownership. A mod scene supports drawing, input and ROM-derived art (`M/mods/scene/SceneContext.java`, `host/ModSceneHost.java`), not three production game loops. Donation/SceneReplay can supply presentation, not independent campaigns. Keeping AudioManager global because the March plan called audio process-wide confuses synthesis ownership with device ownership.

## Common input and scheduler contract

Use one observed immutable **held-pad sample** per 60 Hz challenge tick: directions, distinct A/B/C and Start, with P2 neutral in the initial challenge. Fan out those identical levels to every member. Each game's production polling owner derives pressed edges against its own last actually polled levels. “Same input” means the same signal offered at the same challenge tick, not equal effective pressed masks, positions or executed frames. An ordinary lag tick may not poll; native Pause_Loop does. A universal host-generated pressed mask would violate that evidence. A proposed live native-poll adapter is required: today `setLogicalOverride` accepts already-derived snapshots. Bind edge derivation to production polling/admission, using the recording-driver tests as evidence, not its trace-row dispatcher as gameplay authority.

Start remains game input: titles, menus, native pause, death/continue and special stages interpret it through their own rules. Host Pause, Restart, Rewind and focus/layout controls are separate recorded commands, never secretly remapped Start. Disable raw worker hardware/debug/editor shortcuts, external playback/recording pumps and module speed pacing. `InputHandler.setLogicalOverride` alone is insufficient; `GameLoop.step` can also fast-forward, so add a production exclusive-step owner with exactly one classified iteration.

Tick order: sample once; validate ordinal/generation; offer to every member; step each once through native admission; collect acknowledgments/outcomes; publish the committed tuple. In-process reference order is stable member order; permutation tests must yield identical per-member state. Keep challenge tick, each console V-int, executed-frame count, level clock and audio sample count distinct. Use LIVE production readiness and the declared pinned load-time profile; never use trace rows to choose gameplay or create hardware jobs.

A genuine game lag/readiness iteration is a valid member result; siblings still execute their own tick. An OS/worker stall delays committing the whole next tuple: no sample dropping, selective catch-up or faster-world extra steps. Poll host UI while waiting, bound queues and expose slowed presentation. Repeated presentation may show the last complete tuple; missing/skipped rendering must not advance gameplay. Replay ignores wall-clock stalls and consumes the same ordered pad samples and host commands.

Death/checkpoint respawn remains local. Recommend strict group failure on any game-over, with a separately labeled practice variant. Route completion parks that member at its declared natural terminal boundary while siblings continue; group success requires all terminals. Linked pause freezes new admission and preserves native pause flags. Linked restart rebuilds all members from the manifest.

Linked rewind checkpoints include every member's owners, graph identities, poll baseline, clocks and the host cursor/rule state. The common rewind floor is the latest boundary invalidating any history; show it. Validate all checkpoints before restoring; publish no mixed tuple on failure. Two-phase coordination is not proof that RewindRegistry is transactional: a failed restore stops the run and requires reconstruction. Never transplant local object IDs or CourseCheckpoint handles between members.

Store a versioned challenge manifest and compressed common-input/host-command log, including engine build, API/schema versions, all ROM/content digests, roster, widths, timing profile, boot recipe and result identities. Namespaced challenge storage must not overwrite stock slots. MVP durable resume reconstructs by normal boot plus input replay; require owner-state agreement before handing control back. Portable checkpoints need a separately versioned schema and migration policy, not arbitrary CompositeSnapshot JSON.

## Staged delivery and completion gates

### Prototype: isolation and common-control feasibility

Deliver two workers, S1/GHZ1 and S2/EHZ1, then add S3&K/AIZ1; one sampler, ordered step acknowledgments, side-by-side native output and focused audio. Compare each alone and together; alternate member order, load/close/reopen one, stall one, and rewind one in diagnostic mode while the others' state/media remain unchanged. Measure real GPU frames and pre-mix PCM, not only CPU fields. Include duplicate-game workers to catch sharing hidden by S1/S2/S3K-specific stores.

Completion requires exact common-sample delivery, solo-versus-host agreement, bounded teardown/fault recovery and recorded resource/latency measurements. No full-route, author API, native executable or ranking claim. If an in-process backend is attempted, the same hostile matrix is a separate blocking gate; merely allocating two GameplayModeContexts is not success.

### MVP: a bounded, playable three-game challenge

Recommend GHZ1, EHZ1 and AIZ1 at 320×224, verified S1 World REV01/S2 World REV01/locked-on S3&K (the `AGENTS.md` identity table: CRC32 AFE05EEE/7B905383/63522553), native movement/no donor, Sonic main; freeze CPU-sidekick choices explicitly (AIZ's documented representative is Sonic+CPU Tails). Launch through fresh production entries and finish at actual Act2 handoffs. Provide ROM validation, all-three start barrier, readable panes, focus audio, independent death/respawn/progress, linked pause/restart/rewind, replay export/reconstruction and useful failure notices.

**Blocking feasibility gate:** author one frozen common input program that completes all three acts without per-world steering, debug completion or trace hydration; verify fresh S1/S2 routes rather than positioned signpost tests. Existing AIZ controllers and per-game recordings do not establish this. `docs/architecture/validation/levels/s3k-aiz1-sonic.md` supplies prior fresh-intro/recreation/reload evidence; `T/tests/{TestS1Ghz1Headless,TestS2Ehz1Headless}.java` supply local starting points, not a shared cold-run witness.

Do not silently disable native bonus/special-stage entries. Every reachable branch must run safely or stop with an explicit unsupported-route outcome; the supported finishing program must remain within assessed branches. MVP excludes full campaigns, emerald completion, donor/custom-character products, network play, live editing and leaderboards. Completion requires the witness, independence/rewind/error matrix, actual composed output/audio checks and documented support limits. Keep AIZ→HCZ regression checks green despite the bounded terminal. Initial support is the JVM build on a declared validated desktop platform; three hidden GL contexts and the host device must be proven there. Other platforms/native packaging require their own evidence.

### Polished: complete campaigns and authorable challenges

Deliver ordinary campaign launches through validated final completion/ending boundaries for all three games, including menus, game-over/continues, special/bonus stages and required emerald routes. Establish what “beat” means: recommend ordinary ending completion, with emerald/true-ending challenges separately named. Validate actual uninterrupted campaign chains and retained checkpoint/replay behavior; short first-act runs and positioned final bosses cannot certify this.

Later S3K routes have substantial documented progress, not universal completion: `docs/status/level-test-coverage.md#current-sk-campaign-position--2026-09-28` records MHZ–DDZ route evidence with breadth/presentation gaps and accepted ending-request stop lines. `M/game/sonic3k/Sonic3kGameModule.java` has no ending-provider override; `M/game/GameModule.java#getEndingProvider` defaults to null. Full ending presentation is a prerequisite if promised. Track remaining obligations in per-act/character matrices under the existing level standard/backlog.

Provide scalable triptych/stacked layouts, independent pane magnification without changing simulation width, keyboard/controller navigation, discoverable input mappings, high-contrast status, configurable flashing, subtitle/status alternatives to audio cues, focus/mix/pan controls and safe disconnect/focus behavior. Inspection selects an explicit member/generation: clocks, input/poll/admission, object/PLC state and fault ownership. Editing forks an unranked new identity after stopping the run.

Polished completion requires supported-platform soak/performance budgets, recovery and save compatibility, full campaign/route evidence and maintained executable author examples. Public challenge definitions may be optional mod content; engine supervision/concurrency remains engine-owned. Mutators must ultimately accept explicit member/session capabilities, compose in fixed order and be captured by rewind/replay. Coordinate that requirement with the mutator workstream; do not attach it to global GameServices.

## Work packages and acceptance matrix

Dependency order: (1) define input/admission/terminal identity and solo oracles; (2) extract the exclusive production worker driver from `GameLoop`/`Engine` and the boot/capture patterns in `M/tools/{HeadlessGameBoot,GameplayCaptureSession}.java`; (3) engine-owned worker supervision plus isolated config/save roots; (4) frame/PCM leases, host compositor/sink; (5) challenge checkpoints, outcomes and replay; (6) MVP common-input witness/UI; (7) campaign prerequisites and author API. Suggested new engine owners live under `com.openggf.game.challenge` with thin Engine entry wiring; keep algorithms out of Engine.

An optional in-process migration proceeds through instance session controller → explicit loop/level/sprite/sensor dependencies → console-scoped static state/V-int → per-world media/resources/mod views → tool/editor/save ownership. Maintain the single-game facade during migration, but forbid its use in multiworld paths; never install a ThreadLocal active-world shim as the result. Expand `T/tests/TestArchUnitRules.java` and `T/game/TestProductionSingletonClosureGuard.java`; retain `T/game/rewind/coverage/TestStaticStateRewindCoverageGuard.java` while adding mutable-static/ambient-root prohibitions and negative guard fixtures for migrated paths. Existing frozen root-access exceptions and rewind coverage are not isolation certification.

| Validation lane | Required evidence |
| --- | --- |
| Stock/parity | Feature-off solo baselines and per-member input/clock/state comparisons; existing ROM/native/trace lanes, V5 and `T/trace/timing/TestHardwareTimingAuthorityGuard.java` intact. `T/tests/TestS3kAiz1SkipHeadless.java`, both `T/tests/TestSonic3kLevelLoading.java` and `T/game/sonic3k/TestSonic3kLevelLoading.java`, plus `T/game/sonic3k/{TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils}.java` and AIZ→HCZ preserved. No trace comparison outputs feed gameplay. |
| Input/clocks | Unequal lag/pause/title holds, A/B/C/Start, same/different game instances, ordinal duplicates/reordering, external-drive exclusion, stalls/disconnect; retain `T/TestGameLoopExclusiveFrameDrive.java` and polling hardware-timing regressions. |
| Rewind/lifecycle | Two recreation/replay cycles around damage, contacts, water, boss children, death and load; common history floor; stale/wrong-member/ROM/config checkpoint refusal and failed-restore stop. |
| Media/resources | Solo-versus-triple RGBA and pre-mix PCM/chip/service state while siblings fade/load/mute/rewind/close; palette-latch GPU reads, duplicate IDs, cache eviction, balanced ROM/GL/PCM leases. PCM mean/AC variance/flat windows supplement RMS. |
| Fault/persistence | Input/update/draw/audio/save/restore/close exceptions, worker death and partial launch; no survivor mutations or stale packets; interrupted writes, no stock-save collision and bounded cancellation cleanup. |
| Performance/support | Cold/warm boot; 1/2/3 worlds; transition/boss-heavy soak; p50/p95/p99 step/readback/composition, RSS/GPU memory/queue depth, underruns and end-to-end latency on declared hardware/platforms. At 60 Hz the real-time budget is 16.67 ms; choose published hardware/latency limits from measurements. Raw three-pane RGBA is approximately 51.6 MB/s before copies. |

For future shared-loop/ownership code, normal change-based delivery validation and separate guards apply; inspect the runner plan/preflight, compare failures by identity/message, run affected trace/native fixtures with verified absolute ROM paths and inspect skips. Research here runs none of those suites. Follow the measurement-hazard table in `docs/agent-workflow/briefing-trace-rounds.md`; GPU or campaign claims need their own evidence.

## Worked examples and documentation deliverables

Proposed manifest vocabulary, **not an existing parser**:

```yaml
schema: 1
id: three-openings
rulesVersion: 1
tickHz: 60
input: common-held-pad
hostPause: separate-command
rewind: common-valid-history
members:
  - {id: s1, game: s1, route: GHZ1, terminal: natural-Act2-handoff}
  - {id: s2, game: s2, route: EHZ1, terminal: natural-Act2-handoff}
  - {id: s3k, game: s3k, route: AIZ1, terminal: natural-Act2-handoff}
```

The executable example must fill immutable ROM/launch/config identities, specify each roster and reject missing ROMs before launching anything. Human walkthrough: validate ROMs → select Three Openings → see all ready → play one pad → inspect three independent progress states → pause/restart together → export/replay with the same outcome. No winning input sequence is asserted by this design.

Author-rule pseudocode, **proposed contract** (terminal state also joins host rewind):

```text
onCommittedTick(readonlyView):
  for member in manifest.members:
    if readonlyView.hasNaturalTerminal(member.handle, member.terminal):
      completed.add(member.handle)
  return SUCCESS if completed covers all members else RUNNING
```

The host supplies handles/outcomes and contains owner faults; this callback cannot step, steer or mutate worlds.

Agent example: offer RIGHT+C at tick 42 to all members; let A poll and B perform a native non-polling lag iteration. At tick 43 keep those held: A has no new C edge, B derives one when it first polls. Assert both received identical samples, their polling histories differ legitimately, and neither challenge ordinal overwrote V-int. Replay this case, a natural act completion and a failed checkpoint restore as small executable tests.

Ship `examples/three-openings` with runnable host definition, the validated common-input program, expected per-member terminal checks, rejected-input/ROM examples and a regression test; add an author sample for a readonly completion rule and explicit member-scoped mutator composition. Document actual commands only once implemented. Explain held signals versus native edges, setup/terminal rules, rewind floors, save identity, performance/support limits and feature-off parity. Update existing modding guides/index, configuration and architecture ownership docs, act matrices/backlog and the dated task artifact with rejected approaches/kill evidence.

Any public API change must coordinate `mod-api-release-policy.properties`, `M/mods/ModApiVersion.java` and `src/test/resources/mods/mod-api-signatures-0.7.txt`, plus surface/SDK/Javadoc/sample checks. The observed descriptor is candidate 0.7.0 with no published baselines; do not invent a new API version or publication promise.

Only consequential user decisions remain: ordinary versus all-emerald completion, strict game-over versus practice retries as the default, and target platforms/minimum hardware/accessibility priorities. The process backend, opening-act scope and native-size panes are recommended implementation defaults. Full-campaign feasibility, transport latency and live-mode determinism remain untested risks.

## Research accounting

Read-only checks: `git rev-parse HEAD` matched the supplied base; `git status --porcelain=v1 --untracked-files=no` showed no tracked changes; targeted `rg --files`, `rg -n`, `sed`/`cat` and a Python source-field inventory established the evidence above. No engine tests, builds, ROM execution, runtime probes, installations or repository/disassembly writes occurred. Earlier test results cited from matrices are historical, not new passes.

Two inherited Codex GPT-6.1-sol child audits contributed media/resource and input/rewind evidence, incorporated above. Both completed with no pending work. The coordinator retains exact lifecycle identities outside the repository while accounting for cleanup.

## Owning references

- [Session ownership](../../../src/main/java/com/openggf/game/session/SessionManager.java).
- [World and mode split](2026-04-07-runtime-ownership-migration-design.md).
- [Mutable static oscillator owner](../../../src/main/java/com/openggf/game/OscillationManager.java).
- [Singleton closure guard](../../../src/test/java/com/openggf/game/TestProductionSingletonClosureGuard.java).
- [Historical container plan](../plans/2026-03-24-game-runtime-container.md).
- [Current route coverage](../../status/level-test-coverage.md).
