# Private lobbies and invite codes: common multiplayer framework

Date: 2026-10-25. Status: architecture proposal; no implementation in this
change. Source inspected: `159655c8460dac15912dc4326491386a75368666`
(`develop`, engine `0.7.prerelease`, networking protocol 5).

## Intent and decisions

Add password-protected private rooms and convenient, privacy-conscious invites
to the common multiplayer framework. Time Attack is the first consumer. The
access policy must apply equally to player-hosted, dedicated and master-hosted
rooms, including direct connections and relay fallback. Once admitted, players
use the existing lobby and game behavior.

The user specified public/private access, a password set at private-room
creation and required on connection, unpredictable alphanumeric invite codes,
a masked code with a clickable reveal eye, host sharing, and member sharing
enabled by default with a host-only option. Security/privacy take priority over
code length. The user confirmed that player-hosted games may use the master
service; offline short-code discovery is unnecessary.

Recommended choices made by this design, rather than additional user
requirements:

- `PUBLIC` remains listed and needs no password. `PRIVATE` is unlisted and
  requires a password, including when joining with an invite or a manual
  address. Unlisting is a privacy choice; the password is the admission gate.
- A code locates a room; it never grants membership, bypasses trust/build/ban
  checks, supplies an upload token or replaces authentication.
- Use 16 random base32 characters, displayed in four groups. The underlying
  code is alphanumeric; separators are presentation only. Codes are resolved
  by the configured master service for both remote and player-hosted games.
- Access mode is fixed at creation for the initial delivery. Password changes,
  invite regeneration and the member-sharing setting are owner operations.
  Existing admitted players stay connected when credentials change.
- Keep the existing longer, pinned manual LAN address for offline play. It
  receives the same password enforcement and privacy controls.

No matchmaking, accounts, host migration, new relay service, NAT traversal
protocol, offline discovery, single-use personal invites or Putt transport
migration is included. These room policies do not change server-operator
authentication or make every game in the repository use the same wire protocol.

## Existing architecture and relevant gaps

Paths below are relative to the repository root. These are observations at the
source commit above, not claims that the proposed features already exist.

- `src/main/java/com/openggf/net/` owns the shared protocol, identity, clients,
  `hub/RoomHost` and JDK host. `racing/server/` supplies the master and Netty
  host. `racing/time-attack/` supplies gameplay rules and UI. Preserve this
  boundary; access policy must not depend on Time Attack classes.
- `hub/RoomHost.handleHandshake()` calls `admitMember()` after identity proof.
  Both JDK and Netty hosts and `master/RelayRoomManager` use this driver. Insert
  the access gate before slot allocation, session-token issuance, roster,
  standings, presence or gameplay publication.
- `master/RoomBroker` owns create/list/join/attach. `SessionRegistry.create()`
  currently issues sequential `r-N` room IDs. Those remain internal identifiers,
  never invitations. `RoomBroker` already has bounded 30-second one-use attach
  grants; extend them rather than introducing a second bearer-token system.
- `SessionRegistry.list()` also serves internal stale-tunnel cleanup. Add a
  filtered browser query; do not hide private rooms from internal maintenance.
- `client/DirectJoinAddress` packs certificate and host-identity pins into an
  86-character base64url suffix following an address. This establishes endpoint
  trust, not access permission. Preserve both pins.
- `MasterClient.joinRoom()` currently attempts relay after any failed direct
  connection. Authentication, password, protocol, trust and certificate failures
  must become terminal failures; only classified reachability/transport failures
  may trigger fallback.
- Time Attack's `mp/RaceSession.enterLobby()` logs the LAN invite and writes
  `lan-invite.txt`; successful joins persist `lastJoinAddress`. `RaceLobbyView`
  and menu text entry display the address openly. Remove automatic secret
  export/persistence and mask both new codes and existing complete LAN invites.
- Room admission currently expires after five seconds, client room joining after
  three, and master requests after five. Collect human input outside a pending
  request or admission timeout. Master replies use FIFO matching, not request IDs.
- `RoomBroker.removeHostedRooms()` currently removes rooms by creator fingerprint
  on any matching connection's disconnect. Registration ownership must become
  connection/generation-specific so another browser connection cannot destroy a
  live room or its invite.
- `HostMasterLink` identifies a tunneled peer as `relay:<guestId>`, not its source
  address. Address-based abuse control for that path belongs at the master.
- `SceneContext` exposes mouse input, but no clipboard or window-focus facility.
  Copy and focus-loss hiding therefore need bounded engine API additions.
- Shared networking is JDK-only and fenced from gameplay, mod implementations,
  Netty, SQLite, Bouncy Castle and LWJGL. Putt's TCP/HMAC invite implementation is
  separate; adopting it wholesale would not cover the racing admission paths.

## Ownership and data model

Use common access types in `com.openggf.net`, exposed through the Mod API only
where consumers need them. Names here describe responsibilities; final class
names remain an implementation choice.

| Concern | Owner and contract |
| --- | --- |
| `RoomAccessPolicy` | Immutable non-secret mode and `membersCanInvite` setting; default `PUBLIC`, `true`. |
| Password verifier | Private room-authority state: random salt, algorithm parameters and derived verifier. Never in descriptors, summaries or member snapshots. |
| Admission state | `RoomHost`: identity proved, access pending, admitted or closed. Admission is a separate transition from identity authentication. |
| Creator authority | Trusted creation context binds the creator fingerprint. Use the existing owner check; never infer ownership from a client flag, displayed name or first arriving guest. |
| Invite registration | Master assigns a code and maintains a bounded lookup index tied to a live room instance, owner, routing registration and invite epoch. |
| Invite disclosure | Room authority sends the current code only to the authenticated owner or an admitted member allowed by policy. |
| UI | Time Attack consumes policy/admission/invite state. It never decides whether credentials are sufficient. |

Keep secrets outside generic records whose generated `toString()` exposes fields.
Use deliberately redacted secret containers and explicit serialization. Passwords
are connection-attempt state, not `TimeAttackSettings`, `RoomDescriptor`, command
arguments or mod configuration. A verifier is also sensitive and not a client
credential. Public metadata may carry `accessMode`/`passwordRequired`, never a
salt, verifier, password, invitation or pending admission token.

For a player-hosted room, `RoomHost` retains the verifier; the master needs only
the non-secret policy to register and route it. For a master-hosted relay room,
the master creates its `RoomHost` with the supplied creation password and then
discards the plaintext. The same gate is used in both cases. The trusted
creator's own admitted connection also completes private-room admission; room
creation is not a first-player password bypass.

## Discovery and connection flow

1. **Create.** The creator chooses mode and, for private mode, supplies a
   nonempty valid password before startup/publication. Initialize the verifier
   and owner first. Register the policy and routing only after the authority is
   ready to enforce it. Registration failure must not silently turn a private
   room into a public room. A local room may continue with a manual invite while
   clearly reporting that a short code is unavailable.
2. **Find.** Public browsing filters private rooms before pagination, totals and
   game filters are calculated. Code entry uses an authenticated master request
   such as `RoomInviteResolve(code)`. A successful resolution invokes the same
   build/trust/capacity-routing checks as a browser join, produces the existing
   join result with access metadata, and grants a bounded relay attachment.
   It is already a join preparation; do not perform an extra raw-ID join.
3. **Collect credentials.** For private results, prompt locally before opening
   the final room connection. Cancel means no connection attempt. An offline
   manual address may first probe the pinned room: complete identity proof,
   receive access-required without room/player data, close, and prompt before
   reconnecting. Do not retain an admission socket while the user types.
   Discard the initial attach grant before prompting. After entry, resolve the
   code again to obtain a fresh grant and recheck the expected room instance,
   access mode and access epoch before sending the password. An alias pointing
   to a different instance or a changed access epoch requires fresh user entry;
   never send a retained password to the replacement. Code removal/rotation
   fails resolution normally. If fallback needs another grant after expiry,
   perform the same bounded re-resolution and checks; do not extend grants
   across arbitrary human input time.
4. **Authenticate.** Connect over the existing trusted/pinned TLS path and
   complete the versioned Ed25519 identity handshake. Preserve role, full Hello,
   nonce, authority and observed TLS-certificate binding. Resolve results are
   scoped to the expected room instance; direct endpoints must authenticate that
   instance as well as the existing certificate and host identity.
5. **Prove room access.** For private rooms, the host issues a fresh access
   challenge bound to this connection, authenticated fingerprint, room instance
   and access-policy epoch. The client immediately submits its already-entered
   password with that challenge reference over the same TLS connection. Accept
   one submission per challenge; reject wrong state, cross-room/connection
   replay and stale epochs. Public rooms take the normal admission transition.
6. **Admit.** Only the room authority's successful result permits the existing
   `JoinAccepted`, slot, token, room/round data and membership publication.
   Recheck capacity, duplicate identity and current epoch on the serialized room
   thread after asynchronous verification. A delayed result cannot admit a
   disconnected player or reopen a closed room.

The access challenge is an internal correlation value, not a password-derived
proof. Do not transmit a reusable client-side password hash or put a fast
password digest in an Ed25519 transcript. If the authentication transcript gains
room-instance fields, version its canonical encoding and test both sides. The
password is protected by the authenticated TLS transport and its connection
state, not a new homemade password cryptosystem.

Private raw `RoomJoinRequest(roomId)` from an unrelated identity must return the
same unavailable response as an unknown room; sequential IDs cannot reveal its
endpoint or produce an attach grant. The authenticated creator retains its
existing creation/initial-attach path, still followed by password admission.
Rejoining guests resolve the code again. An existing manual endpoint can still
be used with the password; address knowledge is not secret authorization.

Attach grants must include room-instance and invite/policy epochs where
applicable and be checked again on consumption. Private discovery and attach
authorization must not bypass the host gate. Cover all of:

- JDK and Netty raw direct endpoints, including offline/manual LAN joins;
- master-listed direct joining and its host-tunneled relay fallback;
- master-hosted relay rooms, including the creator's initial attach;
- reconnects and connection failures during a pending password check.

Fallback preserves the same room, identity, policy and certificate checks.
Wrong-password, access-denied, incompatible-protocol and identity/pin failures
are terminal. A relay is not a second chance to get around rejection. Keep the
password only for the bounded active join/fallback attempt, then clear its
owned buffers; a later reconnect prompts again. Existing Java/JSON strings may
leave transient immutable copies, so do not claim guaranteed memory erasure.

## Password handling and abuse bounds

Use the JDK's `PBKDF2WithHmacSHA256`, at least 600,000 iterations, a fresh
16-byte cryptographic salt and a 32-byte verifier. This is a deliberate choice
to preserve the common layer's JDK-only contract. Argon2id is the preferred
memory-hard alternative if a later dependency decision admits an audited
implementation; do not implement it locally. OWASP documents both the
[password-hashing choices and PBKDF2 work factor](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).
Benchmark the chosen fixed work factor on target hosts; never lower it in
response to attacker-supplied parameters or runtime load.

Proposed room-password policy: 15–128 Unicode code points, at most 512 UTF-8
bytes, with no silent trimming, normalization, truncation or character-class
rules. Offer a generated password. Creation, local validation and verification
must agree exactly. Compare derived verifiers using a constant-time operation.
Store only the salted verifier in ephemeral room state. Dedicated configuration
uses an owner-readable secret file, following existing `SecretFile` patterns,
without adding a command-line password or saving it in ordinary config.

Perform cheap framing, identity, challenge-state and rate checks before KDF work.
Use a process-wide bounded executor shared across rooms: initially two workers,
eight queued checks and one pending check per connection. Do not hash on the
room/broker event loop or create an executor per room. Start with bounded token
buckets for authenticated identity, source address and room plus a process-wide
work cap; use separately named operational defaults and focused load evidence
to settle rates. Identity regeneration and opening more connections must not
reset all limits. Avoid permanent room/account lockout from attacker failures.

For direct sockets, take the source bucket key from trusted transport metadata.
For master-hosted and host-tunneled relay guests, enforce source buckets at the
master using the originating socket's observed address, before forwarding access
submissions or scheduling KDF work. Retain bounded, expiring buckets across guest
IDs and reconnects, shared across rooms. A tunneled host enforces identity,
room and process limits in addition; `relay:<guestId>` is never a source bucket
key. Do not forward raw client IPs to player hosts or accept a peer-supplied
address. Cover both repeated tunnels and multiple authenticated master sessions
from the same source.

The existing identity timeout remains bounded; the password phase adds no human
input wait. Account for KDF queue time in an explicit bounded admission deadline
and align client/server timeouts with measured worst-case accepted work. Reject
or shed excess work; do not hide queue delay behind indefinite waiting. Release
pending accounting on timeout, failure, disconnect and close. A completed worker
posts back to the owning room thread with connection and epoch checks.

Unknown/expired codes and unavailable private rooms share an external response
shape. Wrong credentials produce a generic admission denial; do not expose
verifier details or subtly distinguish private-room existence before resolution.
Apply consistent response envelopes and bounded work rather than asserting that
the entire network service has constant response time. These follow OWASP's
[enumeration and throttling guidance](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html).

## Invite format, lifetime and permissions

Generate 80 uniform random bits using `SecureRandom`, encoded as 16 characters
from `0123456789ABCDEFGHJKMNPQRSTVWXYZ`. Display, for example,
`7K3M 9X2R Q5TW 8N6C`. Accept ASCII lowercase, presentation spaces/hyphens and
documented `O → 0`, `I/L → 1` aliases, then require exactly 16 canonical symbols.
Cap raw input before normalization. Do not use Unicode lookalikes, modulo-biased
sampling, timestamps, room IDs, host addresses or deterministic seeds. Retry an
active collision with a bounded generation loop; never replace another room's
mapping.

This is a private locator, not an authenticated session token. For `Q` online
guesses and `L` live codes, the union bound for finding any code is
`min(1, Q × L / 2^80)`, assuming independent uniform codes. For one million live
codes and one billion guesses that bound is approximately `8.3 × 10^-10`.
This design judgment balances typing with privacy; it is not a promise that a
leaked code is safe. Short six/eight-character identifiers are rejected because
the active global population increases guessing success. Existing session
credentials keep their independent, stronger generation and lifetime rules;
OWASP recommends at least 128 random bits for
[custom session identifiers](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html).

Resolution is an authenticated, rate-limited WebSocket request body, not a URL
path, query parameter or public GET endpoint. Bound attempts per identity,
source and service, including aggregate negative lookups. Store a domain-separated
SHA-256 lookup digest in the directory index; a slow password KDF is unnecessary
for a random locator. Only the authorized room/creator control state retains the
raw code in memory for disclosure. A memory-compromised authority remains able
to read it; hashing the index does not change that.

One active code per room instance. It lasts for the live registration until
regeneration, room close, registration expiry or master restart. The master owns
collision checks and atomic alias replacement; the player host receives the
current code through its authenticated, room-owner-bound master link. Stale
registrations, owner disconnect and room removal must retire their aliases using
registration-scoped lifecycle rules. Bind a registration lease to its exact
registering connection and generation, separately from the creator fingerprint
used for authorization. Disconnect cleanup removes only leases still owned by
that connection/generation. Re-registration explicitly replaces the generation;
late disconnect/expiry callbacks cannot remove its replacement. Remove the
current fingerprint-wide disconnect cleanup for these registrations. No code
or verifier survives restart through
ordinary settings. A custom master is selected explicitly in the UI; codes are
unique within that resolver, not globally across arbitrary independent masters.
Never send a code to a list of guessed resolver services.

The owner can reveal/copy/regenerate. An admitted member can request reveal/copy
only when `membersCanInvite=true`; this is checked on the authoritative room
thread. No code appears in a roster, broadcast snapshot, browser response or
automatic `JoinAccepted`. Member responses carry the invite epoch, and clients
discard stale responses after hide, policy change, leave or close.

Turning member sharing off also rotates the code, invalidating copies previously
given to members. Enforce permissions before serializing a response. Rotation
invalidates unconsumed old invite grants, while existing players remain admitted.
Clear visible/cached old code on every client; suppress in-flight disclosure
callbacks from older epochs. Show a policy/rotation operation as pending until
the master acknowledges atomic replacement. If the master is unavailable, do
not claim that a previously shared code has been revoked.

Host-only sharing means only the host can obtain the current code through the
game. It cannot prevent a recipient from forwarding it or telling someone the
password. Changing a code prevents future resolution, not use of an endpoint
already learned. Change the password to invalidate the old admission secret;
that increments the access epoch and cancels pending old checks without
disconnecting admitted players. Removing an admitted player remains a separate
moderation action.

## Transport, privacy and UI

All remote resolve/control/password traffic retains `wss`, certificate validation
and host-identity verification. The existing explicit literal-loopback test path
remains the sole plaintext exception. A code must not introduce trust-all TLS,
an arbitrary certificate pin supplied by a stranger, an HTTP downgrade or a
different Ed25519 endpoint-binding rule.

The trusted master can see room registration metadata and source addresses.
Master-hosted rooms necessarily process passwords there. Host-tunneled relay
traffic is TLS-terminated at the master; this design adds no end-to-end secrecy
from that operator. Prefer relay routing when protecting a player host's IP is
important: direct routing necessarily reveals its endpoint to a joining peer.
A revealed code, weak/reused password, authorized malicious member, compromised
host/master or captured desktop lies outside what UI masking can prevent.

Time Attack changes:

- Creation offers Public/Private and a masked password field shown for Private,
  plus “Members can invite” enabled by default. Invalid/empty private passwords
  prevent creation. Keep the mode and member-sharing preference if desired,
  but never persist the password or generated code.
- Add “Join by code” beside existing browsing/manual joining, using the selected
  master. Mask entered codes and complete manual addresses, including during
  validation/errors. A private result opens the password prompt before joining.
- In lobby details, show a fixed mask, a clickable eye and a separate Copy
  action to authorized viewers. Copy need not reveal; feedback says “Copied”
  without showing the code. Rehide after 10 seconds and on navigation, leave,
  focus loss, rotation or a permission change. Never persist reveal state.
- Provide keyboard/gamepad reveal/hide and copy actions plus an accessible
  action label. Hidden text must not expose the raw code via tooltip, accessible
  name, layout measurement, debug overlay or narration. Revealed text is
  deliberately visible to the user and capture software.
- The host can toggle member sharing and regenerate; members denied by policy
  see “Only the host can invite.” Integrate live policy/epoch updates into
  `ClientRaceSession` and `MultiplayerRaceCoordinator`, replacing the current
  constructor-only `RaceLobbyView.shareCode` state.
- Extend the existing mouse input seam for the eye; use `SceneContext.mouse()`
  and proper text-input capture for browser/password modals. Retain the engine's
  normal input suppression while a modal owns text entry.

Add the missing scene capabilities through engine-owned presentation services:
a bounded clipboard-text write, available only for an explicit foreground user
action, and a window-focus-loss notification. Expose success/unavailable results
without logging copied text; do not expose clipboard reading or native window
handles. Marshal platform writes onto their owning thread. Headless contexts
report clipboard unavailable and support deterministic synthetic focus events.
Focus-loss delivery must invalidate revealed state even while normal scene
updates are paused, before any next presentation; a polled flag handled only
after resume is insufficient. Apply existing owner/fault boundaries and release
callbacks when the scene closes. Include these additions in the Mod API/version
work and focused headless/platform-adapter checks; no shared-desktop automation
is needed.

Remove automatic `lan-invite.txt` writing and full-invite logging, and stop
persisting the credential-bearing `lastJoinAddress`. Migration discards the old
saved complete invite without deleting unrelated files or silently rewriting
user-authored exports. Redact secret-bearing message/exception text at both
inner and relay-wrapper logging layers, diagnostics, telemetry and captures.
No default status display should reveal the endpoint as an alternative to the
hidden code. Clipboard copying is explicit and does not promise protection from
clipboard history; do not overwrite a user's later clipboard contents with a
delayed cleanup timer. This follows OWASP's guidance to
[exclude passwords and access tokens from logs](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html).

## Delivery slices and compatibility

1. **Shared contract and admission.** Add common policy/secret handling, a
   bounded verifier service and the `RoomHost` access state machine. Cover raw
   direct and relay paths before connecting UI or publishing private rooms.
2. **Directory and invites.** Add private browser filtering, authenticated code
   resolution, owner-bound registration/rotation and epoch-aware attach grants.
   Correct connection/generation-scoped cleanup and relay-source throttling.
   Preserve internal registry maintenance. Integrate both hosted-room forms.
3. **Time Attack experience.** Add creation/password/code-entry modals, dynamic
   invite permissions, eye/copy controls, classified fallback and secret-safe
   settings/logging behavior, including the bounded scene clipboard and focus
   APIs. Preserve existing public-room race behavior.
4. **Coordinated activation.** Update codecs, client/master/JDK/Netty hosts,
   standalone `racing/server/build.py` consumers, protocol tools and tests together.
   Document operational limits and offline/manual behavior before activation.

Protocol 5 rejects unknown fields and missing constructor properties. Use one
coordinated version increment (6 if no intervening change), with clear version
rejection rather than silent default-to-public downgrade. Preserve source
convenience overloads only where they deliberately construct public policy;
wire-private state must never default public. Any signed-transcript changes
need matching version/domain separation and golden regression vectors.

Asynchronous private-room creation cannot preserve today's assumption that
same-type replies arrive in FIFO order. In the coordinated protocol increment,
give master requests and their success/rejection replies a connection-scoped,
non-reused request ID, including create/list/join/invite/rotation operations.
Use a bounded pending map, validate expected reply type and retain per-request
deadlines; immediate rejection must echo the same ID. This replaces FIFO matching
instead of attempting to serialize every asynchronous completion. IDs correlate
work and confer no authority. Late/unknown replies cannot complete a different
request. Creation work captures the request and exact registration-connection
generation: after cancellation, disconnect or timeout, a late KDF result must
not publish a room, register an alias or leak retained secret state. Test a slow
private creation followed by a fast public creation and immediate rejection.

Networking contracts are public `@ModApi` candidate surfaces. Actual implementation
must update `ModApiSurfaceInventory`, `ModApiVersion`, the policy descriptor and
normalized `0.7` signature pins as required by `mod-api-release-policy.properties`.
Do not alter these pins, claim a release or add user-facing feature documentation
merely for this proposed design. Follow existing declaration/version rules if
another task changes the candidate line first.

## Acceptance and verification plan

This document receives source-reference, link, whitespace and policy checks.
It changes no executable contract and requires no engine suite. Implementation
must supply focused tests for these behaviors:

- Every admission path listed above: missing/wrong password rejects before slot,
  token, roster, round, presence or binary publication; the correct password
  joins; public-room behavior is unchanged. Creator initial attachment is covered.
- A valid invite never bypasses a password, sanction, build/trust requirement,
  player cap or owner check. Raw sequential private IDs disclose no endpoint,
  metadata, page count or usable attachment grant.
- Wait longer than the 30-second initial attach grant while entering a password:
  final re-resolution still joins the same live private room. Alias reuse or a
  changed instance/access epoch never receives the retained password.
- Both direct and fallback hosts use the same gate. Only transport failures
  trigger relay fallback; wrong-password/pin/protocol failures cannot do so.
- Challenge replay, wrong instance, disconnect, close, verifier overload,
  capacity races, password change and stale asynchronous completions cannot admit.
  Work stays bounded across rooms, identities, connections and address sources.
  Repeated tunneled guest IDs and master reconnects do not reset source limits.
- Invite parsing has deterministic alphabet/normalization/bounds tests; injected
  generator collisions test retry ownership without flaky statistical assertions.
  Invalid, rotated, expired and restarted-room codes resolve consistently.
- Non-owner control attempts fail. Member reveal/copy works by default and is
  denied server-side when disabled. Disabling rotates, stale grants/callbacks
  fail, and admitted members remain connected.
- Host registration loss/reconnect, stale cleanup, failed rotation and master
  restart cannot resurrect an old alias or show false success. Internal private
  rooms still receive lifecycle cleanup. A second connection with the creator's
  fingerprint can close without deleting the active registration; an old
  generation's disconnect cannot remove its replacement.
- Masked rendering, mouse plus keyboard/gamepad control, 10-second rehide,
  input capture, secret-safe errors/logging/settings migration and copy feedback
  are exercised. Joining a private room does not save the password or invite.
- Clipboard writes are bounded, explicit and thread-owned; unavailable/headless
  behavior is harmless. Focus-loss masking works while ordinary updates pause,
  and scene teardown releases callbacks without stale clipboard work.
- Protocol/version, TLS/transcript, Mod API signature and dependency-fence
  checks cover the coordinated change, including the standalone server build.
  Out-of-order creation/rejection replies match their IDs, and late creation
  work after cancellation/disconnect publishes no room or alias.

Before implementation integration, inspect the change-based smoke plan from the
pinned destination base. These shared protocol/admission changes normally need
that combined smoke selection plus separate guards and relevant networking
integration checks. Run it once on the final combined source state, with class
count/cost/stopping rule announced; attribute failures with bounded matched
baseline checks. No release-only full suite or exhaustive trace sweep is needed
for routine delivery. Player-hosted direct, tunneled and relay smoke scenarios
must use owned headless processes, never shared-desktop input automation.

## Alternatives and evidence

- **Recommended: master-resolved random locators plus room-owned passwords.**
  Uses the existing master, authenticated clients, attach grants, pins and relay
  paths. Short codes require that service; offline joining retains a longer
  explicit address. This matches the user's confirmed connectivity scope.
- **Self-contained invites.** Existing `DirectJoinAddress` already needs an
  endpoint and 64 bytes of trust material before adding any secret. Its current
  86-character suffix demonstrates why this cannot also be a short alphanumeric
  code. Keep it as the offline/manual fallback, not the primary sharing UX.
- **Offline LAN short-code discovery.** Requires multicast/discovery, interface
  selection, bounded candidate probing and authenticated pin distribution.
  Not selected because the user permits master-assisted player-hosted games.
- **Broker-only password or an invite that bypasses it.** Rejected: raw JDK/Netty
  endpoints still reach `RoomHost`, and bypass conflicts with the explicit
  private-room password requirement. Client-only hiding also leaves codes
  available to modified members; authoritative disclosure and rotation are needed.
- **Reuse the Putt invite protocol directly.** Rejected for this delivery:
  `examples/putt-putt-paradise` uses independent TCP with a 16-byte HMAC secret,
  whereas racing already owns TLS, Ed25519 identity, master grants and shared
  room admission. A future adapter can consume common policy without claiming
  that the two transports have already been unified.

An independent architecture review against `159655c846` identified and this
proposal incorporates: expired grants during human input, FIFO reply races from
asynchronous password setup, fingerprint-wide registration teardown, unstable
tunneled source keys, and absent clipboard/focus scene APIs. These are design
corrections with source evidence, not runtime fixes or completed test claims.

Related references: [multiplayer design](2026-07-04-multiplayer-time-attack-design.md),
[security design](2026-07-04-time-attack-security-design.md),
[recent hardening](../plans/2026-10-20-multiplayer-security-hardening.md),
[racing module boundary](../../../racing/README.md).
