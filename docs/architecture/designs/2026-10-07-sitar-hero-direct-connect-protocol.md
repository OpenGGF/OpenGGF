# Sitar Hero direct-connect protocol

## Scope and origin

This is the Sitar Hero sample's application protocol over the existing
[`ScenePeer`](../../../src/main/java/com/openggf/mods/scene/ScenePeer.java)
ordered text transport. It changes no engine or Mod API signatures. The host
chooses a shared-ROM song, available role, difficulty, and coop/versus mode;
each player prepares and judges locally. Remote score, hits, misses, rock,
position, and finished status are bounded reports for online presentation.
They never hydrate a `RhythmSession`, supply music/assets, or authorize a
solo/career record. ROM loading, scene presentation, and record exclusion
remain the parent scene's responsibilities.

Base: `f71d88a3fe30fa9739b900e90f0809e2b7fe9350`. Starting source was the exact
uncommitted `OnlineMatch.java` in `.worktrees/ai-sitar-hero-full`, SHA-256
`2cbd5aac41d82362c4f1ea5edd011a5a1ec7c91a163dc7e56e90c6f2a742e74d`.
Implementation branch: `feature/ai-sitar-hero-protocol`, created in
`.worktrees/ai-sitar-hero-protocol` using the cowtree helper and clean source
`.worktrees/ai-sitar-hero-full-s1`. The uncommitted prototype has no Git commit
identity; this digest identifies the rejected starting implementation.

## Wire and state ownership

Every packet is ASCII, single-space delimited, prefixed `SH1`, and at most
1024 characters. Field count, enums, numeric grammar/ranges, and sender role
are checked. Invalid packets stop and close the match with a bounded reason.
ScenePeer itself owns strict UTF-8 framing, asynchronous connection deadlines,
256-message backpressure, and socket cancellation; this sample opens no sockets
or workers itself. At this base the production implementation is
`ManagedSceneNetwork`; there is no `SceneNetworkFactory` class.

The host increments rounds consecutively, up to 1,000,000. Only host `OFFER`
introduces a round. An offer must identify a catalogue song from the shared
game intersection and one of its actual available roles. Guest offers, future
round traffic, conflicting duplicates, and unsupported parts fail before
preparation. Well-formed obsolete-round READY, PING, PONG, START, STARTACK,
PAUSE, RESUME, HOLD, HOLDACK, and STATE messages have no effect. Reset clears
readiness, count-in, clock probes, controls, terminal results, and remote
position; it preserves the established shared library.

| Packet after `SH1` | Fields | Purpose |
| --- | --- | --- |
| `HELLO` | comma-separated game IDs | Nonempty, unique subset of `s1,s2,s3k`; exact duplicates allowed. |
| `HEARTBEAT` | none | Local receive observation proves connection activity. |
| `OFFER` | round song role difficulty `coop`/`versus` | Host-owned selection and rematch. Exact duplicates are idempotent. |
| `READY` | round rate chart-hash | Locally prepared chart, 8,000–192,000 samples/sec. Exact duplicates do not renew deadlines. |
| `PING` | round host-send-time | One outstanding clock probe; obsolete replies are ignored. |
| `PONG` | round echoed-send guest-receive guest-dispatch | Transport observation timestamps separate network delay from scene polling. |
| `START` | round host-deadline offset | Agreed future count-in, converted into the guest clock. |
| `STARTACK` | round echoed-host-deadline | Host requires acknowledgment before exposing `startDue()`. |
| `PAUSE` / `RESUME` | round guest-intent-sequence | Guest sets/releases its own pause intent. |
| `HOLD` | round host-control-sequence host-deadline `P`/`R` acknowledged-guest-intent | Host assigns the effective pause transition and acknowledges guest intent. |
| `HOLDACK` | round host-control-sequence | Acknowledges the current effective control. |
| `STATE` | round sequence position score hits misses rock finished note-count | Monotonic judgment counters; display-only progress and terminal result. |

Control and telemetry sequences are bounded by 1,000,000,000. A natural song
needs only thousands of messages. Local sequence exhaustion requires reconnect,
without wrapping identity. The fingerprint covers chart length, beat clock,
note count, and every note's onset/end/lanes/HOPO/phrase/sustain ticks using the
sample's 64-bit FNV-style hash. It detects accidental build/chart differences;
it is neither authentication nor proof of identical ROM bytes. Game IDs come
from the scene's ROM library; the protocol transfers no ROM or audio content.
The unpublished starting prototype's packet grammar is not a compatibility
contract; both players must use the matching sample build.

## Count-in and clocks

Three valid NTP-style probes choose the minimum network RTT sample:
`RTT = (t4 - t1) - (t3 - t2)` and
`offset = ((t2 - t1) + (t3 - t4)) / 2`, with checked differences and an
integer average that does not overflow or lose matching odd remainders.
`t2` and `t4` use `Message.receivedNanos()`, never the later polling time.
Dispatch uses the scene tick's local clock, advanced to at least any receive
observation sampled after the tick began. Signed and zero clock values are
valid; explicit state flags replace zero timestamp sentinels.

Negative RTT/processing and overflowing timestamps are malformed. Samples
above two seconds of network RTT are discarded. A probe retries after three
seconds; the overall clock exchange stops after 15 seconds. Preparation starts
this deadline only once, even with duplicate READY. Rate/hash mismatch stops
before count-in on either peer that observes both preparations.

Host lead is at least one second, or twice the best RTT plus half a second.
START retries every 250 ms until acknowledgment. The guest validates its local
start deadline (no past start; at most ten seconds ahead) and acknowledges
identical duplicates without restarting. The host fails if acknowledgment has
not arrived by the deadline. `started()` is legal exactly when `startDue()` is
true; valid peer traffic can precede the receiver's estimated due edge or its scene callback.

Offset uncertainty can put the two estimated due edges apart. If a congested
one-way path clears after calibration, valid control or telemetry may arrive
before the receiver's estimated due edge. A new paired regression reproduced a
fatal rejection with the otherwise working implementation, in both directions.
Requiring the receiver's `now >= startAt` was rejected: packet receipt is not
proof that the sender has not started. Matching acknowledged schedule and round
identify valid traffic; reports remain display-only. Host control deadlines use
at least `startAt` as their base, and guest HOLD deadlines cannot precede its
agreed start. Receiving traffic never calls `started()` or advances `startDue()`.
The final 20-check run covers these edges as well as the original callback race.

This schedules one common count-in against local monotonic observations. It
does not compensate continuous sound-device drift, establish simultaneous
sample output under arbitrary scene stalls, or guarantee atomic start during a
connection failure. The scene must tick, poll `startDue()`, call `started()` once,
and resume its already prepared local player. Loopback protocol tests verify
this handoff, not audible hardware phase alignment.

## Pause, completion, and rematch

Each player owns a pause intent. Effective pause is their logical OR. Resume
releases only the caller's intent, so one player's resume cannot release the
other player's pause. This is the essential scene contract change from the
prototype; public method names are unchanged. Calls can replace a pending
intent before its scheduled deadline. The host sequences effective changes,
allowing a later resume to supersede a pending pause. Obsolete controls cannot
reapply an earlier state. Identical retries retain their original deadline and
are acknowledged again, including after application.

Host control lead is at least 500 ms, or best RTT plus 250 ms. New controls may
arrive up to five seconds late and apply on the current tick, providing bounded
recovery from polling delay. Both guest intents and unacknowledged host controls
retry every 250 ms and fail after five seconds. Independent host/guest pause
intent, intent acknowledgment, and effective-control acknowledgment prevent
crossed requests from being discarded behind a pending transition.

`progress(..., true)` releases the finishing player's own pause intent. A host
also releases the remote intent on the terminal STATE report. Finished players
cannot issue new pause requests; stale valid requests from them are ignored.
This keeps a pending pause from stranding a remaining performer. It does not
change the remaining player's judgments or decide their result.

STATE is accepted only after matching preparation and an acknowledged count-in schedule.
Prepared chart length replaces the rejected prototype's fixed 92-second cap:
charts support up to 600 seconds, with ten seconds of bounded lead-in/tail.
The sample's final late judgment window fits in that tail. Per-round note count,
score (0–1 billion), finite rock (0–1), and cumulative hits/misses are bounded.
Newer states cannot decrease score/hits/misses; duplicate/older sequences cannot
replace progress. Finished results remain terminal until a new host offer.
The parent must keep online sessions out of solo/career storage.

Application deadlines are ten seconds for HELLO, 30 seconds without a fresh
receive observation, and 120 seconds for both song preparations. Old queued
heartbeats do not refresh the observation time. Heartbeats cannot renew
preparation, clock, count-in, or control deadlines. Transport failure/EOF or a
rejected send closes the match. Recovery is an explicit new connection, not
silent reconnection or gameplay state transfer.

## Verification and rejected approaches

Behavior checks compile the actual model/net source, not a second implementation.
`TestSitarHeroOnlineMatch` runs the checks through a Java 21 compiler/class loader.
Synthetic chart fixtures exercise protocol clocks and judgments only; they are
not music, ROM data, or runtime fallback assets.

The original source failed five direct behavior checks: ten-minute progress,
obsolete-round rematch traffic, unsupported local roles, resume racing a pending
pause, and one owner's resume releasing another's pause. These results reject
retaining the fixed time cap, treating round mismatch as fatal, trusting arbitrary
roles, dropping controls while one is pending, and using a single ownerless pause.
No fitted clock offset or remote judgment hydration was attempted.

Coverage includes actual paired sessions with independent hits/misses; ten-minute
sample positions and natural completion; bounded tail; shared-library and part
validation; rate/hash mismatch; malformed packets and overflowing/future rounds;
receive-versus-poll clocks; asymmetric samples/minimum RTT; signed/zero clocks;
start acknowledgment and duplicates; the started-callback race; shrinking asymmetric latency at the count-in edges; crossed owners;
pending pause/resume replacement; deterministic bursts of crossed requests; retried controls; terminal intent release;
monotonic telemetry; handshake/readiness/clock/control/idle deadlines; disconnect
and send backpressure. Real loopback uses two production ManagedSceneNetwork
instances for readiness, count-in, pause/resume, ten-minute-position completion,
rematch, and EOF.

The ten-minute test advances sample time; it does not wait ten wall-clock minutes
or play ROM audio. No display, physical controllers, remote host, firewall/NAT,
or sound hardware is involved. A production loopback run takes approximately
three to five seconds, including two count-ins and pause/resume deadlines.

The change-based dry plan at base `f71d88a3f` selected all 3,012 ordinary classes
plus guards because example net paths are unclassified. This implementation
subtask uses focused validation as instructed: parent owns the combined broad
run and integration. Engine code/API, shared catalogue/curator, scene/UI, and
selection policy are unchanged. Tool preflight initially selected the host's
older default Lua; `LUA_BIN=/usr/bin/lua5.4` passes Java 21, Lua 5.4, and PowerShell
preflight. No broad tests were launched here.

Final focused Maven commands and completed-run outcomes are recorded below. Maven work was queued without altering another run or lock.

The reproduction block uses a neutral ROM-root variable; actual invocations
passed the absolute discovered main-workspace paths.

```bash
sitar_rom_root=/path/to/OpenGGF
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestSitarHeroOnlineMatch test -B
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestSitarHeroControls,TestSitarHeroModel,TestManagedSceneNetwork' \
  "-Dsonic1.rom.path=$sitar_rom_root/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$sitar_rom_root/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$sitar_rom_root/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  test -B
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base f71d88a3f --preflight
```

The ROM properties reference discovered absolute root files; no assets or ROM
links were created by the tests. These selected protocol/model/control/transport
checks do not require a ROM. The new protocol bridge compiles and runs 20 checks;
a separate direct Java 21 run of those 20 checks passed on the same frozen source,
including the actual production loopback session. Completed Maven outcomes appear below. The parent confirmed that `started()` is
already called only inside `startDue()` and that canceling guest preparation
before READY can confirm/reprepare the same offered round. The protocol leaves
local readiness unset until `ready(chart, rate)`, which supports that retry.

The first protocol Maven run completed successfully: 20 tests, no failures,
errors, or skips (3.831 seconds in the test class; 1:15 total including the
fresh engine/test compilation). The separate model/transport/package run passed
10 model checks and 16 transport checks with no skips. Its controls class did
not execute its five cases: example packaging rejected an added static
`GAME_IDS` List with `STATIC_STATE_UNSUPPORTED` for both the field and class
initializer. The creator policy permits only literal primitive/String static
constants. That static-list approach was removed in favor of a method-local
supported-ID list. This is a source correction, not a validator or engine change.
The corrective queued run selects only `TestSitarHeroOnlineMatch` and
`TestSitarHeroControls`; the unaffected passing model/transport tests are not
repeated.

The corrective command uses the same flags/absolute ROM paths as the second
command above, replacing its selection with
`-Dtest=TestSitarHeroOnlineMatch,TestSitarHeroControls`. It completed successfully
on 2026-10-07 at 11:50:07 +01:00: protocol 20/20 and packaged controls 5/5,
zero failures/errors/skips, 27.149 seconds total after six seconds queued. The
protocol class took 3.275 seconds and the packaged controls class 1.023 seconds.
The mod compiled, packaged, and validated through ExampleModHarness/ggfmod.
Latest XML summaries and testcase identities were inspected for all four
selected classes: 51 unique passing tests in total, no skips. This is focused
validation, not a full-suite result. No UI, audible hardware, remote-network,
ROM-backed chart curation, or broad engine certification is claimed. Parent
owns those consumer/integration checks and the combined broad run.

Final OnlineMatch source SHA-256: `44fbc13c595ebdfe33df01623966d027f0b268493d51e19985579b4fe4c757a2`.
Pre-commit/commit-message policy checks and documentation links pass; all seven
policy trailers precede the final GPT-6.1 co-author block. The exact prepared
co-author line was verified with `git interpret-trailers --parse`. No push, main
workspace integration, branch switch, or worktree/branch removal is performed
by this implementation subtask.
