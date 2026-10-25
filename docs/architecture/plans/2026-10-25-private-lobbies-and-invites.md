# Private lobbies and invites implementation

Implement the [approved design](../designs/2026-10-25-private-lobbies-and-invite-codes.md)
from destination base `824a16f21cb2c2e4c98c10a20c1117c50bcf525e`.
The user chose six random alphanumeric characters and five attempts per minute.
The proposed absolute invitation lifetime is ten minutes. Private admission
always requires the password, including direct and relayed connections.

## Delivery predicate

Public rooms retain their existing behavior. Private rooms cannot publish a
slot, session token, roster or gameplay data before identity and password
admission. Codes resolve local or remote rooms through the existing master,
expire and rotate, and respect server-enforced member-sharing permission.
The UI masks secrets, supports reveal/copy and keeps credentials out of files
and logs. Focused behavioral checks, integration checks and one combined smoke
selection plus guards have no new or worsened failure. Push the integrated
destination branch and account for task-owned worktrees.

## Throughput checkpoint

- Blocking first steps. Pin the base and agree shared message, policy and invite
  provider contracts before consumers settle their calls. Preserve source
  overloads with explicit public defaults; wire-private state never defaults
  public. Use request correlation in the coordinated protocol increment.
- Independent workstreams. One Sol worker owns shared networking and admission,
  one owns the master/dedicated server, and one owns scene clipboard/focus APIs.
  Each has an isolated checkout and focused verification. The lead owns Time
  Attack integration, documentation, API inventory/pins and combined validation.
- Shared mutable state. Networking owns the protocol schema. Server and UI
  consumers use its published contract. Only the lead merges the complete tree,
  updates signature pins and schedules combined smoke coverage.
- Smallest safe decomposition. Keep these three code owners. Do not create
  duplicate broad validation jobs or workers that merely wait for other workers.
  Reserve one bounded Opus 5.5 pass for actual screen code after Claude is
  authenticated. Sol handles every other task and any follow-up verification.

## Common contract direction

`RoomAccessPolicy` is the non-secret mode and member-invite permission.
`RoomPassword` is a redacted, bounded credential value rather than an ordinary
string in persisted settings. `RoomHost` owns explicit identity/access/admitted
states and a bounded shared verifier service. It supplies its room instance and
access epoch in a connection-bound challenge before admission.

The networking worker publishes protocol definitions first, with an early
commit. Extend room creation/configuration/join metadata through source-compatible
overloads. Code lookup and owner invite-control replies use the same correlated
master request mechanism as creation/list/join. Keep raw code disclosure in
explicit invite replies, outside snapshots and `JoinAccepted`.

Use one small asynchronous invite provider on `RoomHost` for master registration
and rotation. The host checks admitted membership and creator permissions before
calling it and checks epoch/membership again before delivering a response. A
player-hosted room calls the provider over its authenticated owner master link;
a master-hosted room uses the same contract internally. No game-specific gate
or broker-only password check is acceptable.

## Verification and integration

Workers run exact queued regressions for their owned behavior and report SHA,
command, assertions, counts/skips, and live job handles. The lead checks direct,
master-relay and tunneled paths and the real lobby rendering. Use owned headless
or isolated graphics contexts. Do not automate the shared desktop.

Before combined validation, inspect the category plan from the destination base,
run the actual tool preflight and announce class count, expected cost and stopping
rule. Use the category runner's complete smoke selection with separate guards.
Attribute disputed failures through bounded matched baseline/current tests.
Inspect skips, acknowledge consumed diagnostics, and retain no validation caches.

## Execution record

- Grounding reused the design's direct/relay admission map and independent
  review. Repeating a design bakeoff would not resolve a new unknown.
- The initial T3 capability probe reports Claude unauthenticated. The user will
  authenticate it for the limited screen pass. Backend work proceeds with Sol.
- Shared contract `c01bee288` was integrated as `70a4ad2c4`. Its contract and
  candidate signature checks passed 16 tests without skips. The descriptor stays
  at candidate 0.7.0; ordinary pin regeneration must not edit that descriptor.
- Root reproduced saved-invite leakage with one failing settings regression,
  then removed settings import/export and automatic invite-file/log publication.
  Existing user exports are preserved. Settings/legacy checks passed 23 tests;
  LAN session checks passed 25. Fixed-size masked text and exact Unicode password
  editing passed 14 text-entry tests after correcting a misplaced rendering
  block caught by compilation.
- Scene clipboard/focus commit `1ffb9e0f0` passed 70 focused cases without skips.
  It is integrated as `08517fb3e`. Opus completed only the two screen components
  in `f6a313485`, integrated as `52e9e0c81`. Sol owns their wiring and verification.
- A real `SceneRenderer` EGL fixture passed one case without skips at widths
  320, 400 and 800. Different codes produce equal masked pixels, explicit reveal
  produces different glyphs, hiding restores the initial picture, and private
  creation does not reveal password content or length. The optional
  `OPENGGF_PRIVATE_LOBBY_SCREENS` test export supplies inspection pictures outside
  the repository. This is component rendering evidence, not completed networking.
- Time Attack ownership moved to one Sol worker after the independent privacy
  increment. Shared networking, server and Time Attack have separate branches;
  the lead owns final integration and the new engine-side pixel fixture.
- Combined private-room behavior, smoke/guards and final delivery remain pending.
