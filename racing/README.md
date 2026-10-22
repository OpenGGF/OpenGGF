# Racing: the Time Attack mod and the racing server

Time Attack is a mod; reusable networking stays in the engine:

```
src/main/java/com/openggf/net/   shared protocol, room services, clients, identity,
                               JDK room host and ghost codec
racing/time-attack/             Time Attack rules, menus, settings, ghosts and race flow
                               (openggf.timeattack)
racing/server/                  master server, Netty dedicated host, attempt verifier,
                               load-test and track-profile tools (openggf.racing.server)
```

The networking contracts marked `@ModApi` are available to any mod without installing
Time Attack. Internal handshake and WebSocket parsers remain implementation details.
The core dependency fence prevents networking from calling gameplay owners or mod code.
For custom messages and rules, scene mods can use the existing owner-scoped `SceneNetwork`
API (used by Sitar Hero). Putt Putt Paradise's existing direct TCP implementation is
unchanged; it can adopt shared transport separately from Time Attack's racing protocol.

The mod reaches the engine only through the public Mod API: a master-title entry, scene-launched
gameplay runs and the run host (see
[Title entries and gameplay runs](../docs/modding/guides/gameplay-runs.md)). Each run is a stock,
non-saving session with no mod content, so ghost times stay comparable across players.

## Playing and existing data

Open **Extras > Time Attack**. The same mod provides Solo, Host LAN, Join LAN and Browse.
**R** retries an act by default; **Settings > Retry key** captures another keyboard key.
There are no lives: after a death the animation plays (press jump after half a second to skip
the rest), the act reloads straight from its start without a title card, and once the level is
visible a 3-2-1 countdown holds control before the fresh attempt. Solo and room runs behave the
same; neither the fade-in nor the countdown is part of the attempt's time. Acts start without
their intro cutscene (Angel Island 1 begins on the beach), so the clock measures the race.
Best and imported ghosts remain in `ghosts/`, and player identity remains in `identity/`.

A best save validates the ghost and its input recording before publication. The
atomic `.ggfghost` file selects an immutable input sidecar by its recorded hash;
the familiar `.ggfinputs` filename remains a legacy copy. The HUD distinguishes
an unconfirmed save, a preserved rejected primary, and a saved record whose legacy
copy still needs maintenance. This is one atomic publication authority, not a
two-file crash-atomic rename. Existing primary/history pairs remain available
while a new record is staged, and previous valid ghosts can be used for playback
without permitting an overwrite of a corrupt or future primary.

Per-route hash sidecars retain the best and two previous records. Legacy sidecar
lookup visits paths lazily, skips corrupt/oversized files, and limits depth to six,
entries to 4096, legacy sidecars to 512 plus one hash-matching sidecar, and each
input recording to 36,000 frames plus
4096 bytes of metadata. A checked search failure means a requested upload is
unavailable; it does not fault the scene. Ghost clocks and splits must be ordered
within the 36,000-frame recording budget; partial pose streams remain valid.

On first opening, if the mod has no settings file, it imports the old engine's retry,
network and minimap preferences from `config.yaml` or `config.json` and their `.bak` or
numbered backups. Live configuration values take precedence over backup values. It writes
`settings.txt` in the mod's owner storage without changing the source files or replacing
existing mod settings. Configuration files are limited to 1 MiB for this import.

A new player (no saved name and no `identity/player-identity.key`) chooses a display name
before any mode starts; Escape returns to the master title instead of skipping. Names may use
any left-to-right script, typed through the platform's keyboard layout or input method, and are
stored canonical (`DisplayNames`: NFC, single spaces, at most 20 code points and 64 UTF-8
bytes). Profiles that already have an identity but no name keep the identity-prefix name. The
settings page and the lobby's PROFILE action rename the player and edit their look without a
new identity; the lobby announces the change with `UpdateProfile` while the room is in its
lobby phase. Addresses, ports and URLs stay ASCII; chat accepts visible text in any script.

`settings.txt` keys for the look: `lookBase` is the base colour (three hex digits `BGR`, the
Mega Drive colour word; empty for the stock "Classic" look) used for the player's name and
ghost. `lookPalette.<game>:<character>` (for example `lookPalette.s3k:tails=2EEE.900E`) holds a
custom palette as `.`-separated `IBGR` entries of that character's 16-colour line; without one
the body ramp follows the base colour. Unreadable look values fall back to the stock look or
the preset ramp. The canonical look sent on join comes from
`TimeAttackSettings.appearanceFor(game, character)`.

Settings edits and successful LAN joins keep their accepted values when storage refuses or throws
on publication. The settings page reports a pending save, retries every 60 scene ticks, and flushes
pending settings and LAN invite text on resume or exit. Imported legacy values follow the same
retry path. A failed exit flush remains unconfirmed; reverting to confirmed values cancels an
obsolete retry.

Finished attempts retain at most 128 input recordings and 2 MiB of encoded bodies in memory.
Each body has the existing 36,000-frame/4096-byte metadata limit. Unstamped records remain until
explicit round end, then receive the ten-minute upload grace period. Duplicate bodies preserve
that expiry; conflicting bodies or exhausted capacity are explicitly refused before copying.
The uploader admits eight jobs including its active request, deduplicates pending requests and
its last sixteen successful URLs, and rejects conflicting bodies for the same URL. Each uploader
keeps its original session token. Closing resolves all accepted callbacks once and prevents queued
work from sending or retrying; duplicate/rejected admissions own no callback.

## Build and test

Use a Java 25 JDK for the engine, mod and server. Set `JAVA_HOME` to that JDK
when another Java version is on `PATH`. The generated server launcher uses the
selected JVM and declares native access for SQLite and engine verification.

Both projects compile and run their tests with the engine's test classpath, so the ordinary
suite covers them along with the shared networking tests:

```bash
python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=com/openggf/net/**/Test*,openggf/racing/**/Test*,openggf/timeattack/**/Test*" test
```

`python3 tools/testing/maven_queue.py -Dmse=off package` packages `racing/time-attack` as the bundled mod (`openggf.bundled.mods` in
`pom.xml`) through `ggfmod package` with warnings as errors; `TestTimeAttackModPackage` checks the
same thing in the suite. Native builds do not load code mods, so Time Attack is JVM-only.

## Running the server tools

```bash
python3 racing/server/build.py
target/racing-server/racing-server master --config master.yaml
target/racing-server/racing-server verifier --master https://host:27900 --registration-token <token> --rom s3k.gen --data ./verifier-data
target/racing-server/racing-server load-test --n 256 --duration 30 --mix adversarial
target/racing-server/racing-server socket-stress --transport relay --players 4 --duration 1 --mix mixed
```

The verifier embeds the engine to replay attempts, so build it from the same engine commit as the
clients it verifies. Master settings are documented in
[CONFIGURATION.md](../CONFIGURATION.md#racing-server-master-verifier-settings).

Anonymous creation-proof reconnect stamps share the configured new-identity cache
size and TTL limits (`newIdentityCacheSize`, `newIdentityCacheTtlMinutes`). Reconnects
refresh LRU order but cannot extend the proof's expiry. Pending room attachments
retain at most five room grants per master connection for 30 seconds after each
successful join, including the relay creator's initial grant. Attach consumes the
grant once; an expired or evicted grant requires a fresh rate-limited join.

## Adversarial and capacity testing

The ordinary tests combine decoder fuzzing with stateful room/broker attacks and
real transport lifecycle tests. `TestRoomHostAdversarial` applies a reproducible
corpus at connection, challenge, lobby, countdown, active/reset/finished attempt,
round-end, vote and disconnected boundaries, retaining a healthy witness.
`TestRoomBrokerAdversarial` covers master admission, proof, credential and tunnel
authority. `TestMasterTransportLifecycle` holds executor boundaries to reproduce
disconnect/handoff races and checks actual socket admission and shutdown.
Failure context identifies the seed, state and operation. Existing TLS binding,
HTTP admission, slow-consumer, client-timeout and parser-fuzz tests complement these
cases; this is bounded coverage, not proof against every possible attack.

`TestGuestTunnelBounds` covers fallback text and binary in both directions against
the receiver's reported outbound queue: above 1 MiB, the affected guest closes and
its tunnel is removed with one host notification; exactly 1 MiB still forwards.
The host's shared master link and room registrations survive, including unrelated
tunnels and replacement admission after the queue drains. This telemetry check
does not bound cross-event-loop tasks, lifecycle notices or global socket scale;
see the [network contract](../docs/architecture/next-line-subsystems.md).

`socket-stress` runs production clients and servers on owned local endpoints. It
checks simultaneous room admission, a rejected extra player, identity/slot isolation,
ghost delivery, accepted finishes and standings, reconnection and server shutdown.
`--mix mixed` varies idle starts and burst delivery; the report distinguishes expected
rejections from unexpected failures. Inputs are synthetic cosmetic ghost poses,
not engine gameplay or verified input recordings.

The supported maximum is **256 players in a relay room** and **8 in a direct room**.
Select `--transport relay`, `jdk` or `netty`. The real four-connections-per-IP rule
stays enabled: an owned byte-transparent proxy binds separate `127/8` source
addresses. This requires OS support for those loopback addresses and fails explicitly
when unavailable. There is no external target option. Relay capacity identities are
established before measurement, because new identities intentionally face a stricter
high-occupancy admission gate. This measures one room, not an unlimited number of
master connections or rooms.

Build before running a measurement. On Linux, the existing isolated measurement
runner excludes linked-worktree tests and wrapped builds:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 racing/server/build.py
python3 tools/performance/run_isolated.py --estimate-seconds 180 \
  --audit-report target/racing-stress-audit.json -- \
  target/racing-server/racing-server socket-stress \
  --transport relay --players 256 --duration 10 --cycles 1 --seed 4675398 \
  --mix mixed --report target/racing-stress.json
```

Use `--transport jdk --players 8` and `--transport netty --players 8` for direct
capacity. `--cycles` repeats complete setup, traffic, recovery and teardown for
lifecycle soak coverage. `--timeout` bounds individual network waits; it is separate
from `--duration`, which controls the paced traffic. Unknown/duplicate options and
out-of-range configurations fail. A failed contract returns a nonzero CLI exit and
includes bounded failure details in the report.

Read the work and error counts before interpreting timing. Ping percentiles are
end-to-end through the local source proxy, using a bounded sample reservoir.
CPU, sampled used heap and GC belong to the **whole colocated JVM**, including clients,
proxy and server. Control-only warmup does not establish steady-state ghost throughput;
startup/JIT and the load generator can affect the measured interval. Compare repeated
equivalent runs and report variation before claiming a performance change. Normal
correctness tests do not use absolute latency thresholds. Socket and helper cleanup
is observed; JDK-managed HTTP-client selector/executor reclamation is not a separate
thread-leak certification.

The older `load-test` remains an in-process diagnostic: it times only hub ticks and
reports the largest emitted packet, not transport queue depth. It cannot establish
socket throughput. The [design and delivery record](../docs/architecture/plans/2026-10-21-racing-stress-system.md)
records the architecture, validation and remaining coverage limits.

### Recording a capacity demonstration

`racing-server capture-video OUTPUT REPOSITORY_ROOT 256 [WINDOW_SECONDS] [RECORDED_ROUTE]` records
the actual Engine's fresh-start legal screen, master title, Extras, Time Attack,
browser, full relay lobby, countdown and natural Green Hill Act 1 finish. Build
the engine and its bundled Time Attack mod before the server CLI; set
`-Dopenggf.installDir` to the engine install directory containing `bundled/` when
running from class directories. FFmpeg and Linux surfaceless EGL are required.
Launch from a fresh owned directory outside the repository, with `DISPLAY` and
`WAYLAND_DISPLAY` unset. The supplied repository root provides the original S1
World REV01 ROM and maintained `ghz1_fullrun.bk2` controller input (offset 840).

The visible player runs the real ROM-backed engine from its ordinary start. The
other 255 authenticated protocol clients retime poses from a separate fresh
ROM-backed playback of the same controller movie, with paired faster/slower
timelines and smooth surges. The visible player finishes near the middle, while
ghosts enter, leave and return to its viewport. Their finish claims remain
synthetic and unverified; this does not run 256 engine simulations. The route
export subprocess uses no-device audio and never supplies state to gameplay. Established admission identities and a low-cost creation
proof are seeded only for this loopback demonstration; the normal per-address and
256-player limits remain enabled. The production renderer still limits visible
nearby ghosts. The tool requires natural player completion and every accepted
standings row, a middle finish, and actual rendered-ghost/viewport churn, then
verifies owned socket/proxy shutdown. The compact multiplayer HUD reserves the
stock score/time/rings and lives areas, shows only three nearby rows, and keeps
completion panels clear of the act card.

Outputs are `gameplay-silent.mp4` (30 fps), stereo `audio.pcm`, `chapters.csv`,
`state.csv` (including rendered and viewport-eligible ghost transitions), stills
and `result.txt` (including the audio rate, final rank and churn totals). Mux the PCM as
signed 16-bit little-endian stereo at that rate with the MP4 using FFmpeg. The
capture includes rendering, encoding and client work, and is not a benchmark.
