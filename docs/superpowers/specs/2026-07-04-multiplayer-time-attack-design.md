# Multiplayer Time Attack & Ghost Racing — Design

**Date:** 2026-07-04
**Status:** Approved design, pre-implementation
**Scale target:** rooms of tens to hundreds of players (256 is the tested design point), treated as the standard case — not an edge case.

## 1. Overview

Two stacked features built on the engine's deterministic simulation and the existing
user-recording/playback stack:

1. **Solo ghost racing** — race a translucent ghost of your own best run through a
   zone/act with a live delta timer.
2. **Multiplayer time attack** (Trackmania-style) — players join a room via a server
   browser, everyone runs the same track simultaneously with unlimited instant
   retries inside a timed window, best time wins. Other players are visible as
   non-colliding ghosts streamed in near-real-time.

The defining architectural property: **each client simulates and times only its own
player, locally, in deterministic frame counts.** The network carries only cosmetic
ghost streams and event messages. There is no physics synchronization between
machines, therefore no lockstep, rollback, prediction, or input delay — and network
latency can never affect a recorded time or race outcome.

## 2. Decisions (from brainstorming)

| Decision | Choice |
|---|---|
| Connectivity | Live lobby, ghost-only (no collision between players) |
| Discovery | Master server: server browser + lobby system |
| Master server role | Directory + relay. Player-hosted direct-connect only for small rooms (≤8); larger rooms are always relay-routed |
| Race format | Trackmania rounds: host sets a time window on a track, unlimited instant retries, best time in window counts |
| Track definition (v1) | Full act: spawn → end-of-act signpost; boss/capsule acts end at the arena camera-lock trigger or are excluded from the v1 track list |
| Characters | Host picks per-room policy: locked (everyone plays the host-chosen character) or open (any character; standings badge the character) |
| Transport | WebSocket (TCP) everywhere — lobby, browser, relay, ghost streams. Binary frames for ghost data, JSON text frames for control |
| Anti-cheat (v1) | Hooks only: rewind/debug/editor disabled during timed attempts; `AttemptFinish` reserves an `inputRecordingRef` field so times are verifiable by deterministic replay later. No verification in v1 |
| Solo mode | First-class phase-1 feature (§3): `.ggfghost` files, star-post splits, multi-ghost racing including imported files |

## 3. Solo ghost racing (phase 1, and the foundation)

Solo mode is not a demo of the multiplayer feature — it is the strategy for
building it. It ships first, exercises the entire ghost pipeline (format,
persistence, playback, rendering, timing) with zero network code, and remains a
permanent mode in its own right.

- **Entry:** the same master-title "Time Attack" menu, with "Solo" alongside the
  server browser. Identical track picker (data-select zone/act grid); character
  is freely chosen.
- **Ghost files:** best runs persist as `.ggfghost` files under the save
  directory, keyed by (game, zone/act, character). Format: a small header
  (format version, game, zone/act, character, display name, total frames, final
  time, split times) followed by **the same quantized 7-byte 60 Hz frame stream
  used on the wire** (§7). One canonical encoding — a ghost file's body and a
  network `GhostFrames` payload are byte-identical per frame, so solo mode
  validates the exact bytes multiplayer ships. No ROM-derived data is stored,
  so ghost files are freely shareable.
- **Delta timer and splits:** star posts are the split markers (they already
  exist as objects with fixed track positions). The HUD shows the delta vs the
  racing ghost at each star post and at the finish; between splits, the ghost
  itself is the live indicator. Split times are recorded in the header for
  standings-style comparison without replaying the file.
- **Multi-ghost racing:** race up to the renderer cap (8) simultaneously — your
  best run plus imported `.ggfghost` files (picked via menu from a ghosts
  folder). Trading ghost files is a zero-infrastructure multiplayer mode for
  free, and multi-ghost solo racing is the offline test harness for the
  multiplayer rendering path.
- **Improvement flow:** finishing faster than your stored best auto-saves the
  new best (previous bests retained, last 3). Instant retry works exactly as in
  multiplayer rounds.
- **Anti-cheat hooks apply:** an attempt that used rewind, debug tools, or the
  editor is never saved as a best.

## 4. Scale model — why hundreds of players is cheap here

Ghost-only time attack degrades gracefully because every stream is cosmetic. The
naive star topology (hub forwards every packet to every peer) is O(N²) in packets
and bandwidth and dies around N≈16 on residential upload. Three mechanisms fix it:

### 4.1 Aggregation — one packet per tick per recipient

The hub (player-host or relay) never forwards ghost packets. It ingests upstream
frames into per-player ring buffers and, at 20 Hz, composes **one `GhostAggregate`
binary packet per recipient** containing all relevant ghosts' frames. Each client's
edge is O(1): one stream up, one aggregate down, ~20 packets/sec each way regardless
of room size. Per-packet overhead (WS framing, TCP/IP headers, syscalls) is
amortized across all ghosts in the room.

### 4.2 Relevance filtering — full fidelity only for visible ghosts

Per recipient, the hub classifies every other player:

- **Near** (within ~1.5 screens, exit hysteresis at ~2.5 screens to prevent
  flapping): full 60 Hz frames in the aggregate, capped at the **nearest 8**.
- **Far**: excluded from the aggregate; represented only in the roster (below).

Classification runs per hub tick (20 Hz) using **spatial bucketing** (players
hashed into 512-px buckets along x, y-checked within candidate buckets), so the
cost is O(N + near-pairs), not O(N²) distance checks. At 256 players this is
thousands of cheap operations per tick.

### 4.3 Roster channel — coarse state for everyone else

A separate `Roster` packet at 1 Hz carries every player's coarse state: position
quantized to 64-px cells (2 bytes), attempt status (1 byte), ~4 bytes/player.
256 players ≈ 1 KB/s. This feeds the standings panel, "lagging" indicators, and a
future minimap without per-ghost streams.

### 4.4 Budget at the design point (256-player relay room)

Ghost frame: x,y 16-bit each + animId + animFrame + facing/status flags = **7 bytes**.
Upstream: 60 Hz sampled, batched 3 frames/packet at 20 pkt/s ≈ **1.5 KB/s per client**.

| Path | Rate |
|---|---|
| Hub ingress | 256 × 1.5 KB/s ≈ 0.4 MB/s |
| Per-recipient egress | 8 near ghosts (3.4 KB/s) + roster (1 KB/s) + standings deltas ≈ **~6 KB/s** |
| Hub egress total | 256 × 6 KB/s ≈ 1.5 MB/s (~12 Mbit/s) per full room |
| Hub packet rate | ~5 k pkt/s each way |

One 256-player room costs ~12 Mbit/s of VPS egress; a small Netty process hosts
many rooms concurrently (rooms pinned to event-loop threads, no cross-room state).
Player-hosted rooms are capped at 8, where even naive budgets fit residential
upload; the ≤8 cap means player-hosts skip relevance filtering entirely
(everyone is "near").

### 4.5 Backpressure

Per-connection outbound queue depth drives a degradation ladder:
queue > 64 KB → near-cap drops to 4, roster to 0.5 Hz; > 256 KB → roster only;
> 1 MB or sustained 30 s → disconnect with a reason message. A slow client only
ever degrades its own view.

### 4.6 Client-side rendering at scale

The renderer draws at most the **nearest 8 ghosts** (configurable), opacity faded
by distance, nameplates on the nearest 4. Everyone else exists only as roster
entries in the standings panel. Playback bookkeeping for hundreds of roster
entries is trivial; full playback cursors exist only for near ghosts.

## 5. Latency model

1. **Frame-indexed streams, not timestamps.** Every ghost snapshot carries
   `frameIndex` = frames since attempt start (60 Hz). No wall-clock sync is needed
   for playback.
2. **Adaptive jitter buffer.** Each near ghost renders at delay D behind its newest
   received frame — initial ~150 ms (9 frames), adapting per-peer: grow fast on
   stalls, shrink slowly (voice-chat style). Frames arrive at full 60 Hz fidelity,
   so playback is exact, not interpolated.
3. **Stall handling.** Buffer dry → extrapolate up to ~100 ms with last velocity,
   then freeze and fade the ghost to half opacity with a lag marker.
4. **Catch-up (TCP burst) policy.** Backlog > D + 250 ms → play the stream at 2×
   until the cursor returns to D. Backlog > ~1 s → snap to `newest − D` with a
   fade-out/in. Never play backlog at 1× or in slow motion.
5. **Round deadline.** The only wall-clock-sensitive moment. NTP-lite offset
   estimation on join (5 ping samples, median), host/relay broadcasts the deadline
   in hub-clock time. Hard cutoff: an attempt still running at the deadline is
   void (v1). Clock error only affects whether a last-second start squeaks in —
   never a recorded time.
6. **Events are reliable messages.** `AttemptFinish` etc. ride the ordered TCP
   stream; a standings update arriving 200 ms late is imperceptible.

## 6. Architecture

### 6.1 Engine side — new `com.openggf.net` package

| Class | Responsibility |
|---|---|
| `RaceClient` | Connection lifecycle (master, host, or relay), message pump on a dedicated network thread, single thread-safe queue drained once per frame by the game loop. All game-state mutation stays on the game thread |
| `GhostStreamPublisher` | Samples the local player each frame during an attempt (reusing the user-recording capture path), quantizes, batches 3 frames/packet, sends |
| `RemoteGhostRegistry` / `RemoteGhostPlayback` | Per-near-ghost jitter buffer, catch-up policy, playback cursor; roster state for far players |
| `GhostRenderer` | Draws near ghosts + the solo best-run ghost through the existing player sprite render path at reduced opacity with nameplates. Ghosts never enter `ObjectManager` — they cannot touch gameplay state by construction |
| `RaceSession` | Room/round state machine: lobby → countdown → window open → podium → track vote. Standings cache, attempt lifecycle (armed → running → finished/reset/void). Owns instant-retry via the existing session/level restart choreography (purely local; peers just see the ghost blink back to the start) |
| `TimeAttackController` | Timer starts on first input, stops on the finish trigger (signpost / arena lock), frame-count authoritative. Disables rewind, debug overlay, and editor entry for the duration of a timed attempt |
| `GhostHub` | The aggregation/relevance/roster engine of §4. **Shared verbatim between the player-host path and the master server relay** — hosting mode only changes who runs it |

### 6.2 Master server — `com.openggf.net.master` (same artifact, no engine deps)

A separate main class (`MasterServerMain`) in the existing jar, tools-style — no
LWJGL/engine imports, enforced by an ArchUnit rule freezing `com.openggf.net.protocol`,
`net.hub`, and `net.master` against engine packages. Netty-based (added as a
dependency), single process:

- **Session registry:** hosts announce rooms via heartbeat (name, game, zone/act,
  character policy, player count, routing mode); stale rooms expire.
- **Server browser API:** list/filter rooms.
- **Lobby:** room membership, chat (server-side rate limit ~1 msg/2 s/player).
- **Relay:** rooms with capacity > 8 are relay-routed — the room's `GhostHub` runs
  on the master; each room pinned to one Netty event-loop thread.

Multi-module Maven extraction is deliberately deferred; the ArchUnit fence gives
the isolation now, the module split is mechanical later if a slim server jar is
wanted.

### 6.3 UI

Master title → "Time Attack" → server browser → lobby → in-round. Track picker
reuses the data-select zone/act grid. In-round HUD adds the standings panel
(top 10 + 5 rows around you + your rank — never the full list pushed to
hundreds of clients; full list is paged on demand) and the solo/best delta timer.
Podium shows top 3 + your rank; next-track vote offers 3 options.

## 7. Protocol

Versioned; JSON text frames for control, compact binary frames for ghost data.
Rooms advertise the required game/ROM; clients without a matching detected ROM
cannot join. **No ROM-derived bytes ever cross the wire** — only positions,
animation ids, times, and chat.

Control: `Hello/Welcome` (version gate), `RoomCreate`, `RoomList`, `RoomJoin`,
`RoomLeave`, `Chat`, `RoundConfig`, `RoundStart`, `RoundEnd`, `AttemptStart`,
`AttemptFinish` (frame-count time + reserved `inputRecordingRef`), `AttemptReset`,
`StandingsDelta`, `TrackVote`, `Ping/Pong`.

Binary: `GhostFrames` (client → hub, 3 × 7-byte frames + attemptId/frameIndex
header), `GhostAggregate` (hub → client, all near ghosts' frames for one tick),
`Roster` (hub → client, 1 Hz coarse state for all players).

Stale-attempt frames (old `attemptId`) are dropped silently at every layer.

## 8. Data flow (in-round)

Local player simulated exactly as today → `GhostStreamPublisher` samples
post-update → upstream. Network thread enqueues inbound → drained at frame start →
`RemoteGhostPlayback` advances cursors (catch-up policy applied) →
`GhostRenderer` draws during the sprite pass. Hub side: ingress → per-player ring
buffers → 20 Hz tick: spatial re-bucket, per-recipient near-set (hysteresis, cap,
backpressure ladder) → compose one `GhostAggregate` per recipient; 1 Hz roster.

## 9. Error handling

- Peer disconnect → ghost fades out, standings row greys out, best time kept for
  the round.
- Host disconnect (player-hosted) → room dissolves, toast, return to browser.
- Relay room: master restart drops rooms (acceptable v1; rooms are ephemeral).
- Direct-connect timeout (3 s) → automatic relay fallback for that pair.
- Malformed packets / version mismatch → rejected at `Hello` or dropped + logged.
- Slow consumer → §4.5 ladder, never affects other clients.

## 10. Testing

- **Unit (headless):** `RemoteGhostPlayback` jitter buffer, catch-up, and snap
  thresholds driven by synthetic packet traces with injected stalls/bursts;
  `GhostHub` relevance classification, hysteresis, aggregation framing, and
  backpressure ladder; `RaceSession` state machine; `TimeAttackController` timing
  against existing trace replays (a trace is a perfect deterministic opponent).
- **Integration:** loopback test running master + host + two clients in one JVM;
  a `LatencyProxy` test double injecting delay/jitter/bursts between them.
- **Load:** `com.openggf.tools.net.GhostLoadTestTool` — spawns N headless bot
  clients replaying recorded ghost files against a room; asserts hub tick budget,
  queue depths, and egress at N = 32 / 128 / 256. This is the gate for the scale
  target, runnable in CI at reduced N and on demand at full N.
- **Solo mode first:** phase 1 exercises the entire ghost format + renderer with
  zero sockets.

## 11. Phases

1. **Solo ghost racing** (§3) — `.ggfghost` format (shared with the network
   path), `GhostRenderer`, star-post split deltas, best-run persistence and
   improvement flow, multi-ghost racing with file import.
2. **Race session + direct connect** — `RaceSession`, `TimeAttackController`,
   `GhostHub` (trivially degenerate at ≤8 players), host/join by address,
   LAN-testable end to end.
3. **Master server** — browser, lobby, relay routing for rooms > 8, spatial
   bucketing + roster + backpressure at full scale, `GhostLoadTestTool` gate at 256.
4. **Polish** — podium, track vote, open-character standings badges, spectate
   pan after finishing, minimap fed by the roster channel.

## 12. Out of scope (recorded for later)

- Collision netplay (would require lockstep/rollback over the deterministic core).
- Ranked/persistent leaderboards and replay-verification service (the
  `inputRecordingRef` hook plus determinism makes times verifiable by replay when
  this arrives).
- Custom track segments via the level editor (track abstraction is already
  (level, spawn, finish-trigger) shaped; the editor can emit segments later
  without protocol changes).
- Account system / identity beyond per-session display names.
