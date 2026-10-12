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

## Integration record (lead, 2026-10-09)

Branch `feature/ai-time-attack-mod` from `next` 3c569f98a (after the develop sync merge).

| Commit | Step |
|---|---|
| `499970995` | Session `GameplayRunPolicy` replaces the time-attack flag in six ROM objects, the S1/S3K results screens, `LevelManager.advanceToNextLevel` and the GameLoop chokepoints |
| `46b256bea` | Run API (`com.openggf.game.run`) and `HostedRunController`; Time Attack became a `RunHost` |
| `02f567fc1` | Title entries (`ModContext.registerTitleEntry`, EXTRAS chooser), `SceneContext.gameplay()`, scene suspend/resume, fault-bounded hosts |
| merge | Lane A (JDK room host, static state) |
| `7dfaac2a4` | Engine → `racing/` move; Netty/Bouncy Castle/SQLite test-scoped; `RunLevelStart` level size, post-completion `RunHandle.spectate`; ratchets tightened |
| merge `f66de8319` | Lane B (bundled source, binary storage, packaging) |
| `18fdd90c3` | `openggf.bundled.mods=racing/time-attack`; `TestTimeAttackModPackage` |

Decisions taken during integration:

- **No engine headless-replay seam.** The verifier is an operator tool that embeds the engine,
  so `racing/server`'s `AttemptReplayHarness` keeps using engine internals; only the mod is
  held to the public API. Revisit if a second replay consumer appears.
- **Runs admit no mod content, including the host's.** The launching mod takes part only
  through run-host callbacks and the policy, so a run's simulation is the stock game's and
  the determinism fingerprint stays engine build + ROM; the jar-hash admission policy
  ("S6") is unnecessary.
- **Persisted data stays put.** The mod keeps CWD-relative `ghosts/` and `identity/`
  (trusted code may use the filesystem); moving to owner storage is a follow-up.
- **Racing builds inside the engine reactor.** `racing/**` compiles and runs its tests with the
  engine test classpath (build-helper) so the ordinary suite and CI cover it; the bundled
  packager builds the mod jar from the same sources.
- **Lane B open questions:** a fault-triggered disable of a bundled mod persists like a
  player disable (accepted; distinguishing needs a modstate field); bundled mods must use
  title entries, not startup scenes (rule; Time Attack complies); a read-only `saves/`
  blocks the bundled-mod cache (accepted).
- **Rejected:** keeping multiplayer in the engine while solo moved (the coordinator depends on
  the attempt runtime, so the engine would have depended on the mod); a generic in-engine
  netplay transport (no second consumer); moving the race protocol into the public Mod API
  (develop had just removed `@ModApi` from `ControlMessage`).

## Lane MP record: multiplayer racing inside the mod (2026-10-09)

Worktree `.worktrees/ai-time-attack-multiplayer`, branch `feature/ai-time-attack-multiplayer`
from `feature/ai-time-attack-mod` 18fdd90c3.

Design:

- **`RaceSession`** (`openggf.timeattack.mp`, owned by `TimeAttackScene`, closed in `exit()`)
  ports the removed Engine orchestration: host LAN on `JdkRaceHostServer.startTls`, join by
  `DirectJoinAddress` invite, browse the master (`MasterClient`, optional trust-all TLS), create
  relay/direct master rooms (`DirectRoomRegistration`, `HostMasterLink`), the direct-host
  heartbeat and track updates, round launch and leave. The determinism fingerprint comes from
  `SceneGameplay.determinismFingerprint(gameId)` (lead seam d996ab332); empty shows a "ROM
  is not available" message. The scene claims `ModScene.capturesTextInput()` while a text
  field is focused, so the engine holds back its global display and capture shortcuts.
- **Threads.** Each connecting operation runs on one virtual thread and returns a continuation
  that `poll()` runs on the engine thread; network threads never touch the scene, run or
  coordinator. An operation owns what it has built (room host, connections, a created master
  room, a bound host link) until the engine thread adopts it, so leave/cancel/exit mid-connect
  closes half-built rooms and late connections. Waits poll a cancellation flag in 50 ms
  slices instead of interrupting, because an interrupt during `PlayerIdentity.loadOrCreate`
  could cut identity creation short; identity loading is serialized for the same reason.
  The master link thread drains the master socket (nothing else reads it once a room is
  joined, and a full inbound queue disconnects) and sends the direct host's heartbeat, reading
  the room's player count and track on the room host's own thread (the Engine read them
  unsynchronized from its heartbeat thread).
- **Rounds.** The lobby launches one run per `RoundStart` the coordinator has seen
  (`roundsStarted`): joining during a running round launches once, a host restart from
  ROUND_END launches, and a player who returns while the window is still open stays in the
  lobby. The Engine kept a per-screen "launched" flag that reset only in LOBBY and built a new
  lobby screen on every return, so leaving a run during the window relaunched the same round
  at once. A LOCKED round launches with its locked character. The host applies
  `LiveLevelProfileFactory.fromLevelStart` to its room through the coordinator's level-ready
  hook.
- **Views.** Lobby, room browser, settings and a keyboard text field are scene views driven by
  a small `ViewInput` (so tests drive them without a scene host). The text field reads keys
  only: Space and Backspace are pad A and Start by default, so a button-driven field would
  confirm while typing. Scenes have no clipboard: the LAN invite is shown in full on the lobby's
  invite page, written to `lan-invite.txt` in the mod's storage and logged. In the lobby,
  keys 1-3 vote (the Engine lobby had no vote input; votes were in-run only).
- **Settings** (`TimeAttackSettings`, `settings.txt` in mod storage): host port 27888, last join
  address, display name, master URL, master trust-insecure false, minimap true.
- **HUD.** `TimeAttackRuntime.FrameCompanion.drawOverlay(canvas, levelWidth, localCentreX)`;
  the runtime passes the act width from `RunLevelStart` and the player's centre X from the
  latest `RunStep`, and the coordinator draws `MultiplayerHudRenderer`.

Rejected: blocking `.get()` on the engine thread as the Engine did (a five-second frame freeze
per connect); interrupting cancelled workers (identity creation risk, above); a cached
"last fingerprint seen at level start" instead of the engine seam (wrong after every upgrade
and empty before the first solo run); Mockito-mocking the final `MasterClient` in browser tests
(the view now depends on a two-method `RoomDirectory`).

Open items: `RaceClient`/`MasterClient` never close their `HttpClient`, whose selector thread
outlives the connection until collected; the solo and multiplayer HUDs both draw top-right
(as in the Engine); a player who leaves a run while the window is open cannot rejoin that
round; the master link's five-second heartbeat cadence is not covered by a test.

## Delivery validation (lead, 2026-10-09)

Change-based run `--base 3c569f98a` (origin/next) at `b8a9ea66a`, full ordinary selection
plus guards. A first attempt with `--max-minutes 75` timed out after 2,814 reports: the
ordinary lane now takes about 82 minutes, not the September 24. The repeat
(`--max-minutes 150`) completed:

- Ordinary: 3,070 reports, 26,764 tests, 27 failures, 0 errors, 62 skips (4,911 s). 26
  failures are develop's inherited S3K cold-route assertions (MHZ, DEZ, LRZ, SSZ), identical
  first lines to the actual-main table in
  `docs/architecture/audits/2026-10-07-stock-parity-gap-verification.md` on develop (the SSZ
  Tails replica row under that table's normalization). The 27th,
  `TestS1GameplayAudioTimelineCli#shellUsesAbsoluteBootstrapTools...`, is environmental: the
  launching shell exported `LD_LIBRARY_PATH`, which `run_s1_ghz1_gameplay_audio_timeline.sh`
  rejects by design (exit 4; exit 0 without it); script and test are unchanged from base.
  All 62 skips appear in develop's skip table.
- Guards: 86 reports, 674 tests, 2 failures, both caused by this branch. ArchUnit found new
  top-level edges `game -> mods` (a new `game`/`mods` cycle), `control -> debug` and
  `sprites -> debug`.

Fix: the run API moved from `com.openggf.game.run` to `com.openggf.mods.run` (nothing in
`game` uses it; `RunHost.drawOverlay` takes the `mods.ui` canvas), and the Mod API pins were
regenerated; `MasterTitleScreen.setTitleEntries` takes labels and an index opener instead of
`OwnedTitleEntry`; `DebugAssistInput` and `GhostRenderer` moved to the root `com.openggf`
package beside `GameLoop`/`HostedRunController`, their only users. Rejected: adding the three
edges to the core-runtime ratchet, which would pull `mods` into the frozen cycle cluster.

After the fix: `-Pguards` 674 tests, 0 failures, 0 skips; a focused set (Mod API pin/policy
tests, run seams, title entries, master title, input ownership, game loop, ghost renderer,
gate objects, act-practice and hello-scene examples, every `openggf/timeattack` and
`openggf/racing` test) 120 classes, 866 tests, 0 failures, 0 skips. The ordinary suite was not
repeated: the fix is package moves and one constructor-shape change, covered by the guards
and the focused set.

## PR 217 CI follow-up (2026-10-09)

Review input at `b548d49b4`: no submitted reviews or inline comments. CI run
`37916774445` had two failures: `TestOpenAlPcmSink#warmedAudibleCursorQueriesReusePrimitiveLedgerWithoutAllocation`
reported 88 allocated bytes; `TestObjectUpdateClockTerminologyGuard` reached its
two-minute child deadline and then threw `IOException: Stream closed` while
reading the forcibly closed diagnostic pipe. Both tests passed in the unchanged
focused baseline (18 tests, no skips), with the terminology scan taking 58 seconds
and approaching its 1 GiB child heap limit.

The follow-up changes test infrastructure only. The cursor test reads allocation
counters directly around a warmed query loop, keeping the exact zero-byte budget
and checksum. This removes the shared benchmark probe's unrelated work and
polymorphic `Runnable` dispatch; deoptimization/class-loading noise is a plausible
explanation for the CI-only bytes, not an attributed allocation stack. The
terminology child retains isolated attribution, uses a bounded 2 GiB heap and a
four-minute deadline, writes diagnostics to a temporary file before waiting, and
is stopped on every exit path. The file also avoids deadlock if compiler errors
fill an unread pipe. Rejected: tolerating a nonzero allocation floor, skipping the
guard, or changing gameplay to address test-harness failures.

Validation scope: the change-based plan against pre-follow-up `b548d49b4` selects
2,250 ordinary classes (`audio`, `common`, `gameplay`). Under proportionate
validation, the two failing tests, neighboring audio allocation checks, deliberate
failure controls, and the complete structural guard suite cover this test-only
change; engine production code, build policy, and fixture inputs are unchanged.
Tool preflight passes with `LUA_BIN=/usr/bin/lua5.4` (the default `lua` is older).

Completed checks on `b548d49b4` plus this follow-up in `.worktrees/ai-time-attack-mod`:
`PR217_ROM_ROOT` resolves to the main workspace; the three existing ROM identities
matched `AGENTS.md`.

```bash
PR217_ROM_ROOT="$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")"
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Pguards \
  "-Dsonic1.rom.path=$PR217_ROM_ROOT/s1.gen" \
  "-Dsonic2.rom.path=$PR217_ROM_ROOT/s2.gen" \
  "-Ds3k.rom.path=$PR217_ROM_ROOT/s3k.gen" test -B
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  -Dtest=TestOpenAlPcmSink,TestAudioPresentationAllocationBudget test
```

- Guards: all 86 classes, 674 tests, no failures/errors/skips (4 min 35 s);
  terminology attribution completed in 58 seconds.
- Focused audio: 22 tests, no failures/errors/skips. This is focused ordinary
  validation, not a full ordinary-suite pass; the new PR CI run remains separate.
- Deliberate controls: an escaping `long[64]` per cursor query failed the zero-byte
  assertion (5,280,000 measured bytes); renaming the projectile hook's first
  parameter to `frameCounter` failed the terminology guard with the owning method
  identified. A child emitting 480 KB of diagnostics and failing returned the
  expected assertion in under a second, without a timeout or closed-stream error.
  Every mutation was restored before the final checks; no controls or raw logs
  are retained as fixtures.


## Develop integration and shared networking review — 2026-10-12

Integration base: `develop` `f9d4e04747a6474c81cc9c9424951fecf3de191b`.
Existing extraction source: PR 217 merge `1d3b05a568` (first parent `3c569f98ac`).
Work remains in the user's current checkout, `feature/ai-time-attack-mod-extraction`;
the existing dirty `docs/skdisasm` checkout is unrelated.

The earlier broad gameplay-mode draft was rejected in favour of PR 217's narrower
stock-run contract and build-pinned bundled loader. The combined implementation
keeps both solo and multiplayer in one mod, with these corrections:

- Reusable JDK networking, race rooms, clients, protocol and identity live in core
  `com.openggf.net`, with explicitly enumerated candidate Mod API contracts. Mods
  consume these services. Netty, SQLite and Bouncy Castle remain server/test dependencies.
  Existing `SceneNetwork` still provides scene-owned peer messaging. Putt Putt Paradise's
  separate TCP implementation is preserved; migrating it is outside this task.
- The master title always shows the generic Extras chooser, even with one entry.
  Develop's title-time mod reload, target labels and scene audio initialization remain.
- Owner callback faults retire hosted runs and return through scene teardown. Scene
  launch capabilities expire at exit and cannot be used by workers or later visits.
  Invalid ghost lists are validated inside the owner fault boundary. Failed level loads
  use the normal run cleanup, and held gameplay still processes retry/leave transitions.
- Legacy retry/network preferences import once into mod storage. The engine preserves
  unknown YAML fields before destructive writes, including sparse files; no Time Attack
  settings return to the engine configuration model. Ghost and identity paths stay stable.
  Shared identity creation is serialized to avoid concurrent first-use replacement.
- Category inventory includes the additional Maven racing test roots; validation must
  cover the packaged mod and server as well as core. A strict packaging build checks the
  mod's actual dependency surface rather than relying on test compilation alone.

Validation for this composition is recorded below after execution. Earlier run results
in this document qualify their named historical source only.


### Combined verification (current checkout, pre-integration)

- `python3 tools/testing/maven_queue.py -Dmse=off -DskipTests compile`: passed.
  Candidate signature generation reports no missing annotations or external signature
  leaks; the normalized 0.7 candidate pin includes the shared networking closure.
- Focused selector `com/openggf/net/**/Test*,openggf/racing/**/Test*,openggf/timeattack/**/Test*,TestNetIsolationRules,TestHostedRunController,TestSceneRunLauncher,TestModSceneHostGameplay,TestUnknownConfigPreservation,TestGameLoopFreezeContractWiring,TestMasterTitleHub,TestModTitleEntryRegistration,TestBundledModPackager`
  via the queued Maven wrapper: 543 tests, one failure in the new retry test's deep-stub
  fixture, five missing-ROM skips. Explicit checkpoint/transition mocks replaced the
  deep stub, which had manufactured unrelated pending transition requests.
- Queued rerun selector `TestGameLoopFreezeContractWiring,TestAttemptReplayHarness,TestTimeAttackTrackCatalogRomValidation,TestEngineModRuntimeLifecycle,TestModEngineWiringSeams,TestMasterTitleSecondaryActions,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`:
  **96 passed, zero skips**, with all three ROM path properties pointing to the existing
  absolute root `.gen` files. CRC32/SHA-1 matched the project identities. This covers
  the repaired fixture and the five previously skipped replay/track checks.
- `python3 -m unittest discover -s tools/testing -p 'test_run_categories.py'`:
  **28 passed**. Full Python safety (`test_run_categor*.py`): 137 tests, three failures,
  15 skips. The exact failing methods in `test_run_category_memory_profile.MemoryProfileTests`
  (`test_real_junit_reports_dynamic_skipped_and_deliberately_retained_memory`) and
  `test_run_category_queue.QueueTests` (`test_lean_cli_passes_bounded_heaps_and_records_its_own_budget`,
  `test_maven_wrapper_waits_for_same_slot_preserves_args_cwd_output_and_failure`) failed
  identically in separate matched subprocesses with baseline `f9d4e04747` and current
  runner modules: three failures, no errors/skips each. Causes reported were missing
  JUnit platform engine dependencies, macOS first-thread argLine expectations, and the
  existing slot-wait expectation. No unrelated queue or memory-profiler changes were made.
- Change-based tool preflight passed Java 21, Lua 5.4 and PowerShell. Broader engine
  validation and packaging results follow separately; these focused checks are not a
  full-suite result.

- `python3 tools/testing/maven_queue.py -Dmse=off -DskipTests clean package`: passed;
  bundled Time Attack validated with zero findings. Clean artifact inspection confirms
  shared networking and generic ghost rendering in core, no Time Attack/server classes
  or Netty/SQLite/BC libraries in the engine jar, no copied networking in the mod jar,
  and matching bundle size/hash.

- Combined change-based invocation against `f9d4e04747a6474c81cc9c9424951fecf3de191b`:
  `python3 tools/testing/run_categories.py --base f9d4e04747a6474c81cc9c9424951fecf3de191b --run --max-minutes 120`.
  Ordinary lane processed 19,475 tests (7 failures, 5 errors, 151 skips) before
  `TestObjectControlledGravity` stalled. A native sample confirmed `AWTStarter` /
  `NSApplication.run` nested under GLFW, matching the documented macOS hazard;
  the stuck fork was terminated after 42 minutes. This is **incomplete ordinary
  validation**, not a full-suite pass. The separate guards lane completed:
  **674 passed, zero skips** (621 seconds). Skips include hardcoded short ROM
  filenames despite explicit ROM properties, absent KiS2 ROM, opt-in diagnostics,
  unavailable graphics contexts, and inapplicable Infinite Sonic route cases.
  Consumed diagnostics were acknowledged and deleted.
- The broad run exposed descriptor comment parsing, the explicit platform allowlist
  pin, old UI status expectations, and binary-storage methods missing in example
  test doubles. Fresh focused checks below cover their corrections. Unattributed
  failures also involved the S3K data-select shader context, sample-character
  macOS `base64` invocation, and a scene-network connection reset.

- Bounded baseline comparison at `f9d4e04747` used queued `clean test` with selector
  `TestModManagerScreenHost,TestS3kDataSelectPresentation#visualCapture_selectedSaveSlotShowsRightBodyRail,TestPhase3SampleCharacterIntegration,TestManagedSceneNetwork`
  and the same absolute ROM properties: 21 tests, 3 failures, 2 errors, zero skips.
  It reproduced the stale empty-mod UI assertion, both sample-character `base64`
  failures, shader 410 context error, and scene-network connection reset. The UI
  assertion is updated with this integration; unrelated platform failures remain.

- Corrected candidate queued `clean test` with the same four-class baseline selector
  plus `TestBundledModBoot,TestModApiReleasePolicy,TestModApiRuntimePolicy,TestModApiPinPolicy,TestModApiSignatureSurface,TestFlappyTailsExample,TestStarfallFrontierExample`:
  58 tests, 2 failures and 2 errors, zero skips. All integration repairs passed;
  only the same two `base64`, shader-context and socket-reset baseline failures remain.
- Fresh queued `-Dtest=TestObjectControlledGravity test` with the same ROM properties:
  **12 passed, zero skips**. This isolates the stalled class successfully without
  claiming the earlier incomplete ordinary lane finished.
- Remote develop advanced to `5a1f9b0fe7` during validation with documentation-only
  creator-audit changes; integration retains those changes.
- Final corrected develop `-DskipTests package`: passed; bundled mod validation has zero findings.

### Next forward-port

Applies the extraction corrections from `dfa79f3b9e` onto `1d3b05a568`, without
merging unrelated develop features. Keeps next 0.8.prerelease, candidate API 0.7.0,
existing restart-to-apply Mods behavior, scene audio contracts and HUD/security work.
Shared core networking and Time Attack implementation match develop. The API pin
is regenerated from the actual next surface. Develop-only example test fixes are
not introduced into next where those examples do not exist.

- `next` change-based plan against `1d3b05a568` selects all 3,075 ordinary candidates
  and guards. The combined ordinary attempt above is incomplete due to a confirmed
  macOS AWT stall; it is not repeated on this host. Next composition is checked by
  focused tests plus its fresh guards lane, with full-suite coverage still incomplete.
- Next `test-compile` initially found a duplicate `openTitleEntryScene` introduced by
  the three-way merge. Preserving next's existing launcher removed the duplicate;
  subsequent compilation passed. The API generator reports no missing annotations
  or signature leaks; next's actual candidate pin adds 1,505 lines.
- Python category inventory tests: 28 passed. Java 21/Lua 5.4/PowerShell preflight passed.
- Next queued focused selection covered every `com/openggf/net/**/Test*`,
  `openggf/racing/**/Test*` and `openggf/timeattack/**/Test*`, plus
  `TestNetIsolationRules,TestHostedRunController,TestSceneRunLauncher,TestModSceneHostGameplay,TestUnknownConfigPreservation,TestGameLoopFreezeContractWiring,TestMasterTitleHub,TestModTitleEntryRegistration,TestBundledModPackager,TestBundledModBoot,TestModApi*,TestModEngineWiringSeams,TestMasterTitleSecondaryActions,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`.
  With all three verified absolute ROM properties: 693 tests, zero failures, one
  error, zero skips. The host flood test observed an expected reset during sending,
  before reaching its receive-side closure assertion. The test now tolerates that
  specific early socket-close signal while still asserting server closure and
  eventual removal from the room; production behavior is unchanged.
- Focused flood-test repair rerun: `-Dtest=openggf.racing.server.host.TestRaceHostServer test`, **56 passed, zero skips**, covering both Netty and JDK hosts.
- Next fresh `python3 tools/testing/maven_queue.py -Dmse=off -Pguards test -B`:
  676 tests, one failure, zero errors/skips (10m22s). The sole failure is
  `TestBuildToolingGuard.releaseGateToolsShouldRejectCorruptAndChangedEvidence`,
  specifically Python `CollectTests.test_failure_messages_and_report_payloads_have_checkout_paths_normalized`.
  Matched fresh subprocesses loading baseline `1d3b05a568` and candidate collector/test
  modules each reproduced one failure, zero errors/skips: macOS `/var` versus
  `/private/var` temporary checkout paths are not normalized. No unrelated release-tool
  changes are made. Next therefore has **675 passing guards and one inherited failure**,
  not a green guard suite.
- Final next `-DskipTests package`: passed; bundled Time Attack validator reports zero findings. Artifact inspection confirms shared core networking, separate mod/server code, and no Netty/SQLite/Bouncy Castle runtime classes in the engine artifact.
