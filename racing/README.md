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

Open **Extras > Time Attack**. The same mod provides Solo, Host LAN, Join LAN and Browse,
with short-code joining through the configured master. Room creation offers Public or
password-protected Private access. Private rooms are unlisted, and an invite never
bypasses their password. The [configuration guide](../CONFIGURATION.md#time-attack-bundled-mod)
describes creation, invite sharing and offline manual joining.
**R** retries an act by default; **Settings > Retry key** captures another keyboard key.
There are no lives: after a death the animation plays (press jump after half a second to skip
the rest), the act reloads straight from its start without a title card, and once the level is
visible a 3-2-1 countdown holds control before the fresh attempt. Solo and room runs behave the
same; neither the fade-in nor the countdown is part of the attempt's time. In Sonic 3 & Knuckles
this countdown and a room's round-start countdown use Death Egg Zone's tunnel-launcher sounds: a
ready beep as each of 3, 2 and 1 appears and the launch sound on GO. Acts start without
their intro cutscene (Angel Island 1 begins on the beach), so the clock measures the race.
The game's own HUD shows that clock: its TIME counts the attempt (first input to finish), to the
hundredth (`0:00.00`), in each game's own HUD digits. Time Attack lays the native HUD out for
racing (`RunHandle.setHudProfile`): TIME on the top row and RINGS beneath it, with no score or
lives, and its own panel with the best time and the latest split sits directly under them, lined
up with their labels (`LevelOverlayCanvas.hudOriginX`). In a room, the room panel takes the
top-right corner, and a progress bar spans the bottom of the screen: every racer is a segment in
their name colour at their true place, and racers at the same place stack upwards, so a column's
height counts them; yours is outlined in white at the bottom of its column. A column draws at
most five segments (you, then anyone hurt, then by slot) under the number of racers there, so a
big room's pack stays short. A racer's segment
flashes red and shakes while they are hurt and briefly after; other players' hurt state travels
in their ghost frames.
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

Round-end votes offer three acts from the room's vote pool. Each game's default pool is
the curated signpost list. With **Host LAN** or **Browse**, the menu's **Vote pool** row
chooses the default pool or a custom pool with Left/Right; Enter opens the pool editor.
The editor shows the menu's game only, so only games whose ROM is available. NEW POOL and
the default row start from the default pool's acts. Any act that ends in a results tally
can be added: signpost acts and boss/capsule acts, never special or bonus stages (see
`TimeAttackTrackCatalog.eligibleFor`). A pool holds 3-32 acts of one game; 32 is the room
protocol's vote-pool limit, and three leave a choice after any race. `settings.txt` keeps up
to eight pools per game as `votePool.<game>.<n>=<zone>:<act>,...|<name>`, for example
`votePool.s2.1=0:1,1:1,7:2|Boss acts`. The acts are the engine zone/act ints, `n` counts from
1 in creation order, and the name is display text of at most 20 code points after the first
`|`. `votePoolChoice.<game>=<name>` records the chosen pool; if it is absent or names no saved
pool, rooms use the default pool. Unreadable pool lines are dropped. Before creating a room,
the chosen pool is checked against the eligible catalog, including the locked character's
acts. The master checks it again, and either one rejects a bad act with the reason. In S3K the
Lava Reef boss act, Sky Sanctuary and Death Egg 1 are Sonic/Tails acts. When a vote picks an
act the player's character cannot race, that round uses the act's first permitted character.

### The lobby level

In a room, the lobby menu draws over a short level of the room's own game that every player
spawns into (`LobbyAreas`): the Hidden Palace arena where Knuckles fights Sonic for S3K (engine
zone 22 act 1, fenced left of the fight trigger so the fight never starts), the start of Green
Hill 1 for S1 and of Emerald Hill 1 for S2. The fence is a `RunArea`: every load (launch, act
restart, death reload) places the character at its spawn and the engine holds the camera inside
it each frame, after the act's own level events. The lobby run is `GameplayRunPolicy.isolatedAct().withFastRestarts()`
and restarts its act on completion; a death reloads at once at the fence spawn with no lives
counter, game over or countdown. A
room's lobby that cannot load or faults falls back to the menu on its own for that room.

The menu owns the controller while it is shown (`RunHandle.holdControls`). It is a set of cards
in the multiplayer HUD's panel style (`RaceLobbyScreen`): a header with the room, the next track
and whether times are verified; the players with a ready box, HOST/NEW badge and character; the
chat (or, between rounds, the track vote or the last round's podium); and the menu, headed by the
ready rule with who the room is still waiting for. Cards size to their content, so the level
stays in view around them, and the menu covers the level (`RunHandle.setLevelCovered`): the stock
HUD and ghost nameplates are not drawn under it (presentation only; the timer runs). Each
player's identity tag, which tells two players with one name apart, is on the chat and invite
page. **READY UP** marks the player ready (`SetReady`; their ready box fills). Once ready, Start or Enter
on the lobby's own page hides the menu and hands the controller to the character; pressing it
again brings the menu back, like a pause menu. While ready, A (Space) confirms menu actions,
because Start/Enter is the toggle; text editors keep Enter. The run plays behind the still-active
Time Attack scene (`SceneGameplay.launchBehind`), so Start and the pause key never pause it.

The room owner's **READY RULE** is `REQUIRED` (the owner's START ROUND is refused until every
present player is ready), `AUTO` (everyone counts as ready on joining) or the default
`DEFAULT_BY_SIZE` (required with five or fewer players present, `AUTO` with six or more,
re-evaluated as players join and leave; an auto-readied player keeps it unless they unready).
Nothing auto-starts. Ready clears at each round start and in `AUTO` is re-applied at once. The
owner's last choice is `readyRule` in `settings.txt` and is re-sent when they open a room.

Other players appear as named ghosts in their looks. Each client streams its lobby pose between
rounds (`TYPE_LOBBY_FRAMES`, three frames a packet) and the host relays nearby poses every tick
(`TYPE_LOBBY_AGGREGATE`; relay rooms send each recipient its eight nearest senders). Hosts accept
lobby frames only in the lobby, results and vote phases, keep streams forward-only, cap their
rate and per-frame movement, skip congested recipients and close a member after repeated
violations. Ghosts also draw in sprite-masking zones such as Hidden Palace.

Settings edits keep their accepted values when storage refuses or throws on publication.
The settings page reports a pending save, retries every 60 scene ticks, and flushes
pending settings on resume or exit. Imported legacy preferences follow the same retry
path, with complete join addresses discarded. Invites and passwords are never saved
or automatically logged; existing user-authored invite files remain untouched. A
failed exit flush remains unconfirmed; reverting to confirmed values cancels an
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

For Linux servers, [Docker deployment](deploy/README.md) provides separate
master/relay and verifier images, standalone `docker run` commands and an optional
single-host Compose setup. Both deployment styles use the same images, TLS and
file-backed secrets. Verifiers can move to separate machines without changing the
service protocol. Source and packaging remain here because verification shares the
client engine build identity.

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

### Recording a lobby demonstration

`racing-server capture-lobby OUTPUT REPOSITORY_ROOT GAME` (GAME `s1`, `s2` or `s3k`) drives the
same display-free Engine through Extras > Time Attack, hosts a LAN room for GAME, and records its
lobby level: three loopback protocol peers with international names and looks join, ready up and
walk beside the player (replaying the player's own recent poses at an offset, so their animation
is the game's own); the player readies, hides the menu, runs both ways, brings the menu back and
starts the round. Same launch rules as below (fresh owned directory, `DISPLAY`/`WAYLAND_DISPLAY`
unset, bundled mod built). Outputs PNG stills, `lobby.mp4` (with FFmpeg) and `state.csv`
(mode, position, menu state and the received lobby ghosts' positions). The peers' poses are
synthetic.

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
