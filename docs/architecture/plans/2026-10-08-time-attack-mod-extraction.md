# Time Attack and multiplayer racing extraction into a bundled mod

Date: 2026-10-08. Line: 0.8 (`next`, after the develop sync merge). Supersedes the seam list in
[v0.8 roadmap Milestone 2](../../project/v0.8-roadmap.md#milestone-2--extract-time-attack-and-multiplayer-into-a-bundled-mod).

## Decisions (user, 2026-10-08)

- Multiplayer is **split**: race gameplay, UI, coordinator and client in the mod; protocol and room logic
  in an engine-free racing library; master server, dedicated host, verifier and operator tools in a
  separate server artifact; in-process LAN hosting rewritten on the JDK so it can live in the mod.
- The mod is **bundled and enabled by default** through a build-pinned trust manifest. It is validated
  like any other mod and the player can disable it.
- Racing is **JVM-only**. Native builds reject code mods today; Time Attack never shipped on master.

## Review inputs and what they changed

Two independent read-only reviews (Fable 5.1, GPT-6-Astra) of the lead's feasibility assessment, at
develop 378c1d715. Load-bearing claims were re-checked in code by the lead before adoption.

| Finding | Source | Effect on plan |
|---|---|---|
| "Hooks observe only" is false: launch, retry, act-end routing, stage suppression, countdown hold, rewind/editor lockout, debug taint | both | Run-scoped policy + run host with admission (hold) |
| `GhostRenderRegistry`/`AttemptInputRecording` are not `@ModApi` (roadmap wrong) | both | Annotate and pin as part of the ghost seam |
| Validator rejects classes in `com.openggf` and non-literal statics | both | Mod and library use `openggf.*` packages; statics become instance state |
| TA launches disable all mods (`DETERMINISTIC` + `disableForDeterministicSession`) and the latter closes the session view | both + lead | Launching owner keeps a host lease; gameplay content stays stock |
| Startup scenes replace stock titles; `any` mods may only register scenes | both | New master-title entry; relax `any` for it |
| `GameplayFrameController` exists but replaces the ordinary loop | Fable | Not reused; ordinary-mode run host instead |
| Live and verifier replay differ (Start bit masked, suppression flag not set) | Astra | Run policy is recorded data; replay applies it |
| Netty/SQLite cannot ship inside a mod | both | Server artifact; JDK host for in-process rooms |
| No bundled-mod mechanism; new jars are disabled and need per-hash trust | both | Bundled catalog source with build-pinned manifest |
| `ModStorage` is text-only, 1 MiB | both | Bounded binary storage method |
| Persisted data lives in CWD `ghosts/` and `identity/` | both | One-off import; never regenerate identity silently |

## Engine seams (generic, `@ModApi`, pinned, documented, non-TA sample each)

1. **Run policy** (`GameplayRunPolicy`, session-scoped data): special-stage entry, bonus-stage entry,
   act-completion handoff (`CONTINUE` | `RETURN_TO_HOST`), live rewind, editor entry, save mode.
   Stock default reproduces the campaign. Objects read it through `ObjectServices`; replaces the six
   object checks, `GameStateManager.timeAttackActive`, the menu-return queue and the GameLoop
   `TimeAttackLevelEndRouting` branches. Results screens consult it before their ROM side effects.
2. **Gameplay launcher with run host** (`SceneContext.gameplay()`): `launch(RunSpec, RunHost)` from a
   mod scene; engine resolves ROM/module/display/no-save; returns a `RunHandle` (`retry`, `leave`).
   `RunHost` callbacks: `onLevelReady`, `admitStep(input)` (hold = false), `afterStep(CompletedStep)`
   (immutable input mask, player pose, checkpoint, completion signals), `drawOverlay(canvas)`,
   `onRunEnded(reason)`. The launching scene resumes on return. The run is a stock deterministic
   session: no mod gameplay content; the launching owner participates only through these callbacks,
   under its fault boundary and a host lease that survives `disableForDeterministicSession`.
3. **Master-title entry** (`ModContext.registerTitleEntry(label, ModSceneFactory)`), available to
   `any` mods, not replacing stock titles.
4. **Ghost presentation**: owner-scoped ghost source over the existing `GhostRenderRegistry`, with
   immutable frames and engine-owned art-bank allocation; cleanup on run end and fault.
5. **Bounded binary owner storage** on `ModStorage` (atomic write, size cap).
6. **Headless input replay service** for tools: input masks + run policy from level start →
   per-step observations; no addon, no network.

## Mod, library and server layout

- `racing/net` — engine-free library `openggf.racing.*`: protocol, hub, client (JDK WebSocket),
  identity, attempt timing rules, ghost wire codec. Validator-clean.
- `racing/time-attack` — bundled mod `openggf.timeattack`: track catalog, attempt runtime, ghost
  store, HUD, menus (on `mods.ui`), multiplayer coordinator/lobby/browser, JDK in-process host.
- `racing/server` — master, dedicated Netty host, verifier, bot/load/profile tools; Netty + SQLite.
- Distribution: the universal jar embeds the bundled mod jar and a hash manifest; the JVM archive
  ships `bundled/`; native archives omit it. The plain engine artifact contains no racing code.

## Sequence

0. Sync `next` with develop (merge). 
1. Run policy + completion handoff (engine; built-in TA adapter consumes it). Verify object gates,
   S1 SBZ2/FZ, S2 results, S3K results/seamless, campaign defaults, required S3K tests.
2. Run host + launcher + title entry + ghost seam (engine), with TA still in-tree as first consumer
   and a non-TA practice sample. Collapse Engine/GameLoop racing orchestration.
3. In parallel lanes: (a) racing library/server split + JDK host + static cleanup;
   (b) bundled catalog source, universal-jar embedding, release workflow, Mod Manager badge;
   (c) binary storage + headless replay service.
4. Move TA + multiplayer client into the mod; delete engine copies; migrate tests; import legacy data.
5. Combined change-based validation, guards (ratchets tighten), docs, roadmap/changelog.

## Kill conditions

- Racing addon must load in certifying sessions, or needs a feature-name exception to external
  content policy → stop and redesign.
- Default-enabled addon changes ordinary campaign behavior → stop until defaults are proven stock.
- Extraction needs broad internal bootstrap APIs or validator exemptions → stop.
- Attempt replay equivalence (first input, finish frame, splits, hashes) fails after the move.

## Lane B record: bundled catalog source, binary storage, build (2026-10-08)

Worktree `.worktrees/ai-bundled-mod-source`, branch `feature/ai-bundled-mod-source` from
`next` b1e8b5254. Commits: `01bdc4206` binary owner storage; `89b4c98c1` bundled catalog
source and Mod Manager badge; `c9ebcb2f6` locator hardening; `4748838fb` build step,
universal-jar embedding and release checks.

Decisions:

- **Trust anchor.** The manifest (`META-INF/openggf/bundled-mods.json`: id, version, file,
  SHA-256, size) is written into the engine's own classes at `prepare-package`, so the
  engine artifact pins the hashes. `bundled/` beside the jar and `openggf-bundled/`
  resources supply bytes only. Rejected: a manifest inside `bundled/`, because whoever
  can drop a jar there could also rewrite its hash list.
- **Byte sources.** Embedded resources win over `bundled/`. Embedded jars extract to
  `SavePaths.root()/bundled-mod-cache/<sha256>.jar` (staged, hashed while streaming,
  bounded by the recorded size, re-hashed every boot, engine-named stale files pruned).
  Install directory: `-Dopenggf.installDir`, else the engine code source's parent
  directory (jar → its directory; `target/classes` → `target`). The working directory is
  never used.
- **Verification.** Size and SHA-256 are checked before parsing (so damage reads as
  `BUNDLED_MOD_HASH_MISMATCH`), then the retained `ModAssetRoot.jar` snapshot is hashed
  again and its manifest id/version must equal the entry. The descriptor carries that
  snapshot as `retainedSource`, so `ModCatalogValidator`, `ModAudioPreparer` and
  `ModClassLoaderFactory`/`ModValidator` all read the verified bytes.
- **Defaults and persistence.** A verified bundled id without a `modstate.json` entry is
  injected enabled and ordered before user mods (user overrides keep winning). Trust for
  the manifest hash is injected into the in-memory startup state every boot and stripped
  by `PendingModStateEditor.save`, so `modstate.json` only holds player grants and choices.
  An explicit disable persists across upgrades; a new build trusts only its own hash.
- **Duplicates.** Manifest ids are reserved whether or not the bundled jar verified: a
  `mods/` jar with that id becomes an `InvalidModEntry` (`BUNDLED_MOD_ID_RESERVED`) so the
  bundled copy is not blocked as `DUPLICATE_MOD_ID`. Rejected: falling back to the user
  copy when the bundled jar fails, which would let a damaged install silently switch code.
- **Policy.** The locator runs inside the `normalBootLoader` supplier, so
  `STARTUP_DETERMINISTIC` boots never open or extract; development runs skip it. Native
  profile skips packaging; native resource config carries neither manifest nor jars.
- **Binary storage.** `ModStorage.readBytes/writeBytes`, 4 MiB per file, shared namespace
  and name rules, staged-and-renamed writes. No per-owner total quota yet.

Open questions for integration: a fault-boundary disable is persisted exactly like a
player's disable, so it also survives the upgrade that fixes the fault; and a bundled mod
that registers a startup scene would replace stock titles for every player by default.
