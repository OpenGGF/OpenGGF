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

## Lane A design note: JDK room host and racing-library static state

Branch `feature/ai-racing-jdk-host` from `next` b1e8b5254; commits 3a24de4c4 (contract),
158944619 (JDK host + parity tests), 38339e7ea (static state + gates). Focused validation at
38339e7ea: `net/**`, `game/timeattack/**`, `TestNetIsolationRules`, the isolation scope guard,
`TestNetworkMenuFeedback` and `TestVerifierWorker`, 81 classes, 446 tests, 0 failures, 0 skips.

**Contract.** `net.host.RaceRoomHost` (`port`, `tlsCertificateSha256` or null, serialized
`execute`, `room`, idempotent `close`) is implemented by the Netty `RaceHostServer` (dedicated
server) and by `net.host.jdk.JdkRaceHostServer` (in-process rooms inside the mod).
`HostMasterLink.forServer` takes the interface. Engine and `DirectRoomTls` callers are unchanged;
switching the in-process host to the JDK one is an integration step.

**JDK host.** Plain `ServerSocket` with layered server-mode `SSLSocket` (so a deadline or abort
can hard-close the TCP socket without blocking on TLS close), one virtual acceptor, one virtual
reader and writer per peer, one daemon room thread for the 50 ms tick, callbacks and
`execute` tasks. Budgets in `JdkHostLimits.defaults()` mirror the Netty pipeline: exact `/race`,
version 13, masked client frames, 64 KiB frames and messages, 4 KiB binary packets, strict
UTF-8, four sockets per address, the shared `ConnectionHygiene.RateBucket`, 10 s TLS-plus-upgrade
deadline, 60 s read idle. Pinned TLS uses a fresh ECDSA P-256 self-signed v3 certificate (CN
`openggf-direct-room`, notBefore one year back, notAfter 9999-12-31, SHA-256 pin) written by a
small DER encoder and served through a one-key `X509ExtendedKeyManager`; no keystore, password
or temp file. Netty used RSA-2048 through Bouncy Castle; clients pin the digest, not the
algorithm.

Deliberate differences, all stricter or more graceful: RSV bits rejected (no extension is
negotiated; Netty's `allowExtensions=true` ignored them); ping and pong draw from the rate
bucket; non-`/race`, non-GET and malformed upgrades get an HTTP error and a lingering close
(Netty idles a wrong path until the 60 s timeout and leaves a 426 open); room-initiated closes
flush queued frames and send a 1000 close frame carrying the reason, then drain until the peer
answers or a 2 s grace expires; a failing tick is logged and the tick keeps running; total
sockets (64) and per-peer outbound queues (4 MiB, above `GhostHub`'s 1 MiB slow-consumer
drop) are capped. Only WebSocket version 13 is accepted (Netty also spoke drafts 0/7/8).

**Static state.** The validator accepts only literal static constants, so `ControlJsonCodec`
is the instance envelope codec owned by `RoomHost`, `RaceClient` and `MasterClient`
(`RoomHost` has an overload to share one). `ControlCodec` stays as the server-side static
facade over one shared codec for the master, tools and tests, and is excluded from the mod
set. `PlayerIdentity` builds its owner-only permission set per call.

**Gates.** `TestRacingLibraryStaticState` runs the real `ModValidator` over net protocol, hub,
client, identity, `host/jdk` and the shared host classes. The validator reports every
`com/openggf/` class as `RESERVED_ENGINE_PACKAGE` and skips the static rule for it, so a test
that only filtered reserved findings would pass vacuously; the gate relocates the class bytes
to the same-length `org/openggf/net/` (slash form and javac's `$SwitchMap$com$openggf$net$...`
dollar form, which the validator matches against the enum name) and validates the result. A
canary with a static `Object` and `List` proves the rule fires; including the `ControlCodec`
facade makes the gate fail. It also asserts the mod set references only itself (no facade,
master, Netty host, Netty, Bouncy Castle or SQLite). `TestNetIsolationRules` adds ArchUnit
fences for the same packages. `hubBackpressureLadderSeesTheJdkOutboundQueue` shows `GhostHub`'s
slow-consumer drop works from the JDK queue depth alone (transport cap raised out of reach);
both queue tests fail when `queuedBytes()` or the cap is disabled. Delete the relocation gate once the library and mod are built and
validated as their own artifacts.

**Split guidance for integration.** Library: `net.protocol` (without the `ControlCodec` facade),
`net.hub`, `net.client`, `net.identity`, and from `net.host` the Netty-free `RaceRoomHost`,
`HostMasterLink`, `ConnectionHygiene`. Mod (or library): `net.host.jdk`. Server: `RaceHostServer`,
`RaceHostChannelHandler`, `DirectRoomTls`, `net.master`, and either the facade or a server-owned
`ControlJsonCodec`.

**Open items.** `ControlJsonCodec` and `BundledProfileSource` still use Jackson from the parent
class loader (allowed today, not a compatibility promise). `BundledProfileSource` reads a
classpath resource that a mod class loader hides. `RelayRoomManager` uses the five-argument
`RoomHost` constructor, so the master now builds one `ObjectMapper` per relay room; pass a
shared codec through the new overload when the master moves. `TestRaceClientLoopback`'s
malformed-join case still uses a Netty fake server and must be ported to the JDK host when the
library leaves the engine. Virtual-thread pinning was not measured; I/O paths use JDK 21
`ReentrantLock`-based socket and TLS code.

Rejected: blocking `SSLServerSocket.accept` (a stalled TLS peer could not be hard-closed
without its close path), a PKCS12 keystore (password and PBE cost for one key), validating the
in-tree classes with reserved findings filtered (vacuous, see above), and deleting the static
facade now (would edit `net/master`, outside this lane).
