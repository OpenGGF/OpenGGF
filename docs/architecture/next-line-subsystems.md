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
launch teams, deterministic input filters, and row-only HUD profiles. Code-bearing mods
stay namespaced, injected-service-only, rewind-recreatable, transactionally registered,
and owner-fault-bounded. Complete new zones preserve tagged identities, not runtime
indices. Maintained contracts live in [creator handbook](../modding/index.md) and
[compatibility contract](mod-api-compatibility.md);
dated design specs under `docs/architecture/designs/` are historical provenance only.

**Multiplayer time attack.** The direct-connect and master-server core lives under
`com.openggf.net.protocol`, `.hub`, `.host`, `.client`, and `.master`. These packages are
engine-free and may share only the canonical `GhostFrame` / `GhostFrameCodec`;
`TestNetIsolationRules` enforces the boundary. Each `RoomHost` and `GhostHub` is confined
to a single event-loop thread, and the master server reuses those room classes unchanged.
Engine and UI adapters belong in `com.openggf.game.timeattack.mp`. Production masters
require TLS (`plaintextForTest: true` is loopback-test only); the localhost admin HTTP
endpoint requires its bearer token and appends to `admin-audit.jsonl`. Identity age, clean
rounds, sanctions, and trust tiers persist in SQLite. An active BAN or TIMEOUT rejects
admission and immediately closes existing master sessions, their tokens, hosted rooms,
and attached relay connections. TIMEOUT preserves earned standing after expiry; BAN
resets clean-round standing. Verified rooms are relay-only and
need a live replay-verifier worker matching the room's determinism fingerprint; ROM bytes
never cross the network and worker verdicts are Ed25519-signed. The host accepts attempt
controls and ghost data only during the running phase, permits one strictly increasing
attempt at a time per player, and allows ghost progress no more than 12 frames ahead of
server-observed elapsed time. New attempts close at the round deadline; an
already-active attempt may deliver its finish during the two-second transit grace
before the host finalizes the round. The protocol does not supply a trusted
predeadline completion timestamp. Departed members' track votes are removed
from the live tally, including when their slot is reused. Ghost-stream and
finish-evidence strikes apply the same connection-close threshold. A finish is
one-shot: its input hash must be a SHA-256 digest,
and a pending verifier verdict remains bound to that exact claim. JSON control envelopes
reject duplicate and unknown fields. The in-memory verification queue holds at most
4,096 tracked jobs. Uploaded jobs still queued or leased after one hour become durable
`VOID_VERIFIER_UNAVAILABLE` verdicts without a cheating sanction; a late worker result
cannot complete them. Queue exhaustion produces the same void result for a new claim.
Terminal queue entries are pruned on the next submission after 60 seconds, while
recording blobs and durable verdicts have separate retention. Reusing an existing
recording refreshes its retention age so a fresh verification job does not lose
the blob to an hourly sweep. Private identity keys are created with owner-only
filesystem permissions before key bytes are written; legacy keys are tightened
before loading, and filesystems unable to enforce private creation fail closed.

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
queues preserve timed-out requests as FIFO tombstones so a late answer cannot
complete a later request with stale room metadata. The broker sends an explicit
empty `RoomListResult` with negative `totalPages` when browsing is rate-limited;
the client turns that sentinel into a failed request rather than mistaking a
silent drop for a later reply. Invalid room-create routing similarly receives
a rejection alongside its strike. Without wire request IDs, FIFO matching
still favors safety over availability if an earlier answer never arrives. A
pre-wave-3 version-2 browser treats the negative list sentinel as an empty
successful page until its next refresh; it does not crash, but may show a
misleading empty-room state. Protocol compatibility is tracked for the next
audit rather than claimed solved here.

Operator commands:

Broker-listed `DIRECT` rooms use `wss://`. The host creates a fresh self-signed TLS
certificate when its direct server starts; Netty's Java 21 certificate generator
uses Bouncy Castle at runtime. The authenticated host registers the certificate's
SHA-256 digest with the broker, which stores it with the room and returns it to
joiners. A joining client pins that exact certificate and checks the broker-pinned
host identity in `Welcome` before signing its challenge. The session token travels
only after this TLS and identity check, so a fake endpoint cannot collect it and a
live TCP relay sees ciphertext. The private certificate key is temporary and is
deleted when the direct server closes. Manual LAN hosting uses the same authenticated
server; its lobby displays and copies `HOST_IP:port#<share-code>`. The host replaces
`HOST_IP` with a reachable LAN address before sharing. The 86-character unpadded
base64url code contains both 32-byte pins. Manual join decodes those pins, requires
`wss://`, and verifies both the certificate and host `Welcome` identity before
sending any proof. A bare address or `ws://` has no safe identity source and is rejected.
The code is a trust-on-sharing capability: guests must receive it through a trusted
channel and should not accept an invite edited by an untrusted party.
This required wire-field change uses protocol version 2; version 1 clients and
servers cannot mix on control connections.

```bash
java -cp target/OpenGGF-0.7.prerelease-jar-with-dependencies.jar com.openggf.tools.net.GhostLoadTestTool --n 256 --duration 30 --mix adversarial
java -cp target/OpenGGF-0.7.prerelease-jar-with-dependencies.jar com.openggf.tools.verifier.VerifierMain --master https://host:27900 --registration-token <token> --rom s3k.gen --data ./verifier-data
```

The CI scale gate runs 32 in-JVM bots through `TestGhostLoadTest`; the 128/256-player gate
above measures hub aggregation CPU, not deployed socket throughput.
