# Private lobbies and invites implementation

Implement the [approved design](../designs/2026-10-25-private-lobbies-and-invite-codes.md)
from destination base `824a16f21cb2c2e4c98c10a20c1117c50bcf525e`.
The user chose six random alphanumeric characters and five attempts per minute.
The absolute invitation lifetime is ten minutes. Private admission
always requires the password, including direct and relayed connections.

## Delivery predicate

Public rooms retain their existing behavior. Private rooms cannot publish a
slot, session token, roster or gameplay data before identity and password
admission. Codes resolve local or remote rooms through the existing master,
expire and rotate, and respect server-enforced member-sharing permission.
The UI masks secrets, supports reveal/copy and keeps credentials out of files
and logs. Focused behavioral checks, integration checks, proportionate owner
smoke categories and guards have no new or worsened failure. The broader
change-based attempts and their incomplete coverage stay explicit. Push the integrated
destination branch and account for task-owned worktrees.

## Throughput checkpoint

- Blocking first steps. Pin the base and agree shared message, policy and invite
  provider contracts before consumers settle their calls. Preserve source
  overloads with explicit public defaults; wire-private state never defaults
  public. Use request correlation in the coordinated protocol increment.
- Independent workstreams. One Sol worker owns shared networking and admission,
  one owns the master/dedicated server, and one owns scene clipboard/focus APIs.
  Each has an isolated checkout and focused verification. A Sol consumer worker
  owns Time Attack wiring after the limited screen pass. The lead owns final
  integration, documentation, API inventory/pins and combined validation.
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
- Common runtime and recovery commits `d6a8c4fdf` and `913260c28` are integrated
  as `7003240f3` and `964be0a5b`. The final focused selection passed 51 cases
  without skips. Earlier affected checks passed 42. These overlap and are not
  an aggregate test count.
- Server commit `8146a4fb7` is integrated as `b677b5c64`. Its final affected
  selection passed 113 cases without skips, including real relay/tunnel joins
  and raw private hosts. Another 76 unchanged checks passed against the preceding
  SDK. The standalone server build passed. Earlier fixture and shutdown errors
  were repaired and covered by the affected rerun.
- Time Attack commits `63f9d1ff7`, `b6fdd620d` and `250ae55a5` are integrated
  as `498c75eb5`, `a0adc04c9` and `54c837795`. UI checks passed 84 cases;
  subsequent affected password, invalidation and glyph checks passed without
  skips. The real socket class passed 33 of 34 initially, then its sole legacy
  notice assertion passed in an exact rerun. New direct, relay and registered-LAN
  joins passed, including password entry after a 31-second human prompt.
- Exact password editing rejects oversize input without accepting a truncated
  prefix. Display-name filtering was rejected for passwords after two focused
  failures demonstrated loss of joiners and private-use characters.
- Review found JDK host shutdown did not retire its shared room. A bounded
  lifecycle fix and regression are underway. The capture tool now confirms the
  default Public creation modal instead of waiting indefinitely for a lobby.
- Destination `develop` advanced to `c502c600b` with multiplayer communication
  controls during implementation. Reconcile its overlapping chat/protocol/scene
  changes before combined verification. Preserve both features and regenerate
  the actual candidate API snapshot. No combined pass is claimed yet.
- Reconciliation commit `407a5a4df` preserves private access and communication
  modes together, including leases and epochs during metadata changes. Protocol
  7 distinguishes the combined strict schema from destination protocol 6. The
  actual regenerated 23,071-line API snapshot and six other focused classes
  passed 72 cases without skips.
- JDK teardown fix `1bbc087a4` is integrated as `f2534472c`. Its five-case
  baseline reproduction failed as expected; 44 affected checks and the final
  six-case lifecycle check passed. Independent common/server/scene review found
  one additional handoff race. Fix `cec4827cd`, integrated as `ad4adf65a`, keeps
  pending creation cancellation and late replies ahead of relay attachment.
  Its delayed-response reproduction failed twice as expected; focused client
  checks passed 38 cases and a final reentrant case passed separately.
- The reconciled consumer/lifecycle selection ran 231 cases with no failures or
  errors and one graphics skip. Its 35 socket cases include private direct,
  relay and registered-LAN joins and the delayed password prompt. The graphics
  skip demonstrated that `MAVEN_OPTS` does not set the test fork's backend.
  A direct EGL rerun reached rendering and exposed a stale full-frame assertion:
  hiding now removes cached invite metadata, so its expiry label changes to
  unfetched. Compare with a fresh unfetched frame and with another hidden code,
  while retaining distinct revealed-glyph and password pixel assertions.
- Final client/reentrant-handoff and actual EGL rendering checks passed 40 cases
  without skips on `ad4adf65a` plus the corrected pixel fixture. Actual pictures
  at 320, 400 and 800 pixels use the production scene renderer. The Java 25,
  Lua 5.4 and guard-tool preflight passed with the test JVM's explicit EGL
  environment. The change-based plan selects 3,808 smoke candidate classes
  across all ten categories plus separate guards. Coherent packaging and this
  combined selection remain the next delivery gates.
- Coherent packaging on `5ac8f8058` passed, including the Time Attack validator
  with no findings and the standalone server build. The first combined smoke
  invocation completed 66 of 77 batches: 36,266 cases, two failures, one error
  and 90 skips. It is incomplete; the process is no longer live and its final
  retained batch succeeded. No terminal cause was captured. The two failures
  reproduce exactly on destination baseline `c502c600b`: the existing audio
  reset source-order assertion and HCZ miniboss raw-GLFW initialization without
  a display. The error is our merged polymorphic fixture supplying chat's string
  `mode` for the new access enum. Restrict those named fixtures to string fields.
  Skips include opt-in diagnostics/benchmarks, legacy graphics prerequisites and
  route assumptions; none is counted as a pass. Consumed diagnostics were read
  and acknowledged.
- Focus-presentation fix `6acb4e28f` is integrated directly. Invalidating state
  alone was rejected after six pixel failures proved old revealed pixels stayed
  visible while Engine paused. Actual scene and overlay submissions now identify
  the last successfully swapped frame. Blur/minimization withdraws those pixels
  with one neutral presentation without updates, creator redraw or capture.
  Ten new EGL cases and 51 related cases passed without skips in the worker.
  These verify production rendering with injected presentation callbacks, not
  native compositor behavior.
- Destination advanced again to `94c75d8a8` with audit remediation. Its merge
  applies without conflicts; the gameplay/PLC, release tooling and mirrored
  guidance changes remain intact. Final checks must cover this reconciled tree,
  the fixture correction and focus-presentation change. Repeat the complete
  category selection because the earlier invocation did not finish; do not
  treat its unrun suffix or guards as passed.
- The reconciled eight-class selection passed 86 cases, zero failures, errors
  or skips, using Java 25 and explicit `-Dopenggf.headless.gl=surfaceless-egl`.
  Selectors: `TestControlCodecPolymorphism,TestPrivateRoomContract,TestChatProtocol,TestModApiSignatureSurface,TestEngineSceneFocusPresentation,TestEngineLiveCapturePresentation,TestSceneClipboardAndFocus,TestPrivateLobbyScreenRendering`.
  Admission waited 690 seconds; Maven execution took 91 seconds. This repairs
  the exact codec fixture error and verifies the new presentation behavior.
- The second all-category attempt at `dfea98cec` was interrupted during batch
  71 after 70 of 77 batches completed. It recorded 37,988 cases including partial
  batch-71 reports, two unchanged baseline failures, zero errors and 93 skips.
  Its 5,708 seconds of reported execution was below the 180-minute bound; the
  log was active and source clean. The runner followed its interrupt/termination
  cleanup path (exit 130), not the configured timeout or a test failure. The
  signal's origin was not recorded. The broader invocation remains incomplete;
  unrun classes and guards are not counted as passes. All failure/skip details
  were inspected and the diagnostics acknowledged. Skips were opt-in captures,
  measurements, optional external mod jars, graphics prerequisites and route
  assumptions, with no missing-ROM skip reason.
- Controller quick chat advanced destination to `c9028e88c`. Isolated merge
  `5bf53a93c` has parents `dfea98cec` and `c9028e88c`, with no reconciliation
  code beyond the automatic merge. The actual 23,074-line API snapshot matches
  its pin. Sixteen affected input, pause, coordinator, run-host, scene-privacy
  and API classes passed 222 unique cases without failures, errors or skips.
  This includes all known `RunInput` consumers, production overlay rendering and
  clipboard activation; it does not claim native compositor verification.
- Apply the repository's proportionate-validation option after the interrupted
  broad attempts. Complete the whole `network`, `mods`, `rendering` and `tooling`
  categories, plus automatically included `common`, on the final combined tree;
  do not construct a manual suffix of the broad plan. This selects 1,148 smoke
  candidate classes at `5bf53a93c`; recalculate after the latest destination
  merge. It includes all shared networking, master,
  Time Attack, scene/API, rendering and SDK/tooling owners. Retain completed
  broader coverage for unrelated audio, content, gameplay, physics and rewind
  rather than restarting their ROM matrices a third time. These owners and the
  focused real-socket/pixel regressions cover the changed behavior; the broader
  all-category invocation is still reported as incomplete.
- The next fetch found destination `d3fa3d519`, adding the Typing campaign and
  radial quick chat. Reuse the existing integration worker to reconcile its
  scene navigation/key capture, Engine/GameLoop, API pins and lobby-view overlaps
  before freezing the final owner-category run. Preserve all upstream content
  and all private admission, clipboard, focus and invite behavior. The Typing
  execution record separately attributes its completed 77-batch frozen run and
  subsequent composition repairs; do not relabel that run as this composed
  candidate's verification.
- Merge `403d02444` now includes that destination. Only the signature pin
  conflicted; actual compiled-source generation resolved it to 23,103 signatures
  without losing any private or upstream entry. Twenty scene/input/lobby/API
  classes passed 336 unique cases without failures, errors or skips. Source
  merges preserve both navigation/key capture and clipboard/focus ownership;
  no extra runtime reconciliation code was needed. The final whole-owner plan
  selects 1,180 smoke candidate classes in 24 ordinary batches, plus guards.
  Run it with `run_categories.py --category network --category mods --category
  rendering --category tooling --guards --rom-directory
  <original-ROM-directory> --max-minutes 120 --run`, Java 25,
  Lua 5.4, `JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl` and both
  display environment variables unset. Expected execution is 30–60 minutes
  plus unknown queue waiting; source drift, infrastructure failure, execution
  budget or output-silence limits leave coverage explicitly incomplete.
- The owner-category run completed all 24 batches on `0d78e95ca`: 9,130 cases,
  four failures, zero errors and 76 skips. Fresh guards completed 800 cases,
  three failures, zero errors/skips. The two inherited audio/GLFW failures
  remained unchanged. New ordinary failures were relay Scale/SocketStress
  capacity recovery; the exact matched destination `d3fa3d519` selection passed
  all four cases (Scale, RELAY, JDK and NETTY). All failure/skip details were
  inspected without omitted records and diagnostics acknowledged. Skip reasons
  were opt-in captures/measurements, graphics prerequisites, optional external
  mod packages and route eligibility, with no missing-ROM reason.
- Relay fix `b77215acc` is integrated. A bounded probe measured cached discovery
  count 4, authoritative room count 3 and capacity 4 at rejection. A stale
  directory count is not a slot lease; final capacity belongs to `RoomHost`.
  Remove that veto while retaining NEW-identity pressure, private admission,
  epoch and grant checks. A real-room regression also rejects actual overflow
  and requires a fresh session token on recovery. The unchanged candidate
  reproduced both failures in 11 cases; the final 12-class selection passed
  131 cases without failures, errors or skips.
- Repair the three guard integrations without widening runtime authority.
  Exempt only the imported/qualified JDK `SecretKeyFactory`, with a project
  lookalike negative. Follow the presentation helper in the capture-order guard.
  Extract activation callback wiring from `Engine.init`, reducing it from 177
  to 164 lines and lowering its former 170-line ratchet. The six-class affected
  run passed 104 of 105 cases; its only failure was the stale exact ratchet,
  which then passed in its single-method rerun. Engine/scene/pixel behavior and
  all 42 singleton-closure cases passed without skips. Full final guards and
  combined recovery/session/pixel checks remain the next checks; do not replay
  the unchanged owner-category suite after these bounded repairs.
