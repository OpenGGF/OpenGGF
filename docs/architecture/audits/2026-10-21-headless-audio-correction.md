# Headless audio ownership correction

Base: `7ce1638da385f852cc4b693ffaa50d0f37c94f71` (`develop`). Task:
`starch-rival:correct`, isolated branch `bugfix/ai-headless-audio-isolation`.

## Repeated class and scope

Headless hosts have repeatedly depended on callers preserving process-wide audio
ownership across lifecycle boundaries:

- `e03816bc9` corrected native stage probes to use the injected presentation owner
  and track the exact backend installed by the boot.
- `81a2e707f` released the audio ROM reference before closing its owning boot,
  preventing subsequent title launches from rebuilding loaders from a closed ROM.
- `bd042f080` documented that disabled-audio headless boots retain the inherited
  backend, requiring every reused host to replace it manually.
- `731307ac4` (examined as history, not incorporated here) corrected configured
  headless Engine launches that otherwise replaced the supplied offline owner.

The uncovered case on this base is disabled synthesis in `HeadlessGameBoot`.
Its test explicitly expected the inherited backend to survive. A live backend can
therefore survive a silent boot; a configuration flag is not device retirement.
This task closes that case, not every possible custom host's audio lifecycle.

Recent network/import bounds fixes already carry focused regressions. No generic
queue-size lint was added: bounds depend on admission, inflation and asynchronous
ownership semantics, and a token check would not establish those properties.
The reviewed PR 222 inline-comment endpoint returned no comments.

## Decision

Use architecture, the highest applicable level: the boot's existing installation
boundary now always selects a no-device backend. Enabled synthesis retains the
existing `HeadlessSmpsAudioBackend`; disabled synthesis installs `NullAudioBackend`
through `AudioManager.setBackend`, which retires the previous owner. The package
visible installation method permits ROM-free regression checks in ordinary CI;
ROM-backed tests verify the actual boot invokes it and still produces packets.

Rejected: retaining the caller checklist or adding a source-token lint. Both leave
runtime ownership dependent on unrelated callers. No backend type hierarchy or
new public API is needed. Device behavior in interactive Engine is unchanged.

## Verification

On the unchanged production base, the updated ROM-backed test ran three tests,
with one failure and no skips:
`TestHeadlessGameBootAudioIdentity.audioDisabledBootRetiresAnAlreadyInstalledBackend`:
`silent headless boots must replace inherited device ownership ==> expected: <false> but was: <true>`.
This is the actual retained-backend implementation, not a synthetic approximation.

Commands below normalize the tested absolute main-checkout path to
`MAIN_CHECKOUT`; the actual run supplied its existing S2 REV01 ROM.
Command (from the task worktree):

```sh
python3 tools/testing/maven_queue.py -Dmse=off \
  -Dtest=TestHeadlessGameBootAudioIdentity \
  "-Dsonic2.rom.path=${MAIN_CHECKOUT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" test
```

Preflight passed with `LUA_BIN=/usr/bin/lua5.4`; the default `lua` did not satisfy
Lua 5.4. The change-based plan selected 3,542 smoke classes because the boot is
unclassified shared code. Focused boot/audio ownership, installation and architecture
checks replace that disproportionate selection: no gameplay, timing, rendering or
shared mixer algorithm changes. Ordinary CI runs the ROM-free tests through its
existing Maven smoke selection; ROM-backed integration is separately validated
with the explicit existing S2 ROM. No release-only full suite is appropriate.

The ROM-free historical negative restored the base's missing disabled branch
inside the extracted method. Both tests failed, with no errors or skips:
`silentBootRetiresInheritedBackendInsteadOfTrustingTheConfigurationFlag` observed
zero retirement calls instead of one;
`synthesisUsesAnOfflineOwnerAndDisablingItRetiresThatOwner` observed
`HeadlessSmpsAudioBackend` instead of `NullAudioBackend`. Command:

```sh
python3 tools/testing/maven_queue.py -Dmse=off -Dtest=TestHeadlessAudioOwnership test
```

The fixed implementation passed 14 tests (zero failures, errors or skips):
`TestHeadlessAudioOwnership`, `TestHeadlessGameBootAudioIdentity`,
`TestAudioBackendLifetime`, and `TestAudioManagerRuntimeInstallation`. The command
also contained the nonexistent selector `TestHeadlessGameBootCleanup`; it selected
no tests and supplies no cleanup evidence. The final check uses the actual
`HeadlessGameBootTest` class instead.

```sh
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestHeadlessAudioOwnership,TestHeadlessGameBootAudioIdentity,TestHeadlessGameBootCleanup,TestAudioBackendLifetime,TestAudioManagerRuntimeInstallation' \
  "-Dsonic2.rom.path=${MAIN_CHECKOUT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" test
```

Final restored-source validation:

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=HeadlessGameBootTest,TestAudioPresentationArchitectureGuard,TestHeadlessAudioOwnership,TestHeadlessGameBootAudioIdentity' \
  "-Dsonic2.rom.path=${MAIN_CHECKOUT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" test
```

Result: 47 tests passed, zero failures/errors/skips: 38 audio architecture checks,
4 boot lifecycle checks, 2 ROM-free ownership checks and 3 S2 boot/audio checks.
These results apply to the restored task source on the pinned base; the final
commit adds only this evidence afterward. Mirrored policy/skills, skill links and
`git diff --check` passed. No broad smoke/full-suite result is claimed.
