# Multiplayer security hardening implementation plan

> **For agentic workers:** implement the assigned task test-first in its isolated
> worktree. Record the failing regression (RED), the passing focused command
> (GREEN), and the final commit. Do not broaden file ownership without asking the
> integration lead.

**Goal:** close the October 2026 multiplayer audit findings in the core racing
stack, standalone master server, Time Attack mod, and Putt Putt Paradise example.

**Architecture:** authenticated handshakes bind the complete client intent to the
protocol role and the TLS endpoint; untrusted work is bounded before asynchronous
dispatch or rendering; and insecure network exposure is opt-in and visibly
authenticated. Each workstream remains independently reviewable, then the lead
merges them and runs combined change-based smoke coverage.

**Tech stack:** Java 25, JDK WebSocket/TLS, Netty, Ed25519, JUnit Jupiter, Maven.

**Security basis:**
[Time Attack security design](../designs/2026-07-04-time-attack-security-design.md),
[multiplayer design](../designs/2026-07-04-multiplayer-time-attack-design.md), and
[Putt Putt Paradise design](../designs/2026-10-05-sonic-mini-golf.md).

## Global constraints

- Preserve protocol size/framing bounds and reject malformed or over-budget
  input before expensive work.
- Bind Ed25519 proofs to a versioned, domain-separated canonical transcript that
  covers protocol role, full `Hello` intent, nonce, authority identity, and the
  observed TLS certificate identity. A proof for one authority/role/channel must
  not authenticate to another.
- Permit plaintext WebSockets only for literal loopback addresses in the explicit
  local/test path. Non-loopback Time Attack browsing and joining require `wss`.
- At most one client proof-of-work solve may run per master connection. Bound
  accepted difficulty, time, and cancellation; closing or superseding the
  challenge must stop the worker.
- Standalone master HTTP admission must bound pipelined work and retained request
  bodies before asynchronous dispatch. Dependency updates complement rather than
  replace application-level backpressure.
- Scene admission must cap aggregate raster work, not only object counts. Line
  traversal must clip before iteration, and unchanged presentations must reuse a
  composed image.
- Putt network buffering must have packet and byte bounds, and pending replaceable
  scene views must coalesce. Default hosting must not expose an unauthenticated
  first-seat race to the LAN.
- Creator input remains behind existing `ModContext`/fault boundaries; no ROM
  fallback, singleton shortcut, or game/zone-name carve-out is introduced.
- Use the repository queue for tests, never the release-only full suite, and do
  not modify or remove the main checkout's pre-existing user files/submodules.

## Review focus

- A malicious endpoint relays an otherwise valid master or room challenge: the
  proof is rejected because role and TLS authority differ.
- A master sends repeated/high-difficulty PoW challenges, then closes: only one
  bounded worker exists and it terminates promptly.
- One TCP connection pipelines many unauthenticated master HTTP requests: retained
  and dispatched work stays within the configured bound.
- A valid-size scene encodes huge offscreen rectangles or extreme lines: decoding
  or admission rejects excessive aggregate cost and composition stays bounded.
- An uninvited LAN peer races the intended Putt guest: default/local hosting is
  unreachable remotely and explicit LAN hosting requires an unpredictable invite.

## Task 1: handshake, TLS, Time Attack, and PoW hardening

**Owned files:** core handshake/protocol/client/JDK-host classes under
`src/main/java/com/openggf/net/`, their focused tests under
`src/test/java/com/openggf/net/`, and Time Attack multiplayer sources/tests under
`racing/time-attack/`. Do not edit `pom.xml`, `racing/server/master/`, scene
presentation, or Putt files.

- [x] Add failing relay tests proving a proof cannot cross master/room roles,
  Hello metadata, authority identities, or TLS certificate identities.
- [x] Introduce one canonical versioned transcript builder used by client and
  host verification; carry/check the TLS endpoint binding at both ends without
  trusting a creator-supplied value.
- [x] Add failing tests for excessive/repeated PoW challenges and cancellation on
  close/timeout, then implement one tracked bounded solver with interruption.
- [x] Add failing Time Attack URI-policy tests, then require `wss` for every
  non-loopback browse/join/upload endpoint while retaining explicit literal
  loopback coverage.
- [x] Run the exact focused test selectors covering every changed class and commit
  with repository trailers.

## Task 2: standalone master admission and dependency hardening

**Owned files:** `pom.xml`, `examples/putt-putt-paradise/build.py` only if its
runtime dependency copy requires alignment, `racing/server/src/main/java/openggf/
racing/server/master/MasterServer.java`, `MasterHttpRoutes.java`, and corresponding
master-server tests. Do not edit handshake/client or Putt protocol/presentation.

- [x] Add a failing Netty embedded/integration test that pipelines more requests
  than the allowed in-flight limit and asserts bounded dispatch/body retention.
- [x] Gate reads or dispatch with an explicit small bound and release accounting on
  every success, exception, timeout, and closed-channel path.
- [x] Upgrade Netty to at least `4.1.138.Final` (prefer the current compatible
  `4.1.139.Final`) and Jackson to patched `2.22.3`; keep dependency convergence.
- [x] Cover ordinary sequential requests, oversized bodies, disconnects, and
  handler failures so backpressure cannot deadlock a connection.
- [x] Run focused master-server/dependency checks and commit with repository
  trailers.

## Task 3: Putt render, buffering, and peer-admission hardening

**Owned files:** scene presentation classes/tests under
`src/main/java/com/openggf/game/presentation/` and
`src/test/java/com/openggf/game/presentation/`, plus
`examples/putt-putt-paradise/`, its integration tests under
`src/test/java/com/openggf/mods/code/`, and its README. Do not edit Maven,
core racing handshake/client, or standalone master files.

- [x] Add failing tests for the audited worst-case valid-size scene, extreme
  offscreen line coordinates, repeated unchanged draw calls, and aggregate cost.
- [x] Define a deterministic aggregate raster-work estimator enforced when a
  frame is created/decoded/accepted; clip lines to the viewport before traversal;
  cache composed output until frame, pose, or offset changes.
- [x] Add failing transport tests for retained byte pressure and multiple pending
  replaceable view packets; enforce a byte cap and coalesce only semantically
  replaceable, not ordered/control, packets.
- [x] Make local-only bind the hosting default. Explicit LAN hosting must require
  an unpredictable per-session invite secret in the initial authenticated
  admission before a peer can claim the seat; surface the mode/code in the menu.
- [x] Correct README networking claims, run focused presentation/Putt tests, and
  commit with repository trailers.

## Integration and acceptance

- [x] Merge the three task branches into the integration worktree and resolve
  conflicts by the security invariants above.
- [x] Review each task diff and one combined diff for bypasses, lifecycle leaks,
  compatibility mistakes, and tests that pass without exercising the exploit.
- [x] Run focused regression selectors, then inspect and run the change-based smoke
  selection from the pinned base `ff5088abf5cce5878f143817f03c941805f11880`.
- [ ] Fast-forward/reconcile the main `develop` checkout without switching it,
  verify the integrated tree, push only `develop`, and remove only fully merged
  task-owned worktrees/branches.

## Execution record

Implementation was split into three isolated worktrees and merged into the
integration branch:

- Time Attack/core authentication: `9704bb048` and `21a1722a4`.
- Standalone master admission and dependencies: `aa87a1c12`, `4cc82ef40`, and
  the pre-aggregation rejection follow-up `98bd8cc92`.
- Putt presentation/networking: `3fed56df7`, `94564e48d`, and `096a64ad9`.

Independent GPT-6.1 Sol reviews covered each workstream, exploit-test quality,
the combined package, and the final master rejection follow-up. The final reviews
reported no Critical or Important finding. While validation was running,
`develop` advanced from the pinned base to `c13be267f`; merge `00c20c37b`
reconciled those shared-line changes. The only overlapping runtime-adjacent file
was configuration documentation, and the only manual conflicts were this release
line's changelog and the Time Attack security design. A fresh static review of the
reconciled tree was clean.

Validation on reconciled head `00c20c37b`:

- The expanded focused selector ran 570 tests covering authentication/TLS/PoW,
  master HTTP admission, Time Attack, Putt transport/presentation/package,
  Mod API compatibility, configuration, HUD, and build guards: zero failures,
  errors, or skips.
- `tools/testing/test_release_snapshot_policy.py` ran 36 tests: all passed.
- The change-based runner selected all 3,515 smoke classes in all ten routine
  categories, split into 71 ordinary batches, plus the separate guard JVM. With
  `--max-minutes 120`, all batches completed against working-tree fingerprint
  `d5e040b336130dcf1a303d97d2e3a333fd94eb463a319557ad9f6f34ebaf169e`.
  Ordinary smoke ran 37,104 tests: 2 failures, 3 errors, and 81 skips. Guards ran
  717 tests: 4 failures and no errors or skips. Every one of the nine failed cases
  was reproduced on unchanged baseline `c13be267f` with the same exception type
  and assertion text; no failure was new or worsened. The skips were explicit
  opt-in measurement/capture/native-graphics checks or documented route
  assumptions; all configured ROM paths were present. The inspected runner
  diagnostics were acknowledged and deleted.

## Retained security boundaries

- Authentication proofs use the TLS leaf observed by both protocol endpoints.
  A reverse proxy therefore has to preserve that identity; independently
  terminated certificates require a future authenticated trusted-edge binding,
  not a forwarding header.
- Existing protocol-v2 peers reject the strengthened transcript safely rather
  than accepting an old proof. Creator-supplied hub adapters that do not provide
  trusted transport metadata fail closed.
- Explicit Putt LAN hosting authenticates first-seat admission with the private
  invite, but subsequent LAN game traffic remains plaintext and therefore is not
  protected from an active on-path attacker. Loopback remains the default bind.
