# Remaining TAS support: composed receiving checks

## Status and source

This is an in-progress delivery record, not completed integration. The task base
is `8945c0a789b27be57b2361c70f561f31872a76ca`. The receiving checks below use
integration HEAD `3b6bc2fd7836046958f85c674cf9e11a2be2267d` plus the parent
stock reset registration and test changes. Final tested `src` tree:
`fa48fe2cb034f0d1a2604058ca75ec57c4b062cf`.

The isolated integration branch contains the pause, reset, ORT, DEZ, original
cartridge audio and current startup owners. Competition route qualification and
the P2 intro implementation remain worker-owned. Combined smoke coverage, the
four mandatory S3K gates, central verdict reconciliation, main-branch integration,
push and cleanup are not yet complete.

## Stock composition

The parent supplies explicit engine/root/input/mode-publication capabilities to
native Competition visits. The stock S3K module binds the standalone title exit,
suppresses the locked-on save grid for the actual SK cartridge, admits the
standalone intro input owner before the existing ending overlay, and registers
the native session reset policy.

Audio profiles are cached by the actual logical cartridge's resource enum,
without capturing its reader or a retired world. Original S3 Competition also
supplies its explicit audio profile and native sound map: level audio
configuration must not replace a valid map with an empty one.

Receiving fixtures use those stock startup/reset services. The reset lifecycle
fixture retains only its title-input observation seam; the separate output
fixture uses the real title. The composed reset/pause witness starts a fresh
ordinary FBZ world after actual reset, reaches normal admitted gameplay, then
uses the recorded-input ownership lease for real Start edges. Paused V-int
advances while gameplay/RNG freeze, and full registry replay across resume must
match. This is not the published no-Left/Right MHZ-to-FBZ route.

## Commands and observed results

`TAS_WORKTREE` denotes this isolated integration checkout and `TAS_ROM_ROOT`
the existing original-ROM directory. Exact local bindings and live command
handles are in the task ownership registry. No ROM was copied, renamed or linked.

```bash
cd "${TAS_WORKTREE}"
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic1.rom.path=${TAS_ROM_ROOT}/s1.gen" \
  "-Dsonic2.rom.path=${TAS_ROM_ROOT}/s2.gen" \
  "-Ds3k.rom.path=${TAS_ROM_ROOT}/s3k.gen" \
  "-Dkis2.rom.path=${TAS_ROM_ROOT}/kis2.gen" \
  '-Dtest=com.openggf.TestGameLoopNativeMenus,com.openggf.TestTasS3kTitleCapabilities,com.openggf.game.sonic3k.audio.TestSonic3kAudioModuleComposition,com.openggf.tools.TestTasS3kSessionReset,com.openggf.tools.TestTasS3kSessionResetOutput,com.openggf.tools.TestSonic3kStandaloneNativeLaunch' test
```

1. The first request acquired admission after 14 seconds, then stopped at
   test compilation: the newly added stock reset assertion lacked a
   `GameServices` import. Maven took 1m34s; outer exit 1; **zero tests ran**.
2. After that import correction, the same selection ran **25 tests: 24 passed,
   one failed, zero errors/skips**, outer exit 1. Queue wait 0s; Maven 34.811s.
   The failure was the new post-reset pause witness: a logical Start sample had
   no recorded-input ownership lease, so the production loop correctly did not
   treat it as an admitted recorded Start edge.
3. The helper now acquires that existing lease around each authored input step.
   Production code and assertions were unchanged. Only the affected method ran:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Ds3k.rom.path=${TAS_ROM_ROOT}/s3k.gen" \
  '-Dtest=com.openggf.tools.TestTasS3kSessionReset#ordinaryMovieKeepsItsLevelCursorAndRestoreForwardReplayAfterResetUsesFreshRegistry' test
```

The affected method **passed: one test, zero failures/errors/skips**, outer exit
0. Queue wait 10s; Maven 33.325s. The other 24 successful observations remain
applicable: only this new test helper changed between requests. Thus **25
distinct receiving cases have passing observations**, rather than a claim that
one final all-class command passed.

| Receiving class | Cases | Outcome after the affected recheck |
| --- | ---: | --- |
| `TestGameLoopNativeMenus` | 8 | Passed |
| `TestTasS3kTitleCapabilities` | 3 | Passed |
| `TestSonic3kAudioModuleComposition` | 2 | Passed |
| `TestSonic3kStandaloneNativeLaunch` | 5 | Passed |
| `TestTasS3kSessionReset` | 6 | Five unchanged passes; affected method passed |
| `TestTasS3kSessionResetOutput` | 1 | Passed |

## Owner evidence and limits

- [Pause clock and palette rewind](2026-10-28-tas-support-pause.md):
  `ce9005f0b`; includes the separately recorded Maven-success/outer-143
  observation and later clean receiving command.
- [Reset/Power](2026-10-28-tas-support-reset.md): `7274607b1`;
  representative lifecycle support, with the published no-Left/Right route
  unexecuted.
- [ORT placement recovery](2026-10-28-tas-support-ort.md): `d31cc5c44`;
  local cursor/allocation/identity semantics, excluding downstream KosM
  adjacent-memory corruption.
- [Ordered S2 object art](2026-10-28-tas-support-dez.md):
  implementation `b5c3f5594`, record `7e2560314`; real output qualified,
  quick-kill causal route and exact intermediate service timing unexecuted.
- [Cartridge audio](2026-10-28-tas-support-audio.md): `5d57120de`;
  NTSC resource/output support, with original-S3 PAL/SEGA differences retained.
- [Standalone startup](2026-10-28-tas-support-standalone.md):
  source through `b4a926761`; native title/MHZ startup and bounded ordinary
  controls, not full campaign, revision/viewport/team or exact pixel parity.

No full movie, release full suite or exhaustive configuration product was run.
The old verdicts and full test reel retain their original evidence identities.
