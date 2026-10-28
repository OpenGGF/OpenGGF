# Remaining TAS support: composed receiving checks

## Status and source

This is an in-progress delivery record, not completed integration. The task base
is `8945c0a789b27be57b2361c70f561f31872a76ca`. The receiving checks below use
integration HEAD `3b6bc2fd7836046958f85c674cf9e11a2be2267d` plus the parent
stock reset registration and test changes. That receiving stage's tested `src` tree:
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

## Playback ownership review and follow-up

A pinned-source review of `ee9e721ed1ce8c9ef9a997263b0522024f31af66`
found two concrete composition defects. Active movie playback bypassed the
live `LevelInputOverlay`, so recorded P1 Start paused the SK intro and P2 Start
never reached it. Separately, a paused movie lost its post-reset menu owner,
which disconnected inputs and cursor advancement on resume.

Four new actual-session cases reproduced those failures: P1/P2 movie Start and
pause/resume before/after the first held menu action. **Four tests failed,
zero errors/skips**, outer exit 1; queue wait 5s, Maven 1m36s.

The internal `NativeLevelInputOwner` now receives the current published movie
sample before native pause admission. Live configuration overlays retain their
external-input guard. Publication does not advance the cursor. S3K's existing
intro/ending controller handler serves both ordinary and recorded consumers.
Reset-menu ownership now follows the attached timeline identity, preserving a
paused session while distinguishing a replacement timeline of the same movie.
A destination-owner control verifies that an old menu neither acknowledges its
row nor clears its input sample. Public BK2 pad/movie signatures are unchanged.

The first repair selection passed **27 tests, zero failures/errors/skips**,
outer exit 0, queue 2s, Maven 1m35s. Its requested stale bridge-control class
name matched no class; the actual bridge class was verified separately. That
request with `--lean` exhausted the compiler's 1 GiB heap before JUnit, after
39s queue and 56.867s Maven. It is a failed preparation, not a test result.
The required `--normal` retry passed **seven tests, zero failures/errors/skips**,
outer exit 0, Maven 32.871s; no source change or heap override was used.

After strengthening timeline-replacement ownership, the final affected selection
ran all existing/new reset and standalone receiving methods plus ordinary
overlay, pause, external-drive and bridge controls:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Ds3k.rom.path=${TAS_ROM_ROOT}/s3k.gen" \
  '-Dtest=com.openggf.tools.TestSonic3kStandaloneNativeLaunch,com.openggf.tools.TestTasS3kSessionReset,com.openggf.tools.TestTasS3kSessionResetOutput,com.openggf.TestMutatorOverlayRouting,com.openggf.TestRecordedInputHostPause,com.openggf.TestGameLoopExclusiveFrameDrive,com.openggf.debug.playback.TestPlaybackInputBridgeOwnership' test
```

**47 tests passed, zero failures/errors/skips**, outer exit 0; queue 6s,
Maven 1m39s. Tested `src` tree:
`e170932d7bdb723871def61853cb0461338f0cae`. This includes the replacement-timeline
control; no separate red execution is claimed for that additional control.
The previous 13 unchanged menu/capability/audio-composition successes remain
applicable, yielding **60 distinct parent receiving cases with successful
observations**, not one 60-case final command. Combined smoke/domain gates
remain separate.

## Native Competition reference boundary

The controller-only published input is a useful research artifact, but its
candidate Azure/GO boundary was never observed. A bounded original-S3 GPGX
prefix stayed outside Competition; its failed engine-alignment candidates are
not native physics or missing-host verdicts.

The native loop has four *opportunities*, not four unconditional actor passes.
`loc_4C1C` (s3.asm:6077-6086) normally sets `_unkFF7E` to zero; its debug/sound-test
branch can select a nonzero value. After the first body, `loc_4C78`
(s3.asm:6105-6130) admits later bodies only on accumulator carry. No four-pass
change was made to engine physics. This corrects the overbroad wording in the
external native-prefix README; its raw observations and provenance are unchanged.
