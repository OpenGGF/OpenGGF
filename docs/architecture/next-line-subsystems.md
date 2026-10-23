# Next-line subsystem contracts

Read this reference when changing the next-line Mod API or network contracts.

**Mod API (`com.openggf.game.patch`, `com.openggf.mod*`).** Owner-tagged additive
`GamePatch` decorators sit over a root `GameModule`. The engine-owned
`ModuleResolutionService` resolves enabled built-in/mod owners in dependency order at
gameplay launch choke points; `WorldSession` keeps both root and resolved modules so
repeated resolution never double-wraps. Metadata/prerequisite failures disable the owner
and its dependents while independent owners continue; arbitrary creator `apply` failures
abort launch, because partial input mutation is not rollback-safe. `WorldSession` also
owns the module's `GameDataSource` — ROM or bounded standalone assets.

Creator content targets the unpublished mutable `@ModApi` 0.7.0 candidate surface (no Mod
API baseline has been published) via the two-artifact `ggfmod` toolchain: namespaced
objects and art, complete Sonic 2 zones, ROM-art intake, owner-tagged playable characters
(`CharacterKey` identities over the immutable module registry), no-ROM standalone modules
(`AbstractStandaloneGameModule`, durable `GameDataSource`, game-agnostic baked levels,
namespaced slot-1 saves/audio), playable-subclass rewind capture hooks, the host-adapted
S3K custom-zone/palette bridge, and exclusive game-start selection with destination-scoped
launch teams, deterministic input filters, and row-only HUD profiles. Controlled level modes opt into `GameModule.gameplayFrameController()`:
held iterations record mode input/audio while admitting no world clocks or physics.
`CourseControl` exposes validated opaque whole-course checkpoints, exact registered
single-player replacement, explicit level destinations, and creator-calculated native
launch/audio operations; the controller's registered identity stays outside
course rollback while full debug rewind includes it. Prepared value scenes are
composed from locally ROM-loaded art and can be transmitted to a permanently held
guest without importing gameplay state. See
[Putt Putt Paradise](../../examples/putt-putt-paradise/README.md).
ROM-backed startup scenes can use `baseGame: any`, supplied-ROM art libraries,
timestamped physical input and bounded consumed-sample music transport. Rhythm
rules, part ownership and curated charts remain in the
[Sitar Hero example](../../examples/sitar-hero/README.md); these capabilities do
not make the engine's normal gameplay depend on scene timing or diagnostic data.
Code-bearing mods
stay namespaced, injected-service-only, rewind-recreatable, transactionally registered,
and owner-fault-bounded. Complete new zones preserve tagged identities, not runtime
indices. Maintained contracts live in [creator handbook](../modding/index.md) and
[compatibility contract](mod-api-compatibility.md);
dated design specs under `docs/architecture/designs/` are historical provenance only.

**Stageplay.** `com.openggf.stageplay` owns versioned directing data, pure 60 Hz
evaluation and the shared editing controller/store. Both authoring frontends use
`StageplayWorkspace` through `EditorWindowChrome.Host.stageplay()`; UI state is not
scene state. TERRAIN_ONLY resource views are detached from native PLC/terrain
owners, with explicit ROM and relative authored-set/voice dependencies. Opt-in
LIVE scenes use `StageplayLiveWorld` and the production `LevelFrameStep` pipeline
for one world per shot, independent native inputs, objects/rings, abilities and
rewind. Editors and registered playback use `StageplayOfflinePreview` to retain
the host world; capture explicitly transfers its own disposable session. Cinematic
framing follows actual actors at fixed zoom without adding activation regions.
Playback is one
exclusive owner-bound frame controller with captured transport. Optional native
terrain-only main-player binding uses `CourseControl`'s opaque entry capability; the engine's
`DirectedPlayerLease` owns cleanup independently of creator callbacks, including
quarantine, teardown and revoked historical restore. It restores player/camera
state, never a whole course. Offline capture uses an independent transport and
existing final PCM/encoder infrastructure. See the [authoring and mod guide](../guide/creating/stageplay.md)
and [design decisions](designs/2026-10-21-stageplay.md).

**Multiplayer time attack.** The engine retains shared networking under
`com.openggf.net`: typed protocol and ghost codecs, room services, clients, identity,
and the JDK in-process host. Public contracts marked `@ModApi` are part of the
candidate surface and can be used by other mods without installing Time Attack.
Internal WebSocket, certificate and handshake implementations are not creator API.
`TestNetIsolationRules` keeps the shared backend independent of engine gameplay
owners, Time Attack and the standalone server.

The bundled Time Attack mod (`racing/time-attack`, `openggf.timeattack` and
`openggf.timeattack.mp`) owns racing flow, menus, settings and presentation, and
uses the gameplay-run and scene Mod API to play stock acts. The standalone racing
server (`racing/server`, `openggf.racing.server.*`) owns the master, Netty dedicated
host, verifier and operator tools. Netty, Bouncy Castle and SQLite are server/test
build dependencies and are not shipped in the engine runtime.
`TestTimeAttackModPackage` requires strict SDK validation with no warnings.

Scene mods with their own messages and rules can use the existing owner-scoped
`SceneNetwork` transport, as Sitar Hero does. Putt Putt Paradise's independent direct
TCP implementation is unchanged; adopting a shared transport does not require its
rules to become Time Attack races.

Each `RoomHost` and `GhostHub` is confined to a single room thread, and the master server
reuses those room classes unchanged. Production masters
require TLS (`plaintextForTest: true` is loopback-test only); the localhost admin HTTP
endpoint requires its bearer token and appends to `admin-audit.jsonl`. Identity age, clean
rounds, sanctions, and trust tiers persist in SQLite. An active BAN or TIMEOUT rejects
admission and immediately closes existing master sessions, their tokens, hosted rooms,
and attached relay connections. TIMEOUT preserves earned standing after expiry; BAN
resets clean-round standing. Anonymous identities that solve creation proof of work can
reconnect without repeating it while their stamp remains in a bounded LRU cache:
`newIdentityCacheSize` limits its entries and `newIdentityCacheTtlMinutes` expires
each stamp from proof completion (reconnects update recency, not expiry). Eviction or
expiry requires a fresh proof; durable identities retain their existing admission path.
Each master connection retains at most five pending room-attach grants, expiring 30
seconds after the latest successful join for that room. The relay creator's initial
host grant follows the same bounds. Attach consumes a current grant once, including
when the downstream room or tunnel refuses attachment; expired or evicted grants
require another rate-limited join. Verified rooms are relay-only and
need a live replay-verifier worker matching the room's determinism fingerprint; ROM bytes
never cross the network and worker verdicts are Ed25519-signed. The host accepts attempt
controls and ghost data only during the running phase, permits one strictly increasing
attempt at a time per player, and allows ghost progress no more than 12 frames ahead of
server-observed elapsed time. New attempts close at the round deadline; an
already-active attempt may deliver its finish during the two-second transit grace
before the host finalizes the round. The protocol does not supply a trusted
predeadline completion timestamp, and the client sends a finish whatever its local
phase estimate, so in practice an attempt running at the deadline may complete up
to two seconds after it and still be credited. A player may submit any number of
attempts inside the window and the best valid run counts: while the window is open the
client follows each finish's results tally with a fresh attempt, sends each
`AttemptStart` immediately before that attempt's first sampled frame (so a retry's
reload and title card never count against pacing), and leaves an unfinished run for
the lobby once the host closes the round. A start refused only because the window is
not open is not a violation; that attempt's frames, reset and finish are dropped
quietly. The hub shows a finished or reset sender as `FINISHED` or `IDLE` in the
roster until its next accepted batch and, once its buffered tail is relayed, stops
offering it as another recipient's nearby sender. Departed members' track votes are removed
from the live tally, including when their slot is reused. Ghost-stream and
finish-evidence strikes apply the same connection-close threshold. A finish is
one-shot: its input hash must be a SHA-256 digest,
and a pending verifier verdict remains bound to that exact claim. In verified rooms
every unresolved improvement remains a candidate: the shown best is the fastest of the
verified fallback and the pending candidates, a superseded claim's pass becomes the
fallback when faster, a fail or void removes only its own claim, and round-end expiry
(or a round started before the previous linger ended) voids every remaining claim's
route. A participant pushed out of the broadcast top ten receives its new rank
privately. Verifier workers log a failed cycle and retry after a bounded back-off,
and a refused verdict for a voided or re-leased job counts as processed. JSON control envelopes
reject duplicate and unknown fields. The in-memory verification queue holds at most
4,096 tracked jobs. Uploaded jobs still queued or leased after one hour become durable
`VOID_VERIFIER_UNAVAILABLE` verdicts without a cheating sanction; a late worker result
cannot complete them. Queue exhaustion produces the same void result for a new claim.
Terminal queue entries are pruned on the next submission after 60 seconds, while
recording blobs and durable verdicts have separate retention. Reusing an existing
recording refreshes its retention age so a fresh verification job does not lose
the blob to an hourly sweep; blob writes and the sweep are serialized so the sweep
cannot delete a blob whose retention a concurrent reuse has just refreshed. Private
identity keys are created with owner-only filesystem permissions, and that exact
protection is reapplied and verified before key bytes are written, because Windows
merges inheritable parent ACEs into a creation-time DACL. The public key is written
first; a failed creation removes the files it created, and a lone public key left by
an interrupted creation is regenerated, while a lone private key is preserved and
reported. Legacy keys are tightened before loading, and filesystems unable to
enforce private creation fail closed. The Windows ACL path is source-reviewed only;
no Windows runtime test exists.

Within a round, finishes retain the admitted participant session and identity even when a
departed player's slot is reused. Clean-round credit, replay verdicts, and spot checks use
that retained ownership. Each admission receives a non-secret participant ID, which is
captured when a finish enters the verifier queue; a verdict, pending-result expiry,
recording request, or queue-full fallback applies only to that participant's claim,
even when the same identity reconnects and repeats the attempt ID and recording hash.
The ID also distinguishes durable verifier attempt references without changing the
worker job schema. Room display names and selected characters are bounded to 64 and
32 UTF-8 bytes respectively; admission and character changes must keep the serialized
room roster within the 64 KiB client control-frame limit, including a full 256-player
relay roster. Broker room descriptor fields
are bounded before they enter browser results. Client inbound control/ghost event
queues hold at most 512 events and close an overflowing connection; remote ghost
playback retains at most 128 samples per admitted roster member. Decoding rejects
missing required creator fields and invalid nested room/round/standings state before
the game loop consumes it. The broker's slotless `JoinAccepted` is an exact
intermediate master-admission marker; a final direct or relayed room join must
carry a bounded player slot, room descriptor, and round snapshot. Master reply
queues match replies in FIFO order. An unanswered or cancelled request retires
the connection: the transport is aborted, all other pending operations fail
promptly, and further requests reject with an instruction to reconnect. Late
frames cannot complete another operation. Callers recover by establishing a fresh
`MasterClient`; requests are never replayed automatically, because a timed-out
create/join may already have executed at the broker. Explicit close flushes already
accepted writes before its close frame; failure retirement discards queued writes,
including when failure interrupts a graceful close. Retirement also publishes a
`Disconnected` inbound event, including on explicit local close; an attached
relay receives that event through its own inbound queue. The broker sends an explicit
empty `RoomListResult` with negative `totalPages` when browsing is rate-limited;
the client turns that sentinel into a failed request rather than mistaking a
silent drop for a later reply. Invalid room-create routing similarly receives
a rejection alongside its strike. Ordinary explicit rejections retain the usable
connection. Without wire request IDs, reply deadlines require connection
retirement rather than guessing which later request owns an answer. Every broker
path for list, create, and join must still reply. A
failed `RelayAttach` is answered with `RoomJoinRejected` on the already-attached
socket; that frame goes to the relay handshake, not the join queue, and fails the
handshake immediately with the broker's reason. A
Protocol version 3 incorporates the negative list sentinel and membership-delta
contract. Earlier experimental clients fail the exact-version handshake instead
of presenting a rate-limited response as an empty successful page.

Operator commands:

Broker-listed `DIRECT` rooms use `wss://`. The host creates a fresh self-signed TLS
certificate when its direct server starts: the mod's in-process JDK host builds an
in-memory ECDSA P-256 certificate from JDK APIs, and the server's Netty dedicated host
uses Netty's generator, which needs Bouncy Castle. The authenticated host registers the certificate's
SHA-256 digest with the broker, which stores it with the room and returns it to
joiners. A joining client pins that exact certificate and checks the broker-pinned
host identity in `Welcome` before signing its challenge. The session token travels
only after this TLS and identity check, so a fake endpoint cannot collect it and a
live TCP relay sees ciphertext. The private certificate key is temporary: the JDK host
never writes it, and the Netty host deletes it when the direct server closes. Manual LAN
hosting uses the same authenticated protocol; its lobby displays `HOST_IP:port#<share-code>`
and saves it as `lan-invite.txt` in the mod's storage (scenes have no clipboard). The host replaces
`HOST_IP` with a reachable LAN address before sharing. The 86-character unpadded
base64url code contains both 32-byte pins. Manual join decodes those pins, requires
`wss://`, and verifies both the certificate and host `Welcome` identity before
sending any proof. A bare address or `ws://` has no safe identity source and is rejected.
The code is a trust-on-sharing capability: guests must receive it through a trusted
channel and should not accept an invite edited by an untrusted party.
These wire fields and the initial-snapshot/`PlayerUpsert`/`PlayerLeft` membership
contract use protocol version 5. Version 4 added the player's look to the signed
`Hello` (transcript tag `client-auth/v2`), a lobby-only `UpdateProfile` for renaming
or recolouring without a new identity, and a per-player `PlayerLook`; looks stay out
of `RoomState` so a 256-player roster still fits one frame. Version 5 adds readiness
(`SetReady`, per-player `PlayerReady`, `ReadyReset`, the owner's `SetReadyRule` and
the host's `ReadyRule`: `REQUIRED`, `AUTO` or `DEFAULT_BY_SIZE`, required up to five
players) and between-round lobby presence: binary `TYPE_LOBBY_FRAMES` up and
`TYPE_LOBBY_AGGREGATE` down, in the ghost frame and aggregate layouts, validated
(forward-only streams, rate and per-frame movement caps) and relayed to each player's
eight nearest senders in relay rooms. Hosts and the master
accept only `DisplayNames`-canonical names. Earlier experimental clients and servers
cannot mix on control connections; no deployed compatibility path exists.

Direct-room fallback tunnels check the receiving `HubConnection.queuedBytes()`
before forwarding either text or binary in either direction. Above the shared
`GhostHub.BP_DISCONNECT_BYTES` limit of 1 MiB, the router removes and closes only
the affected guest and sends its usual single guest-close notification to the host.
Exactly 1 MiB remains admissible, matching the hub's slow-consumer cutoff. A slow
host retains its shared master link, room registrations and other tunnels, so
traffic can resume when its queue drains and replacement guests can attach. This
is a check of reported transport queue occupancy before a payload send, not an
atomic byte reservation or a bound on cross-event-loop tasks, lifecycle notices,
or global socket/room scale. Transports without queue telemetry report zero.

```bash
python3 racing/server/build.py
target/racing-server/racing-server load-test --n 256 --duration 30 --mix adversarial
target/racing-server/racing-server verifier --master https://host:27900 --registration-token <token> --rom s3k.gen --data ./verifier-data
```

`TestGhostLoadTest` checks exact healthy completion and adversary outcomes with
in-JVM bots. The `load-test` command reports hub-tick CPU timing and largest emitted
packet size; it excludes ingress processing and does not measure transport queues or
socket throughput. The [racing stress plan](plans/2026-10-21-racing-stress-system.md)
defines complementary stateful adversarial and real-socket capacity coverage.

## Scoped mutators prototype

The unpublished 0.7 candidate accepts boot-prepared `MutatorDefinition` contributions
through owner-derived `ModContext` transactions. Each ENABLE, DISABLE and option
has its own scope. WorldSession owns requested/admitted/effective settings and
bounded historical publications; saved requested preferences are independent.
`LevelLoadCause` marks qualifying full assemblies, while the shared configuration
overlay owns explicit LIVE Resume and retained fade-time commands. Factory output
is immutable and capability-bounded; host quarantine cannot be rewound.

The [Mutator Lab guide](../modding/guides/mutators.md) explains the real dry-air
movement and post-native-admission presentation hooks, UI and exact support cell.
It uses the ordinary native level loop, preserving golf's controlled-frame input
contract. Modified user-recording support, placement filters and the later
portfolio/MVP/product gates remain unimplemented. No API version bump or published
baseline edit occurs: policy descriptor and runtime remain 0.7.0 candidate.
