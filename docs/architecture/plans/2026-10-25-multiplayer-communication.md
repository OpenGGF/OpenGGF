# Multiplayer communication implementation and delivery

Goal: add Full Chat, Quick Chat and Off with personal display preferences, using
the [communication design](../designs/2026-10-25-multiplayer-communication.md).
Task base: `159655c8460dac15912dc4326491386a75368666` (`develop`). All implementation
workers share `.worktrees/ai-multiplayer-communication`; only the lead builds,
commits, integrates and pushes. Keep unrelated main-checkout changes intact.

## Implementation ownership

- [x] Protocol worker: `net/protocol`, RoomHost/RoomHostConfig/RoomHostHooks,
  ClientRaceSession and matching tests. Implement the design's exact mode/message
  contracts, protocol 6, owner/phase checks, host-supplied identity/context, shared
  cooldown/trust gates, mode ceilings and bounded cached structured history.
- [x] Preferences worker: `net/chat`, configuration defaults/catalog/timing,
  engine settings and SceneContext/ModSceneHost bridge, CONFIGURATION.md and
  matching tests. Implement both global personal toggles and small exact-token
  multilingual censorship. Keep networking independent of configuration.
- [x] Time Attack worker: `racing/time-attack` main/tests. Persist Full Chat as
  the hosted-room preference, provide both typed and quick actions in Full Chat,
  a quick chooser in lobby and races, mode labels and all display-path filtering.
  Wire global preferences and real zone-name formatting. Advertise direct-host
  mode changes through RoomChatModeUpdate.
- [x] Lead: master config/broker/registry/relay integration and matching tests.
  Apply public relay maximum QUICK_CHAT by default at creation and every owner
  change, preserve metadata across track changes/heartbeats, advertise actual
  mode, and prevent creator-supplied relay metadata from overriding host state.

## Review focus

- Full Chat must allow Quick Chat; Off and personal hiding must cover every player
  message display, including lobby details and the race overlay.
- Cooldown and trust gates must be shared across typed/quick traffic; invalid IDs
  and client-provided wording/context cannot become a text channel.
- Changing mode cannot raise a relay above its operator limit or leave discovery
  metadata disagreeing with admission state.
- Unicode normalization can change length: match normalized complete tokens but
  mask original spans; preserve Scunthorpe and other harmless containing words.
- Race communication must not pause simulation, steal movement, or break retry,
  countdown, reconnect or exit lifecycles. Personal disabling clears history and
  drops suppressed arrivals.

## Validation and delivery

- [x] Main develop fetched and fast-forward pull completed; base unchanged.
- [x] Isolated task worktree created and hooks installed. Tool preflight passes
  with `LUA_BIN=/usr/bin/lua5.4`; initial bare preflight found another Lua version.
- [x] Freeze combined source, compile, and run all affected focused networking,
  server, configuration, scene and Time Attack checks through maven_queue.py.
  Inspect testcase identities, failures and skips; attribute disputed failures
  narrowly against the pinned baseline, never by aggregate counts.
- [x] Regenerate and review the unpublished 0.7 API signature pin; retain current
  version policy. Update configuration, player/server/API guides and current
  development changelog. Verify source/resource packaging and mode defaults.
- [x] Inspect `run_categories.py --base <task-base>` and report candidate count,
  expected cost and stop rule before appropriate bounded smoke coverage. Do not
  launch release-only full-suite. Reuse unchanged completed coverage.
- [x] Review combined changes and address material findings with focused checks.
  Record evidence and important rejected alternatives in this existing plan.
- [ ] Prepare exact policy/co-author commit message, check interpret-trailers,
  commit, fetch/fast-forward develop, merge without changing its branch, verify
  new integration changes if any, validate push policy, push develop only.
- [ ] Inspect for uncommitted/unmerged work, remove the accounted-for task
  worktree and merged local branch, prune metadata, report exact delivery state.

## Checkpoint: Architect synthesis and first verification

All worker and lead edits remain uncommitted on task base `159655c8460d`.
Architect's selected integrated-session shape is recorded in the design: two
candidate sketches plus one cross-judge, then bounded type/mode/catalogue changes.
No architecture scrap is justified. The unpublished API stays 0.7.0; its generated
0.7 signature pin adds 138 lines. The release descriptor is unchanged as required
for ordinary candidate regeneration by the existing policy hook.

Queued focused commands used `python3 tools/testing/maven_queue.py --lean
-Dmse=off -Dtest=<exact comma-separated classes> test`:

- Initial 37-class check: compiled, 389 cases, 2 failures and 2 errors, zero skips.
  `TestNativeImageResourceGuard.everyRuntimeResourceIsReachableFromTheNativeImageConfig`
  found the new dictionary missing from native-image resources; its include was
  added. The other failures were new test assumptions: explicit default settings
  are retained by existing publication, YAML `OFF` needs quotes, and browser
  enumeration is limited to two seconds. Correct those fixtures, not production
  policies. Four affected classes reran: 38 cases, zero failures/errors/skips.
- Architect refactor check: TestClientRaceSessionChat, TestClientRaceSession,
  TestClientRaceSessionVote, TestChatProtocol, TestEnglishQuickChat,
  TestTimeAttackQuickChat, TestMultiplayerRaceCoordinator, TestRaceSession,
  TestTimeAttackModPackage. A misplaced lead-added late-join assertion first caused
  a compilation error; move it into the parameterized master-room test. Completed
  rerun: 93 cases, zero failures/errors/skips. This is bounded evidence, not a
  complete smoke result.

The final source review found three material issues: unjoined lobby test fixtures
after the single-mode-source change, chat drawing over round results, and stale
zone previews in a lobby surviving track changes. A bounded UI follow-up owns
those fixes and focused regressions. No other worker is editing source. Do not
launch combined validation until that worker returns frozen code.

The change-based planner selects all 3,791 smoke classes because racing paths and
configuration are unclassified/shared. Proportionate replacement: the complete
network/common category selection (362 candidates, automatically batched), all
Time Attack/server/configuration tests and relevant scene/API checks, plus a fresh
guards JVM. No physics, audio algorithm, game rule or renderer contract changes;
the configuration additions have two explicit consumers and scene bridge tests.
Expected category cost is several minutes, with the runner's normal 40 execution
minute/10 no-output minute limits; any timeout is incomplete, and new/worsened or
unattributed failures block delivery. ROM identities were checked against all
three canonical CRC32/SHA1 values; tests receive original absolute paths.

Upstream develop advanced with private-lobby design prose (`f7620e579`, then
`824a16f21`); preserve it during integration. Its proposed next wire version is
conditional on intervening work, so this protocol-6 implementation does not
conflict with its historical protocol-5 grounding.

## Completed validation and delivery boundary

The final source review's three findings were fixed. Lobby fixtures join before
injected messages and prove both message kinds were visible before hiding them.
Chat no longer overlaps result notices. Quick previews follow the latest round's
zone. The existing owner menu fits its 224-pixel screen; no layout rewrite was
needed. One medium-effort Opus 5.5 turn refined only the chooser's title, shortcut
help, key colours and focus marker. Sol owns the remaining work. Actual chooser
pixel commands were rendered without a desktop at 256, 320, 400 and 640 by 224;
the 320 and 400 previews were inspected. Component bounds checks cover all four.

Final commands run from the task worktree unless noted. In these portable command
records, `OPENGGF_MAIN_WORKTREE` denotes the original main checkout used for ROMs:

- `env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3
  tools/testing/run_categories.py --category network --rom-directory
  "${OPENGGF_MAIN_WORKTREE}" --run` completed all 8 batches of 362
  candidates. There were 2,719 cases, 1 failure, 3 errors and 0 skips. Lane
  execution was 304 seconds; admission waits are separate. Results were inspected
  and acknowledged with `run_categories.py --acknowledge
  20261025T112127Z-0a5c1067`, deleting the run directory.
- The three errors were missing graphics prerequisites in the invocation, not
  changed source. A queued normal-lane run with `-Dmse=off`,
  `-Dopenggf.headless.gl=surfaceless-egl`, verified absolute S1/S2 ROM paths and
  `-Dtest=TestGameLoopZoneActHandoffS1,TestGameLoopZoneActHandoffS2 test`
  passed all 11 selected cases with 0 skips. DISPLAY and WAYLAND_DISPLAY remained
  unset. No desktop fallback was used.
- The remaining failure is exactly
  `com.openggf.TestModEngineWiringSeams.audioResetAndBackendReplacementReleaseStreamedSessionState`.
  Both the updated baseline `824a16f21cb2c2e4c98c10a20c1117c50bcf525e` in the main
  checkout and the development tree ran
  `python3 tools/testing/maven_queue.py --lean -Dmse=off
  '-Dtest=TestModEngineWiringSeams#audioResetAndBackendReplacementReleaseStreamedSessionState'
  test`. Each produced 1 failure, 0 errors and 0 skips, with
  `public synchronized void resetState() is missing beginSourceMutation()`.
  Only the private-lobby design document differs between the baseline and task
  base. The assertion reads unchanged audio source. No unrelated audio repair
  was made.
- Supplemental queued normal-lane checks used `-Dmse=off`, the explicit EGL
  property and the three verified absolute ROM paths. Selection was every test
  class under `racing/time-attack/src/test/java` (34 classes, 296 cases),
  `racing/server/src/test/java` (39 classes, 285 cases), and
  `src/test/java/com/openggf/configuration` plus TestModApiSignatureSurface,
  TestSceneChatPreferences, TestModSceneHost and TestEngineSettingsScreen (41
  classes, 280 cases). Each exact class-name list was passed as `-Dtest` to
  `tools/testing/maven_queue.py ... test`. All three runs had 0 failures, errors
  and skips. These selections overlap the category run and are not a unique
  combined case count.
- `env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3
  tools/testing/maven_queue.py -Dmse=off
  -Dopenggf.headless.gl=surfaceless-egl -Pguards test -B` passed 800 cases, with 0
  failures, errors and skips in a fresh JVM. TestNetIsolationRules also ran in
  the networking/common selection. The API pin passed in the supplemental run.

The final tested development input is task base `159655c8460d` plus this feature's
changes; the category runner's source fingerprint was
`d71ee351608b3484c3c8473cb67f39df80e7617858ad656c3954af8825a4a7c3`.
Only this plan's evidence and delivery instructions changed after validation.
There is no new or unattributed failure. This is bounded smoke, subsystem and
guard evidence, not release-only full-suite certification. The original smoke
invocation remains red as recorded; its graphics errors were resolved narrowly
and its sole remaining assertion was matched on baseline/current.

Integration follows the repository's current-branch merge and push flow. Reuse
unchanged coverage when upstream changes are only the inspected design prose.
The final Git history and delivery report record the resulting commit, push and
cleanup, rather than adding a self-referencing validation receipt.
